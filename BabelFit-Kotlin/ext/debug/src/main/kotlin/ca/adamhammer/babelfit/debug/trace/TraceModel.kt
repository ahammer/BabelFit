package ca.adamhammer.babelfit.debug.trace

import kotlinx.serialization.Serializable

@Serializable
data class TraceExport(
    val version: String = "1.0",
    val spans: List<TraceSpan>
)

@Serializable
enum class SpanType {
    SESSION, 
    AGENT, 
    STEP, 
    REQUEST, 
    ATTEMPT, 
    TOOL_CALL
}

@Serializable
data class TraceSpan(
    val id: String,
    val type: SpanType,
    val parentId: String? = null,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val name: String,
    val description: String? = null,
    val error: SpanError? = null,
    val metadata: Map<String, String>? = null,
    val usage: SpanUsage? = null,
    // Context payload fields tailored to the span type
    val messages: List<SpanMessage>? = null,
    val schema: String? = null,
    val requestInput: String? = null,
    val responseOutput: String? = null
)

@Serializable
data class SpanMessage(
    val role: String,
    val content: String
)

@Serializable
data class SpanError(
    val type: String,
    val message: String,
    val stackTrace: String? = null
)

@Serializable
data class SpanUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val estimatedCost: Double = 0.0,
    val model: String? = null
) {
    operator fun plus(other: SpanUsage) = SpanUsage(
        inputTokens = inputTokens + other.inputTokens,
        outputTokens = outputTokens + other.outputTokens,
        estimatedCost = estimatedCost + other.estimatedCost,
        model = model ?: other.model
    )
}
