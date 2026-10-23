package com.myfuturecode.mfcdash.widget

import androidx.compose.ui.graphics.Color
import com.myfuturecode.mfcdash.data.TopZone
import com.myfuturecode.mfcdash.data.budgetZone
import com.myfuturecode.mfcdash.data.roasZone
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.screens.payPlanName
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.ui.theme.mfcColors
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.inrK
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
 * "By app" ki ek line: aaj ka kharcha (₹) | app | ROAS % (zone ke rang me). Neeche aaj ke budget ki patti: fill = kharcha /
 * budget (0..1, 100% se upar bhi bas poori), rang = budget zone (bzone), sire par % ki pill (bpct), ant me budget (bud, "₹70K").
 * fill null = budget pata nahi: sirf track + "—", pill nahi. pkg = tap par us app ki screen.
 */
internal class WSpendV(
    val pkg: String, val amount: String, val name: String, val roas: String, val zone: TopZone,
    val fill: Float?, val bpct: String = "", val bzone: TopZone = TopZone.NONE, val bud: String = W_DASH,
)

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

/** Cross promo ki ek line: app, promo installs, patli bar (fill 0..1 = sabse zyada installs wale ke hisaab se). */
internal class WPromoRowV(val name: String, val installs: String, val fill: Float)

/** Cross promo: bada = aaj ke promo installs, neeche views / clicks; daayein top 3 apps (khaali = "Koi promo nahi"). */
internal class WPromoV(val installs: String, val views: String, val clicks: String, val rows: List<WPromoRowV>, val empty: Boolean)

/** Payments ki ek line: paid (hara check) / failed (laal cross), app, "9:32 pm · Yearly" (failed: wajah, laal), ₹ (failed = kata hua). */
internal class WPayRowV(val ok: Boolean, val name: String, val sub: String, val amount: String)

/**
 * Payments: bada = aaj ka ₹ (hara), "38 paid" / "6 failed" (0 = halka), daayein aakhri 2 payments (khaali = "Koi payment nahi").
 * missing = server par payments.php nahi: sirf "Update server".
 */
internal class WPayV(
    val amount: String, val good: Boolean, val paid: String, val failed: String, val anyFailed: Boolean, val rows: List<WPayRowV>,
    val empty: Boolean, val missing: Boolean,
)

/**
 * dot = header ke daayein hara dot + "Aaj" (Cross promo / Payments, taaza data); data purana = time me "3 ghante pehle" (amber).
 */
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
    val promo: WPromoV? = null,
    val pay: WPayV? = null,
    val dot: Boolean = false,
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
        WKind.PROMO -> "Cross promo".t()
        WKind.PAY -> "Payments".t()
    }
    if (!loggedIn) return WView(kind, style, title, "", false, login = true)
    val at = snap?.optLong("at", 0L) ?: 0L
    // Cross promo / Payments "Aaj" ka data dikhate hain: kal ka snap (raat 11 baje ka, subah) bhi purana
    val today = kind == WKind.PROMO || kind == WKind.PAY
    val stale = at > 0L && (failed || now - at > (o.every + 10) * 60_000L || (today && !wSameDay(at, now)))
    val time = when {
        busy -> "…"
        at <= 0L -> W_DASH
        stale -> agoText(at)
        today -> "Today".t()
        else -> wClock(at)
    }
    val dot = today && !busy && at > 0L && !stale
    // payments.php nahi: "Aaj" ka matlab nahi, header ke daayein kuch nahi
    val paySnap = if (kind == WKind.PAY) PaySnap.of(snap) else null
    val payGone = paySnap?.missing == true && !busy
    return when (kind) {
        WKind.PAISA -> WView(kind, style, title, time, stale, false, paisa = paisaV(PaisaSnap.of(snap)))
        WKind.ADS -> WView(kind, style, title, time, stale, false, top = topV(AdsSnap.of(snap), o.byApp))
        WKind.HEALTH -> WView(kind, style, title, time, stale, false, health = healthV(HealthSnap.of(snap)))
        WKind.ALERTS -> WView(kind, style, title, time, stale, false, alerts = alertsV(AlertsSnap.of(snap)))
        WKind.PROMO -> WView(kind, style, title, time, stale, false, promo = promoV(PromoSnap.of(snap)), dot = dot)
        WKind.PAY -> WView(kind, style, title, if (payGone) "" else time, stale && !payGone, false, pay = payV(paySnap), dot = dot && !payGone)
    }
}

