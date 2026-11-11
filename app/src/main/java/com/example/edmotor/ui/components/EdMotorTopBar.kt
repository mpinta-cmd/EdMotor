package com.example.edmotor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Palette

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.edmotor.ui.theme.AppThemeMode
import com.example.edmotor.ui.theme.AzulNitro
import com.example.edmotor.ui.theme.EdMotorTheme


// =====================================================
// ENCABEZADO PREMIUM EDMOTOR
// =====================================================

@Composable
fun EdMotorTopBar(

    themeMode: AppThemeMode,

    onThemeModeChange: (AppThemeMode) -> Unit

) {

    val showAppearanceDialog = remember {
        mutableStateOf(false)
    }


    Surface(

        modifier = Modifier.fillMaxWidth(),

        color = MaterialTheme.colorScheme.surface,

        shadowElevation = 2.dp

    ) {

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {

            Row(

                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = 18.dp,
                        vertical = 12.dp
                    ),

                verticalAlignment = Alignment.CenterVertically

            ) {


                // LOGOTIPO EDMOTOR

                Box(

                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(

                            Brush.linearGradient(

                                colors = listOf(
                                    AzulNitro,
                                    Color(0xFF42A5FF)
                                )
                            )
                        ),

                    contentAlignment = Alignment.Center

                ) {

                    Icon(

                        imageVector = Icons.Outlined.DirectionsCar,

                        contentDescription = "EdMotor",

                        tint = Color.White,

                        modifier = Modifier.size(29.dp)
                    )
                }


                Spacer(Modifier.width(12.dp))


                // NOMBRE Y DESCRIPCIÓN

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(

                        text = "EDMOTOR",

                        style = MaterialTheme.typography.titleLarge,

                        fontWeight = FontWeight.ExtraBold,

                        letterSpacing = 1.2.sp,

                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(

                        text = "REPUESTOS · SERVICIOS · DIAGNÓSTICO",

                        style = MaterialTheme.typography.labelSmall,

                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }


                // BOTÓN DE APARIENCIA

                IconButton(

                    onClick = {
                        showAppearanceDialog.value = true
                    }

                ) {

                    Icon(

                        imageVector = Icons.Outlined.Palette,

                        contentDescription = "Cambiar apariencia",

                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }


            // LÍNEA DE IDENTIDAD VISUAL

            Box(

                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(

                        Brush.horizontalGradient(

                            colors = listOf(

                                MaterialTheme.colorScheme.primary,

                                MaterialTheme.colorScheme.secondary,

                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.2f
                                )
                            )
                        )
                    )
            )
        }
    }


    // =====================================================
    // DIÁLOGO DE APARIENCIA
    // =====================================================

    if (showAppearanceDialog.value) {

        AlertDialog(

            onDismissRequest = {
                showAppearanceDialog.value = false
            },

            title = {

                Text(

                    text = "Apariencia de EdMotor",

                    fontWeight = FontWeight.Bold
                )
            },

            text = {

                Column {

                    Text(

                        text = "Selecciona cómo quieres visualizar la aplicación.",

                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))


                    AppThemeMode.entries.forEach { mode ->

                        val label = when (mode) {

                            AppThemeMode.SYSTEM -> "Automático"

                            AppThemeMode.LIGHT -> "Claro"

                            AppThemeMode.DARK -> "Oscuro"
                        }


                        TextButton(

                            onClick = {

                                onThemeModeChange(mode)

                                showAppearanceDialog.value = false
                            },

                            modifier = Modifier.fillMaxWidth()

                        ) {

                            Text(

                                text = if (themeMode == mode) {
                                    "✓  $label"
                                } else {
                                    label
                                },

                                color = if (themeMode == mode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },

                                fontWeight = if (themeMode == mode) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        }
                    }
                }
            },


            confirmButton = {

                TextButton(

                    onClick = {
                        showAppearanceDialog.value = false
                    }

                ) {

                    Text("Cerrar")
                }
            },


            shape = RoundedCornerShape(24.dp)
        )
    }
}


// =====================================================
// PREVISUALIZACIÓN DEL ENCABEZADO
// =====================================================

@Preview(showBackground = true)
@Composable
private fun EdMotorTopBarPreview() {

    EdMotorTheme(
        themeMode = AppThemeMode.LIGHT
    ) {

        EdMotorTopBar(

            themeMode = AppThemeMode.LIGHT,

            onThemeModeChange = {}
        )
    }
}
