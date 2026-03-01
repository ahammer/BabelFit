package ca.adamhammer.babelfit.interfaces

import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.model.ToolDefinition
import ca.adamhammer.babelfit.model.ToolResult
import ca.adamhammer.babelfit.utils.ReflectiveDispatch
import ca.adamhammer.babelfit.utils.toToolDefinitions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.lang.reflect.Method
import java.util.concurrent.Future
import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

/**
 * A [ToolProvider] that automatically generates tool definitions and dispatch logic
 * from an annotated Kotlin interface, eliminating the need for hand-crafted JSON
 * schemas and manual `when` dispatch blocks.
 *
 * Methods annotated with [@AiOperation][ca.adamhammer.babelfit.annotations.AiOperation]
 * are discovered via reflection and exposed as tools. Parameter types and
 * [@AiParameter][ca.adamhammer.babelfit.annotations.AiParameter] descriptions are
 * used to generate JSON Schema input definitions.
 *
 * Usage:
 * ```kotlin
 * class MyTools {
 *     @AiOperation(description = "Search the FAQ")
 *     fun searchFaq(@AiParameter(description = "The query") query: String): String {
 *         return faqDatabase.search(query)
 *     }
 * }
 *
 * val provider = AnnotatedToolProvider(MyTools(), MyTools::class)
 * // or using the convenience function:
 * val provider = annotatedToolProvider(MyTools())
 *
 * val instance = babelFit<MyAPI> {
 *     adapter(OpenAiAdapter())
 *     toolProvider(provider)
 * }
 * ```
 *
 * @param T the type of the implementation class
 * @param implementation the object that handles tool calls
 * @param apiInterface the class whose annotated methods define the available tools
 */
class AnnotatedToolProvider<T : Any>(
    private val implementation: T,
    private val apiInterface: KClass<T>
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true }

    private val toolDefinitions: List<ToolDefinition> = apiInterface.toToolDefinitions()

    private val methodMap: Map<String, Method> = apiInterface.java.declaredMethods
        .filter { it.isAnnotationPresent(AiOperation::class.java) }
        .associateBy { it.name }

    override fun listTools(): List<ToolDefinition> = toolDefinitions

    override suspend fun callTool(call: ToolCall): ToolResult {
        val method = methodMap[call.toolName]
            ?: return ToolResult(
                id = call.id,
                toolName = call.toolName,
                content = "Unknown tool: ${call.toolName}. Available: ${methodMap.keys}",
                isError = true
            )

        return try {
            val arguments = parseArguments(call.arguments)
            val args = ReflectiveDispatch.resolveMethodArguments(method, arguments)

            val kFunction = method.kotlinFunction
            val result = if (kFunction != null && kFunction.isSuspend) {
                val paramMap = buildSuspendParamMap(kFunction, implementation, args)
                kFunction.callSuspendBy(paramMap)
            } else {
                method.invoke(implementation, *args)
            }

            // Unwrap Future results
            val unwrapped = if (result is Future<*>) result.get() else result
            val content = unwrapped?.toString() ?: ""

            ToolResult(id = call.id, toolName = call.toolName, content = content)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause ?: e
            ToolResult(
                id = call.id,
                toolName = call.toolName,
                content = "Error: ${cause.message}",
                isError = true
            )
        } catch (e: Exception) {
            ToolResult(
                id = call.id,
                toolName = call.toolName,
                content = "Error: ${e.message}",
                isError = true
            )
        }
    }

    private fun parseArguments(argumentsJson: String): Map<String, Any> {
        if (argumentsJson.isBlank()) return emptyMap()
        val element = json.parseToJsonElement(argumentsJson)
        if (element !is JsonObject) return emptyMap()
        return element.entries.associate { (key, value) ->
            key to when {
                value is JsonPrimitive && value.isString -> value.content
                value is JsonPrimitive -> value.content // numbers, booleans as strings
                else -> value.toString()
            }
        }
    }

    private fun buildSuspendParamMap(
        kFunction: kotlin.reflect.KFunction<*>,
        instance: Any,
        args: Array<Any?>
    ): Map<kotlin.reflect.KParameter, Any?> {
        val params = kFunction.parameters
        val map = mutableMapOf<kotlin.reflect.KParameter, Any?>()
        // First parameter is the instance (receiver)
        map[params[0]] = instance
        // Remaining parameters are the method arguments
        val valueParams = params.drop(1)
        for (i in valueParams.indices) {
            if (i < args.size) {
                map[valueParams[i]] = args[i]
            }
        }
        return map
    }
}

/**
 * Creates an [AnnotatedToolProvider] from an implementation instance.
 *
 * Usage:
 * ```kotlin
 * val provider = annotatedToolProvider(MyToolsImpl())
 * ```
 */
inline fun <reified T : Any> annotatedToolProvider(implementation: T): AnnotatedToolProvider<T> =
    AnnotatedToolProvider(implementation, T::class)
