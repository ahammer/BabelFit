package ca.adamhammer.babelfit.agents.graph

import ca.adamhammer.babelfit.annotations.Terminal
import ca.adamhammer.babelfit.annotations.Transitions
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

/**
 * A directed graph of agent states (methods) and valid transitions between them.
 *
 * Constrains agent navigation so the AI can only choose from valid next steps,
 * reducing hallucination and token cost. Nodes with a single outgoing edge skip
 * the LLM decision call entirely.
 *
 * Build via annotations:
 * ```kotlin
 * val graph = AgentGraph.fromAnnotations(MyAgentAPI::class)
 * ```
 *
 * Or via DSL:
 * ```kotlin
 * val graph = agentGraph<MyAgentAPI> {
 *     start("observe")
 *     from("observe").to("checkAbilities", "commitAction")
 *     from("checkAbilities").to("commitAction")
 *     terminal("commitAction")
 * }
 * ```
 */
class AgentGraph<T : Any>(
    /** The starting node of the graph. */
    val startNode: String,
    /** All nodes (method names) in the graph. */
    val nodes: Set<String>,
    /** Directed edges: source method → set of valid target methods. */
    val edges: Map<String, Set<String>>,
    /** Terminal nodes that end the agent loop. */
    val terminalNodes: Set<String>
) {
    /** Returns the valid next methods from the given [currentNode]. */
    fun validTransitions(currentNode: String): Set<String> =
        edges[currentNode] ?: emptySet()

    /** Whether [nodeName] is a terminal (graph-ending) node. */
    fun isTerminal(nodeName: String): Boolean = nodeName in terminalNodes

    /** Whether [currentNode] has exactly one outgoing edge (deterministic transition). */
    fun isDeterministic(currentNode: String): Boolean =
        (edges[currentNode]?.size ?: 0) == 1

    /** Returns the single next node if deterministic, null otherwise. */
    fun deterministicNext(currentNode: String): String? =
        edges[currentNode]?.takeIf { it.size == 1 }?.first()

    /** Generate a Mermaid flowchart diagram of this graph. */
    fun toMermaid(): String = buildString {
        appendLine("graph TD")
        for ((from, tos) in edges) {
            for (to in tos) {
                val fromLabel = if (from == startNode) "$from([\"$from\"])" else from
                val toLabel = if (to in terminalNodes) "$to[/\"$to\"/]" else to
                appendLine("    $fromLabel --> $toLabel")
            }
        }
    }

    companion object {
        /**
         * Builds an [AgentGraph] from `@Transitions` and `@Terminal` annotations
         * on an interface's methods.
         *
         * The start node is the first method with `@Transitions` that is not a
         * target of any other method's transitions.
         */
        fun <T : Any> fromAnnotations(klass: KClass<T>): AgentGraph<T> {
            val functions = klass.declaredFunctions
            val allEdges = mutableMapOf<String, Set<String>>()
            val terminals = mutableSetOf<String>()
            val allNodes = mutableSetOf<String>()
            val allTargets = mutableSetOf<String>()

            for (fn in functions) {
                val transitions = fn.findAnnotation<Transitions>()
                if (transitions != null) {
                    val targets = transitions.to.toSet()
                    allEdges[fn.name] = targets
                    allNodes.add(fn.name)
                    allNodes.addAll(targets)
                    allTargets.addAll(targets)
                }
                if (fn.hasAnnotation<Terminal>()) {
                    terminals.add(fn.name)
                    allNodes.add(fn.name)
                }
            }

            // Start node: first node with transitions that is not a target of any other
            val roots = allEdges.keys - allTargets
            val startNode = roots.firstOrNull()
                ?: allEdges.keys.firstOrNull()
                ?: error("No @Transitions annotations found on ${klass.simpleName}")

            return AgentGraph(
                startNode = startNode,
                nodes = allNodes,
                edges = allEdges,
                terminalNodes = terminals
            )
        }
    }
}

/**
 * DSL builder for constructing an [AgentGraph].
 */
@ca.adamhammer.babelfit.BabelFitDsl
class AgentGraphBuilder<T : Any> {
    private var startNode: String? = null
    private val nodes = mutableSetOf<String>()
    private val edges = mutableMapOf<String, MutableSet<String>>()
    private val terminals = mutableSetOf<String>()

    /** Set the start node of the graph. */
    fun start(methodName: String) {
        startNode = methodName
        nodes.add(methodName)
    }

    /** Begin defining transitions from a method. Returns a [TransitionBuilder]. */
    fun from(methodName: String): TransitionBuilder {
        nodes.add(methodName)
        return TransitionBuilder(methodName)
    }

    /** Mark a method as a terminal (graph-ending) node. */
    fun terminal(methodName: String) {
        terminals.add(methodName)
        nodes.add(methodName)
    }

    inner class TransitionBuilder(private val fromMethod: String) {
        /** Define valid target methods from this node. */
        fun to(vararg targets: String): TransitionBuilder {
            val targetSet = edges.getOrPut(fromMethod) { mutableSetOf() }
            targetSet.addAll(targets)
            nodes.addAll(targets)
            return this
        }
    }

    @PublishedApi
    internal fun build(): AgentGraph<T> {
        val start = startNode
            ?: error("start() must be called in agentGraph {} builder")
        return AgentGraph(
            startNode = start,
            nodes = nodes,
            edges = edges.mapValues { it.value.toSet() },
            terminalNodes = terminals
        )
    }
}

/**
 * DSL entry point for building an [AgentGraph].
 *
 * ```kotlin
 * val graph = agentGraph<MyAPI> {
 *     start("observe")
 *     from("observe").to("plan", "act")
 *     from("plan").to("act")
 *     terminal("act")
 * }
 * ```
 */
inline fun <reified T : Any> agentGraph(block: AgentGraphBuilder<T>.() -> Unit): AgentGraph<T> =
    AgentGraphBuilder<T>().apply(block).build()
