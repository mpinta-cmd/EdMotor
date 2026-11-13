
package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.edmotor.data.repository.ProductRepository
import com.example.edmotor.session.SessionManager

class ProductViewModelFactory(
    private val repository: ProductRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(ProductViewModel::class.java)) {

            @Suppress("UNCHECKED_CAST")
            return ProductViewModel(
                repository,
                sessionManager
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
