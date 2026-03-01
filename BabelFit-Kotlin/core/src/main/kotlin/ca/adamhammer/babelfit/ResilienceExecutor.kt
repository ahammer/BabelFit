package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.model.*
import ca.adamhammer.babelfit.model.UsageInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.logging.Logger
import kotlin.math.pow
import kotlin.reflect.KClass

/**
 * Executes adapter calls with retry, timeout, fallback, and rate-limiting logic.
 */
class ResilienceExecutor(
    private val resilience: ResiliencePolicy,
    private val listeners: List<RequestListener>
) {
    private val logger = Logger.getLogger(ResilienceExecutor::class.java.name)

    /**
     * Run the adapter call with resilience.
     *
     * @param rethrowCancellation when true, [CancellationException] is immediately re-thrown
     */
    @Suppress("LongMethod")
    suspend fun <R : Any> execute(
        adapter: ApiAdapter,
        context: PromptContext,
        resultClass: KClass<R>,
        toolProviders: List<ToolProvider>
    ): R {
        // Embed a unique Request ID for listeners to trace
        val requestId = java.util.UUID.randomUUID().toString()
        var currentContext = context.with(ca.adamhammer.babelfit.context.TraceContextKeys.CURRENT_SPAN_ID, requestId)
        
        notifyStart(currentContext)
        val startMs = System.currentTimeMillis()
        var lastException: Exception? = null
        val maxAttempts = resilience.maxRetries + 1
        var accumulatedUsage: UsageInfo? = null

        for (attempt in 1..maxAttempts) {
            val attemptStartMs = System.currentTimeMillis()
            notifyAttemptStart(currentContext, attempt)
            
            // Allow trace listeners and adapters to know the exact attempt ID they are inside
            val contextWithAttempt = currentContext.with(
                ca.adamhammer.babelfit.context.TraceContextKeys.CURRENT_ATTEMPT, 
                attempt
            )
            try {
                val response = executeWithTimeout(adapter, contextWithAttempt, resultClass, toolProviders)
                accumulatedUsage = mergeUsage(accumulatedUsage, response.usage)

                val validator = resilience.resultValidator
                if (validator != null) {
                    val validationResult = validator(response.result)
                    if (validationResult is ValidationResult.Invalid) {
                        throw ResultValidationException(validationResult.reason, contextWithAttempt)
                    } else if (validationResult == false) { // For backwards compatibility if validator returns Boolean
                        throw ResultValidationException("Result validation failed on attempt $attempt", contextWithAttempt)
                    }
                }

                notifyAttemptComplete(currentContext, attempt, response.result, attemptStartMs, response.usage)
                notifyComplete(currentContext, response.result, startMs, accumulatedUsage)
                return response.result
            } catch (e: CancellationException) {
                notifyAttemptError(currentContext, attempt, e, attemptStartMs)
                throw e // Always rethrow CancellationException to respect structured concurrency
            } catch (e: ResultValidationException) {
                notifyAttemptError(currentContext, attempt, e, attemptStartMs)
                lastException = e
                logger.warning { "Attempt $attempt/$maxAttempts failed: ${e.message}" }
                
                currentContext = currentContext.copy(
                    conversationHistory = currentContext.conversationHistory + Message(
                        role = MessageRole.USER,
                        content = "Validation failed: ${e.message}. Please correct your response."
                    )
                )
                
                if (attempt < maxAttempts) {
                    val delayMs = (resilience.retryDelayMs * resilience.backoffMultiplier.pow(attempt - 1)).toLong()
                    kotlinx.coroutines.delay(delayMs)
                }
            } catch (e: Exception) {
                notifyAttemptError(currentContext, attempt, e, attemptStartMs)
                lastException = e
                logger.warning { "Attempt $attempt/$maxAttempts failed: ${e.message}" }
                
                if (attempt < maxAttempts) {
                    val delayMs = (resilience.retryDelayMs * resilience.backoffMultiplier.pow(attempt - 1)).toLong()
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }

        val fallback = resilience.fallbackAdapter
        if (fallback != null) {
            val attemptStartMs = System.currentTimeMillis()
            val fallbackAttempt = maxAttempts + 1
            notifyAttemptStart(currentContext, fallbackAttempt)
            
            val contextWithAttempt = currentContext.with(
                ca.adamhammer.babelfit.context.TraceContextKeys.CURRENT_ATTEMPT, 
                fallbackAttempt
            )
            logger.info { "Primary adapter exhausted, trying fallback" }
            try {
                val response = executeWithTimeout(fallback, contextWithAttempt, resultClass, toolProviders)
                accumulatedUsage = mergeUsage(accumulatedUsage, response.usage)
                notifyAttemptComplete(currentContext, fallbackAttempt, response.result, attemptStartMs, response.usage)
                notifyComplete(currentContext, response.result, startMs, accumulatedUsage)
                return response.result
            } catch (e: Exception) {
                notifyAttemptError(currentContext, fallbackAttempt, e, attemptStartMs)
                val ex = BabelFitException("All attempts failed including fallback", e, currentContext)
                notifyError(currentContext, ex, startMs)
                throw ex
            }
        }

        val ex = BabelFitException("All $maxAttempts attempt(s) failed", lastException, currentContext)
        notifyError(currentContext, ex, startMs)
        throw ex
    }

    private suspend fun <R : Any> executeWithTimeout(
        adapter: ApiAdapter,
        context: PromptContext,
        resultClass: KClass<R>,
        toolProviders: List<ToolProvider>
    ): AdapterResponse<R> {
        if (resilience.timeoutMs <= 0) {
            return callAdapter(adapter, context, resultClass, toolProviders)
        }
        return kotlinx.coroutines.withTimeoutOrNull(resilience.timeoutMs) {
            callAdapter(adapter, context, resultClass, toolProviders)
        } ?: throw BabelFitTimeoutException("Request timed out after ${resilience.timeoutMs}ms", null, context)
    }

    private suspend fun <R : Any> callAdapter(
        adapter: ApiAdapter,
        context: PromptContext,
        resultClass: KClass<R>,
        toolProviders: List<ToolProvider>
    ): AdapterResponse<R> = if (toolProviders.isNotEmpty()) {
        adapter.handleRequestWithUsage(context, resultClass, toolProviders)
    } else {
        adapter.handleRequestWithUsage(context, resultClass)
    }

    fun notifyStart(context: PromptContext) {
        listeners.forEach { it.onRequestStart(context) }
    }

    private fun notifyAttemptStart(context: PromptContext, attempt: Int) {
        listeners.forEach { it.onAttemptStart(context, attempt) }
    }

    private fun notifyAttemptComplete(
        context: PromptContext, attempt: Int, result: Any, startMs: Long, usage: UsageInfo? = null
    ) {
        val duration = System.currentTimeMillis() - startMs
        listeners.forEach { it.onAttemptComplete(context, attempt, result, duration, usage) }
    }

    private fun notifyAttemptError(context: PromptContext, attempt: Int, error: Exception, startMs: Long) {
        val duration = System.currentTimeMillis() - startMs
        listeners.forEach { it.onAttemptError(context, attempt, error, duration) }
    }

    fun notifyComplete(context: PromptContext, result: Any, startMs: Long, usage: UsageInfo? = null) {
        val duration = System.currentTimeMillis() - startMs
        listeners.forEach { it.onRequestComplete(context, result, duration, usage) }
    }

    fun notifyError(context: PromptContext, error: Exception, startMs: Long) {
        val duration = System.currentTimeMillis() - startMs
        listeners.forEach { it.onRequestError(context, error, duration) }
    }

    private fun mergeUsage(existing: UsageInfo?, incoming: UsageInfo?): UsageInfo? {
        if (incoming == null) return existing
        if (existing == null) return incoming
        return existing.copy(
            inputTokens = existing.inputTokens + incoming.inputTokens,
            outputTokens = existing.outputTokens + incoming.outputTokens
        )
    }
}
