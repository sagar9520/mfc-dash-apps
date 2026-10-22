package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.Campaign
import com.myfuturecode.mfcdash.data.CpData
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.ExtraData
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.ChipRow
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.components.MChip
import com.myfuturecode.mfcdash.ui.kit.KAvatar
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
import com.myfuturecode.mfcdash.ui.kit.KLive
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRoasMeter
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSkelCard
import com.myfuturecode.mfcdash.ui.kit.KSkelHero
import com.myfuturecode.mfcdash.ui.kit.KSkelSection
import com.myfuturecode.mfcdash.ui.kit.KSkelStats
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KSortButton
import com.myfuturecode.mfcdash.ui.kit.KSortKind
import com.myfuturecode.mfcdash.ui.kit.KSortState
import com.myfuturecode.mfcdash.ui.kit.kSortLead
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KTabBar
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.KWeekBars
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.parseHex
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlinx.coroutines.delay

val RANGES = listOf("today" to "Today", "yesterday" to "Yesterday", "7d" to "7 days", "30d" to "30 days", "custom" to "Custom")

/** Jo data tab dikha raha hai: sab apps ya ek app (app filter). */
class View(val d: DashData, val extra: ExtraData?, val extraMissing: Boolean, val cp: CpData?, val pkg: String?) {
    val allApps get() = pkg == null
    /** Sab apps: data aa gaya, app.php ka jawab abhi raste me (missing nahi). Ek app me data + extra saath aate hain. */
    val extraPending get() = allApps && extra == null && !extraMissing
}

// ---------------------------------------------------------------- app bar + period

/**
 * Screen ka kit app bar. back = true: wapas + title (+ sub), warna bada title + bell + settings.
 * Refresh hamesha aakhir me; extra = screen ke apne icons (filter, calendar...).
 */
@Composable
fun DashBar(
    title: String, vm: DashVm, st: UiState, nav: NavHostController, back: Boolean = true,
    sub: String? = null, lead: (@Composable () -> Unit)? = null, extra: @Composable RowScope.() -> Unit = {},
) {
    val lag = st.data?.aggLagSec ?: 0.0
    val stale = if (lag > 300) tf("data %s min old", (lag / 60).toInt()) else null
    val s = listOfNotNull(sub?.takeIf { it.isNotBlank() }, stale).joinToString(" · ").ifBlank { null }
    if (back) {
        KDetailBar(title, { nav.popBackStack() }, sub = s, lead = lead, actions = {
            extra()
            KBarIcon(KIcons.Refresh, "Refresh".t(), { vm.refresh() }, spinning = st.loading)
        })
    } else {
        KTabBar(title) {
            extra()
            KBarIcon(KIcons.Refresh, "Refresh".t(), { vm.refresh() }, spinning = st.loading)
            val al = st.alerts
            KBarIcon(KIcons.Bell, "Alerts".t(), { dashGoTab(nav, "alerts") }, badge = al?.unread ?: 0, badgeUrgent = (al?.urgentOpen ?: 0) > 0)
            KBarIcon(KIcons.Gear, "Settings".t(), { dashGoTab(nav, "settings") })
        }
    }
}

/** Neeche wale tab par jao (Root.goTab jaisa): Home tak pop, tab ki jagah yaad. */
private fun dashGoTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Period tabs: Aaj / 7 din / 30 din. Kal ya custom chuna ho to chautha tab (chuna hua) dikhta hai. */
@Composable
fun DashPeriodTabs(vm: DashVm, st: UiState, modifier: Modifier = Modifier) {
    val keys = mutableListOf("today", "7d", "30d")
    val labels = mutableListOf("Today".t(), "7 days".t(), "30 days".t())
    if (st.range == "yesterday" || st.range == "custom") {
        keys.add(st.range)
        labels.add(if (st.range == "yesterday") "Yesterday".t() else "Custom".t())
    }
    KSegTabs(labels, keys.indexOf(st.range), { i ->
        val k = keys[i]
        if (k != st.range && k != "custom") vm.setRange(k)
    }, modifier.widthIn(max = 560.dp))   // Fold par poori chaudai nahi
}

