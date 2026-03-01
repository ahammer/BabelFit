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

    @AiOperation(
        summary = "Assess a single trace span for quality and provide BabelFit-aware guidance",
        description = """Assess the quality of a single span from an LLM interaction trace.
            Evaluate the prompt, schema, response, and any errors. Identify specific prompt issues
            and provide actionable code guidance that targets the BabelFit application code
            (annotations, interceptors, schema classes, resilience config) rather than the raw
            LLM prompt text. Use the BabelFit framework reference provided in context."""
    )
    @AiResponse(description = "Span assessment with quality rating, observations, prompt issues, and code guidance")
    fun assessSpan(
        @AiParameter(description = "Context for the span including its type, content, parent info, and trace summary")
        spanContext: String
    ): Future<SpanAssessment>

    @AiOperation(
        summary = "Generate an informed agent prompt for fixing BabelFit application code",
        description = """Based on the full analysis context (trace summary, session analysis, per-span
            assessments), generate a comprehensive prompt that a human developer or AI coding agent
            can use to fix the BabelFit application code. The prompt should:
            1. Reference specific BabelFit annotations, interceptors, and configuration
            2. Describe what the code probably looks like based on the trace and framework knowledge
            3. List concrete changes to make (annotation tweaks, schema redesign, interceptor additions, etc.)
            4. Be self-contained and actionable without needing to see the original trace"""
    )
    @AiResponse(description = "An agent prompt with the full fix instructions and a list of key changes")
    fun generateAgentPrompt(
        @AiParameter(description = "Full analysis context including trace summary, weaknesses, assessments, and guidance")
        analysisContext: String
    ): Future<AgentPrompt>
}
