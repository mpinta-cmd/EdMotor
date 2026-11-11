
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

import com.example.edmotor.data.categoryList
import com.example.edmotor.data.productList

import com.example.edmotor.model.UserRole

import com.example.edmotor.ui.screens.CartScreen
import com.example.edmotor.ui.screens.ClientGarageScreen
import com.example.edmotor.ui.screens.DiagnosticScreen
import com.example.edmotor.ui.screens.HomeScreen
import com.example.edmotor.ui.screens.MaintenanceFormScreen
import com.example.edmotor.ui.screens.MaintenanceHistoryScreen
import com.example.edmotor.ui.screens.ProductDetailScreen
import com.example.edmotor.ui.screens.ServicesScreen
import com.example.edmotor.ui.screens.WorkshopGarageScreen

import com.example.edmotor.viewmodel.EdMotorViewModel
import com.example.edmotor.viewmodel.MaintenanceViewModel


/**
 * Grafo principal de navegación de EdMotor.
 *
 * Conecta las pantallas con sus respectivos ViewModels,
 * administra la navegación y controla los permisos
 * según el tipo de usuario.
 */

@Composable
fun EdMotorNavHost(
    navController: NavHostController,
    currentUserRole: String,
    appViewModel: EdMotorViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    onLogout: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {

    // =====================================================
    // ESTADOS GENERALES DE LA APLICACIÓN
    // =====================================================

    val searchQuery by appViewModel.searchQuery.collectAsStateWithLifecycle()

    val selectedCategory by appViewModel.selectedCategory.collectAsStateWithLifecycle()

    val cartQuantity by appViewModel.cartQuantity.collectAsStateWithLifecycle()

    val vehicleList by appViewModel.vehicles.collectAsStateWithLifecycle()

    val favoriteProductIds by appViewModel.favoriteProductIds.collectAsStateWithLifecycle()


    // =====================================================
    // ESTADOS DE MANTENIMIENTO
    // =====================================================

    val maintenanceRecords by maintenanceViewModel.records.collectAsStateWithLifecycle()

    val editingRecord by maintenanceViewModel.editingRecord.collectAsStateWithLifecycle()

    val isLoadingRecord by maintenanceViewModel.isLoadingRecord.collectAsStateWithLifecycle()

    val editingRecordError by maintenanceViewModel.editingRecordError.collectAsStateWithLifecycle()


    // =====================================================
    // CONFIGURACIÓN GENERAL
    // =====================================================

    val isWorkshop =
        UserRole.from(currentUserRole) == UserRole.WORKSHOP

    val context = LocalContext.current

    val carCount = cartQuantity.values.sum()

    val navigateTo: (String) -> Unit = onNavigate


    // =====================================================
    // NAVEGACIÓN PRINCIPAL
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

                categories = categoryList,

                product = productList,

                cartQuantity = cartQuantity,

                selectedCategory = selectedCategory,

                favoriteProductIds = favoriteProductIds,

                onFavoriteClick = appViewModel::toggleFavorite,

                onProductClick = { productId ->

                    navController.navigate(
                        Routes.productDetail(productId)
                    ) {
                        launchSingleTop = true
                    }
                },

                onCategorySelect = appViewModel::toggleCategory,

                onIncrease = appViewModel::increaseQuantity,

                onDecrease = appViewModel::decreaseQuantity
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

            val selectedProduct = productList.firstOrNull {
                it.id == productId
            }

            if (selectedProduct != null) {

                ProductDetailScreen(

                    product = selectedProduct,

                    quantity = cartQuantity[selectedProduct.id] ?: 0,

                    cartCount = carCount,

                    isFavorite = selectedProduct.id in favoriteProductIds,

                    onBack = {
                        navController.popBackStack()
                    },

                    onFavoriteClick = {
                        appViewModel.toggleFavorite(selectedProduct.id)
                    },

                    onIncrease = {
                        appViewModel.increaseQuantity(selectedProduct.id)
                    },

                    onDecrease = {
                        appViewModel.decreaseQuantity(selectedProduct.id)
                    },

                    onOpenCart = {
                        navigateTo(Routes.CAR)
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
        // 3. CARRITO
        // =================================================

        composable(Routes.CAR) {

            CartScreen(

                products = productList,

                cartQuantity = cartQuantity,

                onIncrease = appViewModel::increaseQuantity,

                onDecrease = appViewModel::decreaseQuantity
            )
        }


        // =================================================
        // 4. SERVICIOS
        // =================================================

        composable(Routes.SERVICES) {

            ServicesScreen(

                onOpenGarage = {
                    navigateTo(Routes.PROFILE)
                },

                onOpenDiagnosis = {
                    navigateTo(Routes.DIAGNOSTIC)
                },

                onOpenCatalog = {
                    navigateTo(Routes.HOME)
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

                    onVehicleClick = { vehicleId ->

                        navigateTo(
                            Routes.maintenanceHistory(vehicleId)
                        )
                    },

                    onLogout = onLogout
                )

            } else {

                ClientGarageScreen(

                    vehicles = vehicleList,

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

                        navigateTo(
                            Routes.maintenanceHistory(vehicleId)
                        )
                    },

                    onLogout = onLogout
                )
            }
        }


        // =================================================
        // 6. HISTORIAL DE MANTENIMIENTO
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

                        navController.navigate(
                            Routes.maintenanceForm(vehicleId)
                        )
                    },

                    onEdit = { recordId ->

                        navController.navigate(
                            Routes.maintenanceForm(
                                vehicleId,
                                recordId
                            )
                        )
                    },

                    onDelete = maintenanceViewModel::deleteMaintenance,

                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }


        // =================================================
        // 7. FORMULARIO DE MANTENIMIENTO
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


            // ---------------------------------------------
            // CARGA DEL REGISTRO SELECCIONADO
            // ---------------------------------------------

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


            // ---------------------------------------------
            // CONTROL DE PERMISOS
            // ---------------------------------------------

            if (!isWorkshop) {

                Column(

                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),

                    verticalArrangement = Arrangement.Center

                ) {

                    Text(
                        "Solo el taller puede registrar o editar mantenimientos."
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


                // ---------------------------------------------
                // VEHÍCULO NO ENCONTRADO
                // ---------------------------------------------

            } else if (vehicle == null) {

                Column(

                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),

                    verticalArrangement = Arrangement.Center

                ) {

                    Text(
                        "No se encontró el vehículo seleccionado."
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


                // ---------------------------------------------
                // CARGANDO MANTENIMIENTO
                // ---------------------------------------------

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


                // ---------------------------------------------
                // ERROR AL CARGAR EL REGISTRO
                // ---------------------------------------------

            } else if (
                recordId > 0 &&
                editingRecord == null
            ) {

                Column(

                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),

                    verticalArrangement = Arrangement.Center,

                    horizontalAlignment = Alignment.CenterHorizontally

                ) {

                    Text(
                        editingRecordError
                            ?: "No fue posible cargar el mantenimiento.",

                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            navController.popBackStack()
                        }
                    ) {

                        Text("Volver al historial")
                    }
                }


                // ---------------------------------------------
                // FORMULARIO DE REGISTRO O EDICIÓN
                // ---------------------------------------------

            } else {

                MaintenanceFormScreen(

                    vehicleTitle =
                        "${vehicle.brand} ${vehicle.model} · ${vehicle.plate}",

                    existingRecord =
                        if (recordId > 0) editingRecord else null,

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
        // 8. DIAGNÓSTICO
        // =================================================

        composable(Routes.DIAGNOSTIC) {

            DiagnosticScreen(

                onNavigateToCategory = { categoryName ->

                    appViewModel.selectCategory(categoryName)

                    navigateTo(Routes.HOME)
                }
            )
        }
    }
}
