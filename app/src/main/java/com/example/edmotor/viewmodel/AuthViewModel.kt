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

            restorePreviousSession()
        }
    }

    /**
     * Recupera el usuario y los tokens que quedaron guardados al cerrar EdMotor.
     */
    private suspend fun restorePreviousSession() {
        val savedSession = try {
            sessionManager.restoreSavedSession()
        } catch (_: Exception) {
            null
        }

        if (savedSession == null) {
            _authState.value = AuthState.SignedOut
            return
        }

        val user = try {
            repository.findUserById(savedSession.localUserId)
        } catch (_: Exception) {
            null
        }

        // La sesión solo se restaura si el usuario local corresponde al usuario
        // autenticado en Supabase. Así no se mezclan perfiles ni roles.
        if (
            user == null ||
            user.remoteAuthId.isNullOrBlank() ||
            user.remoteAuthId != savedSession.remoteSession.userId
        ) {
            sessionManager.clearSession()
            _authState.value = AuthState.SignedOut
            return
        }

        sessionManager.restoreInMemory(
            user = user,
            remote = savedSession.remoteSession
        )

        // Si el token está próximo a vencer, intenta renovarlo. Si el dispositivo
        // está sin conexión, conserva la sesión local para no expulsar al usuario.
        try {
            repository.prepareRestoredSession(savedSession.remoteSession)
        } catch (_: Exception) {
            // La renovación se volverá a intentar cuando se realice una petición.
        }

        _authState.value = AuthState.SignedIn(user)
    }

    fun registerClient(
        username: String,
        password: String,
        displayName: String,
        phone: String?
    ) {
        viewModelScope.launch {
            _message.value = null

            when (
                val result = repository.registerClient(
                    username,
                    password,
                    displayName,
                    phone
                )
            ) {
                is UserAuthResult.Success -> {
                    signIn(result.user, result.remoteSession)
                }

                is UserAuthResult.Error -> {
                    _message.value = result.message
                }
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _message.value = null

            when (val result = repository.login(username, password)) {
                is UserAuthResult.Success -> {
                    signIn(result.user, result.remoteSession)
                }

                is UserAuthResult.Error -> {
                    _message.value = result.message
                }
            }
        }
    }

    private suspend fun signIn(
        user: UserEntity,
        remoteSession: RemoteSession
    ) {
        try {
            sessionManager.startSession(user, remoteSession)
            _authState.value = AuthState.SignedIn(user)
            _message.value = null
        } catch (e: Exception) {
            _message.value = e.message
                ?: "No fue posible guardar la sesión en este dispositivo."
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            sessionManager.clearSession()

            _message.value = null
            _authState.value = AuthState.SignedOut
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