/** Dono waqt phone ke ek hi din ke? */
private fun wSameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
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
            // budget % tabhi jab budget bhi pata ho (purana snap / overview fail = dono null)
            val bp = a.budgetPct?.takeIf { a.budgetInr != null && it.isFinite() }
            WSpendV(
                a.pkg, wInr(a.spendInr), a.name, wPct(a.roas), roasZone(a.roas),
                bp?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() }, bp?.let { num(it, 0) + "%" }.orEmpty(), budgetZone(bp),
                if (bp == null) W_DASH else inrK(a.budgetInr),
            )
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

private fun promoV(s: PromoSnap?): WPromoV {
    if (s == null) return WPromoV(W_DASH, W_DASH, W_DASH, List(3) { WPromoRowV(W_DASH, "", 0f) }, empty = false)
    val most = s.top.maxOfOrNull { it.installs }?.takeIf { it > 0 }
    val rows = s.top.take(3).map { a ->
        WPromoRowV(a.name, wNum(a.installs), if (most == null) 0f else (a.installs / most).coerceIn(0.0, 1.0).toFloat())
    }
    return WPromoV(wNum(s.installs), wNum(s.views), wNum(s.clicks), rows, empty = rows.isEmpty())
}

/** Server ka local "2026-10-23 21:32:00" -> "9:32 pm" (wahi ghadi ka waqt; Payments screen ke payTime jaisa, apna formatter). */
internal fun wPayTime(ts: String): String = runCatching {
    val utc = TimeZone.getTimeZone("UTC")
    val inF = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = utc }
    val outF = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = utc }
    outF.format(inF.parse(ts.take(19))!!).lowercase(Locale.US)
}.getOrElse { ts.drop(11).take(5) }

