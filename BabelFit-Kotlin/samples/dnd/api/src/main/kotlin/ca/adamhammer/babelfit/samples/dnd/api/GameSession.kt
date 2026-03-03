package ca.adamhammer.babelfit.samples.dnd.api

import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.interfaces.RequestListener
import ca.adamhammer.babelfit.agents.DecidingAgentAPI
import ca.adamhammer.babelfit.agents.AgentDispatcher
import ca.adamhammer.babelfit.agents.AiDecision
import ca.adamhammer.babelfit.agents.graph.GraphAgent
import ca.adamhammer.babelfit.agents.graph.AgentGraph
import ca.adamhammer.babelfit.model.ImageResult
import ca.adamhammer.babelfit.samples.dnd.*
import ca.adamhammer.babelfit.samples.dnd.model.*
import ca.adamhammer.babelfit.BabelFitInstance
import ca.adamhammer.babelfit.babelFit
import ca.adamhammer.babelfit.utils.toJsonString
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Suppress("LongMethod", "LargeClass", "CyclomaticComplexMethod", "TooManyFunctions", "SwallowedException", "MaxLineLength")
class GameSession(
    private val apiAdapter: ApiAdapter,
    private val listener: GameEventListener,
    maxTurns: Int = 3,
    private val enableImages: Boolean = true,
    private val artStyle: String = "Anime",
    private val requestListeners: List<RequestListener> = emptyList()
) {
    companion object {
        private const val DEFAULT_MAX_TURNS = 3
        private const val MAX_ALLOWED_TURNS = 100
        private const val AGENT_TURN_BUDGET = 5
        private const val IMAGE_THREAD_JOIN_TIMEOUT_MS = 3000L
        private val SELECTION_WEIGHTS = intArrayOf(4, 3, 2, 1)
    }

    private val maxTurnsConfigured = maxTurns.coerceIn(DEFAULT_MAX_TURNS, MAX_ALLOWED_TURNS)

    private lateinit var world: World
    private lateinit var dmInstance: BabelFitInstance<DungeonMasterAPI>
    private val dm: DungeonMasterAPI get() = dmInstance.api
    private lateinit var aiAgents: Map<String, GraphAgent<PlayerAgentAPI>>
    private val turnStates: MutableMap<String, TurnState> = mutableMapOf()
    private val turnHistories: MutableMap<String, MutableList<String>> = mutableMapOf()
    private val characterPreviousActions: MutableMap<String, MutableList<String>> = mutableMapOf()
    private val markdownTimeline: MutableList<MarkdownEntry> = Collections.synchronizedList(mutableListOf())
    private val imageWorkers: MutableList<Thread> = Collections.synchronizedList(mutableListOf())
    private val deadCharacters: MutableList<Character> = mutableListOf()
    private val roundIntentions: ConcurrentHashMap<String, PlayerAction> = ConcurrentHashMap()
    private var teamImagePrompt: String = ""
    private var openingScene: SceneDescription = SceneDescription()

    suspend fun startGame(initialWorld: World) {
        world = initialWorld.copy(maxRounds = maxTurnsConfigured)

        dmInstance = babelFit<DungeonMasterAPI> {
            adapter(apiAdapter)
            addInterceptor(WorldStateInterceptor { world })
            toolProvider(DmToolProvider { world })
            requestListeners.forEach { listener(it) }
            resilience {
                maxRetries = 2
                retryDelayMs = 500
                resultValidator = { r ->
                    when (r) {
                        is ActionResult -> r.narrative.isNotBlank() && r.hpChange in -15..15
                        is SceneDescription -> r.narrative.isNotBlank()
                        is RoundSummaryResult -> r.narrative.isNotBlank()
                        is RoundOutcomeProposals -> r.candidates.count { it.narrative.isNotBlank() } >= 2
                        is ActionOutcomeProposals -> r.candidates.count { it.narrative.isNotBlank() } >= 2
                        else -> true
                    }
                }
            }
        }

        openingScene = buildWorldSetup()
        aiAgents = buildAiAgents(world)

        markdownTimeline += MarkdownEntry.SystemMessage("Game Started", "${world.party.size} adventurers set out from ${world.location.name}.")
        markdownTimeline += MarkdownEntry.DmNarration("Opening Scene", openingScene.narrative, "scene")
        listener.onSceneDescription(openingScene)
        if (enableImages) {
            requestSceneImage()
            requestTeamImage()
        }

        runGameLoop()
    }

    private fun buildWorldSetup(): SceneDescription {
        listener.onWorldBuildingStep("start", "The DM is constructing campaign lore and an opening situation.")

        val worldBuilder = babelFit<DungeonMasterWorldBuilderAPI> {
            adapter(apiAdapter)
            addInterceptor(WorldStateInterceptor { world })
            requestListeners.forEach { listener(it) }
            resilience {
                maxRetries = 2
                retryDelayMs = 500
            }
        }.api

        val steps = listOf(
            "buildCampaignPremise" to { worldBuilder.buildCampaignPremise().get() as Any },
            "buildLocationGraph" to { worldBuilder.buildLocationGraph().get() as Any },
            "buildNpcRegistry" to { worldBuilder.buildNpcRegistry().get() as Any },
            "buildPlotHooks" to { worldBuilder.buildPlotHooks().get() as Any }
        )

        for ((stepName, stepFn) in steps) {
            try {
                val result = stepFn()
                val detail = result.toString().ifBlank { "No details" }
                listener.onWorldBuildingStep(stepName, detail)
                markdownTimeline += MarkdownEntry.WorldBuildStep(stepName, detail)
            } catch (e: Exception) {
                val failDetail = "Step failed (${e.message?.take(120)}); continuing with remaining steps."
                listener.onWorldBuildingStep(stepName, failDetail)
                markdownTimeline += MarkdownEntry.WorldBuildStep(stepName, failDetail)
            }
        }

        val worldBuildResult = try {
            worldBuilder.commitWorldSetup().get()
        } catch (e: Exception) {
            listener.onWorldBuildingStep(
                "fallback",
                "World setup commit failed (${e.message?.take(80)}); using default opening scene."
            )
            return dm.describeScene().get()
        }

        val detail = worldBuildResult.toString().ifBlank { "No details" }
        listener.onWorldBuildingStep("commitWorldSetup", detail)

        val mergedLore = world.lore.copy(
            campaignPremise = worldBuildResult.campaignPremise.ifBlank { world.lore.campaignPremise },
            locations = worldBuildResult.lore.locations.ifEmpty { world.lore.locations },
            npcs = worldBuildResult.lore.npcs.ifEmpty { world.lore.npcs },
            plotHooks = worldBuildResult.lore.plotHooks.ifEmpty { world.lore.plotHooks },
            factions = worldBuildResult.lore.factions.ifEmpty { world.lore.factions }
        )

        val startLocation = worldBuildResult.startingLocation
        val location = if (startLocation.name.isNotBlank()) {
            val seededNpcs = mergedLore.npcs
                .filter { it.currentLocation.equals(startLocation.name, ignoreCase = true) }
                .map { it.name }
                .ifEmpty { startLocation.npcs }
            startLocation.copy(npcs = seededNpcs)
        } else world.location
        val newQuests = (world.questLog + worldBuildResult.initialQuests).distinct()

        world = world.copy(
            lore = mergedLore,
            location = location,
            questLog = newQuests
        )

        val commitDetail = "World setup committed with ${mergedLore.npcs.size} NPC(s) and ${mergedLore.locations.size} location node(s)."
        listener.onWorldBuildingStep("commit", commitDetail)
        markdownTimeline += MarkdownEntry.WorldBuildStep("commit", commitDetail)

        return worldBuildResult.openingScene.takeIf { it.narrative.isNotBlank() }
            ?: dm.describeScene().get()
    }

    private suspend fun runGameLoop() {
        while (world.party.isNotEmpty() && world.round < maxTurnsConfigured) {
            world = world.copy(
                round = world.round + 1,
                turnsAtCurrentLocation = world.turnsAtCurrentLocation + 1
            )
            listener.onRoundStarted(world.round, world)
            markdownTimeline += MarkdownEntry.RoundHeader(world.round, world.location.name)

            processRound()

            if (world.party.isEmpty()) {
                finishGame()
                return
            }

            val proposals = dm.proposeRoundOutcomes().get()
            val selected = weightedRandomSelect(proposals.candidates) { it.engagementScore }
            val summary = selected.toRoundSummaryResult()
            val selectionLog = "Round ${world.round}: \uD83C\uDFAF DM chose '${selected.category}' " +
                "(score: ${selected.engagementScore}/10)"
            world = world.copy(actionLog = (world.actionLog + selectionLog).takeLast(40))
            world = applyRoundSummary(world, summary)
            checkForDeaths()
            val sceneDescription = SceneDescription(
                narrative = summary.narrative,
                availableActions = summary.availableActions
            )
            markdownTimeline += MarkdownEntry.DmNarration("Round ${world.round} Summary", summary.narrative, "summary")
            listener.onRoundSummary(sceneDescription, world)
            // Start scene image generation early — only needs narrative, not fully applied world state
            if (enableImages) requestSceneImage()
        }

        finishGame()
    }

    private suspend fun processRound() {
        val currentRoundCharacters = world.party.filter { it.hp > 0 }
        roundIntentions.clear()

        // Pipeline: players produce actions as they finish, DM consumes and resolves sequentially
        val actionChannel = Channel<Pair<String, PlayerAction>>(Channel.UNLIMITED)

        coroutineScope {
            // Producers: each player agent thinks in parallel, sends result as soon as done
            val producers = currentRoundCharacters.map { character ->
                launch {
                    val currentStats = world.party.find { it.name == character.name } ?: return@launch
                    val playerAction = resolveCharacterAction(currentStats)
                    roundIntentions[currentStats.name] = playerAction
                    actionChannel.send(currentStats.name to playerAction)
                }
            }

            // Consumer: DM resolves actions sequentially as they arrive (preserves world-state awareness)
            launch {
                var resolved = 0
                for ((characterName, playerAction) in actionChannel) {
                    resolveAndApplyAction(characterName, playerAction)
                    resolved++
                    if (resolved >= currentRoundCharacters.size) break
                }
            }

            // Close channel when all producers complete
            producers.forEach { it.join() }
            actionChannel.close()
        }
    }

    private suspend fun resolveAndApplyAction(characterName: String, playerAction: PlayerAction) {
        val currentStats = world.party.find { it.name == characterName } ?: return
        if (currentStats.hp <= 0) return

        val action = playerAction.action.ifBlank { "I hold my position and reassess." }

        val actionProposals = dm.proposeActionOutcomes(currentStats.name, action).get()
        val selectedAction = weightedRandomSelect(actionProposals.candidates) { it.engagementScore }
        dmInstance.memoryStore.put("Last action proposals", selectedAction.toJsonString())
        val result = selectedAction.toActionResult()
        val actionSelLog = "Round ${world.round}: \uD83C\uDFAF ${currentStats.name} outcome '${selectedAction.category}' " +
            "(score: ${selectedAction.engagementScore}/10)"
        world = world.copy(actionLog = (world.actionLog + actionSelLog).takeLast(40))
        val finalResult = handleDiceRoll(currentStats, result)

        world = applyResult(world, finalResult, currentStats.name)
        world = applyPlayerStateUpdate(world, currentStats.name, playerAction)
        checkForDeaths()

        val logEntry = "Round ${world.round}: ${currentStats.name} — $action → " +
            (if (finalResult.success) "success" else "failure") + ". DM: ${finalResult.narrative.take(150)}"

        characterPreviousActions.getOrPut(currentStats.name) { mutableListOf() }
            .apply { add(logEntry); if (size > 5) removeFirst() }

        world = world.copy(
            actionLog = (world.actionLog + logEntry).takeLast(40)
        )
        markdownTimeline += MarkdownEntry.CharacterAction(
            characterName = currentStats.name,
            action = action,
            outcome = finalResult.narrative,
            success = finalResult.success
        )
        listener.onActionResult(finalResult, world)
        if (enableImages) requestActionImage(currentStats.name, action, finalResult.narrative)
    }

    private suspend fun resolveCharacterAction(character: Character): PlayerAction {
        return resolveAiAction(character)
    }

    private suspend fun resolveAiAction(character: Character): PlayerAction {
        listener.onCharacterThinking(character.name)
        val agent = aiAgents[character.name]
        val turnHistory = mutableListOf<String>()
        turnHistories[character.name] = turnHistory
        val prevActions = characterPreviousActions[character.name] ?: emptyList()
        turnStates[character.name] = TurnState(
            phase = "OBSERVE",
            stepsUsed = 0,
            stepsBudget = AGENT_TURN_BUDGET,
            recentSteps = turnHistory,
            previousRoundActions = prevActions
        )
        if (agent != null) {
            try {
                val hasUnreadWhispers = world.whisperLog.any {
                    it.to.equals(character.name, ignoreCase = true) && it.round >= world.round - 1
                }
                if (hasUnreadWhispers) {
                    agent.resetTo("reactToWhispers")
                } else {
                    agent.reset()
                }
                for (stepIndex in 0 until AGENT_TURN_BUDGET) {
                    val stepResult = try {
                        agent.stepSuspend()
                    } catch (e: Exception) {
                        listener.onAgentStep(
                            characterName = character.name,
                            stepNumber = stepIndex,
                            methodName = "error",
                            details = "Step failed: ${e.message?.take(120)}",
                            terminal = false,
                            pointsSpent = stepIndex,
                            pointsRemaining = (AGENT_TURN_BUDGET - stepIndex - 1).coerceAtLeast(0)
                        )
                        break
                    }

                    turnHistory += "${stepResult.methodName}: ${summarizeStepValue(stepResult.value)}"
                    val phase = if (stepResult.isTerminal) "DONE" else "PLAN"
                    turnStates[character.name] = TurnState(
                        phase = phase,
                        stepsUsed = stepIndex + 1,
                        stepsBudget = AGENT_TURN_BUDGET,
                        recentSteps = turnHistory,
                        previousRoundActions = prevActions
                    )
                    listener.onAgentStep(
                        characterName = character.name,
                        stepNumber = stepIndex,
                        methodName = stepResult.methodName,
                        details = summarizeStepValue(stepResult.value),
                        terminal = stepResult.isTerminal,
                        pointsSpent = stepIndex + 1,
                        pointsRemaining = (AGENT_TURN_BUDGET - stepIndex - 1).coerceAtLeast(0)
                    )

                    if (stepResult.isTerminal) {
                        return finalizeAction(character, stepResult, "Graph agent completed.")
                    }
                }
            } catch (e: Exception) {
                listener.onAgentStep(
                    characterName = character.name,
                    stepNumber = -1,
                    methodName = "error",
                    details = "Agent failed: ${e.message?.take(120) ?: "unknown error"}; using default action.",
                    terminal = true,
                    pointsSpent = 0,
                    pointsRemaining = 0
                )
            }
        }
        turnStates[character.name] = TurnState(
            phase = "DONE",
            stepsUsed = AGENT_TURN_BUDGET,
            stepsBudget = AGENT_TURN_BUDGET,
            recentSteps = turnHistory,
            previousRoundActions = prevActions
        )
        val fallback = PlayerAction(
            action = "I look around cautiously.",
            reasoning = "Fallback action after agent failure."
        )
        listener.onCharacterAction(character.name, fallback.action)
        return fallback
    }

    private fun finalizeAction(
        character: Character,
        stepResult: AgentDispatcher.DispatchResult,
        fallbackReasoning: String
    ): PlayerAction {
        val playerAction = when (val value = stepResult.value) {
            is PlayerAction -> value
            else -> PlayerAction(
                action = parseAiAction(value?.toString() ?: "", character.name),
                reasoning = fallbackReasoning
            )
        }
        val actionText = playerAction.action.ifBlank { "I hold my position and reassess." }
        listener.onCharacterAction(character.name, actionText)
        if (playerAction.whisperTarget.isNotBlank() && playerAction.whisperMessage.isNotBlank()) {
            listener.onWhisper(character.name, playerAction.whisperTarget, playerAction.whisperMessage)
            markdownTimeline += MarkdownEntry.Whisper(character.name, playerAction.whisperTarget, playerAction.whisperMessage)
        }
        return playerAction
    }

    private fun handleCharacterDeath(character: Character) {
        world = world.copy(party = world.party.filter { it.name != character.name })
        deadCharacters += character
        val deathLog = "Round ${world.round}: \u2620\uFE0F ${character.name} has fallen!"
        world = world.copy(actionLog = (world.actionLog + deathLog).takeLast(40))
        markdownTimeline += MarkdownEntry.SystemMessage("Character Death", "\u2620\uFE0F ${character.name} has fallen!")
        listener.onCharacterDeath(character.name, world)
    }

    private fun checkForDeaths() {
        val fallen = world.party.filter { it.hp <= 0 }
        for (character in fallen) {
            handleCharacterDeath(character)
        }
    }

    private fun summarizeStepValue(value: Any?): String {
        val raw = when (value) {
            is PlayerAction -> {
                val actionText = value.action.ifBlank { "(no action)" }
                val reason = value.reasoning.ifBlank { "(no reasoning)" }
                "action=$actionText | reasoning=$reason"
            }
            null -> "(no output)"
            else -> value.toString()
        }
        return raw.replace("\n", " ")
    }

    private suspend fun handleDiceRoll(character: Character, result: ActionResult): ActionResult {
        val roll = result.diceRollRequest ?: return result
        listener.onDiceRollRequested(character, roll)
        markdownTimeline += MarkdownEntry.DiceRoll(character.name, roll.rollType, roll.difficulty)
        val diceValue = Random.nextInt(1, 21)

        val normalizedModifier = normalizeRollModifier(character, roll)

        val total = diceValue + normalizedModifier
        val success = total >= roll.difficulty

        listener.onDiceRollResult(
            characterName = character.name,
            rollType = roll.rollType,
            rollValue = diceValue,
            modifier = normalizedModifier,
            total = total,
            difficulty = roll.difficulty,
            success = success
        )

        val modSign = if (normalizedModifier >= 0) "+" else ""
        val rollLog = "Round ${world.round}: \uD83C\uDFB2 ${character.name} ${roll.rollType}: " +
            "d20($diceValue) $modSign$normalizedModifier = $total vs DC ${roll.difficulty} \u2192 " +
            if (success) "SUCCESS" else "FAIL"
        world = world.copy(actionLog = (world.actionLog + rollLog).takeLast(40))

        return dm.resolveRoll(
            roll.characterName,
            roll.rollType,
            diceValue,
            normalizedModifier,
            roll.difficulty,
            total,
            success
        ).get()
    }

    private fun normalizeRollModifier(character: Character, roll: DiceRollRequest): Int {
        val expected = CharacterUtils.expectedRollModifier(character, roll.rollType)
        if (expected == null) {
            return roll.modifier
        }
        return expected
    }

    private fun buildAiAgents(world: World): Map<String, GraphAgent<PlayerAgentAPI>> {
        val agents = mutableMapOf<String, GraphAgent<PlayerAgentAPI>>()
        val graph = AgentGraph.fromAnnotations(PlayerAgentAPI::class)

        for (character in world.party) {
            val characterProvider = {
                this@GameSession.world.party.first { it.name == character.name }
            }

            val toolProvider = PlayerToolProvider(characterProvider, { this@GameSession.world }, roundIntentions)
            val playerInstance = babelFit<PlayerAgentAPI> {
                adapter(apiAdapter)
                addInterceptor(WorldStateInterceptor(isDm = false) { this@GameSession.world })
                addInterceptor(CharacterInterceptor(characterProvider))
                addInterceptor(
                    TurnStateInterceptor {
                        turnStates[character.name] ?: TurnState(
                            phase = "PLAN",
                            stepsUsed = 0,
                            stepsBudget = AGENT_TURN_BUDGET,
                            recentSteps = turnHistories[character.name] ?: emptyList()
                        )
                    }
                )
                toolProvider(toolProvider)
                requestListeners.forEach { listener(it) }
                resilience { maxRetries = 1 }
            }

            val decider = babelFit<DecidingAgentAPI> {
                adapter(apiAdapter)
                addInterceptor(WorldStateInterceptor(isDm = false) { this@GameSession.world })
                addInterceptor(CharacterInterceptor(characterProvider))
                addInterceptor(
                    TurnStateInterceptor {
                        turnStates[character.name] ?: TurnState(
                            phase = "PLAN",
                            stepsUsed = 0,
                            stepsBudget = AGENT_TURN_BUDGET,
                            recentSteps = turnHistories[character.name] ?: emptyList()
                        )
                    }
                )
                toolProvider(toolProvider)
                requestListeners.forEach { listener(it) }
                resilience {
                    maxRetries = 2
                    resultValidator = { r ->
                        if (r is AiDecision) r.method != "decideNextAction" else true
                    }
                }
            }.api

            agents[character.name] = GraphAgent(playerInstance, decider, graph)
        }
        return agents
    }

    private fun applyPlayerStateUpdate(world: World, characterName: String, action: PlayerAction): World {
        val normalizedTarget = action.whisperTarget.trim()
        val whisperMessage = action.whisperMessage.trim()
        val whisper = if (normalizedTarget.isNotBlank() && whisperMessage.isNotBlank()) {
            WhisperMessage(
                from = characterName,
                to = normalizedTarget,
                message = whisperMessage.take(220),
                round = world.round
            )
        } else {
            null
        }

        val updatedParty = world.party.map { character ->
            if (!character.name.equals(characterName, ignoreCase = true)) {
                character
            } else {
                val updatedGoals = action.goalUpdate.trim().takeIf { it.isNotBlank() }
                    ?.let { (character.goals + it).distinct().takeLast(6) }
                    ?: character.goals
                val updatedEmotion = action.emotionalUpdate.trim().ifBlank { character.emotionalState }
                val updatedJournal = action.journalEntry.trim().takeIf { it.isNotBlank() }
                    ?.let { (character.journal + "R${world.round}: $it").takeLast(12) }
                    ?: character.journal
                val updatedRelationships = if (whisper != null && whisper.to.isNotBlank()) {
                    character.relationships + (whisper.to to "coordinating via whispers")
                } else {
                    character.relationships
                }

                character.copy(
                    goals = updatedGoals,
                    emotionalState = updatedEmotion,
                    journal = updatedJournal,
                    relationships = updatedRelationships
                )
            }
        }

        val updatedWhisperLog = if (whisper != null) {
            (world.whisperLog + whisper).takeLast(30)
        } else {
            world.whisperLog
        }

        return world.copy(
            party = updatedParty,
            whisperLog = updatedWhisperLog
        )
    }

    private fun applyResult(world: World, result: ActionResult, actingCharacterName: String): World {
         val targetName = result.targetCharacterName.ifBlank {
            actingCharacterName
        }

        // Check for resurrection: if target is dead and receiving healing
        val resurrectedCharacter = if (result.hpChange > 0) {
            deadCharacters.find { it.name.equals(targetName, ignoreCase = true) }
        } else null

        val activeParty = if (resurrectedCharacter != null) {
            deadCharacters.removeAll { it.name.equals(targetName, ignoreCase = true) }
            val restoredHp = result.hpChange.coerceIn(1, resurrectedCharacter.maxHp)
            val restored = resurrectedCharacter.copy(hp = restoredHp, status = "Resurrected")
            markdownTimeline += MarkdownEntry.SystemMessage("Resurrection", "\u2728 ${restored.name} has been brought back!")
            val resLog = "Round ${world.round}: \u2728 ${restored.name} has been resurrected!"
            world.party + restored to (world.actionLog + resLog).takeLast(40)
        } else {
            world.party to world.actionLog
        }

        val newParty = activeParty.first.map { c ->
            if (c.name.equals(targetName, ignoreCase = true)) {
                val newHp = (c.hp + (if (resurrectedCharacter != null) 0 else result.hpChange))
                    .coerceIn(0, c.maxHp)
                val newInv = (c.inventory + result.itemsGained) -
                    result.itemsLost.toSet()
                val newStatus = result.statusChange.ifBlank { c.status }
                c.copy(hp = newHp, inventory = newInv, status = newStatus)
            } else {
                c
            }
        }

        val updatedLoreNpcs = if (result.newNpcProfiles.isNotEmpty()) {
            val merged = (world.lore.npcs + result.newNpcProfiles)
            merged.associateBy { it.name.lowercase() }.values.toList()
        } else {
            world.lore.npcs
        }

        val locationChanged = result.newLocationName.isNotBlank() && !result.newLocationName.equals(world.location.name, ignoreCase = true)
        val newLocation = if (result.newLocationName.isNotBlank()) {
            Location(
                name = result.newLocationName,
                description = result.newLocationDescription,
                exits = result.newExits,
                npcs = result.newNpcs
            )
        } else if (result.newNpcs.isNotEmpty()) {
            world.location.copy(
                npcs = (world.location.npcs + result.newNpcs).distinct()
            )
        } else {
            world.location
        }

        val newQuestLog = if (result.questUpdate.isNotBlank()) {
            world.questLog + result.questUpdate
        } else {
            world.questLog
        }

        return world.copy(
            party = newParty,
            location = newLocation,
            lore = world.lore.copy(npcs = updatedLoreNpcs),
            questLog = newQuestLog,
            turnsAtCurrentLocation = if (locationChanged) 0 else world.turnsAtCurrentLocation,
            actionLog = activeParty.second
        )
    }

    private fun applyRoundSummary(world: World, summary: RoundSummaryResult): World {
        var w = world

        // Apply location change
        if (summary.newLocationName.isNotBlank() && !summary.newLocationName.equals(w.location.name, ignoreCase = true)) {
            w = w.copy(
                location = Location(
                    name = summary.newLocationName,
                    description = summary.newLocationDescription,
                    exits = summary.newExits,
                    npcs = summary.newNpcs
                ),
                turnsAtCurrentLocation = 0
            )
        } else if (summary.newLocationName.isNotBlank()) {
            w = w.copy(
                location = Location(
                    name = summary.newLocationName,
                    description = summary.newLocationDescription,
                    exits = summary.newExits,
                    npcs = summary.newNpcs
                )
            )
        } else if (summary.newNpcs.isNotEmpty()) {
            w = w.copy(
                location = w.location.copy(
                    npcs = (w.location.npcs + summary.newNpcs).distinct()
                )
            )
        }

        // Apply NPC profiles to lore
        if (summary.newNpcProfiles.isNotEmpty()) {
            val merged = (w.lore.npcs + summary.newNpcProfiles)
                .associateBy { it.name.lowercase() }.values.toList()
            w = w.copy(lore = w.lore.copy(npcs = merged))
        }

        // Apply quest update
        if (summary.questUpdate.isNotBlank()) {
            w = w.copy(questLog = w.questLog + summary.questUpdate)
        }

        // Apply party effects (HP changes, status changes)
        if (summary.partyEffects.isNotEmpty()) {
            val updatedParty = w.party.map { c ->
                val effect = summary.partyEffects.find { it.characterName.equals(c.name, ignoreCase = true) }
                if (effect != null) {
                    val newHp = (c.hp + effect.hpChange).coerceIn(0, c.maxHp)
                    val newStatus = effect.statusChange.ifBlank { c.status }
                    c.copy(hp = newHp, status = newStatus)
                } else c
            }
            w = w.copy(party = updatedParty)
        }

        // Append world event to action log
        if (summary.worldEvent.isNotBlank()) {
            val eventLog = "Round ${w.round}: \uD83C\uDF0D WORLD EVENT — ${summary.worldEvent}"
            w = w.copy(actionLog = (w.actionLog + eventLog).takeLast(40))
        }

        // Append round summary narrative to action log
        if (summary.narrative.isNotBlank()) {
            val narrativeLog = "Round ${w.round} Summary: ${summary.narrative}"
            w = w.copy(actionLog = (w.actionLog + narrativeLog).takeLast(40))
        }

        // Apply level-ups
        if (summary.levelUps.isNotEmpty()) {
            val leveledParty = w.party.map { c ->
                if (summary.levelUps.any { it.equals(c.name, ignoreCase = true) }) {
                    val newLevel = c.level + 1
                    val conMod = CharacterUtils.abilityModifier(c.abilityScores.con)
                    val hitDie = CharacterUtils.hitDieForClass(c.characterClass)
                    val hpGain = (hitDie / 2 + 1 + conMod).coerceAtLeast(1)
                    val newMaxHp = c.maxHp + hpGain
                    val newProficiency = 2 + (newLevel - 1) / 4
                    val levelLog = "Round ${w.round}: \u2B06\uFE0F ${c.name} leveled up to Level $newLevel!"
                    w = w.copy(actionLog = (w.actionLog + levelLog).takeLast(40))
                    listener.onCharacterLevelUp(c.name, newLevel)
                    c.copy(
                        level = newLevel,
                        maxHp = newMaxHp,
                        hp = newMaxHp,
                        proficiencyBonus = newProficiency
                    )
                } else c
            }
            w = w.copy(party = leveledParty)
        }

        return w
    }

    private fun <T> weightedRandomSelect(candidates: List<T>, scoreExtractor: (T) -> Int): T {
        if (candidates.size == 1) return candidates.first()
        val sorted = candidates.sortedByDescending(scoreExtractor)
        val weights = SELECTION_WEIGHTS
        val totalWeight = sorted.indices.sumOf { i -> weights.getOrElse(i) { 1 } }
        var roll = Random.nextInt(totalWeight)
        for ((i, candidate) in sorted.withIndex()) {
            roll -= weights.getOrElse(i) { 1 }
            if (roll < 0) return candidate
        }
        return sorted.first()
    }

    private fun RoundOutcomeCandidate.toRoundSummaryResult() = RoundSummaryResult(
        narrative = narrative,
        availableActions = availableActions,
        worldEvent = worldEvent,
        newLocationName = newLocationName,
        newLocationDescription = newLocationDescription,
        newExits = newExits,
        newNpcs = newNpcs,
        newNpcProfiles = newNpcProfiles,
        questUpdate = questUpdate,
        partyEffects = partyEffects,
        levelUps = levelUps
    )

    private fun ActionOutcomeCandidate.toActionResult() = ActionResult(
        narrative = narrative,
        success = success,
        targetCharacterName = targetCharacterName,
        hpChange = hpChange,
        itemsGained = itemsGained,
        itemsLost = itemsLost,
        newLocationName = newLocationName,
        newLocationDescription = newLocationDescription,
        newExits = newExits,
        newNpcs = newNpcs,
        newNpcProfiles = newNpcProfiles,
        questUpdate = questUpdate,
        statusChange = statusChange,
        diceRollRequest = diceRollRequest
    )

    private fun parseAiAction(stepResult: String, characterName: String): String {
        // Try JSON format: "action": "..."
        val jsonPattern = Regex(""""action"\s*:\s*"([^"]+)"""")
        jsonPattern.find(stepResult)?.groupValues?.get(1)?.let { return it }

        // Try Kotlin toString format: PlayerAction(action=..., reasoning=...)
        val toStringPattern = Regex("""action=([^,)]+)""")
        toStringPattern.find(stepResult)?.groupValues?.get(1)
            ?.trim()?.let { return it }

        return stepResult.ifBlank {
            "$characterName looks around cautiously."
        }
    }

    private fun requestSceneImage() {
        val teamRef = teamImagePrompt
        val worker = thread(isDaemon = true, name = "scene-image-generator") {
            try {
                var prompt = dm.generateSceneImagePrompt(artStyle).get()
                if (teamRef.isNotBlank()) {
                    prompt += "\n\nCharacter visual reference: $teamRef"
                }
                listener.onImagePromptGenerated(prompt, "scene")
            } catch (_: Exception) {
            }
        }
        imageWorkers += worker
    }

    private fun requestActionImage(characterName: String, action: String, outcome: String) {
        val teamRef = teamImagePrompt
        val worker = thread(isDaemon = true, name = "action-image-$characterName") {
            try {
                var prompt = dm.generateActionImagePrompt(characterName, action, outcome, artStyle).get()
                if (teamRef.isNotBlank()) {
                    prompt += "\n\nCharacter visual reference: $teamRef"
                }
                listener.onImagePromptGenerated(prompt, "action")
            } catch (_: Exception) {
            }
        }
        imageWorkers += worker
    }

    private fun requestTeamImage() {
        val worker = thread(isDaemon = true, name = "team-image-generator") {
            try {
                val prompt = dm.generateTeamPortraitPrompt(artStyle).get()
                teamImagePrompt = prompt
                kotlinx.coroutines.runBlocking { dmInstance.memoryStore.put("Team visual reference", prompt) }
                listener.onImagePromptGenerated(prompt, "team-portrait")
            } catch (_: Exception) {
            }
        }
        imageWorkers += worker
    }

    fun generateImageFromPrompt(prompt: String) {
        val image = dm.generateImage(prompt).get()
        if (image.base64.isNotBlank()) {
            markdownTimeline += MarkdownEntry.SceneImage(image)
        }
        listener.onImageGenerated(image)
    }

    private fun finishGame() {
        generateEpilogue()

        val reportPath = runCatching {
            waitForImageWorkers()
            writeEndGameSummaryMarkdown()
        }.getOrNull()

        listener.onGameOver(world)
        if (!reportPath.isNullOrBlank()) {
            listener.onEndGameSummaryGenerated(reportPath)
        }
    }

    private fun generateEpilogue() {
        try {
            val epilogue = dm.generateEpilogue().get()
            markdownTimeline += MarkdownEntry.Epilogue(
                narrative = epilogue.narrative,
                characterFates = epilogue.characterFates,
                themes = epilogue.themes,
                epilogueTeaser = epilogue.epilogueTeaser
            )
            listener.onEpilogueGenerated(epilogue)
        } catch (_: Exception) {
            markdownTimeline += MarkdownEntry.SystemMessage(
                "Epilogue",
                "The tale of these adventurers fades into legend..."
            )
        }
    }

    private fun waitForImageWorkers() {
        val workersSnapshot = synchronized(imageWorkers) { imageWorkers.toList() }
        for (worker in workersSnapshot) {
            if (worker.isAlive) {
                runCatching { worker.join(IMAGE_THREAD_JOIN_TIMEOUT_MS) }
            }
        }
    }

    private fun writeEndGameSummaryMarkdown(): String {
        val generatedAt = LocalDateTime.now()
        val fileTimestamp = generatedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val displayTimestamp = generatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        val reportsDir = File("build/reports/dnd")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val reportFile = File(reportsDir, "dnd-endgame-summary-$fileTimestamp.md")
        val imageDir = File(reportsDir, "images/$fileTimestamp")
        if (!imageDir.exists()) imageDir.mkdirs()

        val timeline = synchronized(markdownTimeline) { markdownTimeline.toList() }
        var imageCounter = 0

        val md = StringBuilder()
        md.appendLine("# D&D Campaign Timeline")
        md.appendLine()
        md.appendLine("*Generated: $displayTimestamp*")
        md.appendLine()

        // Party roster
        md.appendLine("## The Party")
        md.appendLine()
        for (c in world.party) {
            md.appendLine("- **${c.name}** \u2014 ${c.race} ${c.characterClass} (Lvl ${c.level}) \u2014 ${c.hp}/${c.maxHp} HP")
        }
        for (c in deadCharacters) {
            md.appendLine("- **${c.name}** \u2014 ${c.race} ${c.characterClass} (Lvl ${c.level}) \u2014 \u2620\uFE0F DEAD")
        }
        md.appendLine()

        // World building section
        val worldBuildSteps = timeline.filterIsInstance<MarkdownEntry.WorldBuildStep>()
        if (worldBuildSteps.isNotEmpty()) {
            md.appendLine("## World Building")
            md.appendLine()
            for (step in worldBuildSteps) {
                md.appendLine("- **${step.step}:** ${toInline(step.details)}")
            }
            md.appendLine()
        }

        md.appendLine("---")
        md.appendLine()

        // Main timeline
        for (entry in timeline) {
            when (entry) {
                is MarkdownEntry.RoundHeader -> {
                    md.appendLine("---")
                    md.appendLine()
                    md.appendLine("## Round ${entry.round} \u2014 ${entry.locationName}")
                    md.appendLine()
                }
                is MarkdownEntry.DmNarration -> {
                    val icon = when (entry.category) {
                        "summary" -> "\uD83D\uDCDC"
                        "scene" -> "\uD83C\uDFAD"
                        else -> "\uD83D\uDCDD"
                    }
                    md.appendLine("### $icon ${entry.title}")
                    md.appendLine()
                    md.appendLine(entry.narrative.trim())
                    md.appendLine()
                }
                is MarkdownEntry.CharacterAction -> {
                    val icon = when (entry.success) {
                        true -> "\u2705"
                        false -> "\u274C"
                        null -> "\u2694\uFE0F"
                    }
                    md.appendLine("#### $icon ${entry.characterName}")
                    md.appendLine()
                    md.appendLine("> **Action:** ${toInline(entry.action)}")
                    md.appendLine()
                    if (entry.outcome.isNotBlank()) {
                        md.appendLine(entry.outcome.trim())
                        md.appendLine()
                    }
                }
                is MarkdownEntry.DiceRoll -> {
                    md.appendLine("\uD83C\uDFB2 **${entry.characterName}** \u2014 ${entry.rollType} (DC ${entry.difficulty})")
                    md.appendLine()
                }
                is MarkdownEntry.Whisper -> {
                    md.appendLine("\uD83E\uDD2B *${entry.from} whispers to ${entry.to}:* \"${toInline(entry.message)}\"")
                    md.appendLine()
                }
                is MarkdownEntry.SceneImage -> {
                    imageCounter++
                    val imageFileName = "scene-${imageCounter.toString().padStart(3, '0')}.png"
                    val imageFile = File(imageDir, imageFileName)
                    runCatching {
                        imageFile.writeBytes(Base64.getDecoder().decode(entry.image.base64))
                    }.onSuccess {
                        val relativePath = "images/$fileTimestamp/$imageFileName"
                        md.appendLine("![Scene $imageCounter](${relativePath.replace("\\", "/")})")
                        md.appendLine()
                    }
                }
                is MarkdownEntry.SystemMessage -> {
                    md.appendLine("**\u2139\uFE0F ${entry.title}:** ${toInline(entry.details)}")
                    md.appendLine()
                }
                is MarkdownEntry.Epilogue -> {
                    renderEpilogue(md, entry)
                }
                is MarkdownEntry.WorldBuildStep -> {
                    // Already rendered in the World Building section above
                }
            }
        }

        // Final status
        md.appendLine("---")
        md.appendLine()
        md.appendLine("## Final Status")
        md.appendLine()
        md.appendLine("- **Round:** ${world.round}/${world.maxRounds}")
        md.appendLine("- **Location:** ${world.location.name}")
        md.appendLine("- **Quests:** ${world.questLog.joinToString("; ").ifBlank { "None" }}")
        md.appendLine()
        for (c in world.party) {
            md.appendLine("- **${c.name}** (\u2764\uFE0F ${c.hp}/${c.maxHp}) \u2014 ${c.status}")
        }
        for (c in deadCharacters) {
            md.appendLine("- **${c.name}** (\u2620\uFE0F DEAD) \u2014 Fallen in battle")
        }
        md.appendLine()

        reportFile.writeText(md.toString())
        return reportFile.absolutePath
    }

    private fun toInline(text: String): String = text.replace("\n", " ").trim()

    private fun renderEpilogue(md: StringBuilder, entry: MarkdownEntry.Epilogue) {
        md.appendLine("---")
        md.appendLine()
        md.appendLine("## Epilogue")
        md.appendLine()
        md.appendLine(entry.narrative.trim())
        md.appendLine()
        if (entry.characterFates.isNotEmpty()) {
            md.appendLine("### Character Fates")
            md.appendLine()
            for (fate in entry.characterFates) {
                val icon = if (fate.survived) "\u2764\uFE0F" else "\u2620\uFE0F"
                md.appendLine("- $icon **${fate.name}** \u2014 ${fate.fate}")
            }
            md.appendLine()
        }
        if (entry.themes.isNotEmpty()) {
            md.appendLine("*Themes: ${entry.themes.joinToString(", ")}*")
            md.appendLine()
        }
        if (entry.epilogueTeaser.isNotBlank()) {
            md.appendLine("### What Comes Next...")
            md.appendLine()
            md.appendLine("*${entry.epilogueTeaser.trim()}*")
            md.appendLine()
        }
    }
}
