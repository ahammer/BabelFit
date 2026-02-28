# BabelFit Claude Adapter

The `babelfit-claude` module provides an `ApiAdapter` implementation for Anthropic's Claude API. It translates BabelFit's `PromptContext` into Claude message requests.

## Features

- **Native Tool Calling**: automatically converts BabelFit `ToolProvider` definitions into Claude tool schemas and handles the multi-turn tool execution loop.
- **Structured Outputs**: uses Claude's tool-calling capabilities to guarantee responses match your Kotlin data classes.
- **Streaming**: supports token-by-token streaming via `Flow<String>`.
- **Configurable Models**: defaults to `claude-3-5-haiku-latest`, but can be configured for any Claude model.

## Usage

```kotlin
val instance = babelFit<MyAPI> {
    adapter(AnthropicAdapter(model = "claude-3-5-sonnet-latest"))
}
```