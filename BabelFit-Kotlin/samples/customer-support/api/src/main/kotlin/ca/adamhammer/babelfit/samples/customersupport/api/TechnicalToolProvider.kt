package ca.adamhammer.babelfit.samples.customersupport.api

import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.model.ToolDefinition
import ca.adamhammer.babelfit.model.ToolResult
import ca.adamhammer.babelfit.samples.customersupport.models.CompanyTemplate

@Suppress("MaxLineLength")
class TechnicalToolProvider(
    private val template: CompanyTemplate,
    private val listener: SupportEventListener
) : ToolProvider {

    override fun listTools(): List<ToolDefinition> = TOOLS

    override suspend fun callTool(call: ToolCall): ToolResult {
        val args = parseArgs(call.arguments)
        val content = when (call.toolName) {
            "lookup_troubleshooting_steps" -> {
                val query = args["query"] ?: "general"
                val section = template.sections["troubleshooting"]
                val result = "Troubleshooting for '$query':\n${section?.content ?: "No troubleshooting data available."}"
                listener.onToolInvocation(
                    ca.adamhammer.babelfit.samples.customersupport.models.AgentType.TECHNICAL,
                    "lookup_troubleshooting_steps", result
                )
                result
            }
            "check_firmware_info" -> {
                val serial = args["serial_number"] ?: "unknown"
                val result = "Firmware for device $serial: v3.2.1 (latest: v3.2.1). Status: UP TO DATE. " +
                    "Last update: 2026-02-15. Auto-update: enabled."
                listener.onToolInvocation(
                    ca.adamhammer.babelfit.samples.customersupport.models.AgentType.TECHNICAL,
                    "check_firmware_info", result
                )
                result
            }
            "run_diagnostics" -> {
                val serial = args["serial_number"] ?: "unknown"
                val result = "Diagnostics for $serial:\n" +
                    "- Wi-Fi signal: Strong (-42 dBm)\n" +
                    "- Power: Normal (5.1V)\n" +
                    "- Temperature: 38°C (within limits)\n" +
                    "- Memory: 62% used\n" +
                    "- Uptime: 14 days\n" +
                    "- Error log: 2 warnings (non-critical), 0 errors"
                listener.onToolInvocation(
                    ca.adamhammer.babelfit.samples.customersupport.models.AgentType.TECHNICAL,
                    "run_diagnostics", result
                )
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
                name = "lookup_troubleshooting_steps",
                description = "Look up troubleshooting steps for a customer issue from the knowledge base.",
                inputSchema = """{"type":"object","properties":{"query":{"type":"string","description":"The issue to look up troubleshooting steps for"}},"required":["query"]}"""
            ),
            ToolDefinition(
                name = "check_firmware_info",
                description = "Check firmware version and update status for a device by serial number.",
                inputSchema = """{"type":"object","properties":{"serial_number":{"type":"string","description":"The device serial number"}},"required":["serial_number"]}"""
            ),
            ToolDefinition(
                name = "run_diagnostics",
                description = "Run remote diagnostics on a customer's device by serial number.",
                inputSchema = """{"type":"object","properties":{"serial_number":{"type":"string","description":"The device serial number"}},"required":["serial_number"]}"""
            ),
            ToolDefinition(
                name = "transfer_to_agent",
                description = "Transfer the customer to a different support agent. Use when the issue is outside your expertise. Available agents: TECHNICAL, BILLING, GENERAL, ESCALATION",
                inputSchema = """{"type":"object","properties":{"target_agent":{"type":"string","enum":["TECHNICAL","BILLING","GENERAL","ESCALATION"],"description":"The agent to transfer to"},"reason":{"type":"string","description":"Brief reason for the transfer"}},"required":["target_agent","reason"]}"""
            )
        )
    }
}
