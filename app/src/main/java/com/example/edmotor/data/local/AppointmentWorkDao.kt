package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentWorkDao {
    @Query("SELECT * FROM appointment_work WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): AppointmentWorkEntity?

    @Query(
        """
        SELECT * FROM appointment_work
        WHERE clientAuthId = :clientAuthId
          AND appointmentId = :appointmentId
          AND appointmentCreatedAtMillis = :appointmentCreatedAtMillis
        LIMIT 1
        """
    )
    suspend fun findForAppointment(
        clientAuthId: String,
        appointmentId: Long,
        appointmentCreatedAtMillis: Long
    ): AppointmentWorkEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(work: AppointmentWorkEntity): Long

    @Update
    suspend fun update(work: AppointmentWorkEntity): Int

    @Query(
        """
        DELETE FROM appointment_work
        WHERE clientAuthId = :clientAuthId
          AND appointmentId = :appointmentId
          AND appointmentCreatedAtMillis = :appointmentCreatedAtMillis
          AND finishedAtMillis IS NULL AND maintenanceRecordId IS NULL
        """
    )
    suspend fun deleteUnfinishedForAppointment(clientAuthId: String, appointmentId: Long, appointmentCreatedAtMillis: Long)

    @Transaction
    @Query(
        """
        SELECT work.* FROM appointment_work AS work
        WHERE EXISTS (
            SELECT 1 FROM users AS viewer
            WHERE viewer.id = :userId AND viewer.role = :role
              AND (viewer.role = 'WORKSHOP'
                   OR (viewer.role = 'CLIENT' AND viewer.remoteAuthId = work.clientAuthId))
        )
        ORDER BY work.id DESC
        """
    )
    fun observeForViewer(userId: Int, role: String): Flow<List<AppointmentWorkWithMaintenance>>
}
