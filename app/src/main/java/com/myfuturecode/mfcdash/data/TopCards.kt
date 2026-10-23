package com.myfuturecode.mfcdash.data

import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.screens.alertKind
import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.dn

// "Top 3" cards ka hisaab (home-screen widgets; aage Home bhi yahi use kare). Sirf data, koi UI nahi:
// sab functions pehle se parse kiye API models lete hain, isliye widget aur app dono ek jaisa dikhayenge.

/** Rang ka zone: ROAS (red < 80%, amber 80-100%, green >= 100%) ya budget (green 80% tak, amber 80-90%, red 90% se upar). */
enum class TopZone { RED, AMBER, GREEN, NONE }

/** ROAS ratio (1.0 = 100%) ka zone; kharcha nahi (null) = NONE. Paisa meter aur Live users ki line dono me yahi. */
fun roasZone(roas: Double?): TopZone = when {
    roas == null || !roas.isFinite() -> TopZone.NONE
    roas < 0.8 -> TopZone.RED
    roas < 1.0 -> TopZone.AMBER
    else -> TopZone.GREEN
}

/** Budget ka kitna % kharch hua; budget pata nahi (null) = NONE. 100% par server laal alert bhi banata hai. */
fun budgetZone(pct: Double?): TopZone = when {
    pct == null || !pct.isFinite() -> TopZone.NONE
    pct > 90.0 -> TopZone.RED
    pct > 80.0 -> TopZone.AMBER
    else -> TopZone.GREEN
}

// ---------------------------------------------------------------- Google Ads: aaj ke top accounts

/** Ek account ka aaj: kharcha apni currency me, ₹ me (sirf sort ke liye), aaj ka budget aur uska %. */
data class TopAccount(
    val cid: String, val name: String, val currency: String, val spend: Double, val spendInr: Double?,
    val budget: Double?, val budgetPct: Double?,
)

/** Overview ke accounts me se sirf monitor wale (purana server "monitoring" na bheje to sab). */
fun monitoredAccounts(accounts: List<GAccount>): List<GAccount> = accounts.filter { !it.raw.has("monitoring") || it.monitoring }

/** Account ka naam: company, warna account ka naam, warna id. */
fun topAccountName(a: GAccount): String = a.company.ifBlank { a.accountName }.ifBlank { a.cidFmt }.ifBlank { a.cid }

/** ₹ me: INR wahi, USD dashboard ke rate se; baaki currency (ya rate nahi) = null. */
fun topInr(v: Double, currency: String, rate: Double?): Double? = when (currency.trim().uppercase()) {
    "INR" -> v
    "USD" -> rate?.takeIf { it > 0 && it.isFinite() }?.let { v * it }
    else -> null
}

/**
 * Aaj sabse zyada kharch wale n accounts (₹ me badal ke; jo badal na sake uska apna number). pick = sirf ye cid (khaali = sab).
 * budget_today / budget_pct server deta hai (chalu campaigns ke daily budget); na ho to null.
 */
fun topAccounts(accounts: List<GAccount>, rate: Double?, pick: Set<String> = emptySet(), n: Int = 3): List<TopAccount> =
    monitoredAccounts(accounts).filter { pick.isEmpty() || it.cid in pick }.map { a ->
        val cur = a.currency.trim().uppercase()
        val sp = a.raw.dn("spend_today")?.takeIf { it.isFinite() } ?: 0.0
        TopAccount(
            a.cid, topAccountName(a), cur, sp, topInr(sp, cur, rate),
            a.raw.dn("budget_today")?.takeIf { it > 0 && it.isFinite() }, a.raw.dn("budget_pct")?.takeIf { it.isFinite() && it >= 0 },
        )
    }.sortedByDescending { it.spendInr ?: it.spend }.take(n)

// ---------------------------------------------------------------- Google Ads: aaj ke top kharch wale apps

/** Ek app ka aaj ka Google Ads kharcha (₹) aur ROAS (ad revenue + payments / kharcha; pata nahi = null). */
data class TopSpendApp(val pkg: String, val name: String, val spendInr: Double, val roas: Double?)

