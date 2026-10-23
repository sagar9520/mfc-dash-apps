package com.myfuturecode.mfcdash.ui.screens.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.PageLoad
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.StudioVm
import com.myfuturecode.mfcdash.ui.kit.KAvatar
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
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLoadOnChange
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KPullAction
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KProgress
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSearch
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSkelCard
import com.myfuturecode.mfcdash.ui.kit.KSkelHero
import com.myfuturecode.mfcdash.ui.kit.KSkelRows
import com.myfuturecode.mfcdash.ui.kit.KSkelSection
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KSectionLink
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSmallAction
import com.myfuturecode.mfcdash.ui.kit.KSortButton
import com.myfuturecode.mfcdash.ui.kit.KSortKind
import com.myfuturecode.mfcdash.ui.kit.KSortPill
import com.myfuturecode.mfcdash.ui.kit.KSortState
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KSteps
import com.myfuturecode.mfcdash.ui.kit.KSwitch
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kSorted
import com.myfuturecode.mfcdash.ui.kit.rememberKSort
import com.myfuturecode.mfcdash.ui.kit.word
import com.myfuturecode.mfcdash.ui.screens.moneySigned
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.l
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.obj
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.strings
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs
import com.myfuturecode.mfcdash.util.usd
import kotlinx.coroutines.delay
import org.json.JSONObject

val STABS = listOf(
    "overview" to "Overview", "research" to "Research & Ranking", "discovery" to "Daily discovery", "mine" to "Your apps", "projects" to "Projects", "approvals" to "Approvals", "history" to "History",
    "runs" to "Runs", "sources" to "Data sources", "settings" to "Settings",
)

private fun tabIcon(k: String): ImageVector = when (k) {
    "overview" -> KIcons.Bulb
    "research" -> KIcons.Chart
    "discovery" -> KIcons.Search
    "mine" -> KIcons.Rupee
    "projects" -> KIcons.Apps
    "approvals" -> KIcons.DoneAll
    "history" -> KIcons.History
    "runs" -> KIcons.Play
    "sources" -> KIcons.Globe
    "settings" -> KIcons.Gear
    else -> KIcons.More
}

private fun tabLabel(k: String): String = STABS.firstOrNull { it.first == k }?.second?.t() ?: k

// Sort (bar ka Sort button): har list tab ka apna; default = server ka aaj ka order (bina chhede wahi list).
// research: score zyada -> kam; projects: aapka intezaar (gate) wale pehle, phir naye; approvals (recent) / runs: naye pehle.
// mine (Your apps): profit zyada -> kam (Apps aur Countries list).
// History (server pages, Newer / Older) aur Data sources (config order) par Sort nahi.
private val S_SORT_DEF = mapOf(
    "research" to ("score" to true), "mine" to MINE_SORT_DEF, "projects" to ("status" to true), "approvals" to ("time" to true), "runs" to ("time" to true),
)
private val S_SORT_KINDS = mapOf("time" to KSortKind.DATE, "updated" to KSortKind.DATE)

private fun sSortOpts(tab: String): List<Pair<String, String>> = when (tab) {
    "research" -> listOf("score" to "Score".t(), "updated" to "Updated".t(), "name" to "Name".t())
    "projects" -> listOf("status" to "Waiting for you".t(), "updated" to "Updated".t(), "name" to "Name".t())
    "approvals" -> listOf("time" to "Time".t(), "gate" to "Gate".t(), "name" to "Name".t())
    "runs" -> listOf("time" to "Time".t(), "cost" to "Cost".t(), "errors" to "Errors".t())
    "mine" -> mineSortOpts()
    else -> emptyList()
}

/**
 * AI App Research Studio: research -> ranking -> Gate 1 -> competitor analysis -> Gate 2. Kabhi khud publish nahi karta.
 * Overview = mockup (hero, OK chahiye, top ideas, budget); baaki tab ⋮ menu / "Aur" list se, back = overview.
 * run > 0 = run detail ka "See findings" (us run ki Daily discovery).
 */
@Composable
fun StudioScreen(vm: StudioVm, nav: NavHostController, startTab: String, run: Long = 0L) {
    val g by vm.s.collectAsStateWithLifecycle()
    val start = if (startTab == "ranking") "research" else startTab
    val first = if (STABS.any { it.first == start }) start else "overview"
    var tab by rememberSaveable { mutableStateOf(first) }
    var idea by remember { mutableStateOf(false) }
    val reload = remember { mutableIntStateOf(0) }
    val r = reload.intValue
    // Daily discovery: calendar se chuna din (artifact id) / run detail wala run
    var discSel by rememberSaveable { mutableStateOf("") }
    var discRun by rememberSaveable { mutableLongStateOf(run) }
    val discP = discParams(discSel, discRun)
    val go: (String) -> Unit = { k ->
        // dusre tab se discovery = sabse naya din
        if (k == "discovery" && tab != "discovery") {
            discSel = ""
            discRun = 0L
        }
        tab = k
    }
    KLoadOnChange(tab)   // user ne tab badla = upar ki line
    val back: () -> Unit = {
        if (tab != first) {
            tab = first
        } else if (!nav.popBackStack()) {
            nav.navigate("home")
        }
    }
    BackHandler(enabled = tab != first) { tab = first }
    // har list tab ka apna sort (tab badalne par yaad); baaki tabs par null
    val sorts = S_SORT_DEF.mapValues { (k, d) -> key(k) { rememberKSort(d.first, d.second) } }
    val sort = sorts[tab]
    // Your apps: Apps | Countries | For research (yahan yaad, taaki "For research" par bar ka Sort chhupe)
    var mineSeg by rememberSaveable { mutableIntStateOf(0) }
    val bar: @Composable ColumnScope.() -> Unit = {
        val barSort = if (tab == "mine" && !mineSortOn(mineSeg, isWide())) null else sort
        StudioBar(tab, g, back, go, { idea = true }, { reload.intValue++ }, barSort, discP) { discSel = it }
    }
    if (tab == "overview") OverviewPage(g, vm, nav, r, bar, go)
    else key(tab) { TabBody(tab, g, vm, nav, r, go, sort, bar, mineSeg, discP) { mineSeg = it } }
    if (idea) IdeaDialog(g, vm, nav) { idea = false }
}

