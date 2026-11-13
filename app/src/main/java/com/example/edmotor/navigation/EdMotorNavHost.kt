package com.example.edmotor.navigation

import android.widget.Toast

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

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

import com.example.edmotor.model.UserRole

import com.example.edmotor.ui.screens.CartScreen
import com.example.edmotor.ui.screens.ClientGarageScreen
import com.example.edmotor.ui.screens.HomeScreen
import com.example.edmotor.ui.screens.MaintenanceFormScreen
import com.example.edmotor.ui.screens.MaintenanceHistoryScreen
import com.example.edmotor.ui.screens.ProductDetailScreen
import com.example.edmotor.ui.screens.ServicesScreen
import com.example.edmotor.ui.screens.WorkshopGarageScreen
import com.example.edmotor.ui.screens.ProductManagementScreen
import com.example.edmotor.ui.screens.ProductFormScreen
import com.example.edmotor.ui.screens.OrdersManagementScreen
import com.example.edmotor.ui.screens.AppointmentsScreen

import com.example.edmotor.viewmodel.EdMotorViewModel
import com.example.edmotor.viewmodel.MaintenanceViewModel
import com.example.edmotor.viewmodel.ProductViewModel
import com.example.edmotor.viewmodel.OrderViewModel
import com.example.edmotor.viewmodel.AppointmentViewModel


