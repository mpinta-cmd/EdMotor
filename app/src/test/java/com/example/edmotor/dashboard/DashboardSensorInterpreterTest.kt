package com.example.edmotor.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardSensorInterpreterTest {
    @Test
    fun stableOnlyAfterSustainedQuietAndDetectsTurnWithoutChangeOfMagnitude() {
        val interpreter = DashboardSensorInterpreter(hasAccelerometer = true, hasLightSensor = true)

        assertEquals(DashboardStability.Waiting, interpreter.sample(0).stability)
        assertEquals(DashboardStability.Waiting, interpreter.sample(450, x = 0.05f).stability)
        assertEquals(DashboardStability.Waiting, interpreter.sample(599).stability)
        assertEquals(DashboardStability.Stable, interpreter.sample(600).stability)

        assertEquals(DashboardStability.Moving, interpreter.sample(660, y = 9.81f, z = 0f).stability)
        assertEquals(DashboardStability.Moving, interpreter.sample(1000, y = 9.81f, z = 0.04f).stability)
        assertEquals(DashboardStability.Stable, interpreter.sample(1260, y = 9.81f, z = 0f).stability)
    }

    @Test
    fun smallSequentialChangesStillDetectAccumulatedMovement() {
        val interpreter = DashboardSensorInterpreter(hasAccelerometer = true, hasLightSensor = false)

        interpreter.sample(0)
        interpreter.sample(200, x = 0.2f)
        interpreter.sample(400, x = 0.4f)

        assertEquals(DashboardStability.Moving, interpreter.sample(600, x = 0.6f).stability)
        assertEquals(DashboardAmbientLight.Unavailable, interpreter.guidance.light)
    }

    @Test
    fun invalidStaleAndDelayedAccelerationSamplesDoNotCertifyStability() {
        val interpreter = DashboardSensorInterpreter(hasAccelerometer = true, hasLightSensor = true)

        interpreter.sample(100)
        interpreter.sample(400)

        assertEquals(DashboardStability.Waiting, interpreter.sample(450, x = Float.NaN).stability)
        assertEquals(DashboardStability.Waiting, interpreter.sample(300, x = 5f).stability)
        assertEquals(DashboardStability.Waiting, interpreter.sample(400, x = 5f).stability)
        assertEquals(DashboardStability.Stable, interpreter.sample(700).stability)
        assertEquals(DashboardStability.Waiting, interpreter.sample(1700).stability)
        assertEquals(DashboardStability.Stable, interpreter.sample(2300).stability)
    }

    @Test
    fun lightWarningUsesSeparateEntryAndRecoveryThresholds() {
        val interpreter = DashboardSensorInterpreter(hasAccelerometer = false, hasLightSensor = true)

        assertEquals(DashboardAmbientLight.Waiting, interpreter.updateLight(-1f).light)
        assertEquals(DashboardAmbientLight.Waiting, interpreter.updateLight(Float.POSITIVE_INFINITY).light)
        assertEquals(DashboardAmbientLight.Normal, interpreter.updateLight(12f).light)
        assertEquals(DashboardAmbientLight.Low, interpreter.updateLight(11.9f).light)
        assertEquals(DashboardAmbientLight.Low, interpreter.updateLight(19.9f).light)
        assertEquals(DashboardAmbientLight.Normal, interpreter.updateLight(20f).light)
        assertEquals(DashboardAmbientLight.Normal, interpreter.updateLight(12f).light)
        assertEquals(DashboardStability.Unavailable, interpreter.sample(300).stability)
    }

    @Test
    fun unavailableSensorsIgnoreSamples() {
        val interpreter = DashboardSensorInterpreter(hasAccelerometer = false, hasLightSensor = false)
        val unavailable = DashboardSensorGuidance(DashboardStability.Unavailable, DashboardAmbientLight.Unavailable)

        assertEquals(unavailable, interpreter.sample(500))
        assertEquals(unavailable, interpreter.updateLight(0f))
    }

    private fun DashboardSensorInterpreter.sample(
        milliseconds: Long,
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 9.81f
    ): DashboardSensorGuidance = updateAcceleration(milliseconds * 1_000_000, x, y, z)
}
