package ca.adamhammer.babelfit.samples.jsoneditor.compose

import ca.adamhammer.babelfit.samples.common.Vendor

/** Sealed hierarchy for chat timeline entries. */
sealed class ChatEntry(val timestamp: Long = System.currentTimeMillis()) {
    class UserMessage(val text: String) : ChatEntry()
    class AgentResponse(val text: String) : ChatEntry()
    class ToolCall(val name: String, val args: String, val result: String) : ChatEntry()
    class ErrorMessage(val text: String) : ChatEntry()
    class SystemMessage(val text: String) : ChatEntry()
    class DocumentLoaded(val filePath: String) : ChatEntry()
}

/** Toggle between tree view and raw JSON text. */
enum class ContentViewMode { TREE, RAW }

/** Persisted state for vendor/model selection. */
data class EditorConfig(
    val vendor: Vendor = Vendor.entries.firstOrNull { it.isAvailable() } ?: Vendor.OPENAI,
    val model: String = vendor.defaultModel
)