/** App ka naam: data.php ki apps list, warna per_app ka, warna package ka aakhri hissa. */
private fun topAppName(d: DashData, pkg: String, per: PerApp?): String =
    d.apps.firstOrNull { it.pkg == pkg }?.name?.ifBlank { null } ?: per?.name?.ifBlank { null } ?: pkg.substringAfterLast('.')

/** Aaj sabse zyada Google Ads kharch wale n apps (data.php per_app; kharcha 0 wale nahi). Kul kharcha = kpi.gadsCostInr. */
fun topSpendApps(d: DashData, n: Int = 3): List<TopSpendApp> =
    d.perApp.filter { it.pkg.isNotBlank() && it.gadsCostInr.isFinite() && it.gadsCostInr > 0 }
        .sortedByDescending { it.gadsCostInr }.take(n)
        .map { a -> TopSpendApp(a.pkg, topAppName(d, a.pkg, a), a.gadsCostInr, a.roas(d.r)?.takeIf { it.isFinite() }) }

// ---------------------------------------------------------------- Live users: top apps

/** Ek app: pichhle 6 min ke live users, 30 min ke active (app.php se, na mile to null), aaj ka ROAS (kharcha nahi = null). */
data class TopLiveApp(val pkg: String, val name: String, val live6: Double, val live30: Double?, val roas: Double?)

/** 6 min ke live users se top n apps (barabar ho to aaj ke DAU se). live30 baad me [withLive30] se. */
fun topLiveApps(d: DashData, n: Int = 3): List<TopLiveApp> {
    val per = d.perApp.associateBy { it.pkg }
    val pkgs = (d.apps.map { it.pkg } + d.liveByApp.keys + d.perApp.map { it.pkg }).filter { it.isNotBlank() }.distinct()
    return pkgs.map { p ->
        TopLiveApp(p, topAppName(d, p, per[p]), d.liveByApp[p] ?: per[p]?.live ?: 0.0, null, per[p]?.roas(d.r)?.takeIf { it.isFinite() })
    }.sortedWith(compareByDescending<TopLiveApp> { it.live6 }.thenByDescending { per[it.pkg]?.dau ?: 0.0 }).take(n)
}

/** 30 min ke active users jodo (pkg -> ginti; na mila = null). */
fun withLive30(apps: List<TopLiveApp>, live30: Map<String, Double?>): List<TopLiveApp> = apps.map { it.copy(live30 = live30[it.pkg]) }

// ---------------------------------------------------------------- App Health: 3 number + issue wale apps

data class IssueApp(val pkg: String, val name: String, val open: Int, val p0: Boolean)

/** Kul apps, issue wale, theek wale + sabse zyada khule issue wale apps. */
data class HealthTop(val total: Int, val withIssues: Int, val fine: Int, val top: List<IssueApp>)

/**
 * Overview ke number (consoles chune hon to consoles view ke apps se), list consoles view ke openIssues se
 * (barabar ho to P0 pehle). Consoles view na mile to overview ke attention issues se.
 */
fun healthTop(ov: HOverview, cons: HConsolesData?, consoles: Set<String> = emptySet(), n: Int = 3): HealthTop {
    val apps = cons?.apps?.filter { consoles.isEmpty() || it.consoleId in consoles }
    val total: Int
    val bad: Int
    val fine: Int
    if (consoles.isEmpty() || apps == null) {
        total = ov.apps
        bad = ov.withProblems
        fine = ov.healthy
    } else {
        total = apps.size
        bad = apps.count { it.openIssues > 0 }
        fine = total - bad
    }
    val list = if (apps != null) {
        apps.filter { it.openIssues > 0 }.map { IssueApp(it.pkg, it.name.ifBlank { it.pkg.substringAfterLast('.') }, it.openIssues, it.topSeverity == "P0") }
    } else {
        ov.attention.filter { consoles.isEmpty() || it.consoleId in consoles }.groupBy { it.pkg.ifBlank { it.app } }.map { (_, l) ->
            IssueApp(l[0].pkg, l[0].app.ifBlank { l[0].pkg.substringAfterLast('.') }, l.size, l.any { it.severity == "P0" })
        }
    }
    val top = list.sortedWith(compareByDescending<IssueApp> { it.open }.thenByDescending { it.p0 }.thenBy { it.name.lowercase() }).take(n)
    return HealthTop(total.coerceAtLeast(0), bad.coerceAtLeast(0), fine.coerceAtLeast(0), top)
}

