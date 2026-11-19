package com.example.edmotor.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Base64
import androidx.room.withTransaction
import com.example.edmotor.data.local.CartDao
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.FavoriteProductDao
import com.example.edmotor.data.local.UserDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.remote.SupabaseClient
import com.example.edmotor.data.remote.SupabaseException
import com.example.edmotor.data.remote.SupabaseProfile
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.RemoteSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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
    fun observeClients(): Flow<List<UserEntity>> = userDao.observeClients()

    suspend fun findUserById(userId: Int): UserEntity? = userDao.findById(userId)

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

        return authenticate("No fue posible crear la cuenta remota.") {
            require(userDao.findByUsername(normalized) == null) {
                "Ese nombre de usuario ya está registrado en este dispositivo."
            }
            val remote = supabase.signUpClient(
                username = normalized,
                password = password,
                displayName = cleanName,
                phone = phone?.trim()?.takeIf { it.isNotBlank() }
            )
            val profile = supabase.fetchMyProfile(remote)
            require(profile.role == UserRole.CLIENT.name && profile.username == normalized) {
                "La cuenta remota no corresponde al registro solicitado."
            }
            UserAuthResult.Success(saveRemoteProfile(remote, profile), remote)
        }
    }

    suspend fun login(username: String, password: String): UserAuthResult {
        val normalized = username.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank() || password.isEmpty()) {
            return UserAuthResult.Error("Ingresa tu usuario y contraseña.")
        }

        return authenticate("No fue posible iniciar sesión.") {
            val localAccount = userDao.findByUsername(normalized)
            val legacyClient = localAccount?.takeIf {
                it.remoteAuthId == null && it.role == UserRole.CLIENT.name
            }
            val legacyClientId = if (legacyClient != null && withContext(Dispatchers.Default) {
                    verifyLegacyPassword(password, legacyClient.passwordHash, legacyClient.salt)
                }) {
                legacyClient.id
            } else {
                null
            }

            var registeringLegacyClient = false
            val remote = try {
                supabase.signIn(normalized, password)
            } catch (error: SupabaseException) {
                // La migración solo procede cuando la credencial local se comprobó y
                // Supabase respondió que las credenciales remotas no son válidas.
                if (legacyClient == null || legacyClientId == null ||
                    error.errorCode != "invalid_credentials"
                ) {
                    throw error
                }
                registeringLegacyClient = true
                supabase.signUpClient(normalized, password, legacyClient.displayName, legacyClient.phone)
            }
            val profile = supabase.fetchMyProfile(remote)
            require(profile.username == normalized) {
                "El usuario remoto no coincide con la cuenta solicitada."
            }
            if (registeringLegacyClient) {
                require(profile.role == UserRole.CLIENT.name) {
                    "La cuenta migrada debe tener permisos de cliente."
                }
            }
            val user = saveRemoteProfile(remote, profile, legacyClientId, allowLegacyWorkshop = true)
            UserAuthResult.Success(user, remote)
        }
    }

    suspend fun restoreSession(user: UserEntity, saved: RemoteSession): UserAuthResult {
        if (user.remoteAuthId != saved.userId) {
            return UserAuthResult.Error("La sesión guardada no corresponde al perfil local.")
        }

        var remote = saved
        var credentialsRejected = false
        val profile = try {
            remote = supabase.prepareRestoredSession(saved)
            try {
                supabase.fetchMyProfile(remote)
            } catch (error: SupabaseException) {
                if (error.statusCode != 401 || remote !== saved) throw error
                credentialsRejected = true
                remote = supabase.prepareRestoredSession(saved, forceRefresh = true)
                supabase.fetchMyProfile(remote)
            }
        } catch (_: IOException) {
            if (!credentialsRejected) return UserAuthResult.Success(user, remote)
            return UserAuthResult.Error("La sesión ya no es válida. Inicia sesión nuevamente.")
        } catch (error: SupabaseException) {
            val temporaryError = error.statusCode in temporaryHttpErrors || (error.statusCode ?: 0) >= 500
            if (!credentialsRejected && temporaryError) {
                return UserAuthResult.Success(user, remote)
            }
            return UserAuthResult.Error("La sesión ya no es válida. Inicia sesión nuevamente.")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return UserAuthResult.Error("No se pudo comprobar la sesión guardada. Inicia sesión nuevamente.")
        }

        return authenticate("No fue posible actualizar el perfil guardado.") {
            val updatedUser = saveRemoteProfile(remote, profile)
            UserAuthResult.Success(updatedUser, remote)
        }
    }

    suspend fun startRealtime(remote: RemoteSession) {
        supabase.startRealtimeFor(remote)
    }

    fun logout() {
        supabase.stopRealtime()
    }

    private suspend fun saveRemoteProfile(
        remote: RemoteSession,
        profile: SupabaseProfile,
        legacyClientId: Int? = null,
        allowLegacyWorkshop: Boolean = false
    ): UserEntity {
        require(profile.id.isNotBlank() && profile.id == remote.userId) {
            "La identidad del perfil remoto no coincide con la sesión."
        }
        require(profile.role == UserRole.CLIENT.name || profile.role == UserRole.WORKSHOP.name) {
            "La cuenta remota tiene un rol desconocido."
        }

        return database.withTransaction {
            val matchingIdentity = userDao.findByRemoteAuthId(profile.id)
            val matchingName = userDao.findByUsername(profile.username)
            val existing = when {
                matchingName == null || matchingIdentity?.id == matchingName.id -> matchingIdentity
                matchingIdentity != null || matchingName.remoteAuthId != null -> {
                    error("El nombre de usuario pertenece a otro perfil local. Sus datos se conservaron.")
                }
                matchingName.id == legacyClientId && profile.role == UserRole.CLIENT.name -> matchingName
                allowLegacyWorkshop && matchingName.role == UserRole.WORKSHOP.name &&
                    profile.role == UserRole.WORKSHOP.name -> matchingName
                else -> error("La cuenta local anterior requiere su propia contraseña para vincularse.")
            }

            val user = UserEntity(
                id = existing?.id ?: 0,
                username = profile.username,
                displayName = profile.displayName,
                phone = profile.phone,
                passwordHash = "",
                salt = "",
                role = profile.role,
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                remoteAuthId = profile.id
            )
            val saved = if (existing == null) {
                user.copy(id = userDao.insert(user).toInt())
            } else {
                if (user != existing) userDao.update(user)
                user
            }

            if (saved.role == UserRole.WORKSHOP.name) {
                cartDao.assignLegacyItems(legacyUserId = 0, newUserId = saved.id)
                favoriteDao.assignLegacyFavorites(legacyUserId = 0, newUserId = saved.id)
            }
            saved
        }
    }

    private suspend fun authenticate(
        fallbackMessage: String,
        operation: suspend () -> UserAuthResult.Success
    ): UserAuthResult = try {
        operation()
    } catch (error: CancellationException) {
        throw error
    } catch (_: SQLiteConstraintException) {
        UserAuthResult.Error("El perfil entra en conflicto con una cuenta guardada en este dispositivo.")
    } catch (error: Exception) {
        UserAuthResult.Error(error.message ?: fallbackMessage)
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

    private companion object {
        val temporaryHttpErrors = setOf(408, 429)
    }
}

/** Verifica las credenciales de cuentas que todavía no fueron vinculadas a Supabase. */
private fun verifyLegacyPassword(password: String, storedHash: String, storedSalt: String): Boolean {
    val parts = storedHash.split(":")
    if (parts.size != 3) return false
    val algorithm = when (parts[0]) {
        "pbkdf2-sha256" -> "PBKDF2WithHmacSHA256"
        "pbkdf2-sha1" -> "PBKDF2WithHmacSHA1"
        else -> return false
    }
    val iterations = parts[1].toIntOrNull() ?: return false
    if (iterations !in 1..1_000_000) return false

    return try {
        val expected = Base64.decode(parts[2], Base64.NO_WRAP)
        val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        try {
            val actual = SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
            MessageDigest.isEqual(expected, actual)
        } finally {
            spec.clearPassword()
        }
    } catch (_: Exception) {
        false
    }
}
