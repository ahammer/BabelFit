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
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.samples.common.Vendor
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

    var inputText by mutableStateOf("")

    private var savedDocument: JsonDocument = JsonDocument.empty()
    private val history = DocumentHistory<JsonDocument>()

    val chatEntries = mutableStateListOf<ChatEntry>()

    val usageTracker = UsageTracker()

    private var session: JsonEditorSession? = null

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

    fun toggleViewMode() {
        viewMode = if (viewMode == ContentViewMode.TREE) ContentViewMode.RAW else ContentViewMode.TREE
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
    }

    fun redo() {
        val doc = history.redo() ?: return
        document = doc
        isDirty = document != savedDocument
        session?.replaceDocument(doc)
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

        val s = session ?: run {
            rebuildSession()
            session
        } ?: return

        isBusy = true
        uiScope.launch(Dispatchers.IO) {
            s.chat(text)
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
        session = JsonEditorSession(
            apiAdapter = createAdapter(vendor, model),
            listener = this,
            filePath = path,
            autoSave = filePath != null,
            requestListeners = listOf(usageTracker),
            askHandler = askHandler
        )
        // Restore the in-memory document — the session constructor may have
        // loaded a stale copy from disk or created a fresh empty doc.
        document = currentDoc
        session?.replaceDocument(currentDoc)
    }

    private fun createAdapter(vendor: Vendor, model: String): ApiAdapter = when (vendor) {
        Vendor.OPENAI -> OpenAiAdapter(model = ChatModel.of(model))
        Vendor.ANTHROPIC -> ClaudeAdapter(model = model)
        Vendor.GEMINI -> GeminiAdapter(model = model)
    }
}
