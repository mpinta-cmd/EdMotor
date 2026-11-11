
package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.repository.UserAuthResult
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.session.SessionManager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==========================================
// ESTADOS DE AUTENTICACIÓN
// ==========================================

sealed interface AuthState {

    data object Loading : AuthState

    data object SetupRequired : AuthState

    data object SignedOut : AuthState

    data class SignedIn(
        val user: UserEntity
    ) : AuthState
}


// ==========================================
// VIEWMODEL DE AUTENTICACIÓN
// ==========================================

class AuthViewModel(
    private val repository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // ESTADO DEL USUARIO

    private val _authState =
        MutableStateFlow<AuthState>(AuthState.Loading)

    val authState: StateFlow<AuthState> =
        _authState.asStateFlow()


    // MENSAJES DE AUTENTICACIÓN

    private val _message =
        MutableStateFlow<String?>(null)

    val message: StateFlow<String?> =
        _message.asStateFlow()


    // ==========================================
    // INICIALIZACIÓN
    // ==========================================

    init {

        viewModelScope.launch {

            _authState.value =
                if (repository.hasWorkshopAccount()) {

                    AuthState.SignedOut

                } else {

                    AuthState.SetupRequired
                }
        }
    }


    // ==========================================
    // CREAR CUENTA DEL TALLER
    // ==========================================

    fun createWorkshop(
        username: String,
        password: String,
        displayName: String,
        phone: String?
    ) {

        viewModelScope.launch {

            _message.value = null

            when (
                val result = repository.createWorkshop(
                    username,
                    password,
                    displayName,
                    phone
                )
            ) {

                is UserAuthResult.Success -> {

                    signIn(result.user)
                }

                is UserAuthResult.Error -> {

                    _message.value = result.message
                }
            }
        }
    }


    // ==========================================
    // REGISTRAR CLIENTE
    // ==========================================

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

                    signIn(result.user)
                }

                is UserAuthResult.Error -> {

                    _message.value = result.message
                }
            }
        }
    }


    // ==========================================
    // INICIAR SESIÓN
    // ==========================================

    fun login(
        username: String,
        password: String
    ) {

        viewModelScope.launch {

            _message.value = null

            when (
                val result = repository.login(
                    username,
                    password
                )
            ) {

                is UserAuthResult.Success -> {

                    signIn(result.user)
                }

                is UserAuthResult.Error -> {

                    _message.value = result.message
                }
            }
        }
    }


    // ==========================================
    // INICIAR SESIÓN DEL USUARIO
    // ==========================================

    private fun signIn(user: UserEntity) {

        sessionManager.startSession(user)

        _authState.value = AuthState.SignedIn(user)
    }


    // ==========================================
    // CERRAR SESIÓN
    // ==========================================

    fun logout() {

        sessionManager.clearSession()

        _message.value = null

        _authState.value = AuthState.SignedOut
    }


    // ==========================================
    // LIMPIAR MENSAJES
    // ==========================================

    fun clearMessage() {

        _message.value = null
    }
}
