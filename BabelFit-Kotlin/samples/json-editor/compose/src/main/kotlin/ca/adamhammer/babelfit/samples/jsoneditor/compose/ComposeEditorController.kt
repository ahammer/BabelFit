@file:Suppress("TooManyFunctions")
package ca.adamhammer.babelfit.samples.jsoneditor.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.adamhammer.babelfit.UsageTracker
import ca.adamhammer.babelfit.adapters.ClaudeAdapter
import ca.adamhammer.babelfit.adapters.GeminiAdapter
import ca.adamhammer.babelfit.adapters.OpenAiAdapter
import ca.adamhammer.babelfit.debug.trace.TraceSession
import ca.adamhammer.babelfit.debug.trace.TracingAdapter
import ca.adamhammer.babelfit.debug.trace.TracingRequestListener
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.samples.common.Vendor
import ca.adamhammer.babelfit.samples.jsoneditor.api.AgentJsonEditorSession
import ca.adamhammer.babelfit.samples.jsoneditor.api.JsonEditorListener
import ca.adamhammer.babelfit.samples.jsoneditor.api.JsonEditorSession
import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument
import com.openai.models.ChatModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

class ComposeEditorController(private val uiScope: CoroutineScope) : JsonEditorListener {

    // ── Observable state ────────────────────────────────────────────────

    var vendor by mutableStateOf(
        Vendor.entries.firstOrNull { it.isAvailable() } ?: Vendor.OPENAI
    )
        private set

    var model by mutableStateOf(vendor.defaultModel)
        private set

    var document by mutableStateOf(JsonDocument.empty())
        private set

    var filePath by mutableStateOf<String?>(null)
        private set

    var viewMode by mutableStateOf(ContentViewMode.TREE)
        private set

    var isBusy by mutableStateOf(false)
        private set

    var isDirty by mutableStateOf(false)
        private set

    var agentMode by mutableStateOf(false)
        private set

    var inputText by mutableStateOf("")

    private var savedDocument: JsonDocument = JsonDocument.empty()
    private val history = DocumentHistory<JsonDocument>()

    val chatEntries = mutableStateListOf<ChatEntry>()

    val usageTracker = UsageTracker()

    val traceSession = TraceSession()

    private var session: JsonEditorSession? = null
    private var agentSession: AgentJsonEditorSession? = null

    private var pendingAsk: CompletableDeferred<String>? = null

    val isAskPending: Boolean get() = pendingAsk != null

    private val askHandler: suspend (String) -> String = { question ->
        chatEntries.add(ChatEntry.AskQuestion(question))
        val deferred = CompletableDeferred<String>()
        pendingAsk = deferred
        isBusy = false
        val answer = deferred.await()
        isBusy = true
        pendingAsk = null
        answer
    }

    // ── Vendor / model switching ────────────────────────────────────────

    fun selectVendor(v: Vendor) {
        vendor = v
        model = v.defaultModel
        rebuildSession()
    }

    fun selectModel(m: String) {
        model = m
        rebuildSession()
    }

    fun toggleAgentMode() {
        agentMode = !agentMode
        rebuildSession()
    }

    fun toggleViewMode() {
        viewMode = if (viewMode == ContentViewMode.TREE) ContentViewMode.RAW else ContentViewMode.TREE
    }

