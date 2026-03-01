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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.adamhammer.babelfit.samples.common.*
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceStats
import ca.adamhammer.babelfit.samples.traceviewer.models.Severity
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceAnalysis
import ca.adamhammer.babelfit.samples.traceviewer.models.Weakness
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun AnalysisSection(controller: ComposeTraceController) {
    val analysis = controller.analysis

    Surface(color = DarkCardAlt) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI Analysis", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary, fontSize = 16.sp)

            if (controller.isAnalyzing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Analyzing trace...", color = DimText, fontStyle = FontStyle.Italic, fontSize = 13.sp)
                }
                return@Column
            }

            if (analysis == null) return@Column

            // Summary
            DarkCard {
                Text("Summary", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
                Spacer(Modifier.height(6.dp))
                Text(analysis.summary, color = BrightText, fontSize = 13.sp)
            }

            // Token Efficiency
            TokenEfficiencyCard(analysis)

            // Weaknesses
            if (analysis.weaknesses.isNotEmpty()) {
                Text(
                    "Weaknesses (${analysis.weaknesses.size})",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.primary
                )
                analysis.weaknesses.forEach { weakness -> WeaknessCard(weakness) }
            }

            // Prompt Suggestions
            if (analysis.suggestions.isNotEmpty()) {
                Text(
                    "Prompt Improvements (${analysis.suggestions.size})",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.primary
                )
                analysis.suggestions.forEachIndexed { index, suggestion ->
                    SuggestionCard(index + 1, suggestion)
                }
            }
        }
    }
}

@Composable
private fun WeaknessCard(weakness: Weakness) {
    val severityColor = when (weakness.severity) {
        Severity.CRITICAL -> Color(0xFFEF5350)
        Severity.HIGH -> Color(0xFFFFA726)
        Severity.MEDIUM -> Color(0xFFFFC107)
        Severity.LOW -> Color(0xFF78909C)
    }
    ColorBorderCard(accentColor = severityColor, alpha = 0.10f) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = severityColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        weakness.severity.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = severityColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    weakness.category.name.replace("_", " "),
                    color = DimText,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(weakness.description, color = BrightText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TokenEfficiencyCard(analysis: TraceAnalysis) {
    val eff = analysis.tokenEfficiency
    DarkCard {
        Text("Token Efficiency", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TokenStat("Input", TraceStats.formatTokens(eff.totalInputTokens), Color(0xFF42A5F5))
            TokenStat("Output", TraceStats.formatTokens(eff.totalOutputTokens), Color(0xFF66BB6A))
            TokenStat("Failed Calls", TraceStats.formatTokens(eff.failedCallTokens), Color(0xFFFFA726))
            TokenStat("Waste", "~${eff.estimatedWastePercent}%",
                if (eff.estimatedWastePercent > 30) ErrorColor else SuccessColor
            )
        }
    }
}

@Composable
private fun TokenStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, color = DimText, fontSize = 10.sp)
    }
}

@Composable
private fun SuggestionCard(index: Int, suggestion: ca.adamhammer.babelfit.samples.traceviewer.models.PromptSuggestion) {
    var copied by remember { mutableStateOf(false) }

    DarkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$index. ${suggestion.targetArea}",
                color = BrightText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    val text = buildString {
                        appendLine("Target: ${suggestion.targetArea}")
                        appendLine("Current: ${suggestion.currentApproach}")
                        appendLine("Suggested: ${suggestion.suggestedImprovement}")
                        appendLine("Rationale: ${suggestion.rationale}")
                        appendLine("Impact: ${suggestion.estimatedImpact}")
                    }
                    clipboard.setContents(StringSelection(text), null)
                    copied = true
                }
            ) {
                Text(if (copied) "Copied!" else "Copy", fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(4.dp))

        Text("Current:", color = DimText, fontSize = 11.sp)
        Text(suggestion.currentApproach, color = BrightText.copy(alpha = 0.7f), fontSize = 12.sp)

        Spacer(Modifier.height(4.dp))

        Text("Suggested:", color = SuccessColor, fontSize = 11.sp)
        Text(suggestion.suggestedImprovement, color = BrightText, fontSize = 12.sp)

        Spacer(Modifier.height(4.dp))

        Text("Rationale:", color = DimText, fontSize = 11.sp)
        Text(suggestion.rationale, color = DimText, fontSize = 11.sp)

        Spacer(Modifier.height(2.dp))

        Text("Impact: ${suggestion.estimatedImpact}", color = MaterialTheme.colors.primary, fontSize = 11.sp)
    }
}
