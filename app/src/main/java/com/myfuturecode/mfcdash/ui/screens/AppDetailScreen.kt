package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.myfuturecode.mfcdash.util.*

/** One app in full: data.php?app=<pkg> (same as picking the app chip on the site). Filters: section, mediation. */
@Composable
fun AppDetailScreen(vm: DashVm, st: UiState, nav: NavHostController, pkg: String) {
    LaunchedEffect(pkg, st.range, st.from, st.to, st.lastOk) { vm.loadApp(pkg) }
    val all = st.data
    val d = st.appData[pkg]
    val name = all?.appName(pkg) ?: pkg
    val ex = MaterialTheme.extra
    var section by rememberSaveable { mutableStateOf("") }   // "" | users | money | ads | promo | more
    var med by rememberSaveable { mutableStateOf("") }
    val box = remember { FilterBox() }
    Column(Modifier.fillMaxSize()) {
        TopBar(name, vm, st, nav, back = true)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            RangeBar(vm, st) { FilterToggle(box) }; Spacer(Modifier.height(8.dp))
            if (d == null) { Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { if (st.appLoading == pkg || st.loading) CircularProgressIndicator() else Text(st.error ?: "No data received".t()) }; return@Column }
            val meds = optionsOf(d.byFormat.map { it.med } + d.byNetwork.map { it.med })
            CompositionLocalProvider(LocalFilterBox provides box) {
                FilterChips("Show".t(), listOf("" to "All".t(), "users" to "Users".t(), "money" to "Money".t(), "ads" to "Ads".t(), "promo" to "Cross promo".t(), "more" to "More".t()), section) { section = it }
                if ((section == "" || section == "ads") && meds.size > 2) FilterChips("Via".t(), meds, med) { med = it }
            }
            FilterSummary(box)
            fun show(s: String) = section.isBlank() || section == s

            val k = d.kpi; val r = d.r
            val summary = all?.perApp?.firstOrNull { it.pkg == pkg }
            val earned = k.revInr + k.purchaseInr
            val roas = k.roasTotal ?: (if (k.gadsCostInr > 0) earned / k.gadsCostInr else null)

            GradientHeader(name.uppercase() + " · " + periodText(d).uppercase(), pkg) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(inr(k.profitInr), style = MaterialTheme.typography.headlineLarge, color = Color.White, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) { HealthBadge(healthOf(roas, k.profitInr)); Text("ROAS " + roasPct(roas), style = MaterialTheme.typography.titleMedium, color = Color.White) }
                }
                RoasBar(roas, st.roasTarget)
                Row(Modifier.fillMaxWidth()) {
                    listOf("Ad revenue".t() to inr(k.revInr), "Payments".t() to inr(k.purchaseInr), "Ad spend".t() to inr(k.gadsCostInr), "Live".t() to num(d.liveTotal)).forEach { (l, v) ->
                        Column(Modifier.weight(1f)) { Text(l.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f)); Text(v, style = MaterialTheme.typography.titleMedium, color = Color.White) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            val ex2 = st.appExtra[pkg]
            val ax = ex2?.active
            if (show("users")) {
                SectionTitle("Users".t())
                TileGrid(listOf(
                    Triple("${ex2?.liveMinutes ?: 6} min", num(d.liveTotal), "now".t()), Triple("30 min", num(d.live30), "active".t()), Triple("1 hour".t(), num(ax?.live1h), if (ax == null) "app.php" else "active".t()),
                    Triple("DAU", num(k.dau), num(k.sessions) + " " + "sessions".t()), Triple("WAU", num(ax?.wau), "7 days".t()), Triple("MAU", num(ax?.mau), if (ax?.dauMau == null) "30 days".t() else "DAU/MAU " + pct(ax.dauMau, 0)),
                    Triple("Avg session".t(), minutes(k.avgSessionSec), null), Triple("Installs".t(), num(k.installs), "first open".t()), Triple("Uninstalls".t(), num(k.uninstalls), "FCM"),
                ), colors = mapOf(0 to ex.good))
                if (ex2 != null) {
                    SectionTitle("Retention".t(), "D1 / D3 / D7 / D14")
                    Panel { RetentionTable(ex2) }
                    Spacer(Modifier.height(14.dp))
                }
            }
            if (show("money")) {
                if (ex2 != null) {
                    SectionTitle("ROAS cohort".t(), "this app's campaigns".t())
                    Panel { RoasCohortTable(ex2.campaignRoas, all) { c -> all?.campaigns?.indexOfFirst { it.campaign.equals(c.campaign, true) }?.takeIf { it >= 0 }?.let { nav.navigate("campaign/$it") } } }
                    Spacer(Modifier.height(14.dp))
                }
                SectionTitle("Money".t())
                val cpi = if (k.gadsInstalls > 0) k.gadsCostInr / k.gadsInstalls else null
                TileGrid(listOf(
                    Triple("Cost / conv".t(), inr(cpi, 2), num(k.gadsInstalls, 1) + " " + "conv".t()), Triple("Profit / install".t(), inr(if (k.installs > 0) k.profitInr / k.installs else null, 2), null),
                    Triple("Ads users rev".t(), inr(k.revFromGadsUsersInr), null), Triple("eCPM", usd(k.ecpmUsd), inr(k.ecpmUsd * r)),
                    Triple("ARPDAU", inr(k.arpdauUsd * r, 2), null), Triple("Impr / DAU".t(), num(k.impsPerDau, 1), num(k.imps) + " " + "impr".t()),
                    Triple("Fill".t(), pct(d.fill), null), Triple("Crashes".t(), num(k.crashes), null), Triple("Payments".t(), num(k.purchases), num(k.purchaseFailed) + " " + "failed".t()),
                ), colors = mapOf(7 to if (k.crashes > 0) ex.bad else ex.good))

                SectionTitle(if (d.seriesKind == "day") "Daily".t() else "Hourly".t(), "revenue ₹, spend ₹ (dash), installs".t())
                Panel {
                    BarLineChart(d.series.map { it.label }, d.series.map { it.revUsd * r }, d.series.map { it.gadsCostInr }, d.series.map { it.installs })
                    Legend("Revenue ₹".t() to MaterialTheme.colorScheme.primary, "Ad spend ₹".t() to ex.warn, "Installs".t() to ex.good)
                }
                Spacer(Modifier.height(14.dp))

                val camps = d.campaigns.filter { it.pkg == pkg || it.pkg.isBlank() }
                SectionTitle("Google Ads campaigns".t(), "spend".t() + " " + inr(camps.sumOf { it.cost }))
                Panel {
                    Table(listOf("Campaign".t(), "Cost".t(), "Conv".t(), "Cost/conv".t(), "CTR", "Status".t()),
                        camps.sortedByDescending { it.cost }.map { listOf(it.campaign, inr(it.cost), num(it.conversions, 1), inr(it.cpi, 1), pct(it.ctr), it.status.lowercase()) },
                        empty = "No campaign on this app".t(), onRow = { i -> all?.campaigns?.indexOf(camps.sortedByDescending { it.cost }[i])?.takeIf { it >= 0 }?.let { nav.navigate("campaign/$it") } })
                }
                Spacer(Modifier.height(14.dp))
                SectionTitle("Payments".t())
                Panel { Table(listOf("Plan".t(), "Status".t(), "Count".t(), "Amount".t()), d.purchases.map { listOf(it.plan, it.statusL, num(it.n), if (it.ev == "purchase_failed") "–" else inr(it.amt)) }, empty = "No payments".t()) }
                Spacer(Modifier.height(14.dp))
            }

            if (show("ads")) {
                val fmts = d.byFormat.filter { med.isBlank() || it.med == med }
                val nets = d.byNetwork.filter { med.isBlank() || it.med == med }
                val units = d.byUnit.filter { med.isBlank() || it.med == med }
                val fails = d.failReasons.filter { med.isBlank() || it.med == med }
                SectionTitle("Ads: format".t())
                Panel { Table(listOf("Format".t(), "Via".t(), "Impr".t(), "Revenue".t(), "eCPM", "Requests".t(), "Fill".t(), "CTR"), fmts.sortedByDescending { it.rev }.map { listOf(it.fmt, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm), num(it.adRequest), pct(it.fill), pct(it.ctr)) }, empty = "No ad data".t()) }
                Spacer(Modifier.height(14.dp))
                SectionTitle("Ads: network".t())
                Panel { Table(listOf("Network".t(), "Via".t(), "Impr".t(), "Revenue".t(), "eCPM"), nets.sortedByDescending { it.rev }.map { listOf(it.net, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm)) }, empty = "No impressions".t()) }
                Spacer(Modifier.height(14.dp))
                SectionTitle("Ad units".t())
                Panel { Table(listOf("Ad unit".t(), "Format".t(), "Impr".t(), "Revenue".t(), "eCPM"), units.sortedByDescending { it.rev }.map { listOf(it.unit, it.fmt, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) }, empty = "No impressions".t()) }
                Spacer(Modifier.height(14.dp))
                SectionTitle("Ad failure reasons".t())
                Panel { Table(listOf("Format".t(), "Via".t(), "Reason".t(), "Count".t()), fails.sortedByDescending { it.n }.map { listOf(it.fmt, it.med, it.reason, num(it.n)) }, empty = "No failures".t()) }
                Spacer(Modifier.height(14.dp))
            }

            if (show("promo")) {
                val cp = st.appCp[pkg]
                SectionTitle("Cross promo".t(), "shown in this app / installs of this app".t())
                Panel {
                    val shown = cp?.rows?.filter { it.src == pkg } ?: emptyList()
                    Text("Promos shown in this app".t(), style = MaterialTheme.typography.titleSmall)
                    Table(listOf("Promoted app".t(), "Type".t(), "Views".t(), "Clicks".t(), "CTR", "Installs".t()), shown.map { listOf(all?.appName(it.tgt) ?: it.tgt, cpFmtName(it.fmt), num(it.views), num(it.clicks), pct(it.ctr), num(it.installs)) }, empty = "No promo shown in this period".t())
                    Spacer(Modifier.height(10.dp))
                    val into = cp?.rows?.filter { it.tgt == pkg } ?: emptyList()
                    Text("Where this app's installs came from".t(), style = MaterialTheme.typography.titleSmall)
                    Table(listOf("Shown in".t(), "Type".t(), "Views".t(), "Clicks".t(), "Installs".t(), "Inst/click".t()), into.map { listOf(all?.appName(it.src) ?: it.src, cpFmtName(it.fmt), num(it.views), num(it.clicks), num(it.installs), pct(it.installPerClick)) }, empty = "No installs from other apps' promos".t())
                }
                Spacer(Modifier.height(14.dp))
            }

            if (show("more")) {
                SectionTitle("Install source".t())
                Panel { Table(listOf("Source".t(), "Installs".t(), "Users".t(), "Revenue".t()), d.bySource.sortedByDescending { it.installs }.map { listOf(it.src.ifBlank { "(none)" }, num(it.installs), num(it.users), inr(it.revUsd * r, 1)) }, empty = "No installs".t()) }
                Spacer(Modifier.height(14.dp))
                SectionTitle("Countries".t())
                Panel { Table(listOf("CC", "Installs".t(), "Users".t(), "Impr".t(), "Revenue".t(), "eCPM"), d.byCountry.sortedByDescending { it.rev }.map { listOf(it.cc, num(it.installs), num(it.users), num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) }, empty = "No data".t()) }
                Spacer(Modifier.height(14.dp))
                SectionTitle("Crashes".t(), num(k.crashes))
                Panel { Table(listOf("Version".t(), "Error".t(), "Where".t(), "Count".t()), d.crashes.sortedByDescending { it.n }.map { listOf(it.ver, it.error, it.where, num(it.n)) }, empty = "No crashes 🎉".t()) }
                Spacer(Modifier.height(14.dp))
                SectionTitle("Versions".t())
                Panel { Table(listOf("Version".t(), "Users".t()), d.versions.sortedByDescending { it.users }.map { listOf(it.ver, num(it.users)) }, empty = "No data".t()) }
                Spacer(Modifier.height(14.dp))
                SectionTitle("Daily history".t())
                Panel { Table(listOf("Date".t(), "Installs".t(), "DAU", "Revenue".t(), "Spend".t(), "Profit".t()), d.history.sortedByDescending { it.day }.map { listOf(fmtDay(it.day), num(it.installs), num(it.dau), inr(it.revUsd * r), inr(it.gadsCostInr), inr(it.profit(r))) }, empty = "After the cron runs".t()) }
            }
            if (summary != null) Spacer(Modifier.height(4.dp))
        }
    }
    FilterSheet(box)
}
