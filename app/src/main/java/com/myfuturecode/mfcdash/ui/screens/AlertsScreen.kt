package com.myfuturecode.mfcdash.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.AlertItem
import com.myfuturecode.mfcdash.data.AlertsData
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KLazyScreen
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLoading
import com.myfuturecode.mfcdash.ui.kit.KNum
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSearch
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSmallAction
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KTabBar
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.icon
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kCardItems
import com.myfuturecode.mfcdash.ui.kit.kItem
import com.myfuturecode.mfcdash.ui.kit.word
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tOnly
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Alerts tab = server rule engine (api/alerts.php) ka output. Hero: kitne naye + har kism ka nishaan.
 * Tabs: Sab / Turant / Dhyan do / OK chahiye. List din-wise (Aaj, Kal, ...), tap = poori baat + Claude AI + actions.
 * Status / app / kism filter sheet me, "Abhi check" refresh icon se, rules aur settings "More" menu me.
 */
@Composable
fun AlertsScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }           // 0 sab | 1 turant | 2 dhyan do | 3 OK chahiye
    var show by rememberSaveable { mutableStateOf("all") }         // all | open | info | closed
    var appF by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("") }            // "" | ads | gads | crash | money | health | gah | studio | cost | data
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var sheet by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    BackHandler(enabled = searching) { searching = false; q = "" }
    LaunchedEffect(Unit) { vm.refreshAlerts() }
    LaunchedEffect(msg) { if (msg != null) { delay(6000); msg = null } }

    val al = st.alerts
    val filters = (if (show != "all") 1 else 0) + (if (appF.isNotBlank()) 1 else 0) + (if (kind.isNotBlank()) 1 else 0)
    val bar: @Composable ColumnScope.() -> Unit = {
        KTabBar("Alerts".t()) {
            if (al != null) {
                KBarIcon(if (searching) KIcons.Close else KIcons.Search, "Search".t(), {
                    searching = !searching
                    if (!searching) q = ""
                })
                KBarIcon(KIcons.Filter, "Filter".t(), { sheet = true }, badge = filters, badgeUrgent = false)
                KBarIcon(KIcons.Refresh, "Check now".t(), { vm.computeAlerts { msg = it } }, spinning = st.alertsBusy, enabled = !st.alertsBusy)
            } else {
                KBarIcon(KIcons.Refresh, "Check again".t(), { vm.refreshAlerts() })
            }
            AlMoreMenu(onRules = { rules = true }, onSettings = { alGoTab(nav, "settings") })
        }
    }
    if (rules) AlRulesDialog { rules = false }

    if (al == null) {
        KScreen(top = bar) { AlProblem(st, vm) }
        return
    }

    // ---- filter + din-wise groups
    val needle = q.trim()
    val list = al.alerts.filter { a ->
        val s = alSev(a)
        val tabOk = tab == 0 || (tab == 1 && s == KKind.BAD) || (tab == 2 && s == KKind.WARN) || (tab == 3 && s == KKind.ASK)
        val showOk = when (show) {
            "open" -> a.open
            "info" -> a.open && s == KKind.NEUTRAL
            "closed" -> !a.open
            else -> true
        }
        tabOk && showOk && (appF.isBlank() || a.pkg == appF) && (kind.isBlank() || alertKind(a.rule) == kind) &&
            (needle.isEmpty() || a.titleL.contains(needle, true) || a.bodyL.contains(needle, true) ||
                a.appName.contains(needle, true) || a.campaign.contains(needle, true))
    }
    val groups: List<Pair<String, List<AlertItem>>> = list.groupBy { alDayKey(it) }.toList()
        .sortedWith(compareBy<Pair<String, List<AlertItem>>> { it.first.isEmpty() }.thenByDescending { it.first })
        .map { (k, v) -> k to v.sortedWith(AL_ORDER) }
    val fresh = al.alerts.filter { it.open && it.unread }
    val nBad = fresh.count { alSev(it) == KKind.BAD }
    val nWarn = fresh.count { alSev(it) == KKind.WARN }
    val nAsk = fresh.count { alSev(it) == KKind.ASK }
    // hero ka pill: khule alerts me sabse bura (padhe hue bhi), sirf naye nahi
    val openSev = al.alerts.filter { it.open }.map { alSev(it) }
    val worst = when {
        KKind.BAD in openSev -> KKind.BAD
        KKind.WARN in openSev -> KKind.WARN
        KKind.ASK in openSev -> KKind.ASK
        else -> KKind.OK
    }
    val tabs = listOf("All".t(), "Act now".t(), "Watch".t(), "Needs OK".t())
    val plain = tab == 0 && filters == 0 && needle.isEmpty()
    val emptyText = if (plain) "No alerts".t() else "No match".t()
    val readAll: (() -> Unit)? = if (al.unread > 0) { { vm.markAllRead() } } else null
    val toggle: (Long) -> Unit = { id -> expanded[id] = expanded[id] != true }

    if (isWide()) {
        // Fold: baayein hero + tabs, daayein list
        KScreen(top = bar) {
            KTwoPane(
                left = {
                    AlHero(al, nBad, nWarn, nAsk, worst)
                    AlTabs(tabs, tab) { tab = it }
                    if (searching) KSearch(q, { q = it }, "Search alerts".t())
                    msg?.let { AlMsg(it) }
                    AlExtras(al, nav)
                },
                right = {
                    if (groups.isEmpty()) KCard { KEmpty(emptyText, KIcons.Bell) }
                    groups.forEachIndexed { gi, (key, items) ->
                        AlDayHead(alDayLabel(key), if (gi == 0) readAll else null)
                        KCard(list = true) {
                            items.forEachIndexed { i, a ->
                                if (i > 0) KListDivider(true)
                                AlRow(a, expanded[a.id] == true, { toggle(a.id) }, nav) { vm.resolveAlert(a.id) }
                            }
                        }
                    }
                },
            )
        }
    } else {
        KLazyScreen(top = {
            bar()
            AlHero(al, nBad, nWarn, nAsk, worst)
        }) {
            if (searching) kItem(key = "al.search") { KSearch(q, { q = it }, "Search alerts".t()) }
            kItem(key = "al.tabs") { AlTabs(tabs, tab) { tab = it } }
            msg?.let { m -> kItem(key = "al.msg") { AlMsg(m) } }
            if (groups.isEmpty()) kItem(key = "al.empty") { KCard { KEmpty(emptyText, KIcons.Bell) } }
            groups.forEachIndexed { gi, (key, items) ->
                kItem(key = "al.h.$key") { AlDayHead(alDayLabel(key), if (gi == 0) readAll else null) }
                kCardItems(items, key = { it.id }, lead = true) { a ->
                    AlRow(a, expanded[a.id] == true, { toggle(a.id) }, nav) { vm.resolveAlert(a.id) }
                }
            }
            kItem(key = "al.extra") { AlExtras(al, nav) }
        }
    }

    if (sheet) {
        val names = st.data?.apps.orEmpty()
        val apps = al.alerts.map { it.pkg }.filter { it.isNotBlank() }.distinct().map { p ->
            p to (names.firstOrNull { it.pkg == p }?.name ?: al.alerts.first { it.pkg == p }.appName.ifBlank { p })
        }
        AlFilterSheet(
            show = show, onShow = { show = it }, appF = appF, onApp = { appF = it }, kind = kind, onKind = { kind = it }, apps = apps,
            onReset = { show = "all"; appF = ""; kind = "" }, onClose = { sheet = false },
        )
    }
}

