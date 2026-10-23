package com.myfuturecode.mfcdash.widget

import androidx.compose.ui.graphics.Color
import com.myfuturecode.mfcdash.data.TopZone
import com.myfuturecode.mfcdash.data.budgetZone
import com.myfuturecode.mfcdash.data.roasZone
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.ui.theme.mfcColors
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Widget ka roop: data + options se har widget ka "view" (likhai, rang ka zone, bar ki jagah) aur jagah ka hisaab (plan).
// Glance (home screen, WidgetUi.kt) aur Compose (Widgets screen ka preview, WidgetPreview.kt) dono isi se bante hain,
// isliye dono ek jaise dikhte hain. Mockup: scratchpad ui_w2/w.html (px = dp, 172dp ka ek cell).

internal const val W_DASH = "—"

internal enum class WTone { INK, SOFT, GOOD, BAD, MUTED }

/** Pill: shabd + haal (icon aur rang KKind se). */
internal class WPillM(val text: String, val kind: KKind)

// ---------------------------------------------------------------- har widget ka view

/** Paisa ki neeche wali line ka ek khana (Live par hara dot). */
internal class WStat(val label: String, val value: String, val live: Boolean = false)

/**
 * Paisa: bada munafa, ROAS (% + Loss/Profit pill), meter par dot (0..1 = 0..200%, null = kharcha nahi),
 * 4 khane (Kamai | Ads se | Payments | Ads kharcha). live = abhi live users ("100"), header me; null = pata nahi.
 */
internal class WPaisaV(
    val big: String, val bigTone: WTone, val roas: String, val zone: TopZone, val pill: WPillM?, val pos: Float?,
    val stats: List<WStat>, val live: String?,
)

/** Ads ki ek line: kharcha (apni currency) + account; budget bar (fill 0..1; null = budget pata nahi, sirf track). */
internal class WAcctV(val amount: String, val name: String, val fill: Float?, val pct: String, val zone: TopZone)

/** Live ki ek line: 6 min, app, 30 min; ROAS line par jagah (0..1 = 0..200%; null = kharcha nahi, sirf track). */
internal class WAppV(val n6: String, val name: String, val n30: String, val pos: Float?, val roas: String, val zone: TopZone)

/**
 * "By app" ki ek line: aaj ka kharcha (₹) | app | ROAS % (zone ke rang me); patti ROAS tak bhari (100% par poori).
 * fill null = ROAS pata nahi (sirf track). pkg = tap par us app ki screen.
 */
internal class WSpendV(val pkg: String, val amount: String, val name: String, val roas: String, val zone: TopZone, val fill: Float?)

/**
 * Ads + Live: 3-3 line (null = "—" wali line). set = false: Google Ads set nahi.
 * byApp = baayan aadha "By app": total = aaj ka kul kharcha, spends = top kharch wale apps (3 se kam ho to baaki jagah khaali;
 * data hi nahi to 3 "—" lines), noSpend = aaj kisi app par kharcha nahi.
 */
internal class WTopV(
    val set: Boolean, val accs: List<WAcctV?>, val apps: List<WAppV?>,
    val byApp: Boolean = false, val total: String = W_DASH, val spends: List<WSpendV?> = emptyList(), val noSpend: Boolean = false,
)

/** Health ki ek line: badge (khule issue) + app. ok = "Sab theek" wali hari line. */
internal class WIssueV(val count: String, val name: String, val ok: Boolean = false)

internal class WHealthV(val total: String, val bad: String, val fine: String, val rows: List<WIssueV>)

internal class WAlertV(val kind: KKind, val name: String, val label: String)

/** Alerts: header ki "3 naye" pill (0 = null) + 3 line. */
internal class WAlertsV(val unread: String?, val rows: List<WAlertV>)

internal class WView(
    val kind: WKind,
    val style: ThemeStyle,
    val title: String,
    val time: String,
    val stale: Boolean,
    val login: Boolean,
    val paisa: WPaisaV? = null,
    val top: WTopV? = null,
    val health: WHealthV? = null,
    val alerts: WAlertsV? = null,
)

/** Widget ka theme: "app" = app ka roop (Settings), warna chuna hua. */
internal fun wStyle(theme: String, appTheme: String): ThemeStyle = when (theme) {
    "light" -> ThemeStyle.LIGHT
    "dark" -> ThemeStyle.DARK
    "brand" -> ThemeStyle.BRAND
    else -> ThemeStyle.of(appTheme)
}

internal fun wView(
    kind: WKind, o: WOpts, snap: JSONObject?, loggedIn: Boolean, appTheme: String,
    busy: Boolean = false, failed: Boolean = false, now: Long = System.currentTimeMillis(),
): WView {
    val style = wStyle(o.theme, appTheme)
    val title = when (kind) {
        WKind.PAISA -> wProfitLabel(o.range)
        WKind.ADS -> "Ads + Live".t()
        WKind.HEALTH -> "App Health".t()
        WKind.ALERTS -> "Alerts".t()
    }
    if (!loggedIn) return WView(kind, style, title, "", false, login = true)
    val at = snap?.optLong("at", 0L) ?: 0L
    val stale = at > 0L && (failed || now - at > (o.every + 10) * 60_000L)
    val time = when {
        busy -> "…"
        at <= 0L -> W_DASH
        stale -> agoText(at)
        else -> wClock(at)
    }
    return when (kind) {
        WKind.PAISA -> WView(kind, style, title, time, stale, false, paisa = paisaV(PaisaSnap.of(snap)))
        WKind.ADS -> WView(kind, style, title, time, stale, false, top = topV(AdsSnap.of(snap), o.byApp))
        WKind.HEALTH -> WView(kind, style, title, time, stale, false, health = healthV(HealthSnap.of(snap)))
        WKind.ALERTS -> WView(kind, style, title, time, stale, false, alerts = alertsV(AlertsSnap.of(snap)))
    }
}

