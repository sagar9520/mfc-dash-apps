package com.myfuturecode.mfcdash.ui.screens.gads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.GAccount
import com.myfuturecode.mfcdash.data.GAlert
import com.myfuturecode.mfcdash.data.GEvent
import com.myfuturecode.mfcdash.data.GRun
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.GadsVm
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KDivider
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLoadOnChange
import com.myfuturecode.mfcdash.ui.kit.KLive
import com.myfuturecode.mfcdash.ui.kit.KPaceMeter
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSectionLink
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSkelHero
import com.myfuturecode.mfcdash.ui.kit.KSmallAction
import com.myfuturecode.mfcdash.ui.kit.KSortButton
import com.myfuturecode.mfcdash.ui.kit.KSortKind
import com.myfuturecode.mfcdash.ui.kit.KSortState
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kRoasColor
import com.myfuturecode.mfcdash.ui.kit.kSignedPct
import com.myfuturecode.mfcdash.ui.kit.kSortLead
import com.myfuturecode.mfcdash.ui.kit.kSorted
import com.myfuturecode.mfcdash.ui.kit.rememberKSort
import com.myfuturecode.mfcdash.ui.screens.FilterBox
import com.myfuturecode.mfcdash.ui.screens.FilterChips
import com.myfuturecode.mfcdash.ui.screens.FilterSheet
import com.myfuturecode.mfcdash.ui.screens.FilterSummary
import com.myfuturecode.mfcdash.ui.screens.LocalFilterBox
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.l
import com.myfuturecode.mfcdash.util.mapObj
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.obj
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.strings
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tOnly
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs
import org.json.JSONObject
import java.util.Locale

val GTABS = listOf(
    "accounts" to "All Accounts", "health" to "Account Health", "policy" to "Suspensions & Policy", "spend" to "Spend", "cpi" to "CPI / CPA", "roas" to "ROAS",
    "campaigns" to "Campaigns & Ad Groups", "creatives" to "AI Creative Review", "recs" to "AI Recommendations", "live" to "Live Monitoring",
    "history" to "Alert History", "settings" to "Notification Settings",
)

private val G_RANGES = listOf("7d" to "7 days", "today" to "Today", "yesterday" to "Yesterday", "14d" to "14 days", "30d" to "30 days")

private fun statusOptions(tab: String): List<Pair<String, String>> = listOf("" to "All") + when (tab) {
    "accounts", "health" -> listOf("critical" to "Critical", "warning" to "Warning", "problem" to "Any problem", "healthy" to "Healthy", "stale" to "Stale / no data")
    "policy", "history" -> listOf("open" to "Open", "resolved" to "Resolved", "critical" to "Critical", "warning" to "Warning", "info" to "Info",
        "confirmed" to "Confirmed", "suspected" to "Suspected", "insufficient" to "Insufficient data", "simulated" to "Simulated")
    "recs" -> listOf("critical" to "Critical", "warning" to "Warning", "info" to "Info")
    "spend", "cpi", "roas" -> listOf("enabled" to "Enabled", "paused" to "Paused / removed", "problem" to "With alert", "healthy" to "No alert")
    "campaigns" -> listOf("enabled" to "Enabled", "paused" to "Paused / removed")
    "creatives" -> listOf("low" to "Low", "learning" to "Learning", "good" to "Good", "best" to "Best")
    "live" -> listOf("success" to "Success", "partial" to "Partial", "failed" to "Failed", "running" to "Running")
    else -> emptyList()
}

// tabs: Accounts | Alerts | AI | More (baaki 9 menu me)
private val G_MAIN = listOf("accounts", "history", "recs")
private val G_MORE = listOf("health", "policy", "spend", "cpi", "roas", "campaigns", "creatives", "live", "settings")

// Sort (bar ka Sort button, Filter ke baad): har list tab ka apna. Default = server ka aaj ka order (bina chhede wahi list).
// accounts / health: company A -> Z; alerts / AI: khule + level; spend / CPI / ROAS: alert wale pehle, phir 7 din kharcha;
// campaigns: kharcha; live: naye runs pehle. Creatives (campaign id order) aur Settings par Sort nahi.
private val G_SORT_DEF = mapOf(
    "accounts" to ("name" to false), "health" to ("name" to false), "history" to ("level" to true), "policy" to ("level" to true),
    "recs" to ("level" to true), "spend" to ("alerts" to true), "cpi" to ("alerts" to true), "roas" to ("alerts" to true),
    "campaigns" to ("spend" to true), "live" to ("time" to true),
)
private val G_SORT_KINDS = mapOf("time" to KSortKind.DATE, "account" to KSortKind.TEXT, "cpi" to KSortKind.NUM_LOW)

private fun gSortOpts(tab: String): List<Pair<String, String>> = when (tab) {
    "accounts", "health" -> listOf("name" to "Name".t(), "spend" to "Spend today".t(), "alerts" to "Alerts".t(), "pace" to "Pace".t())
    "history", "policy", "recs" -> listOf("level" to "Level".t(), "time" to "Time".t(), "account" to "Account".t())
    "spend" -> listOf("alerts" to "Alerts".t(), "spend" to "Spend".t(), "pace" to "Pace".t(), "name" to "Name".t())
    "cpi" -> listOf("alerts" to "Alerts".t(), "spend" to "Spend".t(), "cpi" to "CPI", "installs" to "Installs".t(), "name" to "Name".t())
    "roas" -> listOf("alerts" to "Alerts".t(), "spend" to "Spend".t(), "roas" to "ROAS", "name" to "Name".t())
    "campaigns" -> listOf("spend" to "Spend".t(), "installs" to "Installs".t(), "cpi" to "CPI", "ctr" to "CTR", "roas" to "ROAS", "name" to "Name".t())
    "live" -> listOf("time" to "Time".t(), "issues" to "Issues".t())
    else -> emptyList()
}

private fun gAcctName(j: JSONObject): String = j.s("company").ifBlank { j.s("account_name").ifBlank { j.s("cid_fmt") } }

