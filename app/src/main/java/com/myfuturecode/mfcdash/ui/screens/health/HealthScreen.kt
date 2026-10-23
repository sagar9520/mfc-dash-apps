package com.myfuturecode.mfcdash.ui.screens.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.HApp
import com.myfuturecode.mfcdash.data.HBuild
import com.myfuturecode.mfcdash.data.HConsole
import com.myfuturecode.mfcdash.data.HFix
import com.myfuturecode.mfcdash.data.HOverview
import com.myfuturecode.mfcdash.ui.HState
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.components.AppIcon
import com.myfuturecode.mfcdash.ui.components.ChipRow
import com.myfuturecode.mfcdash.ui.components.MChip
import com.myfuturecode.mfcdash.ui.kit.KBadge
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
import com.myfuturecode.mfcdash.ui.kit.KLegend
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLoadOnChange
import com.myfuturecode.mfcdash.ui.kit.KLive
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSearch
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSegBar
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSkelBlock
import com.myfuturecode.mfcdash.ui.kit.KSkelCard
import com.myfuturecode.mfcdash.ui.kit.KSkelLine
import com.myfuturecode.mfcdash.ui.kit.KSkelRows
import com.myfuturecode.mfcdash.ui.kit.KSkelSection
import com.myfuturecode.mfcdash.ui.kit.KSkelStats
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KSmallAction
import com.myfuturecode.mfcdash.ui.kit.KSortButton
import com.myfuturecode.mfcdash.ui.kit.KSortKind
import com.myfuturecode.mfcdash.ui.kit.KSortState
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.icon
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kSortLead
import com.myfuturecode.mfcdash.ui.kit.kSorted
import com.myfuturecode.mfcdash.ui.kit.rememberKSort
import com.myfuturecode.mfcdash.ui.kit.word
import com.myfuturecode.mfcdash.ui.screens.FilterBox
import com.myfuturecode.mfcdash.ui.screens.FilterChips
import com.myfuturecode.mfcdash.ui.screens.FilterSheet
import com.myfuturecode.mfcdash.ui.screens.FilterSummary
import com.myfuturecode.mfcdash.ui.screens.FilterToggle
import com.myfuturecode.mfcdash.ui.screens.LocalFilterBox
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

val HTABS = listOf(
    "overview" to "Overview", "apps" to "All apps", "consoles" to "Consoles & Apps", "live" to "Live Activity", "issues" to "Issues",
    "fixes" to "AI Fix History", "builds" to "AAB Releases", "logs" to "System Logs", "settings" to "Settings",
)

// upar ke 3 tab (mockup: Aaj / Sab apps / Consoles), baaki "Aur" menu me
private val MAIN_TABS = listOf("overview", "apps", "consoles")
private val MORE_TABS = listOf("issues", "live", "fixes", "builds", "logs", "settings")

private fun shortTab(k: String): String = when (k) {
    "issues" -> "Issues".t()
    "live" -> "Live".t()
    "fixes" -> "AI fixes".t()
    "builds" -> "Builds".t()
    "logs" -> "Logs".t()
    "settings" -> "Settings".t()
    else -> "More".t()
}

private fun tabIcon(k: String): ImageVector = when (k) {
    "issues" -> KIcons.Bug
    "live" -> KIcons.Live
    "fixes" -> KIcons.Wand
    "builds" -> KIcons.Download
    "logs" -> KIcons.History
    "settings" -> KIcons.Gear
    else -> KIcons.More
}

// Sort (bar ka Sort button): har list tab ka apna; default = aaj ka order (bina chhede wahi list).
// apps: dikkat wale pehle; consoles: naam A -> Z (server); live: chalu + naye runs pehle; issues: review wale, severity, aakhri baar;
// AI fixes / logs: naye pehle; builds: aapka intezaar wale pehle, phir naye. Overview / Settings par Sort nahi.
private val H_SORT_DEF = mapOf(
    "apps" to ("status" to true), "consoles" to ("name" to false), "live" to ("time" to true), "issues" to ("severity" to true),
    "fixes" to ("time" to true), "builds" to ("status" to true), "logs" to ("time" to true),
)
private val H_SORT_KINDS = mapOf("time" to KSortKind.DATE, "last" to KSortKind.DATE, "app" to KSortKind.TEXT)

private fun hSortOpts(tab: String): List<Pair<String, String>> = when (tab) {
    "apps", "consoles" -> listOf(
        "status" to "Problem".t(), "issues" to "Open issues".t(), "crash" to "Crash rate".t(), "anr" to "ANR rate".t(), "name" to "Name".t(),
    )
    "live" -> listOf("time" to "Time".t(), "problems" to "Problems found".t(), "errors" to "Errors".t())
    "issues" -> listOf("severity" to "Severity".t(), "last" to "Last seen".t(), "users" to "Users".t(), "events" to "Events".t())
    "fixes" -> listOf("time" to "Time".t(), "app" to "App".t())
    "builds" -> listOf("status" to "Waiting for you".t(), "time" to "Time".t(), "app" to "App".t())
    "logs" -> listOf("time" to "Time".t(), "level" to "Level".t())
    else -> emptyList()
}

/** Apps: "Problem" = dikkat kitni (PROBLEM > WATCH > khule issues > baaki); default par wahi jo appRank deta tha. */
private fun hSortApps(list: List<HApp>, s: KSortState?): List<HApp> = when {
    s == null || s.isDefault -> if (s?.key == "status") list.sortedBy { appRank(it) } else list
    s.key == "issues" -> list.kSorted(s.desc) { it.openIssues }
    s.key == "crash" -> list.kSorted(s.desc) { it.crashRate }
    s.key == "anr" -> list.kSorted(s.desc) { it.anrRate }
    s.key == "name" -> list.kSorted(s.desc) { it.name.ifBlank { it.pkg }.lowercase() }
    else -> list.kSorted(s.desc) { 3 - appRank(it) }
}

/** Severity ka darja (P0 sabse upar), sort ke liye. */
internal fun hSevRank(s: String): Int = when (s.uppercase()) {
    "P0" -> 3
    "P1" -> 2
    "P2" -> 1
    else -> 0
}

