
package com.example.edmotor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Star

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.edmotor.model.Product
import com.example.edmotor.ui.components.ProductImage

import java.text.NumberFormat
import java.util.Locale

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

    val haptics = LocalHapticFeedback.current

    // ==========================================
    // 1. INFORMACIÓN DEL INVENTARIO
    // ==========================================

    val isOutOfStock = product.stock <= 0

    val canIncrease = !isOutOfStock &&
            quantity < product.stock

    val stockMessage = when {
        product.stock <= 0 -> "Producto agotado"
        product.stock <= 3 -> "¡Últimas ${product.stock} unidades!"
        else -> "${product.stock} unidades disponibles"
    }

    val stockColor = when {
        product.stock <= 0 -> MaterialTheme.colorScheme.error
        product.stock <= 3 -> Color(0xFFD97706)
        else -> Color(0xFF15803D)
    }

    // ==========================================
    // 2. PRECIOS
    // ==========================================

    val originalPrice = product.price

    val promotionPrice = product.promotionPrice

    val hasPromotion = product.isOnPromotion &&
            !promotionPrice.isNullOrBlank()

    val discountPercentage = remember(
        product.price,
        product.promotionPrice,
        hasPromotion
    ) {

        if (hasPromotion) {

            val original = product.price
                .filter { it.isDigit() }
                .toLongOrNull() ?: 0L

            val promotional = promotionPrice
                ?.filter { it.isDigit() }
                ?.toLongOrNull() ?: 0L

            if (original > 0 && promotional > 0 &&
                promotional < original
            ) {
                ((original - promotional) * 100 / original).toInt()
            } else {
                0
            }

        } else {
            0
        }
    }

    // ==========================================
    // 3. CONTENEDOR PRINCIPAL
    // ==========================================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // ======================================
        // 4. BARRA SUPERIOR
        // ======================================

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            IconButton(onClick = onBack) {

                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Volver"
                )
            }

            Text(
                text = "DETALLE DEL REPUESTO",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )

            if (!isWorkshop) {
                Box {

                    IconButton(onClick = onOpenCart) {

                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = "Abrir carrito"
                        )
                    }

                    if (cartCount > 0) {

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(19.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error
                        ) {

                            Box(
                                contentAlignment = Alignment.Center
                            ) {

                                Text(
                                    text = if (cartCount > 99) {
                                        "99+"
                                    } else {
                                        cartCount.toString()
                                    },
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        // ======================================
        // 5. CONTENIDO DESPLAZABLE
        // ======================================

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {

            // ==================================
            // IMAGEN DEL PRODUCTO
            // ==================================

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(22.dp)
                    )
            ) {

                ProductImage(
                    imageUri = product.imageUri,
                    imageRes = product.imageRes,
                    contentDescription = product.contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp),
                    contentScale = ContentScale.Fit
                )

                // ETIQUETAS

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {

                    if (isOutOfStock) {

                        DetailBadge(
                            text = "AGOTADO",
                            background = Color(0xFFFFE4E6),
                            foreground = Color(0xFFBE123C)
                        )

                    } else {

                        if (product.isNew) {

                            DetailBadge(
                                text = "NUEVO",
                                background = Color(0xFFDBEAFE),
                                foreground = Color(0xFF1D4ED8)
                            )
                        }

                        if (hasPromotion) {

                            DetailBadge(
                                text = "PROMOCIÓN",
                                background = Color(0xFFFFEDD5),
                                foreground = Color(0xFFC2410C)
                            )
                        }
                    }
                }

                // FAVORITOS: solo se muestran al cliente.
                if (!isWorkshop) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 3.dp
                    ) {

                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(
                                    HapticFeedbackType.TextHandleMove
                                )
                                onFavoriteClick()
                            }
                        ) {

                            Icon(
                                imageVector = if (isFavorite) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                                contentDescription = "Favoritos",
                                tint = if (isFavorite) {
                                    Color(0xFFDC2626)
                                } else {
                                    Color.DarkGray
                                }
                            )
                        }
                    }
                }

                // DESCUENTO

                if (discountPercentage > 0) {

                    DetailBadge(
                        text = "-$discountPercentage%",
                        background = Color(0xFFDC2626),
                        foreground = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    )
                }
            }

            // ==================================
            // CATEGORÍA Y NOMBRE
            // ==================================

            Column(
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {

                Text(
                    text = product.category.uppercase(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = product.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(19.dp)
                    )

                    Text(
                        text = " ${product.rating}",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.width(7.dp))

                    Text(
                        text = "Calificación del producto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ==================================
            // PRECIOS
            // ==================================

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {

                    Text(
                        "PRECIO DEL PRODUCTO",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasPromotion) {

                        Text(
                            text = originalPrice,
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = promotionPrice.orEmpty(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFEA580C)
                        )

                        if (discountPercentage > 0) {

                            Text(
                                "Ahorras $discountPercentage% en esta compra",
                                color = Color(0xFF15803D),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                    } else {

                        Text(
                            text = originalPrice,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ==================================
            // DISPONIBILIDAD
            // ==================================

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = stockColor,
                        modifier = Modifier.size(27.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            "DISPONIBILIDAD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            stockMessage,
                            fontWeight = FontWeight.Bold,
                            color = stockColor
                        )
                    }
                }
            }

            // ==================================
            // DESCRIPCIÓN
            // ==================================

            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {

                Text(
                    "Descripción del producto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    product.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // ==================================
            // INFORMACIÓN COMERCIAL
            // ==================================

            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {

                Text(
                    "Información de compra",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                DetailInformationRow(
                    label = "Disponibilidad",
                    value = product.availabilityLabel
                )

                DetailInformationRow(
                    label = if (isWorkshop) "Existencias disponibles" else "Unidades en tu carrito",
                    value = if (isWorkshop) product.stock.toString() else quantity.toString()
                )

                DetailInformationRow(
                    label = "Identificación",
                    value = "REF-${product.id}"
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        // ======================================
        // 6. PANEL INFERIOR DE COMPRA
        // ======================================

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(7.dp)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                if (isWorkshop) {
                    Button(
                        onClick = onEditProduct,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(53.dp),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = "EDITAR REPUESTO",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {

                    if (isOutOfStock) {

                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(53.dp),
                            shape = RoundedCornerShape(13.dp)
                        ) {

                            Text(
                                "PRODUCTO AGOTADO",
                                fontWeight = FontWeight.Bold
                            )
                        }

                    } else {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            Column {

                                Text(
                                    "Cantidad",
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "$quantity en carrito",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(13.dp)
                            ) {

                                FilledIconButton(
                                    onClick = {
                                        if (quantity > 0) {
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.TextHandleMove
                                            )
                                            onDecrease()
                                        }
                                    },
                                    enabled = quantity > 0
                                ) {

                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = "Disminuir"
                                    )
                                }

                                Text(
                                    text = quantity.toString(),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleLarge
                                )

                                FilledIconButton(
                                    onClick = {
                                        if (canIncrease) {
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.Confirm
                                            )
                                            onIncrease()
                                        }
                                    },
                                    enabled = canIncrease
                                ) {

                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Aumentar"
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {

                                if (canIncrease) {
                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.Confirm
                                    )
                                    onIncrease()
                                }
                            },
                            enabled = canIncrease,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(53.dp),
                            shape = RoundedCornerShape(13.dp)
                        ) {

                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null
                            )

                            Spacer(Modifier.width(9.dp))

                            Text(
                                if (quantity > 0) {
                                    "AGREGAR OTRA UNIDAD"
                                } else {
                                    "AGREGAR AL CARRITO"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!canIncrease && quantity >= product.stock) {

                            Text(
                                "Has alcanzado el máximo de unidades disponibles.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenCart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(13.dp)
                    ) {

                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(8.dp))

                        Text("VER MI CARRITO ($cartCount)")
                    }
                }
            }
        }
    }
}

// =====================================================
// COMPONENTE: ETIQUETAS
// =====================================================

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
        color = background
    ) {

        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 7.dp
            ),
            color = foreground,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp
        )
    }
}

// =====================================================
// COMPONENTE: INFORMACIÓN
// =====================================================

@Composable
private fun DetailInformationRow(
    label: String,
    value: String
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
