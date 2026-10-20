package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*

/** Paisa = profit, ROAS, spend, per-install hisaab, campaigns, payments (UA/ROAS template). */
@Composable
fun MoneyScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    TabScreen("Paisa", vm, st, nav) { d ->
        val ex = MaterialTheme.extra
        val k = d.kpi; val r = d.r
        val kamaya = k.revInr + k.purchaseInr
        val roas = k.roasTotal ?: (if (k.gadsCostInr > 0) kamaya / k.gadsCostInr else null)

        TileGrid(listOf(
            Triple("Profit", inr(k.profitInr), periodText(d)),
            Triple("ROAS", roasPct(roas), "target " + num(st.roasTarget * 100, 0) + "%"),
            Triple("Kamaya", inr(kamaya), "ads " + inr(k.revInr) + " + pay " + inr(k.purchaseInr)),
            Triple("Kharcha", inr(k.gadsCostInr), num(k.gadsClicks) + " clicks"),
        ), cols = 2, colors = mapOf(0 to if (k.profitInr >= 0) ex.good else ex.bad, 1 to when { roas == null -> ex.muted; roas >= st.roasTarget -> ex.good; roas >= 1 -> ex.warn; else -> ex.bad }))

        // ---- 14 din: revenue vs spend
        val hist = d.history.sortedBy { it.day }.takeLast(14)
        SectionTitle("Daily: kamaya vs kharcha", "last ${hist.size} din")
        Panel {
            BarLineChart(hist.map { fmtDay(it.day).take(2) }, hist.map { it.revUsd * r + it.purchaseInr }, hist.map { it.gadsCostInr })
            Legend("Kamaya ₹" to MaterialTheme.colorScheme.primary, "Ad spend ₹" to ex.warn)
            Spacer(Modifier.height(8.dp))
            val p14 = hist.sumOf { it.profit(r) }; val s14 = hist.sumOf { it.gadsCostInr }; val k14 = hist.sumOf { it.revUsd * r + it.purchaseInr }
            Row { Mini("Profit", inr(p14), Modifier.weight(1f), if (p14 >= 0) ex.good else ex.bad); Mini("ROAS", roasPct(if (s14 > 0) k14 / s14 else null), Modifier.weight(1f)); Mini("Kamaya", inr(k14), Modifier.weight(1f)); Mini("Kharcha", inr(s14), Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Daily ROAS heat strip (history se): har din ka ROAS target ke mukable
        if (hist.isNotEmpty()) {
            SectionTitle("Din-wise ROAS", "hara = target par")
            Panel {
                hist.chunked(7).forEach { week ->
                    Row { week.forEach { h -> HeatCell(h.roas(r), st.roasTarget, roasPct(h.roas(r)), Modifier.weight(1f)) }; repeat(7 - week.size) { Spacer(Modifier.weight(1f)) } }
                    Row { week.forEach { h -> Text(fmtDay(h.day).take(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }; repeat(7 - week.size) { Spacer(Modifier.weight(1f)) } }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---- ROAS cohort (campaign-wise, api/app.php)
        SectionTitle("ROAS cohort: campaign-wise", "D0 / D1 / D3 / D7 vs target")
        val ex2 = st.extra
        if (ex2 == null) ExtraMissingCard("Campaign-wise ROAS cohort")
        else Panel {
            RoasCohortTable(ex2.campaignRoas, d) { c -> d.campaigns.indexOfFirst { it.campaign.equals(c.campaign, true) }.takeIf { it >= 0 }?.let { nav.navigate("campaign/$it") } }
            if (ex2.organicInstalls > 0) Text("Organic (bina utm) installs: " + num(ex2.organicInstalls), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(14.dp))

        // ---- Per-install hisaab
        SectionTitle("Ek install ka hisaab", periodText(d))
        Panel {
            val cpi = if (k.gadsInstalls > 0) k.gadsCostInr / k.gadsInstalls else null
            val revPer = if (k.installs > 0) kamaya / k.installs else null
            val profPer = if (k.installs > 0) k.profitInr / k.installs else null
            val arpdauInr = k.arpdauUsd * r
            val payback = if (cpi != null && arpdauInr > 0) cpi / arpdauInr else null
            Row { Mini("Cost / conv", inr(cpi, 2), Modifier.weight(1f)); Mini("Kamaya / install", inr(revPer, 2), Modifier.weight(1f)); Mini("Profit / install", inr(profPer, 2), Modifier.weight(1f), if ((profPer ?: 0.0) >= 0) ex.good else ex.bad) }
            Spacer(Modifier.height(8.dp))
            Row { Mini("ARPDAU", inr(arpdauInr, 2), Modifier.weight(1f)); Mini("Payback", if (payback == null) "–" else "~" + num(payback, 1) + " din", Modifier.weight(1f)); Mini("Ads users rev", inr(k.revFromGadsUsersInr), Modifier.weight(1f)) }
            Spacer(Modifier.height(8.dp))
            Text("Payback = cost/conv ÷ ARPDAU: itne din mein ek paid user apna kharcha nikaal deta hai.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))

        // ---- Campaigns
        SectionTitle("Google Ads campaigns", if (k.gadsUpdated.isNotBlank()) "update " + tOnly(k.gadsUpdated) else "script se data nahi")
        Panel {
            val cs = d.campaigns.sortedByDescending { it.cost }
            Table(listOf("Campaign", "Cost", "Conv", "Cost/conv", "Clicks", "CTR", "Status"),
                cs.map { listOf(it.campaign + "\n" + d.appName(it.pkg), inr(it.cost), num(it.conversions, 1), if (it.cpi == null) "–" else inr(it.cpi, 1), num(it.clicks), pct(it.ctr), it.status.lowercase()) },
                empty = "Is period mein campaign nahi", onRow = { i -> nav.navigate("campaign/${d.campaigns.indexOf(cs[i])}") })
        }
        Spacer(Modifier.height(14.dp))

        // ---- Accounts
        if (d.gadsAccounts.isNotEmpty()) {
            SectionTitle("Google Ads accounts")
            d.gadsAccounts.forEach { a ->
                Panel {
                    Row { Text(a.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(inr(a.cost), style = MaterialTheme.typography.titleMedium) }
                    Text("ID " + a.idFmt + (if (a.currency.isNotBlank() && a.currency != "INR") " · ${a.currency} → ₹" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row { Mini("Impr", num(a.impressions), Modifier.weight(1f)); Mini("Clicks", num(a.clicks), Modifier.weight(1f)); Mini("CTR", pct(a.ctr), Modifier.weight(1f)); Mini("Conv", num(a.conversions, 1), Modifier.weight(1f)); Mini("Cost/conv", inr(a.cpi, 1), Modifier.weight(1f)) }
                    Text(num(a.campaigns) + " campaigns · " + a.pkgs.joinToString { d.appName(it) } + " · updated " + tOnly(a.updatedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---- Payments
        SectionTitle("Payments", num(k.purchases) + " paid · " + num(k.purchaseFailed) + " failed")
        Panel {
            Table(listOf("App · plan", "Status", "Count", "Amount"),
                d.purchases.sortedByDescending { it.amt ?: 0.0 }.map { listOf(d.appName(it.pkg) + " · " + it.plan, it.status, num(it.n), if (it.ev == "purchase_failed") "–" else inr(it.amt)) }, empty = "Koi payment nahi")
        }
        Spacer(Modifier.height(14.dp))

        // ---- History table
        SectionTitle("Daily summary", "cron se")
        Panel {
            Table(listOf("Date", "Installs", "DAU", "Revenue", "Pay", "Spend", "Profit", "Uninst"),
                d.history.sortedByDescending { it.day }.map { listOf(fmtDay(it.day), num(it.installs), num(it.dau), inr(it.revUsd * r), inr(it.purchaseInr), inr(it.gadsCostInr), inr(it.profit(r)), num(it.uninstalls)) },
                empty = "Cron chalne ke baad yahan daily summary aayegi")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Change log", "budget / bid / creative badlav")
        if (ex2 == null) ExtraMissingCard("Change log") else NotesSection(vm, ex2.notes, "", "")
    }
}
