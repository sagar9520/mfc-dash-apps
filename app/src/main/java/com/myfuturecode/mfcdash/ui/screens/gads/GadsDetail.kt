package com.myfuturecode.mfcdash.ui.screens.gads

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.GAccount
import com.myfuturecode.mfcdash.data.GAlert
import com.myfuturecode.mfcdash.data.GEvent
import com.myfuturecode.mfcdash.data.GRun
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.GadsVm
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KDivider
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KPaceMeter
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSmallAction
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KSwitch
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kRoasColor
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.mapObj
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.obj
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.strings
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs
import org.json.JSONObject
import java.util.Locale

// ---------------------------------------------------------------- alert detail (AI root cause)

@Composable
fun GadsAlertScreen(vm: GadsVm, nav: NavHostController, id: Long) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("id" to id.toString())
    var reload by remember { mutableIntStateOf(0) }
    val j = g.view("alert", p)
    var askedAt by remember { mutableLongStateOf(0L) }
    // AI server par background me chalta hai (~30 sec): tab tak jaldi-jaldi dekho
    val aiRunning = askedAt > 0 && System.currentTimeMillis() - askedAt < 120_000 &&
        j?.obj("alert")?.sn("ai_at").let { it == null || (tsMs(it) ?: 0L) < askedAt - 5_000 }
    gPoll(if (aiRunning) 5_000L else 30_000L, id, reload, aiRunning) { vm.load("alert", p) }
    val a0 = j?.optJSONObject("alert")?.let { GAlert.of(it) }
    val retry = { vm.load("alert", p) }
    val onAsk = { askedAt = System.currentTimeMillis() }
    val bar: @Composable ColumnScope.() -> Unit = {
        KDetailBar("Alert".t() + " #$id", gBack(nav), sub = a0?.company?.ifBlank { null } ?: updatedText(g), actions = {
            KBarIcon(KIcons.Refresh, "Refresh".t(), { reload++ })
        })
    }
    if (isWide()) {
        KScreen(top = bar) {
            GLoad(g, j, retry) { r ->
                val a = GAlert.of(r.obj("alert"))
                KTwoPane(
                    left = { AlertHero(a); AlertMain(r, a, g, vm, id, aiRunning, onAsk) },
                    right = { AlertSide(r, a, nav) },
                )
            }
        }
    } else {
        KScreen(top = { bar(); if (a0 != null) AlertHero(a0) }) {
            GLoad(g, j, retry) { r ->
                val a = GAlert.of(r.obj("alert"))
                AlertMain(r, a, g, vm, id, aiRunning, onAsk)
                AlertSide(r, a, nav)
            }
        }
    }
}

/** Hero: kaunsa account, alert ka title (bada), kya hua, level ka pill. */
@Composable
private fun AlertHero(a: GAlert) {
    KHero(KTint.Sand) {
        val c = Mfc.c
        val k = if (a.open) gLevelKind(a.level) else KKind.OK
        KHeroHead(a.company.ifBlank { "CID " + a.cidFmt }, icon = KIcons.Ads, trailing = { KPill(if (a.open) gLevelWord(a.level) else "Closed".t(), k) })
        GTxt(a.titleL, 22.sp, c.ink, Modifier.padding(top = 8.dp), weight = FontWeight.Bold, lh = 28.sp, maxLines = 4)
        if (a.whatL.isNotBlank()) GTxt(a.whatL, 15.sp, c.ink2, Modifier.padding(top = 6.dp), lh = 21.sp, maxLines = 8)
        val meta = listOfNotNull(gText(a.klass).ifBlank { null }, if (a.sim) "Simulated".t() else null, a.campaign.ifBlank { null }, a.country.ifBlank { null })
        if (meta.isNotEmpty()) KStatus(meta.joinToString(" · "), gStateKind(a.klass), Modifier.padding(top = 8.dp))
    }
}

/** Kya karna hai: Acknowledge / Close, agla kadam, AI root cause. */
@Composable
private fun AlertMain(r: JSONObject, a: GAlert, g: GState, vm: GadsVm, id: Long, aiRunning: Boolean, onAsk: () -> Unit) {
    val c = Mfc.c
    val p = mapOf("id" to id.toString())
    if (a.open) {
        if (a.ackAt == null || a.closable) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (a.ackAt == null) KButton(
                "Acknowledge".t(),
                { vm.act("ack", p, busyKey = "ack$id", ok = "No more notifications for this alert".t()) { vm.load("alert", p) } },
                Modifier.weight(1f), kind = KBtn.SOFT, icon = KIcons.Check, busy = "ack$id" in g.busy,
            )
            if (a.closable) KButton(
                "Close".t(), { vm.act("close", p, busyKey = "close$id", ok = "Closed".t()) { vm.load("alert", p) } },
                Modifier.weight(1f), kind = KBtn.OUTLINE, icon = KIcons.Close, busy = "close$id" in g.busy,
            )
        }
        if (!a.closable) GTxt("Closes by itself".t(), 13.sp, c.ink3, Modifier.fillMaxWidth(), lh = 18.sp, maxLines = 1, align = TextAlign.Center)
    }
    KSection("Next step".t())
    KCard {
        GTxt(a.nextL.ifBlank { "–" }, 16.sp, c.ink, weight = FontWeight.SemiBold, lh = 22.sp)
        if (a.causes.length() > 0) {
            GLabel("Possible causes".t())
            for (i in 0 until a.causes.length()) GStep(a.causes.optString(i))
        }
    }
    val aiAt = a.aiAt
    KSection("AI root cause".t(), trailing = if (aiAt != null) { { GTxt(gWhen(aiAt), 13.sp, c.ink3, lh = 18.sp, maxLines = 1) } } else null)
    KCard {
        val ai = a.ai
        if (ai != null) {
            val cls = ai.s("classification")
            val conf = a.aiConf
            if (cls.isNotBlank() || conf != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cls.isNotBlank()) KPill(gText(cls), gStateKind(cls.substringBefore(' ')))
                Spacer(Modifier.weight(1f))
                if (conf != null) GTxt(tf("%s%% confidence", conf), 13.sp, c.brand, weight = FontWeight.SemiBold, lh = 18.sp, maxLines = 1)
            }
            val sum = if (Lang.hi) ai.s("summary_hi").ifBlank { ai.s("summary") } else ai.s("summary")
            if (sum.isNotBlank()) AiBox("AI summary".t(), sum)
            val causes = ai.arr("causes").mapObj { it }
            if (causes.isNotEmpty()) GLabel("Likely causes".t())
            causes.forEach { cs ->
                val lk = cs.s("likelihood").lowercase()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KPill(
                        when (lk) {
                            "high" -> "High".t()
                            "medium" -> "Medium".t()
                            "low" -> "Low".t()
                            "" -> "–"
                            else -> gText(lk)
                        },
                        when (lk) {
                            "high" -> KKind.BAD
                            "medium" -> KKind.WARN
                            else -> KKind.NEUTRAL
                        },
                    )
                    Column(Modifier.weight(1f)) {
                        GTxt(cs.s("cause"), 14.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
                        if (cs.s("evidence").isNotBlank()) GTxt(cs.s("evidence"), 13.sp, c.ink2, lh = 18.sp)
                    }
                }
            }
            val acts = (if (Lang.hi) ai.arr("manual_actions_hi").strings() else emptyList()).ifEmpty { ai.arr("manual_actions").strings() }
            if (acts.isNotEmpty()) GLabel("What to do".t())
            acts.forEachIndexed { i, s -> GTxt("${i + 1}. $s", 14.sp, c.ink, Modifier.padding(top = 4.dp), lh = 20.sp) }
            ai.sn("business_impact")?.let { GLabel("Business impact".t()); Small(it) }
            ai.sn("creative_suggestion")?.let { AiBox("Creative idea".t(), it) }
        } else {
            if (a.aiErr.isNotBlank()) GNote("AI failed".t() + ": " + a.aiErr, KKind.BAD) else KEmpty("No AI yet".t(), KIcons.Spark)
        }
        if (aiRunning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            GTxt("AI is checking…".t(), 13.sp, c.ink2, Modifier.padding(top = 4.dp), lh = 18.sp, maxLines = 1)
        }
        if (r.b("ai_on")) KButton(
            if (ai == null) "Ask AI now".t() else "Ask AI again".t(),
            { vm.act("ai", p, busyKey = "ai$id", ok = "AI is checking (about 30 sec)".t()) { onAsk(); vm.load("alert", p) } },
            Modifier.fillMaxWidth().padding(top = 12.dp), kind = if (ai == null) KBtn.PRIMARY else KBtn.SOFT, icon = KIcons.Spark,
            busy = "ai$id" in g.busy || aiRunning,
        )
        else KStatus("AI key missing".t(), KKind.WARN, Modifier.padding(top = 10.dp))
    }
}

