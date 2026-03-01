package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.model.Message
import ca.adamhammer.babelfit.model.MessageRole
import ca.adamhammer.babelfit.model.PromptContext

class ConversationHistoryInterceptor : Interceptor {
    private val history = mutableListOf<Message>()

    fun addUserMessage(content: String) {
        history.add(Message(MessageRole.USER, content))
    }

    fun addAssistantResponse(content: String) {
        history.add(Message(MessageRole.ASSISTANT, content))
    }

    override fun intercept(context: PromptContext): PromptContext =
        context.copy(conversationHistory = context.conversationHistory + history)
}
