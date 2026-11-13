package com.example.edmotor.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.edmotor.data.local.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.edMotorSessionDataStore by preferencesDataStore(
    name = "edmotor_secure_session"
)

data class PersistedSession(
    val localUserId: Int,
    val remoteSession: RemoteSession
)

/**
 * Mantiene la sesión activa de EdMotor y la conserva entre aperturas.
 *
 * Los tokens de Supabase se cifran con una clave AES-GCM guardada en Android
 * Keystore. En DataStore solo se almacena el texto cifrado.
 */
class SessionManager(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.edMotorSessionDataStore

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    @Volatile
    var remoteSession: RemoteSession? = null
        private set

    /**
     * Guarda la sesión cifrada y publica el usuario activo.
     */
    suspend fun startSession(user: UserEntity, remote: RemoteSession) {
        persistSession(user.id, remote)
        remoteSession = remote
        _currentUser.value = user
    }

    /**
     * Restaura en memoria una sesión que ya fue recuperada de DataStore.
     */
    fun restoreInMemory(user: UserEntity, remote: RemoteSession) {
        remoteSession = remote
        _currentUser.value = user
    }

    /**
     * Actualiza y vuelve a guardar los tokens cuando Supabase los renueva.
     */
    suspend fun updateRemoteSession(remote: RemoteSession) {
        val localUserId = _currentUser.value?.id
            ?: restoreSavedSession()?.localUserId
            ?: return

        persistSession(localUserId, remote)
        remoteSession = remote
    }

    /**
     * Recupera la sesión cifrada guardada en el dispositivo.
     * Si los datos no pueden descifrarse, elimina el registro dañado.
     */
    suspend fun restoreSavedSession(): PersistedSession? {
        val encrypted = dataStore.data.first()[SESSION_KEY] ?: return null

        return try {
            val json = JSONObject(decrypt(encrypted))

            val localUserId = json.getInt("localUserId")
            val remote = RemoteSession(
                userId = json.getString("remoteUserId"),
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                expiresAtMillis = json.getLong("expiresAtMillis")
            )

            if (
                localUserId <= 0 ||
                remote.userId.isBlank() ||
                remote.accessToken.isBlank() ||
                remote.refreshToken.isBlank()
            ) {
                throw IllegalStateException("La sesión guardada está incompleta.")
            }

            PersistedSession(
                localUserId = localUserId,
                remoteSession = remote
            )
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    /**
     * Cierra la sesión y elimina los tokens cifrados del dispositivo.
     */
    suspend fun clearSession() {
        _currentUser.value = null
        remoteSession = null

        dataStore.edit { preferences ->
            preferences.remove(SESSION_KEY)
        }
    }

    private suspend fun persistSession(
        localUserId: Int,
        remote: RemoteSession
    ) {
        val json = JSONObject()
            .put("localUserId", localUserId)
            .put("remoteUserId", remote.userId)
            .put("accessToken", remote.accessToken)
            .put("refreshToken", remote.refreshToken)
            .put("expiresAtMillis", remote.expiresAtMillis)

        val encrypted = encrypt(json.toString())

        dataStore.edit { preferences ->
            preferences[SESSION_KEY] = encrypted
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encryptedBytes = cipher.doFinal(
            value.toByteArray(StandardCharsets.UTF_8)
        )

        val iv = cipher.iv
        val combined = ByteArray(iv.size + encryptedBytes.size)

        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(
            encryptedBytes,
            0,
            combined,
            iv.size,
            encryptedBytes.size
        )

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val combined = Base64.decode(value, Base64.NO_WRAP)

        if (combined.size <= GCM_IV_LENGTH_BYTES) {
            throw IllegalStateException("Los datos de sesión están incompletos.")
        }

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encryptedBytes = combined.copyOfRange(
            GCM_IV_LENGTH_BYTES,
            combined.size
        )

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply {
            load(null)
        }

        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )

        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        val SESSION_KEY = stringPreferencesKey("encrypted_session")

        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "edmotor_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
