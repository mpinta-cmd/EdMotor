package com.example.edmotor.data.remote

import com.google.gson.Gson
import retrofit2.HttpException

class SupabaseAppointmentAccess internal constructor(
    private val connection: SupabaseConnection,
    private val accessToken: suspend (String, String?) -> String,
    private val service: SupabaseAppointmentService
) {
    constructor(
        connection: SupabaseConnection,
        accessToken: suspend (String, String?) -> String
    ) : this(connection, accessToken, createSupabaseAppointmentService(connection.baseUrl, connection.publishableKey))

    internal fun profileKey(profileId: String): String = connection.profileKey(profileId)

    internal fun profileId(profileKey: String): String? = connection.profileId(profileKey)

    internal suspend fun getAppointments(
        profileKey: String,
        clientId: String?
    ): List<SupabaseAppointmentDto> {
        require(clientId == null || profileId(profileKey) == clientId)
        val appointments = mutableListOf<SupabaseAppointmentDto>()
        var lastId = 0L
        while (true) {
            val after = lastId.takeIf { it > 0L }?.let { "gt.$it" }
            val page = authenticated(profileKey) { bearer ->
                service.getAppointments(bearer, clientId?.let { "eq.$it" }, after, PAGE_SIZE)
            }
            if (page.isEmpty()) return appointments

            for (appointment in page) {
                val id = requireNotNull(appointment.id)
                check(id > lastId)
                lastId = id
                appointments += appointment
            }
        }
    }

    internal suspend fun createAppointment(
        profileKey: String,
        request: SupabaseAppointmentRequest
    ): SupabaseAppointmentDto {
        val response = authenticated(profileKey) { service.createAppointment(it, request) }
        val row = if (response.isJsonArray) response.asJsonArray.single() else response
        require(row.isJsonObject)
        return gson.fromJson(row, SupabaseAppointmentDto::class.java)
    }

    internal suspend fun reviewAppointment(profileKey: String, appointmentId: Long, accept: Boolean) {
        val request = SupabaseAppointmentReviewRequest(appointmentId)
        val result = authenticated(profileKey) {
            if (accept) service.acceptAppointment(it, request) else service.rejectAppointment(it, request)
        }
        check(result)
    }

    private suspend fun <T> authenticated(profileKey: String, request: suspend (String) -> T): T {
        val token = accessToken(profileKey, null)
        return try {
            request("Bearer $token")
        } catch (error: HttpException) {
            error.response()?.errorBody()?.close()
            if (error.code() != 401) throw error
            val renewed = accessToken(profileKey, token)
            request("Bearer $renewed")
        }
    }

    private companion object {
        const val PAGE_SIZE = 200
        val gson = Gson()
    }
}

internal fun createSupabaseAppointmentService(baseUrl: String, publishableKey: String): SupabaseAppointmentService =
    createSupabaseRetrofit(baseUrl, publishableKey)
        .create(SupabaseAppointmentService::class.java)
