package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
 * profit hero → active now → alerts → best/worst → KPI tiles → har app ka card.
 * Filters: period, app, aur app cards ka sort / show.
 */
@Composable
fun HomeScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var sort by remember { mutableStateOf("profit") }
    var show by remember { mutableStateOf("") }
    TabScreen("MFC Dashboard", vm, st, nav, filters = { v ->
        if (v.allApps) {
            FilterChips("Sort".t(), listOf("profit" to "Profit".t(), "revenue" to "Revenue".t(), "installs" to "Installs".t(), "live" to "Live".t(), "roas" to "ROAS"), sort) { sort = it }
            FilterChips("Show".t(), listOf("" to "All".t(), "profit" to "In profit".t(), "loss" to "In loss".t(), "paid" to "With ad spend".t(), "organic" to "Organic only".t()), show) { show = it }
        }
    }) { v ->
        val d = v.d
        val ex = MaterialTheme.extra
        val k = d.kpi; val r = d.r
        val prev = prevDay(d)

        // ---- Profit hero
        val earned = k.revInr + k.purchaseInr
        val roas = k.roasTotal ?: (if (k.gadsCostInr > 0) earned / k.gadsCostInr else null)
        GradientHeader("PROFIT · " + periodText(d).uppercase(), "ad revenue + payments − Google Ads spend".t()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(inr(k.profitInr), style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.width(10.dp))
                delta(k.profitInr, prev?.profit(r))?.let { dl ->
                    Text(signed(dl * 100, 0, "% vs yesterday".t()), style = MaterialTheme.typography.labelMedium, color = if (dl >= 0) Color(0xFF3EE68A) else Color(0xFFFFB4C0), modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("Ad revenue".t() to inr(k.revInr), "Payments".t() to inr(k.purchaseInr), "Ad spend".t() to inr(k.gadsCostInr)).forEach { (l, vv) ->
                    Column(Modifier.weight(1f)) {
                        Text(l.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .7f))
                        Text(vv, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Blended ROAS", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .8f), modifier = Modifier.weight(1f))
                Text(roasPct(roas), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            RoasBar(roas, st.roasTarget)
            Text("| " + "break-even".t() + " 100%   ┆ " + "target".t() + " " + num(st.roasTarget * 100, 0) + "%", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .65f))
        }
        Spacer(Modifier.height(12.dp))

        // ---- AI App Health & Auto Fix (alag module)
        val hOpen = st.alerts?.alerts?.count { it.open && it.rule.startsWith("health") } ?: 0
        AlertRow("AI", "AI App Health & Auto Fix".t(),
            if (hOpen > 0) tf("%s open health alerts · consoles, apps, crashes, fixes, AAB", hOpen) else "Consoles, apps, crashes, AI fixes, AAB releases, new tasks".t(),
            if (hOpen > 0) ex.bad else MaterialTheme.colorScheme.primary) { nav.navigate("health") }
        Spacer(Modifier.height(8.dp))
        // ---- Google Ads AI Health Monitor (alag module, sirf monitoring)
        val gOpen = st.alerts?.alerts?.filter { it.open && it.isGads } ?: emptyList()
        val gCrit = gOpen.count { it.level >= 3 }
        AlertRow("G", "Google Ads AI Health Monitor".t(),
            if (gOpen.isNotEmpty()) tf("%s open Google Ads alerts · accounts, spend, CPI, policy", gOpen.size) else "Accounts, suspensions, spend, CPI, ROAS, creatives · read-only".t(),
            if (gCrit > 0) ex.bad else if (gOpen.isNotEmpty()) ex.warn else MaterialTheme.colorScheme.primary) { nav.navigate("gads") }
        Spacer(Modifier.height(8.dp))
        // ---- AI App Research Studio (alag module: research, ranking, approvals; kabhi khud publish nahi)
        val sOpen = st.alerts?.alerts?.filter { it.open && it.isStudio } ?: emptyList()
        AlertRow("R", "AI App Research Studio".t(),
            if (sOpen.isNotEmpty()) tf("%s Studio updates waiting for you", sOpen.size) else "App ideas, competitors, profit, approvals · never auto-publish".t(),
            if (sOpen.isNotEmpty()) ex.warn else MaterialTheme.colorScheme.primary) { nav.navigate("studio") }
        Spacer(Modifier.height(12.dp))

        // ---- Active now
        SectionTitle("Active now".t(), if (v.allApps) "live, all apps".t() else d.appName(v.pkg!!))
        val ax = v.extra?.active
        val lm = v.extra?.liveMinutes ?: 6
        TileGrid(listOf(
            Triple("$lm min", num(d.liveTotal), "active now".t()),
            Triple("30 min", num(d.live30), "active".t()),
            Triple("1 hour".t(), num(ax?.live1h), if (ax == null) "needs app.php".t() else "active".t()),
            Triple("24 hours".t(), num(ax?.live24h), if (ax == null) "needs app.php".t() else "active".t()),
            Triple("DAU today".t(), num(k.dau), num(k.sessions) + " " + "sessions".t()),
            Triple("WAU / MAU", if (ax == null) "–" else short(ax.wau) + " / " + short(ax.mau), if (ax?.dauMau == null) null else "DAU/MAU " + pct(ax.dauMau, 0)),
            Triple("Avg session".t(), minutes(k.avgSessionSec), null),
            Triple("Installs".t(), num(k.installs), "first open".t()),
            Triple("Uninstalls".t(), num(k.uninstalls), "FCM check".t()),
        ), colors = mapOf(0 to ex.good))

        // ---- Server alerts (api/alerts.php rule engine + Claude AI)
        val sa = st.alerts?.alerts?.filter { it.open && it.level >= 2 && (v.pkg == null || it.pkg == v.pkg || it.pkg.isBlank()) } ?: emptyList()
        if (sa.isNotEmpty()) {
            SectionTitle("Alerts".t(), tf("%s open · tap for details", sa.size))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sa.take(5).forEach { a ->
                    AlertRow(if (a.level == 3) "!!" else "!", a.titleL, (a.ai.ifBlank { a.bodyL }).lineSequence().firstOrNull()?.take(110) ?: "",
                        if (a.level == 3) ex.bad else ex.warn) { nav.navigate("alerts") }
                }
            }
            if (sa.size > 5) TextButton(onClick = { nav.navigate("alerts") }) { Text(tf("See all %s alerts", sa.size)) }
            Spacer(Modifier.height(14.dp))
        } else if (st.alerts != null) {
            SectionTitle("Alerts".t(), "server check".t() + " " + (st.alerts.computedAt.takeIf { it > 0 }?.let { agoText(it * 1000) } ?: ""))
            AlertRow("✓", "All good: no urgent or watch alerts".t(), "Data, ads, Google Ads, crashes, revenue — all normal".t(), ex.good) { nav.navigate("alerts") }
            Spacer(Modifier.height(14.dp))
        }

        // ---- App ke apne (period ke data se) alerts
        val alerts = buildAlerts(d, st.roasTarget)
        SectionTitle("Watch".t(), if (alerts.isEmpty()) "all good".t() else tf("%s items", alerts.size))
        if (alerts.isEmpty()) Panel { Text("No alerts: no crashes, loss campaigns or low fill found.".t(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            alerts.forEach { a -> AlertRow(a.icon, a.text, a.sub, when (a.level) { 2 -> ex.bad; 1 -> ex.warn; else -> ex.muted }) { nav.navigate(a.route) } }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Best / worst
        val apps = d.perApp
        if (apps.size >= 2) {
            SectionTitle("Best / worst".t(), "by profit".t())
            val best = apps.maxBy { it.profit(r) }; val worst = apps.minBy { it.profit(r) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BestWorst("BEST APP".t(), best.name, inr(best.profit(r)), roasPct(best.roas(r)), ex.good, Modifier.weight(1f)) { nav.navigate("app/${best.pkg}") }
                BestWorst("WORST APP".t(), worst.name, inr(worst.profit(r)), roasPct(worst.roas(r)), ex.bad, Modifier.weight(1f)) { nav.navigate("app/${worst.pkg}") }
            }
            Spacer(Modifier.height(8.dp))
        }
        val camps = d.campaigns.filter { it.cost > 0 }
        if (camps.size >= 2) {
            val bc = camps.filter { (it.cpi ?: 0.0) > 0 }.minByOrNull { it.cpi!! }
            val wc = camps.maxByOrNull { it.cpi ?: Double.MAX_VALUE }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bc != null) BestWorst("CHEAPEST CAMPAIGN".t(), bc.campaign, inr(bc.cpi, 2) + " / conv", inr(bc.cost) + " " + "spend".t(), ex.good, Modifier.weight(1f)) { nav.navigate("campaign/${d.campaigns.indexOf(bc)}") }
                if (wc != null) BestWorst("COSTLIEST CAMPAIGN".t(), wc.campaign, if (wc.cpi == null) "0 conv" else inr(wc.cpi, 2) + " / conv", inr(wc.cost) + " " + "spend".t(), ex.bad, Modifier.weight(1f)) { nav.navigate("campaign/${d.campaigns.indexOf(wc)}") }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---- KPI tiles
        SectionTitle("KPIs", periodText(d))
        val profitPerInstall = if (k.installs > 0) k.profitInr / k.installs else null
        val promoInstalls = v.cp?.rows?.sumOf { it.installs }
        TileGrid(listOf(
            Triple("Cost / conv".t(), inr(k.gadsCpiInr, 2), num(k.gadsInstalls, 1) + " conv"),
            Triple("Profit / install".t(), inr(profitPerInstall, 2), "all installs".t()),
            Triple("Ads users rev".t(), inr(k.revFromGadsUsersInr), "from Google Ads installs".t()),
            Triple("eCPM", usd(k.ecpmUsd), inr(k.ecpmUsd * r)),
            Triple("ARPDAU", inr(k.arpdauUsd * r, 2), usd(k.arpdauUsd, 4)),
            Triple("Impr / DAU", num(k.impsPerDau, 1), num(k.imps) + " impr"),
            Triple("Fill".t(), pct(d.fill), "loaded ÷ requests".t()),
            Triple("Crashes".t(), num(k.crashes), null),
            Triple("Promo installs".t(), num(promoInstalls), "from cross promo".t()),
        ), colors = mapOf(7 to if (k.crashes > 0) ex.bad else ex.good))

        // ---- Apps
        val shown = apps.filter { a ->
            when (show) {
                "profit" -> a.profit(r) >= 0
                "loss" -> a.profit(r) < 0
                "paid" -> a.gadsCostInr > 0
                "organic" -> a.gadsCostInr <= 0
                else -> true
            }
        }.sortedByDescending { a ->
            when (sort) { "revenue" -> a.revInr(r) + a.purchaseInr; "installs" -> a.installs; "live" -> a.live; "roas" -> a.roas(r) ?: -1.0; else -> a.profit(r) }
        }
        SectionTitle("Apps".t(), if (shown.size == apps.size) "tap for full data".t() else tf("%s of %s", shown.size, apps.size))
        if (shown.isEmpty()) Empty("No app matches this filter".t())
        shown.forEach { a ->
            AppCard(a, r, d.crashes.filter { it.pkg == a.pkg }.sumOf { it.n }) { nav.navigate("app/${a.pkg}") }
            Spacer(Modifier.height(8.dp))
        }
        Text("Data: api/data.php · " + agoText(st.lastOk) + (if (d.aggLagSec > 300) " · " + tf("summary %s min old (cron?)", (d.aggLagSec / 60).toInt()) else ""),
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
                Text("PROFIT".t(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Mini("Revenue".t(), inr(a.revInr(r)), Modifier.weight(1f)); Mini("Payments".t(), inr(a.purchaseInr), Modifier.weight(1f))
            Mini("Spend".t(), inr(a.gadsCostInr), Modifier.weight(1f)); Mini("Cost/conv".t(), if (a.cpi() == null) "–" else inr(a.cpi(), 1), Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Mini("Live".t(), num(a.live), Modifier.weight(1f), ex.good); Mini("DAU", num(a.dau), Modifier.weight(1f))
            Mini("Installs".t(), num(a.installs), Modifier.weight(1f)); Mini("Crash".t(), num(crashes), Modifier.weight(1f), if (crashes > 0) ex.bad else null)
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

/** Period ke data se app khud nikalti hai: crash, loss campaign, low fill, old summary, Google Ads script stopped. */
fun buildAlerts(d: DashData, target: Double): List<Alert> {
    val out = ArrayList<Alert>()
    val r = d.r
    d.crashes.groupBy { it.pkg }.mapValues { it.value.sumOf { c -> c.n } }.entries.sortedByDescending { it.value }.take(2).forEach { (pkg, n) ->
        if (n > 0) out += Alert(2, "!", "${d.appName(pkg)}: ${num(n)} " + "crashes".t(), d.crashes.filter { it.pkg == pkg }.maxByOrNull { it.n }?.let { it.error.take(40) + " · v" + it.ver } ?: "", "app/$pkg")
    }
    d.perApp.filter { it.gadsCostInr > 50 }.forEach { a ->
        val roas = a.roas(r) ?: return@forEach
        if (roas < 1.0) out += Alert(2, "₹", tf("%s in loss: ROAS %s", a.name, roasPct(roas)), tf("spend %s, earned %s", inr(a.gadsCostInr), inr(a.revInr(r) + a.purchaseInr)), "app/${a.pkg}")
        else if (roas < target) out += Alert(1, "₹", tf("%s below target: ROAS %s", a.name, roasPct(roas)), "target".t() + " " + num(target * 100, 0) + "%", "app/${a.pkg}")
    }
    d.campaigns.forEachIndexed { i, c -> if (c.cost > 100 && c.conversions == 0.0) out += Alert(2, "0", tf("%s: spend but 0 conv", c.campaign), inr(c.cost) + " " + "spent".t() + ", " + num(c.clicks) + " clicks", "campaign/$i") }
    d.byFormat.filter { it.adRequest >= 100 && (it.fill ?: 1.0) < 0.5 }.sortedBy { it.fill }.take(2).forEach { f ->
        out += Alert(1, "%", tf("%s (%s) fill only %s", f.fmt, f.med, pct(f.fill, 0)), num(f.adRequest) + " requests", "ads")
    }
    if (d.aggLagSec > 900) out += Alert(1, "⏱", tf("Summary %s min old", (d.aggLagSec / 60).toInt()), "is cron/run.php running?".t(), "settings")
    if (d.today && d.gadsAccounts.isEmpty()) out += Alert(0, "G", "No Google Ads data coming in".t(), "check the account script".t(), "money")
    return out
}
