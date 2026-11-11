package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.edmotor.data.repository.CartRepository
import com.example.edmotor.data.repository.FavoriteRepository
import com.example.edmotor.data.repository.VehicleRepository

class EdMotorViewModelFactory(
    private val vehicleRepository: VehicleRepository,
    private val cartRepository: CartRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EdMotorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EdMotorViewModel(vehicleRepository, cartRepository, favoriteRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
