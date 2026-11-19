
package com.example.edmotor.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val EdMotorShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

private val LightColorScheme = lightColorScheme(

    primary = AzulNitro,
    onPrimary = Color.White,

    primaryContainer = AzulNitroContenedorClaro,
    onPrimaryContainer = AzulNitroOscuro,

    secondary = NaranjaPerformance,
    onSecondary = Color.White,

    secondaryContainer = NaranjaContenedorClaro,
    onSecondaryContainer = NaranjaPerformanceOscuro,

    tertiary = GrisCarbon,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3EAF4),
    onTertiaryContainer = Color(0xFF26374D),

    background = FondoClaro,
    onBackground = TextoPrincipalClaro,

    surface = SuperficieClaro,
    onSurface = TextoPrincipalClaro,
    surfaceContainerLowest = SuperficieClaro,
    surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = FondoClaro,
    surfaceContainerHigh = Color(0xFFEBF0F7),
    surfaceContainerHighest = Color(0xFFE3EAF4),

    surfaceVariant = TarjetaVarianteClaro,
    onSurfaceVariant = TextoSecundarioClaro,

    outline = BordeClaro,
    outlineVariant = Color(0xFFDCE4EF),

    error = ErrorClaro,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(

    primary = AzulNitroClaro,
    onPrimary = Color(0xFF001B3D),

    primaryContainer = AzulNitroContenedorOscuro,
    onPrimaryContainer = Color(0xFFD6E6FF),

    secondary = NaranjaPerformanceClaro,
    onSecondary = Color(0xFF321000),

    secondaryContainer = NaranjaContenedorOscuro,
    onSecondaryContainer = Color(0xFFFFDCCB),

    tertiary = GrisAceroClaro,
    onTertiary = Color(0xFF101827),
    tertiaryContainer = Color(0xFF313F57),
    onTertiaryContainer = Color(0xFFE1EAFB),

    background = FondoOscuro,
    onBackground = TextoPrincipalOscuro,

    surface = SuperficieOscuro,
    onSurface = TextoPrincipalOscuro,
    surfaceContainerLowest = Color(0xFF0A101B),
    surfaceContainerLow = Color(0xFF101A2A),
    surfaceContainer = Color(0xFF18263A),
    surfaceContainerHigh = Color(0xFF22324B),
    surfaceContainerHighest = Color(0xFF2B3C55),

    surfaceVariant = TarjetaVarianteOscuro,
    onSurfaceVariant = TextoSecundarioOscuro,

    outline = BordeOscuro,
    outlineVariant = Color(0xFF293B53),

    error = ErrorOscuro,
    onError = Color(0xFF410002)
)

@Composable
fun EdMotorTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {

    val useDarkTheme = when (themeMode) {

        AppThemeMode.SYSTEM -> isSystemInDarkTheme()

        AppThemeMode.LIGHT -> false

        AppThemeMode.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) {
            DarkColorScheme
        } else {
            LightColorScheme
        },
        typography = Typography,
        shapes = EdMotorShapes,
        content = content
    )
}
