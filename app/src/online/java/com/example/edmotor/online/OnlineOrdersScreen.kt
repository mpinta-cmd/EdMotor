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
import androidx.compose.material.icons.outlined.ShoppingCartCheckout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun OnlineCartScreen(
    vm: OnlineViewModel,
    state: OnlineState,
    onQuantityChange: (Long, Int) -> Unit,
    onCheckout: () -> Unit,
    onBrowseCatalog: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val guest = state.user == null
    val pending = state.pendingOrder
    val cart = pending?.items ?: state.cart
    val productsById = remember(state.products) { state.products.associateBy { it.id } }
    val cartEntries = cart.entries.toList()
    val review = remember(cart, productsById) { reviewOnlineCart(cart, productsById) }
    val catalog = state.syncStatus(OnlineResource.CATALOG)
    val restriction = when {
        pending != null -> null
        catalog.unavailable -> "Actualiza el catálogo para verificar precios y existencias."
        !catalog.loaded -> "Estamos verificando las existencias."
        else -> review.restriction
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
                    catalogLoaded = catalog.loaded,
                    verifyingPurchase = pending != null,
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
                        if (pending == null) {
                            Text("Total estimado", style = MaterialTheme.typography.labelLarge)
                            Text(onlinePrice(review.total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        } else {
                            Text("Compra por verificar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            when {
                                pending != null -> "Guardamos tus repuestos mientras verificamos la compra."
                                restriction != null -> restriction
                                guest -> "Inicia sesión o crea tu perfil para finalizar la compra."
                                else -> "Revisa tus repuestos y la forma de pago antes de confirmar."
                            },
                            color = if (pending == null && restriction != null) colors.error else colors.onPrimaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = onCheckout,
                            enabled = !state.busy && (pending != null || restriction == null),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (pending != null) "Revisar compra" else "Ir a pagar")
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
    catalogLoaded: Boolean,
    verifyingPurchase: Boolean,
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
                    if (verifyingPurchase) {
                        Text(
                            if (quantity == 1) "1 unidad en la compra" else "$quantity unidades en la compra",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (product == null) {
                        Text(
                            if (catalogLoaded) "Ya no está disponible." else "Disponibilidad por verificar.",
                            color = if (catalogLoaded) colors.error else colors.onSurfaceVariant
                        )
                    } else {
                        Text(onlinePrice(product.currentPriceCop), style = MaterialTheme.typography.titleMedium, color = colors.primary)
                        Text(
                            when {
                                product.stock == 0 -> "Agotado"
                                quantity > product.stock -> "Solo hay ${product.stock} disponibles."
                                product.stock == 1 -> "1 disponible"
                                else -> "${product.stock} disponibles"
                            },
                            color = if (quantity > product.stock) colors.error else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (!verifyingPurchase) {
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
}

@Composable
internal fun OnlineOrdersScreen(vm: OnlineViewModel, state: OnlineState) {
    val workshop = state.user?.role == UserRole.WORKSHOP.name
    val orders = remember(state.orders, workshop) {
        state.orders.sortedWith(
            compareByDescending<OnlineOrder> { workshop && it.status == OrderStatus.PENDING }
                .thenByDescending { workshop && it.paysOnPickup && it.status == OrderStatus.CONFIRMED }
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
        if (orders.isEmpty() && state.syncStatus(OnlineResource.ORDERS).waitingForFirstData) {
            item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        if (state.syncStatus(OnlineResource.ORDERS).loaded && orders.isEmpty()) {
            item(key = "empty") { OnlineListNote("Todavía no hay pedidos registrados.") }
        }
        items(orders, key = { it.id }) { order ->
            OnlineOrderCard(
                order = order,
                workshop = workshop,
                busy = state.busy,
                onAccept = { vm.decideOrder(order.id, OrderStatus.CONFIRMED) },
                onReject = { vm.decideOrder(order.id, OrderStatus.REJECTED) },
                onComplete = { vm.decideOrder(order.id, OrderStatus.COMPLETED) }
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
    onReject: () -> Unit,
    onComplete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val date = remember(order.createdAtMillis) {
        SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("es", "CO")).format(Date(order.createdAtMillis))
    }
    val pickup = order.paysOnPickup
    var confirmingCancellation by remember(order.id) { mutableStateOf(false) }
    val (status, background, textColor) = when (order.status) {
        OrderStatus.PENDING -> Triple(if (pickup) "Por preparar" else "Pendiente", colors.secondaryContainer, colors.onSecondaryContainer)
        OrderStatus.CONFIRMED -> Triple(if (pickup) "Listo para recoger" else "Confirmado", colors.primaryContainer, colors.onPrimaryContainer)
        OrderStatus.REJECTED -> Triple(if (pickup) "Cancelado" else "Rechazado", colors.errorContainer, colors.onErrorContainer)
        OrderStatus.COMPLETED -> Triple("Pagado y entregado", colors.primaryContainer, colors.onPrimaryContainer)
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
            if (pickup && (order.status == OrderStatus.PENDING || order.status == OrderStatus.CONFIRMED)) {
                Text("Pago al recoger en el taller", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
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
                        Text(if (pickup) "Marcar listo para recoger" else "Aceptar pedido")
                    }
                    OutlinedButton(
                        onClick = { if (pickup) confirmingCancellation = true else onReject() },
                        enabled = !busy
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (pickup) "Cancelar compra" else "Rechazar pedido")
                    }
                }
            }
            if (workshop && pickup && order.status == OrderStatus.CONFIRMED) {
                Button(onClick = onComplete, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Registrar pago y entrega")
                }
            }
        }
    }
    if (confirmingCancellation && order.status == OrderStatus.PENDING) {
        AlertDialog(
            onDismissRequest = { confirmingCancellation = false },
            title = { Text("Cancelar compra") },
            text = { Text("Las unidades reservadas volverán al inventario.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingCancellation = false
                        onReject()
                    },
                    enabled = !busy
                ) { Text("Confirmar cancelación") }
            },
            dismissButton = { TextButton(onClick = { confirmingCancellation = false }) { Text("Volver") } }
        )
    }
}
