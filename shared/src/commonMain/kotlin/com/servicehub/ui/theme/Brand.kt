package com.servicehub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand colours. Change them here and the whole app follows. */
object Brand {
    val Ink = Color(0xFF0A101C)          // app background
    val Surface = Color(0xFF111A2B)      // cards
    val SurfaceHigh = Color(0xFF18233A)  // raised / selected
    val Line = Color(0xFF2A3753)         // borders
    val Gold = Color(0xFFD9B44A)         // primary accent
    val GoldDark = Color(0xFF1B1503)     // text on gold
    val Text = Color(0xFFEAEFF8)
    val Muted = Color(0xFF9DABC6)
    val Good = Color(0xFF4CC38A)
    val Warn = Color(0xFFF2B84B)
    val Bad = Color(0xFFFF6B6B)
}

private val ServiceHubColors = darkColorScheme(
    primary = Brand.Gold,
    onPrimary = Brand.GoldDark,
    primaryContainer = Brand.SurfaceHigh,
    onPrimaryContainer = Brand.Gold,
    secondary = Brand.Good,
    onSecondary = Brand.Ink,
    background = Brand.Ink,
    onBackground = Brand.Text,
    surface = Brand.Surface,
    onSurface = Brand.Text,
    surfaceVariant = Brand.SurfaceHigh,
    onSurfaceVariant = Brand.Muted,
    outline = Brand.Line,
    outlineVariant = Brand.Line,
    error = Brand.Bad,
    onError = Brand.Ink
)

private val ServiceHubTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val ServiceHubShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Wrap the whole app in this instead of a plain MaterialTheme. */
@Composable
fun ServiceHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ServiceHubColors,
        typography = ServiceHubTypography,
        shapes = ServiceHubShapes,
        content = content
    )
}
