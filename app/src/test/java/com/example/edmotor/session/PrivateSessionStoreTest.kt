package com.example.edmotor.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.edmotor.data.remote.PrivateCredentials
import com.example.edmotor.data.remote.SupabaseConnection
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateSessionStoreTest {
    @get:Rule val scratch = TemporaryFolder()

    @Test
    fun readsEncryptedCredentialsAfterRecreationAndClearsThemOnLogout() = runBlocking {
        val prefs = File(scratch.root, "private.preferences_pb")
        val key = keyForTest()
        val credentials = PrivateCredentials(USER_ID, "access-fixture", "refresh-fixture", 123_456L)
        withStore(prefs, key) { it.save(SavedPrivateSession("offline-fixture", credentials)) }

        assertFalse(prefs.readText().contains(credentials.accessToken))
        assertFalse(prefs.readText().contains(credentials.refreshToken))
        withStore(prefs, key) { store ->
            val restored = requireNotNull(store.load())
            assertEquals("offline-fixture", restored.projectKey)
            assertEquals(USER_ID, restored.credentials.userId)
            assertEquals(credentials.accessToken, restored.credentials.accessToken)
            assertEquals(credentials.refreshToken, restored.credentials.refreshToken)
            assertEquals(credentials.expiresAtMillis, restored.credentials.expiresAtMillis)
            store.clear()
        }
        withStore(prefs, key) { assertNull(it.load()) }
    }

    @Test
    fun rejectsEncryptedStateAfterTheEncryptionKeyIsLost() = runBlocking {
        val prefs = File(scratch.root, "lost-key.preferences_pb")
        val originalKey = keyForTest()
        val differentKey = keyForTest()
        withStore(prefs, originalKey) {
            it.save(SavedPrivateSession("offline-fixture", PrivateCredentials(USER_ID, "fixture-a", "fixture-b", 123L)))
        }
        withStore(prefs, differentKey) { assertNull(it.load()) }
        withStore(prefs, originalKey) { assertNull(it.load()) }
    }

    @Test
    fun profileKeysAreBoundToHostedProjectAndCanonicalPrivateUid() {
        val publishable = "sb_publishable_offline_fixture"
        val connection = requireNotNull(SupabaseConnection.fromConfig("https://offline-fixture.supabase.co", publishable))
        val localKey = connection.profileKey(USER_ID)
        assertEquals("supabase:offline-fixture:$USER_ID", localKey)
        assertEquals(USER_ID, connection.profileId(localKey))
        assertNull(connection.profileId("local-demo:workshop"))
        assertNull(connection.profileId("supabase:another-fixture:$USER_ID"))
        assertNull(connection.profileId("supabase:offline-fixture:1-1-1-1-1"))
        assertNull(SupabaseConnection.fromConfig("https://offline-fixture.supabase.co.invalid", publishable))
        assertNull(SupabaseConnection.fromConfig("http://offline-fixture.supabase.co", publishable))
        assertNull(SupabaseConnection.fromConfig("https://offline-fixture.supabase.co", "sb_secret_fixture"))
    }

    private suspend fun withStore(file: File, key: SecretKey, action: suspend (PrivateSessionStore) -> Unit) {
        val job = SupervisorJob()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file }
        )
        try {
            action(PrivateSessionStore(dataStore, PrivateSessionCipher { key }))
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun keyForTest(): SecretKey = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()

    private companion object {
        const val USER_ID = "4d65b827-1394-4229-b274-a5071e2cb66b"
    }
}
