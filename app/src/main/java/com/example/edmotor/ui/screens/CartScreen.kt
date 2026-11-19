
package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.data.toProduct
import com.example.edmotor.ui.components.CartItemCard
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CartScreen(
    products: List<ProductEntity>,
    cartQuantity: Map<Int, Int>,
    onIncrease: (Int) -> Unit,
    onDecrease: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onSubmitOrder: () -> Unit,
    isSubmittingOrder: Boolean,
    orderMessage: String?,
    isOrderError: Boolean,
    onClearOrderMessage: () -> Unit
) {

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
    }

    val productsById = remember(products) { products.associateBy { it.id } }
    val cartItems = cartQuantity.filterValues { it > 0 }
    val hasUnavailableProducts = cartItems.any { (productId, quantity) ->
        val product = productsById[productId]
        product == null || !product.isActive || quantity > product.stock
    }
    val totalAmount = cartItems.entries.fold(BigInteger.ZERO) { total, (productId, quantity) ->
        val product = productsById[productId]?.takeIf { it.isActive }
        total + (product?.effectivePriceCop ?: 0L).toBigInteger() * quantity.toBigInteger()
    }
    val totalOutOfRange = totalAmount > Long.MAX_VALUE.toBigInteger()
    val moneyFormat = remember { NumberFormat.getNumberInstance(Locale("es", "CO")) }
    val formattedTotal = moneyFormat.format(totalAmount)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            "Carrito de compras",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (orderMessage != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = orderMessage,
                    color = if (isOrderError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearOrderMessage) { Text("Cerrar") }
            }
        }

        if (cartItems.isEmpty()) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    "Tu carrito está vacío. Selecciona repuestos del catálogo.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        } else {

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                state = listState
            ) {

                items(
                    items = cartItems.entries.toList(),
                    key = { it.key }
                ) { (productId, quantity) ->
                    val product = productsById[productId]
                    if (product != null && product.isActive) {
                        CartItemCard(
                            product = product.toProduct(),
                            cantidad = quantity,
                            onIncrease = { onIncrease(productId) },
                            onDecrease = { onDecrease(productId) },
                            onRemove = { onRemove(productId) },
                            enabled = !isSubmittingOrder
                        )
                    } else {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(product?.name ?: "Repuesto #$productId", fontWeight = FontWeight.Bold)
                                Text("Ya no está disponible. Cantidad: $quantity")
                                TextButton(
                                    onClick = { onRemove(productId) },
                                    enabled = !isSubmittingOrder
                                ) { Text("Quitar del carrito") }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            "Total del pedido:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            "\$$formattedTotal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    val restriction = when {
                        hasUnavailableProducts -> "Ajusta las cantidades o retira los repuestos no disponibles."
                        totalOutOfRange -> "El total supera el importe permitido. Reduce las cantidades."
                        else -> null
                    }
                    Text(
                        restriction ?: "El pedido se guardará en este dispositivo, en Mis compras.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (restriction == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onSubmitOrder,
                        enabled = !isSubmittingOrder && restriction == null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {

                        if (isSubmittingOrder) {

                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        Text(
                            if (isSubmittingOrder) {
                                "Guardando pedido…"
                            } else {
                                "Guardar pedido"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
