package ca.adamhammer.babelfit.samples.traceviewer.api

import ca.adamhammer.babelfit.debug.trace.TraceExport
import ca.adamhammer.babelfit.debug.trace.TraceSpan
import kotlinx.serialization.json.Json
import java.io.File

data class SpanNode(
    val span: TraceSpan,
    val children: MutableList<SpanNode> = mutableListOf(),
    val depth: Int = 0
)

object TraceLoader {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadFromFile(file: File): TraceExport {
        val text = file.readText()
        return json.decodeFromString(TraceExport.serializer(), text)
    }

    fun buildSpanTree(trace: TraceExport): List<SpanNode> {
        val nodeMap = mutableMapOf<String, SpanNode>()
        val roots = mutableListOf<SpanNode>()

        // Create nodes
        for (span in trace.spans) {
            nodeMap[span.id] = SpanNode(span)
        }

        // Link parent-child
        for (span in trace.spans) {
            val node = nodeMap[span.id]!!
            val parentId = span.parentId
            if (parentId != null && nodeMap.containsKey(parentId)) {
                nodeMap[parentId]!!.children.add(node)
            } else {
                roots.add(node)
            }
        }

        // Set depths
        fun setDepths(node: SpanNode, depth: Int) {
            node.children.forEach { setDepths(it, depth + 1) }
        }
        roots.forEach { setDepths(it, 0) }

        return roots
    }

    /** Flatten a tree into a depth-first ordered list of (span, depth) pairs */
    fun flattenTree(roots: List<SpanNode>): List<Pair<TraceSpan, Int>> {
        val result = mutableListOf<Pair<TraceSpan, Int>>()
        fun walk(node: SpanNode, depth: Int) {
            result.add(node.span to depth)
            node.children.sortedBy { it.span.startTimeMs }.forEach { walk(it, depth + 1) }
        }
        roots.forEach { walk(it, 0) }
        return result
    }
}
