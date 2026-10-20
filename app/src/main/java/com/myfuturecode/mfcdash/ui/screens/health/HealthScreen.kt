package com.myfuturecode.mfcdash.ui.screens.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.*
import com.myfuturecode.mfcdash.ui.HState
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.screens.*
import com.myfuturecode.mfcdash.ui.theme.Info
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

val HTABS = listOf(
    "overview" to "Overview", "consoles" to "Consoles & Apps", "live" to "Live Activity", "issues" to "Issues",
    "fixes" to "AI Fix History", "builds" to "AAB Releases", "logs" to "System Logs", "settings" to "Settings",
)

/** AI App Health & Auto Fix: 8 tabs (chips upar), har tab apna data khud laata hai. */
@Composable
fun HealthScreen(vm: HealthVm, nav: NavHostController, startTab: String) {
    val h by vm.s.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (HTABS.any { it.first == startTab }) startTab else "overview") }
    var task by remember { mutableStateOf(false) }
    val box = remember(tab) { FilterBox() }
    val reload = remember(tab) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        HTopBar("AI App Health".t(), updatedText(h), nav, busy = false, onRefresh = { reload.intValue++ }) {
            IconButton(onClick = { task = true }) { Icon(Icons.Filled.Add, "New task".t()) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            ChipRow {
                HTABS.forEach { (k, l) ->
                    val badge = when (k) {
                        "issues" -> h.overview?.issuesOpen?.takeIf { it > 0 }?.toString()
                        "builds" -> h.overview?.aabReady?.takeIf { it > 0 }?.toString()
                        "live" -> h.overview?.runsActive?.takeIf { it > 0 }?.toString()
                        else -> null
                    }
                    MChip(l.t(), tab == k, { tab = k }, trailing = badge)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (box.rows.isNotEmpty()) { ChipRow { FilterToggle(box) }; Spacer(Modifier.height(8.dp)) }
            FilterSummary(box)
            CompositionLocalProvider(LocalFilterBox provides box) {
                when (tab) {
                    "overview" -> OverviewTab(h, vm, nav, reload.intValue) { tab = it }
                    "consoles" -> ConsolesTab(h, vm, nav, reload.intValue)
                    "live" -> LiveTab(h, vm, nav, reload.intValue)
                    "issues" -> IssuesTab(h, vm, nav, reload.intValue)
                    "fixes" -> FixesTab(h, vm, nav, reload.intValue)
                    "builds" -> BuildsTab(h, vm, nav, reload.intValue)
                    "logs" -> LogsTab(h, vm, nav, reload.intValue)
                    "settings" -> HSettingsTab(h, vm, reload.intValue)
                }
            }
        }
    }
    FilterSheet(box)
    if (task) TaskSheet(h, vm, nav, null) { task = false }
}

// ---------------------------------------------------------------- 1. Overview

@Composable
private fun OverviewTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int, go: (String) -> Unit) {
    Poll(30_000, reload) { vm.loadOverview() }
    val o = h.overview ?: run { if (h.error != null) HProblem(h) { vm.loadOverview() } else Loading(); return }
    val ex = MaterialTheme.extra
    if (!o.cronOk || !o.playConfigured) {
        Panel {
            Text("Setup pending".t(), style = MaterialTheme.typography.titleSmall, color = ex.warn)
            if (!o.cronOk) Text("• " + "Cron is not running: scheduled scans will not happen. Add the cron from the setup guide.".t(), style = MaterialTheme.typography.bodySmall)
            if (!o.playConfigured) Text("• " + "No Play Console service account yet: Play crashes/ANR/vitals cannot be read.".t(), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
    }
    GradientHeader("PORTFOLIO HEALTH".t(), tf("%s consoles · %s apps · %s monitored", o.consoles, o.apps, o.appsMonitored)) {
        Spacer(Modifier.height(8.dp))
        Row {
            listOf("Healthy".t() to o.healthy, "Problem".t() to o.withProblems, "Open issues".t() to o.issuesOpen, "P0" to o.issuesP0).forEach { (l, v) ->
                Column(Modifier.weight(1f)) {
                    Text(l.uppercase(), style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .75f))
                    Text(num(v.toDouble()), style = MaterialTheme.typography.headlineMedium, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    TileGrid(listOf(
        Triple("Consoles".t(), "${o.consolesOk}/${o.consoles}", "connected".t()),
        Triple("Investigating".t(), num(o.investigating.toDouble()), "AI".t()),
        Triple("Fixing".t(), num(o.fixing.toDouble()), "AI fix running".t()),
        Triple("Needs you".t(), num(o.needsHuman.toDouble()), "human review".t()),
        Triple("Fixes ✓".t(), num(o.fixesOk30.toDouble()), "verified · 30 days".t()),
        Triple("Fixes ✗".t(), num(o.fixesFailed30.toDouble()), "failed · 30 days".t()),
        Triple("AAB ready".t(), num(o.aabReady.toDouble()), "for your review".t()),
        Triple("Scans 24h".t(), num(o.runsTotal24.toDouble()), tf("%s clean · %s failed", o.runsClean24, o.runsFailed24)),
        Triple("Running".t(), num(o.runsActive.toDouble()), "now".t()),
    ), colors = buildMap<Int, androidx.compose.ui.graphics.Color> {
        if (o.needsHuman > 0) put(3, ex.warn); put(4, ex.good); if (o.fixesFailed30 > 0) put(5, ex.bad); if (o.aabReady > 0) put(6, Info)
    })

    SectionTitle("Scan".t(), o.nextScanAt?.let { "next".t() + ": " + jobText(o.nextScanType) + " " + tOnly(it) })
    Panel {
        val lc = o.lastCycle
        if (lc != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last cycle".t(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                StatusPill(lc.status)
            }
            Text(jobText(lc.type) + " · " + lc.uid + " · " + tFull(lc.finishedAt ?: lc.queuedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        } else Empty("No scan has run yet".t())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BusyButton("Run Scan Now".t(), "scan" in h.busy, Modifier.weight(1f)) { vm.scan() }
            OutlinedButton(onClick = { go("live") }, modifier = Modifier.weight(1f)) { Text("Live Activity".t()) }
        }
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("Needs attention".t(), if (o.attention.isEmpty()) "all good".t() else null)
    Panel {
        if (o.attention.isEmpty()) Text("No open P0/P1 issue.".t(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        o.attention.forEachIndexed { i, it -> IssueRow(it, nav); if (i < o.attention.lastIndex) Hairline() }
        if (o.issuesOpen > o.attention.size) TextButton(onClick = { go("issues") }) { Text(tf("See all %s open issues", o.issuesOpen)) }
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("Integration health".t())
    Panel {
        o.integrations.forEachIndexed { i, it ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(it.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (it.detail.isNotBlank()) Text(it.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(it.status)
            }
            if (i < o.integrations.lastIndex) Hairline()
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(tf("Times in your phone's time zone · server zone %s", o.tz), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------- 2. Consoles & Apps

@Composable
private fun ConsolesTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int) {
    Poll(60_000, reload) { vm.loadConsoles() }
    var q by rememberSaveable { mutableStateOf("") }
    var stF by rememberSaveable { mutableStateOf("") }
    FilterChips("Status".t(), listOf("" to "All".t(), "PROBLEM" to "Problem".t(), "WATCH" to "Watch".t(), "HEALTHY" to "Healthy".t(), "issues" to "With open issues".t(), "off" to "Monitoring off".t()), stF) { stF = it }
    val d = h.consoles ?: run { if (h.error != null) HProblem(h) { vm.loadConsoles() } else Loading(); return }
    SearchField(q, "Search app or package".t()) { q = it }
    Spacer(Modifier.height(10.dp))
    fun match(a: HApp) = (q.isBlank() || a.name.contains(q, true) || a.pkg.contains(q, true)) && when (stF) {
        "" -> true; "issues" -> a.openIssues > 0; "off" -> !a.monitoring; else -> a.status == stF
    }
    val groups = d.consoles.map { c -> c to d.apps.filter { it.consoleId == c.id } } +
        listOf<Pair<HConsole?, List<HApp>>>(null to d.apps.filter { a -> d.consoles.none { it.id == a.consoleId } })
    groups.forEach { (c, apps) ->
        if (c == null && apps.isEmpty()) return@forEach
        val shown = apps.filter(::match)
        Panel {
            if (c != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text(tf("%s apps", c.apps) + " · " + "synced".t() + " " + ago(c.lastSyncAt) + (if (!c.saSet) " · " + "service account missing".t() else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(c.status)
                }
                if (c.lastError.isNotBlank()) Text(c.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extra.bad, maxLines = 3, overflow = TextOverflow.Ellipsis)
                c.warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extra.warn) }
            } else {
                Text("Not linked to a console".t(), style = MaterialTheme.typography.titleMedium)
                Text("Add these to app_console in health_config.php (or give the console's service account access).".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            if (shown.isEmpty()) Empty(if (apps.isEmpty()) "No apps found in this console yet".t() else "Nothing in this filter.".t())
            shown.forEachIndexed { i, a -> AppRow(a, nav); if (i < shown.lastIndex) Hairline() }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun SearchField(v: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(v, onChange, placeholder = { Text(hint) }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CardShape)
}

// ---------------------------------------------------------------- 3. Live Activity

@Composable
private fun LiveTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int) {
    Poll(5_000, reload) { vm.loadRuns() }
    var type by rememberSaveable { mutableStateOf("manual_full") }
    val r = h.runs
    Panel {
        Text("Run a scan".t(), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        ChipRow {
            listOf("manual_full", "critical", "play_errors", "vitals", "ads", "discovery").forEach { k -> MChip(jobText(k), type == k, { type = k }) }
        }
        Spacer(Modifier.height(8.dp))
        BusyButton("Run Scan Now".t(), "scan" in h.busy, Modifier.fillMaxWidth()) { vm.scan(type = type) }
    }
    Spacer(Modifier.height(10.dp))
    if (r == null) { if (h.error != null) HProblem(h) { vm.loadRuns() } else Loading(); return }
    TileGrid(listOf(
        Triple("Running".t(), num(r.running.toDouble()), null), Triple("Queued".t(), num(r.queued.toDouble()), null),
        Triple("Next scan".t(), r.nextScanAt?.let { tOnly(it) } ?: "–", null),
        Triple("Done 24h".t(), num(r.done24.toDouble()), null), Triple("Failed 24h".t(), num(r.failed24.toDouble()), null),
    ), colors = if (r.failed24 > 0) mapOf(4 to MaterialTheme.extra.bad) else emptyMap())
    Text("Scan status (did the check run?) is separate from issue status (did it find a problem?).".t(), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    SectionTitle("Runs".t(), "auto 5 sec".t())
    Panel {
        if (r.runs.isEmpty()) Empty("No runs yet".t())
        r.runs.forEachIndexed { i, it -> RunRow(it, nav); if (i < r.runs.lastIndex) Hairline() }
    }
    Spacer(Modifier.height(12.dp))
    SectionTitle("Activity feed".t())
    Panel {
        if (r.feed.isEmpty()) Empty("Nothing yet".t())
        r.feed.forEach { EventRow(it, nav) }
    }
}

// ---------------------------------------------------------------- 4. Issues

private fun dayAgo(n: Int): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) }.time)

@Composable
private fun IssuesTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int) {
    val ctx = LocalContext.current
    var status by rememberSaveable { mutableStateOf("open") }
    var console by rememberSaveable { mutableStateOf("") }
    var pkg by rememberSaveable { mutableStateOf("") }
    var sev by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { if (h.consoles == null) vm.loadConsoles() }
    val f = mapOf("status" to status, "console" to console, "pkg" to pkg, "severity" to sev, "type" to type, "q" to q.trim(),
        "from" to if (period.isBlank()) "" else dayAgo(period.toInt() - 1))
    Poll(20_000, f, reload) { vm.loadIssues(f) }

    val cons = h.consoles
    if (cons != null && cons.consoles.size > 1) FilterChips("Console".t(), listOf("" to "All".t()) + cons.consoles.map { it.id to it.name }, console) { console = it; pkg = "" }
    if (cons != null) FilterChips("App".t(), listOf("" to "All apps".t()) + cons.apps.filter { console.isBlank() || it.consoleId == console }.map { it.pkg to it.name }, pkg) { pkg = it }
    FilterChips("Severity".t(), listOf("" to "All".t(), "P0" to "P0", "P1" to "P1", "P2" to "P2", "P3" to "P3"), sev) { sev = it }
    FilterChips("Type".t(), listOf("" to "All".t(), "CRASH" to "Crash".t(), "ANR" to "ANR", "VITALS" to "Vitals", "AD" to "Ad issue".t(), "TASK" to "Task".t()), type) { type = it }
    FilterChips("Period".t(), listOf("" to "All time".t(), "1" to "Today".t(), "7" to "7 days".t(), "30" to "30 days".t(), "90" to "90 days".t()), period) { period = it }

    val d = h.issues
    ChipRow {
        listOf("open" to ("Open".t() to d?.open), "fixed" to ("Fixed".t() to d?.fixed), "rejected" to ("Rejected".t() to d?.rejected),
            "resolved" to ("Resolved".t() to d?.resolved), "all" to ("All".t() to d?.total)).forEach { (k, p) ->
            MChip(p.first, status == k, { status = k }, trailing = p.second?.toString())
        }
    }
    Spacer(Modifier.height(8.dp))
    SearchField(q, "Search title, cause, ISS-id".t()) { q = it }
    Spacer(Modifier.height(8.dp))
    if (d == null) { if (h.error != null) HProblem(h) { vm.loadIssues(f) } else Loading(); return }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tf("%s issues", d.issues.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        BusyButton("CSV ⤓", "export" in h.busy, outlined = true) { vm.export(f + ("what" to "issues")) { openUrl(ctx, it) } }
    }
    Spacer(Modifier.height(6.dp))
    Panel {
        if (d.issues.isEmpty()) Empty(if (status == "open") "No open issue. 🎉".t() else "Nothing in this filter.".t())
        d.issues.forEachIndexed { i, it -> IssueRow(it, nav); if (i < d.issues.lastIndex) Hairline() }
    }
}

// ---------------------------------------------------------------- 5. AI Fix History

@Composable
private fun FixesTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int) {
    var res by rememberSaveable { mutableStateOf("") }
    Poll(15_000, res, reload) { vm.loadFixes(res) }
    FilterChips("Result".t(), listOf("" to "All".t(), "ok" to "Verified".t(), "fail" to "Failed".t(), "running" to "Running".t()), res) { res = it }
    val list = h.fixes?.takeIf { h.fixesKey == res } ?: run { if (h.error != null) HProblem(h) { vm.loadFixes(res) } else Loading(); return }
    Text("A fix counts as successful only after unit tests, release build and emulator test all pass.".t(), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    if (list.isEmpty()) Panel { Empty("No AI fix attempts yet".t()) }
    list.forEach { f ->
        Panel(onClick = { nav.navigate("health/issue/${f.issueId}") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.app.ifBlank { f.pkg }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(when (f.success) { true -> "SUCCESS"; false -> "FAILED"; null -> "RUNNING" }, f.status.ifBlank { "running" }.let(::statusText))
            }
            Text("ISS-${f.issueId} · " + tf("attempt %s", f.attempt) + " · " + tFull(f.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(f.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TestMark("Unit".t(), f.unitOk); TestMark("Build".t(), f.buildOk); TestMark("Emulator".t(), f.deviceOk)
            }
            if (f.filesModified.isNotEmpty()) Text(tf("%s files changed", f.filesModified.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (f.failureReason.isNotBlank()) Text(f.failureReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extra.bad, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- 6. AAB Releases

@Composable
private fun BuildsTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int) {
    var show by rememberSaveable { mutableStateOf("") }
    Poll(30_000, reload) { vm.loadBuilds() }
    FilterChips("Show".t(), listOf("" to "All".t(), "review" to "Waiting for you".t(), "PUBLISHED" to "Published".t(), "DISCARDED" to "Discarded".t()), show) { show = it }
    val list = h.builds ?: run { if (h.error != null) HProblem(h) { vm.loadBuilds() } else Loading(); return }
    Panel {
        Text("Signed, tested builds. Test APK = install on your phone to check. AAB = what goes to Play.".t(), style = MaterialTheme.typography.bodySmall)
        Text("Play never gets anything automatically: only your Approve & Live tap sends it.".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extra.good)
    }
    Spacer(Modifier.height(10.dp))
    val shown = list.filter { when (show) { "" -> true; "review" -> it.reviewable; else -> it.status == show } }
    if (shown.isEmpty()) Panel { Empty("No builds yet. They appear here after an AI fix or task passes all tests.".t()) }
    shown.forEach { BuildCard(it, h, vm, nav); Spacer(Modifier.height(10.dp)) }
}

// ---------------------------------------------------------------- 7. System Logs

@Composable
private fun LogsTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int) {
    var level by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    Poll(15_000, level, q, reload) { vm.loadLogs(level, q.trim()) }
    FilterChips("Level".t(), listOf("" to "All".t(), "error" to "Error".t(), "warn" to "Warning".t(), "ok" to "OK", "info" to "Info".t()), level) { level = it }
    SearchField(q, "Search logs".t()) { q = it }
    Spacer(Modifier.height(8.dp))
    val list = h.logs?.takeIf { h.logsKey == "$level|${q.trim()}" } ?: run { if (h.error != null) HProblem(h) { vm.loadLogs(level, q) } else Loading(); return }
    Panel {
        if (list.isEmpty()) Empty("No log lines".t())
        list.forEach { e ->
            Column {
                EventRow(e, nav)
                if (e.pkg.isNotBlank()) Text(e.pkg + " · " + tFull(e.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 15.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- 8. Settings

@Composable
private fun HSettingsTab(h: HState, vm: HealthVm, reload: Int) {
    Poll(60_000, reload) { vm.loadSettings() }
    val s = h.settings ?: run { if (h.error != null) HProblem(h) { vm.loadSettings() } else Loading(); return }
    val ex = MaterialTheme.extra
    SectionTitle("Per app".t(), tf("%s apps", s.apps.size))
    Text("Auto-fix works only after the app's repo and signing are verified. Production auto-publish is always OFF.".t(), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    s.apps.forEach { a ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(a.icon, a.name, 32.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(a.repo.ifBlank { "no repo linked".t() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SwitchLine("24/7 monitoring".t(), a.monitoring) { vm.appSetting(a.pkg, "monitoring", it) }
            SwitchLine("Automatic safe fix attempts".t(), a.autofix, sub = if (a.autofix && !a.autofixEffective) "waiting: repo/signing not verified".t() else null) { vm.appSetting(a.pkg, "autofix", it) }
            SwitchLine("Email notifications".t(), a.email) { vm.appSetting(a.pkg, "email", it) }
            SwitchLine("Production auto-publish".t(), false, enabled = false, sub = "Always off: you approve every release".t()) { }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (a.repoVerified) "repo ✓" else "repo ✗") + "  " + (if (a.signingVerified) "signing ✓" else "signing ✗") +
                    (a.verifyNote.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = if (a.repoVerified && a.signingVerified) ex.good else ex.warn, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (a.repo.isNotBlank()) BusyButton("Verify".t(), "verify${a.pkg}" in h.busy, outlined = true) { vm.verify(a.pkg) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(4.dp))
    SectionTitle("Email".t(), if (s.emailConfigured) s.emailHint else "not set".t())
    Panel {
        if (!s.emailConfigured) Text("Set email_to (and SMTP) in health_config.php.".t(), style = MaterialTheme.typography.bodySmall, color = ex.warn)
        s.mail.forEach { m -> SwitchLine(m.label.t(), m.on, enabled = s.emailConfigured) { vm.mailSetting(m.key, it) } }
        Spacer(Modifier.height(4.dp))
        BusyButton("Send test email".t(), "test_email" in h.busy, outlined = true, enabled = s.emailConfigured) { vm.testEmail() }
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("Scheduled monitoring".t(), if (s.cronOk) "cron OK".t() else "cron not running".t())
    Panel { s.schedule.forEach { (l, e) -> KV(l.t(), e.t()) } }

    Spacer(Modifier.height(12.dp))
    SectionTitle("Integrations".t())
    Panel {
        KV("Claude AI", if (s.aiEnabled) s.aiModel else "off — add key in Settings".t(), if (s.aiEnabled) ex.good else ex.warn)
        KV("GitHub (AI fix worker)", if (s.github) "OK" else "token not set".t(), if (s.github) ex.good else ex.warn)
        s.consoles.forEach { c -> KV(c.name, statusText(c.status), statusColor(c.status)) }
    }
}

@Composable
fun SwitchLine(label: String, on: Boolean, enabled: Boolean = true, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange, enabled = enabled)
    }
}
