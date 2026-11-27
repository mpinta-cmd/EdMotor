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
import androidx.compose.material.icons.outlined.Fingerprint
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
import com.example.edmotor.ui.screens.DashboardEvidenceVehicle
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
    CART("Crea tu perfil de cliente o inicia sesión para finalizar tu compra. Tu carrito se conserva."),
    APPOINTMENTS("Crea tu perfil de cliente o inicia sesión para solicitar una cita y consultar su estado."),
    GARAGE("Crea tu perfil de cliente para guardar tus vehículos y consultar su historial.")
}

private const val CHECKOUT_ROUTE = "finalizarCompra"
private const val PURCHASE_RECEIPT_ROUTE = "compraConfirmada"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineWorkspace(vm: OnlineViewModel, state: OnlineState) {
    val user = state.user
    val workshop = user?.role == UserRole.WORKSHOP.name
    var route by rememberSaveable { mutableStateOf(Routes.HOME) }
    var receiptOrderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var calendarDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var accessEntry by rememberSaveable { mutableStateOf<AccessEntry?>(null) }
    var profileMenu by remember { mutableStateOf(false) }
    val snackbars = remember { SnackbarHostState() }
    val screenState = rememberSaveableStateHolder()
    val cartCount = state.cart.values.sumOf { it.toLong() }
    val activeRoute = when {
        workshop && (route == Routes.CAR || route == CHECKOUT_ROUTE || route == PURCHASE_RECEIPT_ROUTE) -> Routes.HOME
        user == null && route == CHECKOUT_ROUTE -> Routes.CAR
        user == null && (route == Routes.ORDER_MANAGEMENT || route == PURCHASE_RECEIPT_ROUTE) -> Routes.PROFILE
        else -> route
    }
    val ownerSession = vm.inventoryOwnerSession(user?.id)
    val inventoryAccess = if (ownerSession != null) {
        rememberOwnerInventoryAccess(
            sessionIdentity = ownerSession,
            profileScope = vm.onlineProfileScope(user?.id),
            inventoryOpen = activeRoute == Routes.HOME,
            isCurrentSession = vm::isInventoryOwnerSession,
            confirmPassword = vm::confirmInventoryOwnerPassword
        )
    } else null
    val catalogSync = state.syncStatus(OnlineResource.CATALOG)
    val syncNotice = when (activeRoute) {
        Routes.HOME, Routes.CAR, CHECKOUT_ROUTE -> "No pudimos actualizar el catálogo.".takeIf { catalogSync.unavailable }
        Routes.ORDER_MANAGEMENT -> "No pudimos actualizar los pedidos."
            .takeIf { state.syncStatus(OnlineResource.ORDERS).unavailable }
        Routes.PROFILE -> when {
            state.syncStatus(OnlineResource.VEHICLES).unavailable -> "No pudimos actualizar los vehículos."
            state.syncStatus(OnlineResource.MAINTENANCE).unavailable -> "No pudimos actualizar el historial de mantenimiento."
            else -> null
        }
        Routes.APPOINTMENTS -> "No pudimos actualizar los vehículos para la cita."
            .takeIf { user != null && !workshop && state.syncStatus(OnlineResource.VEHICLES).unavailable }
        else -> null
    }

    fun openAccess(entry: AccessEntry) {
        vm.clearMessage()
        accessEntry = entry
    }

    LaunchedEffect(user?.id) {
        val enteringCheckout = accessEntry == AccessEntry.CART
        if (enteringCheckout && user?.role == UserRole.CLIENT.name) {
            route = CHECKOUT_ROUTE
        } else if (activeRoute != route && (!enteringCheckout || user != null)) {
            route = activeRoute
        }
        if (user != null) accessEntry = null
        profileMenu = false
    }
    LaunchedEffect(activeRoute) {
        if (activeRoute == Routes.HOME || activeRoute == CHECKOUT_ROUTE) vm.refresh()
    }
    LaunchedEffect(state.checkoutReceiptId) {
        val orderId = state.checkoutReceiptId ?: return@LaunchedEffect
        if (activeRoute == CHECKOUT_ROUTE) {
            receiptOrderId = orderId
            route = PURCHASE_RECEIPT_ROUTE
        }
        vm.clearCheckoutReceipt()
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
                if (entry == AccessEntry.CART) route = Routes.CAR
                accessEntry = null
            },
            onRegisterOnlineOwner = vm::registerOwner
        )
        return
    }

    BackHandler(activeRoute != Routes.HOME) {
        route = when (activeRoute) {
            CHECKOUT_ROUTE -> Routes.CAR
            PURCHASE_RECEIPT_ROUTE -> Routes.ORDER_MANAGEMENT
            Routes.ORDER_MANAGEMENT -> Routes.PROFILE
            else -> Routes.HOME
        }
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
                                Icon(
                                    Icons.Outlined.ShoppingCart,
                                    when (cartCount) {
                                        0L -> "Carrito"
                                        1L -> "Carrito, 1 unidad"
                                        else -> "Carrito, $cartCount unidades"
                                    }
                                )
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
                                if (inventoryAccess != null) {
                                    DropdownMenuItem(
                                        text = { Text("Huella para Inventario") },
                                        leadingIcon = { Icon(Icons.Outlined.Fingerprint, contentDescription = null) },
                                        onClick = { profileMenu = false; inventoryAccess.openSettings() }
                                    )
                                }
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
            if (syncNotice != null) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        syncNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("Reintentar") }
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                screenState.SaveableStateProvider("${user?.id ?: "guest"}:$activeRoute") { when (activeRoute) {
                    Routes.DASHBOARD -> DashboardScreen(
                        evidenceScope = vm.onlineProfileScope(user?.id),
                        vehicles = if (user == null) emptyList() else state.vehicles
                            .filter { workshop || it.clientId == user.id }
                            .map { vehicle ->
                                val label = listOf("${vehicle.brand} ${vehicle.model}", vehicle.year.toString(), vehicle.plate)
                                    .filter(String::isNotBlank).joinToString(" · ")
                                DashboardEvidenceVehicle(vehicle.id.toString(), label)
                            }
                    )
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
                        onCheckout = {
                            route = CHECKOUT_ROUTE
                            if (user == null) openAccess(AccessEntry.CART)
                        },
                        onBrowseCatalog = { route = Routes.HOME }
                    )
                    CHECKOUT_ROUTE -> OnlineCheckoutScreen(
                        state,
                        onConfirmPurchase = vm::confirmPurchase,
                        onBackToCart = { route = Routes.CAR }
                    )
                    PURCHASE_RECEIPT_ROUTE -> {
                        val order = state.orders.firstOrNull { it.id == receiptOrderId }
                        if (order != null) {
                            OnlinePurchaseReceipt(
                                order,
                                onViewOrders = { route = Routes.ORDER_MANAGEMENT },
                                onBrowseCatalog = { route = Routes.HOME }
                            )
                        } else {
                            val ordersSync = state.syncStatus(OnlineResource.ORDERS)
                            val loading = ordersSync.waitingForFirstData
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    if (loading) "Consultando la compra…" else "Consulta el estado de tu compra en Mis pedidos.",
                                    textAlign = TextAlign.Center
                                )
                                TextButton(onClick = { route = Routes.ORDER_MANAGEMENT }) { Text("Ir a Mis pedidos") }
                            }
                        }
                    }
                    else -> {
                        val voiceSearch = rememberOnlineVoiceSearch()
                        when {
                            state.configurationError != null -> OnlineConnectionNotice(state.configurationError)
                            workshop && inventoryAccess == null -> OnlineConnectionNotice("Verificando el perfil del dueño…", working = true)
                            inventoryAccess?.locked == true -> OwnerInventoryLockedScreen(inventoryAccess)
                            catalogSync.waitingForFirstData && state.products.isEmpty() -> OnlineConnectionNotice(
                                if (catalogSync.retrying) "Volviendo a conectar con el catálogo…" else "Consultando repuestos…",
                                working = true
                            )
                            else -> OnlineCatalogScreen(vm, state, vm::addToCart, voiceSearch = voiceSearch) { route = Routes.CAR }
                        }
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