/** Group rule names into kinds for the Kind filter. */
fun alertKind(rule: String): String = when {
    rule.startsWith("health") -> "health"
    rule.startsWith("gah_") -> "gah"
    rule.startsWith("studio_") -> "studio"
    rule.startsWith("cost_") -> "cost"
    rule.startsWith("adunit") || rule.startsWith("fill") || rule.startsWith("ecpm") || rule.startsWith("ads_") || rule == "imps_low" || rule == "imps_zero" || rule.startsWith("network") -> "ads"
    rule.startsWith("camp") || rule.startsWith("cpi") || rule.startsWith("spend") || rule.startsWith("gads") || rule.startsWith("roas") -> "gads"
    rule.startsWith("crash") || rule.startsWith("ver_crash") -> "crash"
    rule.startsWith("rev") || rule.startsWith("pay") || rule.startsWith("record") || rule.startsWith("brief") -> "money"
    else -> "data"
}

// ---------------------------------------------------------------- kism / din

/** Alert ki kism: aapke faisle wale (Gate, tested build) = OK chahiye, level 3 = Turant, 2 = Dhyan do, baaki Info. */
private fun alSev(a: AlertItem): KKind = when {
    a.rule == "studio_gate" || a.rule == "health_aab" || (a.rule == "studio_research" && a.level >= 2) -> KKind.ASK
    a.level >= 3 -> KKind.BAD
    a.level == 2 -> KKind.WARN
    else -> KKind.NEUTRAL
}

