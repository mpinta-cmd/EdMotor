package com.example.edmotor.online

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class OnlineSession(context: Context, private val project: String) {
    private val storage = AtomicFile(File(context.noBackupFilesDir, "online-session-$project"))
    private val keyAlias = "edmotor.online.session.$project"

    fun load(): String? {
        val encrypted = try {
            storage.openRead().use { it.readWithin(MAX_TOKEN_BYTES + IV_SIZE + 17) }
        } catch (error: FileNotFoundException) {
            if (storage.baseFile.exists()) throw error
            return null
        }
        val key = keyStore().getKey(keyAlias, null) as? SecretKey
        if (encrypted.size <= IV_SIZE + 17 || encrypted[0] != VERSION || key == null) {
            clear()
            return null
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted, 1, IV_SIZE))
        cipher.updateAAD(project.toByteArray(Charsets.UTF_8))
        val token = try {
            cipher.doFinal(encrypted, 1 + IV_SIZE, encrypted.size - 1 - IV_SIZE).toString(Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            clear()
            return null
        }
        return token.takeIf(::validOnlineToken) ?: run { clear(); null }
    }

    fun save(token: String) {
        require(validOnlineToken(token))
        val key = keyStore().getKey(keyAlias, null) as? SecretKey ?: createKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(project.toByteArray(Charsets.UTF_8))
        check(cipher.iv.size == IV_SIZE)
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val output = storage.startWrite()
        try {
            output.write(byteArrayOf(VERSION))
            output.write(cipher.iv)
            output.write(ciphertext)
            storage.finishWrite(output)
        } catch (error: Exception) {
            storage.failWrite(output)
            throw error
        }
    }

    fun clear() {
        storage.delete()
        if (storage.baseFile.exists()) {
            val keys = keyStore()
            keys.deleteEntry(keyAlias)
            check(!keys.containsAlias(keyAlias)) { "No se pudo vaciar la sesión local." }
        }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        .apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val VERSION: Byte = 1
    }
}

private const val MAX_TOKEN_BYTES = 4096

internal fun validOnlineToken(token: String): Boolean = token.length in 1..MAX_TOKEN_BYTES && token.all {
    it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-._~+/="
}
