package ca.adamhammer.babelfit.samples.dnd.cli

import ca.adamhammer.babelfit.adapters.OpenAiAdapter
import ca.adamhammer.babelfit.debug.DebugAdapter
import ca.adamhammer.babelfit.debug.DebugSession
import ca.adamhammer.babelfit.model.ImageResult
import ca.adamhammer.babelfit.samples.dnd.*
import ca.adamhammer.babelfit.samples.dnd.api.*
import ca.adamhammer.babelfit.samples.dnd.model.*
import ca.adamhammer.babelfit.babelFit
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val openAiAdapter = OpenAiAdapter()
    val debugSession = DebugSession()
    val adapter = DebugAdapter(openAiAdapter, debugSession)

    println("═══════════════════════════════════════════════════════")
    println("  ⚔  BABELFIT QUEST — Automated Agentic D&D Demo  ⚔")
    println("═══════════════════════════════════════════════════════")
    println("  Debug session: ${debugSession.getSessionPath()}")
    println()

    val world = createAutomatedParty(adapter, partySize = 3)

    val listener = CliGameListener()
    val session = GameSession(adapter, listener)

    session.startGame(world)
}

class CliGameListener : GameEventListener {
    override fun onWorldBuildingStep(step: String, details: String) {
        println("\n  🧭 DM worldbuild [$step]: $details")
    }

    override fun onSceneDescription(scene: SceneDescription) {
        println()
        println("═══ Scene ═══")
        println(scene.narrative)
        if (scene.availableActions.isNotEmpty()) {
            println()
            println("Possible actions: ${scene.availableActions.joinToString(" | ")}")
        }
    }

    override fun onActionResult(result: ActionResult, world: World) {
        println()
        println("─── DM ───────────────────────────────────────────")
        println(result.narrative)
        val targetName = result.targetCharacterName
        val target = world.party.find { it.name.equals(targetName, true) }
        if (target != null && result.hpChange != 0) {
                val sign = if (result.hpChange > 0) "+" else ""
                println("  ♥ ${target.name} HP: $sign${result.hpChange} (${target.hp}/${target.maxHp})")
        }
        if (result.itemsGained.isNotEmpty()) println("  + Gained: ${result.itemsGained.joinToString()}")
        if (result.itemsLost.isNotEmpty()) println("  - Lost: ${result.itemsLost.joinToString()}")
        if (result.questUpdate.isNotBlank()) println("  📜 Quest: ${result.questUpdate}")
        println("──────────────────────────────────────────────────")
    }

    override fun onRoundSummary(summary: SceneDescription, world: World) {
        println("─── End of Round ${world.round} ─────────────────────")
        println(summary.narrative)
    }

    override fun onImageGenerated(image: ImageResult) {
        println("  🖼 Scene image generated")
    }

    override fun onGameOver(world: World) {
        println("\n💀💀💀 Game Over 💀💀💀")
        world.party.forEach { 
           println(CharacterUtils.formatStats(it))
        }
    }

    override fun onEndGameSummaryGenerated(reportPath: String) {
        println("\n📝 End-game summary: $reportPath")
    }

    override fun onCharacterThinking(characterName: String) {
        println("\n  🤖 $characterName is thinking...")
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
        val marker = if (terminal) "✓" else "·"
        println("    $marker Step $stepNumber [$methodName] MP $pointsSpent/5 (left $pointsRemaining) $details")
    }

    override fun onCharacterAction(characterName: String, action: String) {
        println("  🤖 $characterName: \"$action\"")
    }

    override fun onWhisper(fromCharacter: String, toCharacter: String, message: String) {
        println("  🤫 $fromCharacter -> $toCharacter: $message")
    }
}

private fun createAutomatedParty(adapter: DebugAdapter, partySize: Int): World {
    val backstoryDm = babelFit<DungeonMasterAPI> {
        adapter(adapter)
        resilience { maxRetries = 1 }
    }.api

    val party = mutableListOf<Character>()
    for (i in 1..partySize) {
        val c = createCharacter(i, partySize, backstoryDm, party)
        party.add(c)
        println()
        println(CharacterUtils.formatStats(c))
        println()
    }
    return World(party = party, location = Location(), round = 0)
}


private fun createCharacter(
    index: Int,
    total: Int,
    backstoryDm: DungeonMasterAPI,
    existingParty: List<Character>
): Character {
    println("─── Character $index of $total ────────────────────────")
    val existing = existingParty.joinToString(", ") { "${it.name} the ${it.race} ${it.characterClass}" }
    val prompt = "EXISTING PARTY: [$existing]. GENERATE: name, race, class. HINT: diverse party role $index"

    println("  Generating character concept...")
    val concept = backstoryDm.generateCharacterConcept(prompt).get()
    println("  → ${concept.name} the ${concept.race} ${concept.characterClass}")

    val (backstory, stats, items) = resolveBackstoryAndStats(backstoryDm, concept.name, concept.race, concept.characterClass)

    return CharacterUtils.buildCharacter(
        name = concept.name,
        look = "A typical adventurer.",
        race = concept.race,
        characterClass = concept.characterClass,
        abilityScores = stats,
        backstory = backstory,
        aiSuggestedItems = items
    )
}

private fun resolveBackstoryAndStats(
    dm: DungeonMasterAPI,
    name: String,
    race: String,
    cls: String
): Triple<String, AbilityScores, List<String>> {
    println("  Generating backstory for $name the $race $cls...")
    val res = dm.generateBackstory(name, race, cls).get()
    println("  ${res.backstory.take(200)}...")
    return Triple(res.backstory, res.suggestedAbilityScores, res.startingItems)
}
