package com.example.core.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.abs

/**
 * Stateful controller for the shared Guber Compose text editor.
 * Provides:
 * - Undo/Redo history stack with coalesced typing snapshots & cursor restoration
 * - Automatic smart indentation on newline
 * - Live syntax validation diagnostics
 * - Find & Replace navigation and batch replacement
 * - Code formatting and snippet insertion
 */
@Stable
class EditorStateController(
    initialText: String = "",
    initialLanguage: EditorLanguage = EditorLanguage.FLOWCHART,
    initialFontSizeSp: Float = 14f,
    initialWordWrap: Boolean = true,
    private val maxHistorySize: Int = 150,
    private val onTextChangedCallback: ((String) -> Unit)? = null
) {
    var language by mutableStateOf(initialLanguage)
        private set

    var textFieldValue by mutableStateOf(
        TextFieldValue(text = initialText, selection = TextRange(initialText.length))
    )
        private set

    var fontSizeSp by mutableFloatStateOf(initialFontSizeSp)
    var wordWrap by mutableStateOf(initialWordWrap)
    var showFindReplaceBar by mutableStateOf(false)
    var findQuery by mutableStateOf("")
        private set
    var replaceQuery by mutableStateOf("")
    var activeMatchIndex by mutableIntStateOf(0)
        private set

    var diagnostics by mutableStateOf<List<SyntaxDiagnostic>>(emptyList())
        private set

    private data class HistorySnapshot(
        val value: TextFieldValue,
        val timestampMs: Long
    )

    private val undoStack = ArrayDeque<HistorySnapshot>()
    private val redoStack = ArrayDeque<HistorySnapshot>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    val highlighter: SyntaxHighlighter
        get() = SyntaxHighlighterFactory.forLanguage(language)

    val findMatches: List<IntRange>
        get() {
            if (findQuery.isEmpty()) return emptyList()
            val text = textFieldValue.text
            val result = mutableListOf<IntRange>()
            var idx = 0
            while (idx < text.length) {
                val found = text.indexOf(findQuery, idx, ignoreCase = true)
                if (found == -1) break
                result.add(found until (found + findQuery.length))
                idx = found + findQuery.length.coerceAtLeast(1)
            }
            return result
        }

    init {
        revalidate(initialText)
    }

    fun setLanguageMode(newLanguage: EditorLanguage) {
        if (language != newLanguage) {
            language = newLanguage
            revalidate(textFieldValue.text)
        }
    }

    /**
     * Handles changes coming from BasicTextField.onValueChange, applying automatic
     * indentation when the user presses Enter and recording undo history snapshots.
     */
    fun onValueChange(newValue: TextFieldValue) {
        val oldValue = textFieldValue
        if (newValue.text == oldValue.text) {
            // Selection/cursor movement only
            textFieldValue = newValue
            return
        }

        val adjustedValue = applySmartIndentationIfNeeded(oldValue, newValue)
        recordUndoSnapshot(oldValue, adjustedValue)
        textFieldValue = adjustedValue
        revalidate(adjustedValue.text)
        onTextChangedCallback?.invoke(adjustedValue.text)
    }

    /**
     * Updates text externally (e.g., when loading a project or syncing from the visual canvas).
     */
    fun setTextProgrammatically(newText: String, recordHistory: Boolean = true) {
        if (newText == textFieldValue.text) return
        if (recordHistory) {
            pushUndoSnapshotForced(textFieldValue)
            redoStack.clear()
        } else {
            undoStack.clear()
            redoStack.clear()
        }
        val clampedCursor = textFieldValue.selection.start.coerceIn(0, newText.length)
        textFieldValue = TextFieldValue(text = newText, selection = TextRange(clampedCursor))
        revalidate(newText)
        onTextChangedCallback?.invoke(newText)
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val previous = undoStack.removeLast()
        redoStack.addLast(HistorySnapshot(textFieldValue, System.currentTimeMillis()))
        textFieldValue = previous.value
        revalidate(previous.value.text)
        onTextChangedCallback?.invoke(previous.value.text)
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val next = redoStack.removeLast()
        undoStack.addLast(HistorySnapshot(textFieldValue, System.currentTimeMillis()))
        textFieldValue = next.value
        revalidate(next.value.text)
        onTextChangedCallback?.invoke(next.value.text)
        return true
    }

    fun insertSnippet(snippet: String) {
        val current = textFieldValue
        pushUndoSnapshotForced(current)
        redoStack.clear()

        val start = current.selection.min.coerceIn(0, current.text.length)
        val end = current.selection.max.coerceIn(start, current.text.length)
        val updatedText = current.text.replaceRange(start, end, snippet)
        val newCursor = (start + snippet.length).coerceAtMost(updatedText.length)

        textFieldValue = TextFieldValue(
            text = updatedText,
            selection = TextRange(newCursor)
        )
        revalidate(updatedText)
        onTextChangedCallback?.invoke(updatedText)
    }

    fun formatDocument() {
        val raw = textFieldValue.text
        if (raw.isBlank()) return
        val lines = raw.split('\n')
        val formatted = buildString {
            lines.forEachIndexed { idx, line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    append("")
                } else {
                    val indent = computeIdealIndentForLine(trimmed, language)
                    append(" ".repeat(indent)).append(trimmed)
                }
                if (idx < lines.lastIndex) append('\n')
            }
        }
        if (formatted != raw) {
            setTextProgrammatically(formatted, recordHistory = true)
        }
    }

    fun updateFindQuery(query: String) {
        findQuery = query
        activeMatchIndex = 0
        val matches = findMatches
        if (matches.isNotEmpty()) {
            val first = matches.first()
            textFieldValue = textFieldValue.copy(selection = TextRange(first.first, first.last + 1))
        }
    }

    fun findNext() {
        val matches = findMatches
        if (matches.isEmpty()) return
        activeMatchIndex = (activeMatchIndex + 1) % matches.size
        val match = matches[activeMatchIndex]
        textFieldValue = textFieldValue.copy(selection = TextRange(match.first, match.last + 1))
    }

    fun findPrevious() {
        val matches = findMatches
        if (matches.isEmpty()) return
        activeMatchIndex = if (activeMatchIndex - 1 < 0) matches.lastIndex else activeMatchIndex - 1
        val match = matches[activeMatchIndex]
        textFieldValue = textFieldValue.copy(selection = TextRange(match.first, match.last + 1))
    }

    fun replaceCurrentMatch(): Int {
        val matches = findMatches
        if (matches.isEmpty()) return 0
        val safeIdx = activeMatchIndex.coerceIn(0, matches.lastIndex)
        val target = matches[safeIdx]
        val current = textFieldValue
        pushUndoSnapshotForced(current)
        redoStack.clear()

        val newText = current.text.replaceRange(target.first, target.last + 1, replaceQuery)
        val newCursor = (target.first + replaceQuery.length).coerceAtMost(newText.length)
        textFieldValue = TextFieldValue(text = newText, selection = TextRange(newCursor))
        revalidate(newText)
        onTextChangedCallback?.invoke(newText)
        val remaining = findMatches
        activeMatchIndex = if (remaining.isEmpty()) 0 else safeIdx.coerceAtMost(remaining.lastIndex)
        return 1
    }

    fun replaceAllMatches(): Int {
        if (findQuery.isEmpty()) return 0
        val matches = findMatches
        if (matches.isEmpty()) return 0
        val current = textFieldValue
        pushUndoSnapshotForced(current)
        redoStack.clear()

        val newText = current.text.replace(findQuery, replaceQuery, ignoreCase = true)
        textFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newText.length.coerceAtLeast(0))
        )
        activeMatchIndex = 0
        revalidate(newText)
        onTextChangedCallback?.invoke(newText)
        return matches.size
    }

    fun jumpToLine(lineIndex: Int) {
        val lines = textFieldValue.text.split('\n')
        if (lineIndex !in lines.indices) return
        var offset = 0
        for (i in 0 until lineIndex) {
            offset += lines[i].length + 1
        }
        val lineEnd = (offset + lines[lineIndex].length).coerceAtMost(textFieldValue.text.length)
        textFieldValue = textFieldValue.copy(selection = TextRange(offset, lineEnd))
    }

    fun currentCursorLineIndex(): Int {
        val cursor = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
        var line = 0
        for (i in 0 until cursor) {
            if (textFieldValue.text[i] == '\n') line++
        }
        return line
    }

    fun adjustFontSize(delta: Float) {
        fontSizeSp = (fontSizeSp + delta).coerceIn(11f, 24f)
    }

    private fun revalidate(text: String) {
        diagnostics = highlighter.validate(text)
    }

    private fun recordUndoSnapshot(oldValue: TextFieldValue, newValue: TextFieldValue) {
        val now = System.currentTimeMillis()
        val last = undoStack.lastOrNull()
        val lengthDelta = abs(newValue.text.length - oldValue.text.length)
        val insertedNewline = newValue.text.count { it == '\n' } != oldValue.text.count { it == '\n' }

        // Coalesce rapid single-character typing within 650ms unless a newline or paste occurred
        val shouldCoalesce = last != null &&
            (now - last.timestampMs) < 650L &&
            lengthDelta == 1 &&
            !insertedNewline &&
            undoStack.size > 1

        if (!shouldCoalesce) {
            pushUndoSnapshotForced(oldValue, now)
        }
        redoStack.clear()
    }

    private fun pushUndoSnapshotForced(value: TextFieldValue, timestamp: Long = System.currentTimeMillis()) {
        if (undoStack.lastOrNull()?.value?.text == value.text) return
        if (undoStack.size >= maxHistorySize) {
            undoStack.removeFirst()
        }
        undoStack.addLast(HistorySnapshot(value, timestamp))
    }

    private fun applySmartIndentationIfNeeded(
        oldValue: TextFieldValue,
        newValue: TextFieldValue
    ): TextFieldValue {
        // Detect if the user just typed a single '\n'
        if (newValue.text.length == oldValue.text.length + 1 &&
            newValue.selection.collapsed &&
            newValue.selection.start > 0 &&
            newValue.text[newValue.selection.start - 1] == '\n'
        ) {
            val newlinePos = newValue.selection.start - 1
            val textBeforeNewline = newValue.text.substring(0, newlinePos)
            val prevLine = textBeforeNewline.substringAfterLast('\n')
            val existingIndentCount = prevLine.takeWhile { it == ' ' }.length
            val trimmedPrev = prevLine.trim()
            val upperPrev = trimmedPrev.uppercase()

            val extraIndent = when {
                upperPrev == "CHOICE:" ||
                    upperPrev == "CHARACTERS:" ||
                    upperPrev == "ACTION:" ||
                    upperPrev == "DIALOGUE:" ||
                    upperPrev == "NARRATION:" ||
                    upperPrev == "CAMERA:" ||
                    upperPrev == "AUDIO:" ||
                    upperPrev == "DESCRIPTION:" ||
                    upperPrev == "ENVIRONMENT:" -> 2
                else -> 0
            }

            val totalSpaces = (existingIndentCount + extraIndent).coerceAtMost(12)
            if (totalSpaces > 0) {
                val indentStr = " ".repeat(totalSpaces)
                val updated = newValue.text.substring(0, newValue.selection.start) +
                    indentStr +
                    newValue.text.substring(newValue.selection.start)
                return TextFieldValue(
                    text = updated,
                    selection = TextRange(newValue.selection.start + totalSpaces)
                )
            }
        }
        return newValue
    }

    private fun computeIdealIndentForLine(trimmed: String, lang: EditorLanguage): Int {
        val upper = trimmed.uppercase()
        return when (lang) {
            EditorLanguage.FLOWCHART -> {
                val lower = trimmed.lowercase()
                if (lower.startsWith("yes:") || lower.startsWith("no:") || lower.startsWith("case ")) 2 else 0
            }
            EditorLanguage.DIALOGUE -> {
                if (trimmed.matches(Regex("^\\d+\\..*"))) 2 else 0
            }
            EditorLanguage.SCENE -> {
                val isTopHeader = upper.startsWith("SCENE:") ||
                    upper.startsWith("TITLE:") ||
                    upper.startsWith("LOCATION:") ||
                    upper.startsWith("SETTING:") ||
                    upper.startsWith("TIME:") ||
                    upper.startsWith("WEATHER:") ||
                    upper.startsWith("CHARACTERS:") ||
                    upper.startsWith("DESCRIPTION:") ||
                    upper.startsWith("ENVIRONMENT:") ||
                    upper.startsWith("ACTION:") ||
                    upper.startsWith("DIALOGUE:") ||
                    upper.startsWith("NARRATION:") ||
                    upper.startsWith("CAMERA:") ||
                    upper.startsWith("AUDIO:") ||
                    upper.startsWith("MUSIC:") ||
                    upper.startsWith("VFX:") ||
                    upper.startsWith("NOTES:") ||
                    upper.startsWith("TRANSITION:")
                if (isTopHeader) 0 else 2
            }
            EditorLanguage.PLAIN -> 0
        }
    }
}
