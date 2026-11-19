package com.example.edmotor.data.remote

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

internal interface SupabaseAppointmentService {
    @GET("rest/v1/edmotor_appointments?select=id,client_auth_id,client_name,vehicle_brand,vehicle_model,vehicle_year,scheduled_at_ms,service_type,status,created_at_ms,updated_at_ms&order=id.asc")
    suspend fun getAppointments(
        @Header("Authorization") bearer: String,
        @Query("client_auth_id") clientFilter: String?,
        @Query("id") afterId: String?,
        @Query("limit") limit: Int
    ): List<SupabaseAppointmentDto>

    @POST("rest/v1/rpc/edmotor_create_appointment")
    suspend fun createAppointment(
        @Header("Authorization") bearer: String,
        @Body request: SupabaseAppointmentRequest
    ): JsonElement

    @POST("rest/v1/rpc/edmotor_accept_appointment")
    suspend fun acceptAppointment(
        @Header("Authorization") bearer: String,
        @Body request: SupabaseAppointmentReviewRequest
    ): Boolean

    @POST("rest/v1/rpc/edmotor_reject_appointment")
    suspend fun rejectAppointment(
        @Header("Authorization") bearer: String,
        @Body request: SupabaseAppointmentReviewRequest
    ): Boolean
}

internal data class SupabaseAppointmentDto(
    val id: Long?,
    @SerializedName("client_auth_id") val clientAuthId: String?,
    @SerializedName("client_name") val clientName: String?,
    @SerializedName("vehicle_brand") val vehicleBrand: String?,
    @SerializedName("vehicle_model") val vehicleModel: String?,
    @SerializedName("vehicle_year") val vehicleYear: Int?,
    @SerializedName("scheduled_at_ms") val scheduledAtMillis: Long?,
    @SerializedName("service_type") val serviceType: String?,
    val status: String?,
    @SerializedName("created_at_ms") val createdAtMillis: Long?,
    @SerializedName("updated_at_ms") val updatedAtMillis: Long?
)

internal data class SupabaseAppointmentRequest(
    @SerializedName("p_vehicle_brand") val vehicleBrand: String,
    @SerializedName("p_vehicle_model") val vehicleModel: String,
    @SerializedName("p_vehicle_year") val vehicleYear: Int,
    @SerializedName("p_scheduled_at_ms") val scheduledAtMillis: Long,
    @SerializedName("p_service_type") val serviceType: String
)

internal data class SupabaseAppointmentReviewRequest(@SerializedName("p_appointment_id") val appointmentId: Long)
