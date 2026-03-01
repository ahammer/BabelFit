package ca.adamhammer.babelfit.samples.customersupport.cli

import ca.adamhammer.babelfit.adapters.OpenAiAdapter
import ca.adamhammer.babelfit.debug.trace.TraceSession
import ca.adamhammer.babelfit.debug.trace.TracingAdapter
import ca.adamhammer.babelfit.debug.trace.TracingRequestListener
import ca.adamhammer.babelfit.samples.customersupport.api.SupportEventListener
import ca.adamhammer.babelfit.samples.customersupport.api.SupportSession
import ca.adamhammer.babelfit.samples.customersupport.models.AgentType
import ca.adamhammer.babelfit.samples.customersupport.models.CompanyTemplate
import ca.adamhammer.babelfit.samples.customersupport.models.CustomerContext
import ca.adamhammer.babelfit.samples.customersupport.models.SupportResponse
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val openAiAdapter = OpenAiAdapter()
    val traceSession = TraceSession()
    val adapter = TracingAdapter(openAiAdapter, traceSession)

    val template = CompanyTemplate.loadFromResource()
    val customer = CustomerContext(
        name = "Jane Doe",
        accountId = "ACCT-78432",
        productModel = "Medium",
        serialNumber = "WGT-M-20250815-4721",
        purchaseDate = "2025-08-15"
    )

    println("═══════════════════════════════════════════════════════")
    println("  BabelFit Customer Support — Agentic Support Demo")
    println("═══════════════════════════════════════════════════════")
    println("  Company: ${template.company}")
    println("  Customer: ${customer.name} (${customer.accountId})")
    println("  Product: ${template.product} — ${customer.productModel}")
    println("  Debug: ${traceSession.getSessionId()} (.btrace.json will be saved on exit)")
    println()
    println("  Type your support request. Type 'exit' to quit.")
    println("═══════════════════════════════════════════════════════")

    val listener = CliSupportListener()
    val session = SupportSession(
        apiAdapter = adapter,
        listener = listener,
        companyTemplate = template,
        customerContext = customer,
        requestListeners = listOf(TracingRequestListener(traceSession))
    )
    session.startSession()

    while (true) {
        print("\n[You] > ")
        val input = readlnOrNull()?.trim() ?: break
        when (input.lowercase()) {
            "exit", "quit" -> {
                session.endSession()
                println("Session ended. Goodbye!")
                traceSession.save()
                break
            }
            "" -> continue
            else -> {
                val response = session.chat(input)
                // Response is printed by the listener
            }
        }
    }
}

class CliSupportListener : SupportEventListener {

    override fun onSessionStarted(customerContext: CustomerContext) {
        println("\n  Session started for ${customerContext.name}")
        println("  ─────────────────────────────────────────────")
    }

    override fun onAgentTransfer(from: AgentType, to: AgentType, summary: String) {
        println("\n  ╔═══════════════════════════════════════════╗")
        println("  ║  Transfer: ${from.displayName} → ${to.displayName}")
        println("  ║  Reason: ${summary.take(60)}${if (summary.length > 60) "..." else ""}")
        println("  ╚═══════════════════════════════════════════╝")
    }

    override fun onAgentResponse(agentType: AgentType, response: SupportResponse) {
        println("\n  [${agentType.displayName}] ${response.message}")
        if (response.suggestedActions.isNotEmpty()) {
            println("  Suggested actions:")
            response.suggestedActions.forEach { println("    • $it") }
        }
        if (response.resolved) {
            println("  ✓ Issue marked as resolved")
        }
    }

    override fun onToolInvocation(agentType: AgentType, toolName: String, result: String) {
        val shortResult = if (result.length > 80) result.take(80) + "..." else result
        println("  [tool] $toolName: $shortResult")
    }

    override fun onEscalation(ticket: String) {
        println("\n  ⚠ Escalation ticket created: $ticket")
    }

    override fun onSessionEnded() {
        println("\n  ─────────────────────────────────────────────")
        println("  Session ended. Thank you!")
    }

    override fun onError(error: String) {
        System.err.println("  [error] $error")
    }
}
