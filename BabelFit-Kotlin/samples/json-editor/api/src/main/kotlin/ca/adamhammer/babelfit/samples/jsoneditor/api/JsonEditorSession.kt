package ca.adamhammer.babelfit.samples.jsoneditor.api

import ca.adamhammer.babelfit.babelFit
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.samples.jsoneditor.ConversationHistoryInterceptor
import ca.adamhammer.babelfit.samples.jsoneditor.JsonDocumentInterceptor
import ca.adamhammer.babelfit.samples.jsoneditor.JsonEditorAPI
import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument
import java.io.File

class JsonEditorSession(
    private val apiAdapter: ApiAdapter,
    private val listener: JsonEditorListener = object : JsonEditorListener {},
    filePath: String = "test.json",
    private val autoSave: Boolean = true,
    private val requestListeners: List<RequestListener> = emptyList()
) {
    private val file = File(filePath)
    private var document: JsonDocument = loadOrCreate()
    private val conversationHistory = ConversationHistoryInterceptor()

    private val toolProvider = JsonToolProvider(
        docProvider = { document },
        docUpdater = { newDoc ->
            document = newDoc
            listener.onDocumentChanged(newDoc, "", "tool-update")
            if (autoSave) save()
        },
        listener = listener
    )

    private val editorApi: JsonEditorAPI = babelFit<JsonEditorAPI> {
        adapter(apiAdapter)
        addInterceptor(JsonDocumentInterceptor(
            docProvider = { document },
            filePathProvider = { file.absolutePath }
        ))
        addInterceptor(conversationHistory)
        toolProvider(toolProvider)
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
            conversationHistory.addAssistantResponse(response)
            listener.onAgentResponse(response)
            response
        } catch (e: Exception) {
            val error = "Error: ${e.message}"
            listener.onError(error)
            error
        }
    }

    fun save() {
        document.saveTo(file)
    }

    fun currentDocument(): JsonDocument = document

    fun filePath(): String = file.absolutePath
}
