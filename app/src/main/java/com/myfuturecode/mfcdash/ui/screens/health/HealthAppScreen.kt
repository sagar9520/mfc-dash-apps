package com.myfuturecode.mfcdash.ui.screens.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.HApp
import com.myfuturecode.mfcdash.data.HAppDetail
import com.myfuturecode.mfcdash.data.HEvent
import com.myfuturecode.mfcdash.data.HRun
import com.myfuturecode.mfcdash.data.HVerRow
import com.myfuturecode.mfcdash.ui.HState
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.components.AppIcon
import com.myfuturecode.mfcdash.ui.components.BarLineChart
import com.myfuturecode.mfcdash.ui.components.ChipRow
import com.myfuturecode.mfcdash.ui.components.HBars
import com.myfuturecode.mfcdash.ui.components.Legend
import com.myfuturecode.mfcdash.ui.components.MChip
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
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KKv
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLoadOnChange
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KProgress
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSkelBars
import com.myfuturecode.mfcdash.ui.kit.KSkelBlock
import com.myfuturecode.mfcdash.ui.kit.KSkelCard
import com.myfuturecode.mfcdash.ui.kit.KSkelRows
import com.myfuturecode.mfcdash.ui.kit.KSkelSection
import com.myfuturecode.mfcdash.ui.kit.KSkelStats
import com.myfuturecode.mfcdash.ui.kit.KSortButton
import com.myfuturecode.mfcdash.ui.kit.KSortKind
import com.myfuturecode.mfcdash.ui.kit.KSortPill
import com.myfuturecode.mfcdash.ui.kit.KSortState
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kSorted
import com.myfuturecode.mfcdash.ui.kit.rememberKSort
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs

private val DAYS = listOf(7, 14, 30, 90)

// Play ki "bad behaviour" seema (server lib/health.php jaisi)
private const val CRASH_BAD = 0.0109
private const val ANR_BAD = 0.0047

/** Ek app ka poora haal: status, crash/ANR, installs/uninstalls/crashes (din-wise), issues, builds, scans, switches. */
@Composable
fun HealthAppScreen(vm: HealthVm, nav: NavHostController, pkg: String) {
    val h by vm.s.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(30) }
    var reload by remember { mutableIntStateOf(0) }
    Poll(30_000, pkg, days, reload) { vm.loadApp(pkg, days) }
    KLoadOnChange(days)   // din badle = upar ki line
    val d = h.app["$pkg|$days"] ?: h.app.entries.firstOrNull { it.key.startsWith("$pkg|") }?.value
    var task by remember { mutableStateOf(false) }
    var issueF by rememberSaveable { mutableStateOf("open") }
    // issues ka sort (section ke Sort button se); default = server ka order (aakhri baar, naya pehle)
    val issueSort = rememberKSort("last")
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") }
    val bar: @Composable ColumnScope.() -> Unit = {
        val a = d?.app
        KDetailBar(
            a?.name?.ifBlank { null } ?: pkg, back, sub = pkg,
            lead = if (a != null) { { AppIcon(a.icon, a.name.ifBlank { pkg }, 36.dp) } } else null,
            actions = {
                HPlayButton(pkg)
                KBarIcon(KIcons.Plus, "New task".t(), { task = true })
                KBarIcon(KIcons.Refresh, "Refresh".t(), { reload++ })
            },
        )
    }
    if (d == null) {
        // pehli baar: hero + Scan / Naya kaam, din, number grid, chart | Issues, scans ka dhaancha
        HDetailWait(h, bar, { vm.loadApp(pkg, days) }, left = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { KSkelBlock(48.dp, RoundedCornerShape(24.dp), Modifier.weight(1f)) }
            }
            KSkelBlock(48.dp, RoundedCornerShape(24.dp))
            KSkelStats(6, 3)
            KSkelSection(width = 110.dp)
            KSkelBars(14, 140.dp)
        }, right = {
            KSkelSection(width = 70.dp)
            KSkelRows(4)
            KSkelSection(width = 120.dp)
            KSkelRows(3)
        })
    } else if (isWide()) {
        // Fold: baayein hero + number + chart, daayein issues / builds / scans / switches
        KScreen(top = bar) {
            KTwoPane(
                left = {
                    AppHero(d)
                    AppNumbers(d, h, vm, pkg, days, { days = it }) { task = true }
                },
                right = { AppLists(d, h, vm, nav, issueF, issueSort) { issueF = it } },
            )
        }
    } else {
        KScreen(top = { bar(); AppHero(d) }) {
            AppNumbers(d, h, vm, pkg, days, { days = it }) { task = true }
            AppLists(d, h, vm, nav, issueF, issueSort) { issueF = it }
        }
    }
    if (task) TaskSheet(h, vm, nav, pkg) { task = false }
}