/** "9:41". Taaza data kuch ghante se purana nahi hota (purana = "5 ghante pehle"), isliye tareekh nahi. */
private fun wClock(at: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(at))

/** "+₹18,450" / "−₹2,140" (asli minus). */
internal fun wSignedInr(v: Double?): String = when {
    v == null || !v.isFinite() -> W_DASH
    v > 0 -> "+" + inr(v)
    v < 0 -> "−" + inr(-v)
    else -> inr(v)
}

private fun wInr(v: Double?): String = if (v == null || !v.isFinite()) W_DASH else inr(v)

private fun wNum(v: Double?): String = if (v == null || !v.isFinite()) W_DASH else num(v, 0)

/** ROAS ratio -> "142%". */
private fun wPct(v: Double?): String = if (v == null || !v.isFinite()) W_DASH else num(v * 100, 0) + "%"

/** ROAS ratio -> meter par jagah (0..200% = 0..1). */
private fun wRoasPos(r: Double?): Float? = if (r == null || !r.isFinite()) null else (r / 2.0).coerceIn(0.0, 1.0).toFloat()

/** Account ki currency: ₹ INR, $ USD, baaki code ("EUR 120"). */
private fun wSym(cur: String): String = when (cur) {
    "INR" -> "₹"
    "USD" -> "\$"
    "" -> ""
    else -> "$cur "
}

private fun wCash(v: Double, cur: String): String = (if (v < 0) "−" else "") + wSym(cur) + num(abs(v), 0)

private fun paisaV(s: PaisaSnap?): WPaisaV {
    val p = s?.profit
    val r = s?.roas
    val z = if (s == null) TopZone.NONE else roasZone(r)
    // ROAS < 100% = loss (amber zone bhi loss hi hai, bas kam)
    val pill = when (z) {
        TopZone.RED -> WPillM("Loss".t(), KKind.BAD)
        TopZone.AMBER -> WPillM("Loss".t(), KKind.WARN)
        TopZone.GREEN -> WPillM("Profit".t(), KKind.OK)
        TopZone.NONE -> null
    }
    val tone = when {
        p == null || !p.isFinite() -> WTone.INK
        p > 0 -> WTone.GOOD
        p < 0 -> WTone.BAD
        else -> WTone.INK
    }
    // Kamai = gol kiye dono hisson ka jod (₹2,146 + ₹178 = ₹2,324, ₹1 ka farq nahi); purana snap (ads / pay nahi) = earned
    val ads = s?.ads
    val pay = s?.pay
    val earned = if (ads != null && pay != null) (Math.round(ads) + Math.round(pay)).toDouble() else s?.earned
    return WPaisaV(
        wSignedInr(p), tone, wPct(r), z, pill, wRoasPos(r),
        listOf(
            WStat("Earnings".t(), wInr(earned)),
            WStat("From ads".t(), wInr(ads)),
            WStat("Payments".t(), wInr(pay)),
            WStat("Ads spend".t(), wInr(s?.spend)),
        ),
        s?.live?.let { wNum(it) },
    )
}

private fun <T> pad3(l: List<T>): List<T?> = List(3) { l.getOrNull(it) }

private fun topV(s: AdsSnap?, byApp: Boolean): WTopV {
    val apps = s?.apps.orEmpty().map { a ->
        WAppV(wNum(a.live6), a.name, wNum(a.live30), wRoasPos(a.roas), if (a.roas == null) "" else wPct(a.roas), roasZone(a.roas))
    }
    if (byApp) {
        // apps wala data abhi nahi (pehli baar / purana snap) = 3 "—" lines, agle refresh tak
        val sp = s?.spend ?: return WTopV(true, pad3(emptyList()), pad3(apps), byApp = true, spends = pad3(emptyList()))
        val rows = sp.take(3).map { a ->
            WSpendV(a.pkg, wInr(a.spendInr), a.name, wPct(a.roas), roasZone(a.roas), a.roas?.let { it.coerceIn(0.0, 1.0).toFloat() })
        }
        return WTopV(true, pad3(emptyList()), pad3(apps), byApp = true, total = wInr(s.total), spends = rows, noSpend = rows.isEmpty())
    }
    if (s == null) return WTopV(true, pad3(emptyList()), pad3(emptyList()))
    val accs = s.accs.map { a ->
        val pct = a.budgetPct
        WAcctV(
            wCash(a.spend, a.currency), a.name, pct?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() },
            pct?.let { num(it, 0) + "%" } ?: "", budgetZone(pct),
        )
    }
    return WTopV(s.set, pad3(accs), pad3(apps))
}

