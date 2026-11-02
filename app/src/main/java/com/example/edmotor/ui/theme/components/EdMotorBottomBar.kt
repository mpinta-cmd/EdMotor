package com.example.edmotor.ui.theme.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Warning

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edmotor.navigation.Routes
import com.example.edmotor.ui.theme.AzulNitro

private data class EdMotorTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val tabs = listOf(
    EdMotorTab(Routes.HOME, "Inicio", Icons.Outlined.Home),
    EdMotorTab(Routes.SERVICES, "Servicios", Icons.Outlined.Build),
    EdMotorTab(Routes.PROFILE, "Mi Garaje", Icons.Outlined.DirectionsCar),
    EdMotorTab(Routes.DIAGNOSTIC, "Diagnóstico", Icons.Outlined.Warning)
)

/**
 * Barra premium inspirada en la referencia: fondo blanco, bordes redondos,
 * una hendidura en la parte superior que sigue a la pestaña seleccionada,
 * y un punto azul que acompaña ese movimiento.
 *
 * Conserva exactamente la firma que usa EdMotorApp.kt.
 */
@Composable
fun EdMotorBottomBar(
    currentRoute: String?,
    cartCount: Int,
    onNavigate: (String) -> Unit
) {
    val activeIndex = tabs.indexOfFirst { it.route == currentRoute }
        .let { if (it >= 0) it else 0 }

    // Movimiento suave del recorte en la parte superior del menú.
    val notchPosition by animateFloatAsState(
        targetValue = (activeIndex + 0.5f) / tabs.size,
        animationSpec = tween(durationMillis = 340),
        label = "notchPosition"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 15.dp, end = 15.dp, top = 5.dp, bottom = 9.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            val notchShape = remember(notchPosition) {
                MovingNotchShape(notchPosition)
            }

            // 5 columnas del mismo ancho, para que los iconos queden alineados.
            val tabWidth = maxWidth / tabs.size
            val dotOffset by animateDpAsState(
                targetValue = tabWidth * (activeIndex + 0.5f) - 5.dp,
                animationSpec = tween(durationMillis = 340),
                label = "dotOffset"
            )

            // Cápsula BLANCA con hendidura en la parte superior.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(84.dp)
                    .shadow(10.dp, notchShape, clip = false)
                    .background(Color.White, notchShape)
                    .border(0.7.dp, Color(0xFFE8EDF4), notchShape)
            )

            // 5 botones contenidos dentro de la cápsula (sin expandir su altura).
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(84.dp)
                    .padding(top = 19.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach {tab ->
                    EdMotorTabButton(
                        tab = tab,
                        selected = tab.route == currentRoute,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onNavigate(tab.route) }
                    )
                }
            }

            // Punto azul ubicado dentro de la hendidura, como en tu imagen.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = dotOffset, y = 20.dp)
                    .size(10.dp)
                    .background(AzulNitro, CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }
    }
}


@Composable
private fun EdMotorTabButton(
    tab: EdMotorTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val touch = remember { MutableInteractionSource() }
    val pressed by touch.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val contentColor by animateColorAsState(
        targetValue = if (selected) AzulNitro else Color(0xFF424A55),
        animationSpec = tween(durationMillis = 210),
        label = "tabContentColor"
    )

    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.90f
            selected -> 1.08f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 170),
        label = "tabIconScale"
    )

    Box(
        modifier = modifier.clickable(
            interactionSource = touch,
            indication = null,
            role = Role.Tab,
            onClickLabel = "Abrir ${tab.label}"
        ) {
            haptic.performHapticFeedback(
                HapticFeedbackType.TextHandleMove
            )
            if (!selected) onClick()
        },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(25.dp)
                    .scale(iconScale)
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = tab.label,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


/**
 * Shape de cápsula con una hendidura pequeña y redondeada en el borde superior.
 * La posición se anima cuando cambia currentRoute.
 */
private class MovingNotchShape(
    private val position: Float
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        with(density) {
            val width = size.width
            val height = size.height
            val corner = 27.dp.toPx().coerceAtMost(height / 2)
            val notchHalf = 26.dp.toPx()
            val notchDepth = 15.dp.toPx()

            val center = (width * position).coerceIn(
                corner + 5.dp.toPx(),
                width - corner - 5.dp.toPx()
            )

            // Recortamos los extremos de la hendidura junto a las esquinas.
            val left = (center - notchHalf).coerceAtLeast(corner)
            val right = (center + notchHalf).coerceAtMost(width - corner)
            val leftSpan = center - left
            val rightSpan = right - center

            val path = Path().apply {
                moveTo(0f, corner)
                quadraticBezierTo(0f, 0f, corner, 0f)
                lineTo(left, 0f)

                cubicTo(
                    left + leftSpan * 0.44f, 0f,
                    center - leftSpan * 0.44f, notchDepth,
                    center, notchDepth
                )
                cubicTo(
                    center + rightSpan * 0.44f, notchDepth,
                    right - rightSpan * 0.44f, 0f,
                    right, 0f
                )

                lineTo(width - corner, 0f)
                quadraticBezierTo(width, 0f, width, corner)
                lineTo(width, height - corner)
                quadraticBezierTo(width, height, width - corner, height)
                lineTo(corner, height)
                quadraticBezierTo(0f, height, 0f, height - corner)
                close()
            }

            return Outline.Generic(path)
        }
    }
}
