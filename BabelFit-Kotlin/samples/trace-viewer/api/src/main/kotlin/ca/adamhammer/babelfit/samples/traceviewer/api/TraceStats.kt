package ca.adamhammer.babelfit.samples.traceviewer.api

import ca.adamhammer.babelfit.debug.trace.SpanType
import ca.adamhammer.babelfit.debug.trace.TraceExport
import ca.adamhammer.babelfit.debug.trace.TraceSpan
import ca.adamhammer.babelfit.samples.traceviewer.models.MetricDelta
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceComparison
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceComparisonStats

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

    fun compareTraces(current: TraceExport, previous: TraceExport): TraceComparison {
        val curStats = computeStats(current)
        val prevStats = computeStats(previous)

        val curComp = curStats.toComparisonStats()
        val prevComp = prevStats.toComparisonStats()

        val deltas = buildList {
            add(longDelta(
                "Duration", prevStats.totalDurationMs,
                curStats.totalDurationMs, "ms", lowerIsBetter = true
            ))
            add(longDelta(
                "Input Tokens", prevStats.totalInputTokens,
                curStats.totalInputTokens, "", lowerIsBetter = true
            ))
            add(longDelta(
                "Output Tokens", prevStats.totalOutputTokens,
                curStats.totalOutputTokens, "", lowerIsBetter = true
            ))
            add(intDelta(
                "Errors", prevStats.errorCount,
                curStats.errorCount, lowerIsBetter = true
            ))
            add(intDelta(
                "Retries", prevStats.retriedRequestCount,
                curStats.retriedRequestCount, lowerIsBetter = true
            ))
            add(intDelta(
                "Successes", prevStats.successCount,
                curStats.successCount, lowerIsBetter = false
            ))
            add(longDelta(
                "Avg Call Duration", prevStats.avgCallDurationMs,
                curStats.avgCallDurationMs, "ms", lowerIsBetter = true
            ))
            add(intDelta(
                "Duplicate Prompts", prevStats.duplicatePromptCount,
                curStats.duplicatePromptCount, lowerIsBetter = true
            ))
            add(intDelta(
                "Total Spans", prevStats.totalSpans,
                curStats.totalSpans, lowerIsBetter = true
            ))
        }

        return TraceComparison(current = curComp, previous = prevComp, deltas = deltas)
    }

    private fun TraceStatistics.toComparisonStats() = TraceComparisonStats(
        totalSpans = totalSpans,
        totalDurationMs = totalDurationMs,
        totalInputTokens = totalInputTokens,
        totalOutputTokens = totalOutputTokens,
        errorCount = errorCount,
        retriedRequestCount = retriedRequestCount,
        successCount = successCount,
        avgCallDurationMs = avgCallDurationMs,
        duplicatePromptCount = duplicatePromptCount
    )

    private fun longDelta(metric: String, prev: Long, cur: Long, suffix: String, lowerIsBetter: Boolean): MetricDelta {
        val diff = cur - prev
        val pct = if (prev > 0) "%.1f%%".format((diff.toDouble() / prev) * 100) else "N/A"
        val sign = if (diff > 0) "+" else ""
        val improved = if (lowerIsBetter) diff < 0 else diff > 0
        return MetricDelta(
            metric = metric,
            previous = "$prev$suffix",
            current = "$cur$suffix",
            delta = "$sign$diff$suffix ($pct)",
            improved = improved
        )
    }

    private fun intDelta(metric: String, prev: Int, cur: Int, lowerIsBetter: Boolean): MetricDelta {
        return longDelta(metric, prev.toLong(), cur.toLong(), "", lowerIsBetter)
    }
}
