package com.example.edmotor.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.edMotorAccessDataStore by preferencesDataStore(
    name = "edmotor_selected_profile",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

internal data class PendingDemoRegistration(val alias: String, val recovering: Boolean)

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
            try {
                val preferences = dataStore.data.first()
                val keys = if (credentialedDemo) demoKeys else privateKeys
                val key = preferences[keys.profile]
                val role = preferences[keys.role]
                val createdAt = preferences[keys.createdAt]
                val user = key?.takeIf { it.isNotBlank() }?.let { loadProfile(it) }
                val restored = user?.takeIf {
                    it.id > 0 && it.remoteAuthId == key && it.role == role && it.hasKnownRole() &&
                        (createdAt == null || it.createdAtMillis == createdAt)
                }?.withoutPassword()

                if (restored == null && (keys.existsIn(preferences) ||
                        credentialedDemo && privateKeys.existsIn(preferences))) {
                    removeRememberedProfile()
                }
                if (restored != null && createdAt == null) {
                    dataStore.edit { it[keys.createdAt] = restored.createdAtMillis }
                }
                _currentUser.value = restored
                restored
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _currentUser.value = null
                throw error
            }
        }

    suspend fun selectProfile(user: UserEntity, credentialedDemo: Boolean = false) {
        val key = requireNotNull(user.remoteAuthId)
        require(user.id > 0 && key.isNotBlank() && user.hasKnownRole())

        withContext(NonCancellable) {
            sessionMutex.withLock {
                dataStore.edit { preferences ->
                    val keys = if (credentialedDemo) demoKeys else privateKeys
                    preferences.removeAccess(if (credentialedDemo) privateKeys else demoKeys)
                    preferences[keys.profile] = key
                    preferences[keys.role] = user.role
                    preferences[keys.createdAt] = user.createdAtMillis
                }
                _currentUser.value = user.withoutPassword()
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

    internal suspend fun prepareDemoRegistration(projectKey: String, username: String): PendingDemoRegistration {
        val key = demoRegistrationKey(projectKey, username)
        val newAlias = "Cliente ${UUID.randomUUID()}"
        val prepared = dataStore.edit { preferences ->
            if (!preferences.contains(key)) preferences[key] = newAlias
        }
        val alias = checkNotNull(prepared[key])
        return PendingDemoRegistration(alias, alias != newAlias)
    }

    internal suspend fun finishDemoRegistration(projectKey: String, username: String, alias: String) {
        val key = demoRegistrationKey(projectKey, username)
        dataStore.edit { preferences ->
            if (preferences[key] == alias) preferences.remove(key)
        }
    }

    private fun demoRegistrationKey(projectKey: String, username: String): Preferences.Key<String> {
        require(mockProjectKey.matches(projectKey) && LocalCredentialValidation.isStoredUsername(username))
        return stringPreferencesKey("pending_demo_profile:$projectKey:$username")
    }

    private suspend fun removeRememberedProfile() {
        dataStore.edit { preferences ->
            preferences.removeAccess(privateKeys)
            preferences.removeAccess(demoKeys)
        }
    }

    private fun MutablePreferences.removeAccess(keys: AccessKeys) {
        remove(keys.profile)
        remove(keys.role)
        remove(keys.createdAt)
    }

    private fun UserEntity.hasKnownRole(): Boolean = UserRole.entries.any { it.name == role }

    private fun UserEntity.withoutPassword(): UserEntity = copy(passwordHash = "", salt = "")

    private class AccessKeys(profileKey: String, roleKey: String, createdAtKey: String) {
        val profile = stringPreferencesKey(profileKey)
        val role = stringPreferencesKey(roleKey)
        val createdAt = longPreferencesKey(createdAtKey)

        fun existsIn(preferences: Preferences): Boolean =
            preferences.contains(profile) || preferences.contains(role) || preferences.contains(createdAt)
    }

    private companion object {
        val mockProjectKey = Regex("[0-9a-f]{24}")
        val privateKeys = AccessKeys("selected_profile_key", "selected_profile_role", "selected_profile_created_at")
        // Las selecciones antiguas de la demostración no prueban que se ingresó una contraseña.
        val demoKeys = AccessKeys(
            "demo_credential_profile_key", "demo_credential_profile_role", "demo_credential_profile_created_at"
        )
    }
}
