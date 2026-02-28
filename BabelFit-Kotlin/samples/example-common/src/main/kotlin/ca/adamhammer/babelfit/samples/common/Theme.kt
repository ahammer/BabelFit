package ca.adamhammer.babelfit.samples.common

import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.graphics.Color

// ── Generic Dark Palette ────────────────────────────────────────────────────

val DarkSurface = Color(0xFF1E1E2E)
val DarkCardColor = Color(0xFF2A2A3D)
val DarkCardAlt = Color(0xFF252538)
val DimText = Color(0xFFB0B0C0)
val BrightText = Color(0xFFE0E0F0)
val ErrorColor = Color(0xFFEF5350)
val SuccessColor = Color(0xFF66BB6A)

// ── Theme Factory ───────────────────────────────────────────────────────────

fun babelFitDarkColors(
    primary: Color = Color(0xFF7E57C2),
    primaryVariant: Color = primary,
    secondary: Color = Color(0xFF26A69A)
) = darkColors(
    primary = primary,
    primaryVariant = primaryVariant,
    secondary = secondary,
    background = DarkSurface,
    surface = DarkCardColor,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = BrightText,
    onSurface = BrightText,
    error = ErrorColor
)

// ── Text Field Colors ───────────────────────────────────────────────────────

@Composable
fun darkTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = BrightText,
    cursorColor = MaterialTheme.colors.primary,
    focusedBorderColor = MaterialTheme.colors.primary,
    unfocusedBorderColor = DimText.copy(alpha = 0.3f),
    focusedLabelColor = MaterialTheme.colors.primary,
    unfocusedLabelColor = DimText.copy(alpha = 0.5f)
)