/** Row par dikhne wali kism: band alert = Theek. */
private fun alShown(a: AlertItem): KKind = if (a.open) alSev(a) else KKind.OK

private fun alRank(k: KKind): Int = when (k) {
    KKind.BAD -> 0
    KKind.WARN -> 1
    KKind.ASK -> 2
    KKind.NEUTRAL -> 3
    else -> 4
}

// din ke andar: khule pehle, phir kism, phir naya pehle
private val AL_ORDER: Comparator<AlertItem> = compareByDescending<AlertItem> { it.open }
    .thenBy { alRank(alSev(it)) }
    .thenByDescending { tsMs(it.createdAt) ?: 0L }

private val AL_DAY_KEY = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val AL_DAY_SHOW = SimpleDateFormat("dd MMM", Locale.US)

private fun alDayKey(a: AlertItem): String = tsMs(a.createdAt)?.let { AL_DAY_KEY.format(Date(it)) } ?: ""

/** "Aaj" / "Kal" / "18 Oct" (time na mile to "Purane"). */
private fun alDayLabel(key: String): String {
    if (key.isEmpty()) return "Older".t()
    val cal = Calendar.getInstance()
    val today = AL_DAY_KEY.format(cal.time)
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yest = AL_DAY_KEY.format(cal.time)
    return when (key) {
        today -> "Today".t()
        yest -> "Yesterday".t()
        else -> runCatching { AL_DAY_KEY.parse(key)?.let { AL_DAY_SHOW.format(it) } }.getOrNull() ?: key
    }
}

/** Status line: shabd · app · kitni baar (band = "Theek · band 2 ghante pehle"). */
private fun alSub(a: AlertItem, k: KKind): String {
    val parts = ArrayList<String>()
    parts += if (k == KKind.NEUTRAL) "Info".t() else k.word()
    if (!a.open) {
        val at = a.resolvedAt
        val ago = when {
            at == null -> ""
            at == "now" -> " " + "now".t()
            else -> tsMs(at)?.let { " " + agoText(it) } ?: ""
        }
        parts += "closed".t() + ago
    } else {
        if (a.appName.isNotBlank() && !a.titleL.contains(a.appName, true)) parts += a.appName
        if (a.fired > 1) parts += "${a.fired}×"
    }
    return parts.joinToString(" · ")
}

/** Alert kis screen par khulega (label, route). */
private fun alTarget(a: AlertItem): Pair<String, String>? = when {
    a.isHealth -> (if (a.issueId != null) "Open issue".t() else "AI Health".t()) to a.route
    a.isGads -> (if (a.gaAlertId != null) "Open alert".t() else "Ads Monitor".t()) to a.route
    a.isStudio -> "Open in Studio".t() to a.route
    a.isCost -> "AI Cost & Usage".t() to a.route
    a.pkg.isNotBlank() -> "Open app".t() to "app/${a.pkg}"
    else -> null
}

