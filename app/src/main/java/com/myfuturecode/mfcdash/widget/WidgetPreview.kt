package com.myfuturecode.mfcdash.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfuturecode.mfcdash.R
import com.myfuturecode.mfcdash.data.TopZone
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.theme.Brand1
import com.myfuturecode.mfcdash.ui.theme.Brand2
import com.myfuturecode.mfcdash.ui.theme.Brand3
import com.myfuturecode.mfcdash.ui.theme.MfcAngleGradient
import com.myfuturecode.mfcdash.util.t
import kotlin.math.max

// Widgets screen ka preview: home screen wale widget (WidgetUi.kt) ki Compose nakal, wahi WView + plan,
// isliye dono ek jaise dikhte hain. Text ki line 1.32 em (TextView ki font padding jaisi).

internal val WBrandBrush: Brush = MfcAngleGradient(135f, listOf(0f to Brand1, .52f to Brand2, 1f to Brand3))

/** Home screen wale widget jaisa card. onClick = Widgets screen par "Badlo". */
@Composable
internal fun WPreview(v: WView, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val p = remember(v.style) { wPal(v.style) }
    val shape = RoundedCornerShape(24.dp)
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    var m = modifier.shadow(4.dp, shape).clip(shape)
    val tint = wTint(v)
    m = when {
        p.brand -> m.background(WBrandBrush)
        p.dark -> m.background(p.bg).border(1.dp, Color(0xFF2A2640), shape)
        tint != null -> m.background(Brush.verticalGradient(0f to tint.top, tint.stop to Color.White, 1f to Color.White))
        else -> m.background(p.bg)
    }
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    BoxWithConstraints(m) {
        val w = maxWidth.value
        val h = maxHeight.value
        when {
            v.login -> WpLogin(v, p, w, scale)
            v.kind == WKind.PAISA -> WpPaisa(v, p, w, h, scale)
            v.kind == WKind.ADS -> WpTop(v, p, w, h, scale)
            v.kind == WKind.HEALTH -> WpHealth(v, p, w, h, scale)
            else -> WpAlerts(v, p, w, h, scale)
        }
    }
}

/** Ek line text, widget wale size / weight par (pehle se kata hua; phir bhi lamba ho to "…"). */
@Composable
private fun WpT(
    text: String, sp: Float, c: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start, under: Boolean = false,
) {
    Text(
        text, modifier, color = c, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontSize = sp.sp, fontWeight = weight, lineHeight = (sp * 1.32f).sp, fontFeatureSettings = "tnum", textAlign = align,
            textDecoration = if (under) TextDecoration.Underline else null,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
    )
}

private val PAD = Modifier.padding(horizontal = W_PH.dp, vertical = W_PV.dp)

private fun Modifier.wpRound(c: Color, r: Float): Modifier = clip(RoundedCornerShape(r.dp)).background(c)

private fun wpStatusIcon(k: KKind): ImageVector? = when (k) {
    KKind.OK -> KIcons.Ok
    KKind.WARN -> KIcons.Dhyan
    KKind.BAD -> KIcons.Turant
    KKind.ASK -> KIcons.Wait
    KKind.NEUTRAL -> null
}

private fun wpMarkIcon(k: KKind): ImageVector = when (k) {
    KKind.BAD -> KIcons.Dia
    KKind.WARN -> KIcons.Dhyan
    KKind.OK -> KIcons.Ok
    KKind.ASK -> KIcons.Wait
    KKind.NEUTRAL -> KIcons.Info
}