/** Play Store kholo: pehle Play Store app (market://), wo na ho to browser me play.google.com. */
private fun openPlay(ctx: Context, pkg: String) {
    val ok = runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!ok) openUrl(ctx, "https://play.google.com/store/apps/details?id=$pkg")
}

/**
 * Bar me "Play" pill (naam ke baad, + se pehle; App page jaisa): Play Store icon + shabd, brand-soft + brand
 * (Brand band par halka safed + safed). 48dp touch, tap = Play Store. Chhoti screen (< 400dp) par sirf icon.
 */
@Composable
private fun HPlayButton(pkg: String) {
    val c = Mfc.c
    val ctx = LocalContext.current
    val narrow = LocalConfiguration.current.screenWidthDp < 400
    val says = "Open in Play Store".t()
    Box(
        Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = says) { openPlay(ctx, pkg) },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (c.onBrand) c.pNBg else c.brandSoft)
                .padding(start = if (narrow) 9.dp else 10.dp, end = if (narrow) 9.dp else 12.dp)
                .clearAndSetSemantics { contentDescription = says },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(KIcons.PlayStore, null, tint = c.brand, modifier = Modifier.size(18.dp))
            if (!narrow) Text("Play".t(), color = c.brand, style = hStyle(14.sp, FontWeight.SemiBold, 18.sp), maxLines = 1)
        }
    }
}

