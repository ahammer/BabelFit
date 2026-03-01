package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.model.PromptContext

/**
 * Injects agent workflow context into the system instructions so the LLM
 * understands where it is in the editing pipeline.
 */
class AgentStateInterceptor(
    private val maxSteps: Int
) : Interceptor {
    private var stepCount = 0

    fun nextStep() { stepCount++ }
    fun reset() { stepCount = 0 }

    override fun intercept(context: PromptContext): PromptContext {
        val remaining = maxSteps - stepCount
        val injection = """
            |
            |# AGENT WORKFLOW STATE
            |**Current Step:** ${context.methodName}
            |**Step Number:** ${stepCount + 1} / $maxSteps
            |**Budget Remaining:** $remaining steps
            |
            |You are in an agentic editing workflow. Focus on completing the current step
            |efficiently. Use tools to read and modify the document as needed.
        """.trimMargin()

        return context.copy(
            systemInstructions = context.systemInstructions + injection
        )
    }
}
