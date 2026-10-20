package com.myfuturecode.mfcdash.ui.screens.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.*
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.Info
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*

/** Ek issue: kya hua, AI root cause, fix attempts (tests, diff), builds, aur actions. */
@Composable
fun HealthIssueScreen(vm: HealthVm, nav: NavHostController, id: Long) {
    val h by vm.s.collectAsStateWithLifecycle()
    val d = h.issue[id]
    val active = d != null && (d.investigating || d.base.status in listOf("INVESTIGATING", "FIX_IN_PROGRESS", "TESTING", "AAB_BUILDING"))
    var reload by remember { mutableIntStateOf(0) }
    Poll(if (active) 5_000 else 30_000, id, active, reload) { vm.loadIssue(id) }
    var reject by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        HTopBar("ISS-$id", d?.base?.app, nav, busy = active, onRefresh = { reload++ })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            if (d == null) { if (h.error != null) HProblem(h) { vm.loadIssue(id) } else Loading(); return@Column }
            val i = d.base
            val ex = MaterialTheme.extra
            Panel(onClick = { nav.navigate("health/app/${i.pkg}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(i.icon, i.app, 44.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(i.app, style = MaterialTheme.typography.titleMedium)
                        Text(d.consoleName.ifBlank { "no console".t() } + " · " + i.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill(i.severity, sevColor(i.severity)); Pill(typeText(i.type), Info); StatusPill(i.status) }
                Spacer(Modifier.height(6.dp))
                Text(i.title, style = MaterialTheme.typography.titleMedium)
                if (d.cause.isNotBlank()) Text(d.cause, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.location.isNotBlank()) Text(d.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            TileGrid(listOf(
                Triple("Users".t(), i.users?.let { num(it.toDouble()) } ?: "–", null),
                Triple("Events".t(), num(i.events.toDouble()), null),
                Triple("Version".t(), i.version.ifBlank { "–" }, null),
                Triple("First seen".t(), tFull(i.firstSeen), null),
                Triple("Last seen".t(), ago(i.lastSeen), null),
                Triple("Source".t(), i.source.ifBlank { "–" }, null),
            ))

            // ---- actions
            SectionTitle("Actions".t())
            Panel {
                val open = i.status !in listOf("FIXED", "REJECTED", "RESOLVED")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BusyButton(if (d.investigating) "AI investigating…".t() else "Investigate with AI".t(), "investigate$id" in h.busy || d.investigating, Modifier.weight(1f), outlined = true) {
                        vm.issueAction(id, "investigate", ok = "AI investigation started".t())
                    }
                    BusyButton("Auto-fix".t(), "autofix$id" in h.busy, Modifier.weight(1f), enabled = open && d.autofixBlock == null) {
                        vm.issueAction(id, "autofix", ok = "AI fix started: tests + build run on GitHub".t())
                    }
                }
                d.autofixBlock?.let { Text("Auto-fix not available: ".t() + it, style = MaterialTheme.typography.bodySmall, color = ex.warn, modifier = Modifier.padding(top = 4.dp)) }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (open) {
                        OutlinedButton(onClick = { reject = true }, modifier = Modifier.weight(1f)) { Text("Reject".t()) }
                        BusyButton("Mark fixed".t(), "fixed$id" in h.busy, Modifier.weight(1f), outlined = true) { vm.issueAction(id, "fixed", ok = "Marked fixed".t()) }
                    } else BusyButton("Reopen".t(), "reopen$id" in h.busy, Modifier.weight(1f), outlined = true) { vm.issueAction(id, "reopen", ok = "Reopened".t()) }
                }
                if (i.status == "REJECTED" && d.rejectReason.isNotBlank()) Text("Rejected: ".t() + d.rejectReason, style = MaterialTheme.typography.bodySmall, color = ex.warn)
            }

            // ---- AI root cause
            Spacer(Modifier.height(12.dp))
            SectionTitle("AI root cause".t(), d.aiAt?.let { ago(it) })
            Panel {
                if (d.aiAnalysis.isBlank()) Empty(if (d.investigating) "AI is reading the stack trace and code…".t() else "Not investigated yet.".t())
                else {
                    i.aiConfidence?.let { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Confidence".t(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Text(pct(c, 0), style = MaterialTheme.typography.titleSmall, color = if (c >= .6) ex.good else ex.warn)
                        }
                        LinearProgressIndicator(progress = { c.toFloat() }, Modifier.fillMaxWidth().padding(vertical = 4.dp), color = if (c >= .6) ex.good else ex.warn)
                    }
                    Text(d.aiAnalysis, style = MaterialTheme.typography.bodyMedium)
                    if (d.aiFix.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("SUGGESTED FIX".t(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(d.aiFix, style = MaterialTheme.typography.bodySmall)
                    }
                    if (d.aiMissing.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("MISSING EVIDENCE".t(), style = MaterialTheme.typography.labelSmall, color = ex.warn)
                        d.aiMissing.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    d.aiAutofixOk?.let { Text(if (it) "AI says: safe to auto-fix".t() else "AI says: needs a human".t(), style = MaterialTheme.typography.bodySmall, color = if (it) ex.good else ex.warn, modifier = Modifier.padding(top = 6.dp)) }
                }
            }

            // ---- devices / stack
            if (d.devices.isNotEmpty() || d.osVersions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionTitle("Affected devices".t())
                Panel {
                    val tot = d.devices.sumOf { it.n }.toDouble()
                    HBars(d.devices.take(6).map { Triple(it.k, num(it.n.toDouble()), shareOf(it.n.toDouble(), tot)) })
                    if (d.osVersions.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        val t2 = d.osVersions.sumOf { it.n }.toDouble()
                        HBars(d.osVersions.take(6).map { Triple("Android " + it.k, num(it.n.toDouble()), shareOf(it.n.toDouble(), t2)) }, MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (d.stack.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                var full by remember { mutableStateOf(false) }
                SectionTitle("Stack trace".t(), if (full) "tap to fold".t() else "tap to expand".t())
                Box(Modifier.clickable { full = !full }) { Mono(d.stack, if (full) Int.MAX_VALUE else 8) }
            }

            // ---- fix attempts
            if (d.fixes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionTitle("AI fix attempts".t(), tf("%s attempts", d.fixes.size))
                d.fixes.forEach { FixCard(it); Spacer(Modifier.height(8.dp)) }
            }
            if (d.builds.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SectionTitle("Builds".t())
                d.builds.forEach { BuildCard(it, h, vm, null); Spacer(Modifier.height(8.dp)) }
            }
            if (d.log.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SectionTitle("Timeline".t())
                Panel { d.log.forEach { EventRow(it, null) } }
            }
        }
    }
    if (reject) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { reject = false }, title = { Text("Reject this issue?".t()) },
            text = { OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("Reason (optional)".t()) }) },
            confirmButton = { TextButton(onClick = { reject = false; vm.issueAction(id, "reject", mapOf("reason" to reason.trim()), ok = "Rejected".t()) }) { Text("Reject".t()) } },
            dismissButton = { TextButton(onClick = { reject = false }) { Text("Cancel".t()) } })
    }
}

@Composable
private fun FixCard(f: HFix) {
    val ctx = LocalContext.current
    val ex = MaterialTheme.extra
    var diff by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tf("Attempt %s", f.attempt), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusPill(when (f.success) { true -> "SUCCESS"; false -> "FAILED"; null -> "RUNNING" }, statusText(f.status.ifBlank { "running" }))
        }
        Text(tFull(f.startedAt) + (f.finishedAt?.let { " → " + tOnly(it) } ?: "") + (f.branch.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { TestMark("Unit".t(), f.unitOk); TestMark("Build".t(), f.buildOk); TestMark("Emulator".t(), f.deviceOk) }
        if (f.failureReason.isNotBlank()) Text(f.failureReason, style = MaterialTheme.typography.bodySmall, color = ex.bad, modifier = Modifier.padding(top = 4.dp))
        if (f.explanation.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(f.explanation, style = MaterialTheme.typography.bodySmall) }
        if (f.steps.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            f.steps.forEach { s ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(tOnly(s.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(42.dp))
                    Text(s.step.replace('_', ' '), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = statusColor(s.status), modifier = Modifier.width(92.dp))
                    Text(s.detail, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                }
            }
        }
        if (f.filesInspected.isNotEmpty()) KV("Files inspected".t(), f.filesInspected.size.toString())
        if (f.filesModified.isNotEmpty()) {
            Text("FILES MODIFIED".t(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            f.filesModified.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
        Row {
            if (f.diff.isNotBlank()) TextButton(onClick = { diff = !diff }) { Text(if (diff) "Hide diff".t() else "Show diff".t()) }
            if (f.ghRunUrl.isNotBlank()) TextButton(onClick = { openUrl(ctx, f.ghRunUrl) }) { Text("GitHub run ↗".t()) }
        }
        if (diff) Mono(f.diff)
    }
}