private fun hLevelRank(l: String): Int = when (l) {
    "error" -> 3
    "warn" -> 2
    "ok" -> 1
    else -> 0
}

/** App Health: hero (kitne apps theek) + tabs. Aaj = Turant, AI fix taiyaar, Consoles. Har tab apna data khud laata hai. */
@Composable
fun HealthScreen(vm: HealthVm, nav: NavHostController, startTab: String) {
    val h by vm.s.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (HTABS.any { it.first == startTab }) startTab else "overview") }
    var task by remember { mutableStateOf(false) }
    val box = remember(tab) { FilterBox() }
    val reload = remember(tab) { mutableIntStateOf(0) }
    val rl = reload.intValue
    // hero har tab par dikhta hai, isliye overview yahin se
    Poll(30_000, rl) { vm.loadOverview() }
    KLoadOnChange(tab)   // user ne tab badla = upar ki line
    val go: (String) -> Unit = { tab = it }
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") }
    // har list tab ka apna sort (tab badalne par yaad); Overview / Settings par null
    val sorts = H_SORT_DEF.mapValues { (k, d) -> key(k) { rememberKSort(d.first, d.second) } }
    val sort = sorts[tab]
    val sortOpts = hSortOpts(tab)
    val sortLead = sort?.let { kSortLead(it, sortOpts, H_SORT_KINDS) }
    val bar: @Composable ColumnScope.() -> Unit = {
        KDetailBar("App Health".t(), back, sub = scanSub(h), actions = {
            // Filter is page par list ke upar (pill); Sort bar me pehla
            if (sort != null) KSortButton(sort, sortOpts, H_SORT_KINDS)
            KBarIcon(KIcons.Plus, "New task".t(), { task = true })
            KBarIcon(KIcons.Refresh, "Refresh".t(), { reload.intValue++ })
        })
    }
    val o = h.overview
    if (isWide()) {
        // Fold: baayein hero + tabs (+ Turant), daayein baaki
        KScreen(top = bar) {
            KTwoPane(
                left = {
                    HealthHero(o, wait = h.error == null)
                    HealthTabs(tab, o, go)
                    FilterToggle(box)
                    FilterSummary(box, lead = sortLead)
                    if (tab == "overview") OverviewMain(h, vm, nav, go)
                },
                right = {
                    if (tab == "overview") OverviewSide(h, vm, nav, rl, go)
                    else CompositionLocalProvider(LocalFilterBox provides box) { TabBody(tab, h, vm, nav, rl, go, sort) }
                },
            )
        }
    } else {
        KScreen(top = { bar(); HealthHero(o, wait = h.error == null) }) {
            HealthTabs(tab, o, go)
            FilterToggle(box)
            FilterSummary(box, lead = sortLead)
            CompositionLocalProvider(LocalFilterBox provides box) { TabBody(tab, h, vm, nav, rl, go, sort) }
        }
    }
    FilterSheet(box)
    if (task) TaskSheet(h, vm, nav, null) { task = false }
}

/** "Scan · 11:24" (pichhla cycle), warna "Updated ...". */
private fun scanSub(h: HState): String {
    val lc = h.overview?.lastCycle ?: return updatedText(h)
    return "Scan".t() + " · " + hWhen(lc.finishedAt ?: lc.queuedAt)
}

@Composable
private fun TabBody(tab: String, h: HState, vm: HealthVm, nav: NavHostController, reload: Int, go: (String) -> Unit, sort: KSortState?) {
    when (tab) {
        "apps" -> AppsTab(h, vm, nav, reload, sort)
        "consoles" -> ConsolesTab(h, vm, nav, reload, sort)
        "live" -> LiveTab(h, vm, nav, reload, sort)
        "issues" -> IssuesTab(h, vm, nav, reload, sort)
        "fixes" -> FixesTab(h, vm, nav, reload, sort)
        "builds" -> BuildsTab(h, vm, nav, reload, sort)
        "logs" -> LogsTab(h, vm, nav, reload, sort)
        "settings" -> HSettingsTab(h, vm, reload)
        else -> {
            OverviewMain(h, vm, nav, go)
            OverviewSide(h, vm, nav, reload, go)
        }
    }
}

// ---------------------------------------------------------------- hero + tabs

private fun heroKind(o: HOverview): KKind = when {
    o.issuesP0 > 0 || !o.cronOk || o.integrations.any { (it.kind == "play" || it.kind == "tracker") && it.status == "ERROR" } -> KKind.BAD
    o.withProblems > 0 || o.needsHuman > 0 -> KKind.WARN
    o.aabReady > 0 -> KKind.ASK
    else -> KKind.OK
}

/** Rose hero: 21/25 apps theek + legend + patti + pill (Turant dekho / Sab theek). wait = data aa raha (number ki jagah skeleton). */
@Composable
private fun HealthHero(o: HOverview?, wait: Boolean = false) {
    KHero(KTint.Rose) {
        val c = Mfc.c
        val k = o?.let { heroKind(it) }
        KHeroHead("Apps fine".t(), icon = KIcons.Pulse, trailing = {
            if (k != null) KPill(
                when (k) {
                    KKind.BAD -> "Look now".t()
                    KKind.OK -> "All fine".t()
                    else -> k.word()
                },
                k,
            )
        })
        if (o == null && wait) {
            HeroSkel()
            return@KHero
        }
        val ok = o?.healthy?.coerceAtLeast(0) ?: 0
        val bad = o?.withProblems?.coerceAtLeast(0) ?: 0
        val total = o?.apps?.coerceAtLeast(0) ?: 0
        val wait = (total - ok - bad).coerceAtLeast(0)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) {
                KBigNum(if (o == null) "–" else hN(ok), c.ink, suffix = if (o == null) null else "/" + hN(total), size = 48.sp)
            }
            if (o != null) Box(Modifier.padding(bottom = 6.dp)) {
                KLegend(
                    listOf(
                        Triple(c.zGreen, hN(ok), "fine".t()),
                        Triple(c.zRed, hN(bad), "problem".t()),
                        Triple(c.zGrey, hN(wait), "scan pending".t()),
                    ),
                )
            }
        }
        KSegBar(if (o == null) emptyList() else listOf(ok.toFloat() to c.zGreen, bad.toFloat() to c.zRed, wait.toFloat() to c.zGrey))
    }
}

