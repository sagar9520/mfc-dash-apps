package com.myfuturecode.mfcdash.ui.kit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.MfcColors
import com.myfuturecode.mfcdash.ui.theme.OnBrandTop
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.roasPct
import com.myfuturecode.mfcdash.util.t
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Hero / module ka rang: Green = paisa, Rose = App Health, Sand = Google Ads / AI Kharcha, Lav = Studio. */
enum class KTint { Green, Rose, Sand, Lav }

private fun tintOf(t: KTint, c: MfcColors): Triple<Color, Color, Color> = when (t) {
    KTint.Green -> Triple(c.heroA, c.heroB, c.heroBd)
    KTint.Rose -> Triple(c.roseA, c.roseB, c.roseBd)
    KTint.Sand -> Triple(c.sandA, c.sandB, c.sandBd)
    KTint.Lav -> Triple(c.lavA, c.lavB, c.lavBd)
}

private val HeroShape = RoundedCornerShape(24.dp)
private val ModShape = RoundedCornerShape(22.dp)

/**
 * Screen ka ek bada jawab. Light/dark: tint gradient card (16dp side margin jab KTop me).
 * Brand: KTop ke andar transparent (safed text); bahar (jaise Fold column) gradient card.
 */
@Composable
fun KHero(
    tint: KTint = KTint.Green,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inTop = LocalKInTop.current
    val brand = Mfc.style == ThemeStyle.BRAND
    val outer = if (inTop) modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp) else modifier
    val pad = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
    when {
        brand && inTop -> {
            var m = outer.fillMaxWidth().clip(HeroShape)
            if (onClick != null) m = m.clickable(onClick = onClick)
            CompositionLocalProvider(LocalKInCard provides false, LocalKLine provides Mfc.c.heroLine) {
                Column(m.then(pad), content = content)
            }
        }
        brand -> {
            var m = outer.fillMaxWidth().clip(HeroShape).kBrandCard()
            if (onClick != null) m = m.clickable(onClick = onClick)
            OnBrandTop {
                CompositionLocalProvider(LocalKInTop provides false, LocalKInCard provides false, LocalKLine provides Mfc.c.heroLine) {
                    Column(m.then(pad), content = content)
                }
            }
        }
        else -> {
            val c = Mfc.c
            val (a, b, bd) = tintOf(tint, c)
            val endY = with(LocalDensity.current) { 190.dp.toPx() }
            val bg = Brush.verticalGradient(0f to a, (96f / 190f) to b, 1f to c.card, startY = 0f, endY = endY)
            var m = outer.fillMaxWidth().clip(HeroShape).background(bg).border(1.dp, bd, HeroShape)
            if (onClick != null) m = m.clickable(onClick = onClick)
            CompositionLocalProvider(LocalKInCard provides true, LocalKLine provides if (tint == KTint.Green) c.heroLine else c.line) {
                Column(m.then(pad), content = content)
            }
        }
    }
}

/** Hero ki pehli line: label (15sp, ink2) + daayein pill / KLive. */
@Composable
fun KHeroHead(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: (@Composable () -> Unit)? = null) {
    val c = Mfc.c
    Row(
        modifier.fillMaxWidth().heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, null, tint = c.ink2, modifier = Modifier.size(18.dp))
            KT(label, 15.sp, c.ink2, weight = FontWeight.SemiBold, lh = 20.sp)
        }
        trailing?.invoke()
    }
}

/** Hara dot (ring ke saath) + "Live · 2 min pehle". */
@Composable
fun KLive(text: String) {
    val c = Mfc.c
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).background(c.liveRing, CircleShape).padding(3.dp).background(c.live, CircleShape))
        KT(text, 13.sp, c.ink2, lh = 18.sp)
    }
}

