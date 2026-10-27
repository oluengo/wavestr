package io.github.oluengo.radiostr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paleta de la demo HTML (verde azulado + rojo de directo + ámbar de zap). */
@Immutable
data class RsColors(
    val live: Color,
    val zap: Color,
    val zapSoft: Color,
    val muted: Color,
    val line: Color,
    val accentSoft: Color,
)

private val LightRs = RsColors(
    live = Color(0xFFE5483B), zap = Color(0xFFA86A00), zapSoft = Color(0xFFFDF1DC),
    muted = Color(0xFF61706B), line = Color(0xFFE1E7E4), accentSoft = Color(0xFFDCEFEA),
)
private val DarkRs = RsColors(
    live = Color(0xFFFF6B5E), zap = Color(0xFFF5B342), zapSoft = Color(0xFF33281A),
    muted = Color(0xFF93A39D), line = Color(0xFF2A3531), accentSoft = Color(0xFF1D332E),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E7C6B), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEFEA), onPrimaryContainer = Color(0xFF0B5E52),
    secondary = Color(0xFF17201D), onSecondary = Color(0xFFF3F5F4),
    background = Color(0xFFF3F5F4), onBackground = Color(0xFF17201D),
    surface = Color(0xFFF3F5F4), onSurface = Color(0xFF17201D),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE7ECEA),
    onSurfaceVariant = Color(0xFF61706B), outline = Color(0xFFC9D3CF), outlineVariant = Color(0xFFE1E7E4),
    error = Color(0xFFE5483B),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3FBFA6), onPrimary = Color(0xFF00201A),
    primaryContainer = Color(0xFF1D332E), onPrimaryContainer = Color(0xFF9DE3D3),
    secondary = Color(0xFFE8EEEB), onSecondary = Color(0xFF111715),
    background = Color(0xFF111715), onBackground = Color(0xFFE8EEEB),
    surface = Color(0xFF111715), onSurface = Color(0xFFE8EEEB),
    surfaceContainer = Color(0xFF19211E), surfaceContainerHigh = Color(0xFF1F2925),
    surfaceContainerLow = Color(0xFF19211E), surfaceVariant = Color(0xFF242E2A),
    onSurfaceVariant = Color(0xFF93A39D), outline = Color(0xFF3A4742), outlineVariant = Color(0xFF2A3531),
    error = Color(0xFFFF6B5E),
)

val LocalRsColors = staticCompositionLocalOf { LightRs }

private val RsTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp),
    )
}

/** El modo claro/oscuro lo fija AppCompat (setDefaultNightMode) según el ajuste; aquí solo se lee. */
@Composable
fun RadiostrTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalRsColors provides if (dark) DarkRs else LightRs) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = RsTypography, content = content)
    }
}

object Rs {
    val colors: RsColors @Composable get() = LocalRsColors.current
}

@Suppress("unused")
val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
