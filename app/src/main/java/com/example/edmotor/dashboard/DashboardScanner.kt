package com.example.edmotor.dashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class DashboardBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal data class DashboardMatch(
    val indicator: DashboardIndicator,
    val bounds: DashboardBounds,
    val score: Float
)

internal data class DashboardScanResult(
    val matches: List<DashboardMatch>,
    val unresolvedCount: Int,
    val unresolvedRedCount: Int = 0
)

/** Compara las formas de los testigos iluminados dentro de la foto. */
internal fun scanDashboard(bitmap: Bitmap): DashboardScanResult {
    if (bitmap.isRecycled || min(bitmap.width, bitmap.height) < 8) return DashboardScanResult(emptyList(), 0)

    val image = readDashboardPixels(bitmap)
    val (labels, components) = segmentDashboard(image)
    if (components.isEmpty()) return DashboardScanResult(emptyList(), 0)

    val regions = groupDashboardComponents(components)
    val matches = mutableListOf<MatchCandidate>()
    for (region in regions) {
        val shape = sampledShape(region.bounds) { x, y ->
            val label = labels[y * image.width + x]
            region.parts.any { it.label == label }
        } ?: continue
        val accepted = recognizeDashboardShape(region, shape)
        if (accepted != null) matches.add(accepted)
    }

    // Las versiones de una región con y sin fragmentos pueden describir el mismo testigo.
    val selected = mutableListOf<MatchCandidate>()
    for (match in matches.sortedByDescending { it.score }) {
        if (selected.none { it.region.overlaps(match.region) }) selected.add(match)
        if (selected.size == MAX_MATCHES) break
    }

    val unresolved = mutableListOf<DashboardRegion>()
    for (region in regions.sortedByDescending { it.area }) {
        if (selected.none { it.region.overlaps(region) } && unresolved.none { it.overlaps(region) }) {
            unresolved.add(region)
        }
    }
    return DashboardScanResult(
        matches = selected.sortedWith(compareBy({ it.region.bounds.top }, { it.region.bounds.left })).map { match ->
            DashboardMatch(match.indicator, match.region.bounds.normalized(image.width, image.height), match.score)
        },
        unresolvedCount = unresolved.size,
        unresolvedRedCount = unresolved.count { it.color == DashboardLightColor.RED }
    )
}

private data class DashboardPixels(val width: Int, val height: Int, val values: IntArray)

private fun readDashboardPixels(source: Bitmap): DashboardPixels {
    val scale = (MAX_IMAGE_SIDE.toFloat() / max(source.width, source.height)).coerceAtMost(1f)
    val width = (source.width * scale).toInt().coerceAtLeast(1)
    val height = (source.height * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(source, width, height, true)
    val bitmap = if (Build.VERSION.SDK_INT >= 26 && scaled.config == Bitmap.Config.HARDWARE) {
        checkNotNull(scaled.copy(Bitmap.Config.ARGB_8888, false))
    } else {
        scaled
    }
    try {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return DashboardPixels(width, height, pixels)
    } finally {
        if (bitmap !== scaled) bitmap.recycle()
        if (scaled !== source) scaled.recycle()
    }
}

private data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val maxSide get() = max(width, height)
    val area get() = width * height

    fun union(other: PixelBox) = PixelBox(
        min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom)
    )

    fun overlaps(other: PixelBox): Boolean {
        val width = (min(right, other.right) - max(left, other.left)).coerceAtLeast(0)
        val height = (min(bottom, other.bottom) - max(top, other.top)).coerceAtLeast(0)
        return width * height >= 0.45f * min(area, other.area)
    }

    fun isNear(other: PixelBox, distance: Int): Boolean =
        left - distance <= other.right && right + distance >= other.left &&
            top - distance <= other.bottom && bottom + distance >= other.top

    fun normalized(imageWidth: Int, imageHeight: Int) = DashboardBounds(
        left.toFloat() / imageWidth, top.toFloat() / imageHeight,
        right.toFloat() / imageWidth, bottom.toFloat() / imageHeight
    )
}

private data class LightComponent(
    val label: Int,
    val color: DashboardLightColor,
    val bounds: PixelBox,
    val area: Int
)

private data class DashboardRegion(val parts: List<LightComponent>, val bounds: PixelBox, val area: Int) {
    val color get() = parts.first().color

    fun overlaps(other: DashboardRegion): Boolean = color == other.color && bounds.overlaps(other.bounds)
}

