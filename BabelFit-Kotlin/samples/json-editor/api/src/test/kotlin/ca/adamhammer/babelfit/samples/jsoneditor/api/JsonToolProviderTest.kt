package ca.adamhammer.babelfit.samples.jsoneditor.api

import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonToolProviderTest {

    private fun createProvider(
        doc: JsonDocument = JsonDocument.empty()
    ): Pair<JsonToolProvider, () -> JsonDocument> {
        var current = doc
        val provider = JsonToolProvider(
            docProvider = { current },
            docUpdater = { current = it }
        )
        return provider to { current }
    }

    @Test
    fun `handleSet creates new key`() = runTest {
        val (provider, getDoc) = createProvider()
        val call = ToolCall(id = "1", toolName = "json_set", arguments = """{"path": "/name", "value": "\"Alice\""}""")
        val result = provider.callTool(call)
        assertFalse(result.isError, "Expected success but got: ${result.content}")
        assertEquals("Alice", getDoc().getAtPath("/name")?.jsonPrimitive?.content)
    }

    @Test
    fun `handleSet with array value`() = runTest {
        val (provider, getDoc) = createProvider()
        val call = ToolCall(
            id = "1", toolName = "json_set",
            arguments = """{"path": "/colors", "value": "[\"red\", \"green\", \"blue\"]"}"""
        )
        val result = provider.callTool(call)
        assertFalse(result.isError, "Expected success but got: ${result.content}")
        val colors = getDoc().getAtPath("/colors")?.jsonArray
        assertNotNull(colors)
        assertEquals(3, colors!!.size)
        assertEquals("red", colors[0].jsonPrimitive.content)
    }

    @Test
    fun `handleGet returns value`() = runTest {
        val doc = JsonDocument.empty().setAtPath("/x", JsonPrimitive(42))
        val (provider, _) = createProvider(doc)
        val call = ToolCall(id = "1", toolName = "json_get", arguments = """{"path": "/x"}""")
        val result = provider.callTool(call)
        assertFalse(result.isError)
        assertTrue(result.content.contains("42"))
    }

    @Test
    fun `handleGet missing path returns error`() = runTest {
        val (provider, _) = createProvider()
        val call = ToolCall(id = "1", toolName = "json_get", arguments = """{"path": "/missing"}""")
        val result = provider.callTool(call)
        assertTrue(result.isError)
    }

    @Test
    fun `handleDelete removes key`() = runTest {
        val doc = JsonDocument.empty().setAtPath("/x", JsonPrimitive(1))
        val (provider, getDoc) = createProvider(doc)
        val call = ToolCall(id = "1", toolName = "json_delete", arguments = """{"path": "/x"}""")
        val result = provider.callTool(call)
        assertFalse(result.isError)
        assertNull(getDoc().getAtPath("/x"))
    }

    @Test
    fun `handleList returns keys`() = runTest {
        val doc = JsonDocument.empty()
            .setAtPath("/a", JsonPrimitive(1))
            .setAtPath("/b", JsonPrimitive(2))
        val (provider, _) = createProvider(doc)
        val call = ToolCall(id = "1", toolName = "json_list", arguments = """{"path": ""}""")
        val result = provider.callTool(call)
        assertFalse(result.isError)
        assertTrue(result.content.contains("a"))
        assertTrue(result.content.contains("b"))
    }

    @Test
    fun `listener receives tool call notifications`() = runTest {
        val calls = mutableListOf<Triple<String, String, String>>()
        val listener = object : JsonEditorListener {
            override fun onToolCall(toolName: String, args: String, result: String) {
                calls.add(Triple(toolName, args, result))
            }
        }
        var doc = JsonDocument.empty()
        val provider = JsonToolProvider(
            docProvider = { doc },
            docUpdater = { doc = it },
            listener = listener
        )
        val call = ToolCall(id = "1", toolName = "json_set", arguments = """{"path": "/x", "value": "1"}""")
        provider.callTool(call)
        assertEquals(1, calls.size)
        assertEquals("json_set", calls[0].first)
    }
}
