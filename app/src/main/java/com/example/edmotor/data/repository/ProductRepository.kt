package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.categoryList
import com.example.edmotor.data.productList
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.ProductCategoryDao
import com.example.edmotor.data.local.ProductCategoryEntity
import com.example.edmotor.data.local.ProductDao
import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.model.Category
import com.example.edmotor.model.UserRole
import com.example.edmotor.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface ProductOperationResult {
    data class Success(
        val productId: Int? = null,
        val message: String
    ) : ProductOperationResult

    data class Error(
        val message: String
    ) : ProductOperationResult
}

class ProductRepository(
    private val database: EdMotorDatabase,
    private val productDao: ProductDao,
    private val categoryDao: ProductCategoryDao,
    private val sessionManager: SessionManager
) {

    private fun hasWorkshopPermission(requestedRole: UserRole): Boolean {
        val currentUser = sessionManager.currentUser.value
        return requestedRole == UserRole.WORKSHOP &&
                currentUser?.role == UserRole.WORKSHOP.name
    }

    private fun denied() = ProductOperationResult.Error(
        "No tienes permisos para administrar el inventario."
    )

    private fun categoryEntity(name: String): ProductCategoryEntity {
        val known = categoryList.firstOrNull {
            it.nombre.equals(name.trim(), ignoreCase = true)
        }
        return ProductCategoryEntity(
            name = name.trim(),
            emoji = known?.emoji ?: "🔧"
        )
    }

    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities ->
            entities.map { Category(emoji = it.emoji, nombre = it.name) }
        }

    suspend fun initializeCatalog(): Result<Int> {
        return try {
            val inserted = database.withTransaction {
                val defaultCategories = categoryList.map {
                    ProductCategoryEntity(name = it.nombre, emoji = it.emoji)
                }
                categoryDao.insertAllIgnore(defaultCategories)

                productDao.getDistinctCategories().forEach { name ->
                    if (name.isNotBlank()) categoryDao.insertIgnore(categoryEntity(name))
                }

                if (productDao.countProducts() > 0) {
                    return@withTransaction 0
                }

                val products = productList.map { product ->
                    ProductEntity(
                        id = product.id,
                        name = product.name,
                        description = product.description,
                        category = product.category,
                        priceCop = parsePrice(product.price),
                        promotionPriceCop = null,
                        imageRes = product.imageRes,
                        contentDescription = product.contentDescription,
                        rating = product.rating,
                        stock = 10,
                        isNew = product.badge.equals("Nuevo", ignoreCase = true),
                        isOnPromotion = false,
                        isActive = true
                    )
                }

                val insertedIds = productDao.insertIgnore(products)
                insertedIds.count { it != -1L }
            }
            Result.success(inserted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePrice(value: String): Long {
        return value.filter { it.isDigit() }.toLongOrNull() ?: 0L
    }

    fun observeCatalog(): Flow<List<ProductEntity>> = productDao.observeActiveProducts()
    fun observeAllProducts(): Flow<List<ProductEntity>> = productDao.observeAllProducts()
    fun observeProduct(id: Int): Flow<ProductEntity?> = productDao.observeProduct(id)
    fun observeByCategory(category: String): Flow<List<ProductEntity>> =
        productDao.observeByCategory(category.trim())
    fun searchProducts(query: String): Flow<List<ProductEntity>> =
        productDao.searchProducts(query.trim())
    fun observeNewProducts(): Flow<List<ProductEntity>> = productDao.observeNewProducts()
    fun observePromotionalProducts(): Flow<List<ProductEntity>> =
        productDao.observePromotionalProducts()
    fun observeOutOfStockProducts(): Flow<List<ProductEntity>> =
        productDao.observeOutOfStockProducts()

    suspend fun getAvailableStock(productId: Int): Int? {
        if (productId <= 0) return null
        return productDao.getAvailableStock(productId)
    }

    fun observeProductCount(): Flow<Int> = productDao.observeProductCount()
    fun observeOutOfStockCount(): Flow<Int> = productDao.observeOutOfStockCount()
    fun observeTotalUnits(): Flow<Int> = productDao.observeTotalUnits()

    suspend fun createCategory(
        role: UserRole,
        name: String
    ): ProductOperationResult {
        if (!hasWorkshopPermission(role)) return denied()

        val cleanName = name.trim()
        if (cleanName.length < 2) {
            return ProductOperationResult.Error(
                "El nombre de la categoría debe tener al menos 2 caracteres."
            )
        }

        return try {
            database.withTransaction {
                if (categoryDao.findByName(cleanName) != null) {
                    return@withTransaction ProductOperationResult.Error(
                        "Esa categoría ya existe."
                    )
                }

                val inserted = categoryDao.insertIgnore(categoryEntity(cleanName))
                if (inserted == -1L) {
                    ProductOperationResult.Error("Esa categoría ya existe.")
                } else {
                    ProductOperationResult.Success(
                        message = "Categoría creada correctamente."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProductOperationResult.Error(
                e.message ?: "No fue posible crear la categoría."
            )
        }
    }

    suspend fun createProduct(
        role: UserRole,
        name: String,
        description: String,
        category: String,
        priceCop: Long,
        imageRes: Int,
        contentDescription: String,
        stock: Int,
        isNew: Boolean,
        isOnPromotion: Boolean,
        promotionPriceCop: Long?,
        imageUri: String? = null
    ): ProductOperationResult {
        if (!hasWorkshopPermission(role)) return denied()

        val cleanName = name.trim()
        val cleanDescription = description.trim()
        val cleanCategory = category.trim()

        val validation = validateProduct(
            name = cleanName,
            description = cleanDescription,
            category = cleanCategory,
            priceCop = priceCop,
            imageRes = imageRes,
            imageUri = imageUri,
            stock = stock,
            isOnPromotion = isOnPromotion,
            promotionPriceCop = promotionPriceCop
        )
        if (validation != null) return ProductOperationResult.Error(validation)

        return try {
            val now = System.currentTimeMillis()
            val product = ProductEntity(
                name = cleanName,
                description = cleanDescription,
                category = cleanCategory,
                priceCop = priceCop,
                promotionPriceCop = if (isOnPromotion) promotionPriceCop else null,
                imageRes = imageRes,
                contentDescription = contentDescription.trim().ifBlank { cleanName },
                imageUri = imageUri,
                rating = 0.0,
                stock = stock,
                isNew = isNew,
                isOnPromotion = isOnPromotion,
                isActive = true,
                createdAtMillis = now,
                updatedAtMillis = now
            )

            val id = database.withTransaction {
                categoryDao.insertIgnore(categoryEntity(cleanCategory))
                productDao.insert(product).toInt()
            }

            ProductOperationResult.Success(
                productId = id,
                message = "Repuesto creado con éxito."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProductOperationResult.Error(
                e.message ?: "No fue posible registrar el repuesto."
            )
        }
    }

    suspend fun updateProduct(
        role: UserRole,
        product: ProductEntity
    ): ProductOperationResult {
        if (!hasWorkshopPermission(role)) return denied()
        if (product.id <= 0) {
            return ProductOperationResult.Error("El identificador del producto no es válido.")
        }

        val validation = validateProduct(
            name = product.name,
            description = product.description,
            category = product.category,
            priceCop = product.priceCop,
            imageRes = product.imageRes,
            imageUri = product.imageUri,
            stock = product.stock,
            isOnPromotion = product.isOnPromotion,
            promotionPriceCop = product.promotionPriceCop
        )
        if (validation != null) return ProductOperationResult.Error(validation)

        return try {
            database.withTransaction {
                val existing = productDao.findById(product.id)
                    ?: return@withTransaction ProductOperationResult.Error(
                        "El producto no existe."
                    )

                val cleanName = product.name.trim()
                val updated = product.copy(
                    name = cleanName,
                    description = product.description.trim(),
                    category = product.category.trim(),
                    contentDescription = product.contentDescription.trim().ifBlank { cleanName },
                    promotionPriceCop = if (product.isOnPromotion) {
                        product.promotionPriceCop
                    } else {
                        null
                    },
                    rating = existing.rating,
                    isActive = existing.isActive,
                    createdAtMillis = existing.createdAtMillis,
                    updatedAtMillis = System.currentTimeMillis()
                )

                categoryDao.insertIgnore(categoryEntity(updated.category))
                val rows = productDao.update(updated)
                if (rows > 0) {
                    ProductOperationResult.Success(
                        productId = product.id,
                        message = "Repuesto actualizado correctamente."
                    )
                } else {
                    ProductOperationResult.Error("No se pudo actualizar el repuesto.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProductOperationResult.Error(
                e.message ?: "Error al actualizar el repuesto."
            )
        }
    }

    /**
     * delta positivo = entrada de unidades; delta negativo = salida.
     * El cálculo y la escritura ocurren en una sola transacción.
     */
    suspend fun adjustStock(
        role: UserRole,
        productId: Int,
        delta: Int
    ): ProductOperationResult {
        if (!hasWorkshopPermission(role)) return denied()
        if (productId <= 0) {
            return ProductOperationResult.Error("El producto seleccionado no es válido.")
        }
        if (delta == 0) {
            return ProductOperationResult.Error("Indica una cantidad mayor que cero.")
        }

        return try {
            database.withTransaction {
                val product = productDao.findById(productId)
                    ?: return@withTransaction ProductOperationResult.Error(
                        "No se encontró el repuesto."
                    )

                if (!product.isActive) {
                    return@withTransaction ProductOperationResult.Error(
                        "Este repuesto está retirado del catálogo."
                    )
                }

                val resultingStock = product.stock.toLong() + delta.toLong()
                if (resultingStock < 0L) {
                    return@withTransaction ProductOperationResult.Error(
                        "No puedes disminuir más unidades de las disponibles."
                    )
                }
                if (resultingStock > Int.MAX_VALUE) {
                    return@withTransaction ProductOperationResult.Error(
                        "La cantidad supera el límite permitido."
                    )
                }

                val rows = productDao.updateStock(
                    productId = productId,
                    newStock = resultingStock.toInt()
                )
                if (rows == 1) {
                    val action = if (delta > 0) "aumentado" else "disminuido"
                    ProductOperationResult.Success(
                        productId = productId,
                        message = "Stock $action correctamente. Existencias: ${resultingStock.toInt()}."
                    )
                } else {
                    ProductOperationResult.Error("No fue posible actualizar el inventario.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProductOperationResult.Error(
                e.message ?: "No fue posible modificar el inventario."
            )
        }
    }

    suspend fun updateStock(
        role: UserRole,
        productId: Int,
        newStock: Int
    ): ProductOperationResult {
        if (!hasWorkshopPermission(role)) return denied()
        if (productId <= 0) {
            return ProductOperationResult.Error("El producto seleccionado no es válido.")
        }
        if (newStock < 0) {
            return ProductOperationResult.Error("Las existencias no pueden ser negativas.")
        }

        return try {
            val updated = productDao.updateStock(productId, newStock)
            if (updated > 0) {
                ProductOperationResult.Success(
                    productId = productId,
                    message = "Inventario actualizado correctamente."
                )
            } else {
                ProductOperationResult.Error("No se encontró el producto.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProductOperationResult.Error(
                e.message ?: "No fue posible actualizar el inventario."
            )
        }
    }

    suspend fun deleteProduct(
        role: UserRole,
        productId: Int
    ): ProductOperationResult {
        if (!hasWorkshopPermission(role)) return denied()
        if (productId <= 0) {
            return ProductOperationResult.Error("El producto seleccionado no es válido.")
        }

        return try {
            val updated = productDao.deactivate(productId)
            if (updated > 0) {
                ProductOperationResult.Success(
                    productId = productId,
                    message = "Repuesto eliminado del catálogo correctamente."
                )
            } else {
                ProductOperationResult.Error("No se encontró el repuesto.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProductOperationResult.Error(
                e.message ?: "No se pudo eliminar el repuesto."
            )
        }
    }

    private fun validateProduct(
        name: String,
        description: String,
        category: String,
        priceCop: Long,
        imageRes: Int,
        imageUri: String? = null,
        stock: Int,
        isOnPromotion: Boolean,
        promotionPriceCop: Long?
    ): String? {
        if (name.trim().length < 3) return "El nombre debe tener al menos 3 caracteres."
        if (description.isBlank()) return "La descripción es obligatoria."
        if (category.isBlank()) return "Selecciona una categoría."
        if (priceCop <= 0) return "El precio debe ser mayor que cero."
        if (imageRes <= 0 && imageUri.isNullOrBlank()) {
            return "Selecciona una fotografía válida."
        }
        if (stock < 0) return "Las existencias no pueden ser negativas."

        if (isOnPromotion) {
            if (promotionPriceCop == null || promotionPriceCop <= 0) {
                return "Ingresa un precio promocional válido."
            }
            if (promotionPriceCop >= priceCop) {
                return "El precio promocional debe ser inferior al precio original."
            }
        }
        return null
    }
}
