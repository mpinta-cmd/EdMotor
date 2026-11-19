package com.example.edmotor.data.remote

import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.isValidAppointmentStart
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MockApiService {
    @GET("profiles")
    suspend fun getProfiles(): List<MockProfileDto>

    @POST("profiles")
    suspend fun createProfile(@Body body: MockProfileCreateDto): MockProfileDto

    @GET("appointments")
    suspend fun getAppointments(@Query("profileId") profileId: String? = null): JsonArray

    @GET("appointments?limit=1&page=1")
    suspend fun probeAppointments(): JsonArray

    @GET("appointments")
    suspend fun getAppointmentsForReview(): List<MockAppointmentDto>

    @GET("appointments/{id}")
    suspend fun getAppointment(@Path("id") id: String): MockAppointmentDto

    @POST("appointments")
    suspend fun createAppointment(@Body body: MockAppointmentCreateDto): MockAppointmentDto

    @PUT("appointments/{id}")
    suspend fun updateAppointment(
        @Path("id") id: String,
        @Body body: MockAppointmentStatusDto
    ): MockAppointmentDto
}

data class MockProfileDto(
    val id: String,
    val displayName: String,
    val role: String
)

data class MockProfileCreateDto(val displayName: String) {
    val role = "CLIENT"
}

data class MockAppointmentDto(
    val id: String,
    val profileId: String,
    val serviceType: String,
    val vehicleBrand: String,
    val vehicleModel: String,
    val vehicleYear: Int,
    val scheduledAtMillis: Long,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class MockAppointmentCreateDto(
    val profileId: String,
    val serviceType: String,
    val vehicleBrand: String,
    val vehicleModel: String,
    val vehicleYear: Int,
    val scheduledAtMillis: Long,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class MockAppointmentStatusDto(val status: String, val updatedAtMillis: Long)

internal suspend fun MockApiService.listAppointments(profileId: String? = null): List<MockAppointmentDto> {
    val rows = try {
        getAppointments(profileId)
    } catch (error: HttpException) {
        error.response()?.errorBody()?.close()
        if (profileId == null || error.code() != 404) throw error

        // MockAPI devuelve 404 tanto para un filtro sin coincidencias como para un recurso ausente.
        probeAppointments()
        return emptyList()
    }

    return rows.mapNotNull(JsonElement::toMockAppointment)
        .groupBy { it.id }
        .values
        .mapNotNull { matches -> matches.distinct().singleOrNull() }
        .filter { profileId == null || it.profileId == profileId }
}

private val appointmentJson = Gson()
private val appointmentProfileId = Regex("[A-Za-z0-9_-]{1,64}")
private val appointmentStatuses = setOf(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED, AppointmentStatus.REJECTED)

private fun JsonElement.toMockAppointment(): MockAppointmentDto? {
    if (!isJsonObject) return null
    val appointment = try {
        appointmentJson.fromJson(this, MockAppointmentDto::class.java)
    } catch (_: JsonParseException) {
        return null
    }
    if (mockAppointmentId(appointment.id) == null || !appointmentProfileId.matches(appointment.profileId.orEmpty()) ||
        appointment.status !in appointmentStatuses || !isValidAppointmentStart(appointment.scheduledAtMillis)
    ) return null
    return appointment
}

internal fun mockAppointmentId(value: String?): Long? =
    value?.toLongOrNull()?.takeIf { it > 0L && it.toString() == value }

internal fun mockAppointmentFailureMessage(status: Int, fallback: String): String = when (status) {
    400, 422 -> "MockAPI rechazó los datos de la cita (HTTP $status). Revisa los campos del recurso appointments."
    404 -> "MockAPI no encontró la cita o el recurso appointments (HTTP 404). Revisa la URL del proyecto."
    429 -> "MockAPI alcanzó su límite de solicitudes (HTTP 429). Intenta de nuevo más tarde."
    else -> fallback
}