/** Rose hero: console + status pill, khule issues (bada), crash / ANR / aakhri scan. */
@Composable
private fun AppHero(d: HAppDetail) {
    KHero(KTint.Rose) {
        val c = Mfc.c
        val a = d.app
        val open = d.counts.optInt("open")
        KHeroHead(a.consoleName.ifBlank { "Not linked".t() }, icon = KIcons.Pulse, trailing = { StatusPill(a.status) })
        KBigNum(hN(open), if (open > 0) c.red else c.ink, suffix = " " + "open".t(), size = 48.sp)
        val cr = a.crashRate
        val an = a.anrRate
        KSplit(
            listOf(
                "Crash rate".t() to (cr?.let { pct(it, 2) } ?: "–"),
                "ANR rate".t() to (an?.let { pct(it, 2) } ?: "–"),
                "Last scan".t() to (a.lastScanAt?.let { ago(it) } ?: "–"),
            ),
            listOf(if (cr != null && cr >= CRASH_BAD) c.red else null, if (an != null && an >= ANR_BAD) c.red else null, null),
        )
        // Google ka din (1-2 din purana), sab versions mila ke; version-wise neeche wale card me
        a.vitalsDay?.let { v ->
            Text(
                "Vitals".t() + " · " + "All versions".t() + " · " + fmtDay(v), Modifier.padding(top = 8.dp), color = c.ink3,
                style = hStyle(13.sp, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Scan / naya kaam, version-wise crash, din chuno, number grid, chart, versions. */
@Composable
private fun AppNumbers(d: HAppDetail, h: HState, vm: HealthVm, pkg: String, days: Int, onDays: (Int) -> Unit, onTask: () -> Unit) {
    val c = Mfc.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KButton("Scan".t(), { vm.scan(pkg) }, Modifier.weight(1f), kind = KBtn.SOFT, icon = KIcons.Refresh, busy = "scan$pkg" in h.busy)
        KButton("New task".t(), onTask, Modifier.weight(1f), icon = KIcons.Plus)
    }
    VersionVitals(d.app)
    KSegTabs(DAYS.map { tf("%s days", it) }, DAYS.indexOf(days).coerceAtLeast(0), { onDays(DAYS[it]) })
    KCard {
        HGrid(
            listOf(
                HCell("Installs".t(), hN(d.installs)),
                HCell("Uninstalls".t(), hN(d.uninstalls), sub = d.uninstallRate?.let { pct(it, 0) }),
                HCell("Crashes".t(), hN(d.crashes), sub = d.crashPer1k?.let { num(it, 2) + " / 1K" }, color = if (d.crashes > 0) c.red else null),
                HCell("DAU avg".t(), hN(d.dauAvg)),
                HCell("Revenue".t(), inr(d.revUsd * d.inrPerUsd)),
                HCell("Open issues".t(), hN(d.counts.optInt("open"))),
            ),
        )
    }
    KSection(tf("Last %s days", d.days))
    KCard {
        BarLineChart(
            d.series.map { fmtDay(it.day).substringBefore(' ') }, d.series.map { it.installs.toDouble() },
            line = d.series.map { it.uninstalls.toDouble() }, line2 = d.series.map { it.crashes.toDouble() },
            lineColor = c.zAmber, line2Color = c.zRed,
        )
        Legend("Installs".t() to c.brand, "Uninstalls".t() to c.zAmber, "Crashes".t() to c.zRed)
    }
    if (d.versions.isNotEmpty()) {
        KSection("Versions in use".t(), trailing = { HSecNote("last 3 days".t()) })
        KCard {
            val t = d.versions.sumOf { it.second }.toDouble()
            HBars(d.versions.map { Triple(it.first.ifBlank { "?" }, num(it.second.toDouble()) + " " + "users".t(), shareOf(it.second.toDouble(), t)) })
        }
    }
}

/** Version ke hisaab se crash / ANR (Google ka wahi din): naya version upar, Play ki seema se upar = laal. vitals_ver na ho to kuch nahi. */
@Composable
private fun VersionVitals(a: HApp) {
    val vv = a.vitalsVer ?: return
    KSection("Version-wise".t(), trailing = { HSecNote(fmtDay(vv.day)) })
    KCard(list = true) {
        vv.rows.take(5).forEachIndexed { i, r ->
            if (i > 0) KListDivider()
            VerRow(verLabel(r, a), r, newest = i == 0)
        }
    }
}

/** Tracker ka live version yahi code ho to "v1.2.3", warna "vc 63". */
private fun verLabel(r: HVerRow, a: HApp): String {
    val tv = a.trackerVer
    return if (!tv.isNullOrBlank() && a.trackerVc != null && r.vc == a.trackerVc.toString()) "v" + tv.removePrefix("v") else "vc " + r.vc
}

/** Ek version: naam (+ Naya), users; daayein Crash / ANR %. */
@Composable
private fun VerRow(label: String, r: HVerRow, newest: Boolean) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    label, Modifier.weight(1f, fill = false), color = c.ink, style = hStyle(15.sp, FontWeight.SemiBold, 20.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (newest) KPill("Newest".t(), KKind.ASK, icon = null)
            }
            r.users?.let {
                Text(tf("%s users", num(it.toDouble())), Modifier.padding(top = 2.dp), color = c.ink3, style = hStyle(13.sp, lh = 18.sp), maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            VerRate("Crash".t(), r.crash, CRASH_BAD)
            VerRate("ANR", r.anr, ANR_BAD)
        }
    }
}

/** "Crash 0.42%": seema se upar = laal (shape + rang), warna sada. */
@Composable
private fun VerRate(label: String, v: Double?, bad: Double) {
    val text = label + " " + (v?.let { pct(it, 2) } ?: "–")
    if (v != null && v >= bad) KStatus(text, KKind.BAD)
    else Text(text, color = Mfc.c.ink2, style = hStyle(13.sp, lh = 18.sp), maxLines = 1)
}

/** Issues (status chips + list), builds, haal ke scans, automation switches. */
@Composable
private fun AppLists(
    d: HAppDetail, h: HState, vm: HealthVm, nav: NavHostController, issueF: String, sort: KSortState, onIssueF: (String) -> Unit,
) {
    val a = d.app
    val n = d.counts
    val sortOpts = listOf("last" to "Last seen".t(), "severity" to "Severity".t(), "users" to "Users".t(), "events" to "Events".t())
    val sortKinds = mapOf("last" to KSortKind.DATE)
    KSection("Issues".t(), trailing = {
        // lambi ginti (Crash · ANR · Ad issue · Kaam) "..." ho jaye, Sort button aur "Issues" title ko jagah mile (360dp)
        Row(Modifier.widthIn(max = 220.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1f, fill = false)) {
                HSecNote(
                    listOf("Crash".t() to n.optInt("crash"), "ANR" to n.optInt("anr"), "Ad issue".t() to n.optInt("ad"), "Task".t() to n.optInt("task"))
                        .filter { it.second > 0 }.joinToString(" · ") { "${it.first} ${it.second}" },
                )
            }
            KSortButton(sort, sortOpts, sortKinds)
        }
    })
    ChipRow {
        listOf("open" to "Open", "fixed" to "Fixed", "rejected" to "Rejected", "resolved" to "Resolved", "all" to "All").forEach { (k, l) ->
            MChip(l.t(), issueF == k, { onIssueF(k) }, trailing = if (k == "all") d.issues.size.toString() else n.optInt(k).toString())
        }
    }
    val shown = d.issues.filter {
        when (issueF) {
            "open" -> it.status !in listOf("FIXED", "READY_FOR_REVIEW", "REJECTED", "RESOLVED")
            "fixed" -> it.status == "FIXED" || it.status == "READY_FOR_REVIEW"
            "rejected" -> it.status == "REJECTED"
            "resolved" -> it.status == "RESOLVED"
            else -> true
        }
    }.let { l ->
        when {
            sort.isDefault -> l
            sort.key == "severity" -> l.kSorted(sort.desc) { hSevRank(it.severity) }
            sort.key == "users" -> l.kSorted(sort.desc) { it.users }
            sort.key == "events" -> l.kSorted(sort.desc) { it.events }
            else -> l.kSorted(sort.desc) { tsMs(it.lastSeen) }
        }
    }
    KSortPill(sort, sortOpts, sortKinds)
    if (shown.isEmpty()) KCard { KEmpty("No match".t(), KIcons.Ok) }
    else KCard(list = true) {
        shown.forEachIndexed { i, x ->
            if (i > 0) KListDivider(true)
            IssueRow(x, nav, showApp = false)
        }
    }

    if (d.builds.isNotEmpty()) {
        KSection("Builds".t())
        d.builds.take(5).forEach { BuildCard(it, h, vm, nav) }
    }

    KSection("Recent scans".t())
    if (d.runs.isEmpty()) KCard { KEmpty("No scans yet".t(), KIcons.History) }
    else KCard(list = true) {
        d.runs.take(10).forEachIndexed { i, r ->
            if (i > 0) KListDivider(true)
            RunRow(r, nav)
        }
    }

    KSection("Automation".t())
    KCard(list = true) {
        KRow(
            "Repo".t(), sub = a.repo.ifBlank { "not linked".t() }, subKind = if (a.repo.isBlank()) KKind.WARN else null,
            lead = { KIconTile(KIcons.Fix, if (a.repo.isBlank()) KKind.WARN else KKind.NEUTRAL) },
        )
        KListDivider()
        HAppSwitches(a, h, vm)
    }
}

// ---------------------------------------------------------------- scan run

/** Ek scan run: status, progress, checks aur uske saare log lines. */
@Composable
fun HealthRunScreen(vm: HealthVm, nav: NavHostController, id: Long) {
    val h by vm.s.collectAsStateWithLifecycle()
    val d = h.run[id]
    val active = d?.first?.active == true
    var reload by remember { mutableIntStateOf(0) }
    Poll(if (active || d == null) 4_000 else 60_000, id, active, reload) { vm.loadRun(id) }
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") }
    val bar: @Composable ColumnScope.() -> Unit = {
        KDetailBar(
            d?.first?.uid?.ifBlank { null } ?: "Run".t(), back, sub = d?.first?.let { jobText(it.type) },
            actions = { KBarIcon(KIcons.Refresh, "Refresh".t(), { reload++ }, spinning = active) },
        )
    }
    if (d == null) {
        // pehli baar: hero + run ki jaankari | log ka dhaancha
        HDetailWait(h, bar, { vm.loadRun(id) }, left = {
            KSkelCard(8, title = false)
        }, right = {
            KSkelSection(width = 50.dp)
            KSkelCard(6, title = false)
        })
        return
    }
    val (r, ev) = d
    if (isWide()) {
        KScreen(top = bar) {
            KTwoPane(
                left = {
                    RunHero(r)
                    RunInfo(r, nav)
                },
                right = { RunLog(ev, nav) },
            )
        }
    } else {
        KScreen(top = { bar(); RunHero(r) }) {
            RunInfo(r, nav)
            RunLog(ev, nav)
        }
    }
}

/** Rose hero: job + status pill, kitni problem mili (bada), progress, checks / errors / time. */
@Composable
private fun RunHero(r: HRun) {
    KHero(KTint.Rose) {
        val c = Mfc.c
        KHeroHead(jobText(r.type), icon = hJobIcon(r.type), trailing = { StatusPill(r.status) })
        KBigNum(hN(r.problems), if (r.problems > 0) c.red else c.ink, suffix = " " + "problems".t(), size = 48.sp)
        if (r.active) {
            if (r.checksTotal > 0) KProgress(r.checksDone.toFloat() / r.checksTotal, c.brand)
            else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = c.brand, trackColor = c.zTrack)
            if (r.stage.isNotBlank()) Text(
                r.stage, Modifier.padding(top = 6.dp), color = c.ink2, style = hStyle(13.sp, lh = 18.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        KSplit(
            listOf(
                "Checks".t() to "${r.checksDone}/${r.checksTotal}",
                "Errors".t() to hN(r.errors),
                "Time".t() to (r.durationMs?.let { num(it / 1000.0, 1) + " s" } ?: "–"),
            ),
            listOf(null, if (r.errors > 0) c.red else null, null),
        )
    }
}

@Composable
private fun RunInfo(r: HRun, nav: NavHostController) {
    val c = Mfc.c
    KCard {
        KKv("App".t(), r.app.ifBlank { if (r.pkg.isBlank()) "all apps".t() else r.pkg })
        KKv("Trigger".t(), r.trigger.ifBlank { "–" } + (r.by.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
        KKv("Queued".t(), tFull(r.queuedAt))
        KKv("Started".t(), tFull(r.startedAt))
        KKv("Finished".t(), r.finishedAt?.let { tFull(it) } ?: "–")
        KKv("Checks".t(), "${r.checksDone} / ${r.checksTotal}")
        KKv("Problems found".t(), r.problems.toString(), valueColor = if (r.problems > 0) c.amber else c.ink)
        KKv("Errors".t(), r.errors.toString(), valueColor = if (r.errors > 0) c.red else c.ink)
        if (r.errorText.isNotBlank()) HNote(r.errorText, KKind.BAD, maxLines = 6)
    }
    val iid = r.issueId
    if (iid != null) KCard(list = true) {
        KRow("ISS-$iid", lead = { KIconTile(KIcons.Bug, KKind.BAD) }, chevron = true, onClick = { nav.navigate("health/issue/$iid") })
    }
}

@Composable
private fun RunLog(ev: List<HEvent>, nav: NavHostController) {
    KSection("Log".t(), trailing = { HSecNote(tf("%s lines", ev.size)) })
    KCard {
        if (ev.isEmpty()) KEmpty("No log lines".t(), KIcons.History)
        ev.forEach { EventRow(it, nav) }
    }
}
