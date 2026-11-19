package com.example.edmotor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edmotor.model.Product
import com.example.edmotor.ui.theme.AzulNitroOscuro
import com.example.edmotor.ui.theme.EdMotorPhotoMatte
import com.example.edmotor.ui.theme.GrisCarbon

@Composable
fun ProductGridCard(
    product: Product,
    quantity: Int,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onProductClick: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    isWorkshop: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val salePrice = product.promotionPrice?.takeIf { product.isOnPromotion && it.isNotBlank() }
    val badge = when {
        salePrice != null -> if (product.discountPercentage > 0) "−${product.discountPercentage}%" else "Oferta"
        product.isNew -> "Nuevo"
        product.badge.equals("Top ventas", ignoreCase = true) -> "Top ventas"
        else -> null
    }

    Surface(
        onClick = onProductClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(EdMotorPhotoMatte, RoundedCornerShape(14.dp))
            ) {
                ProductImage(
                    imageUri = product.imageUri,
                    imageRes = product.imageRes,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFavoriteClick()
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Box(
                        modifier = Modifier.size(34.dp).background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                            tint = if (isFavorite) AzulNitroOscuro else GrisCarbon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (badge != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = if (salePrice != null) colors.secondaryContainer else colors.primaryContainer,
                        contentColor = if (salePrice != null) colors.onSecondaryContainer else colors.onPrimaryContainer
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(start = 11.dp, end = 11.dp, top = 3.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = salePrice ?: product.price,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, lineHeight = 23.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (salePrice != null) colors.onSecondaryContainer else colors.onSurface
                    )
                    if (salePrice != null) {
                        Text(
                            text = product.price,
                            style = MaterialTheme.typography.labelMedium,
                            textDecoration = TextDecoration.LineThrough,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
                if (product.description.isNotBlank()) {
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!product.isOutOfStock || quantity > 0 || isWorkshop) {
                    val stockLabel = when {
                        product.isOutOfStock -> "Agotado"
                        product.stock == 1 -> "Última unidad"
                        product.stock <= 3 -> "Quedan ${product.stock}"
                        else -> "${product.stock} disponibles"
                    }
                    val stockColor = when {
                        product.isOutOfStock -> colors.error
                        product.stock <= 3 -> colors.onSecondaryContainer
                        else -> colors.onSurfaceVariant
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(5.dp).background(stockColor, CircleShape))
                        Text(stockLabel, style = MaterialTheme.typography.labelSmall, color = stockColor)
                    }
                }

                if (isWorkshop || quantity > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isWorkshop) Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        else Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDecrease()
                            },
                            enabled = !isWorkshop || product.stock > 0,
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = if (isWorkshop) "Disminuir stock" else "Disminuir cantidad"
                            )
                        }
                        if (!isWorkshop) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = quantity.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "En carrito",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        FilledIconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onIncrease()
                            },
                            enabled = isWorkshop || quantity < product.stock,
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = if (isWorkshop) "Aumentar stock" else "Aumentar cantidad"
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onIncrease()
                        },
                        enabled = !product.isOutOfStock,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 43.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        if (!product.isOutOfStock) {
                            Icon(
                                Icons.Outlined.AddShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Text(
                            text = if (product.isOutOfStock) "Agotado" else "Agregar",
                            modifier = Modifier.padding(start = if (product.isOutOfStock) 0.dp else 6.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
