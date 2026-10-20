package com.myfuturecode.mfcdash.ui.screens.gads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.*
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.GadsVm
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.screens.*
import com.myfuturecode.mfcdash.ui.screens.health.*
import com.myfuturecode.mfcdash.ui.theme.Info
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*
import org.json.JSONObject

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

/** Google Ads AI Health Monitor: 12 pages (chips upar), sab par wahi filters. Sirf monitoring, Google Ads me kuch nahi badalta. */
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
    var add by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<Pair<String, String>?>(null) }
    val box = remember { FilterBox() }
    val reload = remember { mutableIntStateOf(0) }
    LaunchedEffect(reload.intValue) { vm.load("filters") }
    val opts = g.view("filters")
    val sOpts = statusOptions(tab).map { it.first to it.second.t() }
    LaunchedEffect(tab) { if (sOpts.none { it.first == status }) status = "" }
    val f = mapOf("company" to company, "account" to account, "country" to country, "currency" to currency, "campaign" to campaign, "adgroup" to adgroup,
        "status" to status, "range" to range)

    Column(Modifier.fillMaxSize()) {
        HTopBar("Google Ads AI Health".t(), updatedText(g), nav, busy = false, onRefresh = { reload.intValue++ }) {
            IconButton(onClick = { add = true }) { Icon(Icons.Filled.Add, "Add account".t()) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            ChipRow { GTABS.forEach { (k, l) -> MChip(l.t(), tab == k, { tab = k }) } }
            Spacer(Modifier.height(10.dp))
            CompositionLocalProvider(LocalFilterBox provides box) {
                if (tab != "settings") {
                    // har page par wahi filters (Company, Account, Country, Currency, Campaign, Ad group, Status, Date range)
                    val accts = opts?.arr("accounts")?.mapObj { it }.orEmpty()
                    FilterChips("Date range".t(), G_RANGES.map { it.first to it.second.t() }, range) { range = it }
                    FilterChips("Company".t(), listOf("" to "All".t()) + opts?.arr("companies")?.strings().orEmpty().map { it to it }, company) { company = it; account = ""; campaign = ""; adgroup = "" }
                    FilterChips("Account".t(), listOf("" to "All".t()) + accts.filter { company.isBlank() || it.s("company") == company }.map { it.s("cid") to it.s("label") }, account) {
                        account = it; campaign = ""; adgroup = ""
                    }
                    FilterChips("Currency".t(), listOf("" to "All".t()) + opts?.arr("currencies")?.strings().orEmpty().map { it to it }, currency) { currency = it }
                    FilterChips("Country".t(), listOf("" to "All".t()) + opts?.arr("countries")?.strings().orEmpty().map { it to it }, country) { country = it }
                    val camps = opts?.arr("campaigns")?.mapObj { it }.orEmpty().filter { c ->
                        (account.isBlank() || c.s("cid") == account) && (company.isBlank() || accts.any { it.s("cid") == c.s("cid") && it.s("company") == company })
                    }
                    FilterChips("Campaign".t(), listOf("" to "All".t()) + camps.map { it.s("id") to it.s("name").ifBlank { it.s("id") } }, campaign) { campaign = it; adgroup = "" }
                    val ags = opts?.arr("adgroups")?.mapObj { it }.orEmpty().filter { (campaign.isBlank() || it.s("campaign_id") == campaign) && (account.isBlank() || it.s("cid") == account) }
                    FilterChips("Ad group".t(), listOf("" to "All".t()) + ags.map { it.s("id") to it.s("name").ifBlank { it.s("id") } }, adgroup) { adgroup = it }
                    if (sOpts.size > 1) FilterChips("Status".t(), sOpts, status) { status = it }
                    ChipRow { FilterToggle(box) }
                    Spacer(Modifier.height(8.dp))
                    FilterSummary(box)
                }
            }
            val r = reload.intValue
            when (tab) {
                "accounts" -> AccountsTab(g, vm, nav, f, r) { tab = it }
                "health" -> HealthTab(g, vm, nav, f, r)
                "policy" -> AlertsTab(g, vm, nav, f, r, "status")
                "spend", "cpi", "roas" -> MonitorTab(g, vm, nav, f, r, tab)
                "campaigns" -> CampaignsTab(g, vm, nav, f, r)
                "creatives" -> CreativesTab(g, vm, nav, f, r)
                "recs" -> RecsTab(g, vm, nav, f, r)
                "live" -> LiveMonTab(g, vm, nav, f, r)
                "history" -> AlertsTab(g, vm, nav, f, r, "all")
                "settings" -> SettingsTab(g, vm, nav, r) { cid, tok -> token = cid to tok }
            }
        }
    }
    FilterSheet(box)
    if (add) AccountSheet(null, "", "", g, vm, onSaved = { cid, tok -> add = false; reload.intValue++; if (tok != null) token = cid to tok }) { add = false }
    token?.let { (c, t) -> TokenDialog(c, t, vm, g) { token = null } }
}

