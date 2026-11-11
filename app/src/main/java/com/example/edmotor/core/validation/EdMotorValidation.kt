
package com.example.edmotor.core.validation

import java.util.Calendar

object EdMotorValidation {

    fun validateVehicle(
        brand: String,
        model: String,
        year: Int
    ): String? {

        if (brand.trim().isBlank()) {
            return "La marca del vehículo es obligatoria."
        }

        if (model.trim().isBlank()) {
            return "El modelo del vehículo es obligatorio."
        }

        val currentYear =
            Calendar.getInstance().get(Calendar.YEAR) + 1

        if (year !in 1886..currentYear) {
            return "El año del vehículo no es válido."
        }

        return null
    }

    fun validateMaintenance(
        serviceType: String,
        serviceDate: String,
        mileageKm: Int,
        laborCostCop: Long,
        nextServiceMileageKm: Int?
    ): String? {

        if (serviceType.isBlank()) {
            return "Indica el tipo de mantenimiento."
        }

        if (serviceDate.isBlank()) {
            return "Indica la fecha del mantenimiento."
        }

        if (mileageKm < 0) {
            return "El kilometraje no puede ser negativo."
        }

        if (laborCostCop < 0) {
            return "El costo de mano de obra no puede ser negativo."
        }

        if (
            nextServiceMileageKm != null &&
            nextServiceMileageKm < 0
        ) {
            return "El próximo kilometraje no es válido."
        }

        return null
    }

    fun validateMaintenancePart(
        name: String,
        quantity: Double,
        unitPriceCop: Long
    ): String? {

        if (name.isBlank()) return null

        if (!quantity.isFinite() || quantity <= 0.0) {
            return "La cantidad del repuesto debe ser mayor que cero."
        }

        if (unitPriceCop < 0) {
            return "El precio del repuesto no puede ser negativo."
        }

        return null
    }
}
