package com.example.edmotor.online

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

data class OnlineUser(val id: Long, val username: String, val displayName: String, val role: String)

data class OnlineMessage(val text: String, val isError: Boolean)

enum class OnlineResource { CATALOG, ORDERS, VEHICLES, APPOINTMENTS, MAINTENANCE, RATINGS }

data class OnlineSyncStatus(val loaded: Boolean = false, val failures: Int = 0) {
    val retrying: Boolean get() = failures == 1
    val unavailable: Boolean get() = failures >= 2
    val waitingForFirstData: Boolean get() = !loaded && !unavailable

    internal fun result(success: Boolean) =
        if (success) OnlineSyncStatus(loaded = true) else copy(failures = (failures + 1).coerceAtMost(2))
}

data class OnlineState(
    val user: OnlineUser? = null,
    val busy: Boolean = false,
    val message: OnlineMessage? = null,
    val configurationError: String? = null,
    val sync: Map<OnlineResource, OnlineSyncStatus> = emptyMap(),
    val products: List<OnlineProduct> = emptyList(),
    val cart: Map<Long, Int> = emptyMap(),
    val pendingOrder: PendingOnlineOrder? = null,
    val checkoutReceiptId: Long? = null,
    val orders: List<OnlineOrder> = emptyList(),
    val eligibleRatings: List<OnlineRatingItem> = emptyList(),
    val vehicles: List<OnlineVehicle> = emptyList(),
    val appointments: List<OnlineAppointment> = emptyList(),
    val maintenance: List<OnlineMaintenance> = emptyList()
) {
    fun syncStatus(resource: OnlineResource): OnlineSyncStatus = sync[resource] ?: OnlineSyncStatus()

    internal fun checkoutItems(selection: Map<Long, Int>, expectedRequestId: String?): Map<Long, Int> {
        if (pendingOrder?.requestId != expectedRequestId) {
            throw OnlineInputError("El estado de tu envío cambió. Revisa el carrito y Mis pedidos.")
        }
        if (pendingOrder == null && cart != selection) {
            throw OnlineInputError("Tu carrito cambió. Revísalo antes de confirmar la compra.")
        }
        return pendingOrder?.items ?: selection
    }
}

data class OnlineProduct(
    val id: Long,
    val version: Long,
    val name: String,
    val description: String,
    val category: String,
    val priceCop: Long,
    val promotionPriceCop: Long?,
    val stock: Int,
    val imagePath: String?,
    val averageRating: Double? = null,
    val ratingCount: Long = 0
)

data class OnlineRatingItem(
    val productId: Long,
    val productName: String,
    val imagePath: String?,
    val deliveredAtMillis: Long,
    val myRating: Int?
)

internal data class OnlineRatingResult(
    val productId: Long,
    val myRating: Int,
    val averageRating: Double?,
    val ratingCount: Long
)

data class OnlineProductDraft(
    val name: String,
    val description: String,
    val category: String,
    val priceCop: Long,
    val promotionPriceCop: Long? = null,
    val stock: Int,
    val imagePath: String? = null
)

data class OnlineOrder(
    val id: Long,
    val status: String,
    val clientName: String,
    val totalCop: Long,
    val createdAtMillis: Long,
    val items: List<OnlineOrderItem>,
    val paymentMethod: String? = null,
    val inventoryUpdates: List<OnlineProduct>? = null
)

data class OnlineOrderItem(val productId: Long, val productName: String, val quantity: Int, val unitPriceCop: Long)

data class OnlineVehicle(
    val id: Long,
    val clientId: Long,
    val clientName: String,
    val brand: String,
    val model: String,
    val year: Int,
    val plate: String
)

data class OnlineAppointment(
    val id: Long,
    val version: Long,
    val vehicleId: Long,
    val clientId: Long,
    val clientName: String,
    val vehicleBrand: String,
    val vehicleModel: String,
    val vehicleYear: Int,
    val vehiclePlate: String,
    val serviceType: String,
    val scheduledAtMillis: Long,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val finishedAtMillis: Long?
)

data class OnlineMaintenance(
    val id: Long,
    val appointmentId: Long,
    val vehicleId: Long,
    val summary: String,
    val mileageKm: Int?,
    val createdAtMillis: Long
)

internal class OnlineCredentials(val username: String, val password: String)
internal class OnlineRegistration(val username: String, val displayName: String, val password: String)
internal class OnlineOwnerRegistration(
    val username: String,
    val displayName: String,
    val password: String,
    val activationCode: String
)
internal class OnlineAuthResponse(val token: String, val user: OnlineUser)
internal data class OnlineProductWrite(
    val name: String,
    val description: String,
    val category: String,
    val priceCop: Long,
    val promotionPriceCop: Long?,
    val stock: Int,
    val imagePath: String?,
    val version: Long?
)

internal data class OnlineImageUpload(val imagePath: String)
internal data class OnlineOrderWrite(val requestId: String, val items: List<OnlineOrderLine>)
internal data class OnlineCheckoutWrite(val requestId: String, val items: List<OnlineOrderLine>, val expectedTotalCop: Long)
internal data class OnlineOrderLine(val productId: Long, val quantity: Int)
internal data class OnlineOrderDecision(val status: String)
internal data class OnlineRatingWrite(val stars: Int)
internal data class OnlineVehicleWrite(val brand: String, val model: String, val year: Int, val plate: String)
internal data class OnlineAppointmentWrite(
    val requestId: String,
    val vehicleId: Long,
    val scheduledAtMillis: Long,
    val serviceType: String
)

internal data class OnlineAppointmentChange(val version: Long, val action: String, val scheduledAtMillis: Long?)
internal data class OnlineMaintenanceWrite(val summary: String, val mileageKm: Int?)

@Entity(tableName = "online_catalog", primaryKeys = ["project", "id"])
internal data class OnlineProductCacheEntity(
    val project: String,
    val id: Long,
    val version: Long,
    val name: String,
    val description: String,
    val category: String,
    val priceCop: Long,
    val promotionPriceCop: Long?,
    val stock: Int,
    val imagePath: String?,
    val averageRating: Double?,
    @ColumnInfo(defaultValue = "0") val ratingCount: Long
) {
    fun toModel() = OnlineProduct(
        id, version, name, description, category, priceCop, promotionPriceCop, stock, imagePath, averageRating, ratingCount
    )
}

internal fun OnlineProduct.toCache(project: String) = OnlineProductCacheEntity(
    project, id, version, name, description, category, priceCop, promotionPriceCop, stock, imagePath, averageRating, ratingCount
)

@Dao
internal abstract class OnlineCatalogDao {
    @Query("SELECT * FROM online_catalog WHERE project = :project ORDER BY id")
    abstract suspend fun read(project: String): List<OnlineProductCacheEntity>

    @Query("DELETE FROM online_catalog WHERE project = :project")
    protected abstract suspend fun clear(project: String)

    @Insert
    protected abstract suspend fun insert(products: List<OnlineProductCacheEntity>)

    @Transaction
    open suspend fun replace(project: String, products: List<OnlineProductCacheEntity>) {
        val ordered = products.sortedBy { it.id }
        if (ordered == read(project)) return
        clear(project)
        if (ordered.isNotEmpty()) insert(ordered)
    }
}

@Database(entities = [OnlineProductCacheEntity::class], version = 2, exportSchema = false)
internal abstract class OnlineCatalogDatabase : RoomDatabase() {
    abstract fun catalog(): OnlineCatalogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE online_catalog ADD COLUMN averageRating REAL")
                db.execSQL("ALTER TABLE online_catalog ADD COLUMN ratingCount INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
