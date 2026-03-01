package ca.adamhammer.babelfit.samples.traceviewer.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.adamhammer.babelfit.samples.common.BrightText
import ca.adamhammer.babelfit.samples.common.DarkCardAlt
import ca.adamhammer.babelfit.samples.common.DarkCardColor
import ca.adamhammer.babelfit.samples.common.DimText
import ca.adamhammer.babelfit.samples.common.ErrorColor
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceStats

@Composable
fun SpanTreePanel(controller: ComposeTraceController, modifier: Modifier) {
    Surface(modifier = modifier, color = DarkCardAlt) {
        Column {
            // Header
            Surface(color = DarkCardColor) {
                Text(
                    "Span Tree",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.primary
                )
            }

            // Tree
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(controller.flatSpans, key = { it.span.id }) { entry ->
                    SpanTreeRow(
                        entry = entry,
                        isSelected = controller.selectedSpan?.id == entry.span.id,
                        onClick = { controller.selectSpan(entry.span) },
                        onToggle = { controller.toggleExpanded(entry.span.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpanTreeRow(
    entry: FlatSpanEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val span = entry.span
    val color = Color(ComposeTraceController.spanTypeColor(span.type))
    val icon = ComposeTraceController.spanTypeIcon(span.type)
    val bgColor = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { onClick() }
            .padding(start = (8 + entry.depth * 16).dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expand/collapse toggle
        if (entry.hasChildren) {
            Text(
                text = if (entry.isExpanded) "▼" else "▶",
                color = DimText,
                fontSize = 10.sp,
                modifier = Modifier
                    .clickable { onToggle() }
                    .padding(end = 4.dp)
                    .width(14.dp)
            )
        } else {
            Spacer(Modifier.width(14.dp))
        }

        // Type icon
        Text(icon, color = color, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))

        // Name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                span.name,
                color = if (span.error != null) ErrorColor else BrightText,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        val endTime = span.endTimeMs
        if (endTime != null) {
            val duration = TraceStats.formatDuration(endTime - span.startTimeMs)
            Text(duration, color = DimText, fontSize = 10.sp)
        }

        // Token count
        span.usage?.let { usage ->
            val total = usage.inputTokens + usage.outputTokens
            if (total > 0) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    color = color.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        TraceStats.formatTokens(total),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        color = color,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Error indicator
        if (span.error != null) {
            Spacer(Modifier.width(4.dp))
            Text("✗", color = ErrorColor, fontSize = 11.sp)
        }
    }
}
