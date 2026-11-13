
package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.edmotor.data.FAVORITES_CATEGORY
import com.example.edmotor.data.repository.CartOperationResult
import com.example.edmotor.data.repository.CartRepository
import com.example.edmotor.data.repository.FavoriteRepository
import com.example.edmotor.data.repository.VehicleRepository
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class ActiveAccount(
    val userId: Int,
    val role: UserRole
)

@OptIn(ExperimentalCoroutinesApi::class)
class EdMotorViewModel(
    private val vehicleRepository: VehicleRepository,
    private val cartRepository: CartRepository,
    private val favoriteRepository: FavoriteRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val activeAccount =
        MutableStateFlow<ActiveAccount?>(null)

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

            activeAccount.value = next

            _selectedCategory.value = null

            _searchQuery.value = ""
        }
    }

    fun clearActiveUser() {

        activeAccount.value = null

        _selectedCategory.value = null

        _searchQuery.value = ""

        _cartMessage.value = null
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

    val favoriteProductIds = activeAccount.flatMapLatest { account ->

        if (account == null) {
            flowOf(emptySet<Int>())
        } else {
            favoriteRepository.observeFavorites(account.userId)
        }

    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptySet()
    )

    fun toggleFavorite(productId: Int) {

        val userId = activeAccount.value?.userId ?: return

        viewModelScope.launch {
            favoriteRepository.toggleFavorite(
                userId,
                productId
            )
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

    val cartQuantity = activeAccount.flatMapLatest { account ->

        if (account == null) {
            flowOf(emptyMap<Int, Int>())
        } else {
            cartRepository.observeCart(account.userId)
        }

    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyMap()
    )

    // ==========================================
    // MENSAJES DEL CARRITO
    // ==========================================

    private val _cartMessage =
        MutableStateFlow<String?>(null)

    val cartMessage = _cartMessage.asStateFlow()

    fun clearCartMessage() {
        _cartMessage.value = null
    }

    // ==========================================
    // AGREGAR UNIDAD AL CARRITO
    // ==========================================

    fun increaseQuantity(productId: Int) {

        val userId = activeAccount.value?.userId ?: return

        viewModelScope.launch {

            when (
                val result = cartRepository.increaseQuantity(
                    userId = userId,
                    productId = productId
                )
            ) {

                is CartOperationResult.Success -> {
                    // Agregar al carrito es silencioso: no mostramos aviso inferior.
                    _cartMessage.value = null
                }

                is CartOperationResult.Error -> {
                    _cartMessage.value = result.message
                }
            }
        }
    }

    // ==========================================
    // DISMINUIR UNIDAD
    // ==========================================

    fun decreaseQuantity(productId: Int) {

        val userId = activeAccount.value?.userId ?: return

        viewModelScope.launch {

            cartRepository.decreaseQuantity(
                userId,
                productId
            )
        }
    }

    // ==========================================
    // ELIMINAR PRODUCTO
    // ==========================================

    fun removeProductFromCart(productId: Int) {

        val userId = activeAccount.value?.userId ?: return

        viewModelScope.launch {

            cartRepository.removeProduct(
                userId,
                productId
            )
        }
    }

    // ==========================================
    // VACIAR CARRITO
    // ==========================================

    fun clearCart() {

        val userId = activeAccount.value?.userId ?: return

        viewModelScope.launch {
            cartRepository.clearCart(userId)
        }
    }

    // ==========================================
    // VEHÍCULOS
    // ==========================================

    val vehicles = activeAccount.flatMapLatest { account ->

        when {

            account == null ->
                flowOf(emptyList<Vehicle>())

            account.role == UserRole.WORKSHOP ->
                vehicleRepository.observeForWorkshop()

            else ->
                vehicleRepository.observeByOwner(account.userId)
        }

    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    // ==========================================
    // CLIENTES VISIBLES PARA EL DUEÑO
    // ==========================================

    val clients = activeAccount.flatMapLatest { account ->
        if (account?.role == UserRole.WORKSHOP) {
            userRepository.observeClients()
        } else {
            flowOf(emptyList<UserEntity>())
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

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
        notes: String
    ) {

        val account = activeAccount.value ?: return

        if (account.role != UserRole.CLIENT) return

        viewModelScope.launch {

            vehicleRepository.addVehicle(
                ownerUserId = account.userId,
                brand = brand,
                model = model,
                year = year,
                engine = engine,
                plate = plate,
                mileage = mileage,
                notes = notes
            )
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

    // ==========================================
    // SINCRONIZAR FAVORITOS
    // ==========================================

    init {

        viewModelScope.launch {

            favoriteProductIds.collect { favorites ->

                if (
                    favorites.isEmpty() &&
                    _selectedCategory.value == FAVORITES_CATEGORY
                ) {

                    _selectedCategory.value = null
                }
            }
        }
    }
}
