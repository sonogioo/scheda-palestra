package com.scheda.palestra

import android.graphics.Typeface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- Colori base

/** Blu inchiostro al posto del nero piatto: dà profondità e fa risaltare i colori dei gruppi. */
val Ink = Color(0xFF0B1020)
val InkTop = Color(0xFF131B3A)
val Surface2 = Color(0xFF18203F)
val Line = Color(0xFF2B3563)
val SoftText = Color(0xFFB4BEE4)

val Background = Brush.verticalGradient(listOf(InkTop, Ink))

// ---------------------------------------------------------------- Colore per gruppo muscolare
// Ogni gruppo ha un gradiente chiaro e saturo; il testo sopra è sempre Ink (contrasto alto).

data class Palette(val start: Color, val end: Color) {
    val brush get() = Brush.linearGradient(listOf(start, end))
    val solid get() = start
}

private val Petto = Palette(Color(0xFFFF7A7A), Color(0xFFFFB86B))
private val Schiena = Palette(Color(0xFF5EC8FF), Color(0xFF7CF5E0))
private val Spalle = Palette(Color(0xFFFFB547), Color(0xFFFFE066))
private val Bicipiti = Palette(Color(0xFFC79BFF), Color(0xFFFF9BDE))
private val Tricipiti = Palette(Color(0xFF8FA8FF), Color(0xFFC6A6FF))
private val Gambe = Palette(Color(0xFF5EE6A0), Color(0xFFC8F560))
private val Addome = Palette(Color(0xFFFF9BB0), Color(0xFFFFD1A1))
val DefaultPalette = Palette(Color(0xFFC6F432), Color(0xFF7CF5E0))

fun paletteFor(group: String?): Palette {
    val g = group?.lowercase() ?: return DefaultPalette
    return when {
        "pett" in g || "chest" in g -> Petto
        "schien" in g || "dors" in g || "back" in g -> Schiena
        "spall" in g || "should" in g -> Spalle
        "bicip" in g || "bicep" in g -> Bicipiti
        "tricip" in g || "tricep" in g -> Tricipiti
        "gamb" in g || "leg" in g || "glut" in g || "polpacc" in g -> Gambe
        "addom" in g || "core" in g || "abs" in g -> Addome
        else -> DefaultPalette
    }
}

/** Colore dell'allenamento = colore del primo gruppo muscolare. */
fun paletteFor(w: Workout) = paletteFor(w.exercises.firstOrNull()?.group)

// ---------------------------------------------------------------- Tipografia e forme

/** Condensato e pesante per i titoli: più "sportivo" del Roboto standard. */
private val Display = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

val SchedaColors = darkColorScheme(
    primary = DefaultPalette.start,
    onPrimary = Ink,
    background = Ink,
    onBackground = Color.White,
    surface = Ink,
    onSurface = Color.White,
    surfaceVariant = Surface2,
    onSurfaceVariant = SoftText,
    outline = Line,
)

val SchedaType = Typography(
    displayMedium = TextStyle(fontFamily = Display, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontFamily = Display, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = Display, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
)

val SchedaShapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
)
