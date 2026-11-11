
package com.example.edmotor.app

import android.content.Context

import com.example.edmotor.data.local.EdMotorDatabase

import com.example.edmotor.data.repository.CartRepository
import com.example.edmotor.data.repository.FavoriteRepository
import com.example.edmotor.data.repository.MaintenanceRepository
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.data.repository.VehicleRepository

import com.example.edmotor.session.SessionManager

import com.example.edmotor.viewmodel.AuthViewModelFactory
import com.example.edmotor.viewmodel.EdMotorViewModelFactory
import com.example.edmotor.viewmodel.MaintenanceViewModelFactory

/**
 * Contenedor central de dependencias de EdMotor.
 *
 * Responsabilidades:
 * - Inicializar la base de datos.
 * - Crear los repositorios.
 * - Compartir el administrador de sesión.
 * - Proporcionar las fábricas de ViewModels.
 */
class AppContainer(context: Context) {

    // ==========================================
    // BASE DE DATOS
    // ==========================================

    private val database = EdMotorDatabase.getDatabase(
        context.applicationContext
    )

    // ==========================================
    // GESTIÓN DE SESIÓN
    // ==========================================

    val sessionManager = SessionManager()

    // ==========================================
    // REPOSITORIOS
    // ==========================================

    private val userRepository = UserRepository(
        database = database,
        userDao = database.userDao(),
        cartDao = database.cartDao(),
        favoriteDao = database.favoriteProductDao()
    )

    private val vehicleRepository = VehicleRepository(
        database.vehicleDao()
    )

    private val cartRepository = CartRepository(
        database.cartDao()
    )

    private val favoriteRepository = FavoriteRepository(
        database.favoriteProductDao()
    )

    private val maintenanceRepository = MaintenanceRepository(
        database = database,
        maintenanceDao = database.maintenanceDao(),
        partDao = database.maintenancePartDao(),
        vehicleDao = database.vehicleDao()
    )

    // ==========================================
    // FÁBRICAS DE VIEWMODELS
    // ==========================================

    val authViewModelFactory = AuthViewModelFactory(
        userRepository,
        sessionManager
    )

    val appViewModelFactory = EdMotorViewModelFactory(
        vehicleRepository,
        cartRepository,
        favoriteRepository
    )

    val maintenanceViewModelFactory = MaintenanceViewModelFactory(
        maintenanceRepository,
        sessionManager
    )
}
