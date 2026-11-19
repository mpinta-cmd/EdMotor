package com.example.edmotor.data.remote

import com.example.edmotor.model.AppointmentStatus
import com.example.edmotor.model.isValidAppointmentStart
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    @GET("appointments/{id}")
    suspend fun getAppointment(@Path("id") id: String): MockAppointmentDto

    @POST("appointments")
    suspend fun createAppointment(@Body body: MockAppointmentCreateDto): MockAppointmentDto

    @PUT("appointments/{id}")
    suspend fun updateAppointment(
        @Path("id") id: String,
        @Body body: MockAppointmentStatusDto
    ): MockAppointmentDto

    @DELETE("appointments/{id}")
    suspend fun deleteAppointment(@Path("id") id: String)
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
    val serviceType: String?,
    val vehicleBrand: String?,
    val vehicleModel: String?,
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

data class MockAppointmentStatusDto(
    val status: String?,
    val updatedAtMillis: Long,
    val scheduledAtMillis: Long? = null
)

internal suspend fun MockApiService.listAppointments(
    profileId: String? = null,
    requireComplete: Boolean = false,
    includeCancelled: Boolean = false
): List<MockAppointmentDto> {
    val rows = try {
        getAppointments(profileId)
    } catch (error: HttpException) {
        error.response()?.errorBody()?.close()
        if (profileId == null || error.code() != 404) throw error

        // MockAPI devuelve 404 tanto para un filtro sin coincidencias como para un recurso ausente.
        try {
            probeAppointments()
        } catch (probeError: HttpException) {
            probeError.response()?.errorBody()?.close()
            throw probeError
        }
        return emptyList()
    }

    return rows.mapNotNull { element ->
        val appointment = element.toMockAppointment()
        if (requireComplete && appointment == null) {
            val owner = element.appointmentOwner()
            check(profileId != null && owner != null && owner != profileId)
        }
        appointment
    }
        .groupBy { it.id }
        .values
        .mapNotNull { matches ->
            val appointment = matches.distinct().singleOrNull()
            if (requireComplete && (profileId == null || matches.any { it.profileId == profileId })) {
                checkNotNull(appointment)
            }
            appointment
        }
        .filter { profileId == null || it.profileId == profileId }
        .filter { includeCancelled || it.status != AppointmentStatus.CANCELLED }
}

internal sealed interface MockAppointmentAvailability {
    data object Available : MockAppointmentAvailability
    data object HasActive : MockAppointmentAvailability
    data class PreviouslySubmitted(val appointment: MockAppointmentDto) : MockAppointmentAvailability
}

internal suspend fun MockApiService.checkAppointmentAvailability(
    profileId: String,
    previousRequest: MockAppointmentCreateDto? = null
): MockAppointmentAvailability {
    require(appointmentProfileId.matches(profileId))
    require(previousRequest == null || previousRequest.profileId == profileId)
    val appointments = listAppointments(profileId, requireComplete = true, includeCancelled = previousRequest != null)
    val recovered = previousRequest?.let(appointments::findSubmission)
    return when {
        recovered != null -> MockAppointmentAvailability.PreviouslySubmitted(recovered)
        appointments.any { AppointmentStatus.isActive(it.status) } -> MockAppointmentAvailability.HasActive
        else -> MockAppointmentAvailability.Available
    }
}

internal suspend fun MockApiService.findSubmittedAppointment(request: MockAppointmentCreateDto): MockAppointmentDto? {
    return listAppointments(request.profileId, includeCancelled = true).findSubmission(request)
}

private fun List<MockAppointmentDto>.findSubmission(request: MockAppointmentCreateDto): MockAppointmentDto? {
    val matches = filter { it.matchesSubmission(request) }
    check(matches.size <= 1)
    return matches.singleOrNull()
}

internal suspend fun MockApiService.submitAppointment(request: MockAppointmentCreateDto): MockAppointmentDto =
    reconcileMockAppointmentWrite(
        write = {
            createAppointment(request).also {
                check(it.status == AppointmentStatus.PENDING && it.matchesSubmission(request))
            }
        },
        readBack = { findSubmittedAppointment(request) }
    )

internal suspend fun MockApiService.submitAppointmentStatus(
    target: MockAppointmentDto,
    request: MockAppointmentStatusDto
): MockAppointmentDto = reconcileMockAppointmentWrite(
    write = {
        updateAppointment(target.id, request).also { check(it.matchesUpdate(target, request)) }
    },
    readBack = { getAppointment(target.id).takeIf { it.matchesUpdate(target, request) } }
)

