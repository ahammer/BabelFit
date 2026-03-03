package ca.adamhammer.babelfit.samples.dnd.api

import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.model.ToolDefinition
import ca.adamhammer.babelfit.model.ToolResult
import ca.adamhammer.babelfit.samples.dnd.CharacterUtils
import ca.adamhammer.babelfit.samples.dnd.model.Character
import ca.adamhammer.babelfit.samples.dnd.model.PlayerAction
import ca.adamhammer.babelfit.samples.dnd.model.World
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Suppress("MaxLineLength")
class PlayerToolProvider(
    private val characterProvider: () -> Character,
    private val worldProvider: () -> World,
    private val partyIntentions: ConcurrentHashMap<String, PlayerAction>? = null
) : ToolProvider {

    private val noArgSchema = """{"type":"object","properties":{},"additionalProperties":false}"""
    private val nameArgSchema = """{"type":"object","properties":{"name":{"type":"string","description":"Name to look up"}},"required":["name"],"additionalProperties":false}"""

    override fun listTools(): List<ToolDefinition> = buildList {
        add(ToolDefinition(
            name = "inspect_inventory",
            description = "List the character's current inventory items.",
            inputSchema = noArgSchema
        ))
        add(ToolDefinition(
            name = "inspect_skills",
            description = "Inspect the character's skills, saves, AC, and HP.",
            inputSchema = noArgSchema
        ))
        add(ToolDefinition(
            name = "inspect_location",
            description = "Query the current location: name, description, exits, NPCs present, and visible items.",
            inputSchema = noArgSchema
        ))
        add(ToolDefinition(
            name = "inspect_quest_log",
            description = "List all active quests and objectives.",
            inputSchema = noArgSchema
        ))
        add(ToolDefinition(
            name = "read_whispers",
            description = "Read private whispers addressed to your character.",
            inputSchema = noArgSchema
        ))
        add(ToolDefinition(
            name = "inspect_party_status",
            description = "Check the HP, status, and class of every party member for tactical coordination.",
            inputSchema = noArgSchema
        ))
        add(ToolDefinition(
            name = "inspect_npc",
            description = "Look up an NPC's role, motivation, and current location from the world lore.",
            inputSchema = nameArgSchema
        ))
        if (partyIntentions != null) {
            add(ToolDefinition(
                name = "check_party_intentions",
                description = "See what actions other party members have already committed this round. Use this to avoid duplicating their efforts.",
                inputSchema = noArgSchema
            ))
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override suspend fun callTool(call: ToolCall): ToolResult {
        val character = characterProvider()
        val world = worldProvider()
        val content = when (call.toolName) {
            "inspect_inventory" -> {
                if (character.inventory.isEmpty()) {
                    "Inventory is empty."
                } else {
                    "Inventory: ${character.inventory.joinToString(", ")}"
                }
            }

            "inspect_skills" -> {
                val s = character.abilityScores
                buildString {
                    append("HP ${character.hp}/${character.maxHp}, AC ${character.ac}, ")
                    append("Saves ${character.savingThrows.joinToString("/")}, Skills ${character.skills.joinToString("/")}. ")
                    append("Mods STR ${CharacterUtils.abilityModifier(s.str)}, DEX ${CharacterUtils.abilityModifier(s.dex)}, ")
                    append("CON ${CharacterUtils.abilityModifier(s.con)}, INT ${CharacterUtils.abilityModifier(s.int)}, ")
                    append("WIS ${CharacterUtils.abilityModifier(s.wis)}, CHA ${CharacterUtils.abilityModifier(s.cha)}")
                }
            }

            "inspect_location" -> {
                val loc = world.location
                buildString {
                    appendLine("Location: ${loc.name}")
                    appendLine("Description: ${loc.description}")
                    appendLine("Exits: ${loc.exits.ifEmpty { listOf("none visible") }.joinToString(", ")}")
                    appendLine("NPCs present: ${loc.npcs.ifEmpty { listOf("none") }.joinToString(", ")}")
                    append("Items: ${loc.items.ifEmpty { listOf("none visible") }.joinToString(", ")}")
                }
            }

            "inspect_quest_log" -> {
                if (world.questLog.isEmpty()) {
                    "No active quests."
                } else {
                    world.questLog.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")
                }
            }

            "read_whispers" -> {
                val myName = character.name
                val whispers = world.whisperLog.filter { it.to.equals(myName, ignoreCase = true) }
                if (whispers.isEmpty()) {
                    "No whispers addressed to you."
                } else {
                    whispers.joinToString("\n") { "[Round ${it.round}] ${it.from} whispers: \"${it.message}\"" }
                }
            }

            "inspect_party_status" -> {
                world.party.joinToString("\n") { p ->
                    "${p.name} (${p.race} ${p.characterClass} Lvl ${p.level}) — HP ${p.hp}/${p.maxHp}, Status: ${p.status}"
                }
            }

            "inspect_npc" -> {
                val args = Json.parseToJsonElement(call.arguments).jsonObject
                val npcName = args["name"]?.jsonPrimitive?.content?.trim() ?: ""
                val npc = world.lore.npcs.find { it.name.equals(npcName, ignoreCase = true) }
                if (npc != null) {
                    "NPC: ${npc.name}\nRole: ${npc.role}\nMotivation: ${npc.motivation}\nLocation: ${npc.currentLocation}"
                } else {
                    "No NPC named '$npcName' found in world lore."
                }
            }

            "check_party_intentions" -> {
                val intentions = partyIntentions
                if (intentions == null || intentions.isEmpty()) {
                    "No other party members have committed actions yet this round."
                } else {
                    intentions.entries
                        .filter { !it.key.equals(character.name, ignoreCase = true) }
                        .joinToString("\n") { "${it.key}: ${it.value.action}" }
                        .ifBlank { "No other party members have committed actions yet this round." }
                }
            }

            else -> return ToolResult(
                id = call.id,
                toolName = call.toolName,
                content = "Unknown tool: ${call.toolName}",
                isError = true
            )
        }

        return ToolResult(
            id = call.id,
            toolName = call.toolName,
            content = content,
            isError = false
        )
    }
}