private fun segmentDashboard(image: DashboardPixels): Pair<IntArray, List<LightComponent>> {
    val colors = ByteArray(image.values.size) { dashboardColor(image.values[it]) }
    val labels = IntArray(colors.size)
    val queue = IntArray(colors.size)
    val largest = PriorityQueue<LightComponent>(compareBy { it.area })
    var nextLabel = 0

    for (start in colors.indices) {
        val color = colors[start]
        if (color == 0.toByte() || labels[start] != 0) continue

        val label = ++nextLabel
        var read = 0
        var write = 1
        queue[0] = start
        labels[start] = label
        var left = image.width
        var right = 0
        var top = image.height
        var bottom = 0
        while (read < write) {
            val pixel = queue[read++]
            val x = pixel % image.width
            val y = pixel / image.width
            left = min(left, x)
            right = max(right, x + 1)
            top = min(top, y)
            bottom = max(bottom, y + 1)
            for (ny in max(0, y - 1)..min(image.height - 1, y + 1)) {
                val offset = ny * image.width
                for (nx in max(0, x - 1)..min(image.width - 1, x + 1)) {
                    val neighbor = offset + nx
                    if (colors[neighbor] == color && labels[neighbor] == 0) {
                        labels[neighbor] = label
                        queue[write++] = neighbor
                    }
                }
            }
        }

        if (write < MIN_COMPONENT_PIXELS || largest.size == MAX_COMPONENTS && write <= largest.peek()!!.area) continue
        if (largest.size == MAX_COMPONENTS) largest.remove()
        largest.add(LightComponent(
            label, DashboardLightColor.entries[color.toInt() - 1], PixelBox(left, top, right, bottom), write
        ))
    }

    return labels to largest.sortedByDescending { it.area }
}

private fun dashboardColor(pixel: Int): Byte {
    if (pixel ushr 24 < 160) return 0
    val red = pixel shr 16 and 255
    val green = pixel shr 8 and 255
    val blue = pixel and 255
    val peak = max(red, max(green, blue))
    val chroma = peak - min(red, min(green, blue))
    if (peak < 132 || chroma < 38 || chroma * 100 < peak * 28) return 0

    val hue = when (peak) {
        red -> (360 + 60 * (green - blue) / chroma) % 360
        green -> 120 + 60 * (blue - red) / chroma
        else -> 240 + 60 * (red - green) / chroma
    }
    val color = when (hue) {
        in 0..17, in 340..359 -> DashboardLightColor.RED
        in 20..66 -> DashboardLightColor.AMBER
        in 75..165 -> DashboardLightColor.GREEN
        in 174..254 -> DashboardLightColor.BLUE
        else -> return 0
    }
    return (color.ordinal + 1).toByte()
}

private fun groupDashboardComponents(parts: List<LightComponent>): List<DashboardRegion> {
    val anchors = parts.filter {
        it.area >= MIN_REGION_PIXELS && it.bounds.width >= MIN_REGION_SIDE && it.bounds.height >= MIN_REGION_SIDE
    }.take(MAX_ANCHORS)
    val regions = mutableListOf<DashboardRegion>()
    val seen = mutableSetOf<List<Int>>()

    fun add(group: List<LightComponent>, bounds: PixelBox, area: Int) {
        if (seen.add(group.map { it.label }.sorted())) regions.add(DashboardRegion(group, bounds, area))
    }

    for (anchor in anchors) {
        add(listOf(anchor), anchor.bounds, anchor.area)
        val compact = gatherNearbyParts(anchor, parts, allowSeparatedHalves = false)
        add(compact.parts, compact.bounds, compact.area)
        // Algunos testigos, como las luces de posición, tienen dos mitades separadas.
        val extended = gatherNearbyParts(anchor, parts, allowSeparatedHalves = true)
        add(extended.parts, extended.bounds, extended.area)
    }
    return regions
}

private fun gatherNearbyParts(
    anchor: LightComponent,
    parts: List<LightComponent>,
    allowSeparatedHalves: Boolean
): DashboardRegion {
    val anchorSize = anchor.bounds.maxSide
    val gap = if (allowSeparatedHalves) (anchorSize / 2).coerceIn(3, 30) else (anchorSize / 5).coerceIn(2, 20)
    val maxSide = if (allowSeparatedHalves) (anchorSize * 3.25f).toInt() else (anchorSize * 1.85f).toInt()
    val group = mutableListOf(anchor)
    var bounds = anchor.bounds
    var area = anchor.area
    repeat(if (allowSeparatedHalves) 2 else 1) {
        for (part in parts) {
            if (part.color != anchor.color || part.area > anchor.area || part in group) continue
            val reference = if (allowSeparatedHalves) bounds else anchor.bounds
            if (!reference.isNear(part.bounds, gap)) continue
            val joined = bounds.union(part.bounds)
            if (joined.maxSide > maxSide) continue

            group.add(part)
            area += part.area
            bounds = joined
        }
    }
    return DashboardRegion(group, bounds, area)
}

