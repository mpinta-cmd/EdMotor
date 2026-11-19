
package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {

    // CONSULTAR TODAS LAS CITAS
    @Query(
        """
        SELECT * FROM appointments
        ORDER BY scheduledAtMillis ASC
        """
    )
    fun observeAll(): Flow<List<AppointmentEntity>>

    // CONSULTAR CITAS DE UN CLIENTE
    @Query(
        """
        SELECT * FROM appointments
        WHERE clientLocalUserId = :clientId
        ORDER BY scheduledAtMillis ASC
        """
    )
    fun observeForClient(
        clientId: Int
    ): Flow<List<AppointmentEntity>>

    // CONSULTAR CITAS POR ESTADO
    @Query(
        """
        SELECT * FROM appointments
        WHERE status = :status
        ORDER BY scheduledAtMillis ASC
        """
    )
    fun observeByStatus(
        status: String
    ): Flow<List<AppointmentEntity>>

    // CONSULTAR UNA CITA
    @Query(
        """
        SELECT * FROM appointments
        WHERE id = :appointmentId
        LIMIT 1
        """
    )
    suspend fun findById(
        appointmentId: Int
    ): AppointmentEntity?

    // INSERTAR CITA
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(
        appointment: AppointmentEntity
    ): Long

    // INSERTAR VARIAS CITAS
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(
        appointments: List<AppointmentEntity>
    ): List<Long>

    // ACTUALIZAR CITA
    @Update
    suspend fun update(
        appointment: AppointmentEntity
    ): Int

    // ELIMINAR CITA
    @Delete
    suspend fun delete(
        appointment: AppointmentEntity
    ): Int

    // ELIMINAR POR IDENTIFICADOR
    @Query(
        """
        DELETE FROM appointments
        WHERE id = :appointmentId
        """
    )
    suspend fun deleteById(
        appointmentId: Int
    ): Int

    // CONTAR CITAS
    @Query("SELECT COUNT(*) FROM appointments")
    fun observeCount(): Flow<Int>

    // CONTAR CITAS PENDIENTES
    @Query(
        """
        SELECT COUNT(*) FROM appointments
        WHERE status = :status
        """
    )
    fun observeCountByStatus(
        status: String
    ): Flow<Int>

    // CONSULTAR PRÓXIMAS CITAS
    @Query(
        """
        SELECT * FROM appointments
        WHERE scheduledAtMillis >= :currentTime
        ORDER BY scheduledAtMillis ASC
        """
    )
    fun observeUpcoming(
        currentTime: Long
    ): Flow<List<AppointmentEntity>>

    // ELIMINAR TODAS LAS CITAS
    @Query("DELETE FROM appointments")
    suspend fun deleteAll()
}