/** Accounts (overview ki list): default par server ka order (company A -> Z). Kharcha ₹ me milakar (USD x rate). */
private fun gSortAccs(accs: List<JSONObject>, s: KSortState?, usdInr: Double?): List<JSONObject> = when {
    s == null || s.isDefault -> accs
    s.key == "spend" -> accs.kSorted(s.desc) { j -> j.dn("spend_today")?.let { gToInr(it, j.s("currency"), usdInr) ?: it } }
    s.key == "alerts" -> accs.kSorted(s.desc) { it.d("critical") * 1000 + it.d("warning") }
    s.key == "pace" -> accs.kSorted(s.desc) { j -> j.dn("pace_pct")?.takeIf { j.b("monitoring") && j.b("fresh") } }
    else -> accs.kSorted(s.desc) { gAcctName(it).lowercase() }
}

private fun gLevelRank(l: String): Int = when (l.uppercase()) {
    "CRITICAL" -> 3
    "WARNING" -> 2
    "INFO" -> 1
    else -> 0
}

/** Alerts / AI list: default par server ka order (khule pehle, level, aakhri baar). Time = row par dikhne wala (pehli baar). */
private fun gSortAlerts(list: List<GAlert>, s: KSortState?): List<GAlert> = when {
    s == null || s.isDefault -> list
    s.key == "time" -> list.kSorted(s.desc) { tsMs(it.firstSeen) }
    s.key == "account" -> list.kSorted(s.desc) { it.company.ifBlank { it.cidFmt }.lowercase() }
    else -> list.kSorted(s.desc) { (if (it.open) 10 else 0) + gLevelRank(it.level) }
}

/** Spend / CPI / ROAS campaigns: default par server ka order (alert wale pehle, phir 7 din kharcha). Number = 7 din. */
private fun gSortMonitor(rows: List<JSONObject>, s: KSortState?, usdInr: Double?): List<JSONObject> {
    if (s == null || s.isDefault) return rows
    fun d7(r: JSONObject) = r.obj("windows").obj("d7")
    return when (s.key) {
        "spend" -> rows.kSorted(s.desc) { r -> d7(r).dn("cost")?.let { gToInr(it, r.s("currency"), usdInr) ?: it } }
        "pace" -> rows.kSorted(s.desc) { it.dn("pace_pct") }
        "cpi" -> rows.kSorted(s.desc) { r -> d7(r).dn("cpi")?.let { gToInr(it, r.s("currency"), usdInr) ?: it } }
        "installs" -> rows.kSorted(s.desc) { d7(it).dn("conversions") }
        "roas" -> rows.kSorted(s.desc) { d7(it).dn("roas") }
        "name" -> rows.kSorted(s.desc) { it.s("name").ifBlank { it.s("campaign_id") }.lowercase() }
        else -> rows.kSorted(s.desc) { it.arr("alerts").length() }
    }
}

/** Campaigns table: default par server ka order (kharcha zyada -> kam); account ke group ke andar bhi wahi order. */
private fun gSortCamps(rows: List<JSONObject>, s: KSortState?): List<JSONObject> = when {
    s == null || s.isDefault -> rows
    s.key == "installs" -> rows.kSorted(s.desc) { it.dn("installs") }
    s.key == "cpi" -> rows.kSorted(s.desc) { it.dn("cpi") }
    s.key == "ctr" -> rows.kSorted(s.desc) { it.dn("ctr") }
    s.key == "roas" -> rows.kSorted(s.desc) { it.dn("roas") }
    s.key == "name" -> rows.kSorted(s.desc) { it.s("name").ifBlank { it.s("campaign_id") }.lowercase() }
    else -> rows.kSorted(s.desc) { it.dn("cost") }
}

private fun gShortTab(k: String): String = when (k) {
    "accounts" -> "Accounts".t()
    "history" -> "Alerts".t()
    "recs" -> "AI".t()
    "health" -> "Health".t()
    "policy" -> "Policy".t()
    "spend" -> "Spend".t()
    "cpi" -> "CPI"
    "roas" -> "ROAS"
    "campaigns" -> "Campaigns".t()
    "creatives" -> "Creatives".t()
    "live" -> "Live".t()
    "settings" -> "Settings".t()
    else -> "More".t()
}

private fun gTabIcon(k: String): ImageVector = when (k) {
    "health" -> KIcons.Pulse
    "policy" -> KIcons.Shield
    "spend" -> KIcons.Rupee
    "cpi" -> KIcons.Target
    "roas" -> KIcons.TrendUp
    "campaigns" -> KIcons.Ads
    "creatives" -> KIcons.Palette
    "live" -> KIcons.Live
    "settings" -> KIcons.Gear
    else -> KIcons.More
}

/** Overview ke ek account ka haal (kind, chhota shabd). */
private fun acctState(j: JSONObject, drop: Double, spike: Double): Pair<KKind, String> = gAcctState(
    j.b("monitoring"), j.s("health"), j.dn("data_age_min")?.toInt(), j.b("fresh"), j.d("critical").toInt(), j.d("warning").toInt(), j.dn("pace_pct"), drop, spike,
)

/** Sab accounts ki milkar raftaar: Σ aaj / Σ aam − 1 (alag currency ho to ₹ me). Na bane to null. */
private fun portfolioPace(accs: List<JSONObject>, usdInr: Double?): Double? {
    val mixed = accs.map { it.s("currency").trim().uppercase() }.filter { it.isNotBlank() }.distinct().size > 1
    var now = 0.0
    var usual = 0.0
    for (j in accs) {
        if (!j.b("monitoring") || !j.b("fresh")) continue
        val p = j.dn("pace_pct")?.takeIf { it.isFinite() } ?: continue
        val u = j.dn("usual_by_now")?.takeIf { it > 0 && it.isFinite() } ?: continue
        val k = if (mixed) gToInr(1.0, j.s("currency"), usdInr) ?: continue else 1.0
        now += u * (1 + p / 100.0) * k
        usual += u * k
    }
    return if (usual > 0) (now / usual - 1) * 100 else null
}

/**
 * Google Ads monitor: upar Sand hero (aaj ka kharcha + raftaar), neeche tabs (Accounts | Alerts | AI | More).
 * Sab tabs par wahi filters (bar ka Filter icon). Sirf dekhna: Google Ads me kuch nahi badalta.
 */
