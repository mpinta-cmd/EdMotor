
package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    // ==========================================
    // CONSULTAR VEHÍCULOS DEL TALLER
    // ==========================================

    @Query("""
        SELECT
            vehicles.*,
            users.displayName AS ownerName,
            users.username AS ownerUsername
        FROM vehicles
        LEFT JOIN users
            ON users.id = vehicles.ownerUserId
        ORDER BY vehicles.id DESC
    """)
    fun observeAllForWorkshop(): Flow<List<VehicleWithOwnerRow>>

    // ==========================================
    // CONSULTAR VEHÍCULOS DE UN CLIENTE
    // ==========================================

    @Query("""
        SELECT * FROM vehicles
        WHERE ownerUserId = :ownerUserId
        ORDER BY id DESC
    """)
    fun observeByOwner(
        ownerUserId: Int
    ): Flow<List<VehicleEntity>>

    // ==========================================
    // BUSCAR VEHÍCULO
    // ==========================================

    @Query("""
        SELECT * FROM vehicles
        WHERE id = :vehicleId
        LIMIT 1
    """)
    suspend fun findById(
        vehicleId: Int
    ): VehicleEntity?

    // ==========================================
    // REGISTRAR VEHÍCULO
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(
        vehicle: VehicleEntity
    ): Long

    // ==========================================
    // ACTUALIZAR VEHÍCULO
    // ==========================================

    @Update
    suspend fun update(
        vehicle: VehicleEntity
    )

    // Actualización protegida por propietario.
    // No permite modificar vehículos de otras cuentas.

    @Query("""
        UPDATE vehicles
        SET
            brand = :brand,
            model = :model,
            year = :year,
            engine = :engine,
            plate = :plate,
            notes = :notes
        WHERE id = :vehicleId
        AND ownerUserId = :ownerUserId
    """)
    suspend fun updateForOwner(
        vehicleId: Int,
        ownerUserId: Int,
        brand: String,
        model: String,
        year: Int,
        engine: String,
        plate: String,
        notes: String
    ): Int

    // ==========================================
    // ACTUALIZAR KILOMETRAJE
    // ==========================================

    @Query("""
        UPDATE vehicles
        SET mileage = :mileage
        WHERE id = :vehicleId
    """)
    suspend fun updateMileage(
        vehicleId: Int,
        mileage: String
    ): Int

    // ==========================================
    // ASIGNAR VEHÍCULO A CLIENTE
    // ==========================================

    @Query("""
        UPDATE vehicles
        SET ownerUserId = :ownerUserId
        WHERE id = :vehicleId
        AND ownerUserId IS NULL
        AND EXISTS (
            SELECT 1
            FROM users
            WHERE users.id = :ownerUserId
            AND users.role = 'CLIENT'
        )
    """)
    suspend fun assignOwner(
        vehicleId: Int,
        ownerUserId: Int
    ): Int

    // ==========================================
    // ELIMINAR VEHÍCULO
    // ==========================================

    @Delete
    suspend fun delete(
        vehicle: VehicleEntity
    )
}
