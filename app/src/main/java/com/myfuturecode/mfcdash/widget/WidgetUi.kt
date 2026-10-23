package com.myfuturecode.mfcdash.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.myfuturecode.mfcdash.MainActivity
import com.myfuturecode.mfcdash.R
import com.myfuturecode.mfcdash.data.TopZone
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.util.t
import kotlin.math.max

// Home screen par widget (Glance). Roop WView se, jagah ka hisaab WidgetView.kt ke plan se, isliye Widgets screen
// ka preview (WidgetPreview.kt) bhi bilkul aisa hi dikhta hai.
// Nesting: Android 13+ par har view ek nested RemoteViews hai (limit 10). Root (1) + bg/click wala Box (2) + ... ;
// sabse gehra Paisa ki pill ka icon 8 par. Bar ki pill / dot overlay nahi, usi Row me tukdon ke beech (ring = khaali jagah).
// Android 12+ (S): rang + cornerRadius. Usse purane: safed gol drawable ko tint (cornerRadius wahan nahi chalta).

private val S_PLUS: Boolean get() = Build.VERSION.SDK_INT >= 31

/** Live users ki screen (Home ka "More > Live"). */
private const val W_LIVE_ROUTE = "live"

/** Poora widget. Tap = app ka wahi hissa (Ads + Live me dono aadhe alag). */
@Composable
internal fun WGlanceRoot(v: WView) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val scale = ctx.resources.configuration.fontScale.coerceIn(1f, 2f)
    val w = size.width.value.takeIf { it > 0f } ?: (if (v.kind.wide) 340f else 170f)
    val h = size.height.value.takeIf { it > 0f } ?: 170f
    val p = wPal(v.style)
    var root = GlanceModifier.fillMaxSize().appWidgetBackground()
        .background(ImageProvider(wBgRes(v)), contentScale = ContentScale.FillBounds, colorFilter = null)
    if (S_PLUS) root = root.cornerRadius(24.dp)
    if (v.login || v.kind != WKind.ADS) {
        root = root.clickable(actionStartActivity(wOpenIntent(ctx, if (v.login) null else v.kind.route)))
    }
    when {
        v.login -> WLogin(v, p, w, scale, root)
        v.kind == WKind.PAISA -> WPaisa(v, p, w, h, scale, root)
        v.kind == WKind.ADS -> WTop(v, p, w, h, scale, root)
        v.kind == WKind.HEALTH -> WHealth(v, p, w, h, scale, root)
        else -> WAlerts(v, p, w, h, scale, root)
    }
}

/** Card ka background: Ujla = upar halka rang (widget ke hisaab se), Dark = saada + border, Brand = gradient. */
private fun wBgRes(v: WView): Int = when (v.style) {
    ThemeStyle.BRAND -> R.drawable.widget_bg_brand
    ThemeStyle.DARK -> R.drawable.widget_bg_dark
    ThemeStyle.LIGHT -> when (wTint(v)) {
        WTint.RED -> R.drawable.widget_bg_l_red
        WTint.GREEN -> R.drawable.widget_bg_l_green
        WTint.SAND -> R.drawable.widget_bg_l_sand
        WTint.ROSE -> R.drawable.widget_bg_l_rose
        else -> R.drawable.widget_bg_l_lav
    }
}

/**
 * App kholo: route = wahi screen (MainActivity "route" extra -> pendingRoute, notification jaisa); login nahi to bas app.
 * Har route ka apna data URI, taaki do alag tap (Ads / Live) ka PendingIntent ek na ho jaye.
 */
private fun wOpenIntent(ctx: Context, route: String?): Intent =
    Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = Uri.parse("mfcwidget://open/" + (route ?: "app"))
        if (route != null) putExtra("route", route)
    }

private fun wFilter(c: Color): ColorFilter = ColorFilter.tint(ColorProvider(c))