@Composable
fun GadsScreen(vm: GadsVm, nav: NavHostController, startTab: String) {
    val g by vm.s.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (GTABS.any { it.first == startTab }) startTab else "accounts") }
    var company by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("") }
    var campaign by rememberSaveable { mutableStateOf("") }
    var adgroup by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf("7d") }
    var addPre by remember { mutableStateOf<Pair<String, String>?>(null) }
    var token by remember { mutableStateOf<Pair<String, String>?>(null) }
    val box = remember { FilterBox() }
    val reload = remember { mutableIntStateOf(0) }
    val r = reload.intValue
    // filters ke options + limits (pace meter ki seema) ek baar
    LaunchedEffect(r) { vm.load("filters"); vm.load("settings") }
    val opts = g.view("filters")
    val sOpts = statusOptions(tab).map { it.first to it.second.t() }
    LaunchedEffect(tab) { if (statusOptions(tab).none { it.first == status }) status = "" }
    val f = mapOf(
        "company" to company, "account" to account, "country" to country, "currency" to currency, "campaign" to campaign, "adgroup" to adgroup,
        "status" to status, "range" to range,
    )
    // hero har tab par dikhta hai, isliye overview yahin se
    gPoll(60_000L, f, r) { vm.load("overview", f) }
    KLoadOnChange(tab, f)   // user ne tab / filter badla = upar ki line
    val o = g.view("overview", f)
    val accs = o?.arr("accounts")?.mapObj { it }.orEmpty()
    val usdInr = gUsdInr()
    val (drop, spike) = gPaceLimits(g)
    val openAlerts = accs.sumOf { it.d("critical").toInt() + it.d("warning").toInt() }
    val wide = isWide()
    val go: (String) -> Unit = { tab = it }
    val onAdd: (Pair<String, String>) -> Unit = { addPre = it }
    val onToken: (String, String) -> Unit = { c0, t0 -> token = c0 to t0 }
    // har list tab ka apna sort (tab badalne par yaad); Creatives / Settings par null
    val sorts = G_SORT_DEF.mapValues { (k, d) -> key(k) { rememberKSort(d.first, d.second) } }
    // Health tab par ek hi account chuna ho (filter ya akela account) to list nahi, account ka page dikhta hai: Sort nahi
    val sort = sorts[tab]?.takeUnless { tab == "health" && (account.isNotBlank() || accs.size == 1) }
    val sortOpts = gSortOpts(tab)
    val sortLead = sort?.let { kSortLead(it, sortOpts, G_SORT_KINDS) }

    val bar: @Composable ColumnScope.() -> Unit = {
        KDetailBar("Google Ads".t(), gBack(nav), sub = if (o == null) updatedText(g) else if (accs.size == 1) "1 account".t() else tf("%s accounts", accs.size), actions = {
            if (tab != "settings" && box.rows.isNotEmpty()) KBarIcon(KIcons.Filter, "Filters".t(), { box.open = true }, badge = box.active.size, badgeUrgent = false)
            if (sort != null) KSortButton(sort, sortOpts, G_SORT_KINDS)
            KBarIcon(KIcons.Refresh, "Refresh".t(), { reload.intValue++ })
        })
    }
    // har tab par wahi filters (Date range, Company, Account, Currency, Country, Campaign, Ad group, Status): sheet me
    val filters: @Composable () -> Unit = {
        CompositionLocalProvider(LocalFilterBox provides box) {
            if (tab != "settings") {
                val all = "All".t()
                val accOpts = opts?.arr("accounts")?.mapObj { it }.orEmpty()
                FilterChips("Date range".t(), G_RANGES.map { it.first to it.second.t() }, range) { range = it }
                FilterChips("Company".t(), listOf("" to all) + opts?.arr("companies")?.strings().orEmpty().map { it to it }, company) {
                    company = it; account = ""; campaign = ""; adgroup = ""
                }
                FilterChips("Account".t(), listOf("" to all) + accOpts.filter { company.isBlank() || it.s("company") == company }.map { it.s("cid") to it.s("label") }, account) {
                    account = it; campaign = ""; adgroup = ""
                }
                FilterChips("Currency".t(), listOf("" to all) + opts?.arr("currencies")?.strings().orEmpty().map { it to it }, currency) { currency = it }
                FilterChips("Country".t(), listOf("" to all) + opts?.arr("countries")?.strings().orEmpty().map { it to it }, country) { country = it }
                val camps = opts?.arr("campaigns")?.mapObj { it }.orEmpty().filter { cp ->
                    (account.isBlank() || cp.s("cid") == account) && (company.isBlank() || accOpts.any { it.s("cid") == cp.s("cid") && it.s("company") == company })
                }
                FilterChips("Campaign".t(), listOf("" to all) + camps.map { it.s("id") to it.s("name").ifBlank { it.s("id") } }, campaign) { campaign = it; adgroup = "" }
                val ags = opts?.arr("adgroups")?.mapObj { it }.orEmpty().filter {
                    (campaign.isBlank() || it.s("campaign_id") == campaign) && (account.isBlank() || it.s("cid") == account)
                }
                FilterChips("Ad group".t(), listOf("" to all) + ags.map { it.s("id") to it.s("name").ifBlank { it.s("id") } }, adgroup) { adgroup = it }
                if (sOpts.size > 1) FilterChips("Status".t(), sOpts, status) { status = it }
            }
        }
    }
    val body: @Composable () -> Unit = { TabBody(tab, g, vm, nav, f, r, o, usdInr, drop, spike, wide, onAdd, onToken, go, sort) }

    if (wide) {
        // Fold: tabs poori chaudai me (aadhe pane me "Accounts" kat jaata), phir baayein hero (+ setup / naye accounts), daayein tab ka data
        KScreen(top = bar) {
            filters()
            GadsTabs(tab, openAlerts, go)
            FilterSummary(box, lead = sortLead)
            KTwoPane(
                left = {
                    if (gSkelOn(g, o)) KSkelHero(KTint.Sand) else GadsHero(o, accs, usdInr, drop, spike)
                    if (tab == "accounts" && o != null) AccountsExtra(o, onAdd)
                },
                right = { body() },
            )
        }
    } else {
        // pehli baar (overview abhi nahi aaya): hero ki jagah skeleton, "–" nahi
        KScreen(top = { bar(); if (gSkelOn(g, o)) KSkelHero(KTint.Sand) else GadsHero(o, accs, usdInr, drop, spike) }) {
            filters()
            GadsTabs(tab, openAlerts, go)
            FilterSummary(box, lead = sortLead)
            body()
        }
    }
    FilterSheet(box)
    addPre?.let { (c0, n0) ->
        AccountSheet(null, c0, n0, g, vm, onSaved = { cid, tok -> addPre = null; reload.intValue++; if (tok != null) token = cid to tok }) { addPre = null }
    }
    token?.let { (c0, t0) -> TokenDialog(c0, t0, vm, g) { token = null } }
}

