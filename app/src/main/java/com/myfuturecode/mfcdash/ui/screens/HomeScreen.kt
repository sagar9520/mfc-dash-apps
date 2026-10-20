package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.*
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.ui.theme.parseHex
import com.myfuturecode.mfcdash.util.*

/**
 * Home = sabse zyada data ek nazar mein (research: Adjust/BoostOps overview pattern):
 * profit hero → abhi active → alerts → best/worst → KPI tiles → har app ka card.
 */
@Composable
fun HomeScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    TabScreen("MFC Dashboard", vm, st, nav) { d ->
        val ex = MaterialTheme.extra
        val k = d.kpi; val r = d.r
        val prev = prevDay(d)

        // ---- Profit hero
        val kamaya = k.revInr + k.purchaseInr
        val roas = k.roasTotal ?: (if (k.gadsCostInr > 0) kamaya / k.gadsCostInr else null)
        GradientHeader("PROFIT · " + periodText(d).uppercase(), "ad revenue + payments − Google Ads spend") {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(inr(k.profitInr), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.width(10.dp))
                delta(k.profitInr, prev?.profit(r))?.let { dl ->
                    Text(signed(dl * 100, 0, "% vs kal"), style = MaterialTheme.typography.labelMedium, color = if (dl >= 0) Color(0xFF3EE68A) else Color(0xFFFFB4C0), modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("Ad revenue" to inr(k.revInr), "Payments" to inr(k.purchaseInr), "Ad spend" to inr(k.gadsCostInr)).forEach { (l, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(l.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f))
                        Text(v, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Blended ROAS", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .8f), modifier = Modifier.weight(1f))
                Text(roasPct(roas), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            RoasBar(roas, st.roasTarget)
            Text("| break-even 100%   ┆ target " + num(st.roasTarget * 100, 0) + "%", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .65f))
        }
        Spacer(Modifier.height(12.dp))

        // ---- Abhi active
        SectionTitle("Abhi active", "live, sab apps")
        val ax = st.extra?.active
        val lm = st.extra?.liveMinutes ?: 6
        TileGrid(listOf(
            Triple("$lm min", num(d.liveTotal), "abhi active"),
            Triple("30 min", num(d.live30), "active"),
            Triple("1 ghanta", num(ax?.live1h), if (ax == null) "app.php chahiye" else "active"),
            Triple("24 ghante", num(ax?.live24h), if (ax == null) "app.php chahiye" else "active"),
            Triple("Aaj DAU", num(k.dau), num(k.sessions) + " sessions"),
            Triple("WAU / MAU", if (ax == null) "–" else short(ax.wau) + " / " + short(ax.mau), if (ax?.dauMau == null) null else "DAU/MAU " + pct(ax.dauMau, 0)),
            Triple("Avg session", minutes(k.avgSessionSec), null),
            Triple("Installs", num(k.installs), "first open"),
            Triple("Uninstalls", num(k.uninstalls), "FCM check"),
        ), colors = mapOf(0 to ex.good))

        // ---- Alerts
        val alerts = buildAlerts(d, st.roasTarget)
        SectionTitle("Dhyaan do", if (alerts.isEmpty()) "sab theek" else "${alerts.size} baat")
        if (alerts.isEmpty()) Panel { Text("Koi alert nahi: crash, loss campaign ya kam fill nahi mila.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            alerts.forEach { a -> AlertRow(a.icon, a.text, a.sub, when (a.level) { 2 -> ex.bad; 1 -> ex.warn; else -> ex.muted }) { nav.navigate(a.route) } }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Best / worst
        val apps = d.perApp
        if (apps.size >= 2) {
            SectionTitle("Best / worst", "profit ke hisaab se")
            val best = apps.maxBy { it.profit(r) }; val worst = apps.minBy { it.profit(r) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BestWorst("BEST APP", best.name, inr(best.profit(r)), roasPct(best.roas(r)), ex.good, Modifier.weight(1f)) { nav.navigate("app/${best.pkg}") }
                BestWorst("WORST APP", worst.name, inr(worst.profit(r)), roasPct(worst.roas(r)), ex.bad, Modifier.weight(1f)) { nav.navigate("app/${worst.pkg}") }
            }
            Spacer(Modifier.height(8.dp))
        }
        val camps = d.campaigns.filter { it.cost > 0 }
        if (camps.size >= 2) {
            val bc = camps.filter { (it.cpi ?: 0.0) > 0 }.minByOrNull { it.cpi!! }
            val wc = camps.maxByOrNull { it.cpi ?: Double.MAX_VALUE }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bc != null) BestWorst("SASTI CAMPAIGN", bc.campaign, inr(bc.cpi, 2) + " / conv", inr(bc.cost) + " spend", ex.good, Modifier.weight(1f)) { nav.navigate("campaign/${d.campaigns.indexOf(bc)}") }
                if (wc != null) BestWorst("MEHNGI CAMPAIGN", wc.campaign, if (wc.cpi == null) "0 conv" else inr(wc.cpi, 2) + " / conv", inr(wc.cost) + " spend", ex.bad, Modifier.weight(1f)) { nav.navigate("campaign/${d.campaigns.indexOf(wc)}") }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---- KPI tiles
        SectionTitle("KPIs", periodText(d))
        val profitPerInstall = if (k.installs > 0) k.profitInr / k.installs else null
        val promoInstalls = st.cp?.rows?.sumOf { it.installs }
        TileGrid(listOf(
            Triple("Cost / conv", inr(k.gadsCpiInr, 2), num(k.gadsInstalls, 1) + " conv"),
            Triple("Profit / install", inr(profitPerInstall, 2), "sab installs"),
            Triple("Ads users rev", inr(k.revFromGadsUsersInr), "Google Ads installs se"),
            Triple("eCPM", usd(k.ecpmUsd), inr(k.ecpmUsd * r)),
            Triple("ARPDAU", inr(k.arpdauUsd * r, 2), usd(k.arpdauUsd, 4)),
            Triple("Impr / DAU", num(k.impsPerDau, 1), num(k.imps) + " impr"),
            Triple("Fill", pct(d.fill), "loaded ÷ requests"),
            Triple("Crashes", num(k.crashes), null),
            Triple("Promo installs", num(promoInstalls), "cross promo se"),
        ), colors = mapOf(7 to if (k.crashes > 0) ex.bad else ex.good))

        // ---- Apps
        SectionTitle("Apps", "tap karo, poora data")
        if (apps.isEmpty()) Empty("Koi app data nahi")
        apps.sortedByDescending { it.profit(r) }.forEach { a ->
            AppCard(a, r, d.crashes.filter { it.pkg == a.pkg }.sumOf { it.n }) { nav.navigate("app/${a.pkg}") }
            Spacer(Modifier.height(8.dp))
        }
        Text("Data: api/data.php · " + agoText(st.lastOk) + (if (d.aggLagSec > 300) " · summary ${(d.aggLagSec / 60).toInt()} min purani (cron?)" else ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun BestWorst(tag: String, name: String, v1: String, v2: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .10f)).clickable(onClick = onClick).padding(12.dp)) {
        Text(tag, style = MaterialTheme.typography.labelSmall, color = color)
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(v1, style = MaterialTheme.typography.titleMedium, color = color)
        Text(v2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AppCard(a: PerApp, r: Double, crashes: Double, onClick: () -> Unit) {
    val ex = MaterialTheme.extra
    val roas = a.roas(r); val profit = a.profit(r)
    Panel(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(parseHex(a.color)))
            Spacer(Modifier.width(8.dp))
            Text(a.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            HealthBadge(healthOf(roas, profit))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("PROFIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(inr(profit), style = MaterialTheme.typography.headlineMedium, color = if (profit >= 0) ex.good else ex.bad)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("ROAS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(roasPct(roas), style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(6.dp))
        RoasBar(roas, 1.5, height = 6.dp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Mini("Revenue", inr(a.revInr(r)), Modifier.weight(1f)); Mini("Payments", inr(a.purchaseInr), Modifier.weight(1f))
            Mini("Spend", inr(a.gadsCostInr), Modifier.weight(1f)); Mini("Cost/conv", if (a.cpi() == null) "–" else inr(a.cpi(), 1), Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Mini("Live", num(a.live), Modifier.weight(1f), ex.good); Mini("DAU", num(a.dau), Modifier.weight(1f))
            Mini("Installs", num(a.installs), Modifier.weight(1f)); Mini("Crash", num(crashes), Modifier.weight(1f), if (crashes > 0) ex.bad else null)
        }
    }
}

@Composable
fun Mini(l: String, v: String, modifier: Modifier = Modifier, color: Color? = null) {
    Column(modifier) {
        Text(l.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

data class Alert(val level: Int, val icon: String, val text: String, val sub: String, val route: String)

/** Alerts data se khud nikalte hain: crash, loss campaign, kam fill, summary purani, Google Ads script ruka. */
fun buildAlerts(d: DashData, target: Double): List<Alert> {
    val out = ArrayList<Alert>()
    val r = d.r
    d.crashes.groupBy { it.pkg }.mapValues { it.value.sumOf { c -> c.n } }.entries.sortedByDescending { it.value }.take(2).forEach { (pkg, n) ->
        if (n > 0) out += Alert(2, "!", "${d.appName(pkg)}: ${num(n)} crash", d.crashes.filter { it.pkg == pkg }.maxByOrNull { it.n }?.let { it.error.take(40) + " · v" + it.ver } ?: "", "app/$pkg")
    }
    d.perApp.filter { it.gadsCostInr > 50 }.forEach { a ->
        val roas = a.roas(r) ?: return@forEach
        if (roas < 1.0) out += Alert(2, "₹", "${a.name} loss mein: ROAS ${roasPct(roas)}", "spend ${inr(a.gadsCostInr)}, kamaya ${inr(a.revInr(r) + a.purchaseInr)}", "app/${a.pkg}")
        else if (roas < target) out += Alert(1, "₹", "${a.name} target se neeche: ROAS ${roasPct(roas)}", "target ${num(target * 100, 0)}%", "app/${a.pkg}")
    }
    d.campaigns.forEachIndexed { i, c -> if (c.cost > 100 && c.conversions == 0.0) out += Alert(2, "0", "${c.campaign}: spend par 0 conv", inr(c.cost) + " kharch, " + num(c.clicks) + " clicks", "campaign/$i") }
    d.byFormat.filter { it.adRequest >= 100 && (it.fill ?: 1.0) < 0.5 }.sortedBy { it.fill }.take(2).forEach { f ->
        out += Alert(1, "%", "${f.fmt} (${f.med}) fill sirf ${pct(f.fill, 0)}", num(f.adRequest) + " requests", "ads")
    }
    if (d.aggLagSec > 900) out += Alert(1, "⏱", "Summary ${(d.aggLagSec / 60).toInt()} min purani", "cron/run.php chal raha hai?", "settings")
    if (d.today && d.gadsAccounts.isEmpty()) out += Alert(0, "G", "Google Ads data nahi aa raha", "account script ko check karo", "money")
    return out
}
