package com.example.edmotor.navigation

object Routes {
    const val HOME = "Inicio"
    const val CAR = "Carrito"
    const val SERVICES = "Servicios"
    const val PROFILE = "Mi Garaje"
    const val DIAGNOSTIC = "Diagnóstico"

    const val PRODUCT_DETAIL = "detalleProducto/{productId}"
    fun productDetail(productId: Int) = "detalleProducto/$productId"

    const val MAINTENANCE_HISTORY = "historialMantenimiento/{vehicleId}"
    fun maintenanceHistory(vehicleId: Int) = "historialMantenimiento/$vehicleId"

    const val MAINTENANCE_FORM = "formularioMantenimiento/{vehicleId}?recordId={recordId}"
    fun maintenanceForm(vehicleId: Int, recordId: Int? = null): String =
        if (recordId == null) "formularioMantenimiento/$vehicleId"
        else "formularioMantenimiento/$vehicleId?recordId=$recordId"
}
