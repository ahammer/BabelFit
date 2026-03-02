package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.AiParameter
import ca.adamhammer.babelfit.annotations.AiResponse
import ca.adamhammer.babelfit.annotations.AiSchema
import kotlinx.serialization.Serializable
import java.util.concurrent.Future

@Serializable
@AiSchema(
    title = "ActionType",
    description = """Action type for a JSON editor operation.
        SET — set/replace a value (requires path, value).
        DELETE — remove a node (requires path).
        MOVE — move a node (requires path, from, to).
        EXPLAIN — send a message to the user (requires path, message).
        ASK — ask a clarifying question (requires path, message)."""
)
enum class ActionType { SET, DELETE, MOVE, EXPLAIN, ASK }

@Serializable
@AiSchema(
    title = "EditorAction",
    description = """A single editor action. Required fields per type:
        SET: type, path, value (JSON-encoded string). MOVE: type, path, from, to.
        DELETE: type, path. EXPLAIN: type, path (can be empty), message. ASK: type, path (can be empty), message.
        Omit unused optional fields entirely — do not send null or empty strings."""
)
data class EditorAction(
    @field:AiSchema(description = "Action type: SET, DELETE, MOVE, EXPLAIN, or ASK")
    val type: ActionType,
    @field:AiSchema(
        description = "JSON Pointer target path, e.g. '/person/name'. " +
            "Use '' for root or document-level."
    )
    val path: String = "",
    @field:AiSchema(description = "Source path for MOVE actions only")
    val from: String? = null,
    @field:AiSchema(description = "Destination path for MOVE actions only")
    val to: String? = null,
    @field:AiSchema(
        description = "JSON-encoded value for SET actions, " +
            "e.g. '\"hello\"', '42', '[\"a\",\"b\"]'. " +
            "Must be a JSON-encoded string — raw objects or array literals are invalid. " +
            "For EXPLAIN, optional short explanation."
    )
    val value: String? = null,
    @field:AiSchema(description = "Human-readable message for auditing. Required for EXPLAIN/ASK.")
    val message: String? = null
)

@Serializable
@AiSchema(title = "JsonEditorResponse", description = "Response containing one or more editor actions to apply")
data class JsonEditorResponse(
    @field:AiSchema(description = "Ordered list of actions to execute on the JSON document")
    val actions: List<EditorAction>
)

@Serializable
@AiSchema(
    title = "JsonEditRequest",
    description = "Structured request describing the desired edit. " +
        "Use structured fields for programmatic edits; " +
        "use 'message' as natural-language fallback for ambiguous requests."
)
data class JsonEditRequest(
    @field:AiSchema(
        description = "Natural-language description of the desired edit, " +
            "e.g. 'Add a hobbies list to the person'"
    )
    val message: String,
    @field:AiSchema(
        description = "Optional JSON Pointer to the subtree of interest, " +
            "e.g. '/person'. When set, the editor focuses on this subtree."
    )
    val focusPath: String? = null,
    @field:AiSchema(description = "Optional operation hint: 'set', 'delete', 'move', or 'explain'")
    val operation: String? = null,
    @field:AiSchema(description = "Optional source path for MOVE operations, e.g. '/person/email'")
    val sourcePath: String? = null,
    @field:AiSchema(description = "Optional destination path for MOVE operations, e.g. '/person/contact_email'")
    val targetPath: String? = null,
    @field:AiSchema(description = "Optional JSON-encoded value for SET operations, e.g. '[\"reading\",\"hiking\"]'")
    val value: String? = null
)

interface JsonEditorAPI {

    @AiOperation(
        summary = "Respond to user with structured actions",
        description = """You are a JSON document editor. IMPORTANT: Every response MUST include at least one EXPLAIN or ASK action.
            Batch all edits into a single response.
            For SET, 'value' MUST be a JSON-encoded string (not a raw object or array literal).
            For MOVE, 'from' and 'to' are REQUIRED and must be valid JSON Pointers. If the source path does not exist in the document, emit an ASK action to clarify instead of a MOVE.

            Example responses (use these exact field shapes):
            SET: {"actions":[{"type":"SET","path":"/person/hobbies","value":"[\"reading\",\"hiking\"]","message":"Adding hobbies"},{"type":"EXPLAIN","path":"","message":"Added hobbies list"}]}
            MOVE: {"actions":[{"type":"MOVE","from":"/person/email","to":"/person/contact_email","path":"/person/contact_email","message":"Renaming email"},{"type":"EXPLAIN","path":"","message":"Renamed email field"}]}
            DELETE: {"actions":[{"type":"DELETE","path":"/person/address/state","message":"Removing state"},{"type":"EXPLAIN","path":"","message":"Removed state field"}]}
            EXPLAIN: {"actions":[{"type":"EXPLAIN","path":"","message":"Describing structure"}]}"""
    )
    @AiResponse(description = "A JsonEditorResponse with ordered actions to execute", responseClass = JsonEditorResponse::class)
    fun respond(
        @AiParameter(
            description = "Structured edit request with message, optional focusPath, operation hint, and value. " +
                "For rename/move operations, populate sourcePath and targetPath with explicit JSON Pointer paths."
        )
        request: JsonEditRequest
    ): Future<JsonEditorResponse>
}
