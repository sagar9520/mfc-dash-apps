package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.Campaign
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.BarLineChart
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.components.Legend
import com.myfuturecode.mfcdash.ui.components.StatCells
import com.myfuturecode.mfcdash.ui.components.Table
import com.myfuturecode.mfcdash.ui.components.TileGrid
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDivider
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.icon
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf

/** Ek campaign: kharcha (hero), cost/conv, salah (average se sasta / mehenga), ROAS cohort, roz ka hisaab, creatives, utm installs, change log. */
@Composable
fun CampaignScreen(vm: DashVm, st: UiState, nav: NavHostController, idx: Int) {
    val d = st.data
    val c = d?.campaigns?.getOrNull(idx)
    val wide = isWide()
    KScreen(top = {
        DashBar(
            c?.campaign ?: "Campaign".t(), vm, st, nav, back = true,
            sub = if (d == null || c == null) null else listOf(d.appName(c.pkg), c.account).filter { it.isNotBlank() }.joinToString(" · "),
        )
        if (d != null && c != null && !wide) CampHero(d, c)
    }) {
        if (d == null) {
            DashNoData(st, st.loading) { vm.refresh() }
            return@KScreen
        }
        if (c == null) {
            KCard { KEmpty("Campaign not found".t(), KIcons.Ads) }
            return@KScreen
        }
        val k = d.kpi
        val avgCpi = if (k.gadsInstalls > 0) k.gadsCostInr / k.gadsInstalls else null
        val cpi = campCpi(c)
        val ex2 = st.extra
        val left: @Composable ColumnScope.() -> Unit = {
            if (wide) CampHero(d, c)
            TileGrid(
                listOf(
                    Triple("Impressions".t(), num(c.impressions), null), Triple("Clicks".t(), num(c.clicks), null), Triple("CTR", pct(c.ctr), null),
                    Triple("CPC", inr(if (c.clicks > 0) c.cost / c.clicks else null, 2), null),
                    Triple("Conv rate".t(), pct(if (c.clicks > 0) c.conversions / c.clicks else null), null),
                    Triple("Avg cost/conv".t(), inr(avgCpi, 2), "all campaigns".t()),
                ),
            )
            // salah: UA practice = average se 20% sasta → budget badhao; 30% mehenga → creatives / bid dekho
            KSection("Suggestion".t())
            val (verdict, detail, kind) = when {
                cpi == null && c.cost > 100 -> Triple("Pause or fix".t(), tf("0 conv · %s spent", inr(c.cost)), KKind.BAD)
                cpi == null || avgCpi == null -> Triple("Not enough data".t(), null, KKind.NEUTRAL)
                cpi <= avgCpi * 0.8 -> Triple("Raise budget 15–20%".t(), tf("%s%% cheaper than avg", num((1 - cpi / avgCpi) * 100, 0)), KKind.OK)
                cpi >= avgCpi * 1.3 -> Triple("Fix creatives or bid".t(), tf("%s%% costlier than avg", num((cpi / avgCpi - 1) * 100, 0)), KKind.WARN)
                else -> Triple("Keep running".t(), "Around average".t(), KKind.NEUTRAL)
            }
            KCard(list = true) {
                KRow(
                    verdict, sub = detail, subKind = kind, titleLines = 2,
                    lead = { KIconTile(kind.icon() ?: KIcons.Bulb, kind) },
                )
            }
            // ---- ROAS cohort (api/app.php)
            val cr = ex2?.campaignRoas?.firstOrNull { it.campaign.equals(c.campaign, true) && (it.pkg == c.pkg || c.pkg.isBlank()) }
            KSection("ROAS cohort".t())
            when {
                ex2 == null -> ExtraMissingCard("ROAS D0 / D1 / D3 / D7")
                cr == null -> CampNoUtm(c)
                else -> KCard {
                    RoasCurve(cr, st.roasTarget)
                    Spacer(Modifier.height(4.dp))
                    KDivider()
                    Spacer(Modifier.height(12.dp))
                    StatCells(
                        listOf(
                            Triple("Installs (utm)".t(), num(cr.installs), null), Triple("CPI", inr(cr.cpi, 2), null),
                            Triple("Ad rev".t(), inr(cr.revTotal), null), Triple("Payments".t(), inr(cr.payTotal), null),
                        ),
                        cols = 2,
                    )
                }
            }
        }
        val right: @Composable ColumnScope.() -> Unit = {
            val cc = Mfc.c
            // ---- roz ka kharcha / conv
            val daily = ex2?.campaignDaily?.filter { it.campaign.equals(c.campaign, true) }?.sortedBy { it.day } ?: emptyList()
            if (daily.isNotEmpty()) {
                KSection("Daily".t())
                KCard {
                    BarLineChart(daily.map { fmtDay(it.day).take(2) }, daily.map { it.cost }, line2 = daily.map { it.conversions }, barColor = cc.zAmber)
                    Legend("Spend ₹".t() to cc.zAmber, "Conversions".t() to cc.zGreen)
                    Spacer(Modifier.height(8.dp))
                    Table(
                        listOf("Date".t(), "Cost".t(), "Impr".t(), "Clicks".t(), "Conv".t(), "Cost/conv".t()),
                        daily.sortedByDescending { it.day }.map { listOf(fmtDay(it.day), inr(it.cost), num(it.impressions), num(it.clicks), num(it.conversions, 1), inr(if (it.conversions > 0) it.cost / it.conversions else null, 1)) },
                    )
                }
            }
            KSection("Creatives".t(), trailing = { DashText("7 days".t(), 13.sp, cc.ink3) })
            KCard {
                val a = d.assets.filter { it.campaign == c.campaign }.sortedByDescending { it.cost }
                Table(
                    listOf("Creative".t(), "Type".t(), "Label".t(), "Impr".t(), "Clicks".t(), "CTR", "Conv".t(), "Cost/conv".t(), "Cost".t()),
                    a.map { listOf(it.name, it.type, it.label.ifBlank { "–" }, num(it.impressions), num(it.clicks), pct(if (it.impressions > 0) it.clicks / it.impressions else null), num(it.conversions, 1), inr(it.cpi, 1), inr(it.cost, 1)) },
                    empty = "No creatives".t(),
                )
            }
            KSection("Installs from this campaign".t())
            val u = d.utm.filter { it.c.equals(c.campaign, true) || it.c.contains(c.campaign, true) }
            if (u.isEmpty()) CampNoUtm(c)
            else KCard { Table(listOf("utm_campaign", "Installs".t()), u.map { listOf(it.c, num(it.n)) }) }
            KSection("Change log".t())
            if (ex2 == null) ExtraMissingCard("Change log".t()) else NotesSection(vm, ex2.notes.filter { it.campaign.equals(c.campaign, true) }, c.campaign, c.pkg)
        }
        if (wide) KTwoPane(left = left, right = right) else { left(); right() }
    }
}

