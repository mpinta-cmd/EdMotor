package com.example.edmotor.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.edMotorAccessDataStore by preferencesDataStore(name = "edmotor_selected_profile")

/** Recuerda la selección. Room sigue siendo la fuente del perfil. */
class SessionManager internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.edMotorAccessDataStore)

    private val sessionMutex = Mutex()
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    suspend fun restoreProfile(
        credentialedDemo: Boolean = false,
        loadProfile: suspend (String) -> UserEntity?
    ): UserEntity? =
        sessionMutex.withLock {
            val preferences = dataStore.data.first()
            val key = preferences[if (credentialedDemo) DEMO_CREDENTIAL_PROFILE_KEY else PROFILE_KEY]
            val role = preferences[if (credentialedDemo) DEMO_CREDENTIAL_PROFILE_ROLE else PROFILE_ROLE]
            val user = key?.takeIf { it.isNotBlank() }?.let { loadProfile(it) }
            val restored = user?.takeIf {
                it.id > 0 && it.remoteAuthId == key && it.role == role && it.hasKnownRole()
            }

            if (restored == null && (key != null || role != null ||
                (credentialedDemo && (preferences[PROFILE_KEY] != null || preferences[PROFILE_ROLE] != null))
            )) removeRememberedProfile()
            _currentUser.value = restored
            restored
        }

    suspend fun selectProfile(user: UserEntity, credentialedDemo: Boolean = false) {
        val key = requireNotNull(user.remoteAuthId)
        require(user.id > 0 && key.isNotBlank() && user.hasKnownRole())

        withContext(NonCancellable) {
            sessionMutex.withLock {
                dataStore.edit { preferences ->
                    if (credentialedDemo) {
                        preferences.remove(PROFILE_KEY)
                        preferences.remove(PROFILE_ROLE)
                        preferences[DEMO_CREDENTIAL_PROFILE_KEY] = key
                        preferences[DEMO_CREDENTIAL_PROFILE_ROLE] = user.role
                    } else {
                        preferences[PROFILE_KEY] = key
                        preferences[PROFILE_ROLE] = user.role
                    }
                }
                _currentUser.value = user
            }
        }
    }

    suspend fun clearSession() {
        withContext(NonCancellable) {
            sessionMutex.withLock {
                removeRememberedProfile()
                _currentUser.value = null
            }
        }
    }

    private suspend fun removeRememberedProfile() {
        dataStore.edit { preferences ->
            preferences.remove(PROFILE_KEY)
            preferences.remove(PROFILE_ROLE)
            preferences.remove(DEMO_CREDENTIAL_PROFILE_KEY)
            preferences.remove(DEMO_CREDENTIAL_PROFILE_ROLE)
        }
    }

    private fun UserEntity.hasKnownRole(): Boolean = UserRole.entries.any { it.name == role }

    private companion object {
        val PROFILE_KEY = stringPreferencesKey("selected_profile_key")
        val PROFILE_ROLE = stringPreferencesKey("selected_profile_role")
        // Las selecciones antiguas de la demostración no prueban que se ingresó una contraseña.
        val DEMO_CREDENTIAL_PROFILE_KEY = stringPreferencesKey("demo_credential_profile_key")
        val DEMO_CREDENTIAL_PROFILE_ROLE = stringPreferencesKey("demo_credential_profile_role")
    }
}