/** Bada number (default 40sp; hero me 48.sp do). suffix ("/25", " + $120") chhota ink3. Brand band me hamesha safed. */
@Composable
fun KBigNum(text: String, color: Color = Color.Unspecified, suffix: String? = null, size: TextUnit = 40.sp) {
    val c = Mfc.c
    val col = if (c.onBrand) c.ink else color.takeOrElse { c.ink }
    val s = buildAnnotatedString {
        append(text)
        if (suffix != null) withStyle(SpanStyle(fontSize = size * .58f, color = c.ink3, letterSpacing = (-0.01).em)) { append(suffix) }
    }
    KFit(s, kStyle(size, FontWeight.Bold, size * 1.13f, (-0.02).em), col)
}

/**
 * Hero ke 2-4 number ek line me (Kamai | Ads se | Payments | Ads kharcha), beech me line.
 * compact = chhota font, kam gap (4 khane wala paisa hero); false = 3 khano jaisa (Gads account card ke 4 khane).
 */
@Composable
fun KSplit(items: List<Pair<String, String>>, colors: List<Color?> = emptyList(), compact: Boolean = items.size >= 4) {
    val c = Mfc.c
    val three = !compact && items.size >= 3
    val top = LocalKLine.current.takeOrElse { c.line }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        KHLine(top)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            items.forEachIndexed { i, (k, v) ->
                Column(
                    Modifier.weight(1f).kLeftLine(c.line, i > 0)
                        .padding(start = if (i > 0) (if (compact) 10.dp else if (three) 14.dp else 18.dp) else 0.dp, end = 4.dp),
                ) {
                    // compact (4 khaane): label bhi KFit (chhota phone par "Ads khar…" nahi)
                    if (compact) KFit(k, kStyle(13.sp, lh = 18.sp), c.ink2) else KT(k, 14.sp, c.ink2, lh = 18.sp)
                    val col = kFix(colors.getOrNull(i) ?: Color.Unspecified, c).takeOrElse { c.ink }
                    val vs = if (compact) kStyle(18.sp, FontWeight.Bold, 24.sp, (-0.01).em)
                    else kStyle(if (three) 20.sp else 22.sp, FontWeight.Bold, 28.sp, (-0.01).em)
                    KFit(v, vs, col, Modifier.padding(top = 1.dp))
                }
            }
        }
    }
}

// ---------------- meters ----------------

private const val TICK_TEXT_GAP = 6

/** Meter ki zone patti + tick lines + dot. pLo/pHi = zone seema (0..1), dot = 0..1 ya null. */
private fun DrawScope.kMeter(
    pLo: Float, pHi: Float, left: Color, mid: Color, right: Color, tick: Color, dot: Float?, dotColor: Color,
    ring: Color, dark: Boolean,
) {
    val w = size.width
    val barTop = 6.dp.toPx()
    val barH = 8.dp.toPx()
    val gap = 1.5.dp.toPx()
    val r = 4.dp.toPx()
    val single = abs(pHi - pLo) < .001f
    val x1 = pLo * w
    val x2 = pHi * w
    // left zone (gol shuru)
    val lEnd = max(0f, x1 - gap)
    if (lEnd > 0f) {
        drawRoundRect(left, Offset(0f, barTop), Size(lEnd, barH), CornerRadius(r))
        if (lEnd > r) drawRect(left, Offset(lEnd - r, barTop), Size(r, barH))
    }
    if (!single) {
        val mS = x1 + gap
        val mE = x2 - gap
        if (mE > mS) drawRect(mid, Offset(mS, barTop), Size(mE - mS, barH))
    }
    val rS = min(w, x2 + gap)
    if (w - rS > 0f) {
        drawRoundRect(right, Offset(rS, barTop), Size(w - rS, barH), CornerRadius(r))
        if (w - rS > r) drawRect(right, Offset(rS, barTop), Size(r, barH))
    }
    // tick lines (22dp, patti ke neeche)
    val tw = 1.5.dp.toPx()
    val ty = barTop + barH
    val th = 22.dp.toPx()
    drawRect(tick, Offset(x1 - tw, ty), Size(tw, th))
    if (!single) drawRect(tick, Offset(x2, ty), Size(tw, th))
    // dot: 20dp, 4dp ring
    if (dot != null) {
        val rr = 10.dp.toPx()
        val cx = (dot * w).coerceIn(rr, w - rr)
        val cy = barTop + barH / 2f
        drawCircle(Color.Black.copy(alpha = if (dark) .45f else .18f), rr + .75.dp.toPx(), Offset(cx, cy + 1.dp.toPx()))
        if (dark) drawCircle(Color.White.copy(alpha = .55f), rr + 1.5.dp.toPx(), Offset(cx, cy))
        drawCircle(ring, rr, Offset(cx, cy))
        drawCircle(dotColor, rr - 4.dp.toPx(), Offset(cx, cy))
    }
}