/** Cost per conversion: Google ka cpi, warna cost / conv. */
private fun campCpi(c: Campaign): Double? = c.cpi ?: (if (c.conversions > 0) c.cost / c.conversions else null)

/** Hero: kharcha (bada), Running / Paused pill, cost/conv aur conv. */
@Composable
private fun CampHero(d: DashData, c: Campaign) {
    val enabled = c.status.equals("ENABLED", true)
    KHero(KTint.Sand) {
        KHeroHead(tf("Spend · %s", periodText(d)), trailing = {
            KPill(if (enabled) "Running".t() else "Paused".t(), if (enabled) KKind.OK else KKind.NEUTRAL)
        })
        KBigNum(inr(c.cost), size = 48.sp)
        KSplit(listOf("Cost / conv".t() to inr(campCpi(c), 2), "Conversions".t() to num(c.conversions, 1)))
    }
}

/** utm_campaign naam match nahi hua: ek row + wo naam jo ad ke final URL me chahiye. */
@Composable
private fun CampNoUtm(c: Campaign) {
    KCard(list = true) {
        KRow(
            "No utm match".t(), sub = "utm_campaign=" + c.campaign, subKind = KKind.WARN, titleLines = 1,
            lead = { KIconTile(KIcons.Info, KKind.WARN) },
        )
    }
}
