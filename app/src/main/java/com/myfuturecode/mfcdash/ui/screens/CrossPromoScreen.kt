package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.cpFmtName
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*

/** api/crosspromo.php: which app shows which app's promo, ad type, views/clicks/installs, design CTR. Filters: shown in, promoted, type. */
@Composable
fun CrossPromoScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var src by rememberSaveable { mutableStateOf("") }
    var tgt by rememberSaveable { mutableStateOf("") }
    var fmt by rememberSaveable { mutableStateOf("") }
    TabScreen("Cross promo".t(), vm, st, nav, back = true, showApps = false, filters = { v ->
        val all = v.cp?.rows ?: emptyList()
        val appOpts = { pk: List<String> -> listOf("" to "All".t()) + pk.filter { it.isNotBlank() }.distinct().map { it to v.d.appName(it) } }
        if (all.isNotEmpty()) {
            FilterChips("Shown in".t(), appOpts(all.map { it.src }), src) { src = it }
            FilterChips("Promoted".t(), appOpts(all.map { it.tgt }), tgt) { tgt = it }
            FilterChips("Type".t(), listOf("" to "All".t()) + all.map { it.fmt }.distinct().sorted().map { it to cpFmtName(it) }, fmt) { fmt = it }
        }
    }) { v ->
        val d = v.d
        val ex = MaterialTheme.extra
        val cp = v.cp
        val rows = (cp?.rows ?: emptyList()).filter { (src.isBlank() || it.src == src) && (tgt.isBlank() || it.tgt == tgt) && (fmt.isBlank() || it.fmt == fmt) }
        val vw = rows.sumOf { it.views }; val c = rows.sumOf { it.clicks }; val i = rows.sumOf { it.installs }; val rw = rows.sumOf { it.rewards }
        TileGrid(listOf(
            Triple("Views".t(), num(vw), null), Triple("Clicks".t(), num(c), null), Triple("CTR", pct(if (vw > 0) c / vw else null), null),
            Triple("Installs".t(), num(i), "1 phone = 1"), Triple("Install / click".t(), pct(if (c > 0) i / c else null), null), Triple("Rewards".t(), num(rw), "rewarded".t()),
        ), colors = mapOf(3 to ex.good))

        SectionTitle("By ad type".t())
        Panel {
            val g = rows.groupBy { it.fmt }.map { (f, l) -> Triple(cpFmtName(f), l.sumOf { it.views }, l.sumOf { it.installs }) }.sortedByDescending { it.second }
            Table(listOf("Ad type".t(), "Views".t(), "Installs".t(), "Inst / 1000 views".t()), g.map { listOf(it.first, num(it.second), num(it.third), num(if (it.second > 0) it.third / it.second * 1000 else null, 1)) }, empty = "No promo shown in this period".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Which app promotes which".t())
        Panel {
            Table(listOf("Shown in".t(), "Promoted".t(), "Type".t(), "Views".t(), "Clicks".t(), "CTR", "Installs".t(), "Inst/click".t()),
                rows.sortedByDescending { it.views }.map { listOf(d.appName(it.src), d.appName(it.tgt), cpFmtName(it.fmt), num(it.views), num(it.clicks), pct(it.ctr), num(it.installs), pct(it.installPerClick)) },
                empty = "No cross promo shown in this period".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("By promoted app".t(), "who got how many installs".t())
        Panel {
            val g = rows.groupBy { it.tgt }.map { (t, l) -> Triple(d.appName(t), l.sumOf { it.installs }, l.sumOf { it.clicks }) }.sortedByDescending { it.second }
            val tot = g.sumOf { it.second }
            HBars(g.map { Triple(it.first, num(it.second) + " " + "installs".t() + " · " + num(it.third) + " " + "clicks".t(), shareOf(it.second, tot)) }, color = ex.good)
            if (g.isEmpty()) Empty("No data".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("By design".t(), "which mockup gets more clicks".t())
        Panel {
            val ds = (cp?.designs ?: emptyList()).filter { (src.isBlank() || it.src == src) && (fmt.isBlank() || it.fmt == fmt) }
            Table(listOf("Shown in".t(), "Design".t(), "Type".t(), "Views".t(), "Clicks".t(), "CTR"),
                ds.sortedByDescending { it.ctr ?: 0.0 }.map { listOf(d.appName(it.src), it.design.ifBlank { "–" }, cpFmtName(it.fmt), num(it.views), num(it.clicks), pct(it.ctr)) }, empty = "No design data yet".t())
        }
        Spacer(Modifier.height(8.dp))
        Text("An install counts only when the promoted app is also connected to the dashboard (tracker_url/tracker_key) and runs the new tracker version.".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
