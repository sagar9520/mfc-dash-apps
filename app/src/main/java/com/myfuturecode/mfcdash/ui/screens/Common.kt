package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.CpData
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.ExtraData
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.TopBar
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.ChipRow
import com.myfuturecode.mfcdash.ui.components.MChip
import com.myfuturecode.mfcdash.ui.theme.parseHex
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.t
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val RANGES = listOf("today" to "Today", "yesterday" to "Yesterday", "7d" to "7 days", "30d" to "30 days", "custom" to "Custom")

/** Jo data tab dikha raha hai: sab apps ya ek app (app filter). */
class View(val d: DashData, val extra: ExtraData?, val extraMissing: Boolean, val cp: CpData?, val pkg: String?) {
    val allApps get() = pkg == null
}

/** Period chips: today / yesterday / 7d / 30d / custom (date range). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeBar(vm: DashVm, st: UiState, lead: (@Composable () -> Unit)? = null) {
    var pick by remember { mutableStateOf(false) }
    ChipRow {
        lead?.invoke()
        RANGES.forEach { (k, l) ->
            MChip(if (k == "custom" && st.range == "custom" && st.from != null) fmtDay(st.from) + " – " + fmtDay(st.to) else l.t(), on = st.range == k,
                onClick = { if (k == "custom") pick = true else vm.setRange(k) })
        }
    }
    if (pick) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pick = false },
            confirmButton = {
                TextButton(onClick = {
                    val a = state.selectedStartDateMillis; val b = state.selectedEndDateMillis ?: a
                    if (a != null && b != null) {
                        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                        vm.setRange("custom", f.format(Date(minOf(a, b))), f.format(Date(maxOf(a, b))))
                    }
                    pick = false
                }) { Text("Apply".t()) }
            },
            dismissButton = { TextButton(onClick = { pick = false }) { Text("Cancel".t()) } },
        ) { DateRangePicker(state, modifier = Modifier.weight(1f), title = { Text("Pick a period".t(), Modifier.padding(16.dp)) }) }
    }
}

/** App filter chips (har tab par): All apps / ek app. Ek app chunne par us app ka data.php?app=<pkg> aata hai. */
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

/**
 * Screen ke filters ek "Filter" chip ke peeche rehte hain, taaki upar sirf 2 chip lines dikhen (period + apps).
 * Tap = neeche se sheet (Material bottom sheet) jisme har filter ki chips hain; chunne ke baad chip par ginti
 * aur chips ke neeche ek line "Sort: Revenue · Show: In loss · Clear". FilterChips apni poori jankari
 * (label, options, selected, onSelect) FilterBox mein register karta hai aur inline kuch nahi banata.
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        ChipRow { options.forEach { (k, l) -> MChip(l, on = selected == k, onClick = { onSelect(k) }) } }
    }
    Spacer(Modifier.height(6.dp))
}

/** "Filter" chip (ginti ke saath jab kuch chuna ho): tap = sheet. */
@Composable
fun FilterToggle(box: FilterBox) {
    if (box.rows.isEmpty()) return
    val n = box.active.size
    val on = n > 0
    val bg = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).background(bg).clickable { box.open = true }.height(34.dp).padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Tune, null, Modifier.size(16.dp), tint = fg)
        Spacer(Modifier.width(5.dp))
        Text("Filter".t(), style = MaterialTheme.typography.labelLarge, color = fg)
        if (on) {
            Spacer(Modifier.width(6.dp))
            Text(n.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.onPrimary).padding(horizontal = 6.dp, vertical = 1.dp))
        }
    }
}

/** Chips ke neeche ek line: kaunse filter lage hain + Clear. Kuch na laga ho to kuch nahi. */
@Composable
fun FilterSummary(box: FilterBox) {
    val act = box.active
    if (act.isEmpty()) return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val prim = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(buildAnnotatedString {
            act.forEachIndexed { i, r ->
                if (i > 0) withStyle(SpanStyle(color = muted)) { append(" · ") }
                withStyle(SpanStyle(color = muted)) { append(r.label + ": ") }
                withStyle(SpanStyle(color = prim, fontWeight = FontWeight.SemiBold)) { append(r.selectedText) }
            }
        }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Clear".t() + " ✕", style = MaterialTheme.typography.bodySmall, color = prim, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { box.reset() }.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

/** Neeche se khulne wali sheet: har filter ka label + chips, upar Reset, neeche Done. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(box: FilterBox) {
    if (!box.open) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { box.open = false }, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters".t(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { box.reset() }, enabled = box.active.isNotEmpty()) { Text("Reset".t()) }
            }
            box.list.forEach { r ->
                Spacer(Modifier.height(8.dp))
                Text(r.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    r.options.forEach { (k, l) -> MChip(l, on = r.selected == k, onClick = { box.select(r.label, k) }) }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { box.open = false }, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("Done".t()) }
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** Tab screen ka dhaancha: top bar, [Filter] + period chips, app chips, filter summary line, phir content (data aane par). */
@Composable
fun TabScreen(title: String, vm: DashVm, st: UiState, nav: NavHostController, showRange: Boolean = true, back: Boolean = false, showApps: Boolean = true,
              filters: (@Composable ColumnScope.(View) -> Unit)? = null, content: @Composable ColumnScope.(View) -> Unit) {
    val pkg = if (showApps) st.appFilter else null
    LaunchedEffect(pkg, st.range, st.from, st.to, st.lastOk) { if (pkg != null) vm.loadApp(pkg, force = true) }
    val box = remember { FilterBox() }
    val toggle: (@Composable () -> Unit)? = if (filters != null) ({ FilterToggle(box) }) else null
    Column(Modifier.fillMaxSize()) {
        TopBar(title, vm, st, nav, back = back)
        val d = if (pkg == null) st.data else st.appData[pkg]
        val v = if (d == null) null else View(d, if (pkg == null) st.extra else st.appExtra[pkg], st.extraMissing, if (pkg == null) st.cp else st.appCp[pkg], pkg)
        val appsShown = showApps && !st.data?.apps.isNullOrEmpty()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            if (showRange) { RangeBar(vm, st, toggle); Spacer(Modifier.height(8.dp)) }
            if (showApps) { AppChips(vm, st, if (showRange) null else toggle); if (appsShown) Spacer(Modifier.height(8.dp)) }
            if (!showRange && !appsShown && toggle != null && box.rows.isNotEmpty()) { ChipRow { toggle() }; Spacer(Modifier.height(8.dp)) }
            when {
                v != null -> {
                    if (filters != null) CompositionLocalProvider(LocalFilterBox provides box) { filters(v) }
                    FilterSummary(box)
                    content(v)
                }
                st.loading || st.appLoading == pkg -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(st.error ?: "No data received".t(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { if (pkg != null) vm.loadApp(pkg, force = true) else vm.refresh() }) { Text("Try again".t()) }
                }
            }
        }
    }
    FilterSheet(box)
}

fun periodText(d: DashData) = if (d.single) fmtDay(d.from) else fmtDay(d.from) + " – " + fmtDay(d.to)

/** Pichhle din ka history row (delta "vs yesterday" ke liye): sirf single-day period par. */
fun prevDay(d: DashData) = if (d.single) d.history.filter { it.day < d.from }.maxByOrNull { it.day } else null
fun delta(now: Double, prev: Double?): Double? = if (prev == null || prev == 0.0) null else (now - prev) / prev

/** Data se chips ke options: distinct values (sorted), pehle "All". */
fun optionsOf(values: List<String>, all: String = "All".t()): List<Pair<String, String>> =
    listOf("" to all) + values.filter { it.isNotBlank() }.distinct().sorted().map { it to it }
