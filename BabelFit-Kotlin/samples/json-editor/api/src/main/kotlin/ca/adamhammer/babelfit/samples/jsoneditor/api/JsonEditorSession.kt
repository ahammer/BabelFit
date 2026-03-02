package ca.adamhammer.babelfit.samples.jsoneditor.api

import ca.adamhammer.babelfit.babelFit
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.model.ValidationResult
import ca.adamhammer.babelfit.samples.jsoneditor.ActionType
import ca.adamhammer.babelfit.samples.jsoneditor.ConversationHistoryInterceptor
import ca.adamhammer.babelfit.samples.jsoneditor.EditorAction
import ca.adamhammer.babelfit.samples.jsoneditor.EditorActionValidator
import ca.adamhammer.babelfit.samples.jsoneditor.JsonDocumentInterceptor
import ca.adamhammer.babelfit.samples.jsoneditor.JsonEditRequest
import ca.adamhammer.babelfit.samples.jsoneditor.JsonEditorAPI
import ca.adamhammer.babelfit.samples.jsoneditor.JsonEditorResponse
import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument
import kotlinx.serialization.json.Json
import java.io.File
import java.util.logging.Logger

class JsonEditorSession(
    private val apiAdapter: ApiAdapter,
    private val listener: JsonEditorListener = object : JsonEditorListener {},
    filePath: String = "test.json",
    private val autoSave: Boolean = true,
    private val requestListeners: List<RequestListener> = emptyList(),
    private val askHandler: suspend (String) -> String = { "" }
) {
    private val logger = Logger.getLogger(JsonEditorSession::class.java.name)
    private val file = File(filePath)
    private var document: JsonDocument = loadOrCreate()
    private val conversationHistory = ConversationHistoryInterceptor()
    private val documentInterceptor = JsonDocumentInterceptor(
        docProvider = { document },
        filePathProvider = { file.absolutePath }
    )
    private val json = Json { prettyPrint = false }

    private val toolProvider = JsonToolProvider(
        docProvider = { document },
        docUpdater = { newDoc ->
            document = newDoc
            listener.onDocumentChanged(newDoc, "", "tool")
            if (autoSave) save()
        },
        listener = listener,
        askHandler = askHandler
    )

    private val editorApi: JsonEditorAPI = babelFit<JsonEditorAPI> {
        adapter(apiAdapter)
        addInterceptor(documentInterceptor)
        addInterceptor(conversationHistory)
        toolProvider(this@JsonEditorSession.toolProvider)
        requestListeners.forEach { listener(it) }
        resilience {
            maxRetries = 2
            retryDelayMs = 500
            backoffMultiplier = 2.0
            resultValidator = { result ->
                if (result is JsonEditorResponse) {
                    EditorActionValidator.validate(result)
                } else {
                    ValidationResult.Valid
                }
            }
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
            val request = JsonEditRequest(message = userMessage)
            documentInterceptor.currentFocusPath = request.focusPath
            val response = editorApi.respond(request).get()
            conversationHistory.addAssistantResponse(
                json.encodeToString(JsonEditorResponse.serializer(), response)
            )
            executeActions(response.actions)
            ""
        } catch (e: Exception) {
            val fallbackMessage = "I was unable to process your request: ${e.message}. " +
                "Could you rephrase or provide explicit JSON Pointer paths?"
            listener.onError(fallbackMessage)
            val fallback = JsonEditorResponse(
                actions = listOf(
                    EditorAction(
                        type = ActionType.ASK,
                        path = "",
                        message = fallbackMessage
                    )
                )
            )
            conversationHistory.addAssistantResponse(
                json.encodeToString(JsonEditorResponse.serializer(), fallback)
            )
            executeActions(fallback.actions)
            fallbackMessage
        }
    }

    private suspend fun executeActions(actions: List<EditorAction>) {
        for (action in actions) {
            try {
                // Log message for auditing
                if (!action.message.isNullOrBlank()) {
                    logger.info("[${action.type}] ${action.message}")
                }

                // Pre-apply dry-run checks
                val dryRunError = dryRunCheck(action)
                if (dryRunError != null) {
                    listener.onError("Skipping ${action.type}: $dryRunError")
                    continue
                }

                when (action.type) {
                    ActionType.SET -> executeSet(action)
                    ActionType.DELETE -> executeDelete(action)
                    ActionType.MOVE -> executeMove(action)
                    ActionType.EXPLAIN -> listener.onExplain(action.message ?: "")
                    ActionType.ASK -> executeAsk(action)
                }
            } catch (e: Exception) {
                listener.onError("${action.type} failed: ${e.message}")
            }
        }
    }

    private fun dryRunCheck(action: EditorAction): String? = when (action.type) {
        ActionType.SET -> {
            val v = action.value
            if (v.isNullOrBlank()) {
                "SET requires a value"
            } else {
                try {
                    Json.parseToJsonElement(v)
                    null
                } catch (e: Exception) {
                    "SET value is not valid JSON: ${e.message}"
                }
            }
        }
        ActionType.DELETE -> {
            if (action.path.isBlank() || action.path == "/") {
                "Cannot delete root node"
            } else null
        }
        ActionType.MOVE -> {
            val from = action.from
            val to = action.to
            when {
                from.isNullOrBlank() -> "MOVE requires a 'from' path"
                to.isNullOrBlank() -> "MOVE requires a 'to' path"
                document.getAtPath(from) == null -> "MOVE source path '$from' does not exist"
                else -> null
            }
        }
        ActionType.EXPLAIN, ActionType.ASK -> null
    }

    private fun executeSet(action: EditorAction) {
        val value = Json.parseToJsonElement(action.value!!)
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
        val from = action.from!!
        val to = action.to!!
        document = document.moveNode(from, to)
        listener.onToolCall("MOVE", "from=$from to=$to", "Moved $from \u2192 $to")
        listener.onDocumentChanged(document, to, "move")
        if (autoSave) save()
    }

    private suspend fun executeAsk(action: EditorAction) {
        listener.onAskStarted(action.message ?: "")
        val answer = askHandler(action.message ?: "")
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
