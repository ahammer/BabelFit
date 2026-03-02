package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class JsonDocumentInterceptor(
    private val docProvider: () -> JsonDocument,
    private val filePathProvider: () -> String
) : Interceptor {

    /** Set before each API call to focus on a subtree instead of the full document. */
    var currentFocusPath: String? = null

    private var staticRulesInjected = false

    private val prettyJson = Json { prettyPrint = true }

    override fun intercept(context: PromptContext): PromptContext {
        val doc = docProvider()
        val filePath = filePathProvider()
        val topLevel = when (val r = doc.root) {
            is JsonObject -> "Object with keys: ${r.keys.joinToString(", ")}"
            is JsonArray -> "Array with ${r.size} elements"
            else -> "Primitive value"
        }

        val parts = mutableListOf<String>()

        var ctx = context
        if (!staticRulesInjected) {
            ctx = ctx.withPart("json-editor-rules", ca.adamhammer.babelfit.model.PromptPart.RULES, STATIC_RULES)
            staticRulesInjected = true
        }

        return ctx.withPart("json-document", ca.adamhammer.babelfit.model.PromptPart.DOCUMENT, parts.joinToString("\n\n"))
    }

    private fun truncateDoc(docJson: String): String =
        if (docJson.length > 8000) {
            docJson.take(8000) + "\n... (truncated, ${docJson.length} chars total)"
        } else {
            docJson
        }

    companion object {
        private val STATIC_RULES = """
            |# JSON EDITOR RULES
            |
            |## Action Types
            |- **SET**: Set/replace a JSON value. Required: path, value (JSON-encoded string).
            |- **DELETE**: Remove a node. Required: path. Cannot delete root.
            |- **MOVE**: Move a node. Required: path, from, to.
            |- **EXPLAIN**: Message to the user. Required: path (can be ""), message.
            |- **ASK**: Ask a clarifying question. Required: path (can be ""), message.
            |
            |## Rules
            |- Every response MUST include at least one EXPLAIN or ASK action.
            |- Batch ALL edits into a single response.
            |- SET values must be valid JSON-encoded strings (e.g. '"hello"', '42', '["a","b"]').
            |- Omit unused optional fields entirely.
            |
            |## Path Format (JSON Pointer)
            |Use '/' separators. Root: "" or "/". Examples: "/users", "/users/0/name", "/items/2"
        """.trimMargin()
    }
}
