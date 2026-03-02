package ca.adamhammer.babelfit.samples.customersupport.models

import ca.adamhammer.babelfit.interfaces.Interceptor
import ca.adamhammer.babelfit.model.PromptContext

@Suppress("MaxLineLength")
class CompanyContextInterceptor(
    private val agentType: AgentType,
    private val template: CompanyTemplate
) : Interceptor {

    private val sectionsByAgent = mapOf(
        AgentType.TECHNICAL to listOf("troubleshooting", "technical_specs", "maintenance", "getting_started"),
        AgentType.BILLING to listOf("warranty_returns", "support_process"),
        AgentType.GENERAL to listOf("overview", "faq", "getting_started"),
        AgentType.ESCALATION to template.sections.keys.toList(),
        AgentType.ROUTING to listOf("overview", "support_process")
    )

    override fun intercept(context: PromptContext): PromptContext {
        val relevantSections = sectionsByAgent[agentType] ?: emptyList()

        val injection = buildString {
            appendLine()
            appendLine("# ${template.company} Support Knowledge Base")
            appendLine("**Product:** ${template.product}")
            appendLine("**Contact:** ${template.contact.supportEmail} | ${template.contact.supportPhone} | ${template.contact.hours}")
            appendLine()

            for (key in relevantSections) {
                val section = template.sections[key] ?: continue
                appendLine("## ${section.title}")
                appendLine(section.content)
                appendLine()
            }

            if (agentType == AgentType.BILLING || agentType == AgentType.ESCALATION) {
                appendLine("## RMA Request Template")
                appendLine(template.templates.rmaRequest)
                appendLine()
                appendLine("## Support Response Template")
                appendLine(template.templates.supportResponse)
                appendLine()
            }
        }

        return context.withPart("company-context", ca.adamhammer.babelfit.model.PromptPart.KNOWLEDGE, injection.trimEnd())
    }
}
