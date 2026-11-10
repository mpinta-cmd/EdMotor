
package com.example.edmotor.navigation

object Routes {

    const val HOME = "Inicio"

    const val CAR = "Carrito"

    const val SERVICES = "Servicios"

    const val PROFILE = "Mi Garaje"

    const val DIAGNOSTIC = "Diagnóstico"

    // DETALLE DEL PRODUCTO
    const val PRODUCT_DETAIL = "detalleProducto/{productId}"

    // GENERAR RUTA CON EL ID DEL PRODUCTO
    fun productDetail(productId: Int): String {

        return "detalleProducto/$productId"

    }

}
