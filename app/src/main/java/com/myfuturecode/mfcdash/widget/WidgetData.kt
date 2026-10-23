package com.myfuturecode.mfcdash.widget

import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.data.AlertCard
import com.myfuturecode.mfcdash.data.ApiException
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.GAccount
import com.myfuturecode.mfcdash.data.GAppBudget
import com.myfuturecode.mfcdash.data.IssueApp
import com.myfuturecode.mfcdash.data.PayItem
import com.myfuturecode.mfcdash.data.PayLast
import com.myfuturecode.mfcdash.data.TopAccount
import com.myfuturecode.mfcdash.data.TopLiveApp
import com.myfuturecode.mfcdash.data.TopPromoApp
import com.myfuturecode.mfcdash.data.TopSpendApp
import com.myfuturecode.mfcdash.data.alertsTop
import com.myfuturecode.mfcdash.data.dashAppName
import com.myfuturecode.mfcdash.data.healthTop
import com.myfuturecode.mfcdash.data.monitoredAccounts
import com.myfuturecode.mfcdash.data.payItemApps
import com.myfuturecode.mfcdash.data.payLast
import com.myfuturecode.mfcdash.data.promoTop
import com.myfuturecode.mfcdash.data.topAccountName
import com.myfuturecode.mfcdash.data.topAccounts
import com.myfuturecode.mfcdash.data.topLiveApps
import com.myfuturecode.mfcdash.data.topSpendApps
import com.myfuturecode.mfcdash.data.withBudgets
import com.myfuturecode.mfcdash.data.withLive30
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.mapObj
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.sn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Widget ka data: server se laao, chhota "snap" banao (sirf numbers + naam), JSON me cache.
// Label / rang render ke waqt bante hain, isliye bhasha ya theme badle to purana snap bhi chalta hai.
// Top 3 ka hisaab data/TopCards.kt me (Home bhi wahi use kare).

/**
 * Paisa widget: chune samay + apps ka munafa, kamai (= ads se + payments), kharcha, ROAS, abhi live users.
 * ads / pay null = purana snap (unke bina bana), "—" dikhe. allApps = picker ki list.
 */
internal class PaisaSnap(
    val at: Long, val profit: Double, val earned: Double, val ads: Double?, val pay: Double?, val spend: Double, val roas: Double?,
    val live: Double?, val allApps: List<Pair<String, String>>,
) {
    fun toJson(): JSONObject = JSONObject().put("at", at).putD("profit", profit).putD("earned", earned).putD("ads", ads).putD("pay", pay)
        .putD("spend", spend).putD("roas", roas).putD("live", live).put("all", pairs(allApps))

    companion object {
        fun of(j: JSONObject?): PaisaSnap? {
            if (j == null || !j.has("at")) return null
            return PaisaSnap(
                j.optLong("at"), j.d("profit"), j.d("earned"), j.fin("ads"), j.fin("pay"), j.d("spend"), j.fin("roas"),
                j.fin("live"), unpairs(j.optJSONArray("all")),
            )
        }
    }
}

/**
 * Ads + Live widget: baayein "By account" = aaj ke top 3 Google Ads accounts (budget %), "By app" = aaj ka kul Google Ads
 * kharcha (total, ₹) + sabse zyada kharch wale 3 apps (spend: kharcha + ROAS + aaj ka budget ₹ / %); daayein top 3 live apps
 * (30 min + ROAS). set = false: Google Ads monitor server par set nahi. all = picker ke accounts.
 * by nahi (purana snap) = "account"; spend null = apps wala data nahi (purana / accounts wala snap); bud / bpct nahi = budget pata nahi.
 */
