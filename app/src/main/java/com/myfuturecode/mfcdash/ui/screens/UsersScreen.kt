package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.BarLineChart
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.components.Empty
import com.myfuturecode.mfcdash.ui.components.HBars
import com.myfuturecode.mfcdash.ui.components.Legend
import com.myfuturecode.mfcdash.ui.components.Table
import com.myfuturecode.mfcdash.ui.components.TileGrid
import com.myfuturecode.mfcdash.ui.components.shareOf
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KLive
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.minutes
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.usd

/** Users: abhi active (hero), active / installs numbers, retention, sources, countries, versions, live countries. Filters: country sort, rows, source sort. */
@Composable
fun UsersScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var ccSort by rememberSaveable { mutableStateOf("rev") }     // rev | installs | users | imps
    var rows by rememberSaveable { mutableStateOf("10") }        // 10 | all
    var srcSort by rememberSaveable { mutableStateOf("installs") } // installs | rev
    TabScreen("Users".t(), vm, st, nav, back = true, filters = {
        FilterChips("Countries".t(), listOf("rev" to "Revenue".t(), "installs" to "Installs".t(), "users" to "Users".t(), "imps" to "Impressions".t()), ccSort) { ccSort = it }
        FilterChips("Rows".t(), listOf("10" to "Top 10".t(), "all" to "All".t()), rows) { rows = it }
        FilterChips("Sources".t(), listOf("installs" to "Installs".t(), "rev" to "Revenue".t()), srcSort) { srcSort = it }
    }) { v ->
        val d = v.d
        val c = Mfc.c
        val k = d.kpi
        val r = d.r
        val ax = v.extra?.active
        val lm = v.extra?.liveMinutes ?: 6
        val limit = if (rows == "all") Int.MAX_VALUE else 10
        val ex2 = v.extra

        val left: @Composable ColumnScope.() -> Unit = {
            // hero: abhi kitne log app me
            KHero(KTint.Green) {
                KHeroHead("Active now".t(), trailing = { KLive(tf("last %s min", lm)) })
                KBigNum(num(d.liveTotal), size = 48.sp)
                KSplit(listOf("30 min" to num(d.live30), "DAU" to num(k.dau), "Installs".t() to num(k.installs)))
            }
            TileGrid(
                listOf(
                    Triple("1 hour".t(), num(ax?.live1h), if (ax == null) "needs app.php".t() else "active".t()),
                    Triple("24 hours".t(), num(ax?.live24h), if (ax == null) "needs app.php".t() else "active".t()),
                    Triple("WAU", num(ax?.wau), "7 days".t()),
                    Triple("MAU", num(ax?.mau), if (ax?.dauMau == null) "30 days".t() else "DAU/MAU " + pct(ax.dauMau, 0)),
                    Triple("Installed".t(), num(ax?.installedTotal), if (ax == null) null else tf("%s uninstalled (total)", num(ax.uninstalledTotal))),
                    Triple("Sessions".t(), num(k.sessions), if (k.dau > 0) num(k.sessions / k.dau, 1) + " / user" else null),
                    Triple("Avg session".t(), minutes(k.avgSessionSec), null),
                    Triple("Uninstalls".t(), num(k.uninstalls), if (k.installs > 0) tf("%s of installs", pct(k.uninstalls / k.installs, 0)) else null),
                    Triple("Impr / DAU".t(), num(k.impsPerDau, 1), null),
                    Triple("ARPDAU", inr(k.arpdauUsd * r, 2), usd(k.arpdauUsd, 4)),
                ),
                cols = 2,
            )
            KSection("Retention".t(), trailing = { DashText("D1 / D3 / D7 / D14", 13.sp, c.ink3) })
            if (ex2 == null) ExtraMissingCard("Retention".t()) else KCard { RetentionTable(ex2) }
            if (ex2 != null && ex2.retentionDays.size > 1) {
                KSection("Retention by day".t())
                KCard { RetentionDaysTable(ex2) }
            }
            KSection(if (d.seriesKind == "day") "Daily".t() else "Hourly".t())
            KCard {
                BarLineChart(d.series.map { it.label }, d.series.map { it.revUsd * r }, line2 = d.series.map { it.installs })
                Legend("Revenue ₹".t() to c.brand, "Installs".t() to c.zGreen)
            }
        }
        val right: @Composable ColumnScope.() -> Unit = {
            KSection("Install source".t())
            KCard {
                val tot = d.bySource.sumOf { it.installs }
                val src = if (srcSort == "rev") d.bySource.sortedByDescending { it.revUsd } else d.bySource.sortedByDescending { it.installs }
                if (d.bySource.isEmpty()) Empty("No installs yet".t())
                else {
                    HBars(src.take(8).map { Triple(it.src.ifBlank { "(none)" }, num(it.installs) + " · " + inr(it.revUsd * r), shareOf(it.installs, tot)) })
                    Spacer(Modifier.height(8.dp))
                    Table(
                        listOf("Source".t(), "Installs".t(), "Users (ads)".t(), "Revenue".t()),
                        src.take(limit).map { listOf(it.src.ifBlank { "(none)" }, num(it.installs), num(it.users), inr(it.revUsd * r, 1)) },
                        empty = "",
                    )
                }
            }
            if (d.utm.isNotEmpty()) {
                KSection("utm_campaign")
                KCard {
                    Table(listOf("Campaign".t(), "Installs".t()), d.utm.sortedByDescending { it.n }.take(limit).map { listOf(it.c, num(it.n)) })
                }
            }
            KSection("Countries".t(), trailing = {
                DashText(tf("%s of %s", num(minOf(limit, d.byCountry.size).toDouble()), num(d.byCountry.size.toDouble())), 13.sp, c.ink3)
            })
            KCard {
                val cc = when (ccSort) {
                    "installs" -> d.byCountry.sortedByDescending { it.installs }
                    "users" -> d.byCountry.sortedByDescending { it.users }
                    "imps" -> d.byCountry.sortedByDescending { it.imps }
                    else -> d.byCountry.sortedByDescending { it.rev }
                }
                Table(
                    listOf("Country".t(), "Installs".t(), "Users".t(), "Impr".t(), "Revenue".t(), "eCPM"),
                    cc.take(limit).map { listOf(it.cc, num(it.installs), num(it.users), num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) },
                    empty = "No data".t(),
                )
            }
            KSection("App versions".t())
            KCard {
                Table(
                    if (v.allApps) listOf("App".t(), "Version".t(), "Users".t()) else listOf("Version".t(), "Users".t()),
                    d.versions.sortedByDescending { it.users }.take(limit).map { if (v.allApps) listOf(d.appName(it.pkg), it.ver, num(it.users)) else listOf(it.ver, num(it.users)) },
                    empty = "No data".t(),
                )
            }
            KSection("Live countries".t())
            KCard {
                val tot = d.liveCountries.sumOf { it.n }
                if (d.liveCountries.isEmpty()) Empty("Nobody live".t())
                else HBars(d.liveCountries.sortedByDescending { it.n }.take(8).map { Triple(it.cc, num(it.n), shareOf(it.n, tot)) }, color = c.zGreen)
            }
        }
        if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
    }
}
