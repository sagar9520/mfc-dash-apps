package com.myfuturecode.mfcdash.ui.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.myfuturecode.mfcdash.ui.theme.BrandStops
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.MfcAngleGradient
import com.myfuturecode.mfcdash.ui.theme.MfcColors
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.ui.theme.mfcColors
import com.myfuturecode.mfcdash.util.t

// ---------------- text ----------------

/** Tabular numbers: Text(style = KNum) ya kisi style me .merge(KNum). Kit ke saare text me pehle se laga hai. */
val KNum: TextStyle = TextStyle(fontFeatureSettings = "tnum")

internal fun kStyle(
    size: TextUnit, weight: FontWeight = FontWeight.Normal, lh: TextUnit = TextUnit.Unspecified, ls: TextUnit = TextUnit.Unspecified,
): TextStyle = TextStyle(fontSize = size, fontWeight = weight, lineHeight = lh, letterSpacing = ls, fontFeatureSettings = "tnum")

/** Ek line ka kit text (ellipsis). */
@Composable
internal fun KT(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal,
    lh: TextUnit = TextUnit.Unspecified, ls: TextUnit = TextUnit.Unspecified, maxLines: Int = 1, align: TextAlign = TextAlign.Start,
) {
    Text(
        text, modifier, color = color, style = kStyle(size, weight, lh, ls), maxLines = maxLines,
        softWrap = maxLines > 1, overflow = TextOverflow.Ellipsis, textAlign = align,
    )
}

/** Bada number: jagah kam ho to font chhota (max 40% tak), kabhi "..." nahi. */
@Composable
internal fun KFit(text: AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier, minScale: Float = .6f) {
    var scale by remember(text, style.fontSize) { mutableFloatStateOf(1f) }
    var fits by remember(text, style.fontSize) { mutableStateOf(true) }
    // kis max width par chhota kiya tha (jagah badhe to wapas poora size)
    var shrunkAt by remember(text, style.fontSize) { mutableIntStateOf(Int.MAX_VALUE) }
    val st = if (scale < 1f && style.fontSize.isSpecified) style.copy(
        fontSize = style.fontSize * scale,
        lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
    ) else style
    Text(
        text, modifier.drawWithContent { if (fits) drawContent() }, color = color, style = st, maxLines = 1, softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { r ->
            val need = r.multiParagraph.width
            val have = r.size.width.toFloat()
            val maxW = r.layoutInput.constraints.maxWidth
            if (scale < 1f && maxW > shrunkAt) {
                // jagah badh gayi (Fold khula): poore size se dobara
                fits = false
                shrunkAt = Int.MAX_VALUE
                scale = 1f
            } else if (need > have + .5f && need > 0f && scale > minScale) {
                // ek hi baar me sahi size (aadha-kata number kabhi na dikhe)
                fits = false
                shrunkAt = maxW
                scale = (scale * have / need * .98f).coerceAtLeast(minScale)
            } else fits = true
        },
    )
}

@Composable
internal fun KFit(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) =
    KFit(AnnotatedString(text), style, color, modifier)

// ---------------- status kind ----------------

/** Status = shabd + shape + rang. OK=Theek (check), WARN=Dhyan do (triangle), BAD=Turant (diamond), ASK=OK chahiye (clock). */
enum class KKind { OK, WARN, BAD, ASK, NEUTRAL }

@Composable
fun KKind.fg(): Color {
    val c = Mfc.c
    return when (this) {
        KKind.OK -> c.green
        KKind.WARN -> c.amber
        KKind.BAD -> c.red
        KKind.ASK -> c.brand
        KKind.NEUTRAL -> c.ink2
    }
}

/** Pill / tile ka icon (filled shape with mark). */
fun KKind.icon(): ImageVector? = when (this) {
    KKind.OK -> KIcons.Ok
    KKind.WARN -> KIcons.Dhyan
    KKind.BAD -> KIcons.Turant
    KKind.ASK -> KIcons.Wait
    KKind.NEUTRAL -> null
}