/** Do tick labels: pehla tick ke baayein (right aligned), doosra tick ke daayein. Kinaron se bahar nahi jaate. */
@Composable
private fun KTickLabels(pLo: Float, pHi: Float, t1: String, t2: String?) {
    val c = Mfc.c
    Layout(
        content = {
            KT(t1, 12.sp, c.ink2, lh = 18.sp)
            if (t2 != null) KT(t2, 12.sp, c.ink2, lh = 18.sp)
        },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) { ms, cs ->
        val w = cs.maxWidth
        val ps = ms.map { it.measure(Constraints(maxWidth = w)) }
        val gap = TICK_TEXT_GAP.dp.roundToPx()
        val h = ps.maxOfOrNull { it.height } ?: 0
        layout(w, h) {
            val a = ps[0]
            val ax = if (t2 == null) (pLo * w).toInt() + gap else (pLo * w).toInt() - gap - a.width
            a.placeRelative(ax.coerceIn(0, max(0, w - a.width)), 0)
            if (ps.size > 1) {
                val b = ps[1]
                val bx = (pHi * w).toInt() + gap
                b.placeRelative(bx.coerceIn(0, max(0, w - b.width)), 0)
            }
        }
    }
}

/**
 * ROAS: label + bada % + pill (Profit zone / Target se kam / Loss) + 3-zone meter + ticks.
 * roas aur target ratio me (1.0 = 100% = break-even). null = "No spend" (grey).
 * head = false: sirf meter (jab ROAS split me dikh raha ho). divider = upar ki line.
 */
