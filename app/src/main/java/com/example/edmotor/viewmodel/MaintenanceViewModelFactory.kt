package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.edmotor.data.repository.MaintenanceRepository
import com.example.edmotor.session.SessionManager

class MaintenanceViewModelFactory(
    private val repository: MaintenanceRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MaintenanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MaintenanceViewModel(repository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
