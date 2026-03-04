# BabelFit Debug

The `babelfit-debug` module provides a `DebugAdapter` that wraps any other `ApiAdapter` and writes all requests and responses to markdown files on disk. This is useful for post-hoc inspection of what the AI actually saw and generated.

## Usage

```kotlin
val instance = babelFit<MyAPI> {
    // Wrap your real adapter in a DebugAdapter
    adapter(DebugAdapter(OpenAiAdapter()))
}
```

## Output

By default, `DebugAdapter` creates a `debug/` directory in the current working directory. For every Shimmer call, it writes a markdown file containing:

1. The full `PromptContext` (system instructions, method invocation, memory, properties)
2. The raw JSON response from the AI provider
3. The deserialized Kotlin object returned to your application

This allows you to easily audit the exact prompts being sent and the exact responses being received.