package com.myfuturecode.mfcdash.widget

import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.mapObj
import com.myfuturecode.mfcdash.util.s
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Widget ka data: server se laao, chhota "snap" banao (sirf numbers + naam), JSON me cache.
// Label / rang render ke waqt bante hain, isliye bhasha ya theme badle to purana snap bhi chalta hai.

/** Paisa widget: chune samay + apps ka munafa, kamai, kharcha, ROAS, top / loss app. */
internal class PaisaSnap(
    val at: Long, val profit: Double, val earned: Double, val spend: Double, val roas: Double?,
    val wins: Int, val losses: Int, val apps: Int, val withAds: Int,
    val topName: String?, val topProfit: Double?, val lossName: String?, val lossProfit: Double?,
    val allApps: List<Pair<String, String>>,
) {
    fun toJson(): JSONObject = JSONObject().put("at", at).putD("profit", profit).putD("earned", earned).putD("spend", spend)
        .putD("roas", roas).put("wins", wins).put("losses", losses).put("apps", apps).put("with_ads", withAds)
        .putS("top_name", topName).putD("top_profit", topProfit).putS("loss_name", lossName).putD("loss_profit", lossProfit)
        .put("all", pairs(allApps))

    companion object {
        fun of(j: JSONObject?): PaisaSnap? {
            if (j == null || !j.has("at")) return null
            return PaisaSnap(
                j.optLong("at"), j.d("profit"), j.d("earned"), j.d("spend"), j.dn("roas")?.takeIf { it.isFinite() },
                j.d("wins").toInt(), j.d("losses").toInt(), j.d("apps").toInt(), j.d("with_ads").toInt(),
                j.s("top_name").ifBlank { null }, j.dn("top_profit"), j.s("loss_name").ifBlank { null }, j.dn("loss_profit"),
                unpairs(j.optJSONArray("all")),
            )
        }
    }
}

/** Google Ads widget: aaj ka kharcha (har currency alag), dhyan wale accounts, pace, kul ₹. */
internal class AdsSnap(
    val at: Long, val spend: List<Pair<String, Double>>, val watch: List<String>, val bad: Int, val pace: Double?,
    val accounts: Int, val fine: Int, val totalInr: Double?, val all: List<Pair<String, String>>,
) {
    fun toJson(): JSONObject {
        val sp = JSONArray()
        spend.forEach { (c, v) -> if (v.isFinite()) sp.put(JSONArray().put(c).put(v)) }
        return JSONObject().put("at", at).put("spend", sp).put("watch", JSONArray(watch)).put("bad", bad).putD("pace", pace)
            .put("accounts", accounts).put("fine", fine).putD("total_inr", totalInr).put("all", pairs(all))
    }

    companion object {
        fun of(j: JSONObject?): AdsSnap? {
            if (j == null || !j.has("at")) return null
            val sp = j.optJSONArray("spend")
            val spend = if (sp == null) emptyList() else (0 until sp.length()).mapNotNull { i ->
                sp.optJSONArray(i)?.let { a -> a.optString(0) to a.optDouble(1, 0.0) }
            }
            val w = j.optJSONArray("watch")
            return AdsSnap(
                j.optLong("at"), spend, if (w == null) emptyList() else (0 until w.length()).map { w.optString(it) },
                j.d("bad").toInt(), j.dn("pace")?.takeIf { it.isFinite() }, j.d("accounts").toInt(), j.d("fine").toInt(),
                j.dn("total_inr")?.takeIf { it.isFinite() }, unpairs(j.optJSONArray("all")),
            )
        }
    }
}

/** App Health widget: theek / kul apps, P0, problem wale apps, AI fix, khule issues. */
internal class HealthSnap(
    val at: Long, val fine: Int, val total: Int, val p0: Int, val worst: List<String>, val fixes: Int, val issues: Int,
    val consoles: List<Pair<String, String>>,
) {
    fun toJson(): JSONObject = JSONObject().put("at", at).put("fine", fine).put("total", total).put("p0", p0)
        .put("worst", JSONArray(worst)).put("fixes", fixes).put("issues", issues).put("consoles", pairs(consoles))

    companion object {
        fun of(j: JSONObject?): HealthSnap? {
            if (j == null || !j.has("at")) return null
            val w = j.optJSONArray("worst")
            return HealthSnap(
                j.optLong("at"), j.d("fine").toInt(), j.d("total").toInt(), j.d("p0").toInt(),
                if (w == null) emptyList() else (0 until w.length()).map { w.optString(it) },
                j.d("fixes").toInt(), j.d("issues").toInt(), unpairs(j.optJSONArray("consoles")),
            )
        }
    }
}

