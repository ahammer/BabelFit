package ca.adamhammer.babelfit.samples.jsoneditor.api

import ca.adamhammer.babelfit.interfaces.ToolProvider
import ca.adamhammer.babelfit.model.ToolCall
import ca.adamhammer.babelfit.model.ToolDefinition
import ca.adamhammer.babelfit.model.ToolResult
import ca.adamhammer.babelfit.samples.jsoneditor.model.JsonDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class JsonToolProvider(
    private val docProvider: () -> JsonDocument,
    private val docUpdater: (JsonDocument) -> Unit,
    private val listener: JsonEditorListener = object : JsonEditorListener {}
) : ToolProvider {

    override fun listTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "json_get",
            description = "Read the value at a JSON path. Returns the JSON subtree as a string.",
            inputSchema = """{
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "JSON Pointer path, e.g. '/users/0/name'. Use '' or '/' for root." }
                },
                "required": ["path"],
                "additionalProperties": false
            }"""
        ),
        ToolDefinition(
            name = "json_set",
            description = "Set a value at a path. Creates intermediates as needed. Value must be valid JSON.",
            inputSchema = """{
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "JSON Pointer path where the value should be set, e.g. '/users/0/name'" },
                    "value": { "type": "string", "description": "The JSON-encoded value to set, e.g. '\"hello\"', '42', '{\"key\": \"val\"}', '[1,2,3]'" }
                },
                "required": ["path", "value"],
                "additionalProperties": false
            }"""
        ),
        ToolDefinition(
            name = "json_delete",
            description = "Delete the node at a JSON path.",
            inputSchema = """{
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "JSON Pointer path to delete, e.g. '/users/1'" }
                },
                "required": ["path"],
                "additionalProperties": false
            }"""
        ),
        ToolDefinition(
            name = "json_move",
            description = "Move a node from one path to another.",
            inputSchema = """{
                "type": "object",
                "properties": {
                    "from": { "type": "string", "description": "Source JSON Pointer path" },
                    "to": { "type": "string", "description": "Destination JSON Pointer path" }
                },
                "required": ["from", "to"],
                "additionalProperties": false
            }"""
        ),
        ToolDefinition(
            name = "json_list",
            description = "List keys (for objects) or indices (for arrays) at a given path.",
            inputSchema = """{
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "JSON Pointer path to list children of. Use '' or '/' for root." }
                },
                "required": ["path"],
                "additionalProperties": false
            }"""
        ),
        ToolDefinition(
            name = "json_query",
            description = "Find all paths containing a key with the given name.",
            inputSchema = """{
                "type": "object",
                "properties": {
                    "key": { "type": "string", "description": "The key name to search for throughout the document" }
                },
                "required": ["key"],
                "additionalProperties": false
            }"""
        )
    )

    override suspend fun callTool(call: ToolCall): ToolResult {
        val args = Json.parseToJsonElement(call.arguments)
        val argsObj = args as? kotlinx.serialization.json.JsonObject
            ?: return errorResult(call, "Invalid arguments: expected JSON object")

        val result = try {
            when (call.toolName) {
                "json_get" -> handleGet(call, argsObj)
                "json_set" -> handleSet(call, argsObj)
                "json_delete" -> handleDelete(call, argsObj)
                "json_move" -> handleMove(call, argsObj)
                "json_list" -> handleList(call, argsObj)
                "json_query" -> handleQuery(call, argsObj)
                else -> errorResult(call, "Unknown tool: ${call.toolName}")
            }
        } catch (e: Exception) {
            errorResult(call, "Error: ${e.message}")
        }
        listener.onToolCall(call.toolName, call.arguments, result.content)
        return result
    }

    private fun handleGet(call: ToolCall, args: kotlinx.serialization.json.JsonObject): ToolResult {
        val path = args.stringArg("path")
        val value = docProvider().getAtPath(path)
            ?: return errorResult(call, "No value found at path: $path")
        val pretty = Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), value)
        return ToolResult(id = call.id, toolName = call.toolName, content = pretty)
    }

    private fun handleSet(call: ToolCall, args: kotlinx.serialization.json.JsonObject): ToolResult {
        val path = args.stringArg("path")
        val valueStr = args.stringArg("value")
        val value = Json.parseToJsonElement(valueStr)
        val newDoc = docProvider().setAtPath(path, value)
        docUpdater(newDoc)
        return ToolResult(id = call.id, toolName = call.toolName, content = "Set $path successfully")
    }

    private fun handleDelete(call: ToolCall, args: kotlinx.serialization.json.JsonObject): ToolResult {
        val path = args.stringArg("path")
        val newDoc = docProvider().deleteAtPath(path)
        docUpdater(newDoc)
        return ToolResult(id = call.id, toolName = call.toolName, content = "Deleted $path successfully")
    }

    private fun handleMove(call: ToolCall, args: kotlinx.serialization.json.JsonObject): ToolResult {
        val from = args.stringArg("from")
        val to = args.stringArg("to")
        val newDoc = docProvider().moveNode(from, to)
        docUpdater(newDoc)
        return ToolResult(id = call.id, toolName = call.toolName, content = "Moved $from → $to successfully")
    }

    private fun handleList(call: ToolCall, args: kotlinx.serialization.json.JsonObject): ToolResult {
        val path = args.stringArg("path")
        val keys = docProvider().listKeys(path)
        return ToolResult(id = call.id, toolName = call.toolName, content = keys.joinToString(", ").ifEmpty { "(empty)" })
    }

    private fun handleQuery(call: ToolCall, args: kotlinx.serialization.json.JsonObject): ToolResult {
        val key = args.stringArg("key")
        val paths = docProvider().query(key)
        val result = if (paths.isEmpty()) "No paths found containing key '$key'"
        else paths.joinToString("\n")
        return ToolResult(id = call.id, toolName = call.toolName, content = result)
    }

    private fun kotlinx.serialization.json.JsonObject.stringArg(name: String): String {
        val element = this[name] ?: throw IllegalArgumentException("Missing required argument: $name")
        return element.jsonPrimitive.content
    }

    private fun errorResult(call: ToolCall, message: String): ToolResult =
        ToolResult(id = call.id, toolName = call.toolName, content = message, isError = true)
}
