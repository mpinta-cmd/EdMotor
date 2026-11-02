package com.example.edmotor.ui.theme

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.edmotor.data.categoryList
import com.example.edmotor.data.productList
import androidx.compose.ui.platform.LocalContext
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.repository.VehicleRepository
import com.example.edmotor.viewmodel.EdMotorViewModelFactory
import com.example.edmotor.navigation.Routes
import com.example.edmotor.ui.screens.CartScreen
import com.example.edmotor.ui.screens.DiagnosticScreen
import com.example.edmotor.ui.screens.GarageScreen
import com.example.edmotor.ui.screens.HomeScreen
import com.example.edmotor.ui.screens.ServicesScreen
import com.example.edmotor.ui.theme.components.EdMotorBottomBar
import com.example.edmotor.viewmodel.EdMotorViewModel
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.edmotor.data.repository.CartRepository
import com.example.edmotor.data.repository.FavoriteRepository





@Composable
fun EdMotorApp() {

    // ==========================================
    // 1. OBTENER CONTEXTO DE LA APLICACIÓN
    // ==========================================

    val context = LocalContext.current.applicationContext


    // ==========================================
    // 2. OBTENER BASE DE DATOS ROOM
    // ==========================================

    val database = remember(context) {

        EdMotorDatabase.getDatabase(context)

    }


    // ==========================================
    // 3. CREAR VEHICLE REPOSITORY
    // ==========================================

    val vehicleRepository = remember(database) {

        VehicleRepository(
            dao = database.vehicleDao()
        )

    }


    // ==========================================
    // 4. CREAR CART REPOSITORY
    // ==========================================

    val cartRepository = remember(database) {

        CartRepository(
            dao = database.cartDao()
        )

    }


    // ==========================================
    // 5. CREAR FAVORITE REPOSITORY
    // ==========================================

    val favoriteRepository = remember(database) {

        FavoriteRepository(
            dao = database.favoriteProductDao()
        )

    }


    // ==========================================
    // 6. CREAR VIEWMODEL FACTORY
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
    // 7. OBTENER VIEWMODEL
    // ==========================================

    val appViewModel: EdMotorViewModel = viewModel(
        factory = factory
    )


    // ==========================================
    // 8. MOSTRAR APLICACIÓN
    // ==========================================

    EdMotorAppContent(
        appViewModel = appViewModel
    )
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EdMotorAppContent(appViewModel: EdMotorViewModel) {

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


    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarVisibleState = remember { mutableStateOf(true) }
    val bottomBarVisible by bottomBarVisibleState

    // Conservamos el comportamiento de ocultarse al bajar y mostrarse al subir.
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val deltaY = consumed.y + available.y
                when {
                    deltaY < -2f -> bottomBarVisibleState.value = false
                    deltaY > 2f -> bottomBarVisibleState.value = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(currentRoute) {
        bottomBarVisibleState.value = true
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),

        topBar = {
            EdMotorTopBar()
        },

        // BARRA DE NAVEGACIÓN INFERIOR
        bottomBar = {
            AnimatedVisibility(
                visible = bottomBarVisible,
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 250),
                    initialOffsetY = { fullHeight -> fullHeight }
                ) + expandVertically(
                    animationSpec = tween(durationMillis = 250),
                    expandFrom = Alignment.Bottom
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 180)
                ),
                exit = slideOutVertically(
                    animationSpec = tween(durationMillis = 250),
                    targetOffsetY = { fullHeight -> fullHeight }
                ) + shrinkVertically(
                    animationSpec = tween(durationMillis = 250),
                    shrinkTowards = Alignment.Bottom
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 180)
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

        // CARRITO FLOTANTE
        floatingActionButton = {

            AnimatedVisibility(
                visible = carCount > 0 &&
                        currentRoute != Routes.CAR,

                enter = fadeIn() +
                        scaleIn(initialScale = 0.8f),

                exit = fadeOut() +
                        scaleOut(targetScale = 0.8f)
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

                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

    ){ innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
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
                    onFavoriteClick = { productId ->
                        appViewModel.toggleFavorite(productId)
                    },
                    onCategorySelect = { categoryName ->
                        appViewModel.toggleCategory(categoryName)
                    },
                    onIncrease = { productId ->
                        appViewModel.increaseQuantity(productId)
                    },
                    onDecrease = { productId ->
                        appViewModel.decreaseQuantity(productId)
                    }
                )
            }

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

            // NUEVA QUINTA PESTAÑA: SERVICIOS
            composable(Routes.SERVICES) {
                ServicesScreen(
                    onOpenGarage = { navigateTo(Routes.PROFILE) },
                    onOpenDiagnosis = { navigateTo(Routes.DIAGNOSTIC) },
                    onOpenCatalog = { navigateTo(Routes.HOME) }
                )
            }

            composable(Routes.PROFILE) {
                GarageScreen(
                    vehicles = vehicleList,
                    onAddVehicle = { brand, model, year, engine, plate, mileage, notes ->
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
                    onUpdateNotes = { vehicleId, newNotes ->
                        appViewModel.updateVehicleNotes(vehicleId, newNotes)
                    },
                    onUpdateMileage = { vehicleId, newMileage ->
                        appViewModel.updateVehicleMileage(vehicleId, newMileage)
                    }
                )
            }

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

@Preview(showBackground = true)
@Composable
fun EdMotorPreview() {
    EdMotorTheme {
        EdMotorApp()
    }
}


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

        Column (
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

                // LOGO AUTOMOTRIZ

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
                        imageVector = Icons.Outlined.DirectionsCar,
                        contentDescription = "EdMotor",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // IDENTIDAD DE MARCA

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
                        text = "REPUESTOS • SERVICIOS • DIAGNÓSTICO",

                        fontSize = 8.sp,

                        fontWeight = FontWeight.SemiBold,

                        letterSpacing = 0.35.sp,

                        color = secondaryText,

                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // IDENTIDAD MECÁNICA

                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = Color(0xFFEAF2FF)
                ) {

                    Box(
                        modifier = Modifier
                            .size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {

                        Icon(
                            imageVector = Icons.Outlined.Build,
                            contentDescription = "Servicios automotrices",
                            tint = electricBlue,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            }

            // LÍNEA DECORATIVA AZUL

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
