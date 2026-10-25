package com.aicode.core.xmlrender

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.PathParser
import com.aicode.feature.workspace.domain.FileAccessProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import kotlin.math.roundToInt

/**
 * Miniature drawable factory for the XML layout renderer.
 *
 * Rebuilds the subset of drawable XML the preview needs without instantiating arbitrary
 * project classes: rasters via [BitmapFactory] (through [FileAccessProvider.copyToLocal],
 * so remote workspaces work too), vector drawables by re-drawing their paths into a
 * bitmap, shapes as [GradientDrawable], layer-lists/selectors by picking their most
 * representative layer. Anything unsupported degrades to a neutral placeholder plus a
 * warning — rendering never aborts because one drawable is missing.
 */
class MiniDrawables(
    private val context: Context,
    private val fileAccess: FileAccessProvider,
    private val index: ProjectResourceIndex,
    private val dark: Boolean,
    private val warnings: WarningsCollector
) {

    private val density = context.resources.displayMetrics.density

    /**
     * Resolves a background/src token into a Drawable, or null when unsupported
     * (a warning is recorded). Must run off the main thread.
     */
    suspend fun resolve(token: String): Drawable? {
        val value = token.trim()
        return when {
            value.startsWith("@drawable/") || value.startsWith("@mipmap/") -> {
                val name = value.substringAfterLast('/')
                val path = index.drawablePath(name)
                when {
                    path == null -> {
                        warnings.add("Resource not found: $value")
                        null
                    }
                    else -> loadFromPath(path, value)
                }
            }
            value.startsWith("@android:drawable/") -> frameworkDrawable(value)
            value.startsWith("@color/") -> index.color(value.removePrefix("@color/"))
                ?.let { ResourceValues.parseColor(it)?.let(::ColorDrawable) }
                ?: run {
                    warnings.add("Color resource not found: $value")
                    null
                }
            value.startsWith("@android:color/") -> ProjectResourceIndex.ANDROID_COLORS[
                value.removePrefix("@android:color/")
            ]?.let { ResourceValues.parseColor(it)?.let(::ColorDrawable) }
            value.startsWith("#") -> ResourceValues.parseColor(value)?.let(::ColorDrawable)
            value.startsWith("?") -> themeColor(value)?.let(::ColorDrawable)
            else -> {
                warnings.add("Unsupported background value: $value")
                null
            }
        }
    }

    /**
     * Synchronous resolver for tokens that never touch the filesystem: colors,
     * theme attributes and framework drawables. Called during view building (main thread);
     * file-backed tokens ("@drawable/..") were resolved earlier via [resolve].
     */
    fun resolveSync(tokenRaw: String): Drawable? {
        val value = tokenRaw.trim()
        return when {
            value.startsWith("@android:drawable/") -> frameworkDrawable(value)
            value.startsWith("@drawable/") || value.startsWith("@mipmap/") -> null
            value.startsWith("@color/") -> index.color(value.removePrefix("@color/"))
                ?.let { ResourceValues.parseColor(it)?.let(::ColorDrawable) }
            value.startsWith("@android:color/") -> ProjectResourceIndex.ANDROID_COLORS[
                value.removePrefix("@android:color/")
            ]?.let { ResourceValues.parseColor(it)?.let(::ColorDrawable) }
            value.startsWith("#") -> ResourceValues.parseColor(value)?.let(::ColorDrawable)
            value.startsWith("?") -> themeColor(value)?.let(::ColorDrawable)
            else -> null
        }
    }

    /** Resolves "?attr/x" against a fixed Material palette (works without app modules). */
    fun themeColor(token: String): Int? {
        val key = token.removePrefix("?").removePrefix("attr/").substringAfterLast('/').lowercase()
        return SINGLE[key] ?: PAIRS[key]?.let { pair -> if (dark) pair.second else pair.first }
    }

    private suspend fun loadFromPath(path: String, displayToken: String): Drawable? =
        withContext(Dispatchers.IO) {
            val local = runCatching { fileAccess.copyToLocal(path) }.getOrNull()
            if (local == null) {
                warnings.add("Cannot fetch drawable file: $displayToken")
                return@withContext null
            }
            when (path.substringAfterLast('.').lowercase()) {
                "png", "jpg", "jpeg", "webp", "bmp", "gif" ->
                    decodeBounded(local.absolutePath)?.let { BitmapDrawable(context.resources, it) }
                        ?: run {
                            warnings.add("Cannot decode raster: $displayToken")
                            null
                        }
                "xml" -> {
                    val body = runCatching { fileAccess.readFile(path) }.getOrNull()
                    if (body == null) {
                        warnings.add("Cannot read drawable XML: $displayToken")
                        null
                    } else {
                        runCatching { buildFromXml(body, displayToken) }.getOrNull()
                            ?: null.also { warnings.add("Unsupported drawable XML: $displayToken") }
                    }
                }
                else -> null
            }
        }

    private fun frameworkDrawable(token: String): Drawable? {
        val id = context.resources.getIdentifier(
            token.removePrefix("@android:drawable/"), "drawable", "android"
        )
        if (id == 0) {
            warnings.add("Unknown framework drawable: $token")
            return null
        }
        return runCatching { context.getDrawable(id) }.getOrNull()
            ?: run {
                warnings.add("Framework drawable unavailable: $token")
                null
            }
    }

    private fun decodeBounded(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (sample <= 16 &&
            bounds.outWidth / (sample * 2) >= MAX_RASTER_EDGE &&
            bounds.outHeight / (sample * 2) >= MAX_RASTER_EDGE
        ) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private suspend fun buildFromXml(body: String, displayToken: String): Drawable? {
        val parser = ProjectResourceIndex.newPullParser(body)
        while (parser.eventType != XmlPullParser.START_TAG) {
            if (parser.next() == XmlPullParser.END_DOCUMENT) return null
        }
        return when (parser.name) {
            "vector" -> vectorDrawable(parser)
            "shape" -> shapeDrawable(parser)
            "layer-list" -> firstLayer(parser, displayToken)
            "selector", "ripple" -> selectorLayer(parser, displayToken)
            "inset" -> insetDrawable(parser, displayToken)
            else -> {
                warnings.add("Unsupported drawable XML <${parser.name}>: $displayToken")
                null
            }
        }
    }

    /** Draws a <vector>'s paths into a bitmap; no VectorDrawable dependency needed. */
    private fun vectorDrawable(parser: XmlPullParser): Drawable? {
        var widthPx = 0
        var heightPx = 0
        for (i in 0 until parser.attributeCount) {
            when (parser.getAttributeName(i)) {
                "width" -> widthPx =
                    ResourceValues.parseDimensionPx(parser.getAttributeValue(i), density, density) ?: 0
                "height" -> heightPx =
                    ResourceValues.parseDimensionPx(parser.getAttributeValue(i), density, density) ?: 0
            }
        }
        if (widthPx <= 0 || heightPx <= 0) {
            widthPx = (24 * density).roundToInt()
            heightPx = (24 * density).roundToInt()
        }
        widthPx = widthPx.coerceIn(1, MAX_RASTER_EDGE)
        heightPx = heightPx.coerceIn(1, MAX_RASTER_EDGE)

        val paths = mutableListOf<PathSpec>()
        var current: PathSpec? = null
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "path") {
                    current = PathSpec()
                    for (i in 0 until parser.attributeCount) {
                        when (parser.getAttributeName(i)) {
                            "pathData" -> current!!.pathData = parser.getAttributeValue(i)
                            "fillColor" -> current!!.fillColor = parser.getAttributeValue(i)
                            "strokeColor" -> current!!.strokeColor = parser.getAttributeValue(i)
                            "strokeWidth" ->
                                current!!.strokeWidth = parser.getAttributeValue(i)?.toFloatOrNull() ?: 0f
                        }
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "path") {
                    current?.let { if (it.pathData != null) paths += it }
                    current = null
                }
            }
            event = parser.next()
        }
        if (paths.isEmpty()) return null

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Vectors are authored on a 24x24 viewport; scale path coordinates to the bitmap.
        val matrix = android.graphics.Matrix().apply {
            postScale(widthPx / VIEWPORT, heightPx / VIEWPORT)
        }
        for (spec in paths) {
            val path = runCatching {
                spec.pathData?.let { PathParser.createPathFromPathData(it) }
            }.getOrNull() ?: continue
            path.transform(matrix)
            val fill = spec.fillColor?.let { ResourceValues.parseColor(it) } ?: Color.BLACK
            if (Color.alpha(fill) != 0) {
                canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
            }
            val stroke = spec.strokeColor?.let { ResourceValues.parseColor(it) }
            if (stroke != null && Color.alpha(stroke) != 0 && spec.strokeWidth > 0f) {
                canvas.drawPath(
                    path,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        color = stroke
                        strokeWidth = spec.strokeWidth
                        isDither = true
                    }
                )
            }
        }
        return BitmapDrawable(context.resources, bitmap)
    }

    private class PathSpec {
        var pathData: String? = null
        var fillColor: String? = null
        var strokeColor: String? = null
        var strokeWidth: Float = 0f
    }

    private fun shapeDrawable(parser: XmlPullParser): Drawable {
        val drawable = GradientDrawable()
        drawable.shape = when (attrOf(parser, "shape")) {
            "oval" -> GradientDrawable.OVAL
            "line" -> GradientDrawable.LINE
            "ring" -> GradientDrawable.RING
            else -> GradientDrawable.RECTANGLE
        }
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "solid" -> drawableColor(parser, "color")?.let { drawable.setColor(it) }
                    "corners" -> {
                        dimAttr(parser, "radius")?.let { drawable.cornerRadius = it }
                        cornerRadii(parser)?.let { drawable.setCornerRadii(it) }
                    }
                    "stroke" -> {
                        val width = (dimAttr(parser, "width") ?: 0f).roundToInt()
                        drawableColor(parser, "color")?.let { drawable.setStroke(width, it) }
                    }
                    "gradient" -> {
                        val start = drawableColor(parser, "startColor") ?: DEFAULT_GRADIENT
                        val end = drawableColor(parser, "endColor") ?: start
                        drawable.setColors(intArrayOf(start, end))
                        drawable.setOrientation(gradientOrientation(angleAttr(parser)))
                    }
                    "size" -> {
                        val width = dimAttr(parser, "width")?.roundToInt() ?: 0
                        val height = dimAttr(parser, "height")?.roundToInt() ?: 0
                        if (width > 0 && height > 0) drawable.setSize(width, height)
                    }
                }
            }
            event = parser.next()
        }
        return drawable
    }

    private fun gradientOrientation(angle: Int) = when (angle) {
        in 45..135 -> GradientDrawable.Orientation.BL_TR
        in 136..225 -> GradientDrawable.Orientation.RIGHT_LEFT
        in 226..315 -> GradientDrawable.Orientation.TR_BL
        else -> GradientDrawable.Orientation.TOP_BOTTOM
    }

    private fun cornerRadii(parser: XmlPullParser): FloatArray? {
        val tl = dimAttr(parser, "topLeftRadius")
        val tr = dimAttr(parser, "topRightRadius")
        val bl = dimAttr(parser, "bottomLeftRadius")
        val br = dimAttr(parser, "bottomRightRadius")
        if (tl == null && tr == null && bl == null && br == null) return null
        val topLeft = tl ?: 0f
        return floatArrayOf(
            topLeft, topLeft,
            tr ?: 0f, tr ?: 0f,
            br ?: 0f, br ?: 0f,
            bl ?: 0f, bl ?: 0f
        )
    }

    /** Layer-list keeps only its first layer (flat approximation). */
    private suspend fun firstLayer(parser: XmlPullParser, displayToken: String): Drawable? {
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                for (i in 0 until parser.attributeCount) {
                    when (parser.getAttributeName(i)) {
                        "drawable" -> return resolve(parser.getAttributeValue(i))
                        "color" -> drawableColor(parser, "color")?.let { return ColorDrawable(it) }
                    }
                }
            }
            event = parser.next()
        }
        warnings.add("Layer-list has no drawable layer: $displayToken")
        return null
    }

    /** Selectors render their default (state-less) layer; first layer as fallback. */
    private suspend fun selectorLayer(parser: XmlPullParser, displayToken: String): Drawable? {
        var first: Drawable? = null
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                var hasStates = false
                var layer: Drawable? = null
                for (i in 0 until parser.attributeCount) {
                    when (parser.getAttributeName(i)) {
                        "drawable" -> layer = resolve(parser.getAttributeValue(i))
                        "color" -> drawableColor(parser, "color")?.let { layer = ColorDrawable(it) }
                        else -> hasStates = true
                    }
                }
                if (!hasStates && layer != null) return layer
                if (first == null) first = layer
            }
            event = parser.next()
        }
        if (first == null) warnings.add("Selector without a default layer: $displayToken")
        return first
    }

    /** Inset drawables wrap their inner drawable; padding is dropped (approximation). */
    private suspend fun insetDrawable(parser: XmlPullParser, displayToken: String): Drawable? {
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == "drawable") {
                        return resolve(parser.getAttributeValue(i))
                    }
                }
                return null
            }
            event = parser.next()
        }
        warnings.add("Empty inset drawable: $displayToken")
        return null
    }

    private fun attrOf(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == name) return parser.getAttributeValue(i)
        }
        return null
    }

    private fun dimAttr(parser: XmlPullParser, name: String): Float? {
        val raw = attrOf(parser, name) ?: return null
        return ResourceValues.parseDimensionPx(raw, density, density)?.toFloat()
    }

    /** Resolves a color attr that may be "#hex", "@color/x", "@android:color/x" or "?attr/x". */
    private fun drawableColor(parser: XmlPullParser, name: String): Int? {
        val raw = attrOf(parser, name) ?: return null
        val value = raw.trim()
        return when {
            value.startsWith("#") -> ResourceValues.parseColor(value)
            value.startsWith("@color/") -> index.color(value.removePrefix("@color/"))
                ?.let { ResourceValues.parseColor(it) }
                ?: run {
                    warnings.add("Color resource not found: $value")
                    null
                }
            value.startsWith("@android:color/") -> ProjectResourceIndex.ANDROID_COLORS[
                value.removePrefix("@android:color/")
            ]?.let { ResourceValues.parseColor(it) }
            value.startsWith("?") -> themeColor(value)
            else -> {
                warnings.add("Unsupported color value in drawable: $value")
                null
            }
        }
    }

    private fun angleAttr(parser: XmlPullParser): Int = attrOf(parser, "angle")?.toIntOrNull() ?: 0

    private companion object {
        const val MAX_RASTER_EDGE = 2048
        const val VIEWPORT = 24f
        val DEFAULT_GRADIENT = 0xFF9E9E9E.toInt()

        /** Theme attr names shared by both themes. */
        val SINGLE = mapOf(
            "colordivider" to 0x1F000000.toInt(),
            "colorcontrolnormal" to 0x8A000000.toInt()
        )

        /** Theme attr names with (light, dark) pairs, keyed lowercase. */
        val PAIRS = mapOf(
            "colorprimary" to (0xFF3F51B5.toInt() to 0xFF2196F3.toInt()),
            "colorprimarydark" to (0xFF303F9F.toInt() to 0xFF1976D2.toInt()),
            "colorprimaryvariant" to (0xFF303F9F.toInt() to 0xFF1976D2.toInt()),
            "coloraccent" to (0xFFFF4081.toInt() to 0xFFFFAB40.toInt()),
            "coloronprimary" to (0xFFFFFFFF.toInt() to 0xFFFFFFFF.toInt()),
            "colorsurface" to (0xFFFFFFFF.toInt() to 0xFF121212.toInt()),
            "coloronsurface" to (0xDE000000.toInt() to 0xFFFFFFFF.toInt()),
            "colorerror" to (0xFFB00020.toInt() to 0xFFCF6679.toInt()),
            "androidcolorprimary" to (0xFF3F51B5.toInt() to 0xFF2196F3.toInt()),
            "androidcoloraccent" to (0xFFFF4081.toInt() to 0xFFFFAB40.toInt()),
            "androidtextcolorprimary" to (0xDE000000.toInt() to 0xFFFFFFFF.toInt()),
            "androidtextcolorsecondary" to (0x8A000000.toInt() to 0xB3FFFFFF.toInt())
        )
    }
}

/** Shared cap on distinct warnings so pathological layouts don't flood tool output. */
class WarningsCollector {

    private val items = LinkedHashSet<String>()

    fun add(message: String) {
        if (items.size < MAX) items += message
    }

    fun snapshot(): List<String> = items.toList()

    private companion object {
        const val MAX = 50
    }
}
