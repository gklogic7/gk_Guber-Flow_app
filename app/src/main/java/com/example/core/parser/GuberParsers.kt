package com.example.core.parser

import com.example.core.graph.DialogueEdge
import com.example.core.graph.DialogueGraph
import com.example.core.graph.DialogueNode
import com.example.core.graph.DialogueNodeType
import com.example.core.graph.FlowEdge
import com.example.core.graph.FlowGraph
import com.example.core.graph.FlowNode
import com.example.core.graph.FlowNodeType
import com.example.core.graph.LayoutOrientation
import com.example.core.graph.SceneCardModel
import com.example.core.graph.SceneDialogueLine
import com.example.core.graph.SceneEdge
import com.example.core.graph.SceneGraph

/**
 * Structural Graph Parser for Flowchart DSL.
 * Properly handles:
 * - Start / End terminators
 * - Decision diamonds (? or Decision:)
 * - Input:, Process:, Note: directives
 * - Branching (yes:, no:, case:) with automatic branch convergence onto the next common statement
 * - Explicit node references & loops (NodeA -> NodeB, goto: NodeName)
 */
object FlowchartParser {

    fun parse(
        script: String,
        orientation: LayoutOrientation = LayoutOrientation.VERTICAL,
        manualOffsets: Map<String, Pair<Float, Float>> = emptyMap()
    ): FlowGraph {
        val rawLines = script.split('\n')
        val nodesById = linkedMapOf<String, FlowNode>()
        val labelToId = mutableMapOf<String, String>()
        val edges = mutableListOf<FlowEdge>()

        var activeMainNodeId: String? = null
        var currentDecisionId: String? = null
        val pendingBranchEnds = mutableListOf<String>()

        fun getOrCreateNode(
            label: String,
            type: FlowNodeType,
            sourceLine: Int,
            forceId: String? = null
        ): FlowNode {
            val normalizedKey = label.trim().lowercase()
            val existingId = labelToId[normalizedKey]
            if (existingId != null && nodesById.containsKey(existingId) && forceId == null) {
                return nodesById[existingId]!!
            }
            val id = forceId ?: "flow_node_${nodesById.size + 1}"
            val nodeWidth = when (type) {
                FlowNodeType.DECISION -> 220f
                FlowNodeType.ANNOTATION -> 195f
                else -> 210f
            }
            val nodeHeight = when (type) {
                FlowNodeType.DECISION -> 92f
                FlowNodeType.TERMINATOR -> 60f
                else -> 72f
            }
            val node = FlowNode(
                id = id,
                label = label.trim(),
                type = type,
                sourceLine = sourceLine,
                width = nodeWidth,
                height = nodeHeight
            )
            nodesById[id] = node
            labelToId[normalizedKey] = id
            return node
        }

        fun addEdgeUnique(fromId: String, toId: String, label: String = "") {
            if (fromId == toId) return
            if (edges.none { it.fromId == fromId && it.toId == toId && it.label == label }) {
                val fromIdx = nodesById.keys.indexOf(fromId)
                val toIdx = nodesById.keys.indexOf(toId)
                val isLoop = fromIdx != -1 && toIdx != -1 && toIdx < fromIdx
                edges.add(FlowEdge(fromId = fromId, toId = toId, label = label, isBackEdge = isLoop))
            }
        }

        rawLines.forEachIndexed { lineIdx, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                return@forEachIndexed
            }

            val lower = trimmed.lowercase()

            // 1. Check explicit connector: A -> B
            if (trimmed.contains("->") && !lower.startsWith("yes:") && !lower.startsWith("no:") && !lower.startsWith("case ")) {
                val parts = trimmed.split("->")
                if (parts.size == 2) {
                    val leftRaw = parts[0].trim()
                    val rightRaw = parts[1].trim()
                    val edgeLabel = if (leftRaw.contains(":")) leftRaw.substringBefore(":").trim() else ""
                    val leftLabel = if (leftRaw.contains(":")) leftRaw.substringAfter(":").trim() else leftRaw
                    if (leftLabel.isNotEmpty() && rightRaw.isNotEmpty()) {
                        val srcNode = getOrCreateNode(leftLabel, inferNodeType(leftLabel), lineIdx)
                        val dstNode = getOrCreateNode(rightRaw, inferNodeType(rightRaw), lineIdx)
                        addEdgeUnique(srcNode.id, dstNode.id, edgeLabel)
                        activeMainNodeId = dstNode.id
                    }
                    return@forEachIndexed
                }
            }

            // 2. Check goto: TargetLabel
            if (lower.startsWith("goto:")) {
                val targetLabel = trimmed.substringAfter(":").trim()
                if (targetLabel.isNotEmpty()) {
                    val targetNode = getOrCreateNode(targetLabel, inferNodeType(targetLabel), lineIdx)
                    val fromId = pendingBranchEnds.lastOrNull() ?: activeMainNodeId
                    if (fromId != null) {
                        addEdgeUnique(fromId, targetNode.id, "loop")
                    }
                }
                return@forEachIndexed
            }

            // 3. Check Note: annotation
            if (lower.startsWith("note:")) {
                val noteText = trimmed.substringAfter(":").trim().ifEmpty { "Annotation" }
                val noteNode = getOrCreateNode(noteText, FlowNodeType.ANNOTATION, lineIdx, forceId = "note_${lineIdx}")
                val anchorId = currentDecisionId ?: activeMainNodeId
                if (anchorId != null) {
                    addEdgeUnique(anchorId, noteNode.id, "note")
                }
                return@forEachIndexed
            }

            // 4. Check Branch statement: yes:, no:, true:, false:, case X:, else:
            val isBranchLine = lower.startsWith("yes:") ||
                lower.startsWith("no:") ||
                lower.startsWith("true:") ||
                lower.startsWith("false:") ||
                lower.startsWith("else:") ||
                lower.startsWith("default:") ||
                lower.startsWith("case ")

            if (isBranchLine) {
                val branchPrefix = trimmed.substringBefore(":").trim().removePrefix("case ").trim()
                val branchBody = trimmed.substringAfter(":", "").trim()
                if (branchBody.isNotEmpty()) {
                    val decisionSourceId = currentDecisionId ?: activeMainNodeId
                    if (branchBody.contains("->")) {
                        val parts = branchBody.split("->")
                        val stepLabel = parts[0].trim()
                        val targetLabel = parts.getOrNull(1)?.trim().orEmpty()
                        val branchNode = getOrCreateNode(stepLabel, inferNodeType(stepLabel), lineIdx)
                        if (decisionSourceId != null) {
                            addEdgeUnique(decisionSourceId, branchNode.id, branchPrefix.uppercase())
                        }
                        if (targetLabel.isNotEmpty()) {
                            val targetNode = getOrCreateNode(targetLabel, inferNodeType(targetLabel), lineIdx)
                            addEdgeUnique(branchNode.id, targetNode.id)
                        } else {
                            pendingBranchEnds.add(branchNode.id)
                        }
                    } else {
                        val branchNode = getOrCreateNode(branchBody, inferNodeType(branchBody), lineIdx)
                        if (decisionSourceId != null) {
                            addEdgeUnique(decisionSourceId, branchNode.id, branchPrefix.uppercase())
                        }
                        if (branchNode.type == FlowNodeType.DECISION) {
                            currentDecisionId = branchNode.id
                        } else {
                            pendingBranchEnds.add(branchNode.id)
                        }
                    }
                }
                return@forEachIndexed
            }

            // 5. Standard Node (Start, End, Input:, Process:, Decision:, Question?, or normal step)
            val (cleanLabel, nodeType) = extractLabelAndType(trimmed)
            val node = getOrCreateNode(cleanLabel, nodeType, lineIdx)

            // Connect from pending branches if we are merging after a decision, or from activeMainNodeId
            if (pendingBranchEnds.isNotEmpty()) {
                pendingBranchEnds.forEach { branchEndId ->
                    addEdgeUnique(branchEndId, node.id)
                }
                pendingBranchEnds.clear()
                currentDecisionId = null
            } else if (currentDecisionId != null && currentDecisionId != node.id) {
                addEdgeUnique(currentDecisionId!!, node.id)
                currentDecisionId = null
            } else if (activeMainNodeId != null && activeMainNodeId != node.id) {
                addEdgeUnique(activeMainNodeId!!, node.id)
            }

            if (nodeType == FlowNodeType.DECISION) {
                currentDecisionId = node.id
                activeMainNodeId = node.id
            } else {
                activeMainNodeId = node.id
            }
        }

