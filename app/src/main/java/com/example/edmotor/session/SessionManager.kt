package com.example.edmotor.session

import com.example.edmotor.data.local.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sesión local y credenciales temporales de Supabase.
 *
 * El token permanece en memoria. Al cerrar la aplicación se vuelve a solicitar
 * el acceso, igual que en la implementación local anterior.
 */
class SessionManager {

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    @Volatile
    var remoteSession: RemoteSession? = null
        private set

    fun startSession(user: UserEntity, remote: RemoteSession) {
        remoteSession = remote
        _currentUser.value = user
    }

    fun updateRemoteSession(remote: RemoteSession) {
        remoteSession = remote
    }

    fun clearSession() {
        _currentUser.value = null
        remoteSession = null
    }
}
