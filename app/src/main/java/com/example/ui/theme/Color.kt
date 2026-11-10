package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Dark Studio Palette (Obsidian Slate + Electric Teal/Amber accents)
val StudioDarkBg = Color(0xFF0D1117)
val StudioDarkSurface = Color(0xFF161B22)
val StudioDarkSurfaceVariant = Color(0xFF21262D)
val StudioDarkBorder = Color(0xFF30363D)
val StudioDarkPrimary = Color(0xFF38BDF8)
val StudioDarkSecondary = Color(0xFFA78BFA)
val StudioDarkTertiary = Color(0xFFF59E0B)
val StudioDarkOnBg = Color(0xFFE6EDF3)
val StudioDarkOnSurfaceVariant = Color(0xFF8B949E)
val StudioDarkError = Color(0xFFF85149)

// Light Studio Palette (Warm Architectural Paper + Deep Indigo/Teal)
val StudioLightBg = Color(0xFFF8FAFC)
val StudioLightSurface = Color(0xFFFFFFFF)
val StudioLightSurfaceVariant = Color(0xFFF1F5F9)
val StudioLightBorder = Color(0xFFE2E8F0)
val StudioLightPrimary = Color(0xFF0284C7)
val StudioLightSecondary = Color(0xFF6D28D9)
val StudioLightTertiary = Color(0xFFD97706)
val StudioLightOnBg = Color(0xFF0F172A)
val StudioLightOnSurfaceVariant = Color(0xFF64748B)
val StudioLightError = Color(0xFFDC2626)

// Syntax Highlighting Tokens (Dark & Light adaptive via helper)
data class EditorSyntaxPalette(
    val editorBackground: Color,
    val gutterBackground: Color,
    val gutterText: Color,
    val activeLineText: Color,
    val activeLineBackground: Color,
    val keyword: Color,
    val command: Color,
    val characterName: Color,
    val branchLabel: Color,
    val conditionQuestion: Color,
    val stringOrDialogue: Color,
    val commentOrNote: Color,
    val numberOrChoice: Color,
    val errorUnderline: Color,
    val warningUnderline: Color,
    val findMatchHighlight: Color,
    val activeFindMatchHighlight: Color,
    val selectionBackground: Color
)

val DarkEditorSyntaxPalette = EditorSyntaxPalette(
    editorBackground = Color(0xFF0B0F15),
    gutterBackground = Color(0xFF11161F),
    gutterText = Color(0xFF566171),
    activeLineText = Color(0xFF38BDF8),
    activeLineBackground = Color(0xFF17202D),
    keyword = Color(0xFFFF7B72),          // Coral red for structural keywords (SCENE:, CHOICE:, Start, End)
    command = Color(0xFF79C0FF),          // Crisp blue for directives (Process:, Decision:, Input:, CAMERA:, AUDIO:)
    characterName = Color(0xFFD2A8FF),    // Soft violet for character names & speakers
    branchLabel = Color(0xFF3FB950),      // Emerald green for yes:, no:, OPTION:, GOTO:
    conditionQuestion = Color(0xFFF2CC60),// Warm gold for questions ending with ? or IF:
    stringOrDialogue = Color(0xFFA5D6FF), // Ice blue for dialogue text
    commentOrNote = Color(0xFF8B949E),    // Muted slate for Note: and # comments
    numberOrChoice = Color(0xFFFFA657),   // Orange for numbered items & scene numbers
    errorUnderline = Color(0xFFF85149),
    warningUnderline = Color(0xFFD29922),
    findMatchHighlight = Color(0x55F2CC60),
    activeFindMatchHighlight = Color(0x9938BDF8),
    selectionBackground = Color(0x4438BDF8)
)

val LightEditorSyntaxPalette = EditorSyntaxPalette(
    editorBackground = Color(0xFFFCFDFE),
    gutterBackground = Color(0xFFF1F5F9),
    gutterText = Color(0xFF94A3B8),
    activeLineText = Color(0xFF0284C7),
    activeLineBackground = Color(0xFFE0F2FE).copy(alpha = 0.45f),
    keyword = Color(0xFFCF222E),
    command = Color(0xFF0550AE),
    characterName = Color(0xFF8250DF),
    branchLabel = Color(0xFF116329),
    conditionQuestion = Color(0xFF9A6700),
    stringOrDialogue = Color(0xFF0A3069),
    commentOrNote = Color(0xFF6E7781),
    numberOrChoice = Color(0xFF953800),
    errorUnderline = Color(0xFFDC2626),
    warningUnderline = Color(0xFFD97706),
    findMatchHighlight = Color(0x55FACC15),
    activeFindMatchHighlight = Color(0x8838BDF8),
    selectionBackground = Color(0x330284C7)
)

// Visual Node Accent Colors
val NodeTerminatorColor = Color(0xFF10B981) // Emerald
val NodeProcessColor = Color(0xFF38BDF8)    // Sky Blue
val NodeDecisionColor = Color(0xFFF59E0B)   // Amber Gold
val NodeInputColor = Color(0xFFA855F7)      // Purple
val NodeNoteColor = Color(0xFF64748B)       // Slate

val CharacterPresetColors = listOf(
    0xFF38BDF8L, // Sky
    0xFFF472B6L, // Pink
    0xFF34D399L, // Emerald
    0xFFFBBF24L, // Amber
    0xFFA78BFAL, // Violet
    0xFFFB7185L, // Rose
    0xFF60A5FAL, // Blue
    0xFF4ADE80L  // Green
)
