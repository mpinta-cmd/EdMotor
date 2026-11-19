
package com.example.edmotor.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppointmentMigration {

    val MIGRATION_8_9 = object : Migration(8, 9) {

        override fun migrate(database: SupportSQLiteDatabase) {

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `appointments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `clientLocalUserId` INTEGER NOT NULL,
                    `clientAuthId` TEXT NOT NULL,
                    `clientName` TEXT NOT NULL,
                    `clientUsername` TEXT NOT NULL,
                    `clientPhone` TEXT,
                    `vehicleBrand` TEXT NOT NULL,
                    `vehicleModel` TEXT NOT NULL,
                    `vehicleYear` INTEGER NOT NULL,
                    `vehiclePlate` TEXT NOT NULL,
                    `scheduledAtMillis` INTEGER NOT NULL,
                    `description` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `createdAtMillis` INTEGER NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_appointments_clientLocalUserId`
                ON `appointments` (`clientLocalUserId`)
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_appointments_status`
                ON `appointments` (`status`)
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_appointments_scheduledAtMillis`
                ON `appointments` (`scheduledAtMillis`)
                """.trimIndent()
            )
        }
    }
}
