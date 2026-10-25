package com.aicode.feature.settings.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Spacing
import com.aicode.core.theme.semanticColors
import com.aicode.feature.settings.data.local.ToolCatalogEntry
import compose.icons.FeatherIcons
import compose.icons.feathericons.Box
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Terminal

/** 目录分组顺序与图标：key → (标题资源, 图标)。 */
private val CategoryOrder = listOf("files", "execution", "network", "ai", "extensions")

/**
 * 「可用工具」二级页：按类别折叠分组的工具清单，行内展示工具标题与一句话说明，
 * 点击进入工具详情。数据来自 assets/tools_catalog.json，纯展示、不含运行时开关。
 */
@Composable
internal fun AvailableToolsSection(
    entries: List<ToolCatalogEntry>,
    langKey: String,
    onOpenDetail: (ToolCatalogEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        CategoryOrder.forEach { category ->
            val groupEntries = entries.filter { it.category == category }
            val headerRes = categoryTitleRes(category)
            var expanded by rememberSaveable(key = category) { mutableStateOf(true) }
            CollapsibleGroupHeader(
                text = stringResource(headerRes),
                expanded = expanded,
                onToggle = { expanded = !expanded }
            )
            AnimatedVisibility(visible = expanded) {
                SettingsGroup {
                    if (groupEntries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tools_group_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 12.dp)
                        )
                    } else {
                        groupEntries.forEachIndexed { index, entry ->
                            if (index > 0) SettingsDivider()
                            ToolRow(entry = entry, langKey = langKey, onClick = { onOpenDetail(entry) })
                        }
                    }
                }
            }
        }
    }
}

/** 单个工具行：类别图标 + 标题/一句话说明 + 右箭头。 */
@Composable
private fun ToolRow(
    entry: ToolCatalogEntry,
    langKey: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.semanticColors.cardSurface)
            .clickable(onClick = onClick)
            .padding(start = Spacing.lg, end = Spacing.xs, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIcon(entry.category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.titleFor(langKey),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.descriptionFor(langKey),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        Icon(
            imageVector = FeatherIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.semanticColors.subtleText,
            modifier = Modifier.size(18.dp)
        )
    }
}

internal fun categoryTitleRes(category: String): Int = when (category) {
    "files" -> R.string.tools_category_files
    "execution" -> R.string.tools_category_execution
    "network" -> R.string.tools_category_network
    "ai" -> R.string.tools_category_ai
    else -> R.string.tools_category_extensions
}

private fun categoryIcon(category: String) = when (category) {
    "files" -> FeatherIcons.FileText
    "execution" -> FeatherIcons.Terminal
    "network" -> FeatherIcons.Globe
    "ai" -> FeatherIcons.Cpu
    else -> FeatherIcons.Box
}