/** Calendar icon: Aaj / Kal / 7 din / 30 din / Custom (date range picker). */
@Composable
fun DashRangeIcon(vm: DashVm, st: UiState) {
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    Box {
        KBarIcon(KIcons.Calendar, "Pick a period".t(), { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            KPageTokens {
                RANGES.forEach { (k, l) ->
                    DropdownMenuItem(
                        text = { DashText(if (k == "custom" && st.range == "custom" && st.from != null) fmtDay(st.from) + " – " + fmtDay(st.to) else l.t(), 15.sp, Mfc.c.ink) },
                        onClick = {
                            menu = false
                            if (k == "custom") pick = true else vm.setRange(k)
                        },
                        trailingIcon = { if (st.range == k) Icon(KIcons.Check, null, tint = Mfc.c.brand) },
                    )
                }
            }
        }
    }
    if (pick) DashRangeDialog(vm) { pick = false }
}

/** Custom period: date range picker dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashRangeDialog(vm: DashVm, onClose: () -> Unit) {
    val state = rememberDateRangePickerState()
    KPageTokens {
        DatePickerDialog(
            onDismissRequest = onClose,
            confirmButton = {
                TextButton(onClick = {
                    val a = state.selectedStartDateMillis
                    val b = state.selectedEndDateMillis ?: a
                    if (a != null && b != null) {
                        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                        vm.setRange("custom", f.format(Date(minOf(a, b))), f.format(Date(maxOf(a, b))))
                    }
                    onClose()
                }) { Text("Apply".t()) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("Cancel".t()) } },
        ) { DateRangePicker(state, modifier = Modifier.weight(1f), title = { Text("Pick a period".t(), Modifier.padding(16.dp)) }) }
    }
}

/** Period: tabs + calendar (Kal / custom). lead = aage koi chip/toggle. */
@Composable
fun RangeBar(vm: DashVm, st: UiState, lead: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        lead?.invoke()
        DashPeriodTabs(vm, st, Modifier.weight(1f))
        DashRangeIcon(vm, st)
    }
}

// ---------------------------------------------------------------- app filter

/** App filter chips: All apps / ek app. Ek app chunne par us app ka data.php?app=<pkg> aata hai. */
@Composable
fun AppChips(vm: DashVm, st: UiState, lead: (@Composable () -> Unit)? = null) {
    val apps = st.data?.apps ?: return
    if (apps.isEmpty()) return
    ChipRow {
        lead?.invoke()
        MChip("All apps".t(), on = st.appFilter == null, onClick = { vm.setAppFilter(null) })
        apps.forEach { a -> MChip(a.name, on = st.appFilter == a.pkg, onClick = { vm.setAppFilter(a.pkg) }, dot = parseHex(a.color)) }
    }
}

/** Bar icon: app filter sheet kholta hai. Ek app chuna ho to badge. */
@Composable
fun DashAppFilterIcon(st: UiState, onClick: () -> Unit) {
    if (st.data?.apps.isNullOrEmpty()) return
    KBarIcon(KIcons.Filter, "App filter".t(), onClick, badge = if (st.appFilter != null) 1 else 0, badgeUrgent = false)
}

/** Neeche se sheet: All apps + har app (avatar + naam), chuna hua = check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashAppSheet(vm: DashVm, st: UiState, open: Boolean, onClose: () -> Unit) {
    if (!open) return
    val apps = st.data?.apps.orEmpty()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state) {
        KPageTokens {
            val c = Mfc.c
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                DashText("Apps".t(), 18.sp, c.ink, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), weight = FontWeight.Bold)
                KRow(
                    "All apps".t(), lead = { KIconTile(KIcons.Apps) },
                    trailing = { if (st.appFilter == null) Icon(KIcons.Check, null, tint = c.brand) },
                    onClick = { vm.setAppFilter(null); onClose() },
                )
                apps.forEach { a ->
                    KListDivider(true)
                    KRow(
                        a.name, lead = { KAvatar(a.name) },
                        trailing = { if (st.appFilter == a.pkg) Icon(KIcons.Check, null, tint = c.brand) },
                        onClick = { vm.setAppFilter(a.pkg); onClose() },
                    )
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

// ---------------------------------------------------------------- filters

/**
 * Screen ke filters ek "Filter" button ke peeche rehte hain. Tap = neeche se sheet jisme har filter ki chips hain;
 * chunne ke baad button par ginti aur ek line "Sort: Revenue · Show: In loss · Clear". FilterChips apni poori
 * jankari (label, options, selected, onSelect) FilterBox mein register karta hai aur inline kuch nahi banata.
 */
