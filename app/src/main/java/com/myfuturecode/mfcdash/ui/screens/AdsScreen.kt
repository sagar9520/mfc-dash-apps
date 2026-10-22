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
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.components.HBars
import com.myfuturecode.mfcdash.ui.components.Table
import com.myfuturecode.mfcdash.ui.components.TileGrid
import com.myfuturecode.mfcdash.ui.components.pctText
import com.myfuturecode.mfcdash.ui.components.shareOf
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.usd

/** Ads = request → fill → impression → eCPM, network × format × unit. Hero = ad kamai (filter ke hisaab se). Filters: via, format, sort. */
@Composable
fun AdsScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var med by rememberSaveable { mutableStateOf("") }
    var fmt by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("rev") }   // rev | imps | ecpm
    TabScreen("Ads".t(), vm, st, nav, back = true, filters = { v ->
        val meds = optionsOf(v.d.byFormat.map { it.med } + v.d.byNetwork.map { it.med })
        val fmts = optionsOf(v.d.byFormat.map { it.fmt } + v.d.byUnit.map { it.fmt })
        if (meds.size > 2) FilterChips("Via".t(), meds, med) { med = it }
        if (fmts.size > 2) FilterChips("Format".t(), fmts, fmt) { fmt = it }
        FilterChips("Sort".t(), listOf("rev" to "Revenue".t(), "imps" to "Impressions".t(), "ecpm" to "eCPM"), sort) { sort = it }
    }) { v ->
        val d = v.d
        val c = Mfc.c
        val k = d.kpi
        val r = d.r
        val nets = d.byNetwork.filter { med.isBlank() || it.med == med }
        val fmts = d.byFormat.filter { (med.isBlank() || it.med == med) && (fmt.isBlank() || it.fmt == fmt) }
        val units = d.byUnit.filter { (med.isBlank() || it.med == med) && (fmt.isBlank() || it.fmt == fmt) }
        val fails = d.failReasons.filter { (med.isBlank() || it.med == med) && (fmt.isBlank() || it.fmt == fmt) }
        val crs = d.creatives.filter { fmt.isBlank() || it.fmt == fmt }
        val filtered = med.isNotBlank() || fmt.isNotBlank()

        fun <T> List<T>.sortedAds(rev: (T) -> Double, imps: (T) -> Double, ecpm: (T) -> Double?): List<T> = when (sort) {
            "imps" -> sortedByDescending(imps)
            "ecpm" -> sortedByDescending { ecpm(it) ?: 0.0 }
            else -> sortedByDescending(rev)
        }

        val left: @Composable ColumnScope.() -> Unit = {
            if (!filtered) {
                KHero(KTint.Sand) {
                    KHeroHead("Ad revenue".t(), trailing = { DashText(periodText(d), 13.sp, Mfc.c.ink2) })
                    KBigNum(inr(k.revInr), suffix = "  " + usd(k.revUsd), size = 48.sp)
                    KSplit(listOf("Impressions".t() to num(k.imps), "eCPM" to usd(k.ecpmUsd), "Fill".t() to pct(d.fill)))
                }
                TileGrid(
                    listOf(
                        Triple("ARPDAU", inr(k.arpdauUsd * r, 2), usd(k.arpdauUsd, 4)),
                        Triple("Requests".t(), num(d.byFormat.sumOf { it.adRequest }), num(d.byFormat.sumOf { it.adClicked }) + " " + "clicks".t()),
                        Triple("Impr / DAU".t(), num(k.impsPerDau, 1), "eCPM " + inr(k.ecpmUsd * r)),
                    ),
                )
            } else {
                // sirf chuni hui mediation / format ka jod
                val rev = fmts.sumOf { it.rev }
                val imps = fmts.sumOf { it.imps }
                val req = fmts.sumOf { it.adRequest }
                KHero(KTint.Sand) {
                    KHeroHead("Ad revenue".t(), trailing = { DashText(listOf(med, fmt).filter { it.isNotBlank() }.joinToString(" · "), 13.sp, Mfc.c.ink2) })
                    KBigNum(inr(rev * r), suffix = "  " + usd(rev), size = 48.sp)
                    KSplit(
                        listOf(
                            "Impressions".t() to num(imps),
                            "eCPM" to usd(if (imps > 0) rev / imps * 1000 else null),
                            "Fill".t() to pct(if (req > 0) fmts.sumOf { it.adRequest * (it.fill ?: 0.0) } / req else null),
                        ),
                    )
                }
                TileGrid(
                    listOf(
                        Triple("Requests".t(), num(req), num(fmts.sumOf { it.adClicked }) + " " + "clicks".t()),
                        Triple("Share".t(), pct(if (k.revUsd > 0) rev / k.revUsd else null), "of all ad revenue".t()),
                    ),
                    cols = 2,
                )
            }
            KSection("Revenue by network".t())
            KCard {
                val tot = nets.sumOf { it.rev }
                HBars(nets.sortedByDescending { it.rev }.take(6).map { Triple(it.net + " · " + it.med, inr(it.rev * r, 1) + " · " + pctText(shareOf(it.rev, tot)), shareOf(it.rev, tot)) })
                Spacer(Modifier.height(8.dp))
                Table(
                    listOf("Network".t(), "Via".t(), "Impr".t(), "Revenue".t(), "eCPM", "Load ms".t()),
                    nets.sortedAds({ it.rev }, { it.imps }, { it.ecpm }).map { listOf(it.net, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm), if (it.lat == null) "–" else num(it.lat)) },
                    empty = "No impressions yet".t(),
                )
            }
            KSection("Revenue by format".t())
            KCard {
                Table(
                    listOf("Format".t(), "Via".t(), "Impr".t(), "Revenue".t(), "eCPM", "Requests".t(), "Fill".t(), "Clicks".t(), "CTR"),
                    fmts.sortedAds({ it.rev }, { it.imps }, { it.ecpm }).map { listOf(it.fmt, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm), num(it.adRequest), pct(it.fill), num(it.adClicked), pct(it.ctr)) },
                    empty = "No ad data".t(),
                )
            }
            KSection("Ad units".t())
            KCard {
                Table(
                    listOf("Ad unit".t(), "Format".t(), "Via".t(), "Impr".t(), "Revenue".t(), "eCPM"),
                    units.sortedAds({ it.rev }, { it.imps }, { it.ecpm() }).map { listOf(it.unit, it.fmt, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) },
                    empty = "No impressions yet".t(),
                )
            }
            KSection("Ad failure reasons".t())
            KCard {
                Table(
                    listOf("Format".t(), "Via".t(), "Reason".t(), "Count".t()),
                    fails.sortedByDescending { it.n }.map { listOf(it.fmt, it.med, it.reason, num(it.n)) },
                    empty = "No ad failures".t(),
                )
            }
        }
        val right: @Composable ColumnScope.() -> Unit = {
            // cross promo: chhota saar, tap = poori report
            val cp = v.cp
            KSection("Cross promo".t())
            KCard(list = true) {
                if (cp == null || cp.rows.isEmpty()) {
                    KRow("Cross promo".t(), sub = "No promo shown".t(), lead = { KIconTile(KIcons.Apps) }, chevron = true, onClick = { nav.navigate("promo") })
                } else {
                    val vw = cp.rows.sumOf { it.views }
                    val cl = cp.rows.sumOf { it.clicks }
                    val ins = cp.rows.sumOf { it.installs }
                    KRow(
                        "Cross promo".t(), sub = num(vw) + " " + "views".t() + " · CTR " + pct(if (vw > 0) cl / vw else null),
                        lead = { KIconTile(KIcons.Apps) },
                        value = num(ins), valueColor = if (ins > 0) c.green else c.ink, valueSub = "installs".t(),
                        chevron = true, onClick = { nav.navigate("promo") },
                    )
                }
            }
            if (d.promo.isNotEmpty()) {
                KSection("Promo video".t(), trailing = { DashText("splash / in-app", 13.sp, c.ink3) })
                KCard {
                    Table(
                        listOf("App · promo".t(), "Shows".t(), "Clicks".t(), "CTR", "Splash", "In-app"),
                        d.promo.sortedByDescending { it.shows }.map { listOf(d.appName(it.pkg) + " · " + it.title, num(it.shows), num(it.clicks), pct(if (it.shows > 0) it.clicks / it.shows else null), num(it.splash), num(it.tap)) },
                    )
                }
            }
            KSection("In-app ad creatives".t(), trailing = { DashText("MAX", 13.sp, c.ink3) })
            KCard {
                Table(
                    listOf("Creative".t(), "Network".t(), "DSP", "Format".t(), "Impr".t(), "Revenue".t()),
                    crs.sortedByDescending { it.rev }.take(30).map { listOf(it.cr, it.net, it.dsp.ifBlank { "–" }, it.fmt, num(it.imps), inr(it.rev * r, 1)) },
                    empty = "No creative IDs".t(),
                )
            }
            KSection("Google Ads creatives".t(), trailing = { DashText("7 days".t(), 13.sp, c.ink3) })
            KCard {
                Table(
                    listOf("Creative".t(), "Type".t(), "Label".t(), "Campaign".t(), "Impr".t(), "Clicks".t(), "Conv".t(), "Cost/conv".t(), "Cost".t()),
                    d.assets.sortedByDescending { it.cost }.map { listOf(it.name, it.type, it.label.ifBlank { "–" }, it.campaign, num(it.impressions), num(it.clicks), num(it.conversions, 1), inr(it.cpi, 1), inr(it.cost, 1)) },
                    empty = "No creatives".t(),
                )
            }
            KSection("AppLovin check".t(), trailing = { DashText("app vs API".t(), 13.sp, c.ink3) })
            KCard {
                val m = d.maxCheck
                Table(
                    listOf("Source".t(), "Impr".t(), "Revenue".t(), "Update".t()),
                    listOf(
                        listOf("App (live)".t(), num(m.appImps), usd(m.appRev), "live".t()),
                        listOf("AppLovin API", num(m.apiImps), usd(m.apiRev), if (m.apiUpdated.isBlank()) "no key / no cron".t() else tFull(m.apiUpdated)),
                    ),
                )
            }
        }
        if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
    }
}
