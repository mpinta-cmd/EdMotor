
package com.example.edmotor.data.local

import android.content.Context

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VehicleEntity::class,
        CartItemEntity::class,
        FavoriteProductEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class EdMotorDatabase : RoomDatabase() {

    // ==========================================
    // DAO DE VEHÍCULOS
    // ==========================================

    abstract fun vehicleDao(): VehicleDao

    // ==========================================
    // DAO DEL CARRITO
    // ==========================================

    abstract fun cartDao(): CartDao

    // ==========================================
    // DAO DE PRODUCTOS FAVORITOS
    // ==========================================

    abstract fun favoriteProductDao(): FavoriteProductDao


    companion object {

        private const val DATABASE_NAME = "edmotor_database"

        @Volatile
        private var INSTANCE: EdMotorDatabase? = null

        // ======================================
        // MIGRACIÓN DE VERSIÓN 1 A VERSIÓN 2
        // ======================================

        private val MIGRATION_1_2 = object : Migration(1, 2) {

            override fun migrate(
                database: SupportSQLiteDatabase
            ) {

                // Crear tabla del carrito

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cart_items` (
                        `productId` INTEGER NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        PRIMARY KEY(`productId`)
                    )
                    """.trimIndent()
                )

                // Crear tabla de productos favoritos

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
        // OBTENER INSTANCIA DE LA BASE DE DATOS
        // ======================================

        fun getDatabase(context: Context): EdMotorDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EdMotorDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { database ->

                        INSTANCE = database

                    }
            }
        }
    }
}
