package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

class JsonDocumentInterceptor(
    private val docProvider: () -> JsonDocument,
    private val filePathProvider: () -> String
) : Interceptor {

    override fun intercept(context: PromptContext): PromptContext {
        val doc = docProvider()
        val filePath = filePathProvider()
        val topLevel = when (val r = doc.root) {
            is JsonObject -> "Object with keys: ${r.keys.joinToString(", ")}"
            is JsonArray -> "Array with ${r.size} elements"
            else -> "Primitive value"
        }
        val docJson = doc.toJsonString()
        val truncated = if (docJson.length > 8000) {
            docJson.take(8000) + "\n... (truncated, ${docJson.length} chars total)"
        } else {
            docJson
        }

        val injection = """
            |
            |# JSON DOCUMENT CONTEXT
            |**File:** $filePath
            |**Structure:** $topLevel
            |**Nodes:** ${doc.nodeCount()} | **Depth:** ${doc.depth()}
            |
            |## Current Document
            |```json
            |$truncated
            |```
            |
            |## Path Format
            |Use JSON Pointer paths with '/' separators. Examples:
            |- Root: "" or "/"
            |- Object key: "/users"
            |- Nested: "/users/0/name"
            |- Array index: "/items/2"
        """.trimMargin()

        return context.copy(
            systemInstructions = context.systemInstructions + injection
        )
    }
}
