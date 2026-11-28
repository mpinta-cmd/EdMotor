package com.example.edmotor.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardEvidenceRepositoryTest {
    @Test
    fun recoversTheAssociatedPhotoAfterReopenAndKeepsScopesSeparate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "dashboard-evidence-test-${UUID.randomUUID()}.db"
        val sample = jpeg()
        try {
            val original = openDatabase(context, name)
            val saved = try {
                val repository = DashboardEvidenceRepository(original.evidence())
                val olderId = repository.save(ACCOUNT, "engine", null, null, 1000, sample)
                val id = repository.save(ACCOUNT, "oil", "42", "Toyota Corolla · 2021", 2000, sample)
                repository.save(GUEST, "battery", null, null, 3000, sample)

                assertEquals(listOf(id, olderId), repository.observe(ACCOUNT).first().map { it.id })
                DashboardEvidence(id, "oil", "42", "Toyota Corolla · 2021", 2000)
            } finally {
                original.close()
            }

            val reopened = openDatabase(context, name)
            try {
                val repository = DashboardEvidenceRepository(reopened.evidence())
                assertEquals(saved, repository.observe(ACCOUNT).first().first())
                assertEquals("battery", repository.observe(GUEST).first().single().indicatorId)
                assertArrayEquals(sample, repository.loadPhoto(ACCOUNT, saved.id))
                assertNull(repository.loadPhoto(GUEST, saved.id))

                val invalidPhoto = runCatching {
                    repository.save(ACCOUNT, "oil", null, null, 4000, ByteArray(DASHBOARD_EVIDENCE_MAX_JPEG_BYTES + 1))
                }
                assertTrue(invalidPhoto.exceptionOrNull() is IllegalArgumentException)
                assertEquals(2, repository.observe(ACCOUNT).first().size)

                repository.delete(GUEST, saved.id)
                assertArrayEquals(sample, repository.loadPhoto(ACCOUNT, saved.id))
                repository.delete(ACCOUNT, saved.id)
                assertNull(repository.loadPhoto(ACCOUNT, saved.id))
                assertEquals("engine", repository.observe(ACCOUNT).first().single().indicatorId)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun openDatabase(context: Context, name: String): DashboardEvidenceDatabase =
        Room.databaseBuilder(context, DashboardEvidenceDatabase::class.java, name).build()

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.BLUE)
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val ACCOUNT = "online:test-account"
        const val GUEST = "online:guest"
    }
}
