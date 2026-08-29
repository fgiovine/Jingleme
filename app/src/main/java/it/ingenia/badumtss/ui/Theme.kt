package it.ingenia.badumtss.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Il riferimento visivo è il retro di un club: velluto scuro, lampadine calde
 * dell'insegna e le serigrafie bianche sulle manopole del mixer.
 */
object Club {
    val Velvet = Color(0xFF140A0C)
    val Curtain = Color(0xFF241114)
    val Riser = Color(0xFF32191D)
    val Bulb = Color(0xFFFFB02E)
    val BulbOff = Color(0xFF5A3A22)
    val Marquee = Color(0xFFFFF0D4)
    val Dim = Color(0xFF9B6B58)
    val Hot = Color(0xFFE23E3E)
    val Cool = Color(0xFF6FBF73)
}

/** Maiuscoletto spaziato: le etichette incise sui pannelli. */
val Stencil = TextStyle(
    fontWeight = FontWeight.Black,
    fontSize = 11.sp,
    letterSpacing = 3.sp,
    color = Club.Dim
)

val Marquee = TextStyle(
    fontWeight = FontWeight.Black,
    fontSize = 30.sp,
    letterSpacing = 6.sp,
    color = Club.Marquee
)

/** I numeri stanno in monospazio, come sui display dell'attrezzatura. */
val Readout = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    color = Club.Bulb
)

@Composable
fun BaDumTssTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Club.Bulb,
            onPrimary = Club.Velvet,
            background = Club.Velvet,
            onBackground = Club.Marquee,
            surface = Club.Curtain,
            onSurface = Club.Marquee,
            surfaceVariant = Club.Riser,
            outline = Club.Dim
        ),
        content = content
    )
}
