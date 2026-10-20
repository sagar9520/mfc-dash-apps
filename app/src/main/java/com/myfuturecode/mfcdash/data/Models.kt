package com.myfuturecode.mfcdash.data

import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.*
import org.json.JSONObject

/** Yeh sab `api/data.php` ke jawab ka seedha roop hai (index.php jo fields padhta hai, wahi). */

data class AppInfo(val pkg: String, val name: String, val color: String) {
    companion object { fun of(j: JSONObject) = AppInfo(j.s("pkg"), j.s("name"), j.s("color", "#4A1FC4")) }
}

data class Kpi(
    val revInr: Double, val revUsd: Double, val imps: Double, val impsPerDau: Double, val ecpmUsd: Double,
    val installs: Double, val dau: Double, val sessions: Double, val arpdauUsd: Double, val avgSessionSec: Double,
    val gadsCostInr: Double, val gadsImpr: Double, val gadsClicks: Double, val gadsInstalls: Double, val gadsCpiInr: Double,
    val revFromGadsUsersInr: Double, val profitInr: Double, val roasTotal: Double?, val purchaseInr: Double,
    val purchases: Double, val purchaseFailed: Double, val crashes: Double, val uninstalls: Double, val gadsUpdated: String,
) {
    companion object {
        fun of(j: JSONObject) = Kpi(
            j.d("rev_inr"), j.d("rev_usd"), j.d("imps"), j.d("imps_per_dau"), j.d("ecpm_usd"),
            j.d("installs"), j.d("dau"), j.d("sessions"), j.d("arpdau_usd"), j.d("avg_session_sec"),
            j.d("gads_cost_inr"), j.d("gads_impr"), j.d("gads_clicks"), j.d("gads_installs"), j.d("gads_cpi_inr"),
            j.d("rev_from_gads_users_inr"), j.d("profit_inr"), j.dn("roas_total"), j.d("purchase_inr"),
            j.d("purchases"), j.d("purchase_failed"), j.d("crashes"), j.d("uninstalls"), j.s("gads_updated"),
        )
    }
}

data class PerApp(
    val pkg: String, val name: String, val color: String, val live: Double, val installs: Double, val dau: Double,
    val revUsd: Double, val imps: Double, val gadsCostInr: Double, val gadsInstalls: Double, val purchaseInr: Double,
) {
    fun revInr(r: Double) = revUsd * r
    fun profit(r: Double) = revUsd * r + purchaseInr - gadsCostInr
    /** ROAS = (ad revenue + payments) / ad spend; spend 0 ho to null. */
    fun roas(r: Double): Double? = if (gadsCostInr > 0) (revUsd * r + purchaseInr) / gadsCostInr else null
    fun cpi(): Double? = if (gadsInstalls > 0) gadsCostInr / gadsInstalls else null
    fun ecpmUsd() = if (imps > 0) revUsd / imps * 1000 else 0.0
    companion object {
        fun of(j: JSONObject) = PerApp(
            j.s("pkg"), j.s("name"), j.s("color", "#4A1FC4"), j.d("live"), j.d("installs"), j.d("dau"),
            j.d("rev_usd"), j.d("imps"), j.d("gads_cost_inr"), j.d("gads_installs"), j.d("purchase_inr"),
        )
    }
}

data class SeriesPoint(val label: String, val revUsd: Double, val installs: Double, val gadsCostInr: Double) {
    companion object { fun of(j: JSONObject) = SeriesPoint(j.s("label"), j.d("rev_usd"), j.d("installs"), j.d("gads_cost_inr")) }
}

data class GadsAccount(
    val name: String, val idFmt: String, val currency: String, val cost: Double, val impressions: Double, val clicks: Double,
    val ctr: Double?, val conversions: Double, val cpi: Double?, val campaigns: Double, val pkgs: List<String>, val updatedAt: String,
) {
    companion object {
        fun of(j: JSONObject) = GadsAccount(
            j.s("name"), j.s("id_fmt"), j.s("currency"), j.d("cost"), j.d("impressions"), j.d("clicks"),
            j.dn("ctr"), j.d("conversions"), j.dn("cpi"), j.d("campaigns"), j.arr("pkgs").strings(), j.s("updated_at"),
        )
    }
}