private fun payV(s: PaySnap?): WPayV {
    if (s == null) return WPayV(W_DASH, false, tf("%s paid", W_DASH), tf("%s failed", W_DASH), false, List(2) { WPayRowV(true, W_DASH, "", "") }, false, false)
    if (s.missing) return WPayV(W_DASH, false, "", "", false, emptyList(), empty = false, missing = true)
    val rows = s.last.take(2).map { x ->
        val known = x.amount >= 0.5
        // failed: wajah (pata ho to), warna plan
        val what = if (!x.paid && x.reason.isNotBlank()) x.reason.t() else payPlanName(x.plan)
        WPayRowV(x.paid, x.name, wPayTime(x.ts) + " · " + what, if (known) inr(x.amount) else W_DASH)
    }
    // "No payments" sirf jab sach me koi nahi; ginti hai par lines nahi aayin (purana snap / app wali call) = "—" rows
    val none = s.paid + s.failed == 0
    return WPayV(
        inr(s.amount), s.amount >= 0.5, tf("%s paid", num(s.paid.toDouble())), tf("%s failed", num(s.failed.toDouble())), s.failed > 0,
        if (rows.isEmpty() && !none) List(2) { WPayRowV(true, W_DASH, "", "") } else rows, empty = rows.isEmpty() && none, missing = false,
    )
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
 * Ads + Live ret; Health gulabi; Alerts lavender; Cross promo ret; Payments hara. Dark / Brand = null (saada / brand gradient).
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
        WKind.PROMO -> WTint.SAND
        WKind.PAY -> WTint.GREEN
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
 * ROAS % hatta (roas = false). Uske neeche budget ki patti: trackW chaudi (pill isi me, kabhi bahar nahi), phir 6dp + budW me
 * budget ("₹70K", daayein tika; sab lines ki patti ek jagah khatam). budW = 0: jagah nahi (bahut bada font), sirf patti.
 * thin = poori budget line ke liye oonchai nahi (lines kam karne se pehle): sirf 4dp ki bhari patti (3dp upar), pill aur
 * budget nahi (rang phir bhi budget ka haal batata hai). note = "Kharcha nahi" wale note ki lines (1-2, taaki "nahi" na kate).
 */
internal class WSpendPlan(
    val totSp: Float, val label: Boolean, val rows: Int, val rowSp: Float, val roas: Boolean, val note: Int,
    val trackW: Float, val budW: Float, val thin: Boolean = false,
)

/** "By app" ki patti (4dp); budget wali line (pill samet) text ke theek neeche. */
internal const val W_SPEND_BAR = 4f

/** Patti ke ant me budget ("₹70K") ka size. */
internal const val W_BUD_SP = 10f

/** Budget wali line ki oonchai: pill (wBarH) ya budget ki likhai, jo badi. */
internal fun wSpendLineH(scale: Float): Float = max(wBarH(scale), wLine(W_BUD_SP, scale))

internal fun wSpendLabel(): String = "today · ROAS".t()

internal fun wSpendNote(): String = "No ad spend today".t()

/** Ek line bina gap ke (text + budget wali line; thin = 3dp + patti); lines ke beech ka gap plan alag jodta hai. */
internal fun wSpendRowH(sp: Float, scale: Float, thin: Boolean = false): Float =
    wLine(sp, scale) + if (thin) 3f + W_SPEND_BAR else wSpendLineH(scale)

/**
 * room = aadhe ki chaudai, ih = andar ki oonchai, cap = zyada se zyada lines; pair = do column (cap = Live wale aadhe ki
 * lines, taaki dono taraf barabar lines; barabari ke liye lines ke beech 1dp tak chalega). "Kharcha nahi" wala note (13sp,
 * 8dp upar, 1-2 line) bhi isi hisaab me.
 */
internal fun wSpendPlan(tv: WTopV, room: Float, ih: Float, scale: Float, cap: Int = 3, pair: Boolean = false): WSpendPlan {
    val hdr = wHdr(scale)
    // label ki jagah: 6dp gap + chhoti text ka andaza thoda bada (font ka farq), taaki kul ko kabhi kam jagah na mile
    val labW = 10f + wTextW(wSpendLabel(), 10.5f, scale) * 1.15f
    // bada number kabhi "…" na ho: font ki chaudai me 4% chhoot (Paisa jaisa)
    var tot = wFitSp(tv.total, (room - labW) / 1.04f, 19f, 17f, scale)
    // label tabhi jab kul poora aaye aur thodi aur chhoot bache (font ka farq: kul kabhi "…" na ho, label chhod do)
    val label = !tv.noSpend && wTextW(tv.total, tot, scale, true) * 1.12f <= room - labW
    if (!label) tot = wFitSp(tv.total, room / 1.04f, 19f, 12f, scale)
    // line: kharcha + 6 + naam (pehle ~40dp, phir 32 / 24dp; chhota naam poora) + 6 + ROAS %
    fun rowFits(sp: Float, roas: Boolean, name: Float) = tv.spends.all { a ->
        a == null || wTextW(a.amount, sp, scale, true) + 6f + min(name, wTextW(a.name, 11.5f, scale)) +
            (if (roas) 6f + wTextW(a.roas, sp, scale, true) else 0f) <= room
    }
    val sizes = listOf(14f, 13f, 12f)
    val withRoas = listOf(40f, 32f, 24f).firstNotNullOfOrNull { nm -> sizes.firstOrNull { rowFits(it, true, nm) } }
    val widthSp = withRoas ?: sizes.firstOrNull { rowFits(it, false, 24f) } ?: 12f
    val roas = withRoas != null
    // budget wali line: patti + 6dp + budget (sabse lambe budget jitni jagah, font ke farq ke liye 25% + 2dp zyada:
    // daayein tika hai, chhoot bas patti aur budget ke beech dikhti hai). "100%" ki pill + thodi patti na aaye to budget hatao
    val budW = tv.spends.maxOfOrNull { wTextW(it?.bud ?: W_DASH, W_BUD_SP, scale, true) * 1.25f + 2f } ?: 0f
    val pillW = wBarPillW("100%", scale)
    val endW = if (room - 6f - budW >= pillW + 16f) budW else 0f
    val trackW = if (endW > 0f) room - 6f - endW else room
    val noteN = if (wTextW(wSpendNote(), 13f, scale) <= room) 1 else 2
    val note = if (tv.noSpend) 8f + noteN * wLine(13f, scale) else 0f
    fun need(t: Float, n: Int, gap: Float, sp: Float, thin: Boolean) =
        hdr + 1f + wLine(t, scale) + n * (wSpendRowH(sp, scale, thin) + gap) + note
    // har ginti par: pehle budget wali poori line (font 12sp tak chhota), phir patli patti (thin: purani ROAS patti jitni
    // oonchai, isliye lines pehle se kabhi kam nahi); har koshish me gap 6 / 3dp (Live ke barabar ho to 1dp tak), kul minT tak
    val small = sizes.filter { it <= widthSp }
    val tries = small.map { it to false } + small.map { it to true }
    fun fit(n: Int, gaps: List<Float>, minT: Float): WSpendPlan? {
        for ((sp, thin) in tries) for (gap in gaps) {
            var t = tot
            while (t > minT && need(t, n, gap, sp, thin) > ih) t -= 1f
            if (need(t, n, gap, sp, thin) <= ih) return WSpendPlan(t, label, n, sp, roas, noteN, trackW, endW, thin)
        }
        return null
    }
    // pehle zyada lines (kul 17sp tak); na aayein to ek line kam
    for (n in min(cap, if (tv.noSpend) 0 else 3) downTo 1) {
        fit(n, if (pair && n == cap) listOf(6f, 3f, 1f) else listOf(6f, 3f), 17f)?.let { return it }
    }
    // bahut neecha: ek line (kul 15sp tak, wahi koshishein), phir sirf kul
    if (!tv.noSpend && cap >= 1) fit(1, listOf(0f), 15f)?.let { return it }
    var t = tot
    while (t > 15f && need(t, 0, 0f, widthSp, false) > ih) t -= 1f
    return WSpendPlan(t, label, 0, widthSp, roas, noteN, trackW, endW)
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

// ---------------------------------------------------------------- Cross promo + Payments (4x2, mockup promo_pay pp_6)

/** Patli line ke dono taraf ki jagah (mockup .body gap 14). */
internal const val W_GAP2 = 14f

/** Promo ki installs bar (4dp). */
internal const val W_PROMO_BAR = 4f

/**
 * Header: MFC nishaan + naam, daayein hara dot + "Aaj" (purana data = amber "3 ghante pehle", "…" refresh me).
 * Jagah kam ho to pehle nishaan hatta, phir daayein wala, phir naam katta. time = daayein wala dikhe.
 */
internal fun wTodayHeadPlan(v: WView, inner: Float, scale: Float): WHeadPlan {
    val titleW = wTextW(v.title, 14f, scale, bold = true)
    val right = 8f + (if (v.dot) 7f + 5f else 0f) + wTextW(v.time, 12f, scale)
    val mark = 27f
    if (v.time.isEmpty()) {
        return if (mark + titleW <= inner) WHeadPlan(true, v.title, false, false)
        else WHeadPlan(false, wFit(v.title, inner, 14f, scale, true), false, false)
    }
    return when {
        mark + titleW + right <= inner -> WHeadPlan(true, v.title, false, true)
        titleW + right <= inner -> WHeadPlan(false, v.title, false, true)
        mark + titleW <= inner -> WHeadPlan(true, v.title, false, false)
        else -> WHeadPlan(false, wFit(v.title, inner, 14f, scale, true), false, false)
    }
}

/** Do column ho to baayan hissa (bada number): andar ki chaudai ka ~37% (mockup 128 / 347), daayein kam se kam 140dp. */
internal fun wSplitLeft(inner: Float): Float = (inner * .37f).coerceIn(112f, 150f)

internal fun wSplitTwo(inner: Float): Boolean = inner - wSplitLeft(inner) - (2 * W_GAP2 + 1f) >= 140f

/** Chhoti line (13sp) ki oonchai: icon 14dp se kam nahi. */
internal fun wMiniH(scale: Float): Float = max(wLine(13f, scale), 15f)

/**
 * Cross promo. two = do column (baayein bada, daayein apps); patla = sirf baayan hissa poori chaudai me.
 * gap = header ke neeche (8, tang ho to 4). bigSp = installs ka size, unit = "installs" likha, mini = views / clicks lines (0-2),
 * rows = daayein apps ki lines (0-3), note = "Koi promo nahi" ki lines.
 */
internal class WPromoPlan(
    val head: WHeadPlan, val two: Boolean, val leftW: Float, val rightW: Float, val gap: Float,
    val bigSp: Float, val unit: Boolean, val mini: Int, val rows: Int, val note: Int,
)

/** Promo ki ek line: naam (13sp) + installs (15sp) ek line me, 5dp, bar. */
internal fun wPromoRowH(scale: Float): Float = wLine(15f, scale) + 5f + W_PROMO_BAR

internal fun wPromoPlan(v: WView, w: Float, h: Float, scale: Float): WPromoPlan {
    val pv = v.promo!!
    val inner = w - 2 * W_PH
    val ih = h - 2 * W_PV
    val head = wTodayHeadPlan(v, inner, scale)
    val hdr = wHdr(scale)
    val two = wSplitTwo(inner)
    val leftW = if (two) wSplitLeft(inner) else inner
    val rightW = if (two) inner - leftW - (2 * W_GAP2 + 1f) else 0f
    // bada number kabhi "…" na ho: font ki chaudai me 4% chhoot (Paisa jaisa); patle me thoda bada chalega
    val fit = wFitSp(pv.installs, leftW / 1.04f, if (two) 36f else 40f, 16f, scale)
    val mh = wMiniH(scale)
    fun need(b: Float, unit: Boolean, mini: Int) =
        wLine(b, scale) + (if (unit) 1f + wLine(13f, scale) else 0f) + (if (mini > 0) 4f + mini * mh + (mini - 1) * 3f else 0f)
    // kram: gap 8 -> 4, number 28sp tak, views / clicks hatein, number 22sp, "installs" hatta, number 16sp
    val tries = listOf(
        Triple(28f, true, 2), Triple(28f, true, 1), Triple(22f, true, 0), Triple(16f, false, 0),
    )
    var pick: Pair<Float, Float>? = null   // (gap, big)
    var unit = true
    var mini = 2
    loop@ for ((floor, u, m) in tries) {
        for (gap in listOf(8f, 4f)) {
            val body = ih - hdr - gap
            var b = fit
            while (b > floor && need(b, u, m) > body) b -= 1f
            if (need(b, u, m) <= body) {
                pick = gap to b; unit = u; mini = m
                break@loop
            }
        }
    }
    val (gap, big) = pick ?: (4f to 16f).also { unit = false; mini = 0 }
    val body = ih - hdr - gap
    val rowH = wPromoRowH(scale)
    val rows = if (!two || pv.empty) 0 else (3 downTo 1).firstOrNull { n -> n * rowH + (n - 1) * 4f <= body } ?: 0
    val note = if (wTextW(wPromoNote(), 13f, scale) <= rightW) 1 else 2
    return WPromoPlan(head, two, leftW, rightW, gap, big, unit, mini, rows, note)
}

internal fun wPromoNote(): String = "No promo shown".t()

internal fun wPayNote(): String = "No payments".t()

internal fun wPayMissing(): String = "Update server".t()

/**
 * Payments. two / leftW / rightW / gap: promo jaisa. halves = baayan hissa do barabar aadhe (₹ pehle payment ke saamne,
 * paid / failed doosre ke, mockup); jagah na ho to upar ₹, neeche paid / failed. mini = 2 lines, 1 = ek line (paid, jagah ho
 * to " · failed" bhi: oneBoth), 0 = sirf ₹. rows = daayein payments (0-2), sub = har line ke neeche "9:32 pm · Yearly".
 */
internal class WPayPlan(
    val head: WHeadPlan, val two: Boolean, val leftW: Float, val rightW: Float, val gap: Float,
    val bigSp: Float, val halves: Boolean, val mini: Int, val oneBoth: Boolean, val rows: Int, val sub: Boolean, val note: Int,
)

/** Payment ki line: naam (13.5sp) / ₹ (15sp), sub ho to 2dp + 12.5sp. */
internal fun wPayRowH(scale: Float, sub: Boolean): Float =
    max(wLine(13.5f, scale), wLine(15f, scale)).let { if (sub) max(it, wLine(13.5f, scale) + 2f + wLine(12.5f, scale)) else it }

/** paid / failed wali ek line: icon 14 + 6 + likhai (13sp bold). */
internal fun wPayMiniW(text: String, scale: Float): Float = 14f + 6f + wTextW(text, 13f, scale, bold = true)

internal fun wPayPlan(v: WView, w: Float, h: Float, scale: Float): WPayPlan {
    val pv = v.pay!!
    val inner = w - 2 * W_PH
    val ih = h - 2 * W_PV
    val head = wTodayHeadPlan(v, inner, scale)
    val hdr = wHdr(scale)
    val two = wSplitTwo(inner) && !pv.missing
    val leftW = if (two) wSplitLeft(inner) else inner
    val rightW = if (two) inner - leftW - (2 * W_GAP2 + 1f) else 0f
    val fit = wFitSp(pv.amount, leftW / 1.04f, if (two) 32f else 36f, 16f, scale)
    val mh = wMiniH(scale)
    val miniH = { m: Int -> if (m > 0) m * mh + (m - 1) * 3f else 0f }
    val oneBoth = wPayMiniW(pv.paid, scale) + wTextW(" · ", 13f, scale) + wTextW(pv.failed, 13f, scale, true) <= leftW
    fun stacked(b: Float, m: Int) = wLine(b, scale) + (if (m > 0) 4f + miniH(m) else 0f)
    var gap = 4f
    var big = 16f
    var halves = false
    var mini = 0
    run {
        for (g in listOf(8f, 4f)) {
            val body = ih - hdr - g
            // aadhe: dono aadhon me apni cheez poori
            if (two && max(wLine(fit, scale), miniH(2)) <= body / 2f) { gap = g; big = fit; halves = true; mini = 2; return@run }
        }
        // upar ₹, neeche lines: number 24sp tak, phir ek line, number 20sp tak, phir sirf ₹ (₹ hi asli baat), 16sp tak
        for ((floor, m) in listOf(24f to 2, 24f to 1, 20f to 1, 20f to 0, 16f to 0)) {
            for (g in listOf(8f, 4f)) {
                val body = ih - hdr - g
                var b = fit
                while (b > floor && stacked(b, m) > body) b -= 1f
                if (stacked(b, m) <= body) { gap = g; big = b; mini = m; return@run }
            }
        }
    }
    val body = ih - hdr - gap
    // daayein: 2 lines (sub ke saath), 2 bina sub, 1 bina sub; beech me 1dp ki line
    var rows = 0
    var sub = false
    if (two && !pv.empty) {
        for ((n, sb) in listOf(2 to true, 2 to false, 1 to true, 1 to false)) {
            if (n * wPayRowH(scale, sb) + (n - 1) * 1f <= body) { rows = n; sub = sb; break }
        }
    }
    val note = if (wTextW(wPayNote(), 13f, scale) <= rightW) 1 else 2
    return WPayPlan(head, two, leftW, rightW, gap, big, halves, mini, oneBoth, rows, sub, note)
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

/** "By app" ki budget patti: kharch tak budget zone ka rang, baaki track; % ki pill fill ke sire par. Budget pata nahi = sirf track. */
internal fun wSpendSegs(width: Float, a: WSpendV?, p: WPal, scale: Float): List<WSeg> {
    val f = a?.fill
    if (a == null || f == null) return listOf(WSeg(width, p.track))
    val x = width * f
    return wSegs(width, listOf(Triple(0f, x, p.zone(a.bzone)), Triple(x, width, p.track)), x, wBarPillW(a.bpct, scale), 2f)
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
