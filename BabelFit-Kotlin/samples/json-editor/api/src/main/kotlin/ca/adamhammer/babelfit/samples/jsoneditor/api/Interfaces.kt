package ca.adamhammer.babelfit.samples.jsoneditor.api

import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument

interface JsonEditorListener {
    fun onDocumentLoaded(doc: JsonDocument) {}
    fun onToolCall(toolName: String, args: String, result: String) {}
    fun onDocumentChanged(doc: JsonDocument, path: String, operation: String) {}
    fun onAgentResponse(response: String) {}
    fun onError(error: String) {}
}
