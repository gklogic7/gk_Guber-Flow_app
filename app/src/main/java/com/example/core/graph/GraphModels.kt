package com.example.core.graph

enum class LayoutOrientation {
    VERTICAL,
    HORIZONTAL
}

enum class FlowNodeType(val label: String) {
    TERMINATOR("Terminator"),
    PROCESS("Process"),
    DECISION("Decision"),
    INPUT_OUTPUT("Input / Output"),
    ANNOTATION("Note")
}

data class FlowNode(
    val id: String,
    val label: String,
    val type: FlowNodeType,
    val sourceLine: Int,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 210f,
    val height: Float = 72f
)

data class FlowEdge(
    val fromId: String,
    val toId: String,
    val label: String = "",
    val isBackEdge: Boolean = false
)

data class FlowGraph(
    val nodes: List<FlowNode> = emptyList(),
    val edges: List<FlowEdge> = emptyList()
)

enum class DialogueNodeType {
    SPEECH,
    NARRATION,
    THOUGHT,
    CHOICE_HUB,
    OPTION_BRANCH,
    END_CHOICE
}

data class DialogueNode(
    val id: String,
    val type: DialogueNodeType,
    val speaker: String = "",
    val text: String = "",
    val choices: List<String> = emptyList(),
    val gotoTarget: String = "",
    val conditionOrFlag: String = "",
    val sourceLine: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 260f,
    val height: Float = 104f
)

data class DialogueEdge(
    val fromId: String,
    val toId: String,
    val label: String = ""
)

data class DialogueGraph(
    val sceneTitle: String = "Untitled Conversation",
    val nodes: List<DialogueNode> = emptyList(),
    val edges: List<DialogueEdge> = emptyList(),
    val charactersInvolved: List<String> = emptyList(),
    val variables: List<String> = emptyList()
)

data class CharacterProfile(
    val id: String,
    val name: String,
    val role: String = "Protagonist",
    val description: String = "",
    val colorHex: Long = 0xFF38BDF8L,
    val avatarInitials: String = "CH"
)

data class SceneDialogueLine(
    val speaker: String,
    val text: String
)

data class SceneCardModel(
    val id: String,
    val sceneNumber: String,
    val title: String,
    val location: String,
    val settingIntExt: String = "EXT.",
    val timeOfDay: String = "Evening",
    val weather: String = "Clear",
    val characters: List<String> = emptyList(),
    val description: String = "",
    val environment: String = "",
    val actions: List<String> = emptyList(),
    val dialogues: List<SceneDialogueLine> = emptyList(),
    val narration: List<String> = emptyList(),
    val cameraDirections: List<String> = emptyList(),
    val audioNotes: List<String> = emptyList(),
    val musicNotes: List<String> = emptyList(),
    val vfxNotes: List<String> = emptyList(),
    val productionNotes: List<String> = emptyList(),
    val transition: String = "Cut To",
    val linkedSceneNumbers: List<String> = emptyList(),
    val sourceLine: Int = 0,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 290f,
    val height: Float = 190f
)

data class SceneEdge(
    val fromSceneId: String,
    val toSceneId: String,
    val transitionLabel: String = ""
)

data class SceneGraph(
    val scenes: List<SceneCardModel> = emptyList(),
    val edges: List<SceneEdge> = emptyList()
)
