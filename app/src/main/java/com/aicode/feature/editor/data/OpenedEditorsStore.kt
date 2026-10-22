package com.aicode.feature.editor.data

import com.aicode.core.util.FileLogger
import com.aicode.feature.editor.domain.OpenedEditorTab
import com.aicode.feature.editor.domain.OpenedEditorsState
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import com.aicode.feature.workspace.domain.ProjectAicodeRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the open editor tab list under the project `.aicode/config` directory.
 *
 * Local mode writes `<workspace>/.aicode/config/opened_editors.json` so it can be
 * git-tracked with the rest of the project config. Remote mode uses the same
 * [ProjectAicodeRoot] mapping as other project-level files.
 */
@Singleton
class OpenedEditorsStore @Inject constructor(
    private val projectAicodeRoot: ProjectAicodeRoot,
    private val workspaceRepository: WorkspaceRepository
) {
    private val mutex = Mutex()

    fun fileForCurrentWorkspace(): File =
        File(File(projectAicodeRoot.current(), CONFIG_DIR), FILE_NAME)

    fun fileForWorkspace(workspacePath: String): File =
        File(File(projectAicodeRoot.forPath(workspacePath), CONFIG_DIR), FILE_NAME)

    suspend fun load(workspacePath: String = workspaceRepository.currentPath()): OpenedEditorsState =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val file = fileForWorkspace(workspacePath)
                if (!file.isFile) return@withContext OpenedEditorsState()
                runCatching { JSON.decodeFromString<OpenedEditorsState>(file.readText()) }
                    .getOrElse { e ->
                        FileLogger.w(TAG, "Failed to read opened editors: ${file.absolutePath}", e)
                        OpenedEditorsState()
                    }
            }
        }

    suspend fun save(
        state: OpenedEditorsState,
        workspacePath: String = workspaceRepository.currentPath()
    ) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val file = fileForWorkspace(workspacePath)
                runCatching {
                    file.parentFile?.mkdirs()
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(JSON.encodeToString(state))
                    if (!tmp.renameTo(file)) {
                        file.writeText(tmp.readText())
                        tmp.delete()
                    }
                }.onFailure { e ->
                    FileLogger.w(TAG, "Failed to write opened editors: ${file.absolutePath}", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "OpenedEditorsStore"
        private const val CONFIG_DIR = "config"
        private const val FILE_NAME = "opened_editors.json"
        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}

fun OpenedEditorTab.displayName(): String =
    name.ifBlank { path.trimEnd('/').substringAfterLast('/').ifBlank { path } }
