package ca.adamhammer.babelfit.agents.graph

import ca.adamhammer.babelfit.BabelFitInstance
import ca.adamhammer.babelfit.agents.AgentDispatcher
import ca.adamhammer.babelfit.agents.AiArg
import ca.adamhammer.babelfit.agents.AiDecision
import ca.adamhammer.babelfit.agents.DecidingAgentAPI
import ca.adamhammer.babelfit.agents.decide
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger

/**
 * An agent that navigates an [AgentGraph], constraining LLM decisions to only
 * valid transitions from the current node. When a node has a single outgoing
 * edge, the transition is deterministic and skips the LLM decision call entirely.
 *
 * Usage:
 * ```kotlin
 * val graph = agentGraph<PlayerAgentAPI> {
 *     start("observeSituation")
 *     from("observeSituation").to("checkAbilities", "commitAction")
 *     from("checkAbilities").to("whisper", "commitAction")
 *     from("whisper").to("commitAction")
 *     terminal("commitAction")
 * }
 *
 * val agent = GraphAgent(apiInstance, decider, graph)
 * val result = agent.run(maxSteps = 10)
 * ```
 */
class GraphAgent<T : Any>(
    private val apiInstance: BabelFitInstance<T>,
    private val decider: DecidingAgentAPI,
    private val graph: AgentGraph<T>,
    private val initialArgs: Map<String, String> = emptyMap()
) {
    private val logger = Logger.getLogger(GraphAgent::class.java.name)
    private val dispatcher = AgentDispatcher(apiInstance)
    private var currentNode: String = graph.startNode
    private var isFirstStep = true
    private var lastStepValue: String? = null

    /** Backward-compatible constructor without initialArgs. */
    constructor(
        apiInstance: BabelFitInstance<T>,
        decider: DecidingAgentAPI,
        graph: AgentGraph<T>
    ) : this(apiInstance, decider, graph, emptyMap())

    /** Returns the current node in the graph. */
    val current: String get() = currentNode

    /** Reset the agent to the starting node. */
    fun reset() {
        currentNode = graph.startNode
        isFirstStep = true
        lastStepValue = null
    }

    /** Execute one step: dispatch the current node, then transition. Blocking. */
    fun step(): AgentDispatcher.DispatchResult = runBlocking { stepSuspend() }

    /** Execute one step: dispatch the current node, then transition. Suspending. */
    suspend fun stepSuspend(): AgentDispatcher.DispatchResult {
        // 1. Dispatch the current node (use initialArgs on the first step, chain results after)
        val args = if (isFirstStep && initialArgs.isNotEmpty()) {
            isFirstStep = false
            initialArgs.map { (k, v) -> AiArg(k, v) }
        } else if (!isFirstStep && lastStepValue != null) {
            listOf(AiArg("input", lastStepValue!!))
        } else {
            isFirstStep = false
            emptyList()
        }
        val result = dispatcher.dispatchWithMetadata(
            AiDecision(method = currentNode, args = args)
        )

        // Capture result for next step
        lastStepValue = result.value?.toString()

        // 2. If terminal, we're done
        if (graph.isTerminal(currentNode)) {
            return result
        }

        // 3. Transition to next node
        currentNode = if (graph.isDeterministic(currentNode)) {
            // Single outgoing edge = skip LLM decision
            val next = graph.deterministicNext(currentNode)!!
            logger.fine { "Deterministic transition: $currentNode → $next" }
            next
        } else {
            // Multiple edges = ask the LLM, constrained to valid transitions
            val validMethods = graph.validTransitions(currentNode)
            val excludedMethods = graph.nodes
                .filter { it !in validMethods }
                .toSet()
            val decision = decider.decide(apiInstance, excludedMethods).get()
            val resolved = dispatcher.resolveMethod(decision.method).name
            if (resolved !in validMethods) {
                logger.warning {
                    "LLM chose '$resolved' not valid from '$currentNode'. Valid: $validMethods"
                }
                // Fall back to first valid transition
                validMethods.first()
            } else {
                resolved
            }
        }

        return result
    }

    /** Run the graph until a terminal node is reached or [maxSteps] is exhausted. Blocking. */
    fun run(maxSteps: Int): AgentDispatcher.DispatchResult = runBlocking { runSuspend(maxSteps) }

    /** Run the graph until a terminal node is reached or [maxSteps] is exhausted. Suspending. */
    suspend fun runSuspend(maxSteps: Int): AgentDispatcher.DispatchResult {
        require(maxSteps > 0) { "maxSteps must be > 0" }

        var lastResult = AgentDispatcher.DispatchResult(
            methodName = "",
            value = "",
            isTerminal = false
        )

        repeat(maxSteps) {
            lastResult = stepSuspend()
            if (lastResult.isTerminal || graph.isTerminal(lastResult.methodName)) {
                return lastResult
            }
        }

        return lastResult
    }
}
