package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val GuberDarkColorScheme = darkColorScheme(
    primary = StudioDarkPrimary,
    onPrimary = StudioDarkBg,
    primaryContainer = StudioDarkPrimary.copy(alpha = 0.18f),
    onPrimaryContainer = StudioDarkPrimary,
    secondary = StudioDarkSecondary,
    onSecondary = StudioDarkBg,
    secondaryContainer = StudioDarkSecondary.copy(alpha = 0.18f),
    onSecondaryContainer = StudioDarkSecondary,
    tertiary = StudioDarkTertiary,
    onTertiary = StudioDarkBg,
    tertiaryContainer = StudioDarkTertiary.copy(alpha = 0.18f),
    onTertiaryContainer = StudioDarkTertiary,
    background = StudioDarkBg,
    onBackground = StudioDarkOnBg,
    surface = StudioDarkSurface,
    onSurface = StudioDarkOnBg,
    surfaceVariant = StudioDarkSurfaceVariant,
    onSurfaceVariant = StudioDarkOnSurfaceVariant,
    outline = StudioDarkBorder,
    error = StudioDarkError
)

private val GuberLightColorScheme = lightColorScheme(
    primary = StudioLightPrimary,
    onPrimary = StudioLightSurface,
    primaryContainer = StudioLightPrimary.copy(alpha = 0.14f),
    onPrimaryContainer = StudioLightPrimary,
    secondary = StudioLightSecondary,
    onSecondary = StudioLightSurface,
    secondaryContainer = StudioLightSecondary.copy(alpha = 0.14f),
    onSecondaryContainer = StudioLightSecondary,
    tertiary = StudioLightTertiary,
    onTertiary = StudioLightSurface,
    tertiaryContainer = StudioLightTertiary.copy(alpha = 0.14f),
    onTertiaryContainer = StudioLightTertiary,
    background = StudioLightBg,
    onBackground = StudioLightOnBg,
    surface = StudioLightSurface,
    onSurface = StudioLightOnBg,
    surfaceVariant = StudioLightSurfaceVariant,
    onSurfaceVariant = StudioLightOnSurfaceVariant,
    outline = StudioLightBorder,
    error = StudioLightError
)

val GuberShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

val LocalEditorSyntaxPalette = staticCompositionLocalOf { DarkEditorSyntaxPalette }

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GuberDarkColorScheme else GuberLightColorScheme
    val syntaxPalette = if (darkTheme) DarkEditorSyntaxPalette else LightEditorSyntaxPalette

    CompositionLocalProvider(LocalEditorSyntaxPalette provides syntaxPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = GuberShapes,
            content = content
        )
    }
}