private fun healthV(s: HealthSnap?): WHealthV {
    if (s == null) return WHealthV(W_DASH, W_DASH, W_DASH, List(3) { WIssueV(W_DASH, W_DASH) })
    val rows = if (s.top.isEmpty()) listOf(WIssueV("0", "All fine".t(), ok = true))
    else s.top.map { WIssueV(num(it.open.toDouble()), it.name) }
    return WHealthV(num(s.total.toDouble()), num(s.bad.toDouble()), num(s.fine.toDouble()), rows)
}

private fun alertsV(s: AlertsSnap?): WAlertsV {
    if (s == null || s.missing) return WAlertsV(null, List(3) { WAlertV(KKind.NEUTRAL, W_DASH, "") })
    val unread = if (s.unread > 0) tf("%s new", num(s.unread.toDouble())) else null
    if (s.items.isEmpty()) return WAlertsV(unread, listOf(WAlertV(KKind.OK, "No alerts".t(), "All fine".t())))
    return WAlertsV(unread, s.items.map { a -> WAlertV(a.kind, a.nameL(), a.arg?.let { tf(a.label, it) } ?: a.label.t()) })
}

// ---------------------------------------------------------------- rang (Glance + preview dono)

/** Widget ke rang = mockup ke .t1 (Ujla) / .t2 (Brand gradient) / .t3 (Dark) tokens. */
internal class WPal(
    val style: ThemeStyle,
    val bg: Color,
    val ink: Color, val ink2: Color, val ink3: Color, val line: Color, val track: Color,
    val red: Color, val green: Color, val amber: Color, val link: Color,
    val zr: Color, val za: Color, val zg: Color, val ring: Color, val live: Color,
    private val pills: Map<KKind, Pair<Color, Color>>,
) {
    val brand: Boolean get() = style == ThemeStyle.BRAND
    val dark: Boolean get() = style == ThemeStyle.DARK

    /** Pill ka (bg, text) rang. */
    fun pill(k: KKind): Pair<Color, Color> = pills[k] ?: (Color.Transparent to ink2)

    fun tone(t: WTone): Color = when (t) {
        WTone.INK -> ink
        WTone.SOFT -> ink2
        WTone.GOOD -> green
        WTone.BAD -> red
        WTone.MUTED -> ink3
    }

    /** Bar / meter ka rang. */
    fun zone(z: TopZone): Color = when (z) {
        TopZone.RED -> zr
        TopZone.AMBER -> za
        TopZone.GREEN -> zg
        TopZone.NONE -> track
    }

    /** Number ka rang (ROAS %). */
    fun zoneText(z: TopZone): Color = when (z) {
        TopZone.RED -> red
        TopZone.AMBER -> amber
        TopZone.GREEN -> green
        TopZone.NONE -> ink3
    }

    /** Bar par chhoti pill (bg, text): amber / hare par gehra text, laal par safed. */
    fun barPill(z: TopZone): Pair<Color, Color> = zone(z) to when (z) {
        TopZone.RED -> Color.White
        TopZone.AMBER -> Color(0xFF2B1D00)
        else -> Color(0xFF062012)
    }

    /** Paisa meter ka dot (brand par zone ka rang, baaki number wala). */
    fun dot(z: TopZone): Color = if (brand) zone(z) else zoneText(z)

    /** Alerts ki line ka nishaan. */
    fun mark(k: KKind): Color = when (k) {
        KKind.BAD -> red
        KKind.WARN -> amber
        KKind.OK -> green
        else -> link
    }
}

internal fun wPal(style: ThemeStyle): WPal {
    val brand = style == ThemeStyle.BRAND
    val dark = style == ThemeStyle.DARK
    val c = mfcColors(style, top = brand)
    return WPal(
        style = style,
        bg = when (style) {
            ThemeStyle.BRAND -> Color(0xFF4A1FC4)
            ThemeStyle.DARK -> Color(0xFF16131F)
            ThemeStyle.LIGHT -> Color.White
        },
        ink = c.ink, ink2 = c.ink2, ink3 = if (brand) Color.White.copy(alpha = .78f) else c.ink3,
        line = c.line, track = c.zTrack,
        red = c.red, green = c.green, amber = c.amber, link = c.brand,
        zr = c.zRed, za = c.zAmber, zg = c.zGreen, ring = if (dark) Color(0xFF16131F) else c.dotRing, live = c.live,
        pills = mapOf(
            KKind.OK to (c.pOkBg to c.pOkFg),
            KKind.WARN to (c.pWarnBg to c.pWarnFg),
            KKind.BAD to (c.pBadBg to c.pBadFg),
            KKind.ASK to (c.pAskBg to c.pAskFg),
            KKind.NEUTRAL to (c.pNBg to c.pNFg),
        ),
    )
}

/**
 * Ujla theme me card ka upar wala halka rang (neeche safed tak gradient): Paisa munafa = hara, loss = laal;
 * Ads + Live ret; Health gulabi; Alerts lavender. Dark / Brand = null (saada / brand gradient).
 */
internal enum class WTint(val top: Color, val stop: Float) {
    RED(Color(0xFFFDECEA), .46f), GREEN(Color(0xFFE7F5EC), .46f), SAND(Color(0xFFFFF0D6), .5f),
    ROSE(Color(0xFFFCE9E6), .5f), LAV(Color(0xFFEEE9FD), .5f),
}

internal fun wTint(v: WView): WTint? {
    if (v.style != ThemeStyle.LIGHT) return null
    return when (v.kind) {
        WKind.PAISA -> if (v.paisa?.bigTone == WTone.BAD) WTint.RED else WTint.GREEN
        WKind.ADS -> WTint.SAND
        WKind.HEALTH -> WTint.ROSE
        WKind.ALERTS -> WTint.LAV
    }
}

