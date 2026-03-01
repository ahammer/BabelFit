package ca.adamhammer.babelfit.samples.customersupport.models

import ca.adamhammer.babelfit.annotations.AiSchema
import kotlinx.serialization.Serializable

enum class AgentType(val displayName: String) {
    ROUTING("Router"),
    TECHNICAL("Technical Support"),
    BILLING("Billing & Returns"),
    GENERAL("General Support"),
    ESCALATION("Escalation");
}

@Serializable
@AiSchema(title = "TransferDecision", description = "Decision about which specialist agent should handle the customer's request")
data class TransferDecision(
    val targetAgent: String,
    val contextSummary: String,
    val reason: String
)

@Serializable
@AiSchema(title = "SupportResponse", description = "A response from a support agent to the customer")
data class SupportResponse(
    val message: String,
    val suggestedActions: List<String> = emptyList(),
    val resolved: Boolean = false
)

data class CustomerContext(
    val name: String,
    val accountId: String,
    val productModel: String,
    val serialNumber: String,
    val purchaseDate: String
)
