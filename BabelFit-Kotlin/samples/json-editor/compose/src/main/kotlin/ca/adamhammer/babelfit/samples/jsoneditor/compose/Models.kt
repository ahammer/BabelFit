package ca.adamhammer.babelfit.samples.jsoneditor.compose

import java.util.concurrent.atomic.AtomicLong

private val idCounter = AtomicLong(0)

/** Sealed hierarchy for chat timeline entries. */
sealed class ChatEntry(val timestamp: Long = idCounter.incrementAndGet()) {
    class UserMessage(val text: String) : ChatEntry()
    class AgentResponse(val text: String) : ChatEntry()
    class ToolCall(val name: String, val args: String, val result: String) : ChatEntry()
    class Explain(val message: String) : ChatEntry()
    class AskQuestion(val question: String) : ChatEntry()
    class ErrorMessage(val text: String) : ChatEntry()
    class SystemMessage(val text: String) : ChatEntry()
    class DocumentLoaded(val filePath: String) : ChatEntry()
}

/** Toggle between tree view and raw JSON text. */
enum class ContentViewMode { TREE, RAW }

/** Stack-based document history with undo/redo (max [maxSize] steps). */
class DocumentHistory<T>(private val maxSize: Int = 20) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()
    private var _current: T? = null
    val current: T? get() = _current

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun reset(value: T) {
        past.clear()
        future.clear()
        _current = value
    }

    fun push(value: T) {
        _current?.let { past.addLast(it) }
        if (past.size > maxSize) past.removeFirst()
        future.clear()
        _current = value
    }

    fun undo(): T? {
        if (!canUndo) return null
        _current?.let { future.addFirst(it) }
        _current = past.removeLast()
        return _current
    }

    fun redo(): T? {
        if (!canRedo) return null
        _current?.let { past.addLast(it) }
        _current = future.removeFirst()
        return _current
    }
}
