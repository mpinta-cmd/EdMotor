package com.example.edmotor.online

import android.app.Application
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.edmotor.BuildConfig
import com.example.edmotor.model.UserRole
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

class OnlineViewModel(private val app: Application) : AndroidViewModel(app) {
    private val api = OnlineApi.fromUrl(BuildConfig.EDMOTOR_ONLINE_URL, BuildConfig.DEBUG)
    private val vehicleModelLookup by lazy { VehicleModelLookupRepository.create() }
    private val sessionStore = api?.let { OnlineSession(app, it.project) }
    private val cartStore = api?.let { OnlineCartStore(app, it.project) }
    private val catalogDatabase = Room.databaseBuilder(
        app, OnlineCatalogDatabase::class.java, "edmotor_online_catalog.db"
    ).addMigrations(OnlineCatalogDatabase.MIGRATION_1_2).build()
    private val requests = Mutex()
    private var refreshJob: Job? = null
    private val imageMemory = object : LruCache<String, ByteArray>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private var catalogCacheLoaded = false
    private var restoreAllowed = true
    @Volatile private var liveSession: LiveSession? = null

    private val _state = MutableStateFlow(
        OnlineState(
            cart = cartStore?.read().orEmpty(),
            favoriteProductIds = cartStore?.favorites().orEmpty(),
            configurationError = if (api == null) "Configura una URL HTTPS válida para el servidor de EdMotor." else null
        )
    )
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun login(username: String, password: String) = authenticate { service ->
        service.login(OnlineCredentials(username.trim(), password))
    }

    fun register(username: String, displayName: String, password: String) = authenticate { service ->
        service.register(OnlineRegistration(username.trim(), displayName.trim(), password))
    }

    fun registerOwner(username: String, displayName: String, password: String, activationCode: String) =
        authenticate { service ->
            service.registerOwner(OnlineOwnerRegistration(username.trim(), displayName.trim(), password, activationCode.trim()))
        }