/** Row ki chhoti status line ka sada shape (diamond / triangle / check / clock). */
fun KKind.shape(): ImageVector? = when (this) {
    KKind.OK -> KIcons.Ok
    KKind.WARN -> KIcons.Tri
    KKind.BAD -> KIcons.Dia
    KKind.ASK -> KIcons.Wait
    KKind.NEUTRAL -> null
}

/** Status ka shabd: Theek / Dhyan do / Turant / OK chahiye. */
fun KKind.word(): String = when (this) {
    KKind.OK -> "Fine".t()
    KKind.WARN -> "Watch".t()
    KKind.BAD -> "Act now".t()
    KKind.ASK -> "Needs OK".t()
    KKind.NEUTRAL -> "Pending".t()
}

/** Graphic rang (meter, bars, swatch): z-green / z-amber / z-red. */
@Composable
fun KKind.zone(): Color {
    val c = Mfc.c
    return when (this) {
        KKind.OK -> c.zGreen
        KKind.WARN -> c.zAmber
        KKind.BAD -> c.zRed
        KKind.ASK -> c.brand
        KKind.NEUTRAL -> c.zGrey
    }
}

private fun pillColors(kind: KKind, c: MfcColors): Pair<Color, Color> = when (kind) {
    KKind.OK -> c.pOkBg to c.pOkFg
    KKind.WARN -> c.pWarnBg to c.pWarnFg
    KKind.BAD -> c.pBadBg to c.pBadFg
    KKind.ASK -> c.pAskBg to c.pAskFg
    KKind.NEUTRAL -> c.pNBg to c.pNFg
}

/** ROAS rang ka niyam: roas < min(target,1) = BAD, roas >= max(target,1) = OK, beech me WARN. null = NEUTRAL. */
fun kRoasKind(roas: Double?, target: Double): KKind {
    // null / NaN / Infinity (kharcha 0) = NEUTRAL
    if (roas == null || !roas.isFinite()) return KKind.NEUTRAL
    val t = if (target > 0 && target.isFinite()) target else 1.0
    return when {
        roas < minOf(t, 1.0) -> KKind.BAD
        roas >= maxOf(t, 1.0) -> KKind.OK
        else -> KKind.WARN
    }
}

/** ROAS ka text rang (green / amber / red / ink3). */
@Composable
fun kRoasColor(roas: Double?, target: Double): Color {
    val k = kRoasKind(roas, target)
    return if (k == KKind.NEUTRAL) Mfc.c.ink3 else k.fg()
}

/** Brand band ke andar: light theme ke rang (screen ne bahar se liye) ko safed-band wale rang me badlo. */
internal fun kFix(color: Color, c: MfcColors): Color {
    if (!c.onBrand || !color.isSpecified) return color
    val l = mfcColors(ThemeStyle.LIGHT)
    return when (color) {
        l.green -> c.green
        l.red -> c.red
        l.amber -> c.amber
        l.brand -> c.brand
        l.ink -> c.ink
        l.ink2 -> c.ink2
        l.ink3 -> c.ink3
        else -> color
    }
}

// ---------------- pill / status ----------------

@Composable
fun KPill(text: String, kind: KKind, modifier: Modifier = Modifier, icon: ImageVector? = kind.icon(), large: Boolean = false) {
    val c = Mfc.c
    val (bg, fg) = pillColors(kind, c)
    Row(
        modifier.height(if (large) 30.dp else 26.dp).clip(CircleShape).background(bg)
            .padding(start = if (icon != null) (if (large) 10.dp else 8.dp) else (if (large) 12.dp else 10.dp), end = if (large) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(if (large) 16.dp else 14.dp))
        KT(text, if (large) 14.sp else 13.sp, fg, weight = FontWeight.SemiBold)
    }
}

/** Chhoti rangeen status line: shape + shabd (13sp). */
@Composable
fun KStatus(text: String, kind: KKind, modifier: Modifier = Modifier) {
    val col = kind.fg()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        kind.shape()?.let { Icon(it, null, tint = col, modifier = Modifier.size(12.dp)) }
        KT(text, 13.sp, col, weight = if (kind == KKind.NEUTRAL) FontWeight.Normal else FontWeight.SemiBold, lh = 18.sp)
    }
}