// ---------------------------------------------------------------- naap (text kitni jagah lega)

/** Ek line ki oonchai (dp): Android TextView font padding samet ~1.32 em. */
internal fun wLine(sp: Float, scale: Float): Float = sp * 1.32f * scale

/** Text ki andaazan chaudai (dp), Roboto ke akshar ke hisaab se (patle i / l, chaude m / w). */
internal fun wTextW(s: String, sp: Float, scale: Float, bold: Boolean = false): Float {
    var em = 0f
    for (ch in s) em += when {
        ch == ' ' -> .26f
        ch in "il.,:;'|!Ijí" -> .27f
        ch in "frt()[]›-" -> .36f
        ch in "mwMW%" -> .84f
        ch == '…' -> .8f
        ch == '—' -> 1f
        ch.isDigit() -> .57f
        ch.isUpperCase() -> .66f
        ch.isLowerCase() -> .54f
        else -> .62f
    }
    return em * sp * scale * (if (bold) 1.06f else 1f)
}

/** Jagah me na aaye to "…" se chhota (widget me ellipsis bharose ka nahi). */
internal fun wFit(s: String, room: Float, sp: Float, scale: Float, bold: Boolean = false): String {
    if (wTextW(s, sp, scale, bold) <= room) return s
    var n = s.length - 1
    while (n > 1 && wTextW(s.take(n).trimEnd() + "…", sp, scale, bold) > room) n--
    return if (n <= 1) s.take(1) + "…" else s.take(n).trimEnd() + "…"
}

/** Bada (bold) number kitna bada (sp) ho ki jagah me aa jaye (kabhi "..." nahi). */
internal fun wFitSp(s: String, room: Float, max: Float, min: Float, scale: Float): Float {
    val w1 = wTextW(s, 1f, scale, bold = true)
    if (s.isEmpty() || w1 <= 0f || room <= 0f) return if (room <= 0f) min else max
    return floor(room / w1).coerceIn(min, max)
}

// ---------------------------------------------------------------- jagah ka hisaab (Glance + preview dono)

internal const val W_PV = 14f   // upar / neeche padding
internal const val W_PH = 16f   // side padding
internal const val W_PH2 = 14f  // Ads + Live ki side padding
internal const val W_SEP = 12f  // patli line ke dono taraf

/** Header ki line (22dp, bade font par zyada). */
internal fun wHdr(scale: Float): Float = max(22f, wLine(14f, scale))

/** Header: MFC nishaan (Paisa), naam (kata hua), refresh + samay (Paisa). live = "● 100 live" samay se pehle (null = nahi). */
internal class WHeadPlan(val mark: Boolean, val title: String, val refresh: Boolean, val time: Boolean, val live: String? = null)

/** Header ka "● 100 live": 8 gap + dot 7 + 5 + likhai (12sp). */
internal fun wHeadLiveW(text: String, scale: Float): Float = 8f + 7f + 5f + wTextW(text, 12f, scale, bold = true)

/** Paisa header: jagah kam ho to live bachane ko pehle nishaan hatta, phir live, phir samay (data purana ho to pehle nishaan), phir naam katta. */
internal fun wHeadPlan(v: WView, inner: Float, scale: Float): WHeadPlan {
    val titleW = wTextW(v.title, 14f, scale, bold = true)
    if (v.login) return WHeadPlan(27f + titleW <= inner, wFit(v.title, inner, 14f, scale, true), refresh = false, time = false)
    val right = 8f + 16f
    val timeW = 5f + wTextW(v.time, 12f, scale)
    val mark = 27f
    // live tabhi jab poora naam + samay bhi aa jaye
    val live = v.paisa?.live?.let { tf("%s live", it) }
    if (live != null && mark + titleW + wHeadLiveW(live, scale) + right + timeW <= inner) return WHeadPlan(true, v.title, true, true, live)
    // chauda roop me live sirf header me hai: nishaan hata ke jagah mile to wahi
    if (live != null && titleW + wHeadLiveW(live, scale) + right + timeW <= inner) return WHeadPlan(false, v.title, true, true, live)
    return when {
        mark + titleW + right + timeW <= inner -> WHeadPlan(true, v.title, true, true)
        v.stale && titleW + right + timeW <= inner -> WHeadPlan(false, v.title, true, true)
        mark + titleW + right <= inner -> WHeadPlan(true, v.title, true, false)
        titleW + right <= inner -> WHeadPlan(false, v.title, true, false)
        else -> WHeadPlan(false, wFit(v.title, inner - right, 14f, scale, true), true, false)
    }
}

/**
 * Paisa. Chauda (>= 250dp): bada munafa + daayein ROAS, meter, neeche 4 khane (jagah kam ho to pehle meter hatta,
 * phir number chhota, phir khane). Patla (2x2): bada munafa, ROAS line, khane chhoti lines me (wPaisaRows).
 * pill: 0 = nahi, 1 = sirf icon, 2 = icon + shabd. stats = kitne khane / lines; labSp = khane ke label ka size,
 * colW = ek khane ki chaudai, sep = line ke dono taraf ki jagah.
 */
