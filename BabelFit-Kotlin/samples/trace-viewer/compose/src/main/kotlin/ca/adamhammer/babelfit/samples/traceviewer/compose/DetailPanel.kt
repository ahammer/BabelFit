@file:Suppress("TooManyFunctions")
package ca.adamhammer.babelfit.samples.traceviewer.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.adamhammer.babelfit.debug.trace.TraceSpan
import ca.adamhammer.babelfit.samples.common.*
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceStats

private enum class DetailTab(val label: String) {
    OVERVIEW("Overview"),
    MESSAGES("Messages"),
    PROMPT("Prompt"),
    SCHEMA("Schema"),
    RESPONSE("Response")
}

@Composable
fun DetailPanel(controller: ComposeTraceController, modifier: Modifier) {
    val span = controller.selectedSpan
    val analysis = controller.analysis

    Surface(modifier = modifier, color = DarkSurface) {
        if (span == null && analysis == null) {
            EmptyDetail()
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (span != null) {
                    SpanDetail(span)
                }
                if (analysis != null || controller.isAnalyzing) {
                    AnalysisSection(controller)
                }
            }
        }
    }
}

@Composable
private fun EmptyDetail() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Select a span to view details", color = DimText, fontSize = 14.sp)
            Text("or click Analyze for AI insights", color = DimText.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun SpanDetail(span: TraceSpan) {
    var selectedTab by remember(span.id) { mutableStateOf(DetailTab.OVERVIEW) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SpanHeader(span)
        SpanTabRow(span, selectedTab) { selectedTab = it }

        // Tab content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                DetailTab.OVERVIEW -> OverviewTab(span)
                DetailTab.MESSAGES -> MessagesTab(span)
                DetailTab.PROMPT -> PromptTab(span)
                DetailTab.SCHEMA -> SchemaTab(span)
                DetailTab.RESPONSE -> ResponseTab(span)
            }
        }
    }
}

@Composable
private fun SpanTabRow(span: TraceSpan, selectedTab: DetailTab, onSelect: (DetailTab) -> Unit) {
    TabRow(
        selectedTabIndex = DetailTab.entries.indexOf(selectedTab),
        backgroundColor = DarkCardAlt,
        contentColor = MaterialTheme.colors.primary
    ) {
        DetailTab.entries.forEach { tab ->
            val hasContent = tabHasContent(tab, span)
            Tab(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                enabled = hasContent
            ) {
                Text(
                    tab.label,
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = when {
                        selectedTab == tab -> MaterialTheme.colors.primary
                        hasContent -> DimText
                        else -> DimText.copy(alpha = 0.3f)
                    },
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun tabHasContent(tab: DetailTab, span: TraceSpan): Boolean = when (tab) {
    DetailTab.OVERVIEW -> true
    DetailTab.MESSAGES -> !span.messages.isNullOrEmpty()
    DetailTab.PROMPT -> span.requestInput != null
    DetailTab.SCHEMA -> span.schema != null
    DetailTab.RESPONSE -> span.responseOutput != null
}

@Composable
private fun SpanHeader(span: TraceSpan) {
    val color = Color(ComposeTraceController.spanTypeColor(span.type))

    Surface(color = DarkCardColor) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(ComposeTraceController.spanTypeIcon(span.type), color = color, fontSize = 18.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(span.name, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(span.type.name, color = color, fontSize = 11.sp)
            }
            val headerEndTime = span.endTimeMs
            if (headerEndTime != null) {
                val duration = TraceStats.formatDuration(headerEndTime - span.startTimeMs)
                Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        duration,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            span.usage?.let { usage ->
                val total = usage.inputTokens + usage.outputTokens
                if (total > 0) {
                    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "${usage.inputTokens}↑ ${usage.outputTokens}↓",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = color,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            if (span.error != null) {
                Surface(color = ErrorColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "ERROR",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = ErrorColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(span: TraceSpan) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DarkCard {
            Text("Span Details", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
            Spacer(Modifier.height(8.dp))
            InfoRow("ID", span.id)
            InfoRow("Type", span.type.name)
            InfoRow("Name", span.name)
            span.parentId?.let { InfoRow("Parent ID", it) }
            span.description?.let { InfoRow("Description", it) }
        }

        DarkCard {
            Text("Timing", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
            Spacer(Modifier.height(8.dp))
            InfoRow("Start", formatTimestamp(span.startTimeMs))
            span.endTimeMs?.let { endTime ->
                InfoRow("End", formatTimestamp(endTime))
                InfoRow("Duration", TraceStats.formatDuration(endTime - span.startTimeMs))
            }
        }

        span.usage?.let { usage ->
            DarkCard {
                Text("Token Usage", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
                Spacer(Modifier.height(8.dp))
                InfoRow("Input Tokens", usage.inputTokens.toString())
                InfoRow("Output Tokens", usage.outputTokens.toString())
                InfoRow("Total", (usage.inputTokens + usage.outputTokens).toString())
            }
        }

        span.error?.let { error ->
            ColorBorderCard(accentColor = ErrorColor, alpha = 0.12f) {
                Column {
                    Text("Error", fontWeight = FontWeight.Bold, color = ErrorColor, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${error.type}: ${error.message}", color = BrightText, fontSize = 12.sp)
                    error.stackTrace?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = DimText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        span.metadata?.let { metadata ->
            if (metadata.isNotEmpty()) {
                DarkCard {
                    Text("Metadata", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
                    Spacer(Modifier.height(8.dp))
                    metadata.forEach { (key, value) -> InfoRow(key, value) }
                }
            }
        }
    }
}

@Composable
private fun MessagesTab(span: TraceSpan) {
    val messages = span.messages ?: return

    LazyColumnMinimal {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            messages.forEachIndexed { index, msg ->
                val roleColor = when (msg.role.uppercase()) {
                    "USER" -> Color(0xFF42A5F5)
                    "ASSISTANT" -> Color(0xFF66BB6A)
                    "SYSTEM" -> Color(0xFFFFA726)
                    else -> DimText
                }

                ColorBorderCard(accentColor = roleColor, alpha = 0.08f) {
                    Column {
                        Text(
                            msg.role.uppercase(),
                            color = roleColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            msg.content,
                            color = BrightText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptTab(span: TraceSpan) {
    val prompt = span.requestInput ?: return
    MonoTextPane(prompt)
}

@Composable
private fun SchemaTab(span: TraceSpan) {
    val schema = span.schema ?: return
    MonoTextPane(schema)
}

@Composable
private fun ResponseTab(span: TraceSpan) {
    val response = span.responseOutput ?: return
    MonoTextPane(response)
}

@Composable
private fun MonoTextPane(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text,
            color = BrightText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun LazyColumnMinimal(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text("$label: ", color = DimText, fontSize = 12.sp)
        Text(value, color = BrightText, fontSize = 12.sp)
    }
}

private fun formatTimestamp(ms: Long): String {
    val date = java.util.Date(ms)
    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    return format.format(date)
}
