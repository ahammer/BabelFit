package ca.adamhammer.babelfit.samples.traceviewer.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.adamhammer.babelfit.adapters.ClaudeAdapter
import ca.adamhammer.babelfit.adapters.GeminiAdapter
import ca.adamhammer.babelfit.adapters.OpenAiAdapter
import ca.adamhammer.babelfit.debug.trace.SpanType
import ca.adamhammer.babelfit.debug.trace.TraceExport
import ca.adamhammer.babelfit.debug.trace.TraceSpan
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.samples.common.Vendor
import ca.adamhammer.babelfit.samples.traceviewer.api.SpanNode
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceAnalysisSession
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceLoader
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceStatistics
import ca.adamhammer.babelfit.samples.traceviewer.api.TraceStats
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceAnalysis
import ca.adamhammer.babelfit.samples.traceviewer.models.SpanAssessment
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceReport
import com.openai.models.ChatModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

data class FlatSpanEntry(
    val span: TraceSpan,
    val depth: Int,
    val isExpanded: Boolean = true,
    val hasChildren: Boolean = false
)

class ComposeTraceController(
    private val scope: CoroutineScope
) {
    var trace by mutableStateOf<TraceExport?>(null)
        private set
    var spanRoots by mutableStateOf<List<SpanNode>>(emptyList())
        private set
    var stats by mutableStateOf<TraceStatistics?>(null)
        private set
    var analysis by mutableStateOf<TraceAnalysis?>(null)
        private set
    var selectedSpan by mutableStateOf<TraceSpan?>(null)
        private set
    var isAnalyzing by mutableStateOf(false)
        private set
    var traceFileName by mutableStateOf("")
        private set
    var assessingSpanId by mutableStateOf<String?>(null)
        private set
    var generatedReport by mutableStateOf<TraceReport?>(null)
        private set
    var isGeneratingReport by mutableStateOf(false)
        private set

    val spanAssessments = mutableStateMapOf<String, SpanAssessment>()

    var vendor by mutableStateOf(Vendor.OPENAI)
    var model by mutableStateOf(Vendor.OPENAI.defaultModel)

    val flatSpans = mutableStateListOf<FlatSpanEntry>()
    private val collapsedIds = mutableSetOf<String>()

    fun loadTrace(file: File) {
        trace = TraceLoader.loadFromFile(file)
        spanRoots = TraceLoader.buildSpanTree(trace!!)
        stats = TraceStats.computeStats(trace!!)
        traceFileName = file.name
        selectedSpan = null
        analysis = null
        spanAssessments.clear()
        generatedReport = null
        collapsedIds.clear()
        rebuildFlatList()
    }

    fun openFileChooser() {
        val chooser = javax.swing.JFileChooser().apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "BabelFit Trace files (*.btrace.json)", "json"
            )
            dialogTitle = "Open Trace File"
        }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            loadTrace(chooser.selectedFile)
        }
    }

    fun selectSpan(span: TraceSpan) {
        selectedSpan = span
    }

    fun toggleExpanded(spanId: String) {
        if (collapsedIds.contains(spanId)) {
            collapsedIds.remove(spanId)
        } else {
            collapsedIds.add(spanId)
        }
        rebuildFlatList()
    }

    fun analyzeTrace() {
        val currentTrace = trace ?: return
        if (isAnalyzing) return

        isAnalyzing = true
        analysis = null
        scope.launch {
            try {
                val adapter = createAdapter()
                val session = TraceAnalysisSession(apiAdapter = adapter)
                analysis = session.analyze(currentTrace)
            } catch (e: Exception) {
                // Analysis failed — leave analysis null
                e.printStackTrace()
            } finally {
                isAnalyzing = false
            }
        }
    }

    fun assessSpan(span: TraceSpan) {
        val currentTrace = trace ?: return
        if (assessingSpanId != null) return

        assessingSpanId = span.id
        scope.launch {
            try {
                val adapter = createAdapter()
                val session = TraceAnalysisSession(apiAdapter = adapter)
                val assessment = session.assessSpan(span, currentTrace)
                spanAssessments[span.id] = assessment
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                assessingSpanId = null
            }
        }
    }

    fun generateReport() {
        val currentTrace = trace ?: return
        if (isGeneratingReport) return

        isGeneratingReport = true
        generatedReport = null
        scope.launch {
            try {
                val adapter = createAdapter()
                val session = TraceAnalysisSession(apiAdapter = adapter)
                generatedReport = session.generateReport(currentTrace)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isGeneratingReport = false
            }
        }
    }

    private fun createAdapter(): ApiAdapter {
        return when (vendor) {
            Vendor.OPENAI -> OpenAiAdapter(model = ChatModel.of(model))
            Vendor.ANTHROPIC -> ClaudeAdapter(model = model)
            Vendor.GEMINI -> GeminiAdapter(model = model)
        }
    }

    private fun rebuildFlatList() {
        flatSpans.clear()
        fun walk(node: SpanNode, depth: Int) {
            val hasChildren = node.children.isNotEmpty()
            val isExpanded = !collapsedIds.contains(node.span.id)
            flatSpans.add(FlatSpanEntry(node.span, depth, isExpanded, hasChildren))
            if (isExpanded) {
                node.children.sortedBy { it.span.startTimeMs }.forEach { walk(it, depth + 1) }
            }
        }
        spanRoots.forEach { walk(it, 0) }
    }

    companion object {
        fun spanTypeColor(span: TraceSpan): Long = when (span.type) {
            SpanType.SESSION -> 0xFF42A5F5   // blue
            SpanType.REQUEST -> 0xFF66BB6A   // green
            SpanType.ATTEMPT -> if (span.error == null) 0xFF66BB6A else 0xFFEF5350 // green/red
            SpanType.TOOL_CALL -> 0xFFAB47BC // purple
            SpanType.AGENT -> 0xFFFFA726    // orange
            SpanType.STEP -> 0xFF78909C     // grey
        }

        fun spanTypeIcon(span: TraceSpan): String = when (span.type) {
            SpanType.SESSION -> "⬢"
            SpanType.REQUEST -> "▶"
            SpanType.ATTEMPT -> if (span.error == null) "✓" else "✗"
            SpanType.TOOL_CALL -> "⚙"
            SpanType.AGENT -> "◎"
            SpanType.STEP -> "○"
        }
    }
}