/** Numbers, asar, poori jankari (kahan / kab / saboot), links, log. */
@Composable
private fun AlertSide(r: JSONObject, a: GAlert, nav: NavHostController) {
    val c = Mfc.c
    val cur = a.currency
    KSection("Numbers".t())
    KCard { MetricsList(a.metrics, cur) }
    if (a.impact.isNotBlank()) {
        KSection("Business impact".t())
        KCard { GTxt(a.impact, 15.sp, c.ink, lh = 21.sp) }
    }
    KSection("Details".t())
    KCard {
        GLabel("Where".t())
        GKv("Company".t(), a.company.ifBlank { "–" })
        GKv("Customer ID".t(), a.cidFmt)
        val mcc = r.optJSONObject("account")?.let { GAccount.of(it).mccLabel }.orEmpty()
        if (mcc.isNotBlank()) GKv("MCC (label)".t(), mcc)
        if (a.campaign.isNotBlank() || a.campaignId.isNotBlank()) GKv("Campaign".t(), a.campaign.ifBlank { a.campaignId })
        if (a.adGroupId.isNotBlank()) GKv("Ad group".t(), a.adGroupId)
        if (a.country.isNotBlank()) GKv("Country".t(), a.country)
        GKv("Currency".t(), cur.ifBlank { "–" })
        KDivider()
        GLabel("When".t())
        GKv("First seen".t(), tFull(a.firstSeen))
        GKv("Last checked".t(), tFull(a.lastSeen))
        a.notifiedAt?.let { GKv("Notified".t(), tFull(it)) }
        a.ackAt?.let { GKv("Acknowledged".t(), tFull(it)) }
        a.resolvedAt?.let { GKv("Closed".t(), tFull(it) + (if (a.resolvedBy.isNotBlank()) " · " + a.resolvedBy else "")) }
        KDivider()
        GLabel("Evidence".t())
        GKv("Source".t(), a.evidence.ifBlank { "–" })
        GKv("Data freshness".t(), a.freshnessMin?.let { ageMin(it) } ?: "–")
        GKv("Classification".t(), gText(a.klass).ifBlank { "–" }, gStateColor(a.klass))
    }
    KCard(list = true) {
        KRow(
            "Account".t(), sub = a.company.ifBlank { a.cidFmt }, lead = { KAvatar(a.company.ifBlank { a.cid }) }, chevron = true,
            onClick = { nav.navigate("gads/account/${a.cid}") },
        )
        if (a.campaignId.isNotBlank() && !a.sim) {
            KListDivider(lead = true)
            KRow(
                "Campaign".t(), sub = a.campaign.ifBlank { a.campaignId }, lead = { KIconTile(KIcons.Ads) }, chevron = true,
                onClick = { nav.navigate("gads/campaign/${a.cid}/${a.campaignId}") },
            )
        }
    }
    val ev = r.arr("events").mapObj(GEvent::of)
    if (ev.isNotEmpty()) {
        KSection("Log".t())
        KCard { ev.forEach { GEventRow(it, null) } }
    }
    ReadOnlyNote()
}

// ---------------------------------------------------------------- account detail

@Composable
fun GadsAccountScreen(vm: GadsVm, nav: NavHostController, cid: String) {
    val g by vm.s.collectAsStateWithLifecycle()
    var reload by remember { mutableIntStateOf(0) }
    val p = mapOf("cid" to cid)
    gPoll(60_000L, cid, reload) { vm.load("account", p) }
    val j = g.view("account", p)
    val acc = j?.optJSONObject("account")
    val name = acc?.s("company")?.ifBlank { null } ?: ("CID " + cid)
    val wide = isWide()
    val usdInr = gUsdInr()
    val bar: @Composable ColumnScope.() -> Unit = {
        KDetailBar(name, gBack(nav), sub = "CID " + (acc?.s("cid_fmt")?.ifBlank { null } ?: cid), lead = { KAvatar(name, size = 36.dp) }, actions = {
            KBarIcon(KIcons.Refresh, "Refresh".t(), { reload++ })
        })
    }
    KScreen(top = { bar(); if (!wide && j != null) KHero(KTint.Sand) { AcctSummary(j, g, usdInr) } }) {
        AccountContent(g, vm, nav, cid, wide = wide, heroHere = wide, usdInr = usdInr)
    }
}

/** Health tab me ek account chuna ho to (bina apne bar ke). Upar Google Ads ka hero hai, isliye summary sada card me. */
@Composable
fun AccountBody(g: GState, vm: GadsVm, nav: NavHostController, cid: String, reload: Int) {
    gPoll(60_000L, cid, reload) { vm.load("account", mapOf("cid" to cid)) }
    AccountContent(g, vm, nav, cid, wide = false, heroHere = true, usdInr = gUsdInr(), asCard = true)
}

