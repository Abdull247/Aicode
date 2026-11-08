package com.aicode.feature.workspace.presentation.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.aicode.feature.workspace.domain.model.Workspace
import com.aicode.feature.workspace.domain.model.WorkspaceType
import com.aicode.feature.workspace.domain.project.ProjectKindDetector
import com.aicode.feature.workspace.presentation.WorkspaceViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.File
import compose.icons.feathericons.Menu
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Send
import compose.icons.feathericons.Smartphone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onOpenProject: () -> Unit,
    onCreateProject: () -> Unit,
    onOpenSettings: () -> Unit,
    hasRunningSessions: () -> Boolean = { false },
    onSwitchConfirmed: () -> Unit = {},
    viewModel: WorkspaceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()
    val initError by viewModel.initError.collectAsStateWithLifecycle()
    var pendingWorkspaceSelect by remember { mutableStateOf<Workspace?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshAvailability()
    }
    LaunchedEffect(initError) {
        if (initError != null) {
            Toast.makeText(context, initError, Toast.LENGTH_LONG).show()
            viewModel.consumeInitError()
        }
    }

    fun openWorkspace(ws: Workspace) {
        if (!ws.available) return
        if (ws.name == current?.name) {
            onOpenProject()
            return
        }
        if (hasRunningSessions()) {
            pendingWorkspaceSelect = ws
        } else {
            onSwitchConfirmed()
            viewModel.selectWorkspace(ws.name)
            onOpenProject()
        }
    }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            FeatherIcons.Menu,
                            contentDescription = stringResource(R.string.chat_settings)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            FeatherIcons.Send,
                            contentDescription = stringResource(R.string.projects_telegram)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateProject) {
                Icon(
                    FeatherIcons.Plus,
                    contentDescription = stringResource(R.string.workspace_project_type_title)
                )
            }
        }
    ) { padding ->
        if (workspaces.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.projects_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SettingsGroupHeader(stringResource(R.string.projects_section))
                SettingsGroup {
                    workspaces.forEachIndexed { index, ws ->
                        if (index > 0) SettingsDivider()
                        val android = remember(ws.path, ws.available) {
                            ws.available && ProjectKindDetector.isAndroidProject(ws.path)
                        }
                        val subtitle = when {
                            !ws.available -> stringResource(R.string.workspace_external_unavailable_hint)
                            android -> stringResource(R.string.workspace_project_type_android)
                            else -> workspaceDisplayPath(ws)
                        }
                        SettingsRow(
                            icon = if (android) FeatherIcons.Smartphone else FeatherIcons.File,
                            title = ws.name,
                            subtitle = subtitle,
                            enabled = ws.available,
                            onClick = { openWorkspace(ws) }
                        )
                    }
                }
            }
        }
    }

    pendingWorkspaceSelect?.let { ws ->
        AlertDialog(
            onDismissRequest = { pendingWorkspaceSelect = null },
            title = { Text(stringResource(R.string.workspace_switch)) },
            text = { Text(stringResource(R.string.workspace_switch_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onSwitchConfirmed()
                    viewModel.selectWorkspace(ws.name)
                    pendingWorkspaceSelect = null
                    onOpenProject()
                }) { Text(stringResource(R.string.workspace_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingWorkspaceSelect = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

private fun workspaceDisplayPath(workspace: Workspace): String = when (workspace.type) {
    WorkspaceType.INTERNAL -> "projects/${workspace.name}"
    WorkspaceType.EXTERNAL_LOCAL, WorkspaceType.REMOTE -> workspace.path
}
