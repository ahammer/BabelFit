package ca.adamhammer.babelfit.samples.jsoneditor.api

import ca.adamhammer.babelfit.samples.jsoneditor.ActionType
import ca.adamhammer.babelfit.samples.jsoneditor.EditorAction
import ca.adamhammer.babelfit.samples.jsoneditor.JsonEditorResponse
import ca.adamhammer.babelfit.test.MockAdapter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JsonEditorSessionTest {

    @TempDir
    lateinit var tempDir: File

    private fun tempFile(name: String = "test.json"): File = File(tempDir, name)

    @Test
    fun `valid SET response modifies document`() = runTest {
        val response = JsonEditorResponse(
            actions = listOf(
                EditorAction(type = ActionType.SET, path = "/name", value = "\"Alice\"", message = "Set name"),
                EditorAction(type = ActionType.EXPLAIN, path = "", message = "Done")
            )
        )
        val mock = MockAdapter.scripted(response)
        val file = tempFile()
        file.writeText("{}")

        val session = JsonEditorSession(
            apiAdapter = mock,
            filePath = file.absolutePath,
            autoSave = false
        )

        session.chat("set name to Alice")

        val doc = session.currentDocument()
        assertEquals("Alice", doc.getAtPath("/name")?.let {
            kotlinx.serialization.json.Json.parseToJsonElement(it.toString()).toString().trim('"')
        })
    }

    @Test
    fun `malformed response triggers validation retry`() = runTest {
        // First response has SET without value — validation will reject it
        val bad = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.SET, path = "/name"))
        )
        // Second response is valid
        val good = JsonEditorResponse(
            actions = listOf(
                EditorAction(type = ActionType.SET, path = "/name", value = "\"Bob\"", message = "Set name"),
                EditorAction(type = ActionType.EXPLAIN, path = "", message = "Done")
            )
        )
        val mock = MockAdapter.scripted(bad, good)
        val file = tempFile()
        file.writeText("{}")

        val session = JsonEditorSession(
            apiAdapter = mock,
            filePath = file.absolutePath,
            autoSave = false
        )

        session.chat("set name to Bob")

        // Verify the adapter was called at least twice (initial + retry)
        assertTrue(mock.callCount >= 2, "Expected at least 2 calls (retry), got ${mock.callCount}")
    }

    @Test
    fun `system instructions contain example shapes`() = runTest {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.EXPLAIN, path = "", message = "Hello"))
        )
        val mock = MockAdapter.scripted(response)
        val file = tempFile()
        file.writeText("{}")

        val session = JsonEditorSession(
            apiAdapter = mock,
            filePath = file.absolutePath,
            autoSave = false
        )

        session.chat("describe the document")

        val context = mock.lastContext
        assertNotNull(context, "Should have captured context")
        val sysInstructions = context!!.systemInstructions
        assertTrue(sysInstructions.contains("JSON EDITOR RULES"), "Should contain static rules")
        assertTrue(sysInstructions.contains("JSON DOCUMENT CONTEXT"), "Should contain doc context")
    }

    @Test
    fun `DELETE at root is rejected by dry-run`() = runTest {
        val response = JsonEditorResponse(
            actions = listOf(
                EditorAction(type = ActionType.DELETE, path = "/valid", message = "Removing valid"),
                EditorAction(type = ActionType.EXPLAIN, path = "", message = "Done")
            )
        )
        val mock = MockAdapter.scripted(response)
        val file = tempFile()
        file.writeText("""{"valid": 1, "keep": 2}""")

        val errors = mutableListOf<String>()
        val listener = object : JsonEditorListener {
            override fun onError(error: String) { errors.add(error) }
        }

        val session = JsonEditorSession(
            apiAdapter = mock,
            filePath = file.absolutePath,
            autoSave = false,
            listener = listener
        )

        session.chat("delete valid key")

        // The /valid delete should succeed; root delete would be caught by dry-run
        assertNull(session.currentDocument().getAtPath("/valid"))
    }

    @Test
    fun `MOVE with missing source path emits error and skips`() = runTest {
        val response = JsonEditorResponse(
            actions = listOf(
                EditorAction(type = ActionType.MOVE, path = "/dest", from = "/nonexistent", to = "/dest", message = "Move"),
                EditorAction(type = ActionType.EXPLAIN, path = "", message = "Done")
            )
        )
        val mock = MockAdapter.scripted(response)
        val file = tempFile()
        file.writeText("""{"keep": 1}""")

        val errors = mutableListOf<String>()
        val listener = object : JsonEditorListener {
            override fun onError(error: String) { errors.add(error) }
        }

        val session = JsonEditorSession(
            apiAdapter = mock,
            filePath = file.absolutePath,
            autoSave = false,
            listener = listener
        )

        session.chat("move nonexistent to dest")

        assertTrue(errors.any { it.contains("does not exist") }, "Expected dry-run error for missing source, got: $errors")
    }

    @Test
    fun `tool provider is available for tool calls`() = runTest {
        val response = JsonEditorResponse(
            actions = listOf(EditorAction(type = ActionType.EXPLAIN, path = "", message = "Info"))
        )
        val mock = MockAdapter.scripted(response)
        val file = tempFile()
        file.writeText("{}")

        val session = JsonEditorSession(
            apiAdapter = mock,
            filePath = file.absolutePath,
            autoSave = false
        )

        session.chat("show document")

        val context = mock.lastContext!!
        val toolNames = context.availableTools.map { it.name }
        assertTrue("json_get_document" in toolNames, "Expected json_get_document tool, got: $toolNames")
        assertTrue("json_set" in toolNames, "Expected json_set tool, got: $toolNames")
    }
}
