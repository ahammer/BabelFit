package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.agents.*
import ca.adamhammer.babelfit.agents.graph.AgentGraph
import ca.adamhammer.babelfit.agents.graph.GraphAgent
import ca.adamhammer.babelfit.agents.graph.agentGraph
import ca.adamhammer.babelfit.annotations.Terminal
import ca.adamhammer.babelfit.annotations.Transitions
import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.AiResponse
import ca.adamhammer.babelfit.annotations.AiSchema
import ca.adamhammer.babelfit.test.MockAdapter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.Future

// ── Test interface with @Transitions annotations ──────────────────────

@AiSchema(description = "Annotated graph agent")
interface AnnotatedGraphApi {
    @AiOperation(description = "Observe the situation")
    @AiResponse(description = "Observation", responseClass = String::class)
    @Transitions("plan", "act")
    fun observe(): Future<String>

    @AiOperation(description = "Make a plan")
    @AiResponse(description = "Plan", responseClass = String::class)
    @Transitions("act")
    fun plan(): Future<String>

    @AiOperation(description = "Execute action")
    @AiResponse(description = "Action result", responseClass = String::class)
    @Terminal
    fun act(): Future<String>
}

class AgentGraphTest {

    // ── AgentGraph.fromAnnotations ────────────────────────────────────

    @Test
    fun `fromAnnotations builds correct graph from interface`() {
        val graph = AgentGraph.fromAnnotations(AnnotatedGraphApi::class)

        assertEquals("observe", graph.startNode)
        assertEquals(setOf("observe", "plan", "act"), graph.nodes)
        assertEquals(setOf("plan", "act"), graph.validTransitions("observe"))
        assertEquals(setOf("act"), graph.validTransitions("plan"))
        assertEquals(emptySet<String>(), graph.validTransitions("act"))
        assertEquals(setOf("act"), graph.terminalNodes)
    }

    @Test
    fun `fromAnnotations detects terminal nodes`() {
        val graph = AgentGraph.fromAnnotations(AnnotatedGraphApi::class)

        assertFalse(graph.isTerminal("observe"))
        assertFalse(graph.isTerminal("plan"))
        assertTrue(graph.isTerminal("act"))
    }

    @Test
    fun `fromAnnotations identifies deterministic nodes`() {
        val graph = AgentGraph.fromAnnotations(AnnotatedGraphApi::class)

        // observe → plan, act (2 edges, not deterministic)
        assertFalse(graph.isDeterministic("observe"))
        // plan → act (1 edge, deterministic)
        assertTrue(graph.isDeterministic("plan"))
        // act → nothing (terminal, 0 edges)
        assertFalse(graph.isDeterministic("act"))
    }

    @Test
    fun `deterministicNext returns single target or null`() {
        val graph = AgentGraph.fromAnnotations(AnnotatedGraphApi::class)

        assertNull(graph.deterministicNext("observe")) // 2 edges
        assertEquals("act", graph.deterministicNext("plan")) // 1 edge
        assertNull(graph.deterministicNext("act")) // 0 edges
    }

    // ── DSL builder ──────────────────────────────────────────────────

    @Test
    fun `DSL builder creates equivalent graph`() {
        val graph = agentGraph<AnnotatedGraphApi> {
            start("observe")
            from("observe").to("plan", "act")
            from("plan").to("act")
            terminal("act")
        }

        assertEquals("observe", graph.startNode)
        assertEquals(setOf("plan", "act"), graph.validTransitions("observe"))
        assertEquals(setOf("act"), graph.validTransitions("plan"))
        assertTrue(graph.isTerminal("act"))
        assertTrue(graph.isDeterministic("plan"))
    }

    @Test
    fun `DSL builder fails without start node`() {
        assertThrows(IllegalStateException::class.java) {
            agentGraph<AnnotatedGraphApi> {
                from("a").to("b")
                terminal("b")
            }
        }
    }

    // ── toMermaid ────────────────────────────────────────────────────

