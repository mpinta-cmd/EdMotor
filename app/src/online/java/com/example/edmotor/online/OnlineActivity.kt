package com.example.edmotor.online

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.edmotor.model.appointmentDateUtcMillis
import com.example.edmotor.navigation.Routes
import com.example.edmotor.ui.components.EdMotorBottomBar
import com.example.edmotor.ui.screens.CredentialAccessScreen
import com.example.edmotor.ui.screens.DashboardScreen
import com.example.edmotor.ui.theme.EdMotorTheme
import java.util.TimeZone
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class OnlineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EdMotorTheme { OnlineEdMotor() } }
    }
}

@Composable
private fun OnlineEdMotor(vm: OnlineViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val user = state.user
    LaunchedEffect(lifecycle, user?.id) {
        if (user != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                vm.refresh()
                delay(20_000)
            }
        }
    }

    when {
        state.configurationError != null -> OnlineConnectionNotice(requireNotNull(state.configurationError))
        state.restoring -> OnlineConnectionNotice("Comprobando tu sesión…", working = true)
        user == null -> CredentialAccessScreen(
            isSubmitting = state.busy,
            message = state.message?.text,
            isError = state.message?.isError == true,
            canRetry = state.isStale,
            ownerConfigured = true,
            isPrivateMode = false,
            onSignIn = vm::login,
            onRegisterClient = vm::register,
            onSetupOwner = { _, _ -> },
            onClearMessage = vm::clearMessage,
            onRetry = vm::refresh,
            isOnlineMode = true
        )
        else -> key(user.id) { OnlineWorkspace(vm, state, user) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineWorkspace(vm: OnlineViewModel, state: OnlineState, user: OnlineUser) {
    val workshop = user.role == "WORKSHOP"
    var route by rememberSaveable { mutableStateOf(Routes.HOME) }
    var calendarDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var cart by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    val snackbars = remember { SnackbarHostState() }
    val screenState = rememberSaveableStateHolder()

    fun changeQuantity(id: Long, quantity: Int) {
        val available = state.products.firstOrNull { it.id == id }?.stock ?: 0
        val count = quantity.coerceIn(0, available.coerceAtLeast(0))
        cart = if (count == 0) cart - id else cart + (id to count)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbars.showSnackbar(message.text, withDismissAction = true)
        vm.clearMessage()
    }
    BackHandler(route != Routes.HOME) {
        route = if (route == Routes.ORDER_MANAGEMENT) Routes.PROFILE else Routes.HOME
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("EdMotor Automotriz", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (workshop) "Panel del taller" else user.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !state.busy) {
                        Icon(Icons.Outlined.Refresh, "Actualizar datos")
                    }
                }
            )
        },
        bottomBar = {
            EdMotorBottomBar(
                currentRoute = if (route == Routes.CAR) Routes.HOME else route,
                pendingOrderCount = if (workshop) state.orders.count { it.status == "PENDING" } else 0,
                pendingAppointmentCount = if (workshop) state.appointments.count { it.status == "PENDING" } else 0,
                onNavigate = { route = it }
            )
        },
        snackbarHost = { SnackbarHost(snackbars) }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            if (state.isStale && route != Routes.DASHBOARD) {
                Text(
                    "No se pudieron actualizar todos los datos. Parte de la información puede estar desactualizada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                if (route != Routes.DASHBOARD && state.loading && state.lastSyncAtMillis == null && state.products.isEmpty()) {
                    OnlineConnectionNotice("Consultando EdMotor…", working = true)
                } else screenState.SaveableStateProvider(route) { when (route) {
                    Routes.DASHBOARD -> DashboardScreen()
                    Routes.PROFILE -> OnlineGarageScreen(
                        vm = vm,
                        state = state,
                        onOpenAppointment = { appointment ->
                            calendarDay = appointmentDateUtcMillis(
                                appointment.scheduledAtMillis,
                                TimeZone.getTimeZone("America/Bogota")
                            )
                            route = Routes.APPOINTMENTS
                        },
                        onOpenOrders = { route = Routes.ORDER_MANAGEMENT }
                    )
                    Routes.APPOINTMENTS -> OnlineAppointmentsScreen(vm, state, calendarDay) { calendarDay = null }
                    Routes.ORDER_MANAGEMENT -> OnlineOrdersScreen(vm, state)
                    Routes.CAR -> OnlineCartScreen(vm, state, cart, ::changeQuantity) {
                        cart = emptyMap()
                        route = Routes.ORDER_MANAGEMENT
                    }
                    else -> OnlineCatalogScreen(
                        vm = vm,
                        state = state,
                        cart = cart,
                        onAddToCart = { id ->
                            val count = cart[id] ?: 0
                            if (count < Int.MAX_VALUE) changeQuantity(id, count + 1)
                        },
                        onOpenCart = { route = Routes.CAR }
                    )
                } }
            }
        }
    }
}

@Composable
private fun OnlineConnectionNotice(message: String, working: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (working) CircularProgressIndicator()
            Text(message, textAlign = TextAlign.Center)
        }
    }
}
