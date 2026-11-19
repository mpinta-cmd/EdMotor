package com.example.edmotor.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.edmotor.data.local.CartItemEntity
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.OrderStatus
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryOrderTest {
    @Test
    fun failedAndConcurrentConfirmationsKeepStockAndOrderSnapshotsConsistent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, EdMotorDatabase::class.java).build()
        val sessionFile = File.createTempFile("inventory-order-", ".preferences_pb", context.cacheDir)
        sessionFile.delete()
        val sessionJob = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(sessionJob + Dispatchers.IO)) { sessionFile }

        try {
            val session = SessionManager(store)
            val products = database.productDao()
            val cartRows = database.cartDao()
            val orderRows = database.orderDao()
            val catalog = ProductRepository(database, products, database.productCategoryDao(), session)
            val cart = CartRepository(database, cartRows, products)
            val orders = OrderRepository(database, orderRows, products, cartRows, session)
            val client = insertUser(database, "cliente", UserRole.CLIENT)
            val owner = insertUser(database, "edmotor", UserRole.WORKSHOP)
            val first = insertProduct(database, "Filtro inicial", 10, 2)
            val second = insertProduct(database, "Bomba inicial", 20, 1)

            session.selectProfile(client)
            val increases = List(3) { async { cart.increaseQuantity(client.id, first.id) } }.awaitAll()
            assertEquals(2, increases.count { it is CartOperationResult.Success })
            assertEquals(2, cartRows.findByProductId(client.id, first.id)?.quantity)
            assertTrue(cart.increaseQuantity(owner.id, first.id) is CartOperationResult.Error)
            assertTrue(cart.increaseQuantity(client.id, second.id) is CartOperationResult.Success)

            cartRows.upsert(CartItemEntity(client.id, 0, -4))
            assertTrue(orders.createPendingOrder(client) is OrderOperationResult.Error)
            assertEquals(0, orderRows.getOrdersCount())
            assertTrue(cart.removeProduct(client.id, 0) is CartOperationResult.Success)
            assertTrue(orders.createPendingOrder(client) is OrderOperationResult.Success)
            assertTrue(cartRows.getItemsForUser(client.id).isEmpty())
            val orderId = 1L

            session.selectProfile(owner)
            assertTrue(catalog.createCategory(UserRole.WORKSHOP, "Refrigeración") is ProductOperationResult.Success)
            assertTrue(catalog.createCategory(UserRole.WORKSHOP, "REFRIGERACIO\u0301N") is ProductOperationResult.Error)
            assertTrue(catalog.updateProduct(
                UserRole.WORKSHOP,
                first.copy(name = "Filtro actualizado", priceCop = 40, stock = 90, category = "REFRIGERACIO\u0301N")
            ) is ProductOperationResult.Success)
            assertEquals("Refrigeración", products.findById(first.id)?.category)
            assertEquals(2, products.findById(first.id)?.stock)
            assertTrue(catalog.adjustStock(UserRole.WORKSHOP, second.id, -1) is ProductOperationResult.Success)
            assertTrue(orders.confirmOrder(owner, orderId.toInt()) is OrderOperationResult.Error)
            assertEquals(2, products.findById(first.id)?.stock)
            assertEquals(OrderStatus.PENDING, orderRows.getOrderById(orderId)?.status)

            assertTrue(catalog.adjustStock(UserRole.WORKSHOP, second.id, 1) is ProductOperationResult.Success)
            val confirmations = List(2) { async { orders.confirmOrder(owner, orderId.toInt()) } }.awaitAll()
            assertEquals(1, confirmations.count { it is OrderOperationResult.Success })
            assertEquals(0, products.findById(first.id)?.stock)
            assertEquals(0, products.findById(second.id)?.stock)
            assertEquals(OrderStatus.CONFIRMED, orderRows.getOrderById(orderId)?.status)
            assertEquals(40L, orderRows.getOrderById(orderId)?.totalCop)
            val originalItem = orderRows.getOrderItems(orderId).first()
            assertEquals("Filtro inicial", originalItem.productName)
            assertEquals(10L, originalItem.unitPriceCop)
            assertTrue(orders.rejectOrder(owner, orderId.toInt()) is OrderOperationResult.Error)
        } finally {
            sessionJob.cancelAndJoin()
            database.close()
            sessionFile.delete()
        }
    }

    private suspend fun insertUser(database: EdMotorDatabase, username: String, userRole: UserRole): UserEntity {
        val row = UserEntity(
            username = username,
            displayName = username,
            passwordHash = "fixture",
            salt = "fixture",
            role = userRole.name,
            remoteAuthId = "fixture:$username"
        )
        return row.copy(id = database.userDao().insert(row).toInt())
    }

    private suspend fun insertProduct(database: EdMotorDatabase, name: String, price: Long, stock: Int): ProductEntity {
        val row = ProductEntity(
            name = name,
            description = "Descripción",
            category = "Refrigeración",
            priceCop = price,
            imageRes = 1,
            contentDescription = name,
            stock = stock
        )
        return row.copy(id = database.productDao().insert(row).toInt())
    }
}
