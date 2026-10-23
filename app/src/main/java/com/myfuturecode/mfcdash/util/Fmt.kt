package com.myfuturecode.mfcdash.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Indian grouping (1,23,456) — Java ka DecimalFormat sirf 3-3 karta hai, isliye khud. */

fun num(v: Double?, dec: Int = 0): String {
    if (v == null || v.isNaN()) return "–"
    val neg = v < 0
    val rounded = BigDecimal(abs(v)).setScale(dec, RoundingMode.HALF_UP)
    val whole = rounded.toBigInteger().toString()
    val frac = if (dec > 0) rounded.toPlainString().substringAfter('.', "").padEnd(dec, '0') else ""
    val grouped = if (whole.length <= 3) whole else {
        val last3 = whole.takeLast(3)
        val rest = whole.dropLast(3)
        rest.reversed().chunked(2).joinToString(",") { it.reversed() }.let { r -> r.split(",").reversed().joinToString(",") } + "," + last3
    }
    return (if (neg) "-" else "") + grouped + (if (dec > 0) ".$frac" else "")
}
fun inr(v: Double?, dec: Int = 0): String = if (v == null || v.isNaN()) "–" else (if (v < 0) "-₹" else "₹") + num(abs(v), dec)
fun usd(v: Double?, dec: Int = 2): String = if (v == null || v.isNaN()) "–" else "$" + num(v, dec)
fun pct(v: Double?, dec: Int = 1): String = if (v == null || v.isNaN()) "–" else num(v * 100, dec) + "%"
fun roasPct(v: Double?): String = if (v == null) "–" else num(v * 100, 0) + "%"
fun signed(v: Double?, dec: Int = 0, suffix: String = ""): String = if (v == null || v.isNaN()) "–" else (if (v > 0) "▲ " else if (v < 0) "▼ " else "") + num(abs(v), dec) + suffix

/** Chhota number: 12.4K / 1.2M (tiles ke liye). */
fun short(v: Double?): String {
    if (v == null || v.isNaN()) return "–"
    val a = abs(v)
    return when {
        a >= 1_00_00_000 -> num(v / 1_00_00_000, 2) + " Cr"
        a >= 1_00_000 -> num(v / 1_00_000, 2) + " L"
        a >= 10_000 -> num(v / 1000, 1) + "K"
        else -> num(v, if (a < 100 && a != a.toLong().toDouble()) 1 else 0)
    }
}

/** Bahut chhota ₹ (patti ke sire par budget): ₹950 · ₹4.2K · ₹70K · ₹1.5L · ₹2.3Cr (".0" nahi). */
fun inrK(v: Double?): String {
    if (v == null || !v.isFinite()) return "–"
    val a = abs(v)
    fun one(x: Double) = num(x, 1).removeSuffix(".0")
    val body = when {
        a < 999.5 -> num(a, 0)
        a < 9_950 -> one(a / 1000) + "K"
        a < 99_500 -> num(a / 1000, 0) + "K"
        a < 99_95_000 -> one(a / 1_00_000) + "L"
        else -> one(a / 1_00_00_000) + "Cr"
    }
    return (if (v < 0) "-₹" else "₹") + body
}

fun minutes(sec: Double?): String = if (sec == null) "–" else num(sec / 60, 1) + " min"

private val sqlTs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
private val tOnlyF = SimpleDateFormat("HH:mm", Locale.US)
private val tFullF = SimpleDateFormat("dd MMM HH:mm", Locale.US)
private val dayIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val dayOut = SimpleDateFormat("dd MMM", Locale.US)
private val dayOutY = SimpleDateFormat("dd MMM yyyy", Locale.US)

/** Server UTC timestamp ("2026-10-20 08:15:00") → phone ke time mein HH:mm. ISO bhi chalega. */
fun tOnly(ts: String?): String = parseTs(ts)?.let { tOnlyF.format(it) } ?: (ts ?: "–").takeLast(8).take(5)
fun tsMs(ts: String?): Long? = parseTs(ts)?.time
fun tFull(ts: String?): String = parseTs(ts)?.let { tFullF.format(it) } ?: (ts ?: "–")
private fun parseTs(ts: String?): java.util.Date? {
    if (ts.isNullOrBlank()) return null
    val t = ts.replace('T', ' ').substringBefore('.').substringBefore('+').substringBefore('Z')
    return runCatching { sqlTs.parse(t) }.getOrNull()
}
fun fmtDay(d: String?, year: Boolean = false): String {
    if (d.isNullOrBlank()) return "–"
    val x = runCatching { dayIn.parse(d) }.getOrNull() ?: return d
    return (if (year) dayOutY else dayOut).format(x)
}

private val dayWk = SimpleDateFormat("EEE, dd MMM", Locale.US)
private val dayNum = SimpleDateFormat("dd", Locale.US)
private val dayYm = SimpleDateFormat("yyyyMM", Locale.US)
private val dayY = SimpleDateFormat("yyyy", Locale.US)

/** "2026-10-22" → "Wed, 22 Oct" (hafte ke din ke saath; ek din wala period). */
fun fmtDayWk(d: String?): String {
    if (d.isNullOrBlank()) return "–"
    val x = runCatching { dayIn.parse(d) }.getOrNull() ?: return d
    return dayWk.format(x)
}

/**
 * Chhota period: ek hi mahina = "16 – 22 Oct", warna "28 Sep – 04 Oct"; ek hi din = "22 Oct". Saal: is saal ka = nahi likhte;
 * pichhle saal ka = ant me ("16 – 22 Oct 2025"); do saal = dono me ("28 Oct 2025 – 04 Oct 2026", warna ulta / chhota lagta).
 */
fun fmtDayRange(from: String?, to: String?): String {
    if (from.isNullOrBlank() || to.isNullOrBlank() || from == to) return fmtDay(from?.ifBlank { null } ?: to)
    val a = runCatching { dayIn.parse(from) }.getOrNull()
    val b = runCatching { dayIn.parse(to) }.getOrNull()
    if (a == null || b == null) return fmtDay(from) + " – " + fmtDay(to)
    if (dayY.format(a) != dayY.format(b)) return dayOutY.format(a) + " – " + dayOutY.format(b)
    val end = if (dayY.format(b) == dayY.format(java.util.Date())) dayOut.format(b) else dayOutY.format(b)
    return (if (dayYm.format(a) == dayYm.format(b)) dayNum.format(a) else dayOut.format(a)) + " – " + end
}

/** Server ke din (from / to, "aaj", "kal") IST ke hain, phone ka time zone kuch bhi ho. */
private val IST = TimeZone.getTimeZone("Asia/Kolkata")
private val dayIst = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = IST }

/** "2026-10-22" ka ant: agle din 00:00 IST, ms me. Galat taareekh = null. */
fun dayEndMs(d: String?): Long? {
    if (d.isNullOrBlank()) return null
    val x = runCatching { dayIst.parse(d) }.getOrNull() ?: return null
    return java.util.Calendar.getInstance(IST).apply { time = x; add(java.util.Calendar.DAY_OF_MONTH, 1) }.timeInMillis
}

/** IST ki taareekh "2026-10-22": 0 = aaj, -1 = kal (server ke period isi din se). */
fun istDay(addDays: Int = 0): String =
    dayIst.format(java.util.Calendar.getInstance(IST).apply { add(java.util.Calendar.DAY_OF_MONTH, addDays) }.time)
fun agoText(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 5 -> "just now".t()
        s < 60 -> tf("%s sec ago", s)
        s < 3600 -> tf("%s min ago", s / 60)
        else -> tf("%s hours ago", s / 3600)
    }
}
