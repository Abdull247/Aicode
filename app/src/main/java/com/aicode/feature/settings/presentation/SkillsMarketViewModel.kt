package com.aicode.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.feature.agent.domain.skill.SkillForm
import com.aicode.feature.agent.domain.skill.SkillRepository
import com.aicode.feature.agent.domain.skill.SkillSaveError
import com.aicode.feature.agent.domain.skill.SkillScope
import com.aicode.feature.settings.data.remote.MarketplaceCategory
import com.aicode.feature.settings.data.remote.MarketplaceSkillDetail
import com.aicode.feature.settings.data.remote.MarketplaceSkillSummary
import com.aicode.feature.settings.data.remote.SkillsMarketConfig
import com.aicode.feature.settings.data.remote.SkillsMarketService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 装技能失败的归类，UI 层映射成可读文案。 */
enum class SkillsMarketInstallFailure {
    NETWORK,
    SERVER,
    INVALID_NAME,
    NAME_CONFLICT,
    EMPTY_CONTENT,
    IO
}

data class SkillsMarketUiState(
    val categories: List<MarketplaceCategory> = emptyList(),
    /** 当前选中的分类；null 表示「全部」。 */
    val selectedCategoryId: String? = null,
    val skills: List<MarketplaceSkillSummary> = emptyList(),
    /** 最近成功加载的页码（1 起）；0 表示还没拿到任何一页。 */
    val page: Int = 0,
    val totalPages: Int = 0,
    val total: Int = 0,
    /** 首次加载 / 切分类重载（整页骨架态）。 */
    val loading: Boolean = false,
    /** 整页加载失败（分类或列表），展示重试按钮。 */
    val loadFailed: Boolean = false,
    /** 翻页加载中（列表底部小圈）。 */
    val appendingPage: Boolean = false,
    /** 详情底部弹层是否打开。 */
    val detailOpen: Boolean = false,
    /** 弹层里正在拉详情正文。 */
    val detailLoading: Boolean = false,
    /** 详情拉取失败（弹层内错误态，可重试）。 */
    val detailError: Boolean = false,
    val detail: MarketplaceSkillDetail? = null,
    /** 正在安装的技能 id；null 表示空闲。 */
    val installingId: String? = null,
    /** 安装失败原因；UI 用完即清。 */
    val installFailure: SkillsMarketInstallFailure? = null,
    /** 最近一次安装成功的技能名；UI 用完即清。 */
    val lastInstalledName: String? = null
)

/**
 * 技能市场页面 ViewModel：只依赖市场服务与技能仓库，列表数据自持，
 * 与 SettingsViewModel 的本地技能列表解耦；安装成功后由 UI 回调
 * onInstalled() 驱动 SettingsViewModel.refreshSkills() 重新扫描。
 */
