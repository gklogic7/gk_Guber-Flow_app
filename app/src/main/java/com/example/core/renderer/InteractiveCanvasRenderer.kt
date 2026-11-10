package com.example.core.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.editor.EditorLanguage
import com.example.core.graph.CharacterProfile
import com.example.core.graph.DialogueGraph
import com.example.core.graph.DialogueNode
import com.example.core.graph.DialogueNodeType
import com.example.core.graph.FlowGraph
import com.example.core.graph.FlowNode
import com.example.core.graph.FlowNodeType
import com.example.core.graph.LayoutOrientation
import com.example.core.graph.SceneCardModel
import com.example.core.graph.SceneGraph
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NodeDecisionColor
import com.example.ui.theme.NodeInputColor
import com.example.ui.theme.NodeNoteColor
import com.example.ui.theme.NodeProcessColor
import com.example.ui.theme.NodeTerminatorColor
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun InteractiveVisualWorkspace(
    language: EditorLanguage,
    flowGraph: FlowGraph,
    dialogueGraph: DialogueGraph,
    sceneGraph: SceneGraph,
    characters: List<CharacterProfile>,
    layoutOrientation: LayoutOrientation,
    onToggleOrientation: () -> Unit,
    onNodeDragged: (String, Float, Float) -> Unit,
    onResetLayout: () -> Unit,
    onJumpToSourceLine: (Int) -> Unit,
    onMoveSceneOrder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(0.88f) }
    var panOffset by remember { mutableStateOf(Offset(20f, 20f)) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    var showSceneTimelineMode by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val canvasBg = if (isDark) Color(0xFF090D12) else Color(0xFFF3F6FA)
    val gridDotColor = if (isDark) Color(0xFF212936) else Color(0xFFCBD5E1)
    val cardSurface = if (isDark) Color(0xFF151C26) else Color(0xFFFFFFFF)
    val cardText = if (isDark) Color(0xFFE6EDF3) else Color(0xFF0F172A)
    val subText = if (isDark) Color(0xFF8B949E) else Color(0xFF64748B)
    val edgeColor = if (isDark) Color(0xFF58A6FF) else Color(0xFF0284C7)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasBg)
            .testTag("interactive_visual_workspace")
    ) {
        if (language == EditorLanguage.SCENE && showSceneTimelineMode) {
            SceneTimelineListView(
                sceneGraph = sceneGraph,
                characters = characters,
                onSelectScene = { scene ->
                    selectedNodeId = scene.id
                    onJumpToSourceLine(scene.sourceLine)
                },
                onMoveSceneOrder = onMoveSceneOrder,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("visual_graph_canvas")
                    .pointerInput(language, flowGraph, dialogueGraph, sceneGraph, zoom, panOffset) {
                        detectTapGestures { tapPoint ->
                            val worldX = (tapPoint.x - panOffset.x) / zoom
                            val worldY = (tapPoint.y - panOffset.y) / zoom
                            val hit = findNodeAt(
                                language = language,
                                flowGraph = flowGraph,
                                dialogueGraph = dialogueGraph,
                                sceneGraph = sceneGraph,
                                worldX = worldX,
                                worldY = worldY
                            )
                            selectedNodeId = hit?.first
                            if (hit != null) {
                                onJumpToSourceLine(hit.second)
                            }
                        }
                    }
                    .pointerInput(language, flowGraph, dialogueGraph, sceneGraph, zoom, panOffset) {
                        detectDragGestures(
                            onDragStart = { startPoint ->
                                val worldX = (startPoint.x - panOffset.x) / zoom
                                val worldY = (startPoint.y - panOffset.y) / zoom
                                val hit = findNodeAt(
                                    language = language,
                                    flowGraph = flowGraph,
                                    dialogueGraph = dialogueGraph,
                                    sceneGraph = sceneGraph,
                                    worldX = worldX,
                                    worldY = worldY
                                )
                                draggedNodeId = hit?.first
                                if (hit != null) {
                                    selectedNodeId = hit.first
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val targetId = draggedNodeId
                                if (targetId != null) {
                                    val currentPos = getNodePosition(
                                        targetId,
                                        language,
                                        flowGraph,
                                        dialogueGraph,
                                        sceneGraph
                                    )
                                    if (currentPos != null) {
                                        onNodeDragged(
                                            targetId,
                                            currentPos.first + dragAmount.x / zoom,
                                            currentPos.second + dragAmount.y / zoom
                                        )
                                    }
                                } else {
                                    panOffset += dragAmount
                                }
                            },
                            onDragEnd = { draggedNodeId = null },
                            onDragCancel = { draggedNodeId = null }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            if (draggedNodeId == null) {
                                zoom = (zoom * gestureZoom).coerceIn(0.35f, 2.25f)
                                panOffset += pan
                            }
                        }
                    }
            ) {
                // Draw subtle studio dot grid
                drawDotGrid(gridDotColor, panOffset, zoom)

                withTransform({
                    translate(left = panOffset.x, top = panOffset.y)
                    scale(scaleX = zoom, scaleY = zoom, pivot = Offset.Zero)
                }) {
                    when (language) {
                        EditorLanguage.FLOWCHART -> {
                            drawFlowchartGraph(
                                graph = flowGraph,
                                selectedId = selectedNodeId,
                                textMeasurer = textMeasurer,
                                surfaceColor = cardSurface,
                                textColor = cardText,
                                edgeColor = edgeColor
                            )
                        }
                        EditorLanguage.DIALOGUE -> {
                            drawDialogueGraph(
                                graph = dialogueGraph,
                                characters = characters,
                                selectedId = selectedNodeId,
                                textMeasurer = textMeasurer,
                                surfaceColor = cardSurface,
                                textColor = cardText,
                                subTextColor = subText,
                                edgeColor = edgeColor
                            )
                        }
                        EditorLanguage.SCENE, EditorLanguage.PLAIN -> {
                            drawSceneRelationshipGraph(
                                graph = sceneGraph,
                                selectedId = selectedNodeId,
                                textMeasurer = textMeasurer,
                                surfaceColor = cardSurface,
                                textColor = cardText,
                                subTextColor = subText,
                                edgeColor = edgeColor
                            )
                        }
                    }
                }
            }
        }

        // Top-Left Module Summary Pill
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                val summaryText = when (language) {
                    EditorLanguage.FLOWCHART -> "${flowGraph.nodes.size} nodes • ${flowGraph.edges.size} connectors"
                    EditorLanguage.DIALOGUE -> "${dialogueGraph.sceneTitle} • ${dialogueGraph.nodes.size} blocks"
                    EditorLanguage.SCENE, EditorLanguage.PLAIN -> "${sceneGraph.scenes.size} scenes • ${sceneGraph.edges.size} links"
                }
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Top-Right Floating Canvas Controls (Zoom, Fit, Orientation / Timeline Toggle, Reset)
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (language == EditorLanguage.FLOWCHART) {
                    IconButton(
                        onClick = onToggleOrientation,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("canvas_orientation_button")
                    ) {
                        Icon(
                            imageVector = if (layoutOrientation == LayoutOrientation.VERTICAL) {
                                Icons.Default.SwapVert
                            } else {
                                Icons.Default.SwapHoriz
                            },
                            contentDescription = "Toggle Horizontal or Vertical Flowchart",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (language == EditorLanguage.SCENE) {
                    IconButton(
                        onClick = { showSceneTimelineMode = !showSceneTimelineMode },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("scene_timeline_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewTimeline,
                            contentDescription = "Toggle Scene Timeline View",
                            tint = if (showSceneTimelineMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { zoom = (zoom * 1.18f).coerceAtMost(2.25f) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("canvas_zoom_in_button")
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                }

                Text(
                    text = "${(zoom * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                IconButton(
                    onClick = { zoom = (zoom / 1.18f).coerceAtLeast(0.35f) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("canvas_zoom_out_button")
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = {
                        zoom = 0.85f
                        panOffset = Offset(24f, 24f)
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("canvas_fit_button")
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = "Fit to Screen",
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        onResetLayout()
                        zoom = 0.85f
                        panOffset = Offset(24f, 24f)
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("canvas_reset_layout_button")
                ) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = "Auto-Layout Reset",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom Node Inspector Bar when a node is selected
        val selectedDetails = remember(selectedNodeId, language, flowGraph, dialogueGraph, sceneGraph) {
            resolveSelectedNodeSummary(selectedNodeId, language, flowGraph, dialogueGraph, sceneGraph)
        }
        if (selectedDetails != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .testTag("canvas_node_inspector")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedDetails.first,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = selectedDetails.second,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { onJumpToSourceLine(selectedDetails.third) }) {
                        Icon(Icons.Default.EditLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Line ${selectedDetails.third + 1}")
                    }
                    IconButton(onClick = { selectedNodeId = null }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Inspector", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneTimelineListView(
    sceneGraph: SceneGraph,
    characters: List<CharacterProfile>,
    onSelectScene: (SceneCardModel) -> Unit,
    onMoveSceneOrder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .padding(top = 56.dp, start = 14.dp, end = 14.dp, bottom = 16.dp)
            .testTag("scene_timeline_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(sceneGraph.scenes, key = { _, item -> item.id }) { idx, scene ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable { onSelectScene(scene) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "SCENE ${scene.sceneNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = scene.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalIconButton(
                                onClick = { if (idx > 0) onMoveSceneOrder(idx, idx - 1) },
                                enabled = idx > 0,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("↑", fontWeight = FontWeight.Bold)
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    if (idx < sceneGraph.scenes.lastIndex) onMoveSceneOrder(idx, idx + 1)
                                },
                                enabled = idx < sceneGraph.scenes.lastIndex,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("↓", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${scene.settingIntExt} ${scene.location} • ${scene.timeOfDay} • ${scene.weather}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    if (scene.characters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            scene.characters.forEach { charName ->
                                val profile = characters.firstOrNull { it.name.equals(charName, ignoreCase = true) }
                                val chipColor = profile?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.primary
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(chipColor.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = charName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = chipColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    if (scene.actions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = scene.actions.joinToString(" "),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (scene.dialogues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val firstDlg = scene.dialogues.first()
                        Text(
                            text = "💬 ${firstDlg.speaker}: \"${firstDlg.text}\" (+${(scene.dialogues.size - 1).coerceAtLeast(0)} more)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🎥 ${scene.cameraDirections.firstOrNull() ?: "Standard framing"} • 🔊 ${scene.audioNotes.firstOrNull() ?: "Ambient"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "➔ ${scene.transition}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawDotGrid(dotColor: Color, pan: Offset, zoom: Float) {
    val step = (36f * zoom).coerceAtLeast(18f)
    val startX = (pan.x % step).let { if (it < 0) it + step else it }
    val startY = (pan.y % step).let { if (it < 0) it + step else it }

    var x = startX
    while (x < size.width) {
        var y = startY
        while (y < size.height) {
            drawCircle(
                color = dotColor,
                radius = 1.6f,
                center = Offset(x, y)
            )
            y += step
        }
        x += step
    }
}

private fun DrawScope.drawFlowchartGraph(
    graph: FlowGraph,
    selectedId: String?,
    textMeasurer: TextMeasurer,
    surfaceColor: Color,
    textColor: Color,
    edgeColor: Color
) {
    val nodeMap = graph.nodes.associateBy { it.id }

    // 1. Draw connectors & directional arrows first
    graph.edges.forEach { edge ->
        val from = nodeMap[edge.fromId] ?: return@forEach
        val to = nodeMap[edge.toId] ?: return@forEach

        val startCenter = Offset(from.x + from.width / 2f, from.y + from.height / 2f)
        val endCenter = Offset(to.x + to.width / 2f, to.y + to.height / 2f)
        val (startPt, endPt) = computeAnchorPoints(from, to, startCenter, endCenter)

        val strokeColor = when {
            edge.label.equals("YES", ignoreCase = true) || edge.label.equals("TRUE", ignoreCase = true) -> NodeTerminatorColor
            edge.label.equals("NO", ignoreCase = true) || edge.label.equals("FALSE", ignoreCase = true) -> Color(0xFFF85149)
            edge.isBackEdge -> NodeDecisionColor
            else -> edgeColor
        }

        val path = Path().apply {
            moveTo(startPt.x, startPt.y)
            if (edge.isBackEdge) {
                val loopOutX = maxOf(from.x + from.width, to.x + to.width) + 75f
                cubicTo(
                    loopOutX, startPt.y,
                    loopOutX, endPt.y,
                    endPt.x, endPt.y
                )
            } else {
                val midY = (startPt.y + endPt.y) / 2f
                cubicTo(
                    startPt.x, midY,
                    endPt.x, midY,
                    endPt.x, endPt.y
                )
            }
        }

        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(
                width = 2.6f,
                pathEffect = if (edge.isBackEdge || edge.label == "note") {
                    PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                } else {
                    null
                }
            )
        )

        drawArrowHead(
            tip = endPt,
            from = Offset((startPt.x + endPt.x) / 2f, startPt.y),
            color = strokeColor
        )

        if (edge.label.isNotEmpty() && edge.label != "note") {
            val midPt = Offset((startPt.x + endPt.x) / 2f, (startPt.y + endPt.y) / 2f - 10f)
            val badgeLayout = textMeasurer.measure(
                text = edge.label,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            val padH = 6f
            val padV = 3f
            drawRoundRect(
                color = strokeColor,
                topLeft = Offset(midPt.x - badgeLayout.size.width / 2f - padH, midPt.y - badgeLayout.size.height / 2f - padV),
                size = Size(badgeLayout.size.width + padH * 2, badgeLayout.size.height + padV * 2),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawText(
                textLayoutResult = badgeLayout,
                topLeft = Offset(midPt.x - badgeLayout.size.width / 2f, midPt.y - badgeLayout.size.height / 2f)
            )
        }
    }

    // 2. Draw Flowchart Nodes by Shape
    graph.nodes.forEach { node ->
        val isSelected = node.id == selectedId
        val accent = when (node.type) {
            FlowNodeType.TERMINATOR -> NodeTerminatorColor
            FlowNodeType.PROCESS -> NodeProcessColor
            FlowNodeType.DECISION -> NodeDecisionColor
            FlowNodeType.INPUT_OUTPUT -> NodeInputColor
            FlowNodeType.ANNOTATION -> NodeNoteColor
        }
        val borderStroke = if (isSelected) 3.5f else 2f

        when (node.type) {
            FlowNodeType.TERMINATOR -> {
                drawRoundRect(
                    color = surfaceColor,
                    topLeft = Offset(node.x, node.y),
                    size = Size(node.width, node.height),
                    cornerRadius = CornerRadius(node.height / 2f, node.height / 2f),
                    style = Fill
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(node.x, node.y),
                    size = Size(node.width, node.height),
                    cornerRadius = CornerRadius(node.height / 2f, node.height / 2f),
                    style = Stroke(width = borderStroke)
                )
            }
            FlowNodeType.PROCESS -> {
                drawRoundRect(
                    color = surfaceColor,
                    topLeft = Offset(node.x, node.y),
                    size = Size(node.width, node.height),
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Fill
                )
                // Left accent bar
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(node.x, node.y),
                    size = Size(6f, node.height),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                drawRoundRect(
                    color = if (isSelected) accent else accent.copy(alpha = 0.65f),
                    topLeft = Offset(node.x, node.y),
                    size = Size(node.width, node.height),
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Stroke(width = borderStroke)
                )
            }
            FlowNodeType.DECISION -> {
                val cx = node.x + node.width / 2f
                val cy = node.y + node.height / 2f
                val diamond = Path().apply {
                    moveTo(cx, node.y)
                    lineTo(node.x + node.width, cy)
                    lineTo(cx, node.y + node.height)
                    lineTo(node.x, cy)
                    close()
                }
                drawPath(path = diamond, color = surfaceColor, style = Fill)
                drawPath(path = diamond, color = accent, style = Stroke(width = borderStroke))
            }
            FlowNodeType.INPUT_OUTPUT -> {
                val slant = 20f
                val para = Path().apply {
                    moveTo(node.x + slant, node.y)
                    lineTo(node.x + node.width, node.y)
                    lineTo(node.x + node.width - slant, node.y + node.height)
                    lineTo(node.x, node.y + node.height)
                    close()
                }
                drawPath(path = para, color = surfaceColor, style = Fill)
                drawPath(path = para, color = accent, style = Stroke(width = borderStroke))
            }
            FlowNodeType.ANNOTATION -> {
                val fold = 16f
                val notePath = Path().apply {
                    moveTo(node.x, node.y)
                    lineTo(node.x + node.width - fold, node.y)
                    lineTo(node.x + node.width, node.y + fold)
                    lineTo(node.x + node.width, node.y + node.height)
                    lineTo(node.x, node.y + node.height)
                    close()
                }
                drawPath(path = notePath, color = surfaceColor, style = Fill)
                drawPath(
                    path = notePath,
                    color = accent,
                    style = Stroke(width = borderStroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)))
                )
            }
        }

        // Node Label Text
        val labelMeasured = textMeasurer.measure(
            text = node.label,
            style = TextStyle(
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 13.sp,
                fontWeight = if (node.type == FlowNodeType.TERMINATOR || node.type == FlowNodeType.DECISION) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                },
                color = textColor
            ),
            constraints = Constraints(maxWidth = (node.width - 34f).toInt().coerceAtLeast(40)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        drawText(
            textLayoutResult = labelMeasured,
            topLeft = Offset(
                x = node.x + (node.width - labelMeasured.size.width) / 2f,
                y = node.y + (node.height - labelMeasured.size.height) / 2f
            )
        )
    }
}

private fun DrawScope.drawDialogueGraph(
    graph: DialogueGraph,
    characters: List<CharacterProfile>,
    selectedId: String?,
    textMeasurer: TextMeasurer,
    surfaceColor: Color,
    textColor: Color,
    subTextColor: Color,
    edgeColor: Color
) {
    val nodeMap = graph.nodes.associateBy { it.id }

    graph.edges.forEach { edge ->
        val from = nodeMap[edge.fromId] ?: return@forEach
        val to = nodeMap[edge.toId] ?: return@forEach
        val start = Offset(from.x + from.width / 2f, from.y + from.height)
        val end = Offset(to.x + to.width / 2f, to.y)
        val path = Path().apply {
            moveTo(start.x, start.y)
            val midY = (start.y + end.y) / 2f
            cubicTo(start.x, midY, end.x, midY, end.x, end.y)
        }
        val color = if (edge.label.isNotEmpty()) NodeDecisionColor else edgeColor
        drawPath(path = path, color = color, style = Stroke(width = 2.4f))
        drawArrowHead(tip = end, from = Offset(end.x, end.y - 16f), color = color)

        if (edge.label.isNotEmpty()) {
            val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
            val lbl = textMeasurer.measure(
                text = edge.label,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(mid.x - lbl.size.width / 2f - 6f, mid.y - lbl.size.height / 2f - 3f),
                size = Size(lbl.size.width + 12f, lbl.size.height + 6f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawText(lbl, topLeft = Offset(mid.x - lbl.size.width / 2f, mid.y - lbl.size.height / 2f))
        }
    }

    graph.nodes.forEach { node ->
        val isSelected = node.id == selectedId
        val charProfile = characters.firstOrNull { it.name.equals(node.speaker, ignoreCase = true) }
        val accent = when (node.type) {
            DialogueNodeType.CHOICE_HUB -> NodeDecisionColor
            DialogueNodeType.OPTION_BRANCH -> NodeTerminatorColor
            DialogueNodeType.NARRATION -> NodeNoteColor
            DialogueNodeType.END_CHOICE -> NodeInputColor
            else -> charProfile?.let { Color(it.colorHex) } ?: NodeProcessColor
        }

        drawRoundRect(
            color = surfaceColor,
            topLeft = Offset(node.x, node.y),
            size = Size(node.width, node.height),
            cornerRadius = CornerRadius(14f, 14f)
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.2f),
            topLeft = Offset(node.x, node.y),
            size = Size(node.width, 28f),
            cornerRadius = CornerRadius(14f, 14f)
        )
        drawRoundRect(
            color = if (isSelected) accent else accent.copy(alpha = 0.65f),
            topLeft = Offset(node.x, node.y),
            size = Size(node.width, node.height),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(width = if (isSelected) 3.2f else 1.8f)
        )

        // Speaker header
        val headerLayout = textMeasurer.measure(
            text = when (node.type) {
                DialogueNodeType.CHOICE_HUB -> "⚡ CHOICE BRANCH"
                DialogueNodeType.OPTION_BRANCH -> "↳ ${node.text}"
                DialogueNodeType.NARRATION -> "📖 NARRATION"
                DialogueNodeType.THOUGHT -> "💭 ${node.speaker} (Thought)"
                DialogueNodeType.END_CHOICE -> "⊕ END_CHOICE MERGE"
                DialogueNodeType.SPEECH -> "🗣 ${node.speaker}"
            },
            style = TextStyle(
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            ),
            constraints = Constraints(maxWidth = (node.width - 20f).toInt().coerceAtLeast(40)),
            maxLines = 1
        )
        drawText(headerLayout, topLeft = Offset(node.x + 10f, node.y + 6f))

        // Body text or choices list
        val bodyContent = if (node.type == DialogueNodeType.CHOICE_HUB && node.choices.isNotEmpty()) {
            node.choices.mapIndexed { i, c -> "${i + 1}. $c" }.joinToString("\n")
        } else {
            node.text
        }
        val bodyLayout = textMeasurer.measure(
            text = bodyContent,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                fontStyle = if (node.type == DialogueNodeType.NARRATION || node.type == DialogueNodeType.THOUGHT) {
                    FontStyle.Italic
                } else {
                    FontStyle.Normal
                },
                color = if (node.type == DialogueNodeType.NARRATION) subTextColor else textColor
            ),
            constraints = Constraints(maxWidth = (node.width - 20f).toInt().coerceAtLeast(40)),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        drawText(bodyLayout, topLeft = Offset(node.x + 10f, node.y + 34f))
    }
}

private fun DrawScope.drawSceneRelationshipGraph(
    graph: SceneGraph,
    selectedId: String?,
    textMeasurer: TextMeasurer,
    surfaceColor: Color,
    textColor: Color,
    subTextColor: Color,
    edgeColor: Color
) {
    val map = graph.scenes.associateBy { it.id }

    graph.edges.forEach { edge ->
        val from = map[edge.fromSceneId] ?: return@forEach
        val to = map[edge.toSceneId] ?: return@forEach
        val start = Offset(from.x + from.width / 2f, from.y + from.height / 2f)
        val end = Offset(to.x + to.width / 2f, to.y + to.height / 2f)
        val path = Path().apply {
            moveTo(start.x, start.y)
            val midX = (start.x + end.x) / 2f
            cubicTo(midX, start.y, midX, end.y, end.x, end.y)
        }
        drawPath(path = path, color = edgeColor, style = Stroke(width = 2.6f))
    }

    graph.scenes.forEach { scene ->
        val isSelected = scene.id == selectedId
        val accent = NodeProcessColor

        drawRoundRect(
            color = surfaceColor,
            topLeft = Offset(scene.x, scene.y),
            size = Size(scene.width, scene.height),
            cornerRadius = CornerRadius(14f, 14f)
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.22f),
            topLeft = Offset(scene.x, scene.y),
            size = Size(scene.width, 34f),
            cornerRadius = CornerRadius(14f, 14f)
        )
        drawRoundRect(
            color = if (isSelected) accent else accent.copy(alpha = 0.65f),
            topLeft = Offset(scene.x, scene.y),
            size = Size(scene.width, scene.height),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(width = if (isSelected) 3.4f else 1.8f)
        )

        val titleLayout = textMeasurer.measure(
            text = "SCENE ${scene.sceneNumber} • ${scene.title}",
            style = TextStyle(
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            ),
            constraints = Constraints(maxWidth = (scene.width - 20f).toInt()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        drawText(titleLayout, topLeft = Offset(scene.x + 10f, scene.y + 8f))

        val metaLayout = textMeasurer.measure(
            text = "📍 ${scene.location} (${scene.timeOfDay} / ${scene.weather})",
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                color = NodeDecisionColor,
                fontWeight = FontWeight.Medium
            ),
            constraints = Constraints(maxWidth = (scene.width - 20f).toInt()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        drawText(metaLayout, topLeft = Offset(scene.x + 10f, scene.y + 42f))

        val castText = if (scene.characters.isEmpty()) "Cast: None listed" else "Cast: ${scene.characters.joinToString(", ")}"
        val castLayout = textMeasurer.measure(
            text = castText,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                color = NodeTerminatorColor
            ),
            constraints = Constraints(maxWidth = (scene.width - 20f).toInt()),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        drawText(castLayout, topLeft = Offset(scene.x + 10f, scene.y + 62f))

        val actionSummary = scene.actions.firstOrNull()
            ?: scene.dialogues.firstOrNull()?.let { "${it.speaker}: ${it.text}" }
            ?: scene.description.ifEmpty { "Write ACTION: or DIALOGUE: lines..." }

        val bodyLayout = textMeasurer.measure(
            text = actionSummary,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                color = subTextColor
            ),
            constraints = Constraints(maxWidth = (scene.width - 20f).toInt()),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        drawText(bodyLayout, topLeft = Offset(scene.x + 10f, scene.y + 86f))

        val footerLayout = textMeasurer.measure(
            text = "🎥 ${scene.cameraDirections.size} shots • 💬 ${scene.dialogues.size} lines • ➔ ${scene.transition}",
            style = TextStyle(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp,
                color = accent
            ),
            constraints = Constraints(maxWidth = (scene.width - 20f).toInt()),
            maxLines = 1
        )
        drawText(footerLayout, topLeft = Offset(scene.x + 10f, scene.y + scene.height - 24f))
    }
}

private fun computeAnchorPoints(
    from: FlowNode,
    to: FlowNode,
    startCenter: Offset,
    endCenter: Offset
): Pair<Offset, Offset> {
    val dx = endCenter.x - startCenter.x
    val dy = endCenter.y - startCenter.y
    return if (kotlin.math.abs(dy) >= kotlin.math.abs(dx) * 0.45f) {
        if (dy >= 0) {
            Offset(from.x + from.width / 2f, from.y + from.height) to Offset(to.x + to.width / 2f, to.y)
        } else {
            Offset(from.x + from.width / 2f, from.y) to Offset(to.x + to.width / 2f, to.y + to.height)
        }
    } else {
        if (dx >= 0) {
            Offset(from.x + from.width, from.y + from.height / 2f) to Offset(to.x, to.y + to.height / 2f)
        } else {
            Offset(from.x, from.y + from.height / 2f) to Offset(to.x + to.width, to.y + to.height / 2f)
        }
    }
}

private fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color) {
    val angle = atan2(tip.y - from.y, tip.x - from.x)
    val arrowLen = 11f
    val arrowSpread = 0.48f
    val p1 = Offset(
        tip.x - arrowLen * cos(angle - arrowSpread),
        tip.y - arrowLen * sin(angle - arrowSpread)
    )
    val p2 = Offset(
        tip.x - arrowLen * cos(angle + arrowSpread),
        tip.y - arrowLen * sin(angle + arrowSpread)
    )
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path = path, color = color, style = Fill)
}

private fun findNodeAt(
    language: EditorLanguage,
    flowGraph: FlowGraph,
    dialogueGraph: DialogueGraph,
    sceneGraph: SceneGraph,
    worldX: Float,
    worldY: Float
): Pair<String, Int>? {
    return when (language) {
        EditorLanguage.FLOWCHART -> {
            flowGraph.nodes.lastOrNull { n ->
                worldX in n.x..(n.x + n.width) && worldY in n.y..(n.y + n.height)
            }?.let { it.id to it.sourceLine }
        }
        EditorLanguage.DIALOGUE -> {
            dialogueGraph.nodes.lastOrNull { n ->
                worldX in n.x..(n.x + n.width) && worldY in n.y..(n.y + n.height)
            }?.let { it.id to it.sourceLine }
        }
        EditorLanguage.SCENE, EditorLanguage.PLAIN -> {
            sceneGraph.scenes.lastOrNull { s ->
                worldX in s.x..(s.x + s.width) && worldY in s.y..(s.y + s.height)
            }?.let { it.id to it.sourceLine }
        }
    }
}

private fun getNodePosition(
    nodeId: String,
    language: EditorLanguage,
    flowGraph: FlowGraph,
    dialogueGraph: DialogueGraph,
    sceneGraph: SceneGraph
): Pair<Float, Float>? {
    return when (language) {
        EditorLanguage.FLOWCHART -> flowGraph.nodes.firstOrNull { it.id == nodeId }?.let { it.x to it.y }
        EditorLanguage.DIALOGUE -> dialogueGraph.nodes.firstOrNull { it.id == nodeId }?.let { it.x to it.y }
        EditorLanguage.SCENE, EditorLanguage.PLAIN -> sceneGraph.scenes.firstOrNull { it.id == nodeId }?.let { it.x to it.y }
    }
}

private fun resolveSelectedNodeSummary(
    nodeId: String?,
    language: EditorLanguage,
    flowGraph: FlowGraph,
    dialogueGraph: DialogueGraph,
    sceneGraph: SceneGraph
): Triple<String, String, Int>? {
    if (nodeId == null) return null
    return when (language) {
        EditorLanguage.FLOWCHART -> {
            flowGraph.nodes.firstOrNull { it.id == nodeId }?.let {
                Triple(it.type.label, it.label, it.sourceLine)
            }
        }
        EditorLanguage.DIALOGUE -> {
            dialogueGraph.nodes.firstOrNull { it.id == nodeId }?.let {
                Triple(it.speaker.ifEmpty { it.type.name }, it.text, it.sourceLine)
            }
        }
        EditorLanguage.SCENE, EditorLanguage.PLAIN -> {
            sceneGraph.scenes.firstOrNull { it.id == nodeId }?.let {
                Triple(
                    "SCENE ${it.sceneNumber}: ${it.title}",
                    "${it.location} • ${it.characters.joinToString(", ")}",
                    it.sourceLine
                )
            }
        }
    }
}
