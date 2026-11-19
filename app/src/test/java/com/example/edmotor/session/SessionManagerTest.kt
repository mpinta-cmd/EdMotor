package com.example.edmotor.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionManagerTest {
    @get:Rule val scratch = TemporaryFolder()

    @Test
    fun clientAndOwnerSelectionsSurviveRecreationAndLogoutRemovesThem() = runBlocking {
        val file = File(scratch.root, "selected.preferences_pb")
        val client = demoClient()
        val owner = demoOwner()
        withFreshStore(file) { session ->
            session.selectProfile(client)
            assertEquals(client, session.currentUser.value)
        }

        withFreshStore(file) { session ->
            assertNull(session.currentUser.value)
            assertEquals(client, session.restoreProfile { key ->
                assertEquals(client.remoteAuthId, key)
                client
            })
            session.selectProfile(owner)
            assertEquals(owner, session.currentUser.value)
        }

        withFreshStore(file) { session ->
            assertEquals(owner, session.restoreProfile { key ->
                assertEquals(owner.remoteAuthId, key)
                owner
            })
            session.clearSession()
            assertNull(session.currentUser.value)
        }

        withFreshStore(file) { session ->
            assertNull(session.restoreProfile { error("El cierre de sesión no dejó un perfil guardado") })
        }
    }

    @Test
    fun roleMismatchRejectsAndClearsTheRememberedProfile() = runBlocking {
        val file = File(scratch.root, "wrong-role.preferences_pb")
        val user = demoClient()
        withFreshStore(file) { it.selectProfile(user) }

        withFreshStore(file) { session ->
            assertNull(session.restoreProfile { user.copy(role = UserRole.WORKSHOP.name) })
            assertNull(session.currentUser.value)
        }

        withFreshStore(file) { session ->
            assertNull(session.restoreProfile { error("Se eliminó el perfil recordado de rol inválido") })
        }
    }

    private suspend fun withFreshStore(file: File, action: suspend (SessionManager) -> Unit) {
        val job = SupervisorJob()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file }
        )
        try {
            action(SessionManager(dataStore))
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun demoClient() = UserEntity(
        id = 17,
        username = CLIENT_KEY,
        displayName = "Cliente",
        passwordHash = "",
        salt = "",
        role = UserRole.CLIENT.name,
        remoteAuthId = CLIENT_KEY
    )

    private fun demoOwner() = UserEntity(
        id = 18,
        username = "local-demo:workshop",
        displayName = "EdMotor",
        passwordHash = "",
        salt = "",
        role = UserRole.WORKSHOP.name,
        remoteAuthId = "local-demo:workshop"
    )

    private companion object {
        const val CLIENT_KEY = "local-demo:client:4d65b827-1394-4229-b274-a5071e2cb66b"
    }
}
