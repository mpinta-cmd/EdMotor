package com.example.edmotor.online

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.UserRole
import com.example.edmotor.ui.screens.ProductHeaderContent
import com.example.edmotor.ui.theme.EdMotorHeroAmber
import com.example.edmotor.ui.theme.EdMotorHeroNavy
import com.example.edmotor.ui.theme.EdMotorPhotoMatte
import com.example.edmotor.ui.theme.GrisCarbon

@Composable
internal fun OnlineProductDetailScreen(
    vm: OnlineViewModel,
    state: OnlineState,
    productId: Long,
    onBack: () -> Unit,
    onOpenCart: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val workshop = state.user?.role == UserRole.WORKSHOP.name
    val quantity = state.cart[productId] ?: 0
    val cartCount = state.cart.values.sumOf { it.toLong() }
    val product = state.products.firstOrNull { it.id == productId }
    var editing by rememberSaveable(productId) { mutableStateOf(false) }
    var confirmingRemoval by rememberSaveable(productId) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(color = EdMotorHeroNavy, contentColor = ProductHeaderContent) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    "DETALLE DEL REPUESTO",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (workshop) {
                    Spacer(Modifier.width(48.dp))
                } else {
                    IconButton(onClick = onOpenCart) {
                        BadgedBox(badge = {
                            if (cartCount > 0) {
                                Badge(containerColor = EdMotorHeroAmber, contentColor = GrisCarbon) {
                                    Text(if (cartCount > 99) "99+" else cartCount.toString())
                                }
                            }
                        }) {
                            Icon(Icons.Outlined.ShoppingCart, contentDescription = "Abrir carrito")
                        }
                    }
                }
            }
        }

        if (product == null) {
            val sync = state.syncStatus(OnlineResource.CATALOG)
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                if (sync.waitingForFirstData) CircularProgressIndicator()
                Text(
                    when {
                        sync.waitingForFirstData -> "Cargando el repuesto…"
                        sync.unavailable -> "No pudimos consultar el repuesto."
                        else -> "Este repuesto ya no está disponible."
                    },
                    textAlign = TextAlign.Center
                )
                if (sync.unavailable) TextButton(onClick = vm::refresh) { Text("Reintentar") }
            }
            return@Column
        }

        Column(
            modifier = Modifier.weight(1f).widthIn(max = 680.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = EdMotorPhotoMatte,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = 240.dp)) {
                    OnlineProductPhoto(
                        vm,
                        product.imagePath,
                        Modifier.matchParentSize().padding(12.dp)
                            .semantics { contentDescription = "Foto de ${product.name}" }
                    )
                    when {
                        product.stock <= 0 -> DetailStockBadge("AGOTADO", Modifier.align(Alignment.TopStart))
                        product.currentPriceCop < product.priceCop ->
                            DetailStockBadge("PROMOCIÓN", Modifier.align(Alignment.TopStart))
                        else -> Unit
                    }
                    if (!workshop) {
                        OnlineFavoriteButton(
                            product.name,
                            product.id in state.favoriteProductIds,
                            { vm.toggleFavorite(product.id) },
                            Modifier.align(Alignment.TopEnd).padding(6.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    product.category.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                Text(
                    product.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                OnlineProductRating(product, detailed = true)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.outline),
                color = colors.surface
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PRECIO DEL PRODUCTO", style = MaterialTheme.typography.labelMedium)
                    OnlineProductPrice(product, prominent = true)
                    Text("Disponibilidad", style = MaterialTheme.typography.labelSmall)
                    Text(stockLabel(product.stock), fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, colors.outline),
                color = colors.surface
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Descripción del producto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(product.description, color = colors.onSurfaceVariant)
                    HorizontalDivider(color = colors.outline)
                    Text(
                        "Información de compra",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    DetailInformation(
                        if (workshop) "Existencias disponibles" else "Unidades en tu carrito",
                        if (workshop) product.stock.toString() else quantity.toString()
                    )
                    DetailInformation("Identificación", "REF-${product.id}")
                }
            }
        }

        ProductDetailFooter(
            product = product,
            state = state,
            quantity = quantity,
            cartCount = cartCount,
            workshop = workshop,
            onAdd = { vm.addToCart(product.id) },
            onRemove = { vm.removeFromCart(product.id) },
            onOpenCart = onOpenCart,
            onEdit = { editing = true },
            onDeactivate = { confirmingRemoval = true }
        )

        if (workshop && editing) {
            OnlineProductEditor(vm, state, product, onDismiss = { editing = false })
        }
        if (workshop && confirmingRemoval) {
            AlertDialog(
                onDismissRequest = { confirmingRemoval = false },
                title = { Text("Desactivar repuesto") },
                text = { Text("${product.name} dejará de estar disponible en el catálogo.") },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmingRemoval = false
                            vm.deleteProduct(product, onDeleted = onBack)
                        },
                        enabled = !state.busy
                    ) { Text("Desactivar") }
                },
                dismissButton = { TextButton(onClick = { confirmingRemoval = false }) { Text("Volver") } }
            )
        }
    }
}

@Composable
private fun ProductDetailFooter(
    product: OnlineProduct,
    state: OnlineState,
    quantity: Int,
    cartCount: Long,
    workshop: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onOpenCart: () -> Unit,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val enabled = !state.busy && state.pendingOrder == null
    val canAdd = enabled && quantity < product.stock
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (workshop) {
                    Button(onClick = onEdit, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Editar repuesto")
                    }
                    TextButton(onClick = onDeactivate, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Desactivar repuesto")
                    }
                    return@Column
                }
                if (product.stock > 0 || quantity > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Cantidad", fontWeight = FontWeight.Bold)
                            Text("$quantity en carrito", style = MaterialTheme.typography.bodySmall)
                        }
                        Box(Modifier.width(148.dp)) {
                            OnlineCatalogQuantityControls(
                                name = product.name,
                                quantity = quantity,
                                canAdd = canAdd,
                                canRemove = enabled && quantity > 0,
                                onAdd = onAdd,
                                onRemove = onRemove
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onAdd()
                    },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(when {
                        product.stock <= 0 -> "Producto agotado"
                        quantity > 0 -> "Agregar otra unidad"
                        else -> "Agregar al carrito"
                    })
                }
                OutlinedButton(onClick = onOpenCart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Ver mi carrito ($cartCount)")
                }
            }
        }
    }
}

@Composable
private fun DetailStockBadge(label: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.padding(12.dp),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailInformation(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
