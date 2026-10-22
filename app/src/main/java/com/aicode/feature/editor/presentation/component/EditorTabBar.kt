package com.aicode.feature.editor.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.editor.data.displayName
import com.aicode.feature.editor.domain.OpenedEditorTab
import compose.icons.FeatherIcons
import compose.icons.feathericons.X

/** Open-editor tabs with close icons, matching the terminal tab strip. */
@Composable
fun EditorTabBar(
    tabs: List<OpenedEditorTab>,
    activePath: String?,
    dirtyPaths: Set<String>,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return
    val scroll = rememberScrollState()
    val selectedIndex = tabs.indexOfFirst { it.path == activePath }.coerceAtLeast(0)
    LaunchedEffect(selectedIndex, tabs.size) {
        val approx = selectedIndex * 160
        val target = approx.coerceIn(0, scroll.maxValue)
        if (kotlin.math.abs(scroll.value - target) > 24) {
            scroll.animateScrollTo(target)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = tab.path == activePath
                EditorTabChip(
                    title = tab.displayName(),
                    dirty = tab.path in dirtyPaths,
                    selected = selected,
                    onSelect = { onSelect(tab.path) },
                    onClose = { onClose(tab.path) }
                )
            }
        }
    }
}

@Composable
private fun EditorTabChip(
    title: String,
    dirty: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .height(40.dp)
            .padding(horizontal = 2.dp, vertical = Spacing.xs)
            .clip(RoundedCornerShape(Radius.sm))
            .background(bg)
            .clickable(onClick = onSelect)
            .padding(start = Spacing.sm, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (dirty) {
            Text(
                text = "*",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = title,
            color = if (selected) contentColor else contentColor.copy(alpha = 0.72f),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                FeatherIcons.X,
                contentDescription = stringResource(R.string.editor_close_tab),
                tint = contentColor.copy(alpha = if (selected) 0.8f else 0.5f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