/** Count badge (laal; urgent=false par brand). */
@Composable
fun KBadge(count: Int, modifier: Modifier = Modifier, urgent: Boolean = true, ring: Color = Mfc.c.badgeRing) {
    val c = Mfc.c
    val bg = if (urgent) c.badge else c.btnBg
    val fg = if (urgent) Color.White else c.btnFg
    Box(modifier.background(ring, CircleShape).padding(2.dp)) {
        Box(
            Modifier.height(18.dp).widthIn(min = 18.dp).background(bg, CircleShape).padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (count > 99) "99+" else count.toString(), color = fg, style = kStyle(12.sp, FontWeight.Bold, 14.sp), maxLines = 1)
        }
    }
}

// ---------------- cards / rows ----------------

private val CardShape = RoundedCornerShape(22.dp)

/** Sada card (radius 22, dark me halki border). list = true: rows ke liye (sirf 4dp upar-neeche). */
@Composable
fun KCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    list: Boolean = false,
    padding: PaddingValues = if (list) PaddingValues(vertical = 4.dp) else PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Mfc.c
    var m = modifier.fillMaxWidth().clip(CardShape).background(c.card)
    if (c.cardBd.alpha > 0f) m = m.border(1.dp, c.cardBd, CardShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    CompositionLocalProvider(LocalKInCard provides true, LocalKLine provides c.line) {
        Column(m.padding(padding), content = content)
    }
}

@Composable
fun KDivider(inset: Dp = 0.dp, end: Dp = 0.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset, end = end).height(1.dp).background(Mfc.c.line))
}

/** List card me do rows ke beech ki line: 16dp andar (lead = true: avatar ke baad 68dp se). */
@Composable
fun KListDivider(lead: Boolean = false) = KDivider(inset = if (lead) 68.dp else 16.dp, end = 16.dp)

/** Row ke shuru ka status/icon tile (40dp, kind ka halka rang). */
@Composable
fun KIconTile(icon: ImageVector, kind: KKind = KKind.NEUTRAL, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val c = Mfc.c
    val (bg, fg) = when (kind) {
        KKind.BAD -> c.redSoft to c.red
        KKind.WARN -> c.amberSoft to c.amber
        KKind.ASK -> c.brandSoft to c.brand
        KKind.OK -> c.greenSoft to c.green
        KKind.NEUTRAL -> c.pNBg to c.ink2
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(if (size < 40.dp) 10.dp else 12.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)) }
}

/** App ka letter tile: 2 akshar, av1..av6 rang (seed se). round = gol (account). */
@Composable
fun KAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp, seed: Int = name.hashCode(), round: Boolean = false) {
    val c = Mfc.c
    val (bg, fg) = c.avatars[((seed % 6) + 6) % 6]
    val shape = if (round) CircleShape else RoundedCornerShape(size * .3f)
    var m = modifier.size(size).clip(shape).background(bg)
    if (c.onBrand) m = modifier.size(size).border(1.5.dp, Color.White.copy(alpha = .7f), shape).clip(shape).background(bg)
    else if (c.isDark) m = m.border(1.dp, Color.White.copy(alpha = .08f), shape)
    Box(m, contentAlignment = Alignment.Center) {
        Text(initials(name), color = fg, style = kStyle((size.value * .375f).sp, FontWeight.Bold, ls = (-0.01).em), maxLines = 1)
    }
}