internal class WPaisaPlan(
    val narrow: Boolean, val head: WHeadPlan, val bigSp: Float, val roasLabel: Boolean, val roasLine: Boolean, val pill: Int,
    val meter: Boolean, val stats: Int, val statSp: Float, val labSp: Float, val colW: Float, val sep: Float,
)

/**
 * Patle roop ki lines (n tak): saare khane, aur header me live ki jagah na mili ho to aakhri line Live (hara dot).
 * Jagah kam ho to pehle Kamai + Ads kharcha (munafa inhi do se), phir Ads se, Payments, Live; dikhein usi kram me.
 */
internal fun wPaisaRows(pv: WPaisaV, head: WHeadPlan, n: Int = Int.MAX_VALUE): List<WStat> {
    val live = pv.live
    val all = if (head.live == null && live != null) pv.stats + WStat("Live".t(), live, live = true) else pv.stats
    return listOf(0, 3, 1, 2, 4).filter { it < all.size }.take(n).sorted().map { all[it] }
}

internal fun wPillW(text: String, sp: Float, scale: Float, full: Boolean): Float =
    if (full) 6f + 14f + 4f + wTextW(text, sp, scale, true) + 9f else 6f + 14f + 6f

internal fun wPaisaPlan(v: WView, w: Float, h: Float, scale: Float): WPaisaPlan {
    val pv = v.paisa!!
    val inner = w - 2 * W_PH
    val ih = h - 2 * W_PV
    val head = wHeadPlan(v, inner, scale)
    val hdr = wHdr(scale)
    val pillM = pv.pill
    // n khane: har ek (inner - (n-1) line) / n chauda (4 khane par line ke dono taraf 10dp, mockup jaisa).
    // 12sp par koi number na aaye (bada font / lakhon ka mahina) to 4 khano me pehle line paas (6dp), phir 11sp.
    // Tab bhi na aaye to "₹22,9…" jaisa kata number nahi: patla roop, jisme har khana apni poori line me
    val n = pv.stats.size
    fun colOf(sep: Float) = (inner - (n - 1) * (2 * sep + 1f)) / n
    val tries = if (n > 3) listOf(10f to 12f, 6f to 12f, 6f to 11f) else listOf(W_SEP to 12f)
    val fits = tries.firstOrNull { (sep, sp) -> pv.stats.all { wTextW(it.value, sp, scale, true) <= colOf(sep) } }
    val (sep, minSp) = fits ?: tries.first()
    val colW = colOf(sep)
    val statsTight = fits == null
    if (w < 250f || statsTight) {
        // patla: ROAS ek line me, khane label-number lines
        var big = wFitSp(pv.big, inner / 1.04f, 34f, 18f, scale)
        val roasW = wTextW("ROAS", 12f, scale) + 6f + wTextW(pv.roas, 17f, scale, true)
        val pill = when {
            pillM == null -> 0
            roasW + 6f + wPillW(pillM.text, 12.5f, scale, true) <= inner -> 2
            roasW + 6f + wPillW(pillM.text, 12.5f, scale, false) <= inner -> 1
            else -> 0
        }
        val roasH = max(wLine(17f, scale), 24f)
        var roasLine = true
        fun base() = hdr + 2f + wLine(big, scale) + (if (roasLine) 2f + roasH else 0f)
        while (base() > ih && big > 16f) big -= 1f
        if (base() > ih) roasLine = false
        val rowH = wLine(13f, scale) + 2f
        val most = wPaisaRows(pv, head).size
        val rows = floor((ih - base() - 4f) / rowH).toInt().coerceIn(0, most)
        val meter = roasLine && rows == most && ih - base() - 4f - most * rowH >= 16f
        return WPaisaPlan(true, head, big, false, roasLine, pill, meter, rows, 13f, 12f, inner, sep)
    }
    val roasValW = wTextW(pv.roas, 22f, scale, true)
    val roasLblW = wTextW("ROAS", 12f, scale)
    // ROAS ka hissa: label upar (label = true) ya number ke saath ek line me ("ROAS 27%")
    fun roasW(p: Int, label: Boolean): Float {
        val v = roasValW + when (p) {
            2 -> 6f + wPillW(pillM?.text.orEmpty(), 12.5f, scale, true)
            1 -> 6f + wPillW("", 12.5f, scale, false)
            else -> 0f
        }
        return if (label) max(roasLblW, v) else roasLblW + 6f + v
    }
    // bada number kabhi "…" na ho: font ki chaudai me 4% chhoot
    fun bigFit(p: Int, label: Boolean = true): Float = wFitSp(pv.big, (inner - roasW(p, label) - 12f) / 1.04f, 38f, 18f, scale)
    var pill = if (pillM == null) 0 else 2
    if (pill == 2 && bigFit(2) < 30f) pill = 1
    val fit = bigFit(pill)
    // lamba number ho to sab khano ka font chhota (4 khane: 16sp tak, mockup jaisa)
    val statSp = pv.stats.minOf { wFitSp(it.value, colW, if (n > 3) 16f else 17f, minSp, scale) }
    // label 12sp, na aaye to 10.5sp tak chhota; phir bhi na aaye to 11sp par "…" se kata (number kabhi nahi katta)
    val labSp = listOf(12f, 11.5f, 11f, 10.5f).firstOrNull { sp -> pv.stats.all { wTextW(it.label, sp, scale) <= colW } } ?: 11f
    val statsH = wLine(labSp, scale) + wLine(statSp, scale)
    fun midH(b: Float, label: Boolean) = max(wLine(b, scale), (if (label) wLine(12f, scale) else 0f) + max(wLine(22f, scale), 24f))
    fun need(b: Float, meter: Boolean, stats: Boolean, label: Boolean) =
        hdr + 2f + midH(b, label) + (if (meter) 4f + 12f else 0f) + (if (stats) 6f + statsH else 0f)
    fun plan(b: Float, label: Boolean, pl: Int, meter: Boolean, stats: Int) =
        WPaisaPlan(false, head, b, label, true, pl, meter, stats, statSp, labSp, colW, sep)
    if (need(fit, true, true, true) <= ih) return plan(fit, true, pill, true, n)
    if (need(fit, false, true, true) <= ih) return plan(fit, true, pill, false, n)
    // ROAS ek line me ho to number ki jagah kam: tab bhi tang ho to pill hatao
    val pillIn = if (pill > 0 && bigFit(pill, false) < 24f) 0 else pill
    for (label in listOf(true, false)) {
        val pl = if (label) pill else pillIn
        var b = if (label) fit else min(fit, bigFit(pl, false))
        while (b > 24f && need(b, false, true, label) > ih) b -= 1f
        if (need(b, false, true, label) <= ih) return plan(b, label, pl, false, n)
    }
    // bahut neecha: sirf header + bada number + ROAS (ek line)
    var b = min(fit, bigFit(pillIn, false))
    while (b > 18f && need(b, false, false, false) > ih) b -= 1f
    return plan(b, false, pillIn, false, 0)
}

