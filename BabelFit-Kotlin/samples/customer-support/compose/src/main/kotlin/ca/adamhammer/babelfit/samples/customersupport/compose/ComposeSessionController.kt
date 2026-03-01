package ca.adamhammer.babelfit.samples.customersupport.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.adamhammer.babelfit.adapters.OpenAiAdapter
import ca.adamhammer.babelfit.adapters.ClaudeAdapter
import ca.adamhammer.babelfit.adapters.GeminiAdapter
import ca.adamhammer.babelfit.debug.trace.TraceSession
import ca.adamhammer.babelfit.debug.trace.TracingAdapter
import ca.adamhammer.babelfit.debug.trace.TracingRequestListener
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.samples.common.Vendor
import com.openai.models.ChatModel
import ca.adamhammer.babelfit.samples.customersupport.api.SupportEventListener
import ca.adamhammer.babelfit.samples.customersupport.api.SupportSession
import ca.adamhammer.babelfit.samples.customersupport.models.AgentType
import ca.adamhammer.babelfit.samples.customersupport.models.CompanyTemplate
import ca.adamhammer.babelfit.samples.customersupport.models.CustomerContext
import ca.adamhammer.babelfit.samples.customersupport.models.SupportResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class ChatEntry {
    data class UserMessage(val text: String) : ChatEntry()
    data class AgentMessage(val agentType: AgentType, val response: SupportResponse) : ChatEntry()
    data class TransferNotice(val from: AgentType, val to: AgentType, val summary: String) : ChatEntry()
    data class ToolCall(val agentType: AgentType, val toolName: String, val result: String) : ChatEntry()
    data class EscalationNotice(val ticket: String) : ChatEntry()
    data class ErrorMessage(val error: String) : ChatEntry()
}

class ComposeSessionController(
    private val scope: CoroutineScope
) : SupportEventListener {

    val chatEntries = mutableStateListOf<ChatEntry>()
    var currentAgent by mutableStateOf(AgentType.ROUTING)
        private set
    var isProcessing by mutableStateOf(false)
        private set
    var vendor by mutableStateOf(Vendor.OPENAI)
    var model by mutableStateOf(Vendor.OPENAI.defaultModel)

    val customerContext = CustomerContext(
        name = "Jane Doe",
        accountId = "ACCT-78432",
        productModel = "Medium",
        serialNumber = "WGT-M-20250815-4721",
        purchaseDate = "2025-08-15"
    )

    private var session: SupportSession? = null

    val traceSession = TraceSession()

    fun startSession() {
        val adapter = TracingAdapter(createAdapter(), traceSession)
        val template = CompanyTemplate.loadFromResource()
        session = SupportSession(
            apiAdapter = adapter,
            listener = this,
            companyTemplate = template,
            customerContext = customerContext,
            requestListeners = listOf(TracingRequestListener(traceSession))
        )
        session?.startSession()
    }

    fun exportTrace() {
        val chooser = javax.swing.JFileChooser().apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "BabelFit Trace files", "btrace.json"
            )
            dialogTitle = "Export Trace"
            selectedFile = java.io.File("trace.btrace.json")
        }
        if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            traceSession.save(chooser.selectedFile)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isProcessing) return
        chatEntries.add(ChatEntry.UserMessage(text))
        isProcessing = true
        scope.launch {
            try {
                session?.chat(text)
            } catch (e: Exception) {
                chatEntries.add(ChatEntry.ErrorMessage(e.message ?: "Unknown error"))
            } finally {
                isProcessing = false
            }
        }
    }

    private fun createAdapter(): ApiAdapter {
        return when (vendor) {
            Vendor.OPENAI -> OpenAiAdapter(model = ChatModel.of(model))
            Vendor.ANTHROPIC -> ClaudeAdapter(model = model)
            Vendor.GEMINI -> GeminiAdapter(model = model)
        }
    }

    // SupportEventListener callbacks

    override fun onSessionStarted(customerContext: CustomerContext) {
        currentAgent = AgentType.ROUTING
    }

    override fun onAgentTransfer(from: AgentType, to: AgentType, summary: String) {
        currentAgent = to
        chatEntries.add(ChatEntry.TransferNotice(from, to, summary))
    }

    override fun onAgentResponse(agentType: AgentType, response: SupportResponse) {
        chatEntries.add(ChatEntry.AgentMessage(agentType, response))
    }

    override fun onToolInvocation(agentType: AgentType, toolName: String, result: String) {
        chatEntries.add(ChatEntry.ToolCall(agentType, toolName, result))
    }

    override fun onEscalation(ticket: String) {
        chatEntries.add(ChatEntry.EscalationNotice(ticket))
    }

    override fun onError(error: String) {
        chatEntries.add(ChatEntry.ErrorMessage(error))
    }
}
