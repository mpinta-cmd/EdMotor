
package com.example.edmotor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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

    val canIncrease =
        product.stock > 0 &&
                cantidad < product.stock

    val stockMessage = when {

        product.stock <= 0 ->
            "AGOTADO"

        cantidad > product.stock ->
            "Stock insuficiente. Reduce la cantidad."

        cantidad >= product.stock ->
            "Límite de existencias alcanzado."

        else ->
            "Existencias disponibles: ${product.stock}"
    }

    val stockColor = when {

        product.stock <= 0 ||
                cantidad > product.stock ->
            MaterialTheme.colorScheme.error

        cantidad >= product.stock ->
            MaterialTheme.colorScheme.error

        else ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // IMAGEN DEL PRODUCTO

            ProductImage(
                imageUri = product.imageUri,
                imageRes = product.imageRes,
                contentDescription = product.contentDescription,
                modifier = Modifier
                    .size(60.dp)
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            // INFORMACIÓN DEL PRODUCTO

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = product.effectivePrice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = stockMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = stockColor,
                    fontWeight = FontWeight.Bold
                )
            }

            // CONTROLES DE CANTIDAD

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                OutlinedButton(
                    onClick = onDecrease,
                    enabled = enabled && cantidad > 0,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "Disminuir cantidad de ${product.name}"
                    }
                ) {

                    Text("-")
                }

                Text(
                    text = cantidad.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = onIncrease,
                    enabled = enabled && canIncrease,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = "Aumentar cantidad de ${product.name}"
                    }
                ) {

                    Text("+")
                }
            }
        }
        TextButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.align(Alignment.End).padding(end = 12.dp)
        ) {
            Text("Quitar del carrito")
        }
    }
}