internal enum class MockAppointmentCancellation { REMOVED, CHANGED, NOT_ACTIVE }

internal suspend fun MockApiService.cancelAppointmentIfMatches(
    id: String,
    expectedProfileId: String,
    expectedCreatedAtMillis: Long,
    beforeDelete: () -> Unit = {}
): MockAppointmentCancellation {
    require(mockAppointmentId(id) != null && appointmentProfileId.matches(expectedProfileId) && expectedCreatedAtMillis > 0)
    val target = getAppointmentForCancellation(id) ?: return MockAppointmentCancellation.REMOVED
    if (target.id != id || target.profileId != expectedProfileId || target.createdAtMillis != expectedCreatedAtMillis) {
        return MockAppointmentCancellation.CHANGED
    }
    if (!AppointmentStatus.isActive(target.status)) return MockAppointmentCancellation.NOT_ACTIVE

    beforeDelete()
    try {
        deleteAppointment(id)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (error is HttpException) {
            error.response()?.errorBody()?.close()
            if (error.code() in setOf(400, 422, 429)) throw error
        }
        val removed = try {
            getAppointmentForCancellation(id) == null
        } catch (readError: CancellationException) {
            throw readError
        } catch (_: Exception) {
            false
        }
        if (!removed) throw error
    }
    return MockAppointmentCancellation.REMOVED
}

private suspend fun MockApiService.getAppointmentForCancellation(id: String): MockAppointmentDto? = try {
    getAppointment(id)
} catch (error: HttpException) {
    error.response()?.errorBody()?.close()
    if (error.code() != 404) throw error

    // Si falta el recurso completo, el 404 no confirma que se borró esa cita.
    probeAppointments()
    null
}

private suspend fun reconcileMockAppointmentWrite(
    write: suspend () -> MockAppointmentDto,
    readBack: suspend () -> MockAppointmentDto?
): MockAppointmentDto = try {
    write()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    if (error is HttpException) {
        error.response()?.errorBody()?.close()
        if (isDefinitiveMockAppointmentFailure(error.code())) throw error
    }

    val recovered = try {
        readBack()
    } catch (readError: CancellationException) {
        throw readError
    } catch (_: Exception) {
        null
    }
    recovered ?: throw error
}

private fun MockAppointmentDto.matchesSubmission(request: MockAppointmentCreateDto): Boolean =
    mockAppointmentId(id) != null && profileId == request.profileId &&
        scheduledAtMillis == request.scheduledAtMillis && createdAtMillis == request.createdAtMillis &&
        serviceType == request.serviceType && status in appointmentStatuses

private fun MockAppointmentDto.matchesUpdate(target: MockAppointmentDto, request: MockAppointmentStatusDto): Boolean =
    id == target.id && profileId == target.profileId && createdAtMillis == target.createdAtMillis &&
        scheduledAtMillis == (request.scheduledAtMillis ?: target.scheduledAtMillis) &&
        status == (request.status ?: target.status) &&
        (request.status != AppointmentStatus.COMPLETED || updatedAtMillis == request.updatedAtMillis)

private val appointmentJson = Gson()
private val appointmentProfileId = Regex("[A-Za-z0-9_-]{1,64}")
private val appointmentStatuses = setOf(
    AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED, AppointmentStatus.REJECTED,
    AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED
)

private fun JsonElement.appointmentOwner(): String? {
    val profile = takeIf { it.isJsonObject }?.asJsonObject?.get("profileId")
    return profile?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isBoolean }
        ?.asString?.takeIf(appointmentProfileId::matches)
}

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

internal fun isDefinitiveMockAppointmentFailure(status: Int): Boolean = status in setOf(400, 404, 422, 429)

internal fun mockAppointmentFailureMessage(status: Int, fallback: String): String = when (status) {
    400, 422 -> "MockAPI rechazó los datos de la cita (HTTP $status). Revisa los campos del recurso appointments."
    404 -> "MockAPI no encontró la cita o el recurso appointments (HTTP 404). Revisa la URL del proyecto."
    429 -> "MockAPI alcanzó su límite de solicitudes (HTTP 429). Intenta de nuevo más tarde."
    else -> fallback
}
