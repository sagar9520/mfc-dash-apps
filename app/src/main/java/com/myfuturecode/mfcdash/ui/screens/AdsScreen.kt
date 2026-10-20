package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/** Ads = request → fill → impression → eCPM chain, network × format × unit. Filters: mediation (via), format, sort. */
@Composable
fun AdsScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var med by rememberSaveable { mutableStateOf("") }
    var fmt by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("rev") }   // rev | imps | ecpm
    TabScreen("Ads".t(), vm, st, nav, filters = { v ->
        val meds = optionsOf(v.d.byFormat.map { it.med } + v.d.byNetwork.map { it.med })
        val fmts = optionsOf(v.d.byFormat.map { it.fmt } + v.d.byUnit.map { it.fmt })
        if (meds.size > 2) FilterChips("Via".t(), meds, med) { med = it }
        if (fmts.size > 2) FilterChips("Format".t(), fmts, fmt) { fmt = it }
        FilterChips("Sort".t(), listOf("rev" to "Revenue".t(), "imps" to "Impressions".t(), "ecpm" to "eCPM"), sort) { sort = it }
    }) { v ->
        val d = v.d
        val ex = MaterialTheme.extra
        val k = d.kpi; val r = d.r
        val nets = d.byNetwork.filter { med.isBlank() || it.med == med }
        val fmts = d.byFormat.filter { (med.isBlank() || it.med == med) && (fmt.isBlank() || it.fmt == fmt) }
        val units = d.byUnit.filter { (med.isBlank() || it.med == med) && (fmt.isBlank() || it.fmt == fmt) }
        val fails = d.failReasons.filter { (med.isBlank() || it.med == med) && (fmt.isBlank() || it.fmt == fmt) }
        val crs = d.creatives.filter { fmt.isBlank() || it.fmt == fmt }
        val filtered = med.isNotBlank() || fmt.isNotBlank()

        if (!filtered) TileGrid(listOf(
            Triple("Ad revenue".t(), inr(k.revInr), usd(k.revUsd)),
            Triple("Impressions".t(), num(k.imps), num(k.impsPerDau, 1) + " / DAU"),
            Triple("eCPM", usd(k.ecpmUsd), inr(k.ecpmUsd * r)),
            Triple("ARPDAU", inr(k.arpdauUsd * r, 2), usd(k.arpdauUsd, 4)),
            Triple("Fill".t(), pct(d.fill), "loaded ÷ requests".t()),
            Triple("Requests".t(), num(d.byFormat.sumOf { it.adRequest }), num(d.byFormat.sumOf { it.adClicked }) + " " + "clicks".t()),
        )) else {
            // Filtered totals: only the chosen mediation / format
            val rev = fmts.sumOf { it.rev }; val imps = fmts.sumOf { it.imps }; val req = fmts.sumOf { it.adRequest }
            TileGrid(listOf(
                Triple("Ad revenue".t(), inr(rev * r), listOf(med, fmt).filter { it.isNotBlank() }.joinToString(" · ")),
                Triple("Impressions".t(), num(imps), null),
                Triple("eCPM", usd(if (imps > 0) rev / imps * 1000 else null), null),
                Triple("Fill".t(), pct(if (req > 0) fmts.sumOf { it.adRequest * (it.fill ?: 0.0) } / req else null), "loaded ÷ requests".t()),
                Triple("Requests".t(), num(req), num(fmts.sumOf { it.adClicked }) + " " + "clicks".t()),
                Triple("Share".t(), pct(if (k.revUsd > 0) rev / k.revUsd else null), "of all ad revenue".t()),
            ))
        }

        fun <T> List<T>.sortedAds(rev: (T) -> Double, imps: (T) -> Double, ecpm: (T) -> Double?): List<T> = when (sort) {
            "imps" -> sortedByDescending(imps); "ecpm" -> sortedByDescending { ecpm(it) ?: 0.0 }; else -> sortedByDescending(rev)
        }

        SectionTitle("Revenue by network".t(), "impressions · eCPM · load time".t())
        Panel {
            val tot = nets.sumOf { it.rev }
            HBars(nets.sortedByDescending { it.rev }.take(6).map { Triple(it.net + " · " + it.med, inr(it.rev * r, 1) + " · " + pctText(shareOf(it.rev, tot)), shareOf(it.rev, tot)) })
            Spacer(Modifier.height(8.dp))
            Table(listOf("Network".t(), "Via".t(), "Impr".t(), "Revenue".t(), "eCPM", "Load ms".t()),
                nets.sortedAds({ it.rev }, { it.imps }, { it.ecpm }).map { listOf(it.net, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm), if (it.lat == null) "–" else num(it.lat)) }, empty = "No impressions yet".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Revenue by format".t(), "fill = loaded ÷ requests".t())
        Panel {
            Table(listOf("Format".t(), "Via".t(), "Impr".t(), "Revenue".t(), "eCPM", "Requests".t(), "Fill".t(), "Clicks".t(), "CTR"),
                fmts.sortedAds({ it.rev }, { it.imps }, { it.ecpm }).map { listOf(it.fmt, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm), num(it.adRequest), pct(it.fill), num(it.adClicked), pct(it.ctr)) }, empty = "No ad data".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Ad units".t())
        Panel {
            Table(listOf("Ad unit".t(), "Format".t(), "Via".t(), "Impr".t(), "Revenue".t(), "eCPM"),
                units.sortedAds({ it.rev }, { it.imps }, { it.ecpm() }).map { listOf(it.unit, it.fmt, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) }, empty = "No impressions yet".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Ad failure reasons".t())
        Panel {
            Table(listOf("Format".t(), "Via".t(), "Reason".t(), "Count".t()), fails.sortedByDescending { it.n }.map { listOf(it.fmt, it.med, it.reason, num(it.n)) }, empty = "No ad failures".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Cross promo".t(), "mockup ads of your own apps".t())
        val cp = v.cp
        Panel(onClick = { nav.navigate("promo") }) {
            if (cp == null || cp.rows.isEmpty()) Text("No cross promo shown in this period. Tap to open the report.".t(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val vw = cp.rows.sumOf { it.views }; val c = cp.rows.sumOf { it.clicks }; val i = cp.rows.sumOf { it.installs }
                Row { Mini("Views".t(), num(vw), Modifier.weight(1f)); Mini("Clicks".t(), num(c), Modifier.weight(1f)); Mini("CTR", pct(if (vw > 0) c / vw else null), Modifier.weight(1f)); Mini("Installs".t(), num(i), Modifier.weight(1f), ex.good) }
                Text("Full report: which app promotes which, by ad type and design →".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(14.dp))

        if (d.promo.isNotEmpty()) {
            SectionTitle("Promo video".t(), "splash / in-app")
            Panel {
                Table(listOf("App · promo".t(), "Shows".t(), "Clicks".t(), "CTR", "Splash", "In-app"),
                    d.promo.sortedByDescending { it.shows }.map { listOf(d.appName(it.pkg) + " · " + it.title, num(it.shows), num(it.clicks), pct(if (it.shows > 0) it.clicks / it.shows else null), num(it.splash), num(it.tap)) })
            }
            Spacer(Modifier.height(14.dp))
        }

        SectionTitle("In-app ad creatives".t(), "MAX creative ID")
        Panel {
            Table(listOf("Creative".t(), "Network".t(), "DSP", "Format".t(), "Impr".t(), "Revenue".t()),
                crs.sortedByDescending { it.rev }.take(30).map { listOf(it.cr, it.net, it.dsp.ifBlank { "–" }, it.fmt, num(it.imps), inr(it.rev * r, 1)) }, empty = "No creative IDs".t())
            Text("Actual image/video: AppLovin Ad Review and AdMob Ad Review Center.".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Google Ads creatives".t(), "7 days · Google label".t())
        Panel {
            Table(listOf("Creative".t(), "Type".t(), "Label".t(), "Campaign".t(), "Impr".t(), "Clicks".t(), "Conv".t(), "Cost/conv".t(), "Cost".t()),
                d.assets.sortedByDescending { it.cost }.map { listOf(it.name, it.type, it.label.ifBlank { "–" }, it.campaign, num(it.impressions), num(it.clicks), num(it.conversions, 1), inr(it.cpi, 1), inr(it.cost, 1)) }, empty = "No creatives from the Google Ads script".t())
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("AppLovin check".t(), "app vs API".t())
        Panel {
            val m = d.maxCheck
            Table(listOf("Source".t(), "Impr".t(), "Revenue".t(), "Update".t()), listOf(
                listOf("App (live)".t(), num(m.appImps), usd(m.appRev), "live".t()),
                listOf("AppLovin API", num(m.apiImps), usd(m.apiRev), if (m.apiUpdated.isBlank()) "no key / no cron".t() else tFull(m.apiUpdated)),
            ))
        }
    }
}
