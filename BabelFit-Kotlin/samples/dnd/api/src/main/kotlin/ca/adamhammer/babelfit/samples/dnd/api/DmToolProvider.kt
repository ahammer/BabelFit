package ca.adamhammer.babelfit.samples.dnd.api

import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.model.ToolDefinition
import ca.adamhammer.babelfit.model.ToolResult
import ca.adamhammer.babelfit.samples.dnd.CharacterUtils
import ca.adamhammer.babelfit.samples.dnd.model.World
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Suppress("MaxLineLength")
class DmToolProvider(
    private val worldProvider: () -> World
) : ToolProvider {

    private val noArgSchema = """{"type":"object","properties":{},"additionalProperties":false}"""
    private val nameArgSchema = """{"type":"object","properties":{"name":{"type":"string","description":"Character or NPC name"}},"required":["name"],"additionalProperties":false}"""
    private val modifierSchema = """{"type":"object","properties":{"characterName":{"type":"string","description":"Name of the character"},"rollType":{"type":"string","description":"Type of roll (e.g. 'Strength check', 'Stealth', 'Perception')"}},"required":["characterName","rollType"],"additionalProperties":false}"""

    override fun listTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "lookup_character_stats",
            description = "Look up a character's full stat block: ability scores with modifiers, HP, AC, proficiency, skills, saves, and inventory.",
            inputSchema = nameArgSchema
        ),
        ToolDefinition(
            name = "calculate_modifier",
            description = "Calculate the correct ability modifier for a character's roll. Returns the modifier including proficiency if applicable. Use this when setting diceRollRequest to get an accurate modifier.",
            inputSchema = modifierSchema
        ),
        ToolDefinition(
            name = "lookup_lore_npcs",
            description = "List all NPC profiles from world lore with their roles, motivations, and current locations.",
            inputSchema = noArgSchema
        ),
        ToolDefinition(
            name = "lookup_location_graph",
            description = "List all known locations in the world lore with their descriptions and connections. Use this to make coherent location transitions.",
            inputSchema = noArgSchema
        ),
        ToolDefinition(
            name = "check_pacing_status",
            description = "Check current round progress, turns at current location, and pacing guidance.",
            inputSchema = noArgSchema
        )
    )

    @Suppress("CyclomaticComplexMethod")
    override suspend fun callTool(call: ToolCall): ToolResult {
        val world = worldProvider()
        val content = when (call.toolName) {
            "lookup_character_stats" -> {
                val args = Json.parseToJsonElement(call.arguments).jsonObject
                val name = args["name"]?.jsonPrimitive?.content?.trim() ?: ""
                val character = world.party.find { it.name.equals(name, ignoreCase = true) }
                if (character != null) {
                    CharacterUtils.formatStats(character)
                } else {
                    "No character named '$name' in the party."
                }
            }

            "calculate_modifier" -> {
                val args = Json.parseToJsonElement(call.arguments).jsonObject
                val charName = args["characterName"]?.jsonPrimitive?.content?.trim() ?: ""
                val rollType = args["rollType"]?.jsonPrimitive?.content?.trim() ?: ""
                val character = world.party.find { it.name.equals(charName, ignoreCase = true) }
                if (character == null) {
                    "No character named '$charName' in the party."
                } else {
                    val modifier = CharacterUtils.expectedRollModifier(character, rollType)
                    if (modifier != null) {
                        val sign = if (modifier >= 0) "+" else ""
                        "${character.name} $rollType modifier: $sign$modifier"
                    } else {
                        "Could not determine modifier for '$rollType'. Specify the ability or skill (e.g. 'Strength check', 'Stealth')."
                    }
                }
            }

            "lookup_lore_npcs" -> {
                val npcs = world.lore.npcs
                if (npcs.isEmpty()) {
                    "No NPCs registered in world lore."
                } else {
                    npcs.joinToString("\n") { npc ->
                        "${npc.name} — ${npc.role} | Motivation: ${npc.motivation} | Location: ${npc.currentLocation}"
                    }
                }
            }

            "lookup_location_graph" -> {
                val locations = world.lore.locations
                if (locations.isEmpty()) {
                    "No locations registered in world lore."
                } else {
                    locations.joinToString("\n\n") { loc ->
                        buildString {
                            appendLine("${loc.name}: ${loc.description}")
                            append("  Connections: ${loc.connections.ifEmpty { listOf("none") }.joinToString(", ")}")
                        }
                    }
                }
            }

            "check_pacing_status" -> {
                val progress = if (world.maxRounds > 0) world.round.toFloat() / world.maxRounds else 1f
                val pct = (progress * 100).toInt()
                buildString {
                    appendLine("Round: ${world.round}/${world.maxRounds} ($pct%)")
                    appendLine("Current location: ${world.location.name}")
                    appendLine("Turns at this location: ${world.turnsAtCurrentLocation}")
                    if (world.turnsAtCurrentLocation >= 3) {
                        append("⚠ PACING WARNING: Party has been here ${world.turnsAtCurrentLocation} rounds. Force a transition.")
                    }
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
