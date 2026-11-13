
package com.example.edmotor.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object EdMotorMigrations {

    val MIGRATION_4_5 = object : Migration(4, 5) {

        override fun migrate(database: SupportSQLiteDatabase) {

            // ======================================
            // TABLA DE PEDIDOS
            // ======================================

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `orders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `clientUserId` INTEGER NOT NULL,
                    `clientName` TEXT NOT NULL,
                    `clientUsername` TEXT NOT NULL,
                    `clientPhone` TEXT,
                    `status` TEXT NOT NULL,
                    `totalCop` INTEGER NOT NULL,
                    `createdAtMillis` INTEGER NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL,
                    `confirmedByUserId` INTEGER,
                    `confirmedAtMillis` INTEGER,
                    FOREIGN KEY(`clientUserId`)
                        REFERENCES `users`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE RESTRICT,
                    FOREIGN KEY(`confirmedByUserId`)
                        REFERENCES `users`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE SET NULL
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_orders_clientUserId`
                ON `orders` (`clientUserId`)
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_orders_status`
                ON `orders` (`status`)
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_orders_confirmedByUserId`
                ON `orders` (`confirmedByUserId`)
                """.trimIndent()
            )

            // ======================================
            // TABLA DE PRODUCTOS DE CADA PEDIDO
            // ======================================

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `order_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `orderId` INTEGER NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `productName` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `unitPriceCop` INTEGER NOT NULL,
                    `quantity` INTEGER NOT NULL,
                    `subtotalCop` INTEGER NOT NULL,
                    FOREIGN KEY(`orderId`)
                        REFERENCES `orders`(`id`)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                `index_order_items_orderId`
                ON `order_items` (`orderId`)
                """.trimIndent()
            )
        }
    }
}