// JSONObject.put(Double) NaN / Infinity par exception deta hai: aise number chhod do
private fun JSONObject.putD(k: String, v: Double?): JSONObject = if (v != null && v.isFinite()) put(k, v) else this
private fun JSONObject.putS(k: String, v: String?): JSONObject = if (v != null) put(k, v) else this

private fun pairs(l: List<Pair<String, String>>): JSONArray {
    val a = JSONArray()
    l.forEach { (x, y) -> a.put(JSONArray().put(x).put(y)) }
    return a
}

private fun unpairs(a: JSONArray?): List<Pair<String, String>> =
    if (a == null) emptyList() else (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { it.optString(0) to it.optString(1) } }

/** Server se ek widget ka data. Fail ho to exception (cache wala snap rehta hai). */
internal object WidgetData {

    /** lists = Widgets screen ke liye consoles ki list bhi (Health). */
    suspend fun fetch(app: App, kind: WKind, o: WOpts, lists: Boolean = false): JSONObject = when (kind) {
        WKind.PAISA -> paisa(app, o).toJson()
        WKind.ADS -> ads(app, o).toJson()
        WKind.HEALTH -> health(app, o, lists).toJson()
    }

    // ---- Paisa (api/data.php, sab apps; chune apps phone par jode jaate hain)

    private suspend fun paisa(app: App, o: WOpts): PaisaSnap {
        val (range, from, to) = rangeArgs(o.range)
        val d = app.api.data("all", range, from, to)
        val r = d.r
        if (r > 0 && r.isFinite()) WidgetStore.saveRate(app, r)
        val chosen = o.pick.toSet()
        val per = if (chosen.isEmpty()) d.perApp else d.perApp.filter { it.pkg in chosen }
        val earned: Double
        val spend: Double
        val profit: Double
        val roas: Double?
        if (chosen.isEmpty()) {
            // Home jaisa: Kamai = ad revenue + payments, kharcha = Google Ads
            val k = d.kpi
            earned = k.revInr + k.purchaseInr
            spend = k.gadsCostInr
            profit = k.profitInr
            roas = k.roasTotal ?: (if (spend > 0) earned / spend else null)
        } else {
            earned = per.sumOf { it.revInr(r) + it.purchaseInr }
            spend = per.sumOf { it.gadsCostInr }
            profit = earned - spend
            roas = if (spend > 0) earned / spend else null
        }
        val wins = per.filter { it.profit(r) > 0 }.sortedByDescending { it.profit(r) }
        // loss app = kharcha > ₹50 aur ROAS < 100% (Home wala niyam)
        val losses = per.filter { a -> a.gadsCostInr > 50 && (a.roas(r) ?: 1.0) < 1.0 }.sortedBy { it.profit(r) }
        val top = wins.firstOrNull()
        val loss = losses.firstOrNull()
        val all = (d.apps.map { it.pkg to it.name } + d.perApp.map { it.pkg to it.name })
            .filter { it.first.isNotBlank() }
            .map { (p, n) -> p to n.ifBlank { d.appName(p) } }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        return PaisaSnap(
            System.currentTimeMillis(), profit, earned, spend, roas?.takeIf { it.isFinite() },
            wins.size, losses.size, per.size, per.count { it.gadsCostInr > 0 },
            top?.let { it.name.ifBlank { d.appName(it.pkg) } }, top?.profit(r),
            loss?.let { it.name.ifBlank { d.appName(it.pkg) } }, loss?.profit(r),
            all,
        )
    }

    /** Widget ka samay -> data.php ka range (Is mahina = custom, 1 tareekh se aaj tak). */
    private fun rangeArgs(range: String): Triple<String, String?, String?> = when (range) {
        "yesterday" -> Triple("yesterday", null, null)
        "7d" -> Triple("7d", null, null)
        "month" -> {
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            val to = f.format(cal.time)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            Triple("custom", f.format(cal.time), to)
        }
        else -> Triple("today", null, null)
    }

    // ---- Google Ads (api/gads_health.php?view=overview; sirf monitor wale accounts)

