package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*

/** Money = profit, ROAS, spend, per-install economics, campaigns, payments (UA/ROAS template). Filters: campaign status / account / sort, payments status. */
@Composable
fun MoneyScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var status by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("cost") }
    var pay by remember { mutableStateOf("") }
    TabScreen("Money".t(), vm, st, nav, filters = { v ->
        val d = v.d
        FilterChips("Campaigns".t(), listOf("" to "All".t(), "ENABLED" to "Enabled".t(), "PAUSED" to "Paused".t(), "spend" to "With spend".t(), "noconv" to "0 conv".t()), status) { status = it }
        if (d.gadsAccounts.size > 1) FilterChips("Account".t(), listOf("" to "All".t()) + d.gadsAccounts.map { it.name to it.name }, account) { account = it }
        FilterChips("Sort".t(), listOf("cost" to "Cost".t(), "cpi" to "Cost/conv".t(), "conv" to "Conv".t(), "ctr" to "CTR"), sort) { sort = it }
    }) { v ->
        val d = v.d
        val ex = MaterialTheme.extra
        val k = d.kpi; val r = d.r
        val earned = k.revInr + k.purchaseInr
        val roas = k.roasTotal ?: (if (k.gadsCostInr > 0) earned / k.gadsCostInr else null)

        TileGrid(listOf(
            Triple("Profit".t(), inr(k.profitInr), periodText(d)),
            Triple("ROAS", roasPct(roas), "target".t() + " " + num(st.roasTarget * 100, 0) + "%"),
            Triple("Earned".t(), inr(earned), "ads".t() + " " + inr(k.revInr) + " + " + "pay".t() + " " + inr(k.purchaseInr)),
            Triple("Spent".t(), inr(k.gadsCostInr), num(k.gadsClicks) + " clicks"),
        ), cols = 2, colors = mapOf(0 to if (k.profitInr >= 0) ex.good else ex.bad, 1 to when { roas == null -> ex.muted; roas >= st.roasTarget -> ex.good; roas >= 1 -> ex.warn; else -> ex.bad }))

        // ---- 14 days: revenue vs spend
        val hist = d.history.sortedBy { it.day }.takeLast(14)
        SectionTitle("Daily: earned vs spent".t(), tf("last %s days", hist.size))
        Panel {
            BarLineChart(hist.map { fmtDay(it.day).take(2) }, hist.map { it.revUsd * r + it.purchaseInr }, hist.map { it.gadsCostInr })
            Legend("Earned ₹".t() to MaterialTheme.colorScheme.primary, "Ad spend ₹".t() to ex.warn)
            Spacer(Modifier.height(8.dp))
            val p14 = hist.sumOf { it.profit(r) }; val s14 = hist.sumOf { it.gadsCostInr }; val k14 = hist.sumOf { it.revUsd * r + it.purchaseInr }
            Row { Mini("Profit".t(), inr(p14), Modifier.weight(1f), if (p14 >= 0) ex.good else ex.bad); Mini("ROAS", roasPct(if (s14 > 0) k14 / s14 else null), Modifier.weight(1f)); Mini("Earned".t(), inr(k14), Modifier.weight(1f)); Mini("Spent".t(), inr(s14), Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Daily ROAS heat strip
        if (hist.isNotEmpty()) {
            SectionTitle("Daily ROAS".t(), "green = on target".t())
            Panel {
                hist.chunked(7).forEach { week ->
                    Row { week.forEach { h -> HeatCell(h.roas(r), st.roasTarget, roasPct(h.roas(r)), Modifier.weight(1f)) }; repeat(7 - week.size) { Spacer(Modifier.weight(1f)) } }
                    Row { week.forEach { h -> Text(fmtDay(h.day).take(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }; repeat(7 - week.size) { Spacer(Modifier.weight(1f)) } }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---- ROAS cohort (campaign-wise, api/app.php)
        SectionTitle("ROAS cohort: by campaign".t(), "D0 / D1 / D3 / D7 vs " + "target".t())
        val ex2 = v.extra
        if (ex2 == null) ExtraMissingCard("Campaign-wise ROAS cohort".t())
        else Panel {
            RoasCohortTable(ex2.campaignRoas, d) { c -> d.campaigns.indexOfFirst { it.campaign.equals(c.campaign, true) }.takeIf { it >= 0 }?.let { nav.navigate("campaign/$it") } }
            if (ex2.organicInstalls > 0) Text("Organic (no utm) installs".t() + ": " + num(ex2.organicInstalls), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(14.dp))

        // ---- Per-install economics
        SectionTitle("Per-install economics".t(), periodText(d))
        Panel {
            val cpi = if (k.gadsInstalls > 0) k.gadsCostInr / k.gadsInstalls else null
            val revPer = if (k.installs > 0) earned / k.installs else null
            val profPer = if (k.installs > 0) k.profitInr / k.installs else null
            val arpdauInr = k.arpdauUsd * r
            val payback = if (cpi != null && arpdauInr > 0) cpi / arpdauInr else null
            Row { Mini("Cost / conv".t(), inr(cpi, 2), Modifier.weight(1f)); Mini("Earned / install".t(), inr(revPer, 2), Modifier.weight(1f)); Mini("Profit / install".t(), inr(profPer, 2), Modifier.weight(1f), if ((profPer ?: 0.0) >= 0) ex.good else ex.bad) }
            Spacer(Modifier.height(8.dp))
            Row { Mini("ARPDAU", inr(arpdauInr, 2), Modifier.weight(1f)); Mini("Payback".t(), if (payback == null) "–" else "~" + num(payback, 1) + " " + "days".t(), Modifier.weight(1f)); Mini("Ads users rev".t(), inr(k.revFromGadsUsersInr), Modifier.weight(1f)) }
            Spacer(Modifier.height(8.dp))
            Text("Payback = cost/conv ÷ ARPDAU: days until a paid user earns back what they cost.".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))

        // ---- Campaigns (filtered)
        val cs = d.campaigns.filter { c ->
            (account.isBlank() || c.account == account) && when (status) {
                "ENABLED" -> c.status.equals("ENABLED", true)
                "PAUSED" -> !c.status.equals("ENABLED", true)
                "spend" -> c.cost > 0
                "noconv" -> c.cost > 0 && c.conversions == 0.0
                else -> true
            }
        }.let { list ->
            when (sort) {
                "cpi" -> list.sortedBy { it.cpi ?: Double.MAX_VALUE }
                "conv" -> list.sortedByDescending { it.conversions }
                "ctr" -> list.sortedByDescending { it.ctr ?: 0.0 }
                else -> list.sortedByDescending { it.cost }
            }
        }
        SectionTitle("Google Ads campaigns".t(), (if (cs.size != d.campaigns.size) tf("%s of %s", cs.size, d.campaigns.size) + " · " else "") + (if (k.gadsUpdated.isNotBlank()) "update".t() + " " + tOnly(k.gadsUpdated) else "no script data".t()))
        Panel {
            Table(listOf("Campaign".t(), "Cost".t(), "Conv", "Cost/conv".t(), "Clicks", "CTR", "Status".t()),
                cs.map { listOf(it.campaign + "\n" + d.appName(it.pkg), inr(it.cost), num(it.conversions, 1), if (it.cpi == null) "–" else inr(it.cpi, 1), num(it.clicks), pct(it.ctr), it.status.lowercase()) },
                empty = "No campaigns match".t(), onRow = { i -> nav.navigate("campaign/${d.campaigns.indexOf(cs[i])}") })
        }
        Spacer(Modifier.height(14.dp))

        // ---- Accounts
        if (d.gadsAccounts.isNotEmpty()) {
            SectionTitle("Google Ads accounts".t())
            d.gadsAccounts.filter { account.isBlank() || it.name == account }.forEach { a ->
                Panel {
                    Row { Text(a.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(inr(a.cost), style = MaterialTheme.typography.titleMedium) }
                    Text("ID " + a.idFmt + (if (a.currency.isNotBlank() && a.currency != "INR") " · ${a.currency} → ₹" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row { Mini("Impr", num(a.impressions), Modifier.weight(1f)); Mini("Clicks", num(a.clicks), Modifier.weight(1f)); Mini("CTR", pct(a.ctr), Modifier.weight(1f)); Mini("Conv", num(a.conversions, 1), Modifier.weight(1f)); Mini("Cost/conv".t(), inr(a.cpi, 1), Modifier.weight(1f)) }
                    Text(num(a.campaigns) + " " + "campaigns".t() + " · " + a.pkgs.joinToString { d.appName(it) } + " · " + "updated".t() + " " + tOnly(a.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---- Payments
        SectionTitle("Payments".t(), num(k.purchases) + " " + "paid".t() + " · " + num(k.purchaseFailed) + " " + "failed".t())
        FilterChips("Status".t(), listOf("" to "All".t(), "purchase_done" to "Paid".t(), "purchase_failed" to "Failed".t(), "purchase_start" to "Started".t()), pay) { pay = it }
        Panel {
            Table(listOf("App · plan".t(), "Status".t(), "Count".t(), "Amount".t()),
                d.purchases.filter { pay.isBlank() || it.ev == pay }.sortedByDescending { it.amt ?: 0.0 }.map { listOf(d.appName(it.pkg) + " · " + it.plan, it.statusL, num(it.n), if (it.ev == "purchase_failed") "–" else inr(it.amt)) }, empty = "No payments".t())
        }
        Spacer(Modifier.height(14.dp))

        // ---- History table
        SectionTitle("Daily summary".t(), "from cron".t())
        Panel {
            Table(listOf("Date".t(), "Installs".t(), "DAU", "Revenue".t(), "Pay".t(), "Spend".t(), "Profit".t(), "Uninst".t()),
                d.history.sortedByDescending { it.day }.map { listOf(fmtDay(it.day), num(it.installs), num(it.dau), inr(it.revUsd * r), inr(it.purchaseInr), inr(it.gadsCostInr), inr(it.profit(r)), num(it.uninstalls)) },
                empty = "Daily summary appears here once the cron has run".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Change log".t(), "budget / bid / creative changes".t())
        if (ex2 == null) ExtraMissingCard("Change log".t()) else NotesSection(vm, ex2.notes, "", "")
    }
}