/** Account ka poora page (hero ke bina ya saath). Fold: baayein hero + alerts / campaigns, daayein sources / settings. */
@Composable
private fun AccountContent(
    g: GState, vm: GadsVm, nav: NavHostController, cid: String, wide: Boolean, heroHere: Boolean, usdInr: Double?, asCard: Boolean = false,
) {
    val p = mapOf("cid" to cid)
    var edit by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<String?>(null) }
    var newTok by remember { mutableStateOf(false) }
    var th by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    GLoad(g, g.view("account", p), { vm.load("account", p) }) { j ->
        val a = GAccount.of(j.obj("account"))
        val side: @Composable () -> Unit = {
            AcctSide(j, a, g, vm, cid, onEdit = { edit = true }, onNewTok = { newTok = true }, onToken = { token = it }, onTh = { th = it })
        }
        // ek screen, ek hero: Health tab ke andar summary card me (naam ke saath)
        val head: @Composable () -> Unit = {
            if (asCard) KCard { AcctSummary(j, g, usdInr, a.company.ifBlank { a.cidFmt } + " · " + "Spend today".t()) }
            else KHero(KTint.Sand) { AcctSummary(j, g, usdInr) }
        }
        if (wide) {
            KTwoPane(
                left = {
                    if (heroHere) head()
                    AcctMain(j, a, nav, cid)
                },
                right = { side() },
            )
        } else {
            if (heroHere) head()
            AcctMain(j, a, nav, cid)
            side()
        }
        if (edit) AccountSheet(a, a.cidFmt, a.accountName, g, vm, onSaved = { _, tok -> edit = false; vm.load("account", p); if (tok != null) token = tok }) { edit = false }
        if (newTok) AlertDialog(
            onDismissRequest = { newTok = false },
            title = { Text("Make a new token?".t()) },
            text = { Text("Old token stops now".t()) },
            confirmButton = {
                TextButton(onClick = {
                    newTok = false
                    vm.act("token", mapOf("cid" to cid)) { r -> r.optString("token").takeIf { it.isNotBlank() && it != "null" }?.let { token = it } }
                }) { Text("New token".t()) }
            },
            dismissButton = { TextButton(onClick = { newTok = false }) { Text("Cancel".t()) } },
        )
    }
    token?.let { TokenDialog(cid, it, vm, g) { token = null; vm.load("account", p) } }
    th?.let { (s, k, v) -> ThresholdDialog("Threshold".t(), s, k, v, "", vm, { vm.load("account", p) }) { th = null } }
}

/** Hero ke andar: aaj ka kharcha (apni currency, ≈ ₹), aam / data kitna purana, raftaar meter. */
@Composable
private fun AcctSummary(j: JSONObject, g: GState, usdInr: Double?, head: String = "Spend today".t()) {
    val c = Mfc.c
    val a = GAccount.of(j.obj("account"))
    val cur = a.currency
    val open = j.arr("alerts").mapObj(GAlert::of).filter { !it.sim && it.open }
    val crit = open.count { it.level.equals("CRITICAL", ignoreCase = true) }
    val warn = open.count { it.level.equals("WARNING", ignoreCase = true) }
    val (gd, gs) = gPaceLimits(g)
    // sirf is account ki limit (campaign / desh wali nahi)
    val ths = j.arr("thresholds").mapObj { it }.filter { it.s("scope") == "acct:" + a.cid }
    val drop = ths.firstOrNull { it.s("key") == "drop_pct" }?.dn("value")?.takeIf { it > 0 } ?: gd
    val spike = ths.firstOrNull { it.s("key") == "spike_pct" }?.dn("value")?.takeIf { it > 0 } ?: gs
    val age = j.dn("data_age_min")?.toInt()
    val fresh = j.b("fresh")
    val pace = j.dn("pace_pct")
    val (k, word) = gAcctState(a.monitoring, a.health, age, fresh, crit, warn, pace, drop, spike)
    KHeroHead(head, icon = KIcons.Ads)
    val sp = j.dn("spend_today")
    val inr = if (sp != null && !gIsInr(cur)) gToInr(sp, cur, usdInr) else null
    KBigNum(gCash(sp, cur), suffix = inr?.let { " ≈ " + gCash(it, "INR", 0) }, size = 48.sp)
    // haal number ke neeche: head me lamba pill "Aaj ka kharcha" ko kaat deta
    Row(Modifier.padding(top = 6.dp)) { KPill(word, k) }
    if (a.lastState.isNotBlank()) GTxt(a.lastState, 14.sp, c.ink2, Modifier.padding(top = 2.dp), lh = 20.sp, maxLines = 2)
    KSplit(
        listOf("Usual by now".t() to gCash(j.dn("usual_by_now"), cur), "Data".t() to (if (age == null) "No data".t() else gAgeShort(age))),
        listOf(null, if (age == null) c.red else if (!fresh) c.amber else null),
    )
    KPaceMeter(pace, -drop, spike)
    j.sn("reported_through")?.let { rt ->
        GTxt(
            listOfNotNull(tf("Google till %s", rt), j.s("tz_used").ifBlank { null }).joinToString(" · "),
            13.sp, c.ink3, Modifier.padding(top = 8.dp), lh = 18.sp, maxLines = 1,
        )
    }
}

/** Khule alerts, campaigns (aaj / kal), checks. */
@Composable
private fun AcctMain(j: JSONObject, a: GAccount, nav: NavHostController, cid: String) {
    val c = Mfc.c
    val cur = a.currency
    val alerts = j.arr("alerts").mapObj(GAlert::of)
    KSection("Open alerts".t(), trailing = if (alerts.isNotEmpty()) { { KPill(alerts.size.toString(), KKind.NEUTRAL, icon = null) } } else null)
    GAlertList(alerts, nav, "No open alerts".t(), showAccount = false)
    val camps = j.arr("campaigns").mapObj { it }
    KSection("Campaigns".t(), trailing = if (cur.isNotBlank()) { { KPill(cur, KKind.NEUTRAL, icon = null) } } else null)
    KCard(list = true) {
        if (camps.isEmpty()) KEmpty("No campaign data".t())
        camps.forEachIndexed { i, cp ->
            if (i > 0) KListDivider()
            val st = cp.s("status")
            val lv = cp.s("alert_level")
            val kind = if (lv.isNotBlank()) gLevelKind(lv) else gStateKind(st)
            val word = listOfNotNull(if (lv.isNotBlank()) gLevelWord(lv) else null, gText(st).ifBlank { null }).joinToString(" · ").ifBlank { "–" }
            val prev = cp.s("prev_status")
            val body = listOfNotNull(
                "Yesterday".t() + " " + gCash(cp.dn("spend_yesterday"), cur, 0),
                tf("%s installs", num(cp.dn("installs_yesterday"), 0)),
                if (prev.isNotBlank() && prev != st) "Was".t() + " " + gText(prev) else null,
            ).joinToString(" · ")
            GRow(
                cp.s("name").ifBlank { cp.s("campaign_id") }, status = word to kind, body = body, titleLines = 1, chevron = true,
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        GTxt(gCash(cp.dn("spend_today"), cur), 16.sp, c.ink, weight = FontWeight.Bold, lh = 20.sp, maxLines = 1)
                        GTxt("Today".t(), 13.sp, c.ink3, lh = 18.sp, maxLines = 1)
                    }
                },
                onClick = { nav.navigate("gads/campaign/$cid/${cp.s("campaign_id")}") },
            )
        }
    }
    val scan = a.lastScanAt
    KSection("Checks".t(), trailing = if (scan != null) { { GTxt("Scan".t() + " " + gWhen(scan), 13.sp, c.ink3, lh = 18.sp, maxLines = 1) } } else null)
    KCard(list = true) { ChecksList(j.optJSONObject("checks")) }
}