@Composable
fun KRoasMeter(
    roas: Double?,
    target: Double,
    modifier: Modifier = Modifier,
    label: String = "ROAS",
    head: Boolean = true,
    divider: Boolean = true,
) {
    val c = Mfc.c
    val t = if (target > 0 && target.isFinite()) target else 1.0
    val lo = min(t, 1.0)
    val hi = max(t, 1.0)
    val single = abs(hi - lo) < .005
    // NaN / Infinity (kharcha 0) = "No spend"
    val r = roas?.takeIf { it.isFinite() }
    val kind = kRoasKind(r, t)
    val pLo: Float
    val pHi: Float
    when {
        single -> { pLo = .375f; pHi = .375f }
        t < 1.0 -> { pLo = .25f; pHi = .375f }
        else -> { pLo = .35f; pHi = .5f }
    }
    // zone-wise scale: neeche lo/2 se, upar hi*2 tak
    val vMin = lo * .5
    val vMax = hi * 2.0
    val dot: Float? = r?.let { v ->
        when {
            v <= lo -> pLo * ((v - vMin) / (lo - vMin)).toFloat().coerceIn(0f, 1f)
            v <= hi -> if (single) pLo else pLo + (pHi - pLo) * ((v - lo) / (hi - lo)).toFloat()
            else -> pHi + (1f - pHi) * ((v - hi) / (vMax - hi)).toFloat().coerceIn(0f, 1f)
        }
    }
    val pillText = when (kind) {
        KKind.OK -> "Profit zone".t()
        KKind.WARN -> "Below target".t()
        KKind.BAD -> "Loss".t()
        else -> "No spend".t()
    }
    val be = "100% " + "break-even".t()
    val tg = roasPct(t) + " " + "target".t()
    val t1 = if (single) be else if (t < 1.0) tg else be
    val t2 = if (single) null else if (t < 1.0) be else tg
    Column(modifier.fillMaxWidth().padding(top = if (divider) 10.dp else 0.dp)) {
        if (divider) KHLine(c.line)
        if (!head && divider) KGap(10.dp)
        if (head) {
            Row(
                Modifier.fillMaxWidth().padding(top = if (divider) 10.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    KT(label, 15.sp, c.ink2, Modifier.alignByBaseline(), weight = FontWeight.SemiBold)
                    KWGap(8.dp)
                    val col = if (kind == KKind.NEUTRAL) c.ink3 else kind.fg()
                    KFit(if (r == null) "–" else roasPct(r), kStyle(28.sp, FontWeight.Bold, 30.sp, (-0.01).em), col, Modifier.alignByBaseline())
                }
                KPill(pillText, kind)
            }
        }
        val zl = c.zRed; val zm = c.zAmber; val zr = c.zGreen
        val dc = when (kind) { KKind.OK -> c.zGreen; KKind.WARN -> c.zAmber; KKind.BAD -> c.zRed; else -> c.zGrey }
        Box(Modifier.fillMaxWidth().padding(top = if (head || divider) 5.dp else 0.dp)) {
            Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                kMeter(pLo, pHi, zl, zm, zr, c.tick, dot, dc, c.dotRing, c.isDark)
            }
            Box(Modifier.padding(top = 14.dp)) { KTickLabels(pLo, pHi, t1, t2) }
        }
    }
}

/**
 * Google Ads pace (% normal se upar/neeche): amber | green | amber meter, ticks warnLow / warnHigh.
 * pct = -12.0 matlab 12% kam. null = "No data".
 */
@Composable
fun KPaceMeter(
    pct: Double?,
    warnLow: Double = -40.0,
    warnHigh: Double = 60.0,
    modifier: Modifier = Modifier,
    label: String = "Spend pace".t(),
    head: Boolean = true,
    divider: Boolean = true,
) {
    val c = Mfc.c
    val lo = min(warnLow, warnHigh)
    val hi = max(warnLow, warnHigh)
    val span = (hi - lo).takeIf { it > 0 } ?: 1.0
    val vMin = lo - span * .4
    val vMax = hi + span * .4
    val pLo = ((lo - vMin) / (vMax - vMin)).toFloat()
    val pHi = ((hi - vMin) / (vMax - vMin)).toFloat()
    val v = pct?.takeIf { it.isFinite() }
    val kind = when {
        v == null -> KKind.NEUTRAL
        v < lo || v > hi -> KKind.WARN
        else -> KKind.OK
    }
    val pillText = when {
        v == null -> "No data".t()
        v < lo -> "Below normal".t()
        v > hi -> "Above normal".t()
        else -> "Normal".t()
    }
    val dot = v?.let { ((it - vMin) / (vMax - vMin)).toFloat().coerceIn(0f, 1f) }
    Column(modifier.fillMaxWidth().padding(top = if (divider) 10.dp else 0.dp)) {
        if (divider) KHLine(c.line)
        if (!head && divider) KGap(10.dp)
        if (head) {
            Row(
                Modifier.fillMaxWidth().padding(top = if (divider) 10.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    KT(label, 15.sp, c.ink2, Modifier.alignByBaseline(), weight = FontWeight.SemiBold)
                    KWGap(8.dp)
                    KFit(if (v == null) "–" else kSignedPct(v), kStyle(28.sp, FontWeight.Bold, 30.sp, (-0.01).em), c.ink, Modifier.alignByBaseline())
                }
                KPill(pillText, kind)
            }
        }
        val dc = if (kind == KKind.OK) c.zGreen else if (kind == KKind.WARN) c.zAmber else c.zGrey
        Box(Modifier.fillMaxWidth().padding(top = if (head || divider) 5.dp else 0.dp)) {
            Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                kMeter(pLo, pHi, c.zAmber, c.zGreen, c.zAmber, c.tick, dot, dc, c.dotRing, c.isDark)
            }
            Box(Modifier.padding(top = 14.dp)) { KTickLabels(pLo, pHi, kSignedPct(lo), kSignedPct(hi)) }
        }
    }
}

