package com.aicode.feature.agent.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.aicode.feature.settings.presentation.component.SettingsDivider
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.SettingsRow
import compose.icons.FeatherIcons
import compose.icons.feathericons.Code
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Globe
import compose.icons.feathericons.Key
import compose.icons.feathericons.Package
import compose.icons.feathericons.Send
import compose.icons.feathericons.Sliders
import compose.icons.feathericons.Terminal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatToolsSheet(
    isAndroidProject: Boolean,
    onTerminal: () -> Unit,
    onAppConfigurations: () -> Unit,
    onBuildApk: () -> Unit,
    onEditor: () -> Unit,
    onBrowser: () -> Unit,
    onGit: () -> Unit,
    onNetwork: () -> Unit,
    onCredentials: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // The group card stays edge-to-edge; only the title keeps a horizontal inset.
                .padding(bottom = Spacing.xl)
                .imePadding()
        ) {
            Text(
                text = stringResource(R.string.chat_tools_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )
            SettingsGroup {
                SettingsRow(
                    icon = FeatherIcons.Terminal,
                    title = stringResource(R.string.terminal_title),
                    onClick = onTerminal
                )
                if (isAndroidProject) {
                    SettingsDivider()
                    SettingsRow(
                        icon = FeatherIcons.Sliders,
                        title = stringResource(R.string.chat_tools_app_config),
                        subtitle = stringResource(R.string.chat_tools_app_config_desc),
                        onClick = onAppConfigurations
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = FeatherIcons.Package,
                        title = stringResource(R.string.chat_tools_build_apk),
                        subtitle = stringResource(R.string.chat_tools_build_apk_desc),
                        onClick = onBuildApk
                    )
                }
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Code,
                    title = stringResource(R.string.chat_tools_editor),
                    onClick = onEditor
                )
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Globe,
                    title = stringResource(R.string.browser_title),
                    onClick = onBrowser
                )
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.GitBranch,
                    title = stringResource(R.string.git_title),
                    onClick = onGit
                )
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Send,
                    title = stringResource(R.string.network_request_title),
                    onClick = onNetwork
                )
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Key,
                    title = stringResource(R.string.chat_tools_credentials),
                    onClick = onCredentials
                )
            }
        }
    }
}
