package ca.adamhammer.babelfit.samples.dnd.api

import ca.adamhammer.babelfit.model.ImageResult
import ca.adamhammer.babelfit.samples.dnd.model.*

@Suppress("TooManyFunctions")
interface GameEventListener {
    fun onWorldBuildingStep(step: String, details: String) {}
    fun onAgentStep(
        characterName: String,
        stepNumber: Int,
        methodName: String,
        details: String,
        terminal: Boolean,
        pointsSpent: Int,
        pointsRemaining: Int
    ) {
    }
    fun onRoundStarted(round: Int, world: World) {}
    fun onSceneDescription(scene: SceneDescription)
    fun onImageGenerated(image: ImageResult) {}
    fun onActionResult(result: ActionResult, world: World)
    fun onDiceRollRequested(character: Character, request: DiceRollRequest) {}
    fun onDiceRollResult(
        characterName: String,
        rollType: String,
        rollValue: Int,
        modifier: Int,
        total: Int,
        difficulty: Int,
        success: Boolean
    ) {}
    fun onImagePromptGenerated(prompt: String, imageType: String) {}
    fun onCharacterLevelUp(characterName: String, newLevel: Int) {}
    fun onRoundSummary(summary: SceneDescription, world: World)
    fun onGameOver(world: World)
    fun onEndGameSummaryGenerated(reportPath: String) {}
    fun onCharacterThinking(characterName: String)
    fun onCharacterAction(characterName: String, action: String)
    fun onWhisper(fromCharacter: String, toCharacter: String, message: String) {}
    fun onCharacterDeath(characterName: String, world: World) {}
    fun onEpilogueGenerated(epilogue: EpilogueResult) {}
}
