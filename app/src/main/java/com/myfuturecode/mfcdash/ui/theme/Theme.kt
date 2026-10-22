package com.myfuturecode.mfcdash.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// MFC brand: site ke CSS (--brand-1/2/3) se, logo ke gradient wale rang.
val Brand1 = Color(0xFF9C02BA)   // magenta
val Brand2 = Color(0xFF4A1FC4)   // violet (primary)
val Brand3 = Color(0xFF0B1F9E)   // deep blue
val Ink = Color(0xFF17161D)
val InkDark = Color(0xFF140F33)
val Good = Color(0xFF0F7136)
val Bad = Color(0xFFB4221A)
val Warn = Color(0xFF8A4F00)
val Info = Color(0xFF2563EB)

val BrandGradient: Brush = Brush.linearGradient(listOf(Brand1, Brand2, Brand3))
val HeroGradient: Brush = Brush.linearGradient(listOf(Color(0xFF1B0F45), InkDark, Color(0xFF0D0C2A)))

/** Teen roop: Settings se badalta hai (Prefs.theme). */
enum class ThemeStyle(val key: String) {
    LIGHT("light"), DARK("dark"), BRAND("brand");

    companion object {
        fun of(key: String?): ThemeStyle = entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

/** kit.css ke saare tokens (camelCase). Ek set = ek theme (ya brand ka gradient top). */
@Immutable
data class MfcColors(
    val page: Color, val card: Color, val cardBd: Color, val well: Color,
    val ink: Color, val ink2: Color, val ink3: Color, val line: Color, val chev: Color, val tick: Color,
    val brand: Color, val brandSoft: Color, val btnBg: Color, val btnFg: Color,
    val green: Color, val greenSoft: Color, val amber: Color, val amberSoft: Color, val red: Color, val redSoft: Color,
    val zRed: Color, val zAmber: Color, val zGreen: Color, val zGrey: Color, val zTrack: Color,
    val markOk: Color, val markWarn: Color, val markBad: Color,
    val live: Color, val liveRing: Color, val dotRing: Color, val badge: Color,
    val heroA: Color, val heroB: Color, val heroBd: Color, val heroLine: Color,
    val roseA: Color, val roseB: Color, val roseBd: Color,
    val sandA: Color, val sandB: Color, val sandBd: Color,
    val lavA: Color, val lavB: Color, val lavBd: Color,
    val step: Color, val navBg: Color, val swOff: Color,
    val pNBg: Color, val pNFg: Color, val pOkBg: Color, val pOkFg: Color, val pWarnBg: Color, val pWarnFg: Color,
    val pBadBg: Color, val pBadFg: Color, val pAskBg: Color, val pAskFg: Color,
    /** av1..av6: (tile bg, letters). */
    val avatars: List<Pair<Color, Color>>,
    val isDark: Boolean,
    /** Sirf brand ke gradient top (aur gradient hero) wale set me true. */
    val onBrand: Boolean,
    /** Badge ke charon taraf ki ring. */
    val badgeRing: Color,
    /** Seg tabs: chuna hua tab (bg, text). */
    val tabOn: Color, val tabOnFg: Color,
)

private val avLight = listOf(
    Color(0xFFEEE9FD) to Color(0xFF4A2BB8), Color(0xFFDFF4EF) to Color(0xFF0B6650), Color(0xFFFFEBD9) to Color(0xFF8F3F00),
    Color(0xFFE3EEFE) to Color(0xFF1D4FB0), Color(0xFFFCE6F1) to Color(0xFFA1145A), Color(0xFFE9F6DF) to Color(0xFF3B6B10),
)
private val avDark = listOf(
    Color(0xFF2A2352) to Color(0xFFC9BCFF), Color(0xFF123229) to Color(0xFF7FE0C3), Color(0xFF38250F) to Color(0xFFFFBE85),
    Color(0xFF16294A) to Color(0xFF9CC2FF), Color(0xFF3A1729) to Color(0xFFFF9FCB), Color(0xFF1D3112) to Color(0xFFB2E58C),
)

private val LightTokens = MfcColors(
    page = Color(0xFFF4F3F8), card = Color.White, cardBd = Color.Transparent, well = Color(0xFFF4F3F8),
    ink = Color(0xFF17161D), ink2 = Color(0xFF55535F), ink3 = Color(0xFF6A6876), line = Color(0xFFECEAF1),
    chev = Color(0xFF8E8B99), tick = Color(0xFFB3B0BD),
    brand = Color(0xFF5A34D6), brandSoft = Color(0xFFF0ECFD), btnBg = Color(0xFF5A34D6), btnFg = Color.White,
    green = Color(0xFF0F7136), greenSoft = Color(0xFFE3F3E9), amber = Color(0xFF8A4F00), amberSoft = Color(0xFFFFF0D4),
    red = Color(0xFFB4221A), redSoft = Color(0xFFFDE9E7),
    zRed = Color(0xFFE04A3F), zAmber = Color(0xFFEFA21B), zGreen = Color(0xFF1E9E52), zGrey = Color(0xFF8E8B99), zTrack = Color(0xFFECEAF1),
    markOk = Color(0xFF0F7136), markWarn = Color(0xFF8A4F00), markBad = Color(0xFFB4221A),
    live = Color(0xFF1E9E52), liveRing = Color(0xFFCDEBD8), dotRing = Color.White, badge = Color(0xFFB4221A),
    heroA = Color(0xFFE4F4EA), heroB = Color(0xFFF3FAF5), heroBd = Color(0xFFD9EEE0), heroLine = Color(0xFFE3EEE6),
    roseA = Color(0xFFFCE9E6), roseB = Color(0xFFFEF5F4), roseBd = Color(0xFFF5DAD6),
    sandA = Color(0xFFFFF0D6), sandB = Color(0xFFFFF8EC), sandBd = Color(0xFFF3E2C2),
    lavA = Color(0xFFEEE9FD), lavB = Color(0xFFF7F4FE), lavBd = Color(0xFFE2DAF9),
    step = Color(0xFFD6CCFA), navBg = Color.White, swOff = Color(0xFF8E8B99),
    pNBg = Color(0xFFECEAF1), pNFg = Color(0xFF55535F),
    pOkBg = Color(0xFFE3F3E9), pOkFg = Color(0xFF0F7136), pWarnBg = Color(0xFFFFF0D4), pWarnFg = Color(0xFF8A4F00),
    pBadBg = Color(0xFFFDE9E7), pBadFg = Color(0xFFB4221A), pAskBg = Color(0xFFF0ECFD), pAskFg = Color(0xFF5A34D6),
    avatars = avLight, isDark = false, onBrand = false,
    badgeRing = Color(0xFFF4F3F8), tabOn = Color.White, tabOnFg = Color(0xFF17161D),
)

// dark: tick aur step mockup ke override (3:1 contrast)
private val DarkTokens = MfcColors(
    page = Color(0xFF0F0D1A), card = Color(0xFF1A1728), cardBd = Color(0xFF262238), well = Color(0xFF13111F),
    ink = Color(0xFFF3F1FA), ink2 = Color(0xFFC4BFD4), ink3 = Color(0xFFA29DB5), line = Color(0xFF2A2640),
    chev = Color(0xFF8F8AA4), tick = Color(0xFF7D7799),
    brand = Color(0xFFAE9BFF), brandSoft = Color(0xFF2A2352), btnBg = Color(0xFF6A48E8), btnFg = Color.White,
    green = Color(0xFF5BD68A), greenSoft = Color(0xFF15301F), amber = Color(0xFFF2B54A), amberSoft = Color(0xFF33270F),
    red = Color(0xFFFF8A80), redSoft = Color(0xFF3A1A1E),
    zRed = Color(0xFFF0574C), zAmber = Color(0xFFEDA632), zGreen = Color(0xFF2FBF68), zGrey = Color(0xFF6F6A86), zTrack = Color(0xFF2A2640),
    markOk = Color(0xFF4CC97E), markWarn = Color(0xFFF2B54A), markBad = Color(0xFFFF7B70),
    live = Color(0xFF3FD07A), liveRing = Color(0xFF1D4430), dotRing = Color(0xFF1A1728), badge = Color(0xFFD0352B),
    heroA = Color(0xFF14301F), heroB = Color(0xFF172620), heroBd = Color(0xFF23402F), heroLine = Color(0xFF25392E),
    roseA = Color(0xFF2F1A23), roseB = Color(0xFF251925), roseBd = Color(0xFF3E242D),
    sandA = Color(0xFF2D2414), sandB = Color(0xFF242020), sandBd = Color(0xFF3C321F),
    lavA = Color(0xFF261F4D), lavB = Color(0xFF201B39), lavBd = Color(0xFF352B66),
    step = Color(0xFF6C5FBE), navBg = Color(0xFF15121F), swOff = Color(0xFF77728C),
    pNBg = Color(0xFF2A2640), pNFg = Color(0xFFC4BFD4),
    pOkBg = Color(0xFF15301F), pOkFg = Color(0xFF5BD68A), pWarnBg = Color(0xFF33270F), pWarnFg = Color(0xFFF2B54A),
    pBadBg = Color(0xFF3A1A1E), pBadFg = Color(0xFFFF8A80), pAskBg = Color(0xFF2A2352), pAskFg = Color(0xFFAE9BFF),
    avatars = avDark, isDark = true, onBrand = false,
    badgeRing = Color(0xFF0F0D1A), tabOn = Color(0xFF2E2A45), tabOnFg = Color(0xFFF3F1FA),
)

private fun w(a: Float) = Color.White.copy(alpha = a)

// .t-brand .top / .hero: gradient par safed text aur safed pills
private val BrandTopTokens = LightTokens.copy(
    ink = Color.White, ink2 = w(.90f), ink3 = w(.84f), line = w(.22f), heroLine = w(.22f), chev = w(.85f), tick = w(.55f),
    brand = Color.White, green = Color(0xFFA8F5C4), amber = Color(0xFFFFD889), red = Color(0xFFFFCEC9),
    zRed = Color(0xFFFF6F62), zAmber = Color(0xFFFFC043), zGreen = Color(0xFF3EE07F), zGrey = w(.55f), zTrack = w(.22f),
    markOk = Color(0xFFA8F5C4), markWarn = Color(0xFFFFD889), markBad = Color(0xFFFFCEC9),
    live = Color(0xFF7CF0A8), liveRing = Color(0x477CF0A8), dotRing = Color.White, badge = Color(0xFFD0352B),
    step = w(.3f), well = w(.14f),
    pOkBg = Color.White, pOkFg = Color(0xFF0F7136), pWarnBg = Color.White, pWarnFg = Color(0xFF8A4F00),
    pBadBg = Color.White, pBadFg = Color(0xFFB4221A), pAskBg = Color.White, pAskFg = Color(0xFF5A34D6),
    pNBg = w(.18f), pNFg = Color.White,
    btnBg = Color.White, btnFg = Color(0xFF4A1FC4),
    onBrand = true, badgeRing = Color.White, tabOn = Color.White, tabOnFg = Color(0xFF4A1FC4),
)

/** Theme ke tokens (widget waghera ke liye bhi). top = brand ka gradient band. */
fun mfcColors(style: ThemeStyle, top: Boolean = false): MfcColors = when (style) {
    ThemeStyle.LIGHT -> LightTokens
    ThemeStyle.DARK -> DarkTokens
    ThemeStyle.BRAND -> if (top) BrandTopTokens else LightTokens
}

val LocalMfc = staticCompositionLocalOf { LightTokens }
val LocalThemeStyle = staticCompositionLocalOf { ThemeStyle.LIGHT }

/** CSS linear-gradient(angle) ki tarah: kisi bhi size par wahi angle. extraTop = upar ka chhupa hissa (status bar). */
class MfcAngleGradient(
    private val angleDeg: Float,
    private val stops: List<Pair<Float, Color>>,
    private val extraTop: Float = 0f,
    private val extraBottom: Float = 0f,
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val h = size.height + extraTop + extraBottom
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = sin(rad).toFloat()
        val dy = -cos(rad).toFloat()
        val len = abs(size.width * dx) + abs(h * dy)
        val cx = size.width / 2f
        val cy = h / 2f - extraTop
        val from = Offset(cx - dx * len / 2f, cy - dy * len / 2f)
        val to = Offset(cx + dx * len / 2f, cy + dy * len / 2f)
        return LinearGradientShader(from, to, stops.map { it.second }, stops.map { it.first })
    }

    override fun equals(other: Any?): Boolean = other is MfcAngleGradient && other.angleDeg == angleDeg &&
        other.stops == stops && other.extraTop == extraTop && other.extraBottom == extraBottom

    override fun hashCode(): Int = ((angleDeg.hashCode() * 31 + stops.hashCode()) * 31 + extraTop.hashCode()) * 31 + extraBottom.hashCode()
}

internal val BrandStops = listOf(0f to Brand1, .52f to Brand2, 1f to Brand3)

object Mfc {
    val c: MfcColors
        @Composable @ReadOnlyComposable get() = LocalMfc.current
    val style: ThemeStyle
        @Composable @ReadOnlyComposable get() = LocalThemeStyle.current
    /** Brand gradient, 165deg: #9C02BA 0%, #4A1FC4 52%, #0B1F9E 100%. */
    val gradient: Brush = MfcAngleGradient(165f, BrandStops)
}

data class Extra(val good: Color, val bad: Color, val warn: Color, val muted: Color, val line: Color, val field: Color, val heat: List<Color>)

private fun extraOf(c: MfcColors) = Extra(
    good = c.green, bad = c.red, warn = c.amber, muted = c.ink3, line = c.line, field = c.well,
    heat = listOf(c.redSoft, c.amberSoft, c.greenSoft, lerp(c.greenSoft, c.zGreen, .35f)),
)

val LocalExtra = staticCompositionLocalOf { extraOf(LightTokens) }

private val BrandTopExtra = extraOf(BrandTopTokens)

private fun schemeOf(c: MfcColors): ColorScheme = if (c.isDark) darkColorScheme(
    primary = c.btnBg, onPrimary = c.btnFg, primaryContainer = c.brandSoft, onPrimaryContainer = c.brand,
    inversePrimary = Color(0xFF5A34D6),
    secondary = c.brand, onSecondary = Color(0xFF17161D), secondaryContainer = c.brandSoft, onSecondaryContainer = c.brand,
    tertiary = c.green, onTertiary = Color(0xFF0F0D1A), tertiaryContainer = c.greenSoft, onTertiaryContainer = c.green,
    background = c.page, onBackground = c.ink, surface = c.card, onSurface = c.ink,
    surfaceVariant = c.well, onSurfaceVariant = c.ink3, surfaceTint = c.brand,
    inverseSurface = c.ink, inverseOnSurface = c.page,
    error = c.red, onError = Color(0xFF0F0D1A), errorContainer = c.redSoft, onErrorContainer = c.red,
    outline = c.line, outlineVariant = c.line, scrim = Color.Black,
    surfaceBright = c.card, surfaceDim = c.page,
    surfaceContainer = c.card, surfaceContainerHigh = c.well, surfaceContainerHighest = c.well,
    surfaceContainerLow = c.card, surfaceContainerLowest = c.card,
) else lightColorScheme(
    primary = c.btnBg, onPrimary = c.btnFg, primaryContainer = c.brandSoft, onPrimaryContainer = c.brand,
    inversePrimary = Color(0xFFAE9BFF),
    secondary = c.brand, onSecondary = Color.White, secondaryContainer = c.brandSoft, onSecondaryContainer = c.brand,
    tertiary = c.green, onTertiary = Color.White, tertiaryContainer = c.greenSoft, onTertiaryContainer = c.green,
    background = c.page, onBackground = c.ink, surface = c.card, onSurface = c.ink,
    surfaceVariant = c.well, onSurfaceVariant = c.ink3, surfaceTint = c.brand,
    inverseSurface = c.ink, inverseOnSurface = c.page,
    error = c.red, onError = Color.White, errorContainer = c.redSoft, onErrorContainer = c.red,
    outline = c.line, outlineVariant = c.line, scrim = Color.Black,
    surfaceBright = c.card, surfaceDim = c.page,
    surfaceContainer = c.card, surfaceContainerHigh = c.well, surfaceContainerHighest = c.well,
    surfaceContainerLow = c.card, surfaceContainerLowest = c.card,
)

private const val TNUM = "tnum"

// Sab text me tabular numbers (figures ek line me seedhe rahein)
val MfcType = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp, fontFeatureSettings = TNUM),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.3).sp, fontFeatureSettings = TNUM),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, fontFeatureSettings = TNUM),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, fontFeatureSettings = TNUM),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, fontFeatureSettings = TNUM),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFeatureSettings = TNUM),
    bodyLarge = TextStyle(fontSize = 15.sp, fontFeatureSettings = TNUM),
    bodyMedium = TextStyle(fontSize = 13.sp, fontFeatureSettings = TNUM),
    bodySmall = TextStyle(fontSize = 12.sp, fontFeatureSettings = TNUM),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFeatureSettings = TNUM),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFeatureSettings = TNUM),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.2.sp, fontFeatureSettings = TNUM),
)

@Composable
fun MfcTheme(style: ThemeStyle = ThemeStyle.LIGHT, content: @Composable () -> Unit) {
    val c = mfcColors(style)
    val extra = remember(c) { extraOf(c) }
    val scheme = remember(c) { schemeOf(c) }
    CompositionLocalProvider(
        LocalMfc provides c,
        LocalThemeStyle provides style,
        LocalExtra provides extra,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MfcType, content = content)
    }
}

/** Brand theme me gradient band ke andar: safed text/pills wale tokens. Baaki themes me kuch nahi badalta. */
@Composable
fun OnBrandTop(content: @Composable () -> Unit) {
    if (LocalThemeStyle.current == ThemeStyle.BRAND) {
        val c = BrandTopTokens
        CompositionLocalProvider(
            LocalMfc provides c,
            LocalExtra provides BrandTopExtra,
            LocalContentColor provides Color.White,
            content = content,
        )
    } else content()
}

val MaterialTheme.extra: Extra @Composable @ReadOnlyComposable get() = LocalExtra.current

fun parseHex(s: String, fallback: Color = Brand2): Color = runCatching {
    Color(android.graphics.Color.parseColor(if (s.startsWith("#")) s else "#$s"))
}.getOrDefault(fallback)
