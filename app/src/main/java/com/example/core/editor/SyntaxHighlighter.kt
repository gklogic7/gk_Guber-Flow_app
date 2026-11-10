package com.example.core.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.example.ui.theme.EditorSyntaxPalette

enum class EditorLanguage(val displayName: String) {
    FLOWCHART("Flowchart DSL"),
    DIALOGUE("Dialogue DSL"),
    SCENE("Scene DSL"),
    PLAIN("Plain Text")
}

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class SyntaxDiagnostic(
    val lineIndex: Int, // 0-based line index
    val lineNumber: Int, // 1-based line number
    val message: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR
)

enum class TokenCategory {
    KEYWORD,
    COMMAND,
    CHARACTER,
    BRANCH_LABEL,
    CONDITION_QUESTION,
    STRING_DIALOGUE,
    COMMENT_NOTE,
    NUMBER_CHOICE,
    ERROR
}

data class SyntaxSpan(
    val start: Int,
    val end: Int,
    val category: TokenCategory,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underlineError: Boolean = false
)

interface SyntaxHighlighter {
    val language: EditorLanguage
    fun tokenize(text: String): List<SyntaxSpan>
    fun validate(text: String): List<SyntaxDiagnostic>
    fun quickSnippets(): List<Pair<String, String>>
}

class FlowchartSyntaxHighlighter : SyntaxHighlighter {
    override val language = EditorLanguage.FLOWCHART

    private val blockPrefixes = listOf(
        "input:", "process:", "decision:", "note:", "goto:", "branch:", "loop:"
    )
    private val branchPrefixes = listOf(
        "yes:", "no:", "true:", "false:", "default:", "else:"
    )

    override fun tokenize(text: String): List<SyntaxSpan> {
        val spans = mutableListOf<SyntaxSpan>()
        var lineStart = 0
        val lines = text.split('\n')

        for (line in lines) {
            val lineEnd = lineStart + line.length
            val trimmed = line.trim()
            val leadingSpaces = line.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) 0 else it }
            val contentStart = lineStart + leadingSpaces