/** Upar ki patti: back + naam + ⋮ (saare tabs, apna idea, refresh). Daily discovery: calendar (pichhle din). */
@Composable
private fun StudioBar(
    tab: String, g: GState, back: () -> Unit, go: (String) -> Unit, onIdea: () -> Unit, onRefresh: () -> Unit, sort: KSortState? = null,
    discP: Map<String, String> = emptyMap(), onDiscPick: (String) -> Unit = {},
) {
    KPullAction(onRefresh)   // refresh sirf ⋮ menu me: upar se kheencho = yahi
    val o = g.view("overview")
    var menu by remember { mutableStateOf(false) }
    val pend = o?.arr("pending")?.length() ?: 0
    val sub = when (tab) {
        "overview" -> o?.let { tf("%s ideas", num(ideaTotal(it).toDouble())) } ?: sUpdated(g)
        "mine" -> mineSub(g) ?: "Research Studio".t()
        "discovery" -> discSub(g, discP) ?: "Research Studio".t()
        else -> "Research Studio".t()
    }
    KDetailBar(
        if (tab == "overview") "Research Studio".t() else tabLabel(tab), back, sub = sub,
        actions = {
            // filter nahi (list ke upar dropdowns), isliye Sort pehla
            if (sort != null) KSortButton(sort, sSortOpts(tab), S_SORT_KINDS)
            if (tab == "research") KBarIcon(KIcons.Plus, "Add your own idea".t(), onIdea)
            if (tab == "discovery") DiscDayPicker(g, discP, onDiscPick)
            Box {
                KBarIcon(KIcons.More, "More".t(), { menu = true }, badge = if (tab == "overview") 0 else pend, badgeUrgent = false)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    KPageTokens {
                        val c = Mfc.c
                        STABS.filter { it.first != tab }.forEach { (k, l) ->
                            DropdownMenuItem(
                                text = { SText(l.t(), 15.sp, c.ink, weight = FontWeight.Medium, lh = 20.sp) },
                                onClick = { menu = false; go(k) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                leadingIcon = { Icon(tabIcon(k), null, tint = c.ink2, modifier = Modifier.size(20.dp)) },
                                trailingIcon = if (k == "approvals" && pend > 0) { { KBadge(pend, urgent = false, ring = Color.Transparent) } } else null,
                            )
                        }
                        DropdownMenuItem(
                            text = { SText("Add your own idea".t(), 15.sp, c.ink, weight = FontWeight.Medium, lh = 20.sp) },
                            onClick = { menu = false; onIdea() },
                            modifier = Modifier.heightIn(min = 48.dp),
                            leadingIcon = { Icon(KIcons.Plus, null, tint = c.brand, modifier = Modifier.size(20.dp)) },
                        )
                        DropdownMenuItem(
                            text = { SText("Refresh".t(), 15.sp, c.ink, weight = FontWeight.Medium, lh = 20.sp) },
                            onClick = { menu = false; PageLoad.start(); onRefresh() },
                            modifier = Modifier.heightIn(min = 48.dp),
                            leadingIcon = { Icon(KIcons.Refresh, null, tint = c.ink2, modifier = Modifier.size(20.dp)) },
                        )
                    }
                }
            }
        },
    )
}

/** Tab ka page. "mine" / "discovery" ka hero top me (Overview jaisa), isliye woh apna KScreen khud banate hain. */
@Composable
private fun TabBody(
    tab: String, g: GState, vm: StudioVm, nav: NavHostController, reload: Int, go: (String) -> Unit, sort: KSortState?,
    bar: @Composable ColumnScope.() -> Unit, mineSeg: Int, discP: Map<String, String>, onMineSeg: (Int) -> Unit,
) {
    when (tab) {
        "mine" -> MinePage(g, vm, nav, reload, bar, sort, mineSeg, onMineSeg)
        "discovery" -> DiscoveryPage(g, vm, reload, bar, discP)
        else -> KScreen(top = bar) { TabList(tab, g, vm, nav, reload, go, sort) }
    }
}

@Composable
private fun TabList(tab: String, g: GState, vm: StudioVm, nav: NavHostController, reload: Int, go: (String) -> Unit, sort: KSortState?) {
    when (tab) {
        "research" -> ResearchTab(g, vm, nav, reload, sort)
        "projects" -> ProjectsTab(g, vm, nav, reload, sort)
        "approvals" -> ApprovalsTab(g, vm, nav, reload, go, sort)
        "history" -> HistoryTab(g, vm, nav, reload)
        "runs" -> RunsTab(g, vm, nav, reload, sort)
        "sources" -> SourcesTab(g, vm, reload)
        "settings" -> SettingsTab(g, vm, reload)
        else -> {}
    }
}

// ---------------------------------------------------------------- Overview (mockup s08)

/** Gate 2+ par aapka OK chahiye (projects). Gate 1 = naye ideas chunna. */
private fun waitingGates(o: JSONObject): List<JSONObject> = o.arr("pending").objs().filter { it.d("gate") >= 2 }

/** naye + dekh rahe + OK chahiye. */
private fun ideaTotal(o: JSONObject): Int {
    val cnt = o.obj("counts")
    return cnt.d("ideas_new").toInt() + cnt.d("ideas_watch").toInt() + waitingGates(o).size
}

/** Hero ka pill: cron / AI key / budget ki dikkat pehle, phir "OK chahiye", warna Theek. */
private fun studioState(o: JSONObject): Pair<KKind, String> {
    val sp = o.obj("spend")
    val lim = sp.d("limit_usd")
    val used = sp.d("month_usd")
    val cron = tsMs(o.sn("cron_last"))
    val now = tsMs(o.sn("server_time")) ?: System.currentTimeMillis()
    val key = o.arr("sources").objs().firstOrNull { it.s("id") == "claude_web" }?.s("status")
    return when {
        cron == null -> KKind.BAD to "Cron off".t()
        key == "NOT_CONFIGURED" -> KKind.BAD to "No AI key".t()
        lim <= 0.0 -> KKind.BAD to "No budget".t()
        used >= lim -> KKind.BAD to "Budget full".t()
        now - cron > 20 * 60_000L -> KKind.WARN to "Cron late".t()
        o.arr("pending").length() > 0 -> KKind.ASK to KKind.ASK.word()
        else -> KKind.OK to KKind.OK.word()
    }
}

@Composable
private fun OverviewPage(g: GState, vm: StudioVm, nav: NavHostController, reload: Int, bar: @Composable ColumnScope.() -> Unit, go: (String) -> Unit) {
    SPoll(60_000, reload) { vm.load("overview") }
    val o = g.view("overview")
    if (isWide()) {
        // Fold: baayein mockup wala kram (hero, OK chahiye, top ideas, budget, schedule), daayein Aur + haal ke runs / activity
        KScreen(top = bar) {
            SLoad(g, o, { vm.load("overview") }, { OverviewSkel(true) }) { ov ->
                KTwoPane(
                    left = {
                        StudioHero(ov)
                        OkSection(ov, nav, go)
                        DiscoverySection(ov, go)
                        MineSection(ov, g, vm, go)
                        TopIdeas(ov, nav, go)
                        BudgetCard(ov, go)
                        ScheduleCard(ov, go)
                    },
                    right = {
                        MoreCard(ov, go)
                        RecentCards(ov, nav, go)
                        NeverPublishNote()
                    },
                )
            }
        }
    } else {
        KScreen(top = { bar(); if (o != null) StudioHero(o) else if (sSkelOn(g, o)) KSkelHero(KTint.Lav) }) {
            SLoad(g, o, { vm.load("overview") }, { OverviewSkel(false) }) { ov ->
                OkSection(ov, nav, go)
                DiscoverySection(ov, go)
                MineSection(ov, g, vm, go)
                TopIdeas(ov, nav, go)
                BudgetCard(ov, go)
                MoreCard(ov, go)
                ScheduleCard(ov, go)
                RecentCards(ov, nav, go)
                NeverPublishNote()
            }
        }
    }
}

