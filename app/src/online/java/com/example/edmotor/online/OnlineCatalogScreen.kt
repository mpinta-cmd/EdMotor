package com.example.edmotor.online

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.FAVORITES_FILTER_KEY
import com.example.edmotor.data.catalogCategoryFilterKey
import com.example.edmotor.data.categoryList
import com.example.edmotor.data.productList
import com.example.edmotor.model.Category
import com.example.edmotor.model.UserRole
import com.example.edmotor.ui.components.CategoryBadge
import com.example.edmotor.ui.components.ProductImage
import com.example.edmotor.ui.components.ProductImageChoice
import com.example.edmotor.ui.components.ProductImagePicker
import com.example.edmotor.ui.components.StockAdjustmentDialog
import com.example.edmotor.ui.screens.CatalogCover
import com.example.edmotor.ui.theme.EdMotorPhotoMatte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.RoundingMode
import java.text.Collator
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

private val localeColombia = Locale("es", "CO")
private val accentMarks = Regex("\\p{M}+")

internal val OnlineProduct.currentPriceCop: Long
    get() = promotionPriceCop?.takeIf { it in 1 until priceCop } ?: priceCop

internal fun onlinePrice(price: Number): String =
    "\$" + NumberFormat.getIntegerInstance(localeColombia).format(price)

private fun searchTerm(value: String): String =
    Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(accentMarks, "")

