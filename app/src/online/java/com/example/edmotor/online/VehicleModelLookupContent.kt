package com.example.edmotor.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@Composable
internal fun VehicleModelLookupContent(
    brand: String,
    year: Int,
    findModels: suspend (String, Int) -> List<VehicleModelSuggestion>,
    onSelect: (VehicleModelSuggestion) -> Unit
) {
    var attempt by remember(brand, year) { mutableIntStateOf(0) }
    var lookup by remember(brand, year) { mutableStateOf<ModelLookupResult>(ModelLookupResult.Loading) }

    LaunchedEffect(brand, year, attempt) {
        lookup = try {
            val models = findModels(brand, year)
            if (models.isEmpty()) ModelLookupResult.Empty else ModelLookupResult.Found(models)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            ModelLookupResult.Unavailable
        }
    }

    val colors = MaterialTheme.colorScheme
    val status = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$brand · $year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Fuente · NHTSA vPIC", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        when (val result = lookup) {
            ModelLookupResult.Loading -> Row(
                modifier = status.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Consultando el catálogo…")
            }

            ModelLookupResult.Empty -> Text(
                "No encontramos modelos para esta marca y año. Puedes escribir el modelo en el formulario.",
                modifier = status
            )

            ModelLookupResult.Unavailable -> {
                Text(
                    "No pudimos consultar el catálogo. Comprueba tu conexión y vuelve a intentarlo, o escribe el modelo.",
                    modifier = status,
                    color = colors.error
                )
                OutlinedButton(onClick = {
                    lookup = ModelLookupResult.Loading
                    attempt++
                }) { Text("Reintentar") }
            }

            is ModelLookupResult.Found -> {
                val count = result.models.size
                Text(
                    if (count == 1) "Toca el modelo para completar el formulario."
                    else "$count modelos · Toca uno para completar el formulario.",
                    modifier = status,
                    style = MaterialTheme.typography.bodyMedium
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(result.models, key = { it.model }) { suggestion ->
                        Surface(
                            onClick = { onSelect(suggestion) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface
                        ) {
                            Row(
                                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(suggestion.model, modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = colors.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed interface ModelLookupResult {
    data object Loading : ModelLookupResult
    data object Empty : ModelLookupResult
    data object Unavailable : ModelLookupResult
    data class Found(val models: List<VehicleModelSuggestion>) : ModelLookupResult
}
