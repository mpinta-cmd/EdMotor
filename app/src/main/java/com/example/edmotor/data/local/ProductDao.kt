package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    // ==========================================
    // CONSULTAR PRODUCTOS
    // ==========================================

    @Query("""
        SELECT * FROM products
        WHERE isActive = 1
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun observeActiveProducts(): Flow<List<ProductEntity>>

    @Query("""
        SELECT * FROM products
        WHERE isActive = 1
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun observeAllProducts(): Flow<List<ProductEntity>>

    @Query("""
        SELECT DISTINCT category FROM products
        WHERE TRIM(category) <> ''
        ORDER BY category COLLATE NOCASE ASC
    """)
    suspend fun getDistinctCategories(): List<String>

    @Query("""
        SELECT * FROM products
        WHERE id = :productId
        LIMIT 1
    """)
    fun observeProduct(productId: Int): Flow<ProductEntity?>

    @Query("""
        SELECT * FROM products
        WHERE id = :productId
        LIMIT 1
    """)
    suspend fun findById(productId: Int): ProductEntity?

    @Query("""
        SELECT * FROM products
        WHERE name = :name COLLATE NOCASE
          AND category = :category COLLATE NOCASE
          AND isActive = 1
        ORDER BY id ASC
        LIMIT 1
    """)
    suspend fun findByNameAndCategory(
        name: String,
        category: String
    ): ProductEntity?

    // ==========================================
    // BUSCAR PRODUCTOS
    // ==========================================

    @Query("""
        SELECT * FROM products
        WHERE isActive = 1
        AND (
            name LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%'
            OR category LIKE '%' || :query || '%'
        )
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun searchProducts(
        query: String
    ): Flow<List<ProductEntity>>

    // ==========================================
    // FILTRAR POR CATEGORÍA
    // ==========================================

    @Query("""
        SELECT * FROM products
        WHERE isActive = 1
        AND category = :category
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun observeByCategory(
        category: String
    ): Flow<List<ProductEntity>>

    // ==========================================
    // PRODUCTOS NUEVOS
    // ==========================================

    @Query("""
        SELECT * FROM products
        WHERE isActive = 1
        AND isNew = 1
        ORDER BY createdAtMillis DESC
    """)
    fun observeNewProducts(): Flow<List<ProductEntity>>

    // ==========================================
    // PRODUCTOS EN PROMOCIÓN
    // ==========================================

    @Query("""
        SELECT * FROM products
        WHERE isActive = 1
        AND isOnPromotion = 1
        AND promotionPriceCop IS NOT NULL
        AND promotionPriceCop > 0
        AND promotionPriceCop < priceCop
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun observePromotionalProducts(): Flow<List<ProductEntity>>

    // ==========================================
    // PRODUCTOS AGOTADOS
    // ==========================================

    @Query("""
        SELECT * FROM products
        WHERE isActive = 1
        AND stock <= 0
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun observeOutOfStockProducts(): Flow<List<ProductEntity>>

    // ==========================================
    // REGISTRAR PRODUCTO
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(
        product: ProductEntity
    ): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(
        products: List<ProductEntity>
    ): List<Long>

    // ==========================================
    // EDITAR PRODUCTO
    // ==========================================

    @Update
    suspend fun update(
        product: ProductEntity
    ): Int

    // ==========================================
    // ELIMINAR PRODUCTO
    // ==========================================

    @Delete
    suspend fun delete(
        product: ProductEntity
    ): Int

    @Query("""
        UPDATE products
        SET isActive = 0,
            updatedAtMillis = :updatedAt
        WHERE id = :productId
    """)
    suspend fun deactivate(
        productId: Int,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    // ==========================================
    // CONTROL DEL INVENTARIO
    // ==========================================

    @Query("""
        UPDATE products
        SET stock = :newStock,
            updatedAtMillis = :updatedAt
        WHERE id = :productId
        AND :newStock >= 0
    """)
    suspend fun updateStock(
        productId: Int,
        newStock: Int,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    // ==========================================
    // CONTROL DE CANTIDADES DISPONIBLES
    // ==========================================

    @Query("""
        SELECT stock FROM products
        WHERE id = :productId
        AND isActive = 1
        LIMIT 1
    """)
    suspend fun getAvailableStock(
        productId: Int
    ): Int?

    // ==========================================
    // ESTADÍSTICAS DE INVENTARIO
    // ==========================================

    @Query("""
        SELECT COUNT(*) FROM products
        WHERE isActive = 1
    """)
    fun observeProductCount(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM products
        WHERE isActive = 1
        AND stock <= 0
    """)
    fun observeOutOfStockCount(): Flow<Int>

    @Query("""
        SELECT COALESCE(SUM(stock), 0)
        FROM products
        WHERE isActive = 1
    """)
    fun observeTotalUnits(): Flow<Int>

    // ==========================================
    // COMPROBAR CARGA INICIAL
    // ==========================================

    @Query("""
        SELECT COUNT(*) FROM products
    """)
    suspend fun countProducts(): Int
}