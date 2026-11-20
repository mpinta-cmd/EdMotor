package com.example.edmotor.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MAX_CAPTURE_EDGE = 1080
private const val PREVIEW_TIMEOUT_MILLIS = 6_500L

@Composable
internal fun DashboardCamera(
    captureSignal: Int,
    onFrame: (Bitmap) -> Unit,
    onReadyChanged: (Boolean) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestFrame by rememberUpdatedState(onFrame)
    val latestReadiness by rememberUpdatedState(onReadyChanged)
    val latestError by rememberUpdatedState(onError)
    val controller = remember(context) {
        DashboardCameraController(context, { latestReadiness(it) }, { latestError(it) })
    }

    AndroidView(
        factory = { TextureView(it).also(controller::bind) },
        modifier = modifier,
        onRelease = controller::unbind
    )

    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, _ ->
            controller.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        controller.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            controller.setResumed(false)
        }
    }

    var lastCaptureSignal by remember(controller) { mutableIntStateOf(captureSignal) }
    LaunchedEffect(controller, captureSignal) {
        if (lastCaptureSignal != captureSignal) {
            lastCaptureSignal = captureSignal
            val frame = controller.capture()
            if (frame != null) latestFrame(frame)
            else latestError("No se pudo capturar el tablero. Espera a que aparezca la imagen.")
        }
    }
}

private data class PreviewConfiguration(
    val cameraId: String,
    val size: Size,
    val sensorOrientation: Int,
    val continuousFocus: Boolean,
    val allowsNoCropRotation: Boolean
)

private class PreviewRun(val texture: SurfaceTexture, val configuration: PreviewConfiguration) {
    val initialTimestamp = texture.timestamp
    var camera: CameraDevice? = null
    var session: CameraCaptureSession? = null
    var surface: Surface? = null
    var timeout: Runnable? = null
    var ready = false
    var closing = false
}

