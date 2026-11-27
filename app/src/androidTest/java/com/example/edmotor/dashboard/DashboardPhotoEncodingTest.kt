package com.example.edmotor.dashboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Random

@RunWith(AndroidJUnit4::class)
class DashboardPhotoEncodingTest {
    @Test
    fun keepsFrameDimensionsAndOrientationAndLeavesOwnershipWithTheCaller() {
        val source = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(source)
            canvas.drawColor(Color.BLACK)
            val paint = Paint().apply { color = Color.RED }
            canvas.drawRect(40f, 40f, 140f, 140f, paint)
            paint.color = Color.BLUE
            canvas.drawRect(500f, 340f, 600f, 440f, paint)

            val jpeg = requireNotNull(encodeDashboardPhoto(source))
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size))
            try {
                assertEquals(640, decoded.width)
                assertEquals(480, decoded.height)
                assertTrue(Color.red(decoded.getPixel(90, 90)) > 200)
                assertTrue(Color.blue(decoded.getPixel(550, 390)) > 200)
            } finally {
                decoded.recycle()
            }
            assertFalse(source.isRecycled)
        } finally {
            source.recycle()
        }

        assertNull(encodeDashboardPhoto(source))
    }

    @Test
    fun lowersJpegQualityWhenAFullSizeFrameExceedsTheBudget() {
        val width = 1080
        val height = 810
        val random = Random(42L)
        val pixels = IntArray(width * height) { Color.BLACK or random.nextInt(0x1000000) }
        val source = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        try {
            val fullQuality = ByteArrayOutputStream()
            assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 92, fullQuality))
            assertTrue(fullQuality.size() > DASHBOARD_EVIDENCE_MAX_JPEG_BYTES)

            val jpeg = requireNotNull(encodeDashboardPhoto(source))
            assertTrue(jpeg.size in 1..DASHBOARD_EVIDENCE_MAX_JPEG_BYTES)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
            assertEquals("image/jpeg", bounds.outMimeType)
            assertEquals(width, bounds.outWidth)
            assertEquals(height, bounds.outHeight)
        } finally {
            source.recycle()
        }
    }
}
