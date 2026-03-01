package ca.adamhammer.babelfit

import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.AiParameter
import ca.adamhammer.babelfit.interfaces.AnnotatedToolProvider
import ca.adamhammer.babelfit.interfaces.annotatedToolProvider
import ca.adamhammer.babelfit.model.ToolCall
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnotatedToolProviderTest {

    // ── Test tool implementation ──────────────────────────────────────────────

    interface CalculatorAPI {
        @AiOperation(summary = "Add", description = "Adds two numbers")
        fun add(
            @AiParameter(description = "First number") a: Int,
            @AiParameter(description = "Second number") b: Int
        ): String
    }

    class CalculatorImpl : CalculatorAPI {
        override fun add(a: Int, b: Int): String = "${a + b}"
    }

    interface GreeterAPI {
        @AiOperation(summary = "Greet", description = "Greets a person")
        fun greet(
            @AiParameter(description = "Name of the person") name: String
        ): String

        @AiOperation(summary = "Farewell", description = "Says goodbye")
        fun farewell(
            @AiParameter(description = "Name") name: String
        ): String
    }

    class GreeterImpl : GreeterAPI {
        override fun greet(name: String) = "Hello, $name!"
        override fun farewell(name: String) = "Goodbye, $name!"
    }

    interface SuspendToolAPI {
        @AiOperation(summary = "Fetch", description = "Fetches data asynchronously")
        suspend fun fetch(
            @AiParameter(description = "The query") query: String
        ): String
    }

    class SuspendToolImpl : SuspendToolAPI {
        override suspend fun fetch(query: String) = "Result for: $query"
    }

    interface ErrorToolAPI {
        @AiOperation(summary = "Explode", description = "Always throws")
        fun explode(): String
    }

    class ErrorToolImpl : ErrorToolAPI {
        override fun explode(): String = throw RuntimeException("boom")
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `listTools returns tool definitions for annotated methods`() {
        val provider = AnnotatedToolProvider(CalculatorImpl(), CalculatorAPI::class)
        val tools = provider.listTools()

        assertEquals(1, tools.size)
        assertEquals("add", tools[0].name)
        assertTrue(tools[0].inputSchema.contains("\"a\""))
        assertTrue(tools[0].inputSchema.contains("\"b\""))
    }

    @Test
    fun `listTools discovers multiple annotated methods`() {
        val provider = annotatedToolProvider<GreeterAPI>(GreeterImpl())
        val tools = provider.listTools()

        assertEquals(2, tools.size)
        val names = tools.map { it.name }.toSet()
        assertTrue(names.contains("greet"))
        assertTrue(names.contains("farewell"))
    }

    @Test
    fun `callTool dispatches to correct method`() = runBlocking {
        val provider = annotatedToolProvider<GreeterAPI>(GreeterImpl())

        val result = provider.callTool(
            ToolCall("c1", "greet", """{"name":"Alice"}""")
        )

        assertFalse(result.isError)
        assertEquals("Hello, Alice!", result.content)
    }

    @Test
    fun `callTool handles integer parameters`() = runBlocking {
        val provider = annotatedToolProvider<CalculatorAPI>(CalculatorImpl())

        val result = provider.callTool(
            ToolCall("c2", "add", """{"a":3,"b":4}""")
        )

        assertFalse(result.isError)
        assertEquals("7", result.content)
    }

    @Test
    fun `callTool returns error for unknown tool name`() = runBlocking {
        val provider = annotatedToolProvider<CalculatorAPI>(CalculatorImpl())

        val result = provider.callTool(
            ToolCall("c3", "nonexistent", """{}""")
        )

        assertTrue(result.isError)
        assertTrue(result.content.contains("Unknown tool"))
    }

    @Test
    fun `callTool handles suspend methods`() = runBlocking {
        val provider = annotatedToolProvider<SuspendToolAPI>(SuspendToolImpl())

        val result = provider.callTool(
            ToolCall("c4", "fetch", """{"query":"test"}""")
        )

        assertFalse(result.isError)
        assertEquals("Result for: test", result.content)
    }

    @Test
    fun `callTool wraps exceptions as error results`() = runBlocking {
        val provider = annotatedToolProvider<ErrorToolAPI>(ErrorToolImpl())

        val result = provider.callTool(
            ToolCall("c5", "explode", """{}""")
        )

        assertTrue(result.isError)
        assertTrue(result.content.contains("boom"))
    }

    @Test
    fun `callTool dispatches different methods correctly`() = runBlocking {
        val provider = annotatedToolProvider<GreeterAPI>(GreeterImpl())

        val r1 = provider.callTool(ToolCall("c6", "greet", """{"name":"Bob"}"""))
        val r2 = provider.callTool(ToolCall("c7", "farewell", """{"name":"Bob"}"""))

        assertEquals("Hello, Bob!", r1.content)
        assertEquals("Goodbye, Bob!", r2.content)
    }

    @Test
    fun `callTool handles empty arguments`() = runBlocking {
        val provider = annotatedToolProvider<ErrorToolAPI>(ErrorToolImpl())

        val result = provider.callTool(
            ToolCall("c8", "explode", "")
        )

        // Should still dispatch (and throw), not crash on parsing
        assertTrue(result.isError)
    }
}