private fun wts(
    c: Color, size: Float, weight: FontWeight = FontWeight.Normal, align: TextAlign? = null, under: Boolean = false,
): TextStyle = TextStyle(
    color = ColorProvider(c), fontSize = size.sp, fontWeight = weight, textAlign = align,
    textDecoration = if (under) TextDecoration.Underline else null,
)

/** Gol kone wala rang: 12+ par cornerRadius, purane par gol drawable tint. */
private fun GlanceModifier.wRound(c: Color, r: Float): GlanceModifier =
    if (S_PLUS) background(c).cornerRadius(r.dp)
    else background(ImageProvider(R.drawable.widget_pill), contentScale = ContentScale.FillBounds, colorFilter = wFilter(c))

private fun wStatusIcon(k: KKind): Int? = when (k) {
    KKind.OK -> R.drawable.widget_ic_ok
    KKind.WARN -> R.drawable.widget_ic_warn
    KKind.BAD -> R.drawable.widget_ic_bad
    KKind.ASK -> R.drawable.widget_ic_ask
    KKind.NEUTRAL -> null
}

/** Alerts ki line ka nishaan (app ke KKind wale shape): laal diamond, amber triangle, hara check, brand info / ghadi. */
private fun wMarkIcon(k: KKind): Int = when (k) {
    KKind.BAD -> R.drawable.widget_ic_dia
    KKind.WARN -> R.drawable.widget_ic_warn
    KKind.OK -> R.drawable.widget_ic_ok
    KKind.ASK -> R.drawable.widget_ic_ask
    KKind.NEUTRAL -> R.drawable.widget_ic_info
}

// ---------------------------------------------------------------- chhote hisse

/** Haal pill (24dp, bade font par oonchi): icon + shabd; full = false par sirf icon. */
@Composable
private fun WPill(pm: WPillM, p: WPal, full: Boolean, scale: Float) {
    val (bg, fg) = p.pill(pm.kind)
    val icon = wStatusIcon(pm.kind)
    val ph = wPillH(scale)
    Row(
        modifier = GlanceModifier.height(ph.dp).wRound(bg, ph / 2f).padding(start = 6.dp, end = if (full || icon == null) 9.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                ImageProvider(icon), contentDescription = if (full) null else pm.text,
                modifier = GlanceModifier.size(14.dp), colorFilter = wFilter(fg),
            )
        }
        if (full || icon == null) {
            if (icon != null) Spacer(GlanceModifier.width(4.dp))
            Text(pm.text, style = wts(fg, 12.5f, FontWeight.Bold), maxLines = 1)
        }
    }
}

/** Bar / meter: tukde ek Row me (h = Row, barH = patti); mark ki jagah [mark] (pill / dot). Aakhri tukda bachi jagah le. */
@Composable
private fun WSegRow(segs: List<WSeg>, h: Float, barH: Float, r: Float, mark: @Composable (Float) -> Unit) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(h.dp), verticalAlignment = Alignment.CenterVertically) {
        segs.forEachIndexed { i, s ->
            if (s.mark) {
                mark(s.w)
            } else {
                var m = GlanceModifier.height(barH.dp)
                m = if (i == segs.lastIndex) m.defaultWeight() else m.width(s.w.dp)
                val c = s.color
                if (c != null) m = m.wRound(c, r)
                Spacer(m)
            }
        }
    }
}

/** Bar par chhoti pill ("72%"): rang zone ka, ring = dono taraf khaali jagah. */
@Composable
private fun WBarPill(text: String, z: TopZone, p: WPal, w: Float) {
    val (bg, fg) = p.barPill(z)
    Text(
        text, modifier = GlanceModifier.width(w.dp).wRound(bg, 7f),
        style = wts(fg, 9.5f, FontWeight.Bold, TextAlign.Center), maxLines = 1,
    )
}

/** Patli khadi line (column ke beech). */
@Composable
private fun WVLine(p: WPal, h: Float?) {
    val m = GlanceModifier.width(1.dp).background(p.line)
    Spacer(if (h == null) m.fillMaxHeight() else m.height(h.dp))
}

