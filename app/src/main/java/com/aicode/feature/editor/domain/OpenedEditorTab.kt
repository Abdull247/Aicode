package com.aicode.feature.editor.domain

import kotlinx.serialization.Serializable

/** One open editor tab persisted to `.aicode/config/opened_editors.json`. */
@Serializable
data class OpenedEditorTab(
    val path: String,
    val name: String,
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
    val scrollX: Int = 0,
    val scrollY: Int = 0,
    val lastOpenedAt: Long = 0L
)

/** Snapshot of the editor workspace: open tabs plus the last active file. */
@Serializable
data class OpenedEditorsState(
    val tabs: List<OpenedEditorTab> = emptyList(),
    val activePath: String? = null
)
