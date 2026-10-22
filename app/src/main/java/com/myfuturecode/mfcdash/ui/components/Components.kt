package com.myfuturecode.mfcdash.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfuturecode.mfcdash.data.Health
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KKv
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.kRoasKind
import com.myfuturecode.mfcdash.ui.theme.LocalMfc
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.ui.theme.mfcColors
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.t
import kotlin.math.max
import kotlin.math.min

// Purane building blocks, ab kit (ui/kit) ke rang aur shape me. Signatures wahi, andar ka look naya.

val CardShape = RoundedCornerShape(22.dp)

/** Ek line ka text (tabular numbers, ellipsis). Kit jaisa, screens ke chhote labels ke liye. */
@Composable
fun DashText(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1, align: TextAlign = TextAlign.Start,
) {
    Text(
        text, modifier, color = color,
        style = TextStyle(fontSize = size, fontWeight = weight, fontFeatureSettings = "tnum", lineHeight = size * 1.3f),
        maxLines = maxLines, softWrap = maxLines > 1, overflow = TextOverflow.Ellipsis, textAlign = align,
    )
}

/** Section heading (kit KSection) + daayein chhota sub. */
@Composable
fun SectionTitle(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    val c = Mfc.c
    KSection(title, modifier, trailing = {
        if (!sub.isNullOrBlank()) DashText(sub, 13.sp, c.ink3, Modifier.widthIn(max = 200.dp))
    })
}

/** Card = kit KCard (radius 22, dark me halki border). */
@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    KCard(modifier, onClick = onClick, content = content)
}

/** Chhota KPI tile: label, value, sub-line. hi = brand-soft. */
@Composable
fun Tile(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier, valueColor: Color? = null, hi: Boolean = false) {
    val c = Mfc.c
    val shape = RoundedCornerShape(16.dp)
    var m = modifier.clip(shape).background(if (hi) c.brandSoft else c.card)
    if (c.cardBd.alpha > 0f) m = m.border(1.dp, c.cardBd, shape)
    Column(m.padding(horizontal = 14.dp, vertical = 12.dp)) {
        DashText(label, 13.sp, c.ink2)
        DashText(value, 20.sp, valueColor ?: c.ink, Modifier.padding(top = 2.dp), weight = FontWeight.Bold)
        if (!sub.isNullOrBlank()) DashText(sub, 13.sp, c.ink3, Modifier.padding(top = 2.dp))
    }
}