class FilterRow(val label: String, val options: List<Pair<String, String>>, val selected: String) {
    val default get() = options.firstOrNull()?.first ?: ""
    val active get() = selected != default
    val selectedText get() = options.firstOrNull { it.first == selected }?.second ?: selected
}

class FilterBox {
    var open by mutableStateOf(false)
    val rows = mutableStateMapOf<String, FilterRow>()
    val order = mutableStateListOf<String>()
    private val handlers = HashMap<String, (String) -> Unit>()
    val list get() = order.mapNotNull { rows[it] }
    val active get() = list.filter { it.active }
    fun select(label: String, key: String) { handlers[label]?.invoke(key) }
    fun reset() = list.forEach { if (it.active) select(it.label, it.default) }
    /** State sirf badalne par likho, warna recomposition loop ban jata hai. */
    fun register(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
        handlers[label] = onSelect
        if (label !in order) order.add(label)
        val cur = rows[label]
        if (cur == null || cur.options != options || cur.selected != selected) rows[label] = FilterRow(label, options, selected)
    }
    fun unregister(label: String) { handlers.remove(label); rows.remove(label); order.remove(label) }
}
val LocalFilterBox = staticCompositionLocalOf<FilterBox?> { null }

/** Ek screen ka ek filter (label + options). FilterBox ke andar: sirf register; bahar: inline chips. */
@Composable
fun FilterChips(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val box = LocalFilterBox.current
    if (box != null) {
        SideEffect { box.register(label, options, selected, onSelect) }
        DisposableEffect(box, label) { onDispose { box.unregister(label) } }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        DashText(label, 13.sp, Mfc.c.ink3, Modifier.padding(start = 4.dp))
        ChipRow { options.forEach { (k, l) -> MChip(l, on = selected == k, onClick = { onSelect(k) }) } }
    }
}

