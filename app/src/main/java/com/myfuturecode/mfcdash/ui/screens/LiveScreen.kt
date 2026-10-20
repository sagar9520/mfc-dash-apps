package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.ui.theme.parseHex
import com.myfuturecode.mfcdash.util.*

/** Live tab: users now, feed, installs, crashes. Filters: country, ad format, rows. */
@Composable
fun LiveScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var cc by rememberSaveable { mutableStateOf("") }
    var fmt by rememberSaveable { mutableStateOf("") }
    var rows by rememberSaveable { mutableStateOf("30") }
    TabScreen("Live".t(), vm, st, nav, showRange = false, filters = { v ->
        val ccs = optionsOf(v.d.liveCountries.sortedByDescending { it.n }.map { it.cc }.take(8) + v.d.recent.map { it.cc } + v.d.recentInstalls.map { it.cc })
        val fmts = optionsOf(v.d.recent.map { it.fmt })
        if (ccs.size > 2) FilterChips("Country".t(), ccs, cc) { cc = it }
        if (fmts.size > 2) FilterChips("Format".t(), fmts, fmt) { fmt = it }
        FilterChips("Rows".t(), listOf("30" to "30", "100" to "100"), rows) { rows = it }
    }) { v ->
        val d = v.d
        val ex = MaterialTheme.extra
        val r = d.r
        val limit = rows.toIntOrNull() ?: 30
        GradientHeader("LIVE USERS NOW".t(), "refreshes every 20 sec".t()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(num(d.liveTotal), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(num(d.live30) + " " + "in 30 min".t(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(d.liveCountries.sortedByDescending { it.n }.take(5).joinToString(" · ") { it.cc + " " + num(it.n) }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .75f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (v.allApps) {
            SectionTitle("Live by app".t())
            Panel {
                val tot = d.liveTotal
                val list = d.apps.map { it to (d.liveByApp[it.pkg] ?: 0.0) }.sortedByDescending { it.second }
                list.forEach { (a, n) -> HBars(listOf(Triple(a.name, num(n), shareOf(n, tot))), color = parseHex(a.color)) }
                if (list.isEmpty()) Empty("No app list".t())
            }
            Spacer(Modifier.height(14.dp))
        }

        SectionTitle("Live feed".t(), "latest impressions (30 min)".t())
        Panel {
            val feed = d.recent.filter { (cc.isBlank() || it.cc == cc) && (fmt.isBlank() || it.fmt == fmt) }
            Table(if (v.allApps) listOf("Time".t(), "App".t(), "Network".t(), "Format".t(), "CC", "₹") else listOf("Time".t(), "Network".t(), "Format".t(), "CC", "₹"),
                feed.take(limit).map { if (v.allApps) listOf(tOnly(it.ts), d.appName(it.pkg), it.net, it.fmt, it.cc, num(it.revUsd * r, 3)) else listOf(tOnly(it.ts), it.net, it.fmt, it.cc, num(it.revUsd * r, 3)) },
                empty = if (d.today) "No impressions in 30 min".t() else "Live feed only for today's period".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("New installs".t(), "last 6 hours".t())
        Panel {
            val inst = d.recentInstalls.filter { cc.isBlank() || it.cc == cc }
            Table(if (v.allApps) listOf("Time".t(), "App".t(), "CC", "Source".t(), "Phone".t()) else listOf("Time".t(), "CC", "Source".t(), "Phone".t()),
                inst.take(limit).map { if (v.allApps) listOf(tOnly(it.firstSeen), d.appName(it.pkg), it.cc, it.src.ifBlank { "–" }, it.model) else listOf(tOnly(it.firstSeen), it.cc, it.src.ifBlank { "–" }, it.model) },
                empty = if (d.today) "No new install in 6 hours".t() else "Only for today's period".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Crashes".t(), "in this period".t())
        Panel {
            Table(if (v.allApps) listOf("App".t(), "Ver".t(), "Error".t(), "Where".t(), "Count".t()) else listOf("Ver".t(), "Error".t(), "Where".t(), "Count".t()),
                d.crashes.sortedByDescending { it.n }.take(limit).map { if (v.allApps) listOf(d.appName(it.pkg), it.ver, it.error, it.where, num(it.n)) else listOf(it.ver, it.error, it.where, num(it.n)) }, empty = "No crashes 🎉".t())
        }
        Spacer(Modifier.height(8.dp))
        Text("Change the period from the Home/Money tabs. The live feed is always today.".t(), style = MaterialTheme.typography.bodySmall, color = ex.muted)
    }
}
