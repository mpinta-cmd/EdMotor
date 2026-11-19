package com.example.edmotor.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.UserRole
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun credentialedDemoDoesNotRestoreLegacyPickerSession() = runBlocking {
        val file = File(scratch.root, "demo-credential.preferences_pb")
        val owner = demoOwner()
        withFreshStore(file) { it.selectProfile(owner) }

        withFreshStore(file) { session ->
            assertNull(session.restoreProfile(credentialedDemo = true) { error("Selección antigua rechazada") })
            assertNull(session.currentUser.value)
            session.selectProfile(owner, credentialedDemo = true)
        }

        withFreshStore(file) { session ->
            assertEquals(owner, session.restoreProfile(credentialedDemo = true) { key ->
                assertEquals(owner.remoteAuthId, key)
                owner
            })
            session.clearSession()
        }

        withFreshStore(file) { session ->
            assertNull(session.restoreProfile(credentialedDemo = true) { error("Sesión de credenciales eliminada") })
        }
    }

    @Test
    fun recreatedRoomAccountCannotReuseAnOldSessionForTheSameKeyAndRole() = runBlocking {
        val file = File(scratch.root, "different-account.preferences_pb")
        val owner = demoOwner()
        withFreshStore(file) { it.selectProfile(owner, credentialedDemo = true) }

        withFreshStore(file) { session ->
            val replacement = owner.copy(createdAtMillis = owner.createdAtMillis + 1)
            assertNull(session.restoreProfile(credentialedDemo = true) { replacement })
            assertNull(session.currentUser.value)
        }
    }

    @Test
    fun staleActiveAccountIsClearedIfRoomCannotValidateRestore() = runBlocking {
        val file = File(scratch.root, "room-unavailable.preferences_pb")
        val profile = demoClient().copy(passwordHash = "hash-for-test", salt = "salt-for-test")
        withFreshStore(file) { session ->
            session.selectProfile(profile)
            assertEquals("", session.currentUser.value?.passwordHash)
            assertEquals("", session.currentUser.value?.salt)

            val failure = runCatching { session.restoreProfile { error("Room no está disponible") } }
            assertTrue(failure.isFailure)
            assertNull(session.currentUser.value)
            assertEquals("", session.restoreProfile { profile }?.passwordHash)
        }
    }

    @Test
    fun pendingSignupSurvivesProcessRecreationAndStaysInItsProject() = runBlocking {
        val file = File(scratch.root, "pending-registration.preferences_pb")
        val project = "2".repeat(24)
        val otherProject = "3".repeat(24)
        var originalAlias = ""
        withFreshStore(file) { session ->
            val prepared = session.prepareDemoRegistration(project, "cliente_1")
            assertFalse(prepared.recovering)
            originalAlias = prepared.alias
            session.clearSession()
        }

        withFreshStore(file) { session ->
            val retry = session.prepareDemoRegistration(project, "cliente_1")
            assertTrue(retry.recovering)
            assertEquals(originalAlias, retry.alias)
            assertNotEquals(originalAlias, session.prepareDemoRegistration(otherProject, "cliente_1").alias)
            assertNotEquals(originalAlias, session.prepareDemoRegistration(project, "cliente_2").alias)
            session.finishDemoRegistration(project, "cliente_1", retry.alias)
        }

        withFreshStore(file) { session ->
            val newAttempt = session.prepareDemoRegistration(project, "cliente_1")
            assertFalse(newAttempt.recovering)
            assertNotEquals(originalAlias, newAttempt.alias)
        }
    }

    @Test
    fun pickupDismissalSurvivesSignOutAndRecreationAndStaysInItsAccount() = runBlocking {
        val file = File(scratch.root, "pickup.preferences_pb")
        val clientKey = "mockapi:${"2".repeat(24)}:17"
        val otherClientKey = "mockapi:${"2".repeat(24)}:18"
        val otherProjectKey = "mockapi:${"3".repeat(24)}:17"
        val client = demoClient().copy(remoteAuthId = clientKey)
        val otherClient = demoClient().copy(id = 19, remoteAuthId = otherClientKey)
        val otherProject = demoClient().copy(id = 20, remoteAuthId = otherProjectKey)
        val owner = demoOwner()
        val oldEvent = checkNotNull(PickupEvent.decode("200:100:1"))
        val dismissedEvent = checkNotNull(PickupEvent.decode("300:110:2"))
        val staleSessionEvent = checkNotNull(PickupEvent.decode("400:120:3"))

        withFreshStore(file) { session ->
            session.selectProfile(client, credentialedDemo = true)
            val activeClient = checkNotNull(session.currentUser.value)
            assertNull(session.observePickupDismissal(clientKey).first())
            session.dismissPickup(activeClient, dismissedEvent)
            session.dismissPickup(activeClient, oldEvent)

            session.selectProfile(otherClient, credentialedDemo = true)
            session.dismissPickup(activeClient, staleSessionEvent)
            assertNull(session.observePickupDismissal(otherClientKey).first())

            session.selectProfile(otherProject, credentialedDemo = true)
            val otherActive = checkNotNull(session.currentUser.value)
            assertNull(session.observePickupDismissal(otherProjectKey).first())
            session.dismissPickup(otherActive, oldEvent)

            session.selectProfile(owner, credentialedDemo = true)
            session.dismissPickup(checkNotNull(session.currentUser.value), staleSessionEvent)
            assertNull(session.observePickupDismissal(checkNotNull(owner.remoteAuthId)).first())
            session.clearSession()
        }

        withFreshStore(file) { session ->
            assertNull(session.currentUser.value)
            session.selectProfile(client, credentialedDemo = true)
            assertEquals(dismissedEvent, session.observePickupDismissal(clientKey).first())
            assertNull(session.observePickupDismissal(otherClientKey).first())
            assertEquals(oldEvent, session.observePickupDismissal(otherProjectKey).first())
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
