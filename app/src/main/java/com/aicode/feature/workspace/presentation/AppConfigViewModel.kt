package com.aicode.feature.workspace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.feature.workspace.domain.FileAccessProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * 「App Configurations」页状态:展示 `aicode.json` 的应用信息 / SDK / 版本 / 编译器信息。
 * 可编辑字段仅限 SDK 级别与版本(改名/改包名会牵扯源码目录与清单,不在本页处理)。
 */
data class AppConfigUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val appName: String = "",
    val packageName: String = "",
    val minSdk: String = "",
    val targetSdk: String = "",
    val compileSdk: String = "",
    val versionCode: String = "",
    val versionName: String = "",
    val engine: String = "",
    val language: String = "",
    val template: String = "",
    val javaVersion: String = "",
    val buildType: String = "",
    val dependencies: List<String> = emptyList(),
    val saving: Boolean = false,
    val savedTick: Int = 0,
    val saveFailed: Boolean = false
)

@HiltViewModel
class AppConfigViewModel @Inject constructor(
    private val fileAccess: FileAccessProvider
) : ViewModel() {

    private val _state = MutableStateFlow(AppConfigUiState())
    val state: StateFlow<AppConfigUiState> = _state.asStateFlow()

    private var loadedIdentity: String? = null

    /** 幂等加载:同一工作区不重复读盘。[workspacePath] 只作身份标识,实际文件走容器路径。 */
    fun load(workspacePath: String?) {
        val identity = workspacePath?.trim().orEmpty()
        if (identity.isEmpty() || identity == loadedIdentity) return
        loadedIdentity = identity
        _state.value = AppConfigUiState(loading = true)
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { fileAccess.readFile(JSON_PATH) }.getOrNull()
            }
            _state.value = if (text.isNullOrBlank()) {
                AppConfigUiState(loading = false, loadFailed = true)
            } else {
                parse(text)
            }
        }
    }

    fun setMinSdk(value: String) {
        _state.value = _state.value.copy(minSdk = value.filter(Char::isDigit).take(2))
    }

    fun setTargetSdk(value: String) {
        _state.value = _state.value.copy(targetSdk = value.filter(Char::isDigit).take(2))
    }

    fun setCompileSdk(value: String) {
        _state.value = _state.value.copy(compileSdk = value.filter(Char::isDigit).take(2))
    }

    fun setVersionCode(value: String) {
        _state.value = _state.value.copy(versionCode = value.filter(Char::isDigit).take(9))
    }

    fun setVersionName(value: String) {
        _state.value = _state.value.copy(versionName = value.take(32))
    }

    fun save() {
        val current = _state.value
        val minSdk = current.minSdk.toIntOrNull()
        val targetSdk = current.targetSdk.toIntOrNull()
        val compileSdk = current.compileSdk.toIntOrNull()
        val versionCode = current.versionCode.toIntOrNull()
        val versionName = current.versionName.trim()
        if (minSdk == null || targetSdk == null || compileSdk == null || versionCode == null || versionName.isEmpty()) {
            _state.value = current.copy(saveFailed = true)
            return
        }
        _state.value = current.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val root = Json.parseToJsonElement(fileAccess.readFile(JSON_PATH)).jsonObject
                    val platform = root["platform"] as? JsonObject ?: JsonObject(emptyMap())
                    val version = root["version"] as? JsonObject ?: JsonObject(emptyMap())
                    val newRoot = root
                        .with(
                            "platform",
                            platform
                                .with("minSdk", JsonPrimitive(minSdk))
                                .with("targetSdk", JsonPrimitive(targetSdk))
                                .with("compileSdk", JsonPrimitive(compileSdk))
                        )
                        .with(
                            "version",
                            version
                                .with("code", JsonPrimitive(versionCode))
                                .with("name", JsonPrimitive(versionName))
                        )
                    val encoded = PRETTY_JSON.encodeToString(JsonElement.serializer(), newRoot) + "\n"
                    fileAccess.writeFile(JSON_PATH, encoded)
                }.isSuccess
            }
            _state.value = _state.value.copy(
                saving = false,
                savedTick = if (ok) _state.value.savedTick + 1 else _state.value.savedTick,
                saveFailed = !ok
            )
        }
    }

    private fun parse(text: String): AppConfigUiState {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return AppConfigUiState(loading = false, loadFailed = true)
        val platform = root["platform"] as? JsonObject
        val version = root["version"] as? JsonObject
        val compiler = root["compiler"] as? JsonObject
        val build = root["build"] as? JsonObject
        val deps = (root["dependencies"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()
        return AppConfigUiState(
            loading = false,
            appName = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            packageName = root["package"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            minSdk = platform?.get("minSdk")?.jsonPrimitive?.intOrNull?.toString().orEmpty(),
            targetSdk = platform?.get("targetSdk")?.jsonPrimitive?.intOrNull?.toString().orEmpty(),
            compileSdk = platform?.get("compileSdk")?.jsonPrimitive?.intOrNull?.toString().orEmpty(),
            versionCode = version?.get("code")?.jsonPrimitive?.intOrNull?.toString().orEmpty(),
            versionName = version?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
            engine = compiler?.get("engine")?.jsonPrimitive?.contentOrNull.orEmpty(),
            language = compiler?.get("language")?.jsonPrimitive?.contentOrNull.orEmpty(),
            template = compiler?.get("template")?.jsonPrimitive?.contentOrNull.orEmpty(),
            javaVersion = compiler?.get("java")?.jsonPrimitive?.intOrNull?.toString().orEmpty(),
            buildType = build?.get("type")?.jsonPrimitive?.contentOrNull.orEmpty(),
            dependencies = deps
        )
    }

    private fun JsonObject.with(key: String, value: JsonElement): JsonObject =
        JsonObject(toMutableMap().apply { put(key, value) })

    private companion object {
        const val JSON_PATH = "~/workspace/aicode.json"
        val PRETTY_JSON = Json { prettyPrint = true }
    }
}