        // Layout calculation (Layered BFS / Topological Sugiyama-lite)
        val positionedNodes = layoutFlowNodes(
            nodes = nodesById.values.toList(),
            edges = edges,
            orientation = orientation,
            manualOffsets = manualOffsets
        )

        return FlowGraph(nodes = positionedNodes, edges = edges)
    }

    private fun inferNodeType(raw: String): FlowNodeType = extractLabelAndType(raw).second

    private fun extractLabelAndType(trimmed: String): Pair<String, FlowNodeType> {
        val lower = trimmed.lowercase()
        return when {
            lower == "start" || lower.startsWith("start:") -> {
                val lbl = if (trimmed.contains(":")) trimmed.substringAfter(":").trim().ifEmpty { "Start" } else "Start"
                lbl to FlowNodeType.TERMINATOR
            }
            lower == "end" || lower.startsWith("end:") -> {
                val lbl = if (trimmed.contains(":")) trimmed.substringAfter(":").trim().ifEmpty { "End" } else "End"
                lbl to FlowNodeType.TERMINATOR
            }
            lower.startsWith("input:") || lower.startsWith("output:") -> {
                trimmed.substringAfter(":").trim().ifEmpty { trimmed } to FlowNodeType.INPUT_OUTPUT
            }
            lower.startsWith("process:") -> {
                trimmed.substringAfter(":").trim().ifEmpty { trimmed } to FlowNodeType.PROCESS
            }
            lower.startsWith("decision:") -> {
                trimmed.substringAfter(":").trim().ifEmpty { trimmed } to FlowNodeType.DECISION
            }
            lower.startsWith("note:") -> {
                trimmed.substringAfter(":").trim().ifEmpty { trimmed } to FlowNodeType.ANNOTATION
            }
            trimmed.endsWith("?") -> {
                trimmed to FlowNodeType.DECISION
            }
            else -> {
                trimmed to FlowNodeType.PROCESS
            }
        }
    }

    private fun layoutFlowNodes(
        nodes: List<FlowNode>,
        edges: List<FlowEdge>,
        orientation: LayoutOrientation,
        manualOffsets: Map<String, Pair<Float, Float>>
    ): List<FlowNode> {
        if (nodes.isEmpty()) return emptyList()

        val rankMap = mutableMapOf<String, Int>()
        val outgoing = edges.filter { !it.isBackEdge }.groupBy { it.fromId }

        val firstId = nodes.first().id
        rankMap[firstId] = 0
        val queue = ArrayDeque<String>()
        queue.add(firstId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentRank = rankMap[current] ?: 0
            outgoing[current]?.forEach { edge ->
                val nextRank = currentRank + 1
                val existing = rankMap[edge.toId]
                if (existing == null || (nextRank > existing && nextRank < nodes.size + 2)) {
                    rankMap[edge.toId] = nextRank
                    queue.add(edge.toId)
                }
            }
        }

        // Assign any unvisited nodes sequential ranks
        var fallbackRank = (rankMap.values.maxOrNull() ?: 0) + 1
        nodes.forEach { node ->
            if (!rankMap.containsKey(node.id)) {
                rankMap[node.id] = fallbackRank++
            }
        }

        val layers = nodes.groupBy { rankMap[it.id] ?: 0 }.toSortedMap()
        val result = mutableListOf<FlowNode>()

        val layerGap = if (orientation == LayoutOrientation.VERTICAL) 145f else 290f
        val siblingGap = if (orientation == LayoutOrientation.VERTICAL) 265f else 135f

        layers.entries.forEachIndexed { layerIdx, (_, layerNodes) ->
            val count = layerNodes.size
            layerNodes.forEachIndexed { idxInLayer, node ->
                val centerOffset = (idxInLayer - (count - 1) / 2f) * siblingGap
                val baseX = if (orientation == LayoutOrientation.VERTICAL) {
                    420f + centerOffset
                } else {
                    120f + layerIdx * layerGap
                }
                val baseY = if (orientation == LayoutOrientation.VERTICAL) {
                    90f + layerIdx * layerGap
                } else {
                    320f + centerOffset
                }
                val manual = manualOffsets[node.id]
                result.add(
                    node.copy(
                        x = manual?.first ?: baseX,
                        y = manual?.second ?: baseY
                    )
                )
            }
        }
        return result
    }
}

