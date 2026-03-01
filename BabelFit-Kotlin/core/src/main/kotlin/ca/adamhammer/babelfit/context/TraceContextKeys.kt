package ca.adamhammer.babelfit.context

import ca.adamhammer.babelfit.model.TypedKey

/** Type-safe keys used to store tracing-related state in PromptContext */
object TraceContextKeys {
    val PARENT_SPAN_ID = TypedKey<String>("btraceParentSpanId")
    val CURRENT_SPAN_ID = TypedKey<String>("btraceCurrentSpanId")
    val CURRENT_ATTEMPT = TypedKey<Int>("btraceCurrentAttempt")
}
