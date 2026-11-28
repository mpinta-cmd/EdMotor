package com.example.edmotor.online

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.ui.components.EdMotorBottomSheet
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun OnlineRatingsScreen(
    state: OnlineState,
    onRate: (Long, Int) -> Unit,
    onOpenOrders: () -> Unit
) {
    val sync = state.syncStatus(OnlineResource.RATINGS)
    val eligible = state.eligibleRatings
    val (rated, pending) = remember(eligible) { eligible.partition { it.myRating != null } }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            OnlineSectionTitle("Calificar repuestos", Icons.Outlined.StarBorder)
        }
        if (eligible.isEmpty()) {
            item(key = "status") {
                when {
                    sync.waitingForFirstData -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    sync.loaded -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OnlineListNote("Aquí aparecerán los repuestos cuando el taller registre su entrega.")
                        OutlinedButton(onClick = onOpenOrders, modifier = Modifier.fillMaxWidth()) {
                            Text("Ver mis pedidos")
                        }
                    }
                    else -> Unit
                }
            }
        }
        if (pending.isNotEmpty()) {
            item(key = "pending") { RatingSectionHeading("Por calificar") }
            items(pending, key = { it.productId }) { rating ->
                OnlineRatingCard(rating = rating, enabled = !state.busy, onEdit = { editingId = rating.productId })
            }
        }
        if (rated.isNotEmpty()) {
            item(key = "rated") { RatingSectionHeading("Ya calificados") }
            items(rated, key = { it.productId }) { rating ->
                OnlineRatingCard(rating = rating, enabled = !state.busy, onEdit = { editingId = rating.productId })
            }
        }
    }

    val editing = eligible.firstOrNull { it.productId == editingId }
    if (editing != null) {
        OnlineRatingDialog(
            rating = editing,
            busy = state.busy,
            onDismiss = { editingId = null },
            onRate = { stars ->
                onRate(editing.productId, stars)
                editingId = null
            }
        )
    }
}

@Composable
private fun RatingSectionHeading(title: String) {
    Text(
        title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun OnlineRatingCard(rating: OnlineRatingItem, enabled: Boolean, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val date = remember(rating.deliveredAtMillis) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.forLanguageTag("es-CO"))
            .format(Date(rating.deliveredAtMillis))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rating.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Entregado el $date", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            val stars = rating.myRating
            if (stars == null) {
                Button(onClick = onEdit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text("Calificar repuesto")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Text(
                        "Tu calificación de ${starLabel(stars)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onEdit, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text("Editar calificación")
                }
            }
        }
    }
}

@Composable
private fun OnlineRatingDialog(
    rating: OnlineRatingItem,
    busy: Boolean,
    onDismiss: () -> Unit,
    onRate: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var selected by rememberSaveable(rating.productId) { mutableIntStateOf(rating.myRating ?: 0) }
    EdMotorBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        dismissEnabled = !busy,
        title = { Text(if (rating.myRating == null) "Califica este repuesto" else "Editar calificación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(rating.productName, fontWeight = FontWeight.SemiBold)
                Text("Selecciona de 1 a 5 estrellas.")
                FlowRow(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (stars in 1..5) {
                        Box(
                            modifier = Modifier.size(48.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(
                                    selected = selected == stars,
                                    enabled = !busy,
                                    role = Role.RadioButton,
                                    onClick = { selected = stars }
                                )
                                .semantics { contentDescription = starLabel(stars) },
                            contentAlignment = Alignment.Center
                        ) {
                            val filled = stars <= selected
                            Icon(
                                if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = if (filled) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onRate(selected) },
                enabled = !busy && selected in 1..5 && selected != rating.myRating
            ) {
                Text(if (rating.myRating == null) "Guardar calificación" else "Guardar cambios")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } }
    )
}

private fun starLabel(stars: Int): String = if (stars == 1) "1 estrella" else "$stars estrellas"
