
package com.example.edmotor.ui.theme

// ==============================================
// 1. IMPORTACIONES
// ==============================================

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DirectionsCar

import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.platform.LocalContext

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import com.example.edmotor.data.categoryList
import com.example.edmotor.data.productList

import com.example.edmotor.data.local.EdMotorDatabase

import com.example.edmotor.data.repository.VehicleRepository
import com.example.edmotor.data.repository.CartRepository
import com.example.edmotor.data.repository.FavoriteRepository

import com.example.edmotor.navigation.Routes

import com.example.edmotor.ui.screens.HomeScreen
import com.example.edmotor.ui.screens.CartScreen
import com.example.edmotor.ui.screens.GarageScreen
import com.example.edmotor.ui.screens.DiagnosticScreen
import com.example.edmotor.ui.screens.ServicesScreen
import com.example.edmotor.ui.screens.ProductDetailScreen

import com.example.edmotor.ui.theme.components.EdMotorBottomBar

import com.example.edmotor.viewmodel.EdMotorViewModel
import com.example.edmotor.viewmodel.EdMotorViewModelFactory


// ==============================================
// 2. APLICACIÓN PRINCIPAL
// ==============================================

@Composable
fun EdMotorApp() {

    // ==========================================
    // OBTENER CONTEXTO
    // ==========================================

    val context = LocalContext.current.applicationContext


    // ==========================================
    // BASE DE DATOS ROOM
    // ==========================================

    val database = remember(context) {

        EdMotorDatabase.getDatabase(context)

    }


    // ==========================================
    // VEHICLE REPOSITORY
    // ==========================================

    val vehicleRepository = remember(database) {

        VehicleRepository(
            dao = database.vehicleDao()
        )

    }


    // ==========================================
    // CART REPOSITORY
    // ==========================================

    val cartRepository = remember(database) {

        CartRepository(
            dao = database.cartDao()
        )

    }


    // ==========================================
    // FAVORITE REPOSITORY
    // ==========================================

    val favoriteRepository = remember(database) {

        FavoriteRepository(
            dao = database.favoriteProductDao()
        )

    }


    // ==========================================
    // VIEWMODEL FACTORY
    // ==========================================

    val factory = remember(
        vehicleRepository,
        cartRepository,
        favoriteRepository
    ) {

        EdMotorViewModelFactory(

            vehicleRepository = vehicleRepository,

            cartRepository = cartRepository,

            favoriteRepository = favoriteRepository

        )

    }


    // ==========================================
    // OBTENER VIEWMODEL
    // ==========================================

    val appViewModel: EdMotorViewModel = viewModel(
        factory = factory
    )


    // ==========================================
    // MOSTRAR CONTENIDO
    // ==========================================

    EdMotorAppContent(
        appViewModel = appViewModel
    )

}


// ==============================================
// 3. CONTENIDO PRINCIPAL DE EDMOTOR
// ==============================================

