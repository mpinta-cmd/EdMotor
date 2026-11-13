
package com.example.edmotor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UserEntity::class,
        VehicleEntity::class,
        CartItemEntity::class,
        FavoriteProductEntity::class,
        MaintenanceRecordEntity::class,
        MaintenancePartEntity::class,
        ProductEntity::class,
        ProductCategoryEntity::class,

        // ======================================
        // PEDIDOS Y VENTAS
        // ======================================

        OrderEntity::class,
        OrderItemEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class EdMotorDatabase : RoomDatabase() {

    // ==========================================
    // DAO
    // ==========================================

    abstract fun userDao(): UserDao

    abstract fun vehicleDao(): VehicleDao

    abstract fun cartDao(): CartDao

    abstract fun favoriteProductDao(): FavoriteProductDao

    abstract fun maintenanceDao(): MaintenanceDao

    abstract fun maintenancePartDao(): MaintenancePartDao

    abstract fun productDao(): ProductDao

    abstract fun productCategoryDao(): ProductCategoryDao

    // ==========================================
    // DAO DE PEDIDOS Y VENTAS
    // ==========================================

    abstract fun orderDao(): OrderDao


    // ==========================================
    // MIGRACIONES Y CONFIGURACIÓN
    // ==========================================

    companion object {

        private const val DATABASE_NAME = "edmotor_database"

        @Volatile
        private var INSTANCE: EdMotorDatabase? = null


        // ======================================
        // MIGRACIÓN 1 -> 2
        // ======================================

        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(database: SupportSQLiteDatabase) {

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cart_items` (
                        `productId` INTEGER NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        PRIMARY KEY(`productId`)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_products` (
                        `productId` INTEGER NOT NULL,
                        PRIMARY KEY(`productId`)
                    )
                    """.trimIndent()
                )
            }
        }


        // ======================================
        // MIGRACIÓN 2 -> 3
        // ======================================

        private val MIGRATION_2_3 = object : Migration(2, 3) {

            override fun migrate(database: SupportSQLiteDatabase) {

                // ==================================
                // USUARIOS
                // ==================================

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `users` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `username` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `phone` TEXT,
                        `passwordHash` TEXT NOT NULL,
                        `salt` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_users_username`
                    ON `users` (`username`)
                    """.trimIndent()
                )


                // ==================================
                // VEHÍCULOS
                // ==================================

                database.execSQL(
                    """
                    CREATE TABLE `vehicles_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `brand` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `engine` TEXT NOT NULL,
                        `plate` TEXT NOT NULL,
                        `mileage` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `ownerUserId` INTEGER,
                        FOREIGN KEY(`ownerUserId`)
                            REFERENCES `users`(`id`)
                            ON UPDATE NO ACTION
                            ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT INTO `vehicles_new` (
                        `id`,
                        `brand`,
                        `model`,
                        `year`,
                        `engine`,
                        `plate`,
                        `mileage`,
                        `notes`,
                        `ownerUserId`
                    )
                    SELECT
                        `id`,
                        `brand`,
                        `model`,
                        `year`,
                        `engine`,
                        `plate`,
                        `mileage`,
                        `notes`,
                        NULL
                    FROM `vehicles`
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE `vehicles`")

                database.execSQL(
                    "ALTER TABLE `vehicles_new` RENAME TO `vehicles`"
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_vehicles_ownerUserId`
                    ON `vehicles` (`ownerUserId`)
                    """.trimIndent()
                )


                // ==================================
                // CARRITO
                // ==================================

                database.execSQL(
                    """
                    CREATE TABLE `cart_items_new` (
                        `userId` INTEGER NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `productId`)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT INTO `cart_items_new` (
                        `userId`,
                        `productId`,
                        `quantity`
                    )
                    SELECT
                        0,
                        `productId`,
                        `quantity`
                    FROM `cart_items`
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE `cart_items`")

                database.execSQL(
                    "ALTER TABLE `cart_items_new` RENAME TO `cart_items`"
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_cart_items_userId`
                    ON `cart_items` (`userId`)
                    """.trimIndent()
                )


                // ==================================
                // FAVORITOS
                // ==================================

                database.execSQL(
                    """
                    CREATE TABLE `favorite_products_new` (
                        `userId` INTEGER NOT NULL,
                        `productId` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `productId`)
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT INTO `favorite_products_new` (
                        `userId`,
                        `productId`
                    )
                    SELECT
                        0,
                        `productId`
                    FROM `favorite_products`
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE `favorite_products`")

                database.execSQL(
                    """
                    ALTER TABLE `favorite_products_new`
                    RENAME TO `favorite_products`
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_favorite_products_userId`
                    ON `favorite_products` (`userId`)
                    """.trimIndent()
                )


                // ==================================
                // HISTORIAL DE MANTENIMIENTOS
                // ==================================

                database.execSQL(
                    """
                    CREATE TABLE `maintenance_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `vehicleId` INTEGER NOT NULL,
                        `createdByUserId` INTEGER NOT NULL,
                        `updatedByUserId` INTEGER,
                        `serviceType` TEXT NOT NULL,
                        `serviceDate` TEXT NOT NULL,
                        `mileageKm` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `laborCostCop` INTEGER NOT NULL,
                        `nextServiceMileageKm` INTEGER,
                        `nextServiceDate` TEXT,
                        `observations` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`vehicleId`)
                            REFERENCES `vehicles`(`id`)
                            ON UPDATE NO ACTION
                            ON DELETE CASCADE,
                        FOREIGN KEY(`createdByUserId`)
                            REFERENCES `users`(`id`)
                            ON UPDATE NO ACTION
                            ON DELETE RESTRICT,
                        FOREIGN KEY(`updatedByUserId`)
                            REFERENCES `users`(`id`)
                            ON UPDATE NO ACTION
                            ON DELETE SET NULL
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_maintenance_records_vehicleId`
                    ON `maintenance_records` (`vehicleId`)
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_maintenance_records_createdByUserId`
                    ON `maintenance_records` (`createdByUserId`)
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_maintenance_records_updatedByUserId`
                    ON `maintenance_records` (`updatedByUserId`)
                    """.trimIndent()
                )


                // ==================================
                // REPUESTOS DE MANTENIMIENTOS
                // ==================================

                database.execSQL(
                    """
                    CREATE TABLE `maintenance_parts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `maintenanceRecordId` INTEGER NOT NULL,
                        `catalogProductId` INTEGER,
                        `name` TEXT NOT NULL,
                        `brand` TEXT NOT NULL,
                        `reference` TEXT NOT NULL,
                        `specification` TEXT NOT NULL,
                        `quantity` REAL NOT NULL,
                        `unit` TEXT NOT NULL,
                        `unitPriceCop` INTEGER NOT NULL,
                        FOREIGN KEY(`maintenanceRecordId`)
                            REFERENCES `maintenance_records`(`id`)
                            ON UPDATE NO ACTION
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_maintenance_parts_maintenanceRecordId`
                    ON `maintenance_parts` (`maintenanceRecordId`)
                    """.trimIndent()
                )
            }
        }


        // ======================================
        // MIGRACIÓN 3 -> 4
        // INVENTARIO DE PRODUCTOS
        // ======================================

        private val MIGRATION_3_4 = object : Migration(3, 4) {

            override fun migrate(database: SupportSQLiteDatabase) {

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `products` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `priceCop` INTEGER NOT NULL,
                        `promotionPriceCop` INTEGER,
                        `imageRes` INTEGER NOT NULL,
                        `contentDescription` TEXT NOT NULL,
                        `rating` REAL NOT NULL,
                        `stock` INTEGER NOT NULL,
                        `isNew` INTEGER NOT NULL,
                        `isOnPromotion` INTEGER NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )


                // ==================================
                // ÍNDICES DE PRODUCTOS
                // ==================================

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_products_name`
                    ON `products` (`name`)
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_products_category`
                    ON `products` (`category`)
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_products_isActive`
                    ON `products` (`isActive`)
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_products_stock`
                    ON `products` (`stock`)
                    """.trimIndent()
                )
            }
        }


        // ======================================
        // MIGRACIÓN 4 -> 5
        // PEDIDOS Y VENTAS
        // ======================================

        private val MIGRATION_4_5 = object : Migration(4, 5) {

            override fun migrate(database: SupportSQLiteDatabase) {

                // ==================================
                // TABLA DE PEDIDOS
                // ==================================

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


                // ==================================
                // ÍNDICES DE PEDIDOS
                // ==================================

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


                // ==================================
                // TABLA DE PRODUCTOS DE CADA PEDIDO
                // ==================================

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


                // ==================================
                // ÍNDICE DE PRODUCTOS DEL PEDIDO
                // ==================================

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    `index_order_items_orderId`
                    ON `order_items` (`orderId`)
                    """.trimIndent()
                )
            }
        }


        // ======================================
        // MIGRACIÓN 5 -> 6
        // IMAGEN PERSONALIZADA DEL PRODUCTO
        // ======================================

        private val MIGRATION_5_6 = object : Migration(5, 6) {

            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `products` ADD COLUMN `imageUri` TEXT"
                )
            }
        }


        // ======================================
        // MIGRACIÓN 6 -> 7
        // CATEGORÍAS DINÁMICAS DEL INVENTARIO
        // ======================================

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `product_categories` (
                        `name` TEXT NOT NULL COLLATE NOCASE,
                        `emoji` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`name`)
                    )
                    """.trimIndent()
                )
            }
        }


        // ======================================
        // MIGRACIÓN 7 -> 8
        // IDENTIDAD REMOTA SUPABASE
        // ======================================

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `users` ADD COLUMN `remoteAuthId` TEXT"
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_users_remoteAuthId`
                    ON `users` (`remoteAuthId`)
                    """.trimIndent()
                )
            }
        }

        // ======================================
        // INSTANCIA ÚNICA DE ROOM
        // ======================================

        fun getDatabase(context: Context): EdMotorDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EdMotorDatabase::class.java,
                    DATABASE_NAME
                )

                    // ==================================
                    // REGISTRO DE MIGRACIONES
                    // ==================================

                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )

                    .build()

                    .also {
                        INSTANCE = it
                    }
            }
        }
    }
}
