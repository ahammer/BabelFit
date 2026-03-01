package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.AiParameter
import ca.adamhammer.babelfit.annotations.AiResponse
import ca.adamhammer.babelfit.annotations.Memorize
import ca.adamhammer.babelfit.annotations.Terminal
import ca.adamhammer.babelfit.annotations.Transitions
import ca.adamhammer.babelfit.samples.jsoneditor.model.AnalysisResult
import ca.adamhammer.babelfit.samples.jsoneditor.model.EditPlan
import ca.adamhammer.babelfit.samples.jsoneditor.model.ExecutionResult
import ca.adamhammer.babelfit.samples.jsoneditor.model.UserResponse
import ca.adamhammer.babelfit.samples.jsoneditor.model.VerificationResult
import java.util.concurrent.Future

/**
 * Agent-based JSON editor API. Methods form a state graph:
 *
 * ```
 * analyzeRequest ──→ planEdits ──→ executeEdits ──→ verifyResult ──→ respondToUser
 *       │                              ↑                │
 *       └──────────────────────────────→└────────────────┘
 *                                  (if verification fails)
 * ```
 *
 * - Read-only queries shortcut: analyzeRequest → respondToUser
 * - Edit flow: analyzeRequest → planEdits → executeEdits → verifyResult → respondToUser
 * - Retry on verify failure: verifyResult → planEdits (loop)
 */
interface JsonEditorAgentAPI {

    @AiOperation(
        summary = "Analyze the user's request",
        description = """Examine the user's message and the current JSON document to understand
            what they want. Determine if the request requires editing the document or is just
            a question about the document's contents. Identify which paths may be affected."""
    )
    @AiResponse(description = "Analysis of the user's intent and affected paths")
    @Memorize("analysis")
    @Transitions("planEdits", "respondToUser")
    fun analyzeRequest(
        @AiParameter(description = "The user's message describing what they want")
        userMessage: String
    ): Future<AnalysisResult>

    @AiOperation(
        summary = "Plan edits to the JSON document",
        description = """Based on the analysis, create a detailed plan of edits to apply.
            Each planned edit should specify the action type (SET, DELETE, MOVE),
            the target path, the value (for SET), and a brief explanation.
            Review the current document structure to ensure paths are valid."""
    )
    @AiResponse(description = "An ordered plan of edits to execute")
    @Memorize("plan")
    @Transitions("executeEdits")
    fun planEdits(
        @AiParameter(description = "A summary of the analysis — what the user wants and which paths are affected")
        analysis: String
    ): Future<EditPlan>

    @AiOperation(
        summary = "Execute the planned edits using tools",
        description = """Execute the planned edits by calling the json_set, json_delete, and json_move
            tools. Apply each edit in order. Report which edits succeeded and any errors.
            Do NOT skip edits — attempt all of them. Use the json_get tool to inspect values
            if needed before editing."""
    )
    @AiResponse(description = "Report of which edits were applied and any errors")
    @Transitions("verifyResult")
    fun executeEdits(
        @AiParameter(description = "JSON plan summary describing the edits to execute")
        plan: String
    ): Future<ExecutionResult>

    @AiOperation(
        summary = "Verify the edits were applied correctly",
        description = """Check the document's current state against the intended plan.
            Use json_get to inspect key paths and confirm values match expectations.
            If there are issues, set isCorrect=false and describe what went wrong."""
    )
    @AiResponse(description = "Verification result — whether edits match the intended plan")
    @Transitions("respondToUser", "planEdits")
    fun verifyResult(
        @AiParameter(description = "Summary of what was expected after the edits")
        expected: String
    ): Future<VerificationResult>

    @AiOperation(
        summary = "Respond to the user",
        description = """Provide a clear, friendly summary of what was done (or answer the user's
            question if no edits were needed). Mention specific paths and values that were changed."""
    )
    @AiResponse(description = "The final user-facing response")
    @Terminal
    fun respondToUser(
        @AiParameter(description = "Summary of the actions taken or the answer to provide")
        summary: String
    ): Future<UserResponse>
}