private class DashboardCameraController(
    private val context: Context,
    private val onReadyChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) : TextureView.SurfaceTextureListener, DisplayManager.DisplayListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private var preview: TextureView? = null
    private var current: PreviewRun? = null
    private var resumed = false
    private var failed = false

    fun bind(view: TextureView) {
        preview?.let(::unbind)
        preview = view
        view.surfaceTextureListener = this
        onReadyChanged(false)
        startPreview()
    }

    fun unbind(view: TextureView) {
        if (preview !== view) return
        view.surfaceTextureListener = null
        preview = null
        current?.let(::closePreview)
    }

    fun setResumed(value: Boolean) {
        if (resumed == value) return
        resumed = value
        if (value) {
            failed = false
            displays?.registerDisplayListener(this, mainHandler)
            startPreview()
        } else {
            displays?.unregisterDisplayListener(this)
            current?.let(::closePreview)
        }
    }

    fun capture(): Bitmap? {
        val run = current ?: return null
        val view = preview ?: return null
        if (!run.ready || !isCurrent(run) || view.width <= 0 || view.height <= 0) return null
        val scale = minOf(1f, MAX_CAPTURE_EDGE.toFloat() / maxOf(view.width, view.height))
        val viewTransform = view.getTransform(Matrix())
        // Older renderers omit the view transform from getBitmap(). Read an untransformed
        // frame consistently, then apply the preview's rotation and crop in bitmap coordinates.
        if (!viewTransform.isIdentity) view.setTransform(Matrix())
        val frame = try {
            view.getBitmap(
                (view.width * scale).roundToInt().coerceAtLeast(1),
                (view.height * scale).roundToInt().coerceAtLeast(1)
            )
        } catch (_: IllegalStateException) {
            null
        } finally {
            if (!viewTransform.isIdentity) view.setTransform(viewTransform)
        } ?: return null
        if (viewTransform.isIdentity) return frame

        try {
            val scaleX = frame.width.toFloat() / view.width
            val scaleY = frame.height.toFloat() / view.height
            val bitmapTransform = Matrix().apply {
                setScale(1f / scaleX, 1f / scaleY)
                postConcat(viewTransform)
                postScale(scaleX, scaleY)
            }
            return Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888).also { capture ->
                Canvas(capture).drawBitmap(frame, bitmapTransform, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        } finally {
            frame.recycle()
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = startPreview()

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        current?.takeIf { it.texture === texture }?.let(::transformPreview)
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        current?.takeIf { it.texture === texture }?.let(::closePreview)
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        val run = current ?: return
        if (run.texture !== texture || !isCurrent(run) || run.ready || run.session == null) return
        if (texture.timestamp == 0L || texture.timestamp == run.initialTimestamp) return
        run.ready = true
        cancelTimeout(run)
        onReadyChanged(true)
    }

    override fun onDisplayAdded(displayId: Int) = Unit

    override fun onDisplayRemoved(displayId: Int) = Unit

    override fun onDisplayChanged(displayId: Int) {
        if (preview?.display?.displayId == displayId) current?.let(::transformPreview)
    }

    @SuppressLint("MissingPermission")
    private fun startPreview() {
        if (!resumed || failed || current != null) return
        val view = preview ?: return
        val texture = view.surfaceTexture ?: return
        if (!view.isAvailable || view.width <= 0 || view.height <= 0) return
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail("Activa el permiso de cámara para escanear el tablero.")
            return
        }

        val manager = cameras
        val configuration = try {
            manager?.let { findConfiguration(it, view) }
        } catch (_: CameraAccessException) {
            fail("La cámara está ocupada o no está disponible. Vuelve a intentarlo.")
            return
        } catch (_: IllegalArgumentException) {
            fail("La cámara no está disponible. Vuelve a intentarlo.")
            return
        } catch (_: SecurityException) {
            fail("No se pudo acceder a la cámara. Revisa el permiso de EdMotor.")
            return
        }
        if (manager == null || configuration == null) {
            fail("Este dispositivo no tiene una cámara disponible. Usa la guía manual.")
            return
        }

        val run = PreviewRun(texture, configuration)
        current = run
        val timeout = Runnable {
            if (isCurrent(run) && !run.ready) {
                fail("La cámara no mostró una imagen. Vuelve a abrirla o usa la guía manual.")
            }
        }
        run.timeout = timeout
        mainHandler.postDelayed(timeout, PREVIEW_TIMEOUT_MILLIS)

        try {
            manager.openCamera(configuration.cameraId, cameraState(run), mainHandler)
        } catch (_: SecurityException) {
            current = null
            cancelTimeout(run)
            fail("No se pudo acceder a la cámara. Revisa el permiso de EdMotor.")
        } catch (_: CameraAccessException) {
            current = null
            cancelTimeout(run)
            fail("La cámara está ocupada o no está disponible. Vuelve a intentarlo.")
        } catch (_: IllegalArgumentException) {
            current = null
            cancelTimeout(run)
            fail("La cámara no está disponible. Vuelve a intentarlo.")
        }
    }

    private fun cameraState(run: PreviewRun) = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (!isCurrent(run)) {
                camera.close()
                return
            }
            run.camera = camera
            connectPreview(run, camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            failDevice(run, camera, "La cámara se desconectó. Vuelve a abrirla.")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            val message = when (error) {
                ERROR_CAMERA_IN_USE, ERROR_MAX_CAMERAS_IN_USE ->
                    "Otra aplicación está usando la cámara. Ciérrala e inténtalo de nuevo."
                ERROR_CAMERA_DISABLED -> "La cámara está desactivada en el dispositivo."
                else -> "No se pudo abrir la cámara. Vuelve a intentarlo."
            }
            failDevice(run, camera, message)
        }

        override fun onClosed(camera: CameraDevice) {
            if (current !== run) return
            if (!run.closing) fail("La cámara se cerró. Vuelve a abrirla.")
            current = null
            startPreview()
        }
    }

    private fun failDevice(run: PreviewRun, camera: CameraDevice, message: String) {
        if (isCurrent(run)) {
            run.camera = camera
            fail(message)
        } else {
            camera.close()
        }
    }

    // This overload supports Camera2 devices from API 24.
    @Suppress("DEPRECATION")
    private fun connectPreview(run: PreviewRun, camera: CameraDevice) {
        try {
            val configuration = run.configuration
            run.texture.setDefaultBufferSize(configuration.size.width, configuration.size.height)
            transformPreview(run)
            val surface = Surface(run.texture)
            run.surface = surface
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                if (configuration.continuousFocus) {
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }
                if (Build.VERSION.SDK_INT >= 31 && configuration.allowsNoCropRotation) {
                    set(CaptureRequest.SCALER_ROTATE_AND_CROP, CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)
                }
            }.build()
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (!isCurrent(run)) {
                        session.close()
                        return
                    }
                    run.session = session
                    try {
                        session.setRepeatingRequest(request, null, mainHandler)
                    } catch (_: CameraAccessException) {
                        fail("No se pudo iniciar la vista de cámara. Vuelve a abrirla.")
                    } catch (_: IllegalStateException) {
                        fail("No se pudo iniciar la vista de cámara. Vuelve a abrirla.")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    session.close()
                    if (isCurrent(run)) fail("No se pudo preparar la vista de cámara. Vuelve a abrirla.")
                }
            }, mainHandler)
        } catch (_: CameraAccessException) {
            fail("No se pudo preparar la vista de cámara. Vuelve a abrirla.")
        } catch (_: IllegalStateException) {
            fail("No se pudo preparar la vista de cámara. Vuelve a abrirla.")
        } catch (_: IllegalArgumentException) {
            fail("Esta cámara no pudo mostrar la imagen del tablero. Usa la guía manual.")
        }
    }

    private fun isCurrent(run: PreviewRun): Boolean =
        current === run && !run.closing && resumed && preview?.surfaceTexture === run.texture

    private fun fail(message: String) {
        failed = true
        current?.let(::closePreview)
        onError(message)
    }

    private fun closePreview(run: PreviewRun) {
        if (run.closing) return
        run.closing = true
        cancelTimeout(run)
        run.session?.close()
        run.session = null
        run.camera?.close()
        run.camera = null
        run.surface?.release()
        run.surface = null
        if (run.ready) {
            run.ready = false
            onReadyChanged(false)
        }
    }

    private fun cancelTimeout(run: PreviewRun) {
        run.timeout?.let(mainHandler::removeCallbacks)
        run.timeout = null
    }

    private fun transformPreview(run: PreviewRun) {
        val view = preview ?: return
        if (!isCurrent(run) || view.width <= 0 || view.height <= 0) return
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        val size = run.configuration.size
        // TextureView already applies the sensor orientation before stretching to its view bounds.
        val sensorSwapsAxes = run.configuration.sensorOrientation % 180 != 0
        val nativeWidth = if (sensorSwapsAxes) size.height else size.width
        val nativeHeight = if (sensorSwapsAxes) size.width else size.height
        val displaySwapsAxes = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        val rotatedWidth = if (displaySwapsAxes) nativeHeight else nativeWidth
        val rotatedHeight = if (displaySwapsAxes) nativeWidth else nativeHeight
        val scale = maxOf(view.width.toFloat() / rotatedWidth, view.height.toFloat() / rotatedHeight)
        val centerX = view.width / 2f
        val centerY = view.height / 2f
        view.setTransform(Matrix().apply {
            setScale(nativeWidth * scale / view.width, nativeHeight * scale / view.height, centerX, centerY)
            postRotate(-rotation * 90f, centerX, centerY)
        })
    }
}

