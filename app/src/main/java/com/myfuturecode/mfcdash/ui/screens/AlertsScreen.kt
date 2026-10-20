package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tsMs

/**
 * Alerts = server (api/alerts.php) ke rule engine ka output: Urgent / Dhyan do / Info, Claude AI ki explanation ke saath.
 * Notification tap yahin kholta hai.
 */
@Composable
fun AlertsScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    val ex = MaterialTheme.extra
    var filter by remember { mutableStateOf("open") }   // open | 3 | 2 | 1 | all
    var msg by remember { mutableStateOf<String?>(null) }
    val al = st.alerts
    LaunchedEffect(Unit) { vm.refreshAlerts() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Alerts", vm, st, nav, back = true)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (al == null) {
                if (st.alertsMissing) Panel {
                    Text("alerts.php server par nahi hai", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("README ka step dekho: server/api/alerts.php ko mfc_dash/api/ me upload karo. Uske baad yeh screen aur notifications chalu ho jaayenge.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.refreshAlerts() }) { Text("Dobara check karo") }
                } else Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            // ---- AI brief
            val brief = al.brief
            if (brief != null) {
                GradientHeader("CLAUDE AI · SUBAH KA BRIEF", fmtDay(brief.day) + " ke data par") {
                    Spacer(Modifier.height(8.dp))
                    Text(brief.text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
            } else if (!al.ai.enabled) {
                Panel(onClick = { nav.navigate("settings") }) {
                    Text("Claude AI abhi band hai", style = MaterialTheme.typography.titleSmall)
                    Text("Settings me API key daalo: har alert ki wajah + abhi kya karein, aur roz subah 9 baje ka brief.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
            }

            // ---- summary + actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (al.openCount == 0) "Sab theek ✅" else "${al.openCount} khule alert", style = MaterialTheme.typography.titleMedium)
                    val parts = ArrayList<String>()
                    if (al.urgentOpen > 0) parts += "${al.urgentOpen} urgent"
                    if (al.warnOpen > 0) parts += "${al.warnOpen} dhyan do"
                    if (al.computedAt > 0) parts += "check " + agoText(al.computedAt * 1000)
                    if (al.aiPending > 0) parts += "AI likh raha…"
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (al.unread > 0) TextButton(onClick = { vm.markAllRead() }) { Text("Sab padha") }
                FilledTonalButton(onClick = { vm.computeAlerts { msg = it } }, enabled = !st.alertsBusy) {
                    if (st.alertsBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Abhi check")
                }
            }
            msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(10.dp))

            // ---- filters
            ChipRow {
                MChip("Khule", filter == "open", { filter = "open" })
                MChip("Urgent", filter == "3", { filter = "3" }, dot = ex.bad)
                MChip("Dhyan do", filter == "2", { filter = "2" }, dot = ex.warn)
                MChip("Info", filter == "1", { filter = "1" }, dot = Info)
                MChip("History", filter == "all", { filter = "all" })
            }
            Spacer(Modifier.height(10.dp))

            val list = when (filter) {
                "open" -> al.alerts.filter { it.open }
                "all" -> al.alerts
                else -> al.alerts.filter { it.level == filter.toInt() && it.open }
            }
            if (list.isEmpty()) Panel {
                Text(if (filter == "open") "Abhi koi khula alert nahi. Rules har 4-5 min me data check karte hain." else "Is filter me kuch nahi.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { a -> AlertCard(a, nav, onResolve = { vm.resolveAlert(a.id) }) }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("Rules kya dekhte hain")
            Panel {
                listOf(
                    "URGENT" to "data aana band (app / server), ads band, Google Ads script chup, campaign 0 impressions, kharch par 0 install, CPI 2.5x, ROAS loss (<60%), crash spike, revenue 85%+ neeche, cron band",
                    "Dhyan do" to "data kam, impressions gire, CPI +50%, ROAS <85%, campaign D3 ROAS <60%, spend 2x tez, naya version crash, ad fill gira, revenue/installs 60%+ neeche, eCPM aadha, uninstall zyada, payments fail, D1 retention giri",
                    "Info" to "subah 9 baje kal ka hisaab (+AI brief), nayi/paused campaign, revenue/installs upar, naya record, urgent theek ho gaya",
                ).forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(k, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(78.dp), color = when (k) { "URGENT" -> ex.bad; "Dhyan do" -> ex.warn; else -> Info })
                        Text(v, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { nav.navigate("settings") }) { Text("Notification / Claude AI settings →") }
        }
    }
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
            Text(a.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(a.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (open) 30 else 2, overflow = TextOverflow.Ellipsis)
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
                    if (a.pkg.isNotBlank()) TextButton(onClick = { nav.navigate("app/${a.pkg}") }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("App kholo") }
                    if (a.campaign.isNotBlank() || a.account.isNotBlank()) TextButton(onClick = { nav.navigate("money") }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Campaigns") }
                    Spacer(Modifier.weight(1f))
                    if (a.open) TextButton(onClick = onResolve, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("✓ Done") }
                    else Text("band " + (a.resolvedAt?.let { if (it == "now") "abhi" else tsMs(it)?.let { ms -> agoText(ms) } ?: "" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}