    fun exportTrace() {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("BabelFit Trace files", "btrace.json")
            dialogTitle = "Export Trace"
            selectedFile = java.io.File("trace.btrace.json")
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            traceSession.save(chooser.selectedFile)
        }
    }

    // ── File operations ─────────────────────────────────────────────────

    fun openFile() {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("JSON files", "json")
            dialogTitle = "Open JSON file"
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            loadFile(chooser.selectedFile)
        }
    }

    fun newFile() {
        filePath = null
        document = JsonDocument.empty()
        savedDocument = document
        isDirty = false
        history.reset(document)
        chatEntries.clear()
        chatEntries.add(ChatEntry.SystemMessage("New empty document created."))
        rebuildSession()
    }

    fun save() {
        val path = filePath
        if (path != null) {
            document.saveTo(File(path))
            savedDocument = document
            isDirty = false
            chatEntries.add(ChatEntry.SystemMessage("Saved."))
        } else {
            saveAs()
        }
    }

    fun saveAs() {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("JSON files", "json")
            dialogTitle = "Save JSON file as"
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val target = chooser.selectedFile
            document.saveTo(target)
            filePath = target.absolutePath
            savedDocument = document
            isDirty = false
            chatEntries.add(ChatEntry.SystemMessage("Saved to ${target.name}"))
            rebuildSession()
        }
    }

    fun undo() {
        val doc = history.undo() ?: return
        document = doc
        isDirty = document != savedDocument
        session?.replaceDocument(doc)
        agentSession?.replaceDocument(doc)
    }

    fun redo() {
        val doc = history.redo() ?: return
        document = doc
        isDirty = document != savedDocument
        session?.replaceDocument(doc)
        agentSession?.replaceDocument(doc)
    }

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    private fun loadFile(file: File) {
        filePath = file.absolutePath
        document = JsonDocument.fromFile(file)
        savedDocument = document
        isDirty = false
        history.reset(document)
        chatEntries.clear()
        chatEntries.add(ChatEntry.DocumentLoaded(file.name))
        rebuildSession()
    }

    // ── Chat ────────────────────────────────────────────────────────────

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isEmpty()) return
        inputText = ""

        // If the agent asked a question, route the answer back
        val ask = pendingAsk
        if (ask != null) {
            chatEntries.add(ChatEntry.UserMessage(text))
            ask.complete(text)
            return
        }

        if (isBusy) return
        chatEntries.add(ChatEntry.UserMessage(text))

        if (session == null && agentSession == null) rebuildSession()

        isBusy = true
        uiScope.launch(Dispatchers.IO) {
            if (agentMode) {
                agentSession?.chat(text)
            } else {
                session?.chat(text)
            }
            isBusy = false
        }
    }

    // ── Listener callbacks (called from session on IO thread) ───────────

    override fun onDocumentLoaded(doc: JsonDocument) {
        document = doc
    }

    override fun onDocumentChanged(doc: JsonDocument, path: String, operation: String) {
        document = doc
        history.push(doc)
        isDirty = document != savedDocument
    }

    override fun onToolCall(toolName: String, args: String, result: String) {
        chatEntries.add(ChatEntry.ToolCall(toolName, args, result))
    }

    override fun onAgentResponse(response: String) {
        // Structured responses handled via onExplain; no freeform text expected
    }

    override fun onExplain(message: String) {
        chatEntries.add(ChatEntry.Explain(message))
    }

    override fun onAskStarted(question: String) {
        // Handled by askHandler lambda — the ChatEntry is added there
    }

    override fun onError(error: String) {
        chatEntries.add(ChatEntry.ErrorMessage(error))
    }

    // ── Internals ───────────────────────────────────────────────────────

    private fun rebuildSession() {
        val path = filePath ?: "untitled.json"
        val currentDoc = document
        val adapter = TracingAdapter(createAdapter(vendor, model), traceSession)
        val listeners = listOf(usageTracker, TracingRequestListener(traceSession))

        if (agentMode) {
            session = null
            agentSession = AgentJsonEditorSession(
                apiAdapter = adapter,
                listener = this,
                filePath = path,
                autoSave = filePath != null,
                requestListeners = listeners,
                askHandler = askHandler
            )
            agentSession?.replaceDocument(currentDoc)
        } else {
            agentSession = null
            session = JsonEditorSession(
                apiAdapter = adapter,
                listener = this,
                filePath = path,
                autoSave = filePath != null,
                requestListeners = listeners,
                askHandler = askHandler
            )
            session?.replaceDocument(currentDoc)
        }
        document = currentDoc
    }

    private fun createAdapter(vendor: Vendor, model: String): ApiAdapter = when (vendor) {
        Vendor.OPENAI -> OpenAiAdapter(model = ChatModel.of(model))
        Vendor.ANTHROPIC -> ClaudeAdapter(model = model)
        Vendor.GEMINI -> GeminiAdapter(model = model)
    }
}