/** Overview ka skeleton: OK chahiye, top ideas, budget, aur ki list. Fold: hero baayein. */
@Composable
private fun OverviewSkel(wide: Boolean) {
    KSkeleton {
        val left: @Composable ColumnScope.() -> Unit = {
            KSkelSection()
            KSkelRows(2)
            KSkelSection(width = 130.dp)   // Latest discovery
            KSkelRows(3)
            KSkelSection(width = 100.dp)   // Your apps
            KSkelCard(3)
            KSkelSection(width = 110.dp)
            KSkelRows(3)
            KSkelCard(3)
        }
        val right: @Composable ColumnScope.() -> Unit = {
            KSkelSection(width = 60.dp)
            KSkelRows(4, value = false)
        }
        if (wide) KTwoPane(left = { KSkelHero(KTint.Lav); left() }, right = right) else { left(); right() }
    }
}

/** Lavender hero: kitne ideas aapke OK ka wait + naye > dekh rahe > OK chahiye. */
@Composable
private fun StudioHero(o: JSONObject) {
    KHero(KTint.Lav) {
        val cnt = o.obj("counts")
        val newN = cnt.d("ideas_new").toInt()
        val watchN = cnt.d("ideas_watch").toInt()
        val gates = waitingGates(o).size
        val (k, word) = studioState(o)
        KHeroHead("Waiting for your OK".t(), icon = KIcons.Bulb, trailing = { SHeroPill(word, k) })
        KBigNum(num(gates.toDouble()), suffix = " " + "ideas".t(), size = 48.sp)
        KSteps(
            listOf(
                num(newN.toDouble()) to "new".t(),
                num(watchN.toDouble()) to "watching".t(),
                num(gates.toDouble()) to "needs OK".t(),
            ),
            active = when {
                gates > 0 -> 2
                newN > 0 -> 0
                watchN > 0 -> 1
                else -> -1
            },
        )
    }
}

@Composable
private fun OkSection(o: JSONObject, nav: NavHostController, go: (String) -> Unit) {
    val pend = o.arr("pending").objs()
    val gates = pend.count { it.d("gate") >= 2 }
    KSection("Needs OK".t(), trailing = { if (gates > 0) KPill(tf("%s ideas", num(gates.toDouble())), KKind.ASK) })
    PendingCard(pend, nav, go)
}

/** Pending faisle: Gate 2+ (project, "OK do") aur Gate 1 (naye ideas, "Chuno"). OK hamesha detail me confirm ke saath. */
@Composable
private fun PendingCard(pend: List<JSONObject>, nav: NavHostController, go: ((String) -> Unit)?) {
    if (pend.isEmpty()) {
        KCard { KEmpty("Nothing waiting".t(), KIcons.Ok) }
        return
    }
    // project wale pehle (mockup), naye ideas aakhir me
    val list = pend.sortedBy { if (it.d("gate") >= 2) 0 else 1 }
    KCard(list = true) {
        list.forEachIndexed { i, p ->
            if (i > 0) KListDivider(true)
            PendingItem(p, nav, go)
        }
    }
}

@Composable
private fun PendingItem(p: JSONObject, nav: NavHostController, go: ((String) -> Unit)?) {
    val gate = p.d("gate").toInt()
    val open = { sOpen(p.s("route"), nav, go) }
    if (gate <= 1) {
        val n = Regex("\\((\\d+)").find(p.s("title"))?.groupValues?.getOrNull(1)
        KRow(
            "New ideas".t(), sub = tf("Gate %s", 1) + (n?.let { " · " + tf("%s new", it) } ?: ""), subKind = KKind.ASK,
            lead = { KIconTile(KIcons.Bulb, KKind.ASK) }, trailing = { KSmallAction("Select".t(), open) }, onClick = open,
        )
    } else {
        val name = p.s("title").substringAfterLast(" · ").ifBlank { p.s("title") }
        KRow(
            name, sub = gateLine(p), subKind = KKind.ASK, lead = { KAvatar(name) },
            trailing = { KSmallAction("Give OK".t(), open) }, onClick = open,
        )
    }
}

/** "Gate 2 · 2 din se" (detail: "PRJ-004 · since 2026-10-20 09:14:33 UTC"). */
private fun gateLine(p: JSONObject): String {
    val gate = tf("Gate %s", p.d("gate").toInt())
    val ms = Regex("since (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})").find(p.s("detail"))?.groupValues?.getOrNull(1)?.let { tsMs(it) }
        ?: return gate
    val days = ((System.currentTimeMillis() - ms) / 86_400_000L).coerceAtLeast(0L)
    return gate + " · " + when (days) {
        0L -> "today".t()
        1L -> "for 1 day".t()
        else -> tf("for %s days", days)
    }
}

/** Kept for old callers: ek pending faisla (tap = route). */
@Composable
fun PendingRow(p: JSONObject, nav: NavHostController) {
    KCard(list = true) { PendingItem(p, nav, null) }
}

@Composable
private fun TopIdeas(o: JSONObject, nav: NavHostController, go: (String) -> Unit) {
    val top = o.arr("top").objs()
    KSection("Top ideas".t(), trailing = { KSectionLink("See all".t()) { go("research") } })
    if (top.isEmpty()) {
        KCard { KEmpty("No ideas yet".t(), KIcons.Bulb) }
        return
    }
    KCard(list = true) {
        top.forEachIndexed { i, x ->
            if (i > 0) KListDivider(true)
            IdeaRow(x, "best", null, null, compact = true) { nav.navigate("studio/opp/${x.l("id")}") }
        }
    }
}

/** Budget ek line me: label, chhota meter (100% par rukta), "$12.40 / $40". Tap = settings. */
@Composable
private fun BudgetCard(o: JSONObject, go: (String) -> Unit) {
    val c = Mfc.c
    val sp = o.obj("spend")
    val used = sp.d("month_usd")
    val lim = sp.d("limit_usd")
    val f = if (lim > 0) (used / lim).toFloat().coerceIn(0f, 1f) else 0f
    val full = lim <= 0 || used >= lim
    val col = when {
        full -> c.zRed
        f >= .8f -> c.zAmber
        else -> c.brand
    }
    KCard(onClick = { go("settings") }, padding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(KIcons.Wallet, null, tint = c.ink2, modifier = Modifier.size(20.dp))
                SText("Budget".t(), 15.sp, c.ink2, weight = FontWeight.SemiBold, lh = 20.sp)
            }
            KProgress(f, col, Modifier.weight(1f), top = 0.dp)
            Text(
                buildAnnotatedString {
                    append(usd(used))
                    withStyle(SpanStyle(color = c.ink3, fontWeight = FontWeight.Medium, fontSize = 15.sp)) {
                        append(" / " + if (lim > 0) usd(lim, if (lim % 1.0 == 0.0) 0 else 2) else "–")
                    }
                },
                color = if (full) c.red else c.ink, style = sStyle(16.sp, FontWeight.Bold, 20.sp), maxLines = 1, softWrap = false,
            )
        }
    }
}

