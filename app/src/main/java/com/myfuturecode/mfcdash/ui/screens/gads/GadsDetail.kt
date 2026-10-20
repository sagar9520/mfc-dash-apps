package com.myfuturecode.mfcdash.ui.screens.gads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.*
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.GadsVm
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.screens.health.*
import com.myfuturecode.mfcdash.ui.theme.Info
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*
import org.json.JSONObject

@Composable
private fun GFrame(title: String, sub: String?, g: GState, nav: NavHostController, onRefresh: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HTopBar(title, sub ?: updatedText(g), nav, busy = false, onRefresh = onRefresh)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp), content = content)
    }
}

// ---------------------------------------------------------------- alert detail (AI root cause)

@Composable
fun GadsAlertScreen(vm: GadsVm, nav: NavHostController, id: Long) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("id" to id.toString())
    var reload by remember { mutableIntStateOf(0) }
    val j = g.view("alert", p)
    var askedAt by remember { mutableLongStateOf(0L) }
    // AI server par background me chalta hai (~30 sec): tab tak jaldi-jaldi dekho
    val aiRunning = askedAt > 0 && System.currentTimeMillis() - askedAt < 120_000 && j?.obj("alert")?.sn("ai_at").let { it == null || (tsMs(it) ?: 0L) < askedAt - 5_000 }
    Poll(if (aiRunning) 5_000 else 30_000, id, reload, aiRunning) { vm.load("alert", p) }
    GFrame("Alert".t() + " #$id", null, g, nav, { reload++ }) {
        GLoad(g, j, { vm.load("alert", p) }) { r ->
            val a = GAlert.of(r.obj("alert"))
            val ex = MaterialTheme.extra
            val cur = a.currency
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(a.level, levelColor(a.level)); Spacer(Modifier.width(6.dp)); Pill(a.klass, gStateColor(a.klass))
                if (a.sim) { Spacer(Modifier.width(6.dp)); Pill("SIMULATED · dev test".t(), ex.muted) }
                Spacer(Modifier.weight(1f))
                Pill(if (a.open) "OPEN" else "CLOSED", if (a.open) levelColor(a.level) else ex.good)
            }
            Spacer(Modifier.height(8.dp))
            Text(a.titleL, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(a.whatL, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Panel {
                GLabel("Where".t())
                KV("Company".t(), a.company.ifBlank { "–" })
                KV("Customer ID".t(), a.cidFmt)
                r.optJSONObject("account")?.let { acc -> GAccount.of(acc).let { ga -> if (ga.mccLabel.isNotBlank()) KV("MCC (label)".t(), ga.mccLabel) } }
                if (a.campaign.isNotBlank() || a.campaignId.isNotBlank()) KV("Campaign".t(), a.campaign.ifBlank { a.campaignId })
                if (a.adGroupId.isNotBlank()) KV("Ad group".t(), a.adGroupId)
                if (a.country.isNotBlank()) KV("Country".t(), a.country)
                KV("Currency".t(), cur.ifBlank { "–" })
                GLabel("When".t())
                KV("First seen".t(), tFull(a.firstSeen))
                KV("Last checked".t(), tFull(a.lastSeen))
                a.notifiedAt?.let { KV("Notified".t(), tFull(it)) }
                a.ackAt?.let { KV("Acknowledged".t(), tFull(it)) }
                a.resolvedAt?.let { KV("Closed".t(), tFull(it) + (if (a.resolvedBy.isNotBlank()) " · " + a.resolvedBy else "")) }
                GLabel("Evidence".t())
                KV("Source".t(), a.evidence.ifBlank { "–" })
                KV("Data freshness".t(), a.freshnessMin?.let { ageMin(it) } ?: "–")
                KV("Classification".t(), gText(a.klass), valueColor = gStateColor(a.klass))
            }
            Spacer(Modifier.height(10.dp))
            SectionTitle("Numbers (current vs previous)".t())
            Panel { MetricsList(a.metrics, cur) }
            if (a.impact.isNotBlank()) { Spacer(Modifier.height(10.dp)); Panel { GLabel("Business impact".t()); Text(a.impact, style = MaterialTheme.typography.bodyMedium) } }
            Spacer(Modifier.height(10.dp))
            Panel {
                GLabel("Possible causes (rules)".t())
                if (a.causes.length() == 0) Empty("–")
                for (i in 0 until a.causes.length()) Text("• " + a.causes.optString(i), style = MaterialTheme.typography.bodySmall)
                GLabel("Suggested next step (manual)".t())
                Text(a.nextL, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            SectionTitle("AI root cause".t(), a.aiAt?.let { tFull(it) })
            Panel {
                val ai = a.ai
                if (ai != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val k = ai.s("classification")
                        if (k.isNotBlank()) Pill(k, gStateColor(k.substringBefore(' ')))
                        Spacer(Modifier.weight(1f))
                        a.aiConf?.let { Text(tf("%s%% confidence", it), style = MaterialTheme.typography.labelMedium, color = Info) }
                    }
                    val sum = if (Lang.hi) ai.s("summary_hi").ifBlank { ai.s("summary") } else ai.s("summary")
                    if (sum.isNotBlank()) AiBox("AI summary".t(), sum)
                    val causes = ai.arr("causes").mapObj { it }
                    if (causes.isNotEmpty()) GLabel("Likely causes".t())
                    causes.forEach { c ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Pill(c.s("likelihood").ifBlank { "?" }, when (c.s("likelihood")) { "high" -> ex.bad; "medium" -> ex.warn; else -> ex.muted })
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.s("cause"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                if (c.s("evidence").isNotBlank()) Small(c.s("evidence"))
                            }
                        }
                    }
                    val acts = (if (Lang.hi) ai.arr("manual_actions_hi").strings() else emptyList()).ifEmpty { ai.arr("manual_actions").strings() }
                    if (acts.isNotEmpty()) GLabel("What to do (manually in Google Ads)".t())
                    acts.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall) }
                    ai.sn("business_impact")?.let { GLabel("Business impact".t()); Small(it) }
                    ai.sn("creative_suggestion")?.let { AiBox("Creative idea for your review".t(), it) }
                } else {
                    if (a.aiErr.isNotBlank()) Small("AI failed: ".t() + a.aiErr, ex.bad)
                    else Small("No AI analysis yet. AI runs automatically for new critical alerts and in the 6-hourly deep analysis.".t())
                }
                Spacer(Modifier.height(8.dp))
                if (aiRunning) { LinearProgressIndicator(Modifier.fillMaxWidth()); Small("AI is checking…".t()) }
                if (r.b("ai_on")) BusyButton(if (ai == null) "Ask AI now".t() else "Ask AI again".t(), "ai$id" in g.busy || aiRunning, Modifier.fillMaxWidth(), outlined = ai != null) {
                    vm.act("ai", mapOf("id" to id.toString()), busyKey = "ai$id", ok = "AI is checking (about 30 sec)".t()) { askedAt = System.currentTimeMillis(); vm.load("alert", p) }
                } else Small("AI key not set (Settings > AI in the app).".t())
            }
            Spacer(Modifier.height(10.dp))
            if (a.open) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (a.ackAt == null) BusyButton("Acknowledge".t(), "ack$id" in g.busy, Modifier.weight(1f), outlined = true) {
                        vm.act("ack", mapOf("id" to id.toString()), busyKey = "ack$id", ok = "No more notifications for this alert".t()) { vm.load("alert", p) }
                    }
                    if (a.closable) BusyButton("Close".t(), "close$id" in g.busy, Modifier.weight(1f), outlined = true) {
                        vm.act("close", mapOf("id" to id.toString()), busyKey = "close$id", ok = "Closed".t()) { vm.load("alert", p) }
                    }
                }
                Small(if (a.closable) "Email / simulated alerts close by hand. Metric alerts close by themselves when fresh data shows the problem is gone.".t()
                    else "This alert closes by itself when fresh data shows the problem is gone. Acknowledge stops repeat notifications.".t())
            }
            ReadOnlyNote()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.navigate("gads/account/${a.cid}") }, modifier = Modifier.weight(1f)) { Text("Account".t()) }
                if (a.campaignId.isNotBlank() && !a.sim) OutlinedButton(onClick = { nav.navigate("gads/campaign/${a.cid}/${a.campaignId}") }, modifier = Modifier.weight(1f)) { Text("Campaign".t()) }
            }
            val ev = r.arr("events").mapObj(GEvent::of)
            if (ev.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SectionTitle("Log".t())
                Panel { ev.forEach { GEventRow(it, null) } }
            }
        }
    }
}

