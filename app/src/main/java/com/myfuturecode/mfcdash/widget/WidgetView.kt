package com.myfuturecode.mfcdash.widget

import androidx.compose.ui.graphics.Color
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.kRoasKind
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.ui.theme.mfcColors
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// Widget ka roop: data + options se "cells" (label, number, pill). Glance (home screen) aur
// Compose (Widgets screen ka preview) dono isi se bante hain, isliye dono ek jaise dikhte hain.

internal const val W_DASH = "—"

internal enum class WTone { INK, SOFT, GOOD, BAD, MUTED }

internal class WPillM(val text: String, val kind: KKind)

/** Ek khana: label upar, number (value) neeche; sub = number ke baad chhota text; of = "/25". */
internal class WCell(
    val label: String,
    val value: String,
    val tone: WTone = WTone.INK,
    val sub: String? = null,
    val pill: WPillM? = null,
    val strong: Boolean = false,
    val of: String? = null,
)

internal class WView(
    val kind: WKind,
    val style: ThemeStyle,
    val title: String,
    val time: String,
    val stale: Boolean,
    val cells: List<WCell>,
    val login: Boolean,
)

/** Widget ka theme: "app" = app ka roop (Settings), warna chuna hua. */
internal fun wStyle(theme: String, appTheme: String): ThemeStyle = when (theme) {
    "light" -> ThemeStyle.LIGHT
    "dark" -> ThemeStyle.DARK
    "brand" -> ThemeStyle.BRAND
    else -> ThemeStyle.of(appTheme)
}

internal fun wView(
    kind: WKind, o: WOpts, snap: JSONObject?, loggedIn: Boolean, appTheme: String, target: Double,
    busy: Boolean = false, failed: Boolean = false, now: Long = System.currentTimeMillis(),
): WView {
    val style = wStyle(o.theme, appTheme)
    if (!loggedIn) return WView(kind, style, kind.title(), "", false, emptyList(), login = true)
    val at = snap?.optLong("at", 0L) ?: 0L
    val stale = at > 0L && (failed || now - at > (o.every + 10) * 60_000L)
    val time = when {
        busy -> "…"
        at <= 0L -> W_DASH
        stale -> agoText(at)
        else -> tf("%s update", wClock(at, now))
    }
    val cells = when (kind) {
        WKind.PAISA -> paisaCells(o, PaisaSnap.of(snap), target)
        WKind.ADS -> adsCells(o, AdsSnap.of(snap))
        WKind.HEALTH -> healthCells(o, HealthSnap.of(snap))
    }
    return WView(kind, style, kind.title(), time, stale, cells, login = false)
}

/** "9:41" (aaj) ya "21 Oct" (purana). */
private fun wClock(at: Long, now: Long): String {
    val a = Calendar.getInstance().apply { timeInMillis = at }
    val b = Calendar.getInstance().apply { timeInMillis = now }
    val same = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(if (same) "HH:mm" else "dd MMM", Locale.US).format(Date(at))
}

/** "+₹18,450" / "−₹2,140" (asli minus). */
internal fun wSignedInr(v: Double?): String = when {
    v == null || !v.isFinite() -> W_DASH
    v > 0 -> "+" + inr(v)
    v < 0 -> "−" + inr(-v)
    else -> inr(v)
}

private fun wInr(v: Double?): String = if (v == null || !v.isFinite()) W_DASH else inr(v)

/** ROAS ratio -> "142%". */
private fun wPct(v: Double?): String = if (v == null || !v.isFinite()) W_DASH else num(v * 100, 0) + "%"

/** "+12%" / "−12%" / "0%". */
private fun wSignedPct(v: Double?): String = when {
    v == null || !v.isFinite() -> W_DASH
    v > 0 -> "+" + num(v, 0) + "%"
    v < 0 -> "−" + num(-v, 0) + "%"
    else -> "0%"
}

private fun tone(v: Double?): WTone = when {
    v == null || !v.isFinite() -> WTone.INK
    v > 0 -> WTone.GOOD
    v < 0 -> WTone.BAD
    else -> WTone.INK
}

// ---------------------------------------------------------------- Paisa

