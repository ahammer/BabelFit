package ca.adamhammer.babelfit.agents

import ca.adamhammer.babelfit.BabelFitInstance
import ca.adamhammer.babelfit.annotations.Terminal
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Future
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.hasAnnotation

/**
 * Generic reflective dispatcher that invokes methods on a [BabelFitInstance]
 * based on an [AiDecision].
 *
 * Replaces hard-coded `when` blocks with reflective method lookup, making
 * agent dispatch work with any annotated interface — not just fixed ones.
 *
 * Usage:
 * ```kotlin
 * val dispatcher = AgentDispatcher(babelfitInstance)
 * val result = dispatcher.dispatch(decision) // invokes the method reflectively
 * ```
 */
class AgentDispatcher<T : Any>(
    private val babelfitInstance: BabelFitInstance<T>
) {
    private val logger = Logger.getLogger(AgentDispatcher::class.java.name)

    data class DispatchResult(
        val methodName: String,
        val value: Any?,
        val isTerminal: Boolean
    )

    private val methodMap: Map<String, KFunction<*>> = babelfitInstance.klass.declaredFunctions
        .associateBy { it.name }

    /**
     * Dispatch an [AiDecision] by reflectively invoking the named method
     * on the [BabelFitInstance]'s API proxy.
     *
     * Handles [Future] unwrapping automatically.
     *
     * @return the result of the method invocation (unwrapped from Future if applicable)
     * @throws IllegalArgumentException if the method is not found or arguments cannot be resolved
     */
    fun dispatch(decision: AiDecision): Any? = runBlocking { dispatchWithMetadata(decision).value }

    suspend fun dispatchWithMetadata(decision: AiDecision): DispatchResult {
        val method = resolveMethod(decision.method)

        val args = resolveArguments(method, decision.argsMap())
        val result = if (method.isSuspend) {
            method.callSuspendBy(args)
        } else {
            method.callBy(args)
        }

        // Unwrap Future results
        val value = if (result is Future<*>) result.get() else result
        return DispatchResult(
            methodName = method.name,
            value = value,
            isTerminal = method.hasAnnotation<Terminal>()
        )
    }

    fun isTerminal(methodName: String): Boolean {
        val method = resolveMethod(methodName)
        return method.hasAnnotation<Terminal>()
    }

    fun firstParameterlessTerminalMethodName(): String? = methodMap.values
        .firstOrNull { method ->
            method.parameters.size == 1 && method.hasAnnotation<Terminal>() // 1 parameter is the instance itself
        }
        ?.name

    /**
     * Resolves a method name with fuzzy matching fallback.
     *
     * 1. Exact match
     * 2. Case-insensitive match
     * 3. Levenshtein distance ≤ 3
     *
     * Logs a warning when fuzzy matching is used.
     */
    internal fun resolveMethod(name: String): KFunction<*> {
        // 1. Exact match
        methodMap[name]?.let { return it }

        // 2. Case-insensitive match
        val caseMatch = methodMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
        if (caseMatch != null) {
            logger.warning { "Fuzzy match: '${name}' resolved to '${caseMatch.key}' (case-insensitive)" }
            return caseMatch.value
        }

        // 3. Levenshtein distance ≤ 3
        val closest = methodMap.entries
            .map { it to levenshtein(name.lowercase(), it.key.lowercase()) }
            .filter { it.second <= 3 }
            .minByOrNull { it.second }

        if (closest != null) {
            logger.warning { "Fuzzy match: '${name}' resolved to '${closest.first.key}' (edit distance ${closest.second})" }
            return closest.first.value
        }

        throw IllegalArgumentException("Unknown method '$name'. Available: ${methodMap.keys}")
    }

    private fun resolveArguments(method: KFunction<*>, args: Map<String, String>): Map<KParameter, Any?> {
        val resolvedArgs = mutableMapOf<KParameter, Any?>()
        
        // The first parameter is always the instance itself
        val instanceParam = method.parameters.first()
        resolvedArgs[instanceParam] = babelfitInstance.api

        val valueParams = method.parameters.drop(1)
        
        for (param in valueParams) {
            val value = args[param.name]
            if (value != null) {
                resolvedArgs[param] = coerceArgument(value, param.type.classifier as KClass<*>)
            } else if (args.size == 1 && valueParams.size == 1) {
                // If there's exactly one arg and one param, match by position
                resolvedArgs[param] = coerceArgument(args.values.first(), param.type.classifier as KClass<*>)
            } else if (valueParams.isNotEmpty() && args.isEmpty() && !param.isOptional) {
                require(false) {
                    "Method '${method.name}' requires ${valueParams.size} argument(s) but none were provided"
                }
            }
        }
        return resolvedArgs
    }

    private fun coerceArgument(value: String, targetType: KClass<*>): Any = when (targetType) {
        String::class -> value
        Int::class -> value.toInt()
        Long::class -> value.toLong()
        Double::class -> value.toDouble()
        Float::class -> value.toFloat()
        Boolean::class -> value.toBoolean()
        else -> value
    }
}

/** Compute Levenshtein edit distance between two strings. */
internal fun levenshtein(a: String, b: String): Int {
    val m = a.length
    val n = b.length
    var prev = IntArray(n + 1) { it }
    var curr = IntArray(n + 1)
    for (i in 1..m) {
        curr[0] = i
        for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
        }
        val tmp = prev; prev = curr; curr = tmp
    }
    return prev[n]
}
