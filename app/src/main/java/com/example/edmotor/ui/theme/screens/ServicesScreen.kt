package com.example.edmotor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edmotor.ui.theme.AzulNitro
import com.example.edmotor.ui.theme.GrisCarbon

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
        icon = Icons.Filled.Build,
        checks = listOf("Revisar nivel y estado del aceite.", "Verificar filtros y líquidos.", "Consultar el plan de mantenimiento del fabricante.")
    ),
    ServiceInfo(
        id = "brakes",
        name = "Sistema de frenos",
        description = "Seguridad, pastillas, discos y líquido de frenos.",
        icon = Icons.Filled.CarRepair,
        checks = listOf("Inspeccionar pastillas y discos.", "Revisar el líquido de frenos.", "Acudir a un técnico si hay ruidos o pérdida de frenado.")
    ),
    ServiceInfo(
        id = "electronics",
        name = "Diagnóstico electrónico",
        description = "Testigos, sensores y posibles fallas del vehículo.",
        icon = Icons.Filled.Settings,
        checks = listOf("Identificar el testigo encendido.", "Registrar los síntomas observados.", "Confirmar fallas con una revisión técnica y escáner cuando aplique.")
    ),
    ServiceInfo(
        id = "battery",
        name = "Batería y arranque",
        description = "Estado de carga y sistema eléctrico.",
        icon = Icons.Filled.ElectricBolt,
        checks = listOf("Comprobar el estado de la batería.", "Revisar conexiones y terminales.", "Evaluar el sistema de carga y arranque.")
    ),
    ServiceInfo(
        id = "tires",
        name = "Llantas y dirección",
        description = "Presión, desgaste, alineación y estabilidad.",
        icon = Icons.Filled.TireRepair,
        checks = listOf("Comprobar la presión recomendada.", "Revisar desgaste y daños visibles.", "Solicitar inspección si hay vibraciones o desviación.")
    ),
    ServiceInfo(
        id = "climate",
        name = "Aire acondicionado",
        description = "Climatización, ventilación y filtro de cabina.",
        icon = Icons.Filled.AcUnit,
        checks = listOf("Revisar el flujo de aire.", "Comprobar el filtro de cabina.", "Solicitar revisión si el sistema no enfría correctamente.")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    onOpenGarage: () -> Unit,
    onOpenDiagnosis: () -> Unit,
    onOpenCatalog: () -> Unit
) {
    val listState = rememberLazyListState()
    var selectedService by remember { mutableStateOf<ServiceInfo?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "services_hero") {
            ServicesHero()
        }

        item(key = "services_shortcuts") {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Text(
                    text = "ACCESOS RÁPIDOS",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ServiceShortcut(
                        label = "Mi garaje",
                        icon = Icons.Filled.DirectionsCar,
                        onClick = onOpenGarage,
                        modifier = Modifier.weight(1f)
                    )
                    ServiceShortcut(
                        label = "Diagnóstico",
                        icon = Icons.Filled.Search,
                        onClick = onOpenDiagnosis,
                        modifier = Modifier.weight(1f)
                    )
                    ServiceShortcut(
                        label = "Repuestos",
                        icon = Icons.Filled.Build,
                        onClick = onOpenCatalog,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "services_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Áreas de servicio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Explora opciones de cuidado para tu vehículo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(items = automotiveServices, key = { it.id }) { service ->
            ServiceCard(service = service, onClick = { selectedService = service })
        }

        item(key = "services_reservations") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "PRÓXIMAMENTE · AGENDA DE CITAS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Estamos preparando nuevas funciones",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Por ahora puedes consultar servicios, revisar tu garaje, " +
                                "explorar repuestos y acceder a Diagnóstico.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // Ficha de servicio que aparece al tocar una tarjeta.
    selectedService?.let { service ->
        ModalBottomSheet(
            onDismissRequest = { selectedService = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = service.icon,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = service.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "¿Qué conviene revisar?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                service.checks.forEach { point ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("•", color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = point,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Text(
                    text = "Esta información es orientativa y no reemplaza " +
                            "la valoración de un profesional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        selectedService = null
                        onOpenDiagnosis()
                    }
                ) {
                    Text("Abrir diagnóstico")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        selectedService = null
                        onOpenCatalog()
                    }
                ) {
                    Text("Explorar repuestos")
                }
            }
        }
    }
}

@Composable
private fun ServicesHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F172A), GrisCarbon, Color(0xFF0044B3))
                )
            )
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 27.dp, y = (-24).dp)
                .size(164.dp),
            tint = Color.White.copy(alpha = 0.08f)
        )
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = Color.White.copy(alpha = 0.13f)
            ) {
                Text(
                    text = "  EDMOTOR  /  SERVICIOS  ",
                    modifier = Modifier.padding(vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFBFDBFE)
                )
            }
            Text(
                text = "Tu vehículo, en\nbuenas manos.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = "Explora cuidados esenciales y encuentra " +
                        "herramientas para mantener tu auto en marcha.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.14f)
            ) {
                Text(
                    text = "🔧  ${automotiveServices.size} áreas de servicio",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                )
            }
        }
    }
}

@Composable
private fun ServiceShortcut(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AzulNitro,
                modifier = Modifier.size(25.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ServiceCard(
    service: ServiceInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = service.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(13.dp).size(27.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = service.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Text(
                    text = service.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = "Ver detalles",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
