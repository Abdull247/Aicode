package com.aicode.feature.workspace.presentation.component

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.R
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.presentation.component.SettingsDivider
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.SettingsGroupHeader
import com.aicode.feature.settings.presentation.component.SettingsRow
import com.aicode.feature.settings.presentation.component.settingsPageBackground
import com.aicode.feature.workspace.presentation.CreateWorkspaceViewModel
import com.aicode.feature.workspace.presentation.ProjectKind
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.File
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.Smartphone

/**
 * 新建工程类型选择页:空工程 / 带 Git 的空工程 / Android 工程。
 * 前两者就地弹名称框后创建;Android 走底栏选模板再进详情页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectTypeScreen(
    onNavigateToAndroidDetails: () -> Unit,
    onCreated: () -> Unit,
    onNavigateBack: () -> Unit,
    onSwitchConfirmed: () -> Unit = {},
    viewModel: CreateWorkspaceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    var showAndroidSheet by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf<ProjectKind?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.created) {
        if (state.created != null) {
            viewModel.consumeCreated()
            onCreated()
        }
    }

    BackHandler(enabled = !state.creating, onBack = onNavigateBack)

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workspace_project_type_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !state.creating) {
                        Icon(
                            FeatherIcons.ArrowLeft,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            SettingsGroupHeader(stringResource(R.string.workspace_project_type_section))
            SettingsGroup {
                SettingsRow(
                    icon = FeatherIcons.File,
                    title = stringResource(R.string.workspace_project_type_plain),
                    subtitle = stringResource(R.string.workspace_project_type_plain_desc),
                    onClick = { pendingKind = ProjectKind.PLAIN }
                )
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.GitBranch,
                    title = stringResource(R.string.workspace_project_type_git),
                    subtitle = stringResource(R.string.workspace_project_type_git_desc),
                    onClick = { pendingKind = ProjectKind.GIT }
                )
                SettingsDivider()
                SettingsRow(
                    icon = FeatherIcons.Smartphone,
                    title = stringResource(R.string.workspace_project_type_android),
                    subtitle = stringResource(R.string.workspace_project_type_android_desc),
                    onClick = { showAndroidSheet = true }
                )
            }
        }
    }

    if (showAndroidSheet) {
        AndroidTemplateSheet(
            onPickNativeXml = onNavigateToAndroidDetails,
            onDismiss = { showAndroidSheet = false }
        )
    }

    pendingKind?.let { kind ->
        CreateWorkspaceNameDialog(
            existingNames = workspaces.map { it.name },
            onConfirm = { name ->
                when (kind) {
                    ProjectKind.PLAIN -> viewModel.createPlain(name, onSwitchConfirmed)
                    ProjectKind.GIT -> viewModel.createGit(name, onSwitchConfirmed)
                    ProjectKind.ANDROID -> Unit
                }
                pendingKind = null
            },
            onDismiss = { pendingKind = null }
        )
    }
}