/** "Aur": baaki tabs ek list me (ginti / status ke saath). */
@Composable
private fun MoreCard(o: JSONObject, go: (String) -> Unit) {
    val cnt = o.obj("counts")
    val pend = o.arr("pending").length()
    val src = o.arr("sources").objs()
    val srcOk = src.count { it.s("status") == "OK" }
    val srcErr = src.count { it.s("status") == "ERROR" }
    val lastRun = o.arr("runs").objs().firstOrNull()
    KSection("More".t())
    KCard(list = true) {
        KRow(
            "Research & Ranking".t(), sub = tf("%s evidence", num(cnt.d("evidence"))),
            lead = { KIconTile(tabIcon("research")) }, chevron = true, onClick = { go("research") },
        )
        KListDivider(true)
        // key hi nahi (purana server / cache) = row nahi; null = abhi koi khoj nahi
        if (o.has("discovery")) {
            KRow(
                "Daily discovery".t(), sub = discMoreSub(o),
                lead = { KIconTile(tabIcon("discovery")) }, chevron = true, onClick = { go("discovery") },
            )
            KListDivider(true)
        }
        // key hi nahi (purana server / cache) = row nahi; null = job kabhi nahi chala
        if (o.has("mine")) {
            val mine = o.optJSONObject("mine")
            KRow(
                "Your apps".t(),
                sub = when {
                    mine != null -> tf("Profit %s", moneySigned(mine.d("profit_inr"))) + " · " +
                        (mine.dn("roas")?.let { "ROAS " + roasTxt(it) } ?: "No ad spend".t())
                    mineRunning(o) -> "Running".t()
                    else -> "Not run yet".t()
                },
                subKind = if (mine == null && mineRunning(o)) KKind.ASK else null,
                lead = { KIconTile(tabIcon("mine")) }, chevron = true, onClick = { go("mine") },
            )
            KListDivider(true)
        }
        KRow(
            "Projects".t(),
            sub = tf("%s active", num(cnt.d("projects_active"))) + " · " + tf("%s published", num(cnt.d("published"))),
            lead = { KIconTile(tabIcon("projects")) }, chevron = true, onClick = { go("projects") },
        )
        KListDivider(true)
        KRow(
            "Approvals".t(), sub = if (pend > 0) tf("%s waiting", pend) else "Nothing waiting".t(), subKind = if (pend > 0) KKind.ASK else null,
            lead = { KIconTile(tabIcon("approvals")) }, chevron = true, onClick = { go("approvals") },
        )
        KListDivider(true)
        KRow(
            "Runs".t(), sub = lastRun?.let { jobName(it.s("job")) + " · " + stText(it.s("status")) }, subKind = lastRun?.let { stKind(it.s("status")) },
            lead = { KIconTile(tabIcon("runs")) }, chevron = true, onClick = { go("runs") },
        )
        KListDivider(true)
        KRow(
            "Data sources".t(),
            sub = tf("%s/%s OK", srcOk, src.size) + (if (srcErr > 0) " · " + tf("%s error", srcErr) else ""),
            subKind = if (srcErr > 0) KKind.BAD else if (srcOk > 0) KKind.OK else KKind.WARN,
            lead = { KIconTile(tabIcon("sources")) }, chevron = true, onClick = { go("sources") },
        )
        KListDivider(true)
        KRow("History".t(), lead = { KIconTile(tabIcon("history")) }, chevron = true, onClick = { go("history") })
        KListDivider(true)
        KRow("Settings".t(), lead = { KIconTile(tabIcon("settings")) }, chevron = true, onClick = { go("settings") })
    }
}

/** Cron + roz / hafte / mahine ke runs (chalu / band, aakhri baar). Tap = settings. */
@Composable
private fun ScheduleCard(o: JSONObject, go: (String) -> Unit) {
    val c = Mfc.c
    val sch = o.obj("schedule")
    val cronTs = o.sn("cron_last")
    val cron = tsMs(cronTs)
    val now = tsMs(o.sn("server_time")) ?: System.currentTimeMillis()
    val ck = when {
        cron == null -> KKind.BAD
        now - cron > 20 * 60_000L -> KKind.WARN
        else -> KKind.OK
    }
    KSection("Schedule".t(), trailing = { KPill(if (cron == null) "Cron off".t() else "Cron".t() + " · " + sAgo(cronTs), ck) })
    KCard(list = true) {
        listOf(
            Triple("Daily discovery", "daily", "last_daily"),
            Triple("Weekly ranking", "weekly", "last_weekly"),
            Triple("Monthly deep research", "monthly", "last_monthly"),
        ).forEachIndexed { i, (l, k, last) ->
            if (i > 0) KListDivider()
            val on = sch.b(k)
            KRow(
                l.t(), sub = "last".t() + " " + (sch.sn(last) ?: "–"), value = if (on) "On".t() else "Off".t(),
                valueColor = if (on) c.green else c.ink3, valuePlain = true, onClick = { go("settings") },
            )
        }
    }
}

/** Haal ke runs (3) aur activity (5); "Sab dekho" = poora tab. */
@Composable
private fun RecentCards(o: JSONObject, nav: NavHostController, go: (String) -> Unit) {
    val runs = o.arr("runs").objs()
    if (runs.isNotEmpty()) {
        KSection("Recent runs".t(), trailing = { KSectionLink("See all".t()) { go("runs") } })
        KCard(list = true) {
            runs.take(3).forEachIndexed { i, x ->
                if (i > 0) KListDivider(true)
                SRunRow(x, nav)
            }
        }
    }
    val act = o.arr("activity").objs()
    if (act.isNotEmpty()) {
        KSection("Activity".t(), trailing = { KSectionLink("See all".t()) { go("history") } })
        KCard(list = true) {
            act.take(5).forEachIndexed { i, e ->
                if (i > 0) KListDivider(true)
                SEventRow(e, nav)
            }
        }
    }
}

// ---------------------------------------------------------------- Research & ranking (Gate 1)

