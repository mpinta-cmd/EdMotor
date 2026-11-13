
package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.Product
import com.example.edmotor.ui.components.CartItemCard
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CartScreen(
    products: List<Product>,
    cartQuantity: Map<Int, Int>,
    onIncrease: (Int) -> Unit,
    onDecrease: (Int) -> Unit,
    onSubmitOrder: () -> Unit = {},
    isSubmittingOrder: Boolean = false,
    orderMessage: String? = null,
    isOrderError: Boolean = false
) {

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
    }

    val cartItems = products.filter {
        (cartQuantity[it.id] ?: 0) > 0
    }

    fun parsePrice(value: String): Long {
        return value.filter { it.isDigit() }
            .toLongOrNull() ?: 0L
    }

    val totalAmount = cartItems.sumOf { product ->

        val quantity = cartQuantity[product.id] ?: 0

        parsePrice(product.effectivePrice) * quantity
    }

    val formattedTotal = NumberFormat
        .getNumberInstance(Locale("es", "CO"))
        .format(totalAmount)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            "Carrito de compras",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (orderMessage != null) {

            Text(
                text = orderMessage,
                color = if (isOrderError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (cartItems.isEmpty()) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    "Tu carrito está vacío. Selecciona repuestos del catálogo.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        } else {

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                state = listState
            ) {

                items(
                    items = cartItems,
                    key = { it.id }
                ) { product ->

                    val quantity = cartQuantity[product.id] ?: 0

                    CartItemCard(
                        product = product,
                        cantidad = quantity,
                        onIncrease = {
                            onIncrease(product.id)
                        },
                        onDecrease = {
                            onDecrease(product.id)
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            "Total del pedido:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            "\$$formattedTotal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "El pedido será enviado a EdMotor para su revisión y confirmación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onSubmitOrder,
                        enabled = !isSubmittingOrder,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {

                        if (isSubmittingOrder) {

                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 10.dp),
                                strokeWidth = 2.dp
                            )
                        }

                        Text(
                            if (isSubmittingOrder) {
                                "Enviando pedido..."
                            } else {
                                "Enviar pedido al taller"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
