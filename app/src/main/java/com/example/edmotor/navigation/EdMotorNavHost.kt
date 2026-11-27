package com.example.edmotor.navigation

import android.widget.Toast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

import com.example.edmotor.R
import com.example.edmotor.data.productList
import com.example.edmotor.data.toProduct

import com.example.edmotor.model.Appointment
import com.example.edmotor.model.AppointmentSyncState
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle
import com.example.edmotor.model.appointmentDateUtcMillis

import com.example.edmotor.ui.screens.CartScreen
import com.example.edmotor.ui.screens.ClientGarageScreen
import com.example.edmotor.ui.screens.HomeScreen
import com.example.edmotor.ui.screens.MaintenanceFormScreen
import com.example.edmotor.ui.screens.MaintenanceHistoryScreen
import com.example.edmotor.ui.screens.ProductDetailScreen
import com.example.edmotor.ui.screens.DashboardEvidenceVehicle
import com.example.edmotor.ui.screens.DashboardScreen
import com.example.edmotor.ui.screens.WorkshopGarageScreen
import com.example.edmotor.ui.screens.ProductManagementScreen
import com.example.edmotor.ui.screens.ProductFormScreen
import com.example.edmotor.ui.screens.AppointmentsScreen

import com.example.edmotor.viewmodel.EdMotorViewModel
import com.example.edmotor.viewmodel.MaintenanceViewModel
import com.example.edmotor.viewmodel.ProductViewModel
import com.example.edmotor.viewmodel.OrderViewModel
import com.example.edmotor.viewmodel.AppointmentViewModel

private const val APPOINTMENT_WORK_ID = "appointmentWorkId"
private const val APPOINTMENT_CALENDAR_DAY = "appointmentCalendarDay"

/**
 * Navegación principal de EdMotor.
 *
 * Incluye:
 * - Catálogo dinámico.
 * - Detalle de productos.
 * - Carrito.
 * - Registro e historial local de pedidos.
 * - Administración de pedidos y ventas.
 * - Servicios.
 * - Garaje.
 * - Agenda de citas.
 * - Historial de mantenimiento.
 * - Formularios de mantenimiento.
 * - Administración de inventario.
 * - Creación y edición de repuestos.
 */

