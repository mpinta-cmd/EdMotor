package com.example.edmotor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.Order
import com.example.edmotor.model.OrderItem
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.OrderWithItems
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.Vehicle
import com.example.edmotor.ui.screens.WorkshopGarageScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkshopGarageScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun opensVehicleHistoryFromTheClientCard() {
        val client = UserEntity(
            id = 8,
            username = "cliente",
            displayName = "Cliente",
            passwordHash = "",
            salt = "",
            role = UserRole.CLIENT.name
        )
        val vehicle = Vehicle(16, "Toyota", "Corolla", 2021, "1.8", ownerUserId = client.id)
        var openedVehicle: Int? = null

        compose.setContent {
            MaterialTheme {
                WorkshopGarageScreen(
                    profileName = "EdMotor",
                    vehicles = listOf(vehicle),
                    clients = listOf(client),
                    orders = emptyList(),
                    isProcessing = false,
                    message = null,
                    isError = false,
                    onVehicleClick = { openedVehicle = it },
                    onConfirm = {},
                    onReject = {},
                    onLogout = {}
                )
            }
        }

        compose.onNodeWithText("Clientes (1)").assertIsSelected()
        compose.onNodeWithText("Vehículos (1)").assertDoesNotExist()
        compose.onNodeWithText("Administrar pedidos y confirmar ventas").assertDoesNotExist()
        compose.onNodeWithText("Ver vehículos e historial de compras").performClick()
        compose.onNodeWithText("Toyota Corolla").assertIsDisplayed()
        compose.onNodeWithText("Abrir historial de mantenimiento").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(vehicle.id, openedVehicle) }
    }

    @Test
    fun acceptsAndRejectsPendingOrdersFromPurchases() {
        var order by mutableStateOf(
            Order(
                id = 21,
                clientUserId = 8,
                clientName = "Cliente",
                clientUsername = "cliente",
                clientPhone = null,
                status = OrderStatus.PENDING,
                totalCop = 20_000,
                createdAtMillis = 1,
                confirmedAtMillis = null
            )
        )
        val item = OrderItem("Filtro de aceite", "Filtros", 20_000, 1, 20_000)
        val accepted = mutableListOf<Int>()
        val rejected = mutableListOf<Int>()

        compose.setContent {
            MaterialTheme {
                WorkshopGarageScreen(
                    profileName = "EdMotor",
                    vehicles = emptyList(),
                    clients = emptyList(),
                    orders = listOf(OrderWithItems(order, listOf(item))),
                    isProcessing = false,
                    message = null,
                    isError = false,
                    onVehicleClick = {},
                    onConfirm = {
                        accepted += it
                        order = order.copy(status = OrderStatus.CONFIRMED)
                    },
                    onReject = {
                        rejected += it
                        order = order.copy(status = OrderStatus.REJECTED)
                    },
                    onLogout = {}
                )
            }
        }

        compose.onNodeWithText("Compras (1)").performClick().assertIsSelected()
        compose.onNodeWithText("Aceptar pedido").performScrollTo().performClick()
        compose.onNodeWithText("CONFIRMADO").assertIsDisplayed()
        compose.onNodeWithText("Aceptar pedido").assertDoesNotExist()
        compose.onNodeWithText("Rechazar pedido").assertDoesNotExist()

        compose.runOnIdle { order = order.copy(id = 22, status = OrderStatus.PENDING) }
        compose.onNodeWithText("Rechazar pedido").performScrollTo().performClick()
        compose.onNodeWithText("RECHAZADO").assertIsDisplayed()
        compose.onNodeWithText("Aceptar pedido").assertDoesNotExist()
        compose.onNodeWithText("Rechazar pedido").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(21), accepted)
            assertEquals(listOf(22), rejected)
        }
    }
}