private data class ShapeMask(
    val foreground: IntArray,
    val distances: IntArray,
    val parts: List<IntArray>
)

/** Normaliza escala y posición, manteniendo la proporción de la figura. */
private inline fun sampledShape(
    bounds: PixelBox,
    captureStrokes: Boolean = false,
    foreground: (Int, Int) -> Boolean
): ShapeMask? {
    val step = bounds.maxSide.toFloat() / MASK_DRAW_SIDE
    val offsetX = (MASK_SIDE - bounds.width / step) / 2
    val offsetY = (MASK_SIDE - bounds.height / step) / 2
    val pixels = BooleanArray(MASK_SIDE * MASK_SIDE)

    for (y in 1 until MASK_SIDE - 1) {
        val sourceY = (y - offsetY) * step
        val top = sourceY + step * 0.25f
        val bottom = sourceY + step * 0.75f
        for (x in 1 until MASK_SIDE - 1) {
            val sourceX = (x - offsetX) * step
            val left = sourceX + step * 0.25f
            val right = sourceX + step * 0.75f
            var votes = 0
            if (foregroundAt(bounds, left, top, foreground)) votes++
            if (foregroundAt(bounds, right, top, foreground)) votes++
            if (foregroundAt(bounds, left, bottom, foreground)) votes++
            if (foregroundAt(bounds, right, bottom, foreground)) votes++
            pixels[y * MASK_SIDE + x] = votes >= 2
        }
    }

    val indices = pixels.indices.filter { pixels[it] }.toIntArray()
    if (indices.size < MIN_MASK_PIXELS || indices.size > MASK_DRAW_SIDE * MASK_DRAW_SIDE * 0.72f) return null
    val strokes = if (captureStrokes) separatedStrokes(pixels, indices) else emptyList()
    return ShapeMask(indices, shapeDistances(pixels), strokes)
}

private inline fun foregroundAt(bounds: PixelBox, x: Float, y: Float, foreground: (Int, Int) -> Boolean): Boolean =
    x >= 0f && y >= 0f && x < bounds.width && y < bounds.height &&
        foreground(bounds.left + x.toInt(), bounds.top + y.toInt())

private fun shapeDistances(mask: BooleanArray): IntArray {
    val size = MASK_SIDE
    val distance = IntArray(mask.size) { if (mask[it]) 0 else 36 }
    for (y in 1 until size - 1) for (x in 1 until size - 1) {
        val index = y * size + x
        val orthogonal = min(distance[index - 1], distance[index - size]) + 3
        val diagonal = min(distance[index - size - 1], distance[index - size + 1]) + 4
        distance[index] = minOf(distance[index], orthogonal, diagonal)
    }
    for (y in size - 2 downTo 1) for (x in size - 2 downTo 1) {
        val index = y * size + x
        val orthogonal = min(distance[index + 1], distance[index + size]) + 3
        val diagonal = min(distance[index + size - 1], distance[index + size + 1]) + 4
        distance[index] = minOf(distance[index], orthogonal, diagonal)
    }
    return distance
}

private fun separatedStrokes(mask: BooleanArray, indices: IntArray): List<IntArray> {
    val visited = BooleanArray(mask.size)
    val queue = IntArray(indices.size)
    val strokes = mutableListOf<IntArray>()
    val importantStrokeSize = (indices.size / 36).coerceAtLeast(6)
    for (start in indices) {
        if (visited[start]) continue
        var read = 0
        var write = 1
        visited[start] = true
        queue[0] = start
        while (read < write) {
            val index = queue[read++]
            for (offsetY in -1..1) for (offsetX in -1..1) {
                val neighbor = index + offsetY * MASK_SIDE + offsetX
                if (mask[neighbor] && !visited[neighbor]) {
                    visited[neighbor] = true
                    queue[write++] = neighbor
                }
            }
        }
        if (write >= importantStrokeSize) strokes.add(queue.copyOf(write))
    }
    return strokes
}

private data class MatchCandidate(val region: DashboardRegion, val indicator: DashboardIndicator, val score: Float)
private data class ShapeVariant(val shape: ShapeMask, val angle: Int)
private data class DashboardTemplate(val indicator: DashboardIndicator, val variants: List<ShapeVariant>)