@Composable
private fun TabBody(
    tab: String, g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, r: Int, o: JSONObject?, usdInr: Double?,
    drop: Double, spike: Double, wide: Boolean, onAdd: (Pair<String, String>) -> Unit, onToken: (String, String) -> Unit, go: (String) -> Unit,
    sort: KSortState?,
) {
    when (tab) {
        "health" -> HealthTab(g, vm, nav, f, r, o, sort, usdInr)
        "policy" -> AlertsTab(g, vm, nav, f, r, "status", sort)
        "spend", "cpi", "roas" -> MonitorTab(g, vm, nav, f, r, tab, sort, usdInr)
        "campaigns" -> CampaignsTab(g, vm, nav, f, r, sort)
        "creatives" -> CreativesTab(g, vm, nav, f, r)
        "recs" -> RecsTab(g, vm, nav, f, r, sort)
        "live" -> LiveMonTab(g, vm, nav, f, r, sort)
        "history" -> AlertsTab(g, vm, nav, f, r, "all", sort)
        "settings" -> SettingsTab(g, vm, nav, r, onToken)
        else -> {
            AccountsMain(g, vm, nav, f, o, usdInr, drop, spike, onAdd, go, sort)
            if (!wide && o != null) AccountsExtra(o, onAdd)
        }
    }
}

// ---------------------------------------------------------------- hero + tabs

/** Sand hero: aaj ka kharcha (₹ + doosri currency alag), "Kul ≈ ₹", raftaar meter, daayein kitne accounts par dhyan. */
@Composable
private fun GadsHero(o: JSONObject?, accs: List<JSONObject>, usdInr: Double?, drop: Double, spike: Double) {
    KHero(KTint.Sand) {
        val c = Mfc.c
        val kinds = accs.map { acctState(it, drop, spike).first }
        val nBad = kinds.count { it == KKind.BAD }
        val nWarn = kinds.count { it == KKind.WARN }
        KHeroHead(
            "Spend today".t(), icon = KIcons.Ads,
            trailing = if (o != null && accs.isNotEmpty()) {
                {
                    when {
                        nBad > 0 -> KPill(tf("%s act now", nBad), KKind.BAD)
                        nWarn > 0 -> KPill(tf("%s to watch", nWarn), KKind.WARN)
                        // sab monitor band / naye hon to "Sab theek" nahi
                        kinds.any { it == KKind.OK } -> KPill("All fine".t(), KKind.OK)
                        else -> {}
                    }
                }
            } else null,
        )
        // har currency alag: ₹ pehle, baaki " + $120"
        val tot = o?.arr("currency_totals")?.mapObj { it.s("currency").trim().uppercase() to it.d("spend_today") }.orEmpty().filter { it.first.isNotBlank() }
        val main = tot.firstOrNull { gIsInr(it.first) } ?: tot.maxByOrNull { it.second }
        val rest = if (main == null) emptyList() else tot.filter { it.first != main.first }
        KBigNum(
            if (main == null) "–" else gCash(main.second, main.first, 0),
            suffix = if (rest.isEmpty()) null else rest.joinToString("") { " + " + gCash(it.second, it.first, 0) },
            size = 48.sp,
        )
        if (main != null && rest.isNotEmpty()) {
            val inr = tot.map { gToInr(it.second, it.first, usdInr) }
            if (inr.all { it != null }) {
                val sum = inr.sumOf { it ?: 0.0 }
                Text(
                    buildAnnotatedString {
                        append("Total ≈".t() + " ")
                        withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Bold)) { append(gCash(sum, "INR", 0)) }
                    },
                    Modifier.padding(top = 4.dp), color = c.ink2, style = gStyle(15.sp, lh = 20.sp), maxLines = 1,
                )
            }
        }
        if (o != null && accs.isNotEmpty()) KPaceMeter(portfolioPace(accs, usdInr), -drop, spike)
    }
}

