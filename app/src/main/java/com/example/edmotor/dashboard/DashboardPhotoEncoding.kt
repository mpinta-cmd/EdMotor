package com.example.edmotor.dashboard

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

private val evidenceJpegQualities = intArrayOf(92, 84, 74, 62, 48, 32)

/** Encodes the oriented camera frame without changing its dimensions or taking ownership of it. */
internal fun encodeDashboardPhoto(bitmap: Bitmap): ByteArray? {
    if (bitmap.isRecycled) return null

    val jpeg = ByteArrayOutputStream()
    for (quality in evidenceJpegQualities) {
        jpeg.reset()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, jpeg)) return null
        if (jpeg.size() in 1..DASHBOARD_EVIDENCE_MAX_JPEG_BYTES) return jpeg.toByteArray()
    }
    return null
}
