package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.repository.UserAuthResult
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.session.RemoteSession
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: UserEntity) : AuthState
}

class AuthViewModel(
    private val repository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val initializationError = repository.ensureDefaultWorkshopAccount()
            _message.value = initializationError
            _authState.value = AuthState.SignedOut
        }
    }

    fun registerClient(
        username: String,
        password: String,
        displayName: String,
        phone: String?
    ) {
        viewModelScope.launch {
            _message.value = null
            when (val result = repository.registerClient(username, password, displayName, phone)) {
                is UserAuthResult.Success -> signIn(result.user, result.remoteSession)
                is UserAuthResult.Error -> _message.value = result.message
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _message.value = null
            when (val result = repository.login(username, password)) {
                is UserAuthResult.Success -> signIn(result.user, result.remoteSession)
                is UserAuthResult.Error -> _message.value = result.message
            }
        }
    }

    private fun signIn(user: UserEntity, remoteSession: RemoteSession) {
        sessionManager.startSession(user, remoteSession)
        _authState.value = AuthState.SignedIn(user)
    }

    fun logout() {
        repository.logout()
        sessionManager.clearSession()
        _message.value = null
        _authState.value = AuthState.SignedOut
    }

    fun clearMessage() {
        _message.value = null
    }
}
