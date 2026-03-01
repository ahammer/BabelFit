package ca.adamhammer.babelfit.samples.traceviewer.models

import ca.adamhammer.babelfit.annotations.AiSchema
import kotlinx.serialization.Serializable

@Serializable
@AiSchema(
    title = "TraceAnalysis",
    description = "Complete analysis of an LLM interaction trace including weaknesses and improvement suggestions"
)
data class TraceAnalysis(
    val summary: String,
    val weaknesses: List<Weakness>,
    val suggestions: List<PromptSuggestion>,
    val tokenEfficiency: TokenEfficiencyReport
)

@Serializable
enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
enum class WeaknessCategory {
    PROMPT_QUALITY,
    TOKEN_WASTE,
    ERROR_HANDLING,
    SCHEMA_DESIGN,
    RETRY_OVERHEAD,
    TOOL_USAGE
}

@Serializable
@AiSchema(
    title = "Weakness",
    description = "An identified weakness or issue in the trace"
)
data class Weakness(
    val description: String,
    val severity: Severity,
    val category: WeaknessCategory,
    val spanId: String = ""
)

@Serializable
@AiSchema(
    title = "PromptSuggestion",
    description = "A suggestion for improving prompts or schemas found in the trace"
)
data class PromptSuggestion(
    val targetArea: String,
    val currentApproach: String,
    val suggestedImprovement: String,
    val rationale: String,
    val estimatedImpact: String
)

@Serializable
@AiSchema(
    title = "TokenEfficiencyReport",
    description = "Token usage efficiency analysis"
)
data class TokenEfficiencyReport(
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val failedCallTokens: Long,
    val estimatedWastePercent: Int
)

@Serializable
enum class Quality { GOOD, NEEDS_IMPROVEMENT, POOR }

@Serializable
enum class GuidanceTarget {
    ANNOTATION,
    INTERCEPTOR,
    SCHEMA,
    RESILIENCE,
    TOOL,
    CONVERSATION
}

@Serializable
@AiSchema(
    title = "PromptIssue",
    description = "A specific issue found in the prompt or schema for a span"
)
data class PromptIssue(
    val area: String,
    val issue: String,
    val impact: String
)

@Serializable
@AiSchema(
    title = "CodeGuidance",
    description = """Actionable guidance for fixing BabelFit application code.
        Target the Kotlin code that generates the prompt (annotations, interceptors, schema classes),
        not the raw LLM prompt text. Reference specific BabelFit concepts."""
)
data class CodeGuidance(
    val target: GuidanceTarget,
    val currentBehavior: String,
    val suggestedChange: String,
    val rationale: String,
    val exampleCode: String = ""
)

@Serializable
@AiSchema(
    title = "SpanAssessment",
    description = """Assessment of a single trace span. Evaluate the quality of the LLM interaction,
        identify prompt issues, and provide BabelFit-aware code guidance for improvements."""
)
data class SpanAssessment(
    val quality: Quality,
    val observations: List<String>,
    val promptIssues: List<PromptIssue>,
    val codeGuidance: List<CodeGuidance>
)

@Serializable
@AiSchema(
    title = "AgentPrompt",
    description = """An informed prompt that a human developer or AI coding agent can use to fix
        the BabelFit application code. Should reference specific annotations, interceptor patterns,
        schema design, and resilience configuration. Should be actionable and self-contained."""
)
data class AgentPrompt(
    val prompt: String,
    val keyChanges: List<String>
)

data class TraceReport(
    val sessionAnalysis: TraceAnalysis,
    val spanAssessments: Map<String, SpanAssessment>,
    val agentPrompt: AgentPrompt,
    val markdownSummary: String
)
