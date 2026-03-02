package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.model.ValidationResult
import kotlinx.serialization.json.Json

object EditorActionValidator {

    @Suppress("ReturnCount")
    fun validate(response: JsonEditorResponse): ValidationResult {
        if (response.actions.isEmpty()) {
            return ValidationResult.Invalid("Response must contain at least one action")
        }

        for ((i, action) in response.actions.withIndex()) {
            val prefix = "Action[$i] (${action.type})"
            if (!isValidJsonPointer(action.path)) {
                return ValidationResult.Invalid(
                    "$prefix: invalid JSON Pointer path '${action.path}'"
                )
            }
            val error = validateAction(action, prefix)
            if (error != null) return error
        }

        // Enforce: if any action modifies the document, at least one EXPLAIN or ASK must be present
        val hasModifyingAction = response.actions.any {
            it.type == ActionType.SET || it.type == ActionType.DELETE || it.type == ActionType.MOVE
        }
        val hasExplainOrAsk = response.actions.any {
            it.type == ActionType.EXPLAIN || it.type == ActionType.ASK
        }
        if (hasModifyingAction && !hasExplainOrAsk) {
            return ValidationResult.Invalid(
                "Response contains edit actions but no EXPLAIN or ASK action. " +
                    "Every response with edits MUST include at least one EXPLAIN or ASK."
            )
        }

        return ValidationResult.Valid
    }

    private fun validateAction(
        action: EditorAction,
        prefix: String
    ): ValidationResult.Invalid? = when (action.type) {
        ActionType.SET -> validateSet(action, prefix)
        ActionType.DELETE -> validateDelete(action, prefix)
        ActionType.MOVE -> validateMove(action, prefix)
        ActionType.EXPLAIN, ActionType.ASK -> validateMessage(action, prefix)
    }

    private fun validateSet(
        action: EditorAction,
        prefix: String
    ): ValidationResult.Invalid? {
        if (action.value.isNullOrBlank()) {
            return ValidationResult.Invalid(
                "$prefix: SET requires a non-empty 'value'"
            )
        }
        return try {
            Json.parseToJsonElement(action.value)
            null
        } catch (e: Exception) {
            ValidationResult.Invalid(
                "$prefix: SET 'value' is not valid JSON: ${e.message}"
            )
        }
    }

    private fun validateDelete(
        action: EditorAction,
        prefix: String
    ): ValidationResult.Invalid? {
        if (action.path.isBlank() || action.path == "/") {
            return ValidationResult.Invalid(
                "$prefix: DELETE cannot target root path"
            )
        }
        return null
    }

    private fun validateMove(
        action: EditorAction,
        prefix: String
    ): ValidationResult.Invalid? {
        if (action.from.isNullOrBlank()) {
            return ValidationResult.Invalid(
                "$prefix: MOVE requires a non-empty 'from' path"
            )
        }
        if (action.to.isNullOrBlank()) {
            return ValidationResult.Invalid(
                "$prefix: MOVE requires a non-empty 'to' path"
            )
        }
        if (!isValidJsonPointer(action.from)) {
            return ValidationResult.Invalid(
                "$prefix: MOVE 'from' is not a valid JSON Pointer"
            )
        }
        if (!isValidJsonPointer(action.to)) {
            return ValidationResult.Invalid(
                "$prefix: MOVE 'to' is not a valid JSON Pointer"
            )
        }
        return null
    }

    private fun validateMessage(
        action: EditorAction,
        prefix: String
    ): ValidationResult.Invalid? {
        if (action.message.isNullOrBlank()) {
            return ValidationResult.Invalid(
                "$prefix: ${action.type} requires a non-empty 'message'"
            )
        }
        return null
    }

    fun safeFallback(reason: String): JsonEditorResponse = JsonEditorResponse(
        actions = listOf(
            EditorAction(
                type = ActionType.EXPLAIN,
                path = "",
                message = "Validation failed: $reason"
            )
        )
    )

    private fun isValidJsonPointer(path: String): Boolean =
        path.isEmpty() || path == "/" || path.startsWith("/")
}
