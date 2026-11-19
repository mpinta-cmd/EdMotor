package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.UserDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.remote.MockApiConnection
import com.example.edmotor.data.remote.MockProfileDto
import com.example.edmotor.model.DemoProfile
import com.example.edmotor.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserRepository(
    private val database: EdMotorDatabase,
    private val userDao: UserDao,
    private val mockApi: MockApiConnection?
) {
    val mockApiConfigured: Boolean = mockApi != null

    val localProfiles = listOf(
        DemoProfile(LOCAL_CLIENT_KEY, "Cliente", UserRole.CLIENT),
        DemoProfile("local-demo:workshop", "EdMotor", UserRole.WORKSHOP)
    )

    fun observeClients(): Flow<List<UserEntity>> = userDao.observeClients().map { users ->
        users.filter { user ->
            user.remoteAuthId == LOCAL_CLIENT_KEY || mockApi?.profileId(user.remoteAuthId) != null
        }
    }

    suspend fun loadRemoteProfiles(): List<DemoProfile> {
        val connection = mockApi ?: return emptyList()
        return connection.service.getProfiles()
            .mapNotNull { it.toDemoProfile(connection) }
            .distinctBy { it.key }
    }

    suspend fun saveSelectedProfile(profile: DemoProfile): UserEntity {
        val remoteId = profile.remoteId
        val valid = if (remoteId == null) {
            profile in localProfiles
        } else {
            mockApi?.profileKey(remoteId) == profile.key
        }
        require(valid) { "Selecciona un perfil disponible para continuar." }

        return database.withTransaction {
            val existing = userDao.findByRemoteAuthId(profile.key)
            if (existing != null) {
                val updated = existing.copy(displayName = profile.displayName, role = profile.role.name)
                if (updated != existing) userDao.update(updated)
                updated
            } else {
                val profileRow = UserEntity(
                    username = profile.key,
                    displayName = profile.displayName,
                    passwordHash = "",
                    salt = "",
                    role = profile.role.name,
                    remoteAuthId = profile.key
                )
                profileRow.copy(id = Math.toIntExact(userDao.insert(profileRow)))
            }
        }
    }

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

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val LOCAL_CLIENT_KEY = "local-demo:client"
    }
}
