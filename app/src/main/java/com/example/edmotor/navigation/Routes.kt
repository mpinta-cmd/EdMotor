
package com.example.edmotor.navigation

object Routes {

    // ==========================================
    // NAVEGACIÓN PRINCIPAL
    // ==========================================

    const val HOME = "Inicio"
    const val CAR = "Carrito"
    const val SERVICES = "Servicios"
    const val PROFILE = "Mi Garaje"
    const val APPOINTMENTS = "Citas"
    @Deprecated("La pestaña Diagnóstico fue reemplazada por Citas.")
    const val DIAGNOSTIC = APPOINTMENTS

    // ==========================================
    // PRODUCTOS
    // ==========================================

    const val PRODUCT_DETAIL = "detalleProducto/{productId}"

    fun productDetail(productId: Int): String {
        return "detalleProducto/$productId"
    }

    // ==========================================
    // INVENTARIO
    // ==========================================

    const val PRODUCT_MANAGEMENT = "administrarRepuestos"

    const val PRODUCT_CREATE = "crearRepuesto"

    const val PRODUCT_EDIT = "editarRepuesto/{productId}"

    fun productEdit(productId: Int): String {
        return "editarRepuesto/$productId"
    }

    // ==========================================
    // PEDIDOS Y VENTAS
    // ==========================================

    const val ORDER_MANAGEMENT = "administrarPedidos"

    // ==========================================
    // MANTENIMIENTOS
    // ==========================================

    const val MAINTENANCE_HISTORY =
        "historialMantenimiento/{vehicleId}"

    fun maintenanceHistory(vehicleId: Int): String {
        return "historialMantenimiento/$vehicleId"
    }

    const val MAINTENANCE_FORM =
        "formularioMantenimiento/{vehicleId}?recordId={recordId}"

    fun maintenanceForm(
        vehicleId: Int,
        recordId: Int? = null
    ): String {

        return if (recordId == null) {
            "formularioMantenimiento/$vehicleId"
        } else {
            "formularioMantenimiento/$vehicleId?recordId=$recordId"
        }
    }
}
