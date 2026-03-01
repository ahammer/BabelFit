package ca.adamhammer.babelfit.samples.traceviewer.api

import ca.adamhammer.babelfit.babelFit
import ca.adamhammer.babelfit.debug.trace.SpanType
import ca.adamhammer.babelfit.debug.trace.TraceExport
import ca.adamhammer.babelfit.debug.trace.TraceSpan
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.samples.traceviewer.models.AgentPrompt
import ca.adamhammer.babelfit.samples.traceviewer.models.PromptSuggestion
import ca.adamhammer.babelfit.samples.traceviewer.models.SpanAssessment
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceAnalysis
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceAnalyzerAPI
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceContextInterceptor
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceReport
import ca.adamhammer.babelfit.samples.traceviewer.models.Weakness

class TraceAnalysisSession(
    apiAdapter: ApiAdapter,
    requestListeners: List<RequestListener> = emptyList()
) {
    private var traceSummary: String = ""

    private val analyzerApi: TraceAnalyzerAPI = babelFit<TraceAnalyzerAPI> {
        adapter(apiAdapter)
        addInterceptor(TraceContextInterceptor(
            cheatSheetProvider = { BabelFitCheatSheet.text },
            traceSummaryProvider = { traceSummary }
        ))
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

    suspend fun assessSpan(span: TraceSpan, trace: TraceExport): SpanAssessment {
        traceSummary = buildTraceSummary(trace)
        val context = buildSpanContext(span, trace)
        return analyzerApi.assessSpan(context).get()
    }

    suspend fun generateReport(trace: TraceExport): TraceReport {
        traceSummary = buildTraceSummary(trace)
        val analysis = analyzerApi.analyzeTrace(traceSummary).get()

        val assessableSpans = trace.spans.filter {
            it.type == SpanType.ATTEMPT || it.type == SpanType.TOOL_CALL
        }
        val assessments = mutableMapOf<String, SpanAssessment>()
        for (span in assessableSpans) {
            val context = buildSpanContext(span, trace)
            assessments[span.id] = analyzerApi.assessSpan(context).get()
        }

        val agentPrompt = analyzerApi.generateAgentPrompt(
            buildAgentPromptContext(analysis, assessments)
        ).get()

        val markdown = TraceReportFormatter.formatMarkdown(
            analysis, assessments, agentPrompt, trace
        )

        return TraceReport(
            sessionAnalysis = analysis,
            spanAssessments = assessments,
            agentPrompt = agentPrompt,
            markdownSummary = markdown
        )
    }

    suspend fun optimizePrompt(
        systemPrompt: String,
        schema: String,
        weaknesses: List<Weakness>
    ): List<PromptSuggestion> {
        val issues = weaknesses.joinToString(", ") { "${it.category}: ${it.description}" }
        return analyzerApi.suggestPromptImprovements(systemPrompt, schema, issues).get()
    }

    private fun buildSpanContext(span: TraceSpan, trace: TraceExport): String = buildString {
        val parent = trace.spans.firstOrNull { it.id == span.parentId }
        appendLine("## Span: ${span.type} — ${span.name}")
        if (parent != null) {
            appendLine("Parent: ${parent.type} — ${parent.name}")
        }
        val endTime = span.endTimeMs
        if (endTime != null) {
            appendLine("Duration: ${TraceStats.formatDuration(endTime - span.startTimeMs)}")
        }
        val spanError = span.error
        if (spanError != null) {
            appendLine("ERROR: ${spanError.type}: ${spanError.message}")
        } else {
            appendLine("Status: Success")
        }
        val spanUsage = span.usage
        if (spanUsage != null) {
            appendLine("Tokens: ${spanUsage.inputTokens} in / ${spanUsage.outputTokens} out")
        }
        span.requestInput?.let { input ->
            val truncated = if (input.length > 3000) input.take(3000) + "...[truncated]" else input
            appendLine("Prompt:\n$truncated")
        }
        span.schema?.let { appendLine("Schema:\n$it") }
        span.responseOutput?.let { output ->
            val truncated = if (output.length > 1000) output.take(1000) + "...[truncated]" else output
            appendLine("Response:\n$truncated")
        }
        if (span.type == SpanType.TOOL_CALL) {
            span.description?.let { appendLine("Tool Args: $it") }
        }
    }

    private fun buildAgentPromptContext(
        analysis: TraceAnalysis,
        assessments: Map<String, SpanAssessment>
    ): String = buildString {
        appendLine("## Session Analysis Summary")
        appendLine(analysis.summary)
        appendLine()

        if (analysis.weaknesses.isNotEmpty()) {
            appendLine("## Weaknesses")
            analysis.weaknesses.forEach { w ->
                appendLine("- [${w.severity}] ${w.category}: ${w.description}")
            }
            appendLine()
        }

        if (assessments.isNotEmpty()) {
            appendLine("## Per-Span Assessments")
            assessments.forEach { (spanId, assessment) ->
                appendLine("### Span $spanId (${assessment.quality})")
                assessment.observations.forEach { appendLine("- $it") }
                assessment.codeGuidance.forEach { g ->
                    appendLine("  Code: [${g.target}] ${g.suggestedChange}")
                }
            }
            appendLine()
        }

        appendLine("## Trace Summary")
        appendLine(traceSummary)
    }

    private fun buildTraceSummary(trace: TraceExport): String = buildString {
        val stats = TraceStats.computeStats(trace)

        appendLine("## Trace Overview")
        appendLine("- Spans: ${stats.totalSpans}")
        appendLine("- Duration: ${TraceStats.formatDuration(stats.totalDurationMs)}")
        val tokIn = TraceStats.formatTokens(stats.totalInputTokens)
        val tokOut = TraceStats.formatTokens(stats.totalOutputTokens)
        appendLine("- Tokens: $tokIn in / $tokOut out")
        appendLine("- Errors: ${stats.errorCount}, Retried Requests: ${stats.retriedRequestCount}")
        appendLine("- Successes: ${stats.successCount}, Failures: ${stats.failureCount}")
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
                    val truncated = if (input.length > 2000) {
                        input.take(2000) + "...[truncated]"
                    } else input
                    appendLine("Prompt: $truncated")
                }
                span.schema?.let { appendLine("Schema: $it") }
                span.responseOutput?.let { output ->
                    val truncated = if (output.length > 500) {
                        output.take(500) + "...[truncated]"
                    } else output
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
