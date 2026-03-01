package ca.adamhammer.babelfit.samples.customersupport.models

import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.model.Message
import ca.adamhammer.babelfit.model.MessageRole
import ca.adamhammer.babelfit.model.PromptContext

class ConversationHistoryInterceptor : Interceptor {
    private val history = mutableListOf<Message>()

    fun addUserMessage(content: String) {
        history.add(Message(role = MessageRole.USER, content = content))
    }

    fun addAssistantResponse(content: String) {
        history.add(Message(role = MessageRole.ASSISTANT, content = content))
    }

    override fun intercept(context: PromptContext): PromptContext =
        context.copy(conversationHistory = context.conversationHistory + history)
}