/** Data sources, Google Ads emails, account ki jankari, switches, limits. */
@Composable
private fun AcctSide(
    j: JSONObject, a: GAccount, g: GState, vm: GadsVm, cid: String,
    onEdit: () -> Unit, onNewTok: () -> Unit, onToken: (String) -> Unit, onTh: (Triple<String, String, String>) -> Unit,
) {
    val c = Mfc.c
    val cur = a.currency
    KSection("Data sources".t())
    KCard {
        SourcesList(j.optJSONObject("sources"))
        if (a.srcScript) {
            KDivider()
            GKv("Script status".t(), gText(a.scriptStatus).ifBlank { "–" }, gStateColor(a.scriptStatus))
            GKv("Token".t(), a.tokenHint.ifBlank { "Not set".t() })
            KButton("New token + script".t(), onNewTok, Modifier.fillMaxWidth().padding(top = 8.dp), kind = KBtn.SOFT, icon = KIcons.Key)
        }
        j.arr("script_errors").mapObj { it }.forEach { e -> GNote(e.s("section") + ": " + e.s("error"), KKind.WARN) }
        j.sn("policy_at")?.let { GKv("Policy snapshot".t(), gAgo(it)) }
    }
    val emails = j.arr("emails").mapObj { it }
    KSection("Google Ads emails".t())
    KCard(list = true) {
        if (!a.srcEmail) GRow("Email source off".t(), lead = { KIconTile(KIcons.Bell) }, titleLines = 1)
        if (emails.isEmpty()) KEmpty("No emails yet".t(), KIcons.Send)
        emails.forEachIndexed { i, e ->
            if (i > 0 || !a.srcEmail) KListDivider()
            val kind = e.s("kind")
            val ok = e.b("verified")
            val kk = when (kind) {
                "REINSTATED" -> if (ok) KKind.OK else KKind.WARN
                "OTHER" -> KKind.NEUTRAL
                else -> KKind.WARN
            }
            val st = listOfNotNull(gText(kind).ifBlank { null }, if (ok) "Verified".t() else "Not verified".t(), if (e.b("sim")) "Simulated".t() else null).joinToString(" · ")
            GRow(
                e.s("subject").ifBlank { "–" }, status = st to kk,
                body = listOfNotNull(e.s("from_addr").ifBlank { null }, e.s("verify_note").ifBlank { null }, e.s("reason").ifBlank { null }).joinToString(" · "),
                trailing = { GTxt(gWhen(e.s("received_at")), 13.sp, c.ink3, lh = 18.sp, maxLines = 1) },
            )
        }
    }
    KSection("Account details".t())
    KCard {
        GKv("Company".t(), a.company.ifBlank { "–" })
        GKv("Account name".t(), a.accountName.ifBlank { "–" })
        GKv("MCC (label only)".t(), a.mccLabel.ifBlank { "–" })
        GKv("Invoice / billing ref".t(), a.invoiceRef.ifBlank { "–" })
        GKv("Credit line ref".t(), a.creditRef.ifBlank { "–" })
        GKv("Currency".t(), cur.ifBlank { "–" })
        GKv("Time zone".t(), a.tz.ifBlank { j.s("tz_used").ifBlank { "–" } + " · " + "Auto".t() })
        GKv("Authorized by".t(), a.authorizedBy.ifBlank { "–" } + (a.authorizedAt?.let { " · " + fmtDay(it.take(10)) } ?: ""))
        if (a.notes.isNotBlank()) GTxt(a.notes, 14.sp, c.ink2, Modifier.padding(top = 6.dp), lh = 20.sp)
    }
    val tg = { f: String, v: Boolean -> toggle(vm, cid, f, v) { tok -> if (tok != null) onToken(tok) } }
    val busy = { f: String -> "tg$cid$f" in g.busy }
    KSection("Settings".t())
    KCard(list = true) {
        GSwitchRow("Monitoring".t(), null, a.monitoring, busy("monitoring"), KIcons.Pulse) { tg("monitoring", it) }
        KListDivider(lead = true)
        GSwitchRow("Phone notifications".t(), null, a.push, busy("push"), KIcons.Bell) { tg("push", it) }
        KListDivider(lead = true)
        GSwitchRow("Email alerts".t(), "Critical only".t(), a.email, busy("email"), KIcons.Send) { tg("email", it) }
        KListDivider(lead = true)
        GSwitchRow("Existing dashboard data".t(), null, a.srcDashboard, busy("src_dashboard"), KIcons.Chart) { tg("src_dashboard", it) }
        KListDivider(lead = true)
        GSwitchRow("Read-only Ads Script".t(), null, a.srcScript, busy("src_script"), KIcons.Lock) { tg("src_script", it) }
        KListDivider(lead = true)
        GSwitchRow("Google Ads emails".t(), null, a.srcEmail, busy("src_email"), KIcons.Ads) { tg("src_email", it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KButton("Edit".t(), onEdit, Modifier.weight(1f), kind = KBtn.OUTLINE, icon = KIcons.Edit)
        KButton(
            "Check now".t(), { vm.act("scan", mapOf("cid" to cid), busyKey = "scan$cid", ok = "Check started".t()) }, Modifier.weight(1f),
            kind = KBtn.SOFT, icon = KIcons.Refresh, busy = "scan$cid" in g.busy,
        )
    }
    val ths = j.arr("thresholds").mapObj { it }
    KSection("Thresholds".t())
    KCard(list = true) {
        if (ths.isEmpty()) GRow("Global limits".t(), lead = { KIconTile(KIcons.Target) }, titleLines = 1)
        ths.forEachIndexed { i, t ->
            if (i > 0) KListDivider()
            val key = t.s("key")
            KRow(
                gThLabel(key), sub = scopeText(t.s("scope")), value = gThVal(key, t.dn("value"), cur), chevron = true,
                onClick = { onTh(Triple(t.s("scope"), key, gPlain(t.dn("value")))) },
            )
        }
        KListDivider(lead = true)
        KRow("Account CPI target".t(), lead = { KIconTile(KIcons.Plus, KKind.ASK) }, onClick = { onTh(Triple("acct:$cid", "cpi_target", "")) })
    }
    ReadOnlyNote()
}

private fun toggle(vm: GadsVm, cid: String, field: String, on: Boolean, onToken: (String?) -> Unit) =
    vm.act("account_toggle", mapOf("cid" to cid, "field" to field, "value" to if (on) "1" else "0"), busyKey = "tg$cid$field") { j ->
        vm.load("account", mapOf("cid" to cid)); vm.load("settings")
        onToken(j.optString("token").takeIf { it.isNotBlank() && it != "null" })
    }

// ---------------------------------------------------------------- campaign detail

private val CAMP_RANGES = listOf("today" to "Today", "yesterday" to "Yesterday", "7d" to "7 days", "14d" to "14 days", "30d" to "30 days")

@Composable
fun GadsCampaignScreen(vm: GadsVm, nav: NavHostController, cid: String, camp: String) {
    val g by vm.s.collectAsStateWithLifecycle()
    var reload by remember { mutableIntStateOf(0) }
    var range by rememberSaveable { mutableStateOf("7d") }
    val p = mapOf("cid" to cid, "campaign_id" to camp, "range" to range)
    gPoll(60_000L, p, reload) { vm.load("campaign", p) }
    var th by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    // range badalne par purana data dikhta rahe jab tak naya na aaye
    val j0 = g.view("campaign", p) ?: CAMP_RANGES.firstNotNullOfOrNull { g.view("campaign", p + ("range" to it.first)) }
    val name = j0?.obj("campaign")?.s("name")?.ifBlank { null } ?: ("Campaign".t() + " " + camp)
    val sub = listOfNotNull(j0?.obj("account")?.s("company")?.ifBlank { null }, "CID " + cid).joinToString(" · ")
    val retry = { vm.load("campaign", p) }
    val onTh: (Triple<String, String, String>) -> Unit = { th = it }
    val onRange: (String) -> Unit = { range = it }
    val bar: @Composable ColumnScope.() -> Unit = {
        KDetailBar(name, gBack(nav), sub = sub, actions = { KBarIcon(KIcons.Refresh, "Refresh".t(), { reload++ }) })
    }
    if (isWide()) {
        KScreen(top = bar) {
            GLoad(g, j0, retry) { j ->
                KTwoPane(
                    left = { CampHero(j, g); CampMain(j) },
                    right = { CampSide(j, cid, camp, range, onRange, nav, onTh) },
                )
            }
        }
    } else {
        KScreen(top = { bar(); if (j0 != null) CampHero(j0, g) }) {
            GLoad(g, j0, retry) { j ->
                CampMain(j)
                CampSide(j, cid, camp, range, onRange, nav, onTh)
            }
        }
    }
    th?.let { (s, k, v) -> ThresholdDialog("Threshold".t(), s, k, v, "", vm, { vm.load("campaign", p) }) { th = null } }
}

/** Hero: aaj ka kharcha, status, channel / bidding / app, raftaar (aaj vs aam). */
@Composable
private fun CampHero(j: JSONObject, g: GState) {
    KHero(KTint.Sand) {
        val c = Mfc.c
        val cur = j.obj("account").s("currency")
        val cp = j.obj("campaign")
        val m = cp.optJSONObject("meta")
        val st = cp.s("status")
        KHeroHead("Spend today".t(), icon = KIcons.Ads, trailing = { KPill(if (st.isBlank()) "–" else gText(st), gStateKind(st)) })
        val w = j.obj("windows")
        val today = w.optJSONObject("today")?.dn("cost")
        val usual = w.optJSONObject("usual")?.dn("cost")
        KBigNum(gCash(today, cur), size = 48.sp)
        val meta = listOfNotNull(
            m?.s("channel")?.ifBlank { null }?.let { gText(it) }, m?.s("bidding")?.ifBlank { null }?.let { gText(it) }, cp.s("pkg").ifBlank { null },
        )
        if (meta.isNotEmpty()) GTxt(meta.joinToString(" · "), 14.sp, c.ink2, Modifier.padding(top = 2.dp), lh = 20.sp, maxLines = 2)
        val th = j.obj("thresholds")
        val (gd, gs) = gPaceLimits(g)
        val pace = if (today != null && usual != null && usual > 0) (today - usual) / usual * 100 else null
        KPaceMeter(pace, -(th.dn("drop_pct") ?: gd), th.dn("spike_pct") ?: gs)
        if (!j.b("fresh")) KStatus("Stale data".t(), KKind.WARN, Modifier.padding(top = 8.dp))
    }
}

/** Budget / Google targets, aaj vs pichhle din, ghante ka kharcha, 30 din. */
@Composable
private fun CampMain(j: JSONObject) {
    val c = Mfc.c
    val cur = j.obj("account").s("currency")
    val m = j.obj("campaign").optJSONObject("meta")
    val roasT = gRoasT()
    if (m != null) {
        val rows = listOfNotNull(
            m.dn("budget")?.let { "Budget".t() to gCash(it, cur, 0) },
            m.dn("target_cpa")?.let { "Google tCPA" to gCash(it, cur, 2) },
            m.dn("target_roas")?.let { "Google tROAS" to gRoas(it) },
            m.s("primary_status").ifBlank { null }?.let { "Primary status".t() to gText(it) },
        )
        val reasons = m.s("primary_reasons").ifBlank { null }?.replace(',', ' ')?.replace('_', ' ')?.lowercase()
        if (rows.isNotEmpty() || reasons != null) KCard {
            rows.forEach { (l, v) -> GKv(l, v) }
            if (reasons != null) GTxt(reasons, 13.sp, c.ink2, Modifier.padding(top = 4.dp), lh = 18.sp)
        }
    }
    val w = j.obj("windows")
    val rt = j.sn("reported_through")
    KSection("Today vs history".t(), trailing = if (rt != null) { { GTxt(tf("till %s", rt), 13.sp, c.ink3, lh = 18.sp, maxLines = 1) } } else null)
    KCard {
        WindowsTable(w, cur, "spend")
        Spacer(Modifier.height(14.dp))
        WindowsTable(w, cur, "cpi")
        val hasValue = w.keys().asSequence().any { (w.optJSONObject(it)?.dn("conv_value") ?: 0.0) > 0 }
        if (hasValue) {
            Spacer(Modifier.height(14.dp))
            WindowsTable(w, cur, "roas")
        }
    }
    val hn = j.arr("hours_today")
    val hu = j.arr("hours_usual")
    if (hu.length() == 24) {
        KSection("Spend by hour".t(), trailing = if (cur.isNotBlank()) { { KPill(cur, KKind.NEUTRAL, icon = null) } } else null)
        KCard { GHourBars(hn, hu) }
    }
    val trend = j.arr("trend").mapObj { it }.filter { it.b("has_data") }
    val rc = trend.map { kRoasColor(it.dn("roas"), roasT) }
    KSection("Last 30 days".t())
    KCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        GTable(
            listOf("Day".t(), "Spend".t(), "Installs".t(), "CPI", "ROAS"),
            trend.map { d -> listOf(fmtDay(d.s("day")), gCashS(d.dn("cost"), cur), num(d.dn("installs"), 0), gCash(d.dn("cpi"), cur, 2), gRoas(d.dn("roas"))) },
            empty = "No daily data".t(), firstWeight = 1.2f,
            tint = { ri, ci -> if (ci == 4) rc[ri] else Color.Unspecified }, firstMin = 56.dp,
        )
    }
}

/** Range chips, ad groups, desh (tap = CPI target), limits, ad policy, creatives, alerts. */
@Composable
private fun CampSide(
    j: JSONObject, cid: String, camp: String, range: String, onRange: (String) -> Unit, nav: NavHostController, onTh: (Triple<String, String, String>) -> Unit,
) {
    val c = Mfc.c
    val cur = j.obj("account").s("currency")
    val note = j.obj("sources_note")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CAMP_RANGES.forEach { (k, l) -> GChip(l.t(), range == k) { onRange(k) } }
    }
    val rr = j.arr("range")
    val rangeTxt = if (rr.length() >= 2) fmtDay(rr.optString(0)) + " – " + fmtDay(rr.optString(1)) else null
    KSection("Ad groups".t(), trailing = if (rangeTxt != null) { { GTxt(rangeTxt, 13.sp, c.ink3, lh = 18.sp, maxLines = 1) } } else null)
    val ags = j.arr("ad_groups").mapObj { it }
    KCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        GTable(
            listOf("Ad group".t(), "Spend".t(), "Installs".t(), "CPI", "CTR"),
            ags.map { a -> listOf(a.s("name").ifBlank { a.s("ad_group_id") }, gCashS(a.dn("cost"), cur), num(a.dn("installs"), 0), gCash(a.dn("cpi"), cur, 2), pct(a.dn("ctr"), 2)) },
            empty = if (note.s("ad_groups").isNotBlank()) "Needs script".t() else "No ad group data".t(), firstWeight = 2f,
        )
    }
    val ccT = j.obj("country_targets")
    val geo = j.arr("countries").mapObj { it }
    val cpiCol = geo.map { r ->
        val tg = ccT.dn(r.s("country"))
        val v = r.dn("cpi")
        if (tg != null && v != null && v > tg) c.red else Color.Unspecified
    }
    KSection("Countries".t())
    KCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        GTable(
            listOf("Country".t(), "Spend".t(), "Installs".t(), "CPI", "Target".t()),
            geo.map { r ->
                listOf(r.s("country"), gCashS(r.dn("cost"), cur), num(r.dn("installs"), 0), gCash(r.dn("cpi"), cur, 2), ccT.dn(r.s("country"))?.let { gCash(it, cur, 2) } ?: "–")
            },
            empty = if (note.s("countries").isNotBlank()) "Needs script".t() else "No country data".t(), firstWeight = .9f,
            onRow = { i ->
                val cc = geo[i].s("country")
                if (cc.length == 2) onTh(Triple("country:$cid:$cc", "cpi_target", gPlain(ccT.dn(cc))))
            },
            tint = { ri, ci -> if (ci == 3) cpiCol[ri] else Color.Unspecified }, firstMin = 56.dp,
        )
    }
    val t = j.obj("thresholds")
    KSection("Thresholds".t())
    KCard(list = true) {
        listOf("cpi_target", "roas_target", "drop_pct", "spike_pct", "cpi_over_pct", "zero_hours", "min_installs").forEachIndexed { i, k ->
            if (i > 0) KListDivider()
            KRow(gThLabel(k), value = gThVal(k, t.dn(k), cur), valuePlain = true, chevron = true, onClick = { onTh(Triple("camp:$cid:$camp", k, "")) })
        }
    }
    val policy = j.arr("policy").mapObj { it }
    if (policy.isNotEmpty()) {
        KSection("Ad policy".t())
        KCard(list = true) {
            policy.forEachIndexed { i, pl ->
                if (i > 0) KListDivider()
                val ap = pl.s("approval")
                GRow("Ad".t() + " " + pl.s("ad_id"), status = gText(ap).ifBlank { "–" } to gStateKind(ap), body = pl.s("topics"), bodyLines = 3, titleLines = 1)
            }
        }
    }
    val assets = j.arr("assets").mapObj { it }
    if (assets.isNotEmpty()) {
        KSection("Creatives".t())
        KCard {
            assets.take(30).forEachIndexed { i, a ->
                if (i > 0) KDivider()
                GAssetRow(a, ctr = false)
            }
        }
    }
    val alerts = j.arr("alerts").mapObj(GAlert::of)
    KSection("Alerts (30 days)".t())
    GAlertList(alerts, nav, "No alerts".t(), showAccount = false)
    ReadOnlyNote()
}

