package ca.adamhammer.babelfit.model

/**
 * Role for a message in a conversation history.
 */
/** Role for a participant in a conversation: SYSTEM prompt, USER input, or ASSISTANT response. */
enum class MessageRole { SYSTEM, USER, ASSISTANT }

/**
 * A part of a message's content. Messages can be composed of one or more parts,
 * supporting text, images, and other media types.
 */
sealed interface ContentPart

/** Plain text content. */
data class TextPart(val text: String) : ContentPart

/** Base64-encoded image content. */
data class ImagePart(val base64: String, val mediaType: String) : ContentPart

/**
 * A single message in a conversation history, supporting multimodal content.
 *
 * For backward compatibility, [Message] can be constructed with a plain [String],
 * which is automatically wrapped in a [TextPart]:
 * ```kotlin
 * Message(MessageRole.USER, "Hello")           // text-only convenience
 * Message(MessageRole.USER, listOf(            // multimodal
 *     TextPart("Describe this image:"),
 *     ImagePart(base64Data, "image/png")
 * ))
 * ```
 */
data class Message(
    val role: MessageRole,
    val content: List<ContentPart>
) {
    /** Convenience constructor for text-only messages. */
    constructor(role: MessageRole, text: String) : this(role, listOf(TextPart(text)))

    /** Returns all text parts concatenated, or empty string if none. */
    val textContent: String
        get() = content.filterIsInstance<TextPart>().joinToString("") { it.text }
}

/**
 * Type-safe key for storing values in [PromptContext.properties].
 *
 * Usage:
 * ```kotlin
 * val SESSION_ID = TypedKey<String>("sessionId")
 * val ctx = context.with(SESSION_ID, "abc-123")
 * val id: String? = ctx[SESSION_ID]
 * ```
 */
class TypedKey<T>(val name: String) {
    override fun equals(other: Any?) = other is TypedKey<*> && name == other.name
    override fun hashCode() = name.hashCode()
    override fun toString() = "TypedKey($name)"
}

/**
 * Estimates the number of tokens in a text string.
 * Implementations can use vendor-specific tokenizers for accuracy, or the
 * default heuristic (1 token ≈ 4 characters).
 */
fun interface TokenEstimator {
    fun estimate(text: String): Int

    companion object {
        /** Default estimator: 1 token per 4 characters. Reasonable for English text. */
        val DEFAULT = TokenEstimator { text -> (text.length + 3) / 4 }
    }
}

/**
 * A named, ordered section of system instructions.
 * Parts are deduplicated by [key] and ordered by [priority] when compiled.
 */
data class PromptPart(
    /** Unique identity — last-write-wins for same key. */
    val key: String,
    /** Lower priority = earlier in the compiled prompt. Default 500 (middle). */
    val priority: Int = DEFAULT_PRIORITY,
    /** The text content of this part. */
    val content: String
) : Comparable<PromptPart> {
    override fun compareTo(other: PromptPart): Int =
        compareValuesBy(this, other, { it.priority }, { it.key })

    companion object {
        const val DEFAULT_PRIORITY = 500

        // Well-known priority tiers
        const val PREAMBLE = 100      // Core system rules (DefaultContextBuilder)
        const val IDENTITY = 200      // Who the AI is (CharacterInterceptor)
        const val KNOWLEDGE = 300     // Domain knowledge (CompanyContext, WorldState)
        const val DOCUMENT = 400      // Current document/state
        const val RULES = 500         // Operation-specific rules
        const val WORKFLOW = 600      // Agent workflow state
        const val TURN_STATE = 700    // Turn/step progress
        const val HINTS = 800         // Ephemeral hints, warnings
    }
}

/**
 * The assembled context that will be sent to an AI adapter.
 * Built by a [ca.adamhammer.babelfit.interfaces.ContextBuilder] and
 * optionally modified by [ca.adamhammer.babelfit.interfaces.Interceptor]s.
 */
