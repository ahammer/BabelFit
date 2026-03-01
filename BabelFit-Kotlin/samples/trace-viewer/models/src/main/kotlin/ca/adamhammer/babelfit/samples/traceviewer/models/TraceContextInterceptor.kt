package ca.adamhammer.babelfit.samples.traceviewer.models

import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.model.PromptContext

class TraceContextInterceptor(
    private val traceSummaryProvider: () -> String
) : Interceptor {

    override fun intercept(context: PromptContext): PromptContext {
        val summary = traceSummaryProvider()
        if (summary.isBlank()) return context

        val injection = buildString {
            appendLine()
            appendLine("# Trace Data for Analysis")
            appendLine(summary)
        }

        return context.copy(
            systemInstructions = context.systemInstructions + injection
        )
    }
}