/**
 * Parser for Guber Dialogue Writer DSL.
 * Supports:
 * - SCENE: header
 * - CHARACTER: + DIALOGUE: / THOUGHT:
 * - NARRATION: blocks
 * - CHOICE: with numbered options, OPTION: branches, GOTO: targets, and END_CHOICE convergence
 * - SET: and IF: variables/flags
 */
object DialogueParser {

    fun parse(
        script: String,
        manualOffsets: Map<String, Pair<Float, Float>> = emptyMap()
    ): DialogueGraph {
        val lines = script.split('\n')
        var sceneTitle = "Untitled Scene"
        val nodes = mutableListOf<DialogueNode>()
        val edges = mutableListOf<DialogueEdge>()
        val characters = linkedSetOf<String>()
        val variables = linkedSetOf<String>()

        var currentCharacter = ""
        var activeNodeId: String? = null
        var currentChoiceHubId: String? = null
        val optionNameMap = mutableMapOf<String, String>() // normalized option name -> nodeId
        val pendingGotoEdges = mutableListOf<Pair<String, String>>() // fromId to targetName
        val openOptionEndIds = mutableListOf<String>()
        var inChoiceList = false
        var inNarrationBlock = false

        fun addNode(node: DialogueNode): DialogueNode {
            nodes.add(node)
            return node
        }

        fun addEdge(from: String, to: String, label: String = "") {
            if (from != to && edges.none { it.fromId == from && it.toId == to && it.label == label }) {
                edges.add(DialogueEdge(from, to, label))
            }
        }

        var idx = 0
        while (idx < lines.size) {
            val raw = lines[idx]
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                inNarrationBlock = false
                idx++
                continue
            }

            val upper = trimmed.uppercase()

            when {
                upper.startsWith("SCENE:") -> {
                    sceneTitle = trimmed.substringAfter(":").trim().ifEmpty { "Untitled Scene" }
                    inChoiceList = false
                    inNarrationBlock = false
                }
                upper.startsWith("CHARACTER:") -> {
                    currentCharacter = trimmed.substringAfter(":").trim()
                    if (currentCharacter.isNotEmpty()) {
                        characters.add(currentCharacter)
                    }
                    inChoiceList = false
                    inNarrationBlock = false
                }
                upper.startsWith("SET:") || upper.startsWith("IF:") -> {
                    val expr = trimmed.substringAfter(":").trim()
                    if (expr.isNotEmpty()) variables.add(expr)
                }
                upper.startsWith("DIALOGUE:") || upper.startsWith("THOUGHT:") -> {
                    inChoiceList = false
                    inNarrationBlock = false
                    val isThought = upper.startsWith("THOUGHT:")
                    val content = trimmed.substringAfter(":").trim()
                    val nodeId = "dlg_node_${nodes.size + 1}"
                    val node = addNode(
                        DialogueNode(
                            id = nodeId,
                            type = if (isThought) DialogueNodeType.THOUGHT else DialogueNodeType.SPEECH,
                            speaker = currentCharacter.ifEmpty { "Narrator" },
                            text = content,
                            sourceLine = idx
                        )
                    )
                    if (activeNodeId != null) {
                        addEdge(activeNodeId!!, node.id)
                    }
                    activeNodeId = node.id
                }
                upper.startsWith("NARRATION:") -> {
                    inChoiceList = false
                    val inlineText = trimmed.substringAfter(":").trim()
                    val textBuilder = StringBuilder(inlineText)
                    if (inlineText.isEmpty() && idx + 1 < lines.size) {
                        inNarrationBlock = true
                        while (idx + 1 < lines.size) {
                            val nextLine = lines[idx + 1].trim()
                            val nextUpper = nextLine.uppercase()
                            if (nextLine.isEmpty() ||
                                nextUpper.startsWith("CHARACTER:") ||
                                nextUpper.startsWith("DIALOGUE:") ||
                                nextUpper.startsWith("CHOICE:") ||
                                nextUpper.startsWith("OPTION:") ||
                                nextUpper.startsWith("SCENE:") ||
                                nextUpper.startsWith("END_CHOICE")
                            ) {
                                break
                            }
                            if (textBuilder.isNotEmpty()) textBuilder.append(" ")
                            textBuilder.append(nextLine)
                            idx++
                        }
                    }
                    val nodeId = "dlg_node_${nodes.size + 1}"
                    val node = addNode(
                        DialogueNode(
                            id = nodeId,
                            type = DialogueNodeType.NARRATION,
                            speaker = "Narration",
                            text = textBuilder.toString().ifEmpty { "..." },
                            sourceLine = idx
                        )
                    )
                    if (activeNodeId != null) {
                        addEdge(activeNodeId!!, node.id)
                    }
                    activeNodeId = node.id
                }
                upper.startsWith("CHOICE:") -> {
                    inChoiceList = true
                    inNarrationBlock = false
                    val choices = mutableListOf<String>()
                    var lookahead = idx + 1
                    while (lookahead < lines.size) {
                        val candidate = lines[lookahead].trim()
                        if (candidate.matches(Regex("^\\d+\\..*"))) {
                            choices.add(candidate.substringAfter(".").trim())
                            lookahead++
                        } else if (candidate.isEmpty()) {
                            lookahead++
                        } else {
                            break
                        }
                    }
                    val hubId = "dlg_choice_${nodes.size + 1}"
                    val hubNode = addNode(
                        DialogueNode(
                            id = hubId,
                            type = DialogueNodeType.CHOICE_HUB,
                            speaker = currentCharacter.ifEmpty { "Choice" },
                            text = "Player Choice (${choices.size} options)",
                            choices = choices,
                            sourceLine = idx,
                            height = (86f + choices.size * 22f).coerceAtMost(160f)
                        )
                    )
                    if (activeNodeId != null) {
                        addEdge(activeNodeId!!, hubNode.id)
                    }
                    currentChoiceHubId = hubNode.id
                    activeNodeId = hubNode.id
                }
                upper.startsWith("OPTION:") -> {
                    inChoiceList = false
                    // Save previous option branch end if it wasn't closed by GOTO
                    if (activeNodeId != null && activeNodeId != currentChoiceHubId) {
                        openOptionEndIds.add(activeNodeId!!)
                    }
                    val optionLabel = trimmed.substringAfter(":").trim()
                    val optId = "dlg_opt_${nodes.size + 1}"
                    val optNode = addNode(
                        DialogueNode(
                            id = optId,
                            type = DialogueNodeType.OPTION_BRANCH,
                            speaker = currentCharacter.ifEmpty { "Option" },
                            text = "Branch: $optionLabel",
                            sourceLine = idx,
                            height = 76f
                        )
                    )
                    optionNameMap[optionLabel.lowercase()] = optId
                    if (currentChoiceHubId != null) {
                        addEdge(currentChoiceHubId!!, optId, optionLabel)
                    } else if (activeNodeId != null) {
                        addEdge(activeNodeId!!, optId, optionLabel)
                    }
                    activeNodeId = optId
                }
                upper.startsWith("GOTO:") -> {
                    val target = trimmed.substringAfter(":").trim()
                    if (activeNodeId != null && target.isNotEmpty()) {
                        pendingGotoEdges.add(activeNodeId!! to target)
                        if (target.equals("END_CHOICE", ignoreCase = true)) {
                            openOptionEndIds.add(activeNodeId!!)
                        }
                        // Detach sequential flow until next OPTION or END_CHOICE
                        activeNodeId = currentChoiceHubId
                    }
                }
                upper == "END_CHOICE" -> {
                    if (activeNodeId != null && activeNodeId != currentChoiceHubId) {
                        openOptionEndIds.add(activeNodeId!!)
                    }
                    val mergeId = "dlg_end_choice_${nodes.size + 1}"
                    val mergeNode = addNode(
                        DialogueNode(
                            id = mergeId,
                            type = DialogueNodeType.END_CHOICE,
                            speaker = "Merge",
                            text = "Conversation Converges",
                            sourceLine = idx,
                            width = 210f,
                            height = 60f
                        )
                    )
                    openOptionEndIds.distinct().forEach { branchEnd ->
                        addEdge(branchEnd, mergeId, "merge")
                    }
                    openOptionEndIds.clear()
                    currentChoiceHubId = null
                    activeNodeId = mergeId
                }
            }
            idx++
        }