private fun paisaCells(o: WOpts, s: PaisaSnap?, target: Double): List<WCell> = o.items.map { item ->
    when (item) {
        "profit" -> WCell(wProfitLabel(o.range), wSignedInr(s?.profit), tone(s?.profit))
        "roas" -> {
            val r = s?.roas
            val k = kRoasKind(r, target)
            val pill = when (k) {
                KKind.OK -> WPillM("Great".t(), k)
                KKind.WARN -> WPillM("A bit low".t(), k)
                KKind.BAD -> WPillM("Loss".t(), k)
                else -> null
            }
            WCell("ROAS", if (s == null) W_DASH else if (r == null) "No spend".t() else wPct(r), if (r == null) WTone.MUTED else WTone.INK, pill = pill)
        }
        "earned" -> WCell("Earnings".t(), wInr(s?.earned))
        "spend" -> WCell("All ads spend".t(), wInr(s?.spend))
        "pl" -> WCell("Profit / loss apps".t(), if (s == null) W_DASH else "${s.wins} / ${s.losses}")
        "top" -> {
            val n = s?.topName
            if (s != null && n == null) WCell("Top profit app".t(), "None".t(), WTone.MUTED)
            else WCell(n?.let { tf("Top: %s", it) } ?: "Top profit app".t(), wSignedInr(s?.topProfit), tone(s?.topProfit))
        }
        "loss" -> {
            val n = s?.lossName
            if (s != null && n == null) WCell("Top loss app".t(), "None".t(), WTone.MUTED)
            else WCell(n?.let { tf("Loss: %s", it) } ?: "Top loss app".t(), wSignedInr(s?.lossProfit), tone(s?.lossProfit))
        }
        "count" -> WCell(if (s == null) "Apps".t() else tf("Apps · %s with ads", s.withAds), if (s == null) W_DASH else num(s.apps.toDouble()))
        else -> WCell(item, W_DASH)
    }
}

// ---------------------------------------------------------------- Google Ads

/** Account ki currency ka nishaan. */
private fun wSym(cur: String): String = when (cur) {
    "INR" -> "₹"
    "USD" -> "\$"
    "EUR" -> "€"
    "GBP" -> "£"
    "" -> ""
    else -> "$cur "
}

private fun wCash(v: Double, cur: String): String = (if (v < 0) "−" else "") + wSym(cur) + num(abs(v), 0)

private fun adsCells(o: WOpts, s: AdsSnap?): List<WCell> = o.items.map { item ->
    when (item) {
        "spend" -> {
            val main = s?.spend?.firstOrNull()
            val rest = s?.spend?.drop(1).orEmpty().filter { it.second != 0.0 }
            WCell(
                "Google Ads spend".t(), if (main == null) W_DASH else wCash(main.second, main.first),
                sub = if (rest.isEmpty()) null else tf("and %s", rest.joinToString(" + ") { wCash(it.second, it.first) }),
                strong = true,
            )
        }
        "watch" -> {
            if (s == null) WCell("Accounts".t(), W_DASH)
            else if (s.accounts == 0) WCell("Accounts".t(), "No accounts".t(), WTone.MUTED)
            else if (s.watch.isEmpty()) WCell(tf("%s accounts", s.accounts), "", pill = WPillM("All fine".t(), KKind.OK))
            else {
                val n = s.watch.size
                val k = if (s.bad > 0) KKind.BAD else KKind.WARN
                WCell(s.watch.joinToString(", "), "", pill = WPillM(if (s.bad > 0) tf("%s act now", n) else tf("%s to watch", n), k))
            }
        }
        "pace" -> {
            val p = s?.pace
            val pill = when {
                p == null -> null
                p < -40.0 -> WPillM("Below normal".t(), KKind.WARN)
                p > 60.0 -> WPillM("Above normal".t(), KKind.WARN)
                else -> WPillM("Normal".t(), KKind.OK)
            }
            WCell("Spend pace".t(), wSignedPct(p), pill = pill)
        }
        "total" -> {
            val t = s?.totalInr
            val mixed = (s?.spend?.size ?: 0) > 1 || s?.spend?.firstOrNull()?.first.let { it != null && it != "INR" }
            WCell("Total, \$ included".t(), if (t == null) W_DASH else (if (mixed) "≈ " else "") + wInr(t))
        }
        "accounts" -> WCell(
            "Accounts".t(), if (s == null) W_DASH else num(s.accounts.toDouble()),
            pill = if (s == null || s.accounts == 0) null else WPillM(tf("%s fine", s.fine), if (s.fine == s.accounts) KKind.OK else KKind.WARN),
        )
        else -> WCell(item, W_DASH)
    }
}