@Composable
private fun WpPill(pm: WPillM, p: WPal, full: Boolean, scale: Float) {
    val (bg, fg) = p.pill(pm.kind)
    val icon = wpStatusIcon(pm.kind)
    Row(
        Modifier.height(wPillH(scale).dp).clip(CircleShape).background(bg).padding(start = 6.dp, end = if (full || icon == null) 9.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, if (full) null else pm.text, tint = fg, modifier = Modifier.size(14.dp))
        if (full || icon == null) {
            if (icon != null) Spacer(Modifier.width(4.dp))
            WpT(pm.text, 12.5f, fg, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WpSegRow(segs: List<WSeg>, h: Float, barH: Float, r: Float, mark: @Composable (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().height(h.dp), verticalAlignment = Alignment.CenterVertically) {
        segs.forEachIndexed { i, s ->
            if (s.mark) {
                mark(s.w)
            } else {
                var m = Modifier.height(barH.dp)
                m = if (i == segs.lastIndex) m.weight(1f) else m.width(s.w.dp)
                val c = s.color
                if (c != null) m = m.wpRound(c, r)
                Spacer(m)
            }
        }
    }
}

@Composable
private fun WpBarPill(text: String, z: TopZone, p: WPal, w: Float) {
    val (bg, fg) = p.barPill(z)
    Box(Modifier.width(w.dp).wpRound(bg, 7f), contentAlignment = Alignment.Center) {
        WpT(text, 9.5f, fg, weight = FontWeight.Bold, align = TextAlign.Center)
    }
}

@Composable
private fun WpVLine(p: WPal, h: Float?) {
    val m = Modifier.width(1.dp).background(p.line)
    Spacer(if (h == null) m.fillMaxHeight() else m.height(h.dp))
}

/** Hara dot (7dp) + 5dp jagah: Live ke aage. */
@Composable
private fun WpLiveDot(p: WPal) {
    Spacer(Modifier.size(7.dp).wpRound(p.live, 3.5f))
    Spacer(Modifier.width(5.dp))
}

@Composable
private fun WpMark() {
    Image(painterResource(R.drawable.mfc_mark), null, Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)))
}

// ---------------------------------------------------------------- login

@Composable
private fun WpLogin(v: WView, p: WPal, w: Float, scale: Float) {
    val head = wHeadPlan(v, w - 2 * W_PH, scale)
    Column(Modifier.fillMaxSize().then(PAD)) {
        Row(Modifier.fillMaxWidth().height(wHdr(scale).dp), verticalAlignment = Alignment.CenterVertically) {
            if (head.mark) {
                WpMark()
                Spacer(Modifier.width(7.dp))
            }
            WpT(head.title, 14f, p.ink, weight = FontWeight.Medium)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            WpT("Log in".t(), 18f, p.ink, weight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- Paisa

@Composable
private fun WpPaisa(v: WView, p: WPal, w: Float, h: Float, scale: Float) {
    val pv = v.paisa ?: return
    val plan = wPaisaPlan(v, w, h, scale)
    val inner = w - 2 * W_PH
    val head = plan.head
    Column(Modifier.fillMaxSize().then(PAD)) {
        Row(Modifier.fillMaxWidth().height(wHdr(scale).dp), verticalAlignment = Alignment.CenterVertically) {
            if (head.mark) {
                WpMark()
                Spacer(Modifier.width(7.dp))
            }
            WpT(head.title, 14f, p.ink, Modifier.weight(1f), weight = FontWeight.Medium)
            val live = head.live
            if (live != null) {
                Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    WpLiveDot(p)
                    WpT(live, 12f, p.ink2, weight = FontWeight.Medium)
                }
            }
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(KIcons.Refresh, null, tint = p.ink3, modifier = Modifier.size(16.dp))
                if (head.time) {
                    Spacer(Modifier.width(5.dp))
                    WpT(v.time, 12f, if (v.stale) p.amber else p.ink3)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        val pill = pv.pill
        if (plan.narrow) {
            WpT(pv.big, plan.bigSp, p.tone(pv.bigTone), weight = FontWeight.Bold)
            if (plan.roasLine) {
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth().height(max(wLine(17f, scale), 24f).dp), verticalAlignment = Alignment.CenterVertically) {
                    WpT("ROAS", 12f, p.ink3)
                    Spacer(Modifier.width(6.dp))
                    WpT(pv.roas, 17f, p.zoneText(pv.zone), weight = FontWeight.Bold)
                    if (pill != null && plan.pill > 0) {
                        Spacer(Modifier.width(6.dp))
                        WpPill(pill, p, full = plan.pill == 2, scale)
                    }
                }
            }
            if (plan.meter) {
                Spacer(Modifier.height(4.dp))
                WpPaisaMeter(pv, p, inner)
            }
            Spacer(Modifier.weight(1f))
            val rowH = wLine(13f, scale) + 2f
            wPaisaRows(pv, head, plan.stats).forEach { s ->
                Row(Modifier.fillMaxWidth().height(rowH.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (s.live) WpLiveDot(p)
                    WpT(s.label, 12f, p.ink3, Modifier.weight(1f))
                    WpT(s.value, plan.statSp, p.ink, weight = FontWeight.Bold)
                }
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            WpT(pv.big, plan.bigSp, p.tone(pv.bigTone), Modifier.weight(1f), weight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                if (plan.roasLabel) WpT("ROAS", 12f, p.ink3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!plan.roasLabel) {
                        WpT("ROAS", 12f, p.ink3)
                        Spacer(Modifier.width(6.dp))
                    }
                    WpT(pv.roas, 22f, p.zoneText(pv.zone), weight = FontWeight.Bold)
                    if (pill != null && plan.pill > 0) {
                        Spacer(Modifier.width(6.dp))
                        WpPill(pill, p, full = plan.pill == 2, scale)
                    }
                }
            }
        }
        if (plan.meter) {
            Spacer(Modifier.height(4.dp))
            WpPaisaMeter(pv, p, inner)
        }
        Spacer(Modifier.weight(1f))
        if (plan.stats > 0) {
            val statsH = wLine(plan.labSp, scale) + wLine(plan.statSp, scale)
            Row(Modifier.fillMaxWidth()) {
                pv.stats.take(plan.stats).forEachIndexed { i, s ->
                    if (i > 0) {
                        Spacer(Modifier.width(plan.sep.dp))
                        WpVLine(p, statsH)
                        Spacer(Modifier.width(plan.sep.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        WpT(wFit(s.label, plan.colW, plan.labSp, scale), plan.labSp, p.ink3)
                        WpT(s.value, plan.statSp, p.ink, weight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WpPaisaMeter(pv: WPaisaV, p: WPal, width: Float) {
    WpSegRow(wMeterSegs(width, pv, p), 12f, 6f, 3f) { mw ->
        Spacer(Modifier.size(mw.dp).clip(CircleShape).background(p.dot(pv.zone)))
    }
}

// ---------------------------------------------------------------- Ads + Live

@Composable
private fun WpTop(v: WView, p: WPal, w: Float, h: Float, scale: Float) {
    val tv = v.top ?: return
    val plan = wTopPlan(w, h, scale, tv.set)
    val barH = wBarH(scale)
    Row(Modifier.fillMaxSize().padding(horizontal = W_PH2.dp, vertical = W_PV.dp)) {
        if (!plan.liveOnly) {
            val left = if (plan.two) Modifier.width(plan.leftW.dp) else Modifier.weight(1f)
            Column(left.fillMaxHeight()) {
                WpTopHead("Ads spend".t(), plan.moreL, p, scale)
                if (plan.labels && tv.set) WpTopLabels("today".t(), "budget %".t(), p)
                if (!tv.set) {
                    Spacer(Modifier.height(8.dp))
                    WpT("Not set".t(), 13f, p.ink3, weight = FontWeight.Medium)
                } else {
                    WpTopRows(plan.rows) { i ->
                        val a = tv.accs.getOrNull(i)
                        val amount = a?.amount ?: W_DASH
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            WpT(amount, 14f, p.ink, weight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            val room = plan.leftW - wTextW(amount, 14f, scale, true) - 6f
                            WpT(wFit(a?.name.orEmpty(), room, 11.5f, scale), 11.5f, p.ink2, Modifier.weight(1f), FontWeight.Medium, TextAlign.End)
                        }
                        Spacer(Modifier.height(2.dp))
                        WpSegRow(wBudgetSegs(plan.leftW, a, p, scale), barH, 4f, 2f) { mw ->
                            if (a != null) WpBarPill(a.pct, a.zone, p, mw)
                        }
                    }
                }
            }
        }
        if (plan.two) {
            Spacer(Modifier.width(W_SEP.dp))
            WpVLine(p, null)
            Spacer(Modifier.width(W_SEP.dp))
        }
        if (plan.two || plan.liveOnly) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                WpTopHead("Live users".t(), plan.moreR, p, scale)
                if (plan.labels) WpTopLabels("6 min", "30 min", p)
                WpTopRows(plan.rows) { i ->
                    val a = tv.apps.getOrNull(i)
                    val n6 = a?.n6 ?: W_DASH
                    val n30 = a?.n30 ?: ""
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WpT(n6, 14f, p.ink, weight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        val room = plan.rightW - wTextW(n6, 14f, scale, true) - wTextW(n30, 14f, scale, true) - 12f
                        WpT(wFit(a?.name.orEmpty(), room, 11.5f, scale), 11.5f, p.ink2, Modifier.weight(1f), FontWeight.Medium, TextAlign.Center)
                        Spacer(Modifier.width(6.dp))
                        WpT(n30, 14f, p.ink2, weight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(2.dp))
                    WpSegRow(wRoasSegs(plan.rightW, a, p, scale), barH, 4f, 2f) { mw ->
                        if (a != null) WpBarPill(a.roas, a.zone, p, mw)
                    }
                }
            }
        }
    }
}

@Composable
private fun WpTopHead(title: String, more: Boolean, p: WPal, scale: Float) {
    Row(Modifier.fillMaxWidth().height(wHdr(scale).dp), verticalAlignment = Alignment.CenterVertically) {
        WpT(title, 12.5f, p.ink, Modifier.weight(1f), FontWeight.Medium)
        WpT(if (more) "View more".t() + " ›" else "›", 11f, p.link, weight = FontWeight.Bold, under = p.brand && more)
    }
}

@Composable
private fun WpTopLabels(a: String, b: String, p: WPal) {
    Row(Modifier.fillMaxWidth().padding(top = 1.dp)) {
        WpT(a, 10.5f, p.ink3, Modifier.weight(1f), FontWeight.Medium)
        WpT(b, 10.5f, p.ink3, weight = FontWeight.Medium)
    }
}

@Composable
private fun ColumnScope.WpTopRows(n: Int, row: @Composable ColumnScope.(Int) -> Unit) {
    for (i in 0 until n) {
        Spacer(Modifier.weight(1f))
        row(i)
        Spacer(Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- App Health

@Composable
private fun WpHealth(v: WView, p: WPal, w: Float, h: Float, scale: Float) {
    val hv = v.health ?: return
    val plan = wHealthPlan(v, w, h, scale)
    val inner = w - 2 * W_PH
    Column(Modifier.fillMaxSize().then(PAD)) {
        WpIconHead(if (plan.icon) painterResource(R.drawable.widget_ic_pulse) else null, v.title, p, scale) {}
        if (plan.tri) {
            Spacer(Modifier.height(2.dp))
            val triH = wLine(plan.triSp, scale) + (if (plan.triLabels) wLine(11.5f, scale) else 0f)
            Row(Modifier.fillMaxWidth()) {
                WpTriCell(hv.total, "apps".t(), p.ink, p, plan)
                WpVLine(p, triH)
                Spacer(Modifier.width(9.dp))
                WpTriCell(hv.bad, "issue".t(), p.red, p, plan)
                WpVLine(p, triH)
                Spacer(Modifier.width(9.dp))
                WpTriCell(hv.fine, "fine".t(), p.green, p, plan)
            }
        }
        if (plan.rows == 0) Spacer(Modifier.weight(1f))
        val bh = wBadgeH(scale)
        WpTopRows(plan.rows) { i ->
            val r = hv.rows[i]
            val (bg, fg) = p.pill(if (r.ok) KKind.OK else KKind.BAD)
            val bw = max(28f, wTextW(r.count, 12.5f, scale, true) + 8f)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(bw.dp).height(bh.dp).wpRound(bg, 6f), contentAlignment = Alignment.Center) {
                    WpT(r.count, 12.5f, fg, weight = FontWeight.Bold)
                }
                Spacer(Modifier.width(7.dp))
                WpT(wFit(r.name, inner - bw - 7f, 12.5f, scale, true), 12.5f, p.ink, weight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun RowScope.WpTriCell(value: String, label: String, c: Color, p: WPal, plan: WHealthPlan) {
    Column(Modifier.weight(1f)) {
        WpT(value, plan.triSp, c, weight = FontWeight.Bold)
        if (plan.triLabels) WpT(label, 11.5f, p.ink3, weight = FontWeight.Medium)
    }
}

@Composable
private fun WpIconHead(icon: Painter?, title: String, p: WPal, scale: Float, end: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(wHdr(scale).dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = p.ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
        }
        WpT(title, 14f, p.ink, Modifier.weight(1f), FontWeight.Medium)
        end()
    }
}

// ---------------------------------------------------------------- Alerts

@Composable
private fun WpAlerts(v: WView, p: WPal, w: Float, h: Float, scale: Float) {
    val av = v.alerts ?: return
    val plan = wAlertsPlan(v, w, h, scale)
    val inner = w - 2 * W_PH
    Column(Modifier.fillMaxSize().then(PAD)) {
        WpIconHead(if (plan.icon) rememberVectorPainter(KIcons.Bell) else null, v.title, p, scale) {
            val u = av.unread
            if (u != null && plan.pill) {
                val (bg, fg) = p.pill(KKind.BAD)
                Box(Modifier.wpRound(bg, 11f).padding(horizontal = 8.dp, vertical = 3.dp)) { WpT(u, 12f, fg, weight = FontWeight.Bold) }
            }
        }
        WpTopRows(plan.rows.coerceAtMost(av.rows.size)) { i ->
            val r = av.rows[i]
            val dash = r.name == W_DASH
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Icon(
                    wpMarkIcon(r.kind), null, tint = if (dash) p.ink3 else p.mark(r.kind),
                    modifier = Modifier.padding(top = 2.dp).size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    WpT(wFit(r.name, inner - 23f, 13f, scale, true), 13f, p.ink, weight = FontWeight.Bold)
                    if (plan.sub && r.label.isNotEmpty()) WpT(wFit(r.label, inner - 23f, 12f, scale), 12f, p.ink3, weight = FontWeight.Medium)
                }
            }
        }
    }
}