internal class AdsSnap(
    val at: Long, val set: Boolean, val accs: List<TopAccount>, val apps: List<TopLiveApp>, val all: List<Pair<String, String>>,
    val by: String = "account", val total: Double? = null, val spend: List<TopSpendApp>? = null,
) {
    fun toJson(): JSONObject {
        val a = JSONArray()
        accs.forEach { x ->
            a.put(JSONObject().put("cid", x.cid).put("name", x.name).put("cur", x.currency).putD("spend", x.spend)
                .putD("inr", x.spendInr).putD("budget", x.budget).putD("pct", x.budgetPct))
        }
        val p = JSONArray()
        apps.forEach { x ->
            p.put(JSONObject().put("pkg", x.pkg).put("name", x.name).putD("l6", x.live6).putD("l30", x.live30).putD("roas", x.roas))
        }
        val j = JSONObject().put("at", at).put("set", set).put("accs", a).put("apps", p).put("all", pairs(all)).put("by", by).putD("total", total)
        val sp = spend
        if (sp != null) {
            val q = JSONArray()
            sp.forEach { x ->
                q.put(
                    JSONObject().put("pkg", x.pkg).put("name", x.name).putD("inr", x.spendInr).putD("roas", x.roas)
                        .putD("bud", x.budgetInr).putD("bpct", x.budgetPct),
                )
            }
            j.put("spend", q)
        }
        return j
    }

    companion object {
        fun of(j: JSONObject?): AdsSnap? {
            if (j == null || !j.has("at")) return null
            val accs = j.arr("accs").mapObj { x ->
                TopAccount(x.s("cid"), x.s("name"), x.s("cur"), x.d("spend"), x.fin("inr"), x.fin("budget"), x.fin("pct"))
            }
            val apps = j.arr("apps").mapObj { x -> TopLiveApp(x.s("pkg"), x.s("name"), x.d("l6"), x.fin("l30"), x.fin("roas")) }
            val spend = j.optJSONArray("spend")?.let { a ->
                a.mapObj { x ->
                    TopSpendApp(x.s("pkg"), x.s("name"), x.d("inr"), x.fin("roas"), x.fin("bud")?.takeIf { it > 0 }, x.fin("bpct")?.takeIf { it >= 0 })
                }
            }
            return AdsSnap(
                j.optLong("at"), !j.has("set") || j.b("set"), accs, apps, unpairs(j.optJSONArray("all")),
                j.optString("by").takeIf { it in WOpts.BY } ?: "account", j.fin("total"), spend,
            )
        }
    }
}

/** App Health widget: kul / issue wale / theek apps + sabse zyada issue wale 3 apps. consoles = picker ki list. */
internal class HealthSnap(
    val at: Long, val total: Int, val bad: Int, val fine: Int, val top: List<IssueApp>, val consoles: List<Pair<String, String>>,
) {
    fun toJson(): JSONObject {
        val a = JSONArray()
        top.forEach { x -> a.put(JSONObject().put("pkg", x.pkg).put("name", x.name).put("open", x.open).put("p0", x.p0)) }
        return JSONObject().put("at", at).put("total", total).put("bad", bad).put("fine", fine).put("top", a).put("consoles", pairs(consoles))
    }

    companion object {
        fun of(j: JSONObject?): HealthSnap? {
            if (j == null || !j.has("at")) return null
            return HealthSnap(
                j.optLong("at"), j.d("total").toInt(), j.d("bad").toInt(), j.d("fine").toInt(),
                j.arr("top").mapObj { x -> IssueApp(x.s("pkg"), x.s("name"), x.d("open").toInt(), x.b("p0")) },
                unpairs(j.optJSONArray("consoles")),
            )
        }
    }
}

/** Alerts widget: naye (unread) kitne + sabse naye 3 khule alerts. missing = server par alerts.php nahi. */
internal class AlertsSnap(val at: Long, val unread: Int, val open: Int, val items: List<AlertCard>, val missing: Boolean) {
    fun toJson(): JSONObject {
        val a = JSONArray()
        items.forEach { x ->
            a.put(
                JSONObject().put("id", x.id).put("kind", x.kind.name).put("app", x.app).put("campaign", x.campaign)
                    .put("title", x.title).put("title_hi", x.titleHi).put("label", x.label).putS("arg", x.arg),
            )
        }
        return JSONObject().put("at", at).put("unread", unread).put("open", open).put("items", a).put("missing", missing)
    }

    companion object {
        fun of(j: JSONObject?): AlertsSnap? {
            if (j == null || !j.has("at")) return null
            val items = j.arr("items").mapObj { x ->
                AlertCard(
                    x.optLong("id"), KKind.entries.firstOrNull { it.name == x.s("kind") } ?: KKind.NEUTRAL,
                    x.s("app"), x.s("campaign"), x.s("title"), x.s("title_hi"), x.s("label"), x.sn("arg"),
                )
            }
            return AlertsSnap(j.optLong("at"), j.d("unread").toInt(), j.d("open").toInt(), items, j.b("missing"))
        }
    }
}

/** Cross promo widget: aaj ke promo installs / views / clicks + sabse zyada installs wale 3 promote hue apps. */
internal class PromoSnap(val at: Long, val installs: Double, val views: Double, val clicks: Double, val top: List<TopPromoApp>) {
    fun toJson(): JSONObject {
        val a = JSONArray()
        top.forEach { x ->
            a.put(JSONObject().put("pkg", x.pkg).put("name", x.name).putD("inst", x.installs).putD("views", x.views).putD("clicks", x.clicks))
        }
        return JSONObject().put("at", at).putD("inst", installs).putD("views", views).putD("clicks", clicks).put("top", a)
    }

