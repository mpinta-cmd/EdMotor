package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppointmentDao {
    @Query(
        """
        SELECT appointments.* FROM appointments
        WHERE EXISTS (
            SELECT 1 FROM users
            WHERE id = :viewerId AND remoteAuthId = :viewerKey AND role = :viewerRole
        )
        AND (:viewerRole = 'WORKSHOP' OR (clientLocalUserId = :viewerId AND clientAuthId = :viewerKey))
        ORDER BY scheduledAtMillis, id
        """
    )
    abstract fun observeLocalAppointments(viewerId: Int, viewerKey: String, viewerRole: String): Flow<List<AppointmentEntity>>

    @Insert
    abstract suspend fun insertLocalAppointment(appointment: AppointmentEntity): Long

    @Query("SELECT * FROM appointments WHERE id = :appointmentId LIMIT 1")
    abstract suspend fun findLocalAppointment(appointmentId: Long): AppointmentEntity?

    @Query(
        """
        SELECT EXISTS (
            SELECT 1 FROM appointments
            WHERE clientLocalUserId = :clientLocalUserId
              AND clientAuthId = :clientAuthId
              AND status IN ('PENDING', 'CONFIRMED')
        )
        """
    )
    abstract suspend fun hasActiveLocalAppointment(clientLocalUserId: Int, clientAuthId: String): Boolean

    @Query(
        """
        UPDATE appointments
        SET status = :status,
            scheduledAtMillis = COALESCE(:scheduledAtMillis, scheduledAtMillis),
            updatedAtMillis = :updatedAtMillis
        WHERE id = :appointmentId AND status = :previousStatus
        """
    )
    abstract suspend fun updateLocalAppointment(
        appointmentId: Long,
        previousStatus: String,
        status: String,
        scheduledAtMillis: Long?,
        updatedAtMillis: Long
    ): Int

    @Query(
        """
        SELECT * FROM appointment_cache
        WHERE accountAuthId = :accountAuthId AND accountRole = :accountRole
        ORDER BY scheduledAtMillis, id
        """
    )
    abstract fun observeCachedAppointments(
        accountAuthId: String,
        accountRole: String
    ): Flow<List<AppointmentCacheEntity>>

    @Upsert
    abstract suspend fun upsertCachedAppointment(appointment: AppointmentCacheEntity)

    @Query(
        """
        SELECT * FROM appointment_cache
        WHERE accountAuthId = :accountAuthId AND accountRole = :accountRole
        ORDER BY scheduledAtMillis, id
        """
    )
    protected abstract suspend fun loadCachedAppointments(
        accountAuthId: String,
        accountRole: String
    ): List<AppointmentCacheEntity>

    @Query(
        """
        DELETE FROM appointment_cache
        WHERE accountAuthId = :accountAuthId AND accountRole = :accountRole
        """
    )
    protected abstract suspend fun deleteCachedAppointments(accountAuthId: String, accountRole: String)

    @Insert
    protected abstract suspend fun insertCachedAppointments(appointments: List<AppointmentCacheEntity>)

    @Transaction
    open suspend fun replaceCachedAppointments(
        accountAuthId: String,
        accountRole: String,
        appointments: List<AppointmentCacheEntity>
    ) {
        require(appointments.all { it.accountAuthId == accountAuthId && it.accountRole == accountRole })
        if (appointments == loadCachedAppointments(accountAuthId, accountRole)) return

        deleteCachedAppointments(accountAuthId, accountRole)
        if (appointments.isNotEmpty()) insertCachedAppointments(appointments)
    }
}
