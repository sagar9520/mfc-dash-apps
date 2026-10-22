package com.myfuturecode.mfcdash.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.myfuturecode.mfcdash.MainActivity
import com.myfuturecode.mfcdash.R
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.util.t

// Home screen par widget (Glance). Roop WView se aata hai; jagah ka hisaab WidgetView.kt ke plan se,
// isliye Widgets screen ka preview bhi bilkul aisa hi dikhta hai.
// Android 12+ (S): rang + cornerRadius (kam nesting; 13+ par har view ek nested RemoteViews hota hai, limit 10).
// Usse purane: safed shape drawable ko tint (cornerRadius wahan nahi chalta).

private val S_PLUS: Boolean get() = Build.VERSION.SDK_INT >= 31

/** Poora widget: band (naam + refresh) aur neeche number. Tap = app ka wahi hissa. */
@Composable
internal fun WGlanceRoot(v: WView) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val scale = ctx.resources.configuration.fontScale.coerceIn(1f, 2f)
    val w = size.width.value.takeIf { it > 0f } ?: 250f
    val h = size.height.value.takeIf { it > 0f } ?: 110f
    val p = wPal(v.style, v.kind)
    var root = GlanceModifier.fillMaxSize().appWidgetBackground()
    root = when {
        p.brand -> root.background(ImageProvider(R.drawable.widget_bg_brand), contentScale = ContentScale.FillBounds, colorFilter = null)
        S_PLUS -> root.background(p.bg)
        else -> root.background(ImageProvider(R.drawable.widget_round), contentScale = ContentScale.FillBounds, colorFilter = wTint(p.bg))
    }
    if (S_PLUS) root = root.cornerRadius(20.dp)
    root = root.clickable(actionStartActivity(wOpenIntent(ctx, if (v.login) null else v.kind.route)))
    Column(modifier = root) {
        WBand(v, p, w, scale)
        val bodyH = h - W_BAND
        when {
            v.login -> WLogin(p)
            v.kind == WKind.HEALTH -> WHealthBody(v, p, w, bodyH, scale)
            else -> WGridBody(v, p, w, bodyH, scale)
        }
    }
}

/** App kholo (route = us widget ki screen; login nahi to bas app). */
private fun wOpenIntent(ctx: Context, route: String?): Intent =
    Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (route != null) putExtra("route", route)
    }

private fun wTint(c: Color): ColorFilter = ColorFilter.tint(ColorProvider(c))

