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

fun minutes(sec: Double?): String = if (sec == null) "–" else num(sec / 60, 1) + " min"

private val sqlTs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
private val tOnlyF = SimpleDateFormat("HH:mm", Locale.US)
private val tFullF = SimpleDateFormat("dd MMM HH:mm", Locale.US)
private val dayIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val dayOut = SimpleDateFormat("dd MMM", Locale.US)
private val dayOutY = SimpleDateFormat("dd MMM yyyy", Locale.US)

/** Server UTC timestamp ("2026-10-20 08:15:00") → phone ke time mein HH:mm. ISO bhi chalega. */
fun tOnly(ts: String?): String = parseTs(ts)?.let { tOnlyF.format(it) } ?: (ts ?: "–").takeLast(8).take(5)
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
fun agoText(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 5 -> "abhi"
        s < 60 -> "$s sec pehle"
        s < 3600 -> "${s / 60} min pehle"
        else -> "${s / 3600} ghante pehle"
    }
}
