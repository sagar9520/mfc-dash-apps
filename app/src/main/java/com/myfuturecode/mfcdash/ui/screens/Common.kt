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
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.TopBar
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.ChipRow
import com.myfuturecode.mfcdash.ui.components.MChip
import com.myfuturecode.mfcdash.util.fmtDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val RANGES = listOf("today" to "Aaj", "yesterday" to "Kal", "7d" to "7 din", "30d" to "30 din", "custom" to "Custom")

/** Period chips: site ke jaise today / yesterday / 7d / 30d / custom (date range). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeBar(vm: DashVm, st: UiState) {
    var pick by remember { mutableStateOf(false) }
    ChipRow {
        RANGES.forEach { (k, l) ->
            MChip(if (k == "custom" && st.range == "custom" && st.from != null) fmtDay(st.from) + " – " + fmtDay(st.to) else l, on = st.range == k,
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
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { pick = false }) { Text("Cancel") } },
        ) { DateRangePicker(state, modifier = Modifier.weight(1f), title = { Text("Period chuno", Modifier.padding(16.dp)) }) }
    }
}

/** Tab screen ka dhaancha: top bar, period chips, phir content (data aane par). */
@Composable
fun TabScreen(title: String, vm: DashVm, st: UiState, nav: NavHostController, showRange: Boolean = true, back: Boolean = false, content: @Composable ColumnScope.(DashData) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title, vm, st, nav, back = back)
        val d = st.data
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (showRange) { RangeBar(vm, st); Spacer(Modifier.height(12.dp)) }
            when {
                d != null -> content(d)
                st.loading -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(st.error ?: "Data nahi aaya", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.refresh() }) { Text("Dobara try karo") }
                }
            }
        }
    }
}

fun periodText(d: DashData) = if (d.single) fmtDay(d.from) else fmtDay(d.from) + " – " + fmtDay(d.to)

/** Pichhle din ka history row (delta "vs kal" ke liye): sirf single-day period par. */
fun prevDay(d: DashData) = if (d.single) d.history.filter { it.day < d.from }.maxByOrNull { it.day } else null
fun delta(now: Double, prev: Double?): Double? = if (prev == null || prev == 0.0) null else (now - prev) / prev
