package ca.adamhammer.babelfit.samples.customersupport.models

import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.model.PromptContext

class HandoffContextInterceptor(
    private val summaryProvider: () -> String?,
    private val customerContextProvider: () -> CustomerContext
) : Interceptor {

    override fun intercept(context: PromptContext): PromptContext {
        val summary = summaryProvider()
        val customer = customerContextProvider()

        val injection = buildString {
            appendLine()
            appendLine("# Customer Information")
            appendLine("- **Name:** ${customer.name}")
            appendLine("- **Account:** ${customer.accountId}")
            appendLine("- **Product Model:** ${customer.productModel}")
            appendLine("- **Serial Number:** ${customer.serialNumber}")
            appendLine("- **Purchase Date:** ${customer.purchaseDate}")

            if (!summary.isNullOrBlank()) {
                appendLine()
                appendLine("# Handoff Context from Previous Agent")
                appendLine(summary)
            }
        }

        return context.copy(
            systemInstructions = context.systemInstructions + injection
        )
    }
}
