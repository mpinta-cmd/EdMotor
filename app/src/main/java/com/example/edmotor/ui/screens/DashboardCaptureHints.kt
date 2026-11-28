package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.dashboard.DashboardAmbientLight
import com.example.edmotor.dashboard.DashboardSensorGuidance
import com.example.edmotor.dashboard.DashboardStability

@Composable
internal fun DashboardCaptureHints(guidance: DashboardSensorGuidance) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (guidance.stability) {
            DashboardStability.Stable -> CaptureHint(
                Icons.Outlined.CheckCircle, "Listo para escanear", colors.primaryContainer, colors.onPrimaryContainer
            )
            DashboardStability.Moving -> CaptureHint(
                Icons.Outlined.PanTool, "Mantén el celular quieto", colors.tertiaryContainer, colors.onTertiaryContainer
            )
            DashboardStability.Waiting -> CaptureHint(
                Icons.Outlined.Timer, "Midiendo estabilidad…", colors.surfaceVariant, colors.onSurfaceVariant
            )
            DashboardStability.Unavailable -> CaptureHint(
                Icons.Outlined.CameraAlt, "Captura manual", colors.surfaceVariant, colors.onSurfaceVariant
            )
        }
        if (guidance.light == DashboardAmbientLight.Low) {
            CaptureHint(
                Icons.Outlined.DarkMode,
                "Poca luz ambiental. Revisa que el tablero se vea claro.",
                colors.tertiaryContainer,
                colors.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun CaptureHint(icon: ImageVector, label: String, background: Color, foreground: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = background,
        contentColor = foreground
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                label,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
