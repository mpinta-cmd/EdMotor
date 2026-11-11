package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MaintenancePartDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(parts: List<MaintenancePartEntity>)

    @Query("DELETE FROM maintenance_parts WHERE maintenanceRecordId = :recordId")
    suspend fun deleteForRecord(recordId: Int)
}