/** Line + dono taraf sep dp jagah, ek hi Box me (Row ka ek bachcha; Glance ek Row me 10 tak hi rakhta hai). */
@Composable
private fun WVSep(p: WPal, h: Float, sep: Float) {
    Box(modifier = GlanceModifier.width((2 * sep + 1f).dp).height(h.dp), contentAlignment = Alignment.Center) { WVLine(p, h) }
}

/** Hara dot (7dp) + 5dp jagah: Live ke aage. */
@Composable
private fun WLiveDot(p: WPal) {
    Spacer(GlanceModifier.size(7.dp).wRound(p.live, 3.5f))
    Spacer(GlanceModifier.width(5.dp))
}

// ---------------------------------------------------------------- login

@Composable
private fun WLogin(v: WView, p: WPal, w: Float, scale: Float, root: GlanceModifier) {
    val head = wHeadPlan(v, w - 2 * W_PH, scale)
    Column(modifier = root.padding(horizontal = W_PH.dp, vertical = W_PV.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth().height(wHdr(scale).dp), verticalAlignment = Alignment.CenterVertically) {
            if (head.mark) {
                WMark()
                Spacer(GlanceModifier.width(7.dp))
            }
            Text(head.title, style = wts(p.ink, 14f, FontWeight.Medium), maxLines = 1)
        }
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
            Text("Log in".t(), style = wts(p.ink, 18f, FontWeight.Bold), maxLines = 1)
        }
    }
}

/** MFC ka nishaan (20dp, gol kone). */
@Composable
private fun WMark() {
    var m = GlanceModifier.size(20.dp)
    if (S_PLUS) m = m.cornerRadius(6.dp)
    Image(ImageProvider(R.drawable.mfc_mark), contentDescription = null, modifier = m)
}

// ---------------------------------------------------------------- Paisa (4x2)

