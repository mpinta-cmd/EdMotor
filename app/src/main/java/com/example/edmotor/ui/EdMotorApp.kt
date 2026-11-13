
package com.example.edmotor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import com.example.edmotor.app.AppContainer
import com.example.edmotor.navigation.EdMotorNavHost
import com.example.edmotor.model.UserRole
import com.example.edmotor.ui.components.EdMotorScaffold
import com.example.edmotor.ui.screens.AuthFlowScreen
import com.example.edmotor.ui.theme.AppThemeMode

import com.example.edmotor.viewmodel.AuthState
import com.example.edmotor.viewmodel.AuthViewModel
import com.example.edmotor.viewmodel.EdMotorViewModel
import com.example.edmotor.viewmodel.MaintenanceViewModel
import com.example.edmotor.viewmodel.ProductViewModel
import com.example.edmotor.viewmodel.OrderViewModel
import com.example.edmotor.viewmodel.AppointmentViewModel

// =====================================================
// APLICACIÓN PRINCIPAL EDMOTOR
// =====================================================

@Composable
fun EdMotorApp(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {}
) {

    // ==========================================
    // CONTENEDOR DE DEPENDENCIAS
    // ==========================================

    val context = LocalContext.current.applicationContext

    val appContainer = remember(context) {
        AppContainer(context)
    }

    // ==========================================
    // VIEWMODELS
    // ==========================================

    val authViewModel: AuthViewModel = viewModel(
        factory = appContainer.authViewModelFactory
    )

    val appViewModel: EdMotorViewModel = viewModel(
        factory = appContainer.appViewModelFactory
    )

    val maintenanceViewModel: MaintenanceViewModel = viewModel(
        factory = appContainer.maintenanceViewModelFactory
    )

    val productViewModel: ProductViewModel = viewModel(
        factory = appContainer.productViewModelFactory
    )

    val orderViewModel: OrderViewModel = viewModel(
        factory = appContainer.orderViewModelFactory
    )

    val appointmentViewModel: AppointmentViewModel = viewModel(
        factory = appContainer.appointmentViewModelFactory
    )

    // ==========================================
    // ESTADOS DE AUTENTICACIÓN
    // ==========================================

    val authState by authViewModel.authState
        .collectAsStateWithLifecycle()

    val authMessage by authViewModel.message
        .collectAsStateWithLifecycle()

    // ==========================================
    // CONTROL DE SESIÓN
    // ==========================================

    when (val state = authState) {

        AuthState.Loading -> {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {

                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        AuthState.SignedOut -> {

            AuthFlowScreen(
                message = authMessage,
                onLogin = authViewModel::login,
                onRegister = authViewModel::registerClient,
                onClearMessage = authViewModel::clearMessage
            )
        }

        is AuthState.SignedIn -> {

            LaunchedEffect(
                state.user.id,
                state.user.role
            ) {

                appViewModel.setActiveUser(
                    state.user.id,
                    state.user.role
                )
            }

            EdMotorAppContent(
                currentUserRole = state.user.role,
                appViewModel = appViewModel,
                maintenanceViewModel = maintenanceViewModel,
                productViewModel = productViewModel,
                orderViewModel = orderViewModel,
                appointmentViewModel = appointmentViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onLogout = {

                    appViewModel.clearActiveUser()

                    authViewModel.logout()
                }
            )
        }
    }
}

// =====================================================
// CONTENIDO PRINCIPAL
// =====================================================

@Composable
private fun EdMotorAppContent(
    currentUserRole: String,
    appViewModel: EdMotorViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    productViewModel: ProductViewModel,
    orderViewModel: OrderViewModel,
    appointmentViewModel: AppointmentViewModel,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onLogout: () -> Unit
) {

    // ==========================================
    // CANTIDAD DEL CARRITO
    // ==========================================

    val cartQuantity by appViewModel.cartQuantity
        .collectAsStateWithLifecycle()

    val cartCount = if (currentUserRole == UserRole.WORKSHOP.name) {
        0
    } else {
        cartQuantity.values.sum()
    }

    val pendingOrderCount by orderViewModel.pendingOrdersCount
        .collectAsStateWithLifecycle()

    val pendingAppointmentCount by appointmentViewModel.pendingCount
        .collectAsStateWithLifecycle()

    val isWorkshop = currentUserRole == UserRole.WORKSHOP.name

    // ==========================================
    // NAVEGACIÓN
    // ==========================================

    val navController = rememberNavController()

    val navBackStackEntry by navController
        .currentBackStackEntryAsState()

    val currentRoute = navBackStackEntry
        ?.destination
        ?.route

    fun navigateTo(route: String) {

        if (currentRoute == route) return

        navController.navigate(route) {

            popUpTo(
                navController.graph.findStartDestination().id
            ) {

                saveState = true
            }

            launchSingleTop = true

            restoreState = true
        }
    }

    // ==========================================
    // ESTRUCTURA VISUAL
    // ==========================================

    EdMotorScaffold(

        currentRoute = currentRoute,

        cartCount = cartCount,

        pendingOrderCount = if (isWorkshop) pendingOrderCount else 0,

        pendingAppointmentCount = if (isWorkshop) pendingAppointmentCount else 0,

        themeMode = themeMode,

        onThemeModeChange = onThemeModeChange,

        onNavigate = ::navigateTo

    ) { innerPadding ->

        // ======================================
        // NAVEGACIÓN DE PANTALLAS
        // ======================================

        EdMotorNavHost(

            navController = navController,

            currentUserRole = currentUserRole,

            appViewModel = appViewModel,

            maintenanceViewModel = maintenanceViewModel,

            productViewModel = productViewModel,

            orderViewModel = orderViewModel,

            appointmentViewModel = appointmentViewModel,

            onLogout = onLogout,

            onNavigate = ::navigateTo,

            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
