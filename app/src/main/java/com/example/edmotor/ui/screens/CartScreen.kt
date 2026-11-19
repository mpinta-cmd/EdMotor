package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    val colors = MaterialTheme.colorScheme
    val productsById = remember(products) { products.associateBy { it.id } }
    val hasUnavailableProducts = cartQuantity.any { (productId, quantity) ->
        val product = productsById[productId]
        product == null || !product.isActive || quantity <= 0 ||
                quantity > product.stock || product.effectivePriceCop <= 0
    }
    val totalAmount = cartQuantity.entries.fold(BigInteger.ZERO) { total, (productId, quantity) ->
        val product = productsById[productId]?.takeIf { it.isActive && it.effectivePriceCop > 0 }
        total + (product?.effectivePriceCop ?: 0L).toBigInteger() *
                quantity.coerceAtLeast(0).toBigInteger()
    }
    val moneyFormat = remember { NumberFormat.getNumberInstance(Locale("es", "CO")) }
    val restriction = when {
        hasUnavailableProducts -> "Ajusta las cantidades o retira los repuestos no disponibles."
        totalAmount > Long.MAX_VALUE.toBigInteger() -> "El total supera el importe permitido. Reduce las cantidades."
        else -> null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, contentColor = colors.primary) {
                        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, modifier = Modifier.padding(12.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Carrito de compras",
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (cartQuantity.isNotEmpty()) {
                            val lines = cartQuantity.size
                            Text(
                                if (lines == 1) "1 repuesto" else "$lines repuestos",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        if (orderMessage != null) {
            item(key = "message") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isOrderError) colors.errorContainer else colors.secondaryContainer,
                    contentColor = if (isOrderError) colors.onErrorContainer else colors.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = orderMessage,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearOrderMessage) { Text("Cerrar") }
                    }
                }
            }
        }

        if (cartQuantity.isEmpty()) {
            item(key = "empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Text(
                        "Tu carrito está vacío. Selecciona repuestos del catálogo.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp)
                    )
                }
            }
        } else {
            items(cartQuantity.entries.toList(), key = { it.key }) { (productId, quantity) ->
                val product = productsById[productId]
                val unavailableReason = when {
                    quantity <= 0 -> "La cantidad guardada no es válida."
                    product == null || !product.isActive -> "Ya no está disponible. Cantidad: $quantity"
                    product.effectivePriceCop <= 0 -> "El precio de este repuesto no es válido."
                    else -> null
                }
                if (unavailableReason == null && product != null) {
                    CartItemCard(
                        product = product.toProduct(),
                        cantidad = quantity,
                        onIncrease = { onIncrease(productId) },
                        onDecrease = { onDecrease(productId) },
                        onRemove = { onRemove(productId) },
                        enabled = !isSubmittingOrder
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outline)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                product?.name ?: "Repuesto #$productId",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(unavailableReason.orEmpty(), color = colors.error)
                            TextButton(
                                onClick = { onRemove(productId) },
                                enabled = !isSubmittingOrder,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Quitar del carrito")
                            }
                        }
                    }
                }
            }

            item(key = "summary") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Total del pedido", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "\$" + moneyFormat.format(totalAmount),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Text(
                            restriction ?: "El pedido se guardará en este dispositivo, en Mis compras.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (restriction == null) colors.onSurfaceVariant else colors.error
                        )
                        Button(
                            onClick = onSubmitOrder,
                            enabled = !isSubmittingOrder && restriction == null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isSubmittingOrder) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = colors.onSurfaceVariant,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isSubmittingOrder) "Guardando pedido…" else "Guardar pedido",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
