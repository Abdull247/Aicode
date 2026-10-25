package com.aicode.feature.settings.data.local

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 可用工具目录里单个参数的展示信息。 */
@Serializable
data class ToolCatalogParam(
    val name: String,
    val type: String = "string",
    val required: Boolean = false,
    val description: String = "",
    val descriptionZh: String? = null
)

/**
 * 「可用工具」页的静态展示条目：工具名 + 标题/描述/参数说明（英文为基准，可选中文变体）。
 * 纯资源目录（assets/tools_catalog.json），不与 ToolRegistry 运行时耦合；
 * 新增/调整工具时需同步维护该文件。
 */
@Serializable
data class ToolCatalogEntry(
    val name: String,
    val title: String,
    val titleZh: String? = null,
    val description: String = "",
    val descriptionZh: String? = null,
    /** 分组 key：files / execution / network / ai / extensions。 */
    val category: String = "extensions",
    /** 授权方式：auto（自动放行）/ ask（需要确认）。 */
    val permission: String = "ask",
    val parameters: List<ToolCatalogParam> = emptyList()
) {
    /** 按语言 key（zh/en）取标题。 */
    fun titleFor(langKey: String): String =
        if (langKey == "zh") titleZh ?: title else title

    fun descriptionFor(langKey: String): String =
        if (langKey == "zh") descriptionZh ?: description else description
}

/** 参数描述按语言取值。 */
fun ToolCatalogParam.descriptionFor(langKey: String): String =
    if (langKey == "zh") descriptionZh ?: description else description

/**
 * 可用工具目录加载器：只读内置 assets，秒级返回，不发网络请求。
 * JSON 解析抽成纯函数便于单测。
 */
object ToolCatalogLibrary {
    const val CATALOG_ASSET_FILE_NAME = "tools_catalog.json"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<ToolCatalogEntry>? = null

    /** 纯函数：解析目录 JSON 文本；结构异常返回空列表。 */
    fun parse(text: String): List<ToolCatalogEntry> = runCatching {
        json.decodeFromString<List<ToolCatalogEntry>>(text)
    }.getOrDefault(emptyList())

    /** 同步读取内置目录（进程内缓存）。 */
    fun load(context: Context): List<ToolCatalogEntry> {
        cached?.let { return it }
        val entries = runCatching {
            context.assets.open(CATALOG_ASSET_FILE_NAME).bufferedReader().use { it.readText() }
        }.map { parse(it) }.getOrDefault(emptyList())
        cached = entries
        return entries
    }
}
