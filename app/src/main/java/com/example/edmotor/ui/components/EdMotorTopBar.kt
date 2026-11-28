package com.example.edmotor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edmotor.ui.theme.AppThemeMode
import com.example.edmotor.ui.theme.EdMotorHeroAmber
import com.example.edmotor.ui.theme.EdMotorHeroNavy

@Composable
internal fun EdMotorMark(size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(EdMotorHeroAmber),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.DirectionsCar,
            contentDescription = null,
            modifier = Modifier.size(size * 0.58f),
            tint = EdMotorHeroNavy
        )
    }
}

@Composable
fun EdMotorTopBar(themeMode: AppThemeMode, onThemeModeChange: (AppThemeMode) -> Unit) {
    var showAppearanceDialog by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EdMotorMark()
                Text(
                    "EdMotor",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.5).sp),
                    fontWeight = FontWeight.ExtraBold
                )
                IconButton(
                    onClick = { showAppearanceDialog = true },
                    shape = CircleShape,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = colors.surfaceContainer,
                        contentColor = colors.primary
                    )
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = "Cambiar apariencia")
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }

    if (showAppearanceDialog) {
        EdMotorBottomSheet(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text("Apariencia de EdMotor") },
            text = {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            AppThemeMode.SYSTEM -> "Automático"
                            AppThemeMode.LIGHT -> "Claro"
                            AppThemeMode.DARK -> "Oscuro"
                        }
                        val selected = themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(if (selected) colors.primaryContainer else colors.surfaceContainer)
                                .selectable(selected, role = Role.RadioButton) {
                                    onThemeModeChange(mode)
                                    showAppearanceDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppearanceDialog = false }) { Text("Cerrar") } }
        )
    }
}
