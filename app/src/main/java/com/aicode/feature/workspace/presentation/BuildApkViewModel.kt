package com.aicode.feature.workspace.presentation

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.core.xmlrender.RenderStatus
import com.aicode.core.xmlrender.XmlRenderEngine
import com.aicode.feature.agent.domain.container.CommandEngine
import com.aicode.feature.agent.domain.container.CommandEvent
import com.aicode.feature.workspace.domain.FileAccessProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 手机框预览状态。 */
sealed interface LayoutPreviewState {
    data object None : LayoutPreviewState
    data object Loading : LayoutPreviewState
    data class Ready(
        val bitmap: Bitmap,
        val approximate: Boolean,
        val warnings: List<String>
    ) : LayoutPreviewState

    data class Failed(val message: String) : LayoutPreviewState
}

/** 一次构建的结果。 */
sealed interface BuildOutcome {
    data class Success(val apkPath: String) : BuildOutcome
    data class Failure(val exitCode: Int?) : BuildOutcome
}

/**
 * 「Build APK」页状态:列出工程 res/layout 下的 XML 布局、渲染手机框预览,并用内置 AICore
 * 编译器(`aic build`)流式产出构建日志。命令跑在容器当前工作区(工程根)。
 */
@HiltViewModel
class BuildApkViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileAccess: FileAccessProvider,
    private val commandEngine: CommandEngine
) : ViewModel() {

    private val _layouts = MutableStateFlow<List<String>>(emptyList())
    val layouts: StateFlow<List<String>> = _layouts.asStateFlow()

    private val _selectedLayout = MutableStateFlow<String?>(null)
    val selectedLayout: StateFlow<String?> = _selectedLayout.asStateFlow()

    private val _preview = MutableStateFlow<LayoutPreviewState>(LayoutPreviewState.None)
    val preview: StateFlow<LayoutPreviewState> = _preview.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _outcome = MutableStateFlow<BuildOutcome?>(null)
    val outcome: StateFlow<BuildOutcome?> = _outcome.asStateFlow()

    private var loadedIdentity: String? = null
    private var buildJob: Job? = null

    /** 幂等加载:同一工作区只扫描一次布局目录。[workspacePath] 只作身份标识。 */
    fun load(workspacePath: String?) {
        val identity = workspacePath?.trim().orEmpty()
        if (identity.isEmpty() || identity == loadedIdentity) return
        loadedIdentity = identity
        viewModelScope.launch {
            val names = withContext(Dispatchers.IO) {
                runCatching {
                    fileAccess.listFiles(LAYOUT_DIR)
                        .filter { !it.isDirectory && it.name.endsWith(".xml", ignoreCase = true) }
                        .map { it.name.removeSuffix(".xml") }
                        .sorted()
                }.getOrDefault(emptyList())
            }
            _layouts.value = names
            if (names.isNotEmpty()) selectLayout(names.first())
        }
    }

    fun selectLayout(name: String) {
        _selectedLayout.value = name
        render(name)
    }

    private fun render(name: String) {
        _preview.value = LayoutPreviewState.Loading
        viewModelScope.launch {
            val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val outcome = runCatching {
                XmlRenderEngine.render(
                    context = context,
                    fileAccess = fileAccess,
                    layoutPath = "$LAYOUT_DIR/$name.xml",
                    dark = dark
                )
            }.getOrNull()
            _preview.value = when {
                outcome == null -> LayoutPreviewState.Failed("render failed")
                outcome.bitmap == null -> LayoutPreviewState.Failed(outcome.errorMessage ?: "render failed")
                else -> LayoutPreviewState.Ready(
                    bitmap = outcome.bitmap,
                    approximate = outcome.status != RenderStatus.SUCCESS,
                    warnings = outcome.warnings
                )
            }
        }
    }

    fun run(workspacePath: String?) {
        val path = workspacePath?.trim().orEmpty()
        if (path.isEmpty() || _running.value) return
        _log.value = emptyList()
        _outcome.value = null
        _running.value = true
        buildJob?.cancel()
        buildJob = viewModelScope.launch {
            val lines = ArrayList<String>()
            commandEngine.runCommandStream(BUILD_COMMAND, path, BUILD_TIMEOUT_MS).collect { event ->
                when (event) {
                    is CommandEvent.Line -> {
                        lines.add(event.text)
                        if (lines.size > MAX_LOG_LINES) lines.subList(0, lines.size - MAX_LOG_LINES).clear()
                        _log.value = lines.toList()
                    }
                    is CommandEvent.Exit -> {
                        _outcome.value = if (event.code == 0) {
                            BuildOutcome.Success(APK_RELATIVE_PATH)
                        } else {
                            BuildOutcome.Failure(event.code)
                        }
                    }
                }
            }
            _running.value = false
        }
    }

    override fun onCleared() {
        buildJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val LAYOUT_DIR = "~/workspace/app/src/main/res/layout"
        const val BUILD_COMMAND = "aic build"
        const val APK_RELATIVE_PATH = "build/outputs/apk/debug/app-debug.apk"
        const val BUILD_TIMEOUT_MS = 600_000L
        const val MAX_LOG_LINES = 4000
    }
}