@Composable
private fun EdMotorAppContent(
    appViewModel: EdMotorViewModel
) {

    // ==========================================
    // OBSERVAR ESTADOS DEL VIEWMODEL
    // ==========================================

    val searchQuery by appViewModel.searchQuery
        .collectAsStateWithLifecycle()

    val selectedCategory by appViewModel.selectedCategory
        .collectAsStateWithLifecycle()

    val cartQuantity by appViewModel.cartQuantity
        .collectAsStateWithLifecycle()

    val vehicleList by appViewModel.vehicles
        .collectAsStateWithLifecycle()

    val favoriteProductIds by appViewModel.favoriteProductIds
        .collectAsStateWithLifecycle()


    // ==========================================
    // CONTADOR TOTAL DEL CARRITO
    // ==========================================

    val carCount = cartQuantity.values.sum()


    // ==========================================
    // CONTROL DE NAVEGACIÓN
    // ==========================================

    val navController = rememberNavController()

    val navBackStackEntry by
    navController.currentBackStackEntryAsState()

    val currentRoute = navBackStackEntry?.destination?.route


    // ==========================================
    // VISIBILIDAD DE LA BARRA INFERIOR
    // ==========================================

    val bottomBarVisibleState = remember {
        mutableStateOf(true)
    }

    val bottomBarVisible by bottomBarVisibleState


    // ==========================================
    // OCULTAR AL BAJAR Y MOSTRAR AL SUBIR
    // ==========================================

    val nestedScrollConnection = remember {

        object : NestedScrollConnection {

            override fun onPostScroll(

                consumed: Offset,

                available: Offset,

                source: NestedScrollSource

            ): Offset {

                val deltaY = consumed.y + available.y

                when {

                    deltaY < -2f -> {

                        bottomBarVisibleState.value = false

                    }

                    deltaY > 2f -> {

                        bottomBarVisibleState.value = true

                    }

                }

                return Offset.Zero

            }

        }

    }


    // ==========================================
    // RESTABLECER BARRA AL CAMBIAR DE PESTAÑA
    // ==========================================

    LaunchedEffect(currentRoute) {

        bottomBarVisibleState.value = true

    }


    // ==========================================
    // FUNCIÓN DE NAVEGACIÓN
    // ==========================================

    fun navigateTo(route: String) {

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
    // 4. SCAFFOLD PRINCIPAL
    // ==========================================

    Scaffold(

        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),


        // ======================================
        // ENCABEZADO SUPERIOR
        // ======================================

        topBar = {

            // En el detalle utilizamos su propia
            // barra superior con el botón de volver.

            if (currentRoute != Routes.PRODUCT_DETAIL) {

                EdMotorTopBar()

            }

        },


        // ======================================
        // BARRA DE NAVEGACIÓN INFERIOR
        // ======================================

        bottomBar = {

            AnimatedVisibility(

                visible = bottomBarVisible &&
                        currentRoute != Routes.PRODUCT_DETAIL,

                enter = slideInVertically(

                    animationSpec = tween(
                        durationMillis = 250
                    ),

                    initialOffsetY = { fullHeight ->
                        fullHeight
                    }

                ) + expandVertically(

                    animationSpec = tween(
                        durationMillis = 250
                    ),

                    expandFrom = Alignment.Bottom

                ) + fadeIn(

                    animationSpec = tween(
                        durationMillis = 180
                    )

                ),

                exit = slideOutVertically(

                    animationSpec = tween(
                        durationMillis = 250
                    ),

                    targetOffsetY = { fullHeight ->
                        fullHeight
                    }

                ) + shrinkVertically(

                    animationSpec = tween(
                        durationMillis = 250
                    ),

                    shrinkTowards = Alignment.Bottom

                ) + fadeOut(

                    animationSpec = tween(
                        durationMillis = 180
                    )

                )

            ) {

                EdMotorBottomBar(

                    currentRoute = currentRoute,

                    cartCount = carCount,

                    onNavigate = { route ->

                        navigateTo(route)

                    }

                )

            }

        },


        // ======================================
        // CARRITO FLOTANTE
        // ======================================

        floatingActionButton = {

            AnimatedVisibility(

                visible = carCount > 0 &&
                        currentRoute != Routes.CAR &&
                        currentRoute != Routes.PRODUCT_DETAIL,

                enter = fadeIn() +
                        scaleIn(
                            initialScale = 0.8f
                        ),

                exit = fadeOut() +
                        scaleOut(
                            targetScale = 0.8f
                        )

            ) {

                ExtendedFloatingActionButton(

                    onClick = {

                        navigateTo(Routes.CAR)

                    },

                    icon = {

                        Icon(

                            imageVector = Icons.Filled.ShoppingCart,

                            contentDescription = "Abrir carrito"

                        )

                    },

                    text = {

                        Text(

                            text = if (carCount > 99) {

                                "Carrito · 99+"

                            } else {

                                "Carrito · $carCount"

                            }

                        )

                    },

                    containerColor =
                        MaterialTheme.colorScheme.primary,

                    contentColor =
                        MaterialTheme.colorScheme.onPrimary

                )

            }

        }

    ) { innerPadding ->


        // ======================================
        // 5. SISTEMA DE NAVEGACIÓN
        // ======================================

        NavHost(

            navController = navController,

            startDestination = Routes.HOME,

            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)

        ) {


            // ==================================
            // INICIO - CATÁLOGO
            // ==================================

            composable(Routes.HOME) {

                HomeScreen(

                    searchQuery = searchQuery,

                    onSearchChange = { newText ->

                        appViewModel.updateSearchQuery(newText)

                    },

                    categories = categoryList,

                    product = productList,

                    cartQuantity = cartQuantity,

                    selectedCategory = selectedCategory,

                    favoriteProductIds = favoriteProductIds,


                    // FAVORITOS

                    onFavoriteClick = { productId ->

                        appViewModel.toggleFavorite(productId)

                    },


                    // NUEVO: ABRIR DETALLE DEL PRODUCTO

                    onProductClick = { productId ->

                        navController.navigate(

                            Routes.productDetail(productId)

                        ) {

                            launchSingleTop = true

                        }

                    },


                    // CATEGORÍAS

                    onCategorySelect = { categoryName ->

                        appViewModel.toggleCategory(categoryName)

                    },


                    // AUMENTAR CANTIDAD

                    onIncrease = { productId ->

                        appViewModel.increaseQuantity(productId)

                    },


                    // DISMINUIR CANTIDAD

                    onDecrease = { productId ->

                        appViewModel.decreaseQuantity(productId)

                    }

                )

            }


            // ==================================
            // DETALLE DEL PRODUCTO
            // ==================================

            composable(

                route = Routes.PRODUCT_DETAIL,

                arguments = listOf(

                    navArgument("productId") {

                        type = NavType.IntType

                    }

                )

            ) { backStackEntry ->


                // ==================================
                // RECUPERAR ID DEL PRODUCTO
                // ==================================

                val productId = backStackEntry.arguments
                    ?.getInt("productId") ?: -1


                // ==================================
                // BUSCAR PRODUCTO EN EL CATÁLOGO
                // ==================================

                val selectedProduct = productList.firstOrNull {

                    it.id == productId

                }


                // ==================================
                // MOSTRAR DETALLE
                // ==================================

                if (selectedProduct != null) {

                    ProductDetailScreen(

                        product = selectedProduct,


                        // CANTIDAD DEL PRODUCTO EN CARRITO

                        quantity = cartQuantity[
                            selectedProduct.id
                        ] ?: 0,


                        // CONTADOR GENERAL DEL CARRITO

                        cartCount = carCount,


                        // ESTADO DE FAVORITOS

                        isFavorite =
                            selectedProduct.id in favoriteProductIds,


                        // VOLVER

                        onBack = {

                            navController.popBackStack()

                        },


                        // AGREGAR O QUITAR FAVORITO

                        onFavoriteClick = {

                            appViewModel.toggleFavorite(
                                selectedProduct.id
                            )

                        },


                        // AUMENTAR CANTIDAD

                        onIncrease = {

                            appViewModel.increaseQuantity(
                                selectedProduct.id
                            )

                        },


                        // DISMINUIR CANTIDAD

                        onDecrease = {

                            appViewModel.decreaseQuantity(
                                selectedProduct.id
                            )

                        },


                        // ABRIR CARRITO

                        onOpenCart = {

                            navigateTo(Routes.CAR)

                        }

                    )

                } else {


                    // ==================================
                    // PRODUCTO NO ENCONTRADO
                    // ==================================

                    Column(

                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),

                        verticalArrangement =
                            Arrangement.Center,

                        horizontalAlignment =
                            Alignment.CenterHorizontally

                    ) {

                        Text(

                            text = "No encontramos este producto.",

                            style = MaterialTheme.typography.titleMedium,

                            fontWeight = FontWeight.Bold

                        )

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

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


            // ==================================
            // CARRITO
            // ==================================

            composable(Routes.CAR) {

                CartScreen(

                    products = productList,

                    cartQuantity = cartQuantity,

                    onIncrease = { productId ->

                        appViewModel.increaseQuantity(productId)

                    },

                    onDecrease = { productId ->

                        appViewModel.decreaseQuantity(productId)

                    }

                )

            }


            // ==================================
            // SERVICIOS
            // ==================================

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


            // ==================================
            // MI GARAJE
            // ==================================

            composable(Routes.PROFILE) {

                GarageScreen(

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

                    onDeleteVehicle = { vehicleId ->

                        appViewModel.deleteVehicle(vehicleId)

                    },

                    onUpdateNotes = {
                            vehicleId,
                            newNotes ->

                        appViewModel.updateVehicleNotes(

                            vehicleId = vehicleId,

                            newNotes = newNotes

                        )

                    },

                    onUpdateMileage = {
                            vehicleId,
                            newMileage ->

                        appViewModel.updateVehicleMileage(

                            vehicleId = vehicleId,

                            newMileage = newMileage

                        )

                    }

                )

            }


            // ==================================
            // DIAGNÓSTICO
            // ==================================

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

}


// ==============================================
// 6. PREVIEW
// ==============================================

@Preview(showBackground = true)
@Composable
fun EdMotorPreview() {

    EdMotorTheme {

        EdMotorApp()

    }

}


// ==============================================
// 7. ENCABEZADO PROFESIONAL EDMOTOR
// ==============================================

@Composable
fun EdMotorTopBar() {

    val graphite = Color(0xFF17212F)

    val electricBlue = Color(0xFF0878F9)

    val secondaryText = Color(0xFF64748B)


    Surface(

        modifier = Modifier.fillMaxWidth(),

        color = Color.White,

        shadowElevation = 4.dp

    ) {

        Column(

            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()

        ) {

            Row(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 18.dp,
                        vertical = 12.dp
                    ),

                verticalAlignment = Alignment.CenterVertically

            ) {


                // ==================================
                // LOGO AUTOMOTRIZ
                // ==================================

                Box(

                    modifier = Modifier
                        .size(49.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(

                            Brush.linearGradient(

                                colors = listOf(

                                    Color(0xFF0066FF),

                                    Color(0xFF269BFF)

                                )

                            )

                        ),

                    contentAlignment = Alignment.Center

                ) {

                    Icon(

                        imageVector =
                            Icons.Outlined.DirectionsCar,

                        contentDescription = "EdMotor",

                        tint = Color.White,

                        modifier = Modifier.size(30.dp)

                    )

                }


                Spacer(
                    modifier = Modifier.width(12.dp)
                )


                // ==================================
                // IDENTIDAD DE MARCA
                // ==================================

                Column(

                    modifier = Modifier.weight(1f),

                    verticalArrangement = Arrangement.Center

                ) {

                    Text(

                        text = "ED MOTOR",

                        fontSize = 21.sp,

                        fontWeight = FontWeight.ExtraBold,

                        letterSpacing = 1.2.sp,

                        color = graphite

                    )

                    Text(

                        text =
                            "REPUESTOS • SERVICIOS • DIAGNÓSTICO",

                        fontSize = 8.sp,

                        fontWeight = FontWeight.SemiBold,

                        letterSpacing = 0.35.sp,

                        color = secondaryText,

                        maxLines = 1

                    )

                }


                Spacer(
                    modifier = Modifier.width(8.dp)
                )


                // ==================================
                // IDENTIDAD MECÁNICA
                // ==================================

                Surface(

                    shape = RoundedCornerShape(13.dp),

                    color = Color(0xFFEAF2FF)

                ) {

                    Box(

                        modifier = Modifier.size(42.dp),

                        contentAlignment = Alignment.Center

                    ) {

                        Icon(

                            imageVector = Icons.Outlined.Build,

                            contentDescription =
                                "Servicios automotrices",

                            tint = electricBlue,

                            modifier = Modifier.size(23.dp)

                        )

                    }

                }

            }


            // ==================================
            // LÍNEA DECORATIVA AZUL
            // ==================================

            Box(

                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(

                        Brush.horizontalGradient(

                            colors = listOf(

                                graphite,

                                electricBlue,

                                Color(0xFF54B5FF)

                            )

                        )

                    )

            )

        }

    }

}
