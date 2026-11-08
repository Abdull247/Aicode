package com.aicode.feature.workspace.domain.project

import com.aicode.core.util.FileLogger
import com.aicode.feature.git.domain.GitRepository
import com.aicode.feature.workspace.domain.FileAccessProvider
import com.aicode.feature.workspace.domain.WorkspacePathMapper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在**当前已选中**的工作区根下落盘模板文件，或执行 `git init`。
 * 调用方必须先 [com.aicode.feature.workspace.data.repository.WorkspaceRepository.selectWorkspace]，
 * 这样 [FileAccessProvider] 与 [GitRepository] 的 cwd 都指向新目录。
 */
@Singleton
class ProjectScaffoldService @Inject constructor(
    private val fileAccess: FileAccessProvider,
    private val gitRepository: GitRepository
) {
    /**
     * 把 native-xml 骨架写入当前工作区（容器路径 `~/workspace/...`）。
     * @return 写入的相对路径列表。
     */
    fun writeAndroidTemplate(spec: AndroidProjectSpec): List<String> {
        val files = AndroidTemplateRenderer.render(spec)
        for ((rel, content) in files) {
            val path = containerPath(rel)
            fileAccess.writeFile(path, content, overwrite = true)
        }
        FileLogger.i(TAG, "写入 Android 模板 ${files.size} 个文件 package=${spec.packageName}")
        return files.keys.sorted()
    }

    /**
     * 在当前工作区执行 `git init -b main`。
     * 容器尚未就绪时由 [GitRepository] 抛错，调用方捕获后提示用户。
     */
    suspend fun initGitRepo(): String = gitRepository.initRepo()

    private fun containerPath(relative: String): String {
        val trimmed = relative.trimStart('/')
        return if (trimmed.isEmpty()) WorkspacePathMapper.CONTAINER_ROOT
        else "${WorkspacePathMapper.CONTAINER_ROOT}/$trimmed"
    }

    private companion object {
        const val TAG = "ProjectScaffold"
    }
}
