package ca.adamhammer.babelfit.utils

import java.lang.reflect.Method

/**
 * Shared reflective dispatch utilities for resolving method arguments from
 * named maps and coercing values to the target parameter types.
 *
 * Used by [ca.adamhammer.babelfit.interfaces.AnnotatedToolProvider] and
 * the MCP server to reflectively invoke annotated methods.
 */
object ReflectiveDispatch {

    private val SYNTHETIC_NAME = Regex("^arg\\d+$")

    private val intTypes = setOf(Int::class.java, Integer::class.java)
    private val longTypes = setOf(Long::class.java, java.lang.Long::class.java)
    private val doubleTypes = setOf(Double::class.java, java.lang.Double::class.java)
    private val floatTypes = setOf(Float::class.java, java.lang.Float::class.java)
    private val boolTypes = setOf(Boolean::class.java, java.lang.Boolean::class.java)

    /**
     * Resolves named arguments to an ordered parameter array matching [method]'s signature.
     *
     * @param method the target method
     * @param arguments named arguments (parameter name → value)
     * @return an array of coerced argument values matching the method's parameter order
     * @throws IllegalStateException if parameter names look synthetic (compiler flag missing)
     */
    fun resolveMethodArguments(method: Method, arguments: Map<String, Any>): Array<Any?> {
        checkParameterNames(method)
        return method.parameters.map { param ->
            val value = arguments[param.name]
            if (value == null) null else coerceValue(value, param.type)
        }.toTypedArray()
    }

    /**
     * Coerces a value to the given target type.
     *
     * Supports String, Int, Long, Double, Float, and Boolean conversions.
     */
    fun coerceValue(value: Any, targetType: Class<*>): Any? = when {
        targetType == String::class.java -> value.toString()
        targetType in intTypes -> (value as? Number)?.toInt() ?: value.toString().toIntOrNull()
        targetType in longTypes -> (value as? Number)?.toLong() ?: value.toString().toLongOrNull()
        targetType in doubleTypes -> (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull()
        targetType in floatTypes -> (value as? Number)?.toFloat() ?: value.toString().toFloatOrNull()
        targetType in boolTypes -> value as? Boolean ?: value.toString().toBoolean()
        else -> value
    }

    /**
     * Validates that the method's parameter names are real (not synthetic `arg0`, `arg1`).
     *
     * @throws IllegalStateException if any parameter has a synthetic name
     */
    fun checkParameterNames(method: Method) {
        val synthetic = method.parameters.filter { SYNTHETIC_NAME.matches(it.name) }
        if (synthetic.isNotEmpty()) {
            throw IllegalStateException(
                "Parameter names for '${method.name}' look synthetic (${synthetic.joinToString { it.name }}). " +
                "Ensure the compiler preserves real names by adding '-parameters' (javac) " +
                "or 'javaParameters = true' (kotlinc) to your build configuration."
            )
        }
    }
}
