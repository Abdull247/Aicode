package com.aicode.feature.workspace.domain.project

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ProjectKindDetectorTest {

    @Test
    fun blankOrMissingPath_isNotAndroid() {
        assertFalse(ProjectKindDetector.isAndroidProject(""))
        assertFalse(ProjectKindDetector.isAndroidProject("/path/does/not/exist"))
    }

    @Test
    fun emptyDirectory_isNotAndroid() {
        val dir = createTempDirectory("plain-project").toFile()
        try {
            assertFalse(ProjectKindDetector.isAndroidProject(dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aicodeJsonWithMinSdk_isAndroid() {
        val dir = createTempDirectory("android-config").toFile()
        try {
            File(dir, "aicode.json").writeText("""{"minSdk": 26, "compileSdk": 28}""")
            assertTrue(ProjectKindDetector.isAndroidProject(dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aicodeJsonWithNativeXml_isAndroid() {
        val dir = createTempDirectory("android-xml").toFile()
        try {
            File(dir, "aicode.json").writeText("""{"template": "native-xml"}""")
            assertTrue(ProjectKindDetector.isAndroidProject(dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aicodeJsonWithAicoreEngine_isAndroid() {
        val dir = createTempDirectory("android-engine").toFile()
        try {
            File(dir, "aicode.json").writeText("""{"engine": "aicore"}""")
            assertTrue(ProjectKindDetector.isAndroidProject(dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun appManifest_isAndroid() {
        val dir = createTempDirectory("android-manifest").toFile()
        try {
            val manifest = File(dir, "app/src/main/AndroidManifest.xml")
            manifest.parentFile.mkdirs()
            manifest.writeText("<manifest />")
            assertTrue(ProjectKindDetector.isAndroidProject(dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rootManifest_isAndroid() {
        val dir = createTempDirectory("android-root-manifest").toFile()
        try {
            File(dir, "AndroidManifest.xml").writeText("<manifest />")
            assertTrue(ProjectKindDetector.isAndroidProject(dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }
}
