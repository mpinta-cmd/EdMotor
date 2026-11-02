
package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.edmotor.data.FAVORITES_CATEGORY

import com.example.edmotor.data.repository.VehicleRepository
import com.example.edmotor.data.repository.CartRepository
import com.example.edmotor.data.repository.FavoriteRepository

import com.example.edmotor.model.Vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect

import kotlinx.coroutines.launch


class EdMotorViewModel(
    private val vehicleRepository: VehicleRepository,
    private val cartRepository: CartRepository,
    private val favoriteRepository: FavoriteRepository

) : ViewModel() {


    // ==============================================
    // 1. BUSCADOR
    // ==============================================

    private val _searchQuery = MutableStateFlow("")

    val searchQuery: StateFlow<String> =
        _searchQuery.asStateFlow()


    fun updateSearchQuery(newText: String) {

        _searchQuery.value = newText

    }


    // ==============================================
    // 2. CATEGORÍAS
    // ==============================================

    private val _selectedCategory =
        MutableStateFlow<String?>(null)

    val selectedCategory: StateFlow<String?> =
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


    // ==============================================
    // 3. PRODUCTOS FAVORITOS
    // ==============================================

    val favoriteProductIds: StateFlow<Set<Int>> =
        favoriteRepository.favoriteProductIds.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet()
        )


    fun toggleFavorite(productId: Int) {

        viewModelScope.launch {

            favoriteRepository.toggleFavorite(productId)

        }
    }


    fun addFavorite(productId: Int) {

        viewModelScope.launch {

            favoriteRepository.addFavorite(productId)

        }
    }


    fun removeFavorite(productId: Int) {

        viewModelScope.launch {

            favoriteRepository.removeFavorite(productId)

        }
    }


    // ==============================================
    // 4. CARRITO DE COMPRAS
    // ==============================================

    val cartQuantity: StateFlow<Map<Int, Int>> =
        cartRepository.cartQuantity.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )


    // Agregar una unidad

    fun increaseQuantity(productId: Int) {

        viewModelScope.launch {

            cartRepository.increaseQuantity(productId)

        }
    }


    // Disminuir una unidad

    fun decreaseQuantity(productId: Int) {

        viewModelScope.launch {

            cartRepository.decreaseQuantity(productId)

        }
    }


    // Eliminar un producto del carrito

    fun removeProductFromCart(productId: Int) {

        viewModelScope.launch {

            cartRepository.removeProduct(productId)

        }
    }


    // Vaciar el carrito

    fun clearCart() {

        viewModelScope.launch {

            cartRepository.clearCart()

        }
    }


    // ==============================================
    // 5. MI GARAJE: OBSERVAR VEHÍCULOS
    // ==============================================

    val vehicles: StateFlow<List<Vehicle>> =
        vehicleRepository.vehicles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )


    // ==============================================
    // 6. AGREGAR VEHÍCULOS
    // ==============================================

    fun addVehicle(
        brand: String,
        model: String,
        year: Int,
        engine: String,
        plate: String,
        mileage: String,
        notes: String
    ) {

        viewModelScope.launch {

            vehicleRepository.addVehicle(
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


    // ==============================================
    // 7. ELIMINAR VEHÍCULOS
    // ==============================================

    fun deleteVehicle(vehicleId: Int) {

        viewModelScope.launch {

            vehicleRepository.deleteVehicle(vehicleId)

        }
    }


    // ==============================================
    // 8. ACTUALIZAR NOTAS
    // ==============================================

    fun updateVehicleNotes(
        vehicleId: Int,
        newNotes: String
    ) {

        viewModelScope.launch {

            vehicleRepository.updateVehicleNotes(
                vehicleId = vehicleId,
                newNotes = newNotes
            )

        }
    }


    // ==============================================
    // 9. ACTUALIZAR KILOMETRAJE
    // ==============================================

    fun updateVehicleMileage(
        vehicleId: Int,
        newMileage: String
    ) {

        viewModelScope.launch {

            vehicleRepository.updateVehicleMileage(
                vehicleId = vehicleId,
                newMileage = newMileage
            )

        }
    }


    // ==============================================
    // 10. ACTUALIZAR VEHÍCULO COMPLETO
    // ==============================================

    fun updateVehicle(vehicle: Vehicle) {

        viewModelScope.launch {

            vehicleRepository.updateVehicle(vehicle)

        }
    }


    // ==============================================
    // 11. CONTROL DE CATEGORÍA FAVORITOS
    // ==============================================

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