    private suspend fun ads(app: App, o: WOpts): AdsSnap {
        val j = app.api.gGet("overview")
        val all = j.arr("accounts").mapObj { it }.filter { !it.has("monitoring") || it.b("monitoring") }
        val chosen = o.pick.toSet()
        val accs = if (chosen.isEmpty()) all else all.filter { it.s("cid") in chosen }
        // har currency alag; ₹ pehle
        val spend = accs.groupBy { cur(it) }
            .map { (c, l) -> c to l.sumOf { a -> a.d("spend_today").takeIf { it.isFinite() } ?: 0.0 } }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.first == "INR" }.thenByDescending { it.second })
        val kinds = accs.map { it to acctKind(it) }
        val watch = kinds.filter { it.second != KKind.OK }.sortedBy { if (it.second == KKind.BAD) 0 else 1 }.map { name(it.first) }
        // ₹ me kul: $ ho to dashboard ka rate (pehle se ho to wahi, warna ek baar data.php se)
        val needRate = spend.any { it.first != "INR" }
        val rate = if (!needRate) null else WidgetStore.rate(app, fresh = true)
            ?: runCatching { app.api.data("all", "today").r }.getOrNull()?.takeIf { it > 0 && it.isFinite() }?.also { WidgetStore.saveRate(app, it) }
            ?: WidgetStore.rate(app, fresh = false)
        val inr = spend.map { (c, v) -> toInr(v, c, rate) }
        val total = if (inr.isEmpty() || inr.any { it == null }) null else inr.sumOf { it ?: 0.0 }
        return AdsSnap(
            System.currentTimeMillis(), spend, watch, kinds.count { it.second == KKind.BAD }, pace(accs),
            accs.size, kinds.count { it.second == KKind.OK }, total, all.map { it.s("cid") to name(it) }.filter { it.first.isNotBlank() },
        )
    }

    private fun cur(a: JSONObject): String = a.s("currency").trim().uppercase()

    private fun name(a: JSONObject): String =
        a.s("company").ifBlank { a.s("account_name") }.ifBlank { a.s("cid_fmt") }.ifBlank { a.s("cid") }

    /** Account ka haal (Home ke Google Ads module jaisa). */
    private fun acctKind(a: JSONObject): KKind {
        val hl = a.s("health").uppercase()
        return when {
            a.d("critical") > 0 || hl == "CRITICAL" -> KKind.BAD
            a.d("warning") > 0 || hl == "WARNING" || hl == "STALE" || hl == "NO_DATA" || (a.has("fresh") && !a.b("fresh")) -> KKind.WARN
            else -> KKind.OK
        }
    }

    /** Sab accounts ka pace (% normal se). Ek hi currency ho to jod kar, warna average. */
    private fun pace(accs: List<JSONObject>): Double? {
        val rows = accs.mapNotNull { a ->
            val p = a.dn("pace_pct")
            if (p == null || !p.isFinite()) null else Triple(cur(a), p, a.dn("usual_by_now")?.takeIf { it.isFinite() } ?: 0.0)
        }
        if (rows.isEmpty()) return null
        val oneCur = rows.all { it.third > 0.0 } && rows.map { it.first }.distinct().size == 1
        if (!oneCur) return rows.map { it.second }.average()
        val usual = rows.sumOf { it.third }
        val now = rows.sumOf { it.third * (1 + it.second / 100.0) }
        return (now - usual) / usual * 100.0
    }

    private fun toInr(v: Double, cur: String, rate: Double?): Double? = when (cur) {
        "INR" -> v
        "USD" -> rate?.let { v * it }
        else -> null
    }

    // ---- App Health (api/health.php: overview; consoles chune hon to consoles view se)

    private suspend fun health(app: App, o: WOpts, lists: Boolean): HealthSnap {
        val ov = app.api.hOverview()
        val chosen = o.pick.toSet()
        val cons = when {
            chosen.isNotEmpty() -> app.api.hConsoles()
            lists -> runCatching { app.api.hConsoles() }.getOrNull()
            else -> null
        }
        val list = cons?.consoles?.map { it.id to it.name.ifBlank { it.id } }.orEmpty()
        val now = System.currentTimeMillis()
        if (chosen.isEmpty() || cons == null) {
            val worst = ov.attention.distinctBy { it.pkg.ifBlank { it.app } }.map { it.app.ifBlank { it.pkg.substringAfterLast('.') } }
            return HealthSnap(now, ov.healthy.coerceAtLeast(0), ov.apps.coerceAtLeast(0), ov.issuesP0, worst, ov.aabReady, ov.issuesOpen, list)
        }
        val apps = cons.apps.filter { it.consoleId in chosen }
        val pk = apps.map { it.pkg }.toSet()
        val attn = ov.attention.filter { it.consoleId in chosen || it.pkg in pk }
        val worst = attn.distinctBy { it.pkg.ifBlank { it.app } }.map { it.app.ifBlank { it.pkg.substringAfterLast('.') } }
        val fixes = runCatching { app.api.hBuilds() }.getOrNull()?.count { it.reviewable && it.pkg in pk } ?: 0
        return HealthSnap(
            now, apps.count { it.openIssues == 0 }, apps.size,
            maxOf(attn.count { it.severity == "P0" }, apps.count { it.topSeverity == "P0" }),
            worst, fixes, apps.sumOf { it.openIssues }, list,
        )
    }
}
