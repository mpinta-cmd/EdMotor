
package com.example.edmotor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

    background = FondoClaro,
    onBackground = TextoPrincipalClaro,

    surface = SuperficieClaro,
    onSurface = TextoPrincipalClaro,

    surfaceVariant = TarjetaVarianteClaro,
    onSurfaceVariant = TextoSecundarioClaro,

    outline = BordeClaro,

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

    background = FondoOscuro,
    onBackground = TextoPrincipalOscuro,

    surface = SuperficieOscuro,
    onSurface = TextoPrincipalOscuro,

    surfaceVariant = TarjetaVarianteOscuro,
    onSurfaceVariant = TextoSecundarioOscuro,

    outline = BordeOscuro,

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

    MaterialTheme(
        colorScheme = if (useDarkTheme) {
            DarkColorScheme
        } else {
            LightColorScheme
        },
        typography = Typography,
        content = content
    )
}