/** Card ke andar chhote numbers ka grid (label, value, sub). Koi andar ka dibba nahi. colors = index -> value rang. */
@Composable
fun StatCells(items: List<Triple<String, String, String?>>, cols: Int = 3, colors: Map<Int, Color> = emptyMap()) {
    val c = Mfc.c
    val n = cols.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(n).forEachIndexed { ri, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { ci, item ->
                    val idx = ri * n + ci
                    Column(Modifier.weight(1f)) {
                        DashText(item.first, 13.sp, c.ink2)
                        DashText(item.second, 18.sp, colors[idx] ?: c.ink, Modifier.padding(top = 1.dp), weight = FontWeight.Bold)
                        item.third?.takeIf { it.isNotBlank() }?.let { DashText(it, 13.sp, c.ink3) }
                    }
                }
                repeat(n - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** KPI grid: ab ek hi card me numbers (pehle alag alag dibbe). */
@Composable
fun TileGrid(items: List<Triple<String, String, String?>>, cols: Int = 3, colors: Map<Int, Color> = emptyMap()) {
    if (items.isEmpty()) return
    KCard { StatCells(items, cols, colors) }
}

/** Chip: chuna = brand pill, baaki = card + line. Dikhne me 36dp, touch 48dp. */
@Composable
fun MChip(text: String, on: Boolean, onClick: () -> Unit, dot: Color? = null, trailing: String? = null) {
    val c = Mfc.c
    val shape = RoundedCornerShape(18.dp)
    val bg = when {
        on -> c.btnBg
        c.onBrand -> c.pNBg
        else -> c.card
    }
    val fg = if (on) c.btnFg else c.ink2
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        var pill = Modifier.height(36.dp).clip(shape).background(bg)
        if (!on && !c.onBrand) pill = pill.border(1.dp, c.line, shape)
        Row(pill.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            DashText(text, 14.sp, fg, weight = FontWeight.SemiBold)
            if (trailing != null) DashText(trailing, 13.sp, fg.copy(alpha = .75f))
        }
    }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** App ka haal: shabd + shape + rang (kit pill). */
@Composable
fun HealthBadge(h: Health) {
    val k = when (h) {
        Health.PROFIT -> KKind.OK
        Health.OK -> KKind.WARN
        Health.LOSS -> KKind.BAD
        Health.NONE -> KKind.NEUTRAL
    }
    KPill(h.label, k)
}

/** Rangeen chhota pill (rang ka halka bg). */
@Composable
fun Pill(text: String, color: Color) {
    Box(
        Modifier.height(26.dp).clip(CircleShape).background(color.copy(alpha = .14f)).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { DashText(text, 13.sp, color, weight = FontWeight.SemiBold) }
}

/** Label-value ki ek line (kit KKv, 48dp). */
@Composable
fun KV(label: String, value: String, valueColor: Color? = null, bold: Boolean = false) {
    KKv(label, value, valueColor = valueColor ?: Color.Unspecified)
}

@Composable
fun Hairline() { HorizontalDivider(color = Mfc.c.line, thickness = 1.dp) }

/** Table: header + rows; pehla column text, baaki right. Zyada columns = side scroll. onRow = row tap (48dp). */
@Composable
fun Table(headers: List<String>, rows: List<List<String>>, empty: String = "No data".t(), firstWeight: Float = 1.6f, onRow: ((Int) -> Unit)? = null, minColWidth: Dp = 64.dp) {
    val c = Mfc.c
    if (rows.isEmpty()) {
        if (empty.isNotBlank()) DashText(empty, 14.sp, c.ink3, Modifier.padding(vertical = 8.dp), maxLines = 2)
        return
    }
    val wide = headers.size > 4
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().let { if (wide) it.horizontalScroll(scroll) else it }) {
        @Composable fun RowScope.cell(s: String, i: Int, header: Boolean) {
            val m = if (wide) Modifier.width(if (i == 0) minColWidth * 2 else minColWidth) else if (i == 0) Modifier.weight(firstWeight) else Modifier.weight(1f)
            DashText(
                s, if (header) 13.sp else 14.sp, if (header) c.ink3 else c.ink, m.padding(horizontal = 3.dp),
                weight = if (header) FontWeight.SemiBold else if (i == 0) FontWeight.Medium else FontWeight.Normal,
                maxLines = if (i == 0 && !header) 2 else 1, align = if (i == 0) TextAlign.Start else TextAlign.End,
            )
        }
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            headers.forEachIndexed { i, h -> cell(h, i, true) }
            if (onRow != null) Spacer(Modifier.width(18.dp))
        }
        Hairline()
        rows.forEachIndexed { ri, r ->
            val rm = if (onRow != null) Modifier.heightIn(min = 48.dp).clickable { onRow(ri) } else Modifier.heightIn(min = 40.dp)
            Row(rm.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                r.forEachIndexed { i, s -> cell(s, i, false) }
                if (onRow != null) Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.size(18.dp))
            }
            if (ri < rows.lastIndex) Hairline()
        }
    }
}

/** ROAS patti: kit ke 3 zone (laal < min(target,100%) | amber | hara >= max(target,100%)) + dot. */
@Composable
fun RoasBar(roas: Double?, target: Double, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val c = Mfc.c
    val t = if (target > 0 && target.isFinite()) target else 1.0
    val lo = min(t, 1.0)
    val hi = max(t, 1.0)
    val r = roas?.takeIf { it.isFinite() }
    val maxV = max(hi * 2.0, 1.3)
    val dotCol = when (kRoasKind(r, t)) {
        KKind.OK -> c.zGreen
        KKind.WARN -> c.zAmber
        KKind.BAD -> c.zRed
        else -> c.zGrey
    }
    val zr = c.zRed; val za = c.zAmber; val zg = c.zGreen; val ring = c.dotRing
    Canvas(modifier.fillMaxWidth().height(height + 8.dp)) {
        val w = size.width
        val h = height.toPx()
        val y = (size.height - h) / 2f
        val gap = 1.5.dp.toPx()
        val x1 = (lo / maxV).toFloat() * w
        val x2 = (hi / maxV).toFloat() * w
        val rad = CornerRadius(h / 2f)
        val lEnd = max(0f, x1 - gap)
        if (lEnd > 0f) {
            drawRoundRect(zr, Offset(0f, y), Size(lEnd, h), rad)
            if (lEnd > h / 2f) drawRect(zr, Offset(lEnd - h / 2f, y), Size(h / 2f, h))
        }
        if (x2 - x1 > gap * 2) drawRect(za, Offset(x1 + gap, y), Size(x2 - x1 - gap * 2, h))
        val rS = min(w, (if (x2 - x1 > gap * 2) x2 else x1) + gap)
        if (w - rS > 0f) {
            drawRoundRect(zg, Offset(rS, y), Size(w - rS, h), rad)
            if (w - rS > h / 2f) drawRect(zg, Offset(rS, y), Size(h / 2f, h))
        }
        if (r != null) {
            val rr = size.height / 2f
            val cx = ((r / maxV).toFloat() * w).coerceIn(rr, max(rr, w - rr))
            drawCircle(ring, rr, Offset(cx, size.height / 2f))
            drawCircle(dotCol, rr - 2.5.dp.toPx(), Offset(cx, size.height / 2f))
        }
    }
}

/** Bars (revenue) + dashed line (spend) + doosri line (installs). Neeche labels. Kit ke rang. */
@Composable
fun BarLineChart(
    labels: List<String>, bars: List<Double>, line: List<Double>? = null, line2: List<Double>? = null,
    barColor: Color = Mfc.c.brand, lineColor: Color = Mfc.c.zAmber, line2Color: Color = Mfc.c.zGreen,
    height: Dp = 150.dp, modifier: Modifier = Modifier,
) {
    val c = Mfc.c
    val grid = c.line
    val n = bars.size
    if (n == 0) { DashText("No data".t(), 14.sp, c.ink3, Modifier.padding(vertical = 8.dp)); return }
    fun fin(v: Double) = if (v.isFinite()) v else 0.0
    val maxV = max(bars.maxOf { fin(it) }, line?.maxOfOrNull { fin(it) } ?: 0.0).takeIf { it > 0 } ?: 1.0
    val max2 = (line2?.maxOfOrNull { fin(it) } ?: 0.0).takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width; val h = size.height
            for (g in 1..3) { val y = h - h * g / 4f; drawLine(grid, Offset(0f, y), Offset(w, y), 1.dp.toPx()) }
            drawLine(c.tick, Offset(0f, h), Offset(w, h), 1.5.dp.toPx())
            val slot = w / n; val bw = (slot * 0.62f).coerceAtMost(30.dp.toPx())
            bars.forEachIndexed { i, v ->
                val bh = (fin(v) / maxV).toFloat().coerceIn(0f, 1f) * h
                if (bh > 0f) drawRoundRect(barColor, Offset(i * slot + (slot - bw) / 2, h - bh), Size(bw, bh), CornerRadius(4.dp.toPx()))
            }
            fun path(vals: List<Double>, mx: Double, color: Color, dashed: Boolean) {
                val pts = vals.mapIndexed { i, v -> Offset(i * slot + slot / 2, h - (fin(v) / mx).toFloat().coerceIn(0f, 1f) * h) }
                for (i in 1 until pts.size) drawLine(
                    color, pts[i - 1], pts[i], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
                )
                pts.forEach { drawCircle(color, 2.5.dp.toPx(), it) }
            }
            line?.let { path(it, maxV, lineColor, true) }
            line2?.let { path(it, max2, line2Color, false) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val step = max(1, n / 6)
            labels.forEachIndexed { i, l ->
                DashText(if (i % step == 0) l else "", 12.sp, c.ink3, Modifier.weight(1f), align = TextAlign.Center)
            }
        }
    }
}