/** Accounts | Alerts | AI | More (menu: Health, Policy, Spend, CPI, ROAS, Campaigns, Creatives, Live, Settings). */
@Composable
private fun GadsTabs(tab: String, alerts: Int, onPick: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val inMore = tab in G_MORE
    val idx = G_MAIN.indexOf(tab).let { if (it < 0) 3 else it }
    Box(Modifier.fillMaxWidth()) {
        KSegTabs(
            listOf(gShortTab("accounts"), gShortTab("history"), gShortTab("recs"), if (inMore) gShortTab(tab) else "More".t()),
            idx,
            { i -> if (i < G_MAIN.size) onPick(G_MAIN[i]) else menu = true },
            badges = listOf(0, if (tab == "history") 0 else alerts, 0, 0),
        )
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                KPageTokens {
                    val c = Mfc.c
                    Column {
                        G_MORE.forEach { k ->
                            val on = k == tab
                            DropdownMenuItem(
                                text = {
                                    GTxt(
                                        GTABS.firstOrNull { it.first == k }?.second?.t() ?: gShortTab(k), 15.sp, if (on) c.brand else c.ink,
                                        weight = if (on) FontWeight.Bold else FontWeight.Medium, lh = 20.sp, maxLines = 1,
                                    )
                                },
                                onClick = { menu = false; onPick(k) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                leadingIcon = { Icon(gTabIcon(k), null, tint = if (on) c.brand else c.ink2, modifier = Modifier.size(20.dp)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- A. Accounts

@Composable
private fun AccountsMain(
    g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, o: JSONObject?, usdInr: Double?, drop: Double, spike: Double,
    onAdd: (Pair<String, String>) -> Unit, go: (String) -> Unit, sort: KSortState?,
) {
    GLoad(g, o, { vm.load("overview", f) }) { ov ->
        val accs = gSortAccs(ov.arr("accounts").mapObj { it }, sort, usdInr)
        KSection("Accounts".t())
        KCard(list = true) {
            accs.forEachIndexed { i, j ->
                if (i > 0) KListDivider(lead = true)
                val a = GAccount.of(j)
                val (k, word) = acctState(j, drop, spike)
                val sp = j.dn("spend_today")
                val inr = if (sp != null && !gIsInr(a.currency)) gToInr(sp, a.currency, usdInr) else null
                val name = a.company.ifBlank { a.accountName.ifBlank { a.cidFmt } }
                KRow(
                    name, sub = word, subKind = k, lead = { KAvatar(name) },
                    value = gCash(sp, a.currency), valueSub = inr?.let { "≈ " + gCash(it, "INR", 0) }, chevron = true,
                    onClick = { nav.navigate("gads/account/${a.cid}") },
                )
            }
            if (accs.isNotEmpty()) KListDivider(lead = true)
            KRow("Add account".t(), lead = { KIconTile(KIcons.Plus, KKind.ASK) }, onClick = { onAdd("" to "") })
        }
        LastScanLine(ov.optJSONObject("last_run"), go)
        KButton(
            "Run check now".t(), { vm.act("scan", ok = "Check started".t()) { vm.load("overview", f) } }, Modifier.fillMaxWidth(),
            kind = KBtn.SOFT, icon = KIcons.Refresh, busy = "scan" in g.busy,
        )
    }
}

/** "63 campaigns · 8 min pehle" (pichhla scan). Tap = Live. */
@Composable
private fun LastScanLine(lr: JSONObject?, go: (String) -> Unit) {
    val c = Mfc.c
    val r = lr?.let { GRun.of(it) }
    val txt = if (r == null) "No scan yet".t() else tf("%s campaigns", r.campaigns) + " · " + gAgo(r.finishedAt ?: r.startedAt)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable { go("live") }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(KIcons.Refresh, null, tint = c.ink3, modifier = Modifier.size(16.dp))
            GTxt(txt, 14.sp, c.ink3, lh = 20.sp, maxLines = 1)
        }
        if (r != null && !r.status.equals("SUCCESS", ignoreCase = true)) KStatus(gText(r.status), gStateKind(r.status), Modifier.padding(top = 2.dp))
    }
}

/** Setup ki kami + dashboard me dikhe par monitor nahi hote accounts. */
@Composable
private fun AccountsExtra(o: JSONObject, onAdd: (Pair<String, String>) -> Unit) {
    SetupWarnings(o.obj("setup"))
    val disc = o.arr("discovered").mapObj { it }
    if (disc.isNotEmpty()) {
        KSection("Not monitored".t())
        KCard(list = true) {
            disc.forEachIndexed { i, d ->
                if (i > 0) KListDivider(lead = true)
                val name = d.s("name").ifBlank { d.s("cid_fmt") }
                KRow(
                    name, sub = listOfNotNull("CID " + d.s("cid_fmt"), d.s("currency").ifBlank { null }).joinToString(" · "), lead = { KAvatar(name) },
                    trailing = { KSmallAction("Add".t(), { onAdd(d.s("cid_fmt") to d.s("name")) }) },
                )
            }
        }
    }
}

/** Setup ki kami: har ek row (shape + rang + shabd). Sab theek ho to kuch nahi. */
@Composable
fun SetupWarnings(s: JSONObject) {
    val items = listOfNotNull<Triple<String, KKind, String?>>(
        if (!s.b("cron_ok")) Triple("Cron off".t(), KKind.BAD, null) else null,
        if (!s.b("email_out")) Triple("Email alerts off".t(), KKind.WARN, null) else null,
        if (!s.b("email_in")) Triple("Email intake off".t(), KKind.WARN, null) else null,
        s.s("email_in_error").ifBlank { null }?.let { Triple("Email intake error".t(), KKind.BAD, it) },
        if (!s.b("ai_on")) Triple("AI key missing".t(), KKind.WARN, null) else null,
        if (s.b("dev_mode")) Triple("Development mode".t(), KKind.WARN, null) else null,
    )
    if (items.isEmpty()) return
    KCard(list = true) {
        items.forEachIndexed { i, (t, k, e) ->
            if (i > 0) KListDivider(lead = true)
            GRow(t, err = e, lead = { KIconTile(gKindIcon(k), k) }, titleLines = 1)
        }
    }
}

// ---------------------------------------------------------------- B. Account Health

@Composable
private fun HealthTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int, o: JSONObject?, sort: KSortState?, usdInr: Double?) {
    GLoad(g, o, { vm.load("overview", f) }) { ov ->
        val accs = ov.arr("accounts").mapObj { it }
        val sel = f["account"].orEmpty().ifBlank { if (accs.size == 1) accs[0].s("cid") else "" }
        when {
            accs.isEmpty() -> KCard { KEmpty("No accounts".t(), KIcons.Ads) }
            sel.isNotBlank() -> AccountBody(g, vm, nav, sel, reload)
            else -> gSortAccs(accs, sort, usdInr).forEach { j -> HealthCard(j, nav) }
        }
    }
}

@Composable
private fun HealthCard(j: JSONObject, nav: NavHostController) {
    val c = Mfc.c
    val a = GAccount.of(j)
    val h = a.health.ifBlank { "NEW" }
    val name = a.company.ifBlank { a.cidFmt }
    val age = j.dn("data_age_min")?.toInt()
    val scan = a.lastScanAt
    KCard(list = true) {
        KRow(
            name, sub = listOfNotNull(gText(h), a.lastState.ifBlank { null }).joinToString(" · "), subKind = gStateKind(h), lead = { KAvatar(name) },
            value = if (scan != null) "Scan".t() + " " + gWhen(scan) else null, valuePlain = true, chevron = true,
            onClick = { nav.navigate("gads/account/${a.cid}") },
        )
        KListDivider()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            GKv("Data".t(), if (age == null) "No data".t() else ageMin(age), if (age == null) c.red else if (!j.b("fresh")) c.amber else Color.Unspecified)
            SourcesList(j.optJSONObject("sources"))
        }
    }
}

// ---------------------------------------------------------------- C / K. alert lists

@Composable
private fun AlertsTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int, group: String, sort: KSortState?) {
    val p = f + ("group" to group)
    gPoll(30_000L, p, reload) { vm.load("alerts", p) }
    GLoad(g, g.view("alerts", p), { vm.load("alerts", p) }) { j ->
        val list = gSortAlerts(j.arr("alerts").mapObj(GAlert::of), sort)
        KSection(
            if (group == "status") "Policy".t() else "Alerts".t(),
            trailing = if (list.isNotEmpty()) { { KPill(list.size.toString(), KKind.NEUTRAL, icon = null) } } else null,
        )
        GAlertList(list, nav, if (group == "status") "No policy issue".t() else "No alerts".t())
    }
}

// ---------------------------------------------------------------- D / E / F. Spend / CPI / ROAS

@Composable
private fun MonitorTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int, kind: String, sort: KSortState?, usdInr: Double?) {
    val p = f + ("kind" to kind)
    gPoll(60_000L, p, reload) { vm.load("monitor", p) }
    GLoad(g, g.view("monitor", p), { vm.load("monitor", p) }) { j ->
        val roasT = gRoasT()
        val notes = j.arr("notes").strings()
        if (notes.isNotEmpty()) KCard(list = true) {
            notes.forEachIndexed { i, n ->
                if (i > 0) KListDivider(lead = true)
                GRow(
                    if (':' in n) "CID " + n.substringBefore(':') else n, status = "Stale data".t() to KKind.WARN,
                    lead = { KIconTile(KIcons.Dhyan, KKind.WARN) }, titleLines = 1,
                )
            }
        }
        // har currency alag (7 din)
        val tot = j.arr("currency_totals").mapObj { it }
        if (tot.isNotEmpty()) {
            KSection("7 days".t())
            KCard(list = true) {
                tot.forEachIndexed { i, t ->
                    if (i > 0) KListDivider()
                    val cur = t.s("currency")
                    val inst = tf("%s installs", num(t.dn("d7_installs"), 0))
                    when (kind) {
                        "cpi" -> KRow("CPI · $cur", sub = inst, value = gCash(t.dn("d7_cpi"), cur, 2))
                        "roas" -> KRow("ROAS · $cur", sub = gCash(t.dn("d7_cost"), cur, 0), value = gRoas(t.dn("d7_roas")), valueColor = kRoasColor(t.dn("d7_roas"), roasT))
                        else -> KRow("Spend".t() + " · $cur", sub = inst, value = gCash(t.dn("d7_cost"), cur, 0))
                    }
                }
            }
        }
        val rows = gSortMonitor(j.arr("rows").mapObj { it }, sort, usdInr)
        KSection(tf("%s campaigns", rows.size))
        if (rows.isEmpty()) KCard { KEmpty("No campaign data".t(), KIcons.Ads) }
        rows.forEach { r -> MonitorCard(r, kind, nav, g) }
    }
}

@Composable
private fun MonitorCard(r: JSONObject, kind: String, nav: NavHostController, g: GState) {
    val c = Mfc.c
    val cur = r.s("currency")
    val th = r.obj("thresholds")
    val (gDrop, gSpike) = gPaceLimits(g)
    KCard(onClick = { nav.navigate("gads/campaign/${r.s("cid")}/${r.s("campaign_id")}") }) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                GTxt(r.s("name").ifBlank { r.s("campaign_id") }, 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
                GTxt(listOfNotNull(r.s("company").ifBlank { null }, "CID " + r.s("cid_fmt")).joinToString(" · "), 13.sp, c.ink3, lh = 18.sp, maxLines = 1)
            }
            val st = r.s("status")
            KPill(if (st.isBlank()) "–" else gText(st), gStateKind(st))
        }
        when (kind) {
            "cpi" -> {
                val t = listOfNotNull(
                    r.dn("cpi_target")?.let { "Target".t() + " " + gCash(it, cur, 2) },
                    th.dn("cpi_over_pct")?.let { tf("Alert at %s", "+" + num(it, 0) + "%") },
                    r.dn("min_installs")?.let { tf("Min %s installs", num(it, 0)) },
                )
                GNote(if (t.isEmpty()) "No CPI target".t() else t.joinToString(" · "), if (t.isEmpty()) KKind.WARN else KKind.NEUTRAL, Modifier.padding(top = 6.dp))
            }
            "roas" -> {
                val t = listOfNotNull(
                    r.dn("roas_target")?.let { "Target".t() + " " + gRoas(it) },
                    th.dn("roas_under_pct")?.let { tf("Alert at %s", "−" + num(it, 0) + "%") },
                )
                GNote(if (t.isEmpty()) "No ROAS target".t() else t.joinToString(" · "), if (t.isEmpty()) KKind.WARN else KKind.NEUTRAL, Modifier.padding(top = 6.dp))
            }
            else -> {
                val p0 = r.dn("pace_pct")?.takeIf { it.isFinite() }
                val lo = th.dn("drop_pct") ?: gDrop
                val hi = th.dn("spike_pct") ?: gSpike
                // KPaceMeter jaisa: seema par = theek
                val pk = when {
                    p0 == null -> KKind.NEUTRAL
                    p0 < -lo || p0 > hi -> KKind.WARN
                    else -> KKind.OK
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GTxt("Pace".t(), 14.sp, c.ink2, lh = 20.sp, maxLines = 1)
                    KStatus(if (p0 == null) "–" else kSignedPct(p0), pk)
                    val bud = r.dn("budget")
                    if (bud != null) GTxt("· " + "Budget".t() + " " + gCash(bud, cur, 0), 14.sp, c.ink2, lh = 20.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        WindowsTable(r.obj("windows"), cur, kind)
        r.arr("alerts").mapObj { it }.forEach { a ->
            val lv = a.s("level")
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { nav.navigate("gads/alert/${a.l("id")}") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KStatus(gLevelWord(lv) + " · " + a.s("title"), gLevelKind(lv), Modifier.weight(1f))
                Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.size(18.dp))
            }
        }
        if (!r.b("fresh")) GNote("Stale data".t(), KKind.WARN, Modifier.padding(top = 4.dp))
    }
}

// ---------------------------------------------------------------- G. Campaigns & ad groups

@Composable
private fun CampaignsTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int, sort: KSortState?) {
    gPoll(120_000L, f, reload) { vm.load("campaigns", f) }
    GLoad(g, g.view("campaigns", f), { vm.load("campaigns", f) }) { j ->
        val roasT = gRoasT()
        val rows = gSortCamps(j.arr("rows").mapObj { it }, sort)
        if (rows.isEmpty()) KCard { KEmpty("No campaign data".t(), KIcons.Ads) }
        rows.groupBy { it.s("cid") }.forEach { (cid, list) ->
            val first = list.first()
            val cur = first.s("currency")
            KSection(
                listOfNotNull(first.s("company").ifBlank { null }, first.s("cid_fmt").ifBlank { null }).joinToString(" · ").ifBlank { cid },
                trailing = if (cur.isNotBlank()) { { KPill(cur, KKind.NEUTRAL, icon = null) } } else null,
            )
            val rc = list.map { kRoasColor(it.dn("roas"), roasT) }
            KCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                GTable(
                    listOf("Campaign".t(), "Spend".t(), "Installs".t(), "CPI", "CTR", "ROAS"),
                    list.map { r ->
                        val sc = r.s("scope")
                        listOf(
                            r.s("name").ifBlank { r.s("campaign_id") } + (if (sc.isNotBlank() && sc != "campaign") " · " + gText(sc) else ""),
                            gCashS(r.dn("cost"), cur), num(r.dn("installs"), 0), gCash(r.dn("cpi"), cur, 2), pct(r.dn("ctr"), 2), gRoas(r.dn("roas")),
                        )
                    },
                    onRow = { i -> nav.navigate("gads/campaign/$cid/${list[i].s("campaign_id")}") },
                    tint = { ri, ci -> if (ci == 5) rc[ri] else Color.Unspecified },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- H. Creatives

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreativesTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int) {
    gPoll(300_000L, f, reload) { vm.load("creatives", f) }
    GLoad(g, g.view("creatives", f), { vm.load("creatives", f) }) { j ->
        val list = j.arr("campaigns").mapObj { it }
        if (list.isEmpty()) KCard { KEmpty("No creative data".t(), KIcons.Palette) }
        list.forEach { cp ->
            KCard(onClick = { nav.navigate("gads/campaign/${cp.s("cid")}/${cp.s("campaign_id")}") }) {
                val c = Mfc.c
                GTxt(cp.s("campaign").ifBlank { cp.s("campaign_id") }, 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
                GTxt(
                    listOfNotNull(
                        cp.s("company").ifBlank { null }, "CID " + cp.s("cid_fmt"), cp.s("source").ifBlank { null }?.t(), cp.s("window").ifBlank { null }?.t(),
                    ).joinToString(" · "),
                    13.sp, c.ink3, lh = 18.sp, maxLines = 2,
                )
                val lb = cp.obj("labels")
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lb.keys().forEach { k -> if (k.isNotBlank()) KPill(gText(k) + " " + lb.optInt(k), gStateKind(k)) }
                }
                cp.optJSONObject("ai")?.let { ai ->
                    val sug = ai.sn("suggestion")
                    if (sug != null) AiBox("AI idea".t(), sug)
                    else KSectionLink("Open alert".t()) { nav.navigate("gads/alert/${ai.optLong("alert_id")}") }
                }
                cp.arr("assets").mapObj { it }.take(12).forEachIndexed { i, a ->
                    if (i > 0) KDivider()
                    GAssetRow(a, ctr = true)
                }
            }
        }
    }
}

/** AI ka text: chhota spark + label (brand), neeche text. Bina box (card ke andar seedha). */
@Composable
fun AiBox(title: String, text: String) {
    val c = Mfc.c
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(KIcons.Spark, null, tint = c.brand, modifier = Modifier.size(16.dp))
            GTxt(title, 13.sp, c.brand, weight = FontWeight.SemiBold, lh = 18.sp, maxLines = 1)
        }
        GTxt(text, 15.sp, c.ink, Modifier.padding(top = 4.dp), lh = 22.sp)
    }
}

// ---------------------------------------------------------------- I. AI: kya karna hai

@Composable
private fun RecsTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int, sort: KSortState?) {
    gPoll(60_000L, f, reload) { vm.load("recs", f) }
    GLoad(g, g.view("recs", f), { vm.load("recs", f) }) { j ->
        val roasT = gRoasT()
        val lv = f["status"].orEmpty().uppercase()
        val list = gSortAlerts(j.arr("alerts").mapObj(GAlert::of).filter { lv.isBlank() || it.level == lv }, sort)
        KSection("What to do".t(), trailing = if (list.isNotEmpty()) { { KPill(list.size.toString(), KKind.NEUTRAL, icon = null) } } else null)
        if (list.isEmpty()) KCard { KEmpty("Nothing to fix".t(), KIcons.Ok) }
        list.forEach { a -> RecCard(a, nav) }
        j.optJSONObject("daily")?.let { d -> DailyReview(d, nav, roasT) }
        val on = j.b("ai_on")
        KSection("AI".t())
        KCard(list = true) {
            KRow(
                "AI explanations".t(), sub = if (on) "On".t() else "AI key missing".t(), subKind = if (on) KKind.OK else KKind.WARN,
                lead = { KIconTile(KIcons.Spark, if (on) KKind.OK else KKind.WARN) },
            )
            KListDivider(lead = true)
            KRow("AI calls today".t(), lead = { KIconTile(KIcons.Chart) }, value = "${j.d("ai_used_today").toInt()} / ${j.d("ai_cap").toInt()}")
            KListDivider(lead = true)
            KRow("Last deep analysis".t(), lead = { KIconTile(KIcons.Clock) }, value = j.sn("last_deep")?.let { gWhen(it) } ?: "–", valuePlain = true)
        }
        KButton(
            "Run deep analysis now".t(), { vm.act("deep_now", ok = "Started".t()) { vm.load("recs", f) } }, Modifier.fillMaxWidth(),
            kind = KBtn.SOFT, icon = KIcons.Spark, enabled = on, busy = "deep_now" in g.busy,
        )
        ReadOnlyNote()
    }
}

@Composable
private fun RecCard(a: GAlert, nav: NavHostController) {
    val c = Mfc.c
    KCard(onClick = { nav.navigate("gads/alert/${a.id}") }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KStatus(listOfNotNull(gLevelWord(a.level), gText(a.klass).ifBlank { null }).joinToString(" · "), gLevelKind(a.level), Modifier.weight(1f))
            a.aiConf?.let { GTxt(tf("AI %s%% sure", it), 13.sp, c.ink3, lh = 18.sp, maxLines = 1) }
        }
        GTxt(a.titleL, 16.sp, c.ink, Modifier.padding(top = 4.dp), weight = FontWeight.SemiBold, lh = 22.sp, maxLines = 3)
        GTxt(listOfNotNull(a.company.ifBlank { null }, "CID " + a.cidFmt, a.campaign.ifBlank { null }).joinToString(" · "), 13.sp, c.ink3, lh = 18.sp, maxLines = 2)
        val ai = a.ai
        if (ai != null) {
            val sum = if (Lang.hi) ai.s("summary_hi").ifBlank { ai.s("summary") } else ai.s("summary")
            if (sum.isNotBlank()) AiBox("AI".t(), sum)
            val acts = (if (Lang.hi) ai.arr("manual_actions_hi").strings() else emptyList()).ifEmpty { ai.arr("manual_actions").strings() }
            acts.forEach { GStep(it) }
        } else if (a.nextL.isNotBlank()) GStep(a.nextL)
    }
}

/** Roz ka review: har account ka kal (kharcha, installs, CPI, ROAS) + AI ki salah. */
@Composable
private fun DailyReview(d: JSONObject, nav: NavHostController, roasT: Double) {
    val c = Mfc.c
    val at = d.sn("at")
    KSection("Daily review".t(), trailing = if (at != null) { { GTxt(gWhen(at), 13.sp, c.ink3, lh = 18.sp, maxLines = 1) } } else null)
    val ai = d.optJSONObject("ai")
    ai?.sn("overall")?.let { KCard { AiBox("AI overall".t(), it) } }
    val recs = ai?.arr("accounts")?.mapObj { it }.orEmpty()
    d.arr("accounts").mapObj { it }.forEach { a ->
        val cur = a.s("currency")
        val digits = a.s("cid").filter { it.isDigit() }
        KCard(onClick = { nav.navigate("gads/account/${a.s("cid")}") }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    GTxt(a.s("company").ifBlank { a.s("cid") }, 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 1)
                    GTxt("CID " + a.s("cid") + " · " + fmtDay(a.s("yesterday")), 13.sp, c.ink3, lh = 18.sp, maxLines = 1)
                }
                val h = a.s("health")
                KPill(if (h.isBlank()) "–" else gText(h), gStateKind(h))
            }
            KSplit(
                listOf(
                    "Spend".t() to gCash(a.dn("spend"), cur, 0), "Installs".t() to num(a.dn("installs"), 0),
                    "CPI" to gCash(a.dn("cpi"), cur, 2), "ROAS" to gRoas(a.dn("roas")),
                ),
                listOf(null, null, null, kRoasColor(a.dn("roas"), roasT)),
                compact = false,
            )
            GTxt(tf("7 day avg %s", gCash(a.dn("spend_avg7"), cur, 0)), 13.sp, c.ink3, Modifier.padding(top = 6.dp), lh = 18.sp, maxLines = 1)
            val r = recs.firstOrNull { it.s("cid").filter { ch -> ch.isDigit() } == digits }
            val lines = (if (Lang.hi) r?.arr("recommendations_hi")?.strings() else null)?.takeIf { it.isNotEmpty() } ?: r?.arr("recommendations")?.strings().orEmpty()
            lines.forEach { GStep(it) }
        }
    }
}