/** "+12%", "−12%" (asli minus), "0%". */
fun kSignedPct(v: Double, dec: Int = 0): String = when {
    !v.isFinite() -> "–"
    v > 0 -> "+" + num(v, dec) + "%"
    v < 0 -> "−" + num(-v, dec) + "%"
    else -> "0%"
}

// ---------------- duo strips ----------------

/** Hero ki neeche wali patti ka ek hissa: head ("18 profit mein"), name, value (+₹9,820). up = ▲ ya ▼. prefix = "Top:". */
data class KDuoSide(
    val head: String,
    val name: String,
    val value: String,
    val kind: KKind,
    val up: Boolean,
    val prefix: String? = "Top:",
)

@Composable
private fun KChev(c: MfcColors) {
    Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.offset(x = 6.dp).size(22.dp))
}

/** Hero ke neeche: do taraf (profit | loss) + chevron. Poori patti ek tap. */
@Composable
fun KHeroDuo(left: KDuoSide?, right: KDuoSide?, onClick: (() -> Unit)?) {
    val c = Mfc.c
    val sides = listOfNotNull(left, right)
    if (sides.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        KHLine(c.line)
        var m = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        if (onClick != null) m = m.clickable(onClick = onClick)
        Row(m.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            sides.forEachIndexed { i, s ->
                val col = kFix(s.kind.fg(), c)
                Column(Modifier.weight(1f).kLeftLine(c.line, i > 0).padding(start = if (i > 0) 16.dp else 0.dp, end = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(if (s.up) KIcons.Up else KIcons.Down, null, tint = col, modifier = Modifier.size(11.dp))
                        KT(s.head, 13.sp, col, weight = FontWeight.SemiBold, lh = 18.sp)
                    }
                    Text(
                        buildAnnotatedString {
                            val pre = s.prefix?.takeIf { it.isNotBlank() }
                            if (pre != null) withStyle(SpanStyle(color = c.ink3)) { append(pre.t() + " ") }
                            append(s.name)
                        },
                        Modifier.padding(top = 3.dp), color = c.ink, style = kStyle(14.sp, lh = 18.sp), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    KFit(s.value, kStyle(18.sp, FontWeight.Bold, 22.sp), col)
                }
            }
            if (onClick != null) KChev(c)
        }
    }
}

/** Module card ki neeche wali patti ka ek naam: name + status line (shape + rang). */
data class KDuoItem(val name: String, val detail: String, val kind: KKind)

/** Module card ke neeche: do naam (har ek ki status line) + chevron. */
@Composable
fun KDuo(left: KDuoItem?, right: KDuoItem?, onClick: (() -> Unit)?) {
    val c = Mfc.c
    val items = listOfNotNull(left, right)
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        KHLine(c.line)
        var m = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        if (onClick != null) m = m.clickable(onClick = onClick)
        Row(m.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, d ->
                Column(Modifier.weight(1f).kLeftLine(c.line, i > 0).padding(start = if (i > 0) 16.dp else 0.dp, end = 4.dp)) {
                    KT(d.name, 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
                    KStatus(d.detail, d.kind, Modifier.padding(top = 2.dp))
                }
            }
            if (onClick != null) KChev(c)
        }
    }
}

// ---------------- module ----------------

