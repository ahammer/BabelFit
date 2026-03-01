package ca.adamhammer.babelfit.samples.traceviewer.api

object BabelFitCheatSheet {
    val text: String = """
# BabelFit Framework Reference (for trace analysis)

## Core Pattern
Developers define a Kotlin interface annotated with BabelFit annotations, then instantiate it
via `babelFit<MyAPI> { adapter(...); ... }.api`. BabelFit generates the system prompt, JSON
schema, and handles serialization/deserialization automatically.

## Key Annotations
- `@AiOperation(summary, description)` — on each interface method; `description` becomes the
  system instruction for that call. This is the primary place developers control prompt content.
- `@AiSchema(title, description)` — on data classes used as return types or parameters; controls
  the JSON schema description the LLM sees.
- `@AiParameter(description)` — on method parameters; becomes parameter descriptions in the schema.
- `@AiResponse(description)` — on methods; describes what the LLM should return.
- `@Memorize` — on method parameters; value is carried across calls in the conversation as context.
- `@Terminal` — marks a method as conversation-ending (agent stops after this call).
- `@Transitions(to = [OtherAPI::class])` — in agent graphs; defines allowed state transitions.

## Builder DSL (`babelFit<T> { ... }`)
- `adapter(apiAdapter)` — sets the LLM provider (OpenAI, Claude, Gemini)
- `addInterceptor(interceptor)` — transforms PromptContext before each call
- `listener(requestListener)` — observes requests/responses (e.g., tracing)
- `toolProvider(provider)` — registers tool functions the LLM can call
- `resilience { maxRetries; retryDelayMs; backoffMultiplier; validation {}; fallback {} }`

## PromptContext (what interceptors transform)
- `systemInstructions: String` — the system prompt text
- `schema: String` — the JSON schema for the expected response
- `messages: List<Message>` — conversation history
- `tools: List<Tool>` — available tool definitions

## Resilience Configuration
```kotlin
resilience {
    maxRetries = 3
    retryDelayMs = 1000
    backoffMultiplier = 2.0
    validation { result -> result.isValid() }
    fallback { error -> defaultResult() }
}
```
Retries trigger new ATTEMPT spans. Validation failures also trigger retries.

## Agents
- `AutonomousAgent` — loops calling methods until `@Terminal` is reached
- `GraphAgent` — state machine with `@Transitions` controlling flow between APIs
- `SequentialRunner` — runs a list of operations in order

## Tool System
- `ToolProvider` / `AnnotatedToolProvider` — expose functions the LLM can invoke
- Tool calls appear as TOOL_CALL spans in traces
- MCP (Model Context Protocol) tool providers also supported

## Trace Span Types
- SESSION — top-level session wrapper
- AGENT — an agent execution (Autonomous/Graph)
- STEP — a step within an agent or sequential runner
- REQUEST — a single logical request to the LLM (may contain multiple attempts)
- ATTEMPT — one actual API call attempt (child of REQUEST); error=null means success
- TOOL_CALL — a tool invocation triggered by the LLM

## Schema Design Impact
The `@AiSchema` descriptions directly affect how well the LLM understands the expected output.
Poor descriptions → parsing failures → retries → wasted tokens.
Overly complex nested schemas → higher failure rates.
Missing `@AiParameter` descriptions → the LLM guesses parameter meaning.

## Common Issues Visible in Traces
1. Retry loops: multiple ATTEMPT spans under one REQUEST → check schema complexity or prompt clarity
2. Validation failures: error field shows validation rejection → tighten @AiSchema descriptions
3. Token waste: duplicate system prompts across attempts → consider @Memorize for context
4. Tool call failures: TOOL_CALL spans with errors → check tool parameter schemas
5. Long durations: high latency spans → consider breaking into smaller operations
""".trimIndent()
}
