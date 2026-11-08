package com.aicode.feature.workspace.domain.project

/**
 * 用户在 Sketchware 风格详情页填的 Android 工程参数。
 * 颜色为 ARGB Int（高 8 位 alpha）。
 */
data class AndroidProjectSpec(
    val appName: String,
    val packageName: String,
    val projectName: String,
    val colorAccent: Int,
    val colorPrimary: Int,
    val colorPrimaryDark: Int,
    val colorControlHighlight: Int,
    val colorControlNormal: Int,
    val minSdk: Int = DEFAULT_MIN_SDK,
    val targetSdk: Int = DEFAULT_TARGET_SDK,
    val compileSdk: Int = DEFAULT_COMPILE_SDK,
    val versionCode: Int = 1,
    val versionName: String = "1.0.0"
) {
    companion object {
        const val DEFAULT_MIN_SDK = 24
        const val DEFAULT_TARGET_SDK = 34
        const val DEFAULT_COMPILE_SDK = 34

        const val DEFAULT_COLOR_ACCENT = 0xFF2196F3.toInt()
        const val DEFAULT_COLOR_PRIMARY = 0xFF2196F3.toInt()
        const val DEFAULT_COLOR_PRIMARY_DARK = 0xFF1976D2.toInt()
        const val DEFAULT_COLOR_HIGHLIGHT = 0xFFE3F2FD.toInt()
        const val DEFAULT_COLOR_NORMAL = 0xFFBDBDBD.toInt()
    }
}

data class ThemePreset(
    val id: String,
    val colorAccent: Int,
    val colorPrimary: Int,
    val colorPrimaryDark: Int,
    val colorControlHighlight: Int,
    val colorControlNormal: Int
)

object AndroidThemePresets {
    val all: List<ThemePreset> = listOf(
        ThemePreset("material_purple", 0xFF03DAC5.toInt(), 0xFF6200EE.toInt(), 0xFF3700B3.toInt(), 0xFFE8EAF6.toInt(), 0xFFBDBDBD.toInt()),
        ThemePreset("material_blue", 0xFF03DAC5.toInt(), 0xFF1976D2.toInt(), 0xFF0D47A1.toInt(), 0xFFE3F2FD.toInt(), 0xFFBDBDBD.toInt()),
        ThemePreset("material_green", 0xFF03DAC5.toInt(), 0xFF388E3C.toInt(), 0xFF1B5E20.toInt(), 0xFFE8F5E8.toInt(), 0xFFBDBDBD.toInt()),
        ThemePreset("material_red", 0xFF03DAC5.toInt(), 0xFFD32F2F.toInt(), 0xFFB71C1C.toInt(), 0xFFFFEBEE.toInt(), 0xFFBDBDBD.toInt()),
        ThemePreset("material_orange", 0xFF03DAC5.toInt(), 0xFFF57C00.toInt(), 0xFFE65100.toInt(), 0xFFFFF3E0.toInt(), 0xFFBDBDBD.toInt()),
        ThemePreset("material_teal", 0xFF03DAC5.toInt(), 0xFF00796B.toInt(), 0xFF004D40.toInt(), 0xFFE0F2F1.toInt(), 0xFFBDBDBD.toInt()),
        ThemePreset("material_indigo", 0xFF03DAC5.toInt(), 0xFF3F51B5.toInt(), 0xFF1A237E.toInt(), 0xFFE8EAF6.toInt(), 0xFFBDBDBD.toInt()),
        ThemePreset("material_pink", 0xFF03DAC5.toInt(), 0xFFC2185B.toInt(), 0xFF880E4F.toInt(), 0xFFFCE4EC.toInt(), 0xFFBDBDBD.toInt()),
        ThemePreset("dark_purple", 0xFF03DAC5.toInt(), 0xFFBB86FC.toInt(), 0xFF3700B3.toInt(), 0xFF1F1F1F.toInt(), 0xFF666666.toInt()),
        ThemePreset("dark_blue", 0xFF03DAC5.toInt(), 0xFF64B5F6.toInt(), 0xFF1976D2.toInt(), 0xFF1F1F1F.toInt(), 0xFF666666.toInt()),
        ThemePreset("light_purple", 0xFF03DAC5.toInt(), 0xFF6200EE.toInt(), 0xFF3700B3.toInt(), 0xFFF3E5F5.toInt(), 0xFFE1BEE7.toInt()),
        ThemePreset("light_blue", 0xFF03DAC5.toInt(), 0xFF1976D2.toInt(), 0xFF0D47A1.toInt(), 0xFFE3F2FD.toInt(), 0xFFBBDEFB.toInt())
    )

    val default: ThemePreset = ThemePreset(
        id = "default",
        colorAccent = AndroidProjectSpec.DEFAULT_COLOR_ACCENT,
        colorPrimary = AndroidProjectSpec.DEFAULT_COLOR_PRIMARY,
        colorPrimaryDark = AndroidProjectSpec.DEFAULT_COLOR_PRIMARY_DARK,
        colorControlHighlight = AndroidProjectSpec.DEFAULT_COLOR_HIGHLIGHT,
        colorControlNormal = AndroidProjectSpec.DEFAULT_COLOR_NORMAL
    )
}