@Composable
internal fun OnlineCatalogScreen(
    vm: OnlineViewModel,
    state: OnlineState,
    onAddToCart: (Long) -> Unit,
    onRemoveFromCart: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    voiceSearch: OnlineVoiceSearchState,
    onOpenAppointments: () -> Unit,
    onReviewOrder: () -> Unit
) {
    val workshop = state.user?.role == UserRole.WORKSHOP.name
    val cart = state.cart
    val pending = state.pendingOrder != null
    val query = voiceSearch.query.trim()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showingEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<OnlineProduct?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deactivating by remember { mutableStateOf<OnlineProduct?>(null) }
    var stockAdjustmentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var increasingStock by rememberSaveable { mutableStateOf(true) }
    val hasFilters = query.isNotEmpty() || selectedCategory != null
    val categories = remember(state.products) {
        (categoryList + state.products.map { Category(emoji = "", nombre = it.category) })
            .distinctBy { catalogCategoryFilterKey(it.nombre) }
    }
    val products = remember(query, selectedCategory, state.products, state.favoriteProductIds) {
        val term = searchTerm(query)
        val alphabet = Collator.getInstance(localeColombia).apply { strength = Collator.PRIMARY }
        state.products.filter { product ->
            val matchesCategory = when (selectedCategory) {
                null -> true
                FAVORITES_FILTER_KEY -> product.id in state.favoriteProductIds
                else -> catalogCategoryFilterKey(product.category) == selectedCategory
            }
            val matchesQuery = term.isEmpty() ||
                searchTerm("${product.name} ${product.category} ${product.description}").contains(term) ||
                (product.currentPriceCop < product.priceCop && "oferta".contains(term))
            matchesCategory && matchesQuery
        }.sortedWith(
            compareByDescending<OnlineProduct> { it.currentPriceCop < it.priceCop }
                .thenComparator { first, second -> alphabet.compare(first.name, second.name) }
                .thenBy { it.id }
        )
    }
    val selectedCategoryName = categories.firstOrNull { catalogCategoryFilterKey(it.nombre) == selectedCategory }?.nombre
    val catalogTitle = when {
        selectedCategory == FAVORITES_FILTER_KEY -> "Favoritos"
        selectedCategoryName != null -> selectedCategoryName
        query.isNotEmpty() -> "Resultados"
        workshop -> "Inventario de repuestos"
        else -> "Catálogo de repuestos"
    }

    LaunchedEffect(query, selectedCategory) { gridState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                OnlineVoiceSearchField(voiceSearch)
            }
            OnlineCatalogCategoryFilters(categories, selectedCategory) { selectedCategory = it }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (LocalDensity.current.fontScale > 1.4f) 230.dp else 155.dp),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasFilters) {
                item(key = "catalog-cover", span = { GridItemSpan(maxLineSpan) }) {
                    CatalogCover(isWorkshop = workshop) {
                        if (workshop) scope.launch { gridState.animateScrollToItem(1) }
                        else onOpenAppointments()
                    }
                }
            }
            if (workshop) {
                item(key = "catalog-create", span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = { editing = null; showingEditor = true },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar repuesto")
                    }
                }
            } else if (pending) {
                item(key = "catalog-pending", span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedButton(onClick = onReviewOrder, modifier = Modifier.fillMaxWidth()) {
                        Text("Revisar envío pendiente")
                    }
                }
            }
            item(key = "catalog-count", span = { GridItemSpan(maxLineSpan) }) {
                OnlineCatalogResults(catalogTitle, products.size)
            }
            if (products.isEmpty() && (state.products.isNotEmpty() || state.syncStatus(OnlineResource.CATALOG).loaded)) {
                item(key = "catalog-empty", span = { GridItemSpan(maxLineSpan) }) {
                    val message = when {
                        selectedCategory == FAVORITES_FILTER_KEY -> "Aún no tienes favoritos."
                        hasFilters -> "No encontramos repuestos con esos filtros."
                        else -> "Todavía no hay repuestos en el catálogo."
                    }
                    Column {
                        OnlineListNote(message)
                        if (hasFilters) {
                            TextButton(onClick = { voiceSearch.query = ""; selectedCategory = null }) {
                                Text("Ver todos los repuestos")
                            }
                        }
                    }
                }
            }
            gridItems(products, key = { it.id }) { product ->
                OnlineProductCard(
                    vm = vm,
                    product = product,
                    workshop = workshop,
                    quantity = cart[product.id] ?: 0,
                    busy = state.busy || pending,
                    isFavorite = product.id in state.favoriteProductIds,
                    onOpen = { selectedId = product.id },
                    onAdd = { onAddToCart(product.id) },
                    onRemove = { onRemoveFromCart(product.id) },
                    onToggleFavorite = { onToggleFavorite(product.id) },
                    onEdit = { editing = product; showingEditor = true },
                    onAdjustStock = { increase ->
                        stockAdjustmentId = product.id
                        increasingStock = increase
                    }
                )
            }
        }
    }

    OnlineVoiceSearchDialogs(voiceSearch)

    val selected = state.products.firstOrNull { it.id == selectedId }
    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text(selected.name) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        OnlineProductPhoto(vm, selected.imagePath, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                        if (!workshop) {
                            OnlineFavoriteButton(
                                selected.name,
                                selected.id in state.favoriteProductIds,
                                { onToggleFavorite(selected.id) },
                                Modifier.align(Alignment.TopEnd)
                            )
                        }
                    }
                    OnlineProductPrice(selected)
                    Text(selected.category, style = MaterialTheme.typography.labelLarge)
                    OnlineProductRating(selected, detailed = true)
                    if (selected.description.isNotBlank()) Text(selected.description)
                    Text(stockLabel(selected.stock), style = MaterialTheme.typography.bodySmall)
                    if (workshop) {
                        TextButton(
                            onClick = { selectedId = null; deactivating = selected },
                            enabled = !state.busy
                        ) {
                            Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Desactivar repuesto")
                        }
                    } else {
                        OnlineCatalogCartControls(
                            product = selected,
                            quantity = cart[selected.id] ?: 0,
                            enabled = !state.busy && !pending,
                            onAdd = { onAddToCart(selected.id) },
                            onRemove = { onRemoveFromCart(selected.id) },
                            addLabel = "Agregar al carrito"
                        )
                    }
                }
            },
            confirmButton = {
                if (workshop) {
                    Button(
                        enabled = !state.busy,
                        onClick = { selectedId = null; editing = selected; showingEditor = true }
                    ) { Text("Editar repuesto") }
                } else {
                    Button(onClick = { selectedId = null; onReviewOrder() }) {
                        Text("Ver mi carrito (${cart.values.sumOf { it.toLong() }})")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { selectedId = null }) { Text("Cerrar") } }
        )
    }

    if (workshop && showingEditor) {
        OnlineProductEditor(
            vm = vm,
            state = state,
            product = editing,
            onDismiss = { showingEditor = false }
        )
    }

    val adjusting = state.products.firstOrNull { it.id == stockAdjustmentId }
    if (workshop && adjusting != null) {
        StockAdjustmentDialog(
            productName = adjusting.name,
            currentStock = adjusting.stock,
            increase = increasingStock,
            onDismiss = { stockAdjustmentId = null },
            onConfirm = { amount ->
                vm.adjustInventoryStock(adjusting.id, if (increasingStock) amount else -amount)
                stockAdjustmentId = null
            }
        )
    }

    val toDeactivate = deactivating
    if (workshop && toDeactivate != null) {
        AlertDialog(
            onDismissRequest = { deactivating = null },
            title = { Text("Desactivar repuesto") },
            text = { Text("${toDeactivate.name} dejará de estar disponible en el catálogo.") },
            confirmButton = {
                Button(enabled = !state.busy, onClick = {
                    vm.deleteProduct(toDeactivate)
                    deactivating = null
                }) { Text("Desactivar") }
            },
            dismissButton = { TextButton(onClick = { deactivating = null }) { Text("Volver") } }
        )
    }
}

