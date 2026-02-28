package ca.adamhammer.babelfit.interfaces

import ca.adamhammer.babelfit.model.PromptContext

/**
 * Transforms a [PromptContext] before it reaches the adapter.
 *
 * Interceptors run in registration order. Each receives the output of the previous one.
 * Use cases: injecting extra context, filtering memory, logging prompts, enforcing token budgets.
 */
fun interface Interceptor {
    fun intercept(context: PromptContext): PromptContext
}
