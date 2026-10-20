package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun RangeBar(vm: DashVm, st: UiState) {
    var pick by remember { mutableStateOf(false) }
    ChipRow {
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
fun AppChips(vm: DashVm, st: UiState) {
    val apps = st.data?.apps ?: return
    if (apps.isEmpty()) return
    ChipRow {
        MChip("All apps".t(), on = st.appFilter == null, onClick = { vm.setAppFilter(null) })
        apps.forEach { a -> MChip(a.name, on = st.appFilter == a.pkg, onClick = { vm.setAppFilter(a.pkg) }, dot = parseHex(a.color)) }
    }
}

/** Ek screen ke apne filter chips (label + options). */
@Composable
fun FilterChips(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        ChipRow { options.forEach { (k, l) -> MChip(l, on = selected == k, onClick = { onSelect(k) }) } }
    }
    Spacer(Modifier.height(6.dp))
}

/** Tab screen ka dhaancha: top bar, period chips, app chips, screen ke filters, phir content (data aane par). */
@Composable
fun TabScreen(title: String, vm: DashVm, st: UiState, nav: NavHostController, showRange: Boolean = true, back: Boolean = false, showApps: Boolean = true,
              filters: @Composable ColumnScope.(View) -> Unit = {}, content: @Composable ColumnScope.(View) -> Unit) {
    val pkg = if (showApps) st.appFilter else null
    LaunchedEffect(pkg, st.range, st.from, st.to, st.lastOk) { if (pkg != null) vm.loadApp(pkg, force = true) }
    Column(Modifier.fillMaxSize()) {
        TopBar(title, vm, st, nav, back = back)
        val d = if (pkg == null) st.data else st.appData[pkg]
        val v = if (d == null) null else View(d, if (pkg == null) st.extra else st.appExtra[pkg], st.extraMissing, if (pkg == null) st.cp else st.appCp[pkg], pkg)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (showRange) { RangeBar(vm, st); Spacer(Modifier.height(8.dp)) }
            if (showApps) { AppChips(vm, st); Spacer(Modifier.height(8.dp)) }
            when {
                v != null -> { filters(v); content(v) }
                st.loading || st.appLoading == pkg -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(st.error ?: "No data received".t(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { if (pkg != null) vm.loadApp(pkg, force = true) else vm.refresh() }) { Text("Try again".t()) }
                }
            }
        }
    }
}

fun periodText(d: DashData) = if (d.single) fmtDay(d.from) else fmtDay(d.from) + " – " + fmtDay(d.to)

/** Pichhle din ka history row (delta "vs yesterday" ke liye): sirf single-day period par. */
fun prevDay(d: DashData) = if (d.single) d.history.filter { it.day < d.from }.maxByOrNull { it.day } else null
fun delta(now: Double, prev: Double?): Double? = if (prev == null || prev == 0.0) null else (now - prev) / prev

/** Data se chips ke options: distinct values (sorted), pehle "All". */
fun optionsOf(values: List<String>, all: String = "All".t()): List<Pair<String, String>> =
    listOf("" to all) + values.filter { it.isNotBlank() }.distinct().sorted().map { it to it }
