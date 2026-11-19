package com.example.edmotor.data.repository

import com.example.edmotor.data.remote.MockProfileDto
import com.example.edmotor.model.DemoProfile
import com.example.edmotor.model.UserRole
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoClientEnrollmentTest {
    @Test
    fun lostCreateResponseIsRecoveredWithoutAnotherPost() = runBlocking {
        var saved: MockProfileDto? = null
        var writes = 0
        val result = enrollDemoClient(
            alias = ALIAS,
            recovering = false,
            listProfiles = { listOfNotNull(saved) },
            createProfile = {
                writes++
                saved = MockProfileDto("7", ALIAS, "CLIENT")
                throw IOException("Se perdió la respuesta")
            },
            mapProfile = ::mapProfile
        )
        assertEquals("7", result.remoteId)
        assertEquals(1, writes)
    }

    @Test
    fun resumedAttemptFindsOnlyItsExactAliasBeforeCreating() = runBlocking {
        val result = enrollDemoClient(
            alias = ALIAS,
            recovering = true,
            listProfiles = {
                listOf(MockProfileDto("7", ALIAS, "CLIENT"), MockProfileDto("8", "Cliente parecido", "CLIENT"))
            },
            createProfile = { error("El perfil recuperado no se envía otra vez") },
            mapProfile = ::mapProfile
        )
        assertEquals("7", result.remoteId)
    }

    @Test
    fun aliasWithDuplicateIdsOrWrongRoleIsNotClaimedOrRecreated() = runBlocking {
        val conflicts = listOf(
            listOf(MockProfileDto("7", ALIAS, "WORKSHOP")),
            listOf(MockProfileDto("7", ALIAS, "CLIENT"), MockProfileDto("8", ALIAS, "CLIENT")),
            listOf(MockProfileDto("7", ALIAS, "CLIENT"), MockProfileDto("7", "Otro cliente", "CLIENT"))
        )
        for (profiles in conflicts) {
            var writes = 0
            val attempted = runCatching {
                enrollDemoClient(ALIAS, true, { profiles }, {
                    writes++
                    MockProfileDto("10", ALIAS, "CLIENT")
                }, ::mapProfile)
            }
            assertTrue(attempted.isFailure)
            assertEquals(0, writes)
        }
    }

    private fun mapProfile(dto: MockProfileDto): DemoProfile =
        DemoProfile("test:${dto.id}", dto.displayName, UserRole.valueOf(dto.role), dto.id)

    private companion object {
        const val ALIAS = "Cliente 4d65b827-1394-4229-b274-a5071e2cb66b"
    }
}
