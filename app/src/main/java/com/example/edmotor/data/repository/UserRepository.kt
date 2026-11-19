package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.UserDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.remote.MockApiConnection
import com.example.edmotor.data.remote.MockProfileCreateDto
import com.example.edmotor.data.remote.MockProfileDto
import com.example.edmotor.data.remote.SupabaseConnection
import com.example.edmotor.model.DemoProfile
import com.example.edmotor.model.UserRole
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ClientProfileCreationUnverifiedException(cause: Exception) : Exception(cause)

class UserRepository(
    private val database: EdMotorDatabase,
    private val userDao: UserDao,
    private val mockApi: MockApiConnection?,
    private val supabase: SupabaseConnection? = null
) {
    val mockApiConfigured: Boolean = mockApi != null

    fun observeClients(): Flow<List<UserEntity>> = userDao.observeClients().map { users ->
        users.filter { user ->
            if (supabase != null) supabase.profileId(user.remoteAuthId) != null
            else isLocalClientKey(user.remoteAuthId) || mockApi?.profileId(user.remoteAuthId) != null
        }
    }

    suspend fun loadLocalClientProfiles(): List<DemoProfile> = userDao.observeClients().first()
        .mapNotNull { user ->
            val key = user.remoteAuthId ?: return@mapNotNull null
            if (isLocalClientKey(key)) DemoProfile(key, user.displayName, UserRole.CLIENT) else null
        }

    suspend fun loadRemoteProfiles(): List<DemoProfile> {
        val connection = mockApi ?: return emptyList()
        return fetchRemoteProfiles(connection).filter { it.role == UserRole.CLIENT }
    }

    suspend fun createClientProfile(name: String): UserEntity {
        val cleanName = name.trim()
        require(cleanName.length in 2..50 && cleanName.none(Char::isISOControl)) {
            "Escribe un nombre de demostración de entre 2 y 50 caracteres."
        }
        val connection = mockApi
        if (connection == null) {
            val profile = DemoProfile("$REGISTERED_CLIENT_PREFIX${UUID.randomUUID()}", cleanName, UserRole.CLIENT)
            return storeProfile(profile, allowExisting = false)
        }

        return try {
            val created = connection.service.createProfile(MockProfileCreateDto(displayName = cleanName))
                .toDemoProfile(connection)
            check(created != null && created.role == UserRole.CLIENT && created.displayName == cleanName) {
                "MockAPI no devolvió el perfil de cliente esperado."
            }
            storeProfile(created, allowExisting = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw ClientProfileCreationUnverifiedException(error)
        }
    }

    suspend fun saveSelectedProfile(profile: DemoProfile): UserEntity {
        require(profile.role == UserRole.CLIENT) { "Selecciona un perfil de cliente." }
        val remoteId = profile.remoteId
        if (remoteId == null) {
            val user = getRestorableProfile(profile.key)
            require(isLocalClientKey(profile.key) && user != null && user.role == UserRole.CLIENT.name) {
                "Selecciona un perfil local disponible."
            }
            return user
        }

        require(mockApi?.profileId(profile.key) == remoteId) { "Selecciona un perfil de este proyecto de MockAPI." }
        return storeProfile(profile)
    }

    suspend fun selectOwner(): UserEntity {
        val connection = mockApi ?: return storeProfile(LOCAL_OWNER)
        val profiles = try {
            fetchRemoteProfiles(connection)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (profiles == null) {
            val cachedOwner = userDao.findWorkshopsByDisplayName(OWNER_NAME)
                .filter { connection.profileId(it.remoteAuthId) != null }
                .singleOrNull()
            return cachedOwner ?: storeProfile(LOCAL_OWNER)
        }

        val owner = profiles.singleOrNull { it.role == UserRole.WORKSHOP && it.displayName.isOwnerName() }
            ?: return storeProfile(LOCAL_OWNER)
        val stored = userDao.findByRemoteAuthId(owner.key)
        return if (stored == null || stored.role == UserRole.WORKSHOP.name) {
            storeProfile(owner)
        } else {
            storeProfile(LOCAL_OWNER)
        }
    }

    suspend fun getRestorableProfile(key: String): UserEntity? {
        val localClient = isLocalClientKey(key)
        val localOwner = key == LOCAL_OWNER.key
        if (!localClient && !localOwner && mockApi?.profileId(key) == null) return null

        val user = userDao.findByRemoteAuthId(key) ?: return null
        val validRole = when {
            localClient -> user.role == UserRole.CLIENT.name
            localOwner -> user.role == UserRole.WORKSHOP.name
            else -> user.role == UserRole.CLIENT.name ||
                (user.role == UserRole.WORKSHOP.name && user.displayName.isOwnerName())
        }
        return user.takeIf { validRole }
    }

    private suspend fun storeProfile(profile: DemoProfile, allowExisting: Boolean = true): UserEntity =
        database.withTransaction {
            val existing = userDao.findByRemoteAuthId(profile.key)
            if (existing != null) {
                check(allowExisting && existing.role == profile.role.name) {
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
        val id = key.removePrefix(REGISTERED_CLIENT_PREFIX)
        return runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun String.isOwnerName(): Boolean = trim().equals(OWNER_NAME, ignoreCase = true)

    private companion object {
        const val LEGACY_CLIENT_KEY = "local-demo:client"
        const val REGISTERED_CLIENT_PREFIX = "local-demo:client:"
        const val OWNER_NAME = "EdMotor"
        val LOCAL_OWNER = DemoProfile("local-demo:workshop", OWNER_NAME, UserRole.WORKSHOP)
    }
}
