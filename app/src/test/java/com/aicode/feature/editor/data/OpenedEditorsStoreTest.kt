package com.aicode.feature.editor.data

import com.aicode.feature.editor.domain.OpenedEditorTab
import com.aicode.feature.editor.domain.OpenedEditorsState
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import com.aicode.feature.workspace.domain.ProjectAicodeRoot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OpenedEditorsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(): OpenedEditorsStore {
        val aicodeRoot = mockk<ProjectAicodeRoot>(relaxed = true)
        every { aicodeRoot.current() } returns File(tmp.root, "ws/.aicode")
        every { aicodeRoot.forPath(any()) } answers { File(tmp.root, ".aicode") }
        val workspaceRepository = mockk<WorkspaceRepository>(relaxed = true)
        every { workspaceRepository.currentPath() } returns "ws"
        return OpenedEditorsStore(aicodeRoot, workspaceRepository)
    }

    @Test
    fun saveAndLoadRoundTripsTabs() = runBlocking {
        val store = store()
        val state = OpenedEditorsState(
            tabs = listOf(
                OpenedEditorTab(
                    path = "app/src/main/Main.kt",
                    name = "Main.kt",
                    cursorLine = 12,
                    cursorColumn = 4,
                    scrollX = 3,
                    scrollY = 240,
                    lastOpenedAt = 42L
                ),
                OpenedEditorTab(path = "README.md", name = "README.md")
            ),
            activePath = "app/src/main/Main.kt"
        )

        store.save(state)
        assertEquals(state, store.load())
    }

    @Test
    fun loadReturnsEmptyStateWhenFileMissing() = runBlocking {
        val store = store()
        assertEquals(OpenedEditorsState(), store.load())
        assertEquals(OpenedEditorsState(), store.load("other-workspace"))
    }

    @Test
    fun saveWritesPrettyJsonIntoConfigDirectory() = runBlocking {
        val store = store()
        val state = OpenedEditorsState(
            tabs = listOf(OpenedEditorTab(path = "a.txt", name = "a.txt", cursorLine = 2)),
            activePath = "a.txt"
        )

        store.save(state)

        val file = File(File(tmp.root, ".aicode"), "config/opened_editors.json")
        assertTrue(file.isFile)
        val text = file.readText()
        val reparsed = Json.decodeFromString(OpenedEditorsState.serializer(), text)
        assertEquals(state, reparsed)
        assertNotNull(reparsed.activePath)
    }

    @Test
    fun loadingIgnoresUnknownJsonKeys() = runBlocking {
        val store = store()
        val dir = File(tmp.root, ".aicode/config")
        dir.mkdirs()
        File(dir, "opened_editors.json").writeText(
            """
            {
              "tabs": [
                {
                  "path": "a.txt",
                  "name": "a.txt",
                  "cursorLine": 5,
                  "futureField": true
                }
              ],
              "activePath": "a.txt"
            }
            """.trimIndent()
        )

        val loaded = store.load()
        assertEquals(1, loaded.tabs.size)
        assertEquals("a.txt", loaded.activePath)
        assertEquals(5, loaded.tabs[0].cursorLine)
    }
}