private fun initials(name: String): String {
    val words = name.trim().split(Regex("[\\s_.-]+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * List row (min 56dp; lead + sub ho to 64dp). sub ke saath subKind: shape + rang.
 * value = right side ka bold number, valueSub uske neeche, valueBottom (pill) sabse neeche. trailing = switch/button.
 * strong = unread (bold), quiet = padha hua.
 */
@Composable
fun KRow(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    subKind: KKind? = null,
    lead: (@Composable () -> Unit)? = null,
    value: String? = null,
    valueColor: Color = Color.Unspecified,
    valueSub: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    valuePlain: Boolean = false,
    strong: Boolean = false,
    quiet: Boolean = false,
    titleLines: Int = 1,
    valueBottom: (@Composable () -> Unit)? = null,
) {
    val c = Mfc.c
    val tall = lead != null && sub != null
    var m = modifier.fillMaxWidth().heightIn(min = if (tall) 64.dp else 56.dp)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(
        m.padding(horizontal = 16.dp, vertical = if (tall) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        lead?.invoke()
        Column(Modifier.weight(1f)) {
            KT(
                title, 15.sp, if (quiet) c.ink2 else c.ink, weight = if (strong) FontWeight.Bold else if (quiet) FontWeight.Medium else FontWeight.SemiBold,
                lh = 20.sp, maxLines = titleLines,
            )
            if (sub != null) {
                val k = if (quiet) null else subKind
                if (k != null && k != KKind.NEUTRAL) KStatus(sub, k, Modifier.padding(top = 2.dp))
                else KT(sub, 13.sp, if (quiet) c.ink3 else c.ink2, Modifier.padding(top = 2.dp), lh = 18.sp)
            }
        }
        if (value != null || valueSub != null || valueBottom != null) Column(horizontalAlignment = Alignment.End) {
            if (value != null) {
                if (valuePlain) KT(value, 14.sp, valueColor.takeOrElse { c.ink2 })
                else KT(value, 16.sp, kFix(valueColor, c).takeOrElse { c.ink }, weight = FontWeight.Bold, lh = 20.sp)
            }
            if (valueSub != null) KT(valueSub, 13.sp, c.ink3, Modifier.padding(top = 2.dp), lh = 18.sp)
            // value ke neeche chhoti pill (jaise "141 crash"), valueSub ke baad
            if (valueBottom != null) Box(Modifier.padding(top = 3.dp)) { valueBottom() }
        }
        trailing?.invoke()
        if (chevron) Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.offset(x = 6.dp).size(22.dp))
    }
}

/** Label baayein, value daayein (48dp). Detail cards ke liye. */
@Composable
fun KKv(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified, valueSub: String? = null) {
    val c = Mfc.c
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KT(label, 15.sp, c.ink2, Modifier.weight(1f))
        Text(
            buildAnnotatedString {
                append(value)
                if (valueSub != null) withStyle(SpanStyle(color = c.ink3, fontWeight = FontWeight.Medium)) { append(" $valueSub") }
            },
            color = kFix(valueColor, c).takeOrElse { c.ink }, style = kStyle(16.sp, FontWeight.Bold), maxLines = 1, softWrap = false,
        )
    }
}

// ---------------- controls ----------------

/**
 * Segmented tabs: 48dp track, chuna hua safed pill 4dp andar. Card/hero ke andar track = page rang.
 * fit = true (Home ke 4-5 period tabs, patla phone / bada font): naam "..." se nahi katte. Sab barabar chaude tab me aa jaayen
 * to barabar; warna har tab apne naam jitna (+ bachi jagah barabar baant ke), aur phir bhi tang ho to SAB naam ek hi chhote
 * size me (14sp ka 60% tak; phir bhi na aaye tabhi "..."). Badge ki jagah nahi ginte.
 */
@Composable
fun KSegTabs(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int> = emptyList(),
    track: Color = Color.Unspecified,
    fit: Boolean = false,
) {
    if (!fit || options.isEmpty()) { KSegRow(options, selected, onSelect, modifier, badges, track, 14.sp, null); return }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tm = rememberTextMeasurer()
        val tw = remember(options, tm) {
            options.map { tm.measure(it, kStyle(14.sp, FontWeight.SemiBold), maxLines = 1, softWrap = false).size.width.toFloat() }
        }
        val n = options.size
        val w = constraints.maxWidth.toFloat()
        // text ke dono taraf ki padding (har tab)
        val pad = with(LocalDensity.current) { (SEG_PAD * 2).toPx() }
        val sum = tw.sum()
        val (scale, weights) = when {
            !constraints.hasBoundedWidth || tw.max() + pad <= w / n -> 1f to null
            else -> {
                // chhota karna pade to 2% aur (gol hone se 1px bada text "..." na bane)
                val fitK = if (sum <= 0f) 1f else (w - n * pad) / sum
                val k = if (fitK >= 1f) 1f else (fitK * .98f).coerceAtLeast(.6f)
                val extra = ((w - n * pad - sum * k) / n).coerceAtLeast(0f)
                k to tw.map { it * k + pad + extra }
            }
        }
        KSegRow(options, selected, onSelect, Modifier, badges, track, 14.sp * scale, weights)
    }
}

/** Tab ke naam aur track ke kinare ki doori: chuna hua pill 4dp andar, to naam pill se 4dp door. */
private val SEG_PAD = 8.dp

/** weights = har tab ki chaudai ka hissa (null = sab barabar). */
@Composable
private fun KSegRow(
    options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier, badges: List<Int>, track: Color, size: TextUnit,
    weights: List<Float>?,
) {
    val c = Mfc.c
    val inCard = LocalKInCard.current
    val tb = track.takeOrElse {
        when {
            c.onBrand -> Color.White.copy(alpha = .16f)
            inCard -> c.page
            c.isDark -> c.card
            else -> c.line
        }
    }
    Row(modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(tb)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(weights?.getOrNull(i)?.takeIf { it > 0f } ?: 1f).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                if (on) {
                    var pill = Modifier.fillMaxWidth().fillMaxHeight().padding(4.dp).clip(RoundedCornerShape(20.dp)).background(c.tabOn)
                    if (!c.isDark && !c.onBrand) pill = pill.border(1.dp, c.line, RoundedCornerShape(20.dp))
                    Box(pill)
                }
                Row(Modifier.padding(horizontal = SEG_PAD), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KT(label, size, if (on) c.tabOnFg else c.ink2, weight = FontWeight.SemiBold)
                    val b = badges.getOrNull(i) ?: 0
                    if (b > 0) KBadge(b, ring = Color.Transparent)
                }
            }
        }
    }
}

