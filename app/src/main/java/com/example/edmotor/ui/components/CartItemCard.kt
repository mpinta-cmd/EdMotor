package com.example.edmotor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Product

@Composable
fun CartItemCard(
    product: Product,
    cantidad: Int,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val canIncrease = product.stock > 0 && cantidad < product.stock
    val stockMessage = when {
        product.stock <= 0 -> "AGOTADO"
        cantidad > product.stock -> "Stock insuficiente. Reduce la cantidad."
        cantidad >= product.stock -> "Límite de existencias alcanzado."
        else -> "Existencias disponibles: ${product.stock}"
    }
    val stockColor = if (canIncrease) colors.onSurfaceVariant else colors.error

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceVariant
                ) {
                    ProductImage(
                        imageUri = product.imageUri,
                        imageRes = product.imageRes,
                        contentDescription = product.contentDescription,
                        modifier = Modifier.size(80.dp).padding(6.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = product.effectivePrice,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = stockMessage, style = MaterialTheme.typography.bodySmall, color = stockColor)
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceVariant,
                    contentColor = colors.onSurfaceVariant
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDecrease, enabled = enabled && cantidad > 0) {
                            Icon(Icons.Outlined.Remove, contentDescription = "Disminuir cantidad de ${product.name}")
                        }
                        Text(
                            text = cantidad.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = false).widthIn(min = 28.dp)
                        )
                        IconButton(onClick = onIncrease, enabled = enabled && canIncrease) {
                            Icon(Icons.Outlined.Add, contentDescription = "Aumentar cantidad de ${product.name}")
                        }
                    }
                }
                TextButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Quitar del carrito")
                }
            }
        }
    }
}
