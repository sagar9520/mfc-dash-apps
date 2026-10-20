package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*

@Composable
fun UsersScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    TabScreen("Users", vm, st, nav) { d ->
        val ex = MaterialTheme.extra
        val k = d.kpi; val r = d.r
        val ax = st.extra?.active
        val lm = st.extra?.liveMinutes ?: 6
        TileGrid(listOf(
            Triple("$lm min", num(d.liveTotal), "abhi active"),
            Triple("30 min", num(d.live30), "active"),
            Triple("1 ghanta", num(ax?.live1h), if (ax == null) "app.php chahiye" else "active"),
            Triple("24 ghante", num(ax?.live24h), if (ax == null) "app.php chahiye" else "active"),
            Triple("WAU", num(ax?.wau), "7 din"),
            Triple("MAU", num(ax?.mau), if (ax?.dauMau == null) "30 din" else "DAU/MAU " + pct(ax.dauMau, 0)),
            Triple("Installed", num(ax?.installedTotal), if (ax == null) null else num(ax.uninstalledTotal) + " uninstalled (total)"),
            Triple("DAU", num(k.dau), "active users"),
            Triple("Sessions", num(k.sessions), if (k.dau > 0) num(k.sessions / k.dau, 1) + " / user" else null),
            Triple("Avg session", minutes(k.avgSessionSec), null),
            Triple("Installs", num(k.installs), "first open"),
            Triple("Uninstalls", num(k.uninstalls), if (k.installs > 0) pct(k.uninstalls / k.installs, 0) + " of installs" else null),
            Triple("Impr / DAU", num(k.impsPerDau, 1), null),
            Triple("ARPDAU", inr(k.arpdauUsd * r, 2), null),
        ), colors = mapOf(0 to ex.good))

        SectionTitle("Retention", "install ke baad wapas aana")
        val ex2 = st.extra
        if (ex2 == null) ExtraMissingCard("Retention D1 / D3 / D7 / D14 (paid vs organic)") else Panel { RetentionTable(ex2) }
        Spacer(Modifier.height(14.dp))
        if (ex2 != null && ex2.retentionDays.size > 1) {
            SectionTitle("Retention din-wise", "cohort = install ka din")
            Panel { RetentionDaysTable(ex2) }
            Spacer(Modifier.height(14.dp))
        }

        SectionTitle(if (d.seriesKind == "day") "Daily" else "Hourly", "installs (line), revenue ₹ (bars)")
        Panel {
            BarLineChart(d.series.map { it.label }, d.series.map { it.revUsd * r }, line2 = d.series.map { it.installs })
            Legend("Revenue ₹" to MaterialTheme.colorScheme.primary, "Installs" to ex.good)
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Install source", "Play Install Referrer")
        Panel {
            val tot = d.bySource.sumOf { it.installs }
            HBars(d.bySource.sortedByDescending { it.installs }.take(8).map { Triple(it.src.ifBlank { "(none)" }, num(it.installs) + " · " + inr(it.revUsd * r), shareOf(it.installs, tot)) })
            if (d.bySource.isEmpty()) Empty("Abhi installs nahi")
            Spacer(Modifier.height(8.dp))
            Table(listOf("Source", "Installs", "Users (ads)", "Revenue"), d.bySource.sortedByDescending { it.installs }.map { listOf(it.src.ifBlank { "(none)" }, num(it.installs), num(it.users), inr(it.revUsd * r, 1)) }, empty = "")
        }
        Spacer(Modifier.height(14.dp))

        if (d.utm.isNotEmpty()) {
            SectionTitle("utm_campaign", "installs")
            Panel { Table(listOf("Campaign", "Installs"), d.utm.sortedByDescending { it.n }.map { listOf(it.c, num(it.n)) }) }
            Spacer(Modifier.height(14.dp))
        }

        SectionTitle("Countries")
        Panel {
            Table(listOf("Country", "Installs", "Users", "Impr", "Revenue", "eCPM"),
                d.byCountry.sortedByDescending { it.rev }.map { listOf(it.cc, num(it.installs), num(it.users), num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) }, empty = "Data nahi")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("App versions", "users")
        Panel {
            Table(listOf("App", "Version", "Users"), d.versions.sortedByDescending { it.users }.map { listOf(d.appName(it.pkg), it.ver, num(it.users)) }, empty = "Data nahi")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Live countries", "abhi")
        Panel {
            val tot = d.liveCountries.sumOf { it.n }
            HBars(d.liveCountries.sortedByDescending { it.n }.take(8).map { Triple(it.cc, num(it.n), shareOf(it.n, tot)) }, color = ex.good)
            if (d.liveCountries.isEmpty()) Empty("Abhi koi live nahi")
        }
    }
}
