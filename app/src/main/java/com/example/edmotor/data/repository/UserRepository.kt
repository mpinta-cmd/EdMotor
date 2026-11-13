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
import com.example.edmotor.data.remote.SupabaseClient
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.RemoteSession
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.Flow

sealed class UserAuthResult {
    data class Success(
        val user: UserEntity,
        val remoteSession: RemoteSession
    ) : UserAuthResult()

    data class Error(val message: String) : UserAuthResult()
}

class UserRepository(
    private val database: EdMotorDatabase,
    private val userDao: UserDao,
    private val cartDao: CartDao,
    private val favoriteDao: FavoriteProductDao,
    private val supabase: SupabaseClient
) {
    companion object {
        private const val DEFAULT_WORKSHOP_USERNAME = "edmotor"
        private const val DEFAULT_WORKSHOP_PASSWORD = "tecnologica"
        private const val DEFAULT_WORKSHOP_NAME = "EdMotor"
    }

    fun observeClients(): Flow<List<UserEntity>> = userDao.observeClients()

    /**
     * Crea la cuenta local inicial. La cuenta remota del taller se crea una sola
     * vez desde Supabase Dashboard y se marca como WORKSHOP con el SQL incluido.
     */
    suspend fun ensureDefaultWorkshopAccount(): String? {
        return try {
            val existing = userDao.findByUsername(DEFAULT_WORKSHOP_USERNAME)
            if (existing != null) {
                return if (existing.role == UserRole.WORKSHOP.name) null
                else "El usuario EdMotor ya existe con un rol diferente al del taller."
            }

            val (hash, salt) = PasswordHasher.create(DEFAULT_WORKSHOP_PASSWORD)
            val workshop = UserEntity(
                username = DEFAULT_WORKSHOP_USERNAME,
                displayName = DEFAULT_WORKSHOP_NAME,
                phone = null,
                passwordHash = hash,
                salt = salt,
                role = UserRole.WORKSHOP.name
            )

            database.withTransaction {
                if (userDao.findByUsername(DEFAULT_WORKSHOP_USERNAME) == null) {
                    val id = userDao.insert(workshop).toInt()
                    cartDao.assignLegacyItems(legacyUserId = 0, newUserId = id)
                    favoriteDao.assignLegacyFavorites(legacyUserId = 0, newUserId = id)
                }
            }
            null
        } catch (_: SQLiteConstraintException) {
            val existing = userDao.findByUsername(DEFAULT_WORKSHOP_USERNAME)
            if (existing?.role == UserRole.WORKSHOP.name) null
            else "No fue posible inicializar la cuenta EdMotor."
        } catch (e: Exception) {
            e.message ?: "Error al inicializar la cuenta EdMotor."
        }
    }

    suspend fun registerClient(
        username: String,
        password: String,
        displayName: String,
        phone: String?
    ): UserAuthResult {
        val normalized = username.trim().lowercase(Locale.ROOT)
        val cleanName = displayName.trim()

        validateCredentials(normalized, password, cleanName)?.let {
            return UserAuthResult.Error(it)
        }

        if (userDao.usernameExists(normalized) > 0) {
            return UserAuthResult.Error("Ese nombre de usuario ya está registrado.")
        }

        return try {
            val remoteSession = supabase.signUpClient(
                username = normalized,
                password = password,
                displayName = cleanName,
                phone = phone?.trim()?.takeIf { it.isNotBlank() }
            )
            val profile = supabase.fetchMyProfile(remoteSession)

            if (profile.role != UserRole.CLIENT.name) {
                return UserAuthResult.Error("La cuenta remota no tiene permisos de cliente.")
            }

            val (hash, salt) = PasswordHasher.create(password)
            val user = UserEntity(
                username = profile.username,
                displayName = profile.displayName,
                phone = profile.phone,
                passwordHash = hash,
                salt = salt,
                role = profile.role,
                remoteAuthId = profile.id
            )
            val id = userDao.insert(user).toInt()
            supabase.startRealtime(remoteSession.accessToken)

            UserAuthResult.Success(user.copy(id = id), remoteSession)
        } catch (e: SQLiteConstraintException) {
            UserAuthResult.Error("Ese nombre de usuario ya está registrado en este dispositivo.")
        } catch (e: Exception) {
            UserAuthResult.Error(
                e.message ?: "No fue posible crear la cuenta remota. Revisa la configuración de Supabase."
            )
        }
    }

    suspend fun login(username: String, password: String): UserAuthResult {
        val normalized = username.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank() || password.isEmpty()) {
            return UserAuthResult.Error("Ingresa tu usuario y contraseña.")
        }

        val localUser = userDao.findByUsername(normalized)

        val remoteSession = try {
            supabase.signIn(normalized, password)
        } catch (signInError: Exception) {
            // Migración transparente de clientes que ya existían en Room antes
            // de activar Supabase. El dueño se crea manualmente en Dashboard.
            val canMigrateLocalClient =
                localUser != null &&
                    localUser.role == UserRole.CLIENT.name &&
                    PasswordHasher.verify(password, localUser.passwordHash, localUser.salt)

            if (!canMigrateLocalClient) {
                return UserAuthResult.Error(
                    signInError.message ?: "Usuario o contraseña incorrectos."
                )
            }

            try {
                supabase.signUpClient(
                    username = normalized,
                    password = password,
                    displayName = localUser!!.displayName,
                    phone = localUser.phone
                )
            } catch (migrationError: Exception) {
                return UserAuthResult.Error(
                    "No se pudo vincular tu cuenta anterior con Supabase: " +
                        (migrationError.message ?: "verifica tu conexión.")
                )
            }
        }

        return try {
            val profile = supabase.fetchMyProfile(remoteSession)

            if (profile.username != normalized) {
                return UserAuthResult.Error("El usuario remoto no coincide con la cuenta solicitada.")
            }

            if (
                localUser != null &&
                localUser.role != profile.role
            ) {
                return UserAuthResult.Error(
                    "El rol de esta cuenta no coincide con el perfil autorizado en Supabase."
                )
            }

            val user = if (localUser != null) {
                val updated = localUser.copy(
                    displayName = profile.displayName,
                    phone = profile.phone,
                    role = profile.role,
                    remoteAuthId = profile.id
                )
                userDao.update(updated)
                updated
            } else {
                val (hash, salt) = PasswordHasher.create(password)
                val remoteUser = UserEntity(
                    username = profile.username,
                    displayName = profile.displayName,
                    phone = profile.phone,
                    passwordHash = hash,
                    salt = salt,
                    role = profile.role,
                    remoteAuthId = profile.id
                )
                val id = userDao.insert(remoteUser).toInt()
                remoteUser.copy(id = id)
            }

            supabase.startRealtime(remoteSession.accessToken)
            UserAuthResult.Success(user, remoteSession)
        } catch (e: Exception) {
            UserAuthResult.Error(
                e.message ?: "No fue posible recuperar tu perfil remoto."
            )
        }
    }

    fun logout() {
        supabase.stopRealtime()
    }

    private fun validateCredentials(
        username: String,
        password: String,
        displayName: String
    ): String? {
        if (!username.matches(Regex("^[a-z0-9_]{3,20}$"))) {
            return "El usuario debe tener entre 3 y 20 caracteres: letras, números o guion bajo."
        }
        if (displayName.length < 2) return "Escribe tu nombre."
        if (password.length < 6) return "La contraseña debe tener al menos 6 caracteres."
        return null
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
