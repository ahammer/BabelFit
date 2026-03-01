package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.interfaces.SlidingWindowConversation
import ca.adamhammer.babelfit.interfaces.UnboundedConversation
import ca.adamhammer.babelfit.model.Message
import ca.adamhammer.babelfit.model.MessageRole
import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.test.MockAdapter
import ca.adamhammer.babelfit.test.SimpleResult
import ca.adamhammer.babelfit.test.SimpleTestAPI
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConversationManagerTest {

    // ── Unit tests for ConversationManager implementations ─────────────────

    @Test
    fun `UnboundedConversation keeps all messages`() {
        val cm = UnboundedConversation()
        repeat(100) { cm.addUserMessage("msg $it") }

        assertEquals(100, cm.getHistory().size)
    }

    @Test
    fun `SlidingWindowConversation keeps only last N messages`() {
        val cm = SlidingWindowConversation(5)
        repeat(10) { cm.addUserMessage("msg $it") }

        val history = cm.getHistory()
        assertEquals(5, history.size)
        assertEquals("msg 5", history[0].textContent)
        assertEquals("msg 9", history[4].textContent)
    }

    @Test
    fun `SlidingWindowConversation with window of 1`() {
        val cm = SlidingWindowConversation(1)
        cm.addUserMessage("first")
        cm.addUserMessage("second")

        assertEquals(1, cm.getHistory().size)
        assertEquals("second", cm.getHistory()[0].textContent)
    }

    @Test
    fun `clear removes all history`() {
        val cm = UnboundedConversation()
        cm.addUserMessage("hello")
        cm.addAssistantMessage("hi")
        cm.clear()

        assertTrue(cm.getHistory().isEmpty())
    }

    @Test
    fun `intercept appends history to context`() {
        val cm = UnboundedConversation()
        cm.addUserMessage("turn 1")
        cm.addAssistantMessage("response 1")

        val context = PromptContext(
            systemInstructions = "test",
            methodInvocation = "method()",
            memory = emptyMap()
        )

        val result = cm.intercept(context)
        assertEquals(2, result.conversationHistory.size)
        assertEquals(MessageRole.USER, result.conversationHistory[0].role)
        assertEquals(MessageRole.ASSISTANT, result.conversationHistory[1].role)
    }

    @Test
    fun `intercept preserves existing conversation history`() {
        val cm = UnboundedConversation()
        cm.addUserMessage("turn 2")

        val existing = Message(MessageRole.USER, "turn 1")
        val context = PromptContext(
            systemInstructions = "test",
            methodInvocation = "method()",
            memory = emptyMap(),
            conversationHistory = listOf(existing)
        )

        val result = cm.intercept(context)
        assertEquals(2, result.conversationHistory.size)
        assertEquals("turn 1", result.conversationHistory[0].textContent)
        assertEquals("turn 2", result.conversationHistory[1].textContent)
    }

    @Test
    fun `SlidingWindowConversation rejects non-positive maxMessages`() {
        assertThrows(IllegalArgumentException::class.java) {
            SlidingWindowConversation(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SlidingWindowConversation(-1)
        }
    }

    // ── Integration: conversation DSL wiring ──────────────────────────────

    @Test
    fun `conversation DSL configures sliding window`() {
        val mock = MockAdapter.scripted(SimpleResult("result"))
        val instance = babelFit<SimpleTestAPI> {
            adapter(mock)
            conversation { maxMessages = 10 }
        }

        instance.api.get().get()

        // After the call, the conversation manager should have recorded the exchange
        val context = mock.lastContext!!
        // First call has no prior history
        assertTrue(context.conversationHistory.isEmpty())
    }

    @Test
    fun `conversation history grows across calls`() {
        val mock = MockAdapter.scripted(
            SimpleResult("first"),
            SimpleResult("second")
        )
        val instance = babelFit<SimpleTestAPI> {
            adapter(mock)
            conversation { maxMessages = 20 }
        }

        instance.api.get().get()
        instance.api.get().get()

        // Second call should see history from the first call
        val context = mock.lastContext!!
        assertEquals(2, context.conversationHistory.size)
    }

    @Test
    fun `unbounded conversation via DSL`() {
        val mock = MockAdapter.scripted(SimpleResult("r1"), SimpleResult("r2"), SimpleResult("r3"))
        val instance = babelFit<SimpleTestAPI> {
            adapter(mock)
            conversation { /* maxMessages = 0 means unbounded */ }
        }

        instance.api.get().get()
        instance.api.get().get()
        instance.api.get().get()

        val context = mock.lastContext!!
        assertEquals(4, context.conversationHistory.size) // 2 messages from each of 2 prior calls
    }

    @Test
    fun `conversation manager instance can be provided directly`() {
        val cm = SlidingWindowConversation(4)
        val mock = MockAdapter.scripted(SimpleResult("r1"), SimpleResult("r2"), SimpleResult("r3"))
        val instance = babelFit<SimpleTestAPI> {
            adapter(mock)
            conversation(cm)
        }

        instance.api.get().get()
        instance.api.get().get()
        instance.api.get().get()

        // Window of 4: should keep last 4 messages (2 from call 2, 2 from call 3)
        assertEquals(4, cm.getHistory().size)
    }
}
