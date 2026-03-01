package ca.adamhammer.babelfit.samples.customersupport.models

import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.AiParameter
import ca.adamhammer.babelfit.annotations.AiResponse
import ca.adamhammer.babelfit.annotations.Memorize
import java.util.concurrent.Future

interface RoutingAgentAPI {

    @AiOperation(
        summary = "Classify and route a customer support request",
        description = """You are a customer support routing agent for WidgetCo.
            Analyze the customer's message and determine which specialist agent should handle it.

            Available agents:
            - TECHNICAL: Hardware/software issues, troubleshooting, setup, firmware, diagnostics, connectivity
            - BILLING: Warranty claims, returns (RMA), order status, purchase questions, refunds
            - GENERAL: Product info, FAQ, general questions, how-to guides, feature inquiries
            - ESCALATION: Angry customers, unresolved issues, requests to speak to a manager, complex multi-department issues

            Provide a concise summary of the customer's issue for the specialist agent."""
    )
    @AiResponse(description = "The routing decision with target agent and context summary")
    @Memorize(label = "Routing Decision")
    fun classifyAndRoute(
        @AiParameter(description = "The customer's support message")
        userMessage: String
    ): Future<TransferDecision>
}
