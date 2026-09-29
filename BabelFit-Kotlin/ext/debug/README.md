# BabelFit Debug

The `babelfit-debug` module records BabelFit request traces as `.btrace.json` files.

## Usage

Wrap the adapter with `TracingAdapter` and register `TracingRequestListener` on the builder. The adapter records tool calls; the listener records request and attempt spans.

This offline example uses the built-in `StubAdapter`, so it makes no provider calls:

```kotlin
import ca.adamhammer.babelfit.adapters.StubAdapter
import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.babelFit
import ca.adamhammer.babelfit.debug.trace.TraceSession
import ca.adamhammer.babelfit.debug.trace.TracingAdapter
import ca.adamhammer.babelfit.debug.trace.TracingRequestListener
import kotlinx.coroutines.runBlocking

interface GreetingApi {
    @AiOperation(description = "Return a greeting")
    suspend fun greet(): String
}

fun main() = runBlocking {
    val traceSession = TraceSession(name = "offline-example")
    val instance = babelFit<GreetingApi> {
        adapter(TracingAdapter(StubAdapter(), traceSession))
        listener(TracingRequestListener(traceSession))
    }

    println(instance.api.greet())
    traceSession.save()
}
```

## Output

`save()` writes one JSON trace export to `debug/<name>.btrace.json` relative to the current working directory. The example writes `debug/offline-example.btrace.json`; with the default session name, the filename includes a timestamp. The export contains a version and a list of session, request, attempt, and tool-call spans, including their parent IDs, timing, and captured context or results where available. It can be parsed as JSON with a standard JSON parser.
