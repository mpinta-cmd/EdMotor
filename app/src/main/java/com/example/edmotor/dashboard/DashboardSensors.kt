package com.example.edmotor.dashboard

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

private val noDashboardSensorGuidance =
    DashboardSensorGuidance(DashboardStability.Unavailable, DashboardAmbientLight.Unavailable)

@Composable
internal fun rememberDashboardSensorGuidance(active: Boolean): DashboardSensorGuidance {
    if (!active) return noDashboardSensorGuidance

    val context = LocalContext.current
    val sensorManager = remember(context) { context.getSystemService(SensorManager::class.java) }
        ?: return noDashboardSensorGuidance
    var guidance by remember(sensorManager) { mutableStateOf(noDashboardSensorGuidance) }

    LifecycleResumeEffect(sensorManager) {
        val handler = Handler(Looper.getMainLooper())
        var interpreter: DashboardSensorInterpreter? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val current = interpreter ?: return
                val values = event.values
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> if (values.size >= 3) {
                        guidance = current.updateAcceleration(event.timestamp, values[0], values[1], values[2])
                    }
                    Sensor.TYPE_LIGHT -> if (values.isNotEmpty()) {
                        guidance = current.updateLight(values[0])
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val current = DashboardSensorInterpreter(
            hasAccelerometer = sensorManager.registerDashboardSensor(listener, Sensor.TYPE_ACCELEROMETER, handler),
            hasLightSensor = sensorManager.registerDashboardSensor(listener, Sensor.TYPE_LIGHT, handler)
        )
        interpreter = current
        guidance = current.guidance

        onPauseOrDispose {
            interpreter = null
            sensorManager.unregisterListener(listener)
            guidance = noDashboardSensorGuidance
        }
    }

    return guidance
}

private fun SensorManager.registerDashboardSensor(
    listener: SensorEventListener,
    type: Int,
    handler: Handler
): Boolean {
    val sensor = getDefaultSensor(type) ?: return false
    return try {
        registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, handler)
    } catch (_: SecurityException) {
        false
    }
}