// ---------------------------------------------------------------- account detail

@Composable
fun GadsAccountScreen(vm: GadsVm, nav: NavHostController, cid: String) {
    val g by vm.s.collectAsStateWithLifecycle()
    var reload by remember { mutableIntStateOf(0) }
    val acc = g.view("account", mapOf("cid" to cid))?.optJSONObject("account")
    GFrame(acc?.s("company")?.ifBlank { null } ?: ("CID " + cid), "CID " + (acc?.s("cid_fmt") ?: cid) + " · " + updatedText(g), g, nav, { reload++ }) {
        AccountBody(g, vm, nav, cid, reload)
    }
}

@Composable
fun AccountBody(g: GState, vm: GadsVm, nav: NavHostController, cid: String, reload: Int) {
    val p = mapOf("cid" to cid)
    Poll(60_000, cid, reload) { vm.load("account", p) }
    var edit by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<String?>(null) }
    var newTok by remember { mutableStateOf(false) }
    var th by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    GLoad(g, g.view("account", p), { vm.load("account", p) }) { j ->
        val a = GAccount.of(j.obj("account"))
        val ex = MaterialTheme.extra
        val cur = a.currency
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.company, style = MaterialTheme.typography.titleMedium)
                    Small("CID " + a.cidFmt + (if (a.accountName.isNotBlank()) " · " + a.accountName else ""))
                }
                Pill(a.health.ifBlank { "NEW" }, gStateColor(a.health))
            }
            if (a.lastState.isNotBlank()) Small(a.lastState)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { GLabel("Spend today".t()); Text(money(j.dn("spend_today"), cur), style = MaterialTheme.typography.titleMedium) }
                Column(Modifier.weight(1f)) { GLabel("Usual by now".t()); Text(money(j.dn("usual_by_now"), cur), style = MaterialTheme.typography.titleMedium) }
                Column(Modifier.weight(1f)) { GLabel("Pace".t()); Text(signedPct(j.dn("pace_pct")), style = MaterialTheme.typography.titleMedium) }
            }
            j.sn("reported_through")?.let { Small(tf("Google reported up to %s (account time %s)", it, j.s("tz_used"))) }
            if (!j.b("fresh")) Small(if (j.dn("data_age_min") == null) "DATA SOURCE DISCONNECTED".t() else "STALE DATA: spend rules paused until fresh data arrives".t(), ex.bad)
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle("Data sources".t(), "data".t() + " " + ageMin(j.dn("data_age_min")?.toInt()))
        Panel {
            SourcesList(j.optJSONObject("sources"))
            if (a.srcScript) {
                Hairline()
                KV("Script status (from Google)".t(), a.scriptStatus.ifBlank { "–" }, valueColor = gStateColor(a.scriptStatus))
                KV("Token".t(), a.tokenHint.ifBlank { "not set".t() })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedButton(onClick = { newTok = true }, modifier = Modifier.weight(1f)) { Text("New token + script".t()) }
                }
            }
            val errs = j.arr("script_errors").mapObj { it }
            errs.forEach { e -> Small("⚠ " + e.s("section") + ": " + e.s("error"), ex.warn) }
            j.sn("policy_at")?.let { Small("Policy snapshot".t() + " " + ago(it)) }
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle("Checks".t(), a.lastScanAt?.let { "scan".t() + " " + ago(it) })
        Panel { ChecksList(j.optJSONObject("checks")) }
        Spacer(Modifier.height(10.dp))
        val alerts = j.arr("alerts").mapObj(GAlert::of)
        SectionTitle("Open alerts".t(), alerts.size.toString())
        GAlertList(alerts, nav, "No open alerts".t(), showAccount = false)
        Spacer(Modifier.height(10.dp))
        val camps = j.arr("campaigns").mapObj { it }
        SectionTitle("Campaigns".t(), cur)
        Panel {
            if (camps.isEmpty()) Empty("No campaign data yet".t())
            camps.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        TextButton(onClick = { nav.navigate("gads/campaign/$cid/${c.s("campaign_id")}") }, contentPadding = PaddingValues(0.dp)) {
                            Text(c.s("name").ifBlank { c.s("campaign_id") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Small("today".t() + " " + money(c.dn("spend_today"), cur, 0) + " · " + "yesterday".t() + " " + money(c.dn("spend_yesterday"), cur, 0) + " · " +
                            tf("%s installs", num(c.dn("installs_yesterday"), 0)) + (if (c.s("prev_status").isNotBlank() && c.s("prev_status") != c.s("status")) " · " + "was".t() + " " + c.s("prev_status") else ""), maxLines = 2)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Pill(c.s("status").ifBlank { "?" }, gStateColor(c.s("status")))
                        if (c.s("alert_level").isNotBlank()) { Spacer(Modifier.height(3.dp)); Pill(c.s("alert_level"), levelColor(c.s("alert_level"))) }
                    }
                }
                if (i < camps.lastIndex) Hairline()
            }
        }
        Spacer(Modifier.height(10.dp))
        val emails = j.arr("emails").mapObj { it }
        SectionTitle("Google Ads emails".t())
        Panel {
            if (!a.srcEmail) Small("Email source is off for this account.".t())
            if (emails.isEmpty()) Empty("No Google Ads email for this account yet".t())
            emails.forEach { e ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(e.s("kind"), gStateColor(if (e.s("kind") == "REINSTATED") "OK" else if (e.s("kind") == "OTHER") "" else "WARNING"))
                        Spacer(Modifier.width(6.dp))
                        Pill(if (e.b("verified")) "verified".t() else "NOT verified".t(), if (e.b("verified")) ex.good else ex.warn)
                        if (e.b("sim")) { Spacer(Modifier.width(6.dp)); Pill("SIMULATED", ex.muted) }
                        Spacer(Modifier.weight(1f)); Small(ago(e.s("received_at")))
                    }
                    Text(e.s("subject"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Small(e.s("from_addr") + " · " + e.s("verify_note") + (if (e.s("reason").isNotBlank()) " · " + e.s("reason") else ""), maxLines = 2)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle("Account details (kept separate)".t())
        Panel {
            KV("Company".t(), a.company)
            KV("MCC (label only)".t(), a.mccLabel.ifBlank { "–" })
            KV("Invoice / billing ref".t(), a.invoiceRef.ifBlank { "–" })
            KV("Credit line ref".t(), a.creditRef.ifBlank { "–" })
            KV("Currency".t(), cur.ifBlank { "–" })
            KV("Time zone".t(), a.tz.ifBlank { j.s("tz_used") + " " + "(from Google / default)".t() })
            KV("Authorized by".t(), a.authorizedBy.ifBlank { "–" } + (a.authorizedAt?.let { " · " + fmtDay(it.take(10)) } ?: ""))
            if (a.notes.isNotBlank()) Small(a.notes)
            Hairline()
            SwitchLine("Monitoring".t(), null, a.monitoring, "tg${cid}monitoring" in g.busy) { v -> toggle(vm, cid, "monitoring", v) { if (it != null) token = it } }
            SwitchLine("Phone notifications".t(), null, a.push, "tg${cid}push" in g.busy) { v -> toggle(vm, cid, "push", v) { } }
            SwitchLine("Email alerts".t(), "critical only".t(), a.email, "tg${cid}email" in g.busy) { v -> toggle(vm, cid, "email", v) { } }
            SwitchLine("Existing dashboard data".t(), null, a.srcDashboard, "tg${cid}src_dashboard" in g.busy) { v -> toggle(vm, cid, "src_dashboard", v) { } }
            SwitchLine("Read-only Ads Script".t(), null, a.srcScript, "tg${cid}src_script" in g.busy) { v -> toggle(vm, cid, "src_script", v) { if (it != null) token = it } }
            SwitchLine("Google Ads emails".t(), null, a.srcEmail, "tg${cid}src_email" in g.busy) { v -> toggle(vm, cid, "src_email", v) { } }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { edit = true }, modifier = Modifier.weight(1f)) { Text("Edit".t()) }
                BusyButton("Check now".t(), "scan$cid" in g.busy, Modifier.weight(1f)) { vm.act("scan", mapOf("cid" to cid), busyKey = "scan$cid", ok = "Check started".t()) }
            }
        }
        Spacer(Modifier.height(10.dp))
        val ths = j.arr("thresholds").mapObj { it }
        SectionTitle("Thresholds for this account".t())
        Panel {
            if (ths.isEmpty()) Small("Using the global thresholds (Notification Settings).".t())
            ths.forEach { t -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(gText(t.s("key")) + " = " + t.s("value"), style = MaterialTheme.typography.bodySmall); Small(scopeText(t.s("scope"))) }
                TextButton(onClick = { th = Triple(t.s("scope"), t.s("key"), t.s("value")) }) { Text("Edit".t()) }
            } }
            TextButton(onClick = { th = Triple("acct:$cid", "cpi_target", "") }, contentPadding = PaddingValues(0.dp)) { Text("+ " + "Account CPI target".t()) }
        }
        ReadOnlyNote()
        if (edit) AccountSheet(a, a.cidFmt, a.accountName, g, vm, onSaved = { c, tok -> edit = false; vm.load("account", p); if (tok != null) token = tok }) { edit = false }
        if (newTok) AlertDialog(onDismissRequest = { newTok = false }, title = { Text("Make a new token?".t()) },
            text = { Text("The old token stops working now. Paste the new one into this account's script.".t()) },
            confirmButton = { TextButton(onClick = { newTok = false; vm.act("token", mapOf("cid" to cid)) { r -> r.optString("token").takeIf { it.isNotBlank() }?.let { token = it } } }) { Text("New token".t()) } },
            dismissButton = { TextButton(onClick = { newTok = false }) { Text("Cancel".t()) } })
    }
    token?.let { TokenDialog(cid, it, vm, g) { token = null; vm.load("account", p) } }
    th?.let { (s, k, v) -> ThresholdDialog("Threshold".t(), s, k, v, "", vm, { vm.load("account", p) }) { th = null } }
}

