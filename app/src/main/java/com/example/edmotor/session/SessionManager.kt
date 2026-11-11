
package com.example.edmotor.session

import com.example.edmotor.data.local.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sesión en memoria.
 * Al cerrar o reiniciar la aplicación se vuelve a solicitar acceso.
 */
class SessionManager {

    private val _currentUser = MutableStateFlow<UserEntity?>(null)

    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    fun startSession(user: UserEntity) {
        _currentUser.value = user
    }

    fun clearSession() {
        _currentUser.value = null
    }
}