    companion object {
        fun of(j: JSONObject?): PromoSnap? {
            if (j == null || !j.has("at")) return null
            return PromoSnap(
                j.optLong("at"), j.d("inst"), j.d("views"), j.d("clicks"),
                j.arr("top").mapObj { x -> TopPromoApp(x.s("pkg"), x.s("name"), x.d("inst"), x.d("views"), x.d("clicks")) },
            )
        }
    }
}

/**
 * Payments widget: aaj ka ₹ (paid orders ka jod), paid / failed ginti, aakhri 2 payments (paid / failed).
 * missing = server par api/payments.php nahi (404): widget "Update server" dikhata hai.
 */
internal class PaySnap(
    val at: Long, val amount: Double, val paid: Int, val failed: Int, val last: List<PayLast>, val missing: Boolean,
) {
    fun toJson(): JSONObject {
        val a = JSONArray()
        last.forEach { x ->
            a.put(
                JSONObject().put("pkg", x.pkg).put("name", x.name).put("ts", x.ts).put("plan", x.plan).putD("amt", x.amount)
                    .put("paid", x.paid).put("reason", x.reason),
            )
        }
        return JSONObject().put("at", at).putD("amt", amount).put("paid", paid).put("failed", failed).put("last", a).put("missing", missing)
    }

    companion object {
        fun of(j: JSONObject?): PaySnap? {
            if (j == null || !j.has("at")) return null
            val last = j.arr("last").mapObj { x -> PayLast(x.s("pkg"), x.s("name"), x.s("ts"), x.s("plan"), x.d("amt"), x.b("paid"), x.s("reason")) }
            return PaySnap(j.optLong("at"), j.d("amt"), j.d("paid").toInt(), j.d("failed").toInt(), last, j.b("missing"))
        }
    }
}

// JSONObject.put(Double) NaN / Infinity par exception deta hai: aise number chhod do
private fun JSONObject.putD(k: String, v: Double?): JSONObject = if (v != null && v.isFinite()) put(k, v) else this
private fun JSONObject.putS(k: String, v: String?): JSONObject = if (v != null) put(k, v) else this
private fun JSONObject.fin(k: String): Double? = dn(k)?.takeIf { it.isFinite() }

private fun pairs(l: List<Pair<String, String>>): JSONArray {
    val a = JSONArray()
    l.forEach { (x, y) -> a.put(JSONArray().put(x).put(y)) }
    return a
}

private fun unpairs(a: JSONArray?): List<Pair<String, String>> =
    if (a == null) emptyList() else (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { it.optString(0) to it.optString(1) } }

/**
 * Ek sync ka chhota cache: data.php (sab apps, aaj) Paisa aur Ads + Live dono ko chahiye, ek hi baar laao; Google Ads
 * overview bhi (By account + By app dono widget ho to ek call). Ek widget ke baad doosra (thread-safe nahi; ek widget ke
 * andar data() aur gads() saath chal sakte hain, dono alag cache).
 */
internal class WFetch(private val app: App) {
    private val dash = HashMap<String, DashData>()
    private var gads: JSONObject? = null
    private var names: DashData? = null
    private var namesTried = false

    /** gads_health.php?view=overview; sirf sahi jawab yaad (galti har baar phir se). */
    suspend fun gads(): JSONObject = gads ?: app.api.gGet("overview").also { gads = it }

    suspend fun data(range: String, from: String? = null, to: String? = null): DashData {
        val k = "$range|$from|$to"
        dash[k]?.let { return it }
        val d = app.api.data("all", range, from, to)
        if (d.r > 0 && d.r.isFinite()) WidgetStore.saveRate(app, d.r)
        dash[k] = d
        return d
    }

