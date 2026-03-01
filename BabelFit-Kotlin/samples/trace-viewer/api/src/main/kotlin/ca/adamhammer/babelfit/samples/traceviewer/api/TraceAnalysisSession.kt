package ca.adamhammer.babelfit.samples.traceviewer.api

import ca.adamhammer.babelfit.babelFit
import ca.adamhammer.babelfit.debug.trace.SpanType
import ca.adamhammer.babelfit.debug.trace.TraceExport
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.samples.traceviewer.models.PromptSuggestion
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceAnalysis
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceAnalyzerAPI
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceContextInterceptor
import ca.adamhammer.babelfit.samples.traceviewer.models.Weakness

class TraceAnalysisSession(
    apiAdapter: ApiAdapter,
    requestListeners: List<RequestListener> = emptyList()
) {
    private var traceSummary: String = ""

    private val analyzerApi: TraceAnalyzerAPI = babelFit<TraceAnalyzerAPI> {
        adapter(apiAdapter)
        addInterceptor(TraceContextInterceptor { traceSummary })
        requestListeners.forEach { listener(it) }
        resilience {
            maxRetries = 1
            retryDelayMs = 500
        }
    }.api

    suspend fun analyze(trace: TraceExport): TraceAnalysis {
        traceSummary = buildTraceSummary(trace)
        return analyzerApi.analyzeTrace(traceSummary).get()
    }

    suspend fun optimizePrompt(
        systemPrompt: String,
        schema: String,
        weaknesses: List<Weakness>
    ): List<PromptSuggestion> {
        val issues = weaknesses.joinToString(", ") { "${it.category}: ${it.description}" }
        return analyzerApi.suggestPromptImprovements(systemPrompt, schema, issues).get()
    }

    private fun buildTraceSummary(trace: TraceExport): String = buildString {
        val stats = TraceStats.computeStats(trace)

        appendLine("## Trace Overview")
        appendLine("- Spans: ${stats.totalSpans}")
        appendLine("- Duration: ${TraceStats.formatDuration(stats.totalDurationMs)}")
        val tokIn = TraceStats.formatTokens(stats.totalInputTokens)
        val tokOut = TraceStats.formatTokens(stats.totalOutputTokens)
        appendLine("- Tokens: $tokIn in / $tokOut out")
        appendLine("- Errors: ${stats.errorCount}, Retries: ${stats.retryCount}")
        appendLine("- Duplicate prompts: ${stats.duplicatePromptCount}")
        appendLine()

        appendLine("## Span Details")
        for (span in trace.spans) {
            val endTime = span.endTimeMs
            val duration = if (endTime != null) {
                TraceStats.formatDuration(endTime - span.startTimeMs)
            } else "ongoing"

            appendLine("### ${span.type}: ${span.name} ($duration)")
            val spanError = span.error
            if (spanError != null) {
                appendLine("ERROR: ${spanError.type}: ${spanError.message}")
            }
            val spanUsage = span.usage
            if (spanUsage != null) {
                appendLine("Tokens: ${spanUsage.inputTokens} in / ${spanUsage.outputTokens} out")
            }
            if (span.type == SpanType.ATTEMPT) {
                span.requestInput?.let { input ->
                    val truncated = if (input.length > 2000) input.take(2000) + "...[truncated]" else input
                    appendLine("Prompt: $truncated")
                }
                span.schema?.let { appendLine("Schema: $it") }
                span.responseOutput?.let { output ->
                    val truncated = if (output.length > 500) output.take(500) + "...[truncated]" else output
                    appendLine("Response: $truncated")
                }
            }
            if (span.type == SpanType.TOOL_CALL) {
                span.description?.let { appendLine("Args: $it") }
                span.responseOutput?.let { appendLine("Result: $it") }
            }
            appendLine()
        }
    }
}