@Composable
private fun WPaisa(v: WView, p: WPal, w: Float, h: Float, scale: Float, root: GlanceModifier) {
    val pv = v.paisa ?: return
    val plan = wPaisaPlan(v, w, h, scale)
    val inner = w - 2 * W_PH
    val head = plan.head
    Column(modifier = root.padding(horizontal = W_PH.dp, vertical = W_PV.dp)) {
        // header: nishaan + "Aaj ka munafa", daayein "● 100 live" (jagah ho to) + refresh + samay (tap = sab widgets naye sire se)
        Row(modifier = GlanceModifier.fillMaxWidth().height(wHdr(scale).dp), verticalAlignment = Alignment.CenterVertically) {
            if (head.mark) {
                WMark()
                Spacer(GlanceModifier.width(7.dp))
            }
            Text(head.title, modifier = GlanceModifier.defaultWeight(), style = wts(p.ink, 14f, FontWeight.Medium), maxLines = 1)
            val live = head.live
            if (live != null) {
                Row(modifier = GlanceModifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    WLiveDot(p)
                    Text(live, style = wts(p.ink2, 12f, FontWeight.Medium), maxLines = 1)
                }
            }
            Row(
                modifier = GlanceModifier.fillMaxHeight().padding(start = 8.dp).clickable(actionRunCallback<WidgetRefresh>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    ImageProvider(R.drawable.widget_ic_refresh), contentDescription = "Refresh".t(),
                    modifier = GlanceModifier.size(16.dp), colorFilter = wFilter(p.ink3),
                )
                if (head.time) {
                    Spacer(GlanceModifier.width(5.dp))
                    Text(v.time, style = wts(if (v.stale) p.amber else p.ink3, 12f), maxLines = 1)
                }
            }
        }
        Spacer(GlanceModifier.height(2.dp))
        val pill = pv.pill
        if (plan.narrow) {
            Text(pv.big, style = wts(p.tone(pv.bigTone), plan.bigSp, FontWeight.Bold), maxLines = 1)
            if (plan.roasLine) {
                Spacer(GlanceModifier.height(2.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(max(wLine(17f, scale), 24f).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ROAS", style = wts(p.ink3, 12f), maxLines = 1)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(pv.roas, style = wts(p.zoneText(pv.zone), 17f, FontWeight.Bold), maxLines = 1)
                    if (pill != null && plan.pill > 0) {
                        Spacer(GlanceModifier.width(6.dp))
                        WPill(pill, p, full = plan.pill == 2, scale)
                    }
                }
            }
            if (plan.meter) {
                Spacer(GlanceModifier.height(4.dp))
                WPaisaMeter(pv, p, inner)
            }
            Spacer(GlanceModifier.defaultWeight())
            val rowH = wLine(13f, scale) + 2f
            // stats ek Column me (Glance: ek Column me 10 bachche tak; yahan 5 tak: 4 khane + Live)
            if (plan.stats > 0) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    wPaisaRows(pv, head, plan.stats).forEach { s ->
                        Row(modifier = GlanceModifier.fillMaxWidth().height(rowH.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (s.live) WLiveDot(p)
                            Text(s.label, modifier = GlanceModifier.defaultWeight(), style = wts(p.ink3, 12f), maxLines = 1)
                            Text(s.value, style = wts(p.ink, plan.statSp, FontWeight.Bold), maxLines = 1)
                        }
                    }
                }
            }
            return@Column
        }
        // bada munafa baayein, ROAS daayein (neeche tika)
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                pv.big, modifier = GlanceModifier.defaultWeight(),
                style = wts(p.tone(pv.bigTone), plan.bigSp, FontWeight.Bold), maxLines = 1,
            )
            Column(horizontalAlignment = Alignment.End) {
                if (plan.roasLabel) Text("ROAS", style = wts(p.ink3, 12f), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!plan.roasLabel) {
                        Text("ROAS", style = wts(p.ink3, 12f), maxLines = 1)
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    Text(pv.roas, style = wts(p.zoneText(pv.zone), 22f, FontWeight.Bold), maxLines = 1)
                    if (pill != null && plan.pill > 0) {
                        Spacer(GlanceModifier.width(6.dp))
                        WPill(pill, p, full = plan.pill == 2, scale)
                    }
                }
            }
        }
        if (plan.meter) {
            Spacer(GlanceModifier.height(4.dp))
            WPaisaMeter(pv, p, inner)
        }
        Spacer(GlanceModifier.defaultWeight())
        if (plan.stats > 0) {
            val statsH = wLine(plan.labSp, scale) + wLine(plan.statSp, scale)
            // 4 khane + 3 line = 7 bachche (line apni jagah samet ek Box me, warna 13 ho jaate)
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                pv.stats.take(plan.stats).forEachIndexed { i, s ->
                    if (i > 0) WVSep(p, statsH, plan.sep)
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(wFit(s.label, plan.colW, plan.labSp, scale), style = wts(p.ink3, plan.labSp), maxLines = 1)
                        Text(s.value, style = wts(p.ink, plan.statSp, FontWeight.Bold), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** ROAS meter: 3 zone (6dp), dot ROAS ki jagah. */
@Composable
private fun WPaisaMeter(pv: WPaisaV, p: WPal, width: Float) {
    WSegRow(wMeterSegs(width, pv, p), 12f, 6f, 3f) { mw ->
        Spacer(GlanceModifier.size(mw.dp).wRound(p.dot(pv.zone), mw / 2f))
    }
}

// ---------------------------------------------------------------- Ads + Live (4x2)

@Composable
private fun WTop(v: WView, p: WPal, w: Float, h: Float, scale: Float, root: GlanceModifier) {
    val tv = v.top ?: return
    val ctx = LocalContext.current
    val plan = wTopPlan(w, h, scale, tv.set)
    Row(modifier = root.padding(horizontal = W_PH2.dp, vertical = W_PV.dp)) {
        if (!plan.liveOnly) {
            // baayan aadha: Google Ads (tap = Google Ads screen)
            var left = GlanceModifier.fillMaxHeight().clickable(actionStartActivity(wOpenIntent(ctx, WKind.ADS.route)))
            left = if (plan.two) left.width(plan.leftW.dp) else left.defaultWeight()
            Column(modifier = left) {
                if (tv.byApp) WTopSpend(tv, plan.leftW, h - 2 * W_PV, plan.two, plan.rows, plan.moreL, p, scale)
                else WTopAds(tv, plan, p, scale)
            }
        }
        if (plan.two) {
            Spacer(GlanceModifier.width(W_SEP.dp))
            WVLine(p, null)
            Spacer(GlanceModifier.width(W_SEP.dp))
        }
        if (plan.two || plan.liveOnly) {
            // daayan aadha: Live users (tap = Live screen)
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    .clickable(actionStartActivity(wOpenIntent(ctx, W_LIVE_ROUTE))),
            ) { WTopLive(tv, plan, p, scale) }
        }
    }
}

/** Ads kharcha wala aadha: top 3 account + budget bar (Ads set nahi = "Set nahi"). */
@Composable
private fun ColumnScope.WTopAds(tv: WTopV, plan: WTopPlan, p: WPal, scale: Float) {
    WTopHead("Ads spend".t(), plan.moreL, p, scale)
    if (plan.labels && tv.set) WTopLabels("today".t(), "budget %".t(), p)
    if (!tv.set) {
        Spacer(GlanceModifier.height(8.dp))
        Text("Not set".t(), style = wts(p.ink3, 13f, FontWeight.Medium), maxLines = 1)
        return
    }
    val barH = wBarH(scale)
    WTopRows(plan.rows) { i ->
        val a = tv.accs.getOrNull(i)
        val amount = a?.amount ?: W_DASH
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(amount, style = wts(p.ink, 14f, FontWeight.Bold), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            val room = plan.leftW - wTextW(amount, 14f, scale, true) - 6f
            Text(
                wFit(a?.name.orEmpty(), room, 11.5f, scale), modifier = GlanceModifier.defaultWeight(),
                style = wts(p.ink2, 11.5f, FontWeight.Medium, TextAlign.End), maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        WSegRow(wBudgetSegs(plan.leftW, a, p, scale), barH, 4f, 2f) { mw ->
            if (a != null) WBarPill(a.pct, a.zone, p, mw)
        }
    }
}

/**
 * Ads kharcha "By app": kul kharcha (+ "aaj · ROAS"), phir top 3 kharch wale apps (kharcha | naam | ROAS %) + aaj ke budget
 * ki patti (% pill + budget). pair = do column: lines Live wale aadhe jitni (liveRows, dono barabar). Line tap (line ka poora
 * hissa) = us app ki screen; baaki aadha = Google Ads (bahar wala Column).
 */
@Composable
private fun ColumnScope.WTopSpend(tv: WTopV, room: Float, ih: Float, pair: Boolean, liveRows: Int, more: Boolean, p: WPal, scale: Float) {
    val ctx = LocalContext.current
    val plan = wSpendPlan(tv, room, ih, scale, if (pair) liveRows else 3, pair)
    WTopHead("Ads spend".t(), more, p, scale)
    // kul + label: neeche tike (label 2dp upar, taaki dono ki line lagbhag ek)
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 1.dp), verticalAlignment = Alignment.Bottom) {
        Text(tv.total, modifier = GlanceModifier.defaultWeight(), style = wts(p.ink, plan.totSp, FontWeight.Bold), maxLines = 1)
        if (plan.label) {
            Text(wSpendLabel(), modifier = GlanceModifier.padding(start = 6.dp, bottom = 2.dp), style = wts(p.ink3, 10.5f, FontWeight.Medium), maxLines = 1)
        }
    }
    if (tv.noSpend) {
        Spacer(GlanceModifier.height(8.dp))
        Text(wSpendNote(), style = wts(p.ink3, 13f, FontWeight.Medium), maxLines = plan.note)
        return
    }
    // tap line ke (weight wale) Column par hi: ek Column kam (RemoteViews ki gehrai), tap ki jagah badi
    val tap: (Int) -> GlanceModifier = { i ->
        val a = tv.spends.getOrNull(i)
        if (a == null) GlanceModifier else GlanceModifier.clickable(actionStartActivity(wOpenIntent(ctx, "app/" + a.pkg)))
    }
    WTopRows(plan.rows, tap) { i ->
        // 3 se kam apps: baaki jagah khaali (lines Live wale aadhe ki seedh me rahein)
        if (i >= tv.spends.size) return@WTopRows
        val a = tv.spends[i]
        val amount = a?.amount ?: W_DASH
        val roas = if (plan.roas) a?.roas.orEmpty() else ""
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(amount, style = wts(p.ink, plan.rowSp, FontWeight.Bold), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            val room2 = room - wTextW(amount, plan.rowSp, scale, true) - (if (roas.isEmpty()) 6f else 12f + wTextW(roas, plan.rowSp, scale, true))
            Text(
                wFit(a?.name.orEmpty(), room2, 11.5f, scale), modifier = GlanceModifier.defaultWeight(),
                style = wts(p.ink2, 11.5f, FontWeight.Medium, TextAlign.Center), maxLines = 1,
            )
            if (roas.isNotEmpty() && a != null) {
                Spacer(GlanceModifier.width(6.dp))
                Text(roas, style = wts(p.zoneText(a.zone), plan.rowSp, FontWeight.Bold), maxLines = 1)
            }
        }
        if (plan.thin) {
            Spacer(GlanceModifier.height(3.dp))
            WSpendThin(a, room, p)
        } else {
            WSpendBud(a, plan, p, scale)
        }
    }
}

/** Tang jagah (plan.thin): sirf 4dp patti, kharch tak budget zone ka rang; budget pata nahi / "—" line = sirf track. */
@Composable
private fun WSpendThin(a: WSpendV?, width: Float, p: WPal) {
    Box(modifier = GlanceModifier.fillMaxWidth().height(W_SPEND_BAR.dp).wRound(p.track, W_SPEND_BAR / 2f)) {
        val f = a?.fill
        if (a != null && f != null && f > 0f) {
            Spacer(GlanceModifier.width((width * f).dp).height(W_SPEND_BAR.dp).wRound(p.zone(a.bzone), W_SPEND_BAR / 2f))
        }
    }
}

/**
 * Budget ki patti (4dp): kharch tak budget zone ka rang, sire par % pill, ant me budget ("₹70K"). Sab ek Row me (pill tukdon
 * ke beech, overlay nahi; gehrai purani patti jitni): tukde trackW tak, bachi jagah budget ki likhai (daayein tiki).
 * Budget pata nahi / "—" line = sirf track + "—".
 */
@Composable
private fun WSpendBud(a: WSpendV?, plan: WSpendPlan, p: WPal, scale: Float) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(wSpendLineH(scale).dp), verticalAlignment = Alignment.CenterVertically) {
        wSpendSegs(plan.trackW, a, p, scale).forEach { s ->
            if (s.mark) {
                if (a != null) WBarPill(a.bpct, a.bzone, p, s.w)
            } else {
                var m = GlanceModifier.width(s.w.dp).height(W_SPEND_BAR.dp)
                val c = s.color
                if (c != null) m = m.wRound(c, W_SPEND_BAR / 2f)
                Spacer(m)
            }
        }
        if (plan.budW > 0f) {
            Text(
                a?.bud ?: W_DASH, modifier = GlanceModifier.defaultWeight().padding(start = 6.dp),
                style = wts(p.ink3, W_BUD_SP, FontWeight.Bold, TextAlign.End), maxLines = 1,
            )
        }
    }
}

/** Live users wala aadha: top 3 app (6 min, naam, 30 min) + ROAS line. */
@Composable
private fun ColumnScope.WTopLive(tv: WTopV, plan: WTopPlan, p: WPal, scale: Float) {
    WTopHead("Live users".t(), plan.moreR, p, scale)
    if (plan.labels) WTopLabels("6 min", "30 min", p)
    val barH = wBarH(scale)
    WTopRows(plan.rows) { i ->
        val a = tv.apps.getOrNull(i)
        val n6 = a?.n6 ?: W_DASH
        val n30 = a?.n30 ?: ""
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(n6, style = wts(p.ink, 14f, FontWeight.Bold), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            val room = plan.rightW - wTextW(n6, 14f, scale, true) - wTextW(n30, 14f, scale, true) - 12f
            Text(
                wFit(a?.name.orEmpty(), room, 11.5f, scale), modifier = GlanceModifier.defaultWeight(),
                style = wts(p.ink2, 11.5f, FontWeight.Medium, TextAlign.Center), maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(n30, style = wts(p.ink2, 14f, FontWeight.Bold), maxLines = 1)
        }
        Spacer(GlanceModifier.height(2.dp))
        WSegRow(wRoasSegs(plan.rightW, a, p, scale), barH, 4f, 2f) { mw ->
            if (a != null) WBarPill(a.roas, a.zone, p, mw)
        }
    }
}

/** Aadhe ka header: naam + daayein "View more ›" (brand rang; Brand theme par underline). */
@Composable
private fun WTopHead(title: String, more: Boolean, p: WPal, scale: Float) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(wHdr(scale).dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = GlanceModifier.defaultWeight(), style = wts(p.ink, 12.5f, FontWeight.Medium), maxLines = 1)
        Text(
            if (more) "View more".t() + " ›" else "›",
            style = wts(p.link, 11f, FontWeight.Bold, under = p.brand && more), maxLines = 1,
        )
    }
}

