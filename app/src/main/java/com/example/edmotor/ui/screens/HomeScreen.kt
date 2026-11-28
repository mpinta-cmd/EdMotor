package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edmotor.R
import com.example.edmotor.data.FAVORITES_FILTER_KEY
import com.example.edmotor.data.catalogCategoryFilterKey
import com.example.edmotor.model.Category
import com.example.edmotor.model.Product
import com.example.edmotor.ui.components.CategoryBadge
import com.example.edmotor.ui.components.ProductGridCard
import com.example.edmotor.ui.components.StockAdjustmentDialog
import com.example.edmotor.ui.theme.EdMotorHeroNavy

@Composable
fun HomeScreen(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    categories: List<Category>,
    product: List<Product>,
    cartQuantity: Map<Int, Int>,
    selectedCategory: String?,
    favoriteProductIds: Set<Int>,
    onFavoriteClick: (Int) -> Unit,
    onProductClick: (Int) -> Unit,
    onCategorySelect: (String) -> Unit,
    onIncrease: (Int) -> Unit,
    onDecrease: (Int) -> Unit,
    onAdjustStock: (Int, Int) -> Unit = { _, _ -> },
    isWorkshop: Boolean = false,
    onOpenInventory: () -> Unit = {},
    onOpenAppointments: () -> Unit = {}
) {
    var stockAdjustmentProduct by remember { mutableStateOf<Product?>(null) }
    var stockAdjustmentIncrease by remember { mutableStateOf(true) }

    val colors = MaterialTheme.colorScheme
    val gridState = rememberLazyGridState()
    val query = searchQuery.trim()
    val categoriesByKey = remember(categories) { categories.distinctBy { catalogCategoryFilterKey(it.nombre) } }
    val selectedCategoryName = categoriesByKey.firstOrNull {
        catalogCategoryFilterKey(it.nombre) == selectedCategory
    }?.nombre
    val hasFilters = query.isNotEmpty() || selectedCategory != null

    LaunchedEffect(query, selectedCategory) { gridState.scrollToItem(0) }

    val filteredProducts = remember(product, query, selectedCategory, favoriteProductIds) {
        product.filter { item ->
            val matchesText = query.isEmpty() ||
                item.name.contains(query, ignoreCase = true) ||
                item.description.contains(query, ignoreCase = true) ||
                item.category.contains(query, ignoreCase = true) ||
                item.badge.contains(query, ignoreCase = true)
            val matchesCategory = when (selectedCategory) {
                null -> true
                FAVORITES_FILTER_KEY -> item.id in favoriteProductIds
                else -> catalogCategoryFilterKey(item.category) == selectedCategory
            }
            matchesText && matchesCategory
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HomeSearchBar(searchQuery, onSearchChange, Modifier.padding(horizontal = 14.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                item(key = "filters:all") {
                    CategoryBadge(
                        category = Category(emoji = "", nombre = "Todos"),
                        isSelected = selectedCategory == null,
                        onClick = { selectedCategory?.let(onCategorySelect) },
                        icon = Icons.Outlined.GridView
                    )
                }
                item(key = FAVORITES_FILTER_KEY) {
                    CategoryBadge(
                        category = Category(emoji = "", nombre = "Favoritos"),
                        isSelected = selectedCategory == FAVORITES_FILTER_KEY,
                        onClick = { onCategorySelect(FAVORITES_FILTER_KEY) },
                        icon = Icons.Outlined.FavoriteBorder
                    )
                }
                items(categoriesByKey, key = { catalogCategoryFilterKey(it.nombre) }) { category ->
                    val key = catalogCategoryFilterKey(category.nombre)
                    CategoryBadge(
                        category = category,
                        isSelected = selectedCategory == key,
                        onClick = { onCategorySelect(key) }
                    )
                }
            }
        }

        val cardMinWidth = if (LocalDensity.current.fontScale > 1.35f) 210.dp else 140.dp
        LazyVerticalGrid(
            columns = GridCells.Adaptive(cardMinWidth),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hasFilters) {
                item(key = "catalog_cover", span = { GridItemSpan(maxLineSpan) }) {
                    CatalogCover(
                        isWorkshop = isWorkshop,
                        onOpen = if (isWorkshop) onOpenInventory else onOpenAppointments
                    )
                }
            }

            item(key = "product_results", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            selectedCategory == FAVORITES_FILTER_KEY -> "Favoritos"
                            selectedCategoryName != null -> selectedCategoryName
                            query.isNotEmpty() -> "Resultados"
                            else -> "Catálogo"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (filteredProducts.size == 1) "1 repuesto" else "${filteredProducts.size} repuestos",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }

            if (filteredProducts.isEmpty()) {
                item(key = "no_products", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyProductsMessage(selectedCategory, hasFilters) {
                        onSearchChange("")
                        selectedCategory?.let(onCategorySelect)
                    }
                }
            }

            gridItems(filteredProducts, key = { it.id }) { item ->
                ProductGridCard(
                    product = item,
                    quantity = cartQuantity[item.id] ?: 0,
                    isFavorite = item.id in favoriteProductIds,
                    onFavoriteClick = { onFavoriteClick(item.id) },
                    onProductClick = { onProductClick(item.id) },
                    onIncrease = {
                        if (isWorkshop) {
                            stockAdjustmentProduct = item
                            stockAdjustmentIncrease = true
                        } else {
                            onIncrease(item.id)
                        }
                    },
                    onDecrease = {
                        if (isWorkshop) {
                            stockAdjustmentProduct = item
                            stockAdjustmentIncrease = false
                        } else {
                            onDecrease(item.id)
                        }
                    },
                    isWorkshop = isWorkshop
                )
            }
        }
    }

    stockAdjustmentProduct?.let { part ->
        StockAdjustmentDialog(
            productName = part.name,
            currentStock = part.stock,
            increase = stockAdjustmentIncrease,
            onDismiss = { stockAdjustmentProduct = null },
            onConfirm = { amount ->
                onAdjustStock(part.id, if (stockAdjustmentIncrease) amount else -amount)
                stockAdjustmentProduct = null
            }
        )
    }
}

@Composable
internal fun CatalogCover(isWorkshop: Boolean, onOpen: () -> Unit) {
    val coverColor = EdMotorHeroNavy
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(coverColor)
    ) {
        Image(
            painter = painterResource(R.drawable.workshop_marketplace_hero),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            alignment = Alignment.CenterEnd,
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to coverColor,
                    0.48f to coverColor.copy(alpha = 0.85f),
                    1f to coverColor.copy(alpha = 0.06f)
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Repuestos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = Color.White
            )
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = if (isWorkshop) Icons.Outlined.Inventory2 else Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = if (isWorkshop) "Inventario" else "Agendar cita",
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun HomeSearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = { Text("Buscar repuestos o marcas", maxLines = 1) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Buscar", tint = colors.primary) },
        trailingIcon = if (searchQuery.isNotEmpty()) {
            {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = colors.outline,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface
        )
    )
}

@Composable
private fun EmptyProductsMessage(selectedCategory: String?, hasFilters: Boolean, onClear: () -> Unit) {
    val message = when {
        selectedCategory == FAVORITES_FILTER_KEY -> "Aún no tienes favoritos"
        hasFilters -> "No encontramos repuestos con esos filtros"
        else -> "Todavía no hay repuestos en el catálogo"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            if (hasFilters) {
                TextButton(onClick = onClear) { Text("Ver todos los repuestos") }
            }
        }
    }
}
