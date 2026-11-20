package com.example.edmotor.dashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScannerTest {
    @Test
    fun findsSeveralDifferentLightsAndTheirPositionsInTheSameImage() {
        withDashboard(360, 220) { bitmap, canvas ->
            canvas.glyph(44f, 73f, 1.3f) { drawBattery() }
            canvas.glyph(144f, 75f, 1.25f, angle = -8f) { drawEngine() }
            canvas.glyph(248f, 79f, 1.2f, angle = 10f) { drawHighBeam() }

            val result = scanDashboard(bitmap)
            assertEquals(
                setOf("battery_charging", "check_engine", "high_beam"),
                result.matches.map { it.indicator.id }.toSet()
            )
            assertEquals(3, result.matches.size)
            assertEquals(0, result.unresolvedRedCount)
            assertPosition(result, "battery_charging", 76f / bitmap.width, 105f / bitmap.height)
            assertPosition(result, "check_engine", 174f / bitmap.width, 106f / bitmap.height)
            assertPosition(result, "high_beam", 277f / bitmap.width, 107f / bitmap.height)
            assertFalse(bitmap.isRecycled)
        }
    }

    @Test
    fun groupsDisconnectedLampHalvesInALargerPhoto() {
        withDashboard(1280, 800) { bitmap, canvas ->
            canvas.glyph(810f, 285f, 2.45f, angle = 7f) { drawPositionLights() }

            val result = scanDashboard(bitmap)
            assertEquals(listOf("position_lights"), result.matches.map { it.indicator.id })
            assertPosition(result, "position_lights", 868f / bitmap.width, 344f / bitmap.height)
        }
    }

    @Test
    fun lightColorAloneDoesNotIdentifyARingOrAnOrangeGlow() {
        withDashboard(420, 220) { bitmap, canvas ->
            canvas.drawCircle(84f, 117f, 27f, stroke(RED, 4f))
            canvas.drawCircle(219f, 119f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER })
            canvas.drawCircle(325f, 117f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

            val result = scanDashboard(bitmap)
            assertTrue(result.matches.toString(), result.matches.isEmpty())
            assertEquals(2, result.unresolvedCount)
            assertEquals(1, result.unresolvedRedCount)
        }
    }

    @Test
    fun darkDashAndNeutralReflectionsDoNotCreateRecognizedLights() {
        withDashboard(240, 120) { bitmap, canvas ->
            canvas.drawCircle(55f, 56f, 21f, stroke(Color.rgb(90, 10, 8), 5f))
            canvas.drawRect(120f, 30f, 174f, 70f, stroke(Color.LTGRAY, 5f))

            val result = scanDashboard(bitmap)
            assertTrue(result.matches.isEmpty())
            assertEquals(0, result.unresolvedCount)
        }
    }

    private inline fun withDashboard(width: Int, height: Int, check: (Bitmap, Canvas) -> Unit) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(20, 24, 30))
            check(bitmap, canvas)
        } finally {
            bitmap.recycle()
        }
    }

    private inline fun Canvas.glyph(x: Float, y: Float, scale: Float, angle: Float = 0f, draw: Canvas.() -> Unit) {
        val savePoint = save()
        translate(x, y)
        scale(scale, scale)
        rotate(angle, 24f, 24f)
        draw()
        restoreToCount(savePoint)
    }

    // Dibujos de prueba independientes. No renderizan las rutas de referencia del catálogo.
    private fun Canvas.drawBattery() {
        val paint = stroke(RED, 3.4f)
        drawRect(5f, 17f, 43f, 37f, paint)
        for (x in listOf(10f, 31f)) {
            drawPath(Path().apply {
                moveTo(x, 17f)
                lineTo(x, 11.5f)
                lineTo(x + 6.5f, 11.5f)
                lineTo(x + 6.5f, 17f)
            }, paint)
        }
        drawLine(11f, 27.5f, 19.5f, 27.5f, paint)
        drawLine(28.5f, 27.5f, 36.5f, 27.5f, paint)
        drawLine(32.5f, 23.5f, 32.5f, 31.5f, paint)
    }

    private fun Canvas.drawEngine() {
        val paint = stroke(AMBER, 3.2f)
        drawPath(Path().apply {
            moveTo(7f, 19f)
            lineTo(13f, 19f)
            lineTo(17f, 15.5f)
            lineTo(31f, 15.5f)
            lineTo(35f, 19.5f)
            lineTo(38.5f, 19.5f)
            lineTo(38.5f, 22.5f)
            lineTo(41f, 22.5f)
            lineTo(41f, 18.5f)
            lineTo(45f, 18.5f)
            lineTo(45f, 36f)
            lineTo(41f, 36f)
            lineTo(41f, 32.5f)
            lineTo(38f, 32.5f)
            lineTo(33f, 39f)
            lineTo(15f, 39f)
            lineTo(11f, 33.5f)
            lineTo(7f, 33.5f)
            close()
        }, paint)
        drawLine(17f, 10f, 30f, 10f, paint)
        drawLine(23.5f, 10f, 23.5f, 15.5f, paint)
        drawLine(3f, 22f, 3f, 30f, paint)
        drawLine(3f, 26f, 7f, 26f, paint)
    }

    private fun Canvas.drawHighBeam() {
        val paint = stroke(BLUE, 2.9f)
        drawPath(Path().apply {
            moveTo(24f, 12f)
            lineTo(31f, 12f)
            quadTo(45f, 14f, 45f, 24f)
            quadTo(45f, 34f, 31f, 36f)
            lineTo(24f, 36f)
            close()
        }, paint)
        for (y in 15..33 step 6) {
            drawLine(4f, y.toFloat(), 16.5f, y.toFloat(), paint)
        }
    }

    private fun Canvas.drawPositionLights() {
        val paint = stroke(GREEN, 2.8f)
        val left = Path().apply {
            moveTo(20f, 16.5f)
            lineTo(16f, 16.5f)
            quadTo(8.5f, 17.5f, 8.5f, 24f)
            quadTo(8.5f, 30.5f, 16f, 31.5f)
            lineTo(20f, 31.5f)
            close()
        }
        drawPath(left, paint)
        drawLine(2f, 16f, 6f, 18f, paint)
        drawLine(2f, 24f, 5f, 24f, paint)
        drawLine(2f, 32f, 6f, 30f, paint)
        val saved = save()
        scale(-1f, 1f, 24f, 24f)
        drawPath(left, paint)
        drawLine(2f, 16f, 6f, 18f, paint)
        drawLine(2f, 24f, 5f, 24f, paint)
        drawLine(2f, 32f, 6f, 30f, paint)
        restoreToCount(saved)
    }

    private fun assertPosition(result: DashboardScanResult, id: String, x: Float, y: Float) {
        val bounds = result.matches.single { it.indicator.id == id }.bounds
        assertTrue(bounds.toString(), bounds.left < x && x < bounds.right)
        assertTrue(bounds.toString(), bounds.top < y && y < bounds.bottom)
        assertTrue(bounds.toString(), bounds.left >= 0f && bounds.right <= 1f && bounds.top >= 0f && bounds.bottom <= 1f)
    }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private companion object {
        val RED = Color.rgb(250, 42, 35)
        val AMBER = Color.rgb(255, 155, 41)
        val GREEN = Color.rgb(52, 231, 84)
        val BLUE = Color.rgb(38, 151, 252)
    }
}
