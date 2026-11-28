package com.example.edmotor.online

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCartCheckout
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.UserRole
import java.math.BigInteger

internal val OnlineOrder.paysOnPickup: Boolean get() = paymentMethod == "PAY_ON_PICKUP"

internal data class OnlineCartReview(val total: BigInteger, val restriction: String?) {
    val totalCop: Long? get() = total.takeIf { restriction == null && it.signum() > 0 }?.toLong()
}

internal fun reviewOnlineCart(
    cart: Map<Long, Int>,
    productsById: Map<Long, OnlineProduct>
): OnlineCartReview {
    var total = BigInteger.ZERO
    var unavailable = false
    cart.forEach { (id, quantity) ->
        val product = productsById[id]
        val price = product?.currentPriceCop ?: 0L
        if (product == null || quantity <= 0 || quantity > product.stock || price <= 0L) {
            unavailable = true
        }
        total += price.coerceAtLeast(0L).toBigInteger() * quantity.coerceAtLeast(0).toBigInteger()
    }
    val restriction = when {
        unavailable -> "Ajusta las cantidades o retira los repuestos que ya no estén disponibles."
        cart.size > 100 -> "Una compra admite hasta 100 repuestos distintos."
        total > Long.MAX_VALUE.toBigInteger() -> "El total supera el importe permitido."
        else -> null
    }
    return OnlineCartReview(total, restriction)
}

@Composable
internal fun OnlineCheckoutScreen(
    state: OnlineState,
    onConfirmPurchase: (Map<Long, Int>, Long?, String?) -> Unit,
    onBackToCart: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val pending = state.pendingOrder
    val cart = pending?.items ?: state.cart
    val productsById = remember(state.products) { state.products.associateBy { it.id } }
    val review = remember(cart, productsById) { reviewOnlineCart(cart, productsById) }
    val catalog = state.syncStatus(OnlineResource.CATALOG)
    val restriction = when {
        pending != null -> null
        catalog.unavailable -> "Actualiza el catálogo para verificar precios y existencias."
        !catalog.loaded -> "Estamos verificando las existencias."
        else -> review.restriction
    }
    val expectedTotal = if (pending == null) review.totalCop else pending.expectedTotalCop
    val checkoutForPickup = pending == null || pending.expectedTotalCop != null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            OnlineSectionTitle(
                "Finalizar compra",
                Icons.Outlined.ShoppingCartCheckout
            )
        }
        if (state.user?.role != UserRole.CLIENT.name || cart.isEmpty()) {
            item(key = "empty") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val message = if (cart.isEmpty()) "Tu carrito está vacío." else "Entra con tu cuenta de cliente para continuar."
                    OnlineListNote(message)
                    OutlinedButton(onClick = onBackToCart, modifier = Modifier.fillMaxWidth()) {
                        Text("Volver al carrito")
                    }
                }
            }
            return@LazyColumn
        }
        if (pending != null) {
            item(key = "pending") {
                OnlineListNote("Estamos verificando tu compra. Puedes reintentar la confirmación con los mismos repuestos.")
            }
        }
        item(key = "summary") {
            Text(
                "Resumen de compra",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        items(cart.entries.toList(), key = { it.key }) { (id, quantity) ->
            val product = productsById[id]
            val unitPrice = product?.currentPriceCop?.takeIf { pending == null && it > 0 }
            CheckoutProductLine(product?.name ?: "Repuesto #$id", quantity, unitPrice)
        }
        if (checkoutForPickup) {
            item(key = "pickup") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CheckoutDetail(Icons.Outlined.Storefront, "Entrega", "Recoger en el taller")
                        HorizontalDivider(color = colors.outlineVariant)
                        CheckoutDetail(Icons.Outlined.Payments, "Forma de pago", "Pagar al recoger")
                    }
                }
            }
        }
        item(key = "actions") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (expectedTotal != null) {
                        Text(
                            if (pending == null) "Total a pagar al recoger" else "Total por confirmar",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            onlinePrice(expectedTotal),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    restriction?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = colors.onPrimaryContainer
                        )
                    }
                    Button(
                        onClick = { onConfirmPurchase(cart, expectedTotal, pending?.requestId) },
                        enabled = !state.busy && (pending != null || (restriction == null && expectedTotal != null)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                state.busy -> "Confirmando…"
                                pending != null -> "Reintentar confirmación"
                                else -> "Confirmar compra"
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = onBackToCart,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Volver al carrito")
                    }
                }
            }
        }
    }
}

@Composable
internal fun OnlinePurchaseReceipt(
    order: OnlineOrder,
    onViewOrders: () -> Unit,
    onBrowseCatalog: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val cancelled = order.status == OrderStatus.REJECTED
    val delivered = order.status == OrderStatus.COMPLETED
    val ready = order.paysOnPickup && order.status == OrderStatus.CONFIRMED
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            OnlineSectionTitle(
                when {
                    cancelled -> if (order.paysOnPickup) "Compra cancelada" else "Pedido rechazado"
                    delivered -> "Compra entregada"
                    ready -> "Listo para recoger"
                    order.paysOnPickup -> "Compra confirmada"
                    else -> "Pedido enviado"
                },
                if (cancelled) Icons.Outlined.Close else Icons.Outlined.CheckCircle
            )
        }
        item(key = "details") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CheckoutDetail(Icons.Outlined.ReceiptLong, "Pedido registrado", "Pedido #${order.id}")
                    if (order.paysOnPickup && !cancelled) {
                        CheckoutDetail(
                            Icons.Outlined.Payments,
                            if (delivered) "Total pagado" else "Total a pagar al recoger",
                            onlinePrice(order.totalCop)
                        )
                        Text(
                            when {
                                delivered -> "El taller registró el pago y la entrega de tus repuestos."
                                ready -> "Ya puedes recoger y pagar tus repuestos en el taller."
                                else -> "Consulta Mis pedidos para saber cuándo puedes recoger tus repuestos en el taller."
                            },
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (!cancelled) {
                        Text(onlinePrice(order.totalCop), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onViewOrders, modifier = Modifier.fillMaxWidth()) { Text("Ver mis pedidos") }
                OutlinedButton(onClick = onBrowseCatalog, modifier = Modifier.fillMaxWidth()) { Text("Seguir comprando") }
            }
        }
    }
}

@Composable
private fun CheckoutProductLine(name: String, quantity: Int, unitPrice: Long?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (unitPrice != null) "$quantity × ${onlinePrice(unitPrice)}"
                    else if (quantity == 1) "1 unidad" else "$quantity unidades",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (unitPrice != null) {
                Text(
                    onlinePrice(unitPrice.toBigInteger() * quantity.toBigInteger()),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun CheckoutDetail(icon: ImageVector, label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
