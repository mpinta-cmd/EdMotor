package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.edmotor.data.repository.PrivateAuthRepository
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.session.SessionManager

class AuthViewModelFactory(
    private val repository: UserRepository,
    private val sessionManager: SessionManager,
    private val privateMode: Boolean = false,
    private val privateAuth: PrivateAuthRepository? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository, sessionManager, privateMode, privateAuth) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
