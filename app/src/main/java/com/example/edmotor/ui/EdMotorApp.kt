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
import com.example.edmotor.ui.components.EdMotorScaffold
import com.example.edmotor.ui.screens.AuthFlowScreen
import com.example.edmotor.ui.theme.AppThemeMode
import com.example.edmotor.viewmodel.AuthState
import com.example.edmotor.viewmodel.AuthViewModel
import com.example.edmotor.viewmodel.EdMotorViewModel
import com.example.edmotor.viewmodel.MaintenanceViewModel


// =====================================================
// EDMOTOR APP
// PUNTO PRINCIPAL DE LA APLICACIÓN
// =====================================================

@Composable
fun EdMotorApp(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    onThemeModeChange: (AppThemeMode) -> Unit = {}
) {

    // ==========================================
    // CONTENEDOR CENTRAL DE DEPENDENCIAS
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

    // ESTADO DE AUTENTICACIÓN

    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    val authMessage by authViewModel.message.collectAsStateWithLifecycle()


    // =====================================================
    // CONTROL DE SESIÓN
    // =====================================================

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

        AuthState.SetupRequired -> {

            AuthFlowScreen(
                setupRequired = true,
                message = authMessage,
                onLogin = authViewModel::login,
                onRegister = authViewModel::registerClient,
                onCreateWorkshop = authViewModel::createWorkshop,
                onClearMessage = authViewModel::clearMessage
            )
        }

        AuthState.SignedOut -> {

            AuthFlowScreen(
                setupRequired = false,
                message = authMessage,
                onLogin = authViewModel::login,
                onRegister = authViewModel::registerClient,
                onCreateWorkshop = authViewModel::createWorkshop,
                onClearMessage = authViewModel::clearMessage
            )
        }

        is AuthState.SignedIn -> {

            LaunchedEffect(state.user.id, state.user.role) {

                appViewModel.setActiveUser(
                    state.user.id,
                    state.user.role
                )
            }

            EdMotorAppContent(
                currentUserRole = state.user.role,
                appViewModel = appViewModel,
                maintenanceViewModel = maintenanceViewModel,

                // NUEVO SISTEMA VISUAL

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
// CONTENIDO PRINCIPAL DE EDMOTOR
// =====================================================

@Composable
private fun EdMotorAppContent(
    currentUserRole: String,
    appViewModel: EdMotorViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onLogout: () -> Unit
) {
    // El Scaffold necesita conocer el número de productos del carrito.
    val cartQuantity by appViewModel.cartQuantity.collectAsStateWithLifecycle()
    val cartCount = cartQuantity.values.sum()

    // Controlador de navegación compartido con el Scaffold y el NavHost.
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    EdMotorScaffold(
        currentRoute = currentRoute,
        cartCount = cartCount,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onNavigate = ::navigateTo
    ) { innerPadding ->
        EdMotorNavHost(
            navController = navController,
            currentUserRole = currentUserRole,
            appViewModel = appViewModel,
            maintenanceViewModel = maintenanceViewModel,
            onLogout = onLogout,
            onNavigate = ::navigateTo,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