private fun wts(c: Color, size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(color = ColorProvider(c), fontSize = size.sp, fontWeight = weight)

private fun wGlyph(k: WKind): Int = when (k) {
    WKind.PAISA -> R.drawable.widget_glyph_paisa
    WKind.ADS -> R.drawable.widget_glyph_ads
    WKind.HEALTH -> R.drawable.widget_glyph_health
}

private fun wStatusIcon(k: KKind): Int? = when (k) {
    KKind.OK -> R.drawable.widget_ic_ok
    KKind.WARN -> R.drawable.widget_ic_warn
    KKind.BAD -> R.drawable.widget_ic_bad
    KKind.ASK -> R.drawable.widget_ic_ask
    KKind.NEUTRAL -> null
}

// ---------------------------------------------------------------- band

@Composable
private fun WBand(v: WView, p: WPal, w: Float, scale: Float) {
    val plan = wBandPlan(v, w, scale)
    var m = GlanceModifier.fillMaxWidth().height(W_BAND.dp)
    m = when {
        // 12+: root ke gol kone band ko bhi kaat dete hain
        S_PLUS -> m.background(p.band)
        p.brand -> m.background(ImageProvider(R.drawable.widget_band_brand), contentScale = ContentScale.FillBounds, colorFilter = null)
        else -> m.background(ImageProvider(R.drawable.widget_round_top), contentScale = ContentScale.FillBounds, colorFilter = wTint(p.band))
    }
    Row(modifier = m.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (plan.mark) {
            Image(ImageProvider(R.drawable.mfc_mark), contentDescription = null, modifier = GlanceModifier.size(18.dp))
            Spacer(GlanceModifier.width(6.dp))
        }
        Image(ImageProvider(wGlyph(v.kind)), contentDescription = null, modifier = GlanceModifier.size(18.dp))
        Spacer(GlanceModifier.width(8.dp))
        Text(v.title, modifier = GlanceModifier.defaultWeight(), style = wts(p.ink, 14f, FontWeight.Medium), maxLines = 1)
        if (plan.refresh) {
            // refresh: kam se kam 48dp chauda
            val pad = if (plan.time) 10.dp else 17.dp
            Row(
                modifier = GlanceModifier.fillMaxHeight().padding(start = pad, end = if (plan.time) 14.dp else 17.dp)
                    .clickable(actionRunCallback<WidgetRefresh>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    ImageProvider(R.drawable.widget_ic_refresh), contentDescription = "Refresh".t(),
                    modifier = GlanceModifier.size(14.dp), colorFilter = wTint(p.ink2),
                )
                if (plan.time) {
                    Spacer(GlanceModifier.width(6.dp))
                    Text(v.time, style = wts(if (v.stale) p.warn else p.ink2, 13f), maxLines = 1)
                }
            }
        } else {
            Spacer(GlanceModifier.width(14.dp))
        }
    }
}

// ---------------------------------------------------------------- Paisa / Google Ads

@Composable
private fun WGridBody(v: WView, p: WPal, w: Float, bodyH: Float, scale: Float) {
    val hero = v.cells.firstOrNull() ?: return
    val rest = v.cells.drop(1)
    val plan = wGridPlan(v, w, bodyH, scale)
    val k = wLineK(scale)
    val heroH = plan.heroSp * k
    Column(modifier = GlanceModifier.fillMaxWidth().padding(start = W_PAD.dp, end = W_PAD.dp, top = W_TOP.dp)) {
        if (plan.narrow) {
            WCellView(hero, p, plan.heroRoom, plan.heroSp, true, heroH, scale)
            rest.take(plan.rows).forEach { c ->
                Spacer(GlanceModifier.height(4.dp))
                WCompact(c, p, plan.heroRoom, scale)
            }
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WCellView(hero, p, plan.heroRoom, plan.heroSp, true, heroH, scale)
                }
                Spacer(GlanceModifier.width(W_COL_GAP.dp))
                Column(modifier = GlanceModifier.width(plan.c2.dp)) {
                    rest.getOrNull(0)?.let { c ->
                        WCellView(c, p, plan.c2, wValueSp(c, plan.c2, 18f, 13f, scale), false, heroH, scale)
                    }
                }
            }
            if (plan.two) {
                Spacer(GlanceModifier.height(W_GAP.dp))
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        rest.getOrNull(1)?.let { c ->
                            WCellView(c, p, plan.heroRoom, wValueSp(c, plan.heroRoom, 18f, 13f, scale), false, wSmallH(scale), scale)
                        }
                    }
                    Spacer(GlanceModifier.width(W_COL_GAP.dp))
                    Column(modifier = GlanceModifier.width(plan.c2.dp)) {
                        rest.getOrNull(2)?.let { c ->
                            WCellView(c, p, plan.c2, wValueSp(c, plan.c2, 18f, 13f, scale), false, wSmallH(scale), scale)
                        }
                    }
                }
            }
        }
    }
}

/** Ek khana: label, phir number (+ "aur $120") (+ pill). lineH = number wali line ki oonchai. */
@Composable
private fun WCellView(c: WCell, p: WPal, room: Float, sp: Float, big: Boolean, lineH: Float, scale: Float) {
    Text(
        c.label, style = wts(if (c.strong) p.ink else p.ink2, 13f, if (c.strong) FontWeight.Medium else FontWeight.Normal),
        maxLines = 1,
    )
    val sub = c.sub
    val pill = c.pill
    val showSub = sub != null && wShowSub(c, room, scale)
    // bada number neeche tika (sub uski line par), chhota beech me
    Row(
        modifier = GlanceModifier.height(lineH.dp),
        verticalAlignment = if (big) Alignment.Bottom else Alignment.CenterVertically,
    ) {
        if (c.value.isNotEmpty()) {
            Text(
                c.value, style = wts(p.tone(c.tone), sp, if (big) FontWeight.Bold else FontWeight.Medium),
                maxLines = 1,
            )
        }
        if (sub != null && showSub) {
            Spacer(GlanceModifier.width(6.dp))
            Text(sub, modifier = GlanceModifier.padding(bottom = (sp * 0.12f).dp), style = wts(p.ink2, 14f), maxLines = 1)
        }
        if (pill != null) {
            if (c.value.isNotEmpty()) Spacer(GlanceModifier.width(8.dp))
            WPill(pill, p, full = wPillFull(c, room, sp, scale))
        }
    }
}

