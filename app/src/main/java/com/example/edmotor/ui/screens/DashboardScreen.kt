package com.example.edmotor.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.edmotor.dashboard.DashboardCamera
import com.example.edmotor.dashboard.DashboardEvidenceRepository
import com.example.edmotor.dashboard.DashboardIndicator
import com.example.edmotor.dashboard.DashboardLightColor
import com.example.edmotor.dashboard.DashboardScanResult
import com.example.edmotor.dashboard.DashboardStability
import com.example.edmotor.dashboard.DashboardUrgency
import com.example.edmotor.dashboard.dashboardCaptureFrame
import com.example.edmotor.dashboard.dashboardIndicators
import com.example.edmotor.dashboard.encodeDashboardPhoto
import com.example.edmotor.dashboard.rememberDashboardSensorGuidance
import com.example.edmotor.dashboard.scanFramedDashboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

@Composable
internal fun DashboardScreen(
    evidenceScope: String,
    vehicles: List<DashboardEvidenceVehicle>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findDashboardActivity() }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val evidence = remember(context) { DashboardEvidenceRepository.create(context) }
    val hasCamera = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var permissionGranted by remember { mutableStateOf(context.hasDashboardCameraPermission()) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var awaitingFrame by remember { mutableStateOf(false) }
    var captureSignal by remember { mutableIntStateOf(0) }
    var analyzing by remember { mutableStateOf(false) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    var issue by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<DashboardScanResult?>(null) }
    var photoDraft by remember(evidenceScope) { mutableStateOf<DashboardPhotoDraft?>(null) }
    var savingPhoto by remember(evidenceScope) { mutableStateOf(false) }
    var saveIssue by remember(evidenceScope) { mutableStateOf<String?>(null) }
    var savedPhotoId by remember(evidenceScope) { mutableStateOf<Long?>(null) }
    var historyOpen by rememberSaveable(evidenceScope) { mutableStateOf(false) }
    var initialHistoryPhotoId by rememberSaveable(evidenceScope) { mutableStateOf<Long?>(null) }
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var guideQuery by rememberSaveable { mutableStateOf("") }
    var selectedGuideId by rememberSaveable { mutableStateOf<String?>(null) }
    val filteredGuide = remember(guideQuery) {
        val words = dashboardSearchText(guideQuery).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) dashboardIndicators else dashboardIndicators.filter { indicator ->
            val text = dashboardSearchText("${indicator.title} ${indicator.summary}")
            words.all { it in text }
        }
    }

    fun closeCamera() {
        cameraOpen = false
        cameraReady = false
        awaitingFrame = false
    }

    fun openCamera() {
        result = null
        photoDraft = null
        saveIssue = null
        savedPhotoId = null
        cameraOpen = true
    }

    fun openHistory(photoId: Long? = null) {
        closeCamera()
        analysisJob?.cancel()
        savedPhotoId = null
        initialHistoryPhotoId = photoId
        historyOpen = true
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
        if (granted && hasCamera) openCamera()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    permissionGranted = context.hasDashboardCameraPermission()
                    if (permissionGranted) permissionDenied = false else closeCamera()
                }
                Lifecycle.Event.ON_STOP -> {
                    closeCamera()
                    analysisJob?.cancel()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionBlocked = !permissionGranted && permissionDenied &&
        activity?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } == true

    if (historyOpen) {
        DashboardPhotoHistory(evidenceScope, evidence, initialHistoryPhotoId, onBack = { historyOpen = false })
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!cameraOpen) {
            item(key = "intro") { DashboardHeader(historyEnabled = !savingPhoto, onHistory = { openHistory() }) }
        }
        item(key = "scan") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    issue?.let { DashboardNotice(it, urgent = true) }
                    when {
                        !hasCamera -> Text("No hay una cámara disponible en este dispositivo. Puedes consultar la guía de testigos.")
                        cameraOpen && permissionGranted -> {
                            val sensorGuidance = rememberDashboardSensorGuidance(active = true)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Cámara de Testigos",
                                    modifier = Modifier.weight(1f).semantics { heading() },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { closeCamera() }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Cerrar cámara")
                                }
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
                                    .clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A1120))
                            ) {
                                DashboardCamera(
                                    captureSignal = captureSignal,
                                    onReadyChanged = { ready ->
                                        cameraReady = cameraOpen && ready
                                        if (!ready) awaitingFrame = false
                                    },
                                    onFrame = { bitmap ->
                                        if (cameraOpen && awaitingFrame) {
                                            val capturedAt = System.currentTimeMillis()
                                            closeCamera()
                                            analyzing = true
                                            analysisJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                                try {
                                                    val (reading, jpeg) = withContext(Dispatchers.Default) {
                                                        val scan = try {
                                                            scanFramedDashboard(bitmap)
                                                        } catch (_: RuntimeException) {
                                                            null
                                                        }
                                                        scan to encodeDashboardPhoto(bitmap)
                                                    }
                                                    result = reading
                                                    photoDraft = jpeg?.let { DashboardPhotoDraft(it, capturedAt) }
                                                    issue = when {
                                                        jpeg == null -> "No pudimos preparar la foto. Repite la toma para guardarla."
                                                        reading == null -> "No pudimos analizar el tablero. Puedes guardar la foto y elegir un testigo."
                                                        else -> null
                                                    }
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (_: Exception) {
                                                    issue = "No se pudo analizar la imagen. Vuelve a intentar el escaneo."
                                                } finally {
                                                    bitmap.recycle()
                                                    analyzing = false
                                                }
                                            }
                                        } else {
                                            bitmap.recycle()
                                        }
                                    },
                                    onError = {
                                        closeCamera()
                                        issue = it
                                    },
                                    modifier = Modifier.matchParentSize()
                                )
                                DashboardCaptureGuide(
                                    modifier = Modifier.matchParentSize(),
                                    stability = if (cameraReady) sensorGuidance.stability else DashboardStability.Waiting
                                )
                                if (!cameraReady) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                                        color = colors.surface,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            "Iniciando cámara…",
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            if (cameraReady) DashboardCaptureHints(sensorGuidance)
                            Text(
                                "Solo se analiza el área clara. Acerca la cámara a los testigos y evita los reflejos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    issue = null
                                    result = null
                                    awaitingFrame = true
                                    captureSignal++
                                },
                                enabled = cameraReady && !awaitingFrame,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (awaitingFrame) "Capturando…" else "Escanear tablero") }
                        }
                        analyzing -> {
                            Text(
                                "Analizando el tablero…",
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = { analysisJob?.cancel() }) { Text("Cancelar análisis") }
                        }
                        else -> {
                            if (permissionDenied && !permissionGranted) {
                                Text(
                                    if (permissionBlocked) "Habilita el permiso de cámara para escanear el tablero."
                                    else "Para escanear el tablero, permite el acceso a la cámara.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    issue = null
                                    permissionGranted = context.hasDashboardCameraPermission()
                                    when {
                                        permissionGranted -> openCamera()
                                        permissionBlocked -> {
                                            val target = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            try {
                                                context.startActivity(target)
                                            } catch (_: ActivityNotFoundException) {
                                                issue = "Abre los ajustes del sistema, busca EdMotor y activa el permiso de cámara."
                                            }
                                        }
                                        else -> requestPermission.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                enabled = !savingPhoto,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    when {
                                        permissionBlocked -> "Abrir ajustes"
                                        result != null -> "Escanear de nuevo"
                                        else -> "Activar cámara"
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        result?.let { scan ->
            item(key = "scanSummary") { DashboardResultSummary(scan) }
        }
        photoDraft?.let { draft ->
            item(key = "photo_${draft.capturedAtMillis}") {
                DashboardPhotoDraftCard(
                    draft = draft,
                    scan = result,
                    vehicles = vehicles,
                    saving = savingPhoto,
                    issue = saveIssue,
                    onSave = { indicatorId, vehicle ->
                        if (!savingPhoto) {
                            savingPhoto = true
                            saveIssue = null
                            scope.launch {
                                try {
                                    savedPhotoId = evidence.save(
                                        scope = evidenceScope,
                                        indicatorId = indicatorId,
                                        vehicleId = vehicle?.id,
                                        vehicleLabel = vehicle?.label,
                                        capturedAtMillis = draft.capturedAtMillis,
                                        jpeg = draft.jpeg
                                    )
                                    photoDraft = null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    saveIssue = "No pudimos guardar la foto en el dispositivo. Vuelve a intentarlo."
                                } finally {
                                    savingPhoto = false
                                }
                            }
                        }
                    }
                )
            }
        }
        savedPhotoId?.let { id ->
            item(key = "savedPhoto_$id") { DashboardPhotoSavedCard(onOpen = { openHistory(id) }) }
        }
        result?.let { scan ->
            val matches = scan.matches.distinctBy { it.indicator.id }.sortedBy { it.indicator.urgency }
            items(matches, key = { "match_${it.indicator.id}" }) { match ->
                DashboardIndicatorCard(match.indicator, expanded = true)
            }
        }
        item(key = "guideSwitch") {
            OutlinedButton(onClick = { guideOpen = !guideOpen }, modifier = Modifier.fillMaxWidth()) {
                Text(if (guideOpen) "Ocultar guía de testigos" else "Consultar guía de testigos")
            }
        }
        if (guideOpen) {
            item(key = "guideIntro") {
                DashboardGuideSearch(
                    query = guideQuery,
                    onQueryChange = { guideQuery = it },
                    resultCount = filteredGuide.size
                )
            }
            items(filteredGuide, key = { "guide_${it.id}" }) { indicator ->
                DashboardIndicatorCard(
                    indicator = indicator,
                    expanded = selectedGuideId == indicator.id,
                    onToggle = { selectedGuideId = if (selectedGuideId == indicator.id) null else indicator.id }
                )
            }
        }
    }
}

private val dashboardDiacritics = Regex("\\p{M}+")
private val dashboardWordBreaks = Regex("[^\\p{L}\\p{N}]+")

private fun dashboardSearchText(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(dashboardDiacritics, "")
        .replace(dashboardWordBreaks, " ")

@Composable
private fun DashboardGuideSearch(query: String, onQueryChange: (String) -> Unit, resultCount: Int) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar testigo") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Borrar búsqueda")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )
        Text(
            when (resultCount) {
                0 -> "No hay coincidencias. Prueba otro nombre o consulta el manual de tu vehículo."
                1 -> "1 testigo en la guía"
                else -> "$resultCount testigos en la guía"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardHeader(historyEnabled: Boolean, onHistory: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(28.dp))
                Text(
                    "Testigos EdMotor",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text("Usa la cámara con el vehículo detenido en un lugar seguro.", style = MaterialTheme.typography.bodyMedium)
            Text("Guarda una foto del tablero para consultarla en este dispositivo.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onHistory, enabled = historyEnabled) {
                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Historial de fotos", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun DashboardCaptureGuide(modifier: Modifier, stability: DashboardStability) {
    val cornerColor = when (stability) {
        DashboardStability.Stable -> Color(0xFF9AEBC4)
        DashboardStability.Moving -> Color(0xFFFFCF80)
        DashboardStability.Waiting, DashboardStability.Unavailable -> Color.White
    }
    Canvas(modifier) {
        val left = size.width * dashboardCaptureFrame.left
        val top = size.height * dashboardCaptureFrame.top
        val right = size.width * dashboardCaptureFrame.right
        val bottom = size.height * dashboardCaptureFrame.bottom
        val shade = Color.Black.copy(alpha = .48f)
        drawRect(shade, size = Size(size.width, top))
        drawRect(shade, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left, bottom - top))
        drawRect(shade, topLeft = Offset(right, top), size = Size(size.width - right, bottom - top))
        val length = size.minDimension * .08f
        val stroke = 2.dp.toPx()
        listOf(
            Offset(left, top) to Offset(length, length),
            Offset(right, top) to Offset(-length, length),
            Offset(left, bottom) to Offset(length, -length),
            Offset(right, bottom) to Offset(-length, -length)
        ).forEach { (corner, direction) ->
            drawLine(cornerColor, corner, corner + Offset(direction.x, 0f), stroke, StrokeCap.Round)
            drawLine(cornerColor, corner, corner + Offset(0f, direction.y), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun DashboardResultSummary(result: DashboardScanResult) {
    val found = result.matches.distinctBy { it.indicator.id }.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Resultado del escaneo",
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            when {
                result.needsReframe -> "La imagen no permite una lectura fiable. Acerca la cámara al tablero, encuadra los testigos y evita reflejos."
                found == 0 -> "No encontramos coincidencias con la guía. Prueba con otra toma o busca el símbolo en el manual de tu vehículo."
                found == 1 -> "1 coincidencia orientativa. Compárala con el manual de tu vehículo."
                else -> "$found coincidencias orientativas. Compáralas con el manual de tu vehículo."
            },
            style = MaterialTheme.typography.bodyMedium
        )
        if (found > 0) {
            Text(
                "Al dar contacto, algunos testigos se encienden durante el chequeo. La urgencia depende de si siguen encendidos o parpadean y del estado del vehículo.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (result.unresolvedCount > 0 && !result.needsReframe) {
            Text(
                if (result.unresolvedCount == 1) "Quedó una señal sin identificar."
                else "Quedaron ${result.unresolvedCount} señales sin identificar.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (result.unresolvedRedCount > 0) {
            DashboardNotice(
                "Hay una zona roja sin identificar. Si ves un testigo rojo, mantén el vehículo detenido en un lugar seguro y consulta el manual o solicita asistencia.",
                urgent = true
            )
        }
    }
}

@Composable
private fun DashboardNotice(message: String, urgent: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (urgent) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (urgent) colors.onErrorContainer else colors.onSecondaryContainer
    ) {
        Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DashboardIndicatorCard(
    indicator: DashboardIndicator,
    expanded: Boolean,
    onToggle: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val urgency = indicator.urgency
    val (tile, iconTint, colorLabel) = when (indicator.lightColor) {
        DashboardLightColor.RED -> Triple(colors.errorContainer, colors.onErrorContainer, "Rojo")
        DashboardLightColor.AMBER -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, "Ámbar")
        DashboardLightColor.GREEN -> if (colors.surface.luminance() < .4f) {
            Triple(Color(0xFF12392D), Color(0xFFB5F5D4), "Verde")
        } else {
            Triple(Color(0xFFDDF4E7), Color(0xFF175639), "Verde")
        }
        DashboardLightColor.BLUE -> Triple(colors.primaryContainer, colors.onPrimaryContainer, "Azul")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().then(
                    if (onToggle == null) Modifier else Modifier
                        .clickable(role = Role.Button, onClick = onToggle)
                        .semantics { stateDescription = if (expanded) "Detalles abiertos" else "Detalles cerrados" }
                ).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = tile, contentColor = iconTint) {
                    DashboardSymbol(indicator, iconTint, Modifier.padding(7.dp).size(36.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(indicator.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(colorLabel, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
                if (onToggle != null) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                }
            }
            if (expanded) {
                HorizontalDivider(color = colors.outline)
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val priorityColor = when (urgency) {
                        DashboardUrgency.STOP_SAFELY -> colors.errorContainer to colors.onErrorContainer
                        DashboardUrgency.CHECK_SOON -> colors.secondaryContainer to colors.onSecondaryContainer
                        DashboardUrgency.INFORMATION -> colors.primaryContainer to colors.onPrimaryContainer
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = priorityColor.first,
                        contentColor = priorityColor.second
                    ) {
                        Text(
                            when (urgency) {
                                DashboardUrgency.STOP_SAFELY -> "Detente de forma segura"
                                DashboardUrgency.CHECK_SOON -> "Revisa pronto"
                                DashboardUrgency.INFORMATION -> "Informativo"
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(indicator.summary, style = MaterialTheme.typography.bodyMedium)
                    if (indicator.causes.isNotEmpty()) {
                        Text("Posibles causas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        indicator.causes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                    Text("Qué hacer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(indicator.advice, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DashboardSymbol(indicator: DashboardIndicator, tint: Color, modifier: Modifier) {
    val paths = remember(indicator) {
        indicator.marks.map { mark -> PathParser().parsePathString(mark.path).toPath() to mark }
    }
    Canvas(modifier) {
        val factor = size.minDimension / 48f
        scale(factor, factor, pivot = Offset.Zero) {
            paths.forEach { (path, mark) ->
                drawPath(
                    path,
                    tint,
                    style = if (mark.filled) Fill else Stroke(
                        width = mark.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

private fun Context.hasDashboardCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.findDashboardActivity(): Activity? {
    var candidate = this
    while (candidate is ContextWrapper && candidate !is Activity) {
        candidate = candidate.baseContext ?: return null
    }
    return candidate as? Activity
}
