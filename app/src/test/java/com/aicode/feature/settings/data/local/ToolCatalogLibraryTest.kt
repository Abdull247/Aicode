package com.aicode.feature.settings.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogLibraryTest {

    @Test
    fun parse_validCatalog() {
        val text = """
            [
              {
                "name": "readFile",
                "title": "Read file",
                "titleZh": "读取文件",
                "description": "Reads a file.",
                "descriptionZh": "读取文件。",
                "category": "files",
                "permission": "auto",
                "parameters": [
                  {
                    "name": "path",
                    "type": "string",
                    "required": true,
                    "description": "File path",
                    "descriptionZh": "文件路径"
                  },
                  {
                    "name": "start_line",
                    "type": "integer",
                    "required": false,
                    "description": "Start line"
                  }
                ]
              }
            ]
        """.trimIndent()
        val entries = ToolCatalogLibrary.parse(text)
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals("readFile", entry.name)
        assertEquals("files", entry.category)
        assertEquals("auto", entry.permission)
        assertEquals(2, entry.parameters.size)
        assertTrue(entry.parameters[0].required)
        assertTrue(!entry.parameters[1].required)
    }

    @Test
    fun parse_invalidJsonReturnsEmpty() {
        assertTrue(ToolCatalogLibrary.parse("not json").isEmpty())
        assertTrue(ToolCatalogLibrary.parse("{\"unexpected\": true}").isEmpty())
    }

    @Test
    fun titleFor_resolvesLanguageWithFallback() {
        val entry = ToolCatalogEntry(
            name = "t",
            title = "Tool",
            titleZh = "工具",
            description = "Desc",
            descriptionZh = "说明"
        )
        assertEquals("Tool", entry.titleFor("en"))
        assertEquals("工具", entry.titleFor("zh"))
        assertEquals("Desc", entry.descriptionFor("en"))
        assertEquals("说明", entry.descriptionFor("zh"))
    }

    @Test
    fun titleFor_fallsBackToBaseWhenVariantMissing() {
        val entry = ToolCatalogEntry(name = "t", title = "Tool", description = "Desc")
        assertEquals("Tool", entry.titleFor("zh"))
        assertEquals("Desc", entry.descriptionFor("zh"))
    }

    @Test
    fun parse_unknownFieldsIgnored() {
        val text = """[{"name":"x","title":"X","futureField":123,"permission":"ask"}]"""
        val entries = ToolCatalogLibrary.parse(text)
        assertEquals(1, entries.size)
        assertEquals("ask", entries[0].permission)
        assertEquals("extensions", entries[0].category)
    }
}
