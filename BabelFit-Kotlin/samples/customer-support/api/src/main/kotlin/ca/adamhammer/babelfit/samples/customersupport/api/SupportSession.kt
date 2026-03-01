package ca.adamhammer.babelfit.samples.customersupport.api

import ca.adamhammer.babelfit.babelFit
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.samples.customersupport.models.AgentType
import ca.adamhammer.babelfit.samples.customersupport.models.CompanyContextInterceptor
import ca.adamhammer.babelfit.samples.customersupport.models.CompanyTemplate
import ca.adamhammer.babelfit.samples.customersupport.models.ConversationHistoryInterceptor
import ca.adamhammer.babelfit.samples.customersupport.models.CustomerContext
import ca.adamhammer.babelfit.samples.customersupport.models.HandoffContextInterceptor
import ca.adamhammer.babelfit.samples.customersupport.models.RoutingAgentAPI
import ca.adamhammer.babelfit.samples.customersupport.models.SupportAgentAPI
import ca.adamhammer.babelfit.samples.customersupport.models.TransferDecision
import kotlinx.serialization.json.Json

class SupportSession(
    private val apiAdapter: ApiAdapter,
    private val listener: SupportEventListener = object : SupportEventListener {},
    private val companyTemplate: CompanyTemplate,
    private val customerContext: CustomerContext,
    private val requestListeners: List<RequestListener> = emptyList()
) {
    private var currentAgent: AgentType = AgentType.ROUTING
    private var handoffSummary: String? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val routingApi: RoutingAgentAPI = createRoutingAgent()

    private val agentApis = mutableMapOf<AgentType, SupportAgentAPI>()
    private val agentConversationHistories = mutableMapOf<AgentType, ConversationHistoryInterceptor>()

    fun currentAgentType(): AgentType = currentAgent

    fun startSession() {
        listener.onSessionStarted(customerContext)
    }

    suspend fun chat(userMessage: String): String {
        return try {
            if (currentAgent == AgentType.ROUTING) {
                handleRouting(userMessage)
            } else {
                handleSpecialistChat(userMessage)
            }
        } catch (e: Exception) {
            val error = "Error: ${e.message}"
            listener.onError(error)
            error
        }
    }

    fun endSession() {
        listener.onSessionEnded()
    }

    private suspend fun handleRouting(userMessage: String): String {
        val decision = routingApi.classifyAndRoute(userMessage).get()
        val targetAgent = parseAgentType(decision.targetAgent)
        transferTo(targetAgent, decision.contextSummary)
        return handleSpecialistChat(userMessage)
    }

    private suspend fun handleSpecialistChat(userMessage: String): String {
        val api = getOrCreateAgent(currentAgent)
        val history = agentConversationHistories.getOrPut(currentAgent) { ConversationHistoryInterceptor() }

        history.addUserMessage(userMessage)
        val response = api.respond(userMessage).get()

        val responseJson = json.encodeToString(
            ca.adamhammer.babelfit.samples.customersupport.models.SupportResponse.serializer(),
            response
        )
        history.addAssistantResponse(responseJson)
        listener.onAgentResponse(currentAgent, response)

        return response.message
    }

    internal suspend fun transferTo(targetAgent: AgentType, summary: String) {
        val fromAgent = currentAgent

        // Summarize conversation from current agent if it's a specialist (not routing)
        if (fromAgent != AgentType.ROUTING && agentApis.containsKey(fromAgent)) {
            val currentApi = agentApis[fromAgent]!!
            val conversationSummary = currentApi.summarizeConversation().get()
            handoffSummary = "$summary\n\nPrevious agent summary: $conversationSummary"
        } else {
            handoffSummary = summary
        }

        currentAgent = targetAgent
        listener.onAgentTransfer(fromAgent, targetAgent, handoffSummary!!)
    }

    // Called by SupportSession when a tool-based transfer is detected
    internal fun detectTransferSignal(toolResult: String): TransferDecision? {
        if (!toolResult.startsWith("TRANSFER:")) return null
        val parts = toolResult.removePrefix("TRANSFER:").split(":", limit = 2)
        val targetAgentStr = parts[0]
        val reason = parts.getOrElse(1) { "" }
        return TransferDecision(
            targetAgent = targetAgentStr,
            contextSummary = reason,
            reason = reason
        )
    }

    private fun createRoutingAgent(): RoutingAgentAPI {
        return babelFit<RoutingAgentAPI> {
            adapter(apiAdapter)
            addInterceptor(CompanyContextInterceptor(AgentType.ROUTING, companyTemplate))
            addInterceptor(HandoffContextInterceptor(
                summaryProvider = { null },
                customerContextProvider = { customerContext }
            ))
            requestListeners.forEach { listener(it) }
            resilience {
                maxRetries = 1
                retryDelayMs = 500
            }
        }.api
    }

    private fun getOrCreateAgent(agentType: AgentType): SupportAgentAPI {
        return agentApis.getOrPut(agentType) {
            val history = agentConversationHistories.getOrPut(agentType) { ConversationHistoryInterceptor() }
            val toolProvider = createToolProvider(agentType)

            babelFit<SupportAgentAPI> {
                adapter(apiAdapter)
                addInterceptor(CompanyContextInterceptor(agentType, companyTemplate))
                addInterceptor(HandoffContextInterceptor(
                    summaryProvider = { handoffSummary },
                    customerContextProvider = { customerContext }
                ))
                addInterceptor(history)
                toolProvider(toolProvider)
                requestListeners.forEach { listener(it) }
                resilience {
                    maxRetries = 1
                    retryDelayMs = 500
                }
            }.api
        }
    }

    private fun createToolProvider(agentType: AgentType): ToolProvider {
        return when (agentType) {
            AgentType.TECHNICAL -> TechnicalToolProvider(companyTemplate, listener)
            AgentType.BILLING -> BillingToolProvider(companyTemplate, listener)
            AgentType.GENERAL -> GeneralToolProvider(companyTemplate, listener)
            AgentType.ESCALATION -> EscalationToolProvider(listener)
            AgentType.ROUTING -> GeneralToolProvider(companyTemplate, listener) // fallback, shouldn't be used
        }
    }

    private fun parseAgentType(value: String): AgentType {
        return try {
            AgentType.valueOf(value.uppercase().trim())
        } catch (_: IllegalArgumentException) {
            AgentType.GENERAL
        }
    }
}
