package ca.adamhammer.babelfit.interfaces

import ca.adamhammer.babelfit.model.ContentPart
import ca.adamhammer.babelfit.model.Message
import ca.adamhammer.babelfit.model.MessageRole
import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.model.TextPart

/**
 * Manages conversation history for multi-turn interactions.
 *
 * Implementations are automatically wired as interceptors when registered via
 * [ca.adamhammer.babelfit.BabelFitBuilder.conversation]. The conversation
 * manager injects history into the [PromptContext] before each request and
 * records the exchange after each response.
 */
interface ConversationManager : Interceptor {

    /** Add a user message to the history. */
    fun addUserMessage(text: String) = addMessage(Message(MessageRole.USER, text))

    /** Add a user message with multimodal content. */
    fun addUserMessage(content: List<ContentPart>) = addMessage(Message(MessageRole.USER, content))

    /** Add an assistant response to the history. */
    fun addAssistantMessage(text: String) = addMessage(Message(MessageRole.ASSISTANT, text))

    /** Add an assistant response with multimodal content. */
    fun addAssistantMessage(content: List<ContentPart>) = addMessage(Message(MessageRole.ASSISTANT, content))

    /** Add an arbitrary message to the history. */
    fun addMessage(message: Message)

    /** Return the current conversation history. */
    fun getHistory(): List<Message>

    /** Clear all conversation history. */
    fun clear()

    override fun intercept(context: PromptContext): PromptContext =
        context.copy(conversationHistory = context.conversationHistory + getHistory())
}

/**
 * Conversation manager that keeps all messages without any limit.
 */
class UnboundedConversation : ConversationManager {
    private val history = mutableListOf<Message>()

    override fun addMessage(message: Message) { history.add(message) }
    override fun getHistory(): List<Message> = history.toList()
    override fun clear() { history.clear() }
}

/**
 * Conversation manager that retains only the most recent [maxMessages] messages.
 *
 * When the history exceeds the limit, the oldest messages are dropped.
 */
class SlidingWindowConversation(private val maxMessages: Int) : ConversationManager {
    init { require(maxMessages > 0) { "maxMessages must be positive, got $maxMessages" } }

    private val history = mutableListOf<Message>()

    override fun addMessage(message: Message) {
        history.add(message)
        while (history.size > maxMessages) {
            history.removeFirst()
        }
    }

    override fun getHistory(): List<Message> = history.toList()
    override fun clear() { history.clear() }
}
