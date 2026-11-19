package com.example.edmotor.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Product
import com.example.edmotor.ui.components.ProductImage
import com.example.edmotor.ui.theme.EdMotorHeroAmber
import com.example.edmotor.ui.theme.EdMotorHeroNavy
import com.example.edmotor.ui.theme.EdMotorPhotoMatte

internal val ProductHeaderContent = Color(0xFFF3F7FF)

@Composable
fun ProductDetailScreen(
    product: Product,
    quantity: Int,
    cartCount: Int,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onFavoriteClick: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onOpenCart: () -> Unit,
    isWorkshop: Boolean = false,
    onEditProduct: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val hasPromotion = product.isOnPromotion && !product.promotionPrice.isNullOrBlank()

    Column(
        modifier = Modifier.fillMaxSize().background(colors.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(color = EdMotorHeroNavy, contentColor = ProductHeaderContent) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    "DETALLE DEL REPUESTO",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                if (isWorkshop) {
                    Spacer(Modifier.width(48.dp))
                } else {
                    Box {
                        IconButton(onClick = onOpenCart) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Abrir carrito")
                        }
                        if (cartCount > 0) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp),
                                color = EdMotorHeroAmber,
                                contentColor = Color(0xFF3D1708),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (cartCount > 99) "99+" else cartCount.toString(),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProductDetailPhoto(
                product = product,
                hasPromotion = hasPromotion,
                isFavorite = isFavorite,
                onFavoriteClick = if (isWorkshop) null else {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFavoriteClick()
                    }
                }
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (product.category.isNotBlank()) {
                    Text(
                        product.category.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    product.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = colors.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "${product.rating}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Calificación del producto",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            ProductDetailPrice(product, hasPromotion)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Descripción del producto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        product.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    HorizontalDivider(color = colors.outline)
                    Text(
                        "Información de compra",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    DetailInformationRow(
                        label = if (isWorkshop) "Existencias disponibles" else "Unidades en tu carrito",
                        value = if (isWorkshop) product.stock.toString() else quantity.toString()
                    )
                    DetailInformationRow(label = "Identificación", value = "REF-${product.id}")
                }
            }
        }

        ProductDetailActions(
            product = product,
            quantity = quantity,
            cartCount = cartCount,
            isWorkshop = isWorkshop,
            onDecrease = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDecrease()
            },
            onIncrease = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onIncrease()
            },
            onOpenCart = onOpenCart,
            onEditProduct = onEditProduct
        )
    }
}

@Composable
private fun ProductDetailPhoto(
    product: Product,
    hasPromotion: Boolean,
    isFavorite: Boolean,
    onFavoriteClick: (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    val photoShape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = EdMotorPhotoMatte,
        shape = photoShape,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Box(Modifier.fillMaxWidth().height(240.dp).clip(photoShape)) {
            ProductImage(
                imageUri = product.imageUri,
                imageRes = product.imageRes,
                contentDescription = product.contentDescription,
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                contentScale = ContentScale.Fit
            )
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (product.isOutOfStock) {
                    DetailBadge("AGOTADO", colors.errorContainer, colors.onErrorContainer)
                } else {
                    if (product.isNew) {
                        DetailBadge("NUEVO", colors.primaryContainer, colors.onPrimaryContainer)
                    }
                    if (hasPromotion) {
                        DetailBadge(
                            "PROMOCIÓN", colors.secondaryContainer, colors.onSecondaryContainer
                        )
                    }
                }
            }

            if (onFavoriteClick != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(9.dp),
                    shape = CircleShape,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    IconButton(onClick = onFavoriteClick) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite
                            else Icons.Default.FavoriteBorder,
                            contentDescription = "Favoritos",
                            tint = if (isFavorite) colors.error else colors.onSurface
                        )
                    }
                }
            }

            if (product.discountPercentage > 0) {
                DetailBadge(
                    text = "-${product.discountPercentage}%",
                    background = colors.secondaryContainer,
                    foreground = colors.onSecondaryContainer,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ProductDetailPrice(product: Product, hasPromotion: Boolean) {
    val colors = MaterialTheme.colorScheme
    val stockMessage = when {
        product.isOutOfStock -> "Producto agotado"
        product.stock <= 3 -> "¡Últimas ${product.stock} unidades!"
        else -> "${product.stock} unidades disponibles"
    }
    val stockColor = when {
        product.isOutOfStock -> colors.onErrorContainer
        product.stock <= 3 -> colors.onSecondaryContainer
        else -> colors.onPrimaryContainer
    }
    val stockBackground = when {
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "PRECIO DEL PRODUCTO",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    product.effectivePrice,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (hasPromotion) colors.onSecondaryContainer else colors.primary,
                    fontWeight = FontWeight.ExtraBold
                )
                if (hasPromotion) {
                    Text(
                        product.price,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough
                    )
                    if (product.discountPercentage > 0) {
                        Text(
                            "Ahorras ${product.discountPercentage}% en esta compra",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = stockBackground,
                contentColor = stockColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(20.dp))
                    Column {
                        Text("DISPONIBILIDAD", style = MaterialTheme.typography.labelSmall)
                        Text(stockMessage, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductDetailActions(
    product: Product,
    quantity: Int,
    cartCount: Int,
    isWorkshop: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onOpenCart: () -> Unit,
    onEditProduct: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val buttonShape = RoundedCornerShape(16.dp)
    val canIncrease = !product.isOutOfStock && quantity < product.stock

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isWorkshop) {
                Button(
                    onClick = onEditProduct,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = buttonShape
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("EDITAR REPUESTO", fontWeight = FontWeight.Bold)
                }
            } else {
                if (!product.isOutOfStock) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Cantidad", fontWeight = FontWeight.Bold)
                            Text(
                                "$quantity en carrito",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(onClick = onDecrease, enabled = quantity > 0) {
                                Icon(Icons.Default.Remove, contentDescription = "Disminuir")
                            }
                            Text(
                                quantity.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalIconButton(onClick = onIncrease, enabled = canIncrease) {
                                Icon(Icons.Default.Add, contentDescription = "Aumentar")
                            }
                        }
                    }
                }

                Button(
                    onClick = onIncrease,
                    enabled = canIncrease,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = buttonShape
                ) {
                    if (!product.isOutOfStock) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        when {
                            product.isOutOfStock -> "PRODUCTO AGOTADO"
                            quantity > 0 -> "AGREGAR OTRA UNIDAD"
                            else -> "AGREGAR AL CARRITO"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!product.isOutOfStock && !canIncrease) {
                    Text(
                        "Has alcanzado el máximo de unidades disponibles.",
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                OutlinedButton(
                    onClick = onOpenCart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = buttonShape
                ) {
                    Text("VER MI CARRITO ($cartCount)")
                }
            }
        }
    }
}

@Composable
private fun DetailBadge(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = background,
        contentColor = foreground
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailInformationRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}
