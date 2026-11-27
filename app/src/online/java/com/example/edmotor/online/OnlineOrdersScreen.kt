package com.example.edmotor.online

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.UserRole
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun OnlineCartScreen(
    vm: OnlineViewModel,
    state: OnlineState,
    onQuantityChange: (Long, Int) -> Unit,
    onAuthenticate: () -> Unit,
    onBrowseCatalog: () -> Unit,
    onOrderCreated: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val guest = state.user == null
    val cart = state.cart
    val productsById = remember(state.products) { state.products.associateBy { it.id } }
    val cartEntries = cart.entries.toList()
    val unavailable = cartEntries.any { (id, quantity) ->
        val product = productsById[id]
        product == null || quantity <= 0 || quantity > product.stock || product.currentPriceCop <= 0
    }
    val total = cartEntries.fold(BigInteger.ZERO) { sum, (id, quantity) ->
        val price = productsById[id]?.currentPriceCop?.coerceAtLeast(0L) ?: 0L
        sum + price.toBigInteger() * quantity.coerceAtLeast(0).toBigInteger()
    }
    val restriction = when {
        unavailable -> "Ajusta las cantidades o retira los repuestos que ya no estén disponibles."
        cartEntries.size > 100 -> "Un pedido admite hasta 100 repuestos distintos."
        total > Long.MAX_VALUE.toBigInteger() -> "El total supera el importe permitido."
        else -> null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") { OnlineSectionTitle("Carrito de compras", Icons.Outlined.ShoppingCart) }
        if (state.user?.role == UserRole.WORKSHOP.name) {
            item(key = "unavailable") { OnlineListNote("El carrito está disponible en el perfil de cliente.") }
        } else if (cart.isEmpty()) {
            item(key = "empty") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnlineListNote("Tu carrito está vacío. Selecciona repuestos del catálogo.")
                    OutlinedButton(onClick = onBrowseCatalog, modifier = Modifier.fillMaxWidth()) {
                        Text("Explorar repuestos")
                    }
                }
            }
        } else {
            items(cartEntries, key = { it.key }) { (id, quantity) ->
                OnlineCartLine(
                    vm = vm,
                    id = id,
                    product = productsById[id],
                    quantity = quantity,
                    enabled = !state.busy,
                    onChange = { onQuantityChange(id, it) }
                )
            }
            item(key = "total") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Total del pedido", style = MaterialTheme.typography.labelLarge)
                        Text(onlinePrice(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text(
                            restriction ?: if (guest) "Crea tu perfil o inicia sesión para enviar el pedido."
                                else "El taller revisará el pedido. Puedes consultar su estado en Pedidos.",
                            color = if (restriction == null) colors.onPrimaryContainer else colors.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                if (guest) onAuthenticate() else vm.createOrder(cart, onCreated = onOrderCreated)
                            },
                            enabled = !state.busy && restriction == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(when {
                                state.busy -> "Enviando…"
                                guest -> "Continuar compra"
                                else -> "Enviar pedido"
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineCartLine(
    vm: OnlineViewModel,
    id: Long,
    product: OnlineProduct?,
    quantity: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val label = product?.name ?: "Repuesto #$id"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnlineProductPhoto(vm, product?.imagePath, Modifier.size(68.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (product == null) {
                        Text("Ya no está disponible.", color = colors.error)
                    } else {
                        Text(onlinePrice(product.currentPriceCop), style = MaterialTheme.typography.titleMedium, color = colors.primary)
                        Text(
                            if (quantity > product.stock) "Solo hay ${product.stock} disponibles."
                            else "${product.stock} disponibles",
                            color = if (quantity > product.stock) colors.error else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colors.surfaceContainerHigh,
                    contentColor = colors.onSurface
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = enabled && quantity > 0, onClick = { onChange(quantity - 1) }) {
                            Icon(Icons.Outlined.Remove, contentDescription = "Disminuir cantidad de $label")
                        }
                        Text(
                            quantity.toString(),
                            modifier = Modifier.weight(1f, fill = false).widthIn(min = 26.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall
                        )
                        IconButton(
                            enabled = enabled && product != null && quantity > 0 && quantity < product.stock,
                            onClick = { onChange(quantity + 1) }
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Aumentar cantidad de $label")
                        }
                    }
                }
                TextButton(enabled = enabled, onClick = { onChange(0) }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Quitar del carrito")
                }
            }
        }
    }
}

@Composable
internal fun OnlineOrdersScreen(vm: OnlineViewModel, state: OnlineState) {
    val workshop = state.user?.role == UserRole.WORKSHOP.name
    val orders = remember(state.orders, workshop) {
        state.orders.sortedWith(
            compareByDescending<OnlineOrder> { workshop && it.status == OrderStatus.PENDING }
                .thenByDescending { it.createdAtMillis }
                .thenByDescending { it.id }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            OnlineSectionTitle(if (workshop) "Pedidos del taller" else "Mis pedidos", Icons.Outlined.ReceiptLong)
        }
        if (!state.loading && orders.isEmpty()) {
            item(key = "empty") { OnlineListNote("Todavía no hay pedidos registrados.") }
        }
        items(orders, key = { it.id }) { order ->
            OnlineOrderCard(
                order = order,
                workshop = workshop,
                busy = state.busy,
                onAccept = { vm.decideOrder(order.id, OrderStatus.CONFIRMED) },
                onReject = { vm.decideOrder(order.id, OrderStatus.REJECTED) }
            )
        }
    }
}

@Composable
private fun OnlineOrderCard(
    order: OnlineOrder,
    workshop: Boolean,
    busy: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val date = remember(order.createdAtMillis) {
        SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO")).format(Date(order.createdAtMillis))
    }
    val (status, background, textColor) = when (order.status) {
        OrderStatus.PENDING -> Triple("Pendiente", colors.secondaryContainer, colors.onSecondaryContainer)
        OrderStatus.CONFIRMED -> Triple("Confirmado", colors.primaryContainer, colors.onPrimaryContainer)
        OrderStatus.REJECTED -> Triple("Rechazado", colors.errorContainer, colors.onErrorContainer)
        else -> Triple(order.status, colors.surfaceVariant, colors.onSurfaceVariant)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Pedido #${order.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(date, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = background,
                    contentColor = textColor
                ) {
                    Text(status, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (workshop) Text("Cliente: ${order.clientName}", style = MaterialTheme.typography.labelLarge)
            HorizontalDivider(color = colors.outlineVariant)
            order.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.productName, fontWeight = FontWeight.Medium)
                        Text(
                            "${item.quantity} × ${onlinePrice(item.unitPriceCop)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    Text(onlinePrice(item.unitPriceCop.toBigInteger() * item.quantity.toBigInteger()))
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colors.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text(onlinePrice(order.totalCop), style = MaterialTheme.typography.titleMedium, color = colors.primary)
                }
            }
            if (workshop && order.status == OrderStatus.PENDING) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(onClick = onAccept, enabled = !busy) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Aceptar pedido")
                    }
                    OutlinedButton(onClick = onReject, enabled = !busy) {
                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Rechazar pedido")
                    }
                }
            }
        }
    }
}
