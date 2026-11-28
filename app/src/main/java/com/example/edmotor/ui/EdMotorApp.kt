
package com.example.edmotor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import com.example.edmotor.BuildConfig
import com.example.edmotor.app.EdMotorApplication
import com.example.edmotor.navigation.EdMotorNavHost
import com.example.edmotor.model.UserRole
import com.example.edmotor.ui.components.EdMotorBottomSheet
import com.example.edmotor.ui.components.EdMotorScaffold
import com.example.edmotor.ui.screens.CredentialAccessScreen
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

    val appContainer = (LocalContext.current.applicationContext as EdMotorApplication).appContainer

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

    val logoutError by authViewModel.logoutError.collectAsStateWithLifecycle()

    // ==========================================
    // CONTROL DE SESIÓN
    // ==========================================

    when (val state = authState) {
        AuthState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                CircularProgressIndicator()
                Text("Cargando perfil…")
            }
        }

        AuthState.SignedOut -> {
            LaunchedEffect(Unit) { appViewModel.clearActiveUser() }

            val access by authViewModel.privateAccess.collectAsStateWithLifecycle()
            CredentialAccessScreen(
                isSubmitting = access.isSubmitting,
                message = access.message,
                isError = access.isError,
                canRetry = access.canRetry,
                ownerConfigured = access.ownerConfigured,
                isPrivateMode = BuildConfig.EDMOTOR_PRIVATE_MODE,
                onSignIn = authViewModel::signInPrivate,
                onRegisterClient = authViewModel::registerPrivateClient,
                onSetupOwner = authViewModel::createPrivateOwner,
                onClearMessage = authViewModel::clearPrivateMessage,
                onRetry = authViewModel::retryPrivateRestore
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

            key(state.user.id, state.user.remoteAuthId, state.user.role) {
                EdMotorAppContent(
                    currentUserId = state.user.id,
                    currentUserName = state.user.displayName,
                    currentUserRole = state.user.role,
                    appViewModel = appViewModel,
                    maintenanceViewModel = maintenanceViewModel,
                    productViewModel = productViewModel,
                    orderViewModel = orderViewModel,
                    appointmentViewModel = appointmentViewModel,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onLogout = authViewModel::logout
                )
            }

            logoutError?.let { error ->
                EdMotorBottomSheet(
                    onDismissRequest = authViewModel::clearLogoutError,
                    title = { Text("Cerrar sesión") },
                    text = { Text(error) },
                    confirmButton = {
                        TextButton(onClick = authViewModel::clearLogoutError) { Text("Entendido") }
                    }
                )
            }
        }
    }
}

// =====================================================
// CONTENIDO PRINCIPAL
// =====================================================

@Composable
private fun EdMotorAppContent(
    currentUserId: Int,
    currentUserName: String,
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

    val cartQuantity by remember(appViewModel, currentUserId, currentUserRole) {
        appViewModel.observeCart(currentUserId, currentUserRole)
    }.collectAsStateWithLifecycle(initialValue = emptyMap())

    val isWorkshop = currentUserRole == UserRole.WORKSHOP.name
    val cartCount = if (isWorkshop) 0 else cartQuantity.values
        .sumOf { it.coerceAtLeast(0).toLong() }
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

    val pendingOrderCount by orderViewModel.pendingOrdersCount
        .collectAsStateWithLifecycle(initialValue = 0)

    val pendingAppointmentCount by appointmentViewModel.pendingCount
        .collectAsStateWithLifecycle(initialValue = 0)

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

        hasCartItems = !isWorkshop && cartQuantity.isNotEmpty(),

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

            currentUserId = currentUserId,

            currentUserName = currentUserName,

            currentUserRole = currentUserRole,

            cartQuantity = cartQuantity,

            cartCount = cartCount,

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
                .consumeWindowInsets(innerPadding)
        )
    }
}
