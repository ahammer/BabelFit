package ca.adamhammer.babelfit.samples.jsoneditor

import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.AiParameter
import ca.adamhammer.babelfit.annotations.AiResponse
import ca.adamhammer.babelfit.annotations.Memorize
import java.util.concurrent.Future

interface JsonEditorAPI {

    @AiOperation(
        summary = "Respond to user",
        description = """You are a JSON document editor assistant. The user will describe changes they want 
            to make to a JSON document in natural language. Use the provided tools to read, modify, and 
            inspect the document. After making changes, respond with a clear summary of what you did. 
            If the user asks a question about the document, inspect it with the tools and answer.
            Always confirm what was changed and show the affected paths.
            IMPORTANT: If a tool call returns an error, report the error to the user honestly. 
            Never claim success when a tool failed. Always verify changes by reading back the path 
            after setting it."""
    )
    @AiResponse(description = "A natural-language response summarizing the actions taken or answering the user's question")
    @Memorize(label = "last-response")
    fun respond(
        @AiParameter(description = "The user's message describing what they want to do with the JSON document")
        userMessage: String
    ): Future<String>
}
