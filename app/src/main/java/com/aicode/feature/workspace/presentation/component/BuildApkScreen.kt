package com.aicode.feature.workspace.presentation.component

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.theme.semanticColors
import com.aicode.core.ui.AdaptiveModalBottomSheet
import com.aicode.feature.settings.presentation.component.SettingsDivider
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.SettingsRow
import com.aicode.feature.settings.presentation.component.settingsPageBackground
import com.aicode.feature.workspace.presentation.BuildApkViewModel
import com.aicode.feature.workspace.presentation.BuildOutcome
import com.aicode.feature.workspace.presentation.LayoutPreviewState
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Play
import compose.icons.feathericons.Smartphone
import compose.icons.feathericons.X

/**
 * 「Build APK」页:左侧列出工程 `res/layout` 下的 XML 布局,中间手机框预览所选布局;
 * 顶栏 Run 用内置 AICore 编译器(`aic build`)构建,并弹出不可取消的终端风格日志底栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildApkScreen(
    workspacePath: String?,
    onNavigateBack: () -> Unit
) {
    val viewModel: BuildApkViewModel = hiltViewModel()
    val layouts by viewModel.layouts.collectAsStateWithLifecycle()
    val selected by viewModel.selectedLayout.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()

    var showLayoutSheet by remember { mutableStateOf(false) }
    var showLogSheet by remember { mutableStateOf(false) }

    LaunchedEffect(workspacePath) { viewModel.load(workspacePath) }

    val background = settingsPageBackground()

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.build_apk_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showLayoutSheet = true }) {
                        Icon(
                            FeatherIcons.Smartphone,
                            contentDescription = stringResource(R.string.build_apk_select_layout)
                        )
                    }
                    IconButton(
                        onClick = {
                            showLogSheet = true
                            viewModel.run(workspacePath)
                        },
                        enabled = !running
                    ) {
                        Icon(FeatherIcons.Play, contentDescription = stringResource(R.string.build_apk_run))
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LayoutRail(
                layouts = layouts,
                selected = selected,
                onSelect = viewModel::selectLayout,
                modifier = Modifier
                    .width(150.dp)
                    .fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.semanticColors.subtleBorder)
            )
            PreviewArea(
                preview = preview,
                layoutName = selected,
                onSelectClick = { showLayoutSheet = true },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }

    if (showLayoutSheet) {
        AdaptiveModalBottomSheet(
            onDismissRequest = { showLayoutSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.xl)
            ) {
                Text(
                    text = stringResource(R.string.build_apk_select_layout),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = Spacing.md)
                )
                if (layouts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.build_apk_no_layouts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SettingsGroup {
                        layouts.forEachIndexed { index, name ->
                            if (index > 0) SettingsDivider()
                            SettingsRow(
                                title = name,
                                onClick = {
                                    showLayoutSheet = false
                                    viewModel.selectLayout(name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLogSheet) {
        BuildLogSheet(
            log = log,
            running = running,
            outcome = outcome,
            onClose = { if (!running) showLogSheet = false }
        )
    }
}

@Composable
private fun LayoutRail(
    layouts: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.padding(vertical = Spacing.sm)) {
        item {
            Text(
                text = stringResource(R.string.build_apk_layouts),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )
        }
        if (layouts.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.build_apk_no_layouts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md)
                )
            }
        } else {
            items(layouts) { name ->
                val isSelected = name == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(name) }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = FeatherIcons.FileText,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewArea(
    preview: LayoutPreviewState,
    layoutName: String?,
    onSelectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (preview) {
                LayoutPreviewState.Loading -> CircularProgressIndicator()
                is LayoutPreviewState.Ready -> PhoneFrame(
                    bitmap = preview.bitmap,
                    contentDescription = layoutName ?: stringResource(R.string.build_apk_title)
                )
                is LayoutPreviewState.Failed -> Text(
                    text = preview.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LayoutPreviewState.None -> Text(
                    text = stringResource(R.string.build_apk_no_layouts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        TextButton(onClick = onSelectClick) {
            Icon(FeatherIcons.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.build_apk_select_layout))
        }
    }
}

@Composable
private fun PhoneFrame(bitmap: Bitmap, contentDescription: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.xxl)
    Surface(
        modifier = modifier
            .widthIn(max = 280.dp)
            .aspectRatio(9f / 16f),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.semanticColors.subtleBorder),
        shadowElevation = 2.dp
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            contentScale = ContentScale.FillBounds
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildLogSheet(
    log: List<String>,
    running: Boolean,
    outcome: BuildOutcome?,
    onClose: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AdaptiveModalBottomSheet(
        onDismissRequest = { if (!running) onClose() },
        sheetState = sheetState,
        containerColor = TerminalPanelBackground,
        contentColor = TerminalTextColor,
        dragHandle = null,
        sheetGesturesEnabled = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.lg, bottom = Spacing.xl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.build_apk_logs_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TerminalTextColor,
                    modifier = Modifier.weight(1f)
                )
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TerminalAccentColor
                    )
                } else {
                    IconButton(onClick = onClose) {
                        Icon(
                            FeatherIcons.X,
                            contentDescription = stringResource(R.string.build_apk_close),
                            tint = TerminalTextColor
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            val scroll = rememberScrollState()
            LaunchedEffect(log.size) { scroll.animateScrollTo(scroll.maxValue) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 440.dp)
                    .clip(RoundedCornerShape(Radius.mdLarge))
                    .background(TerminalScreenBackground)
                    .border(1.dp, TerminalBorderColor, RoundedCornerShape(Radius.mdLarge))
            ) {
                Text(
                    text = if (log.isEmpty() && running) {
                        stringResource(R.string.build_apk_running)
                    } else {
                        log.joinToString("\n")
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TerminalOutputColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .padding(Spacing.md)
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            when {
                running -> Text(
                    text = stringResource(R.string.build_apk_running),
                    style = MaterialTheme.typography.bodySmall,
                    color = TerminalAccentColor
                )
                outcome is BuildOutcome.Success -> Text(
                    text = stringResource(R.string.build_apk_success) + "\n" +
                        stringResource(R.string.build_apk_apk_path),
                    style = MaterialTheme.typography.bodySmall,
                    color = TerminalSuccessColor
                )
                outcome is BuildOutcome.Failure -> Text(
                    text = stringResource(R.string.build_apk_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = TerminalErrorColor
                )
            }
        }
    }
}

private val TerminalPanelBackground = Color(0xFF0D1117)
private val TerminalScreenBackground = Color(0xFF010409)
private val TerminalBorderColor = Color(0xFF21262D)
private val TerminalTextColor = Color(0xFFE6EDF3)
private val TerminalOutputColor = Color(0xFFB7F7C1)
private val TerminalAccentColor = Color(0xFF58A6FF)
private val TerminalSuccessColor = Color(0xFF3FB950)
private val TerminalErrorColor = Color(0xFFF85149)