@Composable
private fun ResearchTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int, sort: KSortState?) {
    var engine by rememberSaveable { mutableStateOf("organic") }
    var tier by rememberSaveable { mutableStateOf("") }
    var cc by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("active") }
    var sel by remember { mutableStateOf(setOf<Long>()) }
    var approve by remember { mutableStateOf(false) }
    val f = mapOf("engine" to engine, "tier" to tier, "cc" to cc, "category" to cat, "status" to status)
    SPoll(90_000, f, reload) { vm.load("research", f) }
    KLoadOnChange(f)   // engine / filter = upar ki line
    val last = g.view("research", f)
    // filter ke options pichhle kisi bhi research jawab se (load ke beech gayab na hon)
    val opts = last ?: g.views.entries.firstOrNull { it.key.startsWith("research?") }?.value
    val engines = listOf("organic", "paid", "combined")
    val controls: @Composable () -> Unit = {
        KSegTabs(listOf("Organic".t(), "Paid".t(), "Combined".t()), engines.indexOf(engine).coerceAtLeast(0), { engine = engines[it] })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SDrop(
                "Status".t(),
                listOf(
                    "active" to "Active".t(), "NEW" to "New".t(), "WATCH" to "Watching".t(), "SELECTED" to "Selected".t(),
                    "REJECTED" to "Rejected".t(), "EVALUATING" to "Evaluating".t(),
                ),
                status,
            ) { status = it }
            SDrop("Tier".t(), listOf("" to "All".t(), "1" to "Tier 1", "2" to "Tier 2", "3" to "Tier 3"), tier) { tier = it }
            SDrop(
                "Country".t(),
                listOf("" to "All".t()) + opts?.arr("countries")?.objs().orEmpty().map { it.s("cc") to (it.s("cc") + " " + it.s("name")) },
                cc,
            ) { cc = it }
            SDrop("Category".t(), listOf("" to "All".t()) + opts?.arr("categories")?.strings().orEmpty().map { it to it }, cat) { cat = it }
        }
        RankingCard(last, g, vm)
        if (sel.isNotEmpty()) SelectionCard(sel.size, { approve = true }, { nav.navigate("studio/compare/" + sel.joinToString(",")) }, { sel = emptySet() })
    }
    val list: @Composable () -> Unit = {
        SLoad(g, last, { vm.load("research", f) }) { o ->
            // default par server ka order (is engine ka score zyada -> kam, bina score aakhir me)
            val items = o.arr("items").objs().let { l ->
                when {
                    sort == null || sort.isDefault -> l
                    sort.key == "updated" -> l.kSorted(sort.desc) { tsMs(it.s("updated_at")) }
                    sort.key == "name" -> l.kSorted(sort.desc) { it.s("title").lowercase() }
                    else -> l.kSorted(sort.desc) { it.dn(engine) }
                }
            }
            if (sort != null) KSortPill(sort, sSortOpts("research"), S_SORT_KINDS)
            if (items.isEmpty()) KCard { KEmpty("No ideas".t(), KIcons.Bulb) }
            else KCard(list = true) {
                items.forEachIndexed { i, x ->
                    if (i > 0) KListDivider(true)
                    val id = x.l("id")
                    val can = x.s("status") in setOf("NEW", "WATCH")
                    IdeaRow(x, engine, if (can) id in sel else null, if (can) ({ sel = if (id in sel) sel - id else sel + id }) else null) {
                        nav.navigate("studio/opp/$id")
                    }
                }
            }
        }
    }
    if (isWide()) KTwoPane(left = { controls() }, right = { list() })
    else {
        controls()
        list()
    }
    if (approve) TextAskDialog(
        "Approve Gate 1?".t(),
        tf("%s ideas will become projects. Competitor research (top 3 per idea) and a feature spec will be prepared, then Gate 2 waits for you. Nothing is built or published.", sel.size),
        "Note (optional)".t(), "Approve".t(), required = false, busy = "gate1" in g.busy, onDismiss = { approve = false },
    ) { note ->
        val fields = HashMap<String, String>()
        sel.forEachIndexed { i, id -> fields["select[$i]"] = id.toString() }
        fields["note"] = note
        vm.act("gate1", fields, "gate1", ok = "Approved. Competitor analysis queued.".t()) {
            approve = false; sel = emptySet(); vm.load("research", f); vm.load("overview")
        }
    }
}

/** Pichhli ranking (version, status, kab) + "Abhi chalao". */
@Composable
private fun RankingCard(last: JSONObject?, g: GState, vm: StudioVm) {
    val lr = last?.optJSONObject("last_ranking")
    KCard(list = true) {
        KRow(
            if (lr != null) tf("Ranking v%s", lr.d("version").toInt()) else "No ranking yet".t(),
            sub = lr?.let { stText(it.s("status")) + " · " + sAgo(it.sn("finished_at") ?: it.s("queued_at")) },
            subKind = lr?.let { stKind(it.s("status")) },
            lead = { KIconTile(KIcons.Sort) },
            trailing = {
                KSmallAction("Run now".t(), {
                    vm.act("run", mapOf("job" to "rank"), "run_rank", ok = "Ranking queued. It starts within 5 minutes.".t())
                }, enabled = "run_rank" !in g.busy)
            },
        )
    }
}

/** Chune hue ideas: Gate 1 approve (1-5) / compare (2-5) / hatao. */
@Composable
private fun SelectionCard(n: Int, onApprove: () -> Unit, onCompare: () -> Unit, onClear: () -> Unit) {
    val c = Mfc.c
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SText(tf("%s ideas selected", n), 16.sp, c.ink, Modifier.weight(1f), weight = FontWeight.Bold, lh = 22.sp)
            KSmallAction("Clear".t(), onClear)
        }
        if (n > 5) KStatus("Max 5 ideas".t(), KKind.BAD, Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KButton("Approve".t(), onApprove, Modifier.weight(1f), icon = KIcons.Check, enabled = n in 1..5)
            KButton("Compare".t(), onCompare, Modifier.weight(1f), kind = KBtn.SOFT, enabled = n in 2..5)
        }
    }
}

// ---------------------------------------------------------------- Projects

@Composable
private fun ProjectsTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int, sort: KSortState?) {
    SPoll(60_000, reload) { vm.load("projects") }
    SLoad(g, g.view("projects"), { vm.load("projects") }) { o ->
        // default par server ka order (gate par aapka intezaar wale pehle, phir naye badlav)
        val items = o.arr("items").objs().let { l ->
            when {
                sort == null || sort.isDefault -> l
                sort.key == "updated" -> l.kSorted(sort.desc) { tsMs(it.s("updated_at")) }
                sort.key == "name" -> l.kSorted(sort.desc) { it.s("name").lowercase() }
                else -> l.kSorted(sort.desc) { if (it.dn("next_gate") != null) 1 else 0 }
            }
        }
        if (sort != null) KSortPill(sort, sSortOpts("projects"), S_SORT_KINDS)
        if (items.isEmpty()) KCard { KEmpty("No projects yet".t(), KIcons.Apps) }
        else if (isWide()) KTwoPane(
            left = { items.filterIndexed { i, _ -> i % 2 == 0 }.forEach { ProjectCard(it, nav) } },
            right = { items.filterIndexed { i, _ -> i % 2 == 1 }.forEach { ProjectCard(it, nav) } },
        )
        else items.forEach { ProjectCard(it, nav) }
        NeverPublishNote()
    }
}

/** Project: avatar + naam, PRJ · stage · kab, gate pill, 8 gates ki patti, blocker. */
@Composable
fun ProjectCard(p: JSONObject, nav: NavHostController) {
    val c = Mfc.c
    val ng = p.dn("next_gate")?.toInt()
    val stage = p.s("stage")
    KCard(onClick = { nav.navigate("studio/project/${p.l("id")}") }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KAvatar(p.s("name"))
            Column(Modifier.weight(1f)) {
                SText(p.s("name"), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
                SText(listOf(p.s("puid"), stText(stage), sAgo(p.s("updated_at"))).filter { it.isNotBlank() }.joinToString(" · "), 13.sp, c.ink2, lh = 18.sp)
            }
            if (ng != null) KPill(tf("Gate %s", ng), KKind.ASK) else SStatusPill(stage)
        }
        SGateStrip(p.arr("gates").objs(), Modifier.padding(top = 14.dp))
        p.sn("blocker")?.let { KStatus(it, KKind.WARN, Modifier.padding(top = 10.dp)) }
    }
}

// ---------------------------------------------------------------- Approvals

