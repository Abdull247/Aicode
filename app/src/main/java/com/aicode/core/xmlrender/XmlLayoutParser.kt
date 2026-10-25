package com.aicode.core.xmlrender

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Parses Android layout XML into a [LayoutNode] tree.
 *
 * Namespace declarations are intentionally OFF: attribute names are split on ':' manually.
 * This keeps prefixed names stable ("android:text") regardless of the underlying pull parser.
 *
 * Include/merge handling:
 * - `<include layout="@layout/other">` is inlined from [layoutResolver]; extra attributes
 *   on the include tag (except android:id and layout_* ones) override the included root's.
 * - A `<merge>` root reached through an include is spliced into the including parent's children.
 * - A top-level `<merge>` root is kept as-is; the renderer maps it onto a FrameLayout host.
 * - `<fragment>` and unknown class tags are kept as nodes with a [LayoutNode.note] so the
 *   renderer draws labeled placeholders instead of failing the whole render.
 *
 * Include recursion is capped at [MAX_INCLUDE_DEPTH]; deeper chains yield a placeholder.
 */
class XmlLayoutParser {

    fun parse(
        xml: String,
        sourceName: String,
        layoutResolver: (name: String) -> String? = { null }
    ): LayoutNode = parseInternal(xml, sourceName, layoutResolver, depth = 0)

    private fun parseInternal(
        xml: String,
        sourceName: String,
        layoutResolver: (String) -> String?,
        depth: Int
    ): LayoutNode {
        val parser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }.newPullParser()
        parser.setInput(StringReader(xml))
        while (parser.next() != XmlPullParser.START_TAG) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) {
                return placeholder("empty", "Empty or malformed layout: $sourceName")
            }
        }
        return parseNode(parser, sourceName, layoutResolver, depth)
    }

    private fun parseNode(
        parser: XmlPullParser,
        sourceName: String,
        layoutResolver: (String) -> String?,
        depth: Int
    ): LayoutNode {
        val tag = parser.name.orEmpty()
        val attrs = collectAttrs(parser)

        if (tag == "include") {
            return parseInclude(attrs, sourceName, layoutResolver, depth)
        }

        val node = LayoutNode(tag = tag, attrs = attrs, source = sourceName)
        when {
            tag == "fragment" ->
                node.note = "Fragment tag renders as a placeholder; its fragment class is not instantiated"
            looksLikeCustomView(tag) ->
                node.note = "Unknown view '$tag' renders as a placeholder"
        }

        var inside = parser.next()
        while (inside != XmlPullParser.END_TAG) {
            if (inside == XmlPullParser.START_TAG) {
                val child = parseNode(parser, sourceName, layoutResolver, depth)
                if (child.tag == "merge" && node.children.isEmpty()) {
                    node.children += child.children
                } else {
                    node.children += child
                }
            }
            inside = parser.next()
        }
        return node
    }

    private fun parseInclude(
        attrs: Map<String, String>,
        sourceName: String,
        layoutResolver: (String) -> String?,
        depth: Int
    ): LayoutNode {
        val ref = attrs["layout"]?.removePrefix("@layout/")?.takeIf { it.isNotBlank() }
            ?: return placeholder("include", "include without layout attribute")
        if (depth >= MAX_INCLUDE_DEPTH) {
            return placeholder("include", "Include chain too deep (limit $MAX_INCLUDE_DEPTH): $ref")
        }
        val xml = runCatching { layoutResolver(ref) }.getOrNull()
            ?: return placeholder("include", "Included layout not found: @layout/$ref")
        val root = parseInternal(xml, "@layout/$ref", layoutResolver, depth = depth + 1)
        val inlined = if (root.tag == "merge") {
            LayoutNode(tag = "FrameLayout", attrs = emptyMap(), source = root.source).apply {
                children += root.children
            }
        } else {
            root
        }
        for ((key, value) in attrs) {
            if (key == "layout" || key == "android:id" || key.startsWith("layout_")) continue
            inlined.attrs = inlined.attrs + (key to value)
        }
        return inlined
    }

    private fun collectAttrs(parser: XmlPullParser): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (i in 0 until parser.attributeCount) {
            val rawName = parser.getAttributeName(i)
            val prefix = parser.getAttributePrefix(i)
            val key = if (prefix.isNullOrBlank()) rawName else "$prefix:$rawName"
            map[key] = parser.getAttributeValue(i) ?: ""
        }
        return map
    }

    private fun placeholder(tag: String, note: String): LayoutNode = LayoutNode(
        tag = tag,
        attrs = emptyMap(),
        source = null,
        note = note
    )

    private fun looksLikeCustomView(tag: String): Boolean {
        if (!tag.contains('.')) return false
        return tag.substringAfterLast('.') !in KNOWN_SIMPLE_NAMES
    }

    private companion object {
        const val MAX_INCLUDE_DEPTH = 8

        /** Custom-view tags whose simple class names map to renderable framework widgets. */
        val KNOWN_SIMPLE_NAMES = setOf(
            "TextView", "EditText", "Button", "ImageView", "ImageButton", "CheckBox",
            "RadioButton", "Switch", "CheckedTextView", "ProgressBar", "SeekBar",
            "LinearLayout", "FrameLayout", "RelativeLayout", "GridLayout", "ScrollView",
            "HorizontalScrollView", "Space", "View", "Toolbar", "ListView", "GridView",
            "WebView", "TextureView", "SurfaceView", "CardView", "ConstraintLayout",
            "CoordinatorLayout", "RecyclerView", "ViewPager", "ViewPager2", "SwipeRefreshLayout",
            "FloatingActionButton", "ChipGroup"
        )
    }
}
