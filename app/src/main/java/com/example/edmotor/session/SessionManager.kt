package com.example.edmotor.session

import com.example.edmotor.data.local.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Mantiene la selección de perfil solo mientras vive la aplicación. */
class SessionManager {
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    fun selectProfile(user: UserEntity) {
        _currentUser.value = user
    }

    fun clearSession() {
        _currentUser.value = null
    }
}
