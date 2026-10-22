package com.aicode.feature.network.presentation

import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aicode.feature.editor.domain.TextMateSetup
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.UndoManager
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun JsonCodeEditor(
    value: String,
    onValueChange: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    val lastLocal = remember { mutableStateOf(value) }
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val themeSurface = MaterialTheme.colorScheme.surface

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            TextMateSetup.ensureInitialized(context.applicationContext)
            runCatching { TextMateLanguage.create(JSON_SCOPE, false).destroy() }
        }
        ready = true
    }

    if (!ready) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextMateSetup.applyTheme(dark)
            UndoManager.setMergeTimeLimit(500L)
            CodeEditor(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isEditable = !readOnly
                typefaceText = Typeface.MONOSPACE
                setTextSize(13f)
                setWordwrap(true)
                isCursorAnimationEnabled = false
                setLineNumberMarginLeft(spToPx(ctx, 2f))
                verticalExtraSpaceFactor = 0.12f
                isBlockLineEnabled = true
                colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                applyThemedBackground(this, themeSurface)
                applyLineNumberBackground(this, dark)
                setEditorLanguage(TextMateLanguage.create(JSON_SCOPE, false))
                setText(value)
                if (!readOnly) {
                    val editorView = this
                    subscribeAlways(ContentChangeEvent::class.java) {
                        editorView.post {
                            val text = editorView.text.toString()
                            lastLocal.value = text
                            currentOnValueChange.value?.invoke(text)
                        }
                    }
                }
                editor = this
            }
        },
        update = { view ->
            view.isEditable = !readOnly
        },
        onRelease = {
            editor = null
            it.release()
        }
    )

    LaunchedEffect(dark, themeSurface, editor) {
        val current = editor ?: return@LaunchedEffect
        TextMateSetup.applyTheme(dark)
        current.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        applyThemedBackground(current, themeSurface)
        applyLineNumberBackground(current, dark)
        current.invalidate()
    }

    LaunchedEffect(value, editor) {
        val current = editor ?: return@LaunchedEffect
        if (value != lastLocal.value) {
            current.setText(value)
            lastLocal.value = value
        }
    }

}

private fun applyThemedBackground(editor: CodeEditor, themeSurface: Color) {
    val scheme = editor.colorScheme
    val base = Color(scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND))
    scheme.setColor(
        EditorColorScheme.WHOLE_BACKGROUND,
        lerp(base, themeSurface, 0.9f).toArgb()
    )
}

private fun applyLineNumberBackground(editor: CodeEditor, dark: Boolean) {
    val scheme = editor.colorScheme
    val base = Color(scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND))
    val target = if (dark) Color.White else Color.Black
    scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, lerp(base, target, 0.08f).toArgb())
}

private fun spToPx(context: android.content.Context, sp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)

private const val JSON_SCOPE = "source.json"