// ---------------------------------------------------------------- run (audit log)

@Composable
fun GadsRunScreen(vm: GadsVm, nav: NavHostController, id: Long) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("id" to id.toString())
    var reload by remember { mutableIntStateOf(0) }
    val j0 = g.view("run", p)
    val running = j0?.obj("run")?.s("status") == "RUNNING"
    gPoll(if (running) 5_000L else 60_000L, id, reload, running) { vm.load("run", p) }
    val r0 = j0?.optJSONObject("run")?.let { GRun.of(it) }
    val retry = { vm.load("run", p) }
    val bar: @Composable ColumnScope.() -> Unit = {
        KDetailBar("Run".t() + " #$id", gBack(nav), sub = r0?.uid?.ifBlank { null } ?: updatedText(g), actions = {
            KBarIcon(KIcons.Refresh, "Refresh".t(), { reload++ })
        })
    }
    if (isWide()) {
        KScreen(top = bar) {
            GLoad(g, j0, retry) { j ->
                val r = GRun.of(j.obj("run"))
                KTwoPane(left = { RunHero(r); RunMain(r, running) }, right = { RunSide(j, r, nav) })
            }
        }
    } else {
        KScreen(top = { bar(); if (r0 != null) RunHero(r0) }) {
            GLoad(g, j0, retry) { j ->
                val r = GRun.of(j.obj("run"))
                RunMain(r, running)
                RunSide(j, r, nav)
            }
        }
    }
}

