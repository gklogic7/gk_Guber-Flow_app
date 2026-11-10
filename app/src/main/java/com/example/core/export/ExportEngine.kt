package com.example.core.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.core.graph.DialogueGraph
import com.example.core.graph.FlowGraph
import com.example.core.graph.FlowNodeType
import com.example.core.graph.SceneGraph
import com.example.data.CharacterEntity
import com.example.data.ProjectEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

enum class ExportFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val isBinary: Boolean = false
) {
    JSON("JSON Project (.json)", "json", "application/json", false),
    TXT("Plain Text (.txt)", "txt", "text/plain", false),
    MARKDOWN("Markdown (.md)", "md", "text/markdown", false),
    HTML("HTML Web Doc (.html)", "html", "text/html", false),
    SVG("Scalable Vector (.svg)", "svg", "image/svg+xml", false),
    PNG("PNG Image (.png)", "png", "image/png", true),
    JPEG("JPEG Image (.jpg)", "jpg", "image/jpeg", true),
    PDF("PDF Document (.pdf)", "pdf", "application/pdf", true)
}

data class ExportConfig(
    val format: ExportFormat = ExportFormat.JSON,
    val transparentBackground: Boolean = false,
    val darkBackground: Boolean = true,
    val scaleMultiplier: Int = 2, // 1x, 2x, 3x high-res
    val landscapePage: Boolean = false
)

object ExportEngine {

    fun generateTextExport(
        project: ProjectEntity,
        characters: List<CharacterEntity>,
        flowGraph: FlowGraph,
        dialogueGraph: DialogueGraph,
        sceneGraph: SceneGraph,
        config: ExportConfig
    ): String {
        return when (config.format) {
            ExportFormat.JSON -> buildProjectJson(project, characters)
            ExportFormat.TXT -> buildPlainText(project, characters)
            ExportFormat.MARKDOWN -> buildMarkdown(project, characters, flowGraph, dialogueGraph, sceneGraph)
            ExportFormat.HTML -> buildHtmlDocument(project, characters, flowGraph, config)
            ExportFormat.SVG -> buildFlowchartSvg(flowGraph, config)
            else -> buildMarkdown(project, characters, flowGraph, dialogueGraph, sceneGraph)
        }
    }

