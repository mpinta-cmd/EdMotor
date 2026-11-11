package com.example.edmotor.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import android.util.Base64
import androidx.room.withTransaction
import com.example.edmotor.data.local.CartDao
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.FavoriteProductDao
import com.example.edmotor.data.local.UserDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class UserAuthResult {
    data class Success(val user: UserEntity) : UserAuthResult()
    data class Error(val message: String) : UserAuthResult()
}

class UserRepository(
    private val database: EdMotorDatabase,
    private val userDao: UserDao,
    private val cartDao: CartDao,
    private val favoriteDao: FavoriteProductDao
) {
    suspend fun hasWorkshopAccount(): Boolean = userDao.countWorkshopAccounts() > 0

    suspend fun createWorkshop(
        username: String,
        password: String,
        displayName: String,
        phone: String?
    ): UserAuthResult {
        if (hasWorkshopAccount()) {
            return UserAuthResult.Error("La cuenta del taller ya está configurada.")
        }
        return createUser(username, password, displayName, phone, UserRole.WORKSHOP)
    }

    suspend fun registerClient(
        username: String,
        password: String,
        displayName: String,
        phone: String?
    ): UserAuthResult = createUser(
        username, password, displayName, phone, UserRole.CLIENT
    )

    private suspend fun createUser(
        username: String,
        password: String,
        displayName: String,
        phone: String?,
        role: UserRole
    ): UserAuthResult {
        val normalizedUsername = username.trim().lowercase(Locale.ROOT)
        val cleanName = displayName.trim()

        if (!normalizedUsername.matches(Regex("^[a-z0-9_]{3,20}$"))) {
            return UserAuthResult.Error("El usuario debe tener entre 3 y 20 caracteres: letras, números o guion bajo.")
        }
        if (cleanName.length < 2) {
            return UserAuthResult.Error("Escribe tu nombre o el nombre del taller.")
        }
        if (password.length < 6) {
            return UserAuthResult.Error("La contraseña debe tener al menos 6 caracteres.")
        }
        if (userDao.usernameExists(normalizedUsername) > 0) {
            return UserAuthResult.Error("Ese nombre de usuario ya está registrado.")
        }

        val (hash, salt) = PasswordHasher.create(password)
        val entity = UserEntity(
            username = normalizedUsername,
            displayName = cleanName,
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
            passwordHash = hash,
            salt = salt,
            role = role.name
        )

        return try {
            val id = database.withTransaction {
                val generatedId = userDao.insert(entity).toInt()
                // Los datos anteriores a la versión multiusuario se conservan
                // dentro de la cuenta inicial del taller.
                if (role == UserRole.WORKSHOP) {
                    cartDao.assignLegacyItems(legacyUserId = 0, newUserId = generatedId)
                    favoriteDao.assignLegacyFavorites(legacyUserId = 0, newUserId = generatedId)
                }
                generatedId
            }
            UserAuthResult.Success(entity.copy(id = id))
        } catch (_: SQLiteConstraintException) {
            UserAuthResult.Error("Ese nombre de usuario ya está registrado.")
        } catch (e: Exception) {
            UserAuthResult.Error(e.message ?: "No fue posible crear la cuenta.")
        }
    }

    suspend fun login(username: String, password: String): UserAuthResult {
        val normalizedUsername = username.trim().lowercase(Locale.ROOT)
        if (normalizedUsername.isBlank() || password.isEmpty()) {
            return UserAuthResult.Error("Ingresa tu usuario y contraseña.")
        }
        val user = userDao.findByUsername(normalizedUsername)
            ?: return UserAuthResult.Error("Usuario o contraseña incorrectos.")
        val valid = PasswordHasher.verify(password, user.passwordHash, user.salt)
        return if (valid) UserAuthResult.Success(user)
        else UserAuthResult.Error("Usuario o contraseña incorrectos.")
    }
}

private object PasswordHasher {
    private const val SHA256_ITERATIONS = 210_000
    private const val SHA1_ITERATIONS = 600_000
    private const val KEY_LENGTH_BITS = 256

    fun create(password: String): Pair<String, String> {
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val useSha256 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val algorithm = if (useSha256) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val label = if (useSha256) "pbkdf2-sha256" else "pbkdf2-sha1"
        val iterations = if (useSha256) SHA256_ITERATIONS else SHA1_ITERATIONS
        val hashBytes = derive(password, saltBytes, algorithm, iterations)
        val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val hash = Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        return "$label:$iterations:$hash" to salt
    }

    fun verify(password: String, storedHash: String, storedSalt: String): Boolean {
        return try {
            val parts = storedHash.split(":")
            if (parts.size != 3) return false
            val algorithm = when (parts[0]) {
                "pbkdf2-sha256" -> "PBKDF2WithHmacSHA256"
                "pbkdf2-sha1" -> "PBKDF2WithHmacSHA1"
                else -> return false
            }
            val iterations = parts[1].toIntOrNull() ?: return false
            if (iterations !in 1..1_000_000) return false
            val expected = Base64.decode(parts[2], Base64.NO_WRAP)
            val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
            val actual = derive(password, salt, algorithm, iterations)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    private fun derive(
        password: String,
        salt: ByteArray,
        algorithm: String,
        iterations: Int
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
