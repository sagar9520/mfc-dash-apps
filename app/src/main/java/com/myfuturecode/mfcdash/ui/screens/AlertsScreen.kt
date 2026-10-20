package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.AlertItem
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.TopBar
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.ChipRow
import com.myfuturecode.mfcdash.ui.components.GradientHeader
import com.myfuturecode.mfcdash.ui.components.MChip
import com.myfuturecode.mfcdash.ui.components.Panel
import com.myfuturecode.mfcdash.ui.components.SectionTitle
import com.myfuturecode.mfcdash.ui.theme.Info
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.ui.theme.parseHex
import com.myfuturecode.mfcdash.util.*

/**
 * Alerts = output of the server rule engine (api/alerts.php): Urgent / Watch / Info, with Claude AI explanation.
 * A notification tap opens this screen. Filters: state/level, app, kind (ads / campaigns / crash / data).
 */
@Composable
fun AlertsScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    val ex = MaterialTheme.extra
    var filter by rememberSaveable { mutableStateOf("open") }   // open | 3 | 2 | 1 | all
    var appF by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("") }         // "" | ads | gads | crash | data | money
    var msg by remember { mutableStateOf<String?>(null) }
    val box = remember { FilterBox() }
    val al = st.alerts
    LaunchedEffect(Unit) { vm.refreshAlerts() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Alerts".t(), vm, st, nav, back = true)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            if (al == null) {
                if (st.alertsMissing) Panel {
                    Text("alerts.php is not on the server".t(), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("See the README step: upload server/api/alerts.php into mfc_dash/api/. After that this screen and notifications start working.".t(),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.refreshAlerts() }) { Text("Check again".t()) }
                } else Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            // ---- AI brief
            val brief = al.brief
            if (brief != null) {
                GradientHeader("CLAUDE AI · MORNING BRIEF".t(), tf("on %s data", fmtDay(brief.day))) {
                    Spacer(Modifier.height(8.dp))
                    Text(brief.text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
            } else if (!al.ai.enabled) {
                Panel(onClick = { nav.navigate("settings") }) {
                    Text("Claude AI is off".t(), style = MaterialTheme.typography.titleSmall)
                    Text("Add the API key in Settings: cause + what to do now for every alert, and a daily 9 AM brief.".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- summary + actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (al.openCount == 0) "All good ✅".t() else tf("%s open alerts", al.openCount), style = MaterialTheme.typography.titleMedium)
                    val parts = ArrayList<String>()
                    if (al.urgentOpen > 0) parts += "${al.urgentOpen} " + "urgent".t()
                    if (al.warnOpen > 0) parts += "${al.warnOpen} " + "watch".t()
                    if (al.computedAt > 0) parts += "check".t() + " " + agoText(al.computedAt * 1000)
                    if (al.aiPending > 0) parts += "AI writing…".t()
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (al.unread > 0) TextButton(onClick = { vm.markAllRead() }) { Text("Mark read".t()) }
                FilledTonalButton(onClick = { vm.computeAlerts { msg = it } }, enabled = !st.alertsBusy) {
                    if (st.alertsBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Check now".t())
                }
            }
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(10.dp))

            // ---- filters
            ChipRow {
                FilterToggle(box)
                MChip("Open".t(), filter == "open", { filter = "open" })
                MChip("Urgent".t(), filter == "3", { filter = "3" }, dot = ex.bad)
                MChip("Watch".t(), filter == "2", { filter = "2" }, dot = ex.warn)
                MChip("Info".t(), filter == "1", { filter = "1" }, dot = Info)
                MChip("History".t(), filter == "all", { filter = "all" })
            }
            Spacer(Modifier.height(6.dp))
            val apps = st.data?.apps ?: emptyList()
            val pkgs = al.alerts.map { it.pkg }.filter { it.isNotBlank() }.distinct()
            if (pkgs.isNotEmpty()) {
                ChipRow {
                    MChip("All apps".t(), appF.isBlank(), { appF = "" })
                    pkgs.forEach { p -> val a = apps.firstOrNull { it.pkg == p }; MChip(a?.name ?: al.alerts.first { it.pkg == p }.appName.ifBlank { p }, appF == p, { appF = p }, dot = a?.let { parseHex(it.color) }) }
                }
                Spacer(Modifier.height(6.dp))
            }
            CompositionLocalProvider(LocalFilterBox provides box) {
                FilterChips("Kind".t(), listOf("" to "All".t(), "ads" to "Ads".t(), "gads" to "Google Ads", "crash" to "Crash".t(), "money" to "Money".t(), "health" to "AI Health".t(), "gah" to "Ads Monitor".t(), "cost" to "AI Cost".t(), "data" to "Data".t()), kind) { kind = it }
            }
            FilterSummary(box)

            val list = al.alerts.filter { a ->
                (when (filter) { "open" -> a.open; "all" -> true; else -> a.level == filter.toInt() && a.open }) &&
                (appF.isBlank() || a.pkg == appF) && (kind.isBlank() || alertKind(a.rule) == kind)
            }
            if (list.isEmpty()) Panel {
                Text(if (filter == "open" && appF.isBlank() && kind.isBlank()) "No open alert right now. Rules check the data every few minutes.".t() else "Nothing in this filter.".t(),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { a -> AlertCard(a, nav, onResolve = { vm.resolveAlert(a.id) }) }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("What the rules watch".t())
            Panel {
                listOf(
                    "URGENT".t() to "data stopped (app / server), ads stopped, Google Ads script silent, campaign 0 impressions, spend with 0 installs, CPI 2.5x, ROAS loss (<60%), crash spike, revenue 85%+ down, ad unit no-fill, cron down".t(),
                    "Watch".t() to "data low, impressions down, CPI +50%, ROAS <85%, campaign D3 ROAS <60%, spend 2x faster, new version crashing, ad fill down, ad unit CTR/eCPM/fill down, revenue/installs 60%+ down, eCPM halved, uninstalls high, payments failing, D1 retention down".t(),
                    "Info".t() to "9 AM summary of yesterday (+AI brief), new/paused campaign, revenue/installs up, new record, urgent issue recovered".t(),
                ).forEachIndexed { i, (k, v) ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(k, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(78.dp), color = when (i) { 0 -> ex.bad; 1 -> ex.warn; else -> Info })
                        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { nav.navigate("settings") }) { Text("Notification / Claude AI settings →".t()) }
        }
    }
    FilterSheet(box)
}

/** Group rule names into kinds for the Kind filter. */
fun alertKind(rule: String): String = when {
    rule.startsWith("health") -> "health"
    rule.startsWith("gah_") -> "gah"
    rule.startsWith("cost_") -> "cost"
    rule.startsWith("adunit") || rule.startsWith("fill") || rule.startsWith("ecpm") || rule.startsWith("ads_") || rule == "imps_low" || rule == "imps_zero" || rule.startsWith("network") -> "ads"
    rule.startsWith("camp") || rule.startsWith("cpi") || rule.startsWith("spend") || rule.startsWith("gads") || rule.startsWith("roas") -> "gads"
    rule.startsWith("crash") || rule.startsWith("ver_crash") -> "crash"
    rule.startsWith("rev") || rule.startsWith("pay") || rule.startsWith("record") || rule.startsWith("brief") -> "money"
    else -> "data"
}

@Composable
private fun AlertCard(a: AlertItem, nav: NavHostController, onResolve: () -> Unit) {
    val ex = MaterialTheme.extra
    val color = when (a.level) { 3 -> ex.bad; 2 -> ex.warn; else -> Info }
    var open by remember(a.id) { mutableStateOf(a.level == 3) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
        .clickable { open = !open }.height(IntrinsicSize.Min)) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(if (a.open) color else MaterialTheme.colorScheme.outline))
        Column(Modifier.padding(12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.levelText.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = .12f)).padding(horizontal = 6.dp, vertical = 2.dp))
                if (a.appName.isNotBlank()) { Spacer(Modifier.width(6.dp)); Text(a.appName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.weight(1f))
                Text((tsMs(a.createdAt)?.let { agoText(it) } ?: tFull(a.createdAt)) + (if (a.fired > 1) " · ${a.fired}x" else ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (a.unread && a.open) { Spacer(Modifier.width(6.dp)); Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color)) }
            }
            Spacer(Modifier.height(4.dp))
            Text(a.titleL, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(a.bodyL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (open) 40 else 2, overflow = TextOverflow.Ellipsis)
            if (a.ai.isNotBlank() && open) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)).padding(10.dp)) {
                    Text("CLAUDE AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(a.ai, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (a.isHealth) TextButton(onClick = { nav.navigate(a.route) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(if (a.issueId != null) "Open issue".t() else "AI Health".t()) }
                    else if (a.isGads) TextButton(onClick = { nav.navigate(a.route) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(if (a.gaAlertId != null) "Open alert".t() else "Ads Monitor".t()) }
                    else if (a.isCost) TextButton(onClick = { nav.navigate(a.route) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("AI Cost & Usage".t()) }
                    else if (a.pkg.isNotBlank()) TextButton(onClick = { nav.navigate("app/${a.pkg}") }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Open app".t()) }
                    if (a.campaign.isNotBlank() || a.account.isNotBlank()) TextButton(onClick = { nav.navigate("money") }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Campaigns".t()) }
                    Spacer(Modifier.weight(1f))
                    if (a.open) TextButton(onClick = onResolve, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("✓ Done".t()) }
                    else Text("closed".t() + " " + (a.resolvedAt?.let { if (it == "now") "now".t() else tsMs(it)?.let { ms -> agoText(ms) } ?: "" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}
