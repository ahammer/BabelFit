package ca.adamhammer.babelfit.agents

import ca.adamhammer.babelfit.BabelFitInstance
import ca.adamhammer.babelfit.annotations.Terminal
import kotlinx.coroutines.runBlocking
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.hasAnnotation

/**
 * Executes methods on a [BabelFitInstance] in declaration order, without any LLM
 * decision calls. Useful for deterministic, linear workflows where the execution
 * sequence is known at compile time.
 *
 * Methods annotated with [@Terminal][Terminal] will stop the run after execution.
 *
 * Usage:
 * ```kotlin
 * val runner = SequentialRunner(babelfitInstance)
 * val result = runner.runSuspend() // walks all methods in order
 * ```
 *
 * @param T the BabelFit interface type
 * @param apiInstance the configured BabelFit instance to execute against
 * @param methodNames optional explicit ordering; defaults to declaration order from the interface 
 */
class SequentialRunner<T : Any>(
    private val apiInstance: BabelFitInstance<T>,
    private val methodNames: List<String> = apiInstance.klass.declaredFunctions
        .filter { it.name != "equals" && it.name != "hashCode" && it.name != "toString" }
        .map { it.name }
) {
    private val dispatcher = AgentDispatcher(apiInstance)

    /** Run all methods in order, blocking. Returns the last result. */
    fun run(): AgentDispatcher.DispatchResult = runBlocking { runSuspend() }

    /** Run all methods in order, suspending. Returns the last result. */
    suspend fun runSuspend(): AgentDispatcher.DispatchResult {
        var lastResult = AgentDispatcher.DispatchResult(
            methodName = "",
            value = "",
            isTerminal = false
        )

        for (methodName in methodNames) {
            val result = dispatcher.dispatchWithMetadata(
                AiDecision(method = methodName, args = emptyList())
            )
            lastResult = result
            if (result.isTerminal) break
        }

        return lastResult
    }
}
