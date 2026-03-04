package ca.adamhammer.babelfit.debug.trace

import ca.adamhammer.babelfit.model.TypedKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

val PARENT_SPAN_ID_KEY = TypedKey<String>("btraceParentSpanId")

class TraceSession(
    private val name: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")),
    private val baseDir: String = "debug"
) {
    private val spans = ConcurrentHashMap<String, TraceSpan>()
    private val sessionId: String = UUID.randomUUID().toString()
    private val startTimeMs: Long = System.currentTimeMillis()
    private val json = Json { prettyPrint = true }

    init {
        // Create root session span
        val rootSpan = TraceSpan(
            id = sessionId,
            type = SpanType.SESSION,
            name = "Session: $name",
            startTimeMs = startTimeMs
        )
        spans[sessionId] = rootSpan
    }
    
    fun getSessionId(): String = sessionId

    fun startSpan(
        type: SpanType,
        name: String,
        id: String = UUID.randomUUID().toString(),
        description: String? = null,
        parentId: String? = sessionId,
        startTimeMs: Long = System.currentTimeMillis()
    ): String {
        val span = TraceSpan(
            id = id,
            type = type,
            parentId = parentId,
            startTimeMs = startTimeMs,
            name = name,
            description = description
        )
        spans[id] = span
        return id
    }

    fun completeSpan(
        id: String,
        endTimeMs: Long = System.currentTimeMillis(),
        usage: SpanUsage? = null,
        schema: String? = null,
        requestInput: String? = null,
        responseOutput: String? = null,
        messages: List<SpanMessage>? = null
    ) {
        spans.computeIfPresent(id) { _, span ->
            span.copy(
                endTimeMs = endTimeMs,
                usage = usage ?: span.usage,
                schema = schema ?: span.schema,
                requestInput = requestInput ?: span.requestInput,
                responseOutput = responseOutput ?: span.responseOutput,
                messages = messages ?: span.messages
            )
        }
    }

    fun failSpan(
        id: String,
        error: Exception,
        endTimeMs: Long = System.currentTimeMillis()
    ) {
        spans.computeIfPresent(id) { _, span ->
            span.copy(
                endTimeMs = endTimeMs,
                error = SpanError(
                    type = error.javaClass.simpleName,
                    message = error.message ?: "Unknown error",
                    stackTrace = error.stackTraceToString()
                )
            )
        }
    }

    fun save() {
        val dir = File(baseDir)
        if (!dir.exists()) dir.mkdirs()
        save(File(dir, "$name.btrace.json"))
    }

    fun save(file: File) {
        val endMs = System.currentTimeMillis()

        // Roll up child span metrics into the SESSION span
        spans.computeIfPresent(sessionId) { _, span ->
            val allSpans = spans.values

            // Aggregate usage from ATTEMPT spans (leaf-level LLM calls)
            val attemptSpans = allSpans.filter { it.type == SpanType.ATTEMPT }
            val aggregatedUsage = attemptSpans
                .mapNotNull { it.usage }
                .fold(SpanUsage()) { acc, u -> acc + u }

            // Compute active LLM time (sum of ATTEMPT durations) vs wall time
            val activeLlmTimeMs = attemptSpans.sumOf { a ->
                val end = a.endTimeMs ?: endMs
                end - a.startTimeMs
            }
            val wallTimeMs = endMs - startTimeMs

            // Count resilience metrics
            val requestCount = allSpans.count { it.type == SpanType.REQUEST }
            val attemptCount = attemptSpans.size
            val errorCount = attemptSpans.count { it.error != null }
            val toolCallCount = allSpans.count { it.type == SpanType.TOOL_CALL }
            val retryCount = (attemptCount - requestCount).coerceAtLeast(0)

            val metadata = buildMap {
                put("wallTimeMs", wallTimeMs.toString())
                put("activeLlmTimeMs", activeLlmTimeMs.toString())
                put("totalRequests", requestCount.toString())
                put("totalAttempts", attemptCount.toString())
                put("retryCount", retryCount.toString())
                put("errorCount", errorCount.toString())
                put("toolCallCount", toolCallCount.toString())
            }

            span.copy(
                endTimeMs = if (span.endTimeMs == null) endMs else span.endTimeMs,
                usage = if (aggregatedUsage.inputTokens > 0 || aggregatedUsage.outputTokens > 0)
                    aggregatedUsage else span.usage,
                metadata = (span.metadata.orEmpty()) + metadata
            )
        }

        val export = TraceExport(
            spans = spans.values.sortedBy { it.startTimeMs }
        )

        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        file.writeText(json.encodeToString(export))
    }
}