@Composable
private fun ApprovalsTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int, go: (String) -> Unit, sort: KSortState?) {
    SPoll(60_000, reload) { vm.load("approvals") }
    SLoad(g, g.view("approvals"), { vm.load("approvals") }) { o ->
        val pend = o.arr("pending").objs()
        // Sort = "Recent decisions" (badi list); default par server ka order (naye pehle). Intezaar wali list jaisi hai.
        val rec = o.arr("recent").objs().let { l ->
            when {
                sort == null || sort.isDefault -> l
                sort.key == "gate" -> l.kSorted(sort.desc) { it.dn("gate") }
                sort.key == "name" -> l.kSorted(sort.desc) { it.s("name").lowercase() }
                else -> l.kSorted(sort.desc) { tsMs(it.s("created_at")) }
            }
        }
        val waiting: @Composable () -> Unit = {
            KSection("Waiting for your decision".t(), trailing = { if (pend.isNotEmpty()) KPill(num(pend.size.toDouble()), KKind.ASK) })
            PendingCard(pend, nav, go)
        }
        val recent: @Composable () -> Unit = {
            KSection("Recent decisions".t())
            if (sort != null) KSortPill(sort, sSortOpts("approvals"), S_SORT_KINDS)
            if (rec.isEmpty()) KCard { KEmpty("No decisions yet".t(), KIcons.History) }
            else KCard(list = true) {
                rec.forEachIndexed { i, a ->
                    if (i > 0) KListDivider(true)
                    DecisionRow(a, nav)
                }
            }
        }
        if (isWide()) KTwoPane(left = { waiting() }, right = { recent() })
        else {
            waiting()
            recent()
        }
    }
}

@Composable
private fun DecisionRow(a: JSONObject, nav: NavHostController) {
    val route = a.dn("project_id")?.let { "studio/project/${it.toLong()}" } ?: a.dn("opp_id")?.let { "studio/opp/${it.toLong()}" }
    val k = stKind(a.s("decision"))
    KRow(
        a.s("name").ifBlank { "–" },
        sub = listOfNotNull(
            if (a.d("gate") > 0) tf("Gate %s", a.d("gate").toInt()) else null,
            decisionText(a.s("decision")),
            a.dn("artifact_version")?.let { "v" + it.toInt() },
        ).joinToString(" · "),
        subKind = k,
        lead = { KIconTile(stIcon(k), k) },
        value = tFull(a.s("created_at")), valuePlain = true, valueSub = a.s("user").ifBlank { null },
        chevron = route != null,
        onClick = route?.let { r -> { nav.navigate(r) } },
    )
    a.sn("feedback")?.let { SNote(it, lead = true) }
}

private fun stIcon(k: KKind): ImageVector = when (k) {
    KKind.OK -> KIcons.Check
    KKind.BAD -> KIcons.Close
    KKind.WARN -> KIcons.Edit
    KKind.ASK -> KIcons.Clock
    else -> KIcons.History
}

// ---------------------------------------------------------------- History (search)

@Composable
private fun HistoryTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int) {
    var q by rememberSaveable { mutableStateOf("") }
    var qApplied by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    // likhna ruke to khojo (alag Search button nahi)
    LaunchedEffect(q) {
        delay(500)
        val t = q.trim()
        if (t != qApplied) {
            qApplied = t
            page = 0
        }
    }
    val f = mapOf("q" to qApplied, "kind" to kind, "page" to page.toString())
    LaunchedEffect(f, reload) { vm.load("history", f) }
    val controls: @Composable () -> Unit = {
        KSearch(q, { q = it }, "Search history".t())
        SDrop(
            "Kind".t(),
            listOf(
                "" to "All".t(), "decision" to "Decisions".t(), "artifact" to "Versions".t(), "action" to "Actions".t(),
                "notify" to "Notifications".t(), "log" to "Logs".t(),
            ),
            kind,
        ) { kind = it; page = 0 }
    }
    val list: @Composable () -> Unit = {
        SLoad(g, g.view("history", f), { vm.load("history", f) }) { o ->
            val items = o.arr("items").objs()
            if (items.isEmpty()) KCard { KEmpty("Nothing found".t(), KIcons.Search) }
            else KCard(list = true) {
                items.forEachIndexed { i, e ->
                    if (i > 0) KListDivider(true)
                    SEventRow(e, nav)
                }
            }
            if (page > 0 || items.size >= 60) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (page > 0) KButton("Newer".t(), { page-- }, Modifier.weight(1f), kind = KBtn.OUTLINE)
                if (items.size >= 60) KButton("Older".t(), { page++ }, Modifier.weight(1f), kind = KBtn.OUTLINE)
            }
        }
    }
    if (isWide()) KTwoPane(left = { controls() }, right = { list() })
    else {
        controls()
        list()
    }
}

// ---------------------------------------------------------------- Runs

private val RUN_JOBS = listOf(
    "economics" to "No AI cost", "portfolio" to "No AI cost", "discover" to "AI + web", "rank" to "AI + web", "deep" to "AI + web",
)

private fun jobIcon(j: String): ImageVector = when (j) {
    "economics" -> KIcons.Rupee
    "portfolio" -> KIcons.Wallet
    "discover" -> KIcons.Search
    "rank" -> KIcons.Sort
    "deep" -> KIcons.Spark
    else -> KIcons.Play
}

@Composable
private fun RunsTab(g: GState, vm: StudioVm, nav: NavHostController, reload: Int, sort: KSortState?) {
    SPoll(20_000, reload) { vm.load("runs") }
    val start: @Composable () -> Unit = {
        KSection("Start a run now".t())
        KCard(list = true) {
            RUN_JOBS.forEachIndexed { i, (j, sub) ->
                if (i > 0) KListDivider(true)
                KRow(
                    jobName(j), sub = sub.t(), lead = { KIconTile(jobIcon(j)) },
                    trailing = {
                        KSmallAction("Start".t(), {
                            vm.act("run", mapOf("job" to j), "run_$j", ok = "Queued".t()) { vm.load("runs") }
                        }, enabled = "run_$j" !in g.busy)
                    },
                )
            }
        }
    }
    val list: @Composable () -> Unit = {
        KSection("Runs".t())
        SLoad(g, g.view("runs"), { vm.load("runs") }) { o ->
            // default par server ka order (naye pehle)
            val items = o.arr("items").objs().let { l ->
                when {
                    sort == null || sort.isDefault -> l
                    sort.key == "cost" -> l.kSorted(sort.desc) { it.dn("cost_usd") }
                    sort.key == "errors" -> l.kSorted(sort.desc) { it.dn("errors") }
                    else -> l.kSorted(sort.desc) { tsMs(it.sn("started_at") ?: it.s("queued_at")) }
                }
            }
            if (sort != null) KSortPill(sort, sSortOpts("runs"), S_SORT_KINDS)
            if (items.isEmpty()) KCard { KEmpty("No runs yet".t(), KIcons.Play) }
            else KCard(list = true) {
                items.forEachIndexed { i, x ->
                    if (i > 0) KListDivider(true)
                    SRunRow(x, nav)
                }
            }
        }
    }
    if (isWide()) KTwoPane(left = { start() }, right = { list() })
    else {
        start()
        list()
    }
}

// ---------------------------------------------------------------- Sources

@Composable
private fun SourcesTab(g: GState, vm: StudioVm, reload: Int) {
    LaunchedEffect(reload) { vm.load("sources") }
    SLoad(g, g.view("sources"), { vm.load("sources") }) { o ->
        val items = o.arr("items").objs()
        val okN = items.count { it.s("status") == "OK" }
        KSection("Data sources".t(), trailing = { KPill(tf("%s/%s OK", okN, items.size), if (okN > 0) KKind.OK else KKind.NEUTRAL) })
        if (items.isEmpty()) KCard { KEmpty("No data".t(), KIcons.Globe) }
        else if (isWide()) KTwoPane(
            left = { SourceList(items.filterIndexed { i, _ -> i % 2 == 0 }, g, vm) },
            right = { SourceList(items.filterIndexed { i, _ -> i % 2 == 1 }, g, vm) },
        )
        else SourceList(items, g, vm)
    }
}

