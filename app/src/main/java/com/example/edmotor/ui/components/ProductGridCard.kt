
package com.example.edmotor.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.edmotor.model.Product
import com.example.edmotor.ui.theme.AzulNitro
import com.example.edmotor.ui.theme.AzulNitroContenedorClaro
import com.example.edmotor.ui.theme.GrisCarbon
import com.example.edmotor.ui.theme.NaranjaContenedorClaro
import com.example.edmotor.ui.theme.NaranjaPerformance

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

    val haptics = LocalHapticFeedback.current

    // ==========================================
    // 1. CONTROL DE INVENTARIO
    // ==========================================

    val isOutOfStock = product.stock <= 0

    val canIncrease = !isOutOfStock &&
            quantity < product.stock

    val stockLabel = when {
        isOutOfStock -> "AGOTADO"
        product.stock <= 3 -> "ÚLTIMAS ${product.stock} UNIDADES"
        else -> "STOCK: ${product.stock}"
    }

    // ==========================================
    // 2. ETIQUETAS COMERCIALES
    // ==========================================

    val badgeText = when {
        isOutOfStock -> "AGOTADO"
        product.isOnPromotion -> "PROMOCIÓN"
        product.isNew -> "NUEVO"
        product.badge.equals("top ventas", true) -> "TOP VENTAS"
        else -> product.badge.uppercase()
    }

    val badgeBackground = when {
        isOutOfStock -> Color(0xFFFFE4E6)
        product.isOnPromotion -> NaranjaContenedorClaro
        product.isNew -> AzulNitroContenedorClaro
        product.badge.equals("top ventas", true) ->
            Color(0xFFFFF3CD)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val badgeForeground = when {
        isOutOfStock -> Color(0xFFBE123C)
        product.isOnPromotion -> NaranjaPerformance
        product.isNew -> AzulNitro
        product.badge.equals("top ventas", true) ->
            Color(0xFF92400E)
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    // ==========================================
    // 3. TARJETA PRINCIPAL
    // ==========================================

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable {
                onProductClick()
            },
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {

        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // =====================================
            // 4. IMAGEN DEL PRODUCTO
            // =====================================

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(14.dp)
                    )
            ) {

                ProductImage(
                    imageUri = product.imageUri,
                    imageRes = product.imageRes,
                    contentDescription = product.contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(9.dp),
                    contentScale = ContentScale.Fit
                )

                // =================================
                // FAVORITOS
                // =================================

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {

                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(
                                HapticFeedbackType.TextHandleMove
                            )

                            onFavoriteClick()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {

                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Default.Favorite
                            } else {
                                Icons.Default.FavoriteBorder
                            },
                            contentDescription = if (isFavorite) {
                                "Quitar de favoritos"
                            } else {
                                "Agregar a favoritos"
                            },
                            tint = if (isFavorite) {
                                AzulNitro
                            } else {
                                GrisCarbon
                            },
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // =================================
                // ETIQUETAS
                // =================================

                if (badgeText.isNotBlank()) {

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBackground
                    ) {

                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(
                                horizontal = 7.dp,
                                vertical = 5.dp
                            ),
                            color = badgeForeground,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // =====================================
            // 5. NOMBRE DEL REPUESTO
            // =====================================

            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // =====================================
            // 6. DESCRIPCIÓN
            // =====================================

            Text(
                text = product.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // =====================================
            // 7. PRECIOS
            // =====================================

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {

                if (
                    product.isOnPromotion &&
                    product.promotionPrice != null
                ) {

                    Text(
                        text = product.price,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                        maxLines = 1
                    )

                    Text(
                        text = product.promotionPrice,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = NaranjaPerformance,
                        maxLines = 1
                    )

                } else {

                    Text(
                        text = product.price,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = AzulNitro,
                        maxLines = 1
                    )
                }

                // =================================
                // EXISTENCIAS
                // =================================

                Text(
                    text = stockLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isOutOfStock ->
                            MaterialTheme.colorScheme.error

                        product.stock <= 3 ->
                            NaranjaPerformance

                        else ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // =====================================
            // 8. CONTROL DEL CARRITO
            // =====================================

            if (isWorkshop) {
                // En el inicio del dueño solo se muestran los controles + y -.
                // El resto de la tarjeta continúa abriendo la ficha del repuesto.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onDecrease,
                        enabled = product.stock > 0,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Disminuir stock"
                        )
                    }

                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier.size(18.dp)
                    )

                    FilledIconButton(
                        onClick = onIncrease,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Aumentar stock"
                        )
                    }
                }
            } else when {

                // PRODUCTO AGOTADO
                isOutOfStock -> {

                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = Color(0xFFE2E8F0),
                            disabledContentColor = Color(0xFF64748B)
                        )
                    ) {

                        Text(
                            text = "AGOTADO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // PRODUCTO YA AGREGADO AL CARRITO
                quantity > 0 -> {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        // DISMINUIR
                        FilledTonalIconButton(
                            onClick = {
                                haptics.performHapticFeedback(
                                    HapticFeedbackType.TextHandleMove
                                )

                                onDecrease()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {

                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Disminuir cantidad"
                            )
                        }

                        // CANTIDAD
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Text(
                                text = "$quantity",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = "En carrito",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // AUMENTAR
                        FilledIconButton(
                            onClick = {
                                if (canIncrease) {

                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove
                                    )

                                    onIncrease()
                                }
                            },
                            enabled = canIncrease,
                            modifier = Modifier.size(34.dp)
                        ) {

                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Aumentar cantidad"
                            )
                        }
                    }

                    // MENSAJE DE LÍMITE
                    if (quantity >= product.stock) {

                        Text(
                            text = "Máximo de unidades disponibles alcanzado.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // PRODUCTO DISPONIBLE
                else -> {

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
                            .height(40.dp),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AzulNitro
                        )
                    ) {

                        Icon(
                            imageVector = Icons.Default.AddShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )

                        Text(
                            text = "AGREGAR",
                            modifier = Modifier.padding(start = 5.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