    /**
     * Apps ke naam (Cross promo / Payments): is sync ka data.php ho to wahi, warna disk wala data.php (network nahi; naam
     * roz nahi badalte), woh bhi na ho to ek baar data.php (aaj). Kuch na mile = package ka aakhri hissa; galti widget na roke.
     */
    suspend fun name(pkg: String): String {
        if (!namesTried) {
            namesTried = true
            names = dash.values.firstOrNull() ?: try {
                app.api.dataCached("all", "today")?.value ?: data("today")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        return dashAppName(dash.values.firstOrNull() ?: names, pkg)
    }
}

/** Server se ek widget ka data. Fail ho to exception (cache wala snap rehta hai). */
internal object WidgetData {

    suspend fun fetch(app: App, kind: WKind, o: WOpts, f: WFetch = WFetch(app)): JSONObject = when (kind) {
        WKind.PAISA -> paisa(o, f).toJson()
        WKind.ADS -> ads(app, o, f).toJson()
        WKind.HEALTH -> health(app, o).toJson()
        WKind.ALERTS -> alerts(app, o).toJson()
        WKind.PROMO -> promo(app, f).toJson()
        WKind.PAY -> pay(app, f).toJson()
    }

    // ---- Paisa (api/data.php, sab apps; chune apps phone par jode jaate hain)

    private suspend fun paisa(o: WOpts, f: WFetch): PaisaSnap {
        val (range, from, to) = rangeArgs(o.range)
        val d = f.data(range, from, to)
        val r = d.r
        val chosen = o.pick.toSet()
        val ads: Double
        val pay: Double
        val earned: Double
        val spend: Double
        val profit: Double
        val roas: Double?
        val live: Double
        if (chosen.isEmpty()) {
            // Home jaisa: Kamai = ad revenue + payments, kharcha = Google Ads
            val k = d.kpi
            ads = k.revInr
            pay = k.purchaseInr
            earned = ads + pay
            spend = k.gadsCostInr
            profit = k.profitInr
            roas = k.roasTotal ?: (if (spend > 0) earned / spend else null)
            live = d.liveTotal
        } else {
            val per = d.perApp.filter { it.pkg in chosen }
            ads = per.sumOf { it.revInr(r) }
            pay = per.sumOf { it.purchaseInr }
            earned = ads + pay
            spend = per.sumOf { it.gadsCostInr }
            profit = earned - spend
            roas = if (spend > 0) earned / spend else null
            live = chosen.sumOf { p -> d.liveByApp[p] ?: d.perApp.firstOrNull { it.pkg == p }?.live ?: 0.0 }
        }
        val all = (d.apps.map { it.pkg to it.name } + d.perApp.map { it.pkg to it.name })
            .filter { it.first.isNotBlank() }
            .map { (p, n) -> p to n.ifBlank { d.appName(p) } }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        return PaisaSnap(System.currentTimeMillis(), profit, earned, ads, pay, spend, roas?.takeIf { it.isFinite() }, live, all)
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

    // ---- Ads + Live (By account: gads_health.php?view=overview; dono me data.php aaj + har top app ka app.php)

    private suspend fun ads(app: App, o: WOpts, f: WFetch): AdsSnap {
        if (!o.byApp) return adsByAccount(app, o, f)
        // By app: baayan aadha bhi data.php (aaj) se: kul Google Ads kharcha (Paisa widget wala) + top 3 kharch wale apps.
        // Google Ads monitor ki zaroorat nahi; net / login ki dikkat = exception (purana snap rahe).
        // Apps ka budget overview se, saath me; woh fail (404 = monitor nahi, 500, parse) = bas budget pata nahi
        val (d, bud) = coroutineScope {
            val b = async { appBudgets(f) }
            f.data("today") to b.await()
        }
        val top = topLiveApps(d)
        val l30 = live30(app, top)
        return AdsSnap(
            System.currentTimeMillis(), true, emptyList(), withLive30(top, l30), emptyList(),
            by = "app", total = d.kpi.gadsCostInr.takeIf { it.isFinite() }, spend = withBudgets(topSpendApps(d), bud, d.r),
        )
    }

    /** Overview ka app_budgets; koi bhi galti = khaali (widget na ruke), cancel aage jaye. */
    private suspend fun appBudgets(f: WFetch): List<GAppBudget> = try {
        GAppBudget.list(f.gads())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    /** 30 min ke active: sirf in apps ka app.php, saath me; koi fail = "—". */
    private suspend fun live30(app: App, top: List<TopLiveApp>): Map<String, Double?> = coroutineScope {
        top.map { a ->
            async {
                a.pkg to try {
                    app.api.extra(a.pkg, "today")?.active?.live30m
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().toMap()
    }

    private suspend fun adsByAccount(app: App, o: WOpts, f: WFetch): AdsSnap {
        // Google Ads monitor server par nahi (404, GadsVm jaisa) = "Set nahi"; net / login ki dikkat = exception (purana snap rahe).
        // Server ki baaki galti (500 jaisi): pichhle snap ke accounts rakho, Live wala hissa phir bhi naya ("Set nahi" cache na ho)
        var set = true
        var accs: List<GAccount> = emptyList()
        var old: AdsSnap? = null
        try {
            accs = monitoredAccounts(f.gads().arr("accounts").mapObj(GAccount::of))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.network || e.unauthorized) throw e
            if (e.message?.contains("404") == true) set = false
            else old = AdsSnap.of(WidgetStore.snap(app, o.dataKey(WKind.ADS))) ?: throw e
        }
        if (old == null && accs.isEmpty()) set = false
        val d = f.data("today")
        val top = topLiveApps(d)
        val l30 = live30(app, top)
        val prev = old
        return if (prev != null) {
            AdsSnap(System.currentTimeMillis(), prev.set, prev.accs, withLive30(top, l30), prev.all)
        } else {
            AdsSnap(
                System.currentTimeMillis(), set, topAccounts(accs, d.r, o.pick.toSet()), withLive30(top, l30),
                accs.map { it.cid to topAccountName(it) }.filter { it.first.isNotBlank() },
            )
        }
    }

    // ---- App Health (api/health.php: overview + consoles; 2 call)

    private suspend fun health(app: App, o: WOpts): HealthSnap {
        val ov = app.api.hOverview()
        val chosen = o.pick.toSet()
        val cons = if (chosen.isNotEmpty()) {
            app.api.hConsoles()
        } else {
            try {
                app.api.hConsoles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // list overview ke attention se ban jayegi
            }
        }
        val h = healthTop(ov, cons, chosen)
        val list = cons?.consoles?.map { it.id to it.name.ifBlank { it.id } }.orEmpty()
        return HealthSnap(System.currentTimeMillis(), h.total, h.withIssues, h.fine, h.top, list)
    }

    // ---- Alerts (api/alerts.php, sirf khule; Alerts tab wahi feed)

    private suspend fun alerts(app: App, o: WOpts): AlertsSnap {
        val d = app.api.alerts(limit = 40, open = true)
        if (d == null) {
            // null = alerts.php nahi YA server ki koi galti (500): pehle sahi data tha to wahi rahe, warna "—"
            if (AlertsSnap.of(WidgetStore.snap(app, o.dataKey(WKind.ALERTS)))?.missing == false) throw ApiException("alerts.php ne data nahi diya")
            return AlertsSnap(System.currentTimeMillis(), 0, 0, emptyList(), missing = true)
        }
        val t = alertsTop(d)
        return AlertsSnap(System.currentTimeMillis(), t.unread, t.open, t.items, missing = false)
    }

    // ---- Cross promo (api/crosspromo.php, sab apps, aaj; Cross promo screen wahi data)

    private suspend fun promo(app: App, f: WFetch): PromoSnap {
        val cp = app.api.crossPromo("all", "today")
        // naam ek-ek karke (suspend), phir hisaab
        val names = HashMap<String, String>()
        for (p in cp.rows.map { it.tgt }.filter { it.isNotBlank() }.distinct()) names[p] = f.name(p)
        val t = promoTop(cp.rows, { p -> names[p] ?: p.substringAfterLast('.') })
        return PromoSnap(System.currentTimeMillis(), t.installs, t.views, t.clicks, t.top)
    }

    // ---- Payments (api/payments.php, sab apps, aaj; Payments screen wahi data)

    private suspend fun pay(app: App, f: WFetch): PaySnap {
        // null = server par payments.php nahi (404): "Update server". Net / login / server ki galti = exception (purana snap rahe)
        val d = app.api.payments("all", "today") ?: return PaySnap(System.currentTimeMillis(), 0.0, 0, 0, emptyList(), missing = true)
        // items sirf app=<pkg> par aate hain: aaj jin apps me paid / failed hua (4 tak), unke saath me. Ek app fail = bas uski
        // lines nahi; net / login gaya = exception (aadhi list cache na ho)
        var bad: ApiException? = null
        val items: List<PayItem> = d.items.ifEmpty {
            coroutineScope {
                payItemApps(d.apps).map { p ->
                    async {
                        try {
                            app.api.payments(p, "today")?.items.orEmpty()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: ApiException) {
                            if (e.network || e.unauthorized) throw e
                            bad = e
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
        }
        // naam (Payments screen jaisa): data.php wala, warna server ka (apps config), warna package ka aakhri hissa
        val server = d.apps.associate { it.pkg to it.name }
        val last = payLast(items, { p -> server[p].orEmpty() }).map { x ->
            val dn = f.name(x.pkg)
            val known = dn != x.pkg.substringAfterLast('.')
            x.copy(name = if (known) dn else x.name.takeIf { it.isNotBlank() && it != x.pkg } ?: dn)
        }
        // app wali call(s) fail aur ek bhi line nahi: "No payments" taaza data jaisa save na ho (ginti 38 paid ke saath).
        // Exception = pichhla snap rahe, header amber (net ki galti jaisa)
        bad?.let { if (last.isEmpty() && d.paid + d.failed > 0) throw it }
        return PaySnap(System.currentTimeMillis(), d.amount, d.paid, d.failed, last, missing = false)
    }
}