/** Hero: kaunsa run + status, kitni problems, accounts / campaigns / failed checks. */
@Composable
private fun RunHero(r: GRun) {
    KHero(KTint.Sand) {
        val c = Mfc.c
        KHeroHead(
            runType(r.type) + (if (r.sim) " · " + "Simulated".t() else ""), icon = gRunIcon(r.type),
            trailing = { KPill(gText(r.status), gStateKind(r.status)) },
        )
        KBigNum(r.issues.toString(), suffix = " " + "issues".t(), size = 48.sp)
        KSplit(
            listOf("Accounts".t() to r.accounts.toString(), "Campaigns".t() to r.campaigns.toString(), "Failed checks".t() to r.failedChecks.toString()),
            listOf(null, null, if (r.failedChecks > 0) c.red else null),
        )
    }
}

@Composable
private fun RunMain(r: GRun, running: Boolean) {
    val c = Mfc.c
    if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
    KCard {
        GKv("Run ID".t(), r.uid.ifBlank { "–" })
        GKv("Trigger".t(), r.trigger.ifBlank { "–" } + (if (r.by.isNotBlank()) " · " + r.by else ""))
        GKv("Started".t(), tFull(r.startedAt))
        GKv("Finished".t(), r.finishedAt?.let { tFull(it) } ?: "–")
        GKv("Recommendations".t(), r.recs.toString())
        GKv("Next run".t(), r.nextRunAt?.let { tFull(it) } ?: "–")
    }
    if (r.errors.isNotBlank()) {
        KSection("Errors".t())
        KCard { GTxt(r.errors, 14.sp, c.red, lh = 20.sp) }
    }
}

