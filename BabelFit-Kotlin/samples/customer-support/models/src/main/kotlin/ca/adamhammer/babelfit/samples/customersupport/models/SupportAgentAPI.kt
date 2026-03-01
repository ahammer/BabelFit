package ca.adamhammer.babelfit.samples.customersupport.models

import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.AiParameter
import ca.adamhammer.babelfit.annotations.AiResponse
import ca.adamhammer.babelfit.annotations.Memorize
import java.util.concurrent.Future

interface SupportAgentAPI {

    @AiOperation(
        summary = "Respond to a customer support message",
        description = """You are a customer support agent for WidgetCo.
            Help the customer with their issue using your available tools and knowledge.
            Be professional, empathetic, and solution-oriented.
            If the customer's issue falls outside your expertise, use the transfer_to_agent tool.
            If the issue is resolved, set resolved=true in your response."""
    )
    @AiResponse(description = "Your response to the customer including any suggested follow-up actions")
    fun respond(
        @AiParameter(description = "The customer's message")
        userMessage: String
    ): Future<SupportResponse>

    @AiOperation(
        summary = "Summarize the conversation so far for handoff",
        description = """Produce a concise summary of the conversation for another agent.
            Include: the customer's core issue, what has been tried/discussed,
            current status, and any relevant details the next agent needs."""
    )
    @AiResponse(description = "A concise summary of the conversation for agent handoff")
    @Memorize(label = "Conversation Summary")
    fun summarizeConversation(): Future<String>
}
