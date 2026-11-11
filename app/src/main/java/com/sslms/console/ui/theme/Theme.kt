package com.sslms.console.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sslms.console.R

// "Sodium night": indigo night sky, sodium-lamp amber for lit, teal healthy, red faults.
object C {
    val Night = Color(0xFF161C36)
    val Night2 = Color(0xFF1A2142)
    val Panel = Color(0xFF1F274A)
    val Panel2 = Color(0xFF252E57)
    val Line = Color(0xFF2E3866)
    val Line2 = Color(0xFF3B4677)
    val Text = Color(0xFFE8EAF6)
    val Muted = Color(0xFF9AA3C7)
    val Faint = Color(0xFF6C76A3)
    val Sodium = Color(0xFFFFB547)
    val OnSodium = Color(0xFF2A1A00)
    val Teal = Color(0xFF4FD1C5)
    val Red = Color(0xFFFF6B6B)
    val Warn = Color(0xFFF5C451)
    val Blue = Color(0xFF7FA8FF)
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

/** Condensed face for readings: volts, watts, counts. */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
)

private val base = TextStyle(fontFamily = Barlow)

private val SslmsType = Typography(
    headlineMedium = base.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.copy(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = base.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp, color = C.Muted),
    labelLarge = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = C.Muted),
    labelSmall = base.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, color = C.Muted),
)

object Readout {
    val Big = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, lineHeight = 40.sp, color = C.Text)
    val Mid = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 32.sp, color = C.Text)
    val Small = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 20.sp, color = C.Text)
    val Unit = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = C.Muted)
}

@Composable
fun SslmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Sodium, onPrimary = C.OnSodium,
            secondary = C.Teal, onSecondary = C.Night,
            tertiary = C.Blue,
            background = C.Night, onBackground = C.Text,
            surface = C.Night, onSurface = C.Text,
            surfaceVariant = C.Panel, onSurfaceVariant = C.Muted,
            surfaceContainerLowest = C.Night, surfaceContainerLow = C.Night2,
            surfaceContainer = C.Panel, surfaceContainerHigh = C.Panel2, surfaceContainerHighest = C.Panel2,
            outline = C.Line2, outlineVariant = C.Line,
            error = C.Red, onError = Color(0xFF2B0606),
            secondaryContainer = C.Panel2, onSecondaryContainer = C.Text,
            inverseSurface = C.Text, inverseOnSurface = C.Night,
        ),
        typography = SslmsType,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
