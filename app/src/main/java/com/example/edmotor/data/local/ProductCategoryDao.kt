package com.example.edmotor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductCategoryDao {

    @Query("SELECT * FROM product_categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProductCategoryEntity>>

    @Query("SELECT * FROM product_categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ProductCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(category: ProductCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(categories: List<ProductCategoryEntity>): List<Long>
}
