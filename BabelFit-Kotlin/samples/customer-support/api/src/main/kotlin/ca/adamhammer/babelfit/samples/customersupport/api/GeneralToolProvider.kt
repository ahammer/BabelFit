package ca.adamhammer.babelfit.samples.customersupport.api

import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.model.ToolDefinition
import ca.adamhammer.babelfit.model.ToolResult
import ca.adamhammer.babelfit.samples.customersupport.models.AgentType
import ca.adamhammer.babelfit.samples.customersupport.models.CompanyTemplate

@Suppress("MaxLineLength")
class GeneralToolProvider(
    private val template: CompanyTemplate,
    private val listener: SupportEventListener
) : ToolProvider {

    override fun listTools(): List<ToolDefinition> = TOOLS

    override suspend fun callTool(call: ToolCall): ToolResult {
        val args = parseArgs(call.arguments)
        val content = when (call.toolName) {
            "search_faq" -> {
                val query = args["query"] ?: "general"
                val faq = template.sections["faq"]
                val result = "FAQ results for '$query':\n${faq?.content ?: "No FAQ data available."}"
                listener.onToolInvocation(AgentType.GENERAL, "search_faq", result)
                result
            }
            "lookup_product_info" -> {
                val model = args["model"] ?: "all"
                val overview = template.sections["overview"]
                val specs = template.sections["technical_specs"]
                val result = buildString {
                    appendLine("Product info for '$model':")
                    appendLine(overview?.content ?: "")
                    appendLine()
                    appendLine("Available models: ${template.companyInfo.models.joinToString(", ")}")
                    appendLine("Sales channels: ${template.companyInfo.salesChannels.joinToString(", ")}")
                    if (specs != null) {
                        appendLine()
                        appendLine(specs.content)
                    }
                }
                listener.onToolInvocation(AgentType.GENERAL, "lookup_product_info", result)
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
                name = "search_faq",
                description = "Search the FAQ knowledge base for answers to common customer questions.",
                inputSchema = """{"type":"object","properties":{"query":{"type":"string","description":"The question or topic to search for"}},"required":["query"]}"""
            ),
            ToolDefinition(
                name = "lookup_product_info",
                description = "Look up product information, specifications, and available models.",
                inputSchema = """{"type":"object","properties":{"model":{"type":"string","description":"The product model to look up (Small, Medium, Large, or 'all')"}},"required":["model"]}"""
            ),
            ToolDefinition(
                name = "transfer_to_agent",
                description = "Transfer the customer to a different support agent. Use when the issue is outside your expertise. Available agents: TECHNICAL, BILLING, GENERAL, ESCALATION",
                inputSchema = """{"type":"object","properties":{"target_agent":{"type":"string","enum":["TECHNICAL","BILLING","GENERAL","ESCALATION"],"description":"The agent to transfer to"},"reason":{"type":"string","description":"Brief reason for the transfer"}},"required":["target_agent","reason"]}"""
            )
        )
    }
}
