package ca.adamhammer.babelfit.samples.customersupport.api

import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.model.ToolDefinition
import ca.adamhammer.babelfit.model.ToolResult
import ca.adamhammer.babelfit.samples.customersupport.models.AgentType

@Suppress("MaxLineLength")
class EscalationToolProvider(
    private val listener: SupportEventListener
) : ToolProvider {

    override fun listTools(): List<ToolDefinition> = TOOLS

    override suspend fun callTool(call: ToolCall): ToolResult {
        val args = parseArgs(call.arguments)
        val content = when (call.toolName) {
            "collect_escalation_details" -> {
                val issue = args["issue_summary"] ?: "Not provided"
                val priority = args["priority"] ?: "HIGH"
                val result = "Escalation details collected:\n" +
                    "- Summary: $issue\n" +
                    "- Priority: $priority\n" +
                    "- Previous agents consulted: noted in conversation history\n" +
                    "- Ready for ticket creation"
                listener.onToolInvocation(AgentType.ESCALATION, "collect_escalation_details", result)
                result
            }
            "create_escalation_ticket" -> {
                val issue = args["issue_summary"] ?: "Not provided"
                val priority = args["priority"] ?: "HIGH"
                val ticketId = "ESC-2026-${(10000..99999).random()}"
                val result = "Escalation Ticket Created:\n" +
                    "- Ticket #: $ticketId\n" +
                    "- Priority: $priority\n" +
                    "- Summary: $issue\n" +
                    "- Status: OPEN\n" +
                    "- Assigned to: Senior Support Team\n" +
                    "- SLA: Response within 4 business hours"
                listener.onToolInvocation(AgentType.ESCALATION, "create_escalation_ticket", result)
                listener.onEscalation(ticketId)
                result
            }
            "transfer_to_agent" -> {
                val target = args["target_agent"] ?: "GENERAL"
                val reason = args["reason"] ?: ""
                "TRANSFER:$target:$reason"
            }
            else -> return ToolResult(
                id = call.id, toolName = call.toolName,
                content = "Unknown tool: ${call.toolName}", isError = true
            )
        }
        return ToolResult(id = call.id, toolName = call.toolName, content = content, isError = false)
    }

    companion object {
        val TOOLS = listOf(
            ToolDefinition(
                name = "collect_escalation_details",
                description = "Collect and structure details needed for an escalation ticket.",
                inputSchema = """{"type":"object","properties":{"issue_summary":{"type":"string","description":"Summary of the unresolved issue"},"priority":{"type":"string","enum":["CRITICAL","HIGH","MEDIUM"],"description":"Priority level for the escalation"}},"required":["issue_summary","priority"]}"""
            ),
            ToolDefinition(
                name = "create_escalation_ticket",
                description = "Create a formal escalation ticket for the senior support team.",
                inputSchema = """{"type":"object","properties":{"issue_summary":{"type":"string","description":"Detailed summary of the issue"},"priority":{"type":"string","enum":["CRITICAL","HIGH","MEDIUM"],"description":"Priority level"}},"required":["issue_summary","priority"]}"""
            ),
            ToolDefinition(
                name = "transfer_to_agent",
                description = "Transfer the customer to a different support agent. Available agents: TECHNICAL, BILLING, GENERAL, ESCALATION",
                inputSchema = """{"type":"object","properties":{"target_agent":{"type":"string","enum":["TECHNICAL","BILLING","GENERAL","ESCALATION"],"description":"The agent to transfer to"},"reason":{"type":"string","description":"Brief reason for the transfer"}},"required":["target_agent","reason"]}"""
            )
        )
    }
}
