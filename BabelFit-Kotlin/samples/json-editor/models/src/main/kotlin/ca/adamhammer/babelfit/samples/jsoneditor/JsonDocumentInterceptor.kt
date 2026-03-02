package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.model.PromptPart
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

    private val prettyJson = Json { prettyPrint = true }

    override fun intercept(context: PromptContext): PromptContext {
        val doc = docProvider()
        val filePath = filePathProvider()
        val topLevel = when (val r = doc.root) {
            is JsonObject -> "Object with keys: ${r.keys.joinToString(", ")}"
            is JsonArray -> "Array with ${r.size} elements"
            else -> "Primitive value"
        }

        // Document context — focused subtree or full document
        val focusPath = currentFocusPath
        val docContent = if (!focusPath.isNullOrBlank()) {
            val subtree = doc.getAtPath(focusPath)
            if (subtree != null) {
                val pretty = prettyJson.encodeToString(JsonElement.serializer(), subtree)
                "**Focus:** `$focusPath`\n```json\n$pretty\n```"
            } else {
                val full = truncateDoc(doc.toJsonString())
                "**Note:** focusPath '$focusPath' not found, showing full document.\n```json\n$full\n```"
            }
        } else {
            val full = truncateDoc(doc.toJsonString())
            "```json\n$full\n```"
        }

        val documentSection = """
            |# JSON DOCUMENT CONTEXT
            |**File:** $filePath
            |**Structure:** $topLevel
            |**Nodes:** ${doc.nodeCount()} | **Depth:** ${doc.depth()}
            |
            |## Current Document
            |$docContent
        """.trimMargin()

        // Warn when a requested source path does not exist in the document
        val invocation = context.methodInvocation
        val sourcePathMatch = """"sourcePath"\s*:\s*"([^"]+)"""".toRegex().find(invocation)
        val warning = if (sourcePathMatch != null) {
            val sourcePath = sourcePathMatch.groupValues[1]
            if (doc.getAtPath(sourcePath) == null) {
                "\n\nNOTE: The requested source path '$sourcePath' does not exist in the current document. " +
                    "Emit an ASK action to clarify instead of a MOVE."
            } else {
                ""
            }
        } else {
            ""
        }

        return context
            .withPart("json-editor-rules", PromptPart.RULES, STATIC_RULES)
            .withPart("json-document", PromptPart.DOCUMENT, documentSection + warning)
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
