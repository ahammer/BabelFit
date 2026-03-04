package ca.adamhammer.babelfit.debug.trace

import ca.adamhammer.babelfit.context.TraceContextKeys
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.model.UsageInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TracingRequestListener(
    private val session: TraceSession
) : RequestListener {

    private fun getRequestName(context: PromptContext): String {
        return context.methodName.takeIf { it.isNotEmpty() } ?: "Unknown Request"
    }

    private fun getRequestId(context: PromptContext): String {
        return context[TraceContextKeys.CURRENT_SPAN_ID] ?: "unknown-request"
    }

    private fun getAttemptId(requestId: String, attempt: Int): String {
        return "$requestId-attempt-$attempt"
    }

    override fun onRequestStart(context: PromptContext) {
        val parentId = context[TraceContextKeys.PARENT_SPAN_ID] ?: session.getSessionId()
        val requestId = getRequestId(context)

        session.startSpan(
            type = SpanType.REQUEST,
            name = "Request: ${getRequestName(context)}",
            id = requestId,
            parentId = parentId
        )
    }

    override fun onRequestComplete(context: PromptContext, result: Any, durationMs: Long, usage: UsageInfo?) {
        val requestId = getRequestId(context)
        session.completeSpan(
            id = requestId,
            endTimeMs = System.currentTimeMillis(),
            usage = usage?.toSpanUsage()
        )
    }

    override fun onRequestError(context: PromptContext, error: Exception, durationMs: Long) {
        val requestId = getRequestId(context)
        session.failSpan(requestId, error)
    }

    override fun onAttemptStart(context: PromptContext, attemptNumber: Int) {
        val requestId = getRequestId(context)
        val attemptId = getAttemptId(requestId, attemptNumber)

        val messages = context.conversationHistory.map {
            SpanMessage(it.role.name, it.textContent)
        }

        session.startSpan(
            type = SpanType.ATTEMPT,
            name = "Attempt $attemptNumber",
            id = attemptId,
            parentId = requestId
        )
        // Set the request inputs/schema
        session.completeSpan(
            id = attemptId,
            schema = context.methodInvocation,
            requestInput = "System: ${context.systemInstructions}\nMemory:\n${context.memory}",
            messages = messages
        )
    }

    override fun onAttemptComplete(context: PromptContext, attemptNumber: Int, result: Any, durationMs: Long, usage: UsageInfo?) {
        val requestId = getRequestId(context)
        val attemptId = getAttemptId(requestId, attemptNumber)

        session.completeSpan(
            id = attemptId,
            responseOutput = result.toString(),
            usage = usage?.toSpanUsage()
        )
    }

    private fun UsageInfo.toSpanUsage() = SpanUsage(
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        estimatedCost = estimatedCost,
        model = model
    )

    override fun onAttemptError(context: PromptContext, attemptNumber: Int, error: Exception, durationMs: Long) {
        val requestId = getRequestId(context)
        val attemptId = getAttemptId(requestId, attemptNumber)

        session.failSpan(attemptId, error)
    }
}
