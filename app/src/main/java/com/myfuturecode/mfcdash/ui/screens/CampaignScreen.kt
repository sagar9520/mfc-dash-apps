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

/** One campaign: cost, conv, cost/conv, creatives, and a suggestion (cheap/costly vs average). */
@Composable
fun CampaignScreen(vm: DashVm, st: UiState, nav: NavHostController, idx: Int) {
    val d = st.data
    val c = d?.campaigns?.getOrNull(idx)
    val ex = MaterialTheme.extra
    Column(Modifier.fillMaxSize()) {
        TopBar(c?.campaign ?: "Campaign".t(), vm, st, nav, back = true)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (d == null || c == null) { Text("Campaign not found (period changed?)".t(), modifier = Modifier.padding(top = 40.dp)); return@Column }
            val k = d.kpi
            val avgCpi = if (k.gadsInstalls > 0) k.gadsCostInr / k.gadsInstalls else null
            val cpi = c.cpi ?: (if (c.conversions > 0) c.cost / c.conversions else null)
            GradientHeader(c.campaign.uppercase(), d.appName(c.pkg) + " · " + c.account + " · " + c.status.lowercase() + " · " + periodText(d)) {
                Spacer(Modifier.height(6.dp))
                Row { Column(Modifier.weight(1f)) { Text("SPEND".t(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f)); Text(inr(c.cost), style = MaterialTheme.typography.headlineLarge, color = Color.White) }
                    Column { Text("COST / CONV".t(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f)); Text(inr(cpi, 2), style = MaterialTheme.typography.headlineMedium, color = Color.White) } }
            }
            Spacer(Modifier.height(12.dp))
            TileGrid(listOf(
                Triple("Conversions".t(), num(c.conversions, 1), "installs (Google)".t()), Triple("Impressions".t(), num(c.impressions), null), Triple("Clicks".t(), num(c.clicks), null),
                Triple("CTR", pct(c.ctr), null), Triple("CPC", inr(if (c.clicks > 0) c.cost / c.clicks else null, 2), null), Triple("Conv rate".t(), pct(if (c.clicks > 0) c.conversions / c.clicks else null), "conv ÷ clicks".t()),
            ))

            // Suggestion: UA practice = cost/conv 20% under average → raise budget; 30% over → check creatives/bid.
            SectionTitle("Suggestion".t(), "avg cost/conv".t() + " " + inr(avgCpi, 2))
            Panel {
                val txt = when {
                    cpi == null && c.cost > 100 -> "Spending but 0 conversions. Check creatives and the Play listing first, otherwise pause.".t() to ex.bad
                    cpi == null || avgCpi == null -> "Not enough data yet for a suggestion.".t() to ex.muted
                    cpi <= avgCpi * 0.8 -> tf("This campaign is %s%% cheaper than average. Worth raising budget 15–20%%, then watch ROAS for 3 days.", num((1 - cpi / avgCpi) * 100, 0)) to ex.good
                    cpi >= avgCpi * 1.3 -> tf("%s%% costlier than average. Remove low-label creatives or lower bid/target.", num((cpi / avgCpi - 1) * 100, 0)) to ex.warn
                    else -> "Around average. Let it run, keep rotating creatives.".t() to ex.muted
                }
                Text(txt.first, style = MaterialTheme.typography.bodyMedium, color = txt.second, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(14.dp))

            // ---- ROAS cohort (api/app.php)
            val ex2 = st.extra
            val cr = ex2?.campaignRoas?.firstOrNull { it.campaign.equals(c.campaign, true) && (it.pkg == c.pkg || c.pkg.isBlank()) }
            SectionTitle("ROAS cohort".t(), "from install day".t())
            when {
                ex2 == null -> ExtraMissingCard("ROAS D0 / D1 / D3 / D7 vs target")
                cr == null -> Panel { Text(tf("No utm_campaign with this name in the Play referrer. Put utm_campaign=%s in the ad's final URL (same as the Google Ads campaign name) and cohort ROAS will show here.", c.campaign), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> Panel {
                    RoasCurve(cr, st.roasTarget)
                    Spacer(Modifier.height(8.dp))
                    Row { Mini("Installs (utm)".t(), num(cr.installs), Modifier.weight(1f)); Mini("CPI", inr(cr.cpi, 2), Modifier.weight(1f)); Mini("Ad rev".t(), inr(cr.revTotal), Modifier.weight(1f)); Mini("Payments".t(), inr(cr.payTotal), Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- Daily spend / conv
            val daily = ex2?.campaignDaily?.filter { it.campaign.equals(c.campaign, true) }?.sortedBy { it.day } ?: emptyList()
            if (daily.isNotEmpty()) {
                SectionTitle("Daily".t(), "spend ₹ (bars), conversions (line)".t())
                Panel {
                    BarLineChart(daily.map { fmtDay(it.day).take(2) }, daily.map { it.cost }, line2 = daily.map { it.conversions }, barColor = ex.warn)
                    Legend("Spend ₹".t() to ex.warn, "Conversions".t() to ex.good)
                    Spacer(Modifier.height(8.dp))
                    Table(listOf("Date".t(), "Cost".t(), "Impr".t(), "Clicks".t(), "Conv".t(), "Cost/conv".t()), daily.sortedByDescending { it.day }.map { listOf(fmtDay(it.day), inr(it.cost), num(it.impressions), num(it.clicks), num(it.conversions, 1), inr(if (it.conversions > 0) it.cost / it.conversions else null, 1)) })
                }
                Spacer(Modifier.height(14.dp))
            }

            SectionTitle("Creatives".t(), "this campaign · 7 days".t())
            Panel {
                val a = d.assets.filter { it.campaign == c.campaign }.sortedByDescending { it.cost }
                Table(listOf("Creative".t(), "Type".t(), "Label".t(), "Impr".t(), "Clicks".t(), "CTR", "Conv".t(), "Cost/conv".t(), "Cost".t()),
                    a.map { listOf(it.name, it.type, it.label.ifBlank { "–" }, num(it.impressions), num(it.clicks), pct(if (it.impressions > 0) it.clicks / it.impressions else null), num(it.conversions, 1), inr(it.cpi, 1), inr(it.cost, 1)) },
                    empty = "No creatives for this campaign from the script".t())
                Text("One creative can run in several ads, so creative cost will not match campaign cost.".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(14.dp))

            SectionTitle("Installs from this campaign".t(), "utm_campaign")
            Panel {
                val u = d.utm.filter { it.c.equals(c.campaign, true) || it.c.contains(c.campaign, true) }
                if (u.isEmpty()) Text(tf("No utm_campaign with this name in the Play referrer. Put utm_campaign=%s in the ad's final URL to see installs here and (after app.php) the ROAS cohort.", c.campaign), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Table(listOf("utm_campaign", "Installs".t()), u.map { listOf(it.c, num(it.n)) })
            }
            Spacer(Modifier.height(14.dp))
            SectionTitle("Change log".t(), "what changed, when".t())
            if (ex2 == null) ExtraMissingCard("Change log / notes".t()) else NotesSection(vm, ex2.notes.filter { it.campaign.equals(c.campaign, true) }, c.campaign, c.pkg)
        }
    }
}
