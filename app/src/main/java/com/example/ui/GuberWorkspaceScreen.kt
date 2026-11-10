package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.editor.EditorLanguage
import com.example.core.editor.EditorStateController
import com.example.core.editor.GuberTextEditor
import com.example.core.graph.CharacterProfile
import com.example.core.graph.DialogueGraph
import com.example.core.graph.FlowGraph
import com.example.core.graph.LayoutOrientation
import com.example.core.graph.SceneGraph
import com.example.core.renderer.InteractiveVisualWorkspace
import com.example.data.CharacterEntity

@Composable
fun GuberSplitWorkspace(
    language: EditorLanguage,
    controller: EditorStateController,
    flowGraph: FlowGraph,
    dialogueGraph: DialogueGraph,
    sceneGraph: SceneGraph,
    characters: List<CharacterEntity>,
    characterProfiles: List<CharacterProfile>,
    layoutOrientation: LayoutOrientation,
    workspaceViewMode: WorkspaceViewMode,
    initialSplitFraction: Float,
    onSplitFractionChanged: (Float) -> Unit,
    onWorkspaceViewModeChanged: (WorkspaceViewMode) -> Unit,
    onToggleOrientation: () -> Unit,
    onNodeDragged: (String, Float, Float) -> Unit,
    onResetLayout: () -> Unit,
    onMoveSceneOrder: (Int, Int) -> Unit,
    onManualGenerate: () -> Unit,
    onOpenCharacterSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember(initialSplitFraction) {
        mutableFloatStateOf(initialSplitFraction.coerceIn(0.25f, 0.75f))
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Contextual Top Bar: Workspace Mode Switcher + Character Quick-Insert Ribbon (for Dialogue/Scene)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // View Mode Toggle (Split | Visual | Editor)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = workspaceViewMode == WorkspaceViewMode.SPLIT,
                        onClick = { onWorkspaceViewModeChanged(WorkspaceViewMode.SPLIT) },
                        label = { Text("Split View", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.VerticalSplit,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("mode_split_chip")
                    )
                    FilterChip(
                        selected = workspaceViewMode == WorkspaceViewMode.VIEWER_ONLY,
                        onClick = { onWorkspaceViewModeChanged(WorkspaceViewMode.VIEWER_ONLY) },
                        label = { Text("Visual", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("mode_visual_chip")
                    )
                    FilterChip(
                        selected = workspaceViewMode == WorkspaceViewMode.EDITOR_ONLY,
                        onClick = { onWorkspaceViewModeChanged(WorkspaceViewMode.EDITOR_ONLY) },
                        label = { Text("Editor", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("mode_editor_chip")
                    )
                }

                // Character Quick Bar when in Dialogue or Scene Writer
                if (language == EditorLanguage.DIALOGUE || language == EditorLanguage.SCENE) {
                    LazyRow(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(characters, key = { it.id }) { char ->
                            val cColor = Color(char.colorHex)
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(cColor.copy(alpha = 0.16f))
                                    .border(1.dp, cColor.copy(alpha = 0.5f), CircleShape)
                                    .clickable {
                                        if (language == EditorLanguage.DIALOGUE) {
                                            controller.insertSnippet("\nCHARACTER: ${char.name}\nDIALOGUE: ")
                                        } else {
                                            controller.insertSnippet("  ${char.name}: ")
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(cColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char.avatarInitials.take(1),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = char.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { onOpenCharacterSheet() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("quick_add_character_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = "Manage Characters",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Cast",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        // Main Responsive Split Area
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWideScreen = maxWidth >= 680.dp
            val density = LocalDensity.current
            val totalWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
            val totalHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

            when (workspaceViewMode) {
                WorkspaceViewMode.VIEWER_ONLY -> {
                    InteractiveVisualWorkspace(
                        language = language,
                        flowGraph = flowGraph,
                        dialogueGraph = dialogueGraph,
                        sceneGraph = sceneGraph,
                        characters = characterProfiles,
                        layoutOrientation = layoutOrientation,
                        onToggleOrientation = onToggleOrientation,
                        onNodeDragged = onNodeDragged,
                        onResetLayout = onResetLayout,
                        onJumpToSourceLine = { line ->
                            controller.jumpToLine(line)
                        },
                        onMoveSceneOrder = onMoveSceneOrder,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                WorkspaceViewMode.EDITOR_ONLY -> {
                    GuberTextEditor(
                        controller = controller,
                        onManualGenerate = onManualGenerate,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                WorkspaceViewMode.SPLIT -> {
                    if (isWideScreen) {
                        // Tablet / Landscape: Persistent Left (Visual) + Draggable Divider + Right (Text Editor)
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(splitFraction)
                            ) {
                                InteractiveVisualWorkspace(
                                    language = language,
                                    flowGraph = flowGraph,
                                    dialogueGraph = dialogueGraph,
                                    sceneGraph = sceneGraph,
                                    characters = characterProfiles,
                                    layoutOrientation = layoutOrientation,
                                    onToggleOrientation = onToggleOrientation,
                                    onNodeDragged = onNodeDragged,
                                    onResetLayout = onResetLayout,
                                    onJumpToSourceLine = { line -> controller.jumpToLine(line) },
                                    onMoveSceneOrder = onMoveSceneOrder,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Draggable Split Handle
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .pointerInput(totalWidthPx) {
                                        detectDragGestures(
                                            onDragEnd = { onSplitFractionChanged(splitFraction) }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            val delta = dragAmount.x / totalWidthPx
                                            splitFraction = (splitFraction + delta).coerceIn(0.25f, 0.75f)
                                        }
                                    }
                                    .testTag("split_screen_divider"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f - splitFraction)
                            ) {
                                GuberTextEditor(
                                    controller = controller,
                                    onManualGenerate = onManualGenerate,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        // Mobile Portrait Split: Top Visual Canvas + Draggable Divider + Bottom Text Editor
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(splitFraction.coerceIn(0.28f, 0.68f))
                            ) {
                                InteractiveVisualWorkspace(
                                    language = language,
                                    flowGraph = flowGraph,
                                    dialogueGraph = dialogueGraph,
                                    sceneGraph = sceneGraph,
                                    characters = characterProfiles,
                                    layoutOrientation = layoutOrientation,
                                    onToggleOrientation = onToggleOrientation,
                                    onNodeDragged = onNodeDragged,
                                    onResetLayout = onResetLayout,
                                    onJumpToSourceLine = { line -> controller.jumpToLine(line) },
                                    onMoveSceneOrder = onMoveSceneOrder,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Horizontal Draggable Divider
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .pointerInput(totalHeightPx) {
                                        detectDragGestures(
                                            onDragEnd = { onSplitFractionChanged(splitFraction) }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            val delta = dragAmount.y / totalHeightPx
                                            splitFraction = (splitFraction + delta).coerceIn(0.28f, 0.68f)
                                        }
                                    }
                                    .testTag("split_screen_divider_vertical"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Resize Split Panels",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f - splitFraction.coerceIn(0.28f, 0.68f))
                            ) {
                                GuberTextEditor(
                                    controller = controller,
                                    onManualGenerate = onManualGenerate,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
