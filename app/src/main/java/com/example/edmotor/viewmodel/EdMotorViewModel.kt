
package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.edmotor.data.FAVORITES_FILTER_KEY
import com.example.edmotor.data.repository.CartOperationResult
import com.example.edmotor.data.repository.CartRepository
import com.example.edmotor.data.repository.FavoriteRepository
import com.example.edmotor.data.repository.VehicleRepository
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private data class ActiveAccount(
    val userId: Int,
    val role: UserRole
)

private data class CartFeedback(val account: ActiveAccount, val message: String)

@OptIn(ExperimentalCoroutinesApi::class)
class EdMotorViewModel(
    private val vehicleRepository: VehicleRepository,
    private val cartRepository: CartRepository,
    private val favoriteRepository: FavoriteRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val activeAccount =
        MutableStateFlow<ActiveAccount?>(null)
    private var isSavingVehicle = false

    // ==========================================
    // SESIÓN ACTIVA
    // ==========================================

    fun setActiveUser(
        userId: Int,
        roleName: String
    ) {

        if (userId <= 0) return

        val role = UserRole.from(roleName)

        val next = ActiveAccount(userId, role)

        if (activeAccount.value != next) {
            _selectedCategory.value = null
            _searchQuery.value = ""
            cartFeedback.value = null
            activeAccount.value = next
        }
    }

    fun clearActiveUser() {

        activeAccount.value = null

        _selectedCategory.value = null

        _searchQuery.value = ""

        cartFeedback.value = null
    }

    // ==========================================
    // BÚSQUEDA
    // ==========================================

    private val _searchQuery =
        MutableStateFlow("")

    val searchQuery = _searchQuery.asStateFlow()

    fun updateSearchQuery(newText: String) {
        _searchQuery.value = newText
    }

    // ==========================================
    // CATEGORÍAS
    // ==========================================

    private val _selectedCategory =
        MutableStateFlow<String?>(null)

    val selectedCategory =
        _selectedCategory.asStateFlow()

    fun toggleCategory(categoryName: String) {

        _selectedCategory.value =
            if (_selectedCategory.value == categoryName) {
                null
            } else {
                categoryName
            }
    }

    fun selectCategory(categoryName: String?) {
        _selectedCategory.value = categoryName
    }

    // ==========================================
    // FAVORITOS
    // ==========================================

    fun observeFavorites(userId: Int, roleName: String): Flow<Set<Int>> =
        observeForAccount(userId, roleName, emptySet()) { account ->
            favoriteRepository.observeFavorites(account.userId).onEach { favorites ->
                if (activeAccount.value == account && favorites.isEmpty() &&
                    _selectedCategory.value == FAVORITES_FILTER_KEY
                ) {
                    _selectedCategory.value = null
                }
            }
        }

    fun toggleFavorite(productId: Int) {
        val account = activeAccount.value ?: return
        viewModelScope.launch {
            try {
                favoriteRepository.toggleFavorite(account.userId, productId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showAccountError(account, "No pudimos actualizar tus favoritos. Intenta de nuevo.")
            }
        }
    }

    fun addFavorite(productId: Int) {

        val userId = activeAccount.value?.userId ?: return

        viewModelScope.launch {
            favoriteRepository.addFavorite(
                userId,
                productId
            )
        }
    }

    fun removeFavorite(productId: Int) {

        val userId = activeAccount.value?.userId ?: return

        viewModelScope.launch {
            favoriteRepository.removeFavorite(
                userId,
                productId
            )
        }
    }

    // ==========================================
    // CARRITO
    // ==========================================

    fun observeCart(userId: Int, roleName: String): Flow<Map<Int, Int>> =
        observeForAccount(userId, roleName, emptyMap()) { account ->
            cartRepository.observeCart(account.userId)
        }

    // ==========================================
    // MENSAJES DEL CARRITO
    // ==========================================

    private val cartFeedback = MutableStateFlow<CartFeedback?>(null)

    fun observeCartMessage(userId: Int, roleName: String): Flow<String?> =
        observeForAccount(userId, roleName, null) { account ->
            cartFeedback.map { feedback -> feedback?.takeIf { it.account == account }?.message }
        }

    fun clearCartMessage() {
        cartFeedback.value = null
    }

    // ==========================================
    // AGREGAR UNIDAD AL CARRITO
    // ==========================================

    fun increaseQuantity(productId: Int) = changeCart { userId ->
        cartRepository.increaseQuantity(userId, productId)
    }

    // ==========================================
    // DISMINUIR UNIDAD
    // ==========================================

    fun decreaseQuantity(productId: Int) = changeCart { userId ->
        cartRepository.decreaseQuantity(userId, productId)
    }

    // ==========================================
    // ELIMINAR PRODUCTO
    // ==========================================

    fun removeProductFromCart(productId: Int) = changeCart { userId ->
        cartRepository.removeProduct(userId, productId)
    }

    // ==========================================
    // VACIAR CARRITO
    // ==========================================

    fun clearCart() = changeCart { userId -> cartRepository.clearCart(userId) }

    private fun changeCart(operation: suspend (Int) -> CartOperationResult) {
        val account = activeAccount.value?.takeIf { it.role == UserRole.CLIENT } ?: return
        viewModelScope.launch {
            val result = operation(account.userId)
            if (result is CartOperationResult.Error) showAccountError(account, result.message)
        }
    }

    private fun showAccountError(account: ActiveAccount, message: String) {
        if (activeAccount.value == account) cartFeedback.value = CartFeedback(account, message)
    }

    // ==========================================
    // VEHÍCULOS
    // ==========================================

    fun observeVehicles(userId: Int, roleName: String): Flow<List<Vehicle>> =
        observeForAccount(userId, roleName, emptyList()) { account ->
            if (account.role == UserRole.WORKSHOP) vehicleRepository.observeForWorkshop()
            else vehicleRepository.observeByOwner(account.userId)
        }

    // ==========================================
    // CLIENTES VISIBLES PARA EL DUEÑO
    // ==========================================

    fun observeClients(userId: Int, roleName: String): Flow<List<UserEntity>> =
        observeForAccount(userId, roleName, emptyList()) { account ->
            if (account.role == UserRole.WORKSHOP) userRepository.observeClients()
            else flowOf(emptyList())
        }

    // ==========================================
    // REGISTRAR VEHÍCULO DEL CLIENTE
    // ==========================================

    fun addVehicle(
        brand: String,
        model: String,
        year: Int,
        engine: String,
        plate: String,
        mileage: String,
        notes: String,
        onResult: (Result<Long>) -> Unit = {}
    ) {
        val account = activeAccount.value
        if (account?.role != UserRole.CLIENT) {
            onResult(Result.failure(IllegalStateException(
                "Inicia sesión como cliente para registrar el vehículo."
            )))
            return
        }
        if (isSavingVehicle) {
            onResult(Result.failure(IllegalStateException("Ya hay un vehículo guardándose.")))
            return
        }

        isSavingVehicle = true
        viewModelScope.launch {
            val result = try {
                val vehicleId = vehicleRepository.addVehicle(
                    ownerUserId = account.userId,
                    brand = brand,
                    model = model,
                    year = year,
                    engine = engine,
                    plate = plate,
                    mileage = mileage,
                    notes = notes
                )
                Result.success(vehicleId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(IllegalStateException(
                    "No fue posible guardar el vehículo. Intenta de nuevo.", e
                ))
            } finally {
                isSavingVehicle = false
            }
            if (activeAccount.value == account) onResult(result)
        }
    }

    // ==========================================
    // ASIGNAR VEHÍCULO A CLIENTE
    // ==========================================

    fun assignVehicleToClient(
        vehicleId: Int,
        clientUserId: Int
    ) {

        if (activeAccount.value?.role != UserRole.WORKSHOP) return

        viewModelScope.launch {

            vehicleRepository.assignVehicleToClient(
                vehicleId,
                clientUserId
            )
        }
    }

    // ==========================================
    // ACTUALIZAR VEHÍCULO DEL PROPIETARIO
    // ==========================================

    fun updateVehicle(vehicle: Vehicle) {

        val account = activeAccount.value ?: return

        if (account.role != UserRole.CLIENT) return

        viewModelScope.launch {

            vehicleRepository.updateVehicleForOwner(
                account.userId,
                vehicle
            )
        }
    }

    private fun <T> observeForAccount(
        userId: Int,
        roleName: String,
        emptyValue: T,
        observe: (ActiveAccount) -> Flow<T>
    ): Flow<T> {
        val requested = ActiveAccount(userId, UserRole.from(roleName))
        return activeAccount.flatMapLatest { active ->
            if (active == requested) observe(requested) else flowOf(emptyValue)
        }.map { value -> if (activeAccount.value == requested) value else emptyValue }
    }
}
