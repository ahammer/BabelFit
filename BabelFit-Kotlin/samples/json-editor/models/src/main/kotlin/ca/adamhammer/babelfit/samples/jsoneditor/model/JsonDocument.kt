package ca.adamhammer.babelfit.samples.jsoneditor.model

import kotlinx.serialization.json.*
import java.io.File

/**
 * Immutable wrapper around a JSON tree. All mutation operations return a new instance.
 * Paths follow JSON Pointer style: "/key/0/nested" (split on '/').
 */
@Suppress("TooManyFunctions")
class JsonDocument private constructor(val root: JsonElement) {

    companion object {
        private val json = Json { prettyPrint = true }

        fun fromString(content: String): JsonDocument =
            JsonDocument(Json.parseToJsonElement(content))

        fun fromFile(file: File): JsonDocument =
            fromString(file.readText())

        fun empty(): JsonDocument =
            JsonDocument(JsonObject(emptyMap()))
    }

    // ── Path helpers ────────────────────────────────────────────────────

    private fun parsePath(path: String): List<String> =
        path.trim('/').split('/').filter { it.isNotEmpty() }

    private fun resolveChild(element: JsonElement, segment: String): JsonElement? = when (element) {
        is JsonObject -> element[segment]
        is JsonArray -> segment.toIntOrNull()?.let { element.getOrNull(it) }
        else -> null
    }

    private fun requireArrayIndex(segment: String): Int =
        segment.toIntOrNull() ?: throw IllegalArgumentException("Array index expected, got '$segment'")

    // ── Read operations ─────────────────────────────────────────────────

    fun getAtPath(path: String): JsonElement? {
        val segments = parsePath(path)
        if (segments.isEmpty()) return root
        return segments.fold<String, JsonElement?>(root) { cur, seg ->
            cur?.let { resolveChild(it, seg) }
        }
    }

    fun listKeys(path: String): List<String> = when (val node = getAtPath(path)) {
        is JsonObject -> node.keys.toList()
        is JsonArray -> (0 until node.size).map { it.toString() }
        else -> emptyList()
    }