@Composable
private fun RunSide(j: JSONObject, r: GRun, nav: NavHostController) {
    if (r.sources.length() > 0) {
        KSection("Data source status".t())
        KCard(list = true) {
            r.sources.mapObj { it }.forEachIndexed { i, s ->
                if (i > 0) KListDivider()
                val st = s.s("status")
                GRow(
                    listOfNotNull(s.sn("company"), s.sn("cid")?.let { "CID $it" }).joinToString(" · ").ifBlank { s.toString().take(80) },
                    status = if (st.isNotBlank()) gText(st) to gStateKind(st) else null,
                    body = s.keys().asSequence().filter { it !in setOf("company", "cid", "status") }
                        .joinToString(" · ") { k -> gText(k) + ": " + s.opt(k)?.toString().orEmpty().take(60) },
                    bodyLines = 4,
                )
            }
        }
    }
    r.summary?.optString("text")?.takeIf { it.isNotBlank() }?.let { t ->
        KSection("Summary".t())
        GMono(t)
    }
    val ev = j.arr("events").mapObj(GEvent::of)
    KSection("Log".t())
    KCard {
        if (ev.isEmpty()) KEmpty("Nothing logged".t())
        ev.forEach { GEventRow(it, nav) }
    }
}

// ---------------------------------------------------------------- L. Notification settings

