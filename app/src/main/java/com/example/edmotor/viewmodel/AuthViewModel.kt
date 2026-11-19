package com.example.edmotor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.model.DemoProfile
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val user: UserEntity) : AuthState
}

data class ProfileSelectionState(
    val profiles: List<DemoProfile>,
    val mockApiConfigured: Boolean,
    val isLoading: Boolean = false,
    val selectingKey: String? = null,
    val message: String? = null
)

class AuthViewModel(
    private val repository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _profileSelection = MutableStateFlow(
        ProfileSelectionState(repository.localProfiles, repository.mockApiConfigured)
    )
    val profileSelection: StateFlow<ProfileSelectionState> = _profileSelection.asStateFlow()

    init {
        reloadProfiles()
    }

    fun reloadProfiles() {
        val state = _profileSelection.value
        if (!state.mockApiConfigured || state.isLoading) return

        _profileSelection.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            try {
                val remoteProfiles = repository.loadRemoteProfiles()
                _profileSelection.update {
                    it.copy(
                        profiles = repository.localProfiles + remoteProfiles,
                        message = if (remoteProfiles.isEmpty()) {
                            "MockAPI no tiene perfiles de demostración disponibles."
                        } else {
                            null
                        }
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _profileSelection.update {
                    it.copy(profiles = repository.localProfiles, message = "No se pudieron consultar los perfiles de MockAPI.")
                }
            } finally {
                _profileSelection.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectProfile(key: String) {
        val state = _profileSelection.value
        if (state.selectingKey != null || _authState.value != AuthState.SignedOut) return
        val profile = state.profiles.firstOrNull { it.key == key } ?: return

        _profileSelection.update { it.copy(selectingKey = profile.key, message = null) }
        viewModelScope.launch {
            try {
                val user = repository.saveSelectedProfile(profile)
                sessionManager.selectProfile(user)
                _authState.value = AuthState.SignedIn(user)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _profileSelection.update { it.copy(message = "No se pudo abrir el perfil en la base local.") }
            } finally {
                _profileSelection.update { it.copy(selectingKey = null) }
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
        _authState.value = AuthState.SignedOut
    }
}
