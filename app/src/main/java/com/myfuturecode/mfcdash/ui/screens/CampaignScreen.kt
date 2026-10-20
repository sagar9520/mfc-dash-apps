package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.TopBar
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*

/** Ek campaign: cost, conv, cost/conv, creatives, aur suggestion (sasti/mehngi avg ke mukable). */
@Composable
fun CampaignScreen(vm: DashVm, st: UiState, nav: NavHostController, idx: Int) {
    val d = st.data
    val c = d?.campaigns?.getOrNull(idx)
    val ex = MaterialTheme.extra
    Column(Modifier.fillMaxSize()) {
        TopBar(c?.campaign ?: "Campaign", vm, st, nav, back = true)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (d == null || c == null) { Text("Campaign nahi mili (period badla?)", modifier = Modifier.padding(top = 40.dp)); return@Column }
            val k = d.kpi
            val avgCpi = if (k.gadsInstalls > 0) k.gadsCostInr / k.gadsInstalls else null
            val cpi = c.cpi ?: (if (c.conversions > 0) c.cost / c.conversions else null)
            GradientHeader(c.campaign.uppercase(), d.appName(c.pkg) + " · " + c.account + " · " + c.status.lowercase() + " · " + periodText(d)) {
                Spacer(Modifier.height(6.dp))
                Row { Column(Modifier.weight(1f)) { Text("SPEND", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f)); Text(inr(c.cost), style = MaterialTheme.typography.headlineLarge, color = Color.White) }
                    Column { Text("COST / CONV", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f)); Text(inr(cpi, 2), style = MaterialTheme.typography.headlineMedium, color = Color.White) } }
            }
            Spacer(Modifier.height(12.dp))
            TileGrid(listOf(
                Triple("Conversions", num(c.conversions, 1), "installs (Google)"), Triple("Impressions", num(c.impressions), null), Triple("Clicks", num(c.clicks), null),
                Triple("CTR", pct(c.ctr), null), Triple("CPC", inr(if (c.clicks > 0) c.cost / c.clicks else null, 2), null), Triple("Conv rate", pct(if (c.clicks > 0) c.conversions / c.clicks else null), "conv ÷ clicks"),
            ))

            // Suggestion: UA practice = cost/conv avg se 20% kam ho to budget badhao, 30% zyada ho to creatives/bid dekho.
            SectionTitle("Suggestion", "avg cost/conv " + inr(avgCpi, 2))
            Panel {
                val txt = when {
                    cpi == null && c.cost > 100 -> "Spend ho raha hai par 0 conversions. Pehle creatives aur Play listing check karo, warna pause karo." to ex.bad
                    cpi == null || avgCpi == null -> "Abhi itna data nahi ki raay di jaaye." to ex.muted
                    cpi <= avgCpi * 0.8 -> "Yeh campaign average se ${num((1 - cpi / avgCpi) * 100, 0)}% sasti hai. Budget 15–20% badhane layak, phir 3 din ROAS dekho." to ex.good
                    cpi >= avgCpi * 1.3 -> "Average se ${num((cpi / avgCpi - 1) * 100, 0)}% mehngi. Low-label creatives hatao ya bid/target kam karo." to ex.warn
                    else -> "Average ke aas-paas. Jaise chal rahi hai chalne do, creatives rotate karte raho." to ex.muted
                }
                Text(txt.first, style = MaterialTheme.typography.bodyMedium, color = txt.second, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(14.dp))

            // ---- ROAS cohort (api/app.php)
            val ex2 = st.extra
            val cr = ex2?.campaignRoas?.firstOrNull { it.campaign.equals(c.campaign, true) && (it.pkg == c.pkg || c.pkg.isBlank()) }
            SectionTitle("ROAS cohort", "install ke din se")
            when {
                ex2 == null -> ExtraMissingCard("ROAS D0 / D1 / D3 / D7 vs target")
                cr == null -> Panel { Text("Is naam ka utm_campaign Play referrer mein nahi mila. Ads ke final URL mein utm_campaign=${c.campaign} lagao (Google Ads ke campaign naam jaisa), tab cohort ROAS yahan aayega.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> Panel {
                    RoasCurve(cr, st.roasTarget)
                    Spacer(Modifier.height(8.dp))
                    Row { Mini("Installs (utm)", num(cr.installs), Modifier.weight(1f)); Mini("CPI", inr(cr.cpi, 2), Modifier.weight(1f)); Mini("Ad rev", inr(cr.revTotal), Modifier.weight(1f)); Mini("Payments", inr(cr.payTotal), Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- Daily spend / conv
            val daily = ex2?.campaignDaily?.filter { it.campaign.equals(c.campaign, true) }?.sortedBy { it.day } ?: emptyList()
            if (daily.isNotEmpty()) {
                SectionTitle("Roz ka", "spend ₹ (bars), conversions (line)")
                Panel {
                    BarLineChart(daily.map { fmtDay(it.day).take(2) }, daily.map { it.cost }, line2 = daily.map { it.conversions }, barColor = ex.warn)
                    Legend("Spend ₹" to ex.warn, "Conversions" to ex.good)
                    Spacer(Modifier.height(8.dp))
                    Table(listOf("Date", "Cost", "Impr", "Clicks", "Conv", "Cost/conv"), daily.sortedByDescending { it.day }.map { listOf(fmtDay(it.day), inr(it.cost), num(it.impressions), num(it.clicks), num(it.conversions, 1), inr(if (it.conversions > 0) it.cost / it.conversions else null, 1)) })
                }
                Spacer(Modifier.height(14.dp))
            }

            SectionTitle("Creatives", "is campaign ke · 7 din")
            Panel {
                val a = d.assets.filter { it.campaign == c.campaign }.sortedByDescending { it.cost }
                Table(listOf("Creative", "Type", "Label", "Impr", "Clicks", "CTR", "Conv", "Cost/conv", "Cost"),
                    a.map { listOf(it.name, it.type, it.label.ifBlank { "–" }, num(it.impressions), num(it.clicks), pct(if (it.impressions > 0) it.clicks / it.impressions else null), num(it.conversions, 1), inr(it.cpi, 1), inr(it.cost, 1)) },
                    empty = "Script se is campaign ke creatives nahi aaye")
                Text("Ek creative kai ads mein chal sakta hai, isliye creative ka cost campaign ke cost se match nahi karega.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(14.dp))

            SectionTitle("Is campaign se installs", "utm_campaign")
            Panel {
                val u = d.utm.filter { it.c.equals(c.campaign, true) || it.c.contains(c.campaign, true) }
                if (u.isEmpty()) Text("Play referrer mein is naam ka utm_campaign nahi mila. Ads ke final URL mein utm_campaign=${c.campaign} lagao to yahan installs aur (app.php ke baad) ROAS cohort dikhega.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Table(listOf("utm_campaign", "Installs"), u.map { listOf(it.c, num(it.n)) })
            }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Change log", "kya badla, kab")
            if (ex2 == null) ExtraMissingCard("Change log / notes") else NotesSection(vm, ex2.notes.filter { it.campaign.equals(c.campaign, true) }, c.campaign, c.pkg)
        }
    }
}
