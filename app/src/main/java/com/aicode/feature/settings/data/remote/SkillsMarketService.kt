package com.aicode.feature.settings.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Marketplace 后端地址。后端（AiCodeProBackend）默认跑在开发机的 8787 端口；
 * 模拟器用 10.0.2.2 访问宿主机回环地址，真机先执行 `adb reverse tcp:8787 tcp:8787`
 * 再把这里改成 http://localhost:8787。
 */
object SkillsMarketConfig {
    const val BASE_URL = "http://10.0.2.2:8787"
    const val CATEGORIES_PATH = "/api/marketplace/categories"
    const val SKILLS_PATH = "/api/marketplace/skills"
    const val DEFAULT_PAGE_SIZE = 20
}

@Serializable
data class MarketplaceCategory(
    val id: String,
    val name: String,
    val skillCount: Int = 0
)

@Serializable
data class MarketplaceSkillSummary(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val creator: String? = null,
    val publishedAt: String? = null,
    val version: String? = null,
    val scope: String? = null,
    val requiredTools: List<String> = emptyList()
)

/** /skills 列表接口的分页信封。 */
@Serializable
data class MarketplaceSkillPage(
    val items: List<MarketplaceSkillSummary> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = SkillsMarketConfig.DEFAULT_PAGE_SIZE,
    val total: Int = 0,
    val totalPages: Int = 1
)

/** /skills/:id 详情，包含指令正文。 */
@Serializable
data class MarketplaceSkillDetail(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val creator: String? = null,
    val publishedAt: String? = null,
    val version: String? = null,
    val scope: String? = null,
    val requiredTools: List<String> = emptyList(),
    val instructions: String
)

/** 服务端错误信封 { error: { code, message } }。 */
@Serializable
internal data class MarketplaceErrorEnvelope(val error: MarketplaceError? = null)

@Serializable
internal data class MarketplaceError(val code: String? = null, val message: String? = null)

/** 市场请求失败，[code] 尽量带服务端错误码或 HTTP_ 状态，便于排查。 */
class SkillsMarketException(message: String, val code: String? = null) : IOException(message)

@Serializable
internal data class MarketplaceCategoriesResponse(val categories: List<MarketplaceCategory> = emptyList())

/**
 * 技能市场只读接口：分类、分页列表、详情。
 * 与 [ModelApiService] 同套路——直接 OkHttp + kotlinx.serialization，
 * 复用全局 OkHttpClient 的连接池，只收紧超时避免页面卡死。
 */
@Singleton
class SkillsMarketService @Inject constructor(
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val marketClient by lazy {
        client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** 全部分类（带各自 skillCount）。 */
    suspend fun categories(): Result<List<MarketplaceCategory>> = runCatching {
        val body = getBody(SkillsMarketConfig.BASE_URL + SkillsMarketConfig.CATEGORIES_PATH)
        json.decodeFromString<MarketplaceCategoriesResponse>(body).categories
    }

    /** 分页技能列表；[category] 传 null 表示全部分类。 */
    suspend fun skills(
        category: String? = null,
        page: Int = 1,
        pageSize: Int = SkillsMarketConfig.DEFAULT_PAGE_SIZE
    ): Result<MarketplaceSkillPage> = runCatching {
        val url = (SkillsMarketConfig.BASE_URL + SkillsMarketConfig.SKILLS_PATH).toHttpUrlOrNull()
            ?.newBuilder()
            ?.apply {
                category?.let { addQueryParameter("category", it) }
                addQueryParameter("page", page.toString())
                addQueryParameter("pageSize", pageSize.toString())
            }
            ?.toString()
            ?: (SkillsMarketConfig.BASE_URL + SkillsMarketConfig.SKILLS_PATH)
        json.decodeFromString<MarketplaceSkillPage>(getBody(url))
    }

    /** 单个技能详情（含 instructions 正文）。 */
    suspend fun skill(id: String): Result<MarketplaceSkillDetail> = runCatching {
        val body = getBody(SkillsMarketConfig.BASE_URL + SkillsMarketConfig.SKILLS_PATH + "/" + id)
        json.decodeFromString<MarketplaceSkillDetail>(body)
    }

    /** GET 并返回响应体；非 2xx 时解析错误信封并抛 [SkillsMarketException]。 */
    private suspend fun getBody(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        try {
            marketClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverError = runCatching {
                        json.decodeFromString<MarketplaceErrorEnvelope>(body).error
                    }.getOrNull()
                    throw SkillsMarketException(
                        message = serverError?.message ?: "HTTP ${response.code}",
                        code = serverError?.code ?: "HTTP_${response.code}"
                    )
                }
                body
            }
        } catch (e: SkillsMarketException) {
            throw e
        } catch (e: Exception) {
            throw SkillsMarketException(message = e.message ?: "network error", code = CODE_NETWORK)
        }
    }

    companion object {
        const val CODE_NETWORK = "NETWORK"
    }
}
