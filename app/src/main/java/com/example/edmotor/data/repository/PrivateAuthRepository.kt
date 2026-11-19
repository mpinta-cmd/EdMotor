package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.UserDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.remote.PrivateAuthHttpException
import com.example.edmotor.data.remote.PrivateCredentialValidation
import com.example.edmotor.data.remote.PrivateCredentials
import com.example.edmotor.data.remote.PrivateSignupConfigurationException
import com.example.edmotor.data.remote.SupabaseConnection
import com.example.edmotor.data.remote.VerifiedPrivateProfile
import com.example.edmotor.session.PrivateSessionStore
import com.example.edmotor.session.SavedPrivateSession
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class PrivateAccessException(message: String, val canRetryRestore: Boolean = false) : Exception(message)

class PrivateAuthRepository(
    private val database: EdMotorDatabase,
    private val userDao: UserDao,
    private val connection: SupabaseConnection?,
    private val secureStore: PrivateSessionStore,
    private val sessionManager: SessionManager
) {
    val isConfigured: Boolean get() = connection != null

    private val sessionMutex = Mutex()
    private var active: SavedPrivateSession? = null
    private var mustVerifyRefreshedRole = false

    suspend fun signIn(username: String, password: String): UserEntity = sessionMutex.withLock {
        val server = configuredConnection()
        PrivateCredentialValidation.loginError(username, password)?.let { throw PrivateAccessException(it) }
        val normalizedUsername = PrivateCredentialValidation.normalizeUsername(username)
        try {
            val credentials = server.signIn(normalizedUsername, password)
            val profile = server.fetchProfile(credentials)
            if (profile.username != normalizedUsername) throw PrivateAuthHttpException(403)
            activate(server, credentials, profile)
        } catch (error: CancellationException) {
            throw error
        } catch (error: PrivateAuthHttpException) {
            throw PrivateAccessException(
                when (error.status) {
                    400, 401, 403, 422 -> "El usuario o la contraseña no son válidos, o el correo aún no está confirmado."
                    429 -> "Demasiados intentos. Espera un momento y vuelve a intentarlo."
                    else -> "No se pudo comprobar el acceso privado. Intenta de nuevo."
                }
            )
        } catch (_: Exception) {
            throw PrivateAccessException("No se pudo abrir la sesión privada. Comprueba tu conexión e inténtalo de nuevo.")
        }
    }

    suspend fun registerClient(username: String, displayName: String, email: String, password: String) {
        val server = configuredConnection()
        PrivateCredentialValidation.registrationError(username, displayName, email, password)?.let {
            throw PrivateAccessException(it)
        }
        try {
            server.registerClient(
                PrivateCredentialValidation.normalizeUsername(username),
                displayName.trim(),
                email.trim(),
                password
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: PrivateSignupConfigurationException) {
            throw PrivateAccessException("El proyecto debe exigir la confirmación por correo antes de permitir registros.")
        } catch (error: PrivateAuthHttpException) {
            val message = if (error.status == 429) {
                "Espera antes de solicitar otro código por correo."
            } else {
                "No se pudo iniciar el registro. Comprueba los datos o busca el código si ya lo solicitaste."
            }
            throw PrivateAccessException(message)
        } catch (_: Exception) {
            throw PrivateAccessException("No se pudo comprobar el registro. Revisa si recibiste un código antes de repetirlo.")
        }
    }

    suspend fun verifyEmail(email: String, code: String) {
        val server = configuredConnection()
        PrivateCredentialValidation.verificationError(email, code)?.let { throw PrivateAccessException(it) }
        try {
            server.verifyEmail(email.trim(), code.trim())
        } catch (error: CancellationException) {
            throw error
        } catch (error: PrivateAuthHttpException) {
            throw PrivateAccessException(
                when (error.status) {
                    400, 401, 403, 422 -> "El código no es válido o venció. Comprueba el correo y el código."
                    429 -> "Demasiados intentos. Espera un momento antes de intentarlo de nuevo."
                    else -> "No se pudo comprobar el código. Intenta de nuevo."
                }
            )
        } catch (_: Exception) {
            throw PrivateAccessException("No se pudo comprobar el código. Revisa tu conexión e inténtalo de nuevo.")
        }
    }

    suspend fun restore(): UserEntity? = sessionMutex.withLock {
        val server = connection ?: return@withLock null
        val stored = try {
            secureStore.load()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw PrivateAccessException("No se pudo leer la sesión guardada. Intenta de nuevo.", canRetryRestore = true)
        } ?: return@withLock null
        if (stored.projectKey != server.projectKey) {
            discardSession()
            return@withLock null
        }

        try {
            val oldCredentials = stored.credentials
            var credentials = if (oldCredentials.expiresSoon()) {
                refreshAndSave(server, oldCredentials)
            } else {
                oldCredentials
            }
            val profile = try {
                server.fetchProfile(credentials)
            } catch (error: PrivateAuthHttpException) {
                if (error.status != 401 || credentials !== oldCredentials) throw error
                credentials = refreshAndSave(server, credentials)
                server.fetchProfile(credentials)
            }
            activate(server, credentials, profile)
        } catch (error: CancellationException) {
            throw error
        } catch (error: PrivateAuthHttpException) {
            if (error.isRejectedSession()) {
                discardSession()
                throw PrivateAccessException("La sesión guardada ya no es válida. Inicia sesión nuevamente.")
            }
            throw connectionRequired()
        } catch (_: Exception) {
            throw connectionRequired()
        }
    }

    suspend fun accessToken(expectedProfileKey: String, rejectedToken: String? = null): String =
        sessionMutex.withLock {
            val server = configuredConnection()
            val session = active ?: throw PrivateAccessException("Inicia sesión para realizar esta operación.")
            var credentials = session.credentials
            val user = sessionManager.currentUser.value
                ?: throw PrivateAccessException("Inicia sesión para realizar esta operación.")
            if (server.profileId(expectedProfileKey) != credentials.userId ||
                user.remoteAuthId != expectedProfileKey
            ) throw PrivateAccessException("La cuenta activa cambió. Vuelve a abrir esta pantalla.")

            try {
                if (credentials.expiresSoon() || rejectedToken == credentials.accessToken) {
                    credentials = refreshAndSave(server, credentials)
                    active = SavedPrivateSession(server.projectKey, credentials)
                    mustVerifyRefreshedRole = true
                }
                if (mustVerifyRefreshedRole) {
                    val profile = server.fetchProfile(credentials)
                    if (profile.role.name != user.role) throw PrivateAuthHttpException(403)
                    mustVerifyRefreshedRole = false
                }
                credentials.accessToken
            } catch (error: PrivateAuthHttpException) {
                if (error.isRejectedSession()) expireSession()
                throw PrivateAccessException("No se pudo renovar la sesión privada. Comprueba tu conexión o vuelve a entrar.")
            }
        }

    suspend fun logout() {
        val server = connection
        val previous = sessionMutex.withLock {
            val session = active
            discardSession()
            session
        }
        if (server != null && previous != null) {
            try {
                withTimeoutOrNull(3_000L) { server.revoke(previous.credentials) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // La sesión del dispositivo ya se eliminó, incluso si se cerró sin conexión.
            }
        }
    }

    private suspend fun activate(
        server: SupabaseConnection,
        credentials: PrivateCredentials,
        profile: VerifiedPrivateProfile
    ): UserEntity {
        if (profile.id != credentials.userId) throw PrivateAuthHttpException(403)
        val key = server.profileKey(profile.id)
        val user = database.withTransaction {
            val prior = userDao.findByRemoteAuthId(key)
            val row = if (prior == null) {
                UserEntity(
                    username = key,
                    displayName = profile.displayName,
                    passwordHash = "",
                    salt = "",
                    role = profile.role.name,
                    remoteAuthId = key
                )
            } else {
                prior.copy(displayName = profile.displayName, role = profile.role.name)
            }
            if (prior == null) row.copy(id = Math.toIntExact(userDao.insert(row)))
            else {
                if (row != prior) userDao.update(row)
                row
            }
        }
        withContext(NonCancellable) {
            val verified = SavedPrivateSession(server.projectKey, credentials)
            secureStore.save(verified)
            active = verified
            mustVerifyRefreshedRole = false
            sessionManager.publishPrivateProfile(user)
        }
        return user
    }

    private suspend fun refreshAndSave(server: SupabaseConnection, current: PrivateCredentials): PrivateCredentials =
        withContext(NonCancellable) {
            val updated = server.refresh(current)
            secureStore.save(SavedPrivateSession(server.projectKey, updated))
            updated
        }

    private suspend fun discardSession() {
        withContext(NonCancellable) {
            secureStore.clear()
            clearActiveProfile()
        }
    }

    private suspend fun expireSession() {
        withContext(NonCancellable) {
            clearActiveProfile()
            secureStore.clear()
        }
    }

    private suspend fun clearActiveProfile() {
        active = null
        mustVerifyRefreshedRole = false
        sessionManager.publishPrivateProfile(null)
    }

    private fun configuredConnection(): SupabaseConnection = connection
        ?: throw PrivateAccessException("Configura el acceso privado de EdMotor para continuar.")

    private fun connectionRequired() = PrivateAccessException(
        "Conéctate a internet para verificar la sesión privada y vuelve a intentarlo.",
        canRetryRestore = true
    )

    private fun PrivateAuthHttpException.isRejectedSession(): Boolean =
        status == 400 || status == 401 || status == 403 || status == 422
}