private fun toggle(vm: GadsVm, cid: String, field: String, on: Boolean, onToken: (String?) -> Unit) =
    vm.act("account_toggle", mapOf("cid" to cid, "field" to field, "value" to if (on) "1" else "0"), busyKey = "tg$cid$field") { j ->
        vm.load("account", mapOf("cid" to cid)); vm.load("settings")
        onToken(j.optString("token").takeIf { it.isNotBlank() && it != "null" })
    }

// ---------------------------------------------------------------- campaign detail

@Composable
fun GadsCampaignScreen(vm: GadsVm, nav: NavHostController, cid: String, camp: String) {
    val g by vm.s.collectAsStateWithLifecycle()
    var reload by remember { mutableIntStateOf(0) }
    var range by remember { mutableStateOf("7d") }
    val p = mapOf("cid" to cid, "campaign_id" to camp, "range" to range)
    Poll(60_000, p, reload) { vm.load("campaign", p) }
    var th by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    val j0 = g.view("campaign", p)
    GFrame(j0?.obj("campaign")?.s("name")?.ifBlank { null } ?: ("Campaign".t() + " " + camp), (j0?.obj("account")?.s("company") ?: "") + " · CID " + cid, g, nav, { reload++ }) {
        GLoad(g, j0, { vm.load("campaign", p) }) { j ->
            val acc = GAccount.of(j.obj("account"))
            val cur = acc.currency
            val c = j.obj("campaign")
            val m = c.optJSONObject("meta")
            val ex = MaterialTheme.extra
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(c.s("status").ifBlank { "?" }, gStateColor(c.s("status")))
                Spacer(Modifier.width(6.dp))
                Small(listOfNotNull(m?.s("channel")?.ifBlank { null }?.let { gText(it) }, m?.s("bidding")?.ifBlank { null }?.let { gText(it) }, c.s("pkg").ifBlank { null }).joinToString(" · "), maxLines = 1)
            }
            if (m != null) {
                Small(listOfNotNull(m.dn("budget")?.let { "budget".t() + " " + money(it, cur, 0) }, m.dn("target_cpa")?.let { "Google tCPA " + money(it, cur) },
                    m.dn("target_roas")?.let { "Google tROAS " + num(it * 100, 0) + "%" }, m.s("primary_status").ifBlank { null }?.let { gText(it) }).joinToString(" · "))
                m.s("primary_reasons").ifBlank { null }?.let { Small(it.replace(',', ' ').replace('_', ' ').lowercase()) }
            }
            if (!j.b("fresh")) Small("STALE DATA".t(), ex.bad)
            Spacer(Modifier.height(8.dp))
            SectionTitle("Today vs history".t(), j.sn("reported_through")?.let { tf("reported up to %s", it) })
            Panel { WindowsTable(j.obj("windows"), cur, "spend"); Spacer(Modifier.height(6.dp)); WindowsTable(j.obj("windows"), cur, "cpi") }
            Small("Today counts only hours Google has reported (reporting lag). Installs arrive late, so today's CPI is not judged.".t())
            Spacer(Modifier.height(10.dp))
            val hn = j.arr("hours_today"); val hu = j.arr("hours_usual")
            if (hu.length() == 24) {
                SectionTitle("Spend by hour (today vs usual)".t(), cur)
                Panel {
                    val maxV = (0 until 24).maxOf { maxOf(hn.optDouble(it, 0.0).let { v -> if (v.isNaN()) 0.0 else v }, hu.optDouble(it, 0.0).let { v -> if (v.isNaN()) 0.0 else v }) }.coerceAtLeast(1.0)
                    HBars((0 until 24).filter { !hu.isNull(it) || !hn.isNull(it) }.map { h ->
                        val now = if (hn.isNull(h)) null else hn.optDouble(h)
                        Triple(String.format("%02d:00", h), (now?.let { num(it, 0) } ?: "·") + " / " + num(hu.optDouble(h, 0.0), 0), (now ?: 0.0) / maxV)
                    })
                    Small("bar = today · text: today / usual (median of comparable days)".t())
                }
                Spacer(Modifier.height(10.dp))
            }
            SegRange(range) { range = it }
            val rr = j.arr("range")
            SectionTitle("Ad groups".t(), rr.optString(0) + " – " + rr.optString(1))
            Panel {
                val ags = j.arr("ad_groups").mapObj { it }
                if (ags.isEmpty()) Small(j.obj("sources_note").s("ad_groups").ifBlank { "No ad group data".t() })
                else Table(listOf("Ad group".t(), "Spend".t(), "Installs".t(), "CPI", "CTR"), ags.map { a ->
                    listOf(a.s("name").ifBlank { a.s("ad_group_id") }, num(a.dn("cost"), 0), num(a.dn("installs"), 0), a.dn("cpi")?.let { num(it, 2) } ?: "–", pct(a.dn("ctr"), 2))
                }, firstWeight = 2f)
            }
            Spacer(Modifier.height(10.dp))
            val ccT = j.obj("country_targets")
            SectionTitle("Countries".t(), cur)
            Panel {
                val geo = j.arr("countries").mapObj { it }
                if (geo.isEmpty()) Small(j.obj("sources_note").s("countries").ifBlank { "No country data".t() })
                else Table(listOf("Country".t(), "Spend".t(), "Installs".t(), "CPI", "Target".t()), geo.map { r ->
                    listOf(r.s("country"), num(r.dn("cost"), 0), num(r.dn("installs"), 0), r.dn("cpi")?.let { num(it, 2) } ?: "–", ccT.dn(r.s("country"))?.let { num(it, 2) } ?: "–")
                }, firstWeight = 1.2f, onRow = { i -> val cc = geo[i].s("country"); if (cc.length == 2) th = Triple("country:$cid:$cc", "cpi_target", ccT.dn(cc)?.toString() ?: "") })
                Small("Tap a country to set its CPI target.".t())
            }
            Spacer(Modifier.height(10.dp))
            SectionTitle("Thresholds for this campaign".t())
            Panel {
                val t = j.obj("thresholds")
                listOf("cpi_target", "roas_target", "drop_pct", "spike_pct", "cpi_over_pct", "zero_hours", "min_installs").forEach { k ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(gText(k), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(t.dn(k)?.let { num(it, 2) } ?: "–", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        TextButton(onClick = { th = Triple("camp:$cid:$camp", k, "") }) { Text("Set".t()) }
                    }
                }
                Small("Campaign values override account and global values. Google's own tCPA / tROAS are used when no CPI / ROAS target is set here.".t())
            }
            Spacer(Modifier.height(10.dp))
            val policy = j.arr("policy").mapObj { it }
            if (policy.isNotEmpty()) {
                SectionTitle("Ad policy".t())
                Panel { policy.forEach { pl -> Row(Modifier.padding(vertical = 3.dp)) {
                    Pill(pl.s("approval"), gStateColor(pl.s("approval"))); Spacer(Modifier.width(8.dp))
                    Small("ad ".t() + pl.s("ad_id") + " · " + pl.s("topics"), maxLines = 3)
                } } }
                Spacer(Modifier.height(10.dp))
            }
            val assets = j.arr("assets").mapObj { it }
            if (assets.isNotEmpty()) {
                SectionTitle("Creatives".t(), "Google labels, no asset CPI".t())
                Panel { assets.take(30).forEach { a -> Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(a.s("label").ifBlank { "–" }, gStateColor(a.s("label"))); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.s("name").ifBlank { a.s("url") }.ifBlank { a.s("asset_id") }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Small(gText(a.s("field_type")) + " · " + short(a.dn("impressions")) + " impr.", maxLines = 1)
                    }
                } } }
                Spacer(Modifier.height(10.dp))
            }
            val trend = j.arr("trend").mapObj { it }.filter { it.b("has_data") }
            SectionTitle("Last 30 days".t(), cur)
            Panel {
                Table(listOf("Day".t(), "Spend".t(), "Installs".t(), "CPI", "ROAS"), trend.map { d ->
                    listOf(fmtDay(d.s("day")), num(d.dn("cost"), 0), num(d.dn("installs"), 0), d.dn("cpi")?.let { num(it, 2) } ?: "–", d.dn("roas")?.let { num(it * 100, 0) + "%" } ?: "–")
                }, empty = "No daily data".t())
            }
            Spacer(Modifier.height(10.dp))
            val alerts = j.arr("alerts").mapObj(GAlert::of)
            SectionTitle("Alerts (30 days)".t())
            GAlertList(alerts, nav, "No alerts for this campaign".t(), showAccount = false)
            ReadOnlyNote()
        }
    }
    th?.let { (s, k, v) -> ThresholdDialog("Threshold".t(), s, k, v, "", vm, { vm.load("campaign", p) }) { th = null } }
}