@Composable
private fun OnlineCatalogCategoryFilters(
    categories: List<Category>,
    selection: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item(key = "filters:all") {
            CategoryBadge(Category("", "Todos"), selection == null, { onSelect(null) }, Icons.Outlined.GridView)
        }
        item(key = FAVORITES_FILTER_KEY) {
            CategoryBadge(
                Category("", "Favoritos"),
                selection == FAVORITES_FILTER_KEY,
                { onSelect(FAVORITES_FILTER_KEY) },
                Icons.Outlined.FavoriteBorder
            )
        }
        items(categories, key = { catalogCategoryFilterKey(it.nombre) }) { category ->
            val key = catalogCategoryFilterKey(category.nombre)
            CategoryBadge(category, selection == key, { onSelect(key) })
        }
    }
}

@Composable
private fun OnlineCatalogResults(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Text(
            if (count == 1) "1 repuesto" else "$count repuestos",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun OnlineFavoriteButton(
    name: String,
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggle()
        },
        modifier = modifier
    ) {
        Box(Modifier.size(34.dp).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isFavorite) "Quitar $name de favoritos" else "Agregar $name a favoritos",
                tint = if (isFavorite) colors.primary else colors.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun OnlineProductCard(
    vm: OnlineViewModel,
    product: OnlineProduct,
    workshop: Boolean,
    quantity: Int,
    busy: Boolean,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onAdjustStock: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                OnlineProductPhoto(vm, product.imagePath, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                OnlineFavoriteButton(product.name, isFavorite, onToggleFavorite, Modifier.align(Alignment.TopEnd))
                if (product.currentPriceCop < product.priceCop) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
                        shape = MaterialTheme.shapes.small,
                        color = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    ) {
                        Text("Oferta", Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                product.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(product.category, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            OnlineProductRating(product)
            OnlineProductPrice(product)
            if (product.description.isNotBlank()) {
                Text(
                    product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                stockLabel(product.stock),
                style = MaterialTheme.typography.bodySmall,
                color = if (product.stock <= 0) colors.error else colors.onSurfaceVariant
            )
            if (workshop) {
                OnlineCatalogQuantityControls(
                    name = product.name,
                    quantity = product.stock,
                    canAdd = !busy && product.stock < Int.MAX_VALUE,
                    canRemove = !busy && product.stock > 0,
                    onAdd = { onAdjustStock(true) },
                    onRemove = { onAdjustStock(false) },
                    isInventory = true
                )
                OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar")
                }
            } else {
                OnlineCatalogCartControls(product, quantity, enabled = !busy, onAdd = onAdd, onRemove = onRemove)
            }
        }
    }
}

@Composable
private fun OnlineCatalogCartControls(
    product: OnlineProduct,
    quantity: Int,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    addLabel: String = "Agregar"
) {
    val haptics = LocalHapticFeedback.current
    val canAdd = enabled && quantity < product.stock
    if (quantity == 0) {
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onAdd()
            },
            enabled = canAdd,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Text(addLabel)
        }
        return
    }

    OnlineCatalogQuantityControls(
        name = product.name,
        quantity = quantity,
        canAdd = canAdd,
        canRemove = enabled,
        onAdd = onAdd,
        onRemove = onRemove
    )
}

@Composable
private fun OnlineCatalogQuantityControls(
    name: String,
    quantity: Int,
    canAdd: Boolean,
    canRemove: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    isInventory: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    val label = if (isInventory) "stock" else "cantidad"
    val description = if (isInventory) "Existencias: $quantity" else "Cantidad en carrito: $quantity"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRemove()
            },
            enabled = canRemove,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "Disminuir $label de $name")
        }
        Text(
            text = quantity.toString(),
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FilledIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAdd()
            },
            enabled = canAdd,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Aumentar $label de $name")
        }
    }
}

