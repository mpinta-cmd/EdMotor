
package com.example.edmotor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        // Usuarios y vehículos
        UserEntity::class,
        VehicleEntity::class,

        // Inventario
        ProductEntity::class,
        ProductCategoryEntity::class,

        // Carrito y favoritos
        CartItemEntity::class,
        FavoriteProductEntity::class,

        // Mantenimiento
        MaintenanceRecordEntity::class,
        MaintenancePartEntity::class,

        // Pedidos y ventas
        OrderEntity::class,
        OrderItemEntity::class,

        // Citas
        AppointmentEntity::class,
        AppointmentCacheEntity::class,
        AppointmentWorkEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class EdMotorDatabase : RoomDatabase() {

    // USUARIOS
    abstract fun userDao(): UserDao

    // VEHÍCULOS
    abstract fun vehicleDao(): VehicleDao

    // INVENTARIO
    abstract fun productDao(): ProductDao
    abstract fun productCategoryDao(): ProductCategoryDao

    // CARRITO Y FAVORITOS
    abstract fun cartDao(): CartDao
    abstract fun favoriteProductDao(): FavoriteProductDao

    // MANTENIMIENTO
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun maintenancePartDao(): MaintenancePartDao

    // PEDIDOS
    abstract fun orderDao(): OrderDao

    // CITAS
    abstract fun appointmentDao(): AppointmentDao
    abstract fun appointmentWorkDao(): AppointmentWorkDao


    companion object {

        private const val DATABASE_NAME = "edmotor_database"
        private const val PRIVATE_DATABASE_NAME = "edmotor_local_accounts"

        @Volatile
        private var INSTANCE: EdMotorDatabase? = null

        /**
         * Obtiene la única instancia de EdMotorDatabase.
         */
        fun getDatabase(context: Context, privateMode: Boolean): EdMotorDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: buildDatabase(context, privateMode).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Construcción centralizada de Room.
         */
        private fun buildDatabase(
            context: Context,
            privateMode: Boolean
        ): EdMotorDatabase {

            return Room.databaseBuilder(
                context.applicationContext,
                EdMotorDatabase::class.java,
                if (privateMode) PRIVATE_DATABASE_NAME else DATABASE_NAME
            )
                .addMigrations(
                    *EdMotorMigrations.ALL
                )
                .build()
        }
    }
}
