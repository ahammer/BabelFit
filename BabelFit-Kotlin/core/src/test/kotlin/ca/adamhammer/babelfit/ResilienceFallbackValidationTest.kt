package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.model.BabelFitException
import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.model.ResiliencePolicy
import ca.adamhammer.babelfit.model.ResultValidationException
import ca.adamhammer.babelfit.model.UsageInfo
import ca.adamhammer.babelfit.model.ValidationResult
import ca.adamhammer.babelfit.test.MockAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResilienceFallbackValidationTest {

    @Test
    fun `fallback rejects both supported invalid validator results`() {
        listOf<(Any) -> Any>(
            { false },
            { ValidationResult.Invalid("fallback rejected") }
        ).forEach { validator ->
            val listener = RecordingListener()
            val error = assertThrows(BabelFitException::class.java) {
                execute(
                    fallback = MockAdapter.scripted("invalid fallback"),
                    validator = validator,
                    listener = listener
                )
            }

            assertTrue(error.cause is ResultValidationException)
            assertEquals(2, listener.attemptErrors)
            assertEquals(0, listener.attemptCompletes)
            assertEquals(0, listener.requestCompletes)
            assertEquals(1, listener.requestErrors)
        }
    }

    @Test
    fun `valid fallback succeeds and notifies success listeners once`() {
        val listener = RecordingListener()
        var validations = 0

        val result = execute(
            fallback = MockAdapter.scripted("valid fallback"),
            validator = {
                validations++
                it == "valid fallback"
            },
            listener = listener
        )

        assertEquals("valid fallback", result)
        assertEquals(1, validations)
        assertEquals(1, listener.attemptErrors)
        assertEquals(1, listener.attemptCompletes)
        assertEquals(1, listener.requestCompletes)
        assertEquals(0, listener.requestErrors)
    }

    @Test
    fun `fallback cancellation propagates unchanged`() {
        val cancellation = CancellationException("fallback cancelled")
        val listener = RecordingListener()
        val error = assertThrows(CancellationException::class.java) {
            execute(
                fallback = MockAdapter.dynamic { _, _ -> throw cancellation },
                listener = listener
            )
        }

        assertSame(cancellation, error)
        assertEquals(2, listener.attemptErrors)
        assertEquals(0, listener.requestCompletes)
        assertEquals(0, listener.requestErrors)
    }

    @Test
    fun `ordinary fallback failure remains wrapped`() {
        val fallbackFailure = IllegalStateException("fallback failed")
        val listener = RecordingListener()
        val error = assertThrows(BabelFitException::class.java) {
            execute(
                fallback = MockAdapter.dynamic { _, _ -> throw fallbackFailure },
                listener = listener
            )
        }

        assertSame(fallbackFailure, error.cause)
        assertEquals(1, listener.requestErrors)
        assertEquals(0, listener.requestCompletes)
    }

    private fun execute(
        fallback: ApiAdapter,
        validator: ((Any) -> Any)? = null,
        listener: RecordingListener
    ): String {
        val primary = MockAdapter.dynamic { _, _ -> throw IllegalStateException("primary failed") }
        return runBlocking {
            ResilienceExecutor(
                ResiliencePolicy(maxRetries = 0, resultValidator = validator, fallbackAdapter = fallback),
                listOf(listener)
            ).execute(primary, PromptContext(), String::class, emptyList())
        }
    }

    private class RecordingListener : RequestListener {
        var attemptErrors = 0
        var attemptCompletes = 0
        var requestCompletes = 0
        var requestErrors = 0

        override fun onAttemptError(context: PromptContext, attemptNumber: Int, error: Exception, durationMs: Long) {
            attemptErrors++
        }

        override fun onAttemptComplete(
            context: PromptContext,
            attemptNumber: Int,
            result: Any,
            durationMs: Long,
            usage: UsageInfo?
        ) {
            attemptCompletes++
        }

        override fun onRequestComplete(context: PromptContext, result: Any, durationMs: Long, usage: UsageInfo?) {
            requestCompletes++
        }

        override fun onRequestError(context: PromptContext, error: Exception, durationMs: Long) {
            requestErrors++
        }
    }
}