private fun findConfiguration(manager: CameraManager, view: TextureView): PreviewConfiguration? {
    val candidates = manager.cameraIdList.mapNotNull { id ->
        val characteristics = manager.getCameraCharacteristics(id)
        val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceTexture::class.java)
            ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Triple(id, characteristics, sizes)
    }
    val (id, characteristics, sizes) = candidates.minByOrNull {
        if (it.second.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) 0 else 1
    } ?: return null
    val orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    val swapsAxes = (orientation + (view.display?.rotation ?: Surface.ROTATION_0) * 90) % 180 != 0
    val desiredAspect = view.width.toFloat() / view.height
    val bounded = sizes.filter { maxOf(it.width, it.height) <= 1920 && minOf(it.width, it.height) <= 1080 }
        .ifEmpty { listOf(sizes.minBy { it.width.toLong() * it.height }) }
    val desiredShortEdge = minOf(view.width, view.height, MAX_CAPTURE_EDGE)
    val candidatesBySize = bounded.filter { minOf(it.width, it.height) >= desiredShortEdge }.ifEmpty { bounded }
    val size = candidatesBySize.minWith(compareBy<Size> {
        val aspect = if (swapsAxes) it.height.toFloat() / it.width else it.width.toFloat() / it.height
        abs(aspect - desiredAspect)
    }.thenBy { it.width.toLong() * it.height })

    return PreviewConfiguration(
        cameraId = id,
        size = size,
        sensorOrientation = orientation,
        continuousFocus = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            ?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true,
        allowsNoCropRotation = Build.VERSION.SDK_INT >= 31 &&
            characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES)
                ?.contains(CaptureRequest.SCALER_ROTATE_AND_CROP_NONE) == true
    )
}
