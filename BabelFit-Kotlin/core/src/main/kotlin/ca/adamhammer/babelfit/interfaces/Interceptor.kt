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

/**
 * An [Interceptor] that only fires for specific method names.
 *
 * When [methods] is empty, the interceptor fires for all methods.
 * Otherwise, it only fires when `context.methodName` is in [methods].
 */
abstract class MethodFilteredInterceptor(private val methods: Set<String>) : Interceptor {
    override fun intercept(context: PromptContext): PromptContext {
        if (methods.isEmpty() || context.methodName in methods) return doIntercept(context)
        return context
    }

    /** Called only when the method name matches. Implement your interception logic here. */
    abstract fun doIntercept(context: PromptContext): PromptContext
}
