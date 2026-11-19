package com.example.edmotor.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.data.repository.ProductOperationResult
import com.example.edmotor.model.Category
import com.example.edmotor.ui.components.CategorySelector
import com.example.edmotor.ui.components.ProductImage
import com.example.edmotor.ui.components.ProductImageChoice
import com.example.edmotor.ui.components.ProductImagePicker
import com.example.edmotor.ui.theme.EdMotorHeroNavy
import com.example.edmotor.ui.theme.EdMotorPhotoMatte

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormScreen(
    product: ProductEntity? = null,
    availableImages: List<Pair<String, Int>>,
    previousProducts: List<ProductEntity> = emptyList(),
    categories: List<Category>,
    isSaving: Boolean = false,
    onCreateCategory: (String, (Boolean, String?) -> Unit) -> Unit,
    onSave: (
        name: String,
        description: String,
        category: String,
        priceCop: Long,
        imageRes: Int,
        contentDescription: String,
        stock: Int,
        isNew: Boolean,
        isOnPromotion: Boolean,
        promotionPriceCop: Long?,
        imageUri: String?,
        onResult: (ProductOperationResult) -> Unit
    ) -> Unit,
    onUpdate: (ProductEntity, (ProductOperationResult) -> Unit) -> Unit,
    onCreated: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isEditing = product != null

    var name by rememberSaveable(product?.id) { mutableStateOf(product?.name ?: "") }
    var description by rememberSaveable(product?.id) { mutableStateOf(product?.description ?: "") }
    var category by rememberSaveable(product?.id) { mutableStateOf(product?.category ?: "") }
    var price by rememberSaveable(product?.id) { mutableStateOf(product?.priceCop?.toString() ?: "") }
    var promotionPrice by rememberSaveable(product?.id) { mutableStateOf(product?.promotionPriceCop?.toString() ?: "") }
    var stock by rememberSaveable(product?.id) { mutableStateOf(product?.stock?.toString() ?: "10") }
    var imageRes by rememberSaveable(product?.id) {
        mutableStateOf(product?.imageRes ?: 0)
    }
    var imageUri by rememberSaveable(product?.id) { mutableStateOf(product?.imageUri) }
    var isNew by rememberSaveable(product?.id) { mutableStateOf(product?.isNew ?: false) }
    var isOnPromotion by rememberSaveable(product?.id) { mutableStateOf(product?.isOnPromotion ?: false) }
    var error by rememberSaveable(product?.id) { mutableStateOf<String?>(null) }
    var photoError by rememberSaveable(product?.id) { mutableStateOf<String?>(null) }
    var showImageLibrary by rememberSaveable(product?.id) { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                photoError = "No se pudo conservar el acceso a esa foto. Elige otro archivo."
                return@rememberLauncherForActivityResult
            }
            imageUri = uri.toString()
            photoError = null
            error = null
        }
    }

    val priceValue = price.toLongOrNull()
    val stockValue = stock.toIntOrNull()
    val promotionValue = promotionPrice.toLongOrNull()

    val colors = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(14.dp)

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar repuesto" else "Nuevo repuesto") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EdMotorHeroNavy,
                    titleContentColor = ProductHeaderContent,
                    navigationIconContentColor = ProductHeaderContent
                ),
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !isSaving) {
                        Text(
                            "Volver",
                            color = ProductHeaderContent.copy(alpha = if (isSaving) 0.38f else 1f)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.widthIn(max = 700.dp).fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ProductFormSection("Datos del repuesto") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            error = null
                        },
                        label = { Text("Nombre del repuesto") },
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it
                            error = null
                        },
                        label = { Text("Descripción") },
                        minLines = 3,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Categoría del repuesto", fontWeight = FontWeight.SemiBold)
                    CategorySelector(
                        selectedCategory = category,
                        categories = categories,
                        onCategorySelected = {
                            category = it
                            error = null
                        },
                        onCreateCategory = onCreateCategory
                    )
                }

                ProductFormSection("Precio e inventario") {
                    OutlinedTextField(
                        value = price,
                        onValueChange = {
                            price = it.filter(Char::isDigit)
                            error = null
                        },
                        label = { Text("Precio normal (COP)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Producto en promoción",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold
                                )
                                Switch(
                                    checked = isOnPromotion,
                                    onCheckedChange = {
                                        isOnPromotion = it
                                        error = null
                                    }
                                )
                            }
                            if (isOnPromotion) {
                                Surface(
                                    color = colors.surface,
                                    shape = fieldShape
                                ) {
                                    OutlinedTextField(
                                        value = promotionPrice,
                                        onValueChange = {
                                            promotionPrice = it.filter(Char::isDigit)
                                            error = null
                                        },
                                        label = { Text("Precio promocional (COP)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = fieldShape,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    if (product == null) {
                        OutlinedTextField(
                            value = stock,
                            onValueChange = {
                                stock = it.filter(Char::isDigit)
                                error = null
                            },
                            label = { Text("Cantidad inicial") },
                            supportingText = { Text("Ingresa 0 si el producto está agotado.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = fieldShape,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Producto nuevo", fontWeight = FontWeight.Bold)
                            Text(
                                "Mostrar la etiqueta NUEVO en el catálogo.",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Checkbox(checked = isNew, onCheckedChange = { isNew = it })
                    }
                }

                ProductFormSection("Fotografía del repuesto") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = EdMotorPhotoMatte,
                        border = BorderStroke(1.dp, colors.outline)
                    ) {
                        ProductImage(
                            imageUri = imageUri,
                            imageRes = imageRes,
                            contentDescription = name.ifBlank { "Vista previa del repuesto" },
                            modifier = Modifier.fillMaxWidth().height(200.dp).padding(12.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    OutlinedButton(
                        onClick = { photoPicker.launch(arrayOf("image/*")) },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = fieldShape
                    ) {
                        Icon(
                            Icons.Outlined.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp).size(20.dp)
                        )
                        Text("Elegir foto de mi dispositivo")
                    }
                    OutlinedButton(
                        onClick = { showImageLibrary = true },
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = fieldShape
                    ) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp).size(20.dp)
                        )
                        Text("Elegir imágenes previamente cargadas")
                    }
                    photoError?.let { Text(it, color = colors.error) }
                }

                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(5.dp))
                Button(
                    onClick = {
                        error = when {
                            name.trim().length < 3 -> "El nombre debe tener al menos 3 caracteres."
                            description.isBlank() -> "Escribe una descripción."
                            category.isBlank() -> "Selecciona una categoría o crea una nueva."
                            priceValue == null || priceValue <= 0 -> "El precio debe ser mayor que cero."
                            product == null && stockValue == null -> "La cantidad ingresada no es válida."
                            imageRes <= 0 && imageUri.isNullOrBlank() ->
                                "Selecciona una fotografía del dispositivo o una imagen del catálogo."
                            isOnPromotion && (
                                    promotionValue == null ||
                                            promotionValue <= 0 ||
                                            promotionValue >= (priceValue ?: 0)
                                    ) -> "El precio promocional debe ser menor al precio normal."
                            else -> null
                        }

                        if (error == null) {
                            if (product == null) {
                                onSave(
                                    name.trim(),
                                    description.trim(),
                                    category.trim(),
                                    priceValue!!,
                                    imageRes,
                                    name.trim(),
                                    stockValue!!,
                                    isNew,
                                    isOnPromotion,
                                    if (isOnPromotion) promotionValue else null,
                                    imageUri
                                ) { result ->
                                    when (result) {
                                        is ProductOperationResult.Success -> {
                                            Toast.makeText(
                                                context,
                                                "Repuesto creado con éxito.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            result.productId?.let(onCreated)
                                                ?: run {
                                                    error = "El repuesto se creó, pero no se recibió su identificador."
                                                }
                                        }
                                        is ProductOperationResult.Error -> error = result.message
                                    }
                                }
                            } else {
                                onUpdate(
                                    product.copy(
                                        name = name.trim(),
                                        description = description.trim(),
                                        category = category.trim(),
                                        priceCop = priceValue!!,
                                        promotionPriceCop = if (isOnPromotion) promotionValue else null,
                                        imageRes = imageRes,
                                        imageUri = imageUri,
                                        contentDescription = name.trim(),
                                        isNew = isNew,
                                        isOnPromotion = isOnPromotion
                                    )
                                ) { result ->
                                    when (result) {
                                        is ProductOperationResult.Success -> {
                                            Toast.makeText(
                                                context,
                                                "Repuesto actualizado correctamente.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            onBack()
                                        }
                                        is ProductOperationResult.Error -> error = result.message
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 10.dp).size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        if (isEditing) "Guardar cambios" else "Registrar Repuesto",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showImageLibrary) {
        val previousPhotos = remember(previousProducts, context) {
            val readableUris = context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .mapTo(mutableSetOf()) { it.uri.toString() }
            previousProducts.mapNotNull { product ->
                product.imageUri?.takeIf { it in readableUris }?.let { uri ->
                    ProductImageChoice(product.name, product.imageRes, uri)
                }
            }.distinctBy { it.imageUri }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
        val catalogImages = remember(availableImages) {
            availableImages
                .map { (name, resourceId) -> ProductImageChoice(name, resourceId) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }

        ProductImagePicker(
            previousPhotos = previousPhotos,
            catalogImages = catalogImages,
            selectedImageRes = imageRes,
            selectedImageUri = imageUri,
            onDismiss = { showImageLibrary = false },
            onSelect = { choice ->
                imageRes = choice.imageRes
                imageUri = choice.imageUri
                photoError = null
                error = null
                showImageLibrary = false
            }
        )
    }
}

@Composable
private fun ProductFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
