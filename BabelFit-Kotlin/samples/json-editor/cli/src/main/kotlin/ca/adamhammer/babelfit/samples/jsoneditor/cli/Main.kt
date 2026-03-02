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
import java.io.File

fun main(args: Array<String>) = runBlocking {
    val agentMode = args.contains("--agent")
    val scriptPath = argValue(args, "--script")
    val tracePath = argValue(args, "--trace")
    val filePath = args.firstOrNull { !it.startsWith("--") && it != scriptPath && it != tracePath } ?: "test.json"

    val openAiAdapter = OpenAiAdapter()
    val traceSession = TraceSession()
    val adapter = TracingAdapter(openAiAdapter, traceSession)

    printBanner(filePath, agentMode, scriptPath, traceSession)

    val listener = CliJsonEditorListener()
    val requestListeners = listOf(TracingRequestListener(traceSession))
    val scriptLines = scriptPath?.let { parseScriptFile(it) }
    val askHandler = buildAskHandler(scriptLines, scriptPath != null)

    val ops = createSession(agentMode, adapter, listener, filePath, requestListeners, askHandler)

    if (scriptLines != null) {
        scriptedRun(scriptLines.prompts, ops)
    } else {
        replLoop(ops)
    }

    saveTrace(traceSession, tracePath)
}

private fun printBanner(filePath: String, agentMode: Boolean, scriptPath: String?, traceSession: TraceSession) {
    val mode = if (agentMode) "Agent" else "Simple"
    println("═══════════════════════════════════════════════════════")
    println("  BabelFit JSON Editor — Conversational Document Editing")
    println("═══════════════════════════════════════════════════════")
    println("  File: $filePath")
    println("  Mode: $mode")
    if (scriptPath != null) println("  Script: $scriptPath")
    println("  Debug: ${traceSession.getSessionId()} (.btrace.json will be saved on exit)")
    println()
    println("  Commands: 'show' (print doc), 'save', 'exit'")
    println("  Or type naturally to edit the document.")
    println("═══════════════════════════════════════════════════════")
}

private data class SessionOps(
    val chat: suspend (String) -> Unit,
    val save: suspend () -> Unit,
    val show: () -> String,
    val filePath: () -> String
)

private fun buildAskHandler(scriptLines: ScriptInput?, isScripted: Boolean): suspend (String) -> String {
    val askResponses = scriptLines?.askResponses?.toMutableList() ?: mutableListOf()
    return { question ->
        println("\n  [?] $question")
        if (askResponses.isNotEmpty()) {
            val answer = askResponses.removeFirst()
            println("  > $answer  [scripted]")
            answer
        } else if (isScripted) {
            val fallback = "Please proceed with your best judgment"
            println("  > $fallback  [auto]")
            fallback
        } else {
            print("  > ")
            readlnOrNull()?.trim() ?: ""
        }
    }
}

private fun createSession(
    agentMode: Boolean,
    adapter: TracingAdapter,
    listener: JsonEditorListener,
    filePath: String,
    requestListeners: List<TracingRequestListener>,
    askHandler: suspend (String) -> String
): SessionOps {
    return if (agentMode) {
        val session = AgentJsonEditorSession(
            apiAdapter = adapter, listener = listener, filePath = filePath,
            requestListeners = requestListeners, askHandler = askHandler
        )
        SessionOps({ session.chat(it) }, { session.save() },
            { session.currentDocument().toJsonString() }, { session.filePath() })
    } else {
        val session = JsonEditorSession(
            apiAdapter = adapter, listener = listener, filePath = filePath,
            requestListeners = requestListeners, askHandler = askHandler
        )
        SessionOps({ session.chat(it) }, { session.save() },
            { session.currentDocument().toJsonString() }, { session.filePath() })
    }
}

private fun saveTrace(traceSession: TraceSession, tracePath: String?) {
    if (tracePath != null) {
        traceSession.save(File(tracePath).also { it.parentFile?.mkdirs() })
    } else {
        traceSession.save()
    }
}

private fun argValue(args: Array<String>, flag: String): String? {
    val idx = args.indexOf(flag)
    return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else null
}

private data class ScriptInput(val prompts: List<String>, val askResponses: List<String>)

private fun parseScriptFile(path: String): ScriptInput {
    val lines = File(path).readLines()
    val prompts = mutableListOf<String>()
    val askResponses = mutableListOf<String>()
    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() || trimmed.startsWith("#") -> continue
            trimmed.startsWith(">") -> askResponses.add(trimmed.removePrefix(">").trim())
            else -> prompts.add(trimmed)
        }
    }
    return ScriptInput(prompts, askResponses)
}

private suspend fun scriptedRun(prompts: List<String>, ops: SessionOps) {
    for ((i, prompt) in prompts.withIndex()) {
        println("\n[${ i + 1}/${prompts.size}] > $prompt")
        ops.chat(prompt)
    }
    ops.save()
    println("\nScript complete. Final document:")
    println(ops.show())
}

private suspend fun replLoop(ops: SessionOps) {
    while (true) {
        print("\n> ")
        val input = readlnOrNull()?.trim() ?: break

        when (input.lowercase()) {
            "exit", "quit" -> {
                ops.save()
                println("Saved and exiting.")
                break
            }
            "save" -> {
                ops.save()
                println("Saved to ${ops.filePath()}")
            }
            "show" -> {
                println(ops.show())
            }
            "" -> continue
            else -> {
                ops.chat(input)
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