/**
 * App ke sabse bure khule issue ki kism (English key: "Crash" / "ANR" / "Ads" / "Policy" ...), Home ki issue line ke daayein.
 * Overview ke attention se (server severity, users se sort karke 8 bhejta hai); app wahan na ho to null.
 */
fun issueReason(ov: HOverview, pkg: String): String? {
    val t = ov.attention.firstOrNull { it.pkg == pkg }?.type?.trim()?.uppercase() ?: return null
    return when (t) {
        "CRASH", "NON_FATAL" -> "Crash"
        "ANR" -> "ANR"
        "AD" -> "Ads"
        "POLICY" -> "Policy"
        "VITALS" -> "Vitals"
        "TASK" -> "Task"
        "" -> null
        else -> t.lowercase().replaceFirstChar { it.uppercase() }
    }
}

// ---------------------------------------------------------------- Alerts: naye 3 + chhoti kism

/**
 * Ek alert ka chhota roop. label = English key (.t() se Hinglish), arg = uska "%s" (naye version ka number).
 * Naam: app, warna campaign, warna alert ka title (bhasha ke hisaab se, [nameL]).
 */
data class AlertCard(
    val id: Long, val kind: KKind, val app: String, val campaign: String, val title: String, val titleHi: String,
    val label: String, val arg: String?,
) {
    fun nameL(): String = app.ifBlank { campaign }.ifBlank { if (Lang.hi && titleHi.isNotBlank()) titleHi else title }
}

data class AlertsTop(val unread: Int, val open: Int, val items: List<AlertCard>)

/** Alert ki kism (Alerts tab jaisi): faisla chahiye = ASK, level 3 = BAD, 2 = WARN, naya version = OK, baaki NEUTRAL. */
fun alertSev(a: AlertItem): KKind = when {
    a.rule == "studio_gate" || a.rule == "health_aab" || (a.rule == "studio_research" && a.level >= 2) -> KKind.ASK
    a.rule == "new_version" && a.level <= 1 -> KKind.OK
    a.level >= 3 -> KKind.BAD
    a.level == 2 -> KKind.WARN
    else -> KKind.NEUTRAL
}

private fun sevRank(k: KKind): Int = when (k) {
    KKind.BAD -> 0
    KKind.ASK -> 1
    KKind.WARN -> 2
    else -> 3
}

/** Sabse naye n khule alerts; un n me sabse zaroori upar (barabar ho to naya pehle). */
fun alertsTop(d: AlertsData, n: Int = 3): AlertsTop {
    val newest = d.alerts.filter { it.open }.sortedByDescending { it.id }.take(n)
    val items = newest.sortedWith(compareBy<AlertItem> { sevRank(alertSev(it)) }.thenByDescending { it.id }).map { a ->
        val (label, arg) = alertLabel(a.rule, a.title, a.titleHi)
        AlertCard(a.id, alertSev(a), a.appName.trim(), a.campaign.trim(), a.title.trim(), a.titleHi.trim(), label, arg)
    }
    return AlertsTop(d.unread.coerceAtLeast(0), d.openCount.coerceAtLeast(0), items)
}

