package com.aicode.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.data.local.ToolCatalogEntry
import com.aicode.feature.settings.data.local.ToolCatalogParam
import com.aicode.feature.settings.data.local.descriptionFor

/**
 * 工具详情页：名称与说明卡、授权方式卡、参数说明卡。
 * 纯展示页（数据来自 assets/tools_catalog.json），与技能详情页同一分组卡片质感。
 */
@Composable
internal fun ToolDetailSection(
    entry: ToolCatalogEntry,
    langKey: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // 卡片 1：工具名 + 标题 + 说明
        SettingsGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.titleFor(langKey),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    McpPill(
                        text = entry.name,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = entry.descriptionFor(langKey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 卡片 2：授权方式
        SettingsGroupHeader(text = stringResource(R.string.tools_permission_header))
        SettingsGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tools_permission_mode),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                McpPill(
                    text = stringResource(
                        if (entry.permission == "auto") R.string.tools_permission_auto
                        else R.string.tools_permission_ask
                    ),
                    textColor = if (entry.permission == "auto") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    backgroundColor = (if (entry.permission == "auto") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)
                )
            }
            SettingsDivider()
            Text(
                text = stringResource(
                    if (entry.permission == "auto") R.string.tools_permission_note_auto
                    else R.string.tools_permission_note_ask
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = 12.dp)
            )
        }

        // 卡片 3：参数说明
        SettingsGroupHeader(text = stringResource(R.string.tools_parameters_header))
        SettingsGroup {
            if (entry.parameters.isEmpty()) {
                Text(
                    text = stringResource(R.string.tools_parameters_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 12.dp)
                )
            } else {
                entry.parameters.forEachIndexed { index, param ->
                    if (index > 0) SettingsDivider()
                    ToolParamRow(param, langKey)
                }
            }
        }
    }
}

/** 单个参数行：参数名（等宽）+ 类型/必填 pill + 描述。 */
@Composable
private fun ToolParamRow(
    param: ToolCatalogParam,
    langKey: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = param.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            McpPill(
                text = param.type.lowercase(),
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            McpPill(
                text = stringResource(
                    if (param.required) R.string.tools_param_required else R.string.tools_param_optional
                ),
                textColor = if (param.required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                backgroundColor = (if (param.required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline).copy(alpha = 0.12f)
            )
        }
        if (param.descriptionFor(langKey).isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = param.descriptionFor(langKey),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