/**
 * Ads + Live. Chauda: do column (baayan ~47%), patla (2x2 tak): sirf Ads wala aadha (liveOnly = Ads set nahi, to Live wala).
 * labels = "aaj / budget %" wali chhoti line; rows = kitni lines (3 tak); more = "View more ›" poora (warna sirf "›").
 */
internal class WTopPlan(
    val two: Boolean, val liveOnly: Boolean, val leftW: Float, val rightW: Float, val labels: Boolean, val rows: Int,
    val moreL: Boolean, val moreR: Boolean,
)

/** Bar / ROAS line ki oonchai (pill samet): 13dp, bade font par utni ki pill ka "72%" na kate. */
internal fun wBarH(scale: Float): Float = max(13f, wLine(9.5f, scale))

/** Paisa ki Loss / Profit pill: 24dp, bade font par text ke hisaab se. */
internal fun wPillH(scale: Float): Float = max(24f, wLine(12.5f, scale))

internal fun wTopRowH(scale: Float): Float = wLine(14f, scale) + 2f + wBarH(scale)

/**
 * set = Google Ads monitor set hai. Patla widget sirf Ads wala aadha dikhata hai; Ads set na ho to (khaali "Set nahi"
 * ki jagah) poori chaudai me Live users wala aadha (liveOnly).
 */
internal fun wTopPlan(w: Float, h: Float, scale: Float, set: Boolean = true): WTopPlan {
    val inner = w - 2 * W_PH2
    val ih = h - 2 * W_PV
    val two = inner >= 268f
    val liveOnly = !two && !set
    val avail = inner - (2 * W_SEP + 1f)
    val leftW = if (two) avail * .47f else if (liveOnly) 0f else inner
    val rightW = if (two) avail - leftW else if (liveOnly) inner else 0f
    val more = wTextW("View more".t() + " ›", 11f, scale, true)
    val moreL = wTextW("Ads spend".t(), 12.5f, scale, true) + 6f + more <= leftW
    val moreR = (two || liveOnly) && wTextW("Live users".t(), 12.5f, scale, true) + 6f + more <= rightW
    val hdr = wHdr(scale)
    val labH = wLine(10.5f, scale) + 1f
    val rowH = wTopRowH(scale)
    for (rows in 3 downTo 1) {
        for (labels in listOf(true, false)) {
            if (hdr + (if (labels) labH else 0f) + rows * rowH <= ih) return WTopPlan(two, liveOnly, leftW, rightW, labels, rows, moreL, moreR)
        }
    }
    return WTopPlan(two, liveOnly, leftW, rightW, false, 1, moreL, moreR)
}

/**
 * Ads + Live "By app" ka baayan aadha: header ke neeche kul kharcha (totSp) + daayein "aaj · ROAS" (label; jagah na ho to
 * pehle kul 17sp tak chhota, phir label hatta; kul kabhi "…" nahi; kharcha nahi = label nahi), phir rows tak apps ki lines.
 * Line: kharcha | naam (beech) | ROAS % (rowSp, 14sp se 12sp tak, naam ko pehle ~40dp, phir 32 / 24dp); tab bhi tang ho to
 * ROAS % hatta (roas = false, patti ka rang phir bhi ROAS batata hai). note = "Kharcha nahi" wale note ki lines (1-2, taaki
 * "nahi" na kate).
 */
internal class WSpendPlan(val totSp: Float, val label: Boolean, val rows: Int, val rowSp: Float, val roas: Boolean, val note: Int)

/** "By app" ki patti (4dp), text se 3dp neeche. */
internal const val W_SPEND_BAR = 4f

internal fun wSpendLabel(): String = "today · ROAS".t()

internal fun wSpendNote(): String = "No ad spend today".t()

