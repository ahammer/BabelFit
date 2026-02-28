package ca.adamhammer.babelfit.samples.jsoneditor.api

import ca.adamhammer.babelfit.babelFit
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.samples.jsoneditor.ActionType
import ca.adamhammer.babelfit.samples.jsoneditor.ConversationHistoryInterceptor
import ca.adamhammer.babelfit.samples.jsoneditor.EditorAction
import ca.adamhammer.babelfit.samples.jsoneditor.JsonDocumentInterceptor
import ca.adamhammer.babelfit.samples.jsoneditor.JsonEditorAPI
import ca.adamhammer.babelfit.samples.jsoneditor.JsonEditorResponse
import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

class JsonEditorSession(
    private val apiAdapter: ApiAdapter,
    private val listener: JsonEditorListener = object : JsonEditorListener {},
    filePath: String = "test.json",
    private val autoSave: Boolean = true,
    private val requestListeners: List<RequestListener> = emptyList(),
    private val askHandler: suspend (String) -> String = { "" }
) {
    private val file = File(filePath)
    private var document: JsonDocument = loadOrCreate()
    private val conversationHistory = ConversationHistoryInterceptor()
    private val json = Json { prettyPrint = false }

    private val editorApi: JsonEditorAPI = babelFit<JsonEditorAPI> {
        adapter(apiAdapter)
        addInterceptor(JsonDocumentInterceptor(
            docProvider = { document },
            filePathProvider = { file.absolutePath }
        ))
        addInterceptor(conversationHistory)
        requestListeners.forEach { listener(it) }
        resilience {
            maxRetries = 1
            retryDelayMs = 500
        }
    }.api

    private fun loadOrCreate(): JsonDocument {
        val doc = if (file.exists() && file.length() > 0) {
            JsonDocument.fromFile(file)
        } else {
            JsonDocument.empty()
        }
        listener.onDocumentLoaded(doc)
        return doc
    }

    suspend fun chat(userMessage: String): String {
        return try {
            conversationHistory.addUserMessage(userMessage)
            val response = editorApi.respond(userMessage).get()
            conversationHistory.addAssistantResponse(
                json.encodeToString(JsonEditorResponse.serializer(), response)
            )
            executeActions(response.actions)
            ""
        } catch (e: Exception) {
            val error = "Error: ${e.message}"
            listener.onError(error)
            error
        }
    }

    private suspend fun executeActions(actions: List<EditorAction>) {
        for (action in actions) {
            try {
                when (action.type) {
                    ActionType.SET -> executeSet(action)
                    ActionType.DELETE -> executeDelete(action)
                    ActionType.MOVE -> executeMove(action)
                    ActionType.EXPLAIN -> listener.onExplain(action.message)
                    ActionType.ASK -> executeAsk(action)
                }
            } catch (e: Exception) {
                listener.onError("${action.type} failed: ${e.message}")
            }
        }
    }

    private fun executeSet(action: EditorAction) {
        val value = Json.parseToJsonElement(action.value)
        document = document.setAtPath(action.path, value)
        listener.onToolCall("SET", "path=${action.path}", "Set ${action.path} successfully")
        listener.onDocumentChanged(document, action.path, "set")
        if (autoSave) save()
    }

    private fun executeDelete(action: EditorAction) {
        document = document.deleteAtPath(action.path)
        listener.onToolCall("DELETE", "path=${action.path}", "Deleted ${action.path} successfully")
        listener.onDocumentChanged(document, action.path, "delete")
        if (autoSave) save()
    }

    private fun executeMove(action: EditorAction) {
        document = document.moveNode(action.from, action.to)
        listener.onToolCall("MOVE", "from=${action.from} to=${action.to}", "Moved ${action.from} \u2192 ${action.to}")
        listener.onDocumentChanged(document, action.to, "move")
        if (autoSave) save()
    }

    private suspend fun executeAsk(action: EditorAction) {
        listener.onAskStarted(action.message)
        val answer = askHandler(action.message)
        chat(answer)
    }

    fun save() {
        document.saveTo(file)
    }

    fun currentDocument(): JsonDocument = document

    fun replaceDocument(doc: JsonDocument) {
        document = doc
    }

    fun filePath(): String = file.absolutePath
}
