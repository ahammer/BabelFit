package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.AiParameter
import ca.adamhammer.babelfit.annotations.AiResponse
import kotlinx.serialization.Serializable
import java.util.concurrent.Future

@Serializable
enum class ActionType { SET, DELETE, MOVE, EXPLAIN, ASK }

@Serializable
data class EditorAction(
    val type: ActionType,
    val path: String = "",
    val value: String = "",
    val from: String = "",
    val to: String = "",
    val message: String = ""
)

@Serializable
data class JsonEditorResponse(
    val actions: List<EditorAction>
)

interface JsonEditorAPI {

    @AiOperation(
        summary = "Respond to user with structured actions",
        description = """You are a JSON document editor. Respond ONLY with a structured list of actions.

            ACTION TYPES:
            - SET: Set a JSON value. Requires 'path' (JSON Pointer) and 'value' (JSON-encoded string).
            - DELETE: Remove a node. Requires 'path'.
            - MOVE: Move a node. Requires 'from' and 'to' paths.
            - EXPLAIN: Send a message to the user. Requires 'message'.
              Use for summaries, answers, confirmations, and error reports.
            - ASK: Ask a clarifying question. Requires 'message'.
              Use when you need more information before proceeding.

            RULES:
            - Every response MUST include at least one EXPLAIN or ASK action.
            - Batch ALL edits into a single response.
            - For SET: 'value' must be valid JSON-encoded (strings need inner quotes: '"hello"').
            - Unused fields should be empty strings."""
    )
    @AiResponse(description = "A structured list of actions to execute on the JSON document")
    fun respond(
        @AiParameter(description = "The user's message describing what they want to do with the JSON document")
        userMessage: String
    ): Future<JsonEditorResponse>
}
