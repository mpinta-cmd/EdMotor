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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.edmotor.model.UserRole
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
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                vm.refresh()
                delay(20_000)
            }
        }
    }
    OnlineWorkspace(vm, state)
}

private enum class AccessEntry(val reason: String? = null) {
    PROFILE,
    CART("Crea tu perfil de cliente o inicia sesión para enviar tu pedido. Tu carrito se conserva."),
    APPOINTMENTS("Crea tu perfil de cliente o inicia sesión para solicitar una cita y consultar su estado."),
    GARAGE("Crea tu perfil de cliente para guardar tus vehículos y consultar su historial.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineWorkspace(vm: OnlineViewModel, state: OnlineState) {
    val user = state.user
    val workshop = user?.role == UserRole.WORKSHOP.name
    var route by rememberSaveable { mutableStateOf(Routes.HOME) }
    var calendarDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var accessEntry by rememberSaveable { mutableStateOf<AccessEntry?>(null) }
    var profileMenu by remember { mutableStateOf(false) }
    val snackbars = remember { SnackbarHostState() }
    val screenState = rememberSaveableStateHolder()
    val cartCount = state.cart.values.sumOf { it.toLong() }
    val activeRoute = when {
        workshop && route == Routes.CAR -> Routes.HOME
        user == null && route == Routes.ORDER_MANAGEMENT -> Routes.PROFILE
        else -> route
    }

    fun openAccess(entry: AccessEntry) {
        vm.clearMessage()
        accessEntry = entry
    }

    LaunchedEffect(user?.id) {
        if (user != null) accessEntry = null
        if (activeRoute != route) route = activeRoute
        profileMenu = false
    }
    LaunchedEffect(state.message, accessEntry) {
        if (accessEntry != null) return@LaunchedEffect
        val message = state.message ?: return@LaunchedEffect
        snackbars.showSnackbar(message.text, withDismissAction = true)
        vm.clearMessage()
    }

    val entry = accessEntry
    if (entry != null && user == null) {
        CredentialAccessScreen(
            isSubmitting = state.busy,
            message = state.configurationError ?: state.message?.text,
            isError = state.configurationError != null || state.message?.isError == true,
            canRetry = false,
            ownerConfigured = if (state.configurationError == null) true else null,
            isPrivateMode = false,
            onSignIn = vm::login,
            onRegisterClient = vm::register,
            onSetupOwner = { _, _ -> },
            onClearMessage = vm::clearMessage,
            onRetry = vm::refresh,
            isOnlineMode = true,
            initiallyRegisterClient = entry != AccessEntry.PROFILE,
            accessReason = entry.reason,
            onDismiss = {
                vm.clearMessage()
                accessEntry = null
            },
            onRegisterOnlineOwner = vm::registerOwner
        )
        return
    }

    BackHandler(activeRoute != Routes.HOME) {
        route = if (activeRoute == Routes.ORDER_MANAGEMENT) Routes.PROFILE else Routes.HOME
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "EdMotor Automotriz",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (workshop) {
                            Text(
                                "Panel del taller",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (!workshop) {
                        IconButton(onClick = { route = Routes.CAR }) {
                            BadgedBox(badge = {
                                if (cartCount > 0) Badge { Text(if (cartCount > 99) "99+" else cartCount.toString()) }
                            }) {
                                Icon(Icons.Outlined.ShoppingCart, if (cartCount == 0L) "Carrito" else "Carrito, $cartCount unidades")
                            }
                        }
                    } else {
                        IconButton(onClick = vm::refresh, enabled = !state.busy) {
                            Icon(Icons.Outlined.Refresh, "Actualizar datos")
                        }
                    }
                    Box {
                        IconButton(onClick = {
                            if (user == null) openAccess(AccessEntry.PROFILE) else profileMenu = true
                        }) {
                            Icon(Icons.Outlined.AccountCircle, if (user == null) "Tu perfil, iniciar sesión o registrarte" else "Tu perfil")
                        }
                        if (user != null) {
                            DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Text(user.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (workshop) "Dueño del taller" else "Cliente", style = MaterialTheme.typography.bodySmall)
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Mi Garaje") },
                                    onClick = { profileMenu = false; route = Routes.PROFILE }
                                )
                                if (!workshop) {
                                    DropdownMenuItem(
                                        text = { Text("Mis pedidos") },
                                        onClick = { profileMenu = false; route = Routes.ORDER_MANAGEMENT }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Actualizar datos") },
                                        enabled = !state.busy,
                                        onClick = { profileMenu = false; vm.refresh() }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Cerrar sesión") },
                                    enabled = !state.busy,
                                    onClick = { profileMenu = false; route = Routes.HOME; vm.logout() }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            EdMotorBottomBar(
                currentRoute = activeRoute,
                pendingOrderCount = if (workshop) state.orders.count { it.status == "PENDING" } else 0,
                pendingAppointmentCount = if (workshop) state.appointments.count { it.status == "PENDING" } else 0,
                onNavigate = { route = it }
            )
        },
        snackbarHost = { SnackbarHost(snackbars) }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            if (state.isStale && activeRoute != Routes.DASHBOARD) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        if (user == null) "No pudimos actualizar el catálogo. Revisa tu conexión."
                        else "Parte de la información puede estar desactualizada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("Reintentar") }
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                screenState.SaveableStateProvider("${user?.id ?: "guest"}:$activeRoute") { when (activeRoute) {
                    Routes.DASHBOARD -> DashboardScreen()
                    Routes.PROFILE -> if (user == null) GuestGarageScreen(
                        onCreateAccount = { openAccess(AccessEntry.GARAGE) },
                        onBrowseCatalog = { route = Routes.HOME }
                    ) else OnlineGarageScreen(
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
                    Routes.APPOINTMENTS -> if (user == null) {
                        GuestAppointmentsScreen(onAuthenticate = { openAccess(AccessEntry.APPOINTMENTS) })
                    } else {
                        OnlineAppointmentsScreen(vm, state, calendarDay, onOpenGarage = { route = Routes.PROFILE }) {
                            calendarDay = null
                        }
                    }
                    Routes.ORDER_MANAGEMENT -> OnlineOrdersScreen(vm, state)
                    Routes.CAR -> OnlineCartScreen(
                        vm, state, vm::changeCartQuantity,
                        onAuthenticate = { openAccess(AccessEntry.CART) },
                        onBrowseCatalog = { route = Routes.HOME },
                        onOrderCreated = { route = Routes.ORDER_MANAGEMENT }
                    )
                    else -> when {
                        state.configurationError != null -> OnlineConnectionNotice(state.configurationError)
                        state.loading && state.products.isEmpty() -> OnlineConnectionNotice("Consultando repuestos…", working = true)
                        else -> OnlineCatalogScreen(vm, state, vm::addToCart)
                    }
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
