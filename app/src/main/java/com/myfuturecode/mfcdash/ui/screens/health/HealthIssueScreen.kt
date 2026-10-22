package com.myfuturecode.mfcdash.ui.screens.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.HFix
import com.myfuturecode.mfcdash.data.HIssueDetail
import com.myfuturecode.mfcdash.ui.HState
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.components.AppIcon
import com.myfuturecode.mfcdash.ui.components.HBars
import com.myfuturecode.mfcdash.ui.components.shareOf
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KKv
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KProgress
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSectionLink
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tOnly
import com.myfuturecode.mfcdash.util.tf

private val ACTIVE = listOf("INVESTIGATING", "FIX_IN_PROGRESS", "TESTING", "AAB_BUILDING")

/** Ek issue: kya hua, AI root cause, fix attempts (tests, diff), builds, aur actions. */
@Composable
fun HealthIssueScreen(vm: HealthVm, nav: NavHostController, id: Long) {
    val h by vm.s.collectAsStateWithLifecycle()
    val d = h.issue[id]
    val active = d != null && (d.investigating || d.base.status in ACTIVE)
    var reload by remember { mutableIntStateOf(0) }
    Poll(if (active) 5_000 else 30_000, id, active, reload) { vm.loadIssue(id) }
    var reject by remember { mutableStateOf(false) }
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") }
    val bar: @Composable ColumnScope.() -> Unit = {
        KDetailBar(
            "ISS-$id", back, sub = d?.base?.app?.ifBlank { null } ?: d?.base?.pkg,
            actions = { KBarIcon(KIcons.Refresh, "Refresh".t(), { reload++ }, spinning = active) },
        )
    }
    if (d == null) {
        KScreen(top = bar) { if (h.error != null) HProblem(h) { vm.loadIssue(id) } else Loading() }
    } else if (isWide()) {
        // Fold: baayein hero + app + actions + AI, daayein devices / stack / fixes / builds / timeline
        KScreen(top = bar) {
            KTwoPane(
                left = {
                    IssueHero(d)
                    IssueMain(d, h, vm, nav, id) { reject = true }
                },
                right = { IssueMore(d, h, vm) },
            )
        }
    } else {
        KScreen(top = { bar(); IssueHero(d) }) {
            IssueMain(d, h, vm, nav, id) { reject = true }
            IssueMore(d, h, vm)
        }
    }
    if (reject) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reject = false },
            title = { Text("Reject this issue?".t()) },
            text = { OutlinedTextField(reason, { reason = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Reason (optional)".t()) }) },
            confirmButton = {
                TextButton(onClick = {
                    reject = false
                    vm.issueAction(id, "reject", mapOf("reason" to reason.trim()), ok = "Rejected".t())
                }) { Text("Reject".t()) }
            },
            dismissButton = { TextButton(onClick = { reject = false }) { Text("Cancel".t()) } },
        )
    }
}