/** Rangeen module card (hero jaisa, chhota): App Health = Rose, Google Ads = Sand, Studio = Lav. */
@Composable
fun KModule(tint: KTint, onClick: (() -> Unit)?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Mfc.c
    val (a, b, bd) = tintOf(tint, c)
    val endY = with(LocalDensity.current) { 132.dp.toPx() }
    val bg = Brush.verticalGradient(0f to a, (64f / 132f) to b, 1f to c.card, startY = 0f, endY = endY)
    var m = modifier.fillMaxWidth().clip(ModShape).background(bg).border(1.dp, bd, ModShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    CompositionLocalProvider(LocalKInCard provides true, LocalKLine provides c.line) {
        Column(m.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp), content = content)
    }
}

/** Module ki pehli line: icon + naam (15sp ink2) + daayein pill. */
@Composable
fun KModuleHead(icon: ImageVector, label: String, pill: (@Composable () -> Unit)? = null) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = c.ink2, modifier = Modifier.size(18.dp))
            KT(label, 15.sp, c.ink2, weight = FontWeight.SemiBold, lh = 20.sp)
        }
        pill?.invoke()
    }
}

/** Module ka number: "21" + of ("/25" ya " ideas") + caption ("apps theek") · captionAccent (brand rang). */
@Composable
fun KCount(num: String, of: String? = null, caption: String? = null, captionAccent: String? = null) {
    val c = Mfc.c
    Column(Modifier.padding(top = 4.dp)) {
        val s = buildAnnotatedString {
            append(num)
            if (of != null) withStyle(SpanStyle(fontSize = 22.sp, color = c.ink3, letterSpacing = (-0.01).em)) { append(of) }
        }
        KFit(s, kStyle(34.sp, FontWeight.Bold, 40.sp, (-0.02).em), c.ink)
        if (caption != null || captionAccent != null) Text(
            buildAnnotatedString {
                if (caption != null) append(caption)
                if (caption != null && captionAccent != null) append(" · ")
                if (captionAccent != null) withStyle(SpanStyle(color = c.brand)) { append(captionAccent) }
            },
            color = c.ink2, style = kStyle(14.sp, lh = 18.sp), maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

// ---------------- tiny visuals ----------------

/** Ek patti me hisse (21 theek | 3 problem | 1 baaki). 0 wale hisse chhod deta hai. */
@Composable
fun KSegBar(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier, top: Dp = 14.dp) {
    val c = Mfc.c
    val ps = parts.filter { it.first > 0f && it.first.isFinite() }
    Row(modifier.fillMaxWidth().padding(top = top).height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (ps.isEmpty()) Box(Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c.zTrack))
        ps.forEachIndexed { i, (w, col) ->
            val shape = RoundedCornerShape(
                topStart = if (i == 0) 4.dp else 0.dp, bottomStart = if (i == 0) 4.dp else 0.dp,
                topEnd = if (i == ps.lastIndex) 4.dp else 0.dp, bottomEnd = if (i == ps.lastIndex) 4.dp else 0.dp,
            )
            Box(Modifier.weight(w).fillMaxHeight().clip(shape).background(col))
        }
    }
}

/** Legend column: rang ka chhota square + bold number + shabd ("21 theek"). Triple(color, number, word). */
@Composable
fun KLegend(items: List<Triple<Color, String, String>>) {
    val c = Mfc.c
    Column(Modifier.widthIn(min = 106.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEach { (col, n, w) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(col))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Bold)) { append(n) }
                        append(" ")
                        append(w)
                    },
                    color = c.ink2, style = kStyle(13.sp, lh = 18.sp), maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

/** Account marks: check (theek), triangle (dhyan do), diamond (turant). max se zyada ho to "+N". */
@Composable
fun KMarks(ok: Int, warn: Int, bad: Int = 0, max: Int = 8) {
    val c = Mfc.c
    val b = bad.coerceAtLeast(0).coerceAtMost(max)
    val w = warn.coerceAtLeast(0).coerceAtMost(max - b)
    val o = ok.coerceAtLeast(0).coerceAtMost(max - b - w)
    val extra = ok.coerceAtLeast(0) + warn.coerceAtLeast(0) + bad.coerceAtLeast(0) - (o + w + b)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(o) { Icon(KIcons.Ok, null, tint = c.markOk, modifier = Modifier.padding(1.dp).size(22.dp)) }
        repeat(w) { Icon(KIcons.Dhyan, null, tint = c.markWarn, modifier = Modifier.size(24.dp)) }
        repeat(b) { Icon(KIcons.Turant, null, tint = c.markBad, modifier = Modifier.size(24.dp)) }
        if (extra > 0) KT("+$extra", 13.sp, c.ink3, weight = FontWeight.SemiBold)
    }
}

/** Studio pipeline: har step ek patti + number + shabd; active step brand rang. Pair(number, word). */
@Composable
fun KSteps(steps: List<Pair<String, String>>, active: Int) {
    val c = Mfc.c
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { i, (n, w) ->
            if (i > 0) Box(Modifier.width(22.dp), contentAlignment = Alignment.TopCenter) {
                Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.offset(y = (-5).dp).size(16.dp))
            }
            val on = i == active
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (on) c.brand else c.step))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (on) c.brand else c.ink)) { append(n) }
                        append(" ")
                        append(w)
                    },
                    Modifier.padding(top = 6.dp), color = if (on) c.brand else c.ink2, style = kStyle(14.sp, lh = 20.sp), maxLines = 1,
                    softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 7 din ke bars: plus = hare (baseline ke upar), minus = laal (neeche). Aakhri bar (aaj) gehra + bold label.
 * height = poora chart (labels chhod kar). valueText = har bar ke upar chhota text (optional).
 */
