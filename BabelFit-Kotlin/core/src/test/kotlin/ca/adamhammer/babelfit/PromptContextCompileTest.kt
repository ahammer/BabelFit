package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.model.PromptPart
import ca.adamhammer.babelfit.model.TokenEstimator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PromptContextCompileTest {

    private fun contextWithParts(vararg parts: PromptPart) =
        PromptContext(parts = parts.toList())

    @Test
    fun `compile with no budget returns same as systemInstructions`() {
        val ctx = contextWithParts(
            PromptPart("preamble", PromptPart.PREAMBLE, "System rules."),
            PromptPart("knowledge", PromptPart.KNOWLEDGE, "Domain info."),
            PromptPart("hints", PromptPart.HINTS, "Be concise.")
        )

        assertEquals(ctx.systemInstructions, ctx.compile())
        assertEquals(ctx.systemInstructions, ctx.compile(maxTokens = null))
    }

    @Test
    fun `compile drops highest priority number parts first when over budget`() {
        val ctx = contextWithParts(
            PromptPart("preamble", PromptPart.PREAMBLE, "PREAMBLE"),
            PromptPart("identity", PromptPart.IDENTITY, "IDENTITY"),
            PromptPart("hints", PromptPart.HINTS, "HINTS")
        )

        // Budget that fits preamble + identity but not hints
        val withoutHints = "PREAMBLE\n\nIDENTITY"
        val budget = TokenEstimator.DEFAULT.estimate(withoutHints)
        val compiled = ctx.compile(maxTokens = budget)

        assertTrue(compiled.contains("PREAMBLE"))
        assertTrue(compiled.contains("IDENTITY"))
        assertFalse(compiled.contains("HINTS"))
    }

    @Test
    fun `compile drops multiple parts in priority order`() {
        val ctx = contextWithParts(
            PromptPart("preamble", PromptPart.PREAMBLE, "PREAMBLE"),
            PromptPart("knowledge", PromptPart.KNOWLEDGE, "KNOWLEDGE"),
            PromptPart("workflow", PromptPart.WORKFLOW, "WORKFLOW"),
            PromptPart("hints", PromptPart.HINTS, "HINTS")
        )

        // Budget that fits only preamble + knowledge
        val target = "PREAMBLE\n\nKNOWLEDGE"
        val budget = TokenEstimator.DEFAULT.estimate(target)
        val compiled = ctx.compile(maxTokens = budget)

        assertTrue(compiled.contains("PREAMBLE"))
        assertTrue(compiled.contains("KNOWLEDGE"))
        assertFalse(compiled.contains("WORKFLOW"))
        assertFalse(compiled.contains("HINTS"))
    }

    @Test
    fun `compile never drops PREAMBLE parts`() {
        val ctx = contextWithParts(
            PromptPart("preamble", PromptPart.PREAMBLE, "A".repeat(1000)),
            PromptPart("hints", PromptPart.HINTS, "HINTS")
        )

        // Very tight budget — should drop hints but keep preamble even if over budget
        val compiled = ctx.compile(maxTokens = 10)

        assertTrue(compiled.contains("A".repeat(1000)))
        assertFalse(compiled.contains("HINTS"))
    }

    @Test
    fun `compile returns all parts when within budget`() {
        val ctx = contextWithParts(
            PromptPart("preamble", PromptPart.PREAMBLE, "P"),
            PromptPart("hints", PromptPart.HINTS, "H")
        )

        val compiled = ctx.compile(maxTokens = 1000)

        assertTrue(compiled.contains("P"))
        assertTrue(compiled.contains("H"))
    }

    @Test
    fun `compile deduplicates by key before budgeting`() {
        val ctx = contextWithParts(
            PromptPart("key", PromptPart.KNOWLEDGE, "OLD"),
            PromptPart("key", PromptPart.KNOWLEDGE, "NEW"),
            PromptPart("preamble", PromptPart.PREAMBLE, "PREAMBLE")
        )

        val compiled = ctx.compile()

        assertFalse(compiled.contains("OLD"))
        assertTrue(compiled.contains("NEW"))
        assertTrue(compiled.contains("PREAMBLE"))
    }

    @Test
    fun `compile preserves sort order by priority`() {
        val ctx = contextWithParts(
            PromptPart("hints", PromptPart.HINTS, "THIRD"),
            PromptPart("preamble", PromptPart.PREAMBLE, "FIRST"),
            PromptPart("knowledge", PromptPart.KNOWLEDGE, "SECOND")
        )

        val compiled = ctx.compile()
        val firstIdx = compiled.indexOf("FIRST")
        val secondIdx = compiled.indexOf("SECOND")
        val thirdIdx = compiled.indexOf("THIRD")

        assertTrue(firstIdx < secondIdx)
        assertTrue(secondIdx < thirdIdx)
    }

    @Test
    fun `custom TokenEstimator is used for budget calculation`() {
        val ctx = contextWithParts(
            PromptPart("preamble", PromptPart.PREAMBLE, "P"),
            PromptPart("hints", PromptPart.HINTS, "H")
        )

        // Estimator that says every string is 100 tokens
        val expensive = TokenEstimator { 100 }
        val compiled = ctx.compile(maxTokens = 50, estimator = expensive)

        // Should drop hints, keep preamble (even though still over budget)
        assertTrue(compiled.contains("P"))
        assertFalse(compiled.contains("H"))
    }

    @Test
    fun `default TokenEstimator uses chars div 4 heuristic`() {
        val estimator = TokenEstimator.DEFAULT
        assertEquals(0, estimator.estimate(""))
        assertEquals(1, estimator.estimate("Hi"))
        assertEquals(1, estimator.estimate("1234"))
        assertEquals(2, estimator.estimate("12345"))
        assertEquals(25, estimator.estimate("A".repeat(100)))
    }
}
