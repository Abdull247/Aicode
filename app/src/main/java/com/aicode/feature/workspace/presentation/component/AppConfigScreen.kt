package com.aicode.feature.workspace.presentation.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.R
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.AppTextField
import com.aicode.core.ui.FloatingTabBar
import com.aicode.core.ui.FloatingTabItem
import com.aicode.feature.settings.presentation.component.SettingsDivider
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.SettingsGroupHeader
import com.aicode.feature.settings.presentation.component.SettingsRow
import com.aicode.feature.settings.presentation.component.settingsPageBackground
import com.aicode.feature.workspace.presentation.AppConfigViewModel
import com.aicode.feature.workspace.presentation.AppConfigUiState
import compose.icons.FeatherIcons
import compose.icons.feathericons.Activity
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Code
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.Package
import compose.icons.feathericons.Sliders
import compose.icons.feathericons.Terminal

/**
 * 「App Configurations」页:浮底 Tab 栏 + 左右滑动切换「Settings / Build Features」。
 * 数据来自工作区根的 `aicode.json`,编辑 SDK 级别与版本后写回。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConfigScreen(
    workspacePath: String?,
    onNavigateBack: () -> Unit
) {
    val viewModel: AppConfigViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pagerState = rememberPagerState { 2 }

    LaunchedEffect(workspacePath) { viewModel.load(workspacePath) }

    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) {
            Toast.makeText(context, context.getString(R.string.app_config_saved), Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            Toast.makeText(context, context.getString(R.string.app_config_save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_config_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            FeatherIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading -> CenterMessage(loading = true)
                state.loadFailed -> CenterMessage(
                    message = stringResource(R.string.app_config_load_failed)
                )
                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        if (page == 0) {
                            SettingsTab(state = state, viewModel = viewModel)
                        } else {
                            FeaturesTab(state = state)
                        }
                    }
                    FloatingTabBar(
                        pagerState = pagerState,
                        items = listOf(
                            FloatingTabItem(FeatherIcons.Sliders, stringResource(R.string.app_config_tab_settings)),
                            FloatingTabItem(FeatherIcons.Cpu, stringResource(R.string.app_config_tab_features))
                        ),
                        maskColor = settingsPageBackground(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(loading: Boolean = false, message: String? = null) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.app_config_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsTab(state: AppConfigUiState, viewModel: AppConfigViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.sm, bottom = 96.dp)
    ) {
        SettingsGroupHeader(stringResource(R.string.app_config_section_general))
        Spacer(Modifier.height(Spacing.sm))
        AppTextField(
            value = state.appName,
            onValueChange = {},
            label = stringResource(R.string.app_config_app_name),
            readOnly = true
        )
        Spacer(Modifier.height(Spacing.md))
        AppTextField(
            value = state.packageName,
            onValueChange = {},
            label = stringResource(R.string.app_config_package),
            readOnly = true
        )

        Spacer(Modifier.height(Spacing.lg))
        SettingsGroupHeader(stringResource(R.string.app_config_section_sdk))
        Spacer(Modifier.height(Spacing.sm))
        AppTextField(
            value = state.minSdk,
            onValueChange = viewModel::setMinSdk,
            label = stringResource(R.string.app_config_min_sdk),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(Spacing.md))
        AppTextField(
            value = state.targetSdk,
            onValueChange = viewModel::setTargetSdk,
            label = stringResource(R.string.app_config_target_sdk),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(Spacing.md))
        AppTextField(
            value = state.compileSdk,
            onValueChange = viewModel::setCompileSdk,
            label = stringResource(R.string.app_config_compile_sdk),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(Modifier.height(Spacing.lg))
        SettingsGroupHeader(stringResource(R.string.app_config_section_version))
        Spacer(Modifier.height(Spacing.sm))
        AppTextField(
            value = state.versionCode,
            onValueChange = viewModel::setVersionCode,
            label = stringResource(R.string.app_config_version_code),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(Spacing.md))
        AppTextField(
            value = state.versionName,
            onValueChange = viewModel::setVersionName,
            label = stringResource(R.string.app_config_version_name)
        )

        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = viewModel::save,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.app_config_save))
        }
    }
}

@Composable
private fun FeaturesTab(state: AppConfigUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.sm, bottom = 96.dp)
    ) {
        SettingsGroupHeader(stringResource(R.string.app_config_section_compiler))
        Spacer(Modifier.height(Spacing.sm))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Cpu,
                title = stringResource(R.string.app_config_engine),
                subtitle = state.engine.ifBlank { "-" }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Code,
                title = stringResource(R.string.app_config_language),
                subtitle = state.language.ifBlank { "-" }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Package,
                title = stringResource(R.string.app_config_template),
                subtitle = state.template.ifBlank { "-" }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Terminal,
                title = stringResource(R.string.app_config_java),
                subtitle = state.javaVersion.ifBlank { "-" }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Activity,
                title = stringResource(R.string.app_config_build_type),
                subtitle = state.buildType.ifBlank { "-" }
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        SettingsGroupHeader(stringResource(R.string.app_config_dependencies))
        Spacer(Modifier.height(Spacing.sm))
        SettingsGroup {
            if (state.dependencies.isEmpty()) {
                SettingsRow(title = stringResource(R.string.app_config_no_dependencies))
            } else {
                state.dependencies.forEachIndexed { index, dependency ->
                    if (index > 0) SettingsDivider()
                    SettingsRow(title = dependency)
                }
            }
        }
    }
}