/** "Filter" button (ginti ke saath jab kuch chuna ho): tap = sheet. 48dp touch. */
@Composable
fun FilterToggle(box: FilterBox) {
    if (box.rows.isEmpty()) return
    val c = Mfc.c
    val n = box.active.size
    val on = n > 0
    val bg = when {
        on -> c.btnBg
        c.onBrand -> c.pNBg
        else -> c.brandSoft
    }
    val fg = if (on) c.btnFg else if (c.onBrand) Color.White else c.brand
    Box(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button) { box.open = true },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(bg).padding(start = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(KIcons.Filter, null, tint = fg, modifier = Modifier.size(16.dp))
            DashText("Filter".t(), 14.sp, fg, weight = FontWeight.SemiBold)
            if (on) Box(Modifier.size(20.dp).clip(CircleShape).background(c.btnFg), contentAlignment = Alignment.Center) {
                DashText(n.toString(), 13.sp, c.btnBg, weight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Ek line: kaunse filter lage hain + Hatao. Kuch na laga ho to kuch nahi.
 * lead = shuru me sort pill (`kSortLead(sort, opts)`: sort default par null); sirf pill ho to sirf pill.
 */
@Composable
fun FilterSummary(box: FilterBox, lead: (@Composable () -> Unit)? = null) {
    val act = box.active
    if (act.isEmpty() && lead == null) return
    val c = Mfc.c
    Row(Modifier.fillMaxWidth().padding(start = if (lead != null) 0.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        lead?.invoke()
        if (act.isEmpty()) return@Row
        Text(
            buildAnnotatedString {
                act.forEachIndexed { i, r ->
                    if (i > 0) withStyle(SpanStyle(color = c.ink3)) { append(" · ") }
                    withStyle(SpanStyle(color = c.ink3)) { append(r.label + ": ") }
                    withStyle(SpanStyle(color = c.brand, fontWeight = FontWeight.SemiBold)) { append(r.selectedText) }
                }
            },
            Modifier.weight(1f).padding(start = if (lead != null) 8.dp else 0.dp), style = TextStyle(fontSize = 13.sp, fontFeatureSettings = "tnum"), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button) { box.reset() }.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { DashText("Clear".t() + " ✕", 14.sp, c.brand, weight = FontWeight.SemiBold) }
    }
}

/** Neeche se khulne wali sheet: har filter ka label + chips, upar Reset, neeche Done. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(box: FilterBox) {
    if (!box.open) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { box.open = false }, sheetState = state) {
        KPageTokens {
            val c = Mfc.c
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DashText("Filters".t(), 18.sp, c.ink, Modifier.weight(1f), weight = FontWeight.Bold)
                    TextButton(onClick = { box.reset() }, enabled = box.active.isNotEmpty()) { Text("Reset".t()) }
                }
                box.list.forEach { r ->
                    DashText(r.label, 13.sp, c.ink3, Modifier.padding(top = 10.dp, bottom = 2.dp), weight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.options.forEach { (k, l) -> MChip(l, on = r.selected == k, onClick = { box.select(r.label, k) }) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                KButton("Done".t(), { box.open = false }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

// ---------------------------------------------------------------- tab / detail screen frame

/**
 * Data na ho: load ho raha = skeleton (screen jaisa dhaancha, chamak ke saath), warna error + Dobara try.
 * Data pehle se ho aur refresh chale to ye nahi aata (purana data + bar ka ghoomta refresh).
 */
@Composable
fun DashNoData(st: UiState, loading: Boolean, skeleton: @Composable () -> Unit = { DashSkel() }, onRetry: () -> Unit) {
    if (loading) { skeleton(); return }
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KEmpty(st.error ?: "No data received".t(), KIcons.Info)
        KButton("Try again".t(), onRetry, kind = KBtn.SOFT, icon = KIcons.Refresh)
    }
}

/**
 * Screen ka pehla frame (ya keys badle): load wala LaunchedEffect abhi chala nahi, to bhi "load ho raha" maano,
 * taaki skeleton se pehle ek frame "No data received" na jhalke. keys = load wale LaunchedEffect ki keys.
 */
@Composable
fun dashFirstFrame(vararg keys: Any?): Boolean {
    var first by remember(*keys) { mutableStateOf(true) }
    LaunchedEffect(*keys) { first = false }
    return first
}

/**
 * Data aa gaya par saath wala jawab (jaise cross promo) abhi raste me: pending ho to ms tak true (skeleton dikhao),
 * phir false (jawab na aaye to screen jaisi hai waisi). pending false = turant false.
 */
@Composable
fun dashPending(pending: Boolean, ms: Long = 8_000L): Boolean {
    var wait by remember(pending) { mutableStateOf(pending) }
    if (pending) LaunchedEffect(Unit) { delay(ms); wait = false }
    return wait
}

/**
 * Aam detail screen ka skeleton: hero + numbers card (TileGrid jaisa; stats = 0: nahi) + do section (Users / Ads / Promo jaisa).
 * Fold: do column.
 */
@Composable
fun DashSkel(tint: KTint = KTint.Green, stats: Int = 4, cols: Int = 2) {
    KSkeleton {
        val left: @Composable ColumnScope.() -> Unit = {
            KSkelHero(tint)
            if (stats > 0) KSkelStats(stats, cols)
        }
        val right: @Composable ColumnScope.() -> Unit = {
            KSkelSection()
            KSkelCard(4)
            KSkelSection(width = 110.dp)
            KSkelCard(3)
        }
        if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
    }
}

/**
 * Detail screen ka dhaancha (kit): app bar (back = wapas), period tabs, app chips, filter summary, phir content.
 * Filters bar ke Filter icon (ginti ke saath) ke peeche sheet me.
 * sort (rememberKSort) diya ho to Filter ke baad Sort button, aur default se alag ho to filter summary ke shuru me pill.
 */
@Composable
fun TabScreen(title: String, vm: DashVm, st: UiState, nav: NavHostController, showRange: Boolean = true, back: Boolean = false, showApps: Boolean = true,
              skeleton: @Composable () -> Unit = { DashSkel() },
              filters: (@Composable ColumnScope.(View) -> Unit)? = null,
              sort: KSortState? = null, sortOptions: List<Pair<String, String>> = emptyList(), sortKinds: Map<String, KSortKind> = emptyMap(),
              content: @Composable ColumnScope.(View) -> Unit) {
    val pkg = if (showApps) st.appFilter else null
    LaunchedEffect(pkg, st.range, st.from, st.to, st.lastOk) { if (pkg != null) vm.loadApp(pkg, force = true) }
    val first = dashFirstFrame(pkg, st.range, st.from, st.to, st.lastOk)
    val box = remember { FilterBox() }
    val d = if (pkg == null) st.data else st.appData[pkg]
    val v = if (d == null) null else View(d, if (pkg == null) st.extra else st.appExtra[pkg], st.extraMissing, if (pkg == null) st.cp else st.appCp[pkg], pkg)
    KScreen(top = {
        DashBar(title, vm, st, nav, back = back, extra = {
            if (filters != null && box.rows.isNotEmpty()) {
                KBarIcon(KIcons.Filter, "Filters".t(), { box.open = true }, badge = box.active.size, badgeUrgent = false)
            }
            if (sort != null) KSortButton(sort, sortOptions, sortKinds)
            if (showRange) DashRangeIcon(vm, st)
        })
        if (showRange) DashPeriodTabs(vm, st, Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp))
    }) {
        if (showApps) AppChips(vm, st)
        when {
            v != null -> {
                if (filters != null) CompositionLocalProvider(LocalFilterBox provides box) { filters(v) }
                FilterSummary(box, lead = sort?.let { kSortLead(it, sortOptions, sortKinds) })
                content(v)
            }
            else -> DashNoData(st, st.loading || (pkg != null && (st.appLoading == pkg || first)), skeleton) {
                if (pkg != null) vm.loadApp(pkg, force = true) else vm.refresh()
            }
        }
    }
    FilterSheet(box)
}

// ---------------------------------------------------------------- money bits (Paisa + App detail)

/** "+₹18,450" / "−₹2,140" (asli minus) / "₹0". */
fun moneySigned(v: Double): String = when {
    !v.isFinite() -> "–"
    abs(v) < 0.5 -> "₹0"
    v > 0 -> "+" + inr(v)
    else -> "−₹" + num(-v)
}

/** Hero label period ke hisaab se: Aaj ka munafa / Kal ka munafa / 7 din ka munafa ... */
fun moneyProfitLabel(range: String): String = when (range) {
    "today" -> "Today's profit".t()
    "yesterday" -> "Yesterday's profit".t()
    "7d" -> "7 days profit".t()
    "30d" -> "30 days profit".t()
    else -> "Net profit".t()
}

/** Hero ke daayein: aaj ka data = Live · 2 min pehle, warna period. */
@Composable
private fun MoneyNote(d: DashData) {
    if (d.today && !d.cached) KLive("Live".t() + " · " + agoText(d.fetchedAt))   // disk wala copy = Live nahi, period dikhao
    else DashText(periodText(d), 13.sp, Mfc.c.ink2)
}

/**
 * Hero ki paisa line (Home + Paisa + App detail): Kamai (ads + payments) | Ads se | Payments | Ads kharcha.
 * Kamai gol kiye hisson ka jod (₹2,146 + ₹178 = ₹2,324), taaki saath dikhte number ₹1 se na bhatkein.
 */
fun moneySplit(ads: Double, pay: Double, spend: Double): List<Pair<String, String>> {
    val a = Math.round(ads).toDouble()
    val p = Math.round(pay).toDouble()
    return listOf("Earnings".t() to inr(a + p), "From ads".t() to inr(a), "Payments".t() to inr(p), "Ads spend".t() to inr(spend))
}

/** Munafa hero: bada profit, Kamai | Ads se | Payments | Ads kharcha, ROAS meter (target = Settings ka ROAS target). */
@Composable
fun MoneyHero(d: DashData, st: UiState, label: String, modifier: Modifier = Modifier) {
    val k = d.kpi
    val earned = k.revInr + k.purchaseInr
    val roas = k.roasTotal ?: (if (k.gadsCostInr > 0) earned / k.gadsCostInr else null)
    val p = k.profitInr
    KHero(KTint.Green, modifier) {
        KHeroHead(label, trailing = { MoneyNote(d) })
        val c = Mfc.c
        KBigNum(moneySigned(p), when { p >= 0.5 -> c.green; p <= -0.5 -> c.red; else -> Color.Unspecified }, size = 48.sp)
        KSplit(moneySplit(k.revInr, k.purchaseInr, k.gadsCostInr))
        KRoasMeter(roas, st.roasTarget)
    }
}

private val moneyDayIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val moneyDayWk = SimpleDateFormat("EEE", Locale.US)

/**
 * Pichhle n din ka munafa (history + aaj ka live kpi). Pair(label, profit ₹); aakhri = sabse naya din.
 * Label: n <= 10 to din ka naam (Thu) / Aaj, warna tareekh (12).
 */
fun moneyDays(d: DashData, n: Int = 7): List<Pair<String, Double>> {
    val r = d.r
    val m = HashMap<String, Double>()
    d.history.forEach { m[it.day] = it.profit(r) }
    // ek din ka period: kpi hi us din ka sahi (aaj ho to live) hisaab hai
    if (d.single && d.from.isNotBlank()) m[d.from] = d.kpi.profitInr
    val end = d.to.ifBlank { d.from }
    val days = m.keys.filter { end.isBlank() || it <= end }.sorted().takeLast(n)
    if (days.isEmpty() && d.seriesKind == "day") {
        return d.series.takeLast(n).map { it.label to (it.revUsd * r - it.gadsCostInr) }
    }
    val today = if (d.today) end else moneyDayIn.format(Date())
    return days.map { day ->
        val label = when {
            day == today -> "Today".t()
            n > 10 -> day.takeLast(2).trimStart('0')
            else -> runCatching { moneyDayIn.parse(day)?.let { moneyDayWk.format(it) } }.getOrNull() ?: fmtDay(day)
        }
        label to (m[day] ?: 0.0)
    }
}

/** "7 din ka munafa" section + bars card (30d range par 30 din). */
@Composable
fun MoneyWeekCard(d: DashData, range: String) {
    val n = if (range == "30d") 30 else 7
    val days = moneyDays(d, n)
    KSection(if (n == 30) "30 days profit".t() else "7 days profit".t(), trailing = {
        if (days.isNotEmpty()) KPill(moneySigned(days.sumOf { it.second }), KKind.NEUTRAL, large = true)
    })
    KCard { KWeekBars(days.map { it.second }, days.map { it.first }) }
}

// ---------------------------------------------------------------- small helpers

fun periodText(d: DashData) = if (d.single) fmtDay(d.from) else fmtDay(d.from) + " – " + fmtDay(d.to)

/** Pichhle din ka history row (delta "vs yesterday" ke liye): sirf single-day period par. */
fun prevDay(d: DashData) = if (d.single) d.history.filter { it.day < d.from }.maxByOrNull { it.day } else null
fun delta(now: Double, prev: Double?): Double? = if (prev == null || prev == 0.0) null else (now - prev) / prev

/** CampaignScreen ka index (st.data.campaigns me). App ke data wala campaign bhi naam + account se mil jata hai. -1 = nahi mila. */
fun dashCampaignIdx(st: UiState, c: Campaign): Int {
    val list = st.data?.campaigns ?: return -1
    val i = list.indexOf(c)
    if (i >= 0) return i
    return list.indexOfFirst { it.campaign == c.campaign && it.account == c.account }
}

/** Data se chips ke options: distinct values (sorted), pehle "All". */
fun optionsOf(values: List<String>, all: String = "All".t()): List<Pair<String, String>> =
    listOf("" to all) + values.filter { it.isNotBlank() }.distinct().sorted().map { it to it }
