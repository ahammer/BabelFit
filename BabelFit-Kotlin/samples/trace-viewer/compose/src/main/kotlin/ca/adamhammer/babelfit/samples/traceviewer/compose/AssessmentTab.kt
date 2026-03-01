@file:Suppress("TooManyFunctions")
package ca.adamhammer.babelfit.samples.traceviewer.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.adamhammer.babelfit.debug.trace.TraceSpan
import ca.adamhammer.babelfit.samples.common.*
import ca.adamhammer.babelfit.samples.traceviewer.models.CodeGuidance
import ca.adamhammer.babelfit.samples.traceviewer.models.Quality
import ca.adamhammer.babelfit.samples.traceviewer.models.SpanAssessment
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun AssessmentTab(span: TraceSpan, controller: ComposeTraceController) {
    val assessment = controller.spanAssessments[span.id]
    val isAssessing = controller.assessingSpanId == span.id

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (assessment == null && !isAssessing) {
            AssessPrompt { controller.assessSpan(span) }
        } else if (isAssessing) {
            AssessingIndicator()
        } else if (assessment != null) {
            AssessmentContent(assessment)
        }
    }
}

@Composable
private fun AssessPrompt(onAssess: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Assess this span with AI", color = DimText, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAssess,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text("Run Assessment")
            }
        }
    }
}

@Composable
private fun AssessingIndicator() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            Text("Assessing span...", color = DimText, fontStyle = FontStyle.Italic, fontSize = 13.sp)
            Text("This may take a moment", color = DimText.copy(alpha = 0.5f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun AssessmentContent(assessment: SpanAssessment) {
    // Copy All button
    CopyAllButton(assessment)

    QualityBadge(assessment.quality)

    if (assessment.observations.isNotEmpty()) {
        SectionWithCopy("Observations", buildObservationsText(assessment)) {
            assessment.observations.forEach { obs ->
                Text("• $obs", color = BrightText, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }

    if (assessment.promptIssues.isNotEmpty()) {
        SectionWithCopy("Prompt Issues", buildPromptIssuesText(assessment)) {
            assessment.promptIssues.forEach { issue ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(issue.area, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(issue.issue, color = BrightText.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("Impact: ${issue.impact}", color = DimText, fontSize = 11.sp)
                }
            }
        }
    }

    if (assessment.codeGuidance.isNotEmpty()) {
        Text("Code Guidance", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
        assessment.codeGuidance.forEach { guidance ->
            CodeGuidanceCard(guidance)
        }
    }
}

@Composable
private fun CopyAllButton(assessment: SpanAssessment) {
    var copied by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = {
            copyToClipboard(buildFullAssessmentText(assessment))
            copied = true
        }) {
            Text(if (copied) "Copied!" else "Copy All", fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionWithCopy(title: String, copyText: String, content: @Composable ColumnScope.() -> Unit) {
    var copied by remember { mutableStateOf(false) }
    DarkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
            TextButton(onClick = {
                copyToClipboard(copyText)
                copied = true
            }) {
                Text(if (copied) "Copied!" else "Copy", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun QualityBadge(quality: Quality) {
    val (color, label) = when (quality) {
        Quality.GOOD -> Color(0xFF66BB6A) to "Good"
        Quality.NEEDS_IMPROVEMENT -> Color(0xFFFFC107) to "Needs Improvement"
        Quality.POOR -> Color(0xFFEF5350) to "Poor"
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Quality:", color = DimText, fontSize = 13.sp)
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CodeGuidanceCard(guidance: CodeGuidance) {
    var copied by remember { mutableStateOf(false) }

    DarkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colors.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    guidance.target.name,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colors.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = {
                val text = buildGuidanceText(guidance)
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(text), null)
                copied = true
            }) {
                Text(if (copied) "Copied!" else "Copy", fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(4.dp))

        Text("Current:", color = DimText, fontSize = 11.sp)
        Text(guidance.currentBehavior, color = BrightText.copy(alpha = 0.7f), fontSize = 12.sp)

        Spacer(Modifier.height(4.dp))

        Text("Suggested:", color = SuccessColor, fontSize = 11.sp)
        Text(guidance.suggestedChange, color = BrightText, fontSize = 12.sp)

        Spacer(Modifier.height(4.dp))

        Text("Rationale:", color = DimText, fontSize = 11.sp)
        Text(guidance.rationale, color = DimText, fontSize = 11.sp)

        if (guidance.exampleCode.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    guidance.exampleCode,
                    modifier = Modifier.padding(8.dp),
                    color = BrightText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun buildGuidanceText(guidance: CodeGuidance): String = buildString {
    appendLine("[${guidance.target}]")
    appendLine("Current: ${guidance.currentBehavior}")
    appendLine("Suggested: ${guidance.suggestedChange}")
    appendLine("Rationale: ${guidance.rationale}")
    if (guidance.exampleCode.isNotBlank()) {
        appendLine("Example:\n${guidance.exampleCode}")
    }
}

private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

private fun buildObservationsText(assessment: SpanAssessment): String =
    assessment.observations.joinToString("\n") { "• $it" }

private fun buildPromptIssuesText(assessment: SpanAssessment): String =
    assessment.promptIssues.joinToString("\n\n") { issue ->
        "${issue.area}\n${issue.issue}\nImpact: ${issue.impact}"
    }

private fun buildFullAssessmentText(assessment: SpanAssessment): String = buildString {
    appendLine("Quality: ${assessment.quality}")
    if (assessment.observations.isNotEmpty()) {
        appendLine("\n--- Observations ---")
        append(buildObservationsText(assessment))
        appendLine()
    }
    if (assessment.promptIssues.isNotEmpty()) {
        appendLine("\n--- Prompt Issues ---")
        append(buildPromptIssuesText(assessment))
        appendLine()
    }
    if (assessment.codeGuidance.isNotEmpty()) {
        appendLine("\n--- Code Guidance ---")
        assessment.codeGuidance.forEach { guidance ->
            append(buildGuidanceText(guidance))
            appendLine()
        }
    }
}