    private fun authenticate(authenticate: suspend (OnlineService) -> OnlineAuthResponse) = runAction {
        if (liveSession != null) return@runAction null
        val service = requireNotNull(api).service
        val response = authenticate(service)
        val session = validatedSession(response.token, response.user)
        try {
            withContext(Dispatchers.IO) { requireNotNull(sessionStore).save(response.token) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            closeErrorResponse(capture { service.logout(session.auth) }.exceptionOrNull())
            throw OnlineInputError("El teléfono no pudo guardar la sesión de forma segura.")
        }
        openSession(session)
        return@runAction { refresh() }
    }

    fun logout() = runAction {
        val current = liveSession
        restoreAllowed = false
        resetSession()
        val local = capture { withContext(Dispatchers.IO) { requireNotNull(sessionStore).clear() } }
        val remote = if (current == null) Result.success(Unit) else capture { requireNotNull(api).service.logout(current.auth) }
        val remoteClosed = remote.isSuccess || (remote.exceptionOrNull() as? HttpException)?.code() == 401
        closeErrorResponse(remote.exceptionOrNull())
        if (local.isFailure) {
            showMessage("El teléfono no confirmó que se borró la sesión guardada.", true)
        } else if (!remoteClosed) {
            showMessage("Sesión cerrada en este teléfono. El servidor no confirmó el cierre.", true)
        }
        return@runAction { refresh() }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun refresh() {
        if (api == null || _state.value.busy || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            refreshReads()
            val retrying = _state.value.sync.filterValues { it.retrying }.keys
            if (retrying.isNotEmpty()) {
                delay(2_000)
                refreshReads(retrying)
            }
        }
        refreshJob?.start()
    }

    private suspend fun refreshReads(only: Set<OnlineResource>? = null) {
        if (_state.value.busy || !requests.tryLock()) return
        try {
            loadCatalogCache()
            val existing = liveSession
            val session = existing ?: if (restoreAllowed) restoreSession() else null
            if (session == null) {
                refreshPublicCatalog()
            } else {
                refreshSession(session, if (existing == null || only == null) OnlineResource.entries.toSet() else only)
            }
        } finally {
            requests.unlock()
        }
    }

    private suspend fun restoreSession(): LiveSession? {
        return try {
            val token = withContext(Dispatchers.IO) { requireNotNull(sessionStore).load() }
            if (token == null) {
                restoreAllowed = false
                return null
            }
            val user = requireNotNull(api).service.me("Bearer $token")
            val restored = validatedSession(token, user)
            openSession(restored)
            restored
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (unauthorized(error)) expireSession()
            closeErrorResponse(error)
            null
        }
    }

    private fun validatedSession(token: String, user: OnlineUser): LiveSession {
        check(validOnlineToken(token) && user.id > 0 && user.role in setOf("CLIENT", "WORKSHOP"))
        val auth = "Bearer $token"
        return LiveSession(auth, user, OnlineAppointmentRepository(requireNotNull(api).service, auth))
    }

    private suspend fun openSession(session: LiveSession) {
        val clientId = session.user.id
        val (cart, pending, favorites) = withContext(Dispatchers.IO) {
            val store = requireNotNull(cartStore)
            if (session.user.role == UserRole.CLIENT.name) {
                Triple(store.moveGuestCartTo(clientId), store.pendingOrder(clientId), store.moveGuestFavoritesTo(clientId))
            } else {
                Triple(emptyMap(), null, store.favorites(clientId))
            }
        }
        resetSession()
        restoreAllowed = true
        liveSession = session
        _state.update { it.copy(user = session.user, cart = cart, pendingOrder = pending, favoriteProductIds = favorites) }
    }

    private suspend fun loadCatalogCache() {
        if (catalogCacheLoaded) return
        val project = requireNotNull(api).project
        val cached = capture { catalogDatabase.catalog().read(project).map { it.toModel() } }.getOrNull() ?: return
        catalogCacheLoaded = true
        _state.update { it.copy(products = cached.sortedForCatalog()) }
    }

    private suspend fun refreshPublicCatalog() {
        refreshResource(OnlineResource.CATALOG, { storeCatalog(requireNotNull(api).service.products()) }) {
            copy(products = it)
        }
    }

    private suspend fun storeCatalog(products: List<OnlineProduct>): List<OnlineProduct> {
        val sorted = products.sortedForCatalog()
        val project = requireNotNull(api).project
        capture { catalogDatabase.catalog().replace(project, sorted.map { it.toCache(project) }) }
        catalogCacheLoaded = true
        return sorted
    }

    private suspend fun refreshSession(session: LiveSession, resources: Set<OnlineResource>) {
        val service = requireNotNull(api).service
        try {
            coroutineScope {
                if (OnlineResource.CATALOG in resources) launch {
                    refreshResource(OnlineResource.CATALOG, { storeCatalog(service.products(session.auth)) }) {
                        copy(products = it)
                    }
                }
                if (OnlineResource.ORDERS in resources) launch {
                    refreshResource(OnlineResource.ORDERS, { service.orders(session.auth) }) { copy(orders = it) }
                }
                if (session.user.role == UserRole.CLIENT.name && OnlineResource.RATINGS in resources) launch {
                    refreshResource(OnlineResource.RATINGS, { service.eligibleRatings(session.auth) }) {
                        copy(eligibleRatings = it)
                    }
                }
                if (OnlineResource.VEHICLES in resources) launch {
                    refreshResource(OnlineResource.VEHICLES, { service.vehicles(session.auth) }) { copy(vehicles = it) }
                }
                if (OnlineResource.APPOINTMENTS in resources) launch {
                    refreshResource(OnlineResource.APPOINTMENTS, session.appointments::list) { copy(appointments = it) }
                }
                if (OnlineResource.MAINTENANCE in resources) launch {
                    refreshResource(OnlineResource.MAINTENANCE, { service.maintenance(session.auth) }) { copy(maintenance = it) }
                }
            }
            if (OnlineResource.ORDERS in resources) recoverPendingOrder(service, session)
        } catch (error: HttpException) {
            if (!unauthorized(error)) throw error
            expireSession()
            refreshPublicCatalog()
        }
    }

    private suspend fun <T : Any> refreshResource(
        resource: OnlineResource,
        fetch: suspend () -> T,
        adopt: OnlineState.(T) -> OnlineState
    ) {
        val result = capture(fetch)
        val failure = result.exceptionOrNull()
        closeErrorResponse(failure)
        if (failure is HttpException && unauthorized(failure) && liveSession != null) throw failure
        _state.update { current ->
            val updated = result.getOrNull()?.let { current.adopt(it) } ?: current
            updated.copy(sync = current.sync + (resource to current.syncStatus(resource).result(result.isSuccess)))
        }
    }

    fun saveProduct(
        draft: OnlineProductDraft,
        product: OnlineProduct? = null,
        imageUri: Uri? = null,
        onSaved: () -> Unit = {}
    ) = mutate("Repuesto guardado.", onSaved) { service, auth ->
        val imagePath = if (imageUri != null) sendImage(service, auth, imageUri) else draft.imagePath
        val body = OnlineProductWrite(
            draft.name.trim(), draft.description.trim(), draft.category.trim(), draft.priceCop,
            draft.promotionPriceCop, draft.stock, imagePath, product?.version
        )
        val saved = if (product == null) service.createProduct(auth, body) else service.updateProduct(auth, product.id, body)
        _state.update { it.copy(products = it.products.withItem(saved, OnlineProduct::id).sortedForCatalog()) }
    }

    fun deleteProduct(product: OnlineProduct) = mutate("Repuesto retirado del catálogo.") { service, auth ->
        service.deleteProduct(auth, product.id, product.version)
        _state.update { current -> current.copy(products = current.products.filterNot { it.id == product.id }) }
    }

    fun adjustInventoryStock(productId: Long, delta: Int) {
        if (_state.value.user?.role != UserRole.WORKSHOP.name || delta == 0) return
        val product = _state.value.products.firstOrNull { it.id == productId } ?: return
        val stock = product.stock.toLong() + delta
        if (stock !in 0L..Int.MAX_VALUE.toLong()) {
            showMessage("La cantidad no coincide con las existencias actuales.", true)
            return
        }
        saveProduct(
            OnlineProductDraft(
                name = product.name,
                description = product.description,
                category = product.category,
                priceCop = product.priceCop,
                promotionPriceCop = product.promotionPriceCop,
                stock = stock.toInt(),
                imagePath = product.imagePath
            ),
            product
        )
    }

    fun uploadImage(uri: Uri, onUploaded: (String) -> Unit) = runAction {
        val path = sendImage(requireNotNull(api).service, requireSession().auth, uri)
        return@runAction { onUploaded(path) }
    }

    suspend fun loadProductImage(imagePath: String): ByteArray? {
        val id = imageId(imagePath) ?: return null
        val owner = liveSession?.takeIf { it.user.role == UserRole.WORKSHOP.name }
        val service = api?.service ?: return null
        return try {
            val bytes = imageMemory.get(id) ?: withContext(Dispatchers.IO) {
                service.productImage(owner?.auth, id).use { it.byteStream().readWithin(MAX_IMAGE_BYTES) }
            }
            bytes.takeIf { owner == null || liveSession === owner }?.also { imageMemory.put(id, it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (owner != null && unauthorized(error)) requestExpiration(owner)
            closeErrorResponse(error)
            null
        }
    }

    fun addToCart(productId: Long) {
        val count = _state.value.cart[productId] ?: 0
        if (count < Int.MAX_VALUE) changeCartQuantity(productId, count + 1)
    }

    fun removeFromCart(productId: Long) {
        val count = _state.value.cart[productId] ?: return
        if (count > 0) changeCartQuantity(productId, count - 1)
    }

    fun toggleFavorite(productId: Long) {
        val current = _state.value
        val store = cartStore ?: return
        if (current.busy || current.products.none { it.id == productId }) return
        val favorites = if (productId in current.favoriteProductIds) {
            current.favoriteProductIds - productId
        } else current.favoriteProductIds + productId
        store.saveFavorites(liveSession?.user?.id, favorites)
        _state.update { it.copy(favoriteProductIds = favorites) }
    }

    fun changeCartQuantity(productId: Long, quantity: Int) {
        val current = _state.value
        if (current.busy || current.pendingOrder != null || current.user?.role == UserRole.WORKSHOP.name) return
        val stock = current.products.firstOrNull { it.id == productId }?.stock ?: current.cart[productId] ?: 0
        val count = quantity.coerceIn(0, stock.coerceAtLeast(0))
        updateCart(if (count == 0) current.cart - productId else current.cart + (productId to count))
    }

    private fun updateCart(cart: Map<Long, Int>) {
        cartStore?.save(liveSession?.user?.id, cart)
        _state.update { it.copy(cart = cart) }
    }

    fun confirmPurchase(items: Map<Long, Int>, expectedTotalCop: Long?, expectedRequestId: String?) {
        val selection = items.toMap()
        val successMessage = if (expectedTotalCop == null) "Pedido enviado." else "Compra confirmada."
        mutate(successMessage) { service, auth ->
            val client = requireSession().user
            if (client.role != UserRole.CLIENT.name) throw OnlineInputError("Entra con tu perfil de cliente para finalizar la compra.")
            if (expectedRequestId == null && (expectedTotalCop == null || expectedTotalCop <= 0L)) {
                throw OnlineInputError("Revisa el total antes de confirmar la compra.")
            }
            val selected = _state.value.checkoutItems(selection, expectedRequestId)
            if (selected.isEmpty() || selected.size > 100 || selected.any { (id, count) -> id <= 0 || count <= 0 }) {
                throw OnlineInputError("Agrega repuestos válidos al carrito.")
            }
            val store = requireNotNull(cartStore)
            val pending = withContext(Dispatchers.IO) {
                if (store.pendingOrder(client.id)?.requestId != expectedRequestId) {
                    throw OnlineInputError("El estado de tu envío cambió. Revisa el carrito y Mis pedidos.")
                }
                store.prepareOrder(client.id, selected, expectedTotalCop)
            }
            _state.update { it.copy(pendingOrder = pending) }
            val lines = pending.items.map { (id, count) -> OnlineOrderLine(id, count) }
            val total = pending.expectedTotalCop
            val order = try {
                if (total == null) service.createOrder(auth, OnlineOrderWrite(pending.requestId, lines))
                else service.checkout(auth, OnlineCheckoutWrite(pending.requestId, lines, total))
            } catch (error: HttpException) {
                if (error.response()?.headers()?.get("EdMotor-Order-Result") == "rejected") {
                    withContext(Dispatchers.IO) { store.discardOrder(client.id, pending) }
                    _state.update { it.copy(pendingOrder = null) }
                }
                throw error
            }
            acknowledgeOrder(client.id, pending, order)
        }
    }

    private suspend fun recoverPendingOrder(service: OnlineService, session: LiveSession) {
        val pending = _state.value.pendingOrder ?: return
        val result = capture { service.orderByRequest(session.auth, pending.requestId) }
        val error = result.exceptionOrNull()
        closeErrorResponse(error)
        if (error is HttpException && unauthorized(error)) throw error
        val order = result.getOrNull() ?: return
        if (capture { acknowledgeOrder(session.user.id, pending, order) }.isSuccess) {
            showMessage("Encontramos tu pedido #${order.id}. Puedes seguirlo en Mis pedidos.", false)
        }
    }

    private suspend fun acknowledgeOrder(clientId: Long, pending: PendingOnlineOrder, order: OnlineOrder) {
        if (order.items.associate { it.productId to it.quantity } != pending.items ||
            (pending.expectedTotalCop != null && order.totalCop != pending.expectedTotalCop)
        ) {
            throw OnlineInputError("Aún estamos verificando tu pedido. Conservamos los repuestos del envío.")
        }
        // A foreground action can cancel background recovery. Keep the disk update and visible cart together.
        withContext(NonCancellable) {
            val cart = withContext(Dispatchers.IO) { requireNotNull(cartStore).acknowledgeOrder(clientId, pending) }
            val products = applyInventoryUpdates(order.inventoryUpdates)
            _state.update {
                it.copy(
                    cart = cart,
                    pendingOrder = null,
                    checkoutReceiptId = order.id,
                    products = products ?: it.products,
                    orders = it.orders.withItem(order, OnlineOrder::id).sortedByDescending { row -> row.createdAtMillis }
                )
            }
        }
    }

    fun clearCheckoutReceipt() = _state.update { it.copy(checkoutReceiptId = null) }

    private suspend fun applyInventoryUpdates(updates: List<OnlineProduct>?): List<OnlineProduct>? {
        if (updates.isNullOrEmpty()) return null
        val catalog = _state.value.products.associateBy(OnlineProduct::id).toMutableMap()
        for (product in updates) {
            val previous = catalog[product.id]
            if (previous == null || product.version >= previous.version) catalog[product.id] = product
        }
        return storeCatalog(catalog.values.toList())
    }

    fun decideOrder(orderId: Long, status: String) = mutate("Pedido actualizado.") { service, auth ->
        val order = service.decideOrder(auth, orderId, OnlineOrderDecision(status))
        val products = applyInventoryUpdates(order.inventoryUpdates)
        _state.update {
            it.copy(
                products = products ?: it.products,
                orders = it.orders.withItem(order, OnlineOrder::id).sortedByDescending { row -> row.createdAtMillis }
            )
        }
    }

    fun rateProduct(productId: Long, stars: Int) = mutateSession("Calificación guardada.") { session ->
        if (session.user.role != UserRole.CLIENT.name) {
            throw OnlineInputError("Entra con tu cuenta de cliente para calificar el repuesto.")
        }
        if (productId <= 0L || stars !in 1..5) throw OnlineInputError("Elige de 1 a 5 estrellas.")
        val result = requireNotNull(api).service.rateProduct(session.auth, productId, OnlineRatingWrite(stars))
        val average = result.averageRating
        if (result.productId != productId || result.myRating != stars || result.ratingCount < 1L ||
            average == null || average !in 1.0..5.0
        ) {
            throw OnlineInputError("No pudimos verificar la calificación. Actualiza el catálogo para revisar el resultado.")
        }
        val catalog = _state.value.products
        val products = if (catalog.any { it.id == productId }) {
            storeCatalog(catalog.map { product ->
                if (product.id == productId) {
                    product.copy(averageRating = average, ratingCount = result.ratingCount)
                } else product
            })
        } else catalog
        _state.update { current ->
            current.copy(
                products = products,
                eligibleRatings = current.eligibleRatings.map { item ->
                    if (item.productId == productId) item.copy(myRating = result.myRating) else item
                }
            )
        }
    }

    fun createVehicle(brand: String, model: String, year: Int, plate: String, onCreated: () -> Unit = {}) =
        mutate("Vehículo guardado.", onCreated) { service, auth ->
            val vehicle = service.createVehicle(auth, OnlineVehicleWrite(brand.trim(), model.trim(), year, plate.trim()))
            _state.update { it.copy(vehicles = it.vehicles.withItem(vehicle, OnlineVehicle::id)) }
        }

    internal suspend fun findVehicleModels(brand: String, year: Int): List<VehicleModelSuggestion> =
        vehicleModelLookup.findModels(brand, year)

    internal fun onlineProfileScope(userId: Long?): String =
        "online:${api?.project.orEmpty()}:${userId ?: "guest"}"

    internal fun inventoryOwnerSession(userId: Long?): Any? =
        liveSession?.takeIf { it.user.id == userId && it.user.role == UserRole.WORKSHOP.name }

    internal fun isInventoryOwnerSession(sessionIdentity: Any): Boolean = liveSession === sessionIdentity

    internal suspend fun confirmInventoryOwnerPassword(sessionIdentity: Any, password: String) {
        val current = liveSession?.takeIf { it === sessionIdentity && it.user.role == UserRole.WORKSHOP.name }
            ?: throw OnlineInputError("Vuelve a iniciar sesión como dueño.")
        val service = requireNotNull(api).service
        val confirmation = try {
            service.login(OnlineCredentials(current.user.username, password))
        } catch (error: HttpException) {
            if (error.code() == 401) {
                closeErrorResponse(error)
                throw OnlineInputError("La contraseña de EdMotor no coincide.")
            }
            throw error
        }
        val confirmationAuth = "Bearer ${confirmation.token}"
        val temporary = validOnlineToken(confirmation.token) && confirmationAuth != current.auth
        if (temporary) {
            viewModelScope.launch {
                closeErrorResponse(capture { service.logout(confirmationAuth) }.exceptionOrNull())
            }
        }
        if (!temporary || liveSession !== current || confirmation.user.id != current.user.id ||
            confirmation.user.role != UserRole.WORKSHOP.name
        ) {
            throw OnlineInputError("No se pudo confirmar la cuenta del dueño. Vuelve a iniciar sesión.")
        }
    }

    fun createAppointment(vehicleId: Long, scheduledAtMillis: Long, serviceType: String) =
        mutateSession("Solicitud de cita enviada.") { session ->
            updateAppointment(session.appointments.create(vehicleId, scheduledAtMillis, serviceType))
        }

    fun changeAppointment(appointment: OnlineAppointment, action: String, scheduledAtMillis: Long? = null) =
        mutateSession("Cita actualizada.") { session ->
            updateAppointment(session.appointments.update(appointment, action, scheduledAtMillis))
        }

    fun cancelAppointment(appointment: OnlineAppointment) = mutateSession("Cita cancelada.") { session ->
        session.appointments.delete(appointment)
        _state.update { current -> current.copy(appointments = current.appointments.filterNot { it.id == appointment.id }) }
    }

    fun recordMaintenance(appointmentId: Long, summary: String, mileageKm: Int? = null, onSaved: () -> Unit = {}) =
        mutate("Mantenimiento registrado.", onSaved) { service, auth ->
            val report = service.recordMaintenance(auth, appointmentId, OnlineMaintenanceWrite(summary.trim(), mileageKm))
            _state.update { it.copy(maintenance = it.maintenance.withItem(report, OnlineMaintenance::id)) }
        }

    private fun updateAppointment(appointment: OnlineAppointment) = _state.update {
        it.copy(appointments = it.appointments.withItem(appointment, OnlineAppointment::id).sortedBy { row -> row.scheduledAtMillis })
    }

    private fun mutate(
        message: String,
        onSaved: () -> Unit = {},
        operation: suspend (OnlineService, String) -> Unit
    ) = mutateSession(message, onSaved) { session ->
        operation(requireNotNull(api).service, session.auth)
    }

    private fun mutateSession(
        message: String,
        onSaved: () -> Unit = {},
        operation: suspend (LiveSession) -> Unit
    ) = runAction {
        operation(requireSession())
        showMessage(message, false)
        return@runAction { onSaved(); refresh() }
    }

    private fun runAction(action: suspend () -> (() -> Unit)?) {
        if (api == null || _state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        refreshJob?.cancel()
        viewModelScope.launch {
            var needsRefresh = false
            val completion = try {
                requests.withLock {
                    try {
                        action()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (unauthorized(error) && liveSession != null) expireSession()
                        else showMessage(onlineError(error), true)
                        closeErrorResponse(error)
                        needsRefresh = liveSession != null && (
                            _state.value.pendingOrder != null || (error as? HttpException)?.code() in setOf(404, 409, 410)
                        )
                        null
                    }
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
            completion?.invoke()
            if (needsRefresh || (completion == null && _state.value.syncStatus(OnlineResource.CATALOG).waitingForFirstData)) {
                refresh()
            }
        }
    }

    private suspend fun sendImage(service: OnlineService, auth: String, uri: Uri): String {
        if (uri.scheme !in setOf("content", "android.resource")) throw OnlineInputError("Selecciona una imagen del teléfono.")
        val bytes = withContext(Dispatchers.IO) {
            try {
                app.contentResolver.openInputStream(uri)?.use { it.readWithin(MAX_IMAGE_BYTES) }
            } catch (_: IOException) {
                throw OnlineInputError("No se pudo leer la imagen. Usa una imagen de hasta 5 MB.")
            } catch (_: SecurityException) {
                throw OnlineInputError("Selecciona la imagen de nuevo para permitir el acceso.")
            }
        } ?: throw OnlineInputError("No se pudo abrir la imagen seleccionada.")
        val extension = when {
            bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "jpeg"
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)) -> "png"
            bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> "webp"
            else -> throw OnlineInputError("Elige una imagen JPEG, PNG o WebP.")
        }
        val part = MultipartBody.Part.createFormData("file", "repuesto.$extension", bytes.toRequestBody("image/$extension".toMediaType()))
        val uploaded = service.uploadImage(auth, part).imagePath
        check(imageId(uploaded) != null)
        return uploaded
    }

    private fun requireSession(): LiveSession = liveSession ?: throw OnlineInputError("Inicia sesión para continuar.")

    private fun requestExpiration(session: LiveSession) {
        viewModelScope.launch {
            requests.withLock { if (liveSession === session) expireSession() }
        }
    }

    private suspend fun expireSession() {
        restoreAllowed = false
        resetSession()
        capture { withContext(Dispatchers.IO) { requireNotNull(sessionStore).clear() } }
        showMessage("Tu sesión venció. Vuelve a iniciar sesión.", true)
    }

    private fun resetSession() {
        liveSession = null
        imageMemory.evictAll()
        val guestCart = cartStore?.read().orEmpty()
        val guestFavorites = cartStore?.favorites().orEmpty()
        _state.update {
            OnlineState(
                busy = it.busy,
                configurationError = it.configurationError,
                products = it.products,
                cart = guestCart,
                favoriteProductIds = guestFavorites,
                sync = mapOf(OnlineResource.CATALOG to it.syncStatus(OnlineResource.CATALOG))
            )
        }
    }

    private fun showMessage(text: String, isError: Boolean) = _state.update { it.copy(message = OnlineMessage(text, isError)) }

    override fun onCleared() {
        catalogDatabase.close()
        super.onCleared()
    }

    private class LiveSession(
        val auth: String,
        val user: OnlineUser,
        val appointments: OnlineAppointmentRepository
    )

    private companion object {
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    }
}

private suspend fun <T> capture(operation: suspend () -> T): Result<T> = try {
    Result.success(operation())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

private fun unauthorized(error: Throwable): Boolean = (error as? HttpException)?.code() == 401

private fun closeErrorResponse(error: Throwable?) {
    (error as? HttpException)?.response()?.errorBody()?.close()
}

private fun List<OnlineProduct>.sortedForCatalog(): List<OnlineProduct> = sortedWith(
    compareBy<OnlineProduct> { it.promotionPriceCop == null }.thenBy { it.name.lowercase() }.thenBy { it.id }
)

private fun <T> List<T>.withItem(item: T, id: (T) -> Long): List<T> = filterNot { id(it) == id(item) } + item
