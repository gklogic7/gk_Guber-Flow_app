package com.example.core.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalEditorSyntaxPalette
import com.example.ui.theme.editorTextStyle

/**
 * Shared Compose-based code/DSL text editor built on BasicTextField.
 * Supports:
 * - Synchronized Line Numbers gutter with active-line & diagnostic error indicators
 * - Live Syntax Highlighting via SyntaxVisualTransformation
 * - Undo / Redo state management with keyboard shortcuts (Ctrl+Z, Ctrl+Y, Ctrl+Shift+Z)
 * - Find & Replace bar with match navigation
 * - Automatic indentation & code formatting
 * - Adjustable font size & word wrapping toggle
 */
@Composable
fun GuberTextEditor(
    controller: EditorStateController,
    onManualGenerate: () -> Unit,
    modifier: Modifier = Modifier,
    headerTitle: String = controller.language.displayName
) {
    val palette = LocalEditorSyntaxPalette.current
    val clipboardManager = LocalClipboardManager.current
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showDiagnosticsDrawer by remember { mutableStateOf(false) }

    val lines = remember(controller.textFieldValue.text) {
        controller.textFieldValue.text.split('\n')
    }
    val activeLineIndex = remember(controller.textFieldValue) {
        controller.currentCursorLineIndex()
    }
    val matches = controller.findMatches
    val activeMatchRange = matches.getOrNull(controller.activeMatchIndex)

    val visualTransformation = remember(
        controller.language,
        palette,
        controller.diagnostics,
        controller.findQuery,
        activeMatchRange
    ) {
        SyntaxVisualTransformation(
            highlighter = controller.highlighter,
            palette = palette,
            diagnostics = controller.diagnostics,
            findQuery = controller.findQuery,
            activeMatchRange = activeMatchRange
        )
    }

    val selectionColors = remember(palette) {
        TextSelectionColors(
            handleColor = palette.activeLineText,
            backgroundColor = palette.selectionBackground
        )
    }

    val errorCount = controller.diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
    val warningCount = controller.diagnostics.count { it.severity == DiagnosticSeverity.WARNING }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.editorBackground)
            .testTag("guber_text_editor_root")
    ) {
        // 1. Compact Editor Action Toolbar
        Surface(
            color = palette.gutterBackground,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Language pill badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Undo
                IconButton(
                    onClick = { controller.undo() },
                    enabled = controller.canUndo,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.editor_undo),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Redo
                IconButton(
                    onClick = { controller.redo() },
                    enabled = controller.canRedo,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.editor_redo),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Find & Replace Toggle
                IconButton(
                    onClick = { controller.showFindReplaceBar = !controller.showFindReplaceBar },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_find_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FindReplace,
                        contentDescription = stringResource(R.string.editor_find),
                        tint = if (controller.showFindReplaceBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Auto-Format / Indent
                IconButton(
                    onClick = { controller.formatDocument() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_format_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Format Indentation",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Word Wrap Toggle
                IconButton(
                    onClick = { controller.wordWrap = !controller.wordWrap },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_wrap_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.WrapText,
                        contentDescription = stringResource(R.string.editor_wrap),
                        tint = if (controller.wordWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Font Size Decrease / Increase
                IconButton(
                    onClick = { controller.adjustFontSize(-1f) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_font_decrease_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.TextDecrease,
                        contentDescription = stringResource(R.string.editor_font_decrease),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "${controller.fontSizeSp.toInt()}sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { controller.adjustFontSize(1f) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_font_increase_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.TextIncrease,
                        contentDescription = stringResource(R.string.editor_font_increase),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Copy & Paste
                IconButton(
                    onClick = {
                        val sel = controller.textFieldValue.selection
                        val textToCopy = if (!sel.collapsed) {
                            controller.textFieldValue.text.substring(sel.min, sel.max)
                        } else {
                            controller.textFieldValue.text
                        }
                        clipboardManager.setText(AnnotatedString(textToCopy))
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Text",
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = {
                        clipboardManager.getText()?.text?.let { clipText ->
                            controller.insertSnippet(clipText)
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("editor_paste_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste Text",
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Manual Generate Button
                FilledTonalButton(
                    onClick = onManualGenerate,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("editor_generate_button"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.editor_generate),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 2. Quick Syntax Snippet Bar
        val snippets = remember(controller.language) { controller.highlighter.quickSnippets() }
        if (snippets.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.gutterBackground.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("editor_snippet_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(snippets) { (label, code) ->
                    AssistChip(
                        onClick = { controller.insertSnippet(code) },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = palette.editorBackground
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        // 3. Collapsible Find & Replace Bar
        AnimatedVisibility(visible = controller.showFindReplaceBar) {
            FindReplacePanel(
                controller = controller,
                matchCount = matches.size,
                onClose = { controller.showFindReplaceBar = false }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

        // 4. Main Synchronized Line Number Gutter + BasicTextField Canvas
        val textStyle = editorTextStyle(controller.fontSizeSp).copy(
            color = MaterialTheme.colorScheme.onSurface
        )
        val density = LocalDensity.current
        val defaultLineHeightPx = with(density) { (controller.fontSizeSp * 1.55f).sp.toPx() }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(verticalScrollState)
        ) {
            // Left Gutter: Line numbers + Error/Warning dots
            val gutterWidth = remember(lines.size, controller.fontSizeSp) {
                val digits = lines.size.toString().length.coerceAtLeast(2)
                (digits * (controller.fontSizeSp * 0.65f) + 28f).dp
            }

            Box(
                modifier = Modifier
                    .width(gutterWidth)
                    .fillMaxHeight()
                    .background(palette.gutterBackground)
                    .padding(top = 12.dp, bottom = 24.dp)
                    .testTag("editor_line_numbers_gutter")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    lines.forEachIndexed { lineIdx, _ ->
                        val isCurrentLine = lineIdx == activeLineIndex
                        val lineDiagnostic = controller.diagnostics.firstOrNull { it.lineIndex == lineIdx }

                        // Calculate exact visual height of this source line if wrapped in BasicTextField
                        val lineHeightDp = remember(textLayoutResult, lineIdx, controller.fontSizeSp, controller.wordWrap) {
                            val layout = textLayoutResult
                            if (layout != null) {
                                val charRange = getLineCharOffsets(controller.textFieldValue.text, lineIdx)
                                if (charRange != null && charRange.first <= layout.layoutInput.text.length) {
                                    val firstVisualLine = layout.getLineForOffset(charRange.first)
                                    val lastVisualLine = layout.getLineForOffset(
                                        charRange.second.coerceAtMost(layout.layoutInput.text.length)
                                    )
                                    val topPx = layout.getLineTop(firstVisualLine)
                                    val bottomPx = layout.getLineBottom(lastVisualLine)
                                    with(density) { (bottomPx - topPx).coerceAtLeast(defaultLineHeightPx).toDp() }
                                } else {
                                    with(density) { defaultLineHeightPx.toDp() }
                                }
                            } else {
                                with(density) { defaultLineHeightPx.toDp() }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lineHeightDp)
                                .background(
                                    if (isCurrentLine) palette.activeLineBackground else palette.gutterBackground
                                )
                                .clickable { controller.jumpToLine(lineIdx) }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (lineDiagnostic != null) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp, end = 4.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (lineDiagnostic.severity) {
                                                DiagnosticSeverity.ERROR -> palette.errorUnderline
                                                DiagnosticSeverity.WARNING -> palette.warningUnderline
                                                DiagnosticSeverity.INFO -> palette.command
                                            }
                                        )
                                )
                            }
                            Text(
                                text = (lineIdx + 1).toString(),
                                style = textStyle.copy(
                                    color = if (isCurrentLine) palette.activeLineText else palette.gutterText,
                                    fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.End
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Vertical divider between gutter and editor
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            )

            // Right Code Area: BasicTextField with active line highlight and syntax transformation
            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                val editorHorizontalModifier = if (controller.wordWrap) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(editorHorizontalModifier)
                        .padding(top = 12.dp, bottom = 24.dp, start = 10.dp, end = 12.dp)
                ) {
                    BasicTextField(
                        value = controller.textFieldValue,
                        onValueChange = { controller.onValueChange(it) },
                        textStyle = textStyle,
                        cursorBrush = SolidColor(palette.activeLineText),
                        visualTransformation = visualTransformation,
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guber_basic_text_field")
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                                    when (event.key) {
                                        Key.Z -> {
                                            if (event.isShiftPressed) controller.redo() else controller.undo()
                                            true
                                        }
                                        Key.Y -> {
                                            controller.redo()
                                            true
                                        }
                                        Key.F -> {
                                            controller.showFindReplaceBar = !controller.showFindReplaceBar
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .drawBehind {
                                val layout = textLayoutResult ?: return@drawBehind
                                val text = controller.textFieldValue.text
                                val activeOffsets = getLineCharOffsets(text, activeLineIndex)
                                if (activeOffsets != null && activeOffsets.first <= layout.layoutInput.text.length) {
                                    val firstVisual = layout.getLineForOffset(activeOffsets.first)
                                    val lastVisual = layout.getLineForOffset(
                                        activeOffsets.second.coerceAtMost(layout.layoutInput.text.length)
                                    )
                                    val top = layout.getLineTop(firstVisual)
                                    val bottom = layout.getLineBottom(lastVisual)
                                    drawRect(
                                        color = palette.activeLineBackground,
                                        topLeft = Offset(0f, top),
                                        size = Size(size.width, (bottom - top).coerceAtLeast(defaultLineHeightPx))
                                    )
                                }
                            },
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (controller.textFieldValue.text.isEmpty()) {
                                    Text(
                                        text = "Start typing ${controller.language.displayName} commands...",
                                        style = textStyle.copy(color = palette.gutterText)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }

        // 5. Status & Diagnostics Footer Bar
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        Surface(
            color = palette.gutterBackground,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = controller.diagnostics.isNotEmpty()) {
                    showDiagnosticsDrawer = !showDiagnosticsDrawer
                }
                .testTag("editor_status_footer")
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Ln ${activeLineIndex + 1} / ${lines.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${controller.textFieldValue.text.length} chars",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Undo: ${controller.undoCount} | Redo: ${controller.redoCount}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (errorCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Syntax Errors",
                                    tint = palette.errorUnderline,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$errorCount",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.errorUnderline,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (warningCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = "Syntax Warnings",
                                    tint = palette.warningUnderline,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$warningCount",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.warningUnderline,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (errorCount == 0 && warningCount == 0) {
                            Text(
                                text = "Syntax Valid",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.branchLabel,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showDiagnosticsDrawer && controller.diagnostics.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.editorBackground)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        controller.diagnostics.forEach { diag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { controller.jumpToLine(diag.lineIndex) }
                                    .padding(vertical = 3.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Line ${diag.lineNumber}: ",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = when (diag.severity) {
                                        DiagnosticSeverity.ERROR -> palette.errorUnderline
                                        DiagnosticSeverity.WARNING -> palette.warningUnderline
                                        DiagnosticSeverity.INFO -> palette.command
                                    }
                                )
                                Text(
                                    text = diag.message,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindReplacePanel(
    controller: EditorStateController,
    matchCount: Int,
    onClose: () -> Unit
) {
    val palette = LocalEditorSyntaxPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.gutterBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("editor_find_replace_panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = controller.findQuery,
                onValueChange = { controller.updateFindQuery(it) },
                placeholder = { Text("Find...", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier
                    .weight(1f)
                    .testTag("editor_find_input")
            )
            Text(
                text = if (matchCount > 0) "${controller.activeMatchIndex + 1}/$matchCount" else "0/0",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFontFamily
            )
            IconButton(
                onClick = { controller.findPrevious() },
                enabled = matchCount > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
            }
            IconButton(
                onClick = { controller.findNext() },
                enabled = matchCount > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close Find Bar")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = controller.replaceQuery,
                onValueChange = { controller.replaceQuery = it },
                placeholder = { Text("Replace with...", style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                modifier = Modifier
                    .weight(1f)
                    .testTag("editor_replace_input")
            )
            TextButton(
                onClick = { controller.replaceCurrentMatch() },
                enabled = matchCount > 0,
                modifier = Modifier.testTag("editor_replace_one_button")
            ) {
                Text("Replace")
            }
            TextButton(
                onClick = { controller.replaceAllMatches() },
                enabled = matchCount > 0,
                modifier = Modifier.testTag("editor_replace_all_button")
            ) {
                Text("All")
            }
        }
    }
}

private fun getLineCharOffsets(text: String, targetLineIndex: Int): Pair<Int, Int>? {
    if (targetLineIndex < 0) return null
    var currentLine = 0
    var lineStart = 0
    for (i in text.indices) {
        if (text[i] == '\n') {
            if (currentLine == targetLineIndex) {
                return lineStart to i
            }
            currentLine++
            lineStart = i + 1
        }
    }
    return if (currentLine == targetLineIndex) {
        lineStart to text.length
    } else {
        null
    }
}
