package com.example.edmotor.data.repository

import com.example.edmotor.data.remote.MockProfileDto
import com.example.edmotor.model.DemoProfile
import com.example.edmotor.model.UserRole
import kotlinx.coroutines.CancellationException

internal suspend fun enrollDemoClient(
    alias: String,
    recovering: Boolean,
    listProfiles: suspend () -> List<MockProfileDto?>,
    createProfile: suspend () -> MockProfileDto?,
    mapProfile: (MockProfileDto) -> DemoProfile?
): DemoProfile {
    fun verified(profile: MockProfileDto?): DemoProfile = checkNotNull(
        profile?.takeIf { it.displayName == alias }?.let(mapProfile)?.takeIf { it.role == UserRole.CLIENT }
    ) { "MockAPI no devolvió el perfil de cliente esperado." }

    suspend fun recover(): DemoProfile? {
        val profiles = listProfiles().filterNotNull().distinct()
        val matches = profiles.filter { it.displayName == alias }
        check(matches.size <= 1) { "MockAPI devolvió perfiles de cliente duplicados." }
        val match = matches.singleOrNull() ?: return null
        check(profiles.count { it.id == match.id } == 1) { "MockAPI devolvió un ID de perfil ambiguo." }
        return verified(match)
    }

    if (recovering) recover()?.let { return it }

    return try {
        verified(createProfile())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val recovered = try {
            recover()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        recovered ?: throw error
    }
}
