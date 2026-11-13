package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.ui.components.StockAdjustmentDialog
import com.example.edmotor.viewmodel.ProductViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductManagementScreen(
    viewModel: ProductViewModel,
    onCreateProduct: () -> Unit,
    onEditProduct: (ProductEntity) -> Unit
) {
    val products by viewModel.products.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val message by viewModel.message.collectAsState()
    val isError by viewModel.isError.collectAsState()

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
    val availableUnits = products.sumOf { it.stock.coerceAtLeast(0) }
    val outOfStock = products.count { it.stock <= 0 && it.isActive }

    Scaffold(
        floatingActionButton = {
            Button(
                onClick = onCreateProduct,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Nuevo repuesto", modifier = Modifier.padding(start = 6.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Administración de inventario",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                "Edita repuestos, registra entradas y salidas y retira productos del catálogo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 5.dp, bottom = 16.dp)
            )

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

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                placeholder = { Text("Buscar repuestos...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(15.dp)
            )

            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(5.dp).size(20.dp),
                    strokeWidth = 2.dp
                )
            }

            if (message != null) {
                Text(
                    text = message.orEmpty(),
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (filteredProducts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("No hay repuestos registrados.", modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductManagementCard(
                            product = product,
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
        AlertDialog(
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
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProductManagementCard(
    product: ProductEntity,
    onEdit: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onDelete: () -> Unit
) {
    val money = remember {
        NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        product.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        product.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar producto")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar producto",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text("Precio: ${money.format(product.priceCop)}", fontWeight = FontWeight.SemiBold)
            if (product.isOnPromotion) {
                Text(
                    "Promoción: ${product.promotionPriceCop?.let { money.format(it) } ?: "Sin precio válido"}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Stock: ${product.stock}", fontWeight = FontWeight.Bold)
                    val statusColor = when {
                        product.stock <= 0 -> MaterialTheme.colorScheme.error
                        product.stock <= 3 -> Color(0xFFB7791F)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(
                        product.availabilityLabel,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onDecrease,
                        enabled = product.stock > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disminuir")
                    }
                    FilledTonalButton(
                        onClick = onIncrease,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Aumentar")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (product.isNew) InventoryTag("NUEVO")
                if (product.isOnPromotion) InventoryTag("PROMOCIÓN")
            }
        }
    }
}

@Composable
private fun InventoryTag(text: String) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