data class Campaign(
    val campaign: String, val account: String, val pkg: String, val impressions: Double, val clicks: Double, val ctr: Double?,
    val conversions: Double, val cpi: Double?, val cost: Double, val status: String,
) {
    companion object {
        fun of(j: JSONObject) = Campaign(
            j.s("campaign"), j.s("account_name", j.s("account", j.s("account_id"))), j.s("pkg"), j.d("impressions"), j.d("clicks"),
            j.dn("ctr"), j.d("conversions"), j.dn("cpi"), j.d("cost"), j.s("status"),
        )
    }
}

data class NetRow(val net: String, val med: String, val imps: Double, val rev: Double, val ecpm: Double, val lat: Double?) {
    companion object { fun of(j: JSONObject) = NetRow(j.s("net", "?"), j.s("med"), j.d("imps"), j.d("rev"), j.d("ecpm"), j.dn("lat")) }
}

data class FmtRow(
    val fmt: String, val med: String, val imps: Double, val rev: Double, val ecpm: Double, val adRequest: Double,
    val fill: Double?, val adClicked: Double, val ctr: Double?,
) {
    companion object {
        fun of(j: JSONObject) = FmtRow(j.s("fmt"), j.s("med"), j.d("imps"), j.d("rev"), j.d("ecpm"), j.d("ad_request"), j.dn("fill"), j.d("ad_clicked"), j.dn("ctr"))
    }
}

data class SourceRow(val src: String, val installs: Double, val users: Double, val revUsd: Double) {
    companion object { fun of(j: JSONObject) = SourceRow(j.s("src"), j.d("installs"), j.d("users"), j.d("rev_usd")) }
}
data class UtmRow(val c: String, val n: Double) { companion object { fun of(j: JSONObject) = UtmRow(j.s("c"), j.d("n")) } }

data class CountryRow(val cc: String, val installs: Double, val users: Double, val imps: Double, val rev: Double) {
    fun ecpm() = if (imps > 0) rev / imps * 1000 else 0.0
    companion object { fun of(j: JSONObject) = CountryRow(j.s("cc", "??"), j.d("installs"), j.d("users"), j.d("imps"), j.d("rev")) }
}

data class Asset(
    val name: String, val url: String, val type: String, val label: String, val campaign: String, val impressions: Double,
    val clicks: Double, val conversions: Double, val cpi: Double?, val cost: Double,
) {
    companion object {
        fun of(j: JSONObject) = Asset(
            j.s("name"), j.s("url"), j.s("field_type", j.s("asset_type")).replace('_', ' ').lowercase(), j.s("label"), j.s("campaign"),
            j.d("impressions"), j.d("clicks"), j.d("conversions"), j.dn("cpi"), j.d("cost"),
        )
    }
}