/** Rose hero: P0 · Crash + status pill, kitne users (bada), title, events / version / aakhri baar. */
@Composable
private fun IssueHero(d: HIssueDetail) {
    val i = d.base
    KHero(KTint.Rose) {
        val c = Mfc.c
        val head = listOf(i.severity, typeText(i.type)).filter { it.isNotBlank() }.joinToString(" · ")
        KHeroHead(head, icon = hTypeIcon(i.type), trailing = { StatusPill(i.status) })
        val u = i.users
        KBigNum(
            num((u ?: i.events).toDouble()), if (hSevKind(i.severity) == KKind.BAD) c.red else c.ink,
            suffix = " " + (if (u != null) "users".t() else "events".t()), size = 48.sp,
        )
        Text(
            i.title, Modifier.padding(top = 2.dp), color = c.ink, style = hStyle(16.sp, FontWeight.SemiBold, 22.sp),
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        KSplit(
            listOf(
                "Events".t() to num(i.events.toDouble()),
                "Version".t() to i.version.ifBlank { "–" },
                "Last seen".t() to ago(i.lastSeen),
            ),
        )
    }
}

/** App, detail, actions, AI root cause. */
@Composable
private fun IssueMain(d: HIssueDetail, h: HState, vm: HealthVm, nav: NavHostController, id: Long, onReject: () -> Unit) {
    val c = Mfc.c
    val i = d.base
    val app = i.app.ifBlank { i.pkg }
    KCard(list = true) {
        KRow(
            app, sub = d.consoleName.ifBlank { "no console".t() } + " · " + i.pkg, lead = { AppIcon(i.icon, app, 40.dp) },
            chevron = true, onClick = { nav.navigate("health/app/${i.pkg}") },
        )
    }
    KCard {
        KKv("First seen".t(), tFull(i.firstSeen))
        KKv("Last seen".t(), tFull(i.lastSeen))
        KKv("Source".t(), i.source.ifBlank { "–" })
        if (i.attempts > 0) KKv("AI fix attempts".t(), i.attempts.toString())
        if (d.cause.isNotBlank()) {
            HLabel("Cause".t())
            Text(d.cause, Modifier.padding(top = 2.dp), color = c.ink, style = hStyle(14.sp, lh = 20.sp))
        }
        if (d.location.isNotBlank()) {
            HLabel("Location".t())
            Text(d.location, Modifier.padding(top = 2.dp), color = c.ink2, style = hStyle(14.sp, lh = 20.sp))
        }
    }

    // ---- actions
    KSection("Actions".t())
    KCard {
        val open = i.status !in listOf("FIXED", "REJECTED", "RESOLVED")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton(
                if (d.investigating) "AI investigating…".t() else "Investigate".t(),
                { vm.issueAction(id, "investigate", ok = "AI investigation started".t()) },
                Modifier.weight(1f), kind = KBtn.SOFT, busy = "investigate$id" in h.busy || d.investigating,
            )
            KButton(
                "Auto-fix".t(), { vm.issueAction(id, "autofix", ok = "AI fix started: tests + build run on GitHub".t()) },
                Modifier.weight(1f), icon = KIcons.Wand, enabled = open && d.autofixBlock == null, busy = "autofix$id" in h.busy,
            )
        }
        d.autofixBlock?.let { HNote("Auto-fix not available: ".t() + it, KKind.WARN) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (open) {
                KButton("Reject".t(), onReject, Modifier.weight(1f), kind = KBtn.OUTLINE, busy = "reject$id" in h.busy)
                KButton(
                    "Mark fixed".t(), { vm.issueAction(id, "fixed", ok = "Marked fixed".t()) }, Modifier.weight(1f), kind = KBtn.OUTLINE,
                    icon = KIcons.Check, busy = "fixed$id" in h.busy,
                )
            } else {
                KButton(
                    "Reopen".t(), { vm.issueAction(id, "reopen", ok = "Reopened".t()) }, Modifier.weight(1f), kind = KBtn.OUTLINE,
                    icon = KIcons.Refresh, busy = "reopen$id" in h.busy,
                )
            }
        }
        if (i.status == "REJECTED" && d.rejectReason.isNotBlank()) HNote("Rejected: ".t() + d.rejectReason, KKind.WARN)
    }

    // ---- AI root cause
    KSection("AI root cause".t(), trailing = { d.aiAt?.let { HSecNote(ago(it)) } })
    KCard {
        if (d.aiAnalysis.isBlank()) {
            KEmpty(if (d.investigating) "AI reading…".t() else "Not investigated".t(), KIcons.Spark)
        } else {
            i.aiConfidence?.let { cf ->
                val good = cf >= .6
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Confidence".t(), Modifier.weight(1f), color = c.ink2, style = hStyle(15.sp, lh = 20.sp))
                    Text(pct(cf, 0), color = if (good) c.green else c.amber, style = hStyle(18.sp, FontWeight.Bold, 22.sp))
                }
                KProgress(cf.toFloat(), if (good) c.zGreen else c.zAmber, top = 6.dp)
                Spacer(Modifier.height(10.dp))
            }
            Text(d.aiAnalysis, color = c.ink, style = hStyle(15.sp, lh = 22.sp))
            if (d.aiFix.isNotBlank()) {
                HLabel("Suggested fix".t())
                Text(d.aiFix, Modifier.padding(top = 2.dp), color = c.ink, style = hStyle(14.sp, lh = 20.sp))
            }
            if (d.aiMissing.isNotEmpty()) {
                HLabel("Missing evidence".t())
                d.aiMissing.forEach { Text("• $it", Modifier.padding(top = 2.dp), color = c.ink2, style = hStyle(14.sp, lh = 20.sp)) }
            }
            d.aiAutofixOk?.let { okFix ->
                HNote(if (okFix) "AI says: safe to auto-fix".t() else "AI says: needs a human".t(), if (okFix) KKind.OK else KKind.WARN, Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Devices, stack trace, fix attempts, builds, timeline. */
@Composable
private fun IssueMore(d: HIssueDetail, h: HState, vm: HealthVm) {
    val c = Mfc.c
    if (d.devices.isNotEmpty() || d.osVersions.isNotEmpty()) {
        KSection("Affected devices".t())
        KCard {
            if (d.devices.isNotEmpty()) {
                val tot = d.devices.sumOf { it.n }.toDouble()
                HBars(d.devices.take(6).map { Triple(it.k, num(it.n.toDouble()), shareOf(it.n.toDouble(), tot)) })
            }
            if (d.osVersions.isNotEmpty()) {
                if (d.devices.isNotEmpty()) Spacer(Modifier.height(8.dp))
                val t2 = d.osVersions.sumOf { it.n }.toDouble()
                HBars(d.osVersions.take(6).map { Triple("Android " + it.k, num(it.n.toDouble()), shareOf(it.n.toDouble(), t2)) }, c.zAmber)
            }
        }
    }
    if (d.stack.isNotBlank()) StackBlock(d.stack)
    if (d.fixes.isNotEmpty()) {
        KSection("AI fix attempts".t(), trailing = { HSecNote(tf("%s attempts", d.fixes.size)) })
        d.fixes.forEach { FixCard(it) }
    }
    if (d.builds.isNotEmpty()) {
        KSection("Builds".t())
        d.builds.forEach { BuildCard(it, h, vm, null) }
    }
    if (d.log.isNotEmpty()) {
        KSection("Timeline".t())
        KCard { d.log.forEach { EventRow(it, null) } }
    }
}

/** Stack trace: 8 line, tap = poora. */
@Composable
private fun StackBlock(stack: String) {
    var full by remember { mutableStateOf(false) }
    KSection("Stack trace".t(), trailing = { KSectionLink(if (full) "Fold".t() else "Expand".t()) { full = !full } })
    Box(Modifier.clickable { full = !full }) { Mono(stack, if (full) Int.MAX_VALUE else 8) }
}

/** Ek AI fix koshish: tests, steps, files, diff, GitHub run. */
@Composable
private fun FixCard(f: HFix) {
    val ctx = LocalContext.current
    val c = Mfc.c
    var diff by remember { mutableStateOf(false) }
    val k = when (f.success) {
        true -> KKind.OK
        false -> KKind.BAD
        null -> KKind.ASK
    }
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tf("Attempt %s", f.attempt), Modifier.weight(1f), color = c.ink, style = hStyle(15.sp, FontWeight.SemiBold, 20.sp), maxLines = 1)
            KPill(statusText(f.status.ifBlank { "running" }), k)
        }
        Text(
            tFull(f.startedAt) + (f.finishedAt?.let { " → " + tOnly(it) } ?: "") + (f.branch.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            Modifier.padding(top = 2.dp), color = c.ink3, style = hStyle(13.sp, lh = 18.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TestMark("Unit".t(), f.unitOk)
            TestMark("Build".t(), f.buildOk)
            TestMark("Emulator".t(), f.deviceOk)
        }
        if (f.failureReason.isNotBlank()) HNote(f.failureReason, KKind.BAD, maxLines = 6)
        if (f.explanation.isNotBlank()) Text(f.explanation, Modifier.padding(top = 8.dp), color = c.ink, style = hStyle(14.sp, lh = 20.sp))
        if (f.steps.isNotEmpty()) Column(Modifier.padding(top = 8.dp)) {
            f.steps.forEach { s ->
                Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tOnly(s.createdAt), Modifier.width(44.dp), color = c.ink3, style = hStyle(13.sp, lh = 18.sp), maxLines = 1)
                    Text(
                        s.step.replace('_', ' '), Modifier.width(96.dp), color = statusColor(s.status), style = hStyle(13.sp, FontWeight.SemiBold, 18.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(s.detail, Modifier.weight(1f), color = c.ink2, style = hStyle(13.sp, lh = 18.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (f.filesInspected.isNotEmpty()) KKv("Files inspected".t(), f.filesInspected.size.toString())
        if (f.filesModified.isNotEmpty()) {
            HLabel("Files modified".t())
            f.filesModified.forEach { Text("• $it", Modifier.padding(top = 2.dp), color = c.ink2, style = hStyle(13.sp, lh = 18.sp)) }
        }
        if (f.diff.isNotBlank() || f.ghRunUrl.isNotBlank()) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (f.diff.isNotBlank()) KButton(if (diff) "Hide diff".t() else "Show diff".t(), { diff = !diff }, Modifier.weight(1f), kind = KBtn.SOFT)
            if (f.ghRunUrl.isNotBlank()) KButton("GitHub run".t(), { openUrl(ctx, f.ghRunUrl) }, Modifier.weight(1f), kind = KBtn.OUTLINE, icon = KIcons.Open)
        }
        // card ke andar diff: bina dibbe ka monospace text
        if (diff) Text(
            f.diff, Modifier.fillMaxWidth().padding(top = 10.dp), color = c.ink2,
            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace),
        )
    }
}