// ---------------------------------------------------------------- App Health

private fun healthCells(o: WOpts, s: HealthSnap?): List<WCell> {
    val hero = WCell(
        "fine".t(), if (s == null) W_DASH else num(s.fine.toDouble()),
        of = if (s == null) null else "/" + num(s.total.toDouble()),
    )
    return listOf(hero) + o.items.map { item ->
        when (item) {
            "p0" -> when {
                s == null -> WCell("", W_DASH)
                s.p0 > 0 -> WCell("", "", pill = WPillM(tf("%s P0 act now", s.p0), KKind.BAD))
                else -> WCell("", "", pill = WPillM("No P0".t(), KKind.OK))
            }
            "worst" -> {
                val w = s?.worst.orEmpty()
                when {
                    s == null -> WCell("", W_DASH, WTone.SOFT)
                    w.isEmpty() -> WCell("", "All fine".t(), WTone.SOFT)
                    // naam lamba ho to naam kategi, "+1 aur" nahi (wJoinFit)
                    else -> WCell("", w[0], WTone.SOFT, sub = if (w.size > 1) tf("+%s more", w.size - 1) else null)
                }
            }
            "fixes" -> when {
                s == null -> WCell("", W_DASH)
                s.fixes > 0 -> WCell("", "", pill = WPillM(tf("%s AI fixes ready", s.fixes), KKind.ASK))
                else -> WCell("", tf("%s AI fixes ready", 0), WTone.MUTED)
            }
            "issues" -> WCell("", if (s == null) W_DASH else tf("%s issues open", s.issues), WTone.SOFT)
            else -> WCell("", W_DASH)
        }
    }
}

// ---------------------------------------------------------------- rang (Glance + preview dono)

/** Widget ke rang. Card / band / text kit ke tokens se; band ka halka rang har widget ka apna. */
internal class WPal(
    val brand: Boolean,
    val bg: Color, val band: Color,
    val ink: Color, val ink2: Color, val ink3: Color,
    val good: Color, val bad: Color, val warn: Color,
    private val pills: Map<KKind, Pair<Color, Color>>,
) {
    /** Pill ka (bg, text) rang. */
    fun pill(k: KKind): Pair<Color, Color> = pills[k] ?: (Color.Transparent to ink2)

    fun tone(t: WTone): Color = when (t) {
        WTone.INK -> ink
        WTone.SOFT -> ink2
        WTone.GOOD -> good
        WTone.BAD -> bad
        WTone.MUTED -> ink3
    }
}

internal fun wPal(style: ThemeStyle, kind: WKind): WPal {
    val brand = style == ThemeStyle.BRAND
    val c = mfcColors(style, top = brand)
    val band = when (style) {
        ThemeStyle.BRAND -> Color(0x24FFFFFF)
        ThemeStyle.DARK -> when (kind) {
            WKind.PAISA -> Color(0xFF3A311C)
            WKind.ADS -> Color(0xFF1D2B4A)
            WKind.HEALTH -> Color(0xFF3D1F36)
        }
        ThemeStyle.LIGHT -> when (kind) {
            WKind.PAISA -> Color(0xFFFFF1CC)
            WKind.ADS -> Color(0xFFE1EBFF)
            WKind.HEALTH -> Color(0xFFFBE2F2)
        }
    }
    return WPal(
        brand = brand,
        bg = if (brand) Color(0xFF4A1FC4) else c.card,
        band = band,
        ink = c.ink, ink2 = c.ink2, ink3 = c.ink3,
        // brand gradient par number safed (mockup); haal pill batati hai
        good = if (brand) c.ink else c.green, bad = if (brand) c.ink else c.red, warn = c.amber,
        pills = mapOf(
            KKind.OK to (c.pOkBg to c.pOkFg),
            KKind.WARN to (c.pWarnBg to c.pWarnFg),
            KKind.BAD to (c.pBadBg to c.pBadFg),
            KKind.ASK to (c.pAskBg to c.pAskFg),
            KKind.NEUTRAL to (c.pNBg to c.pNFg),
        ),
    )
}

