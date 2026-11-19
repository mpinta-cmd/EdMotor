package com.example.edmotor.session

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

internal data class LocalPasswordRecord(val hash: String, val salt: String)

internal object LocalPasswordHasher {
    private enum class Algorithm(val label: String, val factory: String, val iterations: Int) {
        SHA256("pbkdf2-sha256-v1", "PBKDF2WithHmacSHA256", 600_000),
        SHA1("pbkdf2-sha1-v1", "PBKDF2WithHmacSHA1", 1_300_000)
    }

    private data class ParsedRecord(val algorithm: Algorithm, val hash: ByteArray, val salt: ByteArray)

    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32
    private val random = SecureRandom()
    private val dummySalt = ByteArray(SALT_BYTES)
    private val dummyHash = ByteArray(HASH_BYTES)
    private val preferred by lazy {
        try {
            SecretKeyFactory.getInstance(Algorithm.SHA256.factory)
            Algorithm.SHA256
        } catch (_: NoSuchAlgorithmException) {
            // Compatibilidad con proveedores criptográficos de Android API 24 y 25.
            Algorithm.SHA1
        }
    }

    fun create(password: String): LocalPasswordRecord {
        val algorithm = preferred
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val result = derive(password, salt, algorithm)
        val encoded = result.toByteString().base64()
        result.fill(0)
        return LocalPasswordRecord("${algorithm.label}:${algorithm.iterations}:$encoded", salt.toByteString().base64())
    }

    fun isRecognized(hash: String, salt: String): Boolean = parse(hash, salt) != null

    fun verify(password: String, hash: String?, salt: String?): Boolean {
        val parsed = if (hash == null || salt == null) null else parse(hash, salt)
        val algorithm = parsed?.algorithm ?: preferred
        // Una credencial ausente también ejecuta la derivación para evitar una salida temprana.
        val computed = derive(password, parsed?.salt ?: dummySalt, algorithm)
        val matches = MessageDigest.isEqual(computed, parsed?.hash ?: dummyHash)
        computed.fill(0)
        return parsed != null && matches
    }

    private fun derive(password: String, salt: ByteArray, algorithm: Algorithm): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, algorithm.iterations, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance(algorithm.factory).generateSecret(spec).encoded
        } finally {
            chars.fill('\u0000')
            spec.clearPassword()
        }
    }

    private fun parse(hash: String, salt: String): ParsedRecord? {
        if (hash.length > 90 || salt.length != 24) return null
        val components = hash.split(':')
        if (components.size != 3) return null
        val algorithm = Algorithm.entries.firstOrNull { it.label == components[0] } ?: return null
        if (components[1].toIntOrNull() != algorithm.iterations) return null
        val decodedHash = components[2].decodeBase64()?.toByteArray()?.takeIf { it.size == HASH_BYTES } ?: return null
        val decodedSalt = salt.decodeBase64()?.toByteArray()?.takeIf { it.size == SALT_BYTES } ?: return null
        return ParsedRecord(algorithm, decodedHash, decodedSalt)
    }
}
