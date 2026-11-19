package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AppointmentDao {
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