    fun query(keyName: String): List<String> {
        val results = mutableListOf<String>()
        fun walk(element: JsonElement, currentPath: String) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) ->
                    val childPath = "$currentPath/$key"
                    if (key == keyName) results.add(childPath)
                    walk(value, childPath)
                }
                is JsonArray -> element.forEachIndexed { index, value ->
                    walk(value, "$currentPath/$index")
                }
                else -> {} // primitives
            }
        }
        walk(root, "")
        return results
    }

    // ── Write operations (return a new JsonDocument) ────────────────────

    fun setAtPath(path: String, value: JsonElement): JsonDocument {
        val segments = parsePath(path)
        if (segments.isEmpty()) return JsonDocument(value)
        return JsonDocument(setRecursive(root, segments, 0, value))
    }

    fun deleteAtPath(path: String): JsonDocument {
        val segments = parsePath(path)
        require(segments.isNotEmpty()) { "Cannot delete root" }
        return JsonDocument(deleteRecursive(root, segments, 0))
    }

    fun moveNode(fromPath: String, toPath: String): JsonDocument {
        val value = getAtPath(fromPath)
            ?: throw IllegalArgumentException("Source path not found: $fromPath")
        return deleteAtPath(fromPath).setAtPath(toPath, value)
    }

    // ── Set helpers ─────────────────────────────────────────────────────

    private fun setRecursive(current: JsonElement, segments: List<String>, depth: Int, value: JsonElement): JsonElement {
        val seg = segments[depth]
        val isLeaf = depth == segments.lastIndex
        return when (current) {
            is JsonObject -> setInObject(current, seg, segments, depth, value, isLeaf)
            is JsonArray -> setInArray(current, seg, segments, depth, value, isLeaf)
            else -> throw IllegalArgumentException("Cannot navigate through primitive at '$seg'")
        }
    }

    private fun setInObject(
        obj: JsonObject, seg: String, segments: List<String>,
        depth: Int, value: JsonElement, isLeaf: Boolean
    ): JsonObject {
        if (isLeaf) return JsonObject(obj.toMutableMap().apply { put(seg, value) })
        val child = obj[seg] ?: inferContainer(segments[depth + 1])
        val newValue = setRecursive(child, segments, depth + 1, value)
        return JsonObject(obj.toMutableMap().apply { put(seg, newValue) })
    }

    private fun setInArray(
        arr: JsonArray, seg: String, segments: List<String>,
        depth: Int, value: JsonElement, isLeaf: Boolean
    ): JsonArray {
        val idx = requireArrayIndex(seg)
        val list = arr.toMutableList()
        if (isLeaf) {
            if (idx == list.size) list.add(value) else list[idx] = value
            return JsonArray(list)
        }
        val child = arr.getOrNull(idx) ?: inferContainer(segments[depth + 1])
        val newValue = setRecursive(child, segments, depth + 1, value)
        if (idx == list.size) list.add(newValue) else list[idx] = newValue
        return JsonArray(list)
    }

    private fun inferContainer(nextSegment: String): JsonElement =
        if (nextSegment.toIntOrNull() != null) JsonArray(emptyList()) else JsonObject(emptyMap())

    // ── Delete helpers ──────────────────────────────────────────────────

    private fun deleteRecursive(current: JsonElement, segments: List<String>, depth: Int): JsonElement {
        val seg = segments[depth]
        val isLeaf = depth == segments.lastIndex
        return when (current) {
            is JsonObject -> deleteInObject(current, seg, segments, depth, isLeaf)
            is JsonArray -> deleteInArray(current, seg, segments, depth, isLeaf)
            else -> throw IllegalArgumentException("Cannot navigate through primitive at '$seg'")
        }
    }

    private fun deleteInObject(
        obj: JsonObject, seg: String, segments: List<String>,
        depth: Int, isLeaf: Boolean
    ): JsonObject {
        if (isLeaf) return JsonObject(obj.toMutableMap().apply { remove(seg) })
        val child = obj[seg]
            ?: throw IllegalArgumentException("Path not found at '$seg'")
        return JsonObject(
            obj.toMutableMap().apply { put(seg, deleteRecursive(child, segments, depth + 1)) }
        )
    }

    private fun deleteInArray(
        arr: JsonArray, seg: String, segments: List<String>,
        depth: Int, isLeaf: Boolean
    ): JsonArray {
        val idx = requireArrayIndex(seg)
        if (isLeaf) return JsonArray(arr.toMutableList().apply { removeAt(idx) })
        val child = arr.getOrNull(idx)
            ?: throw IllegalArgumentException("Index $idx out of bounds")
        val list = arr.toMutableList()
        list[idx] = deleteRecursive(child, segments, depth + 1)
        return JsonArray(list)
    }

    // ── Serialization ───────────────────────────────────────────────────

    fun toJsonString(prettyPrint: Boolean = true): String {
        val fmt = if (prettyPrint) json else Json { this.prettyPrint = false }
        return fmt.encodeToString(JsonElement.serializer(), root)
    }

    fun saveTo(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(toJsonString())
    }

    // ── Info ────────────────────────────────────────────────────────────

    fun nodeCount(): Int {
        fun count(el: JsonElement): Int = when (el) {
            is JsonObject -> 1 + el.values.sumOf { count(it) }
            is JsonArray -> 1 + el.sumOf { count(it) }
            else -> 1
        }
        return count(root)
    }

    fun depth(): Int {
        fun maxDepth(el: JsonElement): Int = when (el) {
            is JsonObject -> if (el.isEmpty()) 0 else 1 + (el.values.maxOfOrNull { maxDepth(it) } ?: 0)
            is JsonArray -> if (el.isEmpty()) 0 else 1 + (el.maxOfOrNull { maxDepth(it) } ?: 0)
            else -> 0
        }
        return maxDepth(root)
    }

    override fun toString(): String = toJsonString()
}