/** Ek line bina gap ke (text + 3dp + patti); lines ke beech ka gap plan alag jodta hai. */
internal fun wSpendRowH(sp: Float, scale: Float): Float = wLine(sp, scale) + 3f + W_SPEND_BAR

/**
 * room = aadhe ki chaudai, ih = andar ki oonchai, cap = zyada se zyada lines; pair = do column (cap = Live wale aadhe ki
 * lines, taaki dono taraf barabar lines; barabari ke liye lines ke beech 1dp tak chalega). "Kharcha nahi" wala note (13sp,
 * 8dp upar, 1-2 line) bhi isi hisaab me.
 */
internal fun wSpendPlan(tv: WTopV, room: Float, ih: Float, scale: Float, cap: Int = 3, pair: Boolean = false): WSpendPlan {
    val hdr = wHdr(scale)
    val labW = 6f + wTextW(wSpendLabel(), 10.5f, scale)
    // bada number kabhi "…" na ho: font ki chaudai me 4% chhoot (Paisa jaisa)
    fun fits(sp: Float, r: Float) = wTextW(tv.total, sp, scale, true) * 1.04f <= r
    var tot = wFitSp(tv.total, (room - labW) / 1.04f, 19f, 17f, scale)
    val label = !tv.noSpend && fits(tot, room - labW)
    if (!label) tot = wFitSp(tv.total, room / 1.04f, 19f, 12f, scale)
    // line: kharcha + 6 + naam (pehle ~40dp, phir 32 / 24dp; chhota naam poora) + 6 + ROAS %
    fun rowFits(sp: Float, roas: Boolean, name: Float) = tv.spends.all { a ->
        a == null || wTextW(a.amount, sp, scale, true) + 6f + min(name, wTextW(a.name, 11.5f, scale)) +
            (if (roas) 6f + wTextW(a.roas, sp, scale, true) else 0f) <= room
    }
    val sizes = listOf(14f, 13f, 12f)
    val withRoas = listOf(40f, 32f, 24f).firstNotNullOfOrNull { nm -> sizes.firstOrNull { rowFits(it, true, nm) } }
    val rowSp = withRoas ?: sizes.firstOrNull { rowFits(it, false, 24f) } ?: 12f
    val roas = withRoas != null
    val rowH = wSpendRowH(rowSp, scale)
    val noteN = if (wTextW(wSpendNote(), 13f, scale) <= room) 1 else 2
    val note = if (tv.noSpend) 8f + noteN * wLine(13f, scale) else 0f
    fun need(t: Float, n: Int, gap: Float) = hdr + 1f + wLine(t, scale) + n * (rowH + gap) + note
    fun plan(t: Float, n: Int) = WSpendPlan(t, label, n, rowSp, roas, noteN)
    // pehle zyada lines (beech me 6dp, phir 3dp; Live ke barabar ho to 1dp tak) aur kul 17sp tak; na aayein to ek line kam
    for (n in min(cap, if (tv.noSpend) 0 else 3) downTo 1) {
        for (gap in if (pair && n == cap) listOf(6f, 3f, 1f) else listOf(6f, 3f)) {
            var t = tot
            while (t > 17f && need(t, n, gap) > ih) t -= 1f
            if (need(t, n, gap) <= ih) return plan(t, n)
        }
    }
    // bahut neecha: ek line (kul 15sp tak), phir sirf kul
    val n = if (tv.noSpend || cap < 1) 0 else 1
    var t = tot
    while (t > 15f && need(t, n, 0f) > ih) t -= 1f
    if (n == 1 && need(t, 1, 0f) > ih) {
        t = tot
        while (t > 15f && need(t, 0, 0f) > ih) t -= 1f
        return plan(t, 0)
    }
    return plan(t, n)
}

/** App Health: 3 number (labels ke saath ya bina) + kitni issue lines. icon = header me dhadkan ka nishaan (jagah ho to). */
internal class WHealthPlan(val tri: Boolean, val triLabels: Boolean, val rows: Int, val triSp: Float, val icon: Boolean)

/** 2x2 header ka nishaan tabhi jab naam (aur daayein ka hissa) poora aa jaye. */
internal fun wHeadIcon(title: String, inner: Float, end: Float, scale: Float): Boolean =
    16f + 7f + wTextW(title, 14f, scale, true) + end <= inner

internal fun wBadgeH(scale: Float): Float = max(19f, wLine(12.5f, scale))

internal fun wHealthPlan(v: WView, w: Float, h: Float, scale: Float): WHealthPlan {
    val hv = v.health!!
    val inner = w - 2 * W_PH
    val ih = h - 2 * W_PV
    val hdr = wHdr(scale)
    val cell = (inner - 2 * 10f) / 3f
    val triSp = listOf(hv.total, hv.bad, hv.fine).minOf { wFitSp(it, cell - 9f, 22f, 14f, scale) }
    val icon = wHeadIcon(v.title, inner, 0f, scale)
    // "apps / issue / theek" tabhi jab poore aayein (kate hue shabd nahi)
    val labelsFit = listOf("apps".t(), "issue".t(), "fine".t()).all { wTextW(it, 11.5f, scale) <= cell - 9f }
    val triH = 2f + wLine(triSp, scale) + (if (labelsFit) wLine(11.5f, scale) else 0f)
    val rowH = wBadgeH(scale)
    val most = hv.rows.size.coerceAtMost(3)
    for (n in most downTo 0) {
        val list = if (n == 0) 0f else 4f + n * rowH + (n - 1) * 5f
        if (hdr + triH + list <= ih) return WHealthPlan(true, labelsFit, n, triSp, icon)
    }
    val small = hdr + 2f + wLine(triSp, scale) <= ih
    return WHealthPlan(small, false, 0, triSp, icon)
}

