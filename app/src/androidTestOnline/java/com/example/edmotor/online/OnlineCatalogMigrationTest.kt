package com.example.edmotor.online

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlineCatalogMigrationTest {
    @Test
    fun existingCatalogEntriesAndNewRatingStatisticsSurviveReopening() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "catalog_migration_${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
                database.execSQL(
                    """
                    CREATE TABLE online_catalog (
                        project TEXT NOT NULL, id INTEGER NOT NULL, version INTEGER NOT NULL,
                        name TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL,
                        priceCop INTEGER NOT NULL, promotionPriceCop INTEGER, stock INTEGER NOT NULL, imagePath TEXT,
                        PRIMARY KEY (project, id)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """INSERT INTO online_catalog
                       VALUES ('test', 17, 2, 'Filtro de aceite', 'Repuesto de prueba', 'Filtros', 18000, NULL, 3, NULL)"""
                )
                database.version = 1
            }

            val builder = Room.databaseBuilder(context, OnlineCatalogDatabase::class.java, name)
                .addMigrations(OnlineCatalogDatabase.MIGRATION_1_2, OnlineCatalogDatabase.MIGRATION_2_3)
            val database = builder.build()
            val rated = try {
                val original = database.catalog().read("test").single()
                assertEquals("Filtro de aceite", original.name)
                assertEquals(3, original.stock)
                assertEquals(0L, original.ratingCount)
                assertNull(original.averageRating)
                val updated = original.copy(averageRating = 4.5, ratingCount = 2)
                database.catalog().replace("test", listOf(updated))
                assertNull(database.catalog().readSnapshot("unseen"))
                database.catalog().replace("empty", emptyList())
                updated
            } finally {
                database.close()
            }
            val reopened = builder.build()
            try {
                assertEquals(rated, reopened.catalog().read("test").single())
                assertEquals(emptyList<OnlineProductCacheEntity>(), reopened.catalog().readSnapshot("empty"))
                assertNull(reopened.catalog().readSnapshot("unseen"))
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
