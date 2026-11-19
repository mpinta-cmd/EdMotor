
package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {

    @Transaction
    @Query("""
        SELECT maintenance_records.* FROM maintenance_records
        JOIN vehicles ON vehicles.id = maintenance_records.vehicleId
        JOIN users ON users.id = :requestingUserId
        WHERE maintenance_records.vehicleId = :vehicleId
          AND users.role = :requestingRole
          AND (users.role = 'WORKSHOP' OR
               (users.role = 'CLIENT' AND vehicles.ownerUserId = users.id))
        ORDER BY maintenance_records.serviceDate DESC, maintenance_records.id DESC
    """)
    fun observeByVehicle(
        vehicleId: Int,
        requestingUserId: Int,
        requestingRole: String
    ): Flow<List<MaintenanceWithParts>>


    @Transaction
    @Query("""
        SELECT * FROM maintenance_records
        WHERE id = :recordId AND vehicleId = :vehicleId
        LIMIT 1
    """)
    suspend fun findWithParts(
        recordId: Int,
        vehicleId: Int
    ): MaintenanceWithParts?


    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(
        record: MaintenanceRecordEntity
    ): Long


    @Update
    suspend fun update(
        record: MaintenanceRecordEntity
    )


    @Query("""
        DELETE FROM maintenance_records
        WHERE id = :recordId
        AND vehicleId = :vehicleId
    """)
    suspend fun deleteByIdForVehicle(
        recordId: Int,
        vehicleId: Int
    ): Int
}
