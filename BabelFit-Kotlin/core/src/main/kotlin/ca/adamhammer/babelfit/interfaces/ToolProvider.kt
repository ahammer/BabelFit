package ca.adamhammer.babelfit.interfaces

import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.model.ToolDefinition
import ca.adamhammer.babelfit.model.ToolResult

/**
 * Provides tools that can be invoked during AI interactions.
 *
 * Implementations discover and execute tools from various sources:
 * MCP servers, local functions, or other tool registries.
 *
 * Registered via [ca.adamhammer.babelfit.BabelFitBuilder.toolProvider],
 * tool providers are made available to adapters that support multi-turn
 * tool-calling loops.
 */
interface ToolProvider {
    /** Returns the tools currently available from this provider. */
    fun listTools(): List<ToolDefinition>

    /** Executes a tool call and returns the result. */
    suspend fun callTool(call: ToolCall): ToolResult
}