@Composable
private fun SourceList(items: List<JSONObject>, g: GState, vm: StudioVm) {
    if (items.isEmpty()) return
    KCard(list = true) {
        items.forEachIndexed { i, s ->
            if (i > 0) KListDivider()
            SourceRow(s, g, vm)
        }
    }
}

/** Source: naam, engine · label · paid/free, status pill. Tap = kya deta hai / tareeka / kya chahiye + Test / Docs. */
@Composable
private fun SourceRow(s: JSONObject, g: GState, vm: StudioVm) {
    val c = Mfc.c
    val open = rememberOpenUrl()
    val id = s.s("id")
    var more by rememberSaveable(id) { mutableStateOf(false) }
    val st = s.s("status")
    KRow(
        s.s("name"),
        sub = listOf(engineText(s.s("engine")), labelText(s.s("label")), if (s.b("paid")) "paid".t() else "free".t()).joinToString(" · "),
        trailing = {
            SStatusPill(st)
            SExpandIcon(more)
        },
        onClick = { more = !more },
        titleLines = 2,
    )
    if (more) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Para("Provides".t(), s.sn("provides"))
        Para("Method".t(), s.sn("method"))
        Para("Missing".t(), s.sn("missing"))
        s.sn("last_ok_at")?.let {
            SText("Last OK".t() + " " + sAgo(it) + " · " + s.d("last_items").toInt() + " " + "items".t(), 13.sp, c.ink2, Modifier.padding(top = 4.dp), lh = 18.sp)
        }
        s.sn("last_error")?.let { SText("Last error".t() + ": " + it, 13.sp, c.red, Modifier.padding(top = 4.dp), lh = 18.sp, maxLines = 4) }
        val canTest = s.b("configured") && id in setOf("internal", "claude_web", "ads_transparency")
        val docs = s.sn("docs")
        if (canTest || docs != null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canTest) KSmallAction("Test".t(), {
                vm.act("run", mapOf("job" to "source_check", "source" to id), "src_$id", ok = "Test queued. See Runs.".t())
            }, enabled = "src_$id" !in g.busy)
            if (docs != null) KSmallAction("Docs".t(), { open(docs) })
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
        val left: @Composable () -> Unit = {
            if (!o.b("ai_key")) KCard(list = true) {
                KRow("No AI key".t(), sub = "Dash Settings > AI", subKind = KKind.BAD, lead = { KIconTile(KIcons.Key, KKind.BAD) })
            }
            KSection("Schedule".t())
            KCard(list = true) {
                listOf(
                    "st_daily" to "Daily discovery", "st_weekly" to "Weekly ranking + economics", "st_monthly" to "Monthly deep research",
                    "st_push" to "Push notifications", "st_email" to "Email notifications",
                ).forEachIndexed { i, (k, l) ->
                    if (i > 0) KListDivider()
                    KRow(l.t(), trailing = {
                        KSwitch(s.s(k) == "1", { on ->
                            vm.act("setting", mapOf("key" to k, "value" to if (on) "1" else "0"), "set_$k") { reloadS() }
                        }, enabled = "set_$k" !in g.busy)
                    })
                }
                KListDivider()
                NumSetting(g, vm, s, "st_daily_hour", "Daily hour", "0-23", reloadS)
                KListDivider()
                NumSetting(g, vm, s, "st_weekly_dow", "Weekly day", "1-7", reloadS)
            }
            KSection("Budget & limits".t(), trailing = { KPill("spent".t() + " " + usd(o.obj("spend").d("month_usd")), KKind.NEUTRAL, icon = null) })
            KCard(list = true) {
                NumSetting(g, vm, s, "st_ai_budget_usd", "AI budget / month", "$", reloadS)
                KListDivider()
                NumSetting(g, vm, s, "st_search_usd_per_1k", "Search $ / 1K", "$", reloadS)
                KListDivider()
                NumSetting(g, vm, s, "st_max_projects", "Max projects", "", reloadS)
                KListDivider()
                NumSetting(g, vm, s, "st_min_cohort", "Min installs", "", reloadS)
            }
            KSection("Profit assumptions".t())
            KCard(list = true) {
                TextSetting(g, vm, s, "st_ltv_mult", "LTV × D7 revenue", reloadS)
                KListDivider()
                TextSetting(g, vm, s, "st_dev_cost", "Dev cost $", reloadS)
            }
        }
        val right: @Composable () -> Unit = {
            KSection("Countries & tiers".t())
            CountriesEditor(g, vm, o, reloadS)
            KSection("Categories".t())
            CategoriesEditor(g, vm, o, reloadS)
            KSection("Score weights".t(), trailing = { KPill("0-50", KKind.NEUTRAL, icon = null) })
            WeightsEditor(g, vm, o, reloadS)
            KSection("Your apps' categories".t())
            val apps = o.arr("apps").objs()
            if (apps.isEmpty()) KCard { KEmpty("No apps".t(), KIcons.Apps) }
            else KCard(list = true) {
                apps.forEachIndexed { i, a ->
                    if (i > 0) KListDivider()
                    AppCatRow(g, vm, a, reloadS)
                }
            }
        }
        if (isWide()) KTwoPane(left = { left() }, right = { right() })
        else {
            left()
            right()
        }
    }
}

/** Label + chhota number field + save (sirf badalne par). */
@Composable
private fun NumSetting(g: GState, vm: StudioVm, s: JSONObject, key: String, label: String, hint: String, reload: () -> Unit) {
    val c = Mfc.c
    var v by remember(s.s(key)) { mutableStateOf(s.s(key)) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SText(label.t(), 15.sp, c.ink, Modifier.weight(1f), weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
        SField(v, { v = it }, Modifier.width(96.dp), placeholder = hint, number = true)
        SSaveBtn("set_$key" in g.busy, v != s.s(key)) {
            vm.act("setting", mapOf("key" to key, "value" to v.trim()), "set_$key", ok = "Saved".t()) { reload() }
        }
    }
}

/** Lamba setting (LTV multiplier / dev cost): label + save, neeche poora field. */
@Composable
private fun TextSetting(g: GState, vm: StudioVm, s: JSONObject, key: String, label: String, reload: () -> Unit) {
    val c = Mfc.c
    var v by remember(s.s(key)) { mutableStateOf(s.s(key)) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SText(label.t(), 15.sp, c.ink, Modifier.weight(1f), weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
            SSaveBtn("set_$key" in g.busy, v != s.s(key)) {
                vm.act("setting", mapOf("key" to key, "value" to v.trim()), "set_$key", ok = "Saved".t()) { reload() }
            }
        }
        SField(v, { v = it }, Modifier.fillMaxWidth().padding(top = 6.dp, end = 4.dp), singleLine = false, minLines = 2)
    }
}

private data class Cc(val cc: String, val name: String, val tier: Int, val on: Boolean)

@Composable
private fun CountriesEditor(g: GState, vm: StudioVm, o: JSONObject, reload: () -> Unit) {
    val c = Mfc.c
    val orig = o.arr("countries").objs().map { Cc(it.s("cc"), it.s("name"), it.d("tier", 3.0).toInt(), it.b("on")) }
    var list by remember(orig) { mutableStateOf(orig) }
    var add by remember { mutableStateOf("") }
    KCard(list = true) {
        list.forEachIndexed { i, cc ->
            if (i > 0) KListDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SCheck(cc.on) { list = list.toMutableList().also { it[i] = cc.copy(on = !cc.on) } }
                SText(cc.cc + " · " + cc.name, 15.sp, if (cc.on) c.ink else c.ink3, Modifier.weight(1f).padding(start = 4.dp), weight = FontWeight.Medium, lh = 20.sp)
                (1..3).forEach { t -> TierChip(t, cc.tier == t) { list = list.toMutableList().also { it[i] = cc.copy(tier = t) } } }
            }
        }
        if (list.isNotEmpty()) KListDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SField(add, { add = it.uppercase().take(2) }, Modifier.weight(1f), placeholder = "Country code".t(), caps = true)
            KSmallAction("Add".t(), {
                if (add.matches(Regex("[A-Z]{2}")) && list.none { it.cc == add }) list = list + Cc(add, add, 3, true)
                add = ""
            }, enabled = add.matches(Regex("[A-Z]{2}")))
        }
        KButton(
            "Save countries".t(), {
                val f = HashMap<String, String>()
                list.forEachIndexed { i, x ->
                    f["list[$i][cc]"] = x.cc; f["list[$i][name]"] = x.name; f["list[$i][tier]"] = x.tier.toString()
                    if (x.on) f["list[$i][on]"] = "1"
                }
                vm.act("countries", f, "countries", ok = "Saved".t()) { reload() }
            },
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            enabled = list != orig, busy = "countries" in g.busy,
        )
    }
}

