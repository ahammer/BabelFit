package ca.adamhammer.babelfit.samples.traceviewer.api

import ca.adamhammer.babelfit.debug.trace.TraceExport
import ca.adamhammer.babelfit.samples.traceviewer.models.TraceAnalysis

interface TraceEventListener {
    fun onTraceLoaded(trace: TraceExport, roots: List<SpanNode>, stats: TraceStatistics) {}
    fun onAnalysisStarted() {}
    fun onAnalysisComplete(analysis: TraceAnalysis) {}
    fun onError(error: String) {}
}
