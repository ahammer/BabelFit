package ca.adamhammer.babelfit.context

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A coroutine context element that carries the current tracing span ID.
 * Used to propagate hierarchical span relationships across suspend function boundaries.
 */
data class TraceContextElement(val currentSpanId: String) : AbstractCoroutineContextElement(TraceContextElement) {
    companion object Key : CoroutineContext.Key<TraceContextElement>
}
