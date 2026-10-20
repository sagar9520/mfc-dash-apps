package com.myfuturecode.mfcdash.ui.screens.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.StudioVm
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.screens.*
import com.myfuturecode.mfcdash.ui.screens.gads.CheckLine
import com.myfuturecode.mfcdash.ui.screens.gads.GLabel
import com.myfuturecode.mfcdash.ui.screens.gads.Small
import com.myfuturecode.mfcdash.ui.screens.gads.SwitchLine
import com.myfuturecode.mfcdash.ui.screens.gads.updatedText
import com.myfuturecode.mfcdash.ui.screens.health.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*
import org.json.JSONObject

val STABS = listOf(
    "overview" to "Overview", "research" to "Research & Ranking", "projects" to "Projects", "approvals" to "Approvals", "history" to "History",
    "runs" to "Runs", "sources" to "Data sources", "settings" to "Settings",
)

/** AI App Research Studio: research -> ranking -> Gate 1 -> competitor analysis -> Gate 2. Kabhi khud publish nahi karta. */
@Composable
fun StudioScreen(vm: StudioVm, nav: NavHostController, startTab: String) {
    val g by vm.s.collectAsStateWithLifecycle()
    val start = if (startTab == "ranking") "research" else startTab
    var tab by rememberSaveable { mutableStateOf(if (STABS.any { it.first == start }) start else "overview") }
    var idea by remember { mutableStateOf(false) }
    val reload = remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        HTopBar("AI App Research Studio".t(), updatedText(g), nav, busy = false, onRefresh = { reload.intValue++ }) {
            IconButton(onClick = { idea = true }) { Icon(Icons.Filled.Add, "Add your own idea".t()) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            ChipRow { STABS.forEach { (k, l) -> MChip(l.t(), tab == k, { tab = k }) } }
            Spacer(Modifier.height(10.dp))
            val r = reload.intValue
            when (tab) {
                "overview" -> OverviewTab(g, vm, nav, r) { tab = it }
                "research" -> ResearchTab(g, vm, nav, r)
                "projects" -> ProjectsTab(g, vm, nav, r)
                "approvals" -> ApprovalsTab(g, vm, nav, r)
                "history" -> HistoryTab(g, vm, nav, r)
                "runs" -> RunsTab(g, vm, nav, r)
                "sources" -> SourcesTab(g, vm, nav, r)
                "settings" -> SettingsTab(g, vm, r)
            }
        }
    }
    if (idea) IdeaDialog(g, vm, nav) { idea = false }
}

// ---------------------------------------------------------------- Overview

