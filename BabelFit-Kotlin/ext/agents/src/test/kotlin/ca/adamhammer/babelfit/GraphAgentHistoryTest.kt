package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.agents.*
import ca.adamhammer.babelfit.agents.graph.AgentGraph
import ca.adamhammer.babelfit.agents.graph.GraphAgent
import ca.adamhammer.babelfit.agents.graph.GraphAgentHistoryInterceptor
import ca.adamhammer.babelfit.agents.graph.agentGraph
import ca.adamhammer.babelfit.model.MessageRole
import ca.adamhammer.babelfit.test.MockAdapter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GraphAgentHistoryTest {

    @Test
    fun `history interceptor injects previous step messages into subsequent steps`() {
        // Linear deterministic graph: analyze → plan → act
        val graph = agentGraph<AutonomousAIApi> {
            start("analyze")
            from("analyze").to("plan")
            from("plan").to("act")
            terminal("act")
        }

        val historyInterceptor = GraphAgentHistoryInterceptor()
        val apiMock = MockAdapter.scripted("analysis result", "plan result", "final result")
        val apiInstance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(apiMock)
            .addInterceptor(historyInterceptor)
            .build()

        val deciderMock = MockAdapter.scripted(AiDecision("unused"))
        val decider = BabelFitBuilder(DecidingAgentAPI::class)
            .setAdapterDirect(deciderMock)
            .build().api

        val agent = GraphAgent(apiInstance, decider, graph, historyInterceptor = historyInterceptor)
        agent.run(maxSteps = 10)

        // Step 1 (analyze): no history
        val ctx1 = apiMock.contextAt(0)
        assertTrue(ctx1.conversationHistory.isEmpty(), "First step should have no history")

        // Step 2 (plan): should have history from step 1
        val ctx2 = apiMock.contextAt(1)
        assertEquals(2, ctx2.conversationHistory.size, "Second step should have 2 history messages (user + assistant)")
        assertEquals(MessageRole.USER, ctx2.conversationHistory[0].role)
        assertEquals(MessageRole.ASSISTANT, ctx2.conversationHistory[1].role)
        assertTrue(ctx2.conversationHistory[1].textContent.contains("analysis result"))

        // Step 3 (act): should have history from steps 1 and 2
        val ctx3 = apiMock.contextAt(2)
        assertEquals(4, ctx3.conversationHistory.size, "Third step should have 4 history messages")
        assertTrue(ctx3.conversationHistory[3].textContent.contains("plan result"))
    }

    @Test
    fun `without history interceptor no history is injected`() {
        val graph = agentGraph<AutonomousAIApi> {
            start("analyze")
            from("analyze").to("plan")
            from("plan").to("act")
            terminal("act")
        }

        val apiMock = MockAdapter.scripted("analysis", "plan", "done")
        val apiInstance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(apiMock)
            .build()

        val deciderMock = MockAdapter.scripted(AiDecision("unused"))
        val decider = BabelFitBuilder(DecidingAgentAPI::class)
            .setAdapterDirect(deciderMock)
            .build().api

        val agent = GraphAgent(apiInstance, decider, graph)
        agent.run(maxSteps = 10)

        // No history should be injected in any step
        for (i in 0 until apiMock.callCount) {
            assertTrue(apiMock.contextAt(i).conversationHistory.isEmpty(),
                "Step $i should have no history without interceptor")
        }
    }

    @Test
    fun `reset clears accumulated history`() {
        val graph = agentGraph<AutonomousAIApi> {
            start("analyze")
            from("analyze").to("act")
            terminal("act")
        }

        val historyInterceptor = GraphAgentHistoryInterceptor()
        val apiMock = MockAdapter.scripted("first", "done", "second", "done2")
        val apiInstance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(apiMock)
            .addInterceptor(historyInterceptor)
            .build()

        val deciderMock = MockAdapter.scripted(AiDecision("unused"))
        val decider = BabelFitBuilder(DecidingAgentAPI::class)
            .setAdapterDirect(deciderMock)
            .build().api

        val agent = GraphAgent(apiInstance, decider, graph, historyInterceptor = historyInterceptor)

        // First run: 2 steps (analyze + act) = 2 exchanges = 4 messages
        agent.run(maxSteps = 10)
        assertEquals(4, historyInterceptor.messages.size)

        // Reset and run again
        agent.reset()
        assertTrue(historyInterceptor.messages.isEmpty(), "History should be cleared after reset")

        agent.run(maxSteps = 10)
        // After second run, step 1 should have no history
        val ctx3 = apiMock.contextAt(2) // 3rd call overall = first step of second run
        assertTrue(ctx3.conversationHistory.isEmpty(), "First step after reset should have no history")
    }

    @Test
    fun `maxMessages limits history size`() {
        val graph = agentGraph<AutonomousAIApi> {
            start("analyze")
            from("analyze").to("plan")
            from("plan").to("act")
            terminal("act")
        }

        // Only keep last 1 exchange (2 messages)
        val historyInterceptor = GraphAgentHistoryInterceptor(maxMessages = 1)
        val apiMock = MockAdapter.scripted("analysis", "plan", "done")
        val apiInstance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(apiMock)
            .addInterceptor(historyInterceptor)
            .build()

        val deciderMock = MockAdapter.scripted(AiDecision("unused"))
        val decider = BabelFitBuilder(DecidingAgentAPI::class)
            .setAdapterDirect(deciderMock)
            .build().api

        val agent = GraphAgent(apiInstance, decider, graph, historyInterceptor = historyInterceptor)
        agent.run(maxSteps = 10)

        // Step 3 (act): should only have last 1 exchange (from step 2), not step 1
        val ctx3 = apiMock.contextAt(2)
        assertEquals(2, ctx3.conversationHistory.size, "Should have exactly 2 messages (1 exchange)")
        assertTrue(ctx3.conversationHistory[1].textContent.contains("plan"),
            "Should contain the most recent step's result")
    }
}
