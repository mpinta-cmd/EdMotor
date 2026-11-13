
package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.edmotor.data.repository.OrderRepository
import com.example.edmotor.session.SessionManager

class OrderViewModelFactory(
    private val repository: OrderRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(OrderViewModel::class.java)) {

            @Suppress("UNCHECKED_CAST")
            return OrderViewModel(
                repository,
                sessionManager
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
