
package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    // ==========================================
    // PEDIDOS DEL CLIENTE
    // ==========================================

    @Transaction
    @Query("""
        SELECT * FROM orders
        WHERE clientUserId = :clientUserId
        ORDER BY createdAtMillis DESC
    """)
    fun observeOrdersForClient(
        clientUserId: Int
    ): Flow<List<OrderWithItems>>

    // ==========================================
    // TODOS LOS PEDIDOS DEL TALLER
    // ==========================================

    @Transaction
    @Query("""
        SELECT * FROM orders
        ORDER BY createdAtMillis DESC
    """)
    fun observeAllOrders(): Flow<List<OrderWithItems>>

    // ==========================================
    // CONSULTAR UN PEDIDO
    // ==========================================

    @Transaction
    @Query("""
        SELECT * FROM orders
        WHERE id = :orderId
        LIMIT 1
    """)
    suspend fun findWithItems(
        orderId: Int
    ): OrderWithItems?

    // ==========================================
    // REGISTRAR PEDIDO
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(
        order: OrderEntity
    ): Long

    // ==========================================
    // REGISTRAR PRODUCTOS DEL PEDIDO
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(
        items: List<OrderItemEntity>
    )

    // ==========================================
    // ACTUALIZAR ESTADO DEL PEDIDO
    // ==========================================

    @Update
    suspend fun updateOrder(
        order: OrderEntity
    ): Int
}
