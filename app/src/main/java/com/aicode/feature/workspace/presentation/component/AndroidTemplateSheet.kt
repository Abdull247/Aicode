package com.aicode.feature.workspace.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aicode.R
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.AdaptiveModalBottomSheet
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.SettingsRow
import compose.icons.FeatherIcons
import compose.icons.feathericons.Smartphone

/**
 * Android 工程类型选择底栏:目前仅 native-xml(Java + XML,framework Activity)。
 * 选中后进入 Sketchware 风格详情页填写名称 / 包名 / 颜色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidTemplateSheet(
    onPickNativeXml: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = stringResource(R.string.workspace_android_template_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Spacing.md)
            )
            SettingsGroup {
                SettingsRow(
                    icon = FeatherIcons.Smartphone,
                    title = stringResource(R.string.workspace_android_template_native_xml),
                    subtitle = stringResource(R.string.workspace_android_template_native_xml_desc),
                    onClick = {
                        onDismiss()
                        onPickNativeXml()
                    }
                )
            }
        }
    }
}
