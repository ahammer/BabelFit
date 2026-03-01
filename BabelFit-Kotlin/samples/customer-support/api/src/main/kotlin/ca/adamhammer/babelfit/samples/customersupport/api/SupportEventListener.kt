package ca.adamhammer.babelfit.samples.customersupport.api

import ca.adamhammer.babelfit.samples.customersupport.models.AgentType
import ca.adamhammer.babelfit.samples.customersupport.models.CustomerContext
import ca.adamhammer.babelfit.samples.customersupport.models.SupportResponse

interface SupportEventListener {
    fun onSessionStarted(customerContext: CustomerContext) {}
    fun onAgentTransfer(from: AgentType, to: AgentType, summary: String) {}
    fun onAgentResponse(agentType: AgentType, response: SupportResponse) {}
    fun onToolInvocation(agentType: AgentType, toolName: String, result: String) {}
    fun onEscalation(ticket: String) {}
    fun onSessionEnded() {}
    fun onError(error: String) {}
}
