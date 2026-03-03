@file:Suppress("TooManyFunctions")
package ca.adamhammer.babelfit.samples.dnd.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.adamhammer.babelfit.UsageTracker
import ca.adamhammer.babelfit.adapters.ClaudeAdapter
import ca.adamhammer.babelfit.adapters.GeminiAdapter
import ca.adamhammer.babelfit.adapters.OpenAiAdapter
import ca.adamhammer.babelfit.adapters.RoutingAdapter
import ca.adamhammer.babelfit.debug.trace.TraceSession
import ca.adamhammer.babelfit.debug.trace.TracingAdapter
import ca.adamhammer.babelfit.debug.trace.TracingRequestListener
import ca.adamhammer.babelfit.interfaces.ApiAdapter
import ca.adamhammer.babelfit.model.ImageResult
import ca.adamhammer.babelfit.samples.dnd.CharacterUtils
import ca.adamhammer.babelfit.samples.dnd.DungeonMasterAPI
import ca.adamhammer.babelfit.samples.dnd.api.GameEventListener
import ca.adamhammer.babelfit.samples.dnd.api.GameSession
import ca.adamhammer.babelfit.samples.dnd.model.*
import ca.adamhammer.babelfit.babelFit
import com.openai.models.ChatModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class ComposeGameControllerV2(private val uiScope: CoroutineScope) : GameEventListener {

    var screen by mutableStateOf(AppScreen.SETUP)
        private set

    var setupState by mutableStateOf(SetupState())

    var world by mutableStateOf(World())
        private set

    var currentRound by mutableStateOf(0)
        private set

    var isBusy by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var activeTurnCharacterName by mutableStateOf<String?>(null)
        private set

    var gameFinished by mutableStateOf(false)
        private set

    val timelineEntries = mutableStateListOf<TimelineEntry>()

    val usageTracker = UsageTracker()

    var showUsagePane by mutableStateOf(false)

    var traceSession by mutableStateOf<TraceSession?>(null)
        private set

    private var gameSession: GameSession? = null

    // Track pending action per character so we can merge action + outcome
    private val pendingActions = mutableMapOf<String, TimelineEntry.CharacterAction>()
    // Monotonic counter for stable timeline ordering
    private var timeCounter = 0L
    private fun nextTimestamp(): Long = timeCounter++

    fun updatePartySize(size: Int) {
        val clamped = size.coerceIn(1, 4)
        val existing = setupState.drafts
        val resized = buildList {
            repeat(clamped) { index ->
                add(existing.getOrNull(index) ?: CharacterDraft())
            }
        }
        setupState = setupState.copy(partySize = clamped, drafts = resized)
    }

    fun updateDraft(index: Int, transform: (CharacterDraft) -> CharacterDraft) {
        val updated = setupState.drafts.toMutableList()
        updated[index] = transform(updated[index])
        setupState = setupState.copy(drafts = updated)
    }

    fun updateMaxRounds(rounds: Int) {
        setupState = setupState.copy(maxRounds = rounds.coerceIn(3, 100))
    }

    fun randomizePrimer() {
        val genre = BakedGameData.genres.random()
        val selectedCharacters = genre.characters.shuffled().take(setupState.partySize)
        val newDrafts = selectedCharacters.map { baked ->
            CharacterDraft(
                name = baked.name,
                race = baked.race,
                characterClass = baked.characterClass,
                manualBackstory = true,
                backstory = baked.backstory
            )
        }
        setupState = setupState.copy(
            genre = genre.name,
            premise = genre.premise,
            drafts = newDrafts
        )
    }

    fun startGame() {
        if (isBusy) return
        isBusy = true
        errorMessage = null
        timelineEntries.clear()
        pendingActions.clear()
        AvatarColors.reset()
        timeCounter = 0L

        uiScope.launch(Dispatchers.IO) {
            try {
                val textAdapter = createAdapter(setupState.textVendor, setupState.textModel)
                val imageAdapter = createAdapter(setupState.imageVendor, setupState.imageVendor.defaultModel)
                val sessionAdapter = RoutingAdapter { context ->
                    if (context.methodName == "generateImage") imageAdapter else textAdapter
                }
                val newTraceSession = TraceSession()
                traceSession = newTraceSession
                val tracingAdapter = TracingAdapter(sessionAdapter, newTraceSession)
                val initialWorld = buildInitialWorld(setupState, tracingAdapter)
                val session = GameSession(
                    apiAdapter = tracingAdapter,
                    listener = this@ComposeGameControllerV2,
                    maxTurns = setupState.maxRounds,
                    enableImages = setupState.enableImages,
                    artStyle = setupState.artStyle,
                    requestListeners = listOf(
                        usageTracker,
                        TracingRequestListener(newTraceSession)
                    )
                )
                uiScope.launch {
                    world = initialWorld
                    currentRound = 0
                    screen = AppScreen.PLAYING
                    append(
                        TimelineEntry.SystemMessage(
                            title = "The party gathers",
                            details = "${initialWorld.party.size} adventurers set out from ${initialWorld.location.name}.",
                            timestamp = nextTimestamp()
                        )
                    )
                    // Pre-register avatar colors for all party members
                    initialWorld.party.forEach { AvatarColors.colorFor(it.name) }
                }
                gameSession = session
                session.startGame(initialWorld)
            } catch (e: Exception) {
                uiScope.launch {
                    errorMessage = normalizeError(e.message)
                    append(
                        TimelineEntry.SystemMessage(
                            title = "Unable to start adventure",
                            details = errorMessage ?: "Unknown startup error",
                            isError = true,
                            timestamp = nextTimestamp()
                        )
                    )
                    isBusy = false
                    screen = AppScreen.SETUP
                }
            }
        }
    }

    fun backToSetup() {
        activeTurnCharacterName = null
        isBusy = false
        gameFinished = false
        screen = AppScreen.SETUP
    }

    fun exportTrace() {
        val session = traceSession ?: return
        val chooser = javax.swing.JFileChooser().apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "BabelFit Trace files", "btrace.json"
            )
            dialogTitle = "Export Trace"
            selectedFile = java.io.File("trace.btrace.json")
        }
        if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            session.save(chooser.selectedFile)
        }
    }

    // ── GameEventListener ───────────────────────────────────────────────────

    override fun onRoundStarted(round: Int, world: World) {
        uiScope.launch {
            currentRound = round
            this@ComposeGameControllerV2.world = world
            append(
                TimelineEntry.RoundHeader(
                    round = round,
                    locationName = world.location.name,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onWorldBuildingStep(step: String, details: String) {
        uiScope.launch {
            append(
                TimelineEntry.DmNarration(
                    title = "World: $step",
                    narrative = details,
                    category = TimelineEntry.DmCategory.WORLD_BUILD,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onAgentStep(
        characterName: String,
        stepNumber: Int,
        methodName: String,
        details: String,
        terminal: Boolean,
        pointsSpent: Int,
        pointsRemaining: Int
    ) {
        uiScope.launch {
            append(
                TimelineEntry.CharacterThinking(
                    characterName = characterName,
                    stepNumber = stepNumber,
                    methodName = methodName,
                    details = details,
                    isTerminal = terminal,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onSceneDescription(scene: SceneDescription) {
        uiScope.launch {
            append(
                TimelineEntry.DmNarration(
                    title = world.location.name,
                    narrative = scene.narrative,
                    category = TimelineEntry.DmCategory.SCENE,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onImageGenerated(image: ImageResult) {
        uiScope.launch {
            if (image.base64.isNotBlank()) {
                append(
                    TimelineEntry.SceneImage(
                        base64 = image.base64,
                        caption = "",
                        timestamp = nextTimestamp()
                    )
                )
            }
        }
    }

    override fun onCharacterThinking(characterName: String) {
        uiScope.launch {
            activeTurnCharacterName = characterName
            isBusy = true
        }
    }

    override fun onCharacterAction(characterName: String, action: String) {
        uiScope.launch {
            // Store as pending — we'll merge outcome when onActionResult fires
            pendingActions[characterName] = TimelineEntry.CharacterAction(
                characterName = characterName,
                action = action,
                timestamp = nextTimestamp()
            )
            activeTurnCharacterName = null
            isBusy = false
        }
    }

    override fun onActionResult(result: ActionResult, world: World) {
        uiScope.launch {
            this@ComposeGameControllerV2.world = world
            // Find and merge with pending character action
            val targetName = result.targetCharacterName.ifBlank {
                // find most recent pending
                pendingActions.keys.lastOrNull() ?: "Unknown"
            }
            val pending = pendingActions.remove(targetName)
                ?: pendingActions.values.lastOrNull()?.also { pendingActions.clear() }

            if (pending != null) {
                append(
                    pending.copy(
                        outcome = result.narrative,
                        success = result.success,
                        timestamp = pending.timestamp
                    )
                )
            } else {
                append(
                    TimelineEntry.CharacterAction(
                        characterName = targetName,
                        action = "(action)",
                        outcome = result.narrative,
                        success = result.success,
                        timestamp = nextTimestamp()
                    )
                )
            }
        }
    }

    override fun onDiceRollRequested(character: Character, request: DiceRollRequest) {
        uiScope.launch {
            append(
                TimelineEntry.DiceRoll(
                    characterName = character.name,
                    rollType = request.rollType,
                    difficulty = request.difficulty,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onWhisper(fromCharacter: String, toCharacter: String, message: String) {
        uiScope.launch {
            append(
                TimelineEntry.Whisper(
                    from = fromCharacter,
                    to = toCharacter,
                    message = message,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onRoundSummary(summary: SceneDescription, world: World) {
        uiScope.launch {
            this@ComposeGameControllerV2.world = world
            append(
                TimelineEntry.DmNarration(
                    title = "Round $currentRound Summary",
                    narrative = summary.narrative,
                    category = TimelineEntry.DmCategory.SUMMARY,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onGameOver(world: World) {
        uiScope.launch {
            this@ComposeGameControllerV2.world = world
            isBusy = false
            activeTurnCharacterName = null
            gameFinished = true
        }
    }

    override fun onDiceRollResult(
        characterName: String,
        rollType: String,
        rollValue: Int,
        modifier: Int,
        total: Int,
        difficulty: Int,
        success: Boolean
    ) {
        uiScope.launch {
            val idx = timelineEntries.indexOfLast {
                it is TimelineEntry.DiceRoll &&
                    it.characterName == characterName &&
                    it.rollType == rollType &&
                    it.rollValue == null
            }
            if (idx >= 0) {
                val existing = timelineEntries[idx] as TimelineEntry.DiceRoll
                timelineEntries[idx] = existing.copy(
                    rollValue = rollValue,
                    modifier = modifier,
                    total = total,
                    success = success
                )
            }
        }
    }

    override fun onImagePromptGenerated(prompt: String, imageType: String) {
        uiScope.launch {
            append(
                TimelineEntry.ImagePromptPreview(
                    id = UUID.randomUUID().toString(),
                    prompt = prompt,
                    imageType = imageType,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onCharacterLevelUp(characterName: String, newLevel: Int) {
        uiScope.launch {
            append(
                TimelineEntry.LevelUp(
                    characterName = characterName,
                    newLevel = newLevel,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onCharacterDeath(characterName: String, world: World) {
        uiScope.launch {
            this@ComposeGameControllerV2.world = world
            append(
                TimelineEntry.SystemMessage(
                    title = "\u2620\uFE0F $characterName has fallen",
                    details = "$characterName has been slain.",
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onEpilogueGenerated(epilogue: EpilogueResult) {
        uiScope.launch {
            append(
                TimelineEntry.DmNarration(
                    title = "Epilogue",
                    narrative = epilogue.narrative,
                    category = TimelineEntry.DmCategory.SUMMARY,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    override fun onEndGameSummaryGenerated(reportPath: String) {
        uiScope.launch {
            append(
                TimelineEntry.SystemMessage(
                    title = "Report saved",
                    details = reportPath,
                    timestamp = nextTimestamp()
                )
            )
        }
    }

    fun generateImage(entryId: String) {
        val idx = timelineEntries.indexOfFirst {
            it is TimelineEntry.ImagePromptPreview && it.id == entryId
        }
        if (idx < 0) return
        val preview = timelineEntries[idx] as TimelineEntry.ImagePromptPreview
        if (preview.generating || preview.imageBase64 != null) return

        timelineEntries[idx] = preview.copy(generating = true, error = null)

        uiScope.launch(Dispatchers.IO) {
            val session = gameSession ?: return@launch
            try {
                session.generateImageFromPrompt(sanitizeImagePrompt(preview.prompt))
                uiScope.launch {
                    val imgIdx = timelineEntries.indexOfLast { it is TimelineEntry.SceneImage }
                    if (imgIdx >= 0) {
                        val img = timelineEntries.removeAt(imgIdx) as TimelineEntry.SceneImage
                        val curIdx = timelineEntries.indexOfFirst {
                            it is TimelineEntry.ImagePromptPreview && it.id == entryId
                        }
                        if (curIdx >= 0) {
                            timelineEntries[curIdx] = (timelineEntries[curIdx] as TimelineEntry.ImagePromptPreview)
                                .copy(generating = false, imageBase64 = img.base64)
                        }
                    }
                }
            } catch (e: Exception) {
                val cause = if (e is java.util.concurrent.ExecutionException) e.cause ?: e else e
                val errorMsg = cause.message ?: "Image generation failed"
                uiScope.launch {
                    val curIdx = timelineEntries.indexOfFirst {
                        it is TimelineEntry.ImagePromptPreview && it.id == entryId
                    }
                    if (curIdx >= 0) {
                        timelineEntries[curIdx] = (timelineEntries[curIdx] as TimelineEntry.ImagePromptPreview)
                            .copy(generating = false, error = errorMsg)
                    }
                }
            }
        }
    }

    companion object {
        private val PROMPT_SANITIZE_MAP = mapOf(
            "blood" to "crimson energy",
            "bloody" to "crimson",
            "bloodied" to "battle-worn",
            "bleeding" to "glowing with crimson light",
            "bleed" to "shimmer",
            "gore" to "debris",
            "gory" to "intense",
            "wound" to "mark",
            "wounded" to "battle-scarred",
            "wounds" to "battle marks",
            "stab" to "strike",
            "stabbed" to "struck",
            "stabbing" to "striking",
            "slash" to "sweep",
            "slashed" to "swept",
            "slashing" to "sweeping",
            "impale" to "pierce with light",
            "impaled" to "pinned by energy",
            "severed" to "shattered",
            "sever" to "shatter",
            "decapitate" to "defeat",
            "dismember" to "overwhelm",
            "corpse" to "fallen figure",
            "corpses" to "fallen figures",
            "dead body" to "fallen figure",
            "dead bodies" to "fallen figures",
            "mutilate" to "defeat",
            "mutilated" to "defeated",
            "entrails" to "spectral wisps",
            "guts" to "ethereal mist",
            "skull" to "helm",
            "skulls" to "helms",
            "kill" to "defeat",
            "killed" to "defeated",
            "killing" to "defeating",
            "murder" to "vanquish",
            "murdered" to "vanquished",
            "die" to "fall",
            "dying" to "fading",
            "death" to "downfall",
            "dead" to "fallen",
            "torture" to "challenge",
            "torment" to "trial",
            "scream" to "cry out",
            "screaming" to "calling out",
            "naked" to "unarmored",
            "nude" to "unarmored",
            "sex" to "romance",
            "sexual" to "romantic"
        )

        private val SANITIZE_REGEX = PROMPT_SANITIZE_MAP.keys
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
            .let { Regex("\\b($it)\\b", RegexOption.IGNORE_CASE) }

        fun sanitizeImagePrompt(prompt: String): String =
            SANITIZE_REGEX.replace(prompt) { match ->
                PROMPT_SANITIZE_MAP[match.value.lowercase()] ?: match.value
            }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun append(entry: TimelineEntry) {
        timelineEntries.add(entry)
        if (timelineEntries.size > 500) {
            timelineEntries.removeFirst()
        }
    }

    private fun createAdapter(vendor: Vendor, model: String): ApiAdapter = when (vendor) {
        Vendor.OPENAI -> OpenAiAdapter(model = ChatModel.of(model))
        Vendor.ANTHROPIC -> ClaudeAdapter(model = model)
        Vendor.GEMINI -> GeminiAdapter(model = model)
    }

    private fun normalizeError(message: String?): String {
        val text = message?.trim().orEmpty()
        return when {
            text.contains("OPENAI_API_KEY", ignoreCase = true) ->
                "OPENAI_API_KEY is missing. Set it before starting."
            text.contains("ANTHROPIC_API_KEY", ignoreCase = true) ->
                "ANTHROPIC_API_KEY is missing. Set it before starting."
            text.contains("GEMINI_API_KEY", ignoreCase = true) ||
                text.contains("GOOGLE_API_KEY", ignoreCase = true) ->
                "GEMINI_API_KEY (or GOOGLE_API_KEY) is missing. Set it before starting."
            text.isBlank() -> "Failed to start game"
            else -> text
        }
    }

    private fun buildInitialWorld(state: SetupState, apiAdapter: ApiAdapter): World {
        val backstoryDm = babelFit<DungeonMasterAPI> {
            adapter(apiAdapter)
            resilience { maxRetries = 1 }
        }.api

        val party = mutableListOf<Character>()
        state.drafts.forEachIndexed { index, draft ->
            val character = createCharacterFromDraft(backstoryDm, draft, index + 1, party)
            party.add(character)
        }

        return World(
            party = party,
            location = Location(),
            round = 0,
            lore = WorldLore(campaignPremise = state.premise)
        )
    }

    private fun createCharacterFromDraft(
        backstoryDm: DungeonMasterAPI,
        draft: CharacterDraft,
        index: Int,
        existingParty: List<Character>
    ): Character {
        val concept = if (
            draft.name.isBlank() || draft.race.isBlank() || draft.characterClass.isBlank()
        ) {
            val existingMembers = existingParty.joinToString(", ") {
                "${it.name} the ${it.race} ${it.characterClass}"
            }.ifEmpty { "none yet" }
            backstoryDm.generateCharacterConcept(
                "EXISTING PARTY: [$existingMembers]. GENERATE: name, race, class."
            ).get()
        } else {
            CharacterConcept(draft.name, draft.race, draft.characterClass)
        }

        val name = draft.name.ifBlank { concept.name.ifBlank { "Hero$index" } }
        val race = draft.race.ifBlank { concept.race.ifBlank { "Human" } }
        val clazz = draft.characterClass.ifBlank { concept.characterClass.ifBlank { "Fighter" } }

        val backstoryResult = if (draft.manualBackstory && draft.backstory.isNotBlank()) {
            BackstoryResult(
                backstory = draft.backstory,
                suggestedAbilityScores = CharacterUtils.autoAssignScores(clazz),
                startingItems = emptyList()
            )
        } else {
            backstoryDm.generateBackstory(name, race, clazz).get()
        }

        val scores = backstoryResult.suggestedAbilityScores.takeIf { it != AbilityScores() }
            ?: CharacterUtils.autoAssignScores(clazz)

        return CharacterUtils.buildCharacter(
            name = name,
            look = concept.look.ifBlank { "A typical adventurer." },
            race = race,
            characterClass = clazz,
            abilityScores = scores,
            backstory = backstoryResult.backstory,
            aiSuggestedItems = backstoryResult.startingItems
        )
    }
}
