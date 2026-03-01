package ca.adamhammer.babelfit.samples.traceviewer.models

import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.AiParameter
import ca.adamhammer.babelfit.annotations.AiResponse
import java.util.concurrent.Future

interface TraceAnalyzerAPI {

    @AiOperation(
        summary = "Analyze an LLM interaction trace for weaknesses and improvements",
        description = """You are an expert LLM prompt engineer and trace analyzer.
            Analyze the provided trace data to identify:
            1. Weaknesses in prompts, schemas, or tool usage
            2. Token waste from retries, duplicate content, or verbose prompts
            3. Error patterns and their root causes
            4. Opportunities for prompt improvements

            Be specific and actionable in your analysis. Reference specific spans
            and content from the trace. Focus on improvements that would reduce
            token usage, improve reliability, or produce better outputs."""
    )
    @AiResponse(description = "Complete analysis with weaknesses, suggestions, and token efficiency report")
    fun analyzeTrace(
        @AiParameter(description = "The full trace content including all spans, messages, prompts, and schemas")
        traceContent: String
    ): Future<TraceAnalysis>

    @AiOperation(
        summary = "Suggest specific prompt improvements based on identified issues",
        description = """Given a system prompt, schema, and list of identified issues,
            suggest concrete improvements. Each suggestion should include the current
            approach, what to change, why, and the expected impact.
            Focus on practical, copy-paste-ready improvements."""
    )
    @AiResponse(description = "List of specific, actionable prompt improvement suggestions")
    fun suggestPromptImprovements(
        @AiParameter(description = "The current system prompt text")
        systemPrompt: String,
        @AiParameter(description = "The method invocation schema JSON")
        schema: String,
        @AiParameter(description = "Comma-separated list of identified issues to address")
        issues: String
    ): Future<List<PromptSuggestion>>
}
