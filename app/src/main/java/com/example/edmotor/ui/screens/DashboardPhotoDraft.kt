package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.dashboard.DashboardScanResult
import com.example.edmotor.dashboard.dashboardIndicators

internal data class DashboardEvidenceVehicle(val id: String, val label: String)

internal data class DashboardPhotoDraft(val jpeg: ByteArray, val capturedAtMillis: Long)

private const val UNIDENTIFIED_TESTIGO = "unidentified"

@Composable
internal fun DashboardPhotoDraftCard(
    draft: DashboardPhotoDraft,
    scan: DashboardScanResult?,
    vehicles: List<DashboardEvidenceVehicle>,
    saving: Boolean,
    issue: String?,
    onSave: (String, DashboardEvidenceVehicle?) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val detected = remember(scan) {
        scan?.matches.orEmpty().sortedByDescending { it.score }.map { it.indicator }.distinctBy { it.id }
    }
    val indicatorOptions = remember(detected) { (detected + dashboardIndicators).distinctBy { it.id } }
    var indicatorId by rememberSaveable(draft.capturedAtMillis) {
        mutableStateOf(detected.firstOrNull()?.id ?: UNIDENTIFIED_TESTIGO)
    }
    var vehicleId by rememberSaveable(draft.capturedAtMillis) { mutableStateOf<String?>(null) }
    val selectedIndicator = indicatorOptions.firstOrNull { it.id == indicatorId }
    val selectedVehicle = vehicles.firstOrNull { it.id == vehicleId }
    val vehicleOptions = remember(vehicles) { vehicles.sortedBy { it.label } }
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
        color = colors.surface
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Foto del tablero",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            DashboardPhotoPreview(
                jpeg = draft.jpeg,
                contentDescription = "Foto recién capturada del tablero",
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(shape).background(colors.surfaceVariant)
            )
            DashboardEvidencePicker(
                title = "Testigo asociado",
                selection = selectedIndicator,
                options = listOf(null) + indicatorOptions,
                enabled = !saving,
                labelFor = { it?.title ?: "Testigo sin identificar" },
                onSelect = { indicatorId = it?.id ?: UNIDENTIFIED_TESTIGO }
            )
            if (vehicleOptions.isNotEmpty()) {
                DashboardEvidencePicker(
                    title = "Vehículo (opcional)",
                    selection = selectedVehicle,
                    options = listOf(null) + vehicleOptions,
                    enabled = !saving,
                    labelFor = { it?.label ?: "Sin vehículo" },
                    onSelect = { vehicleId = it?.id }
                )
            }
            issue?.let {
                Text(
                    it,
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Button(
                onClick = { onSave(indicatorId, selectedVehicle) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (saving) "Guardando foto…" else "Guardar foto", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
internal fun DashboardPhotoSavedCard(onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(
                    "Foto guardada",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text("Ya puedes consultarla en tu historial.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpen) { Text("Ver foto guardada") }
        }
    }
}

@Composable
private fun <T> DashboardEvidencePicker(
    title: String,
    selection: T?,
    options: List<T?>,
    enabled: Boolean,
    labelFor: (T?) -> String,
    onSelect: (T?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Text(labelFor(selection), modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(labelFor(option)) },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}
