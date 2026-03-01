package ca.adamhammer.babelfit.samples.traceviewer.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.adamhammer.babelfit.samples.common.*
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceStats

@Composable
fun TraceViewerApp() {
    val scope = rememberCoroutineScope()
    val controller = remember { ComposeTraceController(scope) }

    MaterialTheme(colors = babelFitDarkColors()) {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkSurface) {
            if (controller.trace == null) {
                WelcomeScreen(controller)
            } else {
                TraceScreen(controller)
            }
        }
    }
}

@Composable
private fun WelcomeScreen(controller: ComposeTraceController) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.widthIn(max = 480.dp),
            backgroundColor = DarkCardColor,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("BabelFit Trace Viewer", style = MaterialTheme.typography.h5, color = BrightText)
                Text(
                    "Load a .btrace.json file to analyze LLM interactions",
                    color = DimText
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "AI Provider (for analysis)",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.primary
                )
                VendorSelector(
                    vendors = Vendor.entries,
                    selected = controller.vendor,
                    onSelect = {
                        controller.vendor = it
                        controller.model = it.defaultModel
                    }
                )
                ModelDropdown(
                    models = controller.vendor.models,
                    selected = controller.model,
                    onSelect = { controller.model = it }
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { controller.openFileChooser() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
                ) {
                    Text("Open Trace File")
                }
            }
        }
    }
}

@Composable
private fun TraceScreen(controller: ComposeTraceController) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Toolbar(controller)

        // Main content
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Span tree panel
            SpanTreePanel(
                controller = controller,
                modifier = Modifier.width(350.dp).fillMaxHeight()
            )

            // Detail panel
            DetailPanel(
                controller = controller,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        // Status bar
        StatsBar(controller)
    }
}

@Composable
private fun Toolbar(controller: ComposeTraceController) {
    Surface(color = DarkCardAlt, elevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { controller.openFileChooser() },
                colors = ButtonDefaults.buttonColors(backgroundColor = DarkCardColor)
            ) {
                Text("Open Trace", color = BrightText)
            }

            Text(controller.traceFileName, color = DimText, fontSize = 13.sp)

            Spacer(Modifier.weight(1f))

            VendorSelector(
                vendors = Vendor.entries,
                selected = controller.vendor,
                onSelect = {
                    controller.vendor = it
                    controller.model = it.defaultModel
                }
            )

            ModelDropdown(
                models = controller.vendor.models,
                selected = controller.model,
                onSelect = { controller.model = it }
            )

            Button(
                onClick = { controller.analyzeTrace() },
                enabled = controller.trace != null && !controller.isAnalyzing,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
            ) {
                Text(if (controller.isAnalyzing) "Analyzing..." else "Analyze")
            }
        }
    }
}

@Composable
private fun StatsBar(controller: ComposeTraceController) {
    val stats = controller.stats ?: return

    Surface(color = DarkCardAlt, elevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem("Spans", stats.totalSpans.toString())
            StatItem("Duration", TraceStats.formatDuration(stats.totalDurationMs))
            StatItem("Tokens In", TraceStats.formatTokens(stats.totalInputTokens))
            StatItem("Tokens Out", TraceStats.formatTokens(stats.totalOutputTokens))
            StatItem("Errors", stats.errorCount.toString(), isError = stats.errorCount > 0)
            StatItem("Retries", stats.retryCount.toString(), isError = stats.retryCount > 0)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, isError: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = DimText, fontSize = 11.sp)
        Text(
            value,
            color = if (isError) ErrorColor else BrightText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