// ---------------------------------------------------------------- naap (text kitni jagah lega)

/** Text ki andaazan chaudai (dp): Roboto ka ek akshar ~0.56 em; kuch phones ke font chaude, isliye 0.6. */
internal fun wTextW(s: String, sp: Float, scale: Float): Float = s.length * sp * 0.6f * scale

/** Bada (bold) number kitna bada (sp) ho ki jagah me aa jaye (kabhi "..." nahi). */
internal fun wFitSp(s: String, room: Float, max: Float, min: Float, scale: Float): Float {
    if (s.isEmpty() || room <= 0f) return max
    val fit = room / (s.length * 0.64f * scale)
    return fit.coerceIn(min, max)
}

/** "AI Pedometer +1 aur": jagah kam ho to naam chhota, "+1 aur" hamesha dikhe. */
internal fun wJoinFit(main: String, tail: String?, room: Float, sp: Float, scale: Float): String {
    if (tail == null) return main
    val full = "$main $tail"
    if (wTextW(full, sp, scale) <= room) return full
    val keep = ((room - wTextW("… $tail", sp, scale)) / (sp * 0.6f * scale)).toInt()
    return main.take(keep.coerceAtLeast(1)).trimEnd() + "… " + tail
}

/** Pill ki chaudai (dp): icon + text + padding. */
internal fun wPillW(p: WPillM, scale: Float): Float = 6f + 16f + 4f + wTextW(p.text, 13f, scale) + 10f

// ---------------------------------------------------------------- jagah ka hisaab (Glance + preview dono)

internal const val W_BAND = 40f    // upar ka band (dp)
internal const val W_PAD = 16f     // side padding
internal const val W_TOP = 8f      // band ke neeche
internal const val W_BOTTOM = 6f   // neeche
internal const val W_GAP = 6f      // do line ke beech
internal const val W_COL_GAP = 12f // do column ke beech

/** Ek line ki oonchai (dp) har sp par (font padding samet). */
internal fun wLineK(scale: Float): Float = 1.32f * scale

/** Label (13sp) ki line. */
internal fun wLabelH(scale: Float): Float = 18f * scale

/** Chhota number (18sp) ya pill (26dp) wali line. */
internal fun wSmallH(scale: Float): Float = maxOf(24f * scale, 26f)

/** Band: MFC nishaan dikhe? samay dikhe? (jagah kam ho to pehle samay, phir nishaan hatta hai) */
internal class WBandPlan(val mark: Boolean, val time: Boolean, val refresh: Boolean)

internal fun wBandPlan(v: WView, w: Float, scale: Float): WBandPlan {
    val titleW = wTextW(v.title, 14f, scale)
    if (v.login) return WBandPlan(14f + 24f + 18f + 8f + titleW + 14f <= w, time = false, refresh = false)
    // pehle samay (purana data = "5 ghante pehle" zaroori), phir nishaan
    val left = 14f + 18f + 8f + titleW + 8f
    val timeW = 10f + 14f + 6f + wTextW(v.time, 13f, scale) + 14f
    val time = v.time.isNotEmpty() && left + timeW <= w
    val mark = left + 24f + (if (time) timeW else 48f) <= w
    return WBandPlan(mark, time = time, refresh = true)
}

/** Bade number ke saath "aur $120" tabhi jab number kam se kam 20sp rahe. */
internal fun wShowSub(c: WCell, room: Float, scale: Float): Boolean {
    val sub = c.sub ?: return false
    if (c.value.isEmpty()) return true
    return room - 6f - wTextW(sub, 14f, scale) >= wTextW(c.value, 20f, scale)
}

/** Number kitna bada ho ki sub + pill (kam se kam icon) ke saath jagah me aa jaye. */
internal fun wValueSp(c: WCell, room: Float, max: Float, min: Float, scale: Float): Float {
    if (c.value.isEmpty()) return max
    var r = room
    val sub = c.sub
    if (sub != null && wShowSub(c, room, scale)) r -= 6f + wTextW(sub, 14f, scale)
    if (c.pill != null) r -= 8f + 26f
    return wFitSp(c.value, r, max, min, scale)
}

