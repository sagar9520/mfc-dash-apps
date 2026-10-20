package com.myfuturecode.mfcdash.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// MFC brand: site ke CSS (--brand-1/2/3) se, logo ke gradient wale rang.
val Brand1 = Color(0xFF9C02BA)   // magenta
val Brand2 = Color(0xFF4A1FC4)   // violet (primary)
val Brand3 = Color(0xFF0B1F9E)   // deep blue
val Ink = Color(0xFF15132B)
val InkDark = Color(0xFF140F33)
val Good = Color(0xFF12B76A)
val Bad = Color(0xFFE11D48)
val Warn = Color(0xFFF59E0B)
val Info = Color(0xFF2563EB)

val BrandGradient = Brush.linearGradient(listOf(Brand1, Brand2, Brand3))
val HeroGradient = Brush.linearGradient(listOf(Color(0xFF1B0F45), InkDark, Color(0xFF0D0C2A)))

data class Extra(val good: Color, val bad: Color, val warn: Color, val muted: Color, val line: Color, val field: Color, val heat: List<Color>)
val LocalExtra = staticCompositionLocalOf { lightExtra }

private val lightExtra = Extra(Good, Bad, Warn, Color(0xFF6B6A80), Color(0xFFE3E1EE), Color(0xFFF8F7FC),
    heat = listOf(Color(0xFFFDE2E7), Color(0xFFFEF0C7), Color(0xFFD1FADF), Color(0xFFA6F4C5)))
private val darkExtra = Extra(Color(0xFF3EE68A), Color(0xFFFF7D93), Color(0xFFFFC14D), Color(0xFFA3A0BD), Color(0xFF2A2647), Color(0xFF1B1834),
    heat = listOf(Color(0xFF4A1A26), Color(0xFF4A3A14), Color(0xFF14402C), Color(0xFF1A5A3A)))

private val light: ColorScheme = lightColorScheme(
    primary = Brand2, onPrimary = Color.White, primaryContainer = Color(0xFFE9E3FB), onPrimaryContainer = Brand3,
    secondary = Brand1, tertiary = Brand3,
    background = Color(0xFFF3F2F8), onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFF8F7FC), onSurfaceVariant = Color(0xFF6B6A80), outline = Color(0xFFE3E1EE),
    error = Bad,
)
private val dark: ColorScheme = darkColorScheme(
    primary = Color(0xFF9F8BFF), onPrimary = Color(0xFF1B0F45), primaryContainer = Color(0xFF2E2063), onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = Color(0xFFE7A6FF), tertiary = Color(0xFF8FB0FF),
    background = Color(0xFF0C0A1C), onBackground = Color(0xFFEFEDFB), surface = Color(0xFF151229), onSurface = Color(0xFFEFEDFB),
    surfaceVariant = Color(0xFF1B1834), onSurfaceVariant = Color(0xFFA3A0BD), outline = Color(0xFF2A2647),
    error = Color(0xFFFF7D93),
)

val MfcType = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.5.sp, letterSpacing = 0.4.sp),
)

@Composable
fun MfcTheme(content: @Composable () -> Unit) {
    val darkMode = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides if (darkMode) darkExtra else lightExtra) {
        MaterialTheme(colorScheme = if (darkMode) dark else light, typography = MfcType, content = content)
    }
}

val MaterialTheme.extra: Extra @Composable get() = LocalExtra.current

fun parseHex(s: String, fallback: Color = Brand2): Color = runCatching {
    Color(android.graphics.Color.parseColor(if (s.startsWith("#")) s else "#$s"))
}.getOrDefault(fallback)
