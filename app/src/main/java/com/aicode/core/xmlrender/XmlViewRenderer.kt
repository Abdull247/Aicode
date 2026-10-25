package com.aicode.core.xmlrender

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import android.widget.Toolbar
import com.aicode.feature.workspace.domain.FileAccessProvider

/**
 * Turns a [LayoutNode] tree into real framework views and applies a whitelisted attribute
 * subset. Lives on the main thread while invoked (view instantiation); resource-file work
 * happens earlier in [preheat].
 *
 * Design constraints:
 * - No arbitrary class instantiation: unknown/custom/fragment tags become labeled
 *   placeholders (recorded as warnings), so previews stay deterministic and safe.
 * - androidx Material/AppCompat tags map onto lookalike framework widgets; support-library
 *   specifics (ConstraintLayout rules, CoordinatorLayout behaviors) degrade with a note.
 * - Missing resources degrade to defaults plus warnings; rendering never aborts mid-build.
 */
class XmlViewRenderer(
    context: Context,
    fileAccess: FileAccessProvider,
    val index: ProjectResourceIndex,
    private val dark: Boolean,
    private val warnings: WarningsCollector
) {

    private val themedContext = ContextThemeWrapper(
        context,
        if (dark) android.R.style.Theme_Material_NoActionBar
        else android.R.style.Theme_Material_Light_NoActionBar
    )
    private val density: Float = themedContext.resources.displayMetrics.density
    private val scaledDensity: Float = themedContext.resources.displayMetrics.scaledDensity
    private val drawables = MiniDrawables(themedContext, fileAccess, index, dark, warnings)
    private val idRegistry = LinkedHashMap<String, Int>()
    private val drawableCache = LinkedHashMap<String, Drawable?>()

    /** Collects ids and decodes file-backed drawables off the main thread, into caches. */
    suspend fun preheat(root: LayoutNode) {
        collectIds(root)
        val tokens = LinkedHashSet<String>()
        collectDrawableTokens(root, tokens)
        for (token in tokens) {
            drawableCache[token] = runCatching { drawables.resolve(token) }.getOrNull()
        }
    }

    /** Builds the render host measured exactly [widthPx] x [heightPx]. Main thread only. */
    fun renderHost(root: LayoutNode, widthPx: Int, heightPx: Int): FrameLayout {
        val host = FrameLayout(themedContext)
        host.setBackgroundColor(if (dark) HOST_BG_DARK else HOST_BG_LIGHT)
        val roots = if (root.tag == "merge") root.children else listOf(root)
        for (childNode in roots) {
            attach(host, childNode, topLevel = true)
        }
        host.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        host.layout(0, 0, widthPx, heightPx)
        return host
    }

    // ---- Ids ------------------------------------------------------------------

    private fun collectIds(node: LayoutNode) {
        node.idName?.takeIf { it.isNotBlank() && it !in idRegistry }?.let {
            idRegistry[it] = View.generateViewId()
        }
        for (child in node.children) collectIds(child)
    }

    private fun idFor(ref: String?): Int? {
        if (ref == null) return null
        if (!ref.startsWith("@id/") && !ref.startsWith("@+id/")) return null
        val name = ref.substringAfterLast('/')
        return idRegistry[name] ?: run {
            warnings.add("Unresolved id reference: $ref")
            null
        }
    }

    // ---- Value helpers ----------------------------------------------------------

    private fun drawableFor(token: String?): Drawable? {
        if (token.isNullOrBlank()) return null
        val value = token.trim()
        if (value.startsWith("@drawable/") || value.startsWith("@mipmap/")) {
            return drawableCache[value]
        }
        // File-backed tokens were preheated; the rest resolve synchronously (no IO).
        return runCatching { drawables.resolveSync(value) }.getOrNull()
    }

    private fun colorFor(token: String?): Int? {
        if (token.isNullOrBlank()) return null
        val value = token.trim()
        return when {
            value.startsWith("#") -> ResourceValues.parseColor(value)
            value.startsWith("@color/") -> index.color(value.removePrefix("@color/"))
                ?.let { ResourceValues.parseColor(it) }
                ?: null.also { warnings.add("Color resource not found: $value") }
            value.startsWith("@android:color/") -> ProjectResourceIndex.ANDROID_COLORS[
                value.removePrefix("@android:color/")
            ]?.let { ResourceValues.parseColor(it) }
            value.startsWith("?") -> drawables.themeColor(value)
            else -> ResourceValues.parseColor(value)
        }
    }

    private fun textFor(token: String?): CharSequence? {
        if (token.isNullOrBlank()) return null
        val value = token.trim()
        return when {
            value.startsWith("@string/") -> index.string(value.removePrefix("@string/"))
                ?: null.also { warnings.add("String resource not found: $value") }
            value.startsWith("@android:string/") -> {
                val id = themedContext.resources.getIdentifier(
                    value.removePrefix("@android:string/"), "string", "android"
                )
                if (id != 0) themedContext.resources.getString(id) else null
            }
            else -> unescape(value)
        }
    }

    private fun unescape(raw: String): String = raw
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\'", "'")
        .replace("\\\"", "\"")

    private fun dimenFor(token: String?): Int? {
        if (token.isNullOrBlank()) return null
        val value = token.trim()
        val resolved = if (value.startsWith("@dimen/")) {
            index.dimen(value.removePrefix("@dimen/"))
                ?: null.also { warnings.add("Dimen resource not found: $value") }
        } else value
        return resolved?.let { ResourceValues.parseDimensionPx(it, density, scaledDensity) }
    }

    // ---- Attr source: style chain merged under node attrs ------------------------

    private fun effectiveAttrs(node: LayoutNode): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        // Walk the parent chain root-first: parents apply, then children, then node attrs.
        val resolved = ArrayList<ProjectResourceIndex.StyleDef>()
        val seen = LinkedHashSet<String>()
        var current: String? = node.attrs["style"]
        var depth = 0
        while (current != null && depth++ < MAX_STYLE_DEPTH) {
            val name = current.removePrefix("@style/")
            if (name.isEmpty() || !seen.add(name)) break
            val style = index.style(name)
            if (style == null) {
                // Framework/library appearances (TextAppearance.AppCompat and friends) are not
                // re-declared in the project; skip silently, they only default text styling.
                if (!name.contains('.')) warnings.add("Style not found: $name")
                break
            }
            resolved.add(0, style)
            current = style.parent
        }
        for (style in resolved) {
            for ((k, v) in style.items) merged[k] = v
        }
        for ((k, v) in node.attrs) {
            if (k == "style") continue
            merged[k] = v
        }
        return merged
    }

    private fun collectDrawableTokens(node: LayoutNode, out: LinkedHashSet<String>) {
        for ((key, value) in node.attrs) {
            if (key in DRAWABLE_ATTR_KEYS &&
                (value.startsWith("@drawable/") || value.startsWith("@mipmap/"))
            ) {
                out += value.trim()
            }
        }
        // Styles can carry drawable values too.
        node.attrs["style"]?.removePrefix("@style/")?.let { name ->
            mergeStyleChain(name, out)
        }
        for (child in node.children) collectDrawableTokens(child, out)
    }

    private fun mergeStyleChain(name: String, out: LinkedHashSet<String>) {
        var current: String? = name
        var depth = 0
        while (current != null && depth++ < MAX_STYLE_DEPTH) {
            val n = current.removePrefix("@style/")
            val style = index.style(n) ?: return
            for (v in style.items.values) {
                if (v.startsWith("@drawable/") || v.startsWith("@mipmap/")) out += v.trim()
            }
            current = style.parent
        }
    }

    // ---- Tag mapping -------------------------------------------------------------

    private class TagMapping(val simple: String?, val placeholderLabel: String?)

    private fun mapTag(rawTag: String): TagMapping {
        var tag = rawTag
        for (prefix in STRIPPABLE_PREFIXES) {
            while (tag.startsWith(prefix)) tag = tag.removePrefix(prefix)
        }
        val simple = tag.substringAfterLast('.')
        return when {
            simple in WHITELIST -> TagMapping(simple, null)
            simple in DEGRADED_MAP -> {
                val mapped = DEGRADED_MAP[simple]!!
                if (mapped == null) {
                    TagMapping(null, simple)
                } else {
                    warnings.add("$simple is rendered as a lookalike framework widget")
                    TagMapping(mapped, null)
                }
            }
            else -> TagMapping(null, simple)
        }
    }

    // ---- Building ----------------------------------------------------------------

    private fun attach(parent: ViewGroup, node: LayoutNode, topLevel: Boolean) {
        val view = buildView(node) ?: return
        view.layoutParams = makeLayoutArgs(parent, effectiveAttrs(node), topLevel)
        parent.addView(view)
    }

    private fun buildView(node: LayoutNode): View? {
        val mapping = mapTag(node.tag)
        if (mapping.simple == null) return placeholder(node, mapping.placeholderLabel ?: node.tag)
        val view = try {
            instantiate(mapping.simple)
        } catch (e: Exception) {
            warnings.add("Cannot instantiate ${node.tag}: ${e.message}")
            return placeholder(node, mapping.placeholderLabel ?: node.tag)
        }
        val attrs = effectiveAttrs(node)
        applyCommon(view, attrs)
        applyTyped(view, node.tag, attrs)
        if (view is ViewGroup) {
            for (child in node.children) attach(view, child, topLevel = false)
        }
        node.note?.let { warnings.add(it) }
        return view
    }

    private fun instantiate(simpleName: String): View = when (simpleName) {
        "TextView" -> TextView(themedContext)
        "EditText" -> EditText(themedContext)
        "Button" -> Button(themedContext)
        "ImageView" -> ImageView(themedContext)
        "ImageButton" -> ImageButton(themedContext)
        "CheckBox" -> CheckBox(themedContext)
        "RadioButton" -> RadioButton(themedContext)
        "Switch" -> Switch(themedContext)
        "CheckedTextView" -> CheckedTextView(themedContext)
        "ProgressBar" -> ProgressBar(themedContext)
        "SeekBar" -> SeekBar(themedContext)
        "LinearLayout" -> LinearLayout(themedContext)
        "FrameLayout" -> FrameLayout(themedContext)
        "RelativeLayout" -> RelativeLayout(themedContext)
        "GridLayout" -> GridLayout(themedContext)
        "ScrollView" -> ScrollView(themedContext)
        "HorizontalScrollView" -> HorizontalScrollView(themedContext)
        "Space" -> Space(themedContext)
        "View" -> View(themedContext)
        "Toolbar" -> Toolbar(themedContext)
        else -> throw IllegalArgumentException("Tag not whitelisted: $simpleName")
    }

    private fun placeholder(node: LayoutNode, label: String): View {
        val box = FrameLayout(themedContext)
        val background = GradientDrawable().apply {
            setColor(if (dark) PLACEHOLDER_BG_DARK else PLACEHOLDER_BG_LIGHT)
            setStroke(
                (density * 0.75f).toInt().coerceAtLeast(1),
                PLACEHOLDER_STROKE
            )
            cornerRadius = 6 * density
        }
        box.background = background
        val text = TextView(themedContext).apply {
            text = label.substringAfterLast('.')
            setTextColor(if (dark) PLACEHOLDER_TEXT_DARK else PLACEHOLDER_TEXT_LIGHT)
            textSize = 11f
            gravity = Gravity.CENTER
        }
        box.addView(
            text,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        if (node.tag != "empty" && node.tag != "include") {
            warnings.add("'${node.tag}' rendered as a labeled placeholder")
        }
        return box
    }

    // ---- Attribute application ----------------------------------------------------

    private fun applyCommon(view: View, attrs: Map<String, String>) {
        view.id = idFor(attrs["android:id"]) ?: view.id

        // Padding family: base < horizontal/vertical < directional < start/end.
        paddingValue(attrs)?.let { (left, top, right, bottom) ->
            view.setPadding(left, top, right, bottom)
        }

        attrs["android:background"]?.let { token ->
            val drawable = drawableFor(token)
            if (drawable != null) {
                view.background = drawable
            } else {
                warnings.add("Background not resolved: $token")
            }
        }

        when (attrs["android:visibility"]) {
            "gone" -> view.visibility = View.GONE
            "invisible" -> view.visibility = View.INVISIBLE
        }
        attrs["android:alpha"]?.toFloatOrNull()?.let { view.alpha = it }
        attrs["android:elevation"]?.let { dimenFor(it)?.toFloat()?.let { px -> view.elevation = px } }
        attrs["android:rotation"]?.toFloatOrNull()?.let { view.rotation = it }
        attrs["android:enabled"]?.let { view.isEnabled = it.toBoolean() }
        attrs["android:minimumWidth"]?.let { dimenFor(it)?.let { v -> view.minimumWidth = v } }
        attrs["android:minimumHeight"]?.let { dimenFor(it)?.let { v -> view.minimumHeight = v } }
    }

    private fun paddingValue(attrs: Map<String, String>): IntArray? {
        val hasAny = attrs.keys.any { it.startsWith("android:padding") }
        if (!hasAny) return null
        fun px(key: String): Int? = dimenFor(attrs[key])
        // Resolve in priority order; later calls refine individual edges (LTR mapping).
        val any = px("android:padding")
        val horizontal = px("android:paddingHorizontal")
        val vertical = px("android:paddingVertical")
        var left = any ?: horizontal ?: 0
        var right = any ?: horizontal ?: 0
        var top = any ?: vertical ?: 0
        var bottom = any ?: vertical ?: 0
        px("android:paddingLeft")?.let { left = it }
        px("android:paddingRight")?.let { right = it }
        px("android:paddingTop")?.let { top = it }
        px("android:paddingBottom")?.let { bottom = it }
        px("android:paddingStart")?.let { left = it }
        px("android:paddingEnd")?.let { right = it }
        return intArrayOf(left, top, right, bottom)
    }

    private fun applyTyped(view: View, rawTag: String, attrs: Map<String, String>) {
        fun get(key: String): String? = attrs[key]?.takeIf { it.isNotBlank() }

        if (view is TextView) {
            get("android:text")?.let { textFor(it)?.let { t -> view.text = t } }
            get("android:hint")?.let { textFor(it)?.let { t -> view.hint = t } }
            get("android:textSize")?.let { dimenFor(it)?.let { px -> view.textSize = px / scaledDensity } }
            get("android:textColor")?.let { colorFor(it)?.let { c -> view.setTextColor(c) } }
            get("android:textColorHint")?.let { colorFor(it)?.let { c -> view.setHintTextColor(c) } }
            get("android:textStyle")?.let { style ->
                val bold = style.contains("bold")
                val italic = style.contains("italic")
                view.setTypeface(view.typeface, (if (bold) Typeface.BOLD else 0) or
                        (if (italic) Typeface.ITALIC else 0))
            }
            get("android:typeface")?.let { face ->
                view.typeface = when (face) {
                    "monospace" -> Typeface.MONOSPACE
                    "serif" -> Typeface.SERIF
                    "sans_serif" -> Typeface.SANS_SERIF
                    else -> Typeface.DEFAULT
                }
            }
            for (key in listOf("android:gravity", "android:textAlignment")) {
                if (key == "android:gravity") {
                    get(key)?.let { view.gravity = ResourceValues.parseGravity(it) }
                } else {
                    get(key)?.let { alignment ->
                        if (alignment != "inherit") {
                            view.textAlignment = when (alignment) {
                                "center" -> TextView.TEXT_ALIGNMENT_CENTER
                                "textStart" -> TextView.TEXT_ALIGNMENT_TEXT_START
                                "textEnd" -> TextView.TEXT_ALIGNMENT_TEXT_END
                                "viewStart" -> TextView.TEXT_ALIGNMENT_VIEW_START
                                "viewEnd" -> TextView.TEXT_ALIGNMENT_VIEW_END
                                else -> TextView.TEXT_ALIGNMENT_GRAVITY
                            }
                        }
                    }
                }
            }
            get("android:ellipsize")?.let { value ->
                view.ellipsize = when (value) {
                    "start" -> TextUtils.TruncateAt.START
                    "middle" -> TextUtils.TruncateAt.MIDDLE
                    "end", "marquee" -> TextUtils.TruncateAt.END
                    else -> null
                }
            }
            get("android:maxLines")?.toIntOrNull()?.let { view.maxLines = it }
            get("android:minLines")?.toIntOrNull()?.let { view.minLines = it }
            get("android:lines")?.toIntOrNull()?.let { view.setLines(it) }
            get("android:ems")?.toIntOrNull()?.let { view.setEms(it) }
            get("android:letterSpacing")?.toFloatOrNull()?.let { view.letterSpacing = it }
            get("android:textAllCaps")?.toBoolean()?.let { view.isAllCaps = it }
            get("android:drawablePadding")?.let { dimenFor(it)?.let { px -> view.compoundDrawablePadding = px } }
            // Compound drawables: prefer start/end, fall back to the directional pair.
            val start = drawableFor(get("android:drawableStart") ?: get("android:drawableLeft"))
            val end = drawableFor(get("android:drawableEnd") ?: get("android:drawableRight"))
            val top = drawableFor(get("android:drawableTop"))
            val bottom = drawableFor(get("android:drawableBottom"))
            if (anyNotNull(start, end, top, bottom)) {
                view.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    start, top, end, bottom
                )
            }
        }

        if (view is EditText) {
            get("android:inputType")?.let { type -> view.inputType = inputTypeFor(type) }
            get("android:imeOptions")?.let { options -> view.imeOptions = imeOptionsFor(options) }
        }

        if (view is android.widget.CompoundButton) {
            get("android:checked")?.toBoolean()?.let { view.isChecked = it }
        }
        if (view is CheckedTextView) {
            get("android:checked")?.toBoolean()?.let { view.isChecked = it }
        }

        if (view is ImageView) {
            get("android:src")?.let { token ->
                drawableFor(token)?.let { view.setImageDrawable(it) }
                    ?: colorFor(token)?.let { view.setColorFilter(it) }
            }
            get("android:scaleType")?.let { value ->
                view.scaleType = when (value) {
                    "fitXY" -> ImageView.ScaleType.FIT_XY
                    "fitStart" -> ImageView.ScaleType.FIT_START
                    "fitEnd" -> ImageView.ScaleType.FIT_END
                    "center" -> ImageView.ScaleType.CENTER
                    "centerCrop" -> ImageView.ScaleType.CENTER_CROP
                    "centerInside" -> ImageView.ScaleType.CENTER_INSIDE
                    "matrix" -> ImageView.ScaleType.MATRIX
                    else -> ImageView.ScaleType.FIT_CENTER
                }
            }
            get("android:tint")?.let { colorFor(it)?.let { c -> view.setColorFilter(c) } }
            get("android:adjustViewBounds")?.toBoolean()?.let { view.adjustViewBounds = it }
            get("android:cropToPadding")?.toBoolean()?.let { view.cropToPadding = it }
            get("android:maxWidth")?.let { dimenFor(it)?.let { v -> view.maxWidth = v } }
            get("android:maxHeight")?.let { dimenFor(it)?.let { v -> view.maxHeight = v } }
        }

        if (view is ProgressBar) {
            get("android:max")?.toIntOrNull()?.let { view.max = it }
            get("android:progress")?.toIntOrNull()?.let { view.progress = it }
            get("android:indeterminate")?.toBoolean()?.let { view.isIndeterminate = it }
        }

        if (view is LinearLayout) {
            get("android:orientation")?.let { value ->
                view.orientation = if (value == "vertical") {
                    LinearLayout.VERTICAL
                } else {
                    LinearLayout.HORIZONTAL
                }
            }
            get("android:gravity")?.let { view.gravity = ResourceValues.parseGravity(it) }
            get("android:weightSum")?.toFloatOrNull()?.let { view.weightSum = it }
        }

        if (view is GridLayout) {
            get("android:rowCount")?.toIntOrNull()?.let { view.rowCount = it }
            get("android:columnCount")?.toIntOrNull()?.let { view.columnCount = it }
            get("android:orientation")?.let { value ->
                view.orientation = if (value == "vertical") GridLayout.VERTICAL else GridLayout.HORIZONTAL
            }
            get("android:useDefaultMargins")?.toBoolean()?.let { view.useDefaultMargins = it }
        }

        if (view is ScrollView) {
            get("android:fillViewport")?.toBoolean()?.let { view.setFillViewport(it) }
        }
        if (view is HorizontalScrollView) {
            get("android:fillViewport")?.toBoolean()?.let { view.setFillViewport(it) }
        }

        if (view is Toolbar) {
            get("android:title")?.let { textFor(it)?.let { t -> view.title = t } }
            get("android:subtitle")?.let { textFor(it)?.let { t -> view.subtitle = t } }
        }
    }

    private fun anyNotNull(vararg values: Any?): Boolean = values.any { it != null }

    private fun inputTypeFor(raw: String): Int {
        var type = InputType.TYPE_NULL
        for (part in raw.split('|').map { it.trim() }) {
            type = type or when (part) {
                "text" -> InputType.TYPE_CLASS_TEXT
                "textEmailAddress" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                "textPassword" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                "textVisiblePassword" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                "textMultiLine" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                "textCapWords" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                "textCapSentences" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                "textAutoCorrect" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                "textUri" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                "number" -> InputType.TYPE_CLASS_NUMBER
                "numberDecimal" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                "numberSigned" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                "numberPassword" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                "phone" -> InputType.TYPE_CLASS_PHONE
                "datetime" -> InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_NORMAL
                "date" -> InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE
                "time" -> InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
                else -> 0
            }
        }
        return if (type == InputType.TYPE_NULL) InputType.TYPE_CLASS_TEXT else type
    }

    private fun imeOptionsFor(raw: String): Int {
        var options = 0
        for (part in raw.split('|').map { it.trim() }) {
            options = options or when (part) {
                "actionDone" -> android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                "actionGo" -> android.view.inputmethod.EditorInfo.IME_ACTION_GO
                "actionNext" -> android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                "actionSearch" -> android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                "actionSend" -> android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                "flagNoExtractUi" -> android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                else -> 0
            }
        }
        return options
    }

    // ---- Layout params ------------------------------------------------------------

    private fun makeLayoutArgs(
        parent: ViewGroup,
        attrs: Map<String, String>,
        topLevel: Boolean
    ): ViewGroup.LayoutParams {
        fun get(key: String): String? = attrs[key]?.takeIf { it.isNotBlank() }
        fun dimension(key: String, default: Int): Int {
            val raw = get(key) ?: return default
            return when (raw) {
                "match_parent", "fill_parent" -> ViewGroup.LayoutParams.MATCH_PARENT
                "wrap_content" -> ViewGroup.LayoutParams.WRAP_CONTENT
                else -> dimenFor(raw) ?: default
            }
        }

        val defaultSize = if (topLevel) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val width = dimension("android:layout_width", defaultSize)
        val height = dimension("android:layout_height", defaultSize)

        val layoutParams: ViewGroup.LayoutParams = when (parent) {
            is GridLayout -> GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED)
            ).apply {
                this.width = width
                this.height = height
            }
            is LinearLayout -> LinearLayout.LayoutParams(width, height).apply {
                get("android:layout_weight")?.toFloatOrNull()?.let { weight = it }
                get("android:layout_gravity")?.let { gravity = ResourceValues.parseGravity(it) }
                applyMargins(this, attrs)
            }
            is RelativeLayout -> RelativeLayout.LayoutParams(width, height).apply {
                applyRelativeRules(this, attrs)
                applyMargins(this, attrs)
            }
            is FrameLayout -> FrameLayout.LayoutParams(width, height).apply {
                get("android:layout_gravity")?.let { gravity = ResourceValues.parseGravity(it) }
                applyMargins(this, attrs)
            }
            else -> ViewGroup.MarginLayoutParams(width, height).apply { applyMargins(this, attrs) }
        }
        return layoutParams
    }

    private fun applyMargins(lp: ViewGroup.MarginLayoutParams, attrs: Map<String, String>) {
        fun get(key: String): String? = attrs[key]?.takeIf { it.isNotBlank() }
        fun px(key: String): Int? {
            val raw = get(key) ?: return null
            return dimenFor(raw)
        }
        val all = px("android:layout_margin")
        val horizontal = px("android:layout_marginHorizontal")
        val vertical = px("android:layout_marginVertical")
        var left = all ?: horizontal ?: 0
        var right = all ?: horizontal ?: 0
        var top = all ?: vertical ?: 0
        var bottom = all ?: vertical ?: 0
        px("android:layout_marginLeft")?.let { left = it }
        px("android:layout_marginRight")?.let { right = it }
        px("android:layout_marginTop")?.let { top = it }
        px("android:layout_marginBottom")?.let { bottom = it }
        px("android:layout_marginStart")?.let { left = it }
        px("android:layout_marginEnd")?.let { right = it }
        lp.setMargins(left, top, right, bottom)
    }

    private fun applyRelativeRules(lp: RelativeLayout.LayoutParams, attrs: Map<String, String>) {
        fun get(key: String): String? = attrs[key]?.takeIf { it.isNotBlank() }
        fun boolRule(key: String, rule: Int) {
            if (get(key)?.toBoolean() == true) lp.addRule(rule, RelativeLayout.TRUE)
        }
        fun idRule(key: String, rule: Int) {
            get(key)?.let { idFor(it)?.let { id -> lp.addRule(rule, id) } }
        }
        boolRule("android:layout_centerInParent", RelativeLayout.CENTER_IN_PARENT)
        boolRule("android:layout_centerHorizontal", RelativeLayout.CENTER_HORIZONTAL)
        boolRule("android:layout_centerVertical", RelativeLayout.CENTER_VERTICAL)
        boolRule("android:layout_alignParentTop", RelativeLayout.ALIGN_PARENT_TOP)
        boolRule("android:layout_alignParentBottom", RelativeLayout.ALIGN_PARENT_BOTTOM)
        boolRule("android:layout_alignParentStart", RelativeLayout.ALIGN_PARENT_START)
        boolRule("android:layout_alignParentEnd", RelativeLayout.ALIGN_PARENT_END)
        boolRule("android:layout_alignParentLeft", RelativeLayout.ALIGN_PARENT_LEFT)
        boolRule("android:layout_alignParentRight", RelativeLayout.ALIGN_PARENT_RIGHT)
        idRule("android:layout_toStartOf", RelativeLayout.START_OF)
        idRule("android:layout_toEndOf", RelativeLayout.END_OF)
        idRule("android:layout_toLeftOf", RelativeLayout.LEFT_OF)
        idRule("android:layout_toRightOf", RelativeLayout.RIGHT_OF)
        idRule("android:layout_below", RelativeLayout.BELOW)
        idRule("android:layout_above", RelativeLayout.ABOVE)
        idRule("android:layout_alignStart", RelativeLayout.ALIGN_START)
        idRule("android:layout_alignEnd", RelativeLayout.ALIGN_END)
        idRule("android:layout_alignTop", RelativeLayout.ALIGN_TOP)
        idRule("android:layout_alignBottom", RelativeLayout.ALIGN_BOTTOM)
    }

    private companion object {
        const val MAX_STYLE_DEPTH = 8
        val HOST_BG_LIGHT = 0xFFFFFFFF.toInt()
        val HOST_BG_DARK = 0xFF121212.toInt()
        val PLACEHOLDER_BG_LIGHT = 0xFFF0F1F3.toInt()
        val PLACEHOLDER_BG_DARK = 0xFF26282C.toInt()
        val PLACEHOLDER_STROKE = 0x33999999.toInt()
        val PLACEHOLDER_TEXT_LIGHT = 0xFF5F6368.toInt()
        val PLACEHOLDER_TEXT_DARK = 0xFF9AA0A6.toInt()

        val DRAWABLE_ATTR_KEYS = setOf(
            "android:background", "android:src", "android:foreground",
            "android:drawableStart", "android:drawableLeft", "android:drawableTop",
            "android:drawableEnd", "android:drawableRight", "android:drawableBottom",
            "android:thumb", "android:button"
        )

        val WHITELIST = setOf(
            "TextView", "EditText", "Button", "ImageView", "ImageButton", "CheckBox",
            "RadioButton", "Switch", "CheckedTextView", "ProgressBar", "SeekBar",
            "LinearLayout", "FrameLayout", "RelativeLayout", "GridLayout", "ScrollView",
            "HorizontalScrollView", "Space", "View", "Toolbar"
        )

        /** Support-library/Material tags -> lookalike framework widget (null = placeholder). */
        val DEGRADED_MAP = mapOf(
            "ConstraintLayout" to "FrameLayout",
            "CoordinatorLayout" to "FrameLayout",
            "CardView" to "FrameLayout",
            "MaterialCardView" to "FrameLayout",
            "AppBarLayout" to null,
            "CollapsingToolbarLayout" to "FrameLayout",
            "RecyclerView" to null,
            "ViewPager" to null,
            "ViewPager2" to null,
            "NestedScrollView" to "ScrollView",
            "SwipeRefreshLayout" to "FrameLayout",
            "TabLayout" to null,
            "BottomNavigationView" to null,
            "NavigationView" to null,
            "DrawerLayout" to "FrameLayout",
            "TextInputLayout" to "LinearLayout",
            "TextInputEditText" to "EditText",
            "Chip" to "CheckBox",
            "ChipGroup" to "LinearLayout",
            "FloatingActionButton" to "Button",
            "MaterialButton" to "Button",
            "MaterialTextView" to "TextView",
            "ShapeableImageView" to "ImageView",
            "WebView" to null,
            "ListView" to null,
            "GridView" to null,
            "SurfaceView" to null,
            "TextureView" to null,
            "ViewStub" to null
        )

        val STRIPPABLE_PREFIXES = listOf(
            "android.widget.", "android.view.", "android.support.v7.widget.",
            "android.support.v4.widget.", "androidx.appcompat.widget.",
            "androidx.core.widget.", "androidx.recyclerview.widget.",
            "androidx.cardview.widget.", "androidx.constraintlayout.widget.",
            "androidx.constraintlayout.helper.widget.", "androidx.coordinatorlayout.widget.",
            "androidx.viewpager.widget.", "androidx.viewpager2.widget.",
            "androidx.swiperefreshlayout.widget.", "androidx.drawerlayout.widget.",
            "androidx.gridlayout.widget.", "com.google.android.material.button.",
            "com.google.android.material.textview.", "com.google.android.material.card.",
            "com.google.android.material.floatingactionbutton.",
            "com.google.android.material.chip.", "com.google.android.material.bottomnavigation.",
            "com.google.android.material.navigation.", "com.google.android.material.textfield.",
            "com.google.android.material.appbar.", "com.google.android.material.tabs.",
            "com.google.android.material.imageview.", "com.google.android.material.swiperefreshlayout."
        )
    }
}
