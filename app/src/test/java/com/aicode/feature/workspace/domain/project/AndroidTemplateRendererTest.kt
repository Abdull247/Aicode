package com.aicode.feature.workspace.domain.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTemplateRendererTest {

    private val spec = AndroidProjectSpec(
        appName = "My App",
        packageName = "com.example.myapp",
        projectName = "my-app",
        colorAccent = AndroidProjectSpec.DEFAULT_COLOR_ACCENT,
        colorPrimary = AndroidProjectSpec.DEFAULT_COLOR_PRIMARY,
        colorPrimaryDark = AndroidProjectSpec.DEFAULT_COLOR_PRIMARY_DARK,
        colorControlHighlight = AndroidProjectSpec.DEFAULT_COLOR_HIGHLIGHT,
        colorControlNormal = AndroidProjectSpec.DEFAULT_COLOR_NORMAL
    )

    @Test
    fun packageValidation() {
        assertTrue(AndroidTemplateRenderer.isValidPackage("com.example.myapp"))
        assertTrue(AndroidTemplateRenderer.isValidPackage("a.b"))
        assertFalse(AndroidTemplateRenderer.isValidPackage("com"))
        assertFalse(AndroidTemplateRenderer.isValidPackage("Com.Example"))
        assertFalse(AndroidTemplateRenderer.isValidPackage("com..example"))
        assertFalse(AndroidTemplateRenderer.isValidPackage(""))
    }

    @Test
    fun suggestPackage_usesExamplePrefixAndSanitizes() {
        assertEquals("com.example.myapp", AndroidTemplateRenderer.suggestPackage("My App"))
        assertEquals("com.example.app123", AndroidTemplateRenderer.suggestPackage("123"))
        assertEquals("com.example.app", AndroidTemplateRenderer.suggestPackage("!!!"))
    }

    @Test
    fun packagePath_replacesDotsWithSlashes() {
        assertEquals("com/example/myapp", AndroidTemplateRenderer.packagePath("com.example.myapp"))
    }

    @Test
    fun colorHex_formatsOpaqueRgbAndArgb() {
        assertEquals("#2196F3", AndroidTemplateRenderer.toColorHex(0xFF2196F3.toInt()))
        assertEquals("#802196F3", AndroidTemplateRenderer.toColorHex(0x802196F3.toInt()))
    }

    @Test
    fun parseColorHex_acceptsSixEightDigitsAndHash() {
        assertEquals(0xFF2196F3.toInt(), AndroidTemplateRenderer.parseColorHex("#2196F3"))
        assertEquals(0xFF2196F3.toInt(), AndroidTemplateRenderer.parseColorHex("2196F3"))
        assertEquals(0x802196F3.toInt(), AndroidTemplateRenderer.parseColorHex("#802196F3"))
        assertNull(AndroidTemplateRenderer.parseColorHex("xyz"))
        assertNull(AndroidTemplateRenderer.parseColorHex("#12345"))
    }

    @Test
    fun render_substitutesAllPlaceholders() {
        val files = AndroidTemplateRenderer.render(spec)

        assertTrue(files.keys.any { it == "app/src/main/java/com/example/myapp/MainActivity.java" })
        assertTrue(files.keys.contains("aicode.json"))

        val manifest = files.getValue("app/src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("package=\"com.example.myapp\""))
        assertTrue(manifest.contains("android:minSdkVersion=\"24\""))
        assertTrue(manifest.contains("android:targetSdkVersion=\"34\""))

        val colors = files.getValue("app/src/main/res/values/colors.xml")
        assertTrue(colors.contains(">#2196F3<"))
        assertTrue(colors.contains(">#1976D2<"))

        val strings = files.getValue("app/src/main/res/values/strings.xml")
        assertTrue(strings.contains("My App"))

        files.values.forEach { content ->
            assertFalse("placeholder left: $content", content.contains("{{"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun render_rejectsInvalidPackage() {
        AndroidTemplateRenderer.render(spec.copy(packageName = "Bad Package"))
    }
}
