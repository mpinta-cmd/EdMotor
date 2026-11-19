
package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    // ==========================================
    // REGISTRAR PEDIDOS
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(order: OrderEntity): Long

    // ==========================================
    // REGISTRAR PRODUCTOS DE UN PEDIDO
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrderItem(item: OrderItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrderItems(
        items: List<OrderItemEntity>
    ): List<Long>

    // ==========================================
    // CONSULTAR TODOS LOS PEDIDOS
    // ==========================================

    @Query("""
        SELECT * FROM orders
        ORDER BY createdAtMillis DESC
    """)
    fun observeAllOrders(): Flow<List<OrderEntity>>

    // ==========================================
    // CONSULTAR PEDIDOS POR CLIENTE
    // ==========================================

    @Query("""
        SELECT * FROM orders
        WHERE clientUserId = :clientUserId
        ORDER BY createdAtMillis DESC
    """)
    fun observeOrdersByClient(
        clientUserId: Long
    ): Flow<List<OrderEntity>>

    @Transaction
    @Query("SELECT * FROM orders ORDER BY createdAtMillis DESC, id DESC")
    fun observeAllWithItems(): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE clientUserId = :clientUserId ORDER BY createdAtMillis DESC, id DESC")
    fun observeByClientWithItems(clientUserId: Int): Flow<List<OrderWithItems>>

    // ==========================================
    // CONSULTAR PEDIDOS POR ESTADO
    // ==========================================

    @Query("""
        SELECT * FROM orders
        WHERE status = :status
        ORDER BY createdAtMillis DESC
    """)
    fun observeOrdersByStatus(
        status: String
    ): Flow<List<OrderEntity>>

    // ==========================================
    // CONSULTAR UN PEDIDO
    // ==========================================

    @Query("""
        SELECT * FROM orders
        WHERE id = :orderId
        LIMIT 1
    """)
    suspend fun getOrderById(
        orderId: Long
    ): OrderEntity?

    // ==========================================
    // CONSULTAR DETALLES DEL PEDIDO
    // ==========================================

    @Query("""
        SELECT * FROM order_items
        WHERE orderId = :orderId
        ORDER BY id ASC
    """)
    fun observeOrderItems(
        orderId: Long
    ): Flow<List<OrderItemEntity>>

    @Query("""
        SELECT * FROM order_items
        WHERE orderId = :orderId
        ORDER BY id ASC
    """)
    suspend fun getOrderItems(
        orderId: Long
    ): List<OrderItemEntity>

    // ==========================================
    // ACTUALIZAR PEDIDO
    // ==========================================

    @Update
    suspend fun updateOrder(
        order: OrderEntity
    ): Int

    // ==========================================
    // ACTUALIZAR ESTADO
    // ==========================================

    @Query("""
        UPDATE orders
        SET status = :status,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :orderId
          AND status = 'PENDING'
    """)
    suspend fun updateOrderStatus(
        orderId: Long,
        status: String,
        updatedAtMillis: Long
    ): Int

    // ==========================================
    // CONFIRMAR PEDIDO
    // ==========================================

    @Query("""
        UPDATE orders
        SET status = :status,
            updatedAtMillis = :updatedAtMillis,
            confirmedByUserId = :ownerUserId,
            confirmedAtMillis = :confirmedAtMillis
        WHERE id = :orderId
          AND status = 'PENDING'
    """)
    suspend fun confirmOrder(
        orderId: Long,
        status: String,
        ownerUserId: Long,
        confirmedAtMillis: Long,
        updatedAtMillis: Long
    ): Int

    // ==========================================
    // ELIMINAR DETALLES
    // ==========================================

    @Query("""
        DELETE FROM order_items
        WHERE orderId = :orderId
    """)
    suspend fun deleteOrderItems(
        orderId: Long
    ): Int

    // ==========================================
    // ELIMINAR PEDIDO
    // ==========================================

    @Delete
    suspend fun deleteOrder(
        order: OrderEntity
    ): Int

    @Query("""
        DELETE FROM orders
        WHERE id = :orderId
    """)
    suspend fun deleteOrderById(
        orderId: Long
    ): Int

    // ==========================================
    // CONTAR PEDIDOS
    // ==========================================

    @Query("SELECT COUNT(*) FROM orders")
    suspend fun getOrdersCount(): Int

    @Query("""
        SELECT COUNT(*) FROM orders
        WHERE clientUserId = :clientUserId
    """)
    suspend fun getOrdersCountByClient(
        clientUserId: Long
    ): Int
}
