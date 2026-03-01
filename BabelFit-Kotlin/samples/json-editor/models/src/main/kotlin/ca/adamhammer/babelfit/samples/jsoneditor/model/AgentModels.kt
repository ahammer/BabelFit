package ca.adamhammer.babelfit.samples.jsoneditor.model

import ca.adamhammer.babelfit.annotations.AiSchema
import kotlinx.serialization.Serializable

@Serializable
@AiSchema(description = "The result of analyzing the user's request")
data class AnalysisResult(
    @field:AiSchema(description = "A concise summary of the user's intent")
    val intent: String = "",
    @field:AiSchema(description = "Whether the request requires editing the document")
    val requiresEdits: Boolean = false,
    @field:AiSchema(description = "JSON Pointer paths likely affected by the edits")
    val affectedPaths: List<String> = emptyList()
)

@Serializable
@AiSchema(description = "A planned edit operation on the JSON document")
data class PlannedEdit(
    @field:AiSchema(description = "The type of edit: SET, DELETE, or MOVE")
    val action: String = "",
    @field:AiSchema(description = "Target JSON Pointer path for SET/DELETE, or source path for MOVE")
    val path: String = "",
    @field:AiSchema(description = "The JSON-encoded value to set (for SET actions)")
    val value: String = "",
    @field:AiSchema(description = "Destination path (for MOVE actions)")
    val toPath: String = "",
    @field:AiSchema(description = "Brief explanation of what this edit does")
    val explanation: String = ""
)

@Serializable
@AiSchema(description = "A plan for editing the JSON document")
data class EditPlan(
    @field:AiSchema(description = "The ordered list of edit steps to execute")
    val steps: List<PlannedEdit> = emptyList(),
    @field:AiSchema(description = "Overall summary of the planned changes")
    val summary: String = ""
)

@Serializable
@AiSchema(description = "The result of executing edits via tools")
data class ExecutionResult(
    @field:AiSchema(description = "Descriptions of the edits that were successfully applied")
    val appliedEdits: List<String> = emptyList(),
    @field:AiSchema(description = "Whether all edits were applied successfully")
    val success: Boolean = true,
    @field:AiSchema(description = "Error messages for any failed edits")
    val errors: List<String> = emptyList()
)

@Serializable
@AiSchema(description = "The result of verifying edits against the plan")
data class VerificationResult(
    @field:AiSchema(description = "Whether the edits match the intended plan")
    val isCorrect: Boolean = true,
    @field:AiSchema(description = "List of issues found during verification")
    val issues: List<String> = emptyList(),
    @field:AiSchema(description = "Suggestion for fixing any issues")
    val suggestion: String? = null
)

@Serializable
@AiSchema(description = "The final response to show the user")
data class UserResponse(
    @field:AiSchema(description = "A user-friendly summary of what was done")
    val message: String = "",
    @field:AiSchema(description = "Whether documents were modified in this interaction")
    val documentModified: Boolean = false
)