/** "aaj / budget %" jaisi chhoti line. */
@Composable
private fun WTopLabels(a: String, b: String, p: WPal) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 1.dp)) {
        Text(a, modifier = GlanceModifier.defaultWeight(), style = wts(p.ink3, 10.5f, FontWeight.Medium), maxLines = 1)
        Text(b, style = wts(p.ink3, 10.5f, FontWeight.Medium), maxLines = 1)
    }
}

/**
 * n lines barabar jagah me (CSS flex:1 jaisa: upar aadha gap, beech poora, neeche aadha).
 * Har line apne Column me (beech me tiki): Glance ek Row / Column me 10 se zyada bachche kaat deta hai.
 * tap = line wale Column ka modifier (jaise clickable; na ho = kuch nahi).
 */
@Composable
private fun ColumnScope.WTopRows(n: Int, tap: (Int) -> GlanceModifier = { GlanceModifier }, row: @Composable (Int) -> Unit) {
    for (i in 0 until n) {
        Column(
            modifier = tap(i).fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row(i)
        }
    }
}

// ---------------------------------------------------------------- App Health (2x2)

@Composable
private fun WHealth(v: WView, p: WPal, w: Float, h: Float, scale: Float, root: GlanceModifier) {
    val hv = v.health ?: return
    val plan = wHealthPlan(v, w, h, scale)
    val inner = w - 2 * W_PH
    Column(modifier = root.padding(horizontal = W_PH.dp, vertical = W_PV.dp)) {
        WIconHead(if (plan.icon) R.drawable.widget_ic_pulse else null, v.title, p, scale) {}
        if (plan.tri) {
            Spacer(GlanceModifier.height(2.dp))
            val triH = wLine(plan.triSp, scale) + (if (plan.triLabels) wLine(11.5f, scale) else 0f)
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                WTriCell(hv.total, "apps".t(), p.ink, p, plan)
                WVLine(p, triH)
                Spacer(GlanceModifier.width(9.dp))
                WTriCell(hv.bad, "issue".t(), p.red, p, plan)
                WVLine(p, triH)
                Spacer(GlanceModifier.width(9.dp))
                WTriCell(hv.fine, "fine".t(), p.green, p, plan)
            }
        }
        if (plan.rows == 0) Spacer(GlanceModifier.defaultWeight())
        val bh = wBadgeH(scale)
        // lines bachi jagah me barabar (lamba widget ho to beech me khaali patti nahi)
        WTopRows(plan.rows) { i ->
            val r = hv.rows[i]
            val (bg, fg) = p.pill(if (r.ok) KKind.OK else KKind.BAD)
            val bw = max(28f, wTextW(r.count, 12.5f, scale, true) + 8f)
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.width(bw.dp).height(bh.dp).wRound(bg, 6f), contentAlignment = Alignment.Center) {
                    Text(r.count, style = wts(fg, 12.5f, FontWeight.Bold), maxLines = 1)
                }
                Spacer(GlanceModifier.width(7.dp))
                Text(wFit(r.name, inner - bw - 7f, 12.5f, scale, true), style = wts(p.ink, 12.5f, FontWeight.Medium), maxLines = 1)
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.WTriCell(value: String, label: String, c: Color, p: WPal, plan: WHealthPlan) {
    Column(modifier = GlanceModifier.defaultWeight()) {
        Text(value, style = wts(c, plan.triSp, FontWeight.Bold), maxLines = 1)
        if (plan.triLabels) Text(label, style = wts(p.ink3, 11.5f, FontWeight.Medium), maxLines = 1)
    }
}

/** 2x2 ka header: chhota icon (jagah ho to) + naam (+ daayein kuch). */
@Composable
private fun WIconHead(icon: Int?, title: String, p: WPal, scale: Float, end: @Composable () -> Unit) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(wHdr(scale).dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(16.dp), colorFilter = wFilter(p.ink))
            Spacer(GlanceModifier.width(7.dp))
        }
        Text(title, modifier = GlanceModifier.defaultWeight(), style = wts(p.ink, 14f, FontWeight.Medium), maxLines = 1)
        end()
    }
}