            if (trimmed.isNotEmpty()) {
                val lower = trimmed.lowercase()
                when {
                    trimmed.startsWith("#") || trimmed.startsWith("//") -> {
                        spans.add(SyntaxSpan(contentStart, lineEnd, TokenCategory.COMMENT_NOTE, italic = true))
                    }
                    lower == "start" || lower == "end" || lower.startsWith("start ") || lower.startsWith("end ") -> {
                        spans.add(SyntaxSpan(contentStart, lineEnd, TokenCategory.KEYWORD, bold = true))
                    }
                    lower.startsWith("note:") -> {
                        val colonIdx = line.indexOf(':', contentStart - lineStart)
                        if (colonIdx != -1) {
                            spans.add(SyntaxSpan(contentStart, lineStart + colonIdx + 1, TokenCategory.COMMENT_NOTE, bold = true))
                            spans.add(SyntaxSpan(lineStart + colonIdx + 1, lineEnd, TokenCategory.COMMENT_NOTE, italic = true))
                        }
                    }
                    blockPrefixes.any { lower.startsWith(it) } -> {
                        val colonIdx = line.indexOf(':')
                        if (colonIdx != -1) {
                            spans.add(SyntaxSpan(contentStart, lineStart + colonIdx + 1, TokenCategory.COMMAND, bold = true))
                            val restTrimmed = line.substring(colonIdx + 1).trim()
                            if (lower.startsWith("decision:") || restTrimmed.endsWith("?")) {
                                spans.add(SyntaxSpan(lineStart + colonIdx + 1, lineEnd, TokenCategory.CONDITION_QUESTION, bold = true))
                            } else {
                                spans.add(SyntaxSpan(lineStart + colonIdx + 1, lineEnd, TokenCategory.STRING_DIALOGUE))
                            }
                        }
                    }
                    branchPrefixes.any { lower.startsWith(it) } || lower.startsWith("case ") -> {
                        val colonIdx = line.indexOf(':')
                        if (colonIdx != -1) {
                            spans.add(SyntaxSpan(contentStart, lineStart + colonIdx + 1, TokenCategory.BRANCH_LABEL, bold = true))
                            val afterColon = line.substring(colonIdx + 1)
                            val arrowIdx = afterColon.indexOf("->")
                            if (arrowIdx != -1) {
                                val absArrow = lineStart + colonIdx + 1 + arrowIdx
                                spans.add(SyntaxSpan( absArrow, absArrow + 2, TokenCategory.KEYWORD, bold = true))
                            }
                        }
                    }
                    trimmed.contains("->") -> {
                        val arrowIdx = line.indexOf("->")
                        spans.add(SyntaxSpan(contentStart, lineStart + arrowIdx, TokenCategory.CHARACTER, bold = true))
                        spans.add(SyntaxSpan(lineStart + arrowIdx, lineStart + arrowIdx + 2, TokenCategory.KEYWORD, bold = true))
                        spans.add(SyntaxSpan(lineStart + arrowIdx + 2, lineEnd, TokenCategory.BRANCH_LABEL, bold = true))
                    }
                    trimmed.endsWith("?") -> {
                        spans.add(SyntaxSpan(contentStart, lineEnd, TokenCategory.CONDITION_QUESTION, bold = true))
                    }
                }
            }
            lineStart = lineEnd + 1
        }
        return spans
    }

    override fun validate(text: String): List<SyntaxDiagnostic> {
        val diagnostics = mutableListOf<SyntaxDiagnostic>()
        val lines = text.split('\n')
        var hasDecisionContext = false
        var hasStart = false

        lines.forEachIndexed { index, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) return@forEachIndexed
            val lower = trimmed.lowercase()

            if (lower == "start" || lower.startsWith("start:")) hasStart = true
            if (trimmed.endsWith("?") || lower.startsWith("decision:")) {
                hasDecisionContext = true
            }
            if (lower.endsWith(":") && (lower.startsWith("input:") || lower.startsWith("process:") || lower.startsWith("decision:") || lower.startsWith("yes:") || lower.startsWith("no:"))) {
                diagnostics.add(
                    SyntaxDiagnostic(
                        lineIndex = index,
                        lineNumber = index + 1,
                        message = "Directive '${trimmed}' is missing a node label after ':'",
                        severity = DiagnosticSeverity.ERROR
                    )
                )
            }
            if ((lower.startsWith("yes:") || lower.startsWith("no:")) && !hasDecisionContext) {
                diagnostics.add(
                    SyntaxDiagnostic(
                        lineIndex = index,
                        lineNumber = index + 1,
                        message = "Branch '${trimmed.substringBefore(":")}:' appears before any Decision (?) node",
                        severity = DiagnosticSeverity.WARNING
                    )
                )
            }
            if (trimmed.contains("->")) {
                val parts = trimmed.split("->")
                if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    diagnostics.add(
                        SyntaxDiagnostic(
                            lineIndex = index,
                            lineNumber = index + 1,
                            message = "Connector '->' requires both a source and target node name",
                            severity = DiagnosticSeverity.ERROR
                        )
                    )
                }
            }
        }
        if (lines.any { it.isNotBlank() } && !hasStart) {
            diagnostics.add(
                SyntaxDiagnostic(
                    lineIndex = 0,
                    lineNumber = 1,
                    message = "Tip: Start your flowchart with 'Start' for a standard terminator node",
                    severity = DiagnosticSeverity.INFO
                )
            )
        }
        return diagnostics
    }

    override fun quickSnippets(): List<Pair<String, String>> = listOf(
        "Start" to "Start\n",
        "Decision?" to "Decision: Is condition met?\n  yes: Proceed\n  no: Fallback\n",
        "Input:" to "Input: Read user data\n",
        "Process:" to "Process: Execute action\n",
        "yes / no" to "yes: Action A\nno: Action B\n",
        "Note:" to "Note: Add context note\n",
        "Link ->" to "Step A -> Step B\n",
        "End" to "End\n"
    )
}

