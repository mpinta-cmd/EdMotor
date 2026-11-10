
package com.example.edmotor.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edmotor.model.Product
import com.example.edmotor.ui.theme.AzulNitro
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

    onOpenCart: () -> Unit

) {

    // ==========================================
    // 1. INFORMACIÓN DEL PRODUCTO
    // ==========================================

    val isOutOfStock = product.badge.equals(
        "Agotado",
        ignoreCase = true
    )

    val unitPrice = remember(product.price) {

        product.price
            .filter { it.isDigit() }
            .toLongOrNull() ?: 0L

    }

    val subtotal = unitPrice * quantity.toLong()

    val formattedSubtotal = remember(subtotal) {

        formatCop(subtotal)

    }

    // ==========================================
    // 2. COLORES DEL DISTINTIVO
    // ==========================================

    val badgeBackground = when (product.badge.lowercase()) {

        "agotado" -> Color(0xFFFFE4E6)

        "oferta" -> Color(0xFFFFEDD5)

        "nuevo" -> Color(0xFFDBEAFE)

        "top ventas" -> Color(0xFFFEF3C7)

        else -> MaterialTheme.colorScheme.secondaryContainer

    }

    val badgeForeground = when (product.badge.lowercase()) {

        "agotado" -> Color(0xFFBE123C)

        "oferta" -> Color(0xFFC2410C)

        "nuevo" -> Color(0xFF1D4ED8)

        "top ventas" -> Color(0xFF92400E)

        else -> MaterialTheme.colorScheme.onSecondaryContainer

    }

    // ==========================================
    // 3. CONTENEDOR PRINCIPAL
    // ==========================================

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        // ======================================
        // INFORMACIÓN DESPLAZABLE
        // ======================================

        LazyColumn(

            modifier = Modifier.weight(1f),

            contentPadding = PaddingValues(
                bottom = 18.dp
            )

        ) {

            item {

                Column(

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 17.dp)

                ) {

                    // ==================================
                    // BARRA SUPERIOR
                    // ==================================

                    Row(

                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        IconButton(
                            onClick = onBack
                        ) {

                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Volver"
                            )

                        }

                        Text(
                            text = "DETALLE DEL REPUESTO",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = onFavoriteClick
                        ) {

                            Icon(

                                imageVector = if (isFavorite) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },

                                contentDescription = "Favoritos",

                                tint = if (isFavorite) {
                                    AzulNitro
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },

                                modifier = Modifier.size(25.dp)

                            )

                        }

                    }

                    // ==================================
                    // IMAGEN DEL PRODUCTO
                    // ==================================

                    Card(

                        modifier = Modifier
                            .fillMaxWidth()
                            .height(245.dp),

                        shape = RoundedCornerShape(22.dp),

                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceVariant
                        ),

                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        )

                    ) {

                        Box(

                            modifier = Modifier.fillMaxSize(),

                            contentAlignment = Alignment.Center

                        ) {

                            Image(

                                painter = painterResource(
                                    id = product.imageRes
                                ),

                                contentDescription =
                                    product.contentDescription,

                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(17.dp),

                                contentScale = ContentScale.Fit

                            )

                        }

                    }

                    Spacer(
                        modifier = Modifier.height(17.dp)
                    )

                    // ==================================
                    // DISTINTIVO Y CATEGORÍA
                    // ==================================

                    Row(

                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp),

                        verticalAlignment =
                            Alignment.CenterVertically

                    ) {

                        Surface(

                            shape = RoundedCornerShape(9.dp),

                            color = badgeBackground

                        ) {

                            Text(

                                text = if (product.badge.isBlank()) {
                                    "DISPONIBLE"
                                } else {
                                    product.badge.uppercase()
                                },

                                modifier = Modifier.padding(
                                    horizontal = 11.dp,
                                    vertical = 7.dp
                                ),

                                color = badgeForeground,

                                fontSize = 10.sp,

                                fontWeight = FontWeight.ExtraBold

                            )

                        }

                        if (product.category.isNotBlank()) {

                            Surface(

                                shape = RoundedCornerShape(9.dp),

                                color = MaterialTheme.colorScheme.surfaceVariant

                            ) {

                                Text(

                                    text = product.category,

                                    modifier = Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 7.dp
                                    ),

                                    fontSize = 10.sp,

                                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                                    fontWeight = FontWeight.SemiBold

                                )

                            }

                        }

                    }

                    Spacer(
                        modifier = Modifier.height(15.dp)
                    )

                    // ==================================
                    // NOMBRE DEL PRODUCTO
                    // ==================================

                    Text(

                        text = product.name,

                        style = MaterialTheme.typography.headlineSmall,

                        fontWeight = FontWeight.ExtraBold,

                        color = MaterialTheme.colorScheme.onSurface

                    )

                    Spacer(
                        modifier = Modifier.height(9.dp)
                    )

                    // ==================================
                    // CALIFICACIÓN Y REFERENCIA
                    // ==================================

                    Row(

                        verticalAlignment = Alignment.CenterVertically

                    ) {

                        Icon(

                            imageVector = Icons.Default.Star,

                            contentDescription = null,

                            tint = Color(0xFFFFB300),

                            modifier = Modifier.size(19.dp)

                        )

                        Text(

                            text = String.format(
                                Locale("es", "CO"),
                                "%.1f",
                                product.rating
                            ),

                            modifier = Modifier.padding(start = 4.dp),

                            fontWeight = FontWeight.Bold,

                            style = MaterialTheme.typography.bodyMedium

                        )

                        Spacer(
                            modifier = Modifier.width(12.dp)
                        )

                        Text(

                            text = "Código #${product.id}",

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                            style = MaterialTheme.typography.bodySmall

                        )

                    }

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    // ==================================
                    // PRECIO
                    // ==================================

                    Text(

                        text = "PRECIO DE VENTA",

                        style = MaterialTheme.typography.labelMedium,

                        color = MaterialTheme.colorScheme.onSurfaceVariant,

                        fontWeight = FontWeight.Bold

                    )

                    Text(

                        text = product.price,

                        style = MaterialTheme.typography.headlineMedium,

                        fontWeight = FontWeight.ExtraBold,

                        color = AzulNitro

                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Spacer(
                        modifier = Modifier.height(17.dp)
                    )

                    // ==================================
                    // DESCRIPCIÓN
                    // ==================================

                    Text(

                        text = "Descripción del producto",

                        style = MaterialTheme.typography.titleMedium,

                        fontWeight = FontWeight.ExtraBold

                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(

                        text = product.description,

                        style = MaterialTheme.typography.bodyLarge,

                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    // ==================================
                    // INFORMACIÓN ADICIONAL
                    // ==================================

                    Card(

                        modifier = Modifier.fillMaxWidth(),

                        shape = RoundedCornerShape(17.dp),

                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceVariant
                        )

                    ) {

                        Column(

                            modifier = Modifier.padding(16.dp),

                            verticalArrangement =
                                Arrangement.spacedBy(7.dp)

                        ) {

                            Text(

                                text = "Información del catálogo",

                                fontWeight = FontWeight.Bold,

                                style = MaterialTheme.typography.titleSmall

                            )

                            Text(

                                text = "Referencia interna: #${product.id}",

                                style = MaterialTheme.typography.bodySmall

                            )

                            Text(

                                text = "Categoría: ${product.category}",

                                style = MaterialTheme.typography.bodySmall

                            )

                        }

                    }

                    Spacer(
                        modifier = Modifier.height(15.dp)
                    )

                }

            }

        }

        // ==========================================
        // 4. PANEL INFERIOR DE COMPRA
        // ==========================================

        Surface(

            modifier = Modifier.fillMaxWidth(),

            color = MaterialTheme.colorScheme.surface,

            shadowElevation = 9.dp

        ) {

            Column(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 13.dp
                    ),

                verticalArrangement =
                    Arrangement.spacedBy(11.dp)

            ) {

                // ==================================
                // PRECIO Y SUBTOTAL
                // ==================================

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Column {

                        Text(

                            text = "PRECIO UNITARIO",

                            fontSize = 10.sp,

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                        Text(

                            text = product.price,

                            fontWeight = FontWeight.Bold,

                            style = MaterialTheme.typography.titleMedium

                        )

                    }

                    Spacer(
                        modifier = Modifier.weight(1f)
                    )

                    Column(

                        horizontalAlignment = Alignment.End

                    ) {

                        Text(

                            text = "SUBTOTAL",

                            fontSize = 10.sp,

                            fontWeight = FontWeight.Bold,

                            color = MaterialTheme.colorScheme.onSurfaceVariant

                        )

                        Text(

                            text = formattedSubtotal,

                            fontWeight = FontWeight.ExtraBold,

                            style = MaterialTheme.typography.titleLarge,

                            color = AzulNitro

                        )

                    }

                }

                // ==================================
                // SELECTOR DE CANTIDAD
                // ==================================

                Row(

                    modifier = Modifier.fillMaxWidth(),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Text(

                        text = "Cantidad",

                        fontWeight = FontWeight.Bold,

                        style = MaterialTheme.typography.titleSmall

                    )

                    Spacer(
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalIconButton(

                        onClick = onDecrease,

                        enabled = quantity > 0,

                        modifier = Modifier.size(38.dp)

                    ) {

                        Icon(

                            imageVector = Icons.Default.Remove,

                            contentDescription = "Disminuir cantidad"

                        )

                    }

                    Text(

                        text = quantity.toString(),

                        modifier = Modifier.padding(horizontal = 17.dp),

                        style = MaterialTheme.typography.titleLarge,

                        fontWeight = FontWeight.ExtraBold

                    )

                    FilledIconButton(

                        onClick = onIncrease,

                        enabled = !isOutOfStock,

                        modifier = Modifier.size(38.dp)

                    ) {

                        Icon(

                            imageVector = Icons.Default.Add,

                            contentDescription = "Aumentar cantidad"

                        )

                    }

                }

                // ==================================
                // BOTONES DE COMPRA
                // ==================================

                Row(

                    horizontalArrangement =
                        Arrangement.spacedBy(9.dp),

                    verticalAlignment = Alignment.CenterVertically

                ) {

                    Button(

                        onClick = onIncrease,

                        enabled = !isOutOfStock,

                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),

                        shape = RoundedCornerShape(13.dp),

                        colors = ButtonDefaults.buttonColors(
                            containerColor = AzulNitro
                        )

                    ) {

                        Icon(

                            imageVector = Icons.Default.ShoppingCart,

                            contentDescription = null,

                            modifier = Modifier.size(19.dp)

                        )

                        Spacer(
                            modifier = Modifier.width(7.dp)
                        )

                        Text(

                            text = when {
                                isOutOfStock -> "AGOTADO"
                                quantity == 0 -> "AGREGAR AL CARRITO"
                                else -> "AÑADIR UNIDAD"
                            },

                            fontWeight = FontWeight.Bold,

                            fontSize = 11.sp

                        )

                    }

                    // VER CARRITO SI YA HAY PRODUCTOS

                    if (cartCount > 0) {

                        OutlinedButton(

                            onClick = onOpenCart,

                            modifier = Modifier.height(50.dp),

                            shape = RoundedCornerShape(13.dp)

                        ) {

                            Text(
                                text = "Ver carrito",
                                fontWeight = FontWeight.Bold
                            )

                        }

                    }

                }

            }

        }

    }

}

// ==============================================
// FORMATEAR VALORES EN PESOS COLOMBIANOS
// ==============================================

private fun formatCop(amount: Long): String {

    val formatted = NumberFormat
        .getNumberInstance(Locale("es", "CO"))
        .format(amount)

    return "\$$formatted"

}