        // Resolve non-END_CHOICE GOTO edges
        pendingGotoEdges.forEach { (fromId, targetName) ->
            if (!targetName.equals("END_CHOICE", ignoreCase = true)) {
                val targetId = optionNameMap[targetName.lowercase()]
                if (targetId != null) {
                    addEdge(fromId, targetId, "goto")
                }
            }
        }

        // Layout Dialogue Nodes (Layered Vertical with Horizontal Option Fan-Out)
        val positioned = layoutDialogueNodes(nodes, edges, manualOffsets)

        return DialogueGraph(
            sceneTitle = sceneTitle,
            nodes = positioned,
            edges = edges,
            charactersInvolved = characters.toList(),
            variables = variables.toList()
        )
    }

    private fun layoutDialogueNodes(
        nodes: List<DialogueNode>,
        edges: List<DialogueEdge>,
        manualOffsets: Map<String, Pair<Float, Float>>
    ): List<DialogueNode> {
        if (nodes.isEmpty()) return emptyList()
        val rankMap = mutableMapOf<String, Int>()
        val outgoing = edges.groupBy { it.fromId }

        val firstId = nodes.first().id
        rankMap[firstId] = 0
        val queue = ArrayDeque<String>()
        queue.add(firstId)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            val r = rankMap[curr] ?: 0
            outgoing[curr]?.forEach { e ->
                val nextRank = r + 1
                val prev = rankMap[e.toId]
                if (prev == null || (nextRank > prev && nextRank < nodes.size + 2)) {
                    rankMap[e.toId] = nextRank
                    queue.add(e.toId)
                }
            }
        }

        var nextFallback = (rankMap.values.maxOrNull() ?: 0) + 1
        nodes.forEach { n ->
            if (!rankMap.containsKey(n.id)) {
                rankMap[n.id] = nextFallback++
            }
        }

        val layers = nodes.groupBy { rankMap[it.id] ?: 0 }.toSortedMap()
        val result = mutableListOf<DialogueNode>()
        var currentY = 80f

        layers.values.forEach { layerNodes ->
            val count = layerNodes.size
            var maxHeightInLayer = 90f
            layerNodes.forEachIndexed { idx, node ->
                val offsetX = (idx - (count - 1) / 2f) * 300f
                val defaultX = 380f + offsetX
                val defaultY = currentY
                val manual = manualOffsets[node.id]
                result.add(
                    node.copy(
                        x = manual?.first ?: defaultX,
                        y = manual?.second ?: defaultY
                    )
                )
                if (node.height > maxHeightInLayer) maxHeightInLayer = node.height
            }
            currentY += maxHeightInLayer + 68f
        }
        return result
    }
}

