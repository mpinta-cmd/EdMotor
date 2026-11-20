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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.productList
import com.example.edmotor.model.UserRole
import com.example.edmotor.ui.components.ProductImage
import com.example.edmotor.ui.components.ProductImageChoice
import com.example.edmotor.ui.components.ProductImagePicker
import com.example.edmotor.ui.theme.EdMotorPhotoMatte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    cart: Map<Long, Int>,
    onAddToCart: (Long) -> Unit,
    onOpenCart: () -> Unit
) {
    val workshop = state.user?.role == UserRole.WORKSHOP.name
    var query by rememberSaveable { mutableStateOf("") }
    var showingEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<OnlineProduct?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deactivating by remember { mutableStateOf<OnlineProduct?>(null) }
    val products = remember(query, state.products) {
        val term = searchTerm(query.trim())
        val alphabet = Collator.getInstance(localeColombia).apply { strength = Collator.PRIMARY }
        state.products.filter { product ->
            term.isEmpty() || searchTerm("${product.name} ${product.category} ${product.description}").contains(term)
        }.sortedWith(
            compareByDescending<OnlineProduct> { it.currentPriceCop < it.priceCop }
                .thenComparator { first, second -> alphabet.compare(first.name, second.name) }
                .thenBy { it.id }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (LocalDensity.current.fontScale > 1.4f) 230.dp else 155.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OnlineSectionTitle(
                    title = if (workshop) "Inventario de repuestos" else "Catálogo de repuestos",
                    icon = Icons.Outlined.Inventory2
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar repuestos") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true
                )
                if (workshop) {
                    Button(
                        onClick = { editing = null; showingEditor = true },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar repuesto")
                    }
                } else if (cart.isNotEmpty()) {
                    OutlinedButton(onClick = onOpenCart, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.ShoppingCart, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ver carrito")
                    }
                }
            }
        }
        if (!state.loading && products.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OnlineListNote(
                    if (query.isNotBlank()) "No encontramos repuestos con esa búsqueda."
                    else "Todavía no hay repuestos en el catálogo."
                )
            }
        }
        items(products, key = { it.id }) { product ->
            val quantity = cart[product.id] ?: 0
            OnlineProductCard(
                vm = vm,
                product = product,
                workshop = workshop,
                quantity = quantity,
                busy = state.busy,
                onOpen = { selectedId = product.id },
                onAdd = { onAddToCart(product.id) },
                onEdit = { editing = product; showingEditor = true }
            )
        }
    }

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
                    OnlineProductPhoto(vm, selected.imagePath, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                    OnlineProductPrice(selected)
                    Text(selected.category, style = MaterialTheme.typography.labelLarge)
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
                    Button(
                        enabled = !state.busy && (cart[selected.id] ?: 0) < selected.stock,
                        onClick = { onAddToCart(selected.id); selectedId = null }
                    ) { Text("Agregar al carrito") }
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
private fun OnlineProductCard(
    vm: OnlineViewModel,
    product: OnlineProduct,
    workshop: Boolean,
    quantity: Int,
    busy: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit
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
            OnlineProductPrice(product)
            Text(
                stockLabel(product.stock),
                style = MaterialTheme.typography.bodySmall,
                color = if (product.stock <= 0) colors.error else colors.onSurfaceVariant
            )
            if (workshop) {
                OutlinedButton(onClick = onEdit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar")
                }
            } else {
                if (quantity > 0) Text("En carrito: $quantity", style = MaterialTheme.typography.labelSmall)
                Button(
                    onClick = onAdd,
                    enabled = !busy && quantity < product.stock,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.AddShoppingCart, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Agregar")
                }
            }
        }
    }
}

private fun stockLabel(stock: Int): String = when {
    stock <= 0 -> "Agotado"
    stock == 1 -> "Última unidad"
    else -> "$stock disponibles"
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
