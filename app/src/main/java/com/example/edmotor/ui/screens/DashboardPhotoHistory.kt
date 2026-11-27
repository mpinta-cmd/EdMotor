package com.example.edmotor.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.edmotor.dashboard.DashboardEvidence
import com.example.edmotor.dashboard.DashboardEvidenceRepository
import com.example.edmotor.dashboard.dashboardIndicators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DashboardPhotoHistory(
    evidenceScope: String,
    repository: DashboardEvidenceRepository,
    initialPhotoId: Long?,
    onBack: () -> Unit
) {
    key(evidenceScope, initialPhotoId) {
        var selectedPhotoId by rememberSaveable { mutableStateOf(initialPhotoId) }
        var reload by remember { mutableIntStateOf(0) }
        var confirmDelete by remember { mutableStateOf(false) }
        var deleting by remember { mutableStateOf(false) }
        var deleteFailed by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val history = remember(repository, reload) {
            repository.observe(evidenceScope)
                .map<List<DashboardEvidence>, DashboardHistoryState> { DashboardHistoryState.Ready(it) }
                .onStart { emit(DashboardHistoryState.Loading) }
                .catch { emit(DashboardHistoryState.Unavailable) }
        }
        val historyState by history.collectAsStateWithLifecycle(initialValue = DashboardHistoryState.Loading)
        val entries = (historyState as? DashboardHistoryState.Ready)?.entries
        val selected = entries?.firstOrNull { it.id == selectedPhotoId }
        val returnToHistory = {
            confirmDelete = false
            deleteFailed = false
            selectedPhotoId = null
        }
        BackHandler {
            if (!deleting) {
                if (selectedPhotoId != null) returnToHistory() else onBack()
            }
        }

        if (selected != null) {
            DashboardPhotoDetail(
                evidenceScope = evidenceScope,
                repository = repository,
                entry = selected,
                deleting = deleting,
                deleteFailed = deleteFailed,
                onBack = returnToHistory,
                onDelete = { confirmDelete = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item("back") {
                    HistoryBackButton("Volver a Testigos", enabled = true, onClick = onBack)
                }
                item("title") { DashboardHistoryHeader() }
                if (entries == null) {
                    item("state") {
                        when (historyState) {
                            DashboardHistoryState.Loading -> HistoryFeedback(
                                title = "Cargando historial…",
                                body = null
                            )
                            DashboardHistoryState.Unavailable -> HistoryFeedback(
                                title = "No se pudo abrir el historial.",
                                body = null,
                                action = "Reintentar",
                                onAction = { reload++ }
                            )
                            is DashboardHistoryState.Ready -> Unit
                        }
                    }
                } else {
                    if (selectedPhotoId != null) {
                        item("missing") { Text("La foto seleccionada ya no está en este historial.") }
                    }
                    if (entries.isEmpty()) {
                        item("empty") {
                            HistoryFeedback(
                                title = "Aún no hay fotos guardadas.",
                                body = "Escanea el tablero y guarda la foto para consultarla aquí.",
                                action = "Ir a Testigos",
                                onAction = onBack
                            )
                        }
                    }
                    items(entries, key = { it.id }) { entry ->
                        DashboardHistoryItem(evidenceScope, repository, entry) {
                            selectedPhotoId = entry.id
                        }
                    }
                }
            }
        }

        if (confirmDelete && selected != null) {
            AlertDialog(
                onDismissRequest = { if (!deleting) confirmDelete = false },
                title = { Text("¿Eliminar esta foto?") },
                text = { Text("Se eliminarán la imagen y su registro de este dispositivo.") },
                confirmButton = {
                    TextButton(
                        enabled = !deleting,
                        onClick = {
                            deleting = true
                            deleteFailed = false
                            coroutineScope.launch {
                                try {
                                    repository.delete(evidenceScope, selected.id)
                                    returnToHistory()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    confirmDelete = false
                                    deleteFailed = true
                                } finally {
                                    deleting = false
                                }
                            }
                        }
                    ) {
                        Text(
                            if (deleting) "Eliminando…" else "Eliminar foto",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }, enabled = !deleting) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
private fun DashboardHistoryHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(26.dp))
                Text(
                    "Historial de fotos",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text("Fotos de Testigos guardadas en este dispositivo.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DashboardHistoryItem(
    evidenceScope: String,
    repository: DashboardEvidenceRepository,
    entry: DashboardEvidence,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoredDashboardPhoto(
                repository = repository,
                evidenceScope = evidenceScope,
                photoId = entry.id,
                contentDescription = null,
                thumbnail = true,
                modifier = Modifier.size(80.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    evidenceIndicatorTitle(entry.indicatorId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatAppointmentDateTime(entry.capturedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                entry.vehicleLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.primary
            )
        }
    }
}

@Composable
private fun DashboardPhotoDetail(
    evidenceScope: String,
    repository: DashboardEvidenceRepository,
    entry: DashboardEvidence,
    deleting: Boolean,
    deleteFailed: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val date = formatAppointmentDateTime(entry.capturedAtMillis)
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item("back") { HistoryBackButton("Volver al historial", enabled = !deleting, onClick = onBack) }
        item("heading") {
            Text(
                "Foto del tablero",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item("photo") {
            StoredDashboardPhoto(
                repository = repository,
                evidenceScope = evidenceScope,
                photoId = entry.id,
                contentDescription = "Foto del tablero guardada el $date",
                thumbnail = false,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
            )
        }
        item("details") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        evidenceIndicatorTitle(entry.indicatorId),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    PhotoMetadata(Icons.Outlined.CalendarToday, date)
                    entry.vehicleLabel?.let { PhotoMetadata(Icons.Outlined.DirectionsCar, it) }
                }
            }
        }
        item("delete") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (deleteFailed) {
                    Text(
                        "No se pudo eliminar la foto. Inténtalo de nuevo.",
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error
                    )
                }
                OutlinedButton(onClick = onDelete, enabled = !deleting) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Eliminar foto", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryBackButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun HistoryFeedback(title: String, body: String?, action: String? = null, onAction: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurfaceVariant
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onAction == null) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
            }
            Text(
                title,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (action != null && onAction != null) {
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun PhotoMetadata(icon: ImageVector, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DashboardPhotoPreview(
    jpeg: ByteArray,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    key(jpeg) {
        val state by produceState<DashboardPhotoState>(DashboardPhotoState.Loading) {
            value = decodeDashboardPhoto(jpeg, maxEdge = DetailPhotoEdge)
        }
        DashboardPhotoFrame(state, contentDescription, thumbnail = false, modifier = modifier)
    }
}

@Composable
private fun StoredDashboardPhoto(
    repository: DashboardEvidenceRepository,
    evidenceScope: String,
    photoId: Long,
    contentDescription: String?,
    thumbnail: Boolean,
    modifier: Modifier
) {
    var retry by remember(evidenceScope, photoId) { mutableIntStateOf(0) }
    key(repository, evidenceScope, photoId, retry) {
        val state by produceState<DashboardPhotoState>(DashboardPhotoState.Loading) {
            value = try {
                val bytes = repository.loadPhoto(evidenceScope, photoId)
                if (bytes == null) DashboardPhotoState.Unavailable else {
                    decodeDashboardPhoto(bytes, maxEdge = if (thumbnail) ThumbnailPhotoEdge else DetailPhotoEdge)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DashboardPhotoState.Unavailable
            }
        }
        DashboardPhotoFrame(
            state = state,
            contentDescription = contentDescription,
            thumbnail = thumbnail,
            modifier = modifier,
            onRetry = if (thumbnail) null else { { retry++ } }
        )
    }
}

@Composable
private fun DashboardPhotoFrame(
    state: DashboardPhotoState,
    contentDescription: String?,
    thumbnail: Boolean,
    modifier: Modifier,
    onRetry: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (thumbnail) 12.dp else 18.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (state) {
                DashboardPhotoState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (thumbnail) 20.dp else 28.dp),
                        strokeWidth = 2.dp
                    )
                }
                DashboardPhotoState.Unavailable -> Column(
                    modifier = Modifier.padding(if (thumbnail) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.BrokenImage, contentDescription = if (thumbnail) "Foto no disponible" else null)
                    if (!thumbnail) {
                        Text(
                            "No se pudo abrir la foto.",
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (onRetry != null) { TextButton(onClick = onRetry) { Text("Reintentar foto") } }
                }
                is DashboardPhotoState.Available -> Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (thumbnail) ContentScale.Crop else ContentScale.Fit
                )
            }
        }
    }
}

private fun evidenceIndicatorTitle(indicatorId: String): String =
    dashboardIndicators.firstOrNull { it.id == indicatorId }?.title ?: "Testigo sin identificar"

private suspend fun decodeDashboardPhoto(jpeg: ByteArray, maxEdge: Int): DashboardPhotoState =
    withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longestEdge <= 0) return@withContext DashboardPhotoState.Unavailable
        var sample = 1
        while (longestEdge / (sample * 2) >= maxEdge) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
        if (bitmap == null) DashboardPhotoState.Unavailable else DashboardPhotoState.Available(bitmap)
    }

private sealed interface DashboardPhotoState {
    data object Loading : DashboardPhotoState
    data object Unavailable : DashboardPhotoState
    data class Available(val bitmap: Bitmap) : DashboardPhotoState
}

private sealed interface DashboardHistoryState {
    data object Loading : DashboardHistoryState
    data object Unavailable : DashboardHistoryState
    data class Ready(val entries: List<DashboardEvidence>) : DashboardHistoryState
}

private const val ThumbnailPhotoEdge = 240
private const val DetailPhotoEdge = 1_080
