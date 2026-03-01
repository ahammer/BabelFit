package ca.adamhammer.babelfit.samples.traceviewer.api

import ca.adamhammer.babelfit.debug.trace.SpanType
import ca.adamhammer.babelfit.debug.trace.TraceExport
import ca.adamhammer.babelfit.debug.trace.TraceSpan

data class TraceStatistics(
    val totalSpans: Int,
    val spansByType: Map<SpanType, Int>,
    val totalDurationMs: Long,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val retriedRequestCount: Int,
    val errorCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val longestSpan: TraceSpan?,
    val mostExpensiveSpan: TraceSpan?,
    val avgCallDurationMs: Long,
    val duplicatePromptCount: Int
)

object TraceStats {

    fun computeStats(trace: TraceExport): TraceStatistics {
        val spans = trace.spans

        val spansByType = spans.groupBy { it.type }.mapValues { it.value.size }

        val sessionSpan = spans.firstOrNull { it.type == SpanType.SESSION }
        val sessionEnd = sessionSpan?.endTimeMs
        val totalDurationMs = if (sessionSpan != null && sessionEnd != null) {
            sessionEnd - sessionSpan.startTimeMs
        } else if (spans.isNotEmpty()) {
            val start = spans.minOf { it.startTimeMs }
            val end = spans.mapNotNull { it.endTimeMs }.maxOrNull() ?: start
            end - start
        } else 0L

        val totalInputTokens = spans.mapNotNull { it.usage?.inputTokens }.sum()
        val totalOutputTokens = spans.mapNotNull { it.usage?.outputTokens }.sum()

        val attempts = spans.filter { it.type == SpanType.ATTEMPT }

        // Count retries: requests with more than one attempt child
        val attemptsByRequest = attempts.groupBy { it.parentId }
        val retriedRequestCount = attemptsByRequest.values.count { it.size > 1 }

        val errorCount = spans.count { it.error != null }
        val successCount = attempts.count { it.error == null }
        val failureCount = attempts.count { it.error != null }

        val spansWithDuration = spans.filter { it.endTimeMs != null }
        val longestSpan = spansWithDuration
            .filter { it.type != SpanType.SESSION }
            .maxByOrNull { (it.endTimeMs ?: 0) - it.startTimeMs }

        val mostExpensiveSpan = spans
            .filter { it.usage != null }
            .maxByOrNull { (it.usage?.inputTokens ?: 0) + (it.usage?.outputTokens ?: 0) }

        val avgCallDurationMs = if (attempts.isNotEmpty()) {
            val durations = attempts.mapNotNull { a ->
                a.endTimeMs?.let { end -> end - a.startTimeMs }
            }
            if (durations.isNotEmpty()) durations.average().toLong() else 0L
        } else 0L

        // Detect duplicate system prompts
        val promptTexts = attempts.mapNotNull { it.requestInput }
        val duplicatePromptCount = promptTexts.size - promptTexts.distinct().size

        return TraceStatistics(
            totalSpans = spans.size,
            spansByType = spansByType,
            totalDurationMs = totalDurationMs,
            totalInputTokens = totalInputTokens,
            totalOutputTokens = totalOutputTokens,
            retriedRequestCount = retriedRequestCount,
            errorCount = errorCount,
            successCount = successCount,
            failureCount = failureCount,
            longestSpan = longestSpan,
            mostExpensiveSpan = mostExpensiveSpan,
            avgCallDurationMs = avgCallDurationMs,
            duplicatePromptCount = duplicatePromptCount
        )
    }

    fun formatDuration(ms: Long): String = when {
        ms >= 60_000 -> "%.1fm".format(ms / 60_000.0)
        ms >= 1_000 -> "%.1fs".format(ms / 1_000.0)
        else -> "${ms}ms"
    }

    fun formatTokens(count: Long): String = when {
        count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(count / 1_000.0)
        else -> count.toString()
    }
}