// ---------------------------------------------------------------- J. Live

@Composable
private fun LiveMonTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int, sort: KSortState?) {
    gPoll(10_000L, f, reload) { vm.load("live", f) }
    GLoad(g, g.view("live", f), { vm.load("live", f) }) { j ->
        SetupWarnings(j.obj("setup"))
        j.optJSONObject("running")?.let { rr ->
            KCard(list = true) {
                GRunRow(GRun.of(rr), nav)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        val sc = j.obj("schedule")
        val last = j.obj("last")
        val lastTs = { k: String -> last.sn(k)?.toLongOrNull()?.let { agoText(it * 1000) } ?: "Never".t() }
        KSection("Schedule".t())
        KCard(list = true) {
            KRow(
                "Next scan".t(), lead = { KIconTile(KIcons.Refresh) }, value = j.sn("next_scan_at")?.let { tOnly(it) } ?: "–",
                valueSub = tf("every %s min", sc.d("scan_min").toInt()),
            )
            KListDivider(lead = true)
            KRow("Last scan".t(), lead = { KIconTile(KIcons.History) }, value = lastTs("scan"), valuePlain = true)
            KListDivider(lead = true)
            KRow("Email check".t(), lead = { KIconTile(KIcons.Send) }, value = lastTs("email"), valuePlain = true, valueSub = tf("every %s min", sc.d("email_min").toInt()))
            KListDivider(lead = true)
            KRow("Deep AI".t(), lead = { KIconTile(KIcons.Spark) }, value = lastTs("deep"), valuePlain = true, valueSub = tf("every %s h", sc.d("deep_h").toInt()))
            KListDivider(lead = true)
            KRow(
                "Daily review".t(), lead = { KIconTile(KIcons.Calendar) }, value = String.format(Locale.US, "%02d:00", sc.d("daily_hour").toInt()),
                valueSub = "server time".t(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton(
                "Check now".t(), { vm.act("scan", ok = "Check started".t()) { vm.load("live", f) } }, Modifier.weight(1f),
                kind = KBtn.SOFT, busy = "scan" in g.busy,
            )
            KButton(
                "Check email".t(), { vm.act("mail_check", ok = "Started".t()) { vm.load("live", f) } }, Modifier.weight(1f),
                kind = KBtn.OUTLINE, busy = "mail_check" in g.busy,
            )
        }
        // runs: default par server ka order (naye pehle)
        val runs = j.arr("runs").mapObj(GRun::of).let { l ->
            when {
                sort == null || sort.isDefault -> l
                sort.key == "issues" -> l.kSorted(sort.desc) { it.issues + it.failedChecks }
                else -> l.kSorted(sort.desc) { tsMs(it.startedAt) }
            }
        }
        KSection("Runs".t(), trailing = { KLive("auto 10 sec".t()) })
        KCard(list = true) {
            if (runs.isEmpty()) KEmpty("No runs yet".t())
            runs.forEachIndexed { i, r ->
                if (i > 0) KListDivider(lead = true)
                GRunRow(r, nav)
            }
        }
        val ev = j.arr("events").mapObj(GEvent::of)
        KSection("Activity".t())
        KCard {
            if (ev.isEmpty()) KEmpty("Nothing yet".t())
            ev.forEach { GEventRow(it, nav) }
        }
    }
}
