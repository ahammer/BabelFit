package ca.adamhammer.babelfit.samples.jsoneditor.api

import ca.adamhammer.babelfit.babelFit
import ca.adamhammer.babelfit.agents.DecidingAgentAPI
import ca.adamhammer.babelfit.agents.graph.AgentGraph
import ca.adamhammer.babelfit.agents.graph.GraphAgent
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.samples.jsoneditor.AgentStateInterceptor
import ca.adamhammer.babelfit.samples.jsoneditor.ConversationHistoryInterceptor
import ca.adamhammer.babelfit.samples.jsoneditor.JsonDocumentInterceptor
import ca.adamhammer.babelfit.samples.jsoneditor.JsonEditorAgentAPI
import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument
import ca.adamhammer.babelfit.samples.jsoneditor.model.UserResponse
import java.io.File

class AgentJsonEditorSession(
    private val apiAdapter: ApiAdapter,
    private val listener: JsonEditorListener = object : JsonEditorListener {},
    filePath: String = "test.json",
    private val autoSave: Boolean = true,
    private val requestListeners: List<RequestListener> = emptyList(),
    private val askHandler: suspend (String) -> String = { "" },
    private val maxSteps: Int = 8
) {
    private val file = File(filePath)
    private var document: JsonDocument = loadOrCreate()
    private val conversationHistory = ConversationHistoryInterceptor()
    private val agentState = AgentStateInterceptor(maxSteps)

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

    private val agentInstance = babelFit<JsonEditorAgentAPI> {
        adapter(apiAdapter)
        toolProvider(toolProvider)
        addInterceptor(JsonDocumentInterceptor(
            docProvider = { document },
            filePathProvider = { file.absolutePath }
        ))
        addInterceptor(conversationHistory)
        addInterceptor(agentState)
        requestListeners.forEach { listener(it) }
        resilience {
            maxRetries = 2
            retryDelayMs = 500
        }
    }

    private val decider = babelFit<DecidingAgentAPI> {
        adapter(apiAdapter)
        requestListeners.forEach { listener(it) }
    }.api

    private val graph = AgentGraph.fromAnnotations(JsonEditorAgentAPI::class)

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

            val agent = GraphAgent(agentInstance, decider, graph)
            val result = agent.runSuspend(maxSteps)

            val response = try {
                kotlinx.serialization.json.Json.decodeFromString(
                    UserResponse.serializer(),
                    result.value.toString()
                )
            } catch (_: Exception) {
                UserResponse(message = result.value.toString())
            }

            conversationHistory.addAssistantResponse(response.message)
            listener.onExplain(response.message)
            response.message
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

    fun replaceDocument(doc: JsonDocument) {
        document = doc
    }

    fun filePath(): String = file.absolutePath
}