@Composable
private fun SegRange(sel: String, onSel: (String) -> Unit) {
    ChipRow { listOf("today" to "Today", "yesterday" to "Yesterday", "7d" to "7 days", "14d" to "14 days", "30d" to "30 days").forEach { (k, l) -> MChip(l.t(), sel == k, { onSel(k) }) } }
    Spacer(Modifier.height(8.dp))
}

// ---------------------------------------------------------------- run (audit log)

@Composable
fun GadsRunScreen(vm: GadsVm, nav: NavHostController, id: Long) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("id" to id.toString())
    var reload by remember { mutableIntStateOf(0) }
    val j0 = g.view("run", p)
    val running = j0?.obj("run")?.s("status") == "RUNNING"
    Poll(if (running) 5_000 else 60_000, id, reload, running) { vm.load("run", p) }
    GFrame("Run".t() + " #$id", null, g, nav, { reload++ }) {
        GLoad(g, j0, { vm.load("run", p) }) { j ->
            val r = GRun.of(j.obj("run"))
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(runType(r.type), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Pill(r.status, gStateColor(r.status))
                }
                KV("Run ID".t(), r.uid)
                KV("Trigger".t(), r.trigger + (if (r.by.isNotBlank()) " · " + r.by else ""))
                KV("Started".t(), tFull(r.startedAt))
                KV("Finished".t(), r.finishedAt?.let { tFull(it) } ?: "–")
                KV("Accounts checked".t(), r.accounts.toString())
                KV("Campaigns checked".t(), r.campaigns.toString())
                KV("Issues found".t(), r.issues.toString())
                KV("Recommendations".t(), r.recs.toString())
                KV("Failed checks".t(), r.failedChecks.toString(), valueColor = if (r.failedChecks > 0) MaterialTheme.extra.bad else null)
                KV("Next run".t(), r.nextRunAt?.let { tFull(it) } ?: "–")
                if (r.sim) Small("SIMULATED test run (development mode)".t())
                if (r.errors.isNotBlank()) { GLabel("Errors".t()); Small(r.errors, MaterialTheme.extra.bad) }
            }
            if (r.sources.length() > 0) {
                Spacer(Modifier.height(10.dp))
                SectionTitle("Data source status".t())
                Panel {
                    r.sources.mapObj { it }.forEach { s ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(listOfNotNull(s.sn("company"), s.sn("cid")?.let { "CID $it" }).joinToString(" · ").ifBlank { s.toString().take(80) }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Small(s.keys().asSequence().filter { it !in setOf("company", "cid") }.joinToString(" · ") { k -> gText(k) + ": " + s.opt(k).toString().take(60) }, maxLines = 4)
                        }
                    }
                }
            }
            r.summary?.optString("text")?.takeIf { it.isNotBlank() }?.let { t -> Spacer(Modifier.height(10.dp)); SectionTitle("Summary".t()); Mono(t) }
            Spacer(Modifier.height(10.dp))
            SectionTitle("Log".t())
            Panel {
                val ev = j.arr("events").mapObj(GEvent::of)
                if (ev.isEmpty()) Empty("Nothing logged".t())
                ev.forEach { GEventRow(it, nav) }
            }
        }
    }
}

