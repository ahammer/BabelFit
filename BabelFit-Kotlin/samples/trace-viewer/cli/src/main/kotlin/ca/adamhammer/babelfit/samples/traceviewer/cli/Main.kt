package ca.adamhammer.babelfit.samples.traceviewer.cli

import ca.adamhammer.babelfit.adapters.OpenAiAdapter
import ca.adamhammer.babelfit.debug.trace.SpanType
import ca.adamhammer.babelfit.debug.trace.TraceExport
import ca.adamhammer.babelfit.debug.trace.TracingAdapter
import ca.adamhammer.babelfit.debug.trace.TracingRequestListener
import ca.adamhammer.babelfit.debug.trace.TraceSession
import ca.adamhammer.babelfit.samples.traceviewer.api.SpanNode
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceAnalysisSession
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceLoader
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceStatistics
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceStats
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceAnalysis
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        println("Usage: trace-viewer <trace.btrace.json> [--analyze]")
        return@runBlocking
    }

    val filePath = args[0]
    val doAnalyze = args.any { it == "--analyze" }
    val file = File(filePath)

    if (!file.exists()) {
        System.err.println("File not found: $filePath")
        return@runBlocking
    }

    println("═══════════════════════════════════════════════════════")
    println("  BabelFit Trace Viewer")
    println("═══════════════════════════════════════════════════════")
    println("  Loading: ${file.name}")
    println()

    val trace = TraceLoader.loadFromFile(file)
    val roots = TraceLoader.buildSpanTree(trace)
    val stats = TraceStats.computeStats(trace)

    printStats(stats)
    printSpanTree(roots)

    if (doAnalyze) {
        runAnalysis(trace)
    } else {
        println("  Tip: Add --analyze flag to run AI-powered analysis")
    }
}

private fun printStats(stats: TraceStatistics) {
    println("  ── Statistics ──────────────────────────────────────")
    println("  Spans:      ${stats.totalSpans}")
    stats.spansByType.forEach { (type, count) ->
        println("    $type: $count")
    }
    println("  Duration:   ${TraceStats.formatDuration(stats.totalDurationMs)}")
    println("  Tokens In:  ${TraceStats.formatTokens(stats.totalInputTokens)}")
    println("  Tokens Out: ${TraceStats.formatTokens(stats.totalOutputTokens)}")
    println("  Errors:     ${stats.errorCount}")
    println("  Retries:    ${stats.retryCount}")
    println("  Dup Prompts:${stats.duplicatePromptCount}")
    println()
}

private fun printSpanTree(roots: List<SpanNode>) {
    println("  ── Span Tree ───────────────────────────────────────")
    val flatList = TraceLoader.flattenTree(roots)
    for ((span, depth) in flatList) {
        val indent = "  " + "  ".repeat(depth)
        val endTime = span.endTimeMs
        val duration = if (endTime != null) {
            TraceStats.formatDuration(endTime - span.startTimeMs)
        } else "..."

        val tokens = span.usage?.let {
            " [${it.inputTokens}↑ ${it.outputTokens}↓]"
        } ?: ""

        val error = if (span.error != null) " ✗" else ""
        val typeTag = spanTypeIcon(span.type)

        println("$indent$typeTag ${span.name} ($duration)$tokens$error")
    }
    println()
}

private fun spanTypeIcon(type: SpanType): String = when (type) {
    SpanType.SESSION -> "⬢"
    SpanType.REQUEST -> "▶"
    SpanType.ATTEMPT -> "◆"
    SpanType.TOOL_CALL -> "⚙"
    SpanType.AGENT -> "◎"
    SpanType.STEP -> "○"
}

private suspend fun runAnalysis(trace: TraceExport) {
    println("  ── AI Analysis ─────────────────────────────────────")
    println("  Running analysis...")

    val traceSession = TraceSession()
    val adapter = TracingAdapter(OpenAiAdapter(), traceSession)
    val analysisSession = TraceAnalysisSession(
        apiAdapter = adapter,
        requestListeners = listOf(TracingRequestListener(traceSession))
    )

    val analysis = analysisSession.analyze(trace)
    printAnalysis(analysis)
}

private fun printAnalysis(analysis: TraceAnalysis) {
    println()
    println("  Summary: ${analysis.summary}")
    println()

    val eff = analysis.tokenEfficiency
    println("  Token Efficiency:")
    println("    Input:  ${TraceStats.formatTokens(eff.totalInputTokens)}")
    println("    Output: ${TraceStats.formatTokens(eff.totalOutputTokens)}")
    println("    Retry:  ${TraceStats.formatTokens(eff.retryTokens)}")
    println("    Waste:  ~${eff.estimatedWastePercent}%")
    println()

    if (analysis.weaknesses.isNotEmpty()) {
        println("  Weaknesses (${analysis.weaknesses.size}):")
        for (w in analysis.weaknesses) {
            println("    [${w.severity}] ${w.category}: ${w.description}")
        }
        println()
    }

    if (analysis.suggestions.isNotEmpty()) {
        println("  Suggestions (${analysis.suggestions.size}):")
        for ((i, s) in analysis.suggestions.withIndex()) {
            println("    ${i + 1}. ${s.targetArea}")
            println("       Current:    ${s.currentApproach}")
            println("       Suggested:  ${s.suggestedImprovement}")
            println("       Rationale:  ${s.rationale}")
            println("       Impact:     ${s.estimatedImpact}")
            println()
        }
    }
}
