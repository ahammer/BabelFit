package ca.adamhammer.babelfit.samples.traceviewer.api

import ca.adamhammer.babelfit.debug.trace.TraceExport
import ca.adamhammer.babelfit.samples.traceviewer.models.AgentPrompt
import ca.adamhammer.babelfit.samples.traceviewer.models.SpanAssessment
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceAnalysis
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceComparison

object TraceReportFormatter {

    fun formatMarkdown(
        analysis: TraceAnalysis,
        assessments: Map<String, SpanAssessment>,
        agentPrompt: AgentPrompt,
        trace: TraceExport
    ): String = buildString {
        appendLine("# BabelFit Trace Analysis Report")
        appendLine()
        appendSummarySection(analysis, trace)
        appendTokenSection(analysis)
        appendWeaknessSection(analysis)
        appendAssessmentSection(assessments, trace)
        appendCodeGuidanceSection(assessments)
        appendSuggestionSection(analysis)
        appendAgentPromptSection(agentPrompt)
    }

    private fun StringBuilder.appendSummarySection(
        analysis: TraceAnalysis,
        trace: TraceExport
    ) {
        val stats = TraceStats.computeStats(trace)
        appendLine("## Summary")
        appendLine()
        appendLine(analysis.summary)
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|--------|-------|")
        appendLine("| Spans | ${stats.totalSpans} |")
        appendLine("| Duration | ${TraceStats.formatDuration(stats.totalDurationMs)} |")
        appendLine("| Tokens In | ${TraceStats.formatTokens(stats.totalInputTokens)} |")
        appendLine("| Tokens Out | ${TraceStats.formatTokens(stats.totalOutputTokens)} |")
        appendLine("| Successes | ${stats.successCount} |")
        appendLine("| Failures | ${stats.failureCount} |")
        appendLine("| Errors | ${stats.errorCount} |")
        appendLine()
    }

    private fun StringBuilder.appendTokenSection(analysis: TraceAnalysis) {
        val eff = analysis.tokenEfficiency
        appendLine("## Token Efficiency")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|--------|-------|")
        appendLine("| Input Tokens | ${TraceStats.formatTokens(eff.totalInputTokens)} |")
        appendLine("| Output Tokens | ${TraceStats.formatTokens(eff.totalOutputTokens)} |")
        appendLine("| Failed Call Tokens | ${TraceStats.formatTokens(eff.failedCallTokens)} |")
        appendLine("| Estimated Waste | ~${eff.estimatedWastePercent}% |")
        appendLine()
    }

    private fun StringBuilder.appendWeaknessSection(analysis: TraceAnalysis) {
        if (analysis.weaknesses.isEmpty()) return
        appendLine("## Weaknesses")
        appendLine()
        analysis.weaknesses.forEach { w ->
            appendLine("- **[${w.severity}]** ${w.category}: ${w.description}")
        }
        appendLine()
    }

    private fun StringBuilder.appendAssessmentSection(
        assessments: Map<String, SpanAssessment>,
        trace: TraceExport
    ) {
        if (assessments.isEmpty()) return
        appendLine("## Per-Span Assessments")
        appendLine()
        assessments.forEach { (spanId, assessment) ->
            val span = trace.spans.firstOrNull { it.id == spanId }
            val label = if (span != null) "${span.type}: ${span.name}" else spanId
            appendLine("### $label — ${assessment.quality}")
            appendLine()
            if (assessment.observations.isNotEmpty()) {
                appendLine("**Observations:**")
                assessment.observations.forEach { appendLine("- $it") }
                appendLine()
            }
            if (assessment.promptIssues.isNotEmpty()) {
                appendLine("**Prompt Issues:**")
                assessment.promptIssues.forEach { issue ->
                    appendLine("- **${issue.area}**: ${issue.issue} (Impact: ${issue.impact})")
                }
                appendLine()
            }
        }
    }

    private fun StringBuilder.appendCodeGuidanceSection(
        assessments: Map<String, SpanAssessment>
    ) {
        val allGuidance = assessments.values.flatMap { it.codeGuidance }
        if (allGuidance.isEmpty()) return
        appendLine("## Code Guidance")
        appendLine()
        allGuidance.forEachIndexed { i, g ->
            appendLine("### ${i + 1}. [${g.target}] ${g.suggestedChange}")
            appendLine()
            appendLine("**Current:** ${g.currentBehavior}")
            appendLine()
            appendLine("**Rationale:** ${g.rationale}")
            if (g.exampleCode.isNotBlank()) {
                appendLine()
                appendLine("```kotlin")
                appendLine(g.exampleCode)
                appendLine("```")
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendSuggestionSection(analysis: TraceAnalysis) {
        if (analysis.suggestions.isEmpty()) return
        appendLine("## Prompt Improvement Suggestions")
        appendLine()
        analysis.suggestions.forEachIndexed { i, s ->
            appendLine("### ${i + 1}. ${s.targetArea}")
            appendLine()
            appendLine("- **Current:** ${s.currentApproach}")
            appendLine("- **Suggested:** ${s.suggestedImprovement}")
            appendLine("- **Rationale:** ${s.rationale}")
            appendLine("- **Impact:** ${s.estimatedImpact}")
            appendLine()
        }
    }

    private fun StringBuilder.appendAgentPromptSection(agentPrompt: AgentPrompt) {
        appendLine("## Agent Prompt")
        appendLine()
        appendLine("Use the following prompt with a coding agent or as a guide for manual fixes:")
        appendLine()
        appendLine("````")
        appendLine(agentPrompt.prompt)
        appendLine("````")
        appendLine()
        if (agentPrompt.keyChanges.isNotEmpty()) {
            appendLine("**Key Changes:**")
            agentPrompt.keyChanges.forEach { appendLine("- $it") }
            appendLine()
        }
    }

    fun formatComparison(comparison: TraceComparison): String = buildString {
        appendLine("## Comparison vs Previous Trace")
        appendLine()
        appendLine("| Metric | Previous | Current | Delta | Trend |")
        appendLine("|--------|----------|---------|-------|-------|")
        for (delta in comparison.deltas) {
            val trend = if (delta.improved) "improved" else if (delta.delta.contains("+0")) "unchanged" else "regressed"
            appendLine("| ${delta.metric} | ${delta.previous} | ${delta.current} | ${delta.delta} | $trend |")
        }
        appendLine()
    }
}