data class PromptContext(
    val parts: List<PromptPart> = emptyList(),
    val methodInvocation: String = "",
    val memory: Map<String, String> = emptyMap(),
    val properties: Map<String, Any> = emptyMap(),
    val availableTools: List<ToolDefinition> = emptyList(),
    val conversationHistory: List<Message> = emptyList(),
    /** The name of the proxy method that initiated this request. Useful for routing. */
    val methodName: String = ""
) {
    /**
     * Backward compatibility constructor for old callers.
     * Maps the single flat string into a legacy `PromptPart`.
     */
    @Deprecated(
        "Use the parts constructor instead",
        ReplaceWith(
            "PromptContext(parts = listOf(PromptPart(\"legacy\", PromptPart.PREAMBLE, systemInstructions)), " +
                "methodInvocation, memory, properties, availableTools, conversationHistory, methodName)"
        )
    )
    constructor(
        systemInstructions: String,
        methodInvocation: String = "",
        memory: Map<String, String> = emptyMap(),
        properties: Map<String, Any> = emptyMap(),
        availableTools: List<ToolDefinition> = emptyList(),
        conversationHistory: List<Message> = emptyList(),
        methodName: String = ""
    ) : this(
        parts = listOf(PromptPart("legacy", PromptPart.PREAMBLE, systemInstructions)),
        methodInvocation = methodInvocation,
        memory = memory,
        properties = properties,
        availableTools = availableTools,
        conversationHistory = conversationHistory,
        methodName = methodName
    )

    /**
     * Compile parts into final system instructions.
     * - Deduplicate by key (last-write-wins)
     * - Sort by priority (ascending = earlier in prompt)
     * - Join with section separators
     */
    val systemInstructions: String
        get() = compile()

    /**
     * Compile parts into final system instructions with an optional token budget.
     *
     * When [maxTokens] is null, all parts are included (same as [systemInstructions]).
     * When set, parts are dropped from highest priority number (least important) first
     * until the compiled text fits within the budget. Parts at [PromptPart.PREAMBLE]
     * priority (100) are never dropped.
     *
     * @param maxTokens maximum token budget, or null for unlimited
     * @param estimator token estimation strategy (default: 1 token per 4 chars)
     * @return compiled system instructions string
     */
    fun compile(
        maxTokens: Int? = null,
        estimator: TokenEstimator = TokenEstimator.DEFAULT
    ): String {
        val deduplicated = parts
            .groupBy { it.key }
            .mapValues { (_, dups) -> dups.last() }
            .values
            .sorted()

        if (maxTokens == null) {
            return deduplicated.joinToString("\n\n") { it.content.trim() }
        }

        // Start with all parts, drop highest-priority-number (least important) first
        val included = deduplicated.toMutableList()
        while (included.isNotEmpty()) {
            val compiled = included.joinToString("\n\n") { it.content.trim() }
            if (estimator.estimate(compiled) <= maxTokens) {
                return compiled
            }
            // Find the least important part (highest priority number) that isn't PREAMBLE
            val candidate = included.lastOrNull { it.priority > PromptPart.PREAMBLE }
                ?: break // only PREAMBLE parts left — keep them all
            included.remove(candidate)
        }

        // Over budget but only PREAMBLE parts remain — return what we have
        return included.joinToString("\n\n") { it.content.trim() }
    }

    /** Read a typed property, returning null if absent or of the wrong type. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: TypedKey<T>): T? = properties[key.name] as? T

    /** Return a copy with the given typed property set. */
    fun <T : Any> with(key: TypedKey<T>, value: T): PromptContext =
        copy(properties = properties + (key.name to value))

    /** Add or replace a part by key. */
    fun withPart(part: PromptPart): PromptContext =
        copy(parts = parts + part)

    /** Add or replace a part using convenience params. */
    fun withPart(key: String, priority: Int = PromptPart.DEFAULT_PRIORITY, content: String): PromptContext =
        withPart(PromptPart(key, priority, content))
}
