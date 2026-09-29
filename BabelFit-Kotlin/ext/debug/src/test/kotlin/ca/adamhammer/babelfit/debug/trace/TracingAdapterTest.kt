package ca.adamhammer.babelfit.debug.trace

import ca.adamhammer.babelfit.adapters.StubAdapter
import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.babelFit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class TracingAdapterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `offline request is saved as parseable btrace JSON`() = runBlocking {
        val session = TraceSession(name = "offline-example", baseDir = tempDir.toString())
        val instance = babelFit<OfflineTraceApi> {
            adapter(TracingAdapter(StubAdapter(), session))
            listener(TracingRequestListener(session))
        }

        assertEquals("", instance.api.greet())
        session.save()

        val traceFile = tempDir.resolve("offline-example.btrace.json")
        val export = Json.decodeFromString<TraceExport>(traceFile.toFile().readText())
        assertEquals("1.0", export.version)
        assertTrue(export.spans.any { it.type == SpanType.REQUEST })
        assertTrue(export.spans.any { it.type == SpanType.ATTEMPT && it.responseOutput == "" })
    }
}

interface OfflineTraceApi {
    @AiOperation(description = "Return a greeting")
    suspend fun greet(): String
}
