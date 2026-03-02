package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.agents.*
import ca.adamhammer.babelfit.test.MockAdapter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FuzzyMatchAndSequentialTest {

    // ── Fuzzy matching tests ───────────────────────────────────────────────

    @Test
    fun `dispatch resolves exact method name`() {
        val mock = MockAdapter.scripted("result")
        val instance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(mock)
            .build()
        val dispatcher = AgentDispatcher(instance)

        val result = dispatcher.dispatch(AiDecision("analyze", emptyMap()))
        assertEquals("result", result)
    }

    @Test
    fun `dispatch resolves case-insensitive match`() {
        val mock = MockAdapter.scripted("result")
        val instance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(mock)
            .build()
        val dispatcher = AgentDispatcher(instance)

        val result = dispatcher.dispatch(AiDecision("Analyze", emptyMap()))
        assertEquals("result", result)
    }

    @Test
    fun `dispatch resolves close misspelling via Levenshtein`() {
        val mock = MockAdapter.scripted("result")
        val instance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(mock)
            .build()
        val dispatcher = AgentDispatcher(instance)

        // "analize" is edit distance 1 from "analyze"
        val result = dispatcher.dispatch(AiDecision("analize", emptyMap()))
        assertEquals("result", result)
    }

    @Test
    fun `dispatch throws on distant method name`() {
        val mock = MockAdapter.scripted("result")
        val instance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(mock)
            .build()
        val dispatcher = AgentDispatcher(instance)

        assertThrows(IllegalArgumentException::class.java) {
            dispatcher.dispatch(AiDecision("completelyWrong", emptyMap()))
        }
    }

    @Test
    fun `levenshtein distance is correct`() {
        assertEquals(0, levenshtein("abc", "abc"))
        assertEquals(1, levenshtein("abc", "ab"))
        assertEquals(1, levenshtein("abc", "abx"))
        assertEquals(3, levenshtein("abc", "xyz"))
        assertEquals(3, levenshtein("", "abc"))
        assertEquals(3, levenshtein("abc", ""))
    }

    // ── SequentialRunner tests ─────────────────────────────────────────────

    @Test
    fun `sequential runner walks methods in order`() {
        // AutonomousAIApi has: understand(data), analyze, plan, reflect, act(@Terminal)
        // We'll provide responses for each and run with explicit ordering
        val mock = MockAdapter.scripted("u", "a", "p", "r", "done")
        val instance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(mock)
            .build()

        // understand requires a "data" arg, so skip it to avoid arg errors
        val runner = SequentialRunner(instance, listOf("analyze", "plan", "reflect", "act"))
        val result = runner.run()

        assertEquals("act", result.methodName)
        assertTrue(result.isTerminal)
        // Should have called 4 methods (stopped at terminal "act")
        assertEquals(4, mock.callCount)
    }

    @Test
    fun `sequential runner stops at terminal method`() {
        // Put act (terminal) second in order
        val mock = MockAdapter.scripted("first", "done")
        val instance = BabelFitBuilder(AutonomousAIApi::class)
            .setAdapterDirect(mock)
            .build()

        val runner = SequentialRunner(instance, listOf("analyze", "act", "plan", "reflect"))
        val result = runner.run()

        assertEquals("act", result.methodName)
        assertTrue(result.isTerminal)
        assertEquals(2, mock.callCount) // Only analyze + act called
    }
}
