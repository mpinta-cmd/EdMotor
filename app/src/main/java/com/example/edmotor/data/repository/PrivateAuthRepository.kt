package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.UserDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.DemoOwnerProfile
import com.example.edmotor.session.LocalAccountIdentity
import com.example.edmotor.session.LocalCredentialValidation
import com.example.edmotor.session.LocalPasswordHasher
import com.example.edmotor.session.LocalPasswordRecord
import com.example.edmotor.session.SessionManager
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PrivateAccessException(message: String) : Exception(message)

class PrivateAuthRepository(
    private val database: EdMotorDatabase,
    private val userDao: UserDao,
    private val sessionManager: SessionManager,
    private val demoProfiles: UserRepository? = null
) {
    private val credentialMutex = Mutex()

    suspend fun isOwnerConfigured(): Boolean = if (demoProfiles != null) {
        userDao.findByUsername(DemoOwnerProfile.USERNAME)
            ?.let { it.role == UserRole.WORKSHOP.name && isCredentialedAccount(it) } == true
    } else {
        userDao.countWorkshopAccounts() != 0 || userDao.findByRemoteAuthId(LocalAccountIdentity.OWNER_KEY) != null
    }

    suspend fun restore(): UserEntity? = sessionManager.restoreProfile(credentialedDemo = demoProfiles != null) { key ->
        userDao.findByRemoteAuthId(key)?.takeIf(::isCredentialedAccount)?.withoutCredentials()
    }

    suspend fun signIn(username: String, password: String): UserEntity = credentialMutex.withLock {
        LocalCredentialValidation.loginError(username, password)?.let { throw PrivateAccessException(it) }
        val canonicalName = LocalCredentialValidation.normalizeUsername(username)
        val credentialed = userDao.findByUsername(canonicalName)?.takeIf(::isCredentialedAccount)

        val verified = withContext(Dispatchers.Default) {
            LocalPasswordHasher.verify(password, credentialed?.passwordHash, credentialed?.salt)
        }
        credentialed?.takeIf { verified } ?: throw invalidCredentials()
    }

    suspend fun registerClient(username: String, displayName: String, password: String): UserEntity =
        createAccount(username, displayName, password, UserRole.CLIENT)

    suspend fun createOwner(username: String, password: String): UserEntity =
        createAccount(username, "EdMotor", password, UserRole.WORKSHOP)

    suspend fun rememberVerifiedProfile(user: UserEntity): UserEntity {
        require(isCredentialedAccount(user))
        val profile = user.withoutCredentials()
        sessionManager.selectProfile(profile, credentialedDemo = demoProfiles != null)
        return profile
    }

    suspend fun logout() {
        sessionManager.clearSession()
    }

    private suspend fun createAccount(
        username: String,
        displayName: String,
        password: String,
        role: UserRole
    ): UserEntity = credentialMutex.withLock {
        LocalCredentialValidation.registrationError(username, displayName, password)?.let {
            throw PrivateAccessException(it)
        }
        val normalized = LocalCredentialValidation.normalizeUsername(username)
        val isOwner = role == UserRole.WORKSHOP
        val bridge = demoProfiles
        if (isOwner && isOwnerConfigured()) {
            throw PrivateAccessException("El propietario ya está configurado en esta instalación.")
        }
        if (bridge != null) {
            if (isOwner) {
                if (normalized != DemoOwnerProfile.USERNAME) {
                    throw PrivateAccessException("El usuario del dueño de la demostración es EdMotor.")
                }
                val passwordRecord = withContext(Dispatchers.Default) { LocalPasswordHasher.create(password) }
                val owner = bridge.selectOwner()
                return@withLock saveDemoOwnerPassword(bridge, owner, passwordRecord)
            }
            if (normalized == DemoOwnerProfile.USERNAME) {
                throw PrivateAccessException("Ese nombre de usuario está reservado para EdMotor.")
            }
        }
        ensureAvailableUsername(normalized)
        val passwordRecord = withContext(Dispatchers.Default) { LocalPasswordHasher.create(password) }

        val key = if (bridge != null) {
            val provisioned = try {
                bridge.provisionClientProfile("Cliente ${UUID.randomUUID()}")
            } catch (_: ClientProfileCreationUnverifiedException) {
                throw PrivateAccessException("No se pudo confirmar el enlace de demostración. Comprueba tu conexión.")
            }
            if (provisioned.role != UserRole.CLIENT) {
                throw PrivateAccessException("No se pudo verificar el perfil de cliente de demostración.")
            }
            provisioned.key
        } else if (isOwner) {
            LocalAccountIdentity.OWNER_KEY
        } else {
            LocalAccountIdentity.createClientKey()
        }

        database.withTransaction {
            if (isOwner && isOwnerConfigured()) {
                throw PrivateAccessException("El propietario ya está configurado en esta instalación.")
            }
            ensureAvailableUsername(normalized)
            if (bridge != null && userDao.findByRemoteAuthId(key) != null) {
                throw PrivateAccessException("El perfil de demostración ya está vinculado.")
            }
            val row = UserEntity(
                username = normalized,
                displayName = displayName.trim(),
                passwordHash = passwordRecord.hash,
                salt = passwordRecord.salt,
                role = role.name,
                remoteAuthId = key
            )
            val inserted = row.copy(id = Math.toIntExact(userDao.insert(row)))
            if (!isCredentialedAccount(inserted)) throw PrivateAccessException("La identidad de la cuenta no es válida.")
            inserted
        }
    }

    private suspend fun saveDemoOwnerPassword(
        bridge: UserRepository,
        selected: UserEntity,
        passwordRecord: LocalPasswordRecord
    ): UserEntity = database.withTransaction {
        if (isOwnerConfigured()) {
            throw PrivateAccessException("El propietario ya está configurado en esta instalación.")
        }
        val current = userDao.findById(selected.id)
        if (current == null || current.remoteAuthId != selected.remoteAuthId || current.role != UserRole.WORKSHOP.name ||
            !bridge.isDemoProfileIdentity(current) || current.passwordHash.isNotEmpty() || current.salt.isNotEmpty()
        ) {
            throw PrivateAccessException("No se pudo vincular la cuenta de demostración. Sus datos anteriores se conservaron.")
        }
        ensureAvailableUsername(DemoOwnerProfile.USERNAME, current.id)
        val updated = current.copy(
            username = DemoOwnerProfile.USERNAME,
            displayName = DemoOwnerProfile.DISPLAY_NAME,
            passwordHash = passwordRecord.hash,
            salt = passwordRecord.salt
        )
        if (!isCredentialedAccount(updated)) throw PrivateAccessException("El perfil de demostración no es válido.")
        userDao.update(updated)
        updated
    }

    private suspend fun ensureAvailableUsername(username: String, expectedUserId: Int? = null) {
        val existing = userDao.findByUsername(username)
        if (existing != null && existing.id != expectedUserId) {
            throw PrivateAccessException("No se pudo crear la cuenta con ese usuario. Elige otro nombre de usuario.")
        }
    }

    private fun isCredentialedAccount(user: UserEntity): Boolean {
        val validScope = demoProfiles?.isDemoCredentialProfile(user)
            ?: LocalAccountIdentity.matchesRole(user.remoteAuthId, user.role)
        return user.id > 0 && validScope && LocalCredentialValidation.isStoredUsername(user.username) &&
            LocalPasswordHasher.isRecognized(user.passwordHash, user.salt)
    }

    private fun invalidCredentials() = PrivateAccessException("El usuario o la contraseña no son válidos.")

    private fun UserEntity.withoutCredentials(): UserEntity = copy(passwordHash = "", salt = "")
}
