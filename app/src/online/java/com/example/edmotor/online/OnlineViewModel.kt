package com.example.edmotor.online

import android.app.Application
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.edmotor.BuildConfig
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val sessionStore = api?.let { OnlineSession(app, it.project) }
    private val catalogDatabase = Room.databaseBuilder(
        app, OnlineCatalogDatabase::class.java, "edmotor_online_catalog.db"
    ).build()
    private val requests = Mutex()
    private var refreshJob: Job? = null
    private val imageMemory = object : LruCache<String, ByteArray>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private val pendingOrders = mutableMapOf<Map<Long, Int>, String>()
    private val pendingAppointments = mutableMapOf<Booking, String>()
    private var restoreAllowed = true
    @Volatile private var liveSession: LiveSession? = null

    private val _state = MutableStateFlow(
        OnlineState(
            restoring = api != null,
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
        null
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun refresh() {
        if (api == null || _state.value.busy || refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            if (!requests.tryLock()) return@launch
            try {
                val current = liveSession ?: (if (restoreAllowed) restoreSession() else null) ?: return@launch
                refreshSession(current)
            } finally {
                requests.unlock()
            }
        }
        refreshJob?.start()
    }

    private suspend fun restoreSession(): LiveSession? {
        _state.update { it.copy(restoring = true) }
        return try {
            val token = withContext(Dispatchers.IO) { requireNotNull(sessionStore).load() } ?: return null
            val user = requireNotNull(api).service.me("Bearer $token")
            val restored = validatedSession(token, user)
            openSession(restored)
            restored
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (unauthorized(error)) expireSession()
            else {
                val warning = OnlineMessage(onlineError(error), true)
                _state.update { it.copy(message = warning, isStale = true) }
            }
            closeErrorResponse(error)
            null
        } finally {
            _state.update { it.copy(restoring = false) }
        }
    }

    private fun validatedSession(token: String, user: OnlineUser): LiveSession {
        check(validOnlineToken(token) && user.id > 0 && user.role in setOf("CLIENT", "WORKSHOP"))
        return LiveSession("Bearer $token", user)
    }

    private suspend fun openSession(session: LiveSession) {
        val project = requireNotNull(api).project
        val cached = capture { catalogDatabase.catalog().read(project).map { it.toModel() } }.getOrNull().orEmpty()
        resetSession()
        restoreAllowed = true
        liveSession = session
        _state.update {
            it.copy(
                user = session.user,
                loading = cached.isEmpty(),
                products = cached.sortedForCatalog()
            )
        }
    }

    private suspend fun refreshSession(session: LiveSession) = coroutineScope {
        val connection = requireNotNull(api)
        val service = connection.service
        _state.update { current ->
            current.copy(loading = current.lastSyncAtMillis == null && current.products.isEmpty() &&
                current.appointments.isEmpty() && current.orders.isEmpty() && current.vehicles.isEmpty())
        }
        try {
            val products = async { capture { service.products(session.auth) } }
            val orders = async { capture { service.orders(session.auth) } }
            val vehicles = async { capture { service.vehicles(session.auth) } }
            val appointments = async { capture { service.appointments(session.auth) } }
            val maintenance = async { capture { service.maintenance(session.auth) } }
            val productResult = products.await()
            val orderResult = orders.await()
            val vehicleResult = vehicles.await()
            val appointmentResult = appointments.await()
            val maintenanceResult = maintenance.await()
            val results = listOf(productResult, orderResult, vehicleResult, appointmentResult, maintenanceResult)
            val errors = results.mapNotNull { it.exceptionOrNull() }
            errors.forEach(::closeErrorResponse)
            if (errors.any(::unauthorized)) {
                expireSession()
                return@coroutineScope
            }
            if (liveSession !== session) return@coroutineScope

            val refreshedProducts = productResult.getOrNull()?.sortedForCatalog()
            if (refreshedProducts != null) {
                capture { catalogDatabase.catalog().replace(connection.project, refreshedProducts.map { it.toCache(connection.project) }) }
            }
            _state.update { current ->
                current.copy(
                    products = refreshedProducts ?: current.products,
                    orders = orderResult.getOrNull() ?: current.orders,
                    vehicles = vehicleResult.getOrNull() ?: current.vehicles,
                    appointments = appointmentResult.getOrNull() ?: current.appointments,
                    maintenance = maintenanceResult.getOrNull() ?: current.maintenance,
                    isStale = errors.isNotEmpty(),
                    lastSyncAtMillis = if (errors.isEmpty()) System.currentTimeMillis() else current.lastSyncAtMillis
                )
            }
        } finally {
            _state.update { it.copy(loading = false) }
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

    fun uploadImage(uri: Uri, onUploaded: (String) -> Unit) = runAction {
        val path = sendImage(requireNotNull(api).service, requireSession().auth, uri)
        return@runAction { onUploaded(path) }
    }

    suspend fun loadProductImage(imagePath: String): ByteArray? {
        val id = imageId(imagePath) ?: return null
        val current = liveSession ?: return null
        val service = api?.service ?: return null
        return try {
            val bytes = imageMemory.get(id) ?: withContext(Dispatchers.IO) {
                service.productImage(current.auth, id).use { it.byteStream().readWithin(MAX_IMAGE_BYTES) }
            }
            bytes.takeIf { liveSession === current }?.also { imageMemory.put(id, it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (unauthorized(error)) requestExpiration(current)
            closeErrorResponse(error)
            null
        }
    }

    fun createOrder(items: Map<Long, Int>, onCreated: () -> Unit = {}) {
        val selection = items.toMap()
        mutate("Pedido enviado.", onCreated) { service, auth ->
            if (selection.isEmpty() || selection.any { (id, count) -> id <= 0 || count <= 0 }) {
                throw OnlineInputError("Agrega repuestos válidos al carrito.")
            }
            val order = sendOnce(pendingOrders, selection) { requestId ->
                service.createOrder(auth, OnlineOrderWrite(requestId, selection.map { (id, count) -> OnlineOrderLine(id, count) }))
            }
            _state.update { it.copy(orders = it.orders.withItem(order, OnlineOrder::id).sortedByDescending { row -> row.createdAtMillis }) }
        }
    }

    fun decideOrder(orderId: Long, status: String) = mutate("Pedido actualizado.") { service, auth ->
        val order = service.decideOrder(auth, orderId, OnlineOrderDecision(status))
        _state.update { it.copy(orders = it.orders.withItem(order, OnlineOrder::id).sortedByDescending { row -> row.createdAtMillis }) }
    }

    fun createVehicle(brand: String, model: String, year: Int, plate: String, onCreated: () -> Unit = {}) =
        mutate("Vehículo guardado.", onCreated) { service, auth ->
            val vehicle = service.createVehicle(auth, OnlineVehicleWrite(brand.trim(), model.trim(), year, plate.trim()))
            _state.update { it.copy(vehicles = it.vehicles.withItem(vehicle, OnlineVehicle::id)) }
        }

    fun createAppointment(vehicleId: Long, scheduledAtMillis: Long, serviceType: String) =
        mutate("Solicitud de cita enviada.") { service, auth ->
            val booking = Booking(vehicleId, scheduledAtMillis, serviceType)
            val appointment = sendOnce(pendingAppointments, booking) { requestId ->
                service.createAppointment(auth, OnlineAppointmentWrite(requestId, vehicleId, scheduledAtMillis, serviceType))
            }
            updateAppointment(appointment)
        }

    fun changeAppointment(appointment: OnlineAppointment, action: String, scheduledAtMillis: Long? = null) =
        mutate("Cita actualizada.") { service, auth ->
            updateAppointment(service.changeAppointment(
                auth, appointment.id, OnlineAppointmentChange(appointment.version, action, scheduledAtMillis)
            ))
        }

    fun cancelAppointment(appointment: OnlineAppointment) = mutate("Cita cancelada.") { service, auth ->
        service.cancelAppointment(auth, appointment.id, appointment.version)
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
    ) = runAction {
        operation(requireNotNull(api).service, requireSession().auth)
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
                        needsRefresh = liveSession != null && (error as? HttpException)?.code() in setOf(404, 409, 410)
                        if (error is IOException && liveSession != null) _state.update { it.copy(isStale = true) }
                        null
                    }
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
            completion?.invoke()
            if (needsRefresh) refresh()
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
        pendingOrders.clear()
        pendingAppointments.clear()
        _state.update { OnlineState(restoring = false, busy = it.busy, configurationError = it.configurationError) }
    }

    private fun showMessage(text: String, isError: Boolean) = _state.update { it.copy(message = OnlineMessage(text, isError)) }

    override fun onCleared() {
        catalogDatabase.close()
        super.onCleared()
    }

    private class LiveSession(val auth: String, val user: OnlineUser)
    private data class Booking(val vehicleId: Long, val startsAtMillis: Long, val serviceType: String)

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

private suspend fun <K, V> sendOnce(attempts: MutableMap<K, String>, key: K, send: suspend (String) -> V): V {
    val id = attempts.getOrPut(key) { UUID.randomUUID().toString() }
    return try {
        send(id).also { attempts.remove(key) }
    } catch (error: HttpException) {
        if (error.code() in 400..499 && error.code() != 408) attempts.remove(key)
        throw error
    }
}

private fun List<OnlineProduct>.sortedForCatalog(): List<OnlineProduct> = sortedWith(
    compareBy<OnlineProduct> { it.promotionPriceCop == null }.thenBy { it.name.lowercase() }.thenBy { it.id }
)

private fun <T> List<T>.withItem(item: T, id: (T) -> Long): List<T> = filterNot { id(it) == id(item) } + item
