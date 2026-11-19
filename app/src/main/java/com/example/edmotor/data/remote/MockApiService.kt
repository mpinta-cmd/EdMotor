package com.example.edmotor.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MockApiService {
    @GET("profiles")
    suspend fun getProfiles(): List<MockProfileDto>

    @GET("appointments")
    suspend fun getAppointments(@Query("profileId") profileId: String? = null): List<MockAppointmentDto>

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
