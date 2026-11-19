package com.example.edmotor.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.edmotor.data.remote.PrivateCredentials
import com.example.edmotor.data.remote.isCanonicalPrivateUuid
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

private val Context.edMotorPrivateSessionDataStore by preferencesDataStore(name = "edmotor_private_session")

internal class SavedPrivateSession(val projectKey: String, val credentials: PrivateCredentials)

class PrivateSessionStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: PrivateSessionCipher
) {
    constructor(context: Context) : this(
        context.applicationContext.edMotorPrivateSessionDataStore,
        PrivateSessionCipher(::getOrCreatePrivateSessionKey)
    )

    private val gson = Gson()

    internal suspend fun load(): SavedPrivateSession? {
        val encoded = dataStore.data.first()[ENCRYPTED_SESSION] ?: return null
        val record = withContext(Dispatchers.IO) {
            runCatching {
                gson.fromJson(cipher.decrypt(encoded).toString(Charsets.UTF_8), StoredRecord::class.java)
                    ?.toSession()
            }.getOrNull()
        }
        if (record == null) clear()
        return record
    }

    internal suspend fun save(session: SavedPrivateSession) {
        val credentials = session.credentials
        val encrypted = withContext(Dispatchers.IO) {
            val record = StoredRecord(
                session.projectKey,
                credentials.userId,
                credentials.accessToken,
                credentials.refreshToken,
                credentials.expiresAtMillis
            )
            cipher.encrypt(gson.toJson(record).toByteArray(Charsets.UTF_8))
        }
        dataStore.edit { preferences -> preferences[ENCRYPTED_SESSION] = encrypted }
    }

    internal suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(ENCRYPTED_SESSION) }
    }

    private companion object {
        val ENCRYPTED_SESSION = stringPreferencesKey("private_credentials_v1")
    }
}

private class StoredRecord(
    @SerializedName("project") val projectKey: String?,
    @SerializedName("uid") val userId: String?,
    @SerializedName("access") val accessToken: String?,
    @SerializedName("refresh") val refreshToken: String?,
    @SerializedName("expiry") val expiresAtMillis: Long?
) {
    fun toSession(): SavedPrivateSession? {
        val project = projectKey?.takeIf { it.isNotBlank() } ?: return null
        val id = userId?.takeIf(::isCanonicalPrivateUuid) ?: return null
        val access = accessToken?.takeIf { it.isNotBlank() } ?: return null
        val refresh = refreshToken?.takeIf { it.isNotBlank() } ?: return null
        val expiry = expiresAtMillis?.takeIf { it > 0L } ?: return null
        return SavedPrivateSession(project, PrivateCredentials(id, access, refresh, expiry))
    }
}

internal class PrivateSessionCipher(private val secretKey: () -> SecretKey) {
    fun encrypt(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(PURPOSE)
        return (cipher.iv + cipher.doFinal(plaintext)).toByteString().base64()
    }

    fun decrypt(encoded: String): ByteArray {
        val contents = requireNotNull(encoded.decodeBase64()).toByteArray()
        require(contents.size > IV_LENGTH + 16)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, contents, 0, IV_LENGTH))
        cipher.updateAAD(PURPOSE)
        return cipher.doFinal(contents, IV_LENGTH, contents.size - IV_LENGTH)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        val PURPOSE = "EdMotor private session v1".toByteArray(Charsets.UTF_8)
    }
}

private fun getOrCreatePrivateSessionKey(): SecretKey {
    val provider = "AndroidKeyStore"
    val alias = "edmotor_private_session_aes_v1"
    val keyStore = KeyStore.getInstance(provider).apply { load(null) }
    (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)
    val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build()
    generator.init(spec)
    return generator.generateKey()
}
