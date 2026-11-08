package com.aicode.feature.workspace.domain.project

import java.io.File

object ProjectKindDetector {

    fun isAndroidProject(workspacePath: String): Boolean {
        if (workspacePath.isBlank()) return false
        val root = File(workspacePath)
        if (!root.isDirectory) return false
        val config = File(root, "aicode.json")
        if (config.isFile) {
            val text = runCatching { config.readText(Charsets.UTF_8) }.getOrDefault("")
            if (text.contains("\"minSdk\"") ||
                text.contains("\"compileSdk\"") ||
                text.contains("native-xml") ||
                text.contains("\"engine\": \"aicore\"")
            ) {
                return true
            }
        }
        return File(root, "app/src/main/AndroidManifest.xml").isFile ||
            File(root, "AndroidManifest.xml").isFile
    }
}
