package ca.adamhammer.babelfit.samples.jsoneditor.model

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JsonDocumentTest {

    @Test
    fun `set leaf on empty object`() {
        val doc = JsonDocument.empty()
        val result = doc.setAtPath("/name", JsonPrimitive("Alice"))
        assertEquals("Alice", result.getAtPath("/name")?.jsonPrimitive?.content)
    }

    @Test
    fun `set array on empty object`() {
        val doc = JsonDocument.empty()
        val colors = buildJsonArray {
            add("red")
            add("green")
            add("blue")
        }
        val result = doc.setAtPath("/colors", colors)
        val stored = result.getAtPath("/colors")
        assertNotNull(stored)
        assertTrue(stored is JsonArray)
        assertEquals(3, (stored as JsonArray).size)
    }

    @Test
    fun `set nested path creates intermediates`() {
        val doc = JsonDocument.empty()
        val result = doc.setAtPath("/users/0/name", JsonPrimitive("Bob"))
        assertEquals("Bob", result.getAtPath("/users/0/name")?.jsonPrimitive?.content)
    }

    @Test
    fun `set overwrites existing value`() {
        val doc = JsonDocument.empty()
            .setAtPath("/name", JsonPrimitive("Alice"))
        val result = doc.setAtPath("/name", JsonPrimitive("Bob"))
        assertEquals("Bob", result.getAtPath("/name")?.jsonPrimitive?.content)
    }

    @Test
    fun `get at path returns null for missing`() {
        val doc = JsonDocument.empty()
        assertNull(doc.getAtPath("/nonexistent"))
    }

    @Test
    fun `get root returns entire document`() {
        val doc = JsonDocument.empty().setAtPath("/x", JsonPrimitive(1))
        val root = doc.getAtPath("")
        assertTrue(root is JsonObject)
    }

    @Test
    fun `delete key`() {
        val doc = JsonDocument.empty()
            .setAtPath("/a", JsonPrimitive(1))
            .setAtPath("/b", JsonPrimitive(2))
        val result = doc.deleteAtPath("/a")
        assertNull(result.getAtPath("/a"))
        assertNotNull(result.getAtPath("/b"))
    }

    @Test
    fun `delete from array`() {
        val doc = JsonDocument.fromString("""{"items": [1, 2, 3]}""")
        val result = doc.deleteAtPath("/items/1")
        val items = result.getAtPath("/items") as JsonArray
        assertEquals(2, items.size)
        assertEquals(1, items[0].jsonPrimitive.int)
        assertEquals(3, items[1].jsonPrimitive.int)
    }

    @Test
    fun `move node`() {
        val doc = JsonDocument.empty()
            .setAtPath("/source", JsonPrimitive("data"))
        val result = doc.moveNode("/source", "/dest")
        assertNull(result.getAtPath("/source"))
        assertEquals("data", result.getAtPath("/dest")?.jsonPrimitive?.content)
    }

    @Test
    fun `list keys on object`() {
        val doc = JsonDocument.empty()
            .setAtPath("/a", JsonPrimitive(1))
            .setAtPath("/b", JsonPrimitive(2))
        val keys = doc.listKeys("")
        assertTrue(keys.containsAll(listOf("a", "b")))
    }

    @Test
    fun `list keys on array`() {
        val doc = JsonDocument.fromString("""{"items": [10, 20, 30]}""")
        val keys = doc.listKeys("/items")
        assertEquals(listOf("0", "1", "2"), keys)
    }

    @Test
    fun `query finds nested keys`() {
        val doc = JsonDocument.fromString("""{"a": {"name": "x"}, "b": {"name": "y"}}""")
        val paths = doc.query("name")
        assertEquals(2, paths.size)
        assertTrue(paths.contains("/a/name"))
        assertTrue(paths.contains("/b/name"))
    }

    @Test
    fun `nodeCount and depth`() {
        val doc = JsonDocument.fromString("""{"a": {"b": 1}}""")
        assertEquals(3, doc.nodeCount()) // root obj + inner obj + primitive
        assertEquals(2, doc.depth())
    }

    @Test
    fun `set array element by index`() {
        val doc = JsonDocument.fromString("""{"items": [1, 2, 3]}""")
        val result = doc.setAtPath("/items/1", JsonPrimitive(99))
        assertEquals(99, result.getAtPath("/items/1")?.jsonPrimitive?.int)
    }

    @Test
    fun `append to array`() {
        val doc = JsonDocument.fromString("""{"items": [1, 2]}""")
        val result = doc.setAtPath("/items/2", JsonPrimitive(3))
        val items = result.getAtPath("/items") as JsonArray
        assertEquals(3, items.size)
    }

    @Test
    fun `set root replaces entire document`() {
        val doc = JsonDocument.empty()
        val newRoot = Json.parseToJsonElement("""{"a": 1, "b": 2}""")
        val updated = doc.setAtPath("", newRoot)
        assertEquals(1, updated.getAtPath("/a")?.jsonPrimitive?.int)
        assertEquals(2, updated.getAtPath("/b")?.jsonPrimitive?.int)
    }

    @Test
    fun `cannot delete root`() {
        val doc = JsonDocument.empty()
        assertThrows<IllegalArgumentException> {
            doc.deleteAtPath("")
        }
    }

    @Test
    fun `fromString and toJsonString roundtrip`() {
        val json = """{"key": "value"}"""
        val doc = JsonDocument.fromString(json)
        val back = JsonDocument.fromString(doc.toJsonString())
        assertEquals(
            doc.getAtPath("/key")?.jsonPrimitive?.content,
            back.getAtPath("/key")?.jsonPrimitive?.content
        )
    }
}
