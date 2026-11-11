
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
        SELECT * FROM maintenance_records
        WHERE vehicleId = :vehicleId
        ORDER BY serviceDate DESC, id DESC
    """)
    fun observeByVehicle(
        vehicleId: Int
    ): Flow<List<MaintenanceWithParts>>


    @Transaction
    @Query("""
        SELECT * FROM maintenance_records
        WHERE id = :recordId
        LIMIT 1
    """)
    suspend fun findWithParts(
        recordId: Int
    ): MaintenanceWithParts?


    @Query("""
        SELECT * FROM maintenance_records
        WHERE id = :recordId
        LIMIT 1
    """)
    suspend fun findById(
        recordId: Int
    ): MaintenanceRecordEntity?


    @Query("""
        SELECT * FROM maintenance_records
        WHERE id = :recordId
        AND vehicleId = :vehicleId
        LIMIT 1
    """)
    suspend fun findByIdForVehicle(
        recordId: Int,
        vehicleId: Int
    ): MaintenanceRecordEntity?


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
    """)
    suspend fun deleteById(
        recordId: Int
    ): Int


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
