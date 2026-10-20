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

/** Ads = request → fill → impression → eCPM ki chain, network × format × unit (AppLovin MAX reporting pattern). */
@Composable
fun AdsScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    TabScreen("Ads", vm, st, nav) { d ->
        val ex = MaterialTheme.extra
        val k = d.kpi; val r = d.r
        TileGrid(listOf(
            Triple("Ad revenue", inr(k.revInr), usd(k.revUsd)),
            Triple("Impressions", num(k.imps), num(k.impsPerDau, 1) + " / DAU"),
            Triple("eCPM", usd(k.ecpmUsd), inr(k.ecpmUsd * r)),
            Triple("ARPDAU", inr(k.arpdauUsd * r, 2), usd(k.arpdauUsd, 4)),
            Triple("Fill", pct(d.fill), "loaded ÷ requests"),
            Triple("Requests", num(d.byFormat.sumOf { it.adRequest }), num(d.byFormat.sumOf { it.adClicked }) + " clicks"),
        ))

        SectionTitle("Revenue by network", "impressions · eCPM · load time")
        Panel {
            val tot = d.byNetwork.sumOf { it.rev }
            HBars(d.byNetwork.sortedByDescending { it.rev }.take(6).map { Triple(it.net + " · " + it.med, inr(it.rev * r, 1) + " · " + pctText(shareOf(it.rev, tot)), shareOf(it.rev, tot)) })
            Spacer(Modifier.height(8.dp))
            Table(listOf("Network", "Via", "Impr", "Revenue", "eCPM", "Load ms"),
                d.byNetwork.sortedByDescending { it.rev }.map { listOf(it.net, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm), if (it.lat == null) "–" else num(it.lat)) }, empty = "Abhi impressions nahi")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Revenue by format", "fill = loaded ÷ requests")
        Panel {
            Table(listOf("Format", "Via", "Impr", "Revenue", "eCPM", "Requests", "Fill", "Clicks", "CTR"),
                d.byFormat.sortedByDescending { it.rev }.map { listOf(it.fmt, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm), num(it.adRequest), pct(it.fill), num(it.adClicked), pct(it.ctr)) }, empty = "Ad data nahi")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Ad units")
        Panel {
            Table(listOf("Ad unit", "Format", "Via", "Impr", "Revenue", "eCPM"),
                d.byUnit.sortedByDescending { it.rev }.map { listOf(it.unit, it.fmt, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) }, empty = "Abhi impressions nahi")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Ad failure reasons")
        Panel {
            Table(listOf("Format", "Via", "Reason", "Count"), d.failReasons.sortedByDescending { it.n }.map { listOf(it.fmt, it.med, it.reason, num(it.n)) }, empty = "Koi ad failure nahi")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Cross promo", "apne apps ke mockup ads")
        val cp = st.cp
        Panel(onClick = { nav.navigate("promo") }) {
            if (cp == null || cp.rows.isEmpty()) Text("Is period mein cross promo nahi dikha. Tap karke report kholo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val v = cp.rows.sumOf { it.views }; val c = cp.rows.sumOf { it.clicks }; val i = cp.rows.sumOf { it.installs }
                Row { Mini("Views", num(v), Modifier.weight(1f)); Mini("Clicks", num(c), Modifier.weight(1f)); Mini("CTR", pct(if (v > 0) c / v else null), Modifier.weight(1f)); Mini("Installs", num(i), Modifier.weight(1f), ex.good) }
                Text("Poori report: kis app mein kis app ka promo, ad type aur design-wise →", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(14.dp))

        if (d.promo.isNotEmpty()) {
            SectionTitle("Promo video", "splash / in-app")
            Panel {
                Table(listOf("App · promo", "Shows", "Clicks", "CTR", "Splash", "In-app"),
                    d.promo.sortedByDescending { it.shows }.map { listOf(d.appName(it.pkg) + " · " + it.title, num(it.shows), num(it.clicks), pct(if (it.shows > 0) it.clicks / it.shows else null), num(it.splash), num(it.tap)) })
            }
            Spacer(Modifier.height(14.dp))
        }

        SectionTitle("In-app ad creatives", "MAX creative ID")
        Panel {
            Table(listOf("Creative", "Network", "DSP", "Format", "Impr", "Revenue"),
                d.creatives.sortedByDescending { it.rev }.take(30).map { listOf(it.cr, it.net, it.dsp.ifBlank { "–" }, it.fmt, num(it.imps), inr(it.rev * r, 1)) }, empty = "Creative ID nahi")
            Text("Asli image/video: AppLovin Ad Review aur AdMob Ad Review Center.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Google Ads creatives", "7 din · Google label")
        Panel {
            Table(listOf("Creative", "Type", "Label", "Campaign", "Impr", "Clicks", "Conv", "Cost/conv", "Cost"),
                d.assets.sortedByDescending { it.cost }.map { listOf(it.name, it.type, it.label.ifBlank { "–" }, it.campaign, num(it.impressions), num(it.clicks), num(it.conversions, 1), inr(it.cpi, 1), inr(it.cost, 1)) }, empty = "Google Ads script se creatives nahi")
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("AppLovin check", "app vs API")
        Panel {
            val m = d.maxCheck
            Table(listOf("Source", "Impr", "Revenue", "Update"), listOf(
                listOf("App (live)", num(m.appImps), usd(m.appRev), "live"),
                listOf("AppLovin API", num(m.apiImps), usd(m.apiRev), if (m.apiUpdated.isBlank()) "key nahi / cron nahi" else tFull(m.apiUpdated)),
            ))
        }
    }
}
