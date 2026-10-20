package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.cpFmtName
import com.myfuturecode.mfcdash.data.healthOf
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.TopBar
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.ui.theme.parseHex
import com.myfuturecode.mfcdash.util.*

/** Ek app ka poora data: data.php?app=<pkg> (site par app chip chunne jaisa). */
@Composable
fun AppDetailScreen(vm: DashVm, st: UiState, nav: NavHostController, pkg: String) {
    LaunchedEffect(pkg, st.range, st.from, st.to, st.lastOk) { vm.loadApp(pkg) }
    val all = st.data
    val d = st.appData[pkg]
    val name = all?.appName(pkg) ?: pkg
    val ex = MaterialTheme.extra
    Column(Modifier.fillMaxSize()) {
        TopBar(name, vm, st, nav, back = true)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            RangeBar(vm, st); Spacer(Modifier.height(12.dp))
            if (d == null) { Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { if (st.appLoading == pkg || st.loading) CircularProgressIndicator() else Text(st.error ?: "Data nahi aaya") }; return@Column }
            val k = d.kpi; val r = d.r
            val summary = all?.perApp?.firstOrNull { it.pkg == pkg }
            val kamaya = k.revInr + k.purchaseInr
            val roas = k.roasTotal ?: (if (k.gadsCostInr > 0) kamaya / k.gadsCostInr else null)

            GradientHeader(name.uppercase() + " · " + periodText(d).uppercase(), pkg) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(inr(k.profitInr), style = MaterialTheme.typography.headlineLarge, color = Color.White, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) { HealthBadge(healthOf(roas, k.profitInr)); Text("ROAS " + roasPct(roas), style = MaterialTheme.typography.titleMedium, color = Color.White) }
                }
                RoasBar(roas, st.roasTarget)
                Row(Modifier.fillMaxWidth()) {
                    listOf("Ad revenue" to inr(k.revInr), "Payments" to inr(k.purchaseInr), "Ad spend" to inr(k.gadsCostInr), "Live" to num(d.liveTotal)).forEach { (l, v) ->
                        Column(Modifier.weight(1f)) { Text(l.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f)); Text(v, style = MaterialTheme.typography.titleMedium, color = Color.White) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            val ex2 = st.appExtra[pkg]
            val ax = ex2?.active
            SectionTitle("Users")
            TileGrid(listOf(
                Triple("${ex2?.liveMinutes ?: 6} min", num(d.liveTotal), "abhi"), Triple("30 min", num(d.live30), "active"), Triple("1 ghanta", num(ax?.live1h), if (ax == null) "app.php" else "active"),
                Triple("DAU", num(k.dau), num(k.sessions) + " sessions"), Triple("WAU", num(ax?.wau), "7 din"), Triple("MAU", num(ax?.mau), if (ax?.dauMau == null) "30 din" else "DAU/MAU " + pct(ax.dauMau, 0)),
                Triple("Avg session", minutes(k.avgSessionSec), null), Triple("Installs", num(k.installs), "first open"), Triple("Uninstalls", num(k.uninstalls), "FCM"),
            ), colors = mapOf(0 to ex.good))
            if (ex2 != null) {
                SectionTitle("Retention", "D1 / D3 / D7 / D14")
                Panel { RetentionTable(ex2) }
                Spacer(Modifier.height(14.dp))
                SectionTitle("ROAS cohort", "is app ki campaigns")
                Panel { RoasCohortTable(ex2.campaignRoas, all) { c -> all?.campaigns?.indexOfFirst { it.campaign.equals(c.campaign, true) }?.takeIf { it >= 0 }?.let { nav.navigate("campaign/$it") } } }
                Spacer(Modifier.height(14.dp))
            }

            SectionTitle("Paisa")
            val cpi = if (k.gadsInstalls > 0) k.gadsCostInr / k.gadsInstalls else null
            TileGrid(listOf(
                Triple("Cost / conv", inr(cpi, 2), num(k.gadsInstalls, 1) + " conv"), Triple("Profit / install", inr(if (k.installs > 0) k.profitInr / k.installs else null, 2), null),
                Triple("Ads users rev", inr(k.revFromGadsUsersInr), null), Triple("eCPM", usd(k.ecpmUsd), inr(k.ecpmUsd * r)),
                Triple("ARPDAU", inr(k.arpdauUsd * r, 2), null), Triple("Impr / DAU", num(k.impsPerDau, 1), num(k.imps) + " impr"),
                Triple("Fill", pct(d.fill), null), Triple("Crashes", num(k.crashes), null), Triple("Payments", num(k.purchases), num(k.purchaseFailed) + " failed"),
            ), colors = mapOf(7 to if (k.crashes > 0) ex.bad else ex.good))

            SectionTitle(if (d.seriesKind == "day") "Daily" else "Hourly", "revenue ₹, spend ₹ (dash), installs")
            Panel {
                BarLineChart(d.series.map { it.label }, d.series.map { it.revUsd * r }, d.series.map { it.gadsCostInr }, d.series.map { it.installs })
                Legend("Revenue ₹" to MaterialTheme.colorScheme.primary, "Ad spend ₹" to ex.warn, "Installs" to ex.good)
            }
            Spacer(Modifier.height(14.dp))

            val camps = d.campaigns.filter { it.pkg == pkg || it.pkg.isBlank() }
            SectionTitle("Google Ads campaigns", num(camps.sumOf { it.cost }).let { "spend ₹$it" })
            Panel {
                Table(listOf("Campaign", "Cost", "Conv", "Cost/conv", "CTR", "Status"),
                    camps.sortedByDescending { it.cost }.map { listOf(it.campaign, inr(it.cost), num(it.conversions, 1), inr(it.cpi, 1), pct(it.ctr), it.status.lowercase()) },
                    empty = "Is app par campaign nahi", onRow = { i -> all?.campaigns?.indexOf(camps.sortedByDescending { it.cost }[i])?.takeIf { it >= 0 }?.let { nav.navigate("campaign/$it") } })
            }
            Spacer(Modifier.height(14.dp))

            SectionTitle("Ads: format")
            Panel { Table(listOf("Format", "Via", "Impr", "Revenue", "eCPM", "Requests", "Fill", "CTR"), d.byFormat.sortedByDescending { it.rev }.map { listOf(it.fmt, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm), num(it.adRequest), pct(it.fill), pct(it.ctr)) }, empty = "Ad data nahi") }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Ads: network")
            Panel { Table(listOf("Network", "Via", "Impr", "Revenue", "eCPM"), d.byNetwork.sortedByDescending { it.rev }.map { listOf(it.net, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm)) }, empty = "Impressions nahi") }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Ad units")
            Panel { Table(listOf("Ad unit", "Format", "Impr", "Revenue", "eCPM"), d.byUnit.sortedByDescending { it.rev }.map { listOf(it.unit, it.fmt, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) }, empty = "Impressions nahi") }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Ad failure reasons")
            Panel { Table(listOf("Format", "Via", "Reason", "Count"), d.failReasons.sortedByDescending { it.n }.map { listOf(it.fmt, it.med, it.reason, num(it.n)) }, empty = "Koi failure nahi") }
            Spacer(Modifier.height(14.dp))

            val cp = st.appCp[pkg]
            SectionTitle("Cross promo", "is app mein dikhe / is app ke installs")
            Panel {
                val shown = cp?.rows?.filter { it.src == pkg } ?: emptyList()
                Text("Is app mein dikhe promos", style = MaterialTheme.typography.titleSmall)
                Table(listOf("Promoted app", "Type", "Views", "Clicks", "CTR", "Installs"), shown.map { listOf(all?.appName(it.tgt) ?: it.tgt, cpFmtName(it.fmt), num(it.views), num(it.clicks), pct(it.ctr), num(it.installs)) }, empty = "Is period mein promo nahi dikha")
                Spacer(Modifier.height(10.dp))
                val into = cp?.rows?.filter { it.tgt == pkg } ?: emptyList()
                Text("Is app ke installs kis promo se aaye", style = MaterialTheme.typography.titleSmall)
                Table(listOf("Shown in", "Type", "Views", "Clicks", "Installs", "Inst/click"), into.map { listOf(all?.appName(it.src) ?: it.src, cpFmtName(it.fmt), num(it.views), num(it.clicks), num(it.installs), pct(it.installPerClick)) }, empty = "Dusre apps ke promo se install nahi")
            }
            Spacer(Modifier.height(14.dp))

            SectionTitle("Install source")
            Panel { Table(listOf("Source", "Installs", "Users", "Revenue"), d.bySource.sortedByDescending { it.installs }.map { listOf(it.src.ifBlank { "(none)" }, num(it.installs), num(it.users), inr(it.revUsd * r, 1)) }, empty = "Installs nahi") }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Countries")
            Panel { Table(listOf("CC", "Installs", "Users", "Impr", "Revenue", "eCPM"), d.byCountry.sortedByDescending { it.rev }.map { listOf(it.cc, num(it.installs), num(it.users), num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) }, empty = "Data nahi") }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Crashes", num(k.crashes))
            Panel { Table(listOf("Version", "Error", "Where", "Count"), d.crashes.sortedByDescending { it.n }.map { listOf(it.ver, it.error, it.where, num(it.n)) }, empty = "Koi crash nahi 🎉") }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Versions")
            Panel { Table(listOf("Version", "Users"), d.versions.sortedByDescending { it.users }.map { listOf(it.ver, num(it.users)) }, empty = "Data nahi") }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Payments")
            Panel { Table(listOf("Plan", "Status", "Count", "Amount"), d.purchases.map { listOf(it.plan, it.status, num(it.n), if (it.ev == "purchase_failed") "–" else inr(it.amt)) }, empty = "Koi payment nahi") }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Daily history")
            Panel { Table(listOf("Date", "Installs", "DAU", "Revenue", "Spend", "Profit"), d.history.sortedByDescending { it.day }.map { listOf(fmtDay(it.day), num(it.installs), num(it.dau), inr(it.revUsd * r), inr(it.gadsCostInr), inr(it.profit(r))) }, empty = "Cron ke baad") }
            if (summary != null) Spacer(Modifier.height(4.dp))
        }
    }
}