/**
 * Parser for Guber Scene Writer DSL.
 * Supports multi-scene scripts (`SCENE: 01`, `SCENE: 02`, ...) as well as detailed
 * sections (TITLE, LOCATION, SETTING, TIME, WEATHER, CHARACTERS, DESCRIPTION,
 * ENVIRONMENT, ACTION, DIALOGUE, NARRATION, CAMERA, AUDIO, MUSIC, VFX, NOTES, TRANSITION).
 */
object SceneParser {

    private enum class SectionType {
        NONE, CHARACTERS, DESCRIPTION, ENVIRONMENT, ACTION, DIALOGUE,
        NARRATION, CAMERA, AUDIO, MUSIC, VFX, NOTES
    }

    fun parse(
        script: String,
        manualOffsets: Map<String, Pair<Float, Float>> = emptyMap()
    ): SceneGraph {
        val lines = script.split('\n')
        val scenes = mutableListOf<SceneCardModel>()

        var currentNumber = ""
        var currentTitle = ""
        var currentLocation = ""
        var currentSetting = "EXT."
        var currentTime = "Evening"
        var currentWeather = "Clear"
        var currentTransition = "Cut To"
        var currentSourceLine = 0

        val characters = mutableListOf<String>()
        val descriptionLines = mutableListOf<String>()
        val environmentLines = mutableListOf<String>()
        val actions = mutableListOf<String>()
        val dialogues = mutableListOf<SceneDialogueLine>()
        val narrations = mutableListOf<String>()
        val cameras = mutableListOf<String>()
        val audios = mutableListOf<String>()
        val music = mutableListOf<String>()
        val vfx = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val links = mutableListOf<String>()

        var activeSection = SectionType.NONE
        var hasStartedScene = false

        fun flushCurrentScene() {
            if (!hasStartedScene && currentTitle.isEmpty() && actions.isEmpty() && dialogues.isEmpty()) return
            val index = scenes.size + 1
            val numStr = currentNumber.ifEmpty { index.toString().padStart(2, '0') }
            val sceneId = "scene_${index}_$numStr"
            val col = (index - 1) % 2
            val row = (index - 1) / 2
            val defaultX = 90f + col * 340f
            val defaultY = 90f + row * 250f
            val manual = manualOffsets[sceneId]

            scenes.add(
                SceneCardModel(
                    id = sceneId,
                    sceneNumber = numStr,
                    title = currentTitle.ifEmpty { "Scene $numStr" },
                    location = currentLocation.ifEmpty { "Unspecified Location" },
                    settingIntExt = currentSetting,
                    timeOfDay = currentTime,
                    weather = currentWeather,
                    characters = characters.toList(),
                    description = descriptionLines.joinToString(" "),
                    environment = environmentLines.joinToString(" "),
                    actions = actions.toList(),
                    dialogues = dialogues.toList(),
                    narration = narrations.toList(),
                    cameraDirections = cameras.toList(),
                    audioNotes = audios.toList(),
                    musicNotes = music.toList(),
                    vfxNotes = vfx.toList(),
                    productionNotes = notes.toList(),
                    transition = currentTransition,
                    linkedSceneNumbers = links.toList(),
                    sourceLine = currentSourceLine,
                    x = manual?.first ?: defaultX,
                    y = manual?.second ?: defaultY
                )
            )

            currentNumber = ""
            currentTitle = ""
            currentLocation = ""
            currentSetting = "EXT."
            currentTime = "Day"
            currentWeather = "Clear"
            currentTransition = "Cut To"
            characters.clear()
            descriptionLines.clear()
            environmentLines.clear()
            actions.clear()
            dialogues.clear()
            narrations.clear()
            cameras.clear()
            audios.clear()
            music.clear()
            vfx.clear()
            notes.clear()
            links.clear()
            activeSection = SectionType.NONE
        }

        lines.forEachIndexed { idx, raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                return@forEachIndexed
            }
            val upper = trimmed.uppercase()

            when {
                upper.startsWith("SCENE:") -> {
                    if (hasStartedScene) {
                        flushCurrentScene()
                    }
                    hasStartedScene = true
                    currentSourceLine = idx
                    currentNumber = trimmed.substringAfter(":").trim()
                    activeSection = SectionType.NONE
                }
                upper.startsWith("TITLE:") -> {
                    hasStartedScene = true
                    currentTitle = trimmed.substringAfter(":").trim()
                    activeSection = SectionType.NONE
                }
                upper.startsWith("LOCATION:") -> {
                    val loc = trimmed.substringAfter(":").trim()
                    if (loc.uppercase().startsWith("INT.")) {
                        currentSetting = "INT."
                    } else if (loc.uppercase().startsWith("EXT.")) {
                        currentSetting = "EXT."
                    }
                    currentLocation = loc
                    activeSection = SectionType.NONE
                }
                upper.startsWith("SETTING:") -> {
                    currentSetting = trimmed.substringAfter(":").trim()
                    activeSection = SectionType.NONE
                }
                upper.startsWith("TIME:") -> {
                    currentTime = trimmed.substringAfter(":").trim()
                    activeSection = SectionType.NONE
                }
                upper.startsWith("WEATHER:") -> {
                    currentWeather = trimmed.substringAfter(":").trim()
                    activeSection = SectionType.NONE
                }
                upper.startsWith("TRANSITION:") -> {
                    currentTransition = trimmed.substringAfter(":").trim()
                    activeSection = SectionType.NONE
                }
                upper.startsWith("LINK:") -> {
                    val linked = trimmed.substringAfter(":").trim()
                    if (linked.isNotEmpty()) links.add(linked)
                    activeSection = SectionType.NONE
                }
                upper.startsWith("CHARACTERS:") -> activeSection = SectionType.CHARACTERS
                upper.startsWith("DESCRIPTION:") -> {
                    activeSection = SectionType.DESCRIPTION
                    val inline = trimmed.substringAfter(":").trim()
                    if (inline.isNotEmpty()) descriptionLines.add(inline)
                }
                upper.startsWith("ENVIRONMENT:") -> {
                    activeSection = SectionType.ENVIRONMENT
                    val inline = trimmed.substringAfter(":").trim()
                    if (inline.isNotEmpty()) environmentLines.add(inline)
                }
                upper.startsWith("ACTION:") -> {
                    activeSection = SectionType.ACTION
                    val inline = trimmed.substringAfter(":").trim()
                    if (inline.isNotEmpty()) actions.add(inline)
                }
                upper.startsWith("DIALOGUE:") -> activeSection = SectionType.DIALOGUE
                upper.startsWith("NARRATION:") -> {
                    activeSection = SectionType.NARRATION
                    val inline = trimmed.substringAfter(":").trim()
                    if (inline.isNotEmpty()) narrations.add(inline)
                }
                upper.startsWith("CAMERA:") -> {
                    activeSection = SectionType.CAMERA
                    val inline = trimmed.substringAfter(":").trim()
                    if (inline.isNotEmpty()) cameras.add(inline)
                }
                upper.startsWith("AUDIO:") || upper.startsWith("SFX:") -> {
                    activeSection = SectionType.AUDIO
                    val inline = trimmed.substringAfter(":").trim()
                    if (inline.isNotEmpty()) audios.add(inline)
                }
                upper.startsWith("MUSIC:") -> {
                    activeSection = SectionType.MUSIC
                    val inline = trimmed.substringAfter(":").trim()
                    if (inline.isNotEmpty()) music.add(inline)
                }
                upper.startsWith("VFX:") -> {
                    activeSection = SectionType.VFX
                    val inline = trimmed.substringAfter(":").trim()
                    if (inline.isNotEmpty()) vfx.add(inline)
                }
                upper.startsWith("NOTES:") -> {
                    activeSection = SectionType.NOTES
                    val inline = trimmed.substringAfter(":").trim()
                    if (inline.isNotEmpty()) notes.add(inline)
                }
                else -> {
                    val cleanItem = trimmed.removePrefix("-").trim()
                    when (activeSection) {
                        SectionType.CHARACTERS -> if (cleanItem.isNotEmpty()) characters.add(cleanItem)
                        SectionType.DESCRIPTION -> descriptionLines.add(cleanItem)
                        SectionType.ENVIRONMENT -> environmentLines.add(cleanItem)
                        SectionType.ACTION -> actions.add(cleanItem)
                        SectionType.DIALOGUE -> {
                            if (cleanItem.contains(":")) {
                                val spk = cleanItem.substringBefore(":").trim()
                                val txt = cleanItem.substringAfter(":").trim()
                                dialogues.add(SceneDialogueLine(spk, txt))
                            } else {
                                dialogues.add(SceneDialogueLine("Speaker", cleanItem))
                            }
                        }
                        SectionType.NARRATION -> narrations.add(cleanItem)
                        SectionType.CAMERA -> cameras.add(cleanItem)
                        SectionType.AUDIO -> audios.add(cleanItem)
                        SectionType.MUSIC -> music.add(cleanItem)
                        SectionType.VFX -> vfx.add(cleanItem)
                        SectionType.NOTES -> notes.add(cleanItem)
                        SectionType.NONE -> actions.add(cleanItem)
                    }
                }
            }
        }

        flushCurrentScene()

        // Build scene-to-scene sequence edges + explicit LINK edges
        val edges = mutableListOf<SceneEdge>()
        for (i in 0 until scenes.size - 1) {
            edges.add(
                SceneEdge(
                    fromSceneId = scenes[i].id,
                    toSceneId = scenes[i + 1].id,
                    transitionLabel = scenes[i].transition
                )
            )
        }
        scenes.forEach { scene ->
            scene.linkedSceneNumbers.forEach { targetNum ->
                val targetScene = scenes.firstOrNull {
                    it.sceneNumber.equals(targetNum, ignoreCase = true) ||
                        it.title.equals(targetNum, ignoreCase = true)
                }
                if (targetScene != null && targetScene.id != scene.id) {
                    edges.add(SceneEdge(scene.id, targetScene.id, "Link"))
                }
            }
        }

        return SceneGraph(scenes = scenes, edges = edges)
    }
}
