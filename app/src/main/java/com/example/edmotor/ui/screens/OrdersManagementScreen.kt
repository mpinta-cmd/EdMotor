
package com.example.edmotor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.OrderWithItems
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrdersManagementScreen(
    orders: List<OrderWithItems>,
    isProcessing: Boolean,
    message: String?,
    isError: Boolean,
    onConfirm: (Int) -> Unit,
    onReject: (Int) -> Unit,
    onBack: () -> Unit
) {

    val currencyFormat = NumberFormat.getNumberInstance(
        Locale("es", "CO")
    )

    val dateFormat = SimpleDateFormat(
        "dd/MM/yyyy HH:mm",
        Locale("es", "CO")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // ======================================
        // ENCABEZADO
        // ======================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    "PEDIDOS EDMOTOR",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Administración de ventas",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            OutlinedButton(
                onClick = onBack
            ) {
                Text("Volver")
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Confirma los pedidos de este dispositivo para actualizar el inventario.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(16.dp))

        // ======================================
        // MENSAJES
        // ======================================

        if (message != null) {

            Text(
                text = message,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // ======================================
        // LISTA DE PEDIDOS
        // ======================================

        if (orders.isEmpty()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    "Todavía no hay pedidos registrados.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Aquí aparecen los pedidos guardados en este dispositivo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        } else {

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                items(
                    items = orders,
                    key = { it.order.id }
                ) { orderWithItems ->

                    val order = orderWithItems.order

                    val isPending =
                        order.status == OrderStatus.PENDING

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 2.dp
                        )
                    ) {

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {

                            // NÚMERO Y ESTADO

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {

                                Text(
                                    "Pedido #${order.id}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Text(
                                    text = when (order.status) {
                                        OrderStatus.PENDING -> "PENDIENTE"
                                        OrderStatus.CONFIRMED -> "CONFIRMADO"
                                        OrderStatus.REJECTED -> "RECHAZADO"
                                        else -> order.status
                                    },
                                    color = when (order.status) {
                                        OrderStatus.PENDING ->
                                            MaterialTheme.colorScheme.primary

                                        OrderStatus.CONFIRMED ->
                                            MaterialTheme.colorScheme.tertiary

                                        else ->
                                            MaterialTheme.colorScheme.error
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Text(
                                "Fecha: ${dateFormat.format(Date(order.createdAtMillis))}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            // DATOS DEL CLIENTE

                            Text(
                                "CLIENTE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                order.clientName,
                                fontWeight = FontWeight.Bold
                            )

                            // PRODUCTOS

                            Text(
                                "PRODUCTOS SOLICITADOS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            orderWithItems.items.forEach { item ->

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {

                                    Text(
                                        item.productName,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Text(
                                        "${item.quantity} unidades × \$${currencyFormat.format(item.unitPriceCop)}"
                                    )

                                    Text(
                                        "Subtotal: \$${currencyFormat.format(item.subtotalCop)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            // TOTAL

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Text(
                                    "TOTAL DEL PEDIDO",
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "\$${currencyFormat.format(order.totalCop)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            // ACCIONES DEL DUEÑO

                            if (isPending) {

                                Spacer(Modifier.height(6.dp))

                                Button(
                                    onClick = {
                                        onConfirm(order.id)
                                    },
                                    enabled = !isProcessing,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {

                                    if (isProcessing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(end = 8.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }

                                    Text("Confirmar venta")
                                }

                                OutlinedButton(
                                    onClick = {
                                        onReject(order.id)
                                    },
                                    enabled = !isProcessing,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Rechazar pedido")
                                }

                            } else if (
                                order.status == OrderStatus.CONFIRMED
                            ) {

                                Text(
                                    "Venta confirmada. Inventario descontado.",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold
                                )

                            } else if (order.status == OrderStatus.REJECTED) {

                                Text(
                                    "Pedido rechazado. El inventario no fue modificado.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