data class UnitRow(val unit: String, val fmt: String, val med: String, val imps: Double, val rev: Double) {
    fun ecpm() = if (imps > 0) rev / imps * 1000 else 0.0
    companion object { fun of(j: JSONObject) = UnitRow(j.s("unit"), j.s("fmt"), j.s("med"), j.d("imps"), j.d("rev")) }
}
data class CreativeRow(val cr: String, val net: String, val dsp: String, val fmt: String, val imps: Double, val rev: Double) {
    companion object { fun of(j: JSONObject) = CreativeRow(j.s("cr"), j.s("net"), j.s("dsp"), j.s("fmt"), j.d("imps"), j.d("rev")) }
}
data class FeedRow(val ts: String, val pkg: String, val net: String, val fmt: String, val cc: String, val revUsd: Double) {
    companion object { fun of(j: JSONObject) = FeedRow(j.s("ts"), j.s("pkg"), j.s("net"), j.s("fmt"), j.s("cc"), j.d("rev_usd")) }
}
data class InstallRow(val firstSeen: String, val pkg: String, val cc: String, val src: String, val model: String) {
    companion object { fun of(j: JSONObject) = InstallRow(j.s("first_seen"), j.s("pkg"), j.s("cc"), j.s("src"), j.s("model")) }
}
data class FailRow(val fmt: String, val med: String, val reason: String, val n: Double) {
    companion object { fun of(j: JSONObject) = FailRow(j.s("fmt"), j.s("med"), j.s("reason"), j.d("n")) }
}
data class CrashRow(val pkg: String, val ver: String, val error: String, val where: String, val n: Double) {
    companion object { fun of(j: JSONObject) = CrashRow(j.s("pkg"), j.s("ver"), j.s("p1"), j.s("p2"), j.d("n")) }
}
data class PurchaseRow(val pkg: String, val ev: String, val plan: String, val n: Double, val amt: Double?) {
    val status get() = when (ev) { "purchase_start" -> "started"; "purchase_done" -> "paid"; "purchase_failed" -> "failed"; else -> ev }
    /** Localized status (Started / Paid / Failed). */
    val statusL get() = when (ev) { "purchase_start" -> "Started".t(); "purchase_done" -> "Paid".t(); "purchase_failed" -> "Failed".t(); else -> ev }
    companion object { fun of(j: JSONObject) = PurchaseRow(j.s("pkg"), j.s("ev"), j.s("plan"), j.d("n"), j.dn("amt")) }
}
data class VersionRow(val pkg: String, val ver: String, val users: Double) {
    companion object { fun of(j: JSONObject) = VersionRow(j.s("pkg"), j.s("ver"), j.d("users")) }
}
data class PromoVideoRow(val pkg: String, val ad: String, val title: String, val shows: Double, val clicks: Double, val splash: Double, val tap: Double) {
    companion object { fun of(j: JSONObject) = PromoVideoRow(j.s("pkg"), j.s("ad"), j.s("target_title", j.s("ad")), j.d("shows"), j.d("clicks"), j.d("splash"), j.d("tap")) }
}
data class HistoryRow(
    val day: String, val installs: Double, val dau: Double, val imps: Double, val revUsd: Double, val purchaseInr: Double,
    val gadsCostInr: Double, val gadsInstalls: Double, val uninstalls: Double,
) {
    fun profit(r: Double) = revUsd * r + purchaseInr - gadsCostInr
    fun roas(r: Double): Double? = if (gadsCostInr > 0) (revUsd * r + purchaseInr) / gadsCostInr else null
    companion object {
        fun of(j: JSONObject) = HistoryRow(j.s("day"), j.d("installs"), j.d("dau"), j.d("imps"), j.d("rev_usd"), j.d("purchase_inr"), j.d("gads_cost_inr"), j.d("gads_installs"), j.d("uninstalls"))
    }
}
data class MaxCheck(val appImps: Double, val appRev: Double, val apiImps: Double, val apiRev: Double, val apiUpdated: String) {
    companion object { fun of(j: JSONObject) = MaxCheck(j.d("app_imps"), j.d("app_rev"), j.d("api_imps"), j.d("api_rev"), j.s("api_updated")) }
}
data class LiveCountry(val cc: String, val n: Double) { companion object { fun of(j: JSONObject) = LiveCountry(j.s("cc", "??"), j.d("n")) } }