@Composable
fun KWeekBars(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    valueText: ((Double) -> String)? = null,
) {
    val c = Mfc.c
    if (values.isEmpty()) {
        KEmpty("No data".t())
        return
    }
    val vs = values.map { if (it.isFinite()) it else 0.0 }
    val maxPos = vs.maxOrNull()?.takeIf { it > 0 } ?: 0.0
    val maxNeg = vs.minOrNull()?.takeIf { it < 0 }?.let { -it } ?: 0.0
    val hasNeg = maxNeg > 0
    val up = if (hasNeg) height * .79f else height
    val dn = if (hasNeg) height - up else 0.dp
    val n = vs.size
    val gap = if (n > 10) 3.dp else 10.dp
    val every = if (n > 10) (n + 6) / 7 else 1
    val past = if (c.isDark) .62f else .55f
    val valH = if (valueText != null) 16.dp else 0.dp
    Row(
        modifier.fillMaxWidth().padding(top = 14.dp).drawBehind {
            val y = up.toPx()
            drawLine(c.tick, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
        },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        vs.forEachIndexed { i, v ->
            val today = i == n - 1
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(up), contentAlignment = Alignment.BottomCenter) {
                    if (v > 0 && maxPos > 0) {
                        val h = ((up - valH) * (v / maxPos).toFloat()).coerceAtLeast(3.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (valueText != null) KT(valueText(v), 12.sp, c.ink3, lh = 16.sp)
                            Box(
                                Modifier.widthIn(max = 30.dp).fillMaxWidth().height(h)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                    .background(c.zGreen.copy(alpha = if (today) 1f else past)),
                            )
                        }
                    }
                }
                if (hasNeg) Box(Modifier.fillMaxWidth().height(dn), contentAlignment = Alignment.TopCenter) {
                    if (v < 0) {
                        val h = (dn * (-v / maxNeg).toFloat()).coerceAtLeast(6.dp)
                        Box(
                            Modifier.widthIn(max = 30.dp).fillMaxWidth().height(h)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                                .background(c.zRed),
                        )
                    }
                }
                val l = labels.getOrNull(i).orEmpty()
                val show = today || i % every == 0
                KT(
                    if (show) l else "", 12.sp, if (today) c.ink else c.ink3, Modifier.padding(top = 4.dp),
                    weight = if (today) FontWeight.Bold else FontWeight.Normal, lh = 16.sp,
                )
            }
        }
    }
}
