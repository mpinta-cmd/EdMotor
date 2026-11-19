package com.example.edmotor.ui.components

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.TypedValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProductImage(
    imageUri: String?,
    imageRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val hasCatalogImage = remember(resources, configuration, imageRes) { resources.hasProductDrawable(imageRes) }

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = imageUri
    ) {
        value = null
        val parsedUri = imageUri
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)

        if (parsedUri != null) {
            value = withContext(Dispatchers.IO) {
                decodeSampledBitmap(
                    resolver = context.contentResolver,
                    uri = parsedUri,
                    maxWidth = 1200,
                    maxHeight = 1200
                )
            }
        }
    }

    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }

        hasCatalogImage -> {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }

        else -> {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("Sin imagen", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Resources.hasProductDrawable(id: Int): Boolean {
    if (id <= 0) return false
    return try {
        val type = getResourceTypeName(id)
        if (type != "drawable" && type != "mipmap") return false

        val resource = TypedValue().also { getValue(id, it, true) }
        val path = resource.string?.toString() ?: return false
        if (path.endsWith(".xml")) {
            getXml(id).use { parser ->
                parser.nextTag()
                parser.name == "vector"
            }
        } else {
            true
        }
    } catch (_: Exception) {
        false
    }
}

private fun decodeSampledBitmap(
    resolver: android.content.ContentResolver,
    uri: Uri,
    maxWidth: Int,
    maxHeight: Int
): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > maxWidth ||
            bounds.outHeight / sampleSize > maxHeight
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    } catch (_: Exception) {
        null
    }
}

