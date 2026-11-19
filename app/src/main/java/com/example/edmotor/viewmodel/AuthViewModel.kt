package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.repository.ClientProfileCreationUnverifiedException
import com.example.edmotor.data.repository.PrivateAccessException
import com.example.edmotor.data.repository.PrivateAuthRepository
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.model.DemoProfile
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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

data class ProfileSelectionState(
    val profiles: List<DemoProfile>,
    val mockApiConfigured: Boolean,
    val isLoading: Boolean = false,
    val selectingKey: String? = null,
    val isCreating: Boolean = false,
    val message: String? = null
)

data class PrivateAccessState(
    val isConfigured: Boolean,
    val isSubmitting: Boolean = false,
    val canRetry: Boolean = false,
    val isError: Boolean = false,
    val message: String? = null,
    val verificationEmail: String? = null
)

class AuthViewModel(
    private val repository: UserRepository,
    private val sessionManager: SessionManager,
    private val privateMode: Boolean = false,
    private val privateAuth: PrivateAuthRepository? = null
) : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _profileSelection = MutableStateFlow(
        ProfileSelectionState(emptyList(), repository.mockApiConfigured)
    )
    val profileSelection: StateFlow<ProfileSelectionState> = _profileSelection.asStateFlow()

    private val _privateAccess = MutableStateFlow(PrivateAccessState(isConfigured = privateAuth?.isConfigured == true))
    val privateAccess: StateFlow<PrivateAccessState> = _privateAccess.asStateFlow()

    private val _logoutError = MutableStateFlow<String?>(null)
    val logoutError: StateFlow<String?> = _logoutError.asStateFlow()

    private var profilesJob: Job? = null
    private var logoutInProgress = false

    init {
        if (privateMode) {
            restorePrivateSession()
            viewModelScope.launch {
                sessionManager.currentUser.collect { user ->
                    if (user == null && _authState.value is AuthState.SignedIn && !logoutInProgress) {
                        _authState.value = AuthState.SignedOut
                        _privateAccess.update {
                            it.copy(message = "La sesión privada venció. Inicia sesión nuevamente.", isError = true)
                        }
                    }
                }
            }
        } else {
            restoreDemoSession()
        }
    }

    private fun restoreDemoSession() {
        viewModelScope.launch {
            val remembered = try {
                sessionManager.restoreProfile(repository::getRestorableProfile)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _profileSelection.update { it.copy(message = "No se pudo recuperar el perfil recordado.") }
                null
            }
            _authState.value = remembered?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut
            if (remembered == null) loadProfiles(clearMessage = false)
        }
    }

    fun retryPrivateRestore() {
        if (!privateMode || _authState.value != AuthState.SignedOut ||
            !_privateAccess.value.canRetry || _privateAccess.value.isSubmitting
        ) return
        restorePrivateSession()
    }

    private fun restorePrivateSession() {
        val credentials = privateAuth
        if (credentials == null || !credentials.isConfigured) {
            _authState.value = AuthState.SignedOut
            return
        }
        _authState.value = AuthState.Loading
        _privateAccess.update { it.copy(canRetry = false, message = null, isError = false) }
        viewModelScope.launch {
            val remembered = try {
                credentials.restore()
            } catch (error: CancellationException) {
                throw error
            } catch (error: PrivateAccessException) {
                _privateAccess.update {
                    it.copy(message = error.message, canRetry = error.canRetryRestore, isError = true)
                }
                null
            } catch (_: Exception) {
                _privateAccess.update {
                    it.copy(
                        message = "No se pudo recuperar la sesión privada. Vuelve a comprobar tu conexión.",
                        canRetry = true,
                        isError = true
                    )
                }
                null
            }
            val active = remembered?.takeIf { it.remoteAuthId == sessionManager.currentUser.value?.remoteAuthId }
            _authState.value = active?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut
        }
    }

    fun signInPrivate(username: String, password: String) {
        submitPrivateAction { auth ->
            val user = auth.signIn(username, password)
            if (user.remoteAuthId != sessionManager.currentUser.value?.remoteAuthId) {
                throw PrivateAccessException("La sesión privada venció. Inicia sesión nuevamente.")
            }
            _privateAccess.update { it.copy(canRetry = false, verificationEmail = null) }
            _authState.value = AuthState.SignedIn(user)
        }
    }

    fun registerPrivateClient(username: String, displayName: String, email: String, password: String) {
        submitPrivateAction { auth ->
            auth.registerClient(username, displayName, email, password)
            _privateAccess.update {
                it.copy(
                    verificationEmail = email.trim(),
                    message = "Revisa tu correo e ingresa el código de 6 dígitos para confirmar tu cuenta.",
                    isError = false
                )
            }
        }
    }

    fun verifyPrivateEmail(email: String, code: String) {
        submitPrivateAction { auth ->
            auth.verifyEmail(email, code)
            _privateAccess.update {
                it.copy(
                    verificationEmail = null,
                    message = "Correo confirmado. Inicia sesión con tu usuario y contraseña.",
                    isError = false
                )
            }
        }
    }

    private fun submitPrivateAction(action: suspend (PrivateAuthRepository) -> Unit) {
        val credentials = privateAuth ?: return
        if (!privateMode || !credentials.isConfigured || logoutInProgress ||
            _authState.value != AuthState.SignedOut || _privateAccess.value.isSubmitting
        ) return
        _privateAccess.update { it.copy(isSubmitting = true, message = null, isError = false) }
        viewModelScope.launch {
            try {
                action(credentials)
            } catch (error: CancellationException) {
                throw error
            } catch (error: PrivateAccessException) {
                _privateAccess.update { it.copy(message = error.message, isError = true) }
            } catch (_: Exception) {
                _privateAccess.update { it.copy(message = "No se pudo completar el acceso privado.", isError = true) }
            } finally {
                _privateAccess.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun clearPrivateMessage() {
        _privateAccess.update {
            if (it.canRetry || !it.isConfigured) it else it.copy(message = null, isError = false)
        }
    }

    fun reloadProfiles() = loadProfiles(clearMessage = true)

    private fun loadProfiles(clearMessage: Boolean) {
        val state = _profileSelection.value
        if (privateMode || _authState.value != AuthState.SignedOut || state.isLoading) return

        _profileSelection.update { it.copy(isLoading = true, message = if (clearMessage) null else it.message) }
        profilesJob = viewModelScope.launch {
            try {
                refreshProfileDirectory()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _profileSelection.update { current ->
                    current.copy(message = current.message ?: "No se pudieron actualizar los perfiles.")
                }
            } finally {
                _profileSelection.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun refreshProfileDirectory() {
        val localProfiles = repository.loadLocalClientProfiles()
        _profileSelection.update { current ->
            current.copy(profiles = localProfiles + current.profiles.filter { it.remoteId != null })
        }
        if (repository.mockApiConfigured) {
            val remoteProfiles = repository.loadRemoteProfiles()
            _profileSelection.update { it.copy(profiles = localProfiles + remoteProfiles) }
        }
    }

    fun selectProfile(key: String) {
        val profile = _profileSelection.value.profiles.firstOrNull { it.key == key } ?: return
        openProfile(key, failureMessage = "No se pudo abrir el perfil en la base local.") {
            repository.saveSelectedProfile(profile)
        }
    }

    fun selectOwner() {
        openProfile("owner", failureMessage = "No se pudo abrir el perfil de EdMotor.") {
            repository.selectOwner()
        }
    }

    fun createClientProfile(name: String) {
        openProfile(creating = true, failureMessage = "No se pudo crear el perfil de cliente.") {
            repository.createClientProfile(name)
        }
    }

    private fun openProfile(
        selectingKey: String? = null,
        creating: Boolean = false,
        failureMessage: String,
        resolveProfile: suspend () -> UserEntity
    ) {
        val state = _profileSelection.value
        if (privateMode || logoutInProgress || _authState.value != AuthState.SignedOut ||
            state.selectingKey != null || state.isCreating
        ) return

        _profileSelection.update { it.copy(selectingKey = selectingKey, isCreating = creating, message = null) }
        viewModelScope.launch {
            var savedProfile = false
            try {
                val user = resolveProfile()
                savedProfile = true
                sessionManager.selectProfile(user)
                profilesJob?.cancel()
                _authState.value = AuthState.SignedIn(user)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (creating && (savedProfile || error is ClientProfileCreationUnverifiedException)) {
                    recoverClientRegistration()
                } else {
                    val message = (error as? IllegalArgumentException)?.message ?: failureMessage
                    _profileSelection.update { it.copy(message = message) }
                }
            } finally {
                _profileSelection.update { it.copy(selectingKey = null, isCreating = false) }
            }
        }
    }

    private suspend fun recoverClientRegistration() {
        profilesJob?.cancelAndJoin()
        _profileSelection.update {
            it.copy(
                isLoading = true,
                message = "El perfil pudo haberse creado. Revisa la lista antes de volver a registrarlo."
            )
        }
        try {
            refreshProfileDirectory()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _profileSelection.update {
                it.copy(message = "El perfil pudo haberse creado. Reintenta la consulta de perfiles antes de registrarlo otra vez.")
            }
        } finally {
            _profileSelection.update { it.copy(isLoading = false) }
        }
    }

    fun logout() {
        if (_authState.value !is AuthState.SignedIn || logoutInProgress) return
        logoutInProgress = true
        _logoutError.value = null
        viewModelScope.launch {
            try {
                if (privateMode) {
                    checkNotNull(privateAuth).logout()
                    _privateAccess.update { PrivateAccessState(isConfigured = it.isConfigured) }
                } else {
                    sessionManager.clearSession()
                }
                _authState.value = AuthState.SignedOut
                if (!privateMode) {
                    profilesJob?.cancelAndJoin()
                    reloadProfiles()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (privateMode && sessionManager.currentUser.value == null) {
                    _authState.value = AuthState.SignedOut
                    _privateAccess.update {
                        it.copy(message = "La sesión privada terminó. Inicia sesión nuevamente.", isError = true)
                    }
                } else {
                    _logoutError.value = "No se pudo cerrar la sesión guardada. Intenta de nuevo."
                }
            } finally {
                logoutInProgress = false
            }
        }
    }

    fun clearLogoutError() {
        _logoutError.value = null
    }

    fun clearProfileMessage() {
        _profileSelection.update { it.copy(message = null) }
    }
}
