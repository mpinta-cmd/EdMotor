package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.repository.UserAuthResult
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.session.RemoteSession
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.CancellationException
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

    private var authenticating = false

    init {
        viewModelScope.launch {
            restorePreviousSession()
        }
    }

    private suspend fun restorePreviousSession() {
        try {
            val saved = sessionManager.restoreSavedSession()
            if (saved == null) {
                _authState.value = AuthState.SignedOut
                return
            }
            val user = repository.findUserById(saved.localUserId)
            if (user == null || user.remoteAuthId != saved.remoteSession.userId) {
                clearSession()
                return
            }

            when (val result = repository.restoreSession(user, saved.remoteSession)) {
                is UserAuthResult.Success -> signIn(result.user, result.remoteSession)
                is UserAuthResult.Error -> {
                    clearSession()
                    _message.value = result.message
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _authState.value = AuthState.SignedOut
            _message.value = "No fue posible recuperar la sesión. Inicia sesión nuevamente."
        }
    }

    fun registerClient(
        username: String,
        password: String,
        displayName: String,
        phone: String?
    ) = authenticate { repository.registerClient(username, password, displayName, phone) }

    fun login(username: String, password: String) = authenticate { repository.login(username, password) }

    private fun authenticate(operation: suspend () -> UserAuthResult) {
        if (authenticating || _authState.value != AuthState.SignedOut) return
        authenticating = true
        viewModelScope.launch {
            _message.value = null
            try {
                when (val result = operation()) {
                    is UserAuthResult.Success -> signIn(result.user, result.remoteSession)
                    is UserAuthResult.Error -> _message.value = result.message
                }
            } finally {
                authenticating = false
            }
        }
    }

    private suspend fun signIn(user: UserEntity, remote: RemoteSession) {
        try {
            sessionManager.startSession(user, remote)
            repository.startRealtime(remote)
            _authState.value = AuthState.SignedIn(user)
            _message.value = null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            clearSession()
            _message.value = "No fue posible guardar la sesión en este dispositivo."
        }
    }

    fun logout() {
        if (_authState.value !is AuthState.SignedIn) return
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            _message.value = null
            clearSession()
        }
    }

    private suspend fun clearSession() {
        try {
            sessionManager.clearSession()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _message.value = "No fue posible borrar la sesión guardada."
        } finally {
            repository.logout()
            _authState.value = AuthState.SignedOut
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
