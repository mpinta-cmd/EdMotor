package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.UserDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.LocalAccountIdentity
import com.example.edmotor.session.LocalCredentialValidation
import com.example.edmotor.session.LocalPasswordHasher
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrivateAccessException(message: String) : Exception(message)

class PrivateAuthRepository(
    private val database: EdMotorDatabase,
    private val userDao: UserDao,
    private val sessionManager: SessionManager
) {
    suspend fun isOwnerConfigured(): Boolean = userDao.countWorkshopAccounts() != 0 ||
        userDao.findByRemoteAuthId(LocalAccountIdentity.OWNER_KEY) != null

    suspend fun restore(): UserEntity? = sessionManager.restoreProfile { key ->
        if (!LocalAccountIdentity.isKey(key)) return@restoreProfile null
        userDao.findByRemoteAuthId(key)?.takeIf(::isLocalAccount)?.withoutCredentials()
    }

    suspend fun signIn(username: String, password: String): UserEntity {
        LocalCredentialValidation.loginError(username, password)?.let { throw PrivateAccessException(it) }
        val canonicalName = LocalCredentialValidation.normalizeUsername(username)
        val account = userDao.findByUsername(canonicalName)?.takeIf(::isLocalAccount)
        val verified = withContext(Dispatchers.Default) {
            LocalPasswordHasher.verify(password, account?.passwordHash, account?.salt)
        }
        return account?.takeIf { verified }
            ?: throw PrivateAccessException("El usuario o la contraseña no son válidos.")
    }

    suspend fun registerClient(username: String, displayName: String, password: String): UserEntity =
        createAccount(username, displayName, password, UserRole.CLIENT)

    suspend fun createOwner(username: String, password: String): UserEntity =
        createAccount(username, "EdMotor", password, UserRole.WORKSHOP)

    suspend fun rememberVerifiedProfile(user: UserEntity): UserEntity {
        require(isLocalAccount(user))
        val profile = user.withoutCredentials()
        sessionManager.selectProfile(profile)
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
    ): UserEntity {
        LocalCredentialValidation.registrationError(username, displayName, password)?.let {
            throw PrivateAccessException(it)
        }
        val normalized = LocalCredentialValidation.normalizeUsername(username)
        val isOwner = role == UserRole.WORKSHOP
        val key = if (isOwner) LocalAccountIdentity.OWNER_KEY else LocalAccountIdentity.createClientKey()
        val passwordRecord = withContext(Dispatchers.Default) { LocalPasswordHasher.create(password) }
        return database.withTransaction {
            if (isOwner && isOwnerConfigured()) {
                throw PrivateAccessException("El propietario ya está configurado en esta instalación.")
            }
            if (userDao.findByUsername(normalized) != null) {
                throw PrivateAccessException("No se pudo crear la cuenta con ese usuario. Elige otro nombre de usuario.")
            }
            val row = UserEntity(
                username = normalized,
                displayName = displayName.trim(),
                passwordHash = passwordRecord.hash,
                salt = passwordRecord.salt,
                role = role.name,
                remoteAuthId = key
            )
            row.copy(id = Math.toIntExact(userDao.insert(row)))
        }
    }

    private fun isLocalAccount(user: UserEntity): Boolean = user.id > 0 &&
        LocalAccountIdentity.matchesRole(user.remoteAuthId, user.role) &&
        LocalCredentialValidation.isStoredUsername(user.username) &&
        LocalPasswordHasher.isRecognized(user.passwordHash, user.salt)

    private fun UserEntity.withoutCredentials(): UserEntity = copy(passwordHash = "", salt = "")
}
