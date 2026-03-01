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
    val retryTokens: Long,
    val estimatedWastePercent: Int
)