private val G_SIMS = listOf(
    "spend_drop" to "Spend drop", "spend_spike" to "Spend spike", "zero_spend" to "Zero spend", "stale" to "Missing data",
    "paused" to "Campaign stopped", "suspended_email" to "Suspension email",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(g: GState, vm: GadsVm, nav: NavHostController, reload: Int, onToken: (String, String) -> Unit) {
    gPoll(60_000L, reload) { vm.load("settings") }
    var th by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var thDef by remember { mutableStateOf("") }
    var num0 by remember { mutableStateOf<Pair<String, String>?>(null) }
    var simCid by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    GLoad(g, g.view("settings"), { vm.load("settings") }) { j ->
        val c = Mfc.c
        val set = j.obj("settings")
        val setOn = { k: String -> set.s(k) == "1" }
        val save = { k: String, v: String -> vm.act("setting", mapOf("key" to k, "value" to v), busyKey = "set$k", ok = "Saved".t()) { vm.load("settings") } }
        val sw = { k: String, on: Boolean -> save(k, if (on) "1" else "0") }
        val busy = { k: String -> "set$k" in g.busy }

        KSection("Notifications".t())
        KCard(list = true) {
            GSwitchRow("Phone notifications".t(), "Once per problem".t(), setOn("ga_push"), busy("ga_push"), KIcons.Bell) { sw("ga_push", it) }
            KListDivider(lead = true)
            val eo = j.obj("email_out")
            GSwitchRow(
                "Email for critical alerts".t(), if (eo.b("configured")) tf("To %s", eo.s("to")) else "Not configured".t(), setOn("ga_mail_critical"),
                busy("ga_mail_critical"), KIcons.Send,
            ) { sw("ga_mail_critical", it) }
            KListDivider(lead = true)
            val hour = set.s("ga_daily_hour").trim().toIntOrNull()
            val hourTxt = if (hour == null) "–" else String.format(Locale.US, "%02d:00", hour)
            GSwitchRow("Daily summary email".t(), tf("Daily at %s", hourTxt), setOn("ga_mail_daily"), busy("ga_mail_daily"), KIcons.Calendar) { sw("ga_mail_daily", it) }
            KListDivider(lead = true)
            KRow(
                "Daily summary hour".t(), lead = { KIconTile(KIcons.Clock) }, value = hourTxt, valuePlain = true, chevron = true,
                onClick = { num0 = "ga_daily_hour" to set.s("ga_daily_hour") },
            )
            KListDivider(lead = true)
            val ai = j.obj("ai")
            GSwitchRow(
                "AI explanations".t(), tf("%s / %s AI calls today", ai.d("used_today").toInt(), ai.d("cap").toInt()), setOn("ga_ai_on"), busy("ga_ai_on"), KIcons.Spark,
            ) { sw("ga_ai_on", it) }
            ai.sn("last_error")?.let { Box(Modifier.padding(horizontal = 16.dp)) { GNote(it.take(160), KKind.BAD) } }
            KListDivider(lead = true)
            KRow(
                "Daily AI limit".t(), lead = { KIconTile(KIcons.Chart) }, value = set.s("ga_ai_cap").ifBlank { "–" }, valuePlain = true, chevron = true,
                onClick = { num0 = "ga_ai_cap" to set.s("ga_ai_cap") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton(
                "Send test".t(), { vm.act("test_notify", ok = "Test sent: check your notifications".t()) { vm.load("settings") } }, Modifier.weight(1f),
                kind = KBtn.SOFT, busy = "test_notify" in g.busy,
            )
            KButton("Summary now".t(), { vm.act("daily_now", ok = "Started".t()) }, Modifier.weight(1f), kind = KBtn.OUTLINE, busy = "daily_now" in g.busy)
        }

        val e = j.obj("email_in")
        val eOn = e.b("configured")
        KSection("Email intake".t())
        KCard {
            GKv("Mailbox".t(), if (eOn) e.s("mailbox").ifBlank { "–" } else "Not configured".t(), if (eOn) Color.Unspecified else c.amber)
            GKv("Last read".t(), e.sn("last_ok")?.let { tFull(it) } ?: "–")
            if (e.s("error").isNotBlank()) GNote(e.s("error"), KKind.BAD)
            KButton(
                "Check email now".t(), { vm.act("mail_check", ok = "Started".t()) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                kind = KBtn.SOFT, icon = KIcons.Send, enabled = eOn, busy = "mail_check" in g.busy,
            )
        }

        val ths = j.arr("thresholds").mapObj { it }
        KSection("Thresholds (all accounts)".t())
        KCard(list = true) {
            if (ths.isEmpty()) KEmpty("–")
            ths.forEachIndexed { i, t ->
                if (i > 0) KListDivider()
                val k = t.s("key")
                val v = t.dn("value")
                val d = t.dn("default")
                KRow(
                    gThLabel(k), value = gThVal(k, v ?: d, ""), valueColor = if (v != null) c.brand else c.ink3,
                    valueSub = if (v == null && d != null) "default".t() else null, chevron = true,
                    onClick = { thDef = gPlain(d).ifBlank { "–" }; th = Triple("global", k, gPlain(v)) },
                )
            }
        }
        val over = j.arr("overrides").mapObj { it }
        if (over.isNotEmpty()) {
            KSection("Overrides".t())
            KCard(list = true) {
                over.forEachIndexed { i, o ->
                    if (i > 0) KListDivider()
                    KRow(
                        gThLabel(o.s("key")), sub = scopeText(o.s("scope")), value = o.dn("value")?.let { gThVal(o.s("key"), it, "") } ?: o.s("value").ifBlank { "–" },
                        valuePlain = true, chevron = true,
                        onClick = { thDef = ""; th = Triple(o.s("scope"), o.s("key"), o.s("value")) },
                    )
                }
            }
        }

        val accs = j.arr("accounts").mapObj(GAccount::of)
        KSection("Accounts".t(), trailing = if (accs.isNotEmpty()) { { KPill(accs.size.toString(), KKind.NEUTRAL, icon = null) } } else null)
        KCard(list = true) {
            accs.forEachIndexed { i, a ->
                if (i > 0) KListDivider(lead = true)
                val name = a.company.ifBlank { a.cidFmt }
                KRow(
                    name,
                    sub = listOfNotNull(
                        a.cidFmt, a.currency.ifBlank { null }, if (a.srcDashboard) "dashboard".t() else null, if (a.srcScript) "script".t() else null,
                        if (a.srcEmail) "email".t() else null,
                    ).joinToString(" · "),
                    lead = { KAvatar(name) },
                    trailing = {
                        KSwitch(a.monitoring, { v -> toggle(vm, a.cid, "monitoring", v) { tok -> if (tok != null) onToken(a.cid, tok) } }, enabled = "tg${a.cid}monitoring" !in g.busy)
                    },
                    onClick = { nav.navigate("gads/account/${a.cid}") },
                )
            }
            if (accs.isNotEmpty()) KListDivider(lead = true)
            KRow("Add account".t(), lead = { KIconTile(KIcons.Plus, KKind.ASK) }, onClick = { add = true })
        }

        val s = j.obj("setup")
        val cronOk = s.b("cron_ok")
        KSection("Setup".t())
        KCard {
            GKv("Cron (every 5 min)".t(), if (cronOk) "Running".t() + " · " + gAgo(s.sn("cron_last")) else "Not running".t(), if (cronOk) c.green else c.red)
            GLabel("Script data URL".t())
            GTxt(j.s("ingest_url").ifBlank { "–" }, 13.sp, c.ink2, lh = 18.sp, maxLines = 3)
        }

        val dev = setOn("ga_dev_mode")
        KSection("Development mode".t())
        KCard(list = true) {
            GSwitchRow("Development mode".t(), "Simulated events".t(), dev, busy("ga_dev_mode"), KIcons.Wand) { sw("ga_dev_mode", it) }
            if (dev) {
                KListDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    GNote("Simulated only".t(), KKind.WARN)
                    if (accs.isNotEmpty()) {
                        val sel = simCid.ifBlank { accs.first().cid }
                        GLabel("Account".t())
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            accs.forEach { a -> GChip(a.company.take(18) + " · " + a.cidFmt.takeLast(4), sel == a.cid) { simCid = a.cid } }
                        }
                        GLabel("Simulate".t())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            G_SIMS.forEach { (k, l) ->
                                KSmallAction(
                                    l.t(),
                                    { vm.act("simulate", mapOf("cid" to sel, "rule" to k), busyKey = "sim$k", ok = "Simulated alert created".t()) { vm.load("settings") } },
                                    enabled = "sim$k" !in g.busy,
                                )
                            }
                        }
                    }
                    KButton(
                        "Clear simulated alerts".t(), { vm.act("sim_clear", ok = "Cleared".t()) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                        kind = KBtn.OUTLINE, busy = "sim_clear" in g.busy,
                    )
                }
            }
        }
        ReadOnlyNote()
    }
    th?.let { (s, k, v) -> ThresholdDialog("Threshold".t(), s, k, v, thDef, vm, { vm.load("settings") }) { th = null } }
    num0?.let { (k, v) -> NumDialog(k, v, vm) { num0 = null } }
    if (add) AccountSheet(null, "", "", g, vm, onSaved = { cid, tok ->
        add = false; vm.load("settings"); vm.load("filters")
        if (tok != null) onToken(cid, tok)
    }) { add = false }
}

/** Daily summary ka ghanta (0-23) ya AI calls / din (1-500). */
@Composable
private fun NumDialog(k: String, v: String, vm: GadsVm, onClose: () -> Unit) {
    var t by remember(k) { mutableStateOf(v) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (k == "ga_daily_hour") "Daily summary hour (0-23)".t() else "AI calls per day (1-500)".t()) },
        text = {
            OutlinedTextField(
                t, { t = it.filter { ch -> ch.isDigit() }.take(3) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                vm.act("setting", mapOf("key" to k, "value" to t), busyKey = "set$k", ok = "Saved".t()) { vm.load("settings") }
                onClose()
            }) { Text("Save".t()) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel".t()) } },
    )
}
