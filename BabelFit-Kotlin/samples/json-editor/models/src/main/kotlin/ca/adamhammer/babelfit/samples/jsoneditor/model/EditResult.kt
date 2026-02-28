package ca.adamhammer.babelfit.samples.jsoneditor.model

import ca.adamhammer.babelfit.annotations.AiSchema
import kotlinx.serialization.Serializable

@Serializable
@AiSchema(title = "EditResult", description = "The result of a JSON edit operation")
data class EditResult(
    @field:AiSchema(description = "Whether the edit succeeded")
    val success: Boolean,
    @field:AiSchema(description = "The JSON path that was modified")
    val path: String,
    @field:AiSchema(description = "Human-readable description of what was done")
    val description: String
)

@Serializable
@AiSchema(title = "DocumentSummary", description = "A summary of the current JSON document structure")
data class DocumentSummary(
    @field:AiSchema(description = "Human-readable overview of the document")
    val overview: String,
    @field:AiSchema(description = "Total number of nodes in the document")
    val nodeCount: Int,
    @field:AiSchema(description = "Top-level keys or array length")
    val topLevelKeys: List<String>
)