/** Alerts: kitni lines, har line ke neeche kism dikhe ya nahi, header ki "naye" pill aayegi ya nahi, ghanti ka nishaan. */
internal class WAlertsPlan(val rows: Int, val sub: Boolean, val pill: Boolean, val icon: Boolean)

internal fun wAlertsPlan(v: WView, w: Float, h: Float, scale: Float): WAlertsPlan {
    val av = v.alerts!!
    val inner = w - 2 * W_PH
    val ih = h - 2 * W_PV
    val hdr = wHdr(scale)
    val u = av.unread
    val pill = u != null && wHeadIcon(v.title, inner, 8f + wTextW(u, 12f, scale, true) + 16f, scale)
    val icon = wHeadIcon(v.title, inner, 0f, scale)
    val most = av.rows.size.coerceAtMost(3)
    for (sub in listOf(true, false)) {
        val item = wLine(13f, scale) + (if (sub) wLine(12f, scale) else 0f)
        for (n in most downTo 1) {
            if (hdr + 4f + n * item + (n - 1) * 5f <= ih) return WAlertsPlan(n, sub, pill, icon)
        }
    }
    return WAlertsPlan(1, false, pill, icon)
}

// ---------------------------------------------------------------- bar / meter ke tukde

/** Bar ka ek tukda (bayein se daayein): chaudai (dp), rang (null = khaali, card dikhe), mark = yahan pill / dot. */
internal class WSeg(val w: Float, val color: Color?, val mark: Boolean = false)

/**
 * Tukde bana do: parts = (shuru, ant, rang). mark (beech ki jagah, dp) par markW chaudi pill / dot,
 * uske dono taraf ring dp khaali (card ka rang = mockup ki safed ring). Sab ek Row me, bina overlay.
 */
internal fun wSegs(width: Float, parts: List<Triple<Float, Float, Color>>, mark: Float?, markW: Float, ring: Float): List<WSeg> {
    class P(val s: Float, val e: Float, val c: Color?, val m: Boolean)
    val ps = ArrayList<P>()
    if (mark == null) {
        parts.forEach { (s, e, c) -> if (e - s > .3f) ps += P(s, e, c, false) }
    } else {
        val ms = (mark - markW / 2f).coerceIn(0f, max(0f, width - markW))
        val me = ms + markW
        parts.forEach { (s, e, c) ->
            val a = min(e, ms - ring)
            if (a - s > .3f) ps += P(s, a, c, false)
            val b = max(s, me + ring)
            if (e - b > .3f) ps += P(b, e, c, false)
        }
        ps += P(ms, me, null, true)
    }
    ps.sortBy { it.s }
    val out = ArrayList<WSeg>()
    var x = 0f
    for (p in ps) {
        if (p.s - x > .05f) out += WSeg(p.s - x, null)
        out += WSeg(p.e - p.s, p.c, p.m)
        x = p.e
    }
    return out
}

/** 3 zone (laal 0-80%, amber 80-100%, hara 100-200%), beech me gap. */
internal fun wZoneParts(width: Float, gap: Float, p: WPal): List<Triple<Float, Float, Color>> {
    val u = (width - 2 * gap) / 200f
    val a = 80f * u
    val b = a + gap + 20f * u
    return listOf(Triple(0f, a, p.zr), Triple(a + gap, b, p.za), Triple(b + gap, width, p.zg))
}

/** Bar par pill ki chaudai (9.5sp, dono taraf 4dp). */
internal fun wBarPillW(text: String, scale: Float): Float = wTextW(text, 9.5f, scale, true) + 8f

/** Budget bar: fill tak zone ka rang, baaki track; budget % ki pill fill ke sire par. Budget pata nahi = sirf track. */
internal fun wBudgetSegs(width: Float, a: WAcctV?, p: WPal, scale: Float): List<WSeg> {
    val f = a?.fill
    if (a == null || f == null) return listOf(WSeg(width, p.track))
    val x = width * f
    return wSegs(width, listOf(Triple(0f, x, p.zone(a.zone)), Triple(x, width, p.track)), x, wBarPillW(a.pct, scale), 2f)
}

/** ROAS line (Live users): 3 zone, ROAS ki pill uski jagah par. Kharcha nahi = sirf track. */
internal fun wRoasSegs(width: Float, a: WAppV?, p: WPal, scale: Float): List<WSeg> {
    val pos = a?.pos
    if (a == null || pos == null) return listOf(WSeg(width, p.track))
    return wSegs(width, wZoneParts(width, 2f, p), width * pos, wBarPillW(a.roas, scale), 2f)
}

/** Paisa meter: 3 zone, dot (10dp) ROAS ki jagah, dono taraf 3dp ring. */
internal fun wMeterSegs(width: Float, pv: WPaisaV, p: WPal): List<WSeg> {
    val pos = pv.pos ?: return listOf(WSeg(width, p.track))
    return wSegs(width, wZoneParts(width, 3f, p), width * pos, 10f, 3f)
}