/**
 * Navegación principal de EdMotor.
 *
 * Incluye:
 * - Catálogo dinámico.
 * - Detalle de productos.
 * - Carrito.
 * - Envío de pedidos.
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
    currentUserRole: String,
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

    val cartQuantity by appViewModel.cartQuantity
        .collectAsStateWithLifecycle()

    val cartMessage by appViewModel.cartMessage
        .collectAsStateWithLifecycle()

    val vehicleList by appViewModel.vehicles
        .collectAsStateWithLifecycle()

    val favoriteProductIds by appViewModel.favoriteProductIds
        .collectAsStateWithLifecycle()


    // =====================================================
    // 2. ESTADOS DE MANTENIMIENTO
    // =====================================================

    val maintenanceRecords by maintenanceViewModel.records
        .collectAsStateWithLifecycle()

    val editingRecord by maintenanceViewModel.editingRecord
        .collectAsStateWithLifecycle()

    val isLoadingRecord by maintenanceViewModel.isLoadingRecord
        .collectAsStateWithLifecycle()

    val editingRecordError by maintenanceViewModel.editingRecordError
        .collectAsStateWithLifecycle()


    // =====================================================
    // 3. ESTADOS DEL INVENTARIO
    // =====================================================

    val productEntities by productViewModel.products
        .collectAsStateWithLifecycle()

    val categories by productViewModel.categories
        .collectAsStateWithLifecycle()

    val clients by appViewModel.clients
        .collectAsStateWithLifecycle()

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
        .collectAsStateWithLifecycle()

    val isProcessingOrder by orderViewModel.isProcessing
        .collectAsStateWithLifecycle()

    val orderMessage by orderViewModel.message
        .collectAsStateWithLifecycle()

    val orderIsError by orderViewModel.isError
        .collectAsStateWithLifecycle()

    // =====================================================
    // 5. ESTADOS DE CITAS
    // =====================================================

    val appointments by appointmentViewModel.appointments
        .collectAsStateWithLifecycle()

    val isProcessingAppointment by appointmentViewModel.isProcessing
        .collectAsStateWithLifecycle()

    val appointmentMessage by appointmentViewModel.message
        .collectAsStateWithLifecycle()

    val appointmentIsError by appointmentViewModel.isError
        .collectAsStateWithLifecycle()


    // =====================================================
    // 6. CONFIGURACIÓN GENERAL
    // =====================================================

    val isWorkshop =
        UserRole.from(currentUserRole) == UserRole.WORKSHOP

    val context = LocalContext.current

    val carCount = cartQuantity.values.sum()


    // =====================================================
    // 6. MENSAJES PARA EL CLIENTE
    // =====================================================

    LaunchedEffect(orderMessage, isWorkshop) {

        if (!isWorkshop && !orderMessage.isNullOrBlank()) {

            Toast.makeText(
                context,
                orderMessage.orEmpty(),
                Toast.LENGTH_LONG
            ).show()

            orderViewModel.clearMessage()
        }
    }

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
                    productViewModel.adjustStock(productId, delta)
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
                }
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

                    cartCount = if (isWorkshop) 0 else carCount,

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
        // 3. CARRITO Y ENVÍO DE PEDIDOS
        // =================================================

        composable(Routes.CAR) {
            if (isWorkshop) {
                AccessDeniedScreen(
                    message = "El perfil de EdMotor administra el inventario y no utiliza el carrito de compras.",
                    onBack = { navController.popBackStack() }
                )
            } else {
                CartScreen(
                    products = catalogProducts,
                    cartQuantity = cartQuantity,
                    onIncrease = appViewModel::increaseQuantity,
                    onDecrease = appViewModel::decreaseQuantity,
                    onSubmitOrder = { orderViewModel.submitOrder() }
                )
            }
        }


        // =================================================
        // 4. SERVICIOS
        // =================================================

        composable(Routes.SERVICES) {

            ServicesScreen(

                onOpenGarage = {
                    navController.navigate(Routes.PROFILE)
                },

                onOpenAppointments = {
                    navController.navigate(Routes.APPOINTMENTS)
                },

                onOpenCatalog = {
                    navController.navigate(Routes.HOME)
                }
            )
        }


        // =================================================
        // 5. GARAJE / PERFIL
        // =================================================

        composable(Routes.PROFILE) {

            if (isWorkshop) {

                WorkshopGarageScreen(

                    vehicles = vehicleList,

                    clients = clients,

                    orders = orders,

                    onVehicleClick = { vehicleId ->

                        navController.navigate(
                            Routes.maintenanceHistory(vehicleId)
                        )
                    },

                    // ACCESO A LOS PEDIDOS RECIBIDOS

                    onOpenOrders = {

                        navController.navigate(
                            Routes.ORDER_MANAGEMENT
                        ) {
                            launchSingleTop = true
                        }
                    },

                    onLogout = onLogout
                )

            } else {

                ClientGarageScreen(

                    vehicles = vehicleList,

                    orders = orders,

                    onAddVehicle = {
                            brand,
                            model,
                            year,
                            engine,
                            plate,
                            mileage,
                            notes ->

                        appViewModel.addVehicle(
                            brand = brand,
                            model = model,
                            year = year,
                            engine = engine,
                            plate = plate,
                            mileage = mileage,
                            notes = notes
                        )
                    },

                    onVehicleClick = { vehicleId ->

                        navController.navigate(
                            Routes.maintenanceHistory(vehicleId)
                        )
                    },

                    onLogout = onLogout
                )
            }
        }


        // =================================================
        // 6. ADMINISTRACIÓN DE PEDIDOS Y VENTAS
        // =================================================

        composable(Routes.ORDER_MANAGEMENT) {

            if (isWorkshop) {

                OrdersManagementScreen(

                    orders = orders,

                    isProcessing = isProcessingOrder,

                    message = orderMessage,

                    isError = orderIsError,

                    onConfirm = orderViewModel::confirmOrder,

                    onReject = orderViewModel::rejectOrder,

                    onBack = {
                        navController.popBackStack()
                    }
                )

            } else {

                AccessDeniedScreen(
                    message = "Solo el taller puede administrar pedidos.",
                    onBack = {
                        navController.popBackStack()
                    }
                )
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

            LaunchedEffect(vehicleId) {
                maintenanceViewModel.selectVehicle(vehicleId)
            }

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

                    records = maintenanceRecords,

                    canManage = isWorkshop,

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

                    onDelete = maintenanceViewModel::deleteMaintenance,

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

            val vehicle = vehicleList.firstOrNull {
                it.id == vehicleId
            }

            LaunchedEffect(vehicleId, recordId) {

                maintenanceViewModel.selectVehicle(vehicleId)

                if (recordId > 0 && isWorkshop) {

                    maintenanceViewModel.loadRecord(
                        recordId = recordId,
                        vehicleId = vehicleId
                    )

                } else {

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
                        isLoadingRecord ||
                                (
                                        editingRecord == null &&
                                                editingRecordError == null
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
                editingRecord == null
            ) {

                AccessDeniedScreen(
                    message = editingRecordError
                        ?: "No fue posible cargar el mantenimiento.",
                    onBack = {
                        navController.popBackStack()
                    }
                )

            } else {

                MaintenanceFormScreen(

                    vehicleTitle =
                        "${vehicle.brand} ${vehicle.model} · ${vehicle.plate}",

                    existingRecord =
                        if (recordId > 0) editingRecord else null,

                    availableProducts = productEntities,

                    categories = categories,

                    isCreatingProduct = isSavingProduct,

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

                        maintenanceViewModel.saveMaintenance(

                            vehicleId = vehicleId,

                            recordId = recordId.takeIf {
                                it > 0
                            },

                            draft = draft

                        ) { success, message ->

                            if (success) {

                                navController.popBackStack()

                            } else {

                                Toast.makeText(
                                    context,
                                    message ?: "No fue posible guardar.",
                                    Toast.LENGTH_LONG
                                ).show()
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

        composable(Routes.APPOINTMENTS) {

            AppointmentsScreen(
                isWorkshop = isWorkshop,
                appointments = appointments,
                vehicles = vehicleList,
                isProcessing = isProcessingAppointment,
                message = appointmentMessage,
                isError = appointmentIsError,
                onCreateAppointment = { vehicle, scheduledAtMillis, description ->
                    appointmentViewModel.createAppointment(
                        vehicle = vehicle,
                        scheduledAtMillis = scheduledAtMillis,
                        description = description
                    )
                },
                onAccept = appointmentViewModel::acceptAppointment,
                onReject = appointmentViewModel::rejectAppointment,
                onClearMessage = appointmentViewModel::clearMessage
            )
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
                    }
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

        composable(Routes.PRODUCT_CREATE) {

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
                        navController.navigate(Routes.productEdit(productId)) {
                            popUpTo(Routes.PRODUCT_CREATE) { inclusive = true }
                            launchSingleTop = true
                        }
                    },

                    onBack = {
                        navController.popBackStack()
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
                        navController.popBackStack()
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
