package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*

/** Users tab: active / retention / installs / sources / countries / versions. Filters: country sort, rows, source sort. */
@Composable
fun UsersScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var ccSort by rememberSaveable { mutableStateOf("rev") }     // rev | installs | users | imps
    var rows by rememberSaveable { mutableStateOf("10") }        // 10 | all
    var srcSort by rememberSaveable { mutableStateOf("installs") } // installs | rev
    TabScreen("Users".t(), vm, st, nav, filters = {
        FilterChips("Countries".t(), listOf("rev" to "Revenue".t(), "installs" to "Installs".t(), "users" to "Users".t(), "imps" to "Impressions".t()), ccSort) { ccSort = it }
        FilterChips("Rows".t(), listOf("10" to "Top 10".t(), "all" to "All".t()), rows) { rows = it }
        FilterChips("Sources".t(), listOf("installs" to "Installs".t(), "rev" to "Revenue".t()), srcSort) { srcSort = it }
    }) { v ->
        val d = v.d
        val ex = MaterialTheme.extra
        val k = d.kpi; val r = d.r
        val ax = v.extra?.active
        val lm = v.extra?.liveMinutes ?: 6
        val limit = if (rows == "all") Int.MAX_VALUE else 10
        TileGrid(listOf(
            Triple("$lm min", num(d.liveTotal), "active now".t()),
            Triple("30 min", num(d.live30), "active".t()),
            Triple("1 hour".t(), num(ax?.live1h), if (ax == null) "needs app.php".t() else "active".t()),
            Triple("24 hours".t(), num(ax?.live24h), if (ax == null) "needs app.php".t() else "active".t()),
            Triple("WAU", num(ax?.wau), "7 days".t()),
            Triple("MAU", num(ax?.mau), if (ax?.dauMau == null) "30 days".t() else "DAU/MAU " + pct(ax.dauMau, 0)),
            Triple("Installed".t(), num(ax?.installedTotal), if (ax == null) null else tf("%s uninstalled (total)", num(ax.uninstalledTotal))),
            Triple("DAU", num(k.dau), "active users".t()),
            Triple("Sessions".t(), num(k.sessions), if (k.dau > 0) num(k.sessions / k.dau, 1) + " / user" else null),
            Triple("Avg session".t(), minutes(k.avgSessionSec), null),
            Triple("Installs".t(), num(k.installs), "first open".t()),
            Triple("Uninstalls".t(), num(k.uninstalls), if (k.installs > 0) tf("%s of installs", pct(k.uninstalls / k.installs, 0)) else null),
            Triple("Impr / DAU".t(), num(k.impsPerDau, 1), null),
            Triple("ARPDAU", inr(k.arpdauUsd * r, 2), null),
        ), colors = mapOf(0 to ex.good))

        SectionTitle("Retention".t(), "coming back after install".t())
        val ex2 = v.extra
        if (ex2 == null) ExtraMissingCard("Retention D1 / D3 / D7 / D14 (paid vs organic)") else Panel { RetentionTable(ex2) }
        Spacer(Modifier.height(14.dp))
        if (ex2 != null && ex2.retentionDays.size > 1) {
            SectionTitle("Retention by day".t(), "cohort = install day".t())
            Panel { RetentionDaysTable(ex2) }
            Spacer(Modifier.height(14.dp))
        }

        SectionTitle(if (d.seriesKind == "day") "Daily".t() else "Hourly".t(), "installs (line), revenue ₹ (bars)".t())
        Panel {
            BarLineChart(d.series.map { it.label }, d.series.map { it.revUsd * r }, line2 = d.series.map { it.installs })
            Legend("Revenue ₹".t() to MaterialTheme.colorScheme.primary, "Installs".t() to ex.good)
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Install source".t(), "Play Install Referrer")
        Panel {
            val tot = d.bySource.sumOf { it.installs }
            val src = if (srcSort == "rev") d.bySource.sortedByDescending { it.revUsd } else d.bySource.sortedByDescending { it.installs }
            HBars(src.take(8).map { Triple(it.src.ifBlank { "(none)" }, num(it.installs) + " · " + inr(it.revUsd * r), shareOf(it.installs, tot)) })
            if (d.bySource.isEmpty()) Empty("No installs yet".t())
            Spacer(Modifier.height(8.dp))
            Table(listOf("Source".t(), "Installs".t(), "Users (ads)".t(), "Revenue".t()), src.take(limit).map { listOf(it.src.ifBlank { "(none)" }, num(it.installs), num(it.users), inr(it.revUsd * r, 1)) }, empty = "")
        }
        Spacer(Modifier.height(14.dp))

        if (d.utm.isNotEmpty()) {
            SectionTitle("utm_campaign", "installs".t())
            Panel { Table(listOf("Campaign".t(), "Installs".t()), d.utm.sortedByDescending { it.n }.take(limit).map { listOf(it.c, num(it.n)) }) }
            Spacer(Modifier.height(14.dp))
        }

        SectionTitle("Countries".t(), tf("%s of %s", num(minOf(limit, d.byCountry.size).toDouble()), num(d.byCountry.size.toDouble())))
        Panel {
            val cc = when (ccSort) {
                "installs" -> d.byCountry.sortedByDescending { it.installs }
                "users" -> d.byCountry.sortedByDescending { it.users }
                "imps" -> d.byCountry.sortedByDescending { it.imps }
                else -> d.byCountry.sortedByDescending { it.rev }
            }
            Table(listOf("Country".t(), "Installs".t(), "Users".t(), "Impr".t(), "Revenue".t(), "eCPM"),
                cc.take(limit).map { listOf(it.cc, num(it.installs), num(it.users), num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) }, empty = "No data".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("App versions".t(), "users".t())
        Panel {
            Table(if (v.allApps) listOf("App".t(), "Version".t(), "Users".t()) else listOf("Version".t(), "Users".t()),
                d.versions.sortedByDescending { it.users }.take(limit).map { if (v.allApps) listOf(d.appName(it.pkg), it.ver, num(it.users)) else listOf(it.ver, num(it.users)) }, empty = "No data".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Live countries".t(), "right now".t())
        Panel {
            val tot = d.liveCountries.sumOf { it.n }
            HBars(d.liveCountries.sortedByDescending { it.n }.take(8).map { Triple(it.cc, num(it.n), shareOf(it.n, tot)) }, color = ex.good)
            if (d.liveCountries.isEmpty()) Empty("Nobody live right now".t())
        }
    }
}
