package com.example.edmotor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Esquema para Modo Claro
private val LightColorScheme = lightColorScheme(
    primary = AzulNitro,
    onPrimary = Color.White,
    primaryContainer = AzulNitroContenedorClaro,
    onPrimaryContainer = AzulNitroOscuro,

    secondary = NaranjaPerformance,
    onSecondary = Color.White,
    secondaryContainer = NaranjaContenedorClaro,
    onSecondaryContainer = NaranjaPerformance,

    tertiary = GrisCarbon,
    onTertiary = Color.White,

    background = FondoClaro,
    onBackground = Color(0xFF0F172A),

    surface = SuperficieClaro,
    onSurface = Color(0xFF0F172A),

    surfaceVariant = TarjetaVarianteClaro,
    onSurfaceVariant = TextoSecundarioClaro,

    outline = Color(0xFFCBD5E1)
)

// Esquema para Modo Oscuro
private val DarkColorScheme = darkColorScheme(
    primary = AzulNitro,
    onPrimary = Color.White,
    primaryContainer = AzulNitroContenedorOscuro,
    onPrimaryContainer = Color(0xFFB3D1FF),

    secondary = NaranjaPerformanceClaro,
    onSecondary = Color.Black,
    secondaryContainer = NaranjaContenedorOscuro,
    onSecondaryContainer = Color(0xFFFFD1B8),

    tertiary = GrisAcero,
    onTertiary = Color.White,

    background = FondoOscuro,
    onBackground = Color(0xFFF8FAFC),

    surface = SuperficieOscuro,
    onSurface = Color(0xFFF8FAFC),

    surfaceVariant = TarjetaVarianteOscuro,
    onSurfaceVariant = TextoSecundarioOscuro,

    outline = Color(0xFF475569)
)

@Composable
fun EdMotorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}