/** Overview aane tak hero me: bada number + legend (3 line) + patti ki jagah. */
@Composable
private fun HeroSkel() {
    KSkeleton(Modifier.padding(top = 10.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(56.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(124.dp, 38.dp) }
                Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(64.dp, 82.dp, 106.dp).forEach { KSkelLine(it, 10.dp) }
                }
            }
            KSkelLine(1f, 8.dp, Modifier.padding(top = 14.dp))
        }
    }
}

/** Aaj | Sab apps | Consoles | Aur (menu: Issues, Live, AI fixes, Builds, Logs, Settings). */
@Composable
private fun HealthTabs(tab: String, o: HOverview?, onPick: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val inMore = tab in MORE_TABS
    val idx = MAIN_TABS.indexOf(tab).let { if (it < 0) 3 else it }
    val moreBadge = if (inMore) 0 else (o?.issuesOpen ?: 0)
    Box(Modifier.fillMaxWidth()) {
        KSegTabs(
            listOf("Today".t(), "All apps".t(), "Consoles".t(), if (inMore) shortTab(tab) else "More".t()),
            idx,
            { i -> if (i < MAIN_TABS.size) onPick(MAIN_TABS[i]) else menu = true },
            badges = listOf(0, 0, 0, moreBadge),
        )
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val c = Mfc.c
                MORE_TABS.forEach { k ->
                    val n = when (k) {
                        "issues" -> o?.issuesOpen ?: 0
                        "builds" -> o?.aabReady ?: 0
                        "live" -> o?.runsActive ?: 0
                        else -> 0
                    }
                    val label = HTABS.firstOrNull { it.first == k }?.second?.t() ?: shortTab(k)
                    DropdownMenuItem(
                        text = {
                            Text(
                                label, color = if (k == tab) c.brand else c.ink,
                                style = hStyle(15.sp, if (k == tab) FontWeight.Bold else FontWeight.Medium, 20.sp),
                            )
                        },
                        onClick = { menu = false; onPick(k) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        leadingIcon = { Icon(tabIcon(k), null, tint = if (k == tab) c.brand else c.ink2, modifier = Modifier.size(20.dp)) },
                        trailingIcon = if (n > 0) { { KBadge(n, urgent = k == "issues", ring = Color.Transparent) } } else null,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 1. Aaj (overview)

/** "Crash · 340 users" (users na ho to events). */
private fun issueLine(i: com.myfuturecode.mfcdash.data.HIssue): String = listOfNotNull(
    typeText(i.type).ifBlank { null },
    i.users?.takeIf { it > 0 }?.let { tf("%s users", num(it.toDouble())) } ?: i.events.takeIf { it > 0 }?.let { tf("%s events", num(it.toDouble())) },
).joinToString(" · ")

/** Turant: khule P0/P1 issues (app, kya hua, kab). Baaki "Sab N khule" se Issues tab. */
@Composable
private fun OverviewMain(h: HState, vm: HealthVm, nav: NavHostController, go: (String) -> Unit) {
    val o = h.overview ?: run {
        // phone par neeche wale blocks ka dhaancha bhi yahin (ek skeleton); Fold par woh daayein column me
        if (h.error != null) HProblem(h) { vm.loadOverview() }
        else KSkeleton {
            KSkelSection()
            KSkelRows(3)
            if (!isWide()) OverviewSideSkel()
        }
        return
    }
    val att = o.attention.filter { it.severity == "P0" || it.severity == "P1" }
    val nApps = att.map { it.pkg.ifBlank { it.app } }.distinct().size
    val k = when {
        att.any { it.severity == "P0" } -> KKind.BAD
        att.isNotEmpty() -> KKind.WARN
        else -> KKind.OK
    }
    KSection("Act now".t(), trailing = { KPill(if (att.isEmpty()) "Fine".t() else tf("%s apps", nApps), k) })
    val more = o.issuesOpen > att.size
    if (att.isEmpty() && !more) {
        KCard { KEmpty("Nothing urgent".t(), KIcons.Ok) }
        return
    }
    KCard(list = true) {
        att.forEachIndexed { i, x ->
            if (i > 0) KListDivider(true)
            val app = x.app.ifBlank { x.pkg }
            KRow(
                app, sub = issueLine(x).ifBlank { x.title }, subKind = hSevKind(x.severity), lead = { AppIcon(x.icon, app, 40.dp) },
                value = hWhen(x.lastSeen), valuePlain = true, chevron = true, onClick = { nav.navigate("health/issue/${x.id}") },
            )
        }
        if (more) {
            if (att.isNotEmpty()) KListDivider(true)
            KRow(
                tf("All %s open", o.issuesOpen), lead = { KIconTile(KIcons.Bug) }, chevron = true, onClick = { go("issues") },
            )
        }
    }
}

/** AI fix taiyaar, Consoles, Scan, AI kaam ke number, Integrations. */
@Composable
private fun OverviewSide(h: HState, vm: HealthVm, nav: NavHostController, reload: Int, go: (String) -> Unit) {
    Poll(30_000, reload) { vm.loadBuilds() }
    val o = h.overview ?: run {
        if (isWide() && h.error == null) OverviewSideSkel()
        return
    }
    FixReady(h, o, nav, go)
    ConsolesBlock(o, go)
    ScanBlock(h, o, vm, nav, go)
    NumbersBlock(o, go)
    IntegrationsBlock(o)
}

/** Overview aane tak: Consoles (1 line), Scan (3 lines), AI kaam (number grid) ki jagah. */
@Composable
private fun OverviewSideSkel() {
    KSkeleton {
        KSkelSection(width = 100.dp)
        KSkelRows(1)
        KSkelSection(width = 60.dp)
        KSkelRows(3)
        KSkelSection(width = 90.dp)
        KSkelStats(12, 3)
    }
}

/** "Crash fix" (issue ka type pata ho) warna "v1.4.2". */
private fun fixLabel(b: HBuild, o: HOverview): String =
    o.attention.firstOrNull { it.id == b.issueId }?.let { typeText(it.type) + " " + "fix".t() } ?: ("v" + b.versionName)

@Composable
private fun FixReady(h: HState, o: HOverview, nav: NavHostController, go: (String) -> Unit) {
    val all = h.builds
    val ready = all?.filter { it.reviewable }.orEmpty()
    if (ready.isEmpty() && (all != null || o.aabReady == 0)) return
    val n = if (ready.isEmpty()) o.aabReady else ready.size
    KSection("AI fix ready".t(), trailing = { KPill(tf("%s need OK", n), KKind.ASK) })
    if (ready.isEmpty()) {
        // builds abhi aa rahe (ginti overview se pata): utni lines ka dhaancha
        KSkelRows(n.coerceIn(1, 3))
        return
    }
    KCard(list = true) {
        ready.forEachIndexed { i, b ->
            if (i > 0) KListDivider(true)
            val iid = b.issueId
            FixRow(b, fixLabel(b, o)) { if (iid != null) nav.navigate("health/issue/$iid") else go("builds") }
        }
    }
}

/** Fix row: logo + naam + jaadu-chhadi line kholti hai, daayein "Dekho". */
@Composable
private fun FixRow(b: HBuild, label: String, onOpen: () -> Unit) {
    val c = Mfc.c
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = 64.dp).clickable(onClick = onOpen).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(b.icon, b.app, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(b.app, color = c.ink, style = hStyle(15.sp, FontWeight.SemiBold, 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(KIcons.Wand, null, tint = c.brand, modifier = Modifier.size(14.dp))
                    Text(label, color = c.brand, style = hStyle(13.sp, FontWeight.SemiBold, 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        KSmallAction("View".t(), onOpen)
    }
}

/** Consoles: sirf jinme dikkat hai (sab theek ho to ek line). Tap = Consoles tab. */
@Composable
private fun ConsolesBlock(o: HOverview, go: (String) -> Unit) {
    val cons = o.integrations.filter { it.kind == "play" }
    val bad = cons.filter { it.status != "OK" }
    KSection("Consoles".t(), trailing = {
        if (o.consoles > 0) KPill(tf("%s/%s OK", o.consolesOk, o.consoles), KKind.NEUTRAL, icon = null)
    })
    KCard(list = true) {
        when {
            !o.playConfigured || cons.isEmpty() -> KRow(
                "Play Console", sub = "No service account".t(), subKind = KKind.WARN, lead = { KIconTile(KIcons.Dhyan, KKind.WARN) },
                chevron = true, onClick = { go("consoles") },
            )
            bad.isEmpty() -> KRow(
                tf("%s consoles", cons.size), sub = "Fine".t(), subKind = KKind.OK, lead = { KIconTile(KIcons.Ok, KKind.OK) },
                chevron = true, onClick = { go("consoles") },
            )
            else -> bad.forEachIndexed { i, x ->
                if (i > 0) KListDivider(true)
                val k0 = hStatusKind(x.status)
                val k = if (k0 == KKind.OK || k0 == KKind.NEUTRAL) KKind.WARN else k0
                // detail sirf "12 apps" ho to status ka shabd dikhao
                val sub = if (x.detail.isBlank() || x.detail.matches(Regex("\\d+ apps"))) statusText(x.status) else x.detail
                KRow(
                    x.name, sub = sub, subKind = k, lead = { KIconTile(k.icon() ?: KIcons.Dhyan, k) },
                    chevron = true, onClick = { go("consoles") },
                )
            }
        }
    }
}

/** Pichhla cycle, agla scan, aur "Abhi scan karo". */
@Composable
private fun ScanBlock(h: HState, o: HOverview, vm: HealthVm, nav: NavHostController, go: (String) -> Unit) {
    KSection("Scan".t())
    KCard(list = true) {
        val lc = o.lastCycle
        if (lc != null) {
            val k = hStatusKind(lc.status)
            KRow(
                "Last cycle".t(), sub = jobText(lc.type) + " · " + statusText(lc.status), subKind = k, lead = { KIconTile(KIcons.History, k) },
                value = hWhen(lc.finishedAt ?: lc.queuedAt), valuePlain = true, chevron = true, onClick = { nav.navigate("health/run/${lc.id}") },
            )
        } else {
            KRow("No scans yet".t(), lead = { KIconTile(KIcons.History) }, quiet = true)
        }
        val nx = o.nextScanAt
        if (nx != null) {
            KListDivider(true)
            KRow("Next scan".t(), sub = jobText(o.nextScanType), lead = { KIconTile(KIcons.Clock) }, value = hWhen(nx), valuePlain = true)
        }
        if (o.tz.isNotBlank()) {
            KListDivider(true)
            KRow("Server zone".t(), lead = { KIconTile(KIcons.Globe) }, value = o.tz, valuePlain = true)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KButton("Run Scan Now".t(), { vm.scan() }, Modifier.weight(1.5f), busy = "scan" in h.busy)
            KButton("Live".t(), { go("live") }, Modifier.weight(1f), kind = KBtn.OUTLINE, icon = KIcons.Live)
        }
    }
}

/** AI kaam ke number (tap = us tab par). */
@Composable
private fun NumbersBlock(o: HOverview, go: (String) -> Unit) {
    val c = Mfc.c
    KSection("AI work".t())
    KCard {
        HGrid(
            listOf(
                HCell("Open issues".t(), hN(o.issuesOpen), onClick = { go("issues") }),
                HCell("P0", hN(o.issuesP0), color = if (o.issuesP0 > 0) c.red else null, onClick = { go("issues") }),
                HCell("Needs you".t(), hN(o.needsHuman), color = if (o.needsHuman > 0) c.amber else null, onClick = { go("issues") }),
                HCell("Investigating".t(), hN(o.investigating), onClick = { go("issues") }),
                HCell("Fixing".t(), hN(o.fixing), onClick = { go("fixes") }),
                HCell("AAB ready".t(), hN(o.aabReady), color = if (o.aabReady > 0) c.brand else null, onClick = { go("builds") }),
                HCell("Fixes ✓".t(), hN(o.fixesOk30), sub = "30 days".t(), color = if (o.fixesOk30 > 0) c.green else null, onClick = { go("fixes") }),
                HCell("Fixes ✗".t(), hN(o.fixesFailed30), sub = "30 days".t(), color = if (o.fixesFailed30 > 0) c.red else null, onClick = { go("fixes") }),
                HCell("Running".t(), hN(o.runsActive), color = if (o.runsActive > 0) c.brand else null, onClick = { go("live") }),
                HCell("Scans/24h".t(), hN(o.runsTotal24), sub = tf("%s clean · %s failed", o.runsClean24, o.runsFailed24), onClick = { go("live") }),
                HCell("Consoles".t(), "${o.consolesOk}/${o.consoles}", onClick = { go("consoles") }),
                HCell("Monitored".t(), "${o.appsMonitored}/${o.apps}", onClick = { go("apps") }),
            ),
        )
    }
}

/** Tracker, cron, AI, email, GitHub: dikkat wale upar. */
@Composable
private fun IntegrationsBlock(o: HOverview) {
    val list = o.integrations.filter { it.kind != "play" }.sortedBy { if (it.status == "OK") 1 else 0 }
    if (list.isEmpty()) return
    val ok = list.count { it.status == "OK" }
    KSection("Integrations".t(), trailing = {
        KPill(tf("%s/%s OK", ok, list.size), KKind.NEUTRAL, icon = null)
    })
    KCard(list = true) {
        list.forEachIndexed { i, x ->
            if (i > 0) KListDivider(true)
            val k = hStatusKind(x.status)
            KRow(
                x.name, sub = x.detail.ifBlank { statusText(x.status) }, subKind = if (k == KKind.OK) null else k,
                lead = { KIconTile(hIntegIcon(x.kind), k) },
            )
        }
    }
}

// ---------------------------------------------------------------- 2. Sab apps

private fun appStatusOpts(): List<Pair<String, String>> = listOf(
    "" to "All".t(), "PROBLEM" to "Problem".t(), "WATCH" to "Watch".t(), "HEALTHY" to "Healthy".t(),
    "issues" to "With open issues".t(), "off" to "Monitoring off".t(),
)

private fun appMatch(a: HApp, q: String, stF: String): Boolean =
    (q.isBlank() || a.name.contains(q, true) || a.pkg.contains(q, true)) && when (stF) {
        "" -> true
        "issues" -> a.openIssues > 0
        "off" -> !a.monitoring
        else -> a.status == stF
    }

/** Dikkat wale upar: PROBLEM, WATCH, khule issues, baaki. */
private fun appRank(a: HApp): Int = when {
    a.status == "PROBLEM" -> 0
    a.status == "WATCH" -> 1
    a.openIssues > 0 -> 2
    else -> 3
}

@Composable
private fun AppsTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int, sort: KSortState?) {
    Poll(60_000, reload) { vm.loadConsoles() }
    var q by rememberSaveable { mutableStateOf("") }
    var stF by rememberSaveable { mutableStateOf("") }
    var conF by rememberSaveable { mutableStateOf("") }
    FilterChips("Status".t(), appStatusOpts(), stF) { stF = it }
    val d = h.consoles ?: run {
        if (h.error != null) HProblem(h) { vm.loadConsoles() } else ListSkel(6)
        return
    }
    val ids = d.consoles.map { it.id }.toSet()
    if (d.consoles.isNotEmpty()) FilterChips(
        "Console".t(), listOf("" to "All".t()) + d.consoles.map { it.id to it.name } + listOf("-" to "Not linked".t()), conF,
    ) { conF = it }
    KSearch(q, { q = it }, "Search app or package".t())
    val shown = hSortApps(
        d.apps.filter { a ->
            appMatch(a, q, stF) && when (conF) {
                "" -> true
                "-" -> a.consoleId !in ids
                else -> a.consoleId == conF
            }
        },
        sort,
    )
    if (shown.isEmpty()) {
        KCard { KEmpty(if (d.apps.isEmpty()) "No apps".t() else "No match".t(), KIcons.Apps) }
        return
    }
    KCard(list = true) {
        shown.forEachIndexed { i, a ->
            if (i > 0) KListDivider(true)
            AppRow(a, nav)
        }
    }
}

/** Apps / Consoles aane tak: search box + app lines (logo + naam + haal). */
@Composable
private fun ListSkel(rows: Int) {
    KSkeleton {
        KSkelBlock(48.dp, RoundedCornerShape(24.dp))
        KSkelRows(rows)
    }
}

// ---------------------------------------------------------------- 3. Consoles

@Composable
private fun ConsolesTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int, sort: KSortState?) {
    Poll(60_000, reload) { vm.loadConsoles() }
    var q by rememberSaveable { mutableStateOf("") }
    var stF by rememberSaveable { mutableStateOf("") }
    FilterChips("Status".t(), appStatusOpts(), stF) { stF = it }
    val d = h.consoles ?: run {
        if (h.error != null) HProblem(h) { vm.loadConsoles() } else ListSkel(5)
        return
    }
    KSearch(q, { q = it }, "Search app or package".t())
    if (d.consoles.isEmpty() && d.apps.isEmpty()) {
        KCard { KEmpty("No consoles".t(), KIcons.Globe) }
        return
    }
    val groups = d.consoles.map { con -> con to d.apps.filter { it.consoleId == con.id } } +
        listOf<Pair<HConsole?, List<HApp>>>(null to d.apps.filter { a -> d.consoles.none { it.id == a.consoleId } })
    groups.forEach { (con, apps) ->
        if (con == null && apps.isEmpty()) return@forEach
        val shown = hSortApps(apps.filter { appMatch(it, q, stF) }, sort)
        KCard(list = true) {
            if (con != null) ConsoleHead(con)
            else KRow(
                "Not linked to a console".t(), sub = "app_console · health_config.php", lead = { KIconTile(KIcons.Dhyan, KKind.WARN) },
            )
            if (shown.isEmpty()) {
                KListDivider()
                KEmpty(if (apps.isEmpty()) "No apps".t() else "No match".t())
            }
            shown.forEach { a ->
                KListDivider(true)
                AppRow(a, nav)
            }
        }
    }
}

/** Console ki pehli line: naam, apps, sync, status pill; neeche service account / error / warnings. */
@Composable
private fun ConsoleHead(con: HConsole) {
    val k = hStatusKind(con.status)
    KRow(
        con.name, sub = tf("%s apps", con.apps) + " · " + "synced".t() + " " + ago(con.lastSyncAt), lead = { KIconTile(KIcons.Globe, k) },
        trailing = { KPill(statusText(con.status), k) },
    )
    val side = Modifier.padding(start = 16.dp, end = 16.dp)
    if (!con.saSet) HNote("service account missing".t(), KKind.WARN, side)
    if (con.lastError.isNotBlank()) HNote(con.lastError, KKind.BAD, side)
    con.warnings.forEach { HNote(it, KKind.WARN, side) }
    if (!con.saSet || con.lastError.isNotBlank() || con.warnings.isNotEmpty()) Box(Modifier.padding(top = 8.dp))
}

// ---------------------------------------------------------------- 4. Live

private val JOBS = listOf("manual_full", "critical", "play_errors", "vitals", "ads", "discovery")

@Composable
private fun LiveTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int, sort: KSortState?) {
    Poll(5_000, reload) { vm.loadRuns() }
    var type by rememberSaveable { mutableStateOf("manual_full") }
    val c = Mfc.c
    KSection("Run a scan".t())
    KCard {
        ChipRow { JOBS.forEach { k -> MChip(jobText(k), type == k, { type = k }) } }
        KButton("Run Scan Now".t(), { vm.scan(type = type) }, Modifier.fillMaxWidth().padding(top = 8.dp), busy = "scan" in h.busy)
    }
    val r = h.runs ?: run {
        // number grid, Runs, Activity feed ka dhaancha
        if (h.error != null) HProblem(h) { vm.loadRuns() }
        else KSkeleton {
            KSkelStats(5, 3)
            KSkelSection(width = 60.dp)
            KSkelRows(4)
            KSkelSection(width = 110.dp)
            KSkelCard(4, title = false)
        }
        return
    }
    KCard {
        HGrid(
            listOf(
                HCell("Running".t(), hN(r.running), color = if (r.running > 0) c.brand else null),
                HCell("Queued".t(), hN(r.queued)),
                HCell("Next scan".t(), r.nextScanAt?.let { hWhen(it) } ?: "–"),
                HCell("Done 24h".t(), hN(r.done24)),
                HCell("Failed 24h".t(), hN(r.failed24), color = if (r.failed24 > 0) c.red else null),
            ),
        )
    }
    KSection("Runs".t(), trailing = { KLive("auto 5 sec".t()) })
    // default par server ka order (chalu / line me pehle, phir naye)
    val runs = when {
        sort == null || sort.isDefault -> r.runs
        sort.key == "problems" -> r.runs.kSorted(sort.desc) { it.problems }
        sort.key == "errors" -> r.runs.kSorted(sort.desc) { it.errors }
        else -> r.runs.kSorted(sort.desc) { tsMs(it.startedAt ?: it.queuedAt) }
    }
    if (runs.isEmpty()) KCard { KEmpty("No runs yet".t(), KIcons.History) }
    else KCard(list = true) {
        runs.forEachIndexed { i, x ->
            if (i > 0) KListDivider(true)
            RunRow(x, nav)
        }
    }
    KSection("Activity feed".t())
    KCard {
        if (r.feed.isEmpty()) KEmpty("Nothing yet".t())
        r.feed.forEach { EventRow(it, nav) }
    }
}

// ---------------------------------------------------------------- 5. Issues

private fun dayAgo(n: Int): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) }.time)

@Composable
private fun IssuesTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int, sort: KSortState?) {
    val ctx = LocalContext.current
    val c = Mfc.c
    var status by rememberSaveable { mutableStateOf("open") }
    var console by rememberSaveable { mutableStateOf("") }
    var pkg by rememberSaveable { mutableStateOf("") }
    var sev by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    var claude by remember { mutableStateOf(false) }   // "Export for Claude" sheet
    LaunchedEffect(Unit) { if (h.consoles == null) vm.loadConsoles() }
    val f = mapOf(
        "status" to status, "console" to console, "pkg" to pkg, "severity" to sev, "type" to type, "q" to q.trim(),
        "from" to if (period.isBlank()) "" else dayAgo(period.toInt() - 1),
    )
    Poll(20_000, f, reload) { vm.loadIssues(f) }
    KLoadOnChange(status, console, pkg, sev, type, period)   // filter chips = upar ki line (search text nahi)

    val cons = h.consoles
    if (cons != null && cons.consoles.size > 1) FilterChips("Console".t(), listOf("" to "All".t()) + cons.consoles.map { it.id to it.name }, console) {
        console = it; pkg = ""
    }
    if (cons != null) FilterChips(
        "App".t(), listOf("" to "All apps".t()) + cons.apps.filter { console.isBlank() || it.consoleId == console }.map { it.pkg to it.name }, pkg,
    ) { pkg = it }
    FilterChips("Severity".t(), listOf("" to "All".t(), "P0" to "P0", "P1" to "P1", "P2" to "P2", "P3" to "P3"), sev) { sev = it }
    FilterChips(
        "Type".t(), listOf("" to "All".t(), "CRASH" to "Crash".t(), "ANR" to "ANR", "VITALS" to "Vitals", "AD" to "Ad issue".t(), "TASK" to "Task".t()), type,
    ) { type = it }
    FilterChips(
        "Period".t(), listOf("" to "All time".t(), "1" to "Today".t(), "7" to "7 days".t(), "30" to "30 days".t(), "90" to "90 days".t()), period,
    ) { period = it }

    val d = h.issues
    ChipRow {
        listOf(
            "open" to ("Open".t() to d?.open), "fixed" to ("Fixed".t() to d?.fixed), "rejected" to ("Rejected".t() to d?.rejected),
            "resolved" to ("Resolved".t() to d?.resolved), "all" to ("All".t() to d?.total),
        ).forEach { (k, p) -> MChip(p.first, status == k, { status = k }, trailing = p.second?.toString()) }
    }
    KSearch(q, { q = it }, "Search title, cause, ISS-id".t())
    if (d == null) {
        // "N issues" + CSV + For Claude ki line, phir issue lines
        if (h.error != null) HProblem(h) { vm.loadIssues(f) }
        else KSkeleton {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KSkelLine(84.dp, 14.dp, Modifier.padding(start = 4.dp))
                Box(Modifier.weight(1f))
                KSkelBlock(48.dp, RoundedCornerShape(24.dp), Modifier.width(88.dp))
                KSkelBlock(48.dp, RoundedCornerShape(24.dp), Modifier.width(136.dp))
            }
            KSkelRows(5)
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = tf("%s issues", d.issues.size)
        val countStyle = hStyle(15.sp, FontWeight.SemiBold, 20.sp)
        val csv = @Composable { m: Modifier ->
            KButton("CSV", { vm.export(f + ("what" to "issues")) { openUrl(ctx, it) } }, m, kind = KBtn.SOFT, icon = KIcons.Download, busy = "export" in h.busy)
        }
        // crash / ANR + poore stack trace ek .md file me: Claude app ke code me fix kare
        val forClaude = @Composable { m: Modifier -> KButton("For Claude".t(), { claude = true }, m, icon = KIcons.Spark) }
        // "N issues" + dono buttons ek line me aate hain? Nahi (chhota phone / Fold cover / Hinglish / bada font) = ginti upar, buttons neeche
        // (dono ek line me bhi na aayein = ek ke neeche ek, poori chaudai)
        val btns = hBtnW("CSV") + 8.dp + hBtnW("For Claude".t())
        val twoFit = btns <= maxWidth
        if (hTextW(count, countStyle) + 4.dp + 8.dp + btns <= maxWidth) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(count, Modifier.weight(1f).padding(start = 4.dp), color = c.ink2, style = countStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                csv(Modifier)
                forClaude(Modifier)
            }
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(count, Modifier.padding(start = 4.dp), color = c.ink2, style = countStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (twoFit) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                csv(Modifier)
                forClaude(Modifier.weight(1f))
            } else {
                forClaude(Modifier.fillMaxWidth())
                csv(Modifier.fillMaxWidth())
            }
        }
    }
    if (claude) ClaudeSheet(h, vm, prePkg = pkg) { claude = false }
    if (d.issues.isEmpty()) {
        KCard { KEmpty(if (status == "open") "No open issue".t() else "No match".t(), KIcons.Ok) }
        return
    }
    // default par server ka order (review wale pehle, severity, aakhri baar)
    val issues = when {
        sort == null || sort.isDefault -> d.issues
        sort.key == "last" -> d.issues.kSorted(sort.desc) { tsMs(it.lastSeen) }
        sort.key == "users" -> d.issues.kSorted(sort.desc) { it.users }
        sort.key == "events" -> d.issues.kSorted(sort.desc) { it.events }
        else -> d.issues.kSorted(sort.desc) { hSevRank(it.severity) }
    }
    KCard(list = true) {
        issues.forEachIndexed { i, x ->
            if (i > 0) KListDivider(true)
            IssueRow(x, nav)
        }
    }
}

