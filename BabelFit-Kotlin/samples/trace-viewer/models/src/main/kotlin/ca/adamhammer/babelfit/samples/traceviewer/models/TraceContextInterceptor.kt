package ca.adamhammer.babelfit.samples.traceviewer.models

import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.model.PromptContext

class TraceContextInterceptor(
    private val cheatSheetProvider: () -> String = { "" },
    private val traceSummaryProvider: () -> String
) : Interceptor {

    override fun intercept(context: PromptContext): PromptContext {
        val cheatSheet = cheatSheetProvider()
        val summary = traceSummaryProvider()
        if (cheatSheet.isBlank() && summary.isBlank()) return context

        val injection = buildString {
            if (cheatSheet.isNotBlank()) {
                appendLine()
                appendLine(cheatSheet)
            }
            if (summary.isNotBlank()) {
                appendLine()
                appendLine("# Trace Data for Analysis")
                appendLine(summary)
            }
        }

        return context.withPart("trace-context", ca.adamhammer.babelfit.model.PromptPart.DOCUMENT, injection.trimEnd())
    }
}
