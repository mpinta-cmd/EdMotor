package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TireRepair
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ServiceInfo(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val checks: List<String>
)

private val automotiveServices = listOf(
    ServiceInfo(
        id = "maintenance",
        name = "Mantenimiento preventivo",
        description = "Aceite, filtros y revisiones periódicas.",
        icon = Icons.Outlined.Build,
        checks = listOf("Revisar nivel y estado del aceite.", "Verificar filtros y líquidos.", "Consultar el plan de mantenimiento del fabricante.")
    ),
    ServiceInfo(
        id = "brakes",
        name = "Sistema de frenos",
        description = "Seguridad, pastillas, discos y líquido de frenos.",
        icon = Icons.Outlined.CarRepair,
        checks = listOf("Inspeccionar pastillas y discos.", "Revisar el líquido de frenos.", "Acudir a un técnico si hay ruidos o pérdida de frenado.")
    ),
    ServiceInfo(
        id = "electronics",
        name = "Diagnóstico electrónico",
        description = "Testigos, sensores y posibles fallas del vehículo.",
        icon = Icons.Outlined.Settings,
        checks = listOf("Identificar el testigo encendido.", "Registrar los síntomas observados.", "Confirmar fallas con una revisión técnica y escáner cuando aplique.")
    ),
    ServiceInfo(
        id = "battery",
        name = "Batería y arranque",
        description = "Estado de carga y sistema eléctrico.",
        icon = Icons.Outlined.ElectricBolt,
        checks = listOf("Comprobar el estado de la batería.", "Revisar conexiones y terminales.", "Evaluar el sistema de carga y arranque.")
    ),
    ServiceInfo(
        id = "tires",
        name = "Llantas y dirección",
        description = "Presión, desgaste, alineación y estabilidad.",
        icon = Icons.Outlined.TireRepair,
        checks = listOf("Comprobar la presión recomendada.", "Revisar desgaste y daños visibles.", "Solicitar inspección si hay vibraciones o desviación.")
    ),
    ServiceInfo(
        id = "climate",
        name = "Aire acondicionado",
        description = "Climatización, ventilación y filtro de cabina.",
        icon = Icons.Outlined.AcUnit,
        checks = listOf("Revisar el flujo de aire.", "Comprobar el filtro de cabina.", "Solicitar revisión si el sistema no enfría correctamente.")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    onOpenGarage: () -> Unit,
    onOpenAppointments: () -> Unit,
    onOpenCatalog: () -> Unit
) {
    var selectedServiceId by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "services_hero") {
            ServicesHero(onOpenAppointments = onOpenAppointments)
        }
        item(key = "services_shortcuts") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ServiceShortcut(
                    label = "Mi garaje",
                    icon = Icons.Outlined.DirectionsCar,
                    onClick = onOpenGarage,
                    modifier = Modifier.weight(1f)
                )
                ServiceShortcut(
                    label = "Repuestos",
                    icon = Icons.Outlined.Build,
                    onClick = onOpenCatalog,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item(key = "services_header") {
            Text(
                "Áreas de servicio",
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        items(items = automotiveServices, key = { it.id }) { service ->
            ServiceCard(service = service, onClick = { selectedServiceId = service.id })
        }
    }

    automotiveServices.firstOrNull { it.id == selectedServiceId }?.let { service ->
        ModalBottomSheet(
            onDismissRequest = { selectedServiceId = null },
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 22.dp, end = 22.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ServiceIcon(service.icon)
                    Text(
                        service.name,
                        modifier = Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    service.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant
                )
                Surface(
                    color = colors.surfaceVariant,
                    contentColor = colors.onSurfaceVariant,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "¿Qué conviene revisar?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        service.checks.forEach { point ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = colors.primary
                                )
                                Text(point, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Text(
                    "Esta información es orientativa y no reemplaza la valoración de un profesional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        selectedServiceId = null
                        onOpenAppointments()
                    }
                ) { Text("Abrir citas") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        selectedServiceId = null
                        onOpenCatalog()
                    }
                ) { Text("Explorar repuestos") }
            }
        }
    }
}

@Composable
private fun ServicesHero(onOpenAppointments: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF091A36), Color(0xFF153E7A))))
    ) {
        Icon(
            Icons.Outlined.CarRepair,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(118.dp),
            tint = Color(0xFFBCD6FF).copy(alpha = 0.18f)
        )
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Servicios del taller",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Button(
                onClick = onOpenAppointments,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFBE8C),
                    contentColor = Color(0xFF30180B)
                )
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Agenda una cita", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ServiceShortcut(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ServiceCard(service: ServiceInfo, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ServiceIcon(service.icon)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(service.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(service.description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ServiceIcon(icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(13.dp), color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(11.dp).size(24.dp))
    }
}