/** Chart legend: chhota gol-kona square + naam. */
@Composable
fun Legend(vararg items: Pair<String, Color>) {
    val c = Mfc.c
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (label, col) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(col))
                DashText(label, 13.sp, c.ink2)
            }
        }
    }
}

/** Horizontal bars: label, value text, share 0..1. */
@Composable
fun HBars(items: List<Triple<String, String, Double>>, color: Color = Mfc.c.brand) {
    val c = Mfc.c
    items.forEach { (label, value, share) ->
        Column(Modifier.padding(vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DashText(label, 14.sp, c.ink, Modifier.weight(1f))
                DashText(value, 14.sp, c.ink, Modifier.padding(start = 8.dp), weight = FontWeight.SemiBold)
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.zTrack)) {
                val f = if (share.isFinite()) share.coerceIn(0.0, 1.0).toFloat() else 0f
                if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
            }
        }
    }
}

/** Brand gradient card (kit hero jaisa, 24dp kone). Andar safed tokens: har theme me safed text padh sakte. */
@Composable
fun GradientHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    val top = mfcColors(ThemeStyle.BRAND, top = true)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Mfc.gradient).padding(horizontal = 20.dp, vertical = 16.dp)) {
        CompositionLocalProvider(LocalMfc provides top, LocalContentColor provides Color.White) {
            DashText(title, 15.sp, top.ink2, weight = FontWeight.SemiBold)
            if (subtitle != null) DashText(subtitle, 13.sp, top.ink3, maxLines = 2)
            content()
        }
    }
}