/** Tab badlo (Root ke bottom nav jaisa). */
private fun alGoTab(nav: NavHostController, route: String) = nav.navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private val AL_KINDS = listOf(
    "" to "All", "ads" to "Ads", "gads" to "Google Ads", "crash" to "Crash", "money" to "Money", "health" to "AI Health",
    "gah" to "Ads Monitor", "studio" to "Studio", "cost" to "AI Cost", "data" to "Data",
)
private val AL_SHOWS = listOf("all", "open", "info", "closed")

// ---------------------------------------------------------------- pieces

@Composable
private fun AlText(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal, maxLines: Int = 1,
) {
    Text(
        text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = KNum.copy(fontSize = size, fontWeight = weight, lineHeight = (size.value * 1.38f).sp),
    )
}

/** Hero: aaj ke naye alerts ka bada number + har kism ka nishaan; pill = khule alerts ki sabse buri haalat. */
@Composable
private fun AlHero(al: AlertsData, bad: Int, warn: Int, ask: Int, head: KKind) {
    KHero(KTint.Rose) {
        val c = Mfc.c
        KHeroHead("Today's alerts".t(), trailing = { KPill(if (head == KKind.BAD) "See now".t() else head.word(), head) })
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) { KBigNum(al.unread.toString(), suffix = " " + "new".t(), size = 48.sp) }
            AlMarks(bad, warn, ask)
        }
        val parts = ArrayList<String>()
        parts += tf("%s open", al.openCount)
        if (al.computedAt > 0) parts += "check".t() + " " + agoText(al.computedAt * 1000)
        if (al.aiPending > 0) parts += "AI writing…".t()
        AlText(parts.joinToString(" · "), 13.sp, c.ink2, Modifier.padding(top = 6.dp))
    }
}

/** Ek nishaan har naye alert ka: diamond (turant), triangle (dhyan do), ghadi (OK chahiye). */
@Composable
private fun AlMarks(bad: Int, warn: Int, ask: Int, max: Int = 5) {
    val c = Mfc.c
    val b = bad.coerceIn(0, max)
    val w = warn.coerceIn(0, max - b)
    val a = ask.coerceIn(0, max - b - w)
    val extra = bad + warn + ask - (b + w + a)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(b) { Icon(KIcons.Turant, null, tint = c.markBad, modifier = Modifier.size(24.dp)) }
        repeat(w) { Icon(KIcons.Dhyan, null, tint = c.markWarn, modifier = Modifier.size(24.dp)) }
        repeat(a) { Icon(KIcons.Wait, null, tint = c.brand, modifier = Modifier.padding(1.dp).size(22.dp)) }
        if (extra > 0) AlText("+$extra", 13.sp, c.ink3, weight = FontWeight.SemiBold)
    }
}

/** Seg tabs, par har tab apne shabd jitna chauda (OK chahiye kate nahi). */
@Composable
private fun AlTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Mfc.c
    val track = if (c.isDark) c.card else c.line
    Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(track)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(label.length + 4f).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                if (on) {
                    var pill = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(20.dp)).background(c.tabOn)
                    if (!c.isDark) pill = pill.border(1.dp, c.line, RoundedCornerShape(20.dp))
                    Box(pill)
                }
                AlText(label, 14.sp, if (on) c.tabOnFg else c.ink2, Modifier.padding(horizontal = 6.dp), weight = FontWeight.SemiBold)
            }
        }
    }
}

/** Din ki heading + (pehli par) "Sab padha". */
@Composable
private fun AlDayHead(label: String, onReadAll: (() -> Unit)?) {
    val link: (@Composable () -> Unit)? = if (onReadAll != null) { { AlReadAll(onReadAll) } } else null
    KSection(label, trailing = link)
}

@Composable
private fun AlReadAll(onClick: () -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(KIcons.Check, null, tint = c.brand, modifier = Modifier.size(18.dp))
        AlText("Mark read".t(), 15.sp, c.brand, weight = FontWeight.SemiBold)
    }
}

