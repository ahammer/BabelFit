package ca.adamhammer.babelfit.model

import kotlin.reflect.KClass

/**
 * Raw request data extracted from a proxy method invocation.
 * Passed to a [ca.adamhammer.babelfit.interfaces.ContextBuilder] to produce a [PromptContext].
 */
data class BabelFitRequest(
    val descriptor: MethodDescriptor,
    val memory: Map<String, String>,
    val resultClass: KClass<*>
)