/** Alert / link row: rangeen tile + title + sub + chevron (card par). */
@Composable
fun AlertRow(icon: String, text: String, sub: String, color: Color, onClick: () -> Unit) {
    val c = Mfc.c
    val shape = RoundedCornerShape(22.dp)
    var m = Modifier.fillMaxWidth().clip(shape).background(c.card)
    if (c.cardBd.alpha > 0f) m = m.border(1.dp, c.cardBd, shape)
    Row(
        m.clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            DashText(icon, 15.sp, color, weight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            DashText(text, 15.sp, c.ink, weight = FontWeight.SemiBold, maxLines = 2)
            if (sub.isNotBlank()) DashText(sub, 13.sp, c.ink2, Modifier.padding(top = 2.dp), maxLines = 2)
        }
        Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun Empty(text: String) { DashText(text, 14.sp, Mfc.c.ink3, Modifier.padding(vertical = 8.dp), maxLines = 2) }

/** Heat cell: target par = hara, aadhe se upar = amber, neeche = laal (kit pill rang). */
@Composable
fun HeatCell(v: Double?, target: Double, text: String, modifier: Modifier = Modifier) {
    val c = Mfc.c
    val (bg, fg) = when {
        v == null || !v.isFinite() -> c.pNBg to c.ink3
        v >= target -> c.pOkBg to c.pOkFg
        v >= target * .5 -> c.pWarnBg to c.pWarnFg
        else -> c.pBadBg to c.pBadFg
    }
    Box(modifier.padding(2.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
        DashText(text, 13.sp, fg, weight = FontWeight.SemiBold, align = TextAlign.Center)
    }
}

fun shareOf(v: Double, total: Double) = if (total > 0) v / total else 0.0
fun pctText(v: Double) = num(v * 100, 0) + "%"
