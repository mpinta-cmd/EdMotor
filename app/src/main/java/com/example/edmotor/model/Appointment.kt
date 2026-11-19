package com.example.edmotor.model

object AppointmentStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val REJECTED = "REJECTED"
}

enum class AppointmentSyncState { NOT_CONFIGURED, LOADING, UP_TO_DATE, UNAVAILABLE }

object AppointmentServiceType {
    val options = listOf(
        "Revisión general",
        "Mantenimiento preventivo",
        "Diagnóstico",
        "Cambio de aceite",
        "Frenos",
        "Neumáticos"
    )
}

data class Appointment(
    val id: Long,
    val clientAuthId: String,
    val clientName: String,
    val clientUsername: String,
    val clientPhone: String?,
    val vehicleBrand: String,
    val vehicleModel: String,
    val vehicleYear: Int,
    val vehiclePlate: String,
    val scheduledAtMillis: Long,
    val description: String,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

sealed interface AppointmentOperationResult {
    data class Success(val message: String) : AppointmentOperationResult
    data class Error(val message: String) : AppointmentOperationResult
}