// ---------------------------------------------------------------- A. All Accounts

@Composable
private fun AccountsTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int, go: (String) -> Unit) {
    Poll(60_000, f, reload) { vm.load("overview", f) }
    var addCid by remember { mutableStateOf<Pair<String, String>?>(null) }
    var token by remember { mutableStateOf<Pair<String, String>?>(null) }
    GLoad(g, g.view("overview", f), { vm.load("overview", f) }) { o ->
        val ex = MaterialTheme.extra
        SetupWarnings(o.obj("setup"))
        val c = o.obj("counts")
        GradientHeader("GOOGLE ADS PORTFOLIO".t(), "Read-only monitoring · each account separate".t()) {
            Spacer(Modifier.height(8.dp))
            Row {
                listOf("Accounts".t() to c.d("accounts"), "Critical".t() to c.d("critical"), "Warning".t() to c.d("warning"), "Stale".t() to c.d("stale")).forEach { (l, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(l.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .75f))
                        Text(num(v), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val tot = o.arr("currency_totals").mapObj { it }
        if (tot.isNotEmpty()) {
            SectionTitle("Spend today".t(), "per currency, never combined".t())
            TileGrid(tot.map { Triple(it.s("currency"), num(it.d("spend_today"), 0), tf("%s accounts", it.d("accounts").toInt())) })
        }
        SectionTitle("Accounts".t(), o.sn("next_scan_at")?.let { "next scan".t() + " " + tOnly(it) })
        val accs = o.arr("accounts").mapObj { it }
        if (accs.isEmpty()) Panel {
            Empty("No account added yet. Tap + to add one (only accounts you are authorized for).".t())
        }
        accs.forEach { j ->
            val a = GAccount.of(j)
            Panel(onClick = { nav.navigate("gads/account/${a.cid}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.company, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Small("CID " + a.cidFmt + listOfNotNull(a.accountName.ifBlank { null }, a.currency.ifBlank { null }, j.s("tz_used").ifBlank { null }).joinToString(" · ", prefix = " · "), maxLines = 1)
                    }
                    Pill(a.health.ifBlank { "NEW" }, gStateColor(a.health))
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) { GLabel("Spend today".t()); Text(money(j.dn("spend_today"), a.currency), style = MaterialTheme.typography.titleMedium) }
                    Column(Modifier.weight(1f)) {
                        GLabel("Pace vs usual".t())
                        val p = j.dn("pace_pct")
                        Text(signedPct(p), style = MaterialTheme.typography.titleMedium, color = when { p == null -> MaterialTheme.colorScheme.onSurfaceVariant; p <= -40 || p >= 60 -> ex.bad; else -> MaterialTheme.colorScheme.onSurface })
                    }
                    Column(Modifier.weight(1f)) {
                        GLabel("Data".t())
                        Text(ageMin(j.dn("data_age_min")?.toInt()), style = MaterialTheme.typography.bodyMedium, color = if (j.b("fresh")) MaterialTheme.colorScheme.onSurface else ex.bad)
                    }
                }
                if (!j.b("fresh")) Small(if (j.dn("data_age_min") == null) "DATA SOURCE DISCONNECTED: no data received yet".t() else "STALE DATA: numbers may be incomplete, no spend alerts until data is fresh".t(), ex.bad)
                j.sn("reported_through")?.let { Small(tf("Google reported up to %s (account time)", it)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    if (j.d("critical") > 0) Pill(tf("%s critical", j.d("critical").toInt()), ex.bad)
                    if (j.d("warning") > 0) Pill(tf("%s warning", j.d("warning").toInt()), ex.warn)
                    if (j.d("info") > 0) Pill(tf("%s info", j.d("info").toInt()), Info)
                    if (!a.monitoring) Pill("monitoring off".t(), ex.muted)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val disc = o.arr("discovered").mapObj { it }
        if (disc.isNotEmpty()) {
            SectionTitle("In the dashboard, not monitored".t())
            Panel {
                Small("These accounts are in your existing dashboard data. They are monitored only after you add them and confirm authorization.".t())
                disc.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(d.s("name").ifBlank { d.s("cid_fmt") }, style = MaterialTheme.typography.bodyMedium)
                            Small("CID " + d.s("cid_fmt") + " · " + d.s("currency"))
                        }
                        TextButton(onClick = { addCid = d.s("cid_fmt") to d.s("name") }) { Text("Add".t()) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LastRunPanel(o.optJSONObject("last_run"), g, vm, nav) { go("live") }
        ReadOnlyNote()
    }
    addCid?.let { (c, n) -> AccountSheet(null, c, n, g, vm, onSaved = { cid, tok -> addCid = null; vm.load("overview", f); vm.load("filters"); if (tok != null) token = cid to tok }) { addCid = null } }
    token?.let { (c, t) -> TokenDialog(c, t, vm, g) { token = null } }
}

@Composable
fun SetupWarnings(s: JSONObject) {
    val items = listOfNotNull(
        if (!s.b("cron_ok")) "Cron is not running: scheduled checks will not happen. Add the cron from the setup guide.".t() else null,
        if (!s.b("email_out")) "Email alerts are off (email is not configured on the server).".t() else null,
        if (!s.b("email_in")) "Google Ads email intake is not configured: suspension emails are not read.".t() else null,
        s.s("email_in_error").ifBlank { null }?.let { "Email intake error: ".t() + it },
        if (!s.b("ai_on")) "AI key not set: rule alerts work, AI explanations are off.".t() else null,
        if (s.b("dev_mode")) "DEVELOPMENT MODE is on: simulated test events are allowed (labelled SIMULATED).".t() else null,
    )
    if (items.isEmpty()) return
    Panel {
        Text("Setup".t(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.extra.warn)
        items.forEach { Small("• $it") }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun LastRunPanel(lr: JSONObject?, g: GState, vm: GadsVm, nav: NavHostController, live: () -> Unit) {
    Panel {
        if (lr != null) {
            val r = GRun.of(lr)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last scan".t(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Pill(r.status, gStateColor(r.status))
            }
            Small(r.uid + " · " + tFull(r.finishedAt ?: r.startedAt) + " · " + tf("%s accounts", r.accounts) + " · " + tf("%s campaigns", r.campaigns))
            Spacer(Modifier.height(8.dp))
        } else Empty("No scan has run yet".t())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BusyButton("Run check now".t(), "scan" in g.busy, Modifier.weight(1f)) { vm.act("scan", ok = "Check started".t()) }
            OutlinedButton(onClick = live, modifier = Modifier.weight(1f)) { Text("Live Monitoring".t()) }
        }
    }
}

// ---------------------------------------------------------------- B. Account Health

@Composable
private fun HealthTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int) {
    Poll(60_000, f, reload) { vm.load("overview", f) }
    GLoad(g, g.view("overview", f), { vm.load("overview", f) }) { o ->
        val accs = o.arr("accounts").mapObj { GAccount.of(it) to it }
        if (accs.isEmpty()) { Panel { Empty("No account in this filter".t()) }; return@GLoad }
        val sel = f["account"].orEmpty().ifBlank { if (accs.size == 1) accs[0].first.cid else "" }
        if (sel.isNotBlank()) { AccountBody(g, vm, nav, sel, reload); return@GLoad }
        Small("Tap an account for its checks, campaigns, alerts and emails.".t())
        Spacer(Modifier.height(6.dp))
        accs.forEach { (a, j) ->
            Panel(onClick = { nav.navigate("gads/account/${a.cid}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.company + " · " + a.cidFmt, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Small(listOfNotNull(a.lastState.ifBlank { null }, "scan".t() + " " + ago(a.lastScanAt), "data".t() + " " + ageMin(j.dn("data_age_min")?.toInt())).joinToString(" · "), maxLines = 2)
                    }
                    Pill(a.health.ifBlank { "NEW" }, gStateColor(a.health))
                }
                Spacer(Modifier.height(4.dp))
                SourcesList(j.optJSONObject("sources"))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------- C / K. alerts lists

@Composable
private fun AlertsTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int, group: String) {
    val p = f + ("group" to group)
    Poll(30_000, p, reload) { vm.load("alerts", p) }
    if (group == "status") Small("Account status comes only from Google: customer status (read-only script) or verified Google Ads emails. Zero spend alone is never called a suspension.".t())
    else Small("Every alert ever raised, with when it was first seen, notified and closed.".t())
    Spacer(Modifier.height(6.dp))
    GLoad(g, g.view("alerts", p), { vm.load("alerts", p) }) { j ->
        val list = j.arr("alerts").mapObj(GAlert::of)
        SectionTitle(tf("%s alerts", list.size), if (group == "status") "suspension · payment · policy · disapprovals".t() else null)
        GAlertList(list, nav, if (group == "status") "No suspension, payment or policy problem found".t() else "No alerts in this filter".t())
    }
}

// ---------------------------------------------------------------- D / E / F. monitors

@Composable
private fun MonitorTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int, kind: String) {
    val p = f + ("kind" to kind)
    Poll(60_000, p, reload) { vm.load("monitor", p) }
    GLoad(g, g.view("monitor", p), { vm.load("monitor", p) }) { j ->
        val ex = MaterialTheme.extra
        Small(j.s("note").t())
        j.arr("notes").strings().forEach { Small("⚠ $it", ex.warn) }
        val tot = j.arr("currency_totals").mapObj { it }
        if (tot.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            TileGrid(tot.map { t ->
                val cur = t.s("currency")
                when (kind) {
                    "cpi" -> Triple("CPI 7d · $cur", money(t.dn("d7_cpi"), cur), tf("%s installs", num(t.dn("d7_installs"), 0)))
                    "roas" -> Triple("ROAS 7d · $cur", t.dn("d7_roas")?.let { num(it * 100, 0) + "%" } ?: "–", money(t.dn("d7_cost"), cur, 0))
                    else -> Triple("Spend 7d · $cur", num(t.dn("d7_cost"), 0), tf("%s installs", num(t.dn("d7_installs"), 0)))
                }
            }, cols = 2)
        }
        val rows = j.arr("rows").mapObj { it }
        SectionTitle(tf("%s campaigns", rows.size))
        if (rows.isEmpty()) Panel { Empty("No campaign data in this filter".t()) }
        rows.forEach { r ->
            val cur = r.s("currency")
            Panel(onClick = { nav.navigate("gads/campaign/${r.s("cid")}/${r.s("campaign_id")}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Small(r.s("company") + " · CID " + r.s("cid_fmt") + " · " + cur, maxLines = 1)
                    }
                    Pill(r.s("status").ifBlank { "?" }, gStateColor(r.s("status")))
                }
                Spacer(Modifier.height(4.dp))
                when (kind) {
                    "cpi" -> Small(listOfNotNull(r.dn("cpi_target")?.let { "target".t() + " " + money(it, cur) }, r.obj("thresholds").dn("cpi_over_pct")?.let { tf("alert at +%s%%", num(it, 0)) },
                        r.dn("min_installs")?.let { tf("min %s installs", num(it, 0)) }).joinToString(" · ").ifBlank { "No CPI target: set one in the campaign page".t() })
                    "roas" -> Small(listOfNotNull(r.dn("roas_target")?.let { "target".t() + " " + num(it * 100, 0) + "%" }, r.obj("thresholds").dn("roas_under_pct")?.let { tf("alert at -%s%%", num(it, 0)) })
                        .joinToString(" · ").ifBlank { "No ROAS target: set one in the campaign page".t() })
                    else -> {
                        val p0 = r.dn("pace_pct")
                        Row {
                            Small("Pace vs usual".t() + ": ")
                            Text(signedPct(p0), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                                color = when { p0 == null -> MaterialTheme.colorScheme.onSurfaceVariant; p0 <= -(r.obj("thresholds").dn("drop_pct") ?: 40.0) || p0 >= (r.obj("thresholds").dn("spike_pct") ?: 60.0) -> ex.bad; else -> ex.good })
                            r.dn("budget")?.let { Small("  · " + "budget".t() + " " + money(it, cur, 0)) }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                WindowsTable(r.obj("windows"), cur, kind)
                r.arr("alerts").mapObj { it }.forEach { a -> Small("● " + a.s("level") + " · " + a.s("title"), levelColor(a.s("level")), 2) }
                if (!r.b("fresh")) Small("STALE DATA".t(), ex.bad)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------- G. Campaign & Ad Group

@Composable
private fun CampaignsTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int) {
    Poll(120_000, f, reload) { vm.load("campaigns", f) }
    GLoad(g, g.view("campaigns", f), { vm.load("campaigns", f) }) { j ->
        val rows = j.arr("rows").mapObj { it }
        Small(j.s("note").t())
        Spacer(Modifier.height(6.dp))
        rows.groupBy { it.s("cid") }.forEach { (cid, list) ->
            val cur = list.first().s("currency")
            SectionTitle(list.first().s("company") + " · " + list.first().s("cid_fmt"), cur)
            Panel {
                Table(listOf("Campaign".t(), "Spend".t(), "Installs".t(), "CPI", "CTR", "ROAS"), list.map { r ->
                    listOf(r.s("name") + (if (r.s("scope") != "campaign" && r.s("scope").isNotBlank()) " · " + r.s("scope") else ""), num(r.dn("cost"), 0), num(r.dn("installs"), 0),
                        r.dn("cpi")?.let { num(it, 2) } ?: "–", pct(r.dn("ctr"), 2), r.dn("roas")?.let { num(it * 100, 0) + "%" } ?: "–")
                }, firstWeight = 2.2f, onRow = { i -> nav.navigate("gads/campaign/$cid/${list[i].s("campaign_id")}") })
                Small("Money columns in " + cur)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (rows.isEmpty()) Panel { Empty("No campaign data in this filter".t()) }
    }
}

// ---------------------------------------------------------------- H. AI Creative Review

@Composable
private fun CreativesTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int) {
    Poll(300_000, f, reload) { vm.load("creatives", f) }
    GLoad(g, g.view("creatives", f), { vm.load("creatives", f) }) { j ->
        Small(j.s("note").t())
        Spacer(Modifier.height(6.dp))
        val list = j.arr("campaigns").mapObj { it }
        if (list.isEmpty()) Panel { Empty("No creative data yet (needs asset data from the dashboard or the read-only script)".t()) }
        list.forEach { c ->
            Panel(onClick = { nav.navigate("gads/campaign/${c.s("cid")}/${c.s("campaign_id")}") }) {
                Text(c.s("campaign"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Small(c.s("company") + " · CID " + c.s("cid_fmt") + " · " + c.s("source") + " · " + c.s("window"))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    val l = c.obj("labels")
                    l.keys().forEach { k -> Pill("$k ${l.optInt(k)}", gStateColor(k)) }
                }
                c.optJSONObject("ai")?.let { ai ->
                    ai.sn("suggestion")?.let { AiBox("AI idea for manual review".t(), it) }
                    if (ai.sn("suggestion") == null) TextButton(onClick = { nav.navigate("gads/alert/${ai.optLong("alert_id")}") }, contentPadding = PaddingValues(0.dp)) { Text("Low-performing assets: open alert →".t()) }
                }
                c.arr("assets").mapObj { it }.take(12).forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Pill(a.s("label").ifBlank { "–" }, gStateColor(a.s("label")))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.s("name").ifBlank { a.s("url") }.ifBlank { a.s("asset_id") }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Small(listOf(gText(a.s("field_type")), gText(a.s("asset_type")), short(a.dn("impressions")) + " impr.", "CTR " + pct(a.dn("ctr"), 2)).joinToString(" · "), maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun AiBox(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)).padding(10.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- I. AI Recommendations

@Composable
private fun RecsTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int) {
    Poll(60_000, f, reload) { vm.load("recs", f) }
    GLoad(g, g.view("recs", f), { vm.load("recs", f) }) { j ->
        ReadOnlyNote()
        Panel {
            KV("AI".t(), if (j.b("ai_on")) "on".t() else "off (no key)".t(), valueColor = if (j.b("ai_on")) MaterialTheme.extra.good else MaterialTheme.extra.warn)
            KV("AI calls today".t(), j.d("ai_used_today").toInt().toString() + " / " + j.d("ai_cap").toInt())
            KV("Last deep analysis".t(), j.sn("last_deep")?.let { tFull(it) } ?: "–")
            Small("AI runs only when the rules find something (and every 6 hours for a deeper look), so it stays cheap.".t())
            Spacer(Modifier.height(6.dp))
            BusyButton("Run deep analysis now".t(), "deep_now" in g.busy, Modifier.fillMaxWidth(), enabled = j.b("ai_on"), outlined = true) {
                vm.act("deep_now", ok = "Started".t()) { vm.load("recs", f) }
            }
        }
        Spacer(Modifier.height(10.dp))
        j.optJSONObject("daily")?.let { d ->
            SectionTitle("Daily portfolio review".t(), tFull(d.sn("at")))
            val ai = d.optJSONObject("ai")
            ai?.sn("overall")?.let { AiBox("AI overall".t(), it) }
            val recs = ai?.arr("accounts")?.mapObj { it }.orEmpty()
            d.arr("accounts").mapObj { it }.forEach { a ->
                val cur = a.s("currency")
                Panel(onClick = { nav.navigate("gads/account/${a.s("cid")}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.s("company") + " · " + a.s("cid"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Pill(a.s("health").ifBlank { "?" }, gStateColor(a.s("health")))
                    }
                    Small(fmtDay(a.s("yesterday")) + ": " + "spend".t() + " " + money(a.dn("spend"), cur) + " (" + "7d avg".t() + " " + money(a.dn("spend_avg7"), cur) + ") · " +
                        "installs".t() + " " + num(a.dn("installs"), 0) + " · CPI " + money(a.dn("cpi"), cur) + " · ROAS " + (a.dn("roas")?.let { num(it * 100, 0) + "%" } ?: "–"))
                    val r = recs.firstOrNull { it.s("cid").filter { c -> c.isDigit() } == a.s("cid").filter { c -> c.isDigit() } }
                    val lines = (if (Lang.hi) r?.arr("recommendations_hi")?.strings() else null)?.takeIf { it.isNotEmpty() } ?: r?.arr("recommendations")?.strings().orEmpty()
                    lines.forEach { Text("→ $it", style = MaterialTheme.typography.bodySmall) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        val lv = f["status"].orEmpty().uppercase()
        val list = j.arr("alerts").mapObj(GAlert::of).filter { lv.isBlank() || it.level == lv }
        SectionTitle("Open issues and what to do".t(), tf("%s alerts", list.size))
        if (list.isEmpty()) Panel { Empty("Nothing to fix right now".t()) }
        list.forEach { a ->
            Panel(onClick = { nav.navigate("gads/alert/${a.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(a.level, levelColor(a.level)); Spacer(Modifier.width(5.dp)); Pill(a.klass, gStateColor(a.klass))
                    Spacer(Modifier.weight(1f))
                    a.aiConf?.let { Small(tf("AI %s%% sure", it)) }
                }
                Text(a.titleL, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Small(a.company + " · CID " + a.cidFmt + (if (a.campaign.isNotBlank()) " · " + a.campaign else ""))
                val ai = a.ai
                if (ai != null) {
                    val sum = if (Lang.hi) ai.s("summary_hi").ifBlank { ai.s("summary") } else ai.s("summary")
                    if (sum.isNotBlank()) AiBox("AI", sum)
                    val acts = (if (Lang.hi) ai.arr("manual_actions_hi").strings() else emptyList()).ifEmpty { ai.arr("manual_actions").strings() }
                    acts.forEach { Text("→ $it", style = MaterialTheme.typography.bodySmall) }
                } else Text("→ " + a.nextL, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------- J. Live Monitoring Activity

@Composable
private fun LiveMonTab(g: GState, vm: GadsVm, nav: NavHostController, f: Map<String, String>, reload: Int) {
    Poll(10_000, f, reload) { vm.load("live", f) }
    GLoad(g, g.view("live", f), { vm.load("live", f) }) { j ->
        SetupWarnings(j.obj("setup"))
        val sc = j.obj("schedule")
        val last = j.obj("last")
        val lastTs = { k: String -> last.sn(k)?.toLongOrNull()?.let { agoText(it * 1000) } ?: "never".t() }
        TileGrid(listOf(
            Triple("Next scan".t(), j.sn("next_scan_at")?.let { tOnly(it) } ?: "–", tf("every %s min", sc.d("scan_min").toInt())),
            Triple("Last scan".t(), lastTs("scan"), null),
            Triple("Email check".t(), lastTs("email"), tf("every %s min", sc.d("email_min").toInt())),
            Triple("Deep AI".t(), lastTs("deep"), tf("every %s h", sc.d("deep_h").toInt())),
            Triple("Daily review".t(), String.format("%02d:00", sc.d("daily_hour").toInt()), "server time".t()),
        ))
        j.optJSONObject("running")?.let { r ->
            Panel {
                Text("Running now".t(), style = MaterialTheme.typography.titleSmall, color = Info)
                GRunRow(GRun.of(r), nav)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BusyButton("Run check now".t(), "scan" in g.busy, Modifier.weight(1f)) { vm.act("scan", ok = "Check started".t()) { vm.load("live", f) } }
            BusyButton("Check email".t(), "mail_check" in g.busy, Modifier.weight(1f), outlined = true) { vm.act("mail_check", ok = "Started".t()) { vm.load("live", f) } }
        }
        Spacer(Modifier.height(4.dp))
        Small("A run that could not fetch data is shown as PARTIAL or FAILED, never as success.".t())
        Spacer(Modifier.height(8.dp))
        val runs = j.arr("runs").mapObj(GRun::of)
        SectionTitle("Runs".t(), "auto 10 sec".t())
        Panel {
            if (runs.isEmpty()) Empty("No runs yet".t())
            runs.forEachIndexed { i, r -> GRunRow(r, nav); if (i < runs.lastIndex) Hairline() }
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("Activity".t())
        Panel {
            val ev = j.arr("events").mapObj(GEvent::of)
            if (ev.isEmpty()) Empty("Nothing yet".t())
            ev.forEach { GEventRow(it, nav) }
        }
    }
}
