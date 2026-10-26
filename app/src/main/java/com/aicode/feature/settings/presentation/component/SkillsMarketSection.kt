package com.aicode.feature.settings.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.theme.semanticColors
import com.aicode.core.ui.AdaptiveModalBottomSheet
import com.aicode.feature.settings.data.remote.MarketplaceCategory
import com.aicode.feature.settings.data.remote.MarketplaceSkillDetail
import com.aicode.feature.settings.data.remote.MarketplaceSkillSummary
import com.aicode.feature.settings.presentation.SkillsMarketInstallFailure
import com.aicode.feature.settings.presentation.SkillsMarketUiState
import com.aicode.feature.settings.presentation.SkillsMarketViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.Book
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.WifiOff
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.widget.Toast

/**
 * 技能市场分区页：顶部分类筛选条 + 居中骨架 / 列表；列表点击行弹出详情底部弹层，
 * 弹层里一键安装到当前项目。列表滚动接近底部自动翻页。
 */
@Composable
internal fun SkillsMarketSection(
    viewModel: SkillsMarketViewModel,
    onInstalled: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.load() }

    // 安装成功：提示 + 关弹层 + 通知 SettingsScreen 重扫本地技能列表。
    LaunchedEffect(state.lastInstalledName) {
        state.lastInstalledName?.let {
            Toast.makeText(context, context.getString(R.string.skills_market_installed), Toast.LENGTH_SHORT).show()
            viewModel.clearInstallFeedback()
            viewModel.dismissDetail()
            onInstalled()
        }
    }

    // 安装失败：按原因映射文案提示，清掉一次性反馈。
    LaunchedEffect(state.installFailure) {
        state.installFailure?.let { failure ->
            val reasonRes = when (failure) {
                SkillsMarketInstallFailure.NETWORK -> R.string.skills_market_error_network
                SkillsMarketInstallFailure.SERVER -> R.string.skills_market_error_server
                SkillsMarketInstallFailure.INVALID_NAME -> R.string.skills_import_error_invalid_name
                SkillsMarketInstallFailure.NAME_CONFLICT -> R.string.skills_import_error_name_conflict
                SkillsMarketInstallFailure.EMPTY_CONTENT -> R.string.skills_import_error_empty_content
                SkillsMarketInstallFailure.IO -> R.string.skills_import_error_io
            }
            val message = context.getString(R.string.skills_market_install_failed, context.getString(reasonRes))
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearInstallFeedback()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!state.loading && !state.loadFailed && state.categories.isNotEmpty()) {
            CategoryChipRow(
                categories = state.categories,
                selectedCategoryId = state.selectedCategoryId,
                onSelect = viewModel::selectCategory
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.loadFailed -> LoadFailedPane(onRetry = viewModel::reload)
                else -> SkillListPane(
                    state = state,
                    viewModel = viewModel
                )
            }
        }
    }

    if (state.detailOpen) {
        DetailSheet(
            state = state,
            onRetryDetail = viewModel::retryDetail,
            onInstall = viewModel::install,
            onDismiss = viewModel::dismissDetail
        )
    }
}

/** 顶部分类筛选条：「全部」+ 服务端分类，横向滑动。 */
@Composable
private fun CategoryChipRow(
    categories: List<MarketplaceCategory>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.skills_market_all)) }
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedCategoryId == category.id,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) }
            )
        }
    }
}

/** 列表主体：空态 / 带自动翻页的技能列表。 */
@Composable
private fun SkillListPane(
    state: SkillsMarketUiState,
    viewModel: SkillsMarketViewModel
) {
    if (state.skills.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.skills_market_empty_category),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.xl)
            )
        }
        return
    }

    val listState = rememberLazyListState()
    // 无限滚动：可见项接近列表末尾就翻下一页。
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .collect { info ->
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                val current = viewModel.state.value
                if (
                    info.totalItemsCount > 0 &&
                    lastVisible >= info.totalItemsCount - 3 &&
                    current.page < current.totalPages &&
                    !current.loading &&
                    !current.appendingPage
                ) {
                    viewModel.loadNextPage()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        itemsIndexed(state.skills, key = { _, item -> item.id }) { _, summary ->
            MarketplaceSkillRow(summary = summary, onClick = { viewModel.openDetail(summary) })
        }
        if (state.appendingPage) {
            item(key = "appending") {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** 单个市场技能行：图标 + 名称/描述 + 右箭头，样式对齐技能列表。 */
@Composable
private fun MarketplaceSkillRow(
    summary: MarketplaceSkillSummary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(MaterialTheme.semanticColors.cardSurface)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(start = Spacing.md, end = Spacing.md, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FeatherIcons.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.semanticColors.subtleText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 整页加载失败：离线图标 + 文案 + 重试按钮。 */
@Composable
private fun LoadFailedPane(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                imageVector = FeatherIcons.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = stringResource(R.string.skills_market_error_network),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.skills_market_retry))
            }
        }
    }
}

/** 详情底部弹层：元信息 + 描述 + 安装按钮。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(
    state: SkillsMarketUiState,
    onRetryDetail: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            when {
                state.detailLoading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                state.detailError || state.detail == null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg)
                ) {
                    Text(
                        text = stringResource(R.string.skills_market_error_detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onRetryDetail) {
                        Text(stringResource(R.string.skills_market_retry))
                    }
                }
                else -> {
                    val detail = state.detail
                    if (detail != null) {
                        DetailContent(
                            detail = detail,
                            installing = state.installingId != null,
                            onInstall = onInstall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: MarketplaceSkillDetail,
    installing: Boolean,
    onInstall: () -> Unit
) {
    Text(
        text = detail.name,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
    )

    val metaParts = buildList {
        detail.creator?.let { add(it) }
        formatPublishedDate(detail.publishedAt)?.let { add(it) }
        detail.version?.let { add("v" + it) }
    }
    if (metaParts.isNotEmpty()) {
        Text(
            text = metaParts.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (detail.description.isNotBlank()) {
        Text(
            text = detail.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    Text(
        text = stringResource(R.string.skills_market_scope_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Button(
        onClick = onInstall,
        enabled = !installing,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (installing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(stringResource(R.string.skills_market_installing))
        } else {
            Text(stringResource(R.string.skills_market_install))
        }
    }
}

/** ISO 时间转本地化日期；解析失败返回 null（不展示该行）。 */
private fun formatPublishedDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        Instant.parse(raw)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    }.getOrNull()
}
