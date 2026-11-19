package com.example.edmotor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

internal data class ProductImageChoice(
    val label: String,
    val imageRes: Int,
    val imageUri: String? = null
)

@Composable
internal fun ProductImagePicker(
    previousPhotos: List<ProductImageChoice>,
    catalogImages: List<ProductImageChoice>,
    selectedImageRes: Int,
    selectedImageUri: String?,
    onDismiss: () -> Unit,
    onSelect: (ProductImageChoice) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Seleccionar imagen", style = MaterialTheme.typography.titleLarge)
                if (previousPhotos.isEmpty() && catalogImages.isEmpty()) {
                    Text("Aún no hay imágenes cargadas.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f, fill = false).heightIn(max = 400.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (previousPhotos.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("Fotos de repuestos", style = MaterialTheme.typography.titleSmall)
                            }
                            items(previousPhotos, key = { "photo:${it.imageUri}" }) { choice ->
                                ImageChoiceCard(choice, choice.imageUri == selectedImageUri, onSelect)
                            }
                        }
                        if (catalogImages.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("Imágenes del catálogo", style = MaterialTheme.typography.titleSmall)
                            }
                            items(catalogImages, key = { "drawable:${it.imageRes}" }) { choice ->
                                ImageChoiceCard(
                                    choice = choice,
                                    isSelected = selectedImageUri == null && choice.imageRes == selectedImageRes,
                                    onSelect = onSelect
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
private fun ImageChoiceCard(
    choice: ProductImageChoice,
    isSelected: Boolean,
    onSelect: (ProductImageChoice) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        modifier = Modifier.selectable(
            selected = isSelected,
            role = Role.RadioButton,
            onClick = { onSelect(choice) }
        ),
        border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) colors.primaryContainer else colors.surface
        )
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProductImage(
                imageUri = choice.imageUri,
                imageRes = choice.imageRes,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(76.dp)
            )
            Text(
                text = choice.label,
                style = MaterialTheme.typography.labelMedium,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
