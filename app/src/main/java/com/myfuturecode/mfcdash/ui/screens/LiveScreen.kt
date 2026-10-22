package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.components.Empty
import com.myfuturecode.mfcdash.ui.components.HBars
import com.myfuturecode.mfcdash.ui.components.Table
import com.myfuturecode.mfcdash.ui.components.shareOf
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KLive
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.parseHex
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tOnly

/** Live: abhi kitne users (hero), app-wise live, impressions feed, naye installs, crashes. Filters: country, ad format, rows. */
@Composable
fun LiveScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var cc by rememberSaveable { mutableStateOf("") }
    var fmt by rememberSaveable { mutableStateOf("") }
    var rows by rememberSaveable { mutableStateOf("30") }
    TabScreen("Live".t(), vm, st, nav, showRange = false, back = true, filters = { v ->
        val ccs = optionsOf(v.d.liveCountries.sortedByDescending { it.n }.map { it.cc }.take(8) + v.d.recent.map { it.cc } + v.d.recentInstalls.map { it.cc })
        val fmts = optionsOf(v.d.recent.map { it.fmt })
        if (ccs.size > 2) FilterChips("Country".t(), ccs, cc) { cc = it }
        if (fmts.size > 2) FilterChips("Format".t(), fmts, fmt) { fmt = it }
        FilterChips("Rows".t(), listOf("30" to "30", "100" to "100"), rows) { rows = it }
    }) { v ->
        val d = v.d
        val c = Mfc.c
        val r = d.r
        val limit = rows.toIntOrNull() ?: 30

        val left: @Composable ColumnScope.() -> Unit = {
            // hero: abhi live users + 30 min + top desh
            KHero(KTint.Green) {
                KHeroHead("Live users now".t(), trailing = { KLive("Live".t() + " · " + agoText(d.fetchedAt)) })
                KBigNum(num(d.liveTotal), size = 48.sp)
                val top = d.liveCountries.sortedByDescending { it.n }.take(2).map { it.cc to num(it.n) }
                KSplit(listOf("30 min" to num(d.live30)) + top)
            }
            if (v.allApps) {
                KSection("Live by app".t())
                KCard {
                    val tot = d.liveTotal
                    val list = d.apps.map { it to (d.liveByApp[it.pkg] ?: 0.0) }.sortedByDescending { it.second }
                    if (list.isEmpty()) Empty("No apps".t())
                    list.forEach { (a, n) -> HBars(listOf(Triple(a.name, num(n), shareOf(n, tot))), color = parseHex(a.color)) }
                }
            }
        }
        val right: @Composable ColumnScope.() -> Unit = {
            KSection("Live feed".t(), trailing = { DashText("30 min", 13.sp, c.ink3) })
            KCard {
                val feed = d.recent.filter { (cc.isBlank() || it.cc == cc) && (fmt.isBlank() || it.fmt == fmt) }
                Table(
                    if (v.allApps) listOf("Time".t(), "App".t(), "Network".t(), "Format".t(), "CC", "₹") else listOf("Time".t(), "Network".t(), "Format".t(), "CC", "₹"),
                    feed.take(limit).map { if (v.allApps) listOf(tOnly(it.ts), d.appName(it.pkg), it.net, it.fmt, it.cc, num(it.revUsd * r, 3)) else listOf(tOnly(it.ts), it.net, it.fmt, it.cc, num(it.revUsd * r, 3)) },
                    empty = if (d.today) "No impressions".t() else "Today only".t(),
                )
            }
            KSection("New installs".t(), trailing = { DashText("6 hours".t(), 13.sp, c.ink3) })
            KCard {
                val inst = d.recentInstalls.filter { cc.isBlank() || it.cc == cc }
                Table(
                    if (v.allApps) listOf("Time".t(), "App".t(), "CC", "Source".t(), "Phone".t()) else listOf("Time".t(), "CC", "Source".t(), "Phone".t()),
                    inst.take(limit).map { if (v.allApps) listOf(tOnly(it.firstSeen), d.appName(it.pkg), it.cc, it.src.ifBlank { "–" }, it.model) else listOf(tOnly(it.firstSeen), it.cc, it.src.ifBlank { "–" }, it.model) },
                    empty = if (d.today) "No new installs".t() else "Today only".t(),
                )
            }
            KSection("Crashes".t(), trailing = {
                val n = d.crashes.sumOf { it.n }
                KPill(num(n), if (n > 0) KKind.BAD else KKind.OK)
            })
            KCard {
                Table(
                    if (v.allApps) listOf("App".t(), "Ver".t(), "Error".t(), "Where".t(), "Count".t()) else listOf("Ver".t(), "Error".t(), "Where".t(), "Count".t()),
                    d.crashes.sortedByDescending { it.n }.take(limit).map { if (v.allApps) listOf(d.appName(it.pkg), it.ver, it.error, it.where, num(it.n)) else listOf(it.ver, it.error, it.where, num(it.n)) },
                    empty = "No crashes".t(),
                )
            }
        }
        if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
    }
}