/** Ek alert: kism ka tile, title, status line, time + naya dot. Tap = khulta hai (poori baat, AI, actions). */
@Composable
private fun AlRow(a: AlertItem, open: Boolean, onToggle: () -> Unit, nav: NavHostController, onResolve: () -> Unit) {
    val k = alShown(a)
    // padha hua / band = halka (khula urgent hamesha rang me)
    val quiet = !a.open || (!a.unread && alSev(a) != KKind.BAD)
    Column(Modifier.fillMaxWidth()) {
        KRow(
            a.titleL,
            sub = alSub(a, k),
            subKind = k,
            lead = { KIconTile(k.icon() ?: KIcons.Info, if (quiet) KKind.NEUTRAL else k) },
            trailing = { AlTime(a) },
            onClick = onToggle,
            quiet = quiet,
            titleLines = if (open) 3 else 1,
        )
        if (open) AlDetail(a, nav, onResolve)
    }
}

@Composable
private fun AlTime(a: AlertItem) {
    val c = Mfc.c
    Column(horizontalAlignment = Alignment.End) {
        AlText(tOnly(a.createdAt), 13.sp, c.ink3)
        if (a.open && a.unread) Box(Modifier.padding(top = 6.dp).size(8.dp).background(c.brand, CircleShape))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlDetail(a: AlertItem, nav: NavHostController, onResolve: () -> Unit) {
    val c = Mfc.c
    Column(
        Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (a.bodyL.isNotBlank()) AlText(a.bodyL, 14.sp, c.ink2, maxLines = 40)
        if (a.ai.isNotBlank()) Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(KIcons.Spark, null, tint = c.brand, modifier = Modifier.size(16.dp))
                AlText("Claude AI", 13.sp, c.brand, weight = FontWeight.Bold)
            }
            AlText(a.ai, 14.sp, c.ink, Modifier.padding(top = 4.dp), maxLines = 60)
        }
        val target = alTarget(a)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (target != null) KSmallAction(target.first, { nav.navigate(target.second) })
            if (a.campaign.isNotBlank() || a.account.isNotBlank()) KSmallAction("Campaigns".t(), { nav.navigate("money") })
            if (a.open) KSmallAction("✓ Done".t(), onResolve)
        }
    }
}

/** "Abhi check" ka jawab (kuch second). */
@Composable
private fun AlMsg(text: String) {
    KStatus(text, KKind.ASK, Modifier.padding(horizontal = 4.dp))
}

/** List ke neeche: Claude AI subah ka brief, AI band ho to Settings ka rasta. */
@Composable
private fun AlExtras(al: AlertsData, nav: NavHostController) {
    val c = Mfc.c
    var briefOpen by rememberSaveable { mutableStateOf(false) }
    val brief = al.brief
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (brief != null) KCard(onClick = { briefOpen = !briefOpen }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KIconTile(KIcons.Spark, KKind.ASK, 36.dp)
                Column(Modifier.weight(1f)) {
                    AlText("Morning brief".t(), 15.sp, c.ink, weight = FontWeight.SemiBold)
                    AlText(tf("on %s data", fmtDay(brief.day)), 13.sp, c.ink3)
                }
                Icon(KIcons.ChevDown, null, tint = c.chev, modifier = Modifier.size(22.dp).rotate(if (briefOpen) 180f else 0f))
            }
            AlText(brief.text, 14.sp, c.ink, Modifier.padding(top = 10.dp), maxLines = if (briefOpen) 80 else 3)
        }
        if (!al.ai.enabled) KCard(list = true) {
            KRow(
                "Claude AI", sub = "off".t(), lead = { KIconTile(KIcons.Spark, KKind.NEUTRAL, 36.dp) }, chevron = true,
                onClick = { alGoTab(nav, "settings") },
            )
        }
    }
}