data class DashData(
    val from: String, val to: String, val single: Boolean, val today: Boolean, val inrPerUsd: Double, val aggLagSec: Double,
    val apps: List<AppInfo>, val liveTotal: Double, val liveByApp: Map<String, Double>, val live30: Double?,
    val liveCountries: List<LiveCountry>, val gadsAccounts: List<GadsAccount>, val promo: List<PromoVideoRow>, val kpi: Kpi,
    val perApp: List<PerApp>, val series: List<SeriesPoint>, val seriesKind: String, val campaigns: List<Campaign>,
    val byNetwork: List<NetRow>, val byFormat: List<FmtRow>, val bySource: List<SourceRow>, val utm: List<UtmRow>,
    val byCountry: List<CountryRow>, val assets: List<Asset>, val byUnit: List<UnitRow>, val creatives: List<CreativeRow>,
    val recent: List<FeedRow>, val recentInstalls: List<InstallRow>, val failReasons: List<FailRow>, val crashes: List<CrashRow>,
    val purchases: List<PurchaseRow>, val versions: List<VersionRow>, val maxCheck: MaxCheck, val history: List<HistoryRow>,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    fun appName(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.name ?: pkg.substringAfterLast('.')
    fun appColor(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.color ?: "#4A1FC4"
    val r get() = inrPerUsd

    /** Total fill = loaded / requests, sab formats milaake. */
    val fill: Double? get() {
        val req = byFormat.sumOf { it.adRequest }
        if (req <= 0) return null
        val loaded = byFormat.sumOf { (it.fill ?: 0.0) * it.adRequest }
        return loaded / req
    }

    companion object {
        fun parse(j: JSONObject): DashData {
            val live = j.obj("live")
            val byApp = HashMap<String, Double>()
            live.obj("by_app").let { m -> m.keys().forEach { k -> byApp[k] = m.d(k) } }
            val seriesArr = if (j.has("series")) j.arr("series") else j.arr("hours")
            return DashData(
                from = j.s("from"), to = j.s("to"), single = j.b("single"), today = j.b("today"),
                inrPerUsd = j.d("inr_per_usd", 84.0), aggLagSec = j.d("agg_lag_sec"),
                apps = j.arr("apps").mapObj(AppInfo::of), liveTotal = live.d("total"), liveByApp = byApp,
                live30 = j.optJSONObject("live30")?.d("total"),
                liveCountries = j.arr("live_countries").mapObj(LiveCountry::of),
                gadsAccounts = j.arr("gads_accounts").mapObj(GadsAccount::of), promo = j.arr("promo").mapObj(PromoVideoRow::of),
                kpi = Kpi.of(j.obj("kpi")), perApp = j.arr("per_app").mapObj(PerApp::of),
                series = seriesArr.mapObj(SeriesPoint::of), seriesKind = j.s("series_kind", "hour"),
                campaigns = j.arr("gads_campaigns").mapObj(Campaign::of), byNetwork = j.arr("by_network").mapObj(NetRow::of),
                byFormat = j.arr("by_format").mapObj(FmtRow::of), bySource = j.arr("by_source").mapObj(SourceRow::of),
                utm = j.arr("utm_campaigns").mapObj(UtmRow::of), byCountry = j.arr("by_country").mapObj(CountryRow::of),
                assets = j.arr("gads_assets").mapObj(Asset::of), byUnit = j.arr("by_unit").mapObj(UnitRow::of),
                creatives = j.arr("creatives").mapObj(CreativeRow::of), recent = j.arr("recent").mapObj(FeedRow::of),
                recentInstalls = j.arr("recent_installs").mapObj(InstallRow::of), failReasons = j.arr("fail_reasons").mapObj(FailRow::of),
                crashes = j.arr("crashes").mapObj(CrashRow::of), purchases = j.arr("purchases").mapObj(PurchaseRow::of),
                versions = j.arr("versions").mapObj(VersionRow::of), maxCheck = MaxCheck.of(j.obj("max_check")),
                history = j.arr("history").mapObj(HistoryRow::of),
            )
        }
    }
}

/** `api/crosspromo.php` */
data class CpRow(val src: String, val tgt: String, val fmt: String, val views: Double, val clicks: Double, val rewards: Double, val installs: Double) {
    val ctr: Double? get() = if (views > 0) clicks / views else null
    val installPerClick: Double? get() = if (clicks > 0) installs / clicks else null
    companion object { fun of(j: JSONObject) = CpRow(j.s("src"), j.s("tgt"), j.s("fmt"), j.d("views"), j.d("clicks"), j.d("rewards"), j.d("installs")) }
}
data class CpDesign(val src: String, val design: String, val fmt: String, val views: Double, val clicks: Double) {
    val ctr: Double? get() = if (views > 0) clicks / views else null
    companion object { fun of(j: JSONObject) = CpDesign(j.s("src"), j.s("design"), j.s("fmt"), j.d("views"), j.d("clicks")) }
}
data class CpData(val from: String, val to: String, val rows: List<CpRow>, val designs: List<CpDesign>) {
    companion object {
        fun parse(j: JSONObject) = CpData(j.s("from"), j.s("to"), j.arr("rows").mapObj(CpRow::of), j.arr("designs").mapObj(CpDesign::of))
    }
}

fun cpFmtName(f: String) = when (f.uppercase()) {
    "APP_OPEN" -> "App open".t(); "INTER_IMAGE" -> "Inter image".t(); "INTER_VIDEO" -> "Inter video".t(); "REWARDED" -> "Rewarded".t(); else -> f.ifBlank { "–" }
}

/** App ka status label (research: har app ko ek nazar mein Profit / Theek / Loss). */
enum class Health(private val en: String) { PROFIT("Profit"), OK("OK"), LOSS("Loss"), NONE("No spend");
    val label get() = en.t() }
fun healthOf(roas: Double?, profit: Double): Health = when {
    roas == null -> if (profit > 0) Health.PROFIT else Health.NONE
    roas >= 1.2 -> Health.PROFIT
    roas >= 0.8 -> Health.OK
    else -> Health.LOSS
}

/** `api/app.php` (naya endpoint): active, retention, campaign ROAS cohort, campaign daily, notes. */
data class Active(
    val live6m: Double, val live30m: Double, val live1h: Double, val live24h: Double, val dau: Double, val wau: Double, val mau: Double,
    val dauMau: Double?, val installedTotal: Double, val uninstalledTotal: Double,
) {
    companion object {
        fun of(j: JSONObject) = Active(j.d("live_6m"), j.d("live_30m"), j.d("live_1h"), j.d("live_24h"), j.d("dau"), j.d("wau"), j.d("mau"), j.dn("dau_mau"), j.d("installed_total"), j.d("uninstalled_total"))
    }
}
data class Retention(val cohort: String, val installs: Double, val d1: Double?, val d3: Double?, val d7: Double?, val d14: Double?) {
    companion object { fun of(j: JSONObject) = Retention(j.s("cohort"), j.d("installs"), j.dn("d1"), j.dn("d3"), j.dn("d7"), j.dn("d14")) }
}
data class RetentionDay(val day: String, val installs: Double, val paid: Double, val d1: Double?, val d3: Double?, val d7: Double?, val d14: Double?) {
    companion object { fun of(j: JSONObject) = RetentionDay(j.s("day"), j.d("installs"), j.d("paid"), j.dn("d1"), j.dn("d3"), j.dn("d7"), j.dn("d14")) }
}
data class CampaignRoas(
    val campaign: String, val pkg: String, val installs: Double, val spend: Double, val gadsConv: Double, val gadsClicks: Double,
    val revD0: Double, val revD1: Double, val revD3: Double, val revD7: Double, val revTotal: Double, val payTotal: Double,
    val cpi: Double?, val costPerConv: Double?, val roasD0: Double?, val roasD1: Double?, val roasD3: Double?, val roasD7: Double?, val roasTotal: Double?,
    val profit: Double, val revPerInstall: Double?, val retD1: Double?, val retD7: Double?, val noUtm: Boolean,
) {
    companion object {
        fun of(j: JSONObject) = CampaignRoas(
            j.s("campaign"), j.s("pkg"), j.d("installs"), j.d("spend"), j.d("gads_conv"), j.d("gads_clicks"),
            j.d("rev_d0"), j.d("rev_d1"), j.d("rev_d3"), j.d("rev_d7"), j.d("rev_total"), j.d("pay_total"),
            j.dn("cpi"), j.dn("cost_per_conv"), j.dn("roas_d0"), j.dn("roas_d1"), j.dn("roas_d3"), j.dn("roas_d7"), j.dn("roas_total"),
            j.d("profit"), j.dn("rev_per_install"), j.dn("ret_d1"), j.dn("ret_d7"), j.b("no_utm"),
        )
    }
}
data class CampaignDay(val day: String, val campaign: String, val pkg: String, val cost: Double, val impressions: Double, val clicks: Double, val conversions: Double) {
    companion object { fun of(j: JSONObject) = CampaignDay(j.s("day"), j.s("campaign"), j.s("pkg"), j.d("cost"), j.d("impressions"), j.d("clicks"), j.d("conversions")) }
}
data class Note(val id: Long, val campaign: String, val pkg: String, val note: String, val createdAt: String) {
    companion object { fun of(j: JSONObject) = Note(j.l("id"), j.s("campaign"), j.s("pkg"), j.s("note"), j.s("created_at")) }
}
data class ExtraData(
    val from: String, val to: String, val liveMinutes: Int, val active: Active, val retention: List<Retention>, val retentionDays: List<RetentionDay>,
    val campaignRoas: List<CampaignRoas>, val organicInstalls: Double, val campaignDaily: List<CampaignDay>, val notes: List<Note>,
) {
    fun ret(cohort: String) = retention.firstOrNull { it.cohort == cohort }
    companion object {
        fun parse(j: JSONObject) = ExtraData(
            j.s("from"), j.s("to"), j.d("live_minutes", 6.0).toInt(), Active.of(j.obj("active")), j.arr("retention").mapObj(Retention::of),
            j.arr("retention_days").mapObj(RetentionDay::of), j.arr("campaign_roas").mapObj(CampaignRoas::of), j.obj("organic").d("installs"),
            j.arr("campaign_daily").mapObj(CampaignDay::of), j.arr("notes").mapObj(Note::of),
        )
    }
}

/** UA research: ROAS target curve (D0 30%, D1 50%, D3 75%, D7 110%) jo D30 par ~150% deta hai. */
val ROAS_TARGET_CURVE = linkedMapOf("D0" to 0.30, "D1" to 0.50, "D3" to 0.75, "D7" to 1.10)

// ---------------------------------------------------------------- api/alerts.php (smart alerts)
data class AlertItem(
    val id: Long, val level: Int, val rule: String, val pkg: String, val appName: String, val account: String, val campaign: String,
    val title: String, val body: String, val ai: String, val fired: Int, val createdAt: String, val updatedAt: String,
    val resolvedAt: String?, val readAt: String?, val titleHi: String = "", val bodyHi: String = "",
    val issueId: Long? = null,   // AI App Health alerts: metrics.issue_id
    val gaAlertId: Long? = null, // Google Ads monitor alerts ("gah_*"): metrics.ga_alert_id
) {
    val isHealth get() = rule.startsWith("health")
    val isGads get() = rule.startsWith("gah_")
    /** Notification / card tap: health alert -> us issue ki screen; Google Ads monitor alert -> us alert ki screen. */
    val route get() = when {
        isHealth -> issueId?.let { "health/issue/$it" } ?: "health"
        isGads -> gaAlertId?.let { "gads/alert/$it" } ?: "gads"
        else -> "alerts"
    }
    val open get() = resolvedAt == null
    val unread get() = readAt == null
    val levelText get() = when (level) { 3 -> "URGENT".t(); 2 -> "Watch".t(); else -> "Info".t() }
    /** Title/body in the chosen language (server sends English + Hinglish; old alerts.php sends one text only). */
    val titleL get() = if (Lang.hi && titleHi.isNotBlank()) titleHi else title
    val bodyL get() = if (Lang.hi && bodyHi.isNotBlank()) bodyHi else body
    companion object {
        fun of(j: JSONObject) = AlertItem(
            j.l("id"), j.d("level").toInt(), j.s("rule"), j.s("pkg"), j.s("app_name"), j.s("account"), j.s("campaign"),
            j.s("title"), j.s("body"), j.s("ai"), j.d("fired", 1.0).toInt(), j.s("created_at"), j.s("updated_at"),
            j.sn("resolved_at"), j.sn("read_at"), j.s("title_hi"), j.s("body_hi"),
            j.optJSONObject("metrics")?.dn("issue_id")?.toLong(),
            j.optJSONObject("metrics")?.dn("ga_alert_id")?.toLong(),
        )
    }
}

data class AiStatus(val enabled: Boolean, val keyHint: String, val model: String, val lastError: String?, val lastOk: Long) {
    companion object { fun of(j: JSONObject) = AiStatus(j.b("enabled"), j.s("key_hint"), j.s("model"), j.sn("last_error"), j.l("last_ok")) }
}

data class Brief(val day: String, val text: String, val at: Long)

data class AlertsData(
    val alerts: List<AlertItem>, val openCount: Int, val urgentOpen: Int, val warnOpen: Int, val unread: Int,
    val computedAt: Long, val aiPending: Int, val ai: AiStatus, val brief: Brief?,
) {
    val open get() = alerts.filter { it.open }
    companion object {
        fun parse(j: JSONObject) = AlertsData(
            j.arr("alerts").mapObj(AlertItem::of), j.d("open_count").toInt(), j.d("urgent_open").toInt(), j.d("warn_open").toInt(), j.d("unread").toInt(),
            j.l("computed_at"), j.d("ai_pending").toInt(), AiStatus.of(j.obj("ai")),
            j.optJSONObject("brief")?.let { Brief(it.s("day"), it.s("text"), it.l("at")) },
        )
    }
}
