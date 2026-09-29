# BabelFit Agents

The `babelfit-agents` module provides abstractions for building multi-step and decision-making AI workflows on top of BabelFit's core proxy system.

## Features

- **`AutonomousAgent`**: a loop-driven agent that pairs a "brain" interface (`DecidingAgentAPI`) with an "action" interface. The brain decides which action to take next based on the current context.
- **`AgentDispatcher`**: routes tasks to a pool of specialized agents based on their capabilities.
- **`@Terminal`**: marks an action method as the end of an agent's turn, breaking the loop.

## Usage

Define an action interface for your agent, then construct both BabelFit APIs and the agent:

```kotlin
import ca.adamhammer.babelfit.adapters.OpenAiAdapter
import ca.adamhammer.babelfit.agents.AutonomousAgent
import ca.adamhammer.babelfit.agents.DecidingAgentAPI
import ca.adamhammer.babelfit.annotations.AiOperation
import ca.adamhammer.babelfit.annotations.Memorize
import ca.adamhammer.babelfit.annotations.Terminal
import ca.adamhammer.babelfit.babelFit
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Future

interface PlayerAgentAPI {
    @AiOperation(description = "Look around the room")
    @Memorize("observations")
    fun observeSituation(): Future<String>

    @AiOperation(description = "Take a final action")
    @Terminal
    fun commitAction(action: String): Future<String>
}

fun main() = runBlocking {
    val adapter = OpenAiAdapter()
    val apiInstance = babelFit<PlayerAgentAPI> { adapter(adapter) }
    val decider = babelFit<DecidingAgentAPI> { adapter(adapter) }.api
    val agent = AutonomousAgent(apiInstance, decider)

    // Loop for up to five dispatches, stopping early when a @Terminal method is selected.
    val result = agent.runDetailedSuspend(maxSteps = 5)

    // stepDetailedSuspend() dispatches exactly one action without running the loop.
}
```