/** alerts.php nahi / server tak nahi pahunche / load ho raha. */
@Composable
private fun AlProblem(st: UiState, vm: DashVm) {
    val c = Mfc.c
    val err = st.alertsError
    if (!st.alertsMissing && err == null) {
        KLoading()
        return
    }
    KCard {
        if (st.alertsMissing) AlText("alerts.php is not on the server".t(), 15.sp, c.ink, weight = FontWeight.SemiBold, maxLines = 2)
        else if (err != null) AlText(err, 14.sp, c.red, maxLines = 4)
        KButton("Check again".t(), { vm.refreshAlerts() }, Modifier.fillMaxWidth().padding(top = 12.dp), kind = KBtn.SOFT, icon = KIcons.Refresh)
    }
}

/** App bar ka "More": rules ki list, notification / Claude AI settings. */
@Composable
private fun AlMoreMenu(onRules: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        KBarIcon(KIcons.More, "More".t(), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            KPageTokens {
                DropdownMenuItem(
                    text = { Text("What the rules watch".t()) },
                    onClick = { open = false; onRules() },
                    leadingIcon = { Icon(KIcons.Info, null) },
                )
                DropdownMenuItem(
                    text = { Text("Settings".t()) },
                    onClick = { open = false; onSettings() },
                    leadingIcon = { Icon(KIcons.Gear, null) },
                )
            }
        }
    }
}

@Composable
private fun AlRulesDialog(onClose: () -> Unit) {
    val c = Mfc.c
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Done".t()) } },
        title = { Text("What the rules watch".t()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Triple(KKind.BAD, "URGENT".t(), "data stopped (app / server), ads stopped, Google Ads script silent, campaign 0 impressions, spend with 0 installs, CPI 2.5x, ROAS loss (<60%), crash spike, revenue 85%+ down, ad unit no-fill, cron down".t()),
                    Triple(KKind.WARN, "Watch".t(), "data low, impressions down, CPI +50%, ROAS <85%, campaign D3 ROAS <60%, spend 2x faster, new version crashing, ad fill down, ad unit CTR/eCPM/fill down, revenue/installs 60%+ down, eCPM halved, uninstalls high, payments failing, D1 retention down".t()),
                    Triple(KKind.NEUTRAL, "Info".t(), "9 AM summary of yesterday (+AI brief), new/paused campaign, revenue/installs up, new record, urgent issue recovered".t()),
                ).forEach { (k, label, body) ->
                    KStatus(label, k, Modifier.padding(top = 6.dp))
                    AlText(body, 14.sp, c.ink2, maxLines = 20)
                }
            }
        },
    )
}

/** Filter sheet: status (sab / khule / info / band), app, kism. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlFilterSheet(
    show: String, onShow: (String) -> Unit, appF: String, onApp: (String) -> Unit, kind: String, onKind: (String) -> Unit,
    apps: List<Pair<String, String>>, onReset: () -> Unit, onClose: () -> Unit,
) {
    val c = Mfc.c
    ModalBottomSheet(onDismissRequest = onClose, containerColor = c.page) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KSection("Status".t())
            KSegTabs(
                listOf("All".t(), "Open".t(), "Info".t(), "Closed".t()),
                AL_SHOWS.indexOf(show).coerceAtLeast(0),
                { onShow(AL_SHOWS[it]) },
            )
            if (apps.isNotEmpty()) {
                KSection("App".t())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlChip("All apps".t(), appF.isBlank()) { onApp("") }
                    apps.forEach { (p, n) -> AlChip(n, appF == p) { onApp(p) } }
                }
            }
            KSection("Kind".t())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AL_KINDS.forEach { (k, l) -> AlChip(l.t(), kind == k) { onKind(k) } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KButton("Reset".t(), onReset, Modifier.weight(1f), kind = KBtn.OUTLINE)
                KButton("Done".t(), onClose, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AlChip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Mfc.c
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.heightIn(min = 48.dp).clip(shape).selectable(selected = on, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.height(40.dp).clip(shape).background(if (on) c.brandSoft else c.card).border(1.dp, if (on) c.brand else c.line, shape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { AlText(text, 14.sp, if (on) c.brand else c.ink2, weight = FontWeight.SemiBold) }
    }
}
