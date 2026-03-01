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
 * The assembled context that will be sent to an AI adapter.
 * Built by a [ca.adamhammer.babelfit.interfaces.ContextBuilder] and
 * optionally modified by [ca.adamhammer.babelfit.interfaces.Interceptor]s.
 */
data class PromptContext(
    val systemInstructions: String,
    val methodInvocation: String,
    val memory: Map<String, String>,
    val properties: Map<String, Any> = emptyMap(),
    val availableTools: List<ToolDefinition> = emptyList(),
    val conversationHistory: List<Message> = emptyList(),
    /** The name of the proxy method that initiated this request. Useful for routing. */
    val methodName: String = ""
) {
    /** Read a typed property, returning null if absent or of the wrong type. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: TypedKey<T>): T? = properties[key.name] as? T

    /** Return a copy with the given typed property set. */
    fun <T : Any> with(key: TypedKey<T>, value: T): PromptContext =
        copy(properties = properties + (key.name to value))
}