/** Kit switch: on = brand, off = grey ring. Khud 48dp touch target. */
@Composable
fun KSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Mfc.c
    val knob by animateDpAsState(if (checked) 24.dp else 16.dp, label = "kSwKnob")
    val x by animateDpAsState(if (checked) 24.dp else 8.dp, label = "kSwX")
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.heightIn(min = 48.dp).alpha(if (enabled) 1f else .45f)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(52.dp, 32.dp).clip(shape).background(if (checked) c.btnBg else c.well)
                .border(2.dp, if (checked) c.btnBg else c.swOff, shape),
        ) {
            var k = Modifier.offset(x = x, y = (32.dp - knob) / 2).size(knob)
            if (checked) k = k.shadow(1.dp, CircleShape)
            Box(k.background(if (checked) c.btnFg else c.swOff, CircleShape))
        }
    }
}

enum class KBtn { PRIMARY, SOFT, OUTLINE }

private val BtnGradient = MfcAngleGradient(100f, listOf(0f to BrandStops[0].second, .7f to BrandStops[1].second))

/** 48dp button. PRIMARY: brand (brand theme me gradient, band ke andar safed). busy = spinner. */
@Composable
fun KButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: KBtn = KBtn.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val c = Mfc.c
    val shape = RoundedCornerShape(24.dp)
    val bg: Brush; val fg: Color
    when (kind) {
        KBtn.PRIMARY -> {
            bg = if (Mfc.style == ThemeStyle.BRAND && !c.onBrand) BtnGradient else SolidColor(c.btnBg)
            fg = if (Mfc.style == ThemeStyle.BRAND && !c.onBrand) Color.White else c.btnFg
        }
        KBtn.SOFT -> {
            bg = SolidColor(if (c.onBrand) c.pNBg else c.brandSoft)
            fg = if (c.onBrand) Color.White else c.brand
        }
        KBtn.OUTLINE -> {
            bg = SolidColor(Color.Transparent)
            fg = c.ink
        }
    }
    var m = modifier.heightIn(min = 48.dp).alpha(if (enabled) 1f else .45f).clip(shape).background(bg, shape)
    if (kind == KBtn.OUTLINE) m = m.border(1.5.dp, c.line, shape)
    m = m.clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
    Row(m.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
        else if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        KT(text, 15.sp, fg, weight = FontWeight.Bold)
    }
}