/** 1-3 shabd ki kism (English key) + arg. Alerts tab ki kism (alertKind) ke andar rule se thoda khaas naam. */
fun alertLabel(rule: String, title: String, titleHi: String): Pair<String, String?> = when (alertKind(rule)) {
    // title "<App> v2.4 is live on Play": aakhri "vX" (app ke naam me "v2" ho to bhi sahi version)
    "release" -> Regex("\\bv(\\d[\\w.\\-]*)").findAll(title).lastOrNull()?.groupValues?.getOrNull(1)?.trimEnd('.', '-')
        ?.let { "v%s live" to it } ?: ("New version live" to null)
    "play" -> playLabel(title, titleHi) to null
    "crash" -> (if (rule.startsWith("ver_crash")) "Version crash" else "Crash up") to null
    "health" -> (if (rule == "health_aab") "AI fix ready" else "App health") to null
    "gah" -> "Ads monitor" to null
    "studio" -> "Studio" to null
    "cost" -> "AI cost" to null
    "ads" -> when {
        rule.startsWith("adunit_nofill") -> "No fill"
        rule.startsWith("adunit_ecpm") || rule.startsWith("ecpm") -> "eCPM down"
        rule.startsWith("adunit_ctr") -> "CTR down"
        rule.startsWith("adunit") -> "Ad unit down"
        rule.startsWith("fill") -> "Ad fails up"
        rule.startsWith("imps") -> "Impressions down"
        rule.startsWith("network") -> "Network down"
        else -> "Ads"
    } to null
    "gads" -> when {
        rule.startsWith("roas") -> "ROAS low"
        rule.startsWith("camp_roas") -> "Campaign ROAS low"
        rule.startsWith("cpi") -> "CPI up"
        rule.startsWith("spend") -> "Spend fast"
        rule.startsWith("camp_new") -> "New campaign"
        rule.startsWith("camp_paused") -> "Campaign paused"
        rule.startsWith("camp_zero") -> "Campaign stopped"
        rule.startsWith("camp_noinst") -> "0 installs"
        rule.startsWith("camp_imps") -> "Impressions down"
        rule.startsWith("gads_stale") -> "Ads data late"
        else -> "Google Ads"
    } to null
    "money" -> when {
        rule.startsWith("rev_up") -> "Revenue up"
        rule.startsWith("rev") -> "Revenue down"
        rule.startsWith("pay") -> "Payments failing"
        rule.startsWith("record") -> "New record"
        rule.startsWith("brief") -> "Daily summary"
        else -> "Money"
    } to null
    else -> when {
        rule.startsWith("data_stop") || rule.startsWith("collector") -> "Data stopped"
        rule.startsWith("data_low") -> "Data low"
        rule.startsWith("imps_stop") -> "Ads stopped"
        rule.startsWith("agg_lag") -> "Cron late"
        rule.startsWith("inst_up") -> "Installs up"
        rule.startsWith("inst") -> "Installs down"
        rule.startsWith("uninst") -> "Uninstalls high"
        rule.startsWith("ret") -> "Retention down"
        rule.startsWith("net") -> "Network down"
        else -> "Data"
    } to null
}

/**
 * Play Console mail ki kism. Server (lib/playmail.php) Hinglish title me "Play: reject · App" jaisa prefix rakhta hai
 * (metrics.match se); woh na ho (purana alert) to English subject ke shabd.
 */
private fun playLabel(title: String, titleHi: String): String {
    val hi = titleHi.substringBefore(" · ").trim().lowercase()
    val t = if (hi.startsWith("play:")) hi else title.lowercase()
    return when {
        "reject" in t || "not approved" in t -> "Play reject"
        "hataya" in t || "removed" in t || "taken down" in t || "unpublish" in t -> "Play removed"
        "suspend" in t || "account band" in t || "terminat" in t -> "Play suspend"
        "polic" in t || "violat" in t || "complian" in t -> "Play policy"
        "warning" in t || "zaroori" in t || "required" in t || "declaration" in t || "deadline" in t || "limited" in t || "issue" in t -> "Play warning"
        "approv" in t || "review done" in t || "review complete" in t -> "Play approved"
        "live" in t || "publish" in t || "available" in t -> "Play live"
        else -> "Play mail"
    }
}