    fun writeExportToStream(
        outputStream: OutputStream,
        project: ProjectEntity,
        characters: List<CharacterEntity>,
        flowGraph: FlowGraph,
        dialogueGraph: DialogueGraph,
        sceneGraph: SceneGraph,
        config: ExportConfig
    ) {
        when (config.format) {
            ExportFormat.PNG -> {
                val bmp = renderFlowchartBitmap(flowGraph, config, allowTransparency = config.transparentBackground)
                bmp.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            ExportFormat.JPEG -> {
                val bmp = renderFlowchartBitmap(flowGraph, config, allowTransparency = false)
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
            ExportFormat.PDF -> {
                writePdfDocument(outputStream, project, flowGraph, sceneGraph, config)
            }
            else -> {
                val content = generateTextExport(project, characters, flowGraph, dialogueGraph, sceneGraph, config)
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
        }
        outputStream.flush()
    }

    fun parseImportedProjectJson(jsonText: String): Pair<ProjectEntity, List<CharacterEntity>>? {
        return try {
            val root = JSONObject(jsonText)
            val name = root.optString("name", "Imported Guber Project")
            val description = root.optString("description", "Imported from JSON")
            val flowchart = root.optString("flowchartScript", "")
            val dialogue = root.optString("dialogueScript", "")
            val scene = root.optString("sceneScript", "")

            val project = ProjectEntity(
                name = name,
                description = description,
                flowchartScript = flowchart,
                dialogueScript = dialogue,
                sceneScript = scene
            )

            val chars = mutableListOf<CharacterEntity>()
            val charArr = root.optJSONArray("characters")
            if (charArr != null) {
                for (i in 0 until charArr.length()) {
                    val obj = charArr.getJSONObject(i)
                    chars.add(
                        CharacterEntity(
                            projectId = 0,
                            name = obj.optString("name", "Character"),
                            role = obj.optString("role", "Role"),
                            description = obj.optString("description", ""),
                            colorHex = obj.optLong("colorHex", 0xFF38BDF8L),
                            avatarInitials = obj.optString("avatarInitials", "CH")
                        )
                    )
                }
            }
            project to chars
        } catch (e: Exception) {
            null
        }
    }

    private fun buildProjectJson(project: ProjectEntity, characters: List<CharacterEntity>): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("generator", "GUBER Studio Android")
        root.put("name", project.name)
        root.put("description", project.description)
        root.put("updatedAt", project.updatedAt)
        root.put("flowchartScript", project.flowchartScript)
        root.put("dialogueScript", project.dialogueScript)
        root.put("sceneScript", project.sceneScript)

        val charArray = JSONArray()
        characters.forEach { c ->
            val obj = JSONObject()
            obj.put("name", c.name)
            obj.put("role", c.role)
            obj.put("description", c.description)
            obj.put("colorHex", c.colorHex)
            obj.put("avatarInitials", c.avatarInitials)
            charArray.put(obj)
        }
        root.put("characters", charArray)
        return root.toString(2)
    }

    private fun buildPlainText(project: ProjectEntity, characters: List<CharacterEntity>): String = buildString {
        appendLine("====================================================")
        appendLine("GUBER STUDIO PROJECT: ${project.name.uppercase()}")
        appendLine("Description: ${project.description}")
        appendLine("====================================================")
        appendLine()
        appendLine("--- CHARACTERS (${characters.size}) ---")
        characters.forEach { c ->
            appendLine("* ${c.name} [${c.role}]: ${c.description}")
        }
        appendLine()
        appendLine("--- 1. FLOWCHART SCRIPT ---")
        appendLine(project.flowchartScript)
        appendLine()
        appendLine("--- 2. DIALOGUE SCRIPT ---")
        appendLine(project.dialogueScript)
        appendLine()
        appendLine("--- 3. SCENE SCRIPT ---")
        appendLine(project.sceneScript)
    }

    private fun buildMarkdown(
        project: ProjectEntity,
        characters: List<CharacterEntity>,
        flowGraph: FlowGraph,
        dialogueGraph: DialogueGraph,
        sceneGraph: SceneGraph
    ): String = buildString {
        appendLine("# ${project.name}")
        appendLine("> ${project.description}")
        appendLine()
        appendLine("## Characters (${characters.size})")
        if (characters.isEmpty()) {
            appendLine("_No characters defined._")
        } else {
            appendLine("| Name | Role | Description |")
            appendLine("|---|---|---|")
            characters.forEach { c ->
                appendLine("| **${c.name}** | ${c.role} | ${c.description} |")
            }
        }
        appendLine()
        appendLine("## Flowchart (${flowGraph.nodes.size} Nodes, ${flowGraph.edges.size} Edges)")
        appendLine("```flowchart")
        appendLine(project.flowchartScript)
        appendLine("```")
        appendLine()
        appendLine("## Dialogue — ${dialogueGraph.sceneTitle} (${dialogueGraph.nodes.size} Blocks)")
        appendLine("```dialogue")
        appendLine(project.dialogueScript)
        appendLine("```")
        appendLine()
        appendLine("## Scenes (${sceneGraph.scenes.size} Scenes)")
        appendLine("```scene")
        appendLine(project.sceneScript)
        appendLine("```")
    }

    private fun buildFlowchartSvg(flowGraph: FlowGraph, config: ExportConfig): String {
        val maxX = (flowGraph.nodes.maxOfOrNull { it.x + it.width } ?: 800f) + 120f
        val maxY = (flowGraph.nodes.maxOfOrNull { it.y + it.height } ?: 600f) + 120f
        val bgHex = if (config.darkBackground) "#0D1117" else "#F8FAFC"
        val nodeFill = if (config.darkBackground) "#161B22" else "#FFFFFF"
        val textFill = if (config.darkBackground) "#E6EDF3" else "#0F172A"
        val nodeMap = flowGraph.nodes.associateBy { it.id }

        return buildString {
            appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${maxX.toInt()} ${maxY.toInt()}" width="100%" height="100%">""")
            if (!config.transparentBackground) {
                appendLine("""  <rect width="100%" height="100%" fill="$bgHex" rx="12"/>""")
            }
            // Edges
            flowGraph.edges.forEach { edge ->
                val from = nodeMap[edge.fromId] ?: return@forEach
                val to = nodeMap[edge.toId] ?: return@forEach
                val x1 = from.x + from.width / 2f
                val y1 = from.y + from.height
                val x2 = to.x + to.width / 2f
                val y2 = to.y
                appendLine("""  <line x1="$x1" y1="$y1" x2="$x2" y2="$y2" stroke="#38BDF8" stroke-width="2.5" />""")
                if (edge.label.isNotEmpty()) {
                    val mx = (x1 + x2) / 2f
                    val my = (y1 + y2) / 2f
                    appendLine("""  <text x="$mx" y="$my" fill="#F59E0B" font-family="monospace" font-size="11" text-anchor="middle">${escapeXml(edge.label)}</text>""")
                }
            }
            // Nodes
            flowGraph.nodes.forEach { node ->
                val stroke = when (node.type) {
                    FlowNodeType.TERMINATOR -> "#10B981"
                    FlowNodeType.DECISION -> "#F59E0B"
                    FlowNodeType.INPUT_OUTPUT -> "#A855F7"
                    FlowNodeType.ANNOTATION -> "#64748B"
                    FlowNodeType.PROCESS -> "#38BDF8"
                }
                val rx = if (node.type == FlowNodeType.TERMINATOR) (node.height / 2f).toInt() else 10
                appendLine("""  <rect x="${node.x}" y="${node.y}" width="${node.width}" height="${node.height}" rx="$rx" fill="$nodeFill" stroke="$stroke" stroke-width="2.2"/>""")
                val tx = node.x + node.width / 2f
                val ty = node.y + node.height / 2f + 4f
                appendLine("""  <text x="$tx" y="$ty" fill="$textFill" font-family="sans-serif" font-size="13" font-weight="600" text-anchor="middle">${escapeXml(node.label)}</text>""")
            }
            appendLine("</svg>")
        }
    }

    private fun buildHtmlDocument(
        project: ProjectEntity,
        characters: List<CharacterEntity>,
        flowGraph: FlowGraph,
        config: ExportConfig
    ): String {
        val svg = buildFlowchartSvg(flowGraph, config)
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <title>${escapeXml(project.name)} — GUBER Studio Export</title>
              <style>
                body { font-family: system-ui, sans-serif; background: #0D1117; color: #E6EDF3; padding: 32px; max-width: 960px; margin: 0 auto; }
                h1, h2 { color: #38BDF8; }
                pre { background: #161B22; padding: 16px; border-radius: 10px; border: 1px solid #30363D; overflow-x: auto; }
                .svg-box { border: 1px solid #30363D; border-radius: 12px; padding: 16px; margin: 16px 0; background: #0B0F15; }
              </style>
            </head>
            <body>
              <h1>${escapeXml(project.name)}</h1>
              <p>${escapeXml(project.description)}</p>
              <h2>Interactive Flowchart Visual</h2>
              <div class="svg-box">$svg</div>
              <h2>Flowchart DSL</h2>
              <pre>${escapeXml(project.flowchartScript)}</pre>
              <h2>Dialogue Script</h2>
              <pre>${escapeXml(project.dialogueScript)}</pre>
              <h2>Scene Script</h2>
              <pre>${escapeXml(project.sceneScript)}</pre>
            </body>
            </html>
        """.trimIndent()
    }

    private fun renderFlowchartBitmap(
        flowGraph: FlowGraph,
        config: ExportConfig,
        allowTransparency: Boolean
    ): Bitmap {
        val scale = config.scaleMultiplier.coerceIn(1, 3)
        val width = ((flowGraph.nodes.maxOfOrNull { it.x + it.width } ?: 750f) + 140f).toInt().coerceAtLeast(600) * scale
        val height = ((flowGraph.nodes.maxOfOrNull { it.y + it.height } ?: 550f) + 140f).toInt().coerceAtLeast(450) * scale
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale.toFloat(), scale.toFloat())

        if (!allowTransparency) {
            canvas.drawColor(if (config.darkBackground) Color.parseColor("#0D1117") else Color.parseColor("#F8FAFC"))
        }

        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (config.darkBackground) Color.parseColor("#161B22") else Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (config.darkBackground) Color.parseColor("#E6EDF3") else Color.parseColor("#0F172A")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val nodeMap = flowGraph.nodes.associateBy { it.id }
        flowGraph.edges.forEach { e ->
            val from = nodeMap[e.fromId] ?: return@forEach
            val to = nodeMap[e.toId] ?: return@forEach
            canvas.drawLine(
                from.x + from.width / 2f,
                from.y + from.height,
                to.x + to.width / 2f,
                to.y,
                edgePaint
            )
        }

        flowGraph.nodes.forEach { n ->
            borderPaint.color = when (n.type) {
                FlowNodeType.TERMINATOR -> Color.parseColor("#10B981")
                FlowNodeType.DECISION -> Color.parseColor("#F59E0B")
                FlowNodeType.INPUT_OUTPUT -> Color.parseColor("#A855F7")
                FlowNodeType.ANNOTATION -> Color.parseColor("#64748B")
                FlowNodeType.PROCESS -> Color.parseColor("#38BDF8")
            }
            val rect = RectF(n.x, n.y, n.x + n.width, n.y + n.height)
            val corner = if (n.type == FlowNodeType.TERMINATOR) n.height / 2f else 12f
            canvas.drawRoundRect(rect, corner, corner, fillPaint)
            canvas.drawRoundRect(rect, corner, corner, borderPaint)
            canvas.drawText(n.label.take(26), n.x + n.width / 2f, n.y + n.height / 2f + 5f, textPaint)
        }

        return bitmap
    }

    private fun writePdfDocument(
        outputStream: OutputStream,
        project: ProjectEntity,
        flowGraph: FlowGraph,
        sceneGraph: SceneGraph,
        config: ExportConfig
    ) {
        val pdf = PdfDocument()
        val pageWidth = if (config.landscapePage) 842 else 595
        val pageHeight = if (config.landscapePage) 595 else 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }

        var y = 44f
        canvas.drawText("GUBER STUDIO — ${project.name}", 36f, y, titlePaint)
        y += 22f
        canvas.drawText(project.description.take(90), 36f, y, bodyPaint)
        y += 28f

        canvas.drawText("Flowchart Nodes (${flowGraph.nodes.size}):", 36f, y, titlePaint.apply { textSize = 14f })
        y += 18f
        flowGraph.nodes.take(12).forEach { node ->
            canvas.drawText("• [${node.type.label}] ${node.label}", 44f, y, bodyPaint)
            y += 15f
        }

        y += 16f
        canvas.drawText("Scenes (${sceneGraph.scenes.size}):", 36f, y, titlePaint)
        y += 18f
        sceneGraph.scenes.take(10).forEach { scene ->
            canvas.drawText(
                "Scene ${scene.sceneNumber}: ${scene.title} (${scene.location} - ${scene.timeOfDay})",
                44f,
                y,
                bodyPaint
            )
            y += 15f
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        pdf.close()
    }

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
