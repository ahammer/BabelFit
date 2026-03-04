package ca.adamhammer.babelfit.debug.trace

import ca.adamhammer.babelfit.context.TraceContextKeys
import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.model.ToolDefinition
import ca.adamhammer.babelfit.model.ToolResult

class TracingToolProvider(
    private val delegate: ToolProvider,
    private val session: TraceSession,
    private val parentSpanId: String
) : ToolProvider {
    override fun listTools(): List<ToolDefinition> = delegate.listTools()

    override suspend fun callTool(call: ToolCall): ToolResult {
        val toolSpanId = session.startSpan(
            type = SpanType.TOOL_CALL,
            name = "Tool: ${call.toolName}",
            parentId = parentSpanId,
            description = call.arguments
        )

        return try {
            val result = delegate.callTool(call)
            session.completeSpan(
                id = toolSpanId,
                responseOutput = result.content
            )
            result
        } catch (e: Exception) {
            session.failSpan(toolSpanId, e)
            throw e
        }
    }
}
