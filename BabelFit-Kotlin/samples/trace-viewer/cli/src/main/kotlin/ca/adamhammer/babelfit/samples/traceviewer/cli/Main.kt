package ca.adamhammer.babelfit.samples.traceviewer.cli

import ca.adamhammer.babelfit.adapters.ClaudeAdapter
import ca.adamhammer.babelfit.adapters.GeminiAdapter
import ca.adamhammer.babelfit.adapters.OpenAiAdapter
import ca.adamhammer.babelfit.debug.trace.SpanType
import ca.adamhammer.babelfit.debug.trace.TraceExport
import ca.adamhammer.babelfit.debug.trace.TraceSpan
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.samples.traceviewer.api.SpanNode
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceAnalysisSession
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceLoader
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceStatistics
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceReportFormatter
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceStats
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceAnalysis
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceComparison
import com.openai.models.ChatModel
import kotlinx.coroutines.runBlocking
import java.io.File

private const val USAGE = """Usage: trace-viewer <trace.btrace.json> [options]

Options:
  --analyze              Run AI-powered analysis (interactive)
  --report               Generate full markdown report (batch mode)
  --compare <file>       Compare with a previous trace (shows deltas)
  --vendor <name>        AI vendor: openai, anthropic, gemini (default: openai)
  --model <model>        Model name (default: vendor default)
  --output <file>        Output file for report (default: stdout)"""

fun main(args: Array<String>) = runBlocking {
    if (args.isEmpty()) {
        println(USAGE)
        return@runBlocking
    }

    val filePath = args[0]
    val doAnalyze = args.any { it == "--analyze" }
    val doReport = args.any { it == "--report" }
    val comparePath = argValue(args, "--compare")
    val vendor = argValue(args, "--vendor") ?: "openai"
    val model = argValue(args, "--model")
    val outputPath = argValue(args, "--output")

    val file = File(filePath)
    if (!file.exists()) {
        System.err.println("File not found: $filePath")
        return@runBlocking
    }

    val trace = TraceLoader.loadFromFile(file)
    val comparison = comparePath?.let { path ->
        val prevFile = File(path)
        if (!prevFile.exists()) {
            System.err.println("Comparison file not found: $path")
            null
        } else {
            val prevTrace = TraceLoader.loadFromFile(prevFile)
            TraceStats.compareTraces(trace, prevTrace)
        }
    }

    if (doReport) {
        runReport(trace, vendor, model, outputPath, comparison)
        return@runBlocking
    }

    println("═══════════════════════════════════════════════════════")
    println("  BabelFit Trace Viewer")
    println("═══════════════════════════════════════════════════════")
    println("  Loading: ${file.name}")
    println()

    val roots = TraceLoader.buildSpanTree(trace)
    val stats = TraceStats.computeStats(trace)

    printStats(stats)
    if (comparison != null) printComparison(comparison)
    printSpanTree(roots)

    if (doAnalyze) {
        runAnalysis(trace, vendor, model)
    } else {
        println("  Tip: Add --analyze or --report flag for AI-powered analysis")
    }
}

private fun argValue(args: Array<String>, flag: String): String? {
    val idx = args.indexOf(flag)
    return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
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
    println("  Successes:  ${stats.successCount}")
    println("  Failures:   ${stats.failureCount}")
    println("  Retried:    ${stats.retriedRequestCount}")
    println("  Dup Prompts:${stats.duplicatePromptCount}")
    println()
}

private fun printComparison(comparison: TraceComparison) {
    println("  ── Comparison vs Previous ──────────────────────────")
    println("  %-20s %-12s %-12s %s".format("Metric", "Previous", "Current", "Delta"))
    println("  ${"─".repeat(60)}")
    for (delta in comparison.deltas) {
        val marker = if (delta.improved) "+" else if (delta.delta.startsWith("+0") || delta.delta.startsWith("0")) "=" else "-"
        println("  %-20s %-12s %-12s %s %s".format(delta.metric, delta.previous, delta.current, marker, delta.delta))
    }
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

        val typeTag = spanTypeIcon(span)

        println("$indent$typeTag ${span.name} ($duration)$tokens")
    }
    println()
}

private fun spanTypeIcon(span: TraceSpan): String = when (span.type) {
    SpanType.SESSION -> "⬢"
    SpanType.REQUEST -> "▶"
    SpanType.ATTEMPT -> if (span.error == null) "✓" else "✗"
    SpanType.TOOL_CALL -> "⚙"
    SpanType.AGENT -> "◎"
    SpanType.STEP -> "○"
}

private suspend fun runAnalysis(trace: TraceExport, vendor: String, model: String?) {
    println("  ── AI Analysis ─────────────────────────────────────")
    println("  Running analysis...")

    val adapter = createAdapter(vendor, model)
    val analysisSession = TraceAnalysisSession(apiAdapter = adapter)

    val analysis = analysisSession.analyze(trace)
    printAnalysis(analysis)
}

private suspend fun runReport(
    trace: TraceExport,
    vendor: String,
    model: String?,
    outputPath: String?,
    comparison: TraceComparison?
) {
    System.err.println("Generating report...")
    val adapter = createAdapter(vendor, model)
    val session = TraceAnalysisSession(apiAdapter = adapter)
    val report = session.generateReport(trace)

    val fullReport = if (comparison != null) {
        report.markdownSummary + "\n" + TraceReportFormatter.formatComparison(comparison)
    } else {
        report.markdownSummary
    }

    if (outputPath != null) {
        File(outputPath).also { it.parentFile?.mkdirs() }.writeText(fullReport)
        System.err.println("Report written to $outputPath")
    } else {
        println(fullReport)
    }
}

private fun createAdapter(vendor: String, model: String?): ApiAdapter {
    return when (vendor.lowercase()) {
        "openai" -> OpenAiAdapter(model = ChatModel.of(model ?: "gpt-5-mini"))
        "anthropic" -> ClaudeAdapter(model = model ?: "claude-sonnet-4-6")
        "gemini" -> GeminiAdapter(model = model ?: "gemini-2.5-flash")
        else -> error("Unknown vendor: $vendor. Use openai, anthropic, or gemini.")
    }
}

private fun printAnalysis(analysis: TraceAnalysis) {
    println()
    println("  Summary: ${analysis.summary}")
    println()

    val eff = analysis.tokenEfficiency
    println("  Token Efficiency:")
    println("    Input:  ${TraceStats.formatTokens(eff.totalInputTokens)}")
    println("    Output: ${TraceStats.formatTokens(eff.totalOutputTokens)}")
    println("    Failed: ${TraceStats.formatTokens(eff.failedCallTokens)}")
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