/** Row ke daayein chhota brand-soft button ("Dekho", "OK do"), 48dp. */
@Composable
fun KSmallAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = Mfc.c
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.height(48.dp).alpha(if (enabled) 1f else .45f).clip(shape).background(if (c.onBrand) c.pNBg else c.brandSoft)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { KT(text, 15.sp, if (c.onBrand) Color.White else c.brand, weight = FontWeight.Bold) }
}

/** Gol kinaron wali progress line (track = z-track). */
@Composable
fun KProgress(fraction: Float, color: Color, modifier: Modifier = Modifier, top: Dp = 12.dp) {
    val c = Mfc.c
    val f = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().padding(top = top).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.zTrack)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
    }
}

/** Search field (48dp, gol). */
@Composable
fun KSearch(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = Mfc.c
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(shape).background(c.card).border(1.dp, c.line, shape)
            .padding(start = 16.dp, end = if (value.isNotEmpty()) 0.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(KIcons.Search, null, tint = c.ink3, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) KT(placeholder, 15.sp, c.ink3)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = kStyle(15.sp).copy(color = c.ink), cursorBrush = SolidColor(c.brand), modifier = Modifier.fillMaxWidth(),
            )
        }
        // hatao button: 48dp touch
        if (value.isNotEmpty()) Icon(
            KIcons.Close, "Clear".t(), tint = c.ink3,
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button) { onChange("") }.padding(14.dp),
        )
    }
}

/** Kit slider: brand track + gol thumb (safed ring), 48dp touch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onDone: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val c = Mfc.c
    Slider(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().height(48.dp),
        enabled = enabled,
        valueRange = range,
        steps = steps,
        onValueChangeFinished = onDone,
        thumb = {
            Box(
                Modifier.size(24.dp).shadow(2.dp, CircleShape).background(c.dotRing, CircleShape).padding(4.dp)
                    .background(c.brand, CircleShape),
            )
        },
        track = { st: SliderState ->
            val span = st.valueRange.endInclusive - st.valueRange.start
            val f = if (span > 0f) ((st.value - st.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Box(
                Modifier.fillMaxWidth().height(6.dp).drawBehind {
                    val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    drawRoundRect(c.zTrack, cornerRadius = r)
                    drawRoundRect(c.brand, size = size.copy(width = size.width * f), cornerRadius = r)
                },
            )
        },
    )
}

/** Khaali jagah: chhota icon + 1-3 shabd. */
@Composable
fun KEmpty(text: String, icon: ImageVector? = null) {
    val c = Mfc.c
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Icon(icon, null, tint = c.ink3, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
        }
        KT(text, 15.sp, c.ink3, align = TextAlign.Center, maxLines = 2)
    }
}

/** Chhota beech me spinner. */
@Composable
fun KLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = Mfc.c.brand, strokeWidth = 3.dp)
    }
}

// line helpers (separators bina IntrinsicSize ke)
internal fun Modifier.kLeftLine(color: Color, on: Boolean = true): Modifier =
    if (!on) this else drawBehind { drawLine(color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx()) }

@Composable
internal fun KHLine(color: Color) = Box(Modifier.fillMaxWidth().height(1.dp).background(color))

@Composable
internal fun KGap(h: Dp) = Spacer(Modifier.height(h))

@Composable
internal fun KWGap(w: Dp) = Spacer(Modifier.width(w))
