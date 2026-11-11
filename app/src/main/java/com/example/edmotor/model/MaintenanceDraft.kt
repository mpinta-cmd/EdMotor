package com.example.edmotor.model

data class MaintenancePartDraft(
    val catalogProductId: Int? = null,
    val name: String,
    val brand: String = "",
    val reference: String = "",
    val specification: String = "",
    val quantity: Double = 1.0,
    val unit: String = "unidad",
    val unitPriceCop: Long = 0L
)

data class MaintenanceDraft(
    val serviceType: String,
    val serviceDate: String,
    val mileageKm: Int,
    val description: String,
    val laborCostCop: Long,
    val nextServiceMileageKm: Int? = null,
    val nextServiceDate: String? = null,
    val observations: String = "",
    val parts: List<MaintenancePartDraft> = emptyList()
)
