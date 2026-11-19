
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
        AppointmentCacheEntity::class
    ],
    version = 10,
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


    companion object {

        private const val DATABASE_NAME = "edmotor_database"

        private val databases = mutableMapOf<String, EdMotorDatabase>()

        @Synchronized
        fun getDatabase(context: Context, projectKey: String? = null): EdMotorDatabase {
            val name = projectKey?.let { "${DATABASE_NAME}_$it" } ?: DATABASE_NAME
            return databases.getOrPut(name) {
                Room.databaseBuilder(
                    context.applicationContext,
                    EdMotorDatabase::class.java,
                    name
                )
                    .addMigrations(*EdMotorMigrations.ALL)
                    .build()
            }
        }
    }
}