@Composable
private fun OverviewTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int, go: (String) -> Unit) {
    Poll(60_000, reload) { vm.load("overview") }
    SLoad(g, g.view("overview"), { vm.load("overview") }) { o ->
        val ex = MaterialTheme.extra
        val c = o.obj("counts")
        GradientHeader("RESEARCH STUDIO".t(), "Research, rank, approve. Never auto-publish.".t()) {
            Spacer(Modifier.height(8.dp))
            Row {
                listOf("New ideas".t() to c.d("ideas_new"), "Watching".t() to c.d("ideas_watch"), "Projects".t() to c.d("projects_active"), "Evidence".t() to c.d("evidence")).forEach { (l, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(l.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .75f))
                        Text(num(v), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val pend = o.arr("pending").objs()
        SectionTitle("Waiting for you".t(), if (pend.isEmpty()) null else pend.size.toString())
        if (pend.isEmpty()) Panel { Empty("Nothing waiting for your approval.".t()) }
        pend.forEach { p -> PendingRow(p, nav); Spacer(Modifier.height(6.dp)) }

        val sp = o.obj("spend")
        SectionTitle("AI spend this month".t())
        Panel {
            val lim = sp.d("limit_usd")
            KV("Spent".t(), usd(sp.d("month_usd")) + " / " + usd(lim), if (lim > 0 && sp.d("month_usd") >= lim * .8) ex.warn else null, bold = true)
            if (lim > 0) LinearProgressIndicator(progress = { (sp.d("month_usd") / lim).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Small("Research stops (BLOCKED) at the limit. Change it in Settings.".t())
        }

        val top = o.arr("top").objs()
        if (top.isNotEmpty()) {
            SectionTitle("Top ideas".t(), "from the last ranking".t())
            top.forEach { OppCard(it, nav) }
            TextButton(onClick = { go("research") }) { Text("See all ideas".t()) }
        }

        SectionTitle("Data sources".t())
        Panel(onClick = { go("sources") }) {
            o.arr("sources").objs().forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(s.s("name"), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Pill(stText(s.s("status")), stColor(s.s("status")))
                }
            }
        }

        val sch = o.obj("schedule")
        SectionTitle("Schedule".t(), o.sn("cron_last")?.let { "cron".t() + " " + ago(it) } ?: "cron not running yet".t())
        Panel {
            KV("Daily discovery".t(), if (sch.b("daily")) ("on".t() + " · " + "last".t() + " " + (sch.sn("last_daily") ?: "–")) else "off".t())
            KV("Weekly ranking".t(), if (sch.b("weekly")) ("on".t() + " · " + "last".t() + " " + (sch.sn("last_weekly") ?: "–")) else "off".t())
            KV("Monthly deep research".t(), if (sch.b("monthly")) ("on".t() + " · " + "last".t() + " " + (sch.sn("last_monthly") ?: "–")) else "off".t())
            if (o.sn("cron_last") == null) Small("Add the server cron line from the setup guide, otherwise nothing runs.".t(), ex.bad)
        }

        val runs = o.arr("runs").objs()
        if (runs.isNotEmpty()) {
            SectionTitle("Recent runs".t())
            Panel { runs.forEach { SRunRow(it, nav) } }
        }
        val act = o.arr("activity").objs()
        if (act.isNotEmpty()) {
            SectionTitle("Activity".t())
            Panel { act.forEach { SEventRow(it, nav) } }
        }
        Spacer(Modifier.height(8.dp))
        NeverPublishNote()
    }
}

@Composable
fun PendingRow(p: JSONObject, nav: NavHostController) {
    Panel(onClick = {
        val r = p.s("route")
        nav.navigate(if (r == "studio/ranking") "studio/research" else r)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("GATE ".t() + p.d("gate").toInt(), MaterialTheme.extra.warn); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(p.s("title"), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Small(p.s("detail"), maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- Research & ranking (Gate 1)

@Composable
private fun ResearchTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int) {
    var engine by rememberSaveable { mutableStateOf("organic") }
    var tier by rememberSaveable { mutableStateOf("") }
    var cc by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("active") }
    var sel by remember { mutableStateOf(setOf<Long>()) }
    var approve by remember { mutableStateOf(false) }
    val f = mapOf("engine" to engine, "tier" to tier, "cc" to cc, "category" to cat, "status" to status)
    Poll(90_000, f, reload) { vm.load("research", f) }
    val box = remember { FilterBox() }
    val last = g.view("research", f)
    CompositionLocalProvider(LocalFilterBox provides box) {
        FilterChips("Engine".t(), listOf("organic" to "Organic".t(), "paid" to "Paid".t(), "combined" to "Combined".t()), engine) { engine = it }
        FilterChips("Status".t(), listOf("active" to "Active".t(), "NEW" to "New".t(), "WATCH" to "Watching".t(), "SELECTED" to "Selected".t(), "REJECTED" to "Rejected".t(), "EVALUATING" to "Evaluating".t()), status) { status = it }
        FilterChips("Tier".t(), listOf("" to "All".t(), "1" to "Tier 1", "2" to "Tier 2", "3" to "Tier 3"), tier) { tier = it }
        FilterChips("Country".t(), listOf("" to "All".t()) + last?.arr("countries")?.objs().orEmpty().map { it.s("cc") to (it.s("cc") + " " + it.s("name")) }, cc) { cc = it }
        FilterChips("Category".t(), listOf("" to "All".t()) + last?.arr("categories")?.strings().orEmpty().map { it to it }, cat) { cat = it }
    }
    ChipRow { FilterToggle(box) }
    Spacer(Modifier.height(6.dp))
    FilterSummary(box)
    FilterSheet(box)
    SLoad(g, last, { vm.load("research", f) }) { o ->
        val items = o.arr("items").objs()
        val lr = o.optJSONObject("last_ranking")
        Panel {
            Text(when (engine) { "paid" -> "Paid engine: ideas that can be profitable with paid user acquisition".t(); "combined" -> "Combined: average of organic and paid scores".t()
                else -> "Organic engine: ideas that can grow without paid ads".t() }, style = MaterialTheme.typography.bodySmall)
            Small(if (lr != null) tf("Ranking v%s · %s · %s", lr.d("version").toInt(), lr.s("status"), ago(lr.sn("finished_at") ?: lr.s("queued_at"))) else "No ranking run yet.".t())
            Small("Score = weighted average over factors that have data only. Missing data is never counted as zero; it lowers confidence instead.".t())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                BusyButton("Run ranking now".t(), "run_rank" in g.busy, outlined = true) {
                    vm.act("run", mapOf("job" to "rank"), "run_rank", ok = "Ranking queued. It starts within 5 minutes.".t())
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) Panel { Empty("No ideas match these filters.".t()) }
        items.forEach { o2 ->
            val id = o2.l("id")
            val can = o2.s("status") in setOf("NEW", "WATCH")
            OppCard(o2, nav, engine, if (can) id in sel else null, if (can) ({ sel = if (id in sel) sel - id else sel + id }) else null)
        }
        if (sel.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Panel {
                Text(tf("%s ideas selected", sel.size), style = MaterialTheme.typography.titleSmall)
                Small("Gate 1: approving creates a project per idea and starts competitor analysis (uses AI budget).".t())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(onClick = { approve = true }, enabled = sel.size in 1..5) { Text("Approve (Gate 1)".t()) }
                    OutlinedButton(onClick = { nav.navigate("studio/compare/" + sel.joinToString(",")) }, enabled = sel.size in 2..5) { Text("Compare".t()) }
                    TextButton(onClick = { sel = emptySet() }) { Text("Clear".t()) }
                }
                if (sel.size > 5) Small("Select at most 5 ideas at once.".t(), MaterialTheme.extra.bad)
            }
        }
    }
    if (approve) TextAskDialog("Approve Gate 1?".t(),
        tf("%s ideas will become projects. Competitor research (top 3 per idea) and a feature spec will be prepared, then Gate 2 waits for you. Nothing is built or published.", sel.size),
        "Note (optional)".t(), "Approve".t(), required = false, busy = "gate1" in g.busy, onDismiss = { approve = false }) { note ->
        val fields = HashMap<String, String>()
        sel.forEachIndexed { i, id -> fields["select[$i]"] = id.toString() }
        fields["note"] = note
        vm.act("gate1", fields, "gate1", ok = "Approved. Competitor analysis queued.".t()) { approve = false; sel = emptySet(); vm.load("research", f); vm.load("overview") }
    }
}

// ---------------------------------------------------------------- Projects

@Composable
private fun ProjectsTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int) {
    Poll(60_000, reload) { vm.load("projects") }
    SLoad(g, g.view("projects"), { vm.load("projects") }) { o ->
        val items = o.arr("items").objs()
        if (items.isEmpty()) Panel { Empty("No projects yet. Approve ideas at Gate 1 (Research & Ranking) to start one.".t()) }
        items.forEach { p -> ProjectCard(p, nav) }
        NeverPublishNote()
    }
}

@Composable
fun ProjectCard(p: JSONObject, nav: NavHostController) {
    val ex = MaterialTheme.extra
    Panel(onClick = { nav.navigate("studio/project/${p.l("id")}") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Small(p.s("puid") + " · " + stText(p.s("stage")) + " · " + ago(p.s("updated_at")), maxLines = 1)
            }
            p.dn("next_gate")?.let { Pill("Gate".t() + " " + it.toInt(), ex.warn) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
            p.arr("gates").objs().forEach { gt ->
                val c = when (gt.s("state")) { "APPROVED" -> ex.good; "WAITING" -> ex.warn; "REJECTED" -> ex.bad; "CHANGES_REQUESTED" -> ex.warn; else -> ex.line }
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
            }
        }
        p.sn("blocker")?.let { Small(it, ex.bad, maxLines = 2) }
    }
    Spacer(Modifier.height(8.dp))
}

// ---------------------------------------------------------------- Approvals

@Composable
private fun ApprovalsTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int) {
    Poll(60_000, reload) { vm.load("approvals") }
    SLoad(g, g.view("approvals"), { vm.load("approvals") }) { o ->
        val pend = o.arr("pending").objs()
        SectionTitle("Waiting for your decision".t())
        if (pend.isEmpty()) Panel { Empty("Nothing waiting.".t()) }
        pend.forEach { PendingRow(it, nav); Spacer(Modifier.height(6.dp)) }
        SectionTitle("Recent decisions".t())
        Panel {
            val rec = o.arr("recent").objs()
            if (rec.isEmpty()) Empty("No decisions yet.".t())
            rec.forEach { a ->
                val route = a.dn("project_id")?.let { "studio/project/${it.toLong()}" } ?: a.dn("opp_id")?.let { "studio/opp/${it.toLong()}" }
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(a.s("decision"), stColor(a.s("decision"))); Spacer(Modifier.width(6.dp))
                        Text((if (a.d("gate") > 0) "Gate".t() + " " + a.d("gate").toInt() + " · " else "") + a.s("name"), style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (route != null) TextButton(onClick = { nav.navigate(route) }) { Text("Open".t()) }
                    }
                    Small(listOfNotNull(a.dn("artifact_version")?.let { "v" + it.toInt() }, a.s("user"), tFull(a.s("created_at")) + " UTC").joinToString(" · "))
                    a.sn("feedback")?.let { Small(it, maxLines = 3) }
                }
                Hairline()
            }
        }
    }
}

// ---------------------------------------------------------------- History (search)

@Composable
private fun HistoryTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int) {
    var q by rememberSaveable { mutableStateOf("") }
    var qApplied by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val f = mapOf("q" to qApplied, "kind" to kind, "page" to page.toString())
    LaunchedEffect(f, reload) { vm.load("history", f) }
    OutlinedTextField(q, { q = it }, label = { Text("Search history".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        trailingIcon = { TextButton(onClick = { qApplied = q.trim(); page = 0 }) { Text("Search".t()) } })
    Spacer(Modifier.height(6.dp))
    ChipRow {
        listOf("" to "All".t(), "decision" to "Decisions".t(), "artifact" to "Versions".t(), "action" to "Actions".t(), "notify" to "Notifications".t(), "log" to "Logs".t())
            .forEach { (k, l) -> MChip(l, kind == k, { kind = k; page = 0 }) }
    }
    Spacer(Modifier.height(8.dp))
    SLoad(g, g.view("history", f), { vm.load("history", f) }) { o ->
        val items = o.arr("items").objs()
        Panel {
            if (items.isEmpty()) Empty("Nothing found.".t())
            items.forEach { SEventRow(it, nav); Hairline() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            if (page > 0) OutlinedButton(onClick = { page-- }) { Text("Newer".t()) }
            if (items.size >= 60) OutlinedButton(onClick = { page++ }) { Text("Older".t()) }
        }
    }
}

// ---------------------------------------------------------------- Runs

@Composable
private fun RunsTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int) {
    Poll(20_000, reload) { vm.load("runs") }
    Panel {
        Text("Start a run now".t(), style = MaterialTheme.typography.titleSmall)
        Small("Runs are queued and the server cron picks them up within 5 minutes. AI runs use the monthly budget.".t())
        Spacer(Modifier.height(6.dp))
        listOf("economics" to "Internal economics (no AI cost)", "discover" to "Discovery (AI + web)", "rank" to "Ranking (AI + web)", "deep" to "Deep research (AI + web)").chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                row.forEach { (j, l) ->
                    BusyButton(l.t(), "run_$j" in g.busy, Modifier.weight(1f), outlined = true) {
                        vm.act("run", mapOf("job" to j), "run_$j", ok = "Queued".t()) { vm.load("runs") }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    SLoad(g, g.view("runs"), { vm.load("runs") }) { o ->
        Panel {
            val items = o.arr("items").objs()
            if (items.isEmpty()) Empty("No runs yet.".t())
            items.forEach { SRunRow(it, nav) }
        }
    }
}

// ---------------------------------------------------------------- Sources

@Composable
private fun SourcesTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int) {
    val ctx = LocalContext.current
    LaunchedEffect(reload) { vm.load("sources") }
    Small("Only legitimate, licensed sources. Each value keeps its label: VERIFIED, THIRD-PARTY ESTIMATE, AI INFERENCE or UNKNOWN.".t())
    Spacer(Modifier.height(8.dp))
    SLoad(g, g.view("sources"), { vm.load("sources") }) { o ->
        o.arr("items").objs().forEach { s ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.s("name"), style = MaterialTheme.typography.titleSmall)
                        Small(listOf(stText(s.s("engine")), labelText(s.s("label")), if (s.b("paid")) "paid".t() else "free".t()).joinToString(" · "))
                    }
                    Pill(stText(s.s("status")), stColor(s.s("status")))
                }
                Para("Provides".t(), s.sn("provides"))
                Para("Method".t(), s.sn("method"))
                s.sn("missing")?.let { Para("Missing".t(), it) }
                s.sn("last_ok_at")?.let { Small("Last OK".t() + " " + ago(it) + " · " + s.d("last_items").toInt() + " " + "items".t()) }
                s.sn("last_error")?.let { Small("Last error".t() + ": " + it, MaterialTheme.extra.bad, maxLines = 3) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    val id = s.s("id")
                    if (s.b("configured") && id in setOf("internal", "claude_web", "ads_transparency"))
                        BusyButton("Test".t(), "src_$id" in g.busy, outlined = true) {
                            vm.act("run", mapOf("job" to "source_check", "source" to id), "src_$id", ok = "Test queued. See Runs.".t())
                        }
                    s.sn("docs")?.let { d -> TextButton(onClick = { openUrl(ctx, d) }) { Text("Docs".t()) } }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
private fun SettingsTab(g: GState, vm: StudioVm, reload: Int) {
    LaunchedEffect(reload) { vm.load("settings") }
    SLoad(g, g.view("settings"), { vm.load("settings") }) { o ->
        val s = o.obj("settings")
        val reloadS = { vm.load("settings") }
        if (!o.b("ai_key")) Panel { Small("Claude API key is not set: AI research cannot run. Add it in Dash Settings > AI.".t(), MaterialTheme.extra.bad) }

        SectionTitle("Schedule".t())
        Panel {
            listOf("st_daily" to "Daily discovery", "st_weekly" to "Weekly ranking + economics", "st_monthly" to "Monthly deep research", "st_push" to "Push notifications", "st_email" to "Email notifications").forEach { (k, l) ->
                SwitchLine(l.t(), null, s.s(k) == "1", "set_$k" in g.busy) { on -> vm.act("setting", mapOf("key" to k, "value" to if (on) "1" else "0"), "set_$k") { reloadS() } }
            }
            NumSetting(g, vm, s, "st_daily_hour", "Daily run hour (server time, 0-23)", reloadS)
            NumSetting(g, vm, s, "st_weekly_dow", "Weekly run day (1 = Monday … 7 = Sunday)", reloadS)
        }

        SectionTitle("Budget & limits".t(), "spent".t() + " " + usd(o.obj("spend").d("month_usd")))
        Panel {
            NumSetting(g, vm, s, "st_ai_budget_usd", "Monthly AI budget (USD)", reloadS)
            NumSetting(g, vm, s, "st_search_usd_per_1k", "Web search price per 1,000 searches (USD)", reloadS)
            NumSetting(g, vm, s, "st_max_projects", "Parallel projects limit", reloadS)
            NumSetting(g, vm, s, "st_min_cohort", "Minimum installs to judge economics", reloadS)
        }

        SectionTitle("Profit assumptions".t())
        Panel {
            Small("These are YOUR assumptions, shown with every profit estimate.".t())
            TextSetting(g, vm, s, "st_ltv_mult", "LTV multiplier on D7 revenue [conservative, base, optimistic]", reloadS)
            TextSetting(g, vm, s, "st_dev_cost", "Development cost per app by complexity (USD)", reloadS)
        }

        SectionTitle("Countries & tiers".t())
        CountriesEditor(g, vm, o, reloadS)

        SectionTitle("Categories".t())
        CategoriesEditor(g, vm, o, reloadS)

        SectionTitle("Score weights".t(), "0-50".t())
        WeightsEditor(g, vm, o, reloadS)

        SectionTitle("Your apps' categories".t())
        Panel {
            Small("Used to compare a new idea with your own apps in the same category (installs, retention, revenue, CPI).".t())
            o.arr("apps").objs().forEach { a -> AppCatRow(g, vm, a, reloadS) }
        }
    }
}

@Composable
private fun NumSetting(g: GState, vm: StudioVm, s: JSONObject, key: String, label: String, reload: () -> Unit) {
    var v by remember(s.s(key)) { mutableStateOf(s.s(key)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        OutlinedTextField(v, { v = it }, label = { Text(label.t()) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        BusyButton("Save".t(), "set_$key" in g.busy, enabled = v != s.s(key)) { vm.act("setting", mapOf("key" to key, "value" to v.trim()), "set_$key", ok = "Saved".t()) { reload() } }
    }
}

@Composable
private fun TextSetting(g: GState, vm: StudioVm, s: JSONObject, key: String, label: String, reload: () -> Unit) {
    var v by remember(s.s(key)) { mutableStateOf(s.s(key)) }
    Column(Modifier.padding(vertical = 3.dp)) {
        OutlinedTextField(v, { v = it }, label = { Text(label.t()) }, modifier = Modifier.fillMaxWidth())
        BusyButton("Save".t(), "set_$key" in g.busy, enabled = v != s.s(key)) { vm.act("setting", mapOf("key" to key, "value" to v.trim()), "set_$key", ok = "Saved".t()) { reload() } }
    }
}

private data class Cc(val cc: String, val name: String, val tier: Int, val on: Boolean)

@Composable
private fun CountriesEditor(g: GState, vm: StudioVm, o: JSONObject, reload: () -> Unit) {
    val orig = o.arr("countries").objs().map { Cc(it.s("cc"), it.s("name"), it.d("tier", 3.0).toInt(), it.b("on")) }
    var list by remember(orig) { mutableStateOf(orig) }
    var add by remember { mutableStateOf("") }
    Panel {
        Small("Research runs country by country for the countries switched on. Tier groups markets by value.".t())
        list.forEachIndexed { i, c ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Checkbox(c.on, { on -> list = list.toMutableList().also { it[i] = c.copy(on = on) } })
                Text(c.cc + " · " + c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                (1..3).forEach { t -> MChip("T$t", c.tier == t, { list = list.toMutableList().also { it[i] = c.copy(tier = t) } }) ; Spacer(Modifier.width(4.dp)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(add, { add = it.uppercase().take(2) }, label = { Text("Add country code (e.g. BR)".t()) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (add.matches(Regex("[A-Z]{2}")) && list.none { it.cc == add }) list = list + Cc(add, add, 3, true)
                add = ""
            }) { Text("Add".t()) }
        }
        BusyButton("Save countries".t(), "countries" in g.busy, enabled = list != orig) {
            val f = HashMap<String, String>()
            list.forEachIndexed { i, c -> f["list[$i][cc]"] = c.cc; f["list[$i][name]"] = c.name; f["list[$i][tier]"] = c.tier.toString(); if (c.on) f["list[$i][on]"] = "1" }
            vm.act("countries", f, "countries", ok = "Saved".t()) { reload() }
        }
    }
}

@Composable
private fun CategoriesEditor(g: GState, vm: StudioVm, o: JSONObject, reload: () -> Unit) {
    val orig = o.arr("categories").strings()
    var list by remember(orig) { mutableStateOf(orig) }
    var add by remember { mutableStateOf("") }
    Panel {
        list.chunked(3).forEach { row ->
            ChipRow { row.forEach { c -> MChip(c, true, { list = list - c }, trailing = "✕") } }
            Spacer(Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(add, { add = it }, label = { Text("Add category".t()) }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (add.isNotBlank() && add.trim() !in list) list = list + add.trim(); add = "" }) { Text("Add".t()) }
        }
        BusyButton("Save categories".t(), "categories" in g.busy, enabled = list != orig && list.isNotEmpty()) {
            val f = HashMap<String, String>()
            list.forEachIndexed { i, c -> f["list[$i]"] = c }
            vm.act("categories", f, "categories", ok = "Saved".t()) { reload() }
        }
    }
}

@Composable
private fun WeightsEditor(g: GState, vm: StudioVm, o: JSONObject, reload: () -> Unit) {
    val w = o.obj("weights")
    val factors = o.arr("factors").objs()
    val orig = factors.associate { it.s("key") to num(w.d(it.s("key")), 0) }
    var vals by remember(orig) { mutableStateOf(orig) }
    Panel {
        Small("Higher weight = the factor counts more. Changes apply from the next ranking run.".t())
        factors.forEach { f ->
            val k = f.s("key")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(f.s("label"), style = MaterialTheme.typography.bodyMedium)
                    Small(stText(f.s("engine")) + " · " + (if (f.s("higher_is") == "better") "higher is better".t() else "higher is worse".t()) + " · " + "default".t() + " " + num(f.d("default")))
                }
                OutlinedTextField(vals[k] ?: "", { v -> vals = vals + (k to v.filter { it.isDigit() || it == '.' }.take(4)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.width(76.dp))
            }
        }
        BusyButton("Save weights".t(), "weights" in g.busy, enabled = vals != orig) {
            val f = HashMap<String, String>()
            vals.forEach { (k, v) -> if (v.toDoubleOrNull() != null) f["weights[$k]"] = v }
            vm.act("weights", f, "weights", ok = "Saved. Applies to the next ranking.".t()) { reload() }
        }
    }
}

@Composable
private fun AppCatRow(g: GState, vm: StudioVm, a: JSONObject, reload: () -> Unit) {
    val pkg = a.s("pkg")
    var v by remember(a.s("category")) { mutableStateOf(a.s("category")) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Column(Modifier.weight(1f)) {
            Text(a.s("name"), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Small(pkg, maxLines = 1)
        }
        OutlinedTextField(v, { v = it }, singleLine = true, placeholder = { Text("Category".t()) }, modifier = Modifier.width(130.dp))
        if (v != a.s("category")) TextButton(onClick = { vm.act("app_category", mapOf("pkg" to pkg, "category" to v.trim()), "cat_$pkg", ok = "Saved".t()) { reload() } }) { Text("Save".t()) }
    }
}

// ---------------------------------------------------------------- own idea

@Composable
fun IdeaDialog(g: GState, vm: StudioVm, nav: NavHostController, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var concept by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    var ccs by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, title = { Text("Add your own idea".t()) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Small("Studio evaluates it critically with the same rules and evidence labels. It may recommend REJECT.".t())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(title, { title = it }, label = { Text("Title".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(concept, { concept = it }, label = { Text("What the app does".t()) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cat, { cat = it }, label = { Text("Category".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ccs, { ccs = it.uppercase() }, label = { Text("Countries (e.g. IN, US)".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        BusyButton("Evaluate".t(), "idea" in g.busy, enabled = title.trim().length >= 3) {
            val f = hashMapOf("title" to title.trim(), "concept" to concept.trim(), "category" to cat.trim())
            ccs.split(',', ' ').map { it.trim() }.filter { it.matches(Regex("[A-Z]{2}")) }.forEachIndexed { i, c -> f["countries[$i]"] = c }
            vm.act("idea", f, "idea", ok = "Idea queued for evaluation.".t()) { j -> onClose(); nav.navigate("studio/opp/" + j.optLong("id")) }
        }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel".t()) } })
}
