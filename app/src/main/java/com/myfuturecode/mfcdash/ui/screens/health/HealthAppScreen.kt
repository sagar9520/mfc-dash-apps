package com.myfuturecode.mfcdash.ui.screens.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*

/** Ek app ka poora haal: logo, console, installs/uninstalls/crashes (din-wise), issues (open/fixed/rejected), builds, scans, switches. */
@Composable
fun HealthAppScreen(vm: HealthVm, nav: NavHostController, pkg: String) {
    val h by vm.s.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(30) }
    var reload by remember { mutableIntStateOf(0) }
    Poll(30_000, pkg, days, reload) { vm.loadApp(pkg, days) }
    val d = h.app["$pkg|$days"] ?: h.app.entries.firstOrNull { it.key.startsWith("$pkg|") }?.value
    var task by remember { mutableStateOf(false) }
    var issueF by rememberSaveable { mutableStateOf("open") }
    Column(Modifier.fillMaxSize()) {
        HTopBar(d?.app?.name ?: pkg, d?.app?.consoleName, nav, busy = false, onRefresh = { reload++ })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            if (d == null) { if (h.error != null) HProblem(h) { vm.loadApp(pkg, days) } else Loading(); return@Column }
            val a = d.app
            val ex = MaterialTheme.extra
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.icon, a.name, 56.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        Text(a.consoleName.ifBlank { "Not linked to a console".t() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    StatusPill(a.status)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    KVs("Crash rate".t(), a.crashRate?.let { pct(it, 2) } ?: "–", Modifier.weight(1f))
                    KVs("ANR rate".t(), a.anrRate?.let { pct(it, 2) } ?: "–", Modifier.weight(1f))
                    KVs("Last scan".t(), a.lastScanAt?.let { ago(it) } ?: "–", Modifier.weight(1f))
                }
                if (a.vitalsDay != null) Text(tf("Play vitals up to %s (Play data is 1–2 days late)", fmtDay(a.vitalsDay)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BusyButton("Scan this app".t(), "scan$pkg" in h.busy, Modifier.weight(1f), outlined = true) { vm.scan(pkg) }
                    Button(onClick = { task = true }, modifier = Modifier.weight(1f)) { Text("New task".t()) }
                }
            }

            Spacer(Modifier.height(12.dp))
            ChipRow { listOf(7, 14, 30, 90).forEach { n -> MChip(tf("%s days", n), days == n, { days = n }) } }
            Spacer(Modifier.height(8.dp))
            TileGrid(listOf(
                Triple("Installs".t(), num(d.installs.toDouble()), null),
                Triple("Uninstalls".t(), num(d.uninstalls.toDouble()), d.uninstallRate?.let { pct(it, 0) + " " + "of installs".t() }),
                Triple("Crashes".t(), num(d.crashes.toDouble()), d.crashPer1k?.let { num(it, 2) + " / 1K sessions" }),
                Triple("DAU avg".t(), num(d.dauAvg.toDouble()), null),
                Triple("Revenue".t(), inr(d.revUsd * d.inrPerUsd), null),
                Triple("Open issues".t(), d.counts.optInt("open").toString(), null),
            ), colors = buildMap<Int, androidx.compose.ui.graphics.Color> { if (d.crashes > 0) put(2, ex.bad) })
            Panel {
                Text(tf("Last %s days", d.days), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                BarLineChart(d.series.map { fmtDay(it.day).substringBefore(' ') }, d.series.map { it.installs.toDouble() },
                    line = d.series.map { it.uninstalls.toDouble() }, line2 = d.series.map { it.crashes.toDouble() }, line2Color = ex.bad, lineColor = ex.warn)
                Legend("Installs".t() to MaterialTheme.colorScheme.primary, "Uninstalls".t() to ex.warn, "Crashes".t() to ex.bad)
            }
            if (d.versions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionTitle("Versions in use".t(), "last 3 days".t())
                Panel {
                    val t = d.versions.sumOf { it.second }.toDouble()
                    HBars(d.versions.map { Triple(it.first.ifBlank { "?" }, num(it.second.toDouble()) + " " + "users".t(), shareOf(it.second.toDouble(), t)) })
                }
            }

            // ---- issues
            Spacer(Modifier.height(12.dp))
            SectionTitle("Issues".t())
            val c = d.counts
            ChipRow {
                listOf("open" to "Open", "fixed" to "Fixed", "rejected" to "Rejected", "resolved" to "Resolved", "all" to "All").forEach { (k, l) ->
                    MChip(l.t(), issueF == k, { issueF = k }, trailing = if (k == "all") d.issues.size.toString() else c.optInt(k).toString())
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(listOf("Crash".t() to c.optInt("crash"), "ANR" to c.optInt("anr"), "Ad issue".t() to c.optInt("ad"), "Task".t() to c.optInt("task"))
                .joinToString(" · ") { "${it.first} ${it.second}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            val shown = d.issues.filter {
                when (issueF) {
                    "open" -> it.status !in listOf("FIXED", "READY_FOR_REVIEW", "REJECTED", "RESOLVED")
                    "fixed" -> it.status == "FIXED" || it.status == "READY_FOR_REVIEW"
                    "rejected" -> it.status == "REJECTED"; "resolved" -> it.status == "RESOLVED"; else -> true
                }
            }
            Panel {
                if (shown.isEmpty()) Empty("Nothing in this filter.".t())
                shown.forEachIndexed { i, it -> IssueRow(it, nav, showApp = false); if (i < shown.lastIndex) Hairline() }
            }
            Text("Rejected = issues you rejected here. Play policy rejections are not available from Google's API; see Play Console inbox.".t(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

            if (d.builds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionTitle("Builds".t())
                d.builds.take(5).forEach { BuildCard(it, h, vm, nav); Spacer(Modifier.height(8.dp)) }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Recent scans".t())
            Panel {
                if (d.runs.isEmpty()) Empty("No scans yet".t())
                d.runs.take(10).forEachIndexed { i, it -> RunRow(it, nav); if (i < minOf(9, d.runs.lastIndex)) Hairline() }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Automation".t())
            Panel {
                SwitchLine("24/7 monitoring".t(), a.monitoring) { vm.appSetting(a.pkg, "monitoring", it) }
                SwitchLine("Automatic safe fix attempts".t(), a.autofix, sub = if (a.autofix && !a.autofixEffective) "waiting: repo/signing not verified".t() else null) { vm.appSetting(a.pkg, "autofix", it) }
                SwitchLine("Email notifications".t(), a.email) { vm.appSetting(a.pkg, "email", it) }
                SwitchLine("Production auto-publish".t(), false, enabled = false, sub = "Always off: you approve every release".t()) { }
                Spacer(Modifier.height(4.dp))
                KV("Repo".t(), a.repo.ifBlank { "not linked".t() })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (a.repoVerified) "repo ✓" else "repo ✗") + "  " + (if (a.signingVerified) "signing ✓" else "signing ✗") + (a.verifyNote.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = if (a.repoVerified && a.signingVerified) ex.good else ex.warn, modifier = Modifier.weight(1f))
                    if (a.repo.isNotBlank()) BusyButton("Verify".t(), "verify${a.pkg}" in h.busy, outlined = true) { vm.verify(a.pkg) }
                }
            }
        }
    }
    if (task) TaskSheet(h, vm, nav, pkg) { task = false }
}

@Composable
private fun KVs(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** Ek scan run: status, progress aur uske saare log lines. */
@Composable
fun HealthRunScreen(vm: HealthVm, nav: NavHostController, id: Long) {
    val h by vm.s.collectAsStateWithLifecycle()
    val d = h.run[id]
    val active = d?.first?.active == true
    var reload by remember { mutableIntStateOf(0) }
    Poll(if (active || d == null) 4_000 else 60_000, id, active, reload) { vm.loadRun(id) }
    Column(Modifier.fillMaxSize()) {
        HTopBar(d?.first?.uid ?: "Run".t(), d?.first?.let { jobText(it.type) }, nav, busy = active, onRefresh = { reload++ })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            if (d == null) { if (h.error != null) HProblem(h) { vm.loadRun(id) } else Loading(); return@Column }
            val (r, ev) = d
            Panel {
                RunRow(r, nav)
                KV("Trigger".t(), r.trigger + (r.by.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                KV("Queued".t(), tFull(r.queuedAt)); KV("Started".t(), tFull(r.startedAt)); KV("Finished".t(), r.finishedAt?.let { tFull(it) } ?: "–")
                KV("Checks".t(), "${r.checksDone} / ${r.checksTotal}"); KV("Problems found".t(), r.problems.toString()); KV("Errors".t(), r.errors.toString())
                r.issueId?.let { TextButton(onClick = { nav.navigate("health/issue/$it") }) { Text("ISS-$it →") } }
            }
            Spacer(Modifier.height(12.dp))
            SectionTitle("Log".t(), tf("%s lines", ev.size))
            Panel { if (ev.isEmpty()) Empty("No log lines".t()); ev.forEach { EventRow(it, nav) } }
        }
    }
}
