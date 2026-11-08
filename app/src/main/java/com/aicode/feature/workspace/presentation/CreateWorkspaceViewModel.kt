package com.aicode.feature.workspace.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.R
import com.aicode.core.util.FileLogger
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import com.aicode.feature.workspace.domain.model.Workspace
import com.aicode.feature.workspace.domain.project.AndroidProjectSpec
import com.aicode.feature.workspace.domain.project.AndroidTemplateRenderer
import com.aicode.feature.workspace.domain.project.ProjectScaffoldService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProjectKind { PLAIN, GIT, ANDROID }

data class CreateWorkspaceUiState(
    val creating: Boolean = false,
    val error: String? = null,
    val created: Workspace? = null
)

@HiltViewModel
class CreateWorkspaceViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workspaceRepository: WorkspaceRepository,
    private val scaffoldService: ProjectScaffoldService
) : ViewModel() {

    private val _state = MutableStateFlow(CreateWorkspaceUiState())
    val state: StateFlow<CreateWorkspaceUiState> = _state.asStateFlow()

    val workspaces = workspaceRepository.workspaces

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    fun consumeCreated() {
        _state.update { it.copy(created = null) }
    }

    fun createPlain(name: String, onSwitchConfirmed: () -> Unit) {
        create(name, ProjectKind.PLAIN, spec = null, onSwitchConfirmed)
    }

    fun createGit(name: String, onSwitchConfirmed: () -> Unit) {
        create(name, ProjectKind.GIT, spec = null, onSwitchConfirmed)
    }

    fun createAndroid(spec: AndroidProjectSpec, onSwitchConfirmed: () -> Unit) {
        create(spec.projectName, ProjectKind.ANDROID, spec, onSwitchConfirmed)
    }

    private fun create(
        rawName: String,
        kind: ProjectKind,
        spec: AndroidProjectSpec?,
        onSwitchConfirmed: () -> Unit
    ) {
        if (_state.value.creating) return
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = null) }
            val result = runCatching {
                val ws = workspaceRepository.createWorkspace(rawName)
                    ?: error("create_failed")
                onSwitchConfirmed()
                workspaceRepository.selectWorkspace(ws.name)
                when (kind) {
                    ProjectKind.PLAIN -> Unit
                    ProjectKind.GIT -> scaffoldService.initGitRepo()
                    ProjectKind.ANDROID -> {
                        val resolved = spec ?: error("missing_spec")
                        scaffoldService.writeAndroidTemplate(resolved)
                    }
                }
                ws
            }
            result.fold(
                onSuccess = { ws ->
                    FileLogger.i(TAG, "创建工作区成功 kind=$kind name=${ws.name}")
                    _state.update { it.copy(creating = false, created = ws) }
                },
                onFailure = { e ->
                    FileLogger.e(TAG, "创建工作区失败 kind=$kind name=$rawName", e)
                    val msg = when (e.message) {
                        "create_failed" -> context.getString(R.string.workspace_create_failed)
                        else -> context.getString(R.string.workspace_create_scaffold_failed)
                    }
                    _state.update { it.copy(creating = false, error = msg) }
                }
            )
        }
    }

    fun isValidPackage(packageName: String): Boolean =
        AndroidTemplateRenderer.isValidPackage(packageName)

    fun suggestPackage(appName: String): String =
        AndroidTemplateRenderer.suggestPackage(appName)

    private companion object {
        const val TAG = "CreateWorkspaceVM"
    }
}