class DialogueSyntaxHighlighter : SyntaxHighlighter {
    override val language = EditorLanguage.DIALOGUE

    override fun tokenize(text: String): List<SyntaxSpan> {
        val spans = mutableListOf<SyntaxSpan>()
        var lineStart = 0
        val lines = text.split('\n')

        for (line in lines) {
            val lineEnd = lineStart + line.length
            val trimmed = line.trim()
            val leading = line.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) 0 else it }
            val startIdx = lineStart + leading

            if (trimmed.isNotEmpty()) {
                val upper = trimmed.uppercase()
                when {
                    trimmed.startsWith("#") || trimmed.startsWith("//") -> {
                        spans.add(SyntaxSpan(startIdx, lineEnd, TokenCategory.COMMENT_NOTE, italic = true))
                    }
                    upper.startsWith("SCENE:") || upper.startsWith("END_CHOICE") -> {
                        val colon = line.indexOf(':')
                        if (colon != -1) {
                            spans.add(SyntaxSpan(startIdx, lineStart + colon + 1, TokenCategory.KEYWORD, bold = true))
                            spans.add(SyntaxSpan(lineStart + colon + 1, lineEnd, TokenCategory.NUMBER_CHOICE, bold = true))
                        } else {
                            spans.add(SyntaxSpan(startIdx, lineEnd, TokenCategory.KEYWORD, bold = true))
                        }
                    }
                    upper.startsWith("CHARACTER:") -> {
                        val colon = line.indexOf(':')
                        spans.add(SyntaxSpan(startIdx, lineStart + colon + 1, TokenCategory.COMMAND, bold = true))
                        spans.add(SyntaxSpan(lineStart + colon + 1, lineEnd, TokenCategory.CHARACTER, bold = true))
                    }
                    upper.startsWith("DIALOGUE:") || upper.startsWith("THOUGHT:") -> {
                        val colon = line.indexOf(':')
                        spans.add(SyntaxSpan(startIdx, lineStart + colon + 1, TokenCategory.COMMAND, bold = true))
                        spans.add(SyntaxSpan(lineStart + colon + 1, lineEnd, TokenCategory.STRING_DIALOGUE))
                    }
                    upper.startsWith("NARRATION:") -> {
                        val colon = line.indexOf(':')
                        spans.add(SyntaxSpan(startIdx, lineStart + colon + 1, TokenCategory.COMMENT_NOTE, bold = true))
                        spans.add(SyntaxSpan(lineStart + colon + 1, lineEnd, TokenCategory.COMMENT_NOTE, italic = true))
                    }
                    upper.startsWith("CHOICE:") || upper.startsWith("IF:") || upper.startsWith("SET:") -> {
                        val colon = line.indexOf(':')
                        spans.add(SyntaxSpan(startIdx, lineStart + colon + 1, TokenCategory.CONDITION_QUESTION, bold = true))
                        spans.add(SyntaxSpan(lineStart + colon + 1, lineEnd, TokenCategory.BRANCH_LABEL, bold = true))
                    }
                    upper.startsWith("OPTION:") || upper.startsWith("GOTO:") -> {
                        val colon = line.indexOf(':')
                        spans.add(SyntaxSpan(startIdx, lineStart + colon + 1, TokenCategory.BRANCH_LABEL, bold = true))
                        spans.add(SyntaxSpan(lineStart + colon + 1, lineEnd, TokenCategory.NUMBER_CHOICE, bold = true))
                    }
                    trimmed.matches(Regex("^\\d+\\..*")) -> {
                        val dotIdx = line.indexOf('.')
                        spans.add(SyntaxSpan(startIdx, lineStart + dotIdx + 1, TokenCategory.NUMBER_CHOICE, bold = true))
                        spans.add(SyntaxSpan(lineStart + dotIdx + 1, lineEnd, TokenCategory.BRANCH_LABEL))
                    }
                }
            }
            lineStart = lineEnd + 1
        }
        return spans
    }

    override fun validate(text: String): List<SyntaxDiagnostic> {
        val diagnostics = mutableListOf<SyntaxDiagnostic>()
        val lines = text.split('\n')
        var openChoiceLine = -1

        lines.forEachIndexed { index, raw ->
            val trimmed = raw.trim()
            val upper = trimmed.uppercase()
            if (upper.startsWith("CHOICE:")) {
                openChoiceLine = index
            } else if (upper == "END_CHOICE") {
                if (openChoiceLine == -1) {
                    diagnostics.add(
                        SyntaxDiagnostic(
                            lineIndex = index,
                            lineNumber = index + 1,
                            message = "'END_CHOICE' found without a matching 'CHOICE:' block",
                            severity = DiagnosticSeverity.WARNING
                        )
                    )
                }
                openChoiceLine = -1
            } else if (upper == "CHARACTER:" || upper == "OPTION:" || upper == "GOTO:") {
                diagnostics.add(
                    SyntaxDiagnostic(
                        lineIndex = index,
                        lineNumber = index + 1,
                        message = "'$trimmed' requires a value after ':'",
                        severity = DiagnosticSeverity.ERROR
                    )
                )
            }
        }
        if (openChoiceLine != -1) {
            diagnostics.add(
                SyntaxDiagnostic(
                    lineIndex = openChoiceLine,
                    lineNumber = openChoiceLine + 1,
                    message = "Open 'CHOICE:' block is not closed with 'END_CHOICE'",
                    severity = DiagnosticSeverity.WARNING
                )
            )
        }
        return diagnostics
    }

    override fun quickSnippets(): List<Pair<String, String>> = listOf(
        "CHARACTER" to "CHARACTER: Name\nDIALOGUE: ",
        "DIALOGUE" to "DIALOGUE: ",
        "NARRATION" to "NARRATION:\n  ",
        "CHOICE Block" to "CHOICE:\n  1. Option A\n  2. Option B\n\nOPTION: Option A\nDIALOGUE: Response A\nGOTO: END_CHOICE\n\nOPTION: Option B\nDIALOGUE: Response B\n\nEND_CHOICE\n",
        "OPTION" to "OPTION: ChoiceName\nDIALOGUE: ",
        "GOTO" to "GOTO: END_CHOICE\n",
        "SET Flag" to "SET: flag_name = true\n"
    )
}

