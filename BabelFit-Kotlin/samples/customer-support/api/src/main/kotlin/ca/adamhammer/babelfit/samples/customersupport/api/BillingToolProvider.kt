package ca.adamhammer.babelfit.samples.customersupport.api

import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.model.ToolDefinition
import ca.adamhammer.babelfit.model.ToolResult
import ca.adamhammer.babelfit.samples.customersupport.models.AgentType
import ca.adamhammer.babelfit.samples.customersupport.models.CompanyTemplate

@Suppress("MaxLineLength", "UnusedPrivateProperty")
class BillingToolProvider(
    private val template: CompanyTemplate,
    private val listener: SupportEventListener
) : ToolProvider {

    override fun listTools(): List<ToolDefinition> = TOOLS

    override suspend fun callTool(call: ToolCall): ToolResult {
        val args = parseArgs(call.arguments)
        val content = when (call.toolName) {
            "lookup_warranty_status" -> {
                val serial = args["serial_number"] ?: "unknown"
                val result = "Warranty status for $serial:\n" +
                    "- Purchase date: 2025-08-15\n" +
                    "- Warranty expires: 2026-08-15\n" +
                    "- Status: ACTIVE\n" +
                    "- Coverage: Standard (manufacturing defects)\n" +
                    "- Claims filed: 0"
                listener.onToolInvocation(AgentType.BILLING, "lookup_warranty_status", result)
                result
            }
            "create_rma_request" -> {
                val serial = args["serial_number"] ?: "unknown"
                val issue = args["issue_description"] ?: "Not specified"
                val result = "RMA Request Created:\n" +
                    "- RMA #: RMA-2026-${(1000..9999).random()}\n" +
                    "- Serial: $serial\n" +
                    "- Issue: $issue\n" +
                    "- Status: PENDING REVIEW\n" +
                    "- Expected turnaround: 5-7 business days\n" +
                    "- Shipping label will be emailed within 24 hours"
                listener.onToolInvocation(AgentType.BILLING, "create_rma_request", result)
                result
            }
            "check_order_status" -> {
                val orderId = args["order_id"] ?: "unknown"
                val result = "Order $orderId:\n" +
                    "- Status: DELIVERED\n" +
                    "- Shipped: 2025-08-10\n" +
                    "- Delivered: 2025-08-15\n" +
                    "- Items: Widget Medium (x1)\n" +
                    "- Tracking: 1Z999AA10123456784"
                listener.onToolInvocation(AgentType.BILLING, "check_order_status", result)
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
                name = "lookup_warranty_status",
                description = "Look up warranty status and coverage details for a device by serial number.",
                inputSchema = """{"type":"object","properties":{"serial_number":{"type":"string","description":"The device serial number"}},"required":["serial_number"]}"""
            ),
            ToolDefinition(
                name = "create_rma_request",
                description = "Create a Return Merchandise Authorization (RMA) request for a defective device.",
                inputSchema = """{"type":"object","properties":{"serial_number":{"type":"string","description":"The device serial number"},"issue_description":{"type":"string","description":"Description of the issue"}},"required":["serial_number","issue_description"]}"""
            ),
            ToolDefinition(
                name = "check_order_status",
                description = "Check the status of a customer order by order ID.",
                inputSchema = """{"type":"object","properties":{"order_id":{"type":"string","description":"The order ID to look up"}},"required":["order_id"]}"""
            ),
            ToolDefinition(
                name = "transfer_to_agent",
                description = "Transfer the customer to a different support agent. Use when the issue is outside your expertise. Available agents: TECHNICAL, BILLING, GENERAL, ESCALATION",
                inputSchema = """{"type":"object","properties":{"target_agent":{"type":"string","enum":["TECHNICAL","BILLING","GENERAL","ESCALATION"],"description":"The agent to transfer to"},"reason":{"type":"string","description":"Brief reason for the transfer"}},"required":["target_agent","reason"]}"""
            )
        )
    }
}
