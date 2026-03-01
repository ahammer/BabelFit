package ca.adamhammer.babelfit.interfaces

import ca.adamhammer.babelfit.model.PromptContext
import ca.adamhammer.babelfit.model.UsageInfo

/**
 * Observes BabelFit request lifecycle events for logging, metrics, and cost tracking.
 *
 * Register via [ca.adamhammer.babelfit.BabelFitBuilder.listener].
 * Multiple listeners can be registered and are called in registration order.
 */
interface RequestListener {
    /** Called before the adapter receives a request. */
    fun onRequestStart(context: PromptContext) {}

    /** Called after a successful adapter response. */
    fun onRequestComplete(context: PromptContext, result: Any, durationMs: Long, usage: UsageInfo? = null) {}

    /** Called when a request fails (after all retries are exhausted). */
    fun onRequestError(context: PromptContext, error: Exception, durationMs: Long) {}

    /** Called when an individual execution attempt starts (including retries). */
    fun onAttemptStart(context: PromptContext, attemptNumber: Int) {}

    /** Called when an individual execution attempt completes successfully. */
    fun onAttemptComplete(context: PromptContext, attemptNumber: Int, result: Any, durationMs: Long, usage: UsageInfo? = null) {}

    /** Called when an individual execution attempt fails. */
    fun onAttemptError(context: PromptContext, attemptNumber: Int, error: Exception, durationMs: Long) {}
}