// ---------------------------------------------------------------- Alerts (2x2)

@Composable
private fun WAlerts(v: WView, p: WPal, w: Float, h: Float, scale: Float, root: GlanceModifier) {
    val av = v.alerts ?: return
    val plan = wAlertsPlan(v, w, h, scale)
    val inner = w - 2 * W_PH
    Column(modifier = root.padding(horizontal = W_PH.dp, vertical = W_PV.dp)) {
        WIconHead(if (plan.icon) R.drawable.widget_ic_bell else null, v.title, p, scale) {
            val u = av.unread
            if (u != null && plan.pill) {
                val (bg, fg) = p.pill(KKind.BAD)
                Text(
                    u, modifier = GlanceModifier.wRound(bg, 11f).padding(horizontal = 8.dp, vertical = 3.dp),
                    style = wts(fg, 12f, FontWeight.Bold), maxLines = 1,
                )
            }
        }
        // lines bachi jagah me barabar (lamba widget ho to beech me khaali patti nahi)
        WTopRows(plan.rows.coerceAtMost(av.rows.size)) { i ->
            val r = av.rows[i]
            val dash = r.name == W_DASH
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Glance me padding view ke andar lagti hai: 17dp oonchi view, upar 2dp = 15dp ka nishaan (mockup)
                Image(
                    ImageProvider(wMarkIcon(r.kind)), contentDescription = null,
                    modifier = GlanceModifier.width(15.dp).height(17.dp).padding(top = 2.dp),
                    colorFilter = wFilter(if (dash) p.ink3 else p.mark(r.kind)),
                )
                Spacer(GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(wFit(r.name, inner - 23f, 13f, scale, true), style = wts(p.ink, 13f, FontWeight.Bold), maxLines = 1)
                    if (plan.sub && r.label.isNotEmpty()) {
                        Text(wFit(r.label, inner - 23f, 12f, scale), style = wts(p.ink3, 12f, FontWeight.Medium), maxLines = 1)
                    }
                }
            }
        }
    }
}