/** Pill poori (icon + shabd) aayegi ya sirf icon. */
internal fun wPillFull(c: WCell, room: Float, sp: Float, scale: Float): Boolean {
    val pill = c.pill ?: return false
    if (c.value.isEmpty()) return true
    return wTextW(c.value, sp, scale) + 8f + wPillW(pill, scale) <= room
}

/**
 * Paisa / Google Ads: chaudi (>= 250dp) me do column (bada + 3 chhote), patli me ek column (bada + chhoti lines).
 * heroSp = bade number ka size, two = doosri line dikhe, rows = patli me kitni chhoti lines.
 */
internal class WGridPlan(val narrow: Boolean, val c2: Float, val heroRoom: Float, val heroSp: Float, val two: Boolean, val rows: Int)

internal fun wGridPlan(v: WView, w: Float, bodyH: Float, scale: Float): WGridPlan {
    val inner = w - 2 * W_PAD
    val k = wLineK(scale)
    val label = wLabelH(scale)
    val hero = v.cells.firstOrNull()
    if (w < 250f) {
        val rowH = wSmallH(scale) + 4f
        var n = (v.cells.size - 1).coerceAtLeast(0)
        while (n > 0 && W_TOP + label + 22f * k + n * rowH + W_BOTTOM > bodyH) n--
        val fit = if (hero == null) 32f else wValueSp(hero, inner, 32f, 18f, scale)
        val sp = minOf(fit, (bodyH - W_TOP - W_BOTTOM - label - n * rowH) / k).coerceIn(16f, 32f)
        return WGridPlan(true, 0f, inner, sp, false, n)
    }
    // daayan column utna chauda jitna uske number + pill ko chahiye (36%..50%), baaki bade number ko
    val avail = inner - W_COL_GAP
    val want = maxOf(wSmallNeed(v.cells.getOrNull(1), scale), wSmallNeed(v.cells.getOrNull(3), scale))
    val c2 = want.coerceIn(avail * 0.36f, avail * 0.5f).coerceAtMost(190f)
    val heroRoom = avail - c2
    val fit = if (hero == null) 32f else wValueSp(hero, heroRoom, 32f, 18f, scale)
    val twoSp = minOf(fit, (bodyH - W_TOP - W_BOTTOM - label - W_GAP - label - wSmallH(scale)) / k)
    val two = v.cells.size > 2 && twoSp >= 20f
    val sp = if (two) twoSp else minOf(fit, (bodyH - W_TOP - W_BOTTOM - label) / k)
    return WGridPlan(false, c2, heroRoom, sp.coerceIn(16f, 32f), two, 0)
}

/** Chhote khane ko kitni chaudai chahiye: number (18sp) + pill. */
private fun wSmallNeed(c: WCell?, scale: Float): Float {
    if (c == null) return 0f
    val v = if (c.value.isEmpty()) 0f else wTextW(c.value, 18f, scale)
    val p = c.pill?.let { (if (v > 0f) 8f else 0f) + wPillW(it, scale) } ?: 0f
    return v + p
}

/** App Health: bada "21/25 theek" + kitni chhoti lines aayengi. */
internal class WHealthPlan(val heroSp: Float, val lines: Int)

internal fun wLineH(c: WCell, scale: Float): Float = if (c.pill != null) wSmallH(scale) else 19f * scale

internal fun wHealthPlan(v: WView, w: Float, bodyH: Float, scale: Float): WHealthPlan {
    val hero = v.cells.firstOrNull() ?: return WHealthPlan(32f, 0)
    val inner = w - 2 * W_PAD
    val k = wLineK(scale)
    val ofW = hero.of?.let { wTextW(it, 18f, scale) } ?: 0f
    val capW = 6f + wTextW(hero.label, 13f, scale)
    val fit = wFitSp(hero.value, inner - ofW - capW, 40f, 20f, scale)
    val lines = v.cells.drop(1)
    fun used(m: Int): Float = lines.take(m).sumOf { (wLineH(it, scale) + W_GAP).toDouble() }.toFloat()
    var n = lines.size
    while (n > 0 && W_TOP + 24f * k + used(n) + W_BOTTOM > bodyH) n--
    val sp = minOf(fit, (bodyH - W_TOP - W_BOTTOM - used(n)) / k).coerceIn(20f, 40f)
    return WHealthPlan(sp, n)
}
