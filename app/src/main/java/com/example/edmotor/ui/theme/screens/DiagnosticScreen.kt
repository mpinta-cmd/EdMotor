package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.dashboardLightList
import com.example.edmotor.data.productList
import com.example.edmotor.data.symptomList
import com.example.edmotor.model.DashboardLight
import com.example.edmotor.model.Symptom
import com.example.edmotor.ui.theme.components.DashboardLightCard
import com.example.edmotor.ui.theme.components.SymptomCard

private const val TAB_TESTIGOS = "Testigos"
private const val TAB_SINTOMAS = "Síntomas"

@Composable
fun DiagnosticScreen(
    onNavigateToCategory: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(TAB_TESTIGOS) }
    var selectedSeverity by remember { mutableStateOf("Todos") }
    var searchQuery by remember { mutableStateOf("") }

    // Una única LazyColumn permite desplazar la pantalla completa:
    // encabezado, aviso, buscador, filtros y resultados.
    val listState = rememberLazyListState()


    val query = searchQuery.trim()

    val filteredLights = dashboardLightList.filter { light ->
        val matchesSeverity = when (selectedSeverity) {
            "Rojos" -> light.severity.contains("Rojo", ignoreCase = true)
            "Amarillos" -> light.severity.contains("Amarillo", ignoreCase = true)
            else -> true
        }

        val matchesQuery = query.isBlank() || listOf(
            light.name,
            light.severity,
            light.meaning,
            light.recommendedAction,
            light.relatedCategory
        ).any { it.contains(query, ignoreCase = true) }

        matchesSeverity && matchesQuery
    }

    val filteredSymptoms = symptomList.filter { symptom ->
        query.isBlank() || listOf(
            symptom.title,
            symptom.probableCause,
            symptom.solution
        ).any { it.contains(query, ignoreCase = true) }
    }

    val redCount = dashboardLightList.count {
        it.severity.contains("Rojo", ignoreCase = true)
    }
    val yellowCount = dashboardLightList.count {
        it.severity.contains("Amarillo", ignoreCase = true)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 30.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "diagnostic_hero") {
            DiagnosticHeroCard()
        }

        item(key = "diagnostic_stats") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DiagnosticStatCard(
                    modifier = Modifier.weight(1f),
                    icon = "💡",
                    value = dashboardLightList.size.toString(),
                    label = "Testigos"
                )
                DiagnosticStatCard(
                    modifier = Modifier.weight(1f),
                    icon = "🔴",
                    value = redCount.toString(),
                    label = "Testigos rojos"
                )
                DiagnosticStatCard(
                    modifier = Modifier.weight(1f),
                    icon = "🔊",
                    value = symptomList.size.toString(),
                    label = "Síntomas"
                )
            }
        }

        item(key = "diagnostic_safety") {
            DiagnosticSafetyBanner()
        }

        item(key = "diagnostic_search") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "¿Qué está pasando con tu vehículo?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                    placeholder = {
                        Text("Busca un testigo, ruido o síntoma...")
                    },
                    leadingIcon = {
                        Text(
                            text = "⌕",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                Text(
                    text = "La guía ofrece orientación general; no lee sensores ni confirma averías automáticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "diagnostic_mode_selector") {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = "Selecciona el tipo de problema",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DiagnosticModeCard(
                        modifier = Modifier.weight(1f),
                        icon = "💡",
                        title = "Testigos",
                        subtitle = "Luces del tablero",
                        selected = selectedTab == TAB_TESTIGOS,
                        onClick = { selectedTab = TAB_TESTIGOS }
                    )
                    DiagnosticModeCard(
                        modifier = Modifier.weight(1f),
                        icon = "🔊",
                        title = "Síntomas",
                        subtitle = "Ruidos y señales",
                        selected = selectedTab == TAB_SINTOMAS,
                        onClick = { selectedTab = TAB_SINTOMAS }
                    )
                }
            }
        }

        if (selectedTab == TAB_TESTIGOS) {
            item(key = "diagnostic_light_section_title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Testigos del tablero",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Consulta el significado y el siguiente paso recomendado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DiagnosticCountBadge(filteredLights.size)
                }
            }

            item(key = "diagnostic_severity_filters") {
                val severityOptions = listOf("Todos", "Rojos", "Amarillos")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(severityOptions, key = { it }) { option ->
                        DiagnosticFilterChip(
                            label = when (option) {
                                "Rojos" -> "🔴 Alertas rojas"
                                "Amarillos" -> "🟡 Advertencias"
                                else -> "Todos"
                            },
                            selected = selectedSeverity == option,
                            onClick = { selectedSeverity = option }
                        )
                    }
                }
            }

            if (filteredLights.isEmpty()) {
                item(key = "diagnostic_no_lights") {
                    DiagnosticEmptyState(
                        title = "No encontramos ese testigo",
                        message = "Prueba con otra palabra o cambia el filtro."
                    )
                }
            } else {
                items(
                    items = filteredLights,
                    key = { light -> "light_${light.id}" }
                ) { light: DashboardLight ->
                    val categoryExists = productList.any { product ->
                        product.category.equals(
                            light.relatedCategory,
                            ignoreCase = true
                        )
                    }

                    DashboardLightCard(
                        light = light,
                        canBrowseRelatedParts = categoryExists,
                        onShopRelated = {
                            onNavigateToCategory(light.relatedCategory)
                        }
                    )
                }
            }
        } else {
            item(key = "diagnostic_symptom_section_title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Síntomas y ruidos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Empieza por lo que escuchas o sientes al conducir.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DiagnosticCountBadge(filteredSymptoms.size)
                }
            }

            item(key = "diagnostic_symptom_note") {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("ℹ️")
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(
                            text = "Una misma señal puede tener varias causas. Usa estas fichas como orientación y confirma la avería antes de comprar o cambiar piezas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (filteredSymptoms.isEmpty()) {
                item(key = "diagnostic_no_symptoms") {
                    DiagnosticEmptyState(
                        title = "No encontramos ese síntoma",
                        message = "Intenta buscar por palabras como freno, vibración o humo."
                    )
                }
            } else {
                items(
                    items = filteredSymptoms,
                    key = { symptom -> "symptom_${symptom.id}" }
                ) { symptom: Symptom ->
                    SymptomCard(symptom = symptom)
                }
            }
        }

        item(key = "diagnostic_footer_note") {
            Text(
                text = "EDMOTOR · GUÍA DE APOYO · La seguridad va primero. Ante una señal crítica, consulta el manual del vehículo o solicita asistencia profesional.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DiagnosticHeroCard() {
    val graphite = Color(0xFF1E293B)
    val electricBlue = Color(0xFF60A5FA)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = graphite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF334155)
                ) {
                    Text(
                        text = "🩺",
                        modifier = Modifier.padding(13.dp),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "EDMOTOR / DIAGNÓSTICO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = electricBlue
                    )
                    Text(
                        text = "Centro de diagnóstico",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Text(
                text = "Entiende las señales de tu vehículo y encuentra una guía para el siguiente paso.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )

            Surface(
                shape = RoundedCornerShape(9.dp),
                color = Color(0xFF334155)
            ) {
                Text(
                    text = "GUÍA ORIENTATIVA · NO ES UN ESCÁNER OBD",
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 7.dp
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFBFDBFE)
                )
            }
        }
    }
}

@Composable
private fun DiagnosticStatCard(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, style = MaterialTheme.typography.titleLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun DiagnosticSafetyBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.width(11.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "Primero, tu seguridad",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Si aparece una alerta roja relacionada con aceite, temperatura o frenos, detente en un lugar seguro y consulta el manual. Si hay pérdida de frenado, sobrecalentamiento o humo, no continúes conduciendo. Nunca abras el sistema de refrigeración cuando esté caliente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun DiagnosticModeCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = icon, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun DiagnosticFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DiagnosticCountBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun DiagnosticEmptyState(
    title: String,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🔎", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
