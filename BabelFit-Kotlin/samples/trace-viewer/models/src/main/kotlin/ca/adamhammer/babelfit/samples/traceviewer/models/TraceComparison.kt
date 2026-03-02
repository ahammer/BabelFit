package ca.adamhammer.babelfit.samples.traceviewer.models

import kotlinx.serialization.Serializable

@Serializable
data class TraceComparison(
    val current: TraceComparisonStats,
    val previous: TraceComparisonStats,
    val deltas: List<MetricDelta>
)

@Serializable
data class TraceComparisonStats(
    val totalSpans: Int,
    val totalDurationMs: Long,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val errorCount: Int,
    val retriedRequestCount: Int,
    val successCount: Int,
    val avgCallDurationMs: Long,
    val duplicatePromptCount: Int
)

@Serializable
data class MetricDelta(
    val metric: String,
    val previous: String,
    val current: String,
    val delta: String,
    val improved: Boolean
)