/** T1 / T2 / T3 (48dp touch). */
@Composable
private fun TierChip(t: Int, on: Boolean, onClick: () -> Unit) {
    val c = Mfc.c
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).selectable(selected = on, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(38.dp, 32.dp).clip(RoundedCornerShape(10.dp)).background(if (on) c.btnBg else c.well),
            contentAlignment = Alignment.Center,
        ) { SText("T$t", 13.sp, if (on) c.btnFg else c.ink2, weight = FontWeight.Bold, lh = 18.sp) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoriesEditor(g: GState, vm: StudioVm, o: JSONObject, reload: () -> Unit) {
    val c = Mfc.c
    val orig = o.arr("categories").strings()
    var list by remember(orig) { mutableStateOf(orig) }
    var add by remember { mutableStateOf("") }
    KCard {
        if (list.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { cat ->
                // tap = hatao
                Row(
                    Modifier.height(48.dp).clip(CircleShape).background(c.well).clickable(role = Role.Button, onClickLabel = "Remove".t()) { list = list - cat }
                        .padding(start = 14.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SText(cat, 14.sp, c.ink, weight = FontWeight.Medium, lh = 18.sp)
                    Icon(KIcons.Close, null, tint = c.ink3, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = if (list.isNotEmpty()) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SField(add, { add = it }, Modifier.weight(1f), placeholder = "Add category".t())
            KSmallAction("Add".t(), {
                if (add.isNotBlank() && add.trim() !in list) list = list + add.trim()
                add = ""
            }, enabled = add.isNotBlank())
        }
        KButton(
            "Save categories".t(), {
                val f = HashMap<String, String>()
                list.forEachIndexed { i, x -> f["list[$i]"] = x }
                vm.act("categories", f, "categories", ok = "Saved".t()) { reload() }
            },
            Modifier.fillMaxWidth().padding(top = 12.dp),
            enabled = list != orig && list.isNotEmpty(), busy = "categories" in g.busy,
        )
    }
}

@Composable
private fun WeightsEditor(g: GState, vm: StudioVm, o: JSONObject, reload: () -> Unit) {
    val c = Mfc.c
    val w = o.obj("weights")
    val factors = o.arr("factors").objs()
    val orig = factors.associate { it.s("key") to num(w.d(it.s("key")), 0) }
    var vals by remember(orig) { mutableStateOf(orig) }
    KCard(list = true) {
        factors.forEachIndexed { i, f ->
            if (i > 0) KListDivider()
            val k = f.s("key")
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    SText(f.s("label"), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
                    SText(
                        engineText(f.s("engine")) + " · " + (if (f.s("higher_is") == "better") "higher is better".t() else "higher is worse".t()) +
                            " · " + "default".t() + " " + num(f.d("default")),
                        13.sp, c.ink2, Modifier.padding(top = 2.dp), lh = 18.sp, maxLines = 2,
                    )
                }
                SField(vals[k] ?: "", { v -> vals = vals + (k to v.filter { it.isDigit() || it == '.' }.take(4)) }, Modifier.width(72.dp), number = true)
            }
        }
        KButton(
            "Save weights".t(), {
                val f = HashMap<String, String>()
                vals.forEach { (k, v) -> if (v.toDoubleOrNull() != null) f["weights[$k]"] = v }
                vm.act("weights", f, "weights", ok = "Saved. Applies to the next ranking.".t()) { reload() }
            },
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            enabled = vals != orig, busy = "weights" in g.busy,
        )
    }
}

@Composable
private fun AppCatRow(g: GState, vm: StudioVm, a: JSONObject, reload: () -> Unit) {
    val c = Mfc.c
    val pkg = a.s("pkg")
    var v by remember(a.s("category")) { mutableStateOf(a.s("category")) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            SText(a.s("name"), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
            SText(pkg, 13.sp, c.ink3, Modifier.padding(top = 2.dp), lh = 18.sp)
        }
        SField(v, { v = it }, Modifier.width(120.dp), placeholder = "Category".t())
        SSaveBtn("cat_$pkg" in g.busy, v != a.s("category")) {
            vm.act("app_category", mapOf("pkg" to pkg, "category" to v.trim()), "cat_$pkg", ok = "Saved".t()) { reload() }
        }
    }
}

// ---------------------------------------------------------------- own idea

/** Apna idea: Studio usse bhi wahi rules se jaanchta hai (REJECT bhi keh sakta hai). */
@Composable
fun IdeaDialog(g: GState, vm: StudioVm, nav: NavHostController, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var concept by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    var ccs by remember { mutableStateOf("") }
    KPageTokens {
        val c = Mfc.c
        AlertDialog(
            onDismissRequest = onClose,
            title = { SText("Add your own idea".t(), 20.sp, c.ink, weight = FontWeight.Bold, lh = 26.sp, maxLines = 2) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(concept, { concept = it }, label = { Text("What the app does".t()) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cat, { cat = it }, label = { Text("Category".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ccs, { ccs = it.uppercase() }, label = { Text("Countries (e.g. IN, US)".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                KButton("Evaluate".t(), {
                    val f = hashMapOf("title" to title.trim(), "concept" to concept.trim(), "category" to cat.trim())
                    ccs.split(',', ' ').map { it.trim() }.filter { it.matches(Regex("[A-Z]{2}")) }.forEachIndexed { i, x -> f["countries[$i]"] = x }
                    vm.act("idea", f, "idea", ok = "Idea queued for evaluation.".t()) { j -> onClose(); nav.navigate("studio/opp/" + j.optLong("id")) }
                }, enabled = title.trim().length >= 3, busy = "idea" in g.busy)
            },
            dismissButton = { KButton("Cancel".t(), onClose, kind = KBtn.OUTLINE) },
            containerColor = c.card,
        )
    }
}
