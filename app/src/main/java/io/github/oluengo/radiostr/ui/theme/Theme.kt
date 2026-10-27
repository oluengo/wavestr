package io.github.oluengo.radiostr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import io.github.oluengo.radiostr.data.Accent
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

/** Acento: (principal, suave, texto sobre suave) en claro y en oscuro. */
private data class AccentColors(val light: Color, val lightSoft: Color, val lightOn: Color, val dark: Color, val darkSoft: Color, val darkOn: Color)

private fun accentColors(a: Accent) = when (a) {
    Accent.VIOLET -> AccentColors(Color(0xFF0E7C6B), Color(0xFFDCEFEA), Color(0xFF0B5E52), Color(0xFF3FBFA6), Color(0xFF1D332E), Color(0xFF9DE3D3))
    Accent.VIOLET -> AccentColors(Color(0xFF6D28D9), Color(0xFFEDE6FB), Color(0xFF4C1D95), Color(0xFFA78BFA), Color(0xFF2A2142), Color(0xFFDDD0FF))
    Accent.CORAL -> AccentColors(Color(0xFFD9480F), Color(0xFFFDE9DF), Color(0xFF9A3412), Color(0xFFFF8A5B), Color(0xFF3A2318), Color(0xFFFFCDB8))
    Accent.BLUE -> AccentColors(Color(0xFF1D4ED8), Color(0xFFE1E9FC), Color(0xFF1E3A8A), Color(0xFF7EA6FF), Color(0xFF1B2542), Color(0xFFCCDAFF))
    Accent.RASPBERRY -> AccentColors(Color(0xFFBE185D), Color(0xFFFBE4EE), Color(0xFF831843), Color(0xFFF472B6), Color(0xFF3B1A2B), Color(0xFFFFC9E2))
}

/** El modo claro/oscuro lo fija AppCompat (setDefaultNightMode) según el ajuste; aquí solo se lee. */
@Composable
fun RadiostrTheme(accent: Accent = Accent.VIOLET, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val a = accentColors(accent)
    val scheme = if (dark) {
        DarkScheme.copy(primary = a.dark, onPrimary = Color(0xFF0B0F0E), primaryContainer = a.darkSoft, onPrimaryContainer = a.darkOn)
    } else {
        LightScheme.copy(primary = a.light, onPrimary = Color.White, primaryContainer = a.lightSoft, onPrimaryContainer = a.lightOn)
    }
    val rs = (if (dark) DarkRs else LightRs).copy(accentSoft = if (dark) a.darkSoft else a.lightSoft)
    androidx.compose.runtime.CompositionLocalProvider(LocalRsColors provides rs) {
        MaterialTheme(colorScheme = scheme, typography = RsTypography, content = content)
    }
}

object Rs {
    val colors: RsColors @Composable get() = LocalRsColors.current
}

@Suppress("unused")
val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
