package ca.adamhammer.babelfit.samples.dnd

import ca.adamhammer.babelfit.annotations.*
import ca.adamhammer.babelfit.annotations.Terminal
import ca.adamhammer.babelfit.samples.dnd.model.PlayerAction
import java.util.concurrent.Future

/**
 * AI interface for an autonomous party member. Each method represents a phase
 * of the AI character's decision-making process. Driven by [GraphAgent]
 * with deterministic transitions (no routing LLM calls needed).
 *
 * Flow: observeSituation → commitAction → (terminal)
 */
interface PlayerAgentAPI {

    @AiOperation(
        summary = "Observe and Plan",
        description = "Observe the current scene and situation from the world state. " +
                "Consider what is happening, who is present, what dangers or opportunities exist, " +
                "and how your character would perceive this scene given their backstory and skills. " +
                "Review the action log to remember what you and others have done recently. " +
                "Also review your inventory, health, skills, class abilities, and known tactical options " +
                "to decide what actions are realistically available this turn. " +
                "Consider party coordination — if you want to send a whisper to a teammate, " +
                "plan that now and include it in your commitAction."
    )
    @AiResponse(
        description = "Your character's observations, capability assessment, and tactical plan",
        responseClass = String::class
    )
    @Memorize(label = "Current observations and plan")
    @Transitions("commitAction")
    fun observeSituation(): Future<String>

    @AiOperation(
        summary = "Commit Action",
        description = "Based on your observations, capabilities, and team dynamics, commit to a final action for this turn. " +
                "Stay in character — consider your backstory, personality, class abilities, " +
                "and the needs of the party. Be decisive and specific. " +
                "State your action in ONE short sentence as a player command " +
                "(e.g. 'I attack the goblin with my longsword' or 'I search the chest for traps'). " +
                "Do NOT narrate what happens or describe the outcome — that is the DM's job. " +
                "Review your recent actions below. The world has evolved — react to what's new. " +
                "Vary your approach: try different abilities, talk to different NPCs, explore new options. " +
                "Repeating the same action has diminishing returns — the DM will escalate consequences. " +
                "You may optionally provide journalEntry, emotionalUpdate, goalUpdate, whisperTarget, and whisperMessage " +
                "to persist your own internal state and coordinate with teammates."
    )
    @AiResponse(
        description = "A short, specific player action (1-2 sentences max), not narration",
        responseClass = PlayerAction::class
    )
    @Terminal
    fun commitAction(
        @AiParameter(description = "Your recent actions")
        recentActions: String
    ): Future<PlayerAction>
}
