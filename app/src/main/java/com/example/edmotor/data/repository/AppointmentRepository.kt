package com.example.edmotor.data.repository

import android.util.Log
import com.example.edmotor.data.local.AppointmentCacheEntity
import com.example.edmotor.data.local.AppointmentDao
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.data.remote.SupabaseClient
import com.example.edmotor.data.remote.SupabaseConfig
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentOperationResult
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle
import com.example.edmotor.session.SessionManager
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

internal data class AppointmentAccount(val authId: String, val role: UserRole)

internal fun UserEntity?.appointmentAccount(): AppointmentAccount? {
    val user = this ?: return null
    val authId = user.remoteAuthId?.let(::normalizeAuthId) ?: return null
    val role = UserRole.entries.firstOrNull { it.name == user.role } ?: return null
    return AppointmentAccount(authId, role)
}

internal data class AppointmentSnapshot(
    val appointments: List<Appointment>,
    val syncState: AppointmentSyncState
)

class AppointmentRepository(
    private val appointmentDao: AppointmentDao,
    private val supabase: SupabaseClient,
    private val sessionManager: SessionManager
) {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    internal fun observeAppointments(account: AppointmentAccount): Flow<AppointmentSnapshot> =
        combine(
            appointmentDao.observeCachedAppointments(account.authId, account.role.name),
            observeSync(account)
        ) { rows, syncState ->
            AppointmentSnapshot(rows.map { it.toModel() }, syncState)
        }.retryWhen { _, _ ->
            emit(AppointmentSnapshot(emptyList(), AppointmentSyncState.UNAVAILABLE))
            delay(SupabaseConfig.REALTIME_POLL_FALLBACK_MS)
            true
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    internal fun refreshAppointments() {
        refreshRequests.tryEmit(Unit)
    }

    private fun observeSync(account: AppointmentAccount): Flow<AppointmentSyncState> = channelFlow {
        val requests = Channel<Unit>(Channel.CONFLATED)
        launch {
            merge(supabase.realtimeChanges, refreshRequests).collect { requests.trySend(Unit) }
        }

        send(AppointmentSyncState.LOADING)
        while (isActive) {
            send(refreshCache(account))
            withTimeoutOrNull(SupabaseConfig.REALTIME_POLL_FALLBACK_MS) { requests.receive() }
        }
    }.distinctUntilChanged()

    private suspend fun refreshCache(account: AppointmentAccount): AppointmentSyncState {
        return try {
            val snapshot = fetchSnapshot(account)
            currentCoroutineContext().ensureActive()
            requireActiveAccount(account)
            appointmentDao.replaceCachedAppointments(account.authId, account.role.name, snapshot)
            AppointmentSyncState.UP_TO_DATE
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Log.w("EdMotorAppointments", "No fue posible actualizar las citas.")
            AppointmentSyncState.UNAVAILABLE
        }
    }

    private suspend fun fetchSnapshot(account: AppointmentAccount): List<AppointmentCacheEntity> {
        val rows = mutableListOf<AppointmentCacheEntity>()
        val clientFilter = if (account.role == UserRole.CLIENT) {
            "&client_auth_id=eq.${account.authId}"
        } else {
            ""
        }
        var beforeId: Long? = null

        // Consultar por ID descendente evita que las citas nuevas alarguen el recorrido.
        // La última página es la vacía, incluso si el servidor limita el tamaño de las anteriores.
        while (true) {
            requireActiveAccount(account)
            val cursor = beforeId?.let { "&id=lt.$it" }.orEmpty()
            val result = supabase.get(
                "/rest/v1/appointments?select=$APPOINTMENT_COLUMNS&order=id.desc&limit=$PAGE_SIZE$clientFilter$cursor",
                expectedUserId = account.authId
            )
            currentCoroutineContext().ensureActive()
            requireActiveAccount(account)

            val page = JSONArray(result)
            if (page.length() == 0) break

            for (index in 0 until page.length()) {
                val appointment = page.getJSONObject(index).toCacheEntity(account)
                val previousId = beforeId
                check(previousId == null || appointment.id < previousId) {
                    "El servidor no devolvió las citas en el orden esperado."
                }
                beforeId = appointment.id
                rows.add(appointment)
            }
        }

        return rows.sortedWith(compareBy(AppointmentCacheEntity::scheduledAtMillis, AppointmentCacheEntity::id))
    }

    suspend fun createAppointment(
        client: UserEntity,
        vehicle: Vehicle,
        scheduledAtMillis: Long,
        description: String
    ): AppointmentOperationResult {
        val account = client.appointmentAccount()
        if (account == null || account.role != UserRole.CLIENT) {
            return AppointmentOperationResult.Error("Solo un cliente con sesión remota activa puede solicitar una cita.")
        }
        if (scheduledAtMillis < System.currentTimeMillis() + 15 * 60 * 1000L) {
            return AppointmentOperationResult.Error("Selecciona una fecha y hora con al menos 15 minutos de anticipación.")
        }
        val cleanDescription = description.trim()
        if (cleanDescription.length !in 8..600) {
            return AppointmentOperationResult.Error("La descripción debe tener entre 8 y 600 caracteres.")
        }
        if (vehicle.id <= 0 || vehicle.ownerUserId != client.id) {
            return AppointmentOperationResult.Error("Selecciona un vehículo registrado en tu garaje.")
        }

        return performRemoteOperation(
            account = account,
            success = "Solicitud enviada. EdMotor revisará la fecha y la hora.",
            fallback = "No fue posible enviar la solicitud de cita."
        ) {
            val body = JSONObject()
                .put("client_auth_id", account.authId)
                .put("client_name", client.displayName)
                .put("client_username", client.username)
                .put("client_phone", client.phone ?: JSONObject.NULL)
                .put("vehicle_brand", vehicle.brand)
                .put("vehicle_model", vehicle.model)
                .put("vehicle_year", vehicle.year)
                .put("vehicle_plate", vehicle.plate)
                .put("scheduled_at_ms", scheduledAtMillis)
                .put("description", cleanDescription)
                .put("status", AppointmentStatus.PENDING)

            supabase.post("/rest/v1/appointments", body, expectedUserId = account.authId)
        }
    }

    suspend fun acceptAppointment(workshop: UserEntity, appointmentId: Long): AppointmentOperationResult =
        reviewAppointment(
            workshop,
            appointmentId,
            function = "accept_appointment",
            success = "Cita #$appointmentId confirmada.",
            fallback = "No fue posible confirmar la cita."
        )

    suspend fun rejectAppointment(workshop: UserEntity, appointmentId: Long): AppointmentOperationResult =
        reviewAppointment(
            workshop,
            appointmentId,
            function = "reject_appointment",
            success = "Solicitud de cita #$appointmentId rechazada.",
            fallback = "No fue posible rechazar la cita."
        )

    private suspend fun reviewAppointment(
        workshop: UserEntity,
        appointmentId: Long,
        function: String,
        success: String,
        fallback: String
    ): AppointmentOperationResult {
        val account = workshop.appointmentAccount()
        if (account == null || account.role != UserRole.WORKSHOP) {
            return AppointmentOperationResult.Error("Solo EdMotor puede administrar las citas.")
        }
        if (appointmentId <= 0L) return AppointmentOperationResult.Error("La cita no es válida.")

        return performRemoteOperation(account, success, fallback) {
            supabase.rpc(function, JSONObject().put("p_appointment_id", appointmentId), expectedUserId = account.authId)
        }
    }

    private suspend fun performRemoteOperation(
        account: AppointmentAccount,
        success: String,
        fallback: String,
        request: suspend () -> Unit
    ): AppointmentOperationResult {
        return try {
            requireActiveAccount(account)
            request()
            refreshAppointments()
            AppointmentOperationResult.Success(success)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppointmentOperationResult.Error(e.message ?: fallback)
        }
    }

    private fun requireActiveAccount(account: AppointmentAccount) {
        check(
            sessionManager.currentUser.value.appointmentAccount() == account &&
                sessionManager.remoteSession?.userId.equals(account.authId, ignoreCase = true)
        ) { "No hay una sesión remota activa para esta cuenta." }
    }

    private fun JSONObject.toCacheEntity(account: AppointmentAccount): AppointmentCacheEntity {
        val clientAuthId = requireNotNull(normalizeAuthId(getString("client_auth_id")))
        check(account.role == UserRole.WORKSHOP || clientAuthId == account.authId)

        return AppointmentCacheEntity(
            accountAuthId = account.authId,
            accountRole = account.role.name,
            id = getLong("id"),
            clientAuthId = clientAuthId,
            clientName = getString("client_name"),
            clientUsername = getString("client_username"),
            clientPhone = if (isNull("client_phone")) null else getString("client_phone").takeIf { it.isNotBlank() },
            vehicleBrand = getString("vehicle_brand"),
            vehicleModel = getString("vehicle_model"),
            vehicleYear = getInt("vehicle_year"),
            vehiclePlate = getString("vehicle_plate"),
            scheduledAtMillis = getLong("scheduled_at_ms"),
            description = getString("description"),
            status = getString("status"),
            createdAtMillis = getLong("created_at_ms"),
            updatedAtMillis = getLong("updated_at_ms")
        )
    }

    private fun AppointmentCacheEntity.toModel() = Appointment(
        id = id,
        clientAuthId = clientAuthId,
        clientName = clientName,
        clientUsername = clientUsername,
        clientPhone = clientPhone,
        vehicleBrand = vehicleBrand,
        vehicleModel = vehicleModel,
        vehicleYear = vehicleYear,
        vehiclePlate = vehiclePlate,
        scheduledAtMillis = scheduledAtMillis,
        description = description,
        status = status,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis
    )

    private companion object {
        const val PAGE_SIZE = 200
        const val APPOINTMENT_COLUMNS = "id,client_auth_id,client_name,client_username,client_phone," +
            "vehicle_brand,vehicle_model,vehicle_year,vehicle_plate,scheduled_at_ms,description," +
            "status,created_at_ms,updated_at_ms"
    }
}

private fun normalizeAuthId(value: String): String? = try {
    UUID.fromString(value).toString().takeIf { it.equals(value, ignoreCase = true) }
} catch (_: IllegalArgumentException) {
    null
}