// ---------------------------------------------------------------- 6. AI fixes

@Composable
private fun FixesTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int, sort: KSortState?) {
    var res by rememberSaveable { mutableStateOf("") }
    Poll(15_000, res, reload) { vm.loadFixes(res) }
    KLoadOnChange(res)
    FilterChips("Result".t(), listOf("" to "All".t(), "ok" to "Verified".t(), "fail" to "Failed".t(), "running" to "Running".t()), res) { res = it }
    val list = h.fixes?.takeIf { h.fixesKey == res } ?: run {
        if (h.error != null) HProblem(h) { vm.loadFixes(res) } else HSkelCards(3, 4)
        return
    }
    if (list.isEmpty()) {
        KCard { KEmpty("No AI fixes".t(), KIcons.Wand) }
        return
    }
    // default par server ka order (naye pehle)
    val shown = when {
        sort == null || sort.isDefault -> list
        sort.key == "app" -> list.kSorted(sort.desc) { it.app.ifBlank { it.pkg }.lowercase() }
        else -> list.kSorted(sort.desc) { tsMs(it.startedAt) }
    }
    shown.forEach { f -> FixHistoryCard(f) { nav.navigate("health/issue/${f.issueId}") } }
}

@Composable
private fun FixHistoryCard(f: HFix, onOpen: () -> Unit) {
    val c = Mfc.c
    val k = when (f.success) {
        true -> KKind.OK
        false -> KKind.BAD
        null -> KKind.ASK
    }
    KCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                f.app.ifBlank { f.pkg }, Modifier.weight(1f), color = c.ink, style = hStyle(15.sp, FontWeight.SemiBold, 20.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            KPill(statusText(f.status.ifBlank { "running" }), k)
        }
        Text(
            "ISS-${f.issueId} · " + tf("attempt %s", f.attempt) + " · " + tFull(f.startedAt), Modifier.padding(top = 2.dp),
            color = c.ink3, style = hStyle(13.sp, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (f.title.isNotBlank()) Text(
            f.title, Modifier.padding(top = 6.dp), color = c.ink, style = hStyle(14.sp, lh = 20.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TestMark("Unit".t(), f.unitOk)
            TestMark("Build".t(), f.buildOk)
            TestMark("Emulator".t(), f.deviceOk)
        }
        if (f.filesModified.isNotEmpty()) Text(
            tf("%s files changed", f.filesModified.size), Modifier.padding(top = 6.dp), color = c.ink3, style = hStyle(13.sp, lh = 18.sp),
        )
        if (f.failureReason.isNotBlank()) HNote(f.failureReason, KKind.BAD)
    }
}

// ---------------------------------------------------------------- 7. Builds (AAB)

@Composable
private fun BuildsTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int, sort: KSortState?) {
    var show by rememberSaveable { mutableStateOf("") }
    Poll(30_000, reload) { vm.loadBuilds() }
    FilterChips(
        "Show".t(), listOf("" to "All".t(), "review" to "Waiting for you".t(), "PUBLISHED" to "Published".t(), "DISCARDED" to "Discarded".t()), show,
    ) { show = it }
    val list = h.builds ?: run {
        if (h.error != null) HProblem(h) { vm.loadBuilds() } else HSkelCards(3, 3)
        return
    }
    val shown = list.filter {
        when (show) {
            "" -> true
            "review" -> it.reviewable
            else -> it.status == show
        }
    }.let { l ->
        // default par server ka order (aapka intezaar wale pehle, phir naye)
        when {
            sort == null || sort.isDefault -> l
            sort.key == "time" -> l.kSorted(sort.desc) { tsMs(it.createdAt) }
            sort.key == "app" -> l.kSorted(sort.desc) { it.app.ifBlank { it.pkg }.lowercase() }
            else -> l.kSorted(sort.desc) { if (it.reviewable) 1 else 0 }
        }
    }
    if (shown.isEmpty()) KCard { KEmpty("No builds yet".t(), KIcons.Download) }
    shown.forEach { BuildCard(it, h, vm, nav) }
}

// ---------------------------------------------------------------- 8. Logs

@Composable
private fun LogsTab(h: HState, vm: HealthVm, nav: NavHostController, reload: Int, sort: KSortState?) {
    var level by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    val c = Mfc.c
    Poll(15_000, level, q, reload) { vm.loadLogs(level, q.trim()) }
    KLoadOnChange(level)   // level chip = upar ki line (search text nahi)
    FilterChips("Level".t(), listOf("" to "All".t(), "error" to "Error".t(), "warn" to "Warning".t(), "ok" to "OK", "info" to "Info".t()), level) { level = it }
    KSearch(q, { q = it }, "Search logs".t())
    val list = h.logs?.takeIf { h.logsKey == "$level|${q.trim()}" } ?: run {
        if (h.error != null) HProblem(h) { vm.loadLogs(level, q) } else KSkelCard(8, title = false)
        return
    }
    // default par server ka order (naye pehle)
    val shown = when {
        sort == null || sort.isDefault -> list
        sort.key == "level" -> list.kSorted(sort.desc) { hLevelRank(it.level) }
        else -> list.kSorted(sort.desc) { tsMs(it.createdAt) }
    }
    KCard {
        if (shown.isEmpty()) KEmpty("No log lines".t(), KIcons.History)
        shown.forEach { e ->
            Column {
                EventRow(e, nav)
                if (e.pkg.isNotBlank()) Text(
                    e.pkg + " · " + tFull(e.createdAt), Modifier.padding(start = 20.dp, bottom = 4.dp), color = c.ink3,
                    style = hStyle(13.sp, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 9. Settings

@Composable
private fun HSettingsTab(h: HState, vm: HealthVm, reload: Int) {
    Poll(60_000, reload) { vm.loadSettings() }
    val s = h.settings ?: run {
        // Per app cards, Email, monitoring ka dhaancha
        if (h.error != null) HProblem(h) { vm.loadSettings() }
        else KSkeleton {
            KSkelSection(width = 80.dp)
            KSkelRows(3)
            KSkelSection(width = 60.dp)
            KSkelRows(3, lead = false, value = false)
            KSkelSection(width = 150.dp)
            KSkelRows(2, lead = false, value = false)
        }
        return
    }
    KSection("Per app".t(), trailing = { HSecNote(tf("%s apps", s.apps.size)) })
    s.apps.forEach { a ->
        KCard(list = true) {
            KRow(a.name.ifBlank { a.pkg }, sub = a.repo.ifBlank { "no repo linked".t() }, lead = { AppIcon(a.icon, a.name.ifBlank { a.pkg }, 40.dp) })
            KListDivider()
            HAppSwitches(a, h, vm)
        }
    }

    KSection("Email".t(), trailing = { HSecNote(if (s.emailConfigured) s.emailHint else "not set".t()) })
    KCard(list = true) {
        if (!s.emailConfigured) {
            KRow("Email".t(), sub = "email_to · health_config.php", subKind = KKind.WARN, lead = { KIconTile(KIcons.Dhyan, KKind.WARN) })
            KListDivider()
        }
        s.mail.forEachIndexed { i, m ->
            if (i > 0) KListDivider()
            HSwitchRow(m.label.t(), m.on, enabled = s.emailConfigured) { vm.mailSetting(m.key, it) }
        }
        KButton(
            "Send test email".t(), { vm.testEmail() }, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            kind = KBtn.OUTLINE, icon = KIcons.Send, enabled = s.emailConfigured, busy = "test_email" in h.busy,
        )
    }

    KSection("Scheduled monitoring".t(), trailing = {
        KPill(if (s.cronOk) "cron OK".t() else "cron not running".t(), if (s.cronOk) KKind.OK else KKind.BAD)
    })
    if (s.schedule.isNotEmpty()) KCard(list = true) {
        s.schedule.forEachIndexed { i, (l, e) ->
            if (i > 0) KListDivider()
            KRow(l.t(), sub = e.t())
        }
    }

    KSection("Integrations".t())
    KCard(list = true) {
        val aiK = if (s.aiEnabled) KKind.OK else KKind.WARN
        KRow("Claude AI", sub = if (s.aiEnabled) s.aiModel else "off — add key in Settings".t(), subKind = aiK, lead = { KIconTile(KIcons.Spark, aiK) })
        KListDivider(true)
        val ghK = if (s.github) KKind.OK else KKind.WARN
        KRow("GitHub (AI fix worker)", sub = if (s.github) "OK" else "token not set".t(), subKind = ghK, lead = { KIconTile(KIcons.Fix, ghK) })
        s.consoles.forEach { con ->
            KListDivider(true)
            val k = hStatusKind(con.status)
            KRow(con.name, sub = statusText(con.status), subKind = k, lead = { KIconTile(KIcons.Globe, k) })
        }
    }
}

// ---------------------------------------------------------------- purane public helpers (signature wahi)

@Composable
fun SearchField(v: String, hint: String, onChange: (String) -> Unit) {
    KSearch(v, onChange, hint)
}

/** Switch wali row (list card ke andar, kit KRow + KSwitch). */
@Composable
fun SwitchLine(label: String, on: Boolean, enabled: Boolean = true, sub: String? = null, onChange: (Boolean) -> Unit) {
    HSwitchRow(label, on, enabled, sub, null, onChange)
}
