package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.ui.components.EdMotorBottomSheet
import com.example.edmotor.ui.components.ProductImage
import com.example.edmotor.ui.components.StockAdjustmentDialog
import com.example.edmotor.ui.theme.EdMotorHeroNavy
import com.example.edmotor.ui.theme.EdMotorPhotoMatte
import com.example.edmotor.viewmodel.ProductViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductManagementScreen(
    viewModel: ProductViewModel,
    onCreateProduct: () -> Unit,
    onEditProduct: (ProductEntity) -> Unit,
    onBack: () -> Unit
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isError by viewModel.isError.collectAsStateWithLifecycle()

    var stockProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var stockIncrease by remember { mutableStateOf(true) }
    var deleteProduct by remember { mutableStateOf<ProductEntity?>(null) }

    val filteredProducts = remember(products, query) {
        products.filter {
            it.name.contains(query, true) ||
                    it.category.contains(query, true) ||
                    it.description.contains(query, true)
        }
    }
    val availableUnits = products.sumOf { it.stock.coerceAtLeast(0).toLong() }
    val outOfStock = products.count { it.stock <= 0 && it.isActive }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        floatingActionButton = {
            Button(
                onClick = onCreateProduct,
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Nuevo repuesto", modifier = Modifier.padding(start = 6.dp))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp)
            ) {
                item(key = "title") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = EdMotorHeroNavy,
                        contentColor = ProductHeaderContent,
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)) {
                            TextButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp).size(20.dp),
                                    tint = ProductHeaderContent
                                )
                                Text("Volver", color = ProductHeaderContent)
                            }
                            Text(
                                "Administración de inventario",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                item(key = "metrics") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InventoryMetric("Productos", products.size.toString(), Modifier.weight(1f))
                        InventoryMetric("Unidades", availableUnits.toString(), Modifier.weight(1f))
                        InventoryMetric(
                            "Agotados",
                            outOfStock.toString(),
                            Modifier.weight(1f),
                            warning = outOfStock > 0
                        )
                    }
                }
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::updateSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar repuestos") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp)
                    )
                }
                if (isSaving) {
                    item(key = "loading") {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(5.dp).size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                message?.let { status ->
                    item(key = "message") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isError) colors.errorContainer else colors.primaryContainer,
                            contentColor = if (isError) colors.onErrorContainer else colors.onPrimaryContainer
                        ) {
                            Text(
                                text = status,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (filteredProducts.isEmpty()) {
                    item(key = "empty") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp),
                                    tint = colors.primary
                                )
                                Text(
                                    if (query.isBlank()) "No hay repuestos registrados."
                                    else "No encontramos repuestos para esta búsqueda."
                                )
                            }
                        }
                    }
                } else {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductManagementCard(
                            product = product,
                            enabled = !isSaving,
                            onEdit = { onEditProduct(product) },
                            onIncrease = {
                                stockProduct = product
                                stockIncrease = true
                            },
                            onDecrease = {
                                stockProduct = product
                                stockIncrease = false
                            },
                            onDelete = { deleteProduct = product }
                        )
                    }
                }
            }
        }
    }

    stockProduct?.let { product ->
        StockAdjustmentDialog(
            productName = product.name,
            currentStock = product.stock,
            increase = stockIncrease,
            onDismiss = { stockProduct = null },
            onConfirm = { amount ->
                viewModel.adjustStock(
                    productId = product.id,
                    delta = if (stockIncrease) amount else -amount
                )
                stockProduct = null
            }
        )
    }

    deleteProduct?.let { product ->
        EdMotorBottomSheet(
            onDismissRequest = { deleteProduct = null },
            title = { Text("Eliminar repuesto") },
            text = {
                Text(
                    "¿Estás seguro de eliminar ${product.name} del catálogo? " +
                            "Se ocultará para las ventas nuevas, pero se conservará la información " +
                            "histórica de pedidos y mantenimientos."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        viewModel.deleteProduct(product.id)
                        deleteProduct = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteProduct = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun InventoryMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (warning) colors.secondaryContainer else colors.surface,
        contentColor = if (warning) colors.onSecondaryContainer else colors.onSurface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (warning) colors.onSecondaryContainer else colors.primary
            )
            Text(title, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductManagementCard(
    product: ProductEntity,
    enabled: Boolean,
    onEdit: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val money = remember { NumberFormat.getCurrencyInstance(Locale("es", "CO")) }
    val hasValidPromotion = product.effectivePriceCop != product.priceCop
    val stockColor = when {
        !product.isActive -> colors.onSurfaceVariant
        product.isOutOfStock -> colors.onErrorContainer
        product.stock <= 3 -> colors.onSecondaryContainer
        else -> colors.onPrimaryContainer
    }
    val statusBackground = when {
        !product.isActive -> colors.surfaceVariant
        product.isOutOfStock -> colors.errorContainer
        product.stock <= 3 -> colors.secondaryContainer
        else -> colors.primaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(70.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = EdMotorPhotoMatte,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    ProductImage(
                        imageUri = product.imageUri,
                        imageRes = product.imageRes,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(5.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        product.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        product.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (product.isNew || product.isOnPromotion) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (product.isNew) {
                                InventoryTag("NUEVO", colors.primaryContainer, colors.onPrimaryContainer)
                            }
                            if (product.isOnPromotion) {
                                InventoryTag(
                                    "PROMOCIÓN", colors.secondaryContainer, colors.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (product.isOnPromotion) {
                    Text(
                        "Promoción: ${product.promotionPriceCop?.let { money.format(it) } ?: "Sin precio válido"}",
                        color = colors.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    "Precio: ${money.format(product.priceCop)}",
                    style = if (hasValidPromotion) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.titleMedium,
                    color = if (hasValidPromotion) colors.onSurfaceVariant else colors.primary,
                    textDecoration = if (hasValidPromotion) TextDecoration.LineThrough else null,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = statusBackground,
                contentColor = stockColor
            ) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Stock: ${product.stock}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        product.availabilityLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            HorizontalDivider(color = colors.outline)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onDecrease,
                    enabled = enabled && product.stock > 0,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Disminuir")
                }
                FilledTonalButton(
                    onClick = onIncrease,
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Aumentar")
                }
                Row {
                    IconButton(onClick = onEdit, enabled = enabled) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar producto")
                    }
                    IconButton(onClick = onDelete, enabled = enabled) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar producto",
                            tint = if (enabled) colors.error else colors.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryTag(text: String, background: Color, foreground: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = background,
        contentColor = foreground
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