class SceneSyntaxHighlighter : SyntaxHighlighter {
    override val language = EditorLanguage.SCENE

    private val metadataHeaders = setOf(
        "SCENE:", "TITLE:", "LOCATION:", "SETTING:", "TIME:", "WEATHER:", "TRANSITION:", "LINK:"
    )
    private val sectionHeaders = setOf(
        "CHARACTERS:", "DESCRIPTION:", "ENVIRONMENT:", "ACTION:", "DIALOGUE:",
        "NARRATION:", "CAMERA:", "AUDIO:", "MUSIC:", "SFX:", "VFX:", "NOTES:"
    )

    override fun tokenize(text: String): List<SyntaxSpan> {
        val spans = mutableListOf<SyntaxSpan>()
        var lineStart = 0
        val lines = text.split('\n')

        for (line in lines) {
            val lineEnd = lineStart + line.length
            val trimmed = line.trim()
            val leading = line.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) 0 else it }
            val startIdx = lineStart + leading

            if (trimmed.isNotEmpty()) {
                val upper = trimmed.uppercase()
                val headerToken = upper.substringBefore(':') + ":"
                when {
                    trimmed.startsWith("#") || trimmed.startsWith("//") -> {
                        spans.add(SyntaxSpan(startIdx, lineEnd, TokenCategory.COMMENT_NOTE, italic = true))
                    }
                    headerToken in metadataHeaders && trimmed.contains(':') -> {
                        val colon = line.indexOf(':')
                        spans.add(SyntaxSpan(startIdx, lineStart + colon + 1, TokenCategory.KEYWORD, bold = true))
                        spans.add(SyntaxSpan(lineStart + colon + 1, lineEnd, TokenCategory.NUMBER_CHOICE, bold = true))
                    }
                    headerToken in sectionHeaders && trimmed.contains(':') -> {
                        val colon = line.indexOf(':')
                        spans.add(SyntaxSpan(startIdx, lineStart + colon + 1, TokenCategory.COMMAND, bold = true))
                        spans.add(SyntaxSpan(lineStart + colon + 1, lineEnd, TokenCategory.STRING_DIALOGUE))
                    }
                    trimmed.startsWith("- ") -> {
                        spans.add(SyntaxSpan(startIdx, startIdx + 2, TokenCategory.KEYWORD, bold = true))
                        spans.add(SyntaxSpan(startIdx + 2, lineEnd, TokenCategory.CHARACTER, bold = true))
                    }
                    trimmed.contains(':') && leading > 0 -> {
                        val colon = line.indexOf(':')
                        spans.add(SyntaxSpan(startIdx, lineStart + colon + 1, TokenCategory.CHARACTER, bold = true))
                        spans.add(SyntaxSpan(lineStart + colon + 1, lineEnd, TokenCategory.STRING_DIALOGUE))
                    }
                }
            }
            lineStart = lineEnd + 1
        }
        return spans
    }

    override fun validate(text: String): List<SyntaxDiagnostic> {
        val diagnostics = mutableListOf<SyntaxDiagnostic>()
        val lines = text.split('\n')
        var hasTitle = false
        var hasScene = false

        lines.forEachIndexed { index, raw ->
            val trimmed = raw.trim()
            val upper = trimmed.uppercase()
            if (upper.startsWith("SCENE:")) hasScene = true
            if (upper.startsWith("TITLE:")) {
                hasTitle = true
                if (trimmed.substringAfter(':').isBlank()) {
                    diagnostics.add(
                        SyntaxDiagnostic(
                            lineIndex = index,
                            lineNumber = index + 1,
                            message = "'TITLE:' cannot be empty",
                            severity = DiagnosticSeverity.ERROR
                        )
                    )
                }
            }
        }
        if (lines.any { it.isNotBlank() } && (!hasScene || !hasTitle)) {
            diagnostics.add(
                SyntaxDiagnostic(
                    lineIndex = 0,
                    lineNumber = 1,
                    message = "Scene script should include 'SCENE:' number and 'TITLE:' header",
                    severity = DiagnosticSeverity.WARNING
                )
            )
        }
        return diagnostics
    }

    override fun quickSnippets(): List<Pair<String, String>> = listOf(
        "New Scene" to "\nSCENE: 02\nTITLE: New Scene\nLOCATION: INT. Location\nTIME: Night\nWEATHER: Clear\n",
        "CHARACTERS" to "CHARACTERS:\n  - ",
        "ACTION" to "ACTION:\n  ",
        "DIALOGUE" to "DIALOGUE:\n  Speaker: ",
        "CAMERA" to "CAMERA:\n  Wide shot of ",
        "AUDIO" to "AUDIO:\n  ",
        "TRANSITION" to "TRANSITION: Cut To\n"
    )
}

