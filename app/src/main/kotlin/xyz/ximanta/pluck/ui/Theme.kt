package xyz.ximanta.pluck.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pluck brand palette — mirrors the landing page's Tailwind tokens.
 * Screens can reference these directly when they need a specific shade,
 * or use MaterialTheme.colorScheme for the mapped values.
 */
object PluckColors {
    val Slate950 = Color(0xFF020617)
    val Slate900 = Color(0xFF0F172A)
    val Slate800 = Color(0xFF1E293B)
    val Slate700 = Color(0xFF334155)
    val Slate600 = Color(0xFF475569)
    val Slate500 = Color(0xFF64748B)
    val Slate400 = Color(0xFF94A3B8)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate100 = Color(0xFFF1F5F9)

    val Emerald600 = Color(0xFF059669)
    val Emerald500 = Color(0xFF10B981)
    val Emerald400 = Color(0xFF34D399)
    val Emerald100 = Color(0xFFD1FAE5)

    val Red500 = Color(0xFFEF4444)
    val Red400 = Color(0xFFF87171)
    val Red900 = Color(0xFF7F1D1D)

    val Amber400 = Color(0xFFFBBF24)
    val Blue400 = Color(0xFF60A5FA)
}

private val PluckDarkScheme = darkColorScheme(
    primary = PluckColors.Emerald500,
    onPrimary = PluckColors.Slate950,
    primaryContainer = PluckColors.Emerald600,
    onPrimaryContainer = PluckColors.Slate950,

    secondary = PluckColors.Emerald400,
    onSecondary = PluckColors.Slate950,

    tertiary = PluckColors.Blue400,
    onTertiary = PluckColors.Slate950,

    background = PluckColors.Slate950,
    onBackground = PluckColors.Slate100,

    surface = PluckColors.Slate900,
    onSurface = PluckColors.Slate100,

    surfaceVariant = PluckColors.Slate800,
    onSurfaceVariant = PluckColors.Slate400,

    outline = PluckColors.Slate700,
    outlineVariant = PluckColors.Slate800,

    error = PluckColors.Red400,
    onError = PluckColors.Slate950,
    errorContainer = PluckColors.Red900,
    onErrorContainer = PluckColors.Red400
)

private val PluckTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

private val PluckShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PluckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PluckDarkScheme,
        typography = PluckTypography,
        shapes = PluckShapes,
        content = content
    )
}
