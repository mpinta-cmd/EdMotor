package com.example.edmotor.app

import android.content.Context
import com.example.edmotor.BuildConfig
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.remote.MockApiConnection
import com.example.edmotor.data.repository.AppointmentRepository
import com.example.edmotor.data.repository.CartRepository
import com.example.edmotor.data.repository.FavoriteRepository
import com.example.edmotor.data.repository.MaintenanceRepository
import com.example.edmotor.data.repository.OrderRepository
import com.example.edmotor.data.repository.ProductRepository
import com.example.edmotor.data.repository.UserRepository
import com.example.edmotor.data.repository.VehicleRepository
import com.example.edmotor.session.SessionManager
import com.example.edmotor.viewmodel.AppointmentViewModelFactory
import com.example.edmotor.viewmodel.AuthViewModelFactory
import com.example.edmotor.viewmodel.EdMotorViewModelFactory
import com.example.edmotor.viewmodel.MaintenanceViewModelFactory
import com.example.edmotor.viewmodel.OrderViewModelFactory
import com.example.edmotor.viewmodel.ProductViewModelFactory

class AppContainer(context: Context) {

    private val database = EdMotorDatabase.getDatabase(
        context.applicationContext
    )

    val sessionManager = SessionManager()

    private val mockApi = MockApiConnection.fromBaseUrl(BuildConfig.EDMOTOR_MOCKAPI_URL)

    private val userRepository = UserRepository(
        database = database,
        userDao = database.userDao(),
        mockApi = mockApi
    )

    private val vehicleRepository = VehicleRepository(
        database.vehicleDao()
    )

    private val cartRepository = CartRepository(
        database = database,
        dao = database.cartDao(),
        productDao = database.productDao()
    )

    private val favoriteRepository = FavoriteRepository(
        database.favoriteProductDao()
    )

    private val maintenanceRepository = MaintenanceRepository(
        database = database,
        maintenanceDao = database.maintenanceDao(),
        partDao = database.maintenancePartDao(),
        vehicleDao = database.vehicleDao(),
        productDao = database.productDao()
    )

    private val productRepository = ProductRepository(
        database = database,
        productDao = database.productDao(),
        categoryDao = database.productCategoryDao(),
        sessionManager = sessionManager
    )

    val productsRepository: ProductRepository
        get() = productRepository


    private val orderRepository = OrderRepository(
        database = database,
        orderDao = database.orderDao(),
        productDao = database.productDao(),
        cartDao = database.cartDao(),
        sessionManager = sessionManager
    )


    private val appointmentRepository = AppointmentRepository(
        appointmentDao = database.appointmentDao(),
        mockApi = mockApi,
        sessionManager = sessionManager
    )

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

    val appointmentViewModelFactory = AppointmentViewModelFactory(
        appointmentRepository,
        sessionManager
    )
}
