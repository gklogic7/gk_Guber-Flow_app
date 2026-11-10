package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.core.editor.EditorLanguage
import com.example.core.editor.EditorStateController
import com.example.core.editor.SyntaxHighlighterFactory
import com.example.core.graph.FlowNodeType
import com.example.core.parser.DialogueParser
import com.example.core.parser.FlowchartParser
import com.example.core.parser.SceneParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun editorController_undoAndRedo_restoresStateProperly() {
        val controller = EditorStateController(
            initialText = "Start\nEnd",
            initialLanguage = EditorLanguage.FLOWCHART
        )
        assertFalse(controller.canUndo)
        assertFalse(controller.canRedo)

        controller.setTextProgrammatically("Start\nProcess: Step 1\nEnd", recordHistory = true)
        assertTrue(controller.canUndo)
        assertEquals("Start\nProcess: Step 1\nEnd", controller.textFieldValue.text)

        assertTrue(controller.undo())
        assertEquals("Start\nEnd", controller.textFieldValue.text)
        assertTrue(controller.canRedo)

        assertTrue(controller.redo())
        assertEquals("Start\nProcess: Step 1\nEnd", controller.textFieldValue.text)
    }

    @Test
    fun editorController_smartIndentationAndFindReplace_workCorrectly() {
        val controller = EditorStateController(
            initialText = "CHOICE:",
            initialLanguage = EditorLanguage.DIALOGUE
        )
        // Simulate pressing Enter after "CHOICE:"
        controller.onValueChange(
            TextFieldValue(text = "CHOICE:\n", selection = TextRange(8))
        )
        assertEquals("CHOICE:\n  ", controller.textFieldValue.text)

        // Test Find & Replace
        controller.setTextProgrammatically("CHARACTER: Krishna\nDIALOGUE: Hello Krishna", recordHistory = true)
        controller.updateFindQuery("Krishna")
        assertEquals(2, controller.findMatches.size)
        controller.replaceQuery = "Arjun"
        val replacedCount = controller.replaceAllMatches()
        assertEquals(2, replacedCount)
        assertTrue(controller.textFieldValue.text.contains("Arjun"))
    }

    @Test
    fun syntaxHighlighters_tokenizeAndValidateAllThreeModules() {
        val flowHighlighter = SyntaxHighlighterFactory.forLanguage(EditorLanguage.FLOWCHART)
        val flowSpans = flowHighlighter.tokenize("Start\nIn stock?\nyes: Pack\nno: Reorder\nEnd")
        assertTrue(flowSpans.isNotEmpty())
        assertTrue(flowHighlighter.validate("Start\nIn stock?\nyes: Pack\nno: Reorder\nEnd").isEmpty())

        val dlgHighlighter = SyntaxHighlighterFactory.forLanguage(EditorLanguage.DIALOGUE)
        val dlgDiags = dlgHighlighter.validate("CHOICE:\n  1. Tea")
        assertTrue(dlgDiags.any { it.message.contains("END_CHOICE") })
    }

    @Test
    fun flowchartParser_buildsDecisionBranchesAndConverges() {
        val script = """
            Start
            Receive the order
            In stock?
            yes: Pack the order
            no: Reorder from supplier
            Print the invoice
            End
        """.trimIndent()

        val graph = FlowchartParser.parse(script)
        assertEquals(7, graph.nodes.size)
        val decisionNode = graph.nodes.first { it.type == FlowNodeType.DECISION }
        assertEquals("In stock?", decisionNode.label)

        val outgoingFromDecision = graph.edges.filter { it.fromId == decisionNode.id }
        assertEquals(2, outgoingFromDecision.size)
        assertTrue(outgoingFromDecision.any { it.label == "YES" })
        assertTrue(outgoingFromDecision.any { it.label == "NO" })
    }

    @Test
    fun dialogueAndSceneParsers_parseBranchingAndScenes() {
        val dlgScript = """
            SCENE: Coffee Shop
            CHARACTER: Krishna
            DIALOGUE: What would you like?
            CHOICE:
              1. Tea
              2. Coffee
            OPTION: Tea
            DIALOGUE: One tea, please.
            GOTO: END_CHOICE
            OPTION: Coffee
            DIALOGUE: One coffee, please.
            END_CHOICE
        """.trimIndent()
        val dlgGraph = DialogueParser.parse(dlgScript)
        assertEquals("Coffee Shop", dlgGraph.sceneTitle)
        assertTrue(dlgGraph.nodes.size >= 5)

        val sceneScript = """
            SCENE: 01
            TITLE: A Rainy Evening
            LOCATION: Chennai - Tea Shop
            CHARACTERS:
              - Krishna
              - Meera
            ACTION:
              Krishna stands near the tea shop.
            TRANSITION: Fade Out
        """.trimIndent()
        val sceneGraph = SceneParser.parse(sceneScript)
        assertEquals(1, sceneGraph.scenes.size)
        assertEquals("A Rainy Evening", sceneGraph.scenes.first().title)
        assertEquals(2, sceneGraph.scenes.first().characters.size)
    }
}
