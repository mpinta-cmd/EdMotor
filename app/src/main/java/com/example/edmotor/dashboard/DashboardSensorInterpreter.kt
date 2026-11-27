package com.example.edmotor.dashboard

internal enum class DashboardStability { Unavailable, Waiting, Moving, Stable }

internal enum class DashboardAmbientLight { Unavailable, Waiting, Low, Normal }

internal data class DashboardSensorGuidance(
    val stability: DashboardStability,
    val light: DashboardAmbientLight
)

internal class DashboardSensorInterpreter(
    private val hasAccelerometer: Boolean,
    private val hasLightSensor: Boolean
) {
    var guidance = DashboardSensorGuidance(
        stability = if (hasAccelerometer) DashboardStability.Waiting else DashboardStability.Unavailable,
        light = if (hasLightSensor) DashboardAmbientLight.Waiting else DashboardAmbientLight.Unavailable
    )
        private set

    private var lastAcceleration: AccelerationSample? = null
    private var quietReference: AccelerationSample? = null

    fun updateAcceleration(timestampNanos: Long, x: Float, y: Float, z: Float): DashboardSensorGuidance {
        if (!hasAccelerometer || timestampNanos < 0 || !x.isFinite() || !y.isFinite() || !z.isFinite()) {
            return guidance
        }

        val previous = lastAcceleration
        if (previous != null && timestampNanos <= previous.timestampNanos) return guidance

        val sample = AccelerationSample(timestampNanos, x, y, z)
        val reference = quietReference ?: sample
        lastAcceleration = sample

        val stability = when {
            previous == null || timestampNanos - previous.timestampNanos > MAX_SAMPLE_GAP_NANOS -> {
                quietReference = sample
                DashboardStability.Waiting
            }
            // Compare vectors to detect both sudden shakes and accumulated tilt, regardless of gravity.
            sample.differsFrom(previous) || sample.differsFrom(reference) -> {
                quietReference = sample
                DashboardStability.Moving
            }
            timestampNanos - reference.timestampNanos >= QUIET_TIME_NANOS -> DashboardStability.Stable
            else -> guidance.stability
        }

        if (stability != guidance.stability) guidance = guidance.copy(stability = stability)
        return guidance
    }

    fun updateLight(lux: Float): DashboardSensorGuidance {
        if (!hasLightSensor || !lux.isFinite() || lux < 0f) return guidance

        val cutoff = if (guidance.light == DashboardAmbientLight.Low) RECOVERY_LIGHT_LUX else LOW_LIGHT_LUX
        val light = if (lux < cutoff) DashboardAmbientLight.Low else DashboardAmbientLight.Normal
        if (light != guidance.light) guidance = guidance.copy(light = light)
        return guidance
    }

    private data class AccelerationSample(
        val timestampNanos: Long,
        val x: Float,
        val y: Float,
        val z: Float
    ) {
        fun differsFrom(reference: AccelerationSample): Boolean {
            val dx = x - reference.x
            val dy = y - reference.y
            val dz = z - reference.z
            return dx * dx + dy * dy + dz * dz > MOTION_DELTA_SQUARED
        }
    }

    private companion object {
        const val QUIET_TIME_NANOS = 600_000_000L
        const val MAX_SAMPLE_GAP_NANOS = 900_000_000L
        const val MOTION_DELTA_SQUARED = 0.55f * 0.55f
        const val LOW_LIGHT_LUX = 12f
        const val RECOVERY_LIGHT_LUX = 20f
    }
}
