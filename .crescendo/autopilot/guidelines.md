# BabelFit maintenance guidelines

BabelFit is a Kotlin library: a Retrofit-style, type-safe client for LLMs, with vendor adapters
(OpenAI, Gemini, Claude), extensions (MCP, agents, test, debug) and sample apps.

## Gate

- `cd BabelFit-Kotlin && ./gradlew build` must pass; it runs the tests, detekt (`detekt.yml`) and
  JaCoCo. CI runs it on JDK 17 and 21; code must compile on both, so avoid JDK 21-only APIs.
- Take the machine lease for Gradle builds: `machine-lease run -- ./gradlew build`.

## Boundaries

- Never call a real AI vendor or use API keys. Tests and experiments use `babelfit-test`
  (`MockAdapter`, `MockToolProvider`, `babelFitTest<T>()`), and sample apps run against mocks.
  Do not run `eval.ps1` or anything else that needs vendor credentials.
- `babelfit-core` has zero AI-provider dependencies; vendor code stays in `vendor/*`.
- Keep the public API source-compatible unless an issue says otherwise; note any break in the
  pull request and the module README.

## Conventions

- Idiomatic Kotlin: coroutines for async, null-safety over exceptions, small focused types.
- Every behaviour change comes with a test using the mock adapters, and module READMEs stay in step
  with the code they describe.
