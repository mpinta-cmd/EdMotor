package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.UserDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.remote.MockApiConnection
import com.example.edmotor.data.remote.MockProfileCreateDto
import com.example.edmotor.data.remote.MockProfileDto
import com.example.edmotor.model.DemoProfile
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.DemoOwnerProfile
import com.example.edmotor.session.LocalAccountIdentity
import com.example.edmotor.session.LocalCredentialValidation
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ClientProfileCreationUnverifiedException(cause: Exception) : Exception(cause)

internal const val DEMO_LOCAL_OWNER_KEY = "local-demo:workshop"
private val STORED_MOCK_API_PROFILE_KEY = Regex("mockapi:[0-9a-f]{24}:[A-Za-z0-9_-]{1,64}")

internal fun isStoredDemoMockApiProfileKey(key: String?): Boolean =
    key != null && STORED_MOCK_API_PROFILE_KEY.matches(key)

class UserRepository(
    private val database: EdMotorDatabase,
    private val userDao: UserDao,
    private val mockApi: MockApiConnection?,
    private val privateAccounts: Boolean = false
) {
    fun observeClients(): Flow<List<UserEntity>> = userDao.observeClients().map { users ->
        if (privateAccounts) {
            users.filter { LocalAccountIdentity.isClientKey(it.remoteAuthId) }
        } else {
            users.filter { user ->
                isDemoProfileIdentity(user) || isDemoCredentialProfile(user)
            }
        }.map { it.copy(passwordHash = "", salt = "") }
    }

    internal val demoProjectKey: String? get() = mockApi?.projectKey

    internal suspend fun provisionClientProfile(alias: String, recovering: Boolean): DemoProfile {
        require(!privateAccounts && alias.startsWith(DEMO_ALIAS_PREFIX) &&
            isCanonicalUuid(alias.removePrefix(DEMO_ALIAS_PREFIX))) {
            "El perfil de demostración necesita un alias anónimo."
        }
        val connection = mockApi
        if (connection == null) {
            return DemoProfile("$REGISTERED_CLIENT_PREFIX${alias.removePrefix(DEMO_ALIAS_PREFIX)}", alias, UserRole.CLIENT)
        }

        return try {
            enrollDemoClient(
                alias = alias,
                recovering = recovering,
                listProfiles = { connection.service.getProfiles() },
                createProfile = { connection.service.createProfile(MockProfileCreateDto(displayName = alias)) },
                mapProfile = { it.toDemoProfile(connection) }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw ClientProfileCreationUnverifiedException(error)
        }
    }

    suspend fun selectOwner(): UserEntity {
        require(!privateAccounts)
        userDao.findByUsername(DemoOwnerProfile.USERNAME)
            ?.takeIf { it.role == UserRole.WORKSHOP.name && isDemoCredentialProfile(it) }
            ?.let { return it }

        val connection = mockApi
        if (connection != null) {
            val cachedOwner = userDao.findWorkshopsByDisplayName(DemoOwnerProfile.DISPLAY_NAME)
                .filter { connection.profileId(it.remoteAuthId) != null }
                .singleOrNull()
            if (cachedOwner != null) return cachedOwner
        }
        userDao.findByRemoteAuthId(DEMO_LOCAL_OWNER_KEY)?.takeIf(::isDemoProfileIdentity)?.let { return it }
        if (connection == null) return storeProfile(LOCAL_OWNER)

        val profiles = try {
            fetchRemoteProfiles(connection)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val owner = profiles?.singleOrNull { it.role == UserRole.WORKSHOP && it.displayName.isOwnerName() }
            ?: return storeProfile(LOCAL_OWNER)
        val stored = userDao.findByRemoteAuthId(owner.key)
        return if (stored == null || stored.role == UserRole.WORKSHOP.name) {
            storeProfile(owner)
        } else {
            storeProfile(LOCAL_OWNER)
        }
    }

    fun isDemoProfileIdentity(user: UserEntity): Boolean =
        matchesDemoProfileIdentity(user, mockApi?.profileId(user.remoteAuthId) != null)

    private fun matchesDemoProfileIdentity(user: UserEntity, remoteProfile: Boolean): Boolean {
        if (privateAccounts || user.id <= 0) return false
        val key = user.remoteAuthId
        return when (user.role) {
            UserRole.CLIENT.name -> isLocalClientKey(key) || remoteProfile
            UserRole.WORKSHOP.name -> user.displayName.isOwnerName() && (key == DEMO_LOCAL_OWNER_KEY || remoteProfile)
            else -> false
        }
    }

    fun isDemoCredentialProfile(user: UserEntity): Boolean =
        matchesDemoProfileIdentity(user, isStoredDemoMockApiProfileKey(user.remoteAuthId)) &&
            LocalCredentialValidation.isStoredUsername(user.username) &&
            (user.username == DemoOwnerProfile.USERNAME) == (user.role == UserRole.WORKSHOP.name) &&
            user.passwordHash.isNotBlank() && user.salt.isNotBlank()

    private suspend fun storeProfile(profile: DemoProfile): UserEntity =
        database.withTransaction {
            val existing = userDao.findByRemoteAuthId(profile.key)
            if (existing != null) {
                check(existing.role == profile.role.name) {
                    "El perfil guardado no coincide con esta selección."
                }
                val updated = existing.copy(displayName = profile.displayName)
                if (updated != existing) userDao.update(updated)
                updated
            } else {
                val user = UserEntity(
                    username = profile.key,
                    displayName = profile.displayName,
                    passwordHash = "",
                    salt = "",
                    role = profile.role.name,
                    remoteAuthId = profile.key
                )
                user.copy(id = Math.toIntExact(userDao.insert(user)))
            }
        }

    private suspend fun fetchRemoteProfiles(connection: MockApiConnection): List<DemoProfile> =
        connection.service.getProfiles()
            .mapNotNull { it.toDemoProfile(connection) }
            .groupBy { it.key }
            .values
            .mapNotNull { matches -> matches.distinct().singleOrNull() }

    private fun MockProfileDto?.toDemoProfile(connection: MockApiConnection): DemoProfile? {
        val dto = this ?: return null
        val remoteId = dto.id.orEmpty().takeIf { it.isNotBlank() } ?: return null
        val name = dto.displayName.nonBlank() ?: return null
        val roleName = dto.role.nonBlank() ?: return null
        val profileRole = UserRole.entries.firstOrNull { it.name.equals(roleName, ignoreCase = true) }
            ?: return null
        val key = runCatching { connection.profileKey(remoteId) }.getOrNull() ?: return null
        return DemoProfile(key, name, profileRole, remoteId)
    }

    private fun isLocalClientKey(key: String?): Boolean {
        if (key == LEGACY_CLIENT_KEY) return true
        if (key == null || !key.startsWith(REGISTERED_CLIENT_PREFIX)) return false
        return isCanonicalUuid(key.removePrefix(REGISTERED_CLIENT_PREFIX))
    }

    private fun isCanonicalUuid(id: String): Boolean =
        runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.isOwnerName(): Boolean = trim().equals(DemoOwnerProfile.DISPLAY_NAME, ignoreCase = true)

    private companion object {
        const val LEGACY_CLIENT_KEY = "local-demo:client"
        const val REGISTERED_CLIENT_PREFIX = "local-demo:client:"
        const val DEMO_ALIAS_PREFIX = "Cliente "
        val LOCAL_OWNER = DemoProfile(DEMO_LOCAL_OWNER_KEY, DemoOwnerProfile.DISPLAY_NAME, UserRole.WORKSHOP)
    }
}
