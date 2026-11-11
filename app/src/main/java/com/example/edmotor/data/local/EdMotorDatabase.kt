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
        MaintenancePartEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class EdMotorDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun cartDao(): CartDao
    abstract fun favoriteProductDao(): FavoriteProductDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun maintenancePartDao(): MaintenancePartDao

    companion object {
        private const val DATABASE_NAME = "edmotor_database"

        @Volatile
        private var INSTANCE: EdMotorDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `cart_items` (
                        `productId` INTEGER NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        PRIMARY KEY(`productId`)
                    )""".trimIndent()
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `favorite_products` (
                        `productId` INTEGER NOT NULL,
                        PRIMARY KEY(`productId`)
                    )""".trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Usuarios: inicialmente solo se crea la estructura.
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `users` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `username` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `phone` TEXT,
                        `passwordHash` TEXT NOT NULL,
                        `salt` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )""".trimIndent()
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)"
                )

                // Vehículos: se conserva la información anterior y se añade su propietario.
                database.execSQL(
                    """CREATE TABLE `vehicles_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `brand` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `year` INTEGER NOT NULL,
                        `engine` TEXT NOT NULL,
                        `plate` TEXT NOT NULL,
                        `mileage` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `ownerUserId` INTEGER,
                        FOREIGN KEY(`ownerUserId`) REFERENCES `users`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT
                    )""".trimIndent()
                )
                database.execSQL(
                    """INSERT INTO `vehicles_new`
                        (`id`, `brand`, `model`, `year`, `engine`, `plate`, `mileage`, `notes`, `ownerUserId`)
                        SELECT `id`, `brand`, `model`, `year`, `engine`, `plate`, `mileage`, `notes`, NULL
                        FROM `vehicles`""".trimIndent()
                )
                database.execSQL("DROP TABLE `vehicles`")
                database.execSQL("ALTER TABLE `vehicles_new` RENAME TO `vehicles`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_vehicles_ownerUserId` ON `vehicles` (`ownerUserId`)"
                )

                // Carrito: clave compuesta para separar el contenido por cuenta.
                database.execSQL(
                    """CREATE TABLE `cart_items_new` (
                        `userId` INTEGER NOT NULL,
                        `productId` INTEGER NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `productId`)
                    )""".trimIndent()
                )
                database.execSQL(
                    """INSERT INTO `cart_items_new` (`userId`, `productId`, `quantity`)
                        SELECT 0, `productId`, `quantity` FROM `cart_items`""".trimIndent()
                )
                database.execSQL("DROP TABLE `cart_items`")
                database.execSQL("ALTER TABLE `cart_items_new` RENAME TO `cart_items`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cart_items_userId` ON `cart_items` (`userId`)"
                )

                // Favoritos: clave compuesta para separar favoritos por cuenta.
                database.execSQL(
                    """CREATE TABLE `favorite_products_new` (
                        `userId` INTEGER NOT NULL,
                        `productId` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `productId`)
                    )""".trimIndent()
                )
                database.execSQL(
                    """INSERT INTO `favorite_products_new` (`userId`, `productId`)
                        SELECT 0, `productId` FROM `favorite_products`""".trimIndent()
                )
                database.execSQL("DROP TABLE `favorite_products`")
                database.execSQL("ALTER TABLE `favorite_products_new` RENAME TO `favorite_products`")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_favorite_products_userId` ON `favorite_products` (`userId`)"
                )

                // Historial de mantenimientos.
                database.execSQL(
                    """CREATE TABLE `maintenance_records` (
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
                        FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`createdByUserId`) REFERENCES `users`(`id`)
                            ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`updatedByUserId`) REFERENCES `users`(`id`)
                            ON UPDATE NO ACTION ON DELETE SET NULL
                    )""".trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_maintenance_records_vehicleId` ON `maintenance_records` (`vehicleId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_maintenance_records_createdByUserId` ON `maintenance_records` (`createdByUserId`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_maintenance_records_updatedByUserId` ON `maintenance_records` (`updatedByUserId`)"
                )

                database.execSQL(
                    """CREATE TABLE `maintenance_parts` (
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
                        FOREIGN KEY(`maintenanceRecordId`) REFERENCES `maintenance_records`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_maintenance_parts_maintenanceRecordId` ON `maintenance_parts` (`maintenanceRecordId`)"
                )
            }
        }

        fun getDatabase(context: Context): EdMotorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EdMotorDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
