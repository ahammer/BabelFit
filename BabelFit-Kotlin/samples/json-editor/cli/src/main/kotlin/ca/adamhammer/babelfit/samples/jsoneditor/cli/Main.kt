package ca.adamhammer.babelfit.samples.jsoneditor.cli

import ca.adamhammer.babelfit.adapters.OpenAiAdapter
import ca.adamhammer.babelfit.debug.trace.TraceSession
import ca.adamhammer.babelfit.debug.trace.TracingAdapter
import ca.adamhammer.babelfit.debug.trace.TracingRequestListener
import ca.adamhammer.babelfit.samples.jsoneditor.api.AgentJsonEditorSession
import ca.adamhammer.babelfit.samples.jsoneditor.api.JsonEditorListener
import ca.adamhammer.babelfit.samples.jsoneditor.api.JsonEditorSession
import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    val agentMode = args.contains("--agent")
    val filePath = args.firstOrNull { !it.startsWith("--") } ?: "test.json"

    val openAiAdapter = OpenAiAdapter()
    val traceSession = TraceSession()
    val adapter = TracingAdapter(openAiAdapter, traceSession)
    val mode = if (agentMode) "Agent" else "Simple"

    println("═══════════════════════════════════════════════════════")
    println("  BabelFit JSON Editor — Conversational Document Editing")
    println("═══════════════════════════════════════════════════════")
    println("  File: $filePath")
    println("  Mode: $mode")
    println("  Debug: ${traceSession.getSessionId()} (.btrace.json will be saved on exit)")
    println()
    println("  Commands: 'show' (print doc), 'save', 'exit'")
    println("  Or type naturally to edit the document.")
    println("═══════════════════════════════════════════════════════")

    val listener = CliJsonEditorListener()
    val requestListeners = listOf(TracingRequestListener(traceSession))
    val askHandler: suspend (String) -> String = { question ->
        println("\n  [?] $question")
        print("  > ")
        readlnOrNull()?.trim() ?: ""
    }

    if (agentMode) {
        val session = AgentJsonEditorSession(
            apiAdapter = adapter,
            listener = listener,
            filePath = filePath,
            requestListeners = requestListeners,
            askHandler = askHandler
        )
        replLoop(session)
    } else {
        val session = JsonEditorSession(
            apiAdapter = adapter,
            listener = listener,
            filePath = filePath,
            requestListeners = requestListeners,
            askHandler = askHandler
        )
        replLoop(session)
    }
    traceSession.save()
}

private suspend fun replLoop(session: JsonEditorSession) {
    while (true) {
        print("\n> ")
        val input = readlnOrNull()?.trim() ?: break

        when (input.lowercase()) {
            "exit", "quit" -> {
                session.save()
                println("Saved and exiting.")
                break
            }
            "save" -> {
                session.save()
                println("Saved to ${session.filePath()}")
            }
            "show" -> {
                println(session.currentDocument().toJsonString())
            }
            "" -> continue
            else -> {
                session.chat(input)
            }
        }
    }
}

private suspend fun replLoop(session: AgentJsonEditorSession) {
    while (true) {
        print("\n> ")
        val input = readlnOrNull()?.trim() ?: break

        when (input.lowercase()) {
            "exit", "quit" -> {
                session.save()
                println("Saved and exiting.")
                break
            }
            "save" -> {
                session.save()
                println("Saved to ${session.filePath()}")
            }
            "show" -> {
                println(session.currentDocument().toJsonString())
            }
            "" -> continue
            else -> {
                session.chat(input)
            }
        }
    }
}

class CliJsonEditorListener : JsonEditorListener {
    override fun onDocumentLoaded(doc: JsonDocument) {
        println("  Document loaded (${doc.nodeCount()} nodes)")
    }

    override fun onToolCall(toolName: String, args: String, result: String) {
        val isError = result.startsWith("Error:")
        val icon = if (isError) "x" else ">"
        val shortResult = if (result.length > 80) result.take(80) + "..." else result
        println("  [$icon] $toolName: $shortResult")
    }

    override fun onDocumentChanged(doc: JsonDocument, path: String, operation: String) {
        println("  [changed] Document updated (${doc.nodeCount()} nodes)")
    }

    override fun onAgentResponse(response: String) {
        // Structured responses handled via onExplain; no freeform text expected
    }

    override fun onExplain(message: String) {
        println("\n  $message")
    }

    override fun onError(error: String) {
        System.err.println("  [error] $error")
    }
}
