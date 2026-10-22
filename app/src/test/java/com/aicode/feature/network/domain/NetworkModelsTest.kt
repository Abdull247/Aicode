package com.aicode.feature.network.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModelsTest {

    @Test
    fun normalizeRequestUrl_addsHttpsWhenMissing() {
        assertEquals("https://example.com", normalizeRequestUrl("example.com"))
        assertEquals("https://example.com/path", normalizeRequestUrl("  example.com/path  "))
    }

    @Test
    fun normalizeRequestUrl_keepsExistingScheme() {
        assertEquals("http://example.com", normalizeRequestUrl("http://example.com"))
        assertEquals("https://example.com", normalizeRequestUrl("HTTPS://example.com"))
    }

    @Test
    fun normalizeRequestUrl_keepsBlank() {
        assertEquals("", normalizeRequestUrl("   "))
    }

    @Test
    fun prettyPrintJson_formatsObjectAndArray() {
        val objectJson = prettyPrintJson("""{"b":1,"a":2}""")
        assertTrue(objectJson.contains("\n"))
        assertTrue(objectJson.contains("\"b\""))

        val arrayJson = prettyPrintJson("""[1,2]""")
        assertTrue(arrayJson.contains("\n"))
        assertTrue(arrayJson.contains("1"))
    }

    @Test
    fun prettyPrintJson_leavesPlainText() {
        assertEquals("not json", prettyPrintJson("not json"))
    }

    @Test
    fun headersToJson_serializesDuplicateNamesAsArray() {
        val json = headersToJson(
            listOf(
                NetworkHeader("Accept", "text/html"),
                NetworkHeader("Set-Cookie", "a=1"),
                NetworkHeader("Set-Cookie", "b=2")
            )
        )
        assertTrue(json.contains("\"Accept\""))
        assertTrue(json.contains("\"Set-Cookie\""))
        assertTrue(json.contains("a=1"))
        assertTrue(json.contains("b=2"))
    }

    @Test
    fun bodyToTableRows_parsesObject() {
        val rows = bodyToTableRows("""{"name":"ai","ok":true}""")
        assertEquals(2, rows.size)
        assertEquals(setOf("name", "ok"), rows.map { it.name }.toSet())
    }

    @Test
    fun bodyToTableRows_parsesArray() {
        val rows = bodyToTableRows("""["a","b"]""")
        assertEquals(listOf("[0]", "[1]"), rows.map { it.name })
        assertEquals(listOf("a", "b"), rows.map { it.value })
    }

    @Test
    fun defaultViewMode_matchesTab() {
        assertEquals(ResponseViewMode.TABLE, defaultViewMode(ResponseTab.HEADERS))
        assertEquals(ResponseViewMode.JSON, defaultViewMode(ResponseTab.BODY))
        assertNull(defaultViewMode(ResponseTab.METRICS))
    }
}
