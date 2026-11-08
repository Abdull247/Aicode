package com.aicode.feature.workspace.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.AppTextField
import com.aicode.core.ui.dialogTextFieldColors
import com.aicode.feature.settings.presentation.component.SettingsDivider
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.SettingsGroupHeader
import com.aicode.feature.settings.presentation.component.settingsPageBackground
import com.aicode.feature.workspace.domain.project.AndroidProjectSpec
import com.aicode.feature.workspace.domain.project.AndroidTemplateRenderer
import com.aicode.feature.workspace.domain.project.AndroidThemePresets
import com.aicode.feature.workspace.domain.project.ThemePreset
import com.aicode.feature.workspace.presentation.CreateWorkspaceViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Check

/**
 * Sketchware 风格的新建 Android 工程详情页:应用名、包名、工程名、主题颜色、主题预设。
 * 颜色可点开十六进制输入框微调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAndroidProjectScreen(
    onNavigateBack: () -> Unit,
    onCreated: () -> Unit,
    onSwitchConfirmed: () -> Unit,
    viewModel: CreateWorkspaceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var packageEdited by remember { mutableStateOf(false) }
    var projectEdited by remember { mutableStateOf(false) }

    var colorAccent by remember { mutableStateOf(AndroidProjectSpec.DEFAULT_COLOR_ACCENT) }
    var colorPrimary by remember { mutableStateOf(AndroidProjectSpec.DEFAULT_COLOR_PRIMARY) }
    var colorPrimaryDark by remember { mutableStateOf(AndroidProjectSpec.DEFAULT_COLOR_PRIMARY_DARK) }
    var colorHighlight by remember { mutableStateOf(AndroidProjectSpec.DEFAULT_COLOR_HIGHLIGHT) }
    var colorNormal by remember { mutableStateOf(AndroidProjectSpec.DEFAULT_COLOR_NORMAL) }
    var selectedPreset by remember { mutableStateOf<String?>(AndroidThemePresets.default.id) }

    var editingColor by remember { mutableStateOf<ColorSlot?>(null) }

    // 应用名变化时,若用户尚未手改,联动推荐包名与工程名。
    LaunchedEffect(appName) {
        if (appName.isBlank()) return@LaunchedEffect
        if (!packageEdited) packageName = AndroidTemplateRenderer.suggestPackage(appName)
        if (!projectEdited) projectName = appName.trim()
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.created) {
        if (state.created != null) {
            viewModel.consumeCreated()
            onCreated()
        }
    }
    BackHandler(enabled = !state.creating, onBack = onNavigateBack)

    val packageValid = viewModel.isValidPackage(packageName)
    val canCreate = appName.isNotBlank() && projectName.isNotBlank() && packageValid && !state.creating

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workspace_android_new_project)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !state.creating) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 应用图标占位:用主色圆底 + 应用名首字母。
            Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(colorPrimary)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = appName.trim().take(1).uppercase().ifEmpty { "A" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            AppTextField(
                value = appName,
                onValueChange = { appName = it },
                label = stringResource(R.string.workspace_android_app_name),
                placeholder = stringResource(R.string.workspace_android_app_name_hint)
            )
            AppTextField(
                value = packageName,
                onValueChange = { packageEdited = true; packageName = it },
                label = stringResource(R.string.workspace_android_package_name),
                placeholder = stringResource(R.string.workspace_android_package_name_hint),
                isError = packageName.isNotBlank() && !packageValid,
                supportingText = if (packageName.isNotBlank() && !packageValid) {
                    { Text(stringResource(R.string.workspace_android_package_invalid)) }
                } else null
            )
            AppTextField(
                value = projectName,
                onValueChange = { projectEdited = true; projectName = it },
                label = stringResource(R.string.workspace_android_project_name),
                placeholder = stringResource(R.string.workspace_android_project_name_hint)
            )

            SettingsGroupHeader(stringResource(R.string.workspace_android_theme_colors))
            SettingsGroup {
                ColorRow(
                    label = stringResource(R.string.workspace_android_color_accent),
                    color = colorAccent,
                    onClick = { editingColor = ColorSlot.ACCENT }
                )
                SettingsDivider()
                ColorRow(
                    label = stringResource(R.string.workspace_android_color_primary),
                    color = colorPrimary,
                    onClick = { editingColor = ColorSlot.PRIMARY }
                )
                SettingsDivider()
                ColorRow(
                    label = stringResource(R.string.workspace_android_color_primary_dark),
                    color = colorPrimaryDark,
                    onClick = { editingColor = ColorSlot.PRIMARY_DARK }
                )
            }

            SettingsGroupHeader(stringResource(R.string.workspace_android_theme_presets))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(AndroidThemePresets.all + AndroidThemePresets.default) { preset ->
                    ThemePresetCard(
                        preset = preset,
                        selected = preset.id == selectedPreset,
                        onClick = {
                            selectedPreset = preset.id
                            colorAccent = preset.colorAccent
                            colorPrimary = preset.colorPrimary
                            colorPrimaryDark = preset.colorPrimaryDark
                            colorHighlight = preset.colorControlHighlight
                            colorNormal = preset.colorControlNormal
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(enabled = !state.creating, onClick = onNavigateBack) {
                    Text(stringResource(R.string.common_cancel))
                }
                Spacer(Modifier.width(Spacing.sm))
                Button(
                    enabled = canCreate,
                    onClick = {
                        viewModel.createAndroid(
                            AndroidProjectSpec(
                                appName = appName.trim(),
                                packageName = packageName.trim(),
                                projectName = projectName.trim(),
                                colorAccent = colorAccent,
                                colorPrimary = colorPrimary,
                                colorPrimaryDark = colorPrimaryDark,
                                colorControlHighlight = colorHighlight,
                                colorControlNormal = colorNormal
                            ),
                            onSwitchConfirmed
                        )
                    }
                ) {
                    if (state.creating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.common_create))
                    }
                }
            }
        }
    }

    editingColor?.let { slot ->
        val current = when (slot) {
            ColorSlot.ACCENT -> colorAccent
            ColorSlot.PRIMARY -> colorPrimary
            ColorSlot.PRIMARY_DARK -> colorPrimaryDark
        }
        HexColorDialog(
            initial = current,
            onConfirm = { value ->
                when (slot) {
                    ColorSlot.ACCENT -> colorAccent = value
                    ColorSlot.PRIMARY -> colorPrimary = value
                    ColorSlot.PRIMARY_DARK -> colorPrimaryDark = value
                }
                selectedPreset = null
                editingColor = null
            },
            onDismiss = { editingColor = null }
        )
    }
}

private enum class ColorSlot { ACCENT, PRIMARY, PRIMARY_DARK }

@Composable
private fun ColorRow(label: String, color: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = AndroidTemplateRenderer.toColorHex(color),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Spacing.md))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
    }
}

@Composable
private fun ThemePresetCard(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(Radius.mdLarge))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(Radius.mdLarge))
            .clickable(onClick = onClick)
            .padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(preset.colorPrimary)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    FeatherIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.size(Spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(Color(preset.colorPrimaryDark))
        )
    }
}

@Composable
private fun HexColorDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(AndroidTemplateRenderer.toColorHex(initial)) }
    val parsed = AndroidTemplateRenderer.parseColorHex(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workspace_android_edit_color)) },
        text = {
            Column {
                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = stringResource(R.string.workspace_android_color_hex),
                    placeholder = "#2196F3",
                    isError = parsed == null,
                    colors = dialogTextFieldColors()
                )
                parsed?.let {
                    Spacer(Modifier.size(Spacing.md))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(40.dp)
                            .clip(RoundedCornerShape(Radius.mdLarge))
                            .background(Color(it))
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onConfirm) }) {
                Text(stringResource(R.string.common_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