    @Test
    fun `toMermaid generates valid diagram`() {
        val graph = agentGraph<AnnotatedGraphApi> {
            start("observe")
            from("observe").to("plan", "act")
            from("plan").to("act")
            terminal("act")
        }

        val mermaid = graph.toMermaid()
        assertTrue(mermaid.contains("graph TD"))
        assertTrue(mermaid.contains("observe"))
        assertTrue(mermaid.contains("plan"))
        assertTrue(mermaid.contains("act"))
    }

    // ── GraphAgent ──────────────────────────────────────────────────

    @Test
    fun `GraphAgent runs deterministic linear path without LLM`() {
        // Graph: start → middle → end(@Terminal), all deterministic
        val graph = agentGraph<AutonomousAIApi> {
            start("analyze")
            from("analyze").to("plan")
            from("plan").to("act")
            terminal("act")
        }

        val apiMock = MockAdapter.scripted("analysis", "planning", "done")
        val apiInstance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(apiMock)
            .build()

        // Decider should NOT be called since all edges are deterministic
        val deciderMock = MockAdapter.scripted(AiDecision("unused", emptyList()))
        val decider = BabelFitBuilder(DecidingAgentAPI::class)
            .setAdapterDirect(deciderMock)
            .build().api

        val agent = GraphAgent(apiInstance, decider, graph)
        val result = agent.run(maxSteps = 10)

        assertEquals("act", result.methodName)
        assertTrue(result.isTerminal)
        assertEquals(3, apiMock.callCount) // analyze, plan, act
        assertEquals(0, deciderMock.callCount) // no LLM calls
    }

    @Test
    fun `GraphAgent uses LLM for non-deterministic transitions`() {
        // observe → {plan, act}, plan → act
        val graph = agentGraph<AutonomousAIApi> {
            start("analyze")
            from("analyze").to("plan", "act")
            from("plan").to("act")
            terminal("act")
        }

        val apiMock = MockAdapter.scripted("analysis", "planning", "done")
        val apiInstance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(apiMock)
            .build()

        // LLM chooses "plan" at the branching point
        val deciderMock = MockAdapter.scripted(
            AiDecision("plan", emptyList())
        )
        val decider = BabelFitBuilder(DecidingAgentAPI::class)
            .setAdapterDirect(deciderMock)
            .build().api

        val agent = GraphAgent(apiInstance, decider, graph)
        val result = agent.run(maxSteps = 10)

        assertEquals("act", result.methodName)
        assertTrue(result.isTerminal)
        assertEquals(3, apiMock.callCount) // analyze, plan, act
        assertEquals(1, deciderMock.callCount) // 1 LLM call at branching point
    }

    @Test
    fun `GraphAgent stops at maxSteps`() {
        // Cycle: a → b → a (no terminal)
        val graph = agentGraph<AutonomousAIApi> {
            start("analyze")
            from("analyze").to("plan")
            from("plan").to("analyze")
        }

        val apiMock = MockAdapter.scripted("a", "b", "a", "b")
        val apiInstance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(apiMock)
            .build()

        val deciderMock = MockAdapter.scripted(AiDecision("unused", emptyList()))
        val decider = BabelFitBuilder(DecidingAgentAPI::class)
            .setAdapterDirect(deciderMock)
            .build().api

        val agent = GraphAgent(apiInstance, decider, graph)
        val result = agent.run(maxSteps = 4)

        assertFalse(result.isTerminal)
        assertEquals(4, apiMock.callCount)
    }

    @Test
    fun `GraphAgent reset returns to start`() {
        val graph = agentGraph<AutonomousAIApi> {
            start("analyze")
            from("analyze").to("plan")
            from("plan").to("act")
            terminal("act")
        }

        val apiMock = MockAdapter.scripted("a1", "done1", "a2", "p2", "done2")
        val apiInstance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(apiMock)
            .build()

        val deciderMock = MockAdapter.scripted(AiDecision("unused", emptyList()))
        val decider = BabelFitBuilder(DecidingAgentAPI::class)
            .setAdapterDirect(deciderMock)
            .build().api

        val agent = GraphAgent(apiInstance, decider, graph)

        // Step through first two nodes
        agent.step() // analyze → plan
        assertEquals("plan", agent.current)

        // Reset
        agent.reset()
        assertEquals("analyze", agent.current)
    }
}
