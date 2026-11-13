package com.example.edmotor.data.repository

import android.util.Log
import com.example.edmotor.data.remote.SupabaseClient
import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentOperationResult
import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

class AppointmentRepository(
    private val supabase: SupabaseClient,
    private val sessionManager: SessionManager
) {
    fun observeForCurrentUser(isWorkshop: Boolean): Flow<List<Appointment>> = flow {
        var lastKnownAppointments = emptyList<Appointment>()
        while (currentCoroutineContext().isActive) {
            try {
                val remote = sessionManager.remoteSession
                if (remote == null) {
                    emit(emptyList())
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                val clientFilter = if (isWorkshop) "" else "&client_auth_id=eq.${remote.userId}"
                val raw = supabase.get(
                    "/rest/v1/appointments?select=*&order=scheduled_at_ms.asc$clientFilter"
                )
                lastKnownAppointments = parseAppointments(raw)
                emit(lastKnownAppointments)
            } catch (e: Exception) {
                Log.w("EdMotorAppointments", "No fue posible sincronizar citas: ${e.message}")
                emit(lastKnownAppointments)
            }

            withTimeoutOrNull(POLL_INTERVAL_MS) {
                supabase.realtimeChanges.first()
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun createAppointment(
        client: UserEntity,
        vehicle: Vehicle,
        scheduledAtMillis: Long,
        description: String
    ): AppointmentOperationResult {
        if (
            client.role != UserRole.CLIENT.name ||
            client.remoteAuthId.isNullOrBlank() ||
            sessionManager.remoteSession?.userId != client.remoteAuthId
        ) {
            return AppointmentOperationResult.Error(
                "Solo un cliente con sesión remota activa puede solicitar una cita."
            )
        }
        if (scheduledAtMillis < System.currentTimeMillis() + 15 * 60 * 1000L) {
            return AppointmentOperationResult.Error(
                "Selecciona una fecha y hora con al menos 15 minutos de anticipación."
            )
        }
        if (description.trim().length < 8) {
            return AppointmentOperationResult.Error(
                "Describe un poco más el problema del vehículo."
            )
        }
        if (vehicle.id <= 0) {
            return AppointmentOperationResult.Error("Selecciona un vehículo registrado.")
        }

        return try {
            val body = JSONObject()
                .put("client_auth_id", client.remoteAuthId)
                .put("client_name", client.displayName)
                .put("client_username", client.username)
                .put("client_phone", client.phone ?: JSONObject.NULL)
                .put("vehicle_brand", vehicle.brand)
                .put("vehicle_model", vehicle.model)
                .put("vehicle_year", vehicle.year)
                .put("vehicle_plate", vehicle.plate)
                .put("scheduled_at_ms", scheduledAtMillis)
                .put("description", description.trim())
                .put("status", AppointmentStatus.PENDING)
                .put("created_at_ms", System.currentTimeMillis())
                .put("updated_at_ms", System.currentTimeMillis())

            supabase.post(
                "/rest/v1/appointments",
                body,
                prefer = "return=representation"
            )

            AppointmentOperationResult.Success(
                "Solicitud enviada. EdMotor revisará la fecha y la hora."
            )
        } catch (e: Exception) {
            AppointmentOperationResult.Error(
                e.message ?: "No fue posible enviar la solicitud de cita."
            )
        }
    }

    suspend fun acceptAppointment(
        workshop: UserEntity,
        appointmentId: Long
    ): AppointmentOperationResult {
        if (workshop.role != UserRole.WORKSHOP.name) {
            return AppointmentOperationResult.Error("Solo EdMotor puede confirmar citas.")
        }
        return try {
            supabase.rpc(
                "accept_appointment",
                JSONObject().put("p_appointment_id", appointmentId)
            )
            AppointmentOperationResult.Success("Cita #$appointmentId confirmada.")
        } catch (e: Exception) {
            AppointmentOperationResult.Error(
                e.message ?: "No fue posible confirmar la cita."
            )
        }
    }

    suspend fun rejectAppointment(
        workshop: UserEntity,
        appointmentId: Long
    ): AppointmentOperationResult {
        if (workshop.role != UserRole.WORKSHOP.name) {
            return AppointmentOperationResult.Error("Solo EdMotor puede rechazar citas.")
        }
        return try {
            supabase.rpc(
                "reject_appointment",
                JSONObject().put("p_appointment_id", appointmentId)
            )
            AppointmentOperationResult.Success("Solicitud de cita #$appointmentId rechazada.")
        } catch (e: Exception) {
            AppointmentOperationResult.Error(
                e.message ?: "No fue posible rechazar la cita."
            )
        }
    }

    private fun parseAppointments(raw: String): List<Appointment> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val row = array.getJSONObject(index)
                add(
                    Appointment(
                        id = row.getLong("id"),
                        clientAuthId = row.optString("client_auth_id"),
                        clientName = row.optString("client_name"),
                        clientUsername = row.optString("client_username"),
                        clientPhone = row.optString("client_phone").takeIf {
                            it.isNotBlank() && it != "null"
                        },
                        vehicleBrand = row.optString("vehicle_brand"),
                        vehicleModel = row.optString("vehicle_model"),
                        vehicleYear = row.optInt("vehicle_year"),
                        vehiclePlate = row.optString("vehicle_plate"),
                        scheduledAtMillis = row.optLong("scheduled_at_ms"),
                        description = row.optString("description"),
                        status = row.optString("status", AppointmentStatus.PENDING),
                        createdAtMillis = row.optLong("created_at_ms"),
                        updatedAtMillis = row.optLong("updated_at_ms")
                    )
                )
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
