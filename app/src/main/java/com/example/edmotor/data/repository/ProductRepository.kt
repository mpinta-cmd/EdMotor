package com.example.edmotor.data.repository

import androidx.room.withTransaction
import com.example.edmotor.data.categoryList
import com.example.edmotor.data.productList
import com.example.edmotor.data.local.EdMotorDatabase
import com.example.edmotor.data.local.ProductCategoryDao
import com.example.edmotor.data.local.ProductCategoryEntity
import com.example.edmotor.data.local.ProductDao
import com.example.edmotor.data.local.ProductEntity
import com.example.edmotor.data.local.UserEntity
import com.example.edmotor.model.Category
import com.example.edmotor.model.UserRole
import com.example.edmotor.model.categoryKey
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

    private fun workshopFor(requestedRole: UserRole): UserEntity? = sessionManager.currentUser.value?.takeIf {
        requestedRole == UserRole.WORKSHOP && it.id > 0 && it.role == UserRole.WORKSHOP.name
    }

    private fun denied() = ProductOperationResult.Error(
        "No tienes permisos para administrar el inventario."
    )

    private fun categoryEntity(name: String): ProductCategoryEntity {
        val known = categoryList.firstOrNull {
            categoryKey(it.nombre) == categoryKey(name)
        }
        return ProductCategoryEntity(
            name = name.trim(),
            emoji = known?.emoji ?: "🔧"
        )
    }

    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities ->
            entities.distinctBy { categoryKey(it.name) }
                .map { Category(emoji = it.emoji, nombre = it.name) }
        }

    suspend fun initializeCatalog(): Result<Int> {
        return try {
            val inserted = database.withTransaction {
                val knownCategories = categoryDao.getAll().mapTo(mutableSetOf()) { categoryKey(it.name) }
                val defaults = categoryList.map {
                    ProductCategoryEntity(name = it.nombre, emoji = it.emoji)
                }
                val legacyCategories = productDao.getDistinctCategories().map(::categoryEntity)
                categoryDao.insertAllIgnore((defaults + legacyCategories).filter { knownCategories.add(categoryKey(it.name)) })

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
                        stock = if (product.badge.equals("Agotado", ignoreCase = true)) 0 else 10,
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
    fun observeByCategory(category: String): Flow<List<ProductEntity>> {
        val key = categoryKey(category)
        return productDao.observeActiveProducts().map { products -> products.filter { categoryKey(it.category) == key } }
    }
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
    fun observeTotalUnits(): Flow<Long> = productDao.observeTotalUnits()

    suspend fun createCategory(
        role: UserRole,
        name: String
    ): ProductOperationResult {
        val workshop = workshopFor(role) ?: return denied()

        val cleanName = name.trim()
        if (cleanName.length < 2) {
            return ProductOperationResult.Error(
                "El nombre de la categoría debe tener al menos 2 caracteres."
            )
        }

        if (cleanName.any(Char::isISOControl)) {
            return ProductOperationResult.Error("El nombre de la categoría contiene caracteres no válidos.")
        }

        return modifyInventory(workshop, "No fue posible crear la categoría.") {
            if (findCategory(cleanName) != null || categoryDao.insertIgnore(categoryEntity(cleanName)) == -1L) {
                ProductOperationResult.Error("Esa categoría ya existe.")
            } else {
                ProductOperationResult.Success(message = "Categoría creada correctamente.")
            }
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
        val workshop = workshopFor(role) ?: return denied()

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

        return modifyInventory(workshop, "No fue posible registrar el repuesto.") {
            val now = System.currentTimeMillis()
            val product = ProductEntity(
                name = cleanName,
                description = cleanDescription,
                category = resolveCategory(cleanCategory),
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

            ProductOperationResult.Success(
                productId = Math.toIntExact(productDao.insert(product)),
                message = "Repuesto creado con éxito."
            )
        }
    }

    suspend fun updateProduct(
        role: UserRole,
        product: ProductEntity
    ): ProductOperationResult {
        val workshop = workshopFor(role) ?: return denied()
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
            isOnPromotion = product.isOnPromotion,
            promotionPriceCop = product.promotionPriceCop
        )
        if (validation != null) return ProductOperationResult.Error(validation)

        return modifyInventory(workshop, "No fue posible actualizar el repuesto.") {
            val existing = productDao.findById(product.id)?.takeIf { it.isActive }
                ?: return@modifyInventory ProductOperationResult.Error("El repuesto no está activo.")

            val cleanName = product.name.trim()
            val updated = product.copy(
                name = cleanName,
                description = product.description.trim(),
                category = resolveCategory(product.category.trim()),
                contentDescription = product.contentDescription.trim().ifBlank { cleanName },
                promotionPriceCop = product.promotionPriceCop.takeIf { product.isOnPromotion },
                rating = existing.rating,
                stock = existing.stock,
                isActive = existing.isActive,
                createdAtMillis = existing.createdAtMillis,
                updatedAtMillis = System.currentTimeMillis()
            )
            check(productDao.update(updated) == 1)
            ProductOperationResult.Success(product.id, "Repuesto actualizado correctamente.")
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
        val workshop = workshopFor(role) ?: return denied()
        if (productId <= 0) {
            return ProductOperationResult.Error("El producto seleccionado no es válido.")
        }
        if (delta == 0) {
            return ProductOperationResult.Error("Indica una cantidad mayor que cero.")
        }

        return modifyInventory(workshop, "No fue posible modificar el inventario.") {
            val availableStock = productDao.getAvailableStock(productId)
                ?: return@modifyInventory ProductOperationResult.Error("El repuesto no está activo.")
            val resultingStock = availableStock.toLong() + delta.toLong()
            if (resultingStock < 0L) {
                return@modifyInventory ProductOperationResult.Error("No puedes disminuir más unidades de las disponibles.")
            }
            if (resultingStock > Int.MAX_VALUE) {
                return@modifyInventory ProductOperationResult.Error("La cantidad supera el límite permitido.")
            }

            check(productDao.updateStock(productId, resultingStock.toInt()) == 1)
            val action = if (delta > 0) "aumentado" else "disminuido"
            ProductOperationResult.Success(productId, "Stock $action correctamente. Existencias: $resultingStock.")
        }
    }

    suspend fun updateStock(
        role: UserRole,
        productId: Int,
        newStock: Int
    ): ProductOperationResult {
        val workshop = workshopFor(role) ?: return denied()
        if (productId <= 0) {
            return ProductOperationResult.Error("El producto seleccionado no es válido.")
        }
        if (newStock < 0) {
            return ProductOperationResult.Error("Las existencias no pueden ser negativas.")
        }

        return modifyInventory(workshop, "No fue posible actualizar el inventario.") {
            if (productDao.getAvailableStock(productId) == null) {
                return@modifyInventory ProductOperationResult.Error("El repuesto no está activo.")
            }
            check(productDao.updateStock(productId, newStock) == 1)
            ProductOperationResult.Success(productId, "Inventario actualizado correctamente.")
        }
    }

    suspend fun deleteProduct(
        role: UserRole,
        productId: Int
    ): ProductOperationResult {
        val workshop = workshopFor(role) ?: return denied()
        if (productId <= 0) {
            return ProductOperationResult.Error("El producto seleccionado no es válido.")
        }

        return modifyInventory(workshop, "No se pudo eliminar el repuesto.") {
            val updated = productDao.deactivate(productId)
            if (updated > 0) {
                ProductOperationResult.Success(
                    productId = productId,
                    message = "Repuesto eliminado del catálogo correctamente."
                )
            } else {
                ProductOperationResult.Error("El repuesto no está activo.")
            }
        }
    }

    private suspend fun findCategory(name: String): ProductCategoryEntity? {
        val key = categoryKey(name)
        return categoryDao.getAll().firstOrNull { categoryKey(it.name) == key }
    }

    private suspend fun resolveCategory(name: String): String =
        findCategory(name)?.name ?: name.also { check(categoryDao.insertIgnore(categoryEntity(it)) != -1L) }

    private fun matchesWorkshop(candidate: UserEntity?, workshop: UserEntity): Boolean =
        candidate?.id == workshop.id && candidate.remoteAuthId == workshop.remoteAuthId && candidate.role == UserRole.WORKSHOP.name

    private suspend fun modifyInventory(
        workshop: UserEntity,
        fallbackMessage: String,
        operation: suspend () -> ProductOperationResult
    ): ProductOperationResult = try {
        database.withTransaction {
            if (!matchesWorkshop(sessionManager.currentUser.value, workshop) ||
                !matchesWorkshop(database.userDao().findById(workshop.id), workshop)
            ) return@withTransaction denied()
            val result = operation()
            check(matchesWorkshop(sessionManager.currentUser.value, workshop))
            result
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ProductOperationResult.Error(fallbackMessage)
    }

    private fun validateProduct(
        name: String,
        description: String,
        category: String,
        priceCop: Long,
        imageRes: Int,
        imageUri: String? = null,
        stock: Int? = null,
        isOnPromotion: Boolean,
        promotionPriceCop: Long?
    ): String? {
        if (name.trim().length < 3) return "El nombre debe tener al menos 3 caracteres."
        if (description.isBlank()) return "La descripción es obligatoria."
        if (category.isBlank() || category.any(Char::isISOControl)) return "Selecciona una categoría válida."
        if (priceCop <= 0) return "El precio debe ser mayor que cero."
        if (imageRes <= 0 && imageUri.isNullOrBlank()) {
            return "Selecciona una fotografía válida."
        }
        if (stock != null && stock < 0) return "Las existencias no pueden ser negativas."

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