private fun stockLabel(stock: Int): String = when {
    stock <= 0 -> "Agotado"
    stock == 1 -> "Última unidad"
    else -> "$stock disponibles"
}

@Composable
private fun OnlineProductRating(product: OnlineProduct, detailed: Boolean = false) {
    val average = product.averageRating?.takeIf { it in 1.0..5.0 } ?: return
    val count = product.ratingCount.takeIf { it > 0L } ?: return
    val score = remember(average) {
        NumberFormat.getNumberInstance(localeColombia).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
            roundingMode = RoundingMode.HALF_UP
        }.format(average)
    }
    val quantity = remember(count) { NumberFormat.getIntegerInstance(localeColombia).format(count) }
    val votes = if (count == 1L) "$quantity calificación" else "$quantity calificaciones"
    val text = if (detailed) "$score de 5 · $votes" else "$score ($quantity)"
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$score de 5 estrellas, $votes" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OnlineProductPrice(product: OnlineProduct) {
    Column {
        Text(
            onlinePrice(product.currentPriceCop),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold
        )
        if (product.currentPriceCop < product.priceCop) {
            Text(
                onlinePrice(product.priceCop),
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun OnlineProductEditor(
    vm: OnlineViewModel,
    state: OnlineState,
    product: OnlineProduct?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var description by rememberSaveable(product?.id) { mutableStateOf(product?.description.orEmpty()) }
    var category by rememberSaveable(product?.id) { mutableStateOf(product?.category.orEmpty()) }
    var price by rememberSaveable(product?.id) { mutableStateOf(product?.priceCop?.toString().orEmpty()) }
    var promotion by rememberSaveable(product?.id) { mutableStateOf(product?.promotionPriceCop?.toString().orEmpty()) }
    var stock by rememberSaveable(product?.id) { mutableStateOf(product?.stock?.toString() ?: "0") }
    var imagePath by rememberSaveable(product?.id) { mutableStateOf(product?.imagePath) }
    var stagedImage by rememberSaveable(product?.id) { mutableStateOf<String?>(null) }
    var stagedResource by rememberSaveable(product?.id) { mutableStateOf(0) }
    var showLibrary by rememberSaveable(product?.id) { mutableStateOf(false) }
    var submitted by rememberSaveable(product?.id) { mutableStateOf(false) }
    var formError by rememberSaveable(product?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.message) {
        val error = state.message?.takeIf { submitted && it.isError }
        if (error != null) {
            formError = error.text
            submitted = false
        }
    }
    val devicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            stagedImage = uri.toString()
            stagedResource = 0
            formError = null
        }
    }

    fun submit() {
        val priceCop = price.toLongOrNull()
        val promoCop = promotion.toLongOrNull()
        val stockCount = stock.toIntOrNull()
        formError = when {
            name.trim().length < 3 -> "Indica un nombre de al menos tres caracteres."
            category.trim().length < 2 -> "Indica una categoría de al menos dos caracteres."
            description.isBlank() -> "Describe el repuesto."
            priceCop == null || priceCop <= 0 -> "Indica un precio en pesos mayor que cero."
            promotion.isNotBlank() && (promoCop == null || promoCop !in 1 until priceCop) ->
                "El precio de oferta debe ser menor que el precio normal."
            stockCount == null || stockCount < 0 -> "Indica una cantidad de existencias válida."
            else -> null
        }
        if (formError != null || priceCop == null || stockCount == null) return
        val draft = OnlineProductDraft(
            name = name.trim(),
            description = description.trim(),
            category = category.trim(),
            priceCop = priceCop,
            promotionPriceCop = promoCop,
            stock = stockCount,
            imagePath = imagePath
        )
        submitted = true
        val uri = stagedImage?.let(Uri::parse)
        if (uri == null) {
            vm.saveProduct(draft, product, onSaved = onDismiss)
        } else {
            vm.uploadImage(uri) { path ->
                imagePath = path
                stagedImage = null
                stagedResource = 0
                vm.saveProduct(draft.copy(imagePath = path), product, onSaved = onDismiss)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text(if (product == null) "Registrar repuesto" else "Editar repuesto") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OnlineProductField(name, { name = it.take(180) }, "Nombre", !state.busy)
                OnlineProductField(category, { category = it.take(100) }, "Categoría", !state.busy)
                OnlineProductField(description, { description = it.take(4000) }, "Descripción", !state.busy, multiline = true)
                OnlineProductField(price, { price = it.filter(Char::isDigit).take(19) }, "Precio (COP)", !state.busy, number = true)
                OnlineProductField(promotion, { promotion = it.filter(Char::isDigit).take(19) }, "Precio de oferta (opcional)", !state.busy, number = true)
                OnlineProductField(stock, { stock = it.filter(Char::isDigit).take(10) }, "Existencias", !state.busy, number = true)

                if (stagedImage != null) {
                    ProductImage(
                        imageUri = stagedImage,
                        imageRes = stagedResource,
                        contentDescription = "Foto seleccionada",
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                    )
                } else if (imagePath != null) {
                    OnlineProductPhoto(vm, imagePath, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                }
                OutlinedButton(
                    onClick = { devicePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Elegir foto de mi dispositivo")
                }
                OutlinedButton(
                    onClick = { showLibrary = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Elegir imágenes previamente cargadas")
                }
                if (stagedImage != null || imagePath != null) {
                    TextButton(
                        enabled = !state.busy,
                        onClick = { stagedImage = null; stagedResource = 0; imagePath = null }
                    ) { Text("Quitar foto") }
                }
                formError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = !state.busy) {
                Text(
                    when {
                        state.busy -> "Guardando…"
                        product == null -> "Registrar Repuesto"
                        else -> "Guardar cambios"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Volver") } }
    )

    if (showLibrary) {
        val catalogImages = remember {
            productList.filter { it.imageRes > 0 }.distinctBy { it.imageRes }
                .map { ProductImageChoice(it.name, it.imageRes) }
        }
        ProductImagePicker(
            previousPhotos = emptyList(),
            catalogImages = catalogImages,
            selectedImageRes = stagedResource,
            selectedImageUri = null,
            onDismiss = { showLibrary = false },
            onSelect = { choice ->
                stagedResource = choice.imageRes
                stagedImage = Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(context.packageName)
                    .appendPath(choice.imageRes.toString())
                    .build().toString()
                formError = null
                showLibrary = false
            }
        )
    }
}

@Composable
private fun OnlineProductField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    number: Boolean = false,
    multiline: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        maxLines = if (multiline) 4 else 1
    )
}

@Composable
internal fun OnlineProductPhoto(vm: OnlineViewModel, imagePath: String?, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = vm, key2 = imagePath) {
        value = null
        if (imagePath != null) {
            val bytes = vm.loadProductImage(imagePath)
            if (bytes != null && bytes.size in 1..5_242_880) {
                value = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                        bounds.outWidth.toLong() * bounds.outHeight > 20_000_000L
                    ) return@withContext null
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 720) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        }
    }
    Box(
        modifier.clip(MaterialTheme.shapes.medium).background(EdMotorPhotoMatte),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                Icons.Outlined.HideImage,
                contentDescription = "Sin imagen",
                tint = com.example.edmotor.ui.theme.GrisCarbon,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
internal fun OnlineSectionTitle(title: String, icon: ImageVector) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Row(
            Modifier.padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = colors.surface, contentColor = colors.primary, shape = MaterialTheme.shapes.medium) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp))
            }
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun OnlineListNote(text: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Text(text, Modifier.padding(16.dp), color = colors.onSurfaceVariant)
    }
}