@HiltViewModel
class SkillsMarketViewModel @Inject constructor(
    private val marketService: SkillsMarketService,
    private val skillRepository: SkillRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SkillsMarketUiState())
    val state: StateFlow<SkillsMarketUiState> = _state.asStateFlow()

    /** 详情弹层数据缓存：翻看多个技能时避免重复拉取正文。 */
    private val detailCache = LinkedHashMap<String, MarketplaceSkillDetail>()

    /** 打开中的详情对应的行摘要，重试拉详情时用。 */
    private var openDetailSummary: MarketplaceSkillSummary? = null

    /** 列表请求序号：切分类 / 整页重试时丢弃过期响应，避免串页。 */
    private var listRequestId = 0

    private var initialLoadStarted = false

    /** 首次进入加载；重复调用只生效一次。 */
    fun load() {
        if (initialLoadStarted) return
        initialLoadStarted = true
        reload()
    }

    /** 整页失败后的重试：分类与列表都重拉。 */
    fun reload() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            marketService.categories().fold(
                onSuccess = { categories ->
                    _state.update { it.copy(categories = categories) }
                    fetchSkillsPage(reset = true)
                },
                onFailure = {
                    _state.update { it.copy(loading = false, loadFailed = true) }
                }
            )
        }
    }

    /** 切分类；null 表示「全部」。同一分类重复点击不重新请求。 */
    fun selectCategory(categoryId: String?) {
        if (_state.value.selectedCategoryId == categoryId) return
        _state.update { it.copy(selectedCategoryId = categoryId) }
        fetchSkillsPage(reset = true)
    }

    /** 请求下一页（列表滚动接近底部时由 UI 调用）；翻到底或加载中则忽略。 */
    fun loadNextPage() {
        val current = _state.value
        if (current.loading || current.appendingPage || current.loadFailed) return
        if (current.page < 1 || current.page >= current.totalPages) return
        fetchSkillsPage(reset = false)
    }

    /** 打开详情弹层；同技能命中缓存直接展示，否则进入加载态拉正文。 */
    fun openDetail(summary: MarketplaceSkillSummary) {
        openDetailSummary = summary
        val cached = detailCache[summary.id]
        if (cached != null) {
            _state.update { it.copy(detailOpen = true, detail = cached, detailLoading = false, detailError = false) }
            return
        }
        _state.update { it.copy(detailOpen = true, detail = null, detailLoading = true, detailError = false) }
        viewModelScope.launch {
            marketService.skill(summary.id).fold(
                onSuccess = { detail ->
                    detailCache[detail.id] = detail
                    _state.update { state ->
                        // 弹层已被关掉或换了目标时只落缓存，不再改弹层状态。
                        if (state.detailOpen && openDetailSummary?.id == detail.id) {
                            state.copy(detail = detail, detailLoading = false, detailError = false)
                        } else {
                            state.copy(detailLoading = false)
                        }
                    }
                },
                onFailure = {
                    _state.update { state ->
                        if (state.detailOpen && openDetailSummary?.id == summary.id && state.detail == null) {
                            state.copy(detailLoading = false, detailError = true)
                        } else {
                            state.copy(detailLoading = false)
                        }
                    }
                }
            )
        }
    }

    /** 弹层内详情拉取失败后的重试。 */
    fun retryDetail() {
        val summary = openDetailSummary ?: return
        openDetail(summary)
    }

    fun dismissDetail() {
        openDetailSummary = null
        _state.update { it.copy(detailOpen = false, detail = null, detailLoading = false, detailError = false) }
    }

    /**
     * 安装当前弹层中的技能到当前项目作用域：同名冲突自动追加序号重试。
     * 详情未就绪时忽略。
     */
    fun install() {
        val current = _state.value
        val detail = current.detail ?: return
        if (current.installingId != null) return
        _state.update { it.copy(installingId = detail.id, installFailure = null) }
        viewModelScope.launch {
            val (failure, finalName) = installWithUniqueName(detail, baseName = detail.name.trim())
            _state.update { state ->
                if (failure == null) {
                    state.copy(installingId = null, lastInstalledName = finalName)
                } else {
                    state.copy(installingId = null, installFailure = failure)
                }
            }
        }
    }

    /** UI 展示完提示后清掉一次性反馈字段。 */
    fun clearInstallFeedback() {
        _state.update { it.copy(installFailure = null, lastInstalledName = null) }
    }

    /**
     * 落盘并把 NAME_CONFLICT 转成追加序号重试（最多 [MAX_NAME_ATTEMPTS] 次）。
     * 返回 (失败归类或 null=成功, 最终写入的技能名)。
     */
    private suspend fun installWithUniqueName(
        detail: MarketplaceSkillDetail,
        baseName: String
    ): Pair<SkillsMarketInstallFailure?, String> {
        var candidate = baseName
        var attempt = 0
        while (true) {
            val error = withContext(Dispatchers.IO) {
                skillRepository.save(
                    form = SkillForm(
                        name = candidate,
                        description = detail.description,
                        instructions = detail.instructions,
                        requiredTools = detail.requiredTools
                    ),
                    scope = SkillScope.PROJECT
                )
            }
            val failure = error?.toInstallFailure()
            if (failure != SkillsMarketInstallFailure.NAME_CONFLICT || attempt >= MAX_NAME_ATTEMPTS) {
                return failure to candidate
            }
            attempt += 1
            candidate = "$baseName $attempt"
        }
    }

    /** 拉一页列表；reset=true 时清空重来（整页骨架），false 时追加并展示底部小圈。 */
    private fun fetchSkillsPage(reset: Boolean) {
        val requestId = ++listRequestId
        if (reset) {
            _state.update {
                it.copy(
                    loading = true,
                    loadFailed = false,
                    skills = emptyList(),
                    page = 0,
                    totalPages = 0,
                    total = 0,
                    appendingPage = false
                )
            }
        } else {
            _state.update { it.copy(appendingPage = true) }
        }

        val queryPage = _state.value.page + 1
        val queryCategory = _state.value.selectedCategoryId
        viewModelScope.launch {
            marketService.skills(
                category = queryCategory,
                page = queryPage,
                pageSize = SkillsMarketConfig.DEFAULT_PAGE_SIZE
            ).fold(
                onSuccess = { result ->
                    if (requestId != listRequestId) return@fold
                    _state.update { state ->
                        val merged = (state.skills + result.items).distinctBy { it.id }
                        state.copy(
                            skills = merged,
                            page = result.page,
                            totalPages = result.totalPages,
                            total = result.total,
                            loading = false,
                            loadFailed = false,
                            appendingPage = false
                        )
                    }
                },
                onFailure = {
                    if (requestId != listRequestId) return@fold
                    _state.update { state ->
                        if (reset) {
                            state.copy(loading = false, appendingPage = false, loadFailed = true)
                        } else {
                            state.copy(appendingPage = false)
                        }
                    }
                }
            )
        }
    }

    private fun SkillSaveError.toInstallFailure(): SkillsMarketInstallFailure = when (this) {
        SkillSaveError.INVALID_NAME -> SkillsMarketInstallFailure.INVALID_NAME
        SkillSaveError.EMPTY_INSTRUCTIONS -> SkillsMarketInstallFailure.EMPTY_CONTENT
        SkillSaveError.NAME_CONFLICT -> SkillsMarketInstallFailure.NAME_CONFLICT
        SkillSaveError.IO_FAILED -> SkillsMarketInstallFailure.IO
    }

    private companion object {
        const val MAX_NAME_ATTEMPTS = 20
    }
}
