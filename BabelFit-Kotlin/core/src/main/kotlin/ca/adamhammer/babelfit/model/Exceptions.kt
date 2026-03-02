package ca.adamhammer.babelfit.model

/** Base exception for all BabelFit errors. */
open class BabelFitException(
    message: String,
    cause: Throwable? = null,
    val context: PromptContext? = null
) : RuntimeException(message, cause)

/** Thrown when a request times out. */
class BabelFitTimeoutException(
    message: String,
    cause: Throwable? = null,
    context: PromptContext? = null
) : BabelFitException(message, cause, context)

/** Thrown when a result is rejected by the configured validator. */
class ResultValidationException(
    message: String,
    context: PromptContext? = null
) : BabelFitException(message, null, context)

/** Thrown when the BabelFit builder is configured incorrectly. */
class BabelFitConfigurationException(
    message: String,
    cause: Throwable? = null
) : BabelFitException(message, cause)

/** Thrown when the AI response cannot be deserialized into the expected type. */
class BabelFitDeserializationException(
    message: String,
    cause: Throwable? = null,
    context: PromptContext? = null,
    /** The raw text response from the AI that failed to parse. */
    val rawResponse: String? = null,
    /** The expected type/schema that the response should have matched. */
    val expectedType: String? = null
) : BabelFitException(message, cause, context)

/** Thrown when a tool invocation fails. */
class BabelFitToolException(
    message: String,
    cause: Throwable? = null,
    context: PromptContext? = null
) : BabelFitException(message, cause, context)

/** Thrown when the underlying AI adapter encounters an error (e.g., HTTP 500). */
class BabelFitAdapterException(
    message: String,
    cause: Throwable? = null,
    context: PromptContext? = null
) : BabelFitException(message, cause, context)