// ---------------------------------------------------------------- L. Notification Settings

private val TH_HELP = mapOf(
    "drop_pct" to "Spend drop: % below the usual pace for this time of day", "spike_pct" to "Spend spike: % above the usual pace",
    "cpi_over_pct" to "CPI alert: % above the CPI target", "zero_hours" to "Zero spend for this many reported hours (only campaigns expected to spend)",
    "min_impr_pace" to "Minimum usual impressions before pace alerts", "min_spend_pace" to "Minimum usual spend before pace alerts",
    "min_installs" to "Minimum installs before CPI is judged", "min_baseline_days" to "Comparable past days needed",
    "stale_hours" to "Data older than this = STALE DATA", "spend_lag_hours" to "Google spend reporting lag (hours)", "conv_lag_hours" to "Install / conversion reporting lag (hours)",
    "roas_under_pct" to "ROAS alert: % below the ROAS target", "install_drop_pct" to "Installs drop alert (%)", "learning_days" to "New campaign learning period (days)",
    "minor_pct" to "Smaller change shown as INFO (%)", "cpi_target" to "Default CPI target (account currency)", "roas_target" to "Default ROAS target (1.0 = 100%)",
)

@Composable
fun SettingsTab(g: GState, vm: GadsVm, nav: NavHostController, reload: Int, onToken: (String, String) -> Unit) {
    Poll(60_000, reload) { vm.load("settings") }
    var th by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var thDef by remember { mutableStateOf("") }
    var num0 by remember { mutableStateOf<Pair<String, String>?>(null) }
    var simCid by remember { mutableStateOf("") }
    GLoad(g, g.view("settings"), { vm.load("settings") }) { j ->
        val ex = MaterialTheme.extra
        val set = j.obj("settings")
        val setOn = { k: String -> set.s(k) == "1" }
        val save = { k: String, v: String -> vm.act("setting", mapOf("key" to k, "value" to v), busyKey = "set$k", ok = "Saved".t()) { vm.load("settings") } }
        SectionTitle("Notifications".t())
        Panel {
            SwitchLine("Phone notifications".t(), "Critical and warning alerts, once per problem (no repeats)".t(), setOn("ga_push"), "setga_push" in g.busy) { save("ga_push", if (it) "1" else "0") }
            SwitchLine("Email for critical alerts".t(), j.obj("email_out").let { e -> if (e.b("configured")) "to".t() + " " + e.s("to") else "email not configured on the server".t() }, setOn("ga_mail_critical"), "setga_mail_critical" in g.busy) {
                save("ga_mail_critical", if (it) "1" else "0")
            }
            SwitchLine("Daily summary email".t(), tf("every day at %s:00 (server time)", set.s("ga_daily_hour")), setOn("ga_mail_daily"), "setga_mail_daily" in g.busy) { save("ga_mail_daily", if (it) "1" else "0") }
            TextButton(onClick = { num0 = "ga_daily_hour" to set.s("ga_daily_hour") }, contentPadding = PaddingValues(0.dp)) { Text("Change daily summary hour".t()) }
            SwitchLine("AI explanations".t(), j.obj("ai").let { a -> tf("%s / %s AI calls today", a.d("used_today").toInt(), a.d("cap").toInt()) + (a.sn("last_error")?.let { " · " + it.take(60) } ?: "") },
                setOn("ga_ai_on"), "setga_ai_on" in g.busy) { save("ga_ai_on", if (it) "1" else "0") }
            TextButton(onClick = { num0 = "ga_ai_cap" to set.s("ga_ai_cap") }, contentPadding = PaddingValues(0.dp)) { Text("Change daily AI limit".t()) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BusyButton("Send test".t(), "test_notify" in g.busy, Modifier.weight(1f), outlined = true) {
                    vm.act("test_notify", ok = "Test sent: check your notifications".t()) { vm.load("settings") }
                }
                BusyButton("Daily summary now".t(), "daily_now" in g.busy, Modifier.weight(1f), outlined = true) { vm.act("daily_now", ok = "Started".t()) }
            }
            Small("Test puts a [TEST] alert on this phone (and an email if email is configured).".t())
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle("Google Ads email intake".t())
        Panel {
            val e = j.obj("email_in")
            KV("Mailbox".t(), if (e.b("configured")) e.s("mailbox") else "not configured".t(), valueColor = if (e.b("configured")) null else ex.warn)
            KV("Last read".t(), e.sn("last_ok")?.let { tFull(it) } ?: "–")
            if (e.s("error").isNotBlank()) Small(e.s("error"), ex.bad)
            Small("Read-only (IMAP EXAMINE). Only emails from google.com with DKIM/DMARC pass count as verified. Set imap in gads_config.php.".t())
            BusyButton("Check email now".t(), "mail_check" in g.busy, Modifier.fillMaxWidth().padding(top = 6.dp), enabled = e.b("configured"), outlined = true) { vm.act("mail_check", ok = "Started".t()) }
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle("Thresholds (all accounts)".t(), "tap to change".t())
        Panel {
            j.arr("thresholds").mapObj { it }.forEachIndexed { i, t ->
                val k = t.s("key")
                val v = t.dn("value"); val d = t.dn("default")
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(gText(k), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Small((TH_HELP[k] ?: "").t())
                    }
                    Text(v?.let { num(it, 2) } ?: (d?.let { num(it, 2) + " " + "(default)".t() } ?: "–"), style = MaterialTheme.typography.bodySmall,
                        color = if (v != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { thDef = d?.let { num(it, 2) } ?: "–"; th = Triple("global", k, v?.let { num(it, 2) } ?: "") }) { Text("Edit".t()) }
                }
                if (i < j.arr("thresholds").length() - 1) Hairline()
            }
        }
        val over = j.arr("overrides").mapObj { it }
        if (over.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionTitle("Account / campaign / country values".t())
            Panel {
                over.forEach { o -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(gText(o.s("key")) + " = " + o.s("value"), style = MaterialTheme.typography.bodySmall); Small(scopeText(o.s("scope"))) }
                    TextButton(onClick = { thDef = ""; th = Triple(o.s("scope"), o.s("key"), o.s("value")) }) { Text("Edit".t()) }
                } }
            }
        }
        Spacer(Modifier.height(10.dp))
        val accs = j.arr("accounts").mapObj(GAccount::of)
        SectionTitle("Accounts".t(), tf("%s accounts", accs.size))
        Panel {
            if (accs.isEmpty()) Empty("No account yet. Use + at the top.".t())
            accs.forEachIndexed { i, a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        TextButton(onClick = { nav.navigate("gads/account/${a.cid}") }, contentPadding = PaddingValues(0.dp)) { Text(a.company + " · " + a.cidFmt, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Small(listOfNotNull(a.currency.ifBlank { null }, if (a.srcDashboard) "dashboard".t() else null, if (a.srcScript) "script".t() else null, if (a.srcEmail) "email".t() else null).joinToString(" · "))
                    }
                    Switch(a.monitoring, { v -> toggle(vm, a.cid, "monitoring", v) { } })
                }
                if (i < accs.lastIndex) Hairline()
            }
            Small("Each account is monitored on its own. Accounts and MCCs are never linked.".t())
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle("Setup".t())
        Panel {
            val s = j.obj("setup")
            KV("Cron (every 5 min)".t(), if (s.b("cron_ok")) "running".t() + " · " + ago(s.sn("cron_last")) else "NOT running".t(), valueColor = if (s.b("cron_ok")) ex.good else ex.bad)
            KV("Script data URL".t(), "")
            Mono(j.s("ingest_url"), maxLines = 2)
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle("Development mode".t())
        Panel {
            SwitchLine("Development mode".t(), "Allows SIMULATED test events. Keep off in normal use.".t(), setOn("ga_dev_mode"), "setga_dev_mode" in g.busy) { save("ga_dev_mode", if (it) "1" else "0") }
            if (setOn("ga_dev_mode")) {
                Small("Simulated alerts are marked [SIMULATED], never touch Google Ads, and can be cleared any time.".t(), ex.warn)
                if (accs.isNotEmpty()) {
                    val sel = simCid.ifBlank { accs.first().cid }
                    GLabel("Account".t())
                    ChipRow { accs.forEach { a -> MChip(a.company.take(18) + " · " + a.cidFmt.takeLast(4), sel == a.cid, { simCid = a.cid }) } }
                    GLabel("Simulate".t())
                    ChipRow {
                        listOf("spend_drop" to "Spend drop", "spend_spike" to "Spend spike", "zero_spend" to "Zero spend", "stale" to "Missing data", "paused" to "Campaign stopped", "suspended_email" to "Suspension email").forEach { (k, l) ->
                            MChip(l.t(), false, { vm.act("simulate", mapOf("cid" to sel, "rule" to k), busyKey = "sim$k", ok = "Simulated alert created".t()) { vm.load("settings") } })
                        }
                    }
                }
                BusyButton("Clear simulated alerts".t(), "sim_clear" in g.busy, Modifier.fillMaxWidth().padding(top = 6.dp), outlined = true) { vm.act("sim_clear", ok = "Cleared".t()) }
            }
        }
        ReadOnlyNote()
    }
    th?.let { (s, k, v) -> ThresholdDialog("Threshold".t(), s, k, v, thDef, vm, { vm.load("settings") }) { th = null } }
    num0?.let { (k, v) ->
        var t by remember(k) { mutableStateOf(v) }
        AlertDialog(onDismissRequest = { num0 = null }, title = { Text(if (k == "ga_daily_hour") "Daily summary hour (0-23)".t() else "AI calls per day (1-500)".t()) },
            text = { OutlinedTextField(t, { t = it.filter { c -> c.isDigit() }.take(3) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.act("setting", mapOf("key" to k, "value" to t), busyKey = "set$k", ok = "Saved".t()) { vm.load("settings") }; num0 = null }) { Text("Save".t()) } },
            dismissButton = { TextButton(onClick = { num0 = null }) { Text("Cancel".t()) } })
    }
}