/** Patli widget ki chhoti line: label baayein, number + pill daayein. */
@Composable
private fun WCompact(c: WCell, p: WPal, room: Float, scale: Float) {
    val pill = c.pill
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(wSmallH(scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.label, modifier = GlanceModifier.defaultWeight(), style = wts(if (c.strong) p.ink else p.ink2, 13f), maxLines = 1)
        if (c.value.isNotEmpty()) {
            Spacer(GlanceModifier.width(8.dp))
            Text(c.value, style = wts(p.tone(c.tone), 15f, FontWeight.Medium), maxLines = 1)
        }
        if (pill != null) {
            Spacer(GlanceModifier.width(6.dp))
            WPill(pill, p, full = c.value.isEmpty() && wPillW(pill, scale) <= room * 0.6f)
        }
    }
}

// ---------------------------------------------------------------- App Health

@Composable
private fun WHealthBody(v: WView, p: WPal, w: Float, bodyH: Float, scale: Float) {
    val hero = v.cells.firstOrNull() ?: return
    val plan = wHealthPlan(v, w, bodyH, scale)
    val of = hero.of
    Column(modifier = GlanceModifier.fillMaxWidth().padding(start = W_PAD.dp, end = W_PAD.dp, top = W_TOP.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(hero.value, style = wts(p.ink, plan.heroSp, FontWeight.Bold), maxLines = 1)
            if (of != null) {
                Text(of, modifier = GlanceModifier.padding(bottom = (plan.heroSp * 0.12f).dp), style = wts(p.ink, 18f), maxLines = 1)
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(hero.label, modifier = GlanceModifier.padding(bottom = (plan.heroSp * 0.14f).dp), style = wts(p.ink2, 13f), maxLines = 1)
        }
        v.cells.drop(1).take(plan.lines).forEach { c ->
            Spacer(GlanceModifier.height(W_GAP.dp))
            val pill = c.pill
            if (pill != null) WPill(pill, p, full = true)
            else Text(wJoinFit(c.value, c.sub, w - 2 * W_PAD, 14f, scale), style = wts(p.tone(c.tone), 14f), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- login / pill

@Composable
private fun WLogin(p: WPal) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Log in".t(), style = wts(p.ink, 18f, FontWeight.Bold), maxLines = 1)
    }
}

/** Haal: rang + shape + shabd. full = false par sirf shape (jagah kam). */
@Composable
private fun WPill(pm: WPillM, p: WPal, full: Boolean) {
    val (bg, fg) = p.pill(pm.kind)
    val icon = wStatusIcon(pm.kind)
    val text = full || icon == null
    val base = GlanceModifier.height(26.dp)
    val shape = if (S_PLUS) {
        base.background(bg).cornerRadius(13.dp)
    } else {
        base.background(ImageProvider(R.drawable.widget_pill), contentScale = ContentScale.FillBounds, colorFilter = wTint(bg))
    }
    Row(
        modifier = shape.padding(start = if (icon != null) 6.dp else 10.dp, end = if (text) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                ImageProvider(icon), contentDescription = if (text) null else pm.text,
                modifier = GlanceModifier.size(16.dp), colorFilter = wTint(fg),
            )
        }
        if (text) {
            if (icon != null) Spacer(GlanceModifier.width(4.dp))
            Text(pm.text, style = wts(fg, 13f, FontWeight.Medium), maxLines = 1)
        }
    }
}
