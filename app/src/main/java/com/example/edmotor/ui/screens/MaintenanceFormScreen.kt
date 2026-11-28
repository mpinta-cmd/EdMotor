package com.example.edmotor.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.edmotor.data.local.MaintenanceWithParts
import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.model.MaintenanceDraft
import com.example.edmotor.model.Category
import com.example.edmotor.model.MaintenancePartDraft
import com.example.edmotor.ui.components.ProductImage
import com.example.edmotor.ui.components.CategorySelector
import com.example.edmotor.ui.components.EdMotorBottomSheet
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PartFormState(
    val catalogProductId: Int? = null,
    val name: String = "",
    val brand: String = "",
    val reference: String = "",
    val specification: String = "",
    val quantity: String = "1",
    val unit: String = "unidad",
    val unitPrice: String = "0"
) : Serializable

private val partDraftSaver = Saver<SnapshotStateList<PartFormState>, ArrayList<PartFormState>>(
    save = { ArrayList(it) },
    restore = { it.toMutableStateList() }
)

@Composable
fun MaintenanceFormScreen(
    vehicleTitle: String,
    existingRecord: MaintenanceWithParts?,
    availableProducts: List<ProductEntity>,
    categories: List<Category>,
    isCreatingProduct: Boolean = false,
    isSaving: Boolean,
    saveError: String?,
    onCreateCategory: (String, (Boolean, String?) -> Unit) -> Unit,
    onCreateProduct: (
        String,
        String,
        String,
        Long,
        Int,
        String?,
        (Int?, String?) -> Unit
    ) -> Unit,
    onSave: (MaintenanceDraft) -> Unit,
    onBack: () -> Unit,
    appointmentServiceType: String? = null,
    appointmentFinishedAtMillis: Long? = null
) {
    val fromAppointment = appointmentFinishedAtMillis != null
    var serviceType by rememberSaveable(existingRecord?.record?.id, appointmentFinishedAtMillis) {
        val initialType = if (fromAppointment) appointmentServiceType.orEmpty() else "Cambio de aceite"
        mutableStateOf(existingRecord?.record?.serviceType ?: initialType)
    }
    var serviceDate by rememberSaveable(existingRecord?.record?.id, appointmentFinishedAtMillis) {
        val initialDate = appointmentFinishedAtMillis?.let { Date(it) } ?: Date()
        mutableStateOf(existingRecord?.record?.serviceDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(initialDate))
    }
    var mileage by rememberSaveable(existingRecord?.record?.id) {
        mutableStateOf(existingRecord?.record?.mileageKm?.toString() ?: "")
    }
    var description by rememberSaveable(existingRecord?.record?.id) {
        mutableStateOf(existingRecord?.record?.description ?: "")
    }
    var laborCost by rememberSaveable(existingRecord?.record?.id) {
        mutableStateOf(existingRecord?.record?.laborCostCop?.toString() ?: "0")
    }
    var nextMileage by rememberSaveable(existingRecord?.record?.id) {
        mutableStateOf(existingRecord?.record?.nextServiceMileageKm?.toString() ?: "")
    }
    var nextDate by rememberSaveable(existingRecord?.record?.id) {
        mutableStateOf(existingRecord?.record?.nextServiceDate ?: "")
    }
    var observations by rememberSaveable(existingRecord?.record?.id) {
        mutableStateOf(existingRecord?.record?.observations ?: "")
    }

    val parts = rememberSaveable(existingRecord?.record?.id, saver = partDraftSaver) {
        mutableStateListOf<PartFormState>().apply {
            val existingParts = existingRecord?.parts.orEmpty()
            if (existingParts.isEmpty()) add(PartFormState())
            else existingParts.forEach { part ->
                add(
                    PartFormState(
                        catalogProductId = part.catalogProductId,
                        name = part.name,
                        brand = part.brand,
                        reference = part.reference,
                        specification = part.specification,
                        quantity = part.quantity.toString(),
                        unit = part.unit,
                        unitPrice = part.unitPriceCop.toString()
                    )
                )
            }
        }
    }

    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var productPickerIndex by remember { mutableStateOf<Int?>(null) }
    var showNewProductDialog by remember { mutableStateOf(false) }
    var pendingCreatedProductId by rememberSaveable { mutableStateOf<Int?>(null) }
    var newProductError by remember { mutableStateOf<String?>(null) }

    var newProductName by remember { mutableStateOf("") }
    var newProductDescription by remember { mutableStateOf("") }
    var newProductCategory by remember { mutableStateOf(categories.firstOrNull()?.nombre ?: "Repuestos") }
    var newProductPrice by remember { mutableStateOf("") }
    var newProductStock by remember { mutableStateOf("1") }
    var newProductImageUri by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val quickPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                newProductError = "No se pudo conservar el acceso a esa foto. Elige otro archivo."
                return@rememberLauncherForActivityResult
            }
            newProductImageUri = uri.toString()
            newProductError = null
        }
    }

    // Cuando Room emite el nuevo producto creado desde este formulario,
    // se incorpora automáticamente como repuesto utilizado.
    LaunchedEffect(availableProducts, pendingCreatedProductId) {
        val createdId = pendingCreatedProductId ?: return@LaunchedEffect
        val product = availableProducts.firstOrNull { it.id == createdId }
        if (product != null) {
            parts.add(
                PartFormState(
                    catalogProductId = product.id,
                    name = product.name,
                    specification = product.description,
                    quantity = "1",
                    unit = "unidad",
                    unitPrice = product.effectivePriceCop.toString()
                )
            )
            pendingCreatedProductId = null
            showNewProductDialog = false
            newProductError = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack, enabled = !isSaving) {
            Text(if (fromAppointment) "← Volver a Citas" else "← Volver al historial")
        }
        Text(
            if (existingRecord == null) "Registrar mantenimiento" else "Editar mantenimiento",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(vehicleTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Datos del servicio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = serviceType,
                    onValueChange = { serviceType = it },
                    label = { Text("Tipo de mantenimiento") },
                    placeholder = { Text("Ej. Cambio de aceite y filtros") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = serviceDate,
                    onValueChange = { serviceDate = it },
                    label = { Text("Fecha (AAAA-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mileage,
                    onValueChange = { mileage = it.filter(Char::isDigit).take(9) },
                    label = { Text("Kilometraje del vehículo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción del trabajo realizado") },
                    supportingText = if (fromAppointment) {
                        { Text("El cliente verá este detalle en Citas.") }
                    } else null,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = laborCost,
                    onValueChange = { laborCost = it.filter(Char::isDigit).take(12) },
                    label = { Text("Costo de mano de obra (COP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nextMileage,
                    onValueChange = { nextMileage = it.filter(Char::isDigit).take(9) },
                    label = { Text("Próximo mantenimiento (km, opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nextDate,
                    onValueChange = { nextDate = it },
                    label = { Text("Próxima fecha sugerida (AAAA-MM-DD, opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = observations,
                    onValueChange = { observations = it },
                    label = { Text("Observaciones") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Repuestos utilizados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Selecciona los repuestos del inventario. Al guardar el mantenimiento, sus cantidades se descontarán automáticamente del stock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                parts.forEachIndexed { index, part ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Repuesto ${index + 1}", fontWeight = FontWeight.Bold)
                                if (parts.size > 1) {
                                    TextButton(onClick = { parts.removeAt(index) }) { Text("Quitar") }
                                }
                            }

                            OutlinedButton(
                                onClick = { productPickerIndex = index },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (part.catalogProductId == null) "Seleccionar del inventario" else "Cambiar repuesto del inventario")
                            }

                            if (part.catalogProductId != null) {
                                val selectedProduct = availableProducts.firstOrNull { it.id == part.catalogProductId }
                                val previouslyUsed = existingRecord?.parts
                                    ?.filter { it.catalogProductId == part.catalogProductId }
                                    ?.sumOf { it.quantity.toInt().toLong() } ?: 0L
                                val effectiveStock = (selectedProduct?.stock ?: 0).toLong() + previouslyUsed
                                Text(
                                    if (selectedProduct != null) "Stock disponible para este mantenimiento: $effectiveStock" else "Este producto ya no aparece en el catálogo.",
                                    color = if (effectiveStock <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text("También puedes registrar un material manual que no descuente inventario.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            OutlinedTextField(
                                value = part.name,
                                onValueChange = { parts[index] = part.copy(name = it) },
                                label = { Text("Nombre del repuesto/material") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = part.catalogProductId == null
                            )
                            OutlinedTextField(
                                value = part.brand,
                                onValueChange = { parts[index] = part.copy(brand = it) },
                                label = { Text("Marca") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = part.reference,
                                onValueChange = { parts[index] = part.copy(reference = it) },
                                label = { Text("Referencia") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = part.specification,
                                onValueChange = { parts[index] = part.copy(specification = it) },
                                label = { Text("Especificación (opcional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = part.quantity,
                                    onValueChange = {
                                        val filtered = if (part.catalogProductId != null) {
                                            it.filter(Char::isDigit)
                                        } else {
                                            it.filter { c -> c.isDigit() || c == '.' || c == ',' }
                                        }
                                        parts[index] = part.copy(quantity = filtered)
                                    },
                                    label = { Text("Cantidad") },
                                    supportingText = {
                                        if (part.catalogProductId != null) Text("Unidades enteras para descontar del stock.")
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = if (part.catalogProductId != null) KeyboardType.Number else KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = part.unit,
                                    onValueChange = { parts[index] = part.copy(unit = it) },
                                    label = { Text("Unidad") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = part.unitPrice,
                                onValueChange = { parts[index] = part.copy(unitPrice = it.filter(Char::isDigit).take(12)) },
                                label = { Text("Precio unitario (COP)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { parts.add(PartFormState()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Agregar material manual") }

                Button(
                    onClick = {
                        newProductError = null
                        newProductName = ""
                        newProductDescription = ""
                        newProductCategory = categories.firstOrNull()?.nombre ?: "Repuestos"
                        newProductPrice = ""
                        newProductStock = "1"
                        newProductImageUri = null
                        showNewProductDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Agregar repuesto nuevo al inventario") }
            }
        }

        (localError ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                val parsedMileage = mileage.toIntOrNull()
                val parsedLaborCost = laborCost.toLongOrNull()
                val parsedNextMileage = nextMileage.toIntOrNull()
                val namedParts = parts.filter { it.name.isNotBlank() }
                val draftParts = namedParts.map { part ->
                    MaintenancePartDraft(
                        catalogProductId = part.catalogProductId,
                        name = part.name,
                        brand = part.brand,
                        reference = part.reference,
                        specification = part.specification,
                        quantity = part.quantity.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        unit = part.unit,
                        unitPriceCop = part.unitPrice.toLongOrNull() ?: 0L
                    )
                }

                localError = when {
                    serviceType.isBlank() -> "Escribe el tipo de mantenimiento."
                    serviceDate != existingRecord?.record?.serviceDate &&
                            !serviceDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) -> "La fecha debe tener el formato AAAA-MM-DD."
                    parsedMileage == null -> "Ingresa el kilometraje del vehículo."
                    description.isBlank() -> "Describe el trabajo realizado."
                    laborCost.isNotBlank() && parsedLaborCost == null -> "Ingresa un costo de mano de obra válido."
                    nextMileage.isNotBlank() && parsedNextMileage == null -> "Ingresa un kilometraje válido para el próximo servicio."
                    namedParts.any { it.unitPrice.isNotBlank() && it.unitPrice.toLongOrNull() == null } -> "Ingresa un precio válido para cada repuesto."
                    draftParts.any { !it.quantity.isFinite() || it.quantity <= 0.0 } -> "Todas las cantidades de repuestos deben ser mayores que cero."
                    draftParts.any { it.catalogProductId != null && it.quantity % 1.0 != 0.0 } -> "Los repuestos del inventario se descuentan en unidades enteras."
                    else -> null
                }

                if (localError == null) {
                    onSave(
                        MaintenanceDraft(
                            serviceType = serviceType,
                            serviceDate = serviceDate,
                            mileageKm = parsedMileage!!,
                            description = description,
                            laborCostCop = parsedLaborCost ?: 0L,
                            nextServiceMileageKm = parsedNextMileage,
                            nextServiceDate = nextDate.takeIf { it.isNotBlank() },
                            observations = observations,
                            parts = draftParts
                        )
                    )
                }
            },
            enabled = !isSaving && !isCreatingProduct && pendingCreatedProductId == null,
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp)
        ) {
            Text(if (isSaving) "Guardando mantenimiento…" else "Guardar mantenimiento", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onBack, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp)) {
            Text("Cancelar")
        }
    }

    val pickerIndex = productPickerIndex
    if (pickerIndex != null) {
        val currentPart = parts.getOrNull(pickerIndex)
        val productsToShow = availableProducts.filter { product ->
            product.isActive && (product.stock > 0 || product.id == currentPart?.catalogProductId)
        }
        EdMotorBottomSheet(
            onDismissRequest = { productPickerIndex = null },
            scrollBody = false,
            title = { Text("Seleccionar repuesto") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (productsToShow.isEmpty()) {
                        Text("No hay repuestos disponibles en el inventario. Puedes agregar uno nuevo desde el formulario.")
                    }
                    productsToShow.forEach { product ->
                        val oldQuantity = existingRecord?.parts
                            ?.filter { it.catalogProductId == product.id }
                            ?.sumOf { it.quantity.toInt().toLong() } ?: 0L
                        val stockForThisEdit = product.stock.toLong() + oldQuantity
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                ProductImage(
                                    imageUri = product.imageUri,
                                    imageRes = product.imageRes,
                                    contentDescription = product.contentDescription,
                                    modifier = Modifier.size(58.dp)
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(product.name, fontWeight = FontWeight.Bold)
                                    Text("Stock: $stockForThisEdit", style = MaterialTheme.typography.bodySmall)
                                    Text("$${product.effectivePriceCop}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    onClick = {
                                        if (currentPart != null) {
                                            parts[pickerIndex] = currentPart.copy(
                                                catalogProductId = product.id,
                                                name = product.name,
                                                specification = product.description,
                                                quantity = "1",
                                                unit = currentPart.unit.ifBlank { "unidad" },
                                                unitPrice = product.effectivePriceCop.toString()
                                            )
                                        }
                                        productPickerIndex = null
                                    },
                                    enabled = stockForThisEdit > 0
                                ) { Text("Elegir") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { productPickerIndex = null }) { Text("Cerrar") }
            }
        )
    }

    if (showNewProductDialog) {
        EdMotorBottomSheet(
            onDismissRequest = { if (!isCreatingProduct) showNewProductDialog = false },
            dismissEnabled = !isCreatingProduct,
            scrollBody = false,
            title = { Text("Agregar repuesto al inventario") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("El nuevo repuesto se guardará en el catálogo y quedará disponible para seleccionarlo en este mantenimiento.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(newProductName, { newProductName = it }, label = { Text("Nombre") }, singleLine = true)
                    OutlinedTextField(newProductDescription, { newProductDescription = it }, label = { Text("Descripción") }, minLines = 2)
                    CategorySelector(
                        selectedCategory = newProductCategory,
                        categories = categories,
                        onCategorySelected = { newProductCategory = it },
                        onCreateCategory = onCreateCategory,
                        label = "Categoría"
                    )
                    OutlinedTextField(newProductPrice, { newProductPrice = it.filter(Char::isDigit) }, label = { Text("Precio (COP)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(newProductStock, { newProductStock = it.filter(Char::isDigit) }, label = { Text("Existencias iniciales") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    ProductImage(
                        imageUri = newProductImageUri,
                        imageRes = com.example.edmotor.R.drawable.mobil_20_w_50,
                        contentDescription = newProductName.ifBlank { "Foto del repuesto nuevo" },
                        modifier = Modifier.fillMaxWidth().height(130.dp),
                        contentScale = ContentScale.Fit
                    )
                    OutlinedButton(
                        onClick = { quickPhotoPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (newProductImageUri.isNullOrBlank()) "Elegir foto del dispositivo" else "Cambiar foto") }
                    if (newProductError != null) {
                        Text(newProductError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isCreatingProduct && pendingCreatedProductId == null,
                    onClick = {
                        val price = newProductPrice.toLongOrNull()
                        val stock = newProductStock.toIntOrNull()
                        newProductError = when {
                            newProductName.trim().length < 3 -> "El nombre debe tener al menos 3 caracteres."
                            newProductDescription.isBlank() -> "Escribe una descripción."
                            newProductCategory.isBlank() -> "Escribe una categoría."
                            price == null || price <= 0L -> "Ingresa un precio válido."
                            stock == null || stock <= 0 -> "El nuevo repuesto debe tener al menos una existencia."
                            else -> null
                        }
                        if (newProductError == null) {
                            onCreateProduct(
                                newProductName.trim(),
                                newProductDescription.trim(),
                                newProductCategory.trim(),
                                price!!,
                                stock!!,
                                newProductImageUri
                            ) { productId, message ->
                                if (productId != null) {
                                    pendingCreatedProductId = productId
                                } else {
                                    newProductError = message ?: "No fue posible registrar el repuesto."
                                }
                            }
                        }
                    }
                ) {
                    if (isCreatingProduct) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Guardar y agregar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNewProductDialog = false },
                    enabled = !isCreatingProduct
                ) { Text("Cancelar") }
            }
        )
    }
}
