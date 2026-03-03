package ca.adamhammer.babelfit.samples.dnd

import ca.adamhammer.babelfit.annotations.*
import ca.adamhammer.babelfit.annotations.Terminal
import ca.adamhammer.babelfit.samples.dnd.model.PlayerAction
import java.util.concurrent.Future

/**
 * AI interface for an autonomous party member. Each method represents a phase
 * of the AI character's decision-making process. Driven by [GraphAgent]
 * with LLM-driven transitions at multi-edge nodes.
 *
 * Flow: observeSituation → assessThreats|planCoordination|commitAction
 *       assessThreats → planCoordination|commitAction
 *       planCoordination → commitAction (deterministic)
 *       reactToWhispers → observeSituation (deterministic, alternate entry point)
 */
interface PlayerAgentAPI {

    @AiOperation(
        summary = "React to Whispers",
        description = "You have received private whispers from party members. " +
                "Use read_whispers to see what was said. " +
                "Consider how these messages affect your plans, your relationships, " +
                "and your emotional state. Note any tactical coordination suggested. " +
                "This context will inform your observations and action this turn."
    )
    @AiResponse(
        description = "Your reaction to whispers — how they affect your plans and emotional state",
        responseClass = String::class
    )
    @Memorize(label = "Whisper reactions")
    @Transitions("observeSituation")
    fun reactToWhispers(): Future<String>

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
    @Transitions("assessThreats", "planCoordination", "commitAction")
    fun observeSituation(): Future<String>

    @AiOperation(
        summary = "Assess Threats and Opportunities",
        description = "Evaluate the tactical situation using your tools. " +
                "Use inspect_location to check exits and NPCs. " +
                "Use inspect_party_status to see who needs healing or support. " +
                "Use inspect_npc to learn about nearby NPCs' motivations. " +
                "Determine: Are there threats to respond to? Allies in need? " +
                "Opportunities to exploit with your class abilities? " +
                "Decide whether you need to coordinate with teammates before acting."
    )
    @AiResponse(
        description = "Threat and opportunity assessment based on tool queries",
        responseClass = String::class
    )
    @Memorize(label = "Threat assessment")
    @Transitions("planCoordination", "commitAction")
    fun assessThreats(): Future<String>

    @AiOperation(
        summary = "Plan Coordination",
        description = "Review what other party members are doing this round. " +
                "Use check_party_intentions to see committed actions. " +
                "Use read_whispers to check for private messages from teammates. " +
                "Decide how to complement the party's efforts: " +
                "if others are fighting, consider support or flanking; " +
                "if others are socializing, consider scouting or guarding; " +
                "if someone whispered a plan, decide whether to follow it. " +
                "Choose an action that adds unique value based on your class role."
    )
    @AiResponse(
        description = "Coordination plan considering party intentions and whispers",
        responseClass = String::class
    )
    @Memorize(label = "Coordination plan")
    @Transitions("commitAction")
    fun planCoordination(): Future<String>

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