@Composable
fun EdMotorNavHost(
    navController: NavHostController,
    currentUserId: Int,
    currentUserName: String,
    currentUserRole: String,
    cartQuantity: Map<Int, Int>,
    cartCount: Int,
    appViewModel: EdMotorViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    productViewModel: ProductViewModel,
    orderViewModel: OrderViewModel,
    appointmentViewModel: AppointmentViewModel,
    onLogout: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {

    // =====================================================
    // 1. ESTADOS GENERALES
    // =====================================================

    val searchQuery by appViewModel.searchQuery
        .collectAsStateWithLifecycle()

    val selectedCategory by appViewModel.selectedCategory
        .collectAsStateWithLifecycle()

    val cartMessage by remember(appViewModel, currentUserId, currentUserRole) {
        appViewModel.observeCartMessage(currentUserId, currentUserRole)
    }.collectAsStateWithLifecycle(initialValue = null)

    val vehicles by remember(appViewModel, currentUserId, currentUserRole) {
        appViewModel.observeVehicles(currentUserId, currentUserRole)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val favoriteProductIds by remember(appViewModel, currentUserId, currentUserRole) {
        appViewModel.observeFavorites(currentUserId, currentUserRole)
    }.collectAsStateWithLifecycle(initialValue = emptySet())


    // =====================================================
    // 2. ESTADOS DE MANTENIMIENTO
    // =====================================================

    val isSavingMaintenance by maintenanceViewModel.isSaving.collectAsStateWithLifecycle(initialValue = false)

    val appointmentWorkState by remember(maintenanceViewModel, currentUserId, currentUserRole) {
        maintenanceViewModel.observeAppointmentWork(currentUserId, currentUserRole)
    }.collectAsStateWithLifecycle(initialValue = null)
    val appointmentWork = appointmentWorkState.orEmpty()

    // =====================================================
    // 3. ESTADOS DEL INVENTARIO
    // =====================================================

    val productEntities by productViewModel.products
        .collectAsStateWithLifecycle()

    val categories by productViewModel.categories
        .collectAsStateWithLifecycle()

    val clients by remember(appViewModel, currentUserId, currentUserRole) {
        appViewModel.observeClients(currentUserId, currentUserRole)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val isSavingProduct by productViewModel.isSaving
        .collectAsStateWithLifecycle()

    // Conversión de entidades Room al modelo visual Product.

    val catalogProducts = remember(productEntities) {
        productEntities.map { it.toProduct() }
    }


    // =====================================================
    // 4. ESTADOS DE PEDIDOS Y VENTAS
    // =====================================================

    val orders by orderViewModel.orders
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val isProcessingOrder by orderViewModel.isProcessing
        .collectAsStateWithLifecycle()

    val orderMessage by orderViewModel.message
        .collectAsStateWithLifecycle(initialValue = null)

    val orderIsError by orderViewModel.isError
        .collectAsStateWithLifecycle(initialValue = false)

    // =====================================================
    // 5. ESTADOS DE CITAS
    // =====================================================

    val appointments by appointmentViewModel.appointments
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val appointmentSyncState by appointmentViewModel.syncState
        .collectAsStateWithLifecycle(initialValue = AppointmentSyncState.LOADING)

    val isProcessingAppointment by appointmentViewModel.isProcessing
        .collectAsStateWithLifecycle(initialValue = false)

    val appointmentMessage by appointmentViewModel.message
        .collectAsStateWithLifecycle(initialValue = null)

    val appointmentIsError by appointmentViewModel.isError
        .collectAsStateWithLifecycle(initialValue = false)


    // =====================================================
    // 6. CONFIGURACIÓN GENERAL
    // =====================================================

    val isWorkshop =
        UserRole.from(currentUserRole) == UserRole.WORKSHOP

    val vehicleList = remember(vehicles, isWorkshop, currentUserId) {
        if (isWorkshop) vehicles else vehicles.filter { it.ownerUserId == currentUserId }
    }

    val context = LocalContext.current


    // =====================================================
    // 7. MENSAJES DE VALIDACIÓN DEL CARRITO
    // =====================================================

    LaunchedEffect(cartMessage) {

        cartMessage?.let { message ->

            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()

            appViewModel.clearCartMessage()
        }
    }


    // =====================================================
    // 8. GRAFO DE NAVEGACIÓN
    // =====================================================

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {


        // =================================================
        // 1. INICIO / CATÁLOGO
        // =================================================

        composable(Routes.HOME) {

            HomeScreen(

                searchQuery = searchQuery,

                onSearchChange = appViewModel::updateSearchQuery,

                categories = categories,

                product = catalogProducts,

                cartQuantity = cartQuantity,

                selectedCategory = selectedCategory,

                favoriteProductIds = favoriteProductIds,

                onFavoriteClick = appViewModel::toggleFavorite,

                onProductClick = { productId ->
                    // Seleccionar un producto siempre abre su ficha.
                    // El dueño decide desde allí si desea editarlo.
                    navController.navigate(Routes.productDetail(productId)) {
                        launchSingleTop = true
                    }
                },

                onCategorySelect = appViewModel::toggleCategory,

                onIncrease = appViewModel::increaseQuantity,

                onDecrease = appViewModel::decreaseQuantity,

                onAdjustStock = { productId, delta ->
                    productViewModel.adjustStock(productId, delta) { result ->
                        if (result is com.example.edmotor.data.repository.ProductOperationResult.Error) {
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                },

                // ACCESO EXCLUSIVO DEL TALLER

                isWorkshop = isWorkshop,

                onOpenInventory = {

                    if (isWorkshop) {

                        navController.navigate(
                            Routes.PRODUCT_MANAGEMENT
                        ) {
                            launchSingleTop = true
                        }
                    }
                },

                onOpenAppointments = { onNavigate(Routes.APPOINTMENTS) }
            )
        }


        // =================================================
        // 2. DETALLE DEL PRODUCTO
        // =================================================

        composable(
            route = Routes.PRODUCT_DETAIL,
            arguments = listOf(
                navArgument("productId") {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->

            val productId =
                backStackEntry.arguments?.getInt("productId") ?: -1

            val selectedProduct = catalogProducts.firstOrNull {
                it.id == productId
            }

            if (selectedProduct != null) {

                ProductDetailScreen(

                    product = selectedProduct,

                    quantity = cartQuantity[selectedProduct.id] ?: 0,

                    cartCount = cartCount,

                    isFavorite = selectedProduct.id in favoriteProductIds,

                    isWorkshop = isWorkshop,

                    onBack = {
                        navController.popBackStack()
                    },

                    onFavoriteClick = {
                        if (!isWorkshop) {
                            appViewModel.toggleFavorite(selectedProduct.id)
                        }
                    },

                    onIncrease = {
                        if (!isWorkshop) {
                            appViewModel.increaseQuantity(selectedProduct.id)
                        }
                    },

                    onDecrease = {
                        if (!isWorkshop) {
                            appViewModel.decreaseQuantity(selectedProduct.id)
                        }
                    },

                    onOpenCart = {
                        if (!isWorkshop) {
                            navController.navigate(Routes.CAR)
                        }
                    },

                    onEditProduct = {
                        if (isWorkshop) {
                            navController.navigate(Routes.productEdit(selectedProduct.id)) {
                                launchSingleTop = true
                            }
                        }
                    }
                )

            } else {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        "No encontramos este producto.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            navController.popBackStack()
                        }
                    ) {
                        Text("Volver al catálogo")
                    }
                }
            }
        }


        // =================================================
        // 3. CARRITO Y REGISTRO DE PEDIDOS
        // =================================================

        composable(Routes.CAR) {
            if (isWorkshop) {
                AccessDeniedScreen(
                    message = "El perfil de EdMotor administra el inventario y no utiliza el carrito de compras.",
                    onBack = { navController.popBackStack() }
                )
            } else {
                CartScreen(
                    products = productEntities,
                    cartQuantity = cartQuantity,
                    onIncrease = appViewModel::increaseQuantity,
                    onDecrease = appViewModel::decreaseQuantity,
                    onRemove = appViewModel::removeProductFromCart,
                    onSubmitOrder = orderViewModel::submitOrder,
                    isSubmittingOrder = isProcessingOrder,
                    orderMessage = orderMessage,
                    isOrderError = orderIsError,
                    onClearOrderMessage = orderViewModel::clearMessage
                )
            }
        }


        // =================================================
        // 4. TESTIGOS
        // =================================================

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                evidenceScope = "local:$currentUserId",
                vehicles = vehicleList.map { vehicle ->
                    val label = listOf("${vehicle.brand} ${vehicle.model}", vehicle.year.toString(), vehicle.plate)
                        .filter(String::isNotBlank).joinToString(" · ")
                    DashboardEvidenceVehicle(vehicle.id.toString(), label)
                }
            )
        }


        // =================================================
        // 5. GARAJE / PERFIL
        // =================================================

        // Conserva la ruta anterior para restaurar la navegación de una instalación existente.
        listOf(Routes.PROFILE, Routes.ORDER_MANAGEMENT).forEach { route ->
            composable(route) {
                val openHistory: (Int) -> Unit = { vehicleId ->
                    navController.navigate(Routes.maintenanceHistory(vehicleId))
                }
                if (isWorkshop) {
                    WorkshopGarageScreen(
                        profileName = currentUserName,
                        vehicles = vehicleList,
                        clients = clients,
                        orders = orders,
                        isProcessing = isProcessingOrder,
                        message = orderMessage,
                        isError = orderIsError,
                        onVehicleClick = openHistory,
                        onConfirm = orderViewModel::confirmOrder,
                        onReject = orderViewModel::rejectOrder,
                        onLogout = onLogout,
                        showPurchasesInitially = route == Routes.ORDER_MANAGEMENT
                    )
                } else if (route == Routes.PROFILE) {
                    val pickupNotice by appointmentViewModel.pickupNotice
                        .collectAsStateWithLifecycle(initialValue = null)
                    ClientGarageScreen(
                        profileName = currentUserName,
                        vehicles = vehicleList,
                        orders = orders,
                        onAddVehicle = appViewModel::addVehicle,
                        onVehicleClick = openHistory,
                        onLogout = onLogout,
                        pickupNotice = pickupNotice,
                        onViewPickup = { appointment ->
                            navController.navigate(Routes.APPOINTMENTS) { launchSingleTop = true }
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                APPOINTMENT_CALENDAR_DAY,
                                appointmentDateUtcMillis(appointment.scheduledAtMillis)
                            )
                        },
                        onDismissPickup = { appointmentViewModel.dismissPickup(it) }
                    )
                } else {
                    AccessDeniedScreen(
                        message = "Solo el taller puede administrar pedidos.",
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }


        // =================================================
        // 7. HISTORIAL DE MANTENIMIENTO
        // =================================================

        composable(
            route = Routes.MAINTENANCE_HISTORY,
            arguments = listOf(
                navArgument("vehicleId") {
                    type = NavType.IntType
                }
            )
        ) { entry ->

            val vehicleId =
                entry.arguments?.getInt("vehicleId") ?: -1

            val maintenanceRecords by remember(maintenanceViewModel, vehicleId, currentUserId, currentUserRole) {
                maintenanceViewModel.observeRecords(vehicleId, currentUserId, currentUserRole)
            }.collectAsStateWithLifecycle(initialValue = null)
            val maintenanceMessage by remember(maintenanceViewModel, vehicleId) {
                maintenanceViewModel.observeOperationMessage(vehicleId)
            }.collectAsStateWithLifecycle(initialValue = null)

            val vehicle = vehicleList.firstOrNull {
                it.id == vehicleId
            }

            if (vehicle == null) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {

                    Text(
                        "No se encontró el vehículo o no tienes permiso para consultarlo."
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            navController.popBackStack()
                        }
                    ) {
                        Text("Volver")
                    }
                }

            } else {

                MaintenanceHistoryScreen(

                    vehicle = vehicle,

                    records = maintenanceRecords.orEmpty().filter { it.record.vehicleId == vehicleId },

                    isLoading = maintenanceRecords == null,

                    isProcessing = isSavingMaintenance,

                    canManage = isWorkshop,

                    message = maintenanceMessage.takeIf { isWorkshop },

                    onClearMessage = maintenanceViewModel::clearMessage,

                    onAdd = {

                        if (isWorkshop) {
                            navController.navigate(
                                Routes.maintenanceForm(vehicleId)
                            )
                        }
                    },

                    onEdit = { recordId ->

                        if (isWorkshop) {
                            navController.navigate(
                                Routes.maintenanceForm(
                                    vehicleId,
                                    recordId
                                )
                            )
                        }
                    },

                    onDelete = { recordId -> maintenanceViewModel.deleteMaintenance(vehicleId, recordId) },

                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }


        // =================================================
        // 8. FORMULARIO DE MANTENIMIENTO
        // =================================================

        composable(
            route = Routes.MAINTENANCE_FORM,
            arguments = listOf(

                navArgument("vehicleId") {
                    type = NavType.IntType
                },

                navArgument("recordId") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { entry ->

            val vehicleId =
                entry.arguments?.getInt("vehicleId") ?: -1

            val recordId =
                entry.arguments?.getInt("recordId") ?: -1

            val appointmentWorkId by entry.savedStateHandle
                .getStateFlow<Long?>(APPOINTMENT_WORK_ID, null)
                .collectAsStateWithLifecycle()
            val sourceWork = appointmentWork.firstOrNull { it.work.id == appointmentWorkId }?.work
            val sourceAppointment = sourceWork?.let { work ->
                appointments.firstOrNull { appointment ->
                    appointment.clientAuthId == work.clientAuthId && appointment.id == work.appointmentId &&
                        appointment.createdAtMillis == work.appointmentCreatedAtMillis
                }
            }
            val vehicle = vehicleList.firstOrNull {
                it.id == vehicleId
            }

            val editorState by remember(maintenanceViewModel, vehicleId, recordId) {
                maintenanceViewModel.observeEditor(vehicleId, recordId)
            }.collectAsStateWithLifecycle(initialValue = null)
            val recordForRoute = editorState?.record?.takeIf {
                it.record.id == recordId && it.record.vehicleId == vehicleId
            }
            var saveError by rememberSaveable { mutableStateOf<String?>(null) }

            LaunchedEffect(vehicleId, recordId, isWorkshop) {
                if (recordId > 0 && isWorkshop && recordForRoute == null) {
                    maintenanceViewModel.loadRecord(
                        recordId = recordId,
                        vehicleId = vehicleId
                    )
                } else if (recordId <= 0 && isWorkshop) {
                    maintenanceViewModel.clearEditingRecord()
                }
            }

            if (!isWorkshop) {

                AccessDeniedScreen(
                    message = "Solo el taller puede registrar o editar mantenimientos.",
                    onBack = {
                        navController.popBackStack()
                    }
                )

            } else if (appointmentWorkId != null && appointmentWorkState == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            } else if (appointmentWorkId != null && sourceWork?.finishedAtMillis == null) {
                AccessDeniedScreen(
                    message = "No se encontró el cierre de esta cita.",
                    onBack = { navController.popBackStack() }
                )

            } else if (vehicle == null) {

                AccessDeniedScreen(
                    message = "No se encontró el vehículo seleccionado.",
                    onBack = {
                        navController.popBackStack()
                    }
                )

            } else if (
                recordId > 0 &&
                (
                        editorState?.isLoading == true ||
                                (
                                        recordForRoute == null &&
                                                editorState?.error == null
                                        )
                        )
            ) {

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

            } else if (
                recordId > 0 &&
                recordForRoute == null
            ) {

                AccessDeniedScreen(
                    message = editorState?.error
                        ?: "No fue posible cargar el mantenimiento.",
                    onBack = {
                        navController.popBackStack()
                    }
                )

            } else {

                MaintenanceFormScreen(

                    vehicleTitle =
                        "${vehicle.brand} ${vehicle.model} · ${vehicle.plate}",

                    existingRecord = recordForRoute,

                    availableProducts = productEntities,

                    categories = categories,

                    isCreatingProduct = isSavingProduct,

                    isSaving = isSavingMaintenance,

                    saveError = saveError,

                    appointmentServiceType = sourceAppointment?.description,

                    appointmentFinishedAtMillis = sourceWork?.finishedAtMillis,

                    onCreateCategory = { name, onResult ->
                        productViewModel.createCategory(name) { result ->
                            when (result) {
                                is com.example.edmotor.data.repository.ProductOperationResult.Success ->
                                    onResult(true, null)
                                is com.example.edmotor.data.repository.ProductOperationResult.Error ->
                                    onResult(false, result.message)
                            }
                        }
                    },

                    onCreateProduct = { name, description, category, priceCop, stock, imageUri, onResult ->
                        productViewModel.createProduct(
                            name = name,
                            description = description,
                            category = category,
                            priceCop = priceCop,
                            imageRes = productList.firstOrNull()?.imageRes
                                ?: R.drawable.mobil_20_w_50,
                            contentDescription = name,
                            stock = stock,
                            isNew = false,
                            isOnPromotion = false,
                            promotionPriceCop = null,
                            imageUri = imageUri,
                            onResult = { result ->
                                when (result) {
                                    is com.example.edmotor.data.repository.ProductOperationResult.Success ->
                                        onResult(result.productId, null)
                                    is com.example.edmotor.data.repository.ProductOperationResult.Error ->
                                        onResult(null, result.message)
                                }
                            }
                        )
                    },

                    onSave = { draft ->
                        saveError = null
                        maintenanceViewModel.saveMaintenance(

                            vehicleId = vehicleId,

                            recordId = recordId.takeIf {
                                it > 0
                            },

                            draft = draft,

                            appointmentWorkId = appointmentWorkId

                        ) { success, message ->
                            if (navController.currentBackStackEntry == entry) {
                                if (success) navController.popBackStack()
                                else saveError = message ?: "No fue posible guardar el mantenimiento."
                            }
                        }
                    },

                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }


        // =================================================
        // 9. CITAS Y AGENDA DEL TALLER
        // =================================================

        composable(Routes.APPOINTMENTS) { entry ->
            val requestedCalendarDay by entry.savedStateHandle
                .getStateFlow<Long?>(APPOINTMENT_CALENDAR_DAY, null)
                .collectAsStateWithLifecycle()
            var pendingWorkId by rememberSaveable { mutableStateOf<Long?>(null) }
            val selectedWork = appointmentWork.firstOrNull { it.work.id == pendingWorkId }?.work
            val clientId = selectedWork?.let { work ->
                clients.singleOrNull { it.remoteAuthId == work.clientAuthId }?.id
            }
            val eligibleVehicles = if (clientId == null) emptyList()
            else vehicleList.filter { it.ownerUserId == clientId }
            val linkedVehicle = eligibleVehicles.firstOrNull { it.id == selectedWork?.vehicleId }

            fun openMaintenance(vehicleId: Int) {
                val work = selectedWork ?: return
                pendingWorkId = null
                navController.navigate(Routes.maintenanceForm(vehicleId, work.maintenanceRecordId)) {
                    launchSingleTop = true
                }
                navController.currentBackStackEntry?.savedStateHandle?.set(APPOINTMENT_WORK_ID, work.id)
            }

            val startAppointmentMaintenance: (Appointment) -> Unit = { appointment ->
                appointmentViewModel.finishAppointment(appointment) { workId ->
                    if (navController.currentBackStackEntry == entry) pendingWorkId = workId
                }
            }

            LaunchedEffect(selectedWork?.id, linkedVehicle?.id) {
                if (isWorkshop && linkedVehicle != null) openMaintenance(linkedVehicle.id)
            }

            AppointmentsScreen(
                isWorkshop = isWorkshop,
                appointments = appointments,
                syncState = appointmentSyncState,
                vehicles = vehicleList,
                isProcessing = isProcessingAppointment,
                message = appointmentMessage,
                isError = appointmentIsError,
                onCreateAppointment = appointmentViewModel::createAppointment,
                onAccept = appointmentViewModel::acceptAppointment,
                onReject = appointmentViewModel::rejectAppointment,
                onCancel = appointmentViewModel::cancelAppointment,
                onReschedule = appointmentViewModel::rescheduleAppointment,
                onClearMessage = appointmentViewModel::clearMessage,
                onRefresh = appointmentViewModel::refreshAppointments,
                appointmentWork = appointmentWork,
                onFinish = startAppointmentMaintenance,
                onRegisterMaintenance = startAppointmentMaintenance,
                onViewMaintenance = { vehicleId ->
                    navController.navigate(Routes.maintenanceHistory(vehicleId)) { launchSingleTop = true }
                },
                requestedCalendarDay = requestedCalendarDay,
                onCalendarDayHandled = { entry.savedStateHandle.set<Long?>(APPOINTMENT_CALENDAR_DAY, null) }
            )

            if (isWorkshop && selectedWork != null && linkedVehicle == null) {
                AppointmentVehiclePicker(
                    vehicles = eligibleVehicles,
                    onSelect = ::openMaintenance,
                    onDismiss = { pendingWorkId = null }
                )
            }
        }


        // =================================================
        // 10. ADMINISTRACIÓN DE REPUESTOS
        // =================================================

        composable(Routes.PRODUCT_MANAGEMENT) {

            if (isWorkshop) {

                ProductManagementScreen(

                    viewModel = productViewModel,

                    onCreateProduct = {
                        navController.navigate(
                            Routes.PRODUCT_CREATE
                        )
                    },

                    onEditProduct = { product ->

                        navController.navigate(
                            Routes.productEdit(product.id)
                        )
                    },
                    onBack = { navController.popBackStack() }
                )

            } else {

                AccessDeniedScreen(
                    message = "Solo EdMotor puede administrar los repuestos.",
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }


        // =================================================
        // 11. CREAR REPUESTO
        // =================================================

        composable(Routes.PRODUCT_CREATE) { entry ->

            if (isWorkshop) {

                val availableImages = remember {
                    productList.map {
                        it.name to it.imageRes
                    }.distinctBy {
                        it.second
                    }
                }

                ProductFormScreen(

                    product = null,

                    availableImages = availableImages,
                    previousProducts = productEntities,

                    categories = categories,

                    isSaving = isSavingProduct,

                    onCreateCategory = { name, onResult ->
                        productViewModel.createCategory(name) { result ->
                            when (result) {
                                is com.example.edmotor.data.repository.ProductOperationResult.Success ->
                                    onResult(true, null)
                                is com.example.edmotor.data.repository.ProductOperationResult.Error ->
                                    onResult(false, result.message)
                            }
                        }
                    },

                    onSave = {
                            name,
                            description,
                            category,
                            priceCop,
                            imageRes,
                            contentDescription,
                            stock,
                            isNew,
                            isOnPromotion,
                            promotionPriceCop,
                            imageUri,
                            onResult ->

                        productViewModel.createProduct(
                            name = name,
                            description = description,
                            category = category,
                            priceCop = priceCop,
                            imageRes = imageRes,
                            contentDescription = contentDescription,
                            stock = stock,
                            isNew = isNew,
                            isOnPromotion = isOnPromotion,
                            promotionPriceCop = promotionPriceCop,
                            imageUri = imageUri,
                            onResult = onResult
                        )
                    },

                    onUpdate = { _, onResult ->
                        onResult(
                            com.example.edmotor.data.repository.ProductOperationResult.Error(
                                "No se puede actualizar un producto desde el formulario de creación."
                            )
                        )
                    },

                    onCreated = { productId ->
                        if (navController.currentBackStackEntry == entry) {
                            navController.navigate(Routes.productEdit(productId)) {
                                popUpTo(Routes.PRODUCT_CREATE) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },

                    onBack = {
                        if (navController.currentBackStackEntry == entry) navController.popBackStack()
                    }
                )

            } else {

                AccessDeniedScreen(
                    message = "No tienes permiso para crear repuestos.",
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }


        // =================================================
        // 12. EDITAR REPUESTO
        // =================================================

        composable(
            route = Routes.PRODUCT_EDIT,
            arguments = listOf(
                navArgument("productId") {
                    type = NavType.IntType
                }
            )
        ) { entry ->

            val productId =
                entry.arguments?.getInt("productId") ?: -1

            val selectedEntity = productEntities.firstOrNull {
                it.id == productId
            }

            if (!isWorkshop) {

                AccessDeniedScreen(
                    message = "No tienes permiso para modificar repuestos.",
                    onBack = {
                        navController.popBackStack()
                    }
                )

            } else if (selectedEntity == null) {

                AccessDeniedScreen(
                    message = "No encontramos el repuesto solicitado.",
                    onBack = {
                        navController.popBackStack()
                    }
                )

            } else {

                val availableImages = remember {
                    productList.map {
                        it.name to it.imageRes
                    }.distinctBy {
                        it.second
                    }
                }

                ProductFormScreen(

                    product = selectedEntity,

                    availableImages = availableImages,
                    previousProducts = productEntities,

                    categories = categories,

                    isSaving = isSavingProduct,

                    onCreateCategory = { name, onResult ->
                        productViewModel.createCategory(name) { result ->
                            when (result) {
                                is com.example.edmotor.data.repository.ProductOperationResult.Success ->
                                    onResult(true, null)
                                is com.example.edmotor.data.repository.ProductOperationResult.Error ->
                                    onResult(false, result.message)
                            }
                        }
                    },

                    onSave = { _, _, _, _, _, _, _, _, _, _, _, onResult ->
                        onResult(
                            com.example.edmotor.data.repository.ProductOperationResult.Error(
                                "El producto ya existe; utiliza Guardar cambios."
                            )
                        )
                    },

                    onUpdate = { updatedProduct, onResult ->
                        productViewModel.updateProduct(updatedProduct, onResult)
                    },

                    onCreated = { },

                    onBack = {
                        if (navController.currentBackStackEntry == entry) navController.popBackStack()
                    }
                )
            }
        }
    }
}


// =====================================================
// PANTALLA AUXILIAR: ACCESO DENEGADO
// =====================================================

@Composable
private fun AppointmentVehiclePicker(
    vehicles: List<Vehicle>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar mantenimiento") },
        text = {
            if (vehicles.isEmpty()) {
                Text("La cita quedó terminada. Para registrar el mantenimiento, el cliente debe tener su vehículo en Mi Garaje de esta instalación.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selecciona el vehículo atendido en esta cita.")
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(vehicles, key = { it.id }) { vehicle ->
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onSelect(vehicle.id) }
                            ) {
                                Text(listOf("${vehicle.brand} ${vehicle.model}", "${vehicle.year}", vehicle.plate)
                                    .filter { it.isNotBlank() }.joinToString(" · "))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun AccessDeniedScreen(
    message: String,
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Acceso restringido",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onBack
        ) {
            Text("Volver")
        }
    }
}