private val dashboardTemplates: Map<DashboardLightColor, List<DashboardTemplate>> by lazy {
    val rotations = listOf(-12, -6, 0, 6, 12)
    dashboardIndicators.map { indicator ->
        val paths = indicator.marks.map { mark ->
            PathParser().parsePathString(mark.path).toPath().asAndroidPath() to mark
        }
        val variants = rotations.mapNotNull { angle ->
            val image = Bitmap.createBitmap(TEMPLATE_CAPTURE_SIZE, TEMPLATE_CAPTURE_SIZE, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(image)
                canvas.translate(12f, 12f)
                canvas.scale(2f, 2f)
                canvas.rotate(angle.toFloat(), 24f, 24f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
                for ((path, mark) in paths) {
                    paint.style = if (mark.filled) Paint.Style.FILL else Paint.Style.STROKE
                    paint.strokeWidth = mark.strokeWidth
                    canvas.drawPath(path, paint)
                }
                val pixels = IntArray(image.width * image.height)
                image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
                val bounds = templateBounds(pixels) ?: return@mapNotNull null
                val mask = sampledShape(bounds, captureStrokes = true) { x, y ->
                    (pixels[y * TEMPLATE_CAPTURE_SIZE + x] ushr 24) >= 128
                }
                mask?.let { ShapeVariant(it, angle) }
            } finally {
                image.recycle()
            }
        }
        DashboardTemplate(indicator, variants)
    }.groupBy { it.indicator.lightColor }
}

private fun templateBounds(pixels: IntArray): PixelBox? {
    var left = TEMPLATE_CAPTURE_SIZE
    var right = 0
    var top = TEMPLATE_CAPTURE_SIZE
    var bottom = 0
    for (i in pixels.indices) {
        if (pixels[i] ushr 24 < 128) continue
        val x = i % TEMPLATE_CAPTURE_SIZE
        val y = i / TEMPLATE_CAPTURE_SIZE
        left = min(left, x)
        right = max(right, x + 1)
        top = min(top, y)
        bottom = max(bottom, y + 1)
    }
    return if (left < right && top < bottom) PixelBox(left, top, right, bottom) else null
}

private fun recognizeDashboardShape(region: DashboardRegion, observed: ShapeMask): MatchCandidate? {
    val templates = dashboardTemplates[region.color].orEmpty()
    var best: DashboardTemplate? = null
    var bestScore = 0f
    var runnerUpScore = 0f

    for (template in templates) {
        val score = template.variants.maxOfOrNull { shapeScore(observed, it.shape) - abs(it.angle) * 0.0005f }
            ?: continue
        if (score > bestScore) {
            runnerUpScore = bestScore
            bestScore = score
            best = template
        } else if (score > runnerUpScore) {
            runnerUpScore = score
        }
    }

    val winner = best ?: return null
    if (bestScore < MIN_SHAPE_SCORE || bestScore - runnerUpScore < MIN_SCORE_MARGIN) return null
    return MatchCandidate(region, winner.indicator, bestScore)
}

private fun shapeScore(observed: ShapeMask, expected: ShapeMask): Float {
    val precision = strokeCoverage(observed.foreground, expected.distances)
    val recall = strokeCoverage(expected.foreground, observed.distances)
    if (precision < 0.63f || recall < 0.65f) return 0f
    if (expected.parts.any { strokeCoverage(it, observed.distances) < 0.51f }) return 0f

    val foregroundRatio = min(observed.foreground.size, expected.foreground.size).toFloat() /
        max(observed.foreground.size, expected.foreground.size)
    return 0.88f * (2 * precision * recall / (precision + recall)) + 0.12f * foregroundRatio
}

private fun strokeCoverage(stroke: IntArray, distances: IntArray): Float {
    var weight = 0
    for (index in stroke) {
        weight += when (distances[index]) {
            0 -> 100
            3, 4 -> 86
            6, 7 -> 55
            8, 9 -> 27
            else -> 0
        }
    }
    return weight / (100f * stroke.size)
}

private const val MAX_IMAGE_SIDE = 640
private const val MAX_COMPONENTS = 160
private const val MAX_ANCHORS = 36
private const val MAX_MATCHES = 12
private const val MIN_COMPONENT_PIXELS = 4
private const val MIN_REGION_PIXELS = 18
private const val MIN_REGION_SIDE = 8
private const val TEMPLATE_CAPTURE_SIZE = 120
private const val MASK_SIDE = 36
private const val MASK_DRAW_SIDE = 30
private const val MIN_MASK_PIXELS = 24
private const val MIN_SHAPE_SCORE = 0.76f
private const val MIN_SCORE_MARGIN = 0.065f
