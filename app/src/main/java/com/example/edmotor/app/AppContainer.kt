
package com.example.edmotor.app

import android.content.Context

import com.example.edmotor.data.local.EdMotorDatabase

import com.example.edmotor.data.repository.CartRepository
import com.example.edmotor.data.repository.FavoriteRepository
import com.example.edmotor.data.repository.MaintenanceRepository
import com.example.edmotor.data.repository.OrderRepository
import com.example.edmotor.data.repository.ProductRepository
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.data.repository.VehicleRepository

import com.example.edmotor.session.SessionManager

import com.example.edmotor.viewmodel.AuthViewModelFactory
import com.example.edmotor.viewmodel.EdMotorViewModelFactory
import com.example.edmotor.viewmodel.MaintenanceViewModelFactory
import com.example.edmotor.viewmodel.ProductViewModelFactory
import com.example.edmotor.viewmodel.OrderViewModelFactory

class AppContainer(context: Context) {

    // ==========================================
    // BASE DE DATOS
    // ==========================================

    private val database = EdMotorDatabase.getDatabase(
        context.applicationContext
    )

    // ==========================================
    // SESIÓN
    // ==========================================

    val sessionManager = SessionManager()

    // ==========================================
    // USUARIOS
    // ==========================================

    private val userRepository = UserRepository(
        database = database,
        userDao = database.userDao(),
        cartDao = database.cartDao(),
        favoriteDao = database.favoriteProductDao()
    )

    // ==========================================
    // VEHÍCULOS
    // ==========================================

    private val vehicleRepository = VehicleRepository(
        database.vehicleDao()
    )

    // ==========================================
    // CARRITO
    // ==========================================

    private val cartRepository = CartRepository(
        database = database,
        dao = database.cartDao(),
        productDao = database.productDao()
    )

    // ==========================================
    // FAVORITOS
    // ==========================================

    private val favoriteRepository = FavoriteRepository(
        database.favoriteProductDao()
    )

    // ==========================================
    // MANTENIMIENTOS
    // ==========================================

    private val maintenanceRepository = MaintenanceRepository(
        database = database,
        maintenanceDao = database.maintenanceDao(),
        partDao = database.maintenancePartDao(),
        vehicleDao = database.vehicleDao(),
        productDao = database.productDao()
    )

    // ==========================================
    // PRODUCTOS E INVENTARIO
    // ==========================================

    private val productRepository = ProductRepository(
        database = database,
        productDao = database.productDao(),
        categoryDao = database.productCategoryDao(),
        sessionManager = sessionManager
    )

    val productsRepository: ProductRepository
        get() = productRepository

    // ==========================================
    // PEDIDOS Y VENTAS
    // ==========================================

    private val orderRepository = OrderRepository(
        database = database,
        orderDao = database.orderDao(),
        cartDao = database.cartDao(),
        productDao = database.productDao()
    )

    // ==========================================
    // VIEWMODEL FACTORIES
    // ==========================================

    val authViewModelFactory = AuthViewModelFactory(
        userRepository,
        sessionManager
    )

    val appViewModelFactory = EdMotorViewModelFactory(
        vehicleRepository,
        cartRepository,
        favoriteRepository,
        userRepository
    )

    val maintenanceViewModelFactory = MaintenanceViewModelFactory(
        maintenanceRepository,
        sessionManager
    )

    val productViewModelFactory = ProductViewModelFactory(
        productRepository,
        sessionManager
    )

    val orderViewModelFactory = OrderViewModelFactory(
        orderRepository,
        sessionManager
    )
}
