package com.myfuturecode.mfcdash.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfuturecode.mfcdash.data.Health
import com.myfuturecode.mfcdash.ui.theme.BrandGradient
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.num
import kotlin.math.max

val CardShape = RoundedCornerShape(16.dp)

@Composable
fun SectionTitle(title: String, sub: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outline, CardShape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(14.dp)
    Column(m, content = content)
}

/** Chhota KPI tile: label, value, sub-line, optional delta. */
@Composable
fun Tile(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier, valueColor: Color? = null, hi: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (hi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 3 tiles ki row (ya jitne do). */
@Composable
fun TileGrid(items: List<Triple<String, String, String?>>, cols: Int = 3, colors: Map<Int, Color> = emptyMap()) {
    items.chunked(cols).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEachIndexed { i, t ->
                val idx = items.indexOf(t)
                Tile(t.first, t.second, t.third, Modifier.weight(1f), valueColor = colors[idx])
            }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun MChip(text: String, on: Boolean, onClick: () -> Unit, dot: Color? = null, trailing: String? = null) {
    val bg = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).background(bg).border(1.dp, if (on) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).height(34.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
        if (trailing != null) { Spacer(Modifier.width(6.dp)); Text(trailing, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = .75f)) }
    }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun HealthBadge(h: Health) {
    val ex = MaterialTheme.extra
    val (bg, fg) = when (h) {
        Health.PROFIT -> ex.good.copy(alpha = .15f) to ex.good
        Health.OK -> ex.warn.copy(alpha = .18f) to ex.warn
        Health.LOSS -> ex.bad.copy(alpha = .15f) to ex.bad
        Health.NONE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(h.label, style = MaterialTheme.typography.labelSmall, color = fg, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun Pill(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

/** Label-value ki ek line (detail lists ke liye). */
@Composable
fun KV(label: String, value: String, valueColor: Color? = null, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
fun Hairline() { HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp) }

/** Simple table: header + rows; pehla column text (weight 1.4), baaki right-aligned. Horizontal scroll agar zyada columns. */
@Composable
fun Table(headers: List<String>, rows: List<List<String>>, empty: String = "Koi data nahi", firstWeight: Float = 1.6f, onRow: ((Int) -> Unit)? = null, minColWidth: Dp = 64.dp) {
    if (rows.isEmpty()) { Text(empty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)); return }
    val wide = headers.size > 4
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxWidth().let { if (wide) it.horizontalScroll(scroll) else it }) {
        @Composable fun RowScope.cell(t: String, i: Int, header: Boolean) {
            val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium
            val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            val m = if (wide) Modifier.width(if (i == 0) minColWidth * 2 else minColWidth) else if (i == 0) Modifier.weight(firstWeight) else Modifier.weight(1f)
            Text(if (header) t.uppercase() else t, style = style, color = color, maxLines = if (i == 0) 2 else 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End, modifier = m.padding(horizontal = 3.dp))
        }
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { headers.forEachIndexed { i, h -> cell(h, i, true) } }
        Hairline()
        rows.forEachIndexed { ri, r ->
            Row(Modifier.let { if (onRow != null) it.clickable { onRow(ri) } else it }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                r.forEachIndexed { i, c -> cell(c, i, false) }
                if (onRow != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            if (ri < rows.lastIndex) Hairline()
        }
    }
}

/** Progress bar jisme break-even (100%) aur target ka nishaan ho: ROAS ke liye. */
@Composable
fun RoasBar(roas: Double?, target: Double, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val ex = MaterialTheme.extra
    val maxV = max(target * 1.25, (roas ?: 0.0) * 1.1).coerceAtLeast(1.3)
    val color = when { roas == null -> ex.muted; roas >= target -> ex.good; roas >= 1.0 -> ex.warn; else -> ex.bad }
    val track = MaterialTheme.colorScheme.outline
    Canvas(modifier.fillMaxWidth().height(height + 8.dp)) {
        val w = size.width; val h = height.toPx(); val y = 4.dp.toPx()
        drawRoundRect(track, Offset(0f, y), Size(w, h), CornerRadius(h / 2))
        val fw = ((roas ?: 0.0) / maxV).coerceIn(0.0, 1.0).toFloat() * w
        if (fw > 0) drawRoundRect(color, Offset(0f, y), Size(fw, h), CornerRadius(h / 2))
        val be = (1.0 / maxV).toFloat() * w
        drawLine(ex.muted, Offset(be, 0f), Offset(be, h + 8.dp.toPx()), strokeWidth = 2.dp.toPx())
        val tg = (target / maxV).toFloat() * w
        drawLine(ex.good, Offset(tg, 0f), Offset(tg, h + 8.dp.toPx()), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
    }
}

/** Bars (revenue) + optional dashed line (spend) + optional second line (installs). Labels neeche. */
@Composable
fun BarLineChart(
    labels: List<String>, bars: List<Double>, line: List<Double>? = null, line2: List<Double>? = null,
    barColor: Color = MaterialTheme.colorScheme.primary, lineColor: Color = MaterialTheme.extra.warn, line2Color: Color = MaterialTheme.extra.good,
    height: Dp = 150.dp, modifier: Modifier = Modifier,
) {
    val grid = MaterialTheme.colorScheme.outline
    val n = bars.size
    if (n == 0) { Text("Graph ke liye data nahi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val maxV = max(bars.maxOrNull() ?: 0.0, line?.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
    val max2 = (line2?.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width; val h = size.height
            for (g in 1..3) { val y = h - h * g / 4f; drawLine(grid, Offset(0f, y), Offset(w, y), 1f) }
            val slot = w / n; val bw = slot * 0.62f
            bars.forEachIndexed { i, v ->
                val bh = (v / maxV).toFloat() * h
                drawRoundRect(barColor, Offset(i * slot + (slot - bw) / 2, h - bh), Size(bw, bh), CornerRadius(3.dp.toPx()))
            }
            fun path(vals: List<Double>, mx: Double, color: Color, dashed: Boolean) {
                val pts = vals.mapIndexed { i, v -> Offset(i * slot + slot / 2, h - (v / mx).toFloat() * h) }
                for (i in 1 until pts.size) drawLine(color, pts[i - 1], pts[i], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null)
                pts.forEach { drawCircle(color, 2.5.dp.toPx(), it) }
            }
            line?.let { path(it, maxV, lineColor, true) }
            line2?.let { path(it, max2, line2Color, false) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val step = max(1, n / 6)
            labels.forEachIndexed { i, l ->
                Text(if (i % step == 0) l else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

@Composable
fun Legend(vararg items: Pair<String, Color>) {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (t, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(5.dp))
                Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Horizontal bar list: label, value text, share 0..1 */
@Composable
fun HBars(items: List<Triple<String, String, Double>>, color: Color = MaterialTheme.colorScheme.primary) {
    items.forEach { (l, v, share) ->
        Column(Modifier.padding(vertical = 4.dp)) {
            Row { Text(l, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outline)) {
                Box(Modifier.fillMaxWidth(share.coerceIn(0.0, 1.0).toFloat()).height(6.dp).background(color))
            }
        }
    }
}

@Composable
fun GradientHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(BrandGradient).padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .8f))
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .7f))
        content()
    }
}

@Composable
fun AlertRow(icon: String, text: String, sub: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .10f)).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = .2f)), contentAlignment = Alignment.Center) { Text(icon, style = MaterialTheme.typography.labelLarge, color = color) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = color)
    }
}

@Composable
fun Empty(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp)) }

@Composable
fun HeatCell(v: Double?, target: Double, text: String, modifier: Modifier = Modifier) {
    val ex = MaterialTheme.extra
    val bg = when { v == null -> MaterialTheme.colorScheme.surfaceVariant; v >= target -> ex.heat[3]; v >= target * .8 -> ex.heat[2]; v >= target * .5 -> ex.heat[1]; else -> ex.heat[0] }
    Box(modifier.padding(2.dp).clip(RoundedCornerShape(6.dp)).background(bg).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

fun shareOf(v: Double, total: Double) = if (total > 0) v / total else 0.0
fun pctText(v: Double) = num(v * 100, 0) + "%"