class PlainSyntaxHighlighter : SyntaxHighlighter {
    override val language = EditorLanguage.PLAIN
    override fun tokenize(text: String): List<SyntaxSpan> = emptyList()
    override fun validate(text: String): List<SyntaxDiagnostic> = emptyList()
    override fun quickSnippets(): List<Pair<String, String>> = emptyList()
}

object SyntaxHighlighterFactory {
    private val flowchart = FlowchartSyntaxHighlighter()
    private val dialogue = DialogueSyntaxHighlighter()
    private val scene = SceneSyntaxHighlighter()
    private val plain = PlainSyntaxHighlighter()

    fun forLanguage(language: EditorLanguage): SyntaxHighlighter = when (language) {
        EditorLanguage.FLOWCHART -> flowchart
        EditorLanguage.DIALOGUE -> dialogue
        EditorLanguage.SCENE -> scene
        EditorLanguage.PLAIN -> plain
    }
}

/**
 * VisualTransformation for BasicTextField that applies syntax highlighting spans,
 * find-and-replace match highlights, and diagnostic error underlines while preserving
 * 1:1 character offsets via OffsetMapping.Identity.
 */
class SyntaxVisualTransformation(
    private val highlighter: SyntaxHighlighter,
    private val palette: EditorSyntaxPalette,
    private val diagnostics: List<SyntaxDiagnostic>,
    private val findQuery: String = "",
    private val activeMatchRange: IntRange? = null
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val styled = buildAnnotatedString {
            append(raw)

            // 1. Apply language syntax tokens
            val spans = highlighter.tokenize(raw)
            for (span in spans) {
                val safeStart = span.start.coerceIn(0, raw.length)
                val safeEnd = span.end.coerceIn(safeStart, raw.length)
                if (safeStart < safeEnd) {
                    val color = when (span.category) {
                        TokenCategory.KEYWORD -> palette.keyword
                        TokenCategory.COMMAND -> palette.command
                        TokenCategory.CHARACTER -> palette.characterName
                        TokenCategory.BRANCH_LABEL -> palette.branchLabel
                        TokenCategory.CONDITION_QUESTION -> palette.conditionQuestion
                        TokenCategory.STRING_DIALOGUE -> palette.stringOrDialogue
                        TokenCategory.COMMENT_NOTE -> palette.commentOrNote
                        TokenCategory.NUMBER_CHOICE -> palette.numberOrChoice
                        TokenCategory.ERROR -> palette.errorUnderline
                    }
                    addStyle(
                        style = SpanStyle(
                            color = color,
                            fontWeight = if (span.bold) FontWeight.SemiBold else FontWeight.Normal,
                            fontStyle = if (span.italic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (span.underlineError) TextDecoration.Underline else TextDecoration.None
                        ),
                        start = safeStart,
                        end = safeEnd
                    )
                }
            }

            // 2. Apply diagnostic error/warning underlines by line
            if (diagnostics.isNotEmpty()) {
                val lines = raw.split('\n')
                var cursor = 0
                lines.forEachIndexed { idx, line ->
                    val lineEnd = cursor + line.length
                    val diag = diagnostics.firstOrNull { it.lineIndex == idx }
                    if (diag != null && line.isNotBlank()) {
                        val firstNonSpace = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                        val underlineColor = when (diag.severity) {
                            DiagnosticSeverity.ERROR -> palette.errorUnderline
                            DiagnosticSeverity.WARNING -> palette.warningUnderline
                            DiagnosticSeverity.INFO -> palette.command
                        }
                        if (diag.severity != DiagnosticSeverity.INFO) {
                            addStyle(
                                style = SpanStyle(
                                    textDecoration = TextDecoration.Underline,
                                    color = underlineColor
                                ),
                                start = (cursor + firstNonSpace).coerceAtMost(raw.length),
                                end = lineEnd.coerceAtMost(raw.length)
                            )
                        }
                    }
                    cursor = lineEnd + 1
                }
            }

            // 3. Apply Find query highlights
            if (findQuery.isNotEmpty()) {
                var searchIdx = 0
                while (searchIdx < raw.length) {
                    val found = raw.indexOf(findQuery, searchIdx, ignoreCase = true)
                    if (found == -1) break
                    val matchEnd = (found + findQuery.length).coerceAtMost(raw.length)
                    val isCurrent = activeMatchRange != null && activeMatchRange.first == found
                    addStyle(
                        style = SpanStyle(
                            background = if (isCurrent) palette.activeFindMatchHighlight else palette.findMatchHighlight,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        ),
                        start = found,
                        end = matchEnd
                    )
                    searchIdx = found + findQuery.length.coerceAtLeast(1)
                }
            }
        }

        return TransformedText(styled, OffsetMapping.Identity)
    }
}
