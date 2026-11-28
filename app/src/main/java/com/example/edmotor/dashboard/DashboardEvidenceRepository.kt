package com.example.edmotor.dashboard

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

internal const val DASHBOARD_EVIDENCE_MAX_JPEG_BYTES = 512 * 1024

internal data class DashboardEvidence(
    val id: Long,
    val indicatorId: String,
    val vehicleId: String?,
    val vehicleLabel: String?,
    val capturedAtMillis: Long
)

internal class DashboardEvidenceRepository(private val dao: DashboardEvidenceDao) {
    fun observe(scope: String): Flow<List<DashboardEvidence>> = dao.observe(scope)

    suspend fun save(
        scope: String,
        indicatorId: String,
        vehicleId: String?,
        vehicleLabel: String?,
        capturedAtMillis: Long,
        jpeg: ByteArray
    ): Long {
        require(scope.isNotBlank() && indicatorId.isNotBlank())
        require(jpeg.size in 4..DASHBOARD_EVIDENCE_MAX_JPEG_BYTES &&
            jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte() &&
            jpeg[jpeg.lastIndex - 1] == 0xFF.toByte() && jpeg.last() == 0xD9.toByte()
        ) { "La captura debe ser un JPEG válido dentro del límite de tamaño." }

        return dao.insert(
            DashboardEvidenceEntity(
                scope = scope,
                indicatorId = indicatorId,
                vehicleId = vehicleId,
                vehicleLabel = vehicleLabel,
                capturedAtMillis = capturedAtMillis,
                jpeg = jpeg
            )
        )
    }

    suspend fun loadPhoto(scope: String, id: Long): ByteArray? = dao.loadPhoto(scope, id)

    suspend fun delete(scope: String, id: Long) = dao.delete(scope, id)

    companion object {
        @Volatile private var instance: DashboardEvidenceRepository? = null

        fun create(context: Context): DashboardEvidenceRepository = instance ?: synchronized(this) {
            instance ?: DashboardEvidenceRepository(
                Room.databaseBuilder(
                    context.applicationContext,
                    DashboardEvidenceDatabase::class.java,
                    "edmotor_dashboard_evidence.db"
                ).build().evidence()
            ).also { instance = it }
        }
    }
}

@Entity(
    tableName = "dashboard_evidence",
    indices = [Index(value = ["scope", "capturedAtMillis", "id"])]
)
internal data class DashboardEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scope: String,
    val indicatorId: String,
    val vehicleId: String?,
    val vehicleLabel: String?,
    val capturedAtMillis: Long,
    val jpeg: ByteArray
)

@Dao
internal interface DashboardEvidenceDao {
    // Read only metadata for the list. Each JPEG is loaded separately from the same atomic row.
    @Query(
        "SELECT id, indicatorId, vehicleId, vehicleLabel, capturedAtMillis " +
            "FROM dashboard_evidence WHERE scope = :scope ORDER BY capturedAtMillis DESC, id DESC"
    )
    fun observe(scope: String): Flow<List<DashboardEvidence>>

    @Insert
    suspend fun insert(evidence: DashboardEvidenceEntity): Long

    @Query("SELECT jpeg FROM dashboard_evidence WHERE scope = :scope AND id = :id")
    suspend fun loadPhoto(scope: String, id: Long): ByteArray?

    @Query("DELETE FROM dashboard_evidence WHERE scope = :scope AND id = :id")
    suspend fun delete(scope: String, id: Long)
}

@Database(entities = [DashboardEvidenceEntity::class], version = 1, exportSchema = false)
internal abstract class DashboardEvidenceDatabase : RoomDatabase() {
    abstract fun evidence(): DashboardEvidenceDao
}
