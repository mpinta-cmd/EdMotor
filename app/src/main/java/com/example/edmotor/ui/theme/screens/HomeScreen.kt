
package com.example.edmotor.ui.screens

import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.items as gridItems
import kotlinx.coroutines.flow.collect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edmotor.data.FAVORITES_CATEGORY
import com.example.edmotor.model.Category
import com.example.edmotor.model.Product
import com.example.edmotor.ui.theme.AzulNitro
import com.example.edmotor.ui.theme.components.CategoryBadge
import com.example.edmotor.ui.theme.components.ProductGridCard

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
    onCategorySelect: (String) -> Unit,
    onIncrease: (Int) -> Unit,
    onDecrease: (Int) -> Unit
) {

    // ==========================================
    // 1. ESTADO DE LA CUADRÍCULA
    // ==========================================

    val gridState = rememberLazyGridState()

    var showSearchAndCategories by remember {
        mutableStateOf(true)
    }

    // ==========================================
    // 2. DETECTAR DIRECCIÓN DEL DESPLAZAMIENTO
    // ==========================================

    LaunchedEffect(gridState) {

        var previousIndex = gridState.firstVisibleItemIndex

        var previousOffset =
            gridState.firstVisibleItemScrollOffset

        snapshotFlow {

            if (gridState.isScrollInProgress) {
                gridState.firstVisibleItemIndex to
                        gridState.firstVisibleItemScrollOffset
            } else {
                null
            }

        }.collect { currentPosition ->

            if (currentPosition != null) {

                val currentIndex = currentPosition.first
                val currentOffset = currentPosition.second

                val scrollingDown =
                    currentIndex > previousIndex ||
                            (
                                    currentIndex == previousIndex &&
                                            currentOffset > previousOffset
                                    )

                val scrollingUp =
                    currentIndex < previousIndex ||
                            (
                                    currentIndex == previousIndex &&
                                            currentOffset < previousOffset
                                    )

                when {
                    scrollingDown -> {
                        showSearchAndCategories = false
                    }

                    scrollingUp -> {
                        showSearchAndCategories = true
                    }
                }

                previousIndex = currentIndex
                previousOffset = currentOffset
            }
        }
    }

    // ==========================================
    // 3. REINICIAR AL CAMBIAR FILTROS
    // ==========================================

    LaunchedEffect(searchQuery, selectedCategory) {

        showSearchAndCategories = true

        gridState.animateScrollToItem(0)
    }

    // ==========================================
    // 4. CATEGORÍAS VISIBLES
    // ==========================================

    val categoriesToDisplay = remember(
        categories,
        favoriteProductIds
    ) {

        buildList {

            // Favoritos solo aparece si existen productos favoritos.
            if (favoriteProductIds.isNotEmpty()) {

                add(
                    Category(
                        emoji = "♥",
                        nombre = FAVORITES_CATEGORY
                    )
                )
            }

            addAll(categories)
        }
    }

    // ==========================================
    // 5. FILTRAR PRODUCTOS
    // ==========================================

    val query = searchQuery.trim()

    val filteredProducts = remember(
        product,
        query,
        selectedCategory,
        favoriteProductIds
    ) {

        product.filter { item ->

            val matchesText =
                query.isBlank() ||
                        item.name.contains(
                            query,
                            ignoreCase = true
                        ) ||
                        item.description.contains(
                            query,
                            ignoreCase = true
                        ) ||
                        item.category.contains(
                            query,
                            ignoreCase = true
                        ) ||
                        item.badge.contains(
                            query,
                            ignoreCase = true
                        )

            val matchesCategory = when (selectedCategory) {

                null -> true

                FAVORITES_CATEGORY -> {
                    item.id in favoriteProductIds
                }

                else -> {
                    item.category.equals(
                        selectedCategory,
                        ignoreCase = true
                    )
                }
            }

            matchesText && matchesCategory
        }
    }

    // ==========================================
    // 6. CONTENEDOR PRINCIPAL
    // ==========================================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // ======================================
        // 7. ENCABEZADO ANIMADO
        // ======================================

        AnimatedVisibility(
            visible = showSearchAndCategories,

            enter = slideInVertically(
                initialOffsetY = { -it / 2 }
            ) + fadeIn(),

            exit = slideOutVertically(
                targetOffsetY = { -it / 2 }
            ) + fadeOut()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),

                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // TÍTULO PRINCIPAL

                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {

                    Text(
                        text = "REPUESTOS QUE BUSCAS",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "Encuentra lo que tu vehículo necesita.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // BUSCADOR

                HomeSearchBar(
                    searchQuery = searchQuery,
                    onSearchChange = onSearchChange
                )

                // TÍTULO DE CATEGORÍAS

                Text(
                    text = "CATEGORÍAS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AzulNitro
                )

                // CATEGORÍAS HORIZONTALES

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(
                        vertical = 3.dp
                    )
                ) {

                    // TODOS

                    item(key = "category_all") {

                        CategoryBadge(
                            category = Category(
                                emoji = "✦",
                                nombre = "Todos"
                            ),

                            isSelected = selectedCategory == null,

                            onClick = {
                                selectedCategory?.let {
                                    onCategorySelect(it)
                                }
                            }
                        )
                    }

                    // FAVORITOS Y CATEGORÍAS NORMALES

                    items(
                        items = categoriesToDisplay,
                        key = { category -> category.nombre }
                    ) { category ->

                        CategoryBadge(
                            category = category,

                            isSelected =
                                selectedCategory == category.nombre,

                            onClick = {
                                onCategorySelect(category.nombre)
                            }
                        )
                    }
                }
            }
        }

        // ======================================
        // 8. CUADRÍCULA DE PRODUCTOS
        // ======================================

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),

            state = gridState,

            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),

            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 24.dp
            ),

            horizontalArrangement = Arrangement.spacedBy(12.dp),

            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ==================================
            // ENCABEZADO DE RESULTADOS
            // ==================================

            item(
                key = "products_header",
                span = {
                    GridItemSpan(maxLineSpan)
                }
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 3.dp,
                            bottom = 2.dp
                        ),

                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(
                        modifier = Modifier.weight(1f),

                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {

                        Text(
                            text = when (selectedCategory) {

                                FAVORITES_CATEGORY ->
                                    "Tus productos favoritos"

                                null ->
                                    if (query.isBlank()) {
                                        "Nuestro catálogo"
                                    } else {
                                        "Resultados de búsqueda"
                                    }

                                else ->
                                    selectedCategory
                            },

                            style = MaterialTheme.typography.titleMedium,

                            fontWeight = FontWeight.ExtraBold,

                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "${filteredProducts.size} repuestos encontrados",

                            style = MaterialTheme.typography.bodySmall,

                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {

                        Text(
                            text = filteredProducts.size.toString(),

                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 7.dp
                            ),

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // ==================================
            // MENSAJE CUANDO NO HAY PRODUCTOS
            // ==================================

            if (filteredProducts.isEmpty()) {

                item(
                    key = "empty_products",
                    span = {
                        GridItemSpan(maxLineSpan)
                    }
                ) {

                    EmptyProductsMessage(
                        selectedCategory = selectedCategory,
                        onClear = {

                            onSearchChange("")

                            selectedCategory?.let {
                                onCategorySelect(it)
                            }
                        }
                    )
                }

            } else {

                // ==================================
                // PRODUCTOS EN DOS COLUMNAS
                // ==================================

                items(
                    items = filteredProducts,
                    key = { item -> item.id }
                ) { item ->

                    val quantity =
                        cartQuantity[item.id] ?: 0

                    val isFavorite =
                        item.id in favoriteProductIds

                    ProductGridCard(

                        product = item,

                        quantity = quantity,

                        isFavorite = isFavorite,

                        onFavoriteClick = {
                            onFavoriteClick(item.id)
                        },

                        onIncrease = {
                            onIncrease(item.id)
                        },

                        onDecrease = {
                            onDecrease(item.id)
                        },

                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ==================================
            // PIE DEL CATÁLOGO
            // ==================================

            item(
                key = "home_footer",
                span = {
                    GridItemSpan(maxLineSpan)
                }
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 15.dp,
                            bottom = 12.dp
                        ),

                    horizontalAlignment = Alignment.CenterHorizontally,

                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                    Text(
                        text = "EDMOTOR",

                        fontWeight = FontWeight.ExtraBold,

                        color = AzulNitro
                    )

                    Text(
                        text = "Tu aliado en el mundo automotriz.",

                        style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==============================================
// BUSCADOR DE REPUESTOS
// ==============================================

@Composable
private fun HomeSearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {

    OutlinedTextField(

        value = searchQuery,

        onValueChange = onSearchChange,

        modifier = Modifier.fillMaxWidth(),

        singleLine = true,

        shape = RoundedCornerShape(24.dp),

        placeholder = {
            Text("Buscar repuestos, marcas o referencias...")
        },

        leadingIcon = {

            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar",
                tint = AzulNitro
            )
        },

        trailingIcon = {

            if (searchQuery.isNotEmpty()) {

                IconButton(
                    onClick = {
                        onSearchChange("")
                    }
                ) {

                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpiar búsqueda"
                    )
                }
            }
        },

        colors = OutlinedTextFieldDefaults.colors(

            focusedBorderColor = AzulNitro,

            unfocusedBorderColor =
                MaterialTheme.colorScheme.outline.copy(
                    alpha = 0.5f
                ),

            focusedContainerColor =
                MaterialTheme.colorScheme.surface,

            unfocusedContainerColor =
                MaterialTheme.colorScheme.surface,

            cursorColor = AzulNitro
        )
    )
}

// ==============================================
// MENSAJE SIN RESULTADOS
// ==============================================

@Composable
private fun EmptyProductsMessage(
    selectedCategory: String?,
    onClear: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(22.dp),

        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(25.dp),

            horizontalAlignment = Alignment.CenterHorizontally,

            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Surface(
                shape = CircleShape,

                color = MaterialTheme.colorScheme.primaryContainer
            ) {

                Icon(
                    imageVector = Icons.Default.Search,

                    contentDescription = null,

                    tint = MaterialTheme.colorScheme.primary,

                    modifier = Modifier
                        .padding(17.dp)
                        .size(34.dp)
                )
            }

            Text(
                text = if (
                    selectedCategory == FAVORITES_CATEGORY
                ) {
                    "Todavía no tienes favoritos"
                } else {
                    "No encontramos ese repuesto"
                },

                style = MaterialTheme.typography.titleMedium,

                fontWeight = FontWeight.Bold,

                textAlign = TextAlign.Center
            )

            Text(
                text = "Prueba con otra búsqueda o explora las categorías disponibles.",

                style = MaterialTheme.typography.bodySmall,

                color = MaterialTheme.colorScheme.onSurfaceVariant,

                textAlign = TextAlign.Center
            )

            Button(
                onClick = onClear
            ) {

                Text("Ver todos los repuestos")
            }
        }
    }
}
