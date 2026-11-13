package com.example.edmotor.ui.components

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

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text

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
import androidx.compose.ui.unit.dp

import com.example.edmotor.navigation.Routes
import com.example.edmotor.ui.theme.AppThemeMode


/**
 * Estructura visual principal de EdMotor.
 *
 * Responsabilidades:
 * - Mostrar la barra superior.
 * - Controlar la visibilidad de la barra inferior.
 * - Detectar el desplazamiento del contenido.
 * - Mostrar el acceso rápido al carrito.
 * - Proporcionar el espacio de contenido a la navegación.
 */
@Composable
fun EdMotorScaffold(
    currentRoute: String?,
    cartCount: Int,
    pendingOrderCount: Int = 0,
    pendingAppointmentCount: Int = 0,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onNavigate: (String) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    // ==========================================
    // VISIBILIDAD DE LA BARRA INFERIOR
    // ==========================================

    val bottomBarVisibleState = remember {
        mutableStateOf(true)
    }

    val bottomBarVisible by bottomBarVisibleState

    // ==========================================
    // DETECCIÓN DEL DESPLAZAMIENTO
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

    // Restablecer la barra al cambiar de pantalla.
    LaunchedEffect(currentRoute) {
        bottomBarVisibleState.value = true
    }

    // ==========================================
    // SCAFFOLD PRINCIPAL
    // ==========================================

    val isDetailRoute =
        currentRoute == Routes.PRODUCT_DETAIL ||
                currentRoute?.startsWith("historialMantenimiento/") == true ||
                currentRoute?.startsWith("formularioMantenimiento/") == true

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),

        topBar = {
            if (!isDetailRoute) {
                EdMotorTopBar(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
        },

        bottomBar = {
            AnimatedVisibility(
                visible = bottomBarVisible && !isDetailRoute,

                enter =
                    slideInVertically(
                        animationSpec = tween(250),
                        initialOffsetY = { it }
                    ) +
                            expandVertically(
                                animationSpec = tween(250),
                                expandFrom = Alignment.Bottom
                            ) +
                            fadeIn(animationSpec = tween(180)),

                exit =
                    slideOutVertically(
                        animationSpec = tween(250),
                        targetOffsetY = { it }
                    ) +
                            shrinkVertically(
                                animationSpec = tween(250),
                                shrinkTowards = Alignment.Bottom
                            ) +
                            fadeOut(animationSpec = tween(180))
            ) {
                EdMotorBottomBar(
                    currentRoute = currentRoute,
                    cartCount = cartCount,
                    pendingOrderCount = pendingOrderCount,
                    pendingAppointmentCount = pendingAppointmentCount,
                    onNavigate = onNavigate
                )
            }
        },

        floatingActionButton = {
            AnimatedVisibility(
                visible =
                    cartCount > 0 &&
                            currentRoute != Routes.CAR &&
                            !isDetailRoute,

                enter =
                    fadeIn() +
                            scaleIn(initialScale = 0.8f),

                exit =
                    fadeOut() +
                            scaleOut(targetScale = 0.8f)
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        onNavigate(Routes.CAR)
                    },

                    icon = {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = "Abrir carrito"
                        )
                    },

                    text = {
                        Text(
                            if (cartCount > 99) {
                                "Carrito · 99+"
                            } else {
                                "Carrito · $cartCount"
                            }
                        )
                    },

                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}
