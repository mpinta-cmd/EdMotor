package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.repository.PrivateAccessException
import com.example.edmotor.data.repository.PrivateAuthRepository
import com.example.edmotor.model.UserRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: UserEntity) : AuthState
}

data class PrivateAccessState(
    val ownerConfigured: Boolean? = null,
    val isSubmitting: Boolean = false,
    val canRetry: Boolean = false,
    val isError: Boolean = false,
    val message: String? = null
)

class AuthViewModel(private val auth: PrivateAuthRepository) : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _privateAccess = MutableStateFlow(PrivateAccessState())
    val privateAccess: StateFlow<PrivateAccessState> = _privateAccess.asStateFlow()

    private val _logoutError = MutableStateFlow<String?>(null)
    val logoutError: StateFlow<String?> = _logoutError.asStateFlow()

    private var logoutInProgress = false

    init {
        restoreSession()
    }

    fun retryPrivateRestore() {
        if (_authState.value == AuthState.SignedOut && _privateAccess.value.canRetry &&
            !_privateAccess.value.isSubmitting
        ) restoreSession()
    }

    private fun restoreSession() {
        _authState.value = AuthState.Loading
        _privateAccess.update { it.copy(canRetry = false, message = null, isError = false) }
        viewModelScope.launch {
            val remembered = try {
                val ownerConfigured = auth.isOwnerConfigured()
                _privateAccess.update { it.copy(ownerConfigured = ownerConfigured) }
                auth.restore()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _privateAccess.update {
                    it.copy(
                        message = "No se pudo recuperar el acceso local. Intenta de nuevo.",
                        canRetry = true,
                        isError = true
                    )
                }
                null
            }
            _authState.value = remembered?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut
        }
    }

    fun signInPrivate(username: String, password: String) {
        openProfile { auth.signIn(username, password) }
    }

    fun registerPrivateClient(username: String, displayName: String, password: String) {
        openProfile(creating = true) { auth.registerClient(username, displayName, password) }
    }

    fun createPrivateOwner(username: String, password: String) {
        if (_privateAccess.value.ownerConfigured != false) return
        openProfile(creating = true, creatingOwner = true) { auth.createOwner(username, password) }
    }

    private fun openProfile(
        creating: Boolean = false,
        creatingOwner: Boolean = false,
        resolveProfile: suspend () -> UserEntity
    ) {
        if (logoutInProgress || _authState.value != AuthState.SignedOut || _privateAccess.value.isSubmitting) return

        _privateAccess.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            var accountCreated = false
            try {
                val user = resolveProfile()
                accountCreated = creating
                if (user.role == UserRole.WORKSHOP.name) {
                    _privateAccess.update { it.copy(ownerConfigured = true) }
                }
                val profile = auth.rememberVerifiedProfile(user)
                _privateAccess.update { it.copy(canRetry = false) }
                _authState.value = AuthState.SignedIn(profile)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = when {
                    accountCreated -> "La cuenta quedó guardada. Inicia sesión con tu usuario y contraseña."
                    error is PrivateAccessException -> error.message
                    else -> "No se pudo completar el acceso local. Intenta de nuevo."
                }
                if (creatingOwner && !accountCreated) refreshOwnerConfiguration()
                _privateAccess.update { it.copy(message = message, isError = true) }
            } finally {
                _privateAccess.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private suspend fun refreshOwnerConfiguration() {
        try {
            val ownerConfigured = auth.isOwnerConfigured()
            _privateAccess.update { it.copy(ownerConfigured = ownerConfigured) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _privateAccess.update { it.copy(ownerConfigured = null, canRetry = true) }
        }
    }

    fun logout() {
        if (_authState.value !is AuthState.SignedIn || logoutInProgress) return
        logoutInProgress = true
        _logoutError.value = null
        viewModelScope.launch {
            try {
                auth.logout()
                _privateAccess.update { PrivateAccessState(ownerConfigured = it.ownerConfigured) }
                _authState.value = AuthState.SignedOut
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _logoutError.value = "No se pudo cerrar la sesión guardada. Intenta de nuevo."
            } finally {
                logoutInProgress = false
            }
        }
    }

    fun clearLogoutError() {
        _logoutError.value = null
    }

    fun clearPrivateMessage() {
        _privateAccess.update { if (it.canRetry) it else it.copy(message = null, isError = false) }
    }
}
