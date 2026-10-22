package com.myfuturecode.mfcdash.ui.screens.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.StudioVm
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KKv
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KProgress
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSkelHero
import com.myfuturecode.mfcdash.ui.kit.KSkelRows
import com.myfuturecode.mfcdash.ui.kit.KSkelStats
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KSmallAction
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.fg
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.word
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.l
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.obj
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.strings
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tOnly
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.usd
import org.json.JSONObject

@Composable
private fun RefreshIcon(onClick: () -> Unit) = KBarIcon(KIcons.Refresh, "Refresh".t(), onClick)

// ---------------------------------------------------------------- idea (opportunity) detail

@Composable
fun StudioOppScreen(vm: StudioVm, nav: NavHostController, id: Long) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("id" to id.toString())
    SPoll(60_000, id) { vm.load("opp", p) }
    var ask by remember { mutableStateOf<String?>(null) }
    val o = g.view("opp", p)
    val item = o?.obj("item")
    val wide = isWide()
    KScreen(top = {
        KDetailBar(
            item?.s("title")?.ifBlank { null } ?: "OPP-$id", sBack(nav),
            sub = item?.let { oppSub(it, id) } ?: sUpdated(g),
            actions = { RefreshIcon { vm.load("opp", p) } },
        )
        if (!wide && item != null) OppHero(item)
        else if (!wide && sSkelOn(g, o)) KSkelHero(KTint.Lav)
    }) {
        SLoad(g, o, { vm.load("opp", p) }, { SDetailSkel(wide) }) { oo ->
            val it0 = oo.obj("item")
            val pid = oo.dn("project_id")?.toLong()
            if (wide) KTwoPane(
                left = {
                    OppHero(it0)
                    OppActions(it0, pid, nav) { ask = it }
                    OppFacts(it0)
                    OppTexts(it0)
                    DecisionCard(it0.obj("decision_card"), it0.sn("confidence"))
                },
                right = {
                    OppFactors(it0)
                    ProfitPanel(it0.optJSONObject("profit"))
                    OppMore(it0, oo)
                },
            ) else {
                OppActions(it0, pid, nav) { ask = it }
                OppFacts(it0)
                OppTexts(it0)
                DecisionCard(it0.obj("decision_card"), it0.sn("confidence"))
                OppFactors(it0)
                ProfitPanel(it0.optJSONObject("profit"))
                OppMore(it0, oo)
            }
        }
    }
    val d = ask
    if (d != null) {
        val title = when (d) {
            "APPROVE" -> "Approve (Gate 1)?".t()
            "REJECT" -> "Reject this idea?".t()
            "WATCH" -> "Watch this market?".t()
            "MORE_RESEARCH" -> "Research more?".t()
            else -> "Restore idea?".t()
        }
        val body = when (d) {
            "APPROVE" -> "Creates a project, then competitor analysis + feature spec are prepared for Gate 2. Nothing is built or published.".t()
            "REJECT" -> "The idea stays in history with your reason. You can restore it later.".t()
            "MORE_RESEARCH" -> "Runs another AI + web research pass for this idea (uses AI budget).".t()
            else -> ""
        }
        val busyKey = if (d == "APPROVE") "gate1" else "opp"
        TextAskDialog(
            title, body, if (d == "REJECT") "Reason (required)".t() else "Note (optional)".t(), title.removeSuffix("?"), required = d == "REJECT",
            busy = busyKey in g.busy, danger = d == "REJECT", onDismiss = { ask = null },
        ) { note ->
            if (d == "APPROVE") vm.act("gate1", mapOf("select[0]" to id.toString(), "note" to note), "gate1", ok = "Approved. Competitor analysis queued.".t()) { j ->
                ask = null
                vm.load("opp", p)
                j.optJSONArray("projects")?.optLong(0)?.takeIf { it > 0 }?.let { nav.navigate("studio/project/$it") }
            }
            else vm.act("opp", mapOf("id" to id.toString(), "decision" to d, "note" to note), "opp", ok = "Saved".t()) { ask = null; vm.load("opp", p) }
        }
    }
}

/** "OPP-12 · Health · IN US · aapka idea". */
private fun oppSub(it0: JSONObject, id: Long): String = listOfNotNull(
    "OPP-$id", it0.s("category").ifBlank { null }, it0.arr("countries").strings().filter { it.isNotBlank() }.joinToString(" ").ifBlank { null },
    if (it0.s("origin") == "user") "your idea".t() else null,
).joinToString(" · ")

/** Lavender hero: score (organic / paid me zyada) + salah pill + Organic | Paid | Data. */
@Composable
private fun OppHero(it0: JSONObject) {
    KHero(KTint.Lav) {
        val rec = it0.sn("final_rec")
        val org = it0.dn("organic")
        val paid = it0.dn("paid")
        val best = bestScore(it0)
        KHeroHead("Score".t(), icon = KIcons.Bulb, trailing = { if (rec != null) KPill(recText(rec), recKind(rec)) })
        KBigNum(score(best), suffix = if (best != null) "/100" else null, size = 48.sp)
        KSplit(
            listOf(
                "Organic".t() to (score(org) + (it0.dn("rank_organic")?.let { "  #" + it.toInt() } ?: "")),
                "Paid".t() to (score(paid) + (it0.dn("rank_paid")?.let { "  #" + it.toInt() } ?: "")),
                "Data".t() to (it0.dn("coverage")?.let { pct(it, 0) } ?: "–"),
            ),
        )
    }
}

/** Gate 1 ke faisle (sirf NEW / WATCH / REJECTED par). Har button pehle dialog kholta hai. */
@Composable
private fun OppActions(it0: JSONObject, pid: Long?, nav: NavHostController, onAsk: (String) -> Unit) {
    val st = it0.s("status")
    if (pid != null) KButton("Open project".t(), { nav.navigate("studio/project/$pid") }, Modifier.fillMaxWidth(), kind = KBtn.SOFT, icon = KIcons.Open)
    when (st) {
        "NEW", "WATCH" -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KButton("Approve".t(), { onAsk("APPROVE") }, Modifier.weight(1f), icon = KIcons.Check)
                KButton("Reject".t(), { onAsk("REJECT") }, Modifier.weight(1f), kind = KBtn.OUTLINE)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (st != "WATCH") KButton("Keep watching".t(), { onAsk("WATCH") }, Modifier.weight(1f), kind = KBtn.SOFT)
                KButton("More research".t(), { onAsk("MORE_RESEARCH") }, Modifier.weight(1f), kind = KBtn.SOFT)
            }
        }
        "REJECTED" -> KButton("Restore idea".t(), { onAsk("RESTORE") }, Modifier.fillMaxWidth(), kind = KBtn.SOFT, icon = KIcons.History)
        "EVALUATING" -> KCard(list = true) {
            KRow("Research running".t(), lead = { KIconTile(KIcons.Clock, KKind.ASK) })
        }
        else -> {}
    }
}

/** Status, confidence, AI vs rules (jo zyada saavdhan woh dikhta hai), complexity, kab. */
@Composable
private fun OppFacts(it0: JSONObject) {
    val c = Mfc.c
    val st = it0.s("status")
    val conf = it0.s("confidence")
    KCard(padding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
        KKv("Status".t(), oppStatusText(st), valueColor = stKind(st).let { if (it == KKind.NEUTRAL) c.ink else it.fg() })
        KKv("Confidence".t(), conf.ifBlank { "–" }, valueColor = if (conf.isBlank()) c.ink3 else stKind(conf).fg())
        KKv("AI said".t(), it0.sn("ai_rec")?.let { recText(it) } ?: "–")
        KKv("Rules say".t(), it0.sn("rule_rec")?.let { recText(it) } ?: "–")
        it0.sn("complexity")?.let { KKv("Build complexity".t(), it) }
        // factors ke label: kitne VERIFIED, kitne UNKNOWN (purane OppCard wali ginti)
        val lab = it0.obj("labels")
        if (lab.length() > 0) {
            KKv("Verified factors".t(), num(lab.d("VERIFIED")), valueColor = if (lab.d("VERIFIED") > 0) c.ink else c.ink3)
            KKv("Unknown factors".t(), num(lab.d("UNKNOWN")), valueColor = if (lab.d("UNKNOWN") > 0) c.ink else c.ink3)
        }
        KKv("Updated".t(), sAgo(it0.s("updated_at")))
        it0.sn("user_note")?.let { Para("Your note".t(), it) }
    }
}

@Composable
private fun OppTexts(it0: JSONObject) {
    if (listOf("concept", "why_build", "why_avoid").all { it0.sn(it).isNullOrBlank() }) return
    KCard {
        Para("Concept".t(), it0.sn("concept"))
        Para("Why build it".t(), it0.sn("why_build"))
        Para("Why avoid it".t(), it0.sn("why_avoid"))
    }
}

/** Score factors: rating 0-10 + label (shape + rang) + weight; neeche engine · disha · saboot. */
@Composable
private fun OppFactors(it0: JSONObject) {
    val c = Mfc.c
    val fs = it0.arr("factors").objs()
    if (fs.isEmpty()) return
    KSection("Score factors".t(), trailing = { KPill("0-10", KKind.NEUTRAL, icon = null) })
    KCard(list = true) {
        fs.forEachIndexed { i, f ->
            if (i > 0) KListDivider()
            val r = f.dn("rating")
            KRow(
                f.s("label"), sub = labelText(f.s("basis")), subKind = labelKind(f.s("basis")),
                value = r?.let { num(it, 1) } ?: "–", valueColor = if (r == null) c.ink3 else Color.Unspecified,
                valueSub = tf("weight %s", num(f.d("weight"))), titleLines = 2,
            )
            val meta = listOfNotNull(
                engineText(f.s("engine")),
                if (f.s("higher_is") == "better") "higher is better".t() else "higher is worse".t(),
                f.arr("evidence").strings().filter { it.isNotBlank() }.joinToString(" ") { "#$it" }.ifBlank { null },
            ).joinToString(" · ")
            SNote(meta, color = c.ink3, maxLines = 2)
            f.sn("note")?.let { SNote(it, maxLines = 4) }
        }
    }
}

/** Competitors, kamaai / khatre, saboot, aapke faisle, history. */
@Composable
private fun OppMore(it0: JSONObject, o: JSONObject) {
    val c = Mfc.c
    val comps = it0.arr("competitors").objs()
    if (comps.isNotEmpty()) {
        KSection("Competitors seen".t())
        KCard(list = true) {
            comps.forEachIndexed { i, x ->
                if (i > 0) KListDivider(true)
                KRow(x.s("name"), sub = x.s("package").ifBlank { "–" }, lead = { KAvatar(x.s("name")) })
            }
        }
    }
    val mon = it0.arr("monetization").strings().filter { it.isNotBlank() }
    val risks = it0.arr("risks").strings().filter { it.isNotBlank() }
    if (mon.isNotEmpty() || risks.isNotEmpty()) KCard {
        Bullets("Monetization".t(), mon, c.zGreen)
        Bullets("Risks".t(), risks, c.zRed)
    }
    EvidenceBlock(o.arr("evidence").objs())
    val dec = o.arr("decisions").objs()
    if (dec.isNotEmpty()) {
        KSection("Your decisions".t())
        KCard(list = true) {
            dec.forEachIndexed { i, x ->
                if (i > 0) KListDivider()
                // faisla = shabd + shape + rang (kit ka "Turant" / "Theek" yahan galat matlab deta)
                KRow(
                    if (x.d("gate") > 0) tf("Gate %s", x.d("gate").toInt()) else decisionText(x.s("decision")),
                    sub = decisionText(x.s("decision")), subKind = stKind(x.s("decision")),
                    value = tFull(x.s("created_at")), valuePlain = true, valueSub = x.s("user").ifBlank { null },
                )
                x.sn("feedback")?.let { SNote(it, maxLines = 4) }
            }
        }
    }
    HistoryBlock(o.arr("history").objs())
}

@Composable
private fun EvidenceBlock(ev: List<JSONObject>) {
    KSection("Evidence".t(), trailing = { KPill(num(ev.size.toDouble()), KKind.NEUTRAL, icon = null) })
    if (ev.isEmpty()) KCard { KEmpty("No evidence".t(), KIcons.Shield) }
    else KCard(list = true) {
        ev.forEachIndexed { i, e ->
            if (i > 0) KListDivider()
            EvidenceRow(e)
        }
    }
}

@Composable
private fun HistoryBlock(hist: List<JSONObject>) {
    if (hist.isEmpty()) return
    KSection("History".t())
    KCard(list = true) {
        hist.forEachIndexed { i, e ->
            if (i > 0) KListDivider(true)
            SEventRow(e, null)
        }
    }
}

/** Munafa: 3 scenario (saavdhan / base / ummeed) x LTV, CPI, margin, payback. Label: aapka data + aapki maanyataayein. */
@Composable
private fun ProfitPanel(pf: JSONObject?) {
    if (pf == null) return
    val c = Mfc.c
    val portfolio = pf.s("basis") == "portfolio"
    KSection("Profitability".t(), trailing = {
        KPill(if (portfolio) "portfolio average".t() else pf.s("category").ifBlank { "–" }, if (portfolio) KKind.WARN else KKind.NEUTRAL, icon = null)
    })
    KCard {
        if (pf.s("status") != "ok") {
            KStatus("Not enough data".t(), KKind.WARN)
            pf.sn("why")?.let { SText(it, 14.sp, c.ink2, Modifier.padding(top = 6.dp), lh = 20.sp, maxLines = 6) }
        } else {
            if (portfolio) KStatus("Weak proxy".t(), KKind.WARN, Modifier.padding(bottom = 8.dp))
            val sc = pf.obj("scenarios")
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                SText("", 13.sp, c.ink3, Modifier.weight(1.4f))
                listOf("Conservative", "Base", "Optimistic").forEach {
                    SText(it.t(), 13.sp, c.ink3, Modifier.weight(1f), weight = FontWeight.SemiBold, lh = 18.sp, maxLines = 2, align = TextAlign.End)
                }
            }
            listOf(
                "ltv_usd" to "LTV / install", "cpi_usd" to "CPI", "margin_per_install" to "Margin / install", "payback_installs" to "Installs to pay dev cost",
            ).forEach { (k, l) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    SText(l.t(), 14.sp, c.ink2, Modifier.weight(1.4f), lh = 18.sp, maxLines = 2)
                    listOf("conservative", "base", "optimistic").forEach { n ->
                        val v = sc.obj(n).dn(k)
                        val col = when {
                            v == null -> c.ink3
                            k == "margin_per_install" -> if (v >= 0) c.green else c.red
                            else -> c.ink
                        }
                        SText(
                            when {
                                v == null -> "–"
                                k == "payback_installs" -> num(v)
                                else -> usd(v, 3)
                            },
                            14.sp, col, Modifier.weight(1f), weight = FontWeight.SemiBold, lh = 18.sp, align = TextAlign.End,
                        )
                    }
                }
            }
            sc.obj("base").sn("paid_users_basis")?.let { SText("Paid users".t() + ": " + it, 13.sp, c.ink2, Modifier.padding(top = 6.dp), lh = 18.sp, maxLines = 3) }
            val bt = pf.obj("by_type")
            listOf("organic", "paid").forEach { t ->
                bt.optJSONObject(t)?.let { r ->
                    SText(
                        engineText(t) + ": " + num(r.d("installs")) + " " + "installs".t() + " · D1 " + pct(r.dn("d1"), 0) + " · D7 " + pct(r.dn("d7"), 0) +
                            " · CPI " + usd(r.dn("cpi_usd"), 3) + (if (!r.b("sufficient")) " · " + "too few".t() else ""),
                        13.sp, if (r.b("sufficient")) c.ink2 else c.amber, Modifier.padding(top = 4.dp), lh = 18.sp, maxLines = 2,
                    )
                }
            }
            pf.sn("why")?.let { SText(it, 13.sp, c.amber, Modifier.padding(top = 4.dp), lh = 18.sp, maxLines = 4) }
        }
        val a = pf.obj("assumptions")
        SText(
            "Assumptions".t() + ": LTV ×" + a.arr("ltv_multiplier").strings().joinToString("/") + " · " + "dev cost".t() + " " + a.obj("dev_cost_usd").toString() +
                (a.sn("note")?.let { ". $it" } ?: ""),
            13.sp, c.ink3, Modifier.padding(top = 8.dp), lh = 18.sp, maxLines = 4,
        )
        KStatus("Your data + your assumptions".t(), KKind.NEUTRAL, Modifier.padding(top = 4.dp))
    }
}

// ---------------------------------------------------------------- compare

@Composable
fun StudioCompareScreen(vm: StudioVm, nav: NavHostController, ids: String) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("ids" to ids)
    LaunchedEffect(ids) { vm.load("compare", p) }
    val o = g.view("compare", p)
    KScreen(top = {
        KDetailBar(
            "Compare ideas".t(), sBack(nav), sub = o?.let { tf("%s ideas", it.arr("items").length()) } ?: sUpdated(g),
            actions = { RefreshIcon { vm.load("compare", p) } },
        )
    }) {
        SLoad(g, o, { vm.load("compare", p) }, {
            KSkeleton {
                KSkelStats(6, 3)
                KSkelRows(3)
            }
        }) { oo ->
            val items = oo.arr("items").objs()
            if (items.isEmpty()) KCard { KEmpty("No ideas".t(), KIcons.Bulb) }
            else {
                CompareTable(items)
                KCard(list = true) {
                    items.forEachIndexed { i, x ->
                        if (i > 0) KListDivider(true)
                        IdeaRow(x, "best", null, null) { nav.navigate("studio/opp/${x.l("id")}") }
                    }
                }
            }
        }
    }
}

/** Ideas aamne-saamne: har idea ek column (chhoti screen par side scroll). */
@Composable
private fun CompareTable(items: List<JSONObject>) {
    val c = Mfc.c
    val rows = listOf<Pair<String, (JSONObject) -> String>>(
        "Organic".t() to { score(it.dn("organic")) }, "Paid".t() to { score(it.dn("paid")) }, "Combined".t() to { score(it.dn("combined")) },
        "Confidence".t() to { it.s("confidence").ifBlank { "–" } }, "Data coverage".t() to { it.dn("coverage")?.let { x -> pct(x, 0) } ?: "–" },
        "Recommendation".t() to { it.sn("final_rec")?.let { r -> recText(r) } ?: "–" }, "Complexity".t() to { it.s("complexity").ifBlank { "–" } },
        "Base margin / install".t() to { it.optJSONObject("profit")?.obj("scenarios")?.obj("base")?.dn("margin_per_install")?.let { m -> usd(m, 3) } ?: "–" },
        "Verified factors".t() to { num(it.obj("labels").d("VERIFIED")) }, "Unknown factors".t() to { num(it.obj("labels").d("UNKNOWN")) },
    )
    KCard(padding = PaddingValues(vertical = 8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // phone: 96dp column + side scroll; Fold: column khinch kar poori chaudai (khaali jagah nahi)
            val colW = maxOf(96.dp, (maxWidth - 32.dp - 132.dp) / items.size.coerceAtLeast(1))
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.Bottom) {
                    SText("", 13.sp, c.ink3, Modifier.width(132.dp))
                    items.forEach { x ->
                        SText(x.s("title").ifBlank { "OPP-" + x.l("id") }, 13.sp, c.ink, Modifier.width(colW).padding(start = 8.dp), weight = FontWeight.Bold, lh = 18.sp, maxLines = 2)
                    }
                }
                rows.forEachIndexed { ri, (l, f) ->
                    if (ri > 0) KListDividerPlain(132.dp + colW * items.size)
                    Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        SText(l, 14.sp, c.ink2, Modifier.width(132.dp), lh = 18.sp, maxLines = 2)
                        items.forEach { x ->
                            val v = f(x)
                            val col = when (ri) {
                                5 -> x.sn("final_rec")?.let { r -> recColor(r) } ?: c.ink3
                                3 -> if (v == "–") c.ink3 else stColor(v)
                                else -> if (v == "–") c.ink3 else c.ink
                            }
                            SText(v, 14.sp, col, Modifier.width(colW).padding(start = 8.dp), weight = FontWeight.SemiBold, lh = 18.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

/** Table ki patli line (fixed chaudai, side scroll ke saath). */
@Composable
private fun KListDividerPlain(w: Dp) {
    Box(Modifier.width(w).height(1.dp).background(Mfc.c.line))
}

// ---------------------------------------------------------------- project (gates + Gate 2 spec review)

@Composable
fun StudioProjectScreen(vm: StudioVm, nav: NavHostController, id: Long) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("id" to id.toString())
    SPoll(30_000, id) { vm.load("project", p) }
    var ask by remember { mutableStateOf<String?>(null) }
    val o = g.view("project", p)
    val pr = o?.obj("item")
    val wide = isWide()
    KScreen(top = {
        KDetailBar(
            pr?.s("name")?.ifBlank { null } ?: "Project".t(), sBack(nav),
            sub = pr?.let { projSub(it) } ?: sUpdated(g),
            actions = { RefreshIcon { vm.load("project", p) } },
        )
        if (!wide && pr != null) ProjectHero(pr)
        else if (!wide && sSkelOn(g, o)) KSkelHero(KTint.Lav)
    }) {
        SLoad(g, o, { vm.load("project", p) }, { SDetailSkel(wide) }) { oo ->
            val item = oo.obj("item")
            val ft = item.optJSONObject("features")
            val onAsk: (String) -> Unit = { ask = it }
            if (wide) KTwoPane(
                left = {
                    ProjectHero(item)
                    ProjDecision(item, ft, onAsk)
                    ProjGates(item, nav)
                    ProjLog(item, oo, nav, onAsk)
                },
                right = { ProjSpec(item, ft) },
            ) else {
                ProjDecision(item, ft, onAsk)
                ProjGates(item, nav)
                ProjSpec(item, ft)
                ProjLog(item, oo, nav, onAsk)
            }
        }
    }
    val d = ask
    if (d != null) {
        val ft = pr?.optJSONObject("features")
        val ver = ft?.d("version")?.toInt() ?: 0
        val title = when (d) {
            "APPROVE" -> tf("Approve spec v%s?", ver)
            "REQUEST_CHANGES" -> "Request changes".t()
            "REJECT" -> "Reject project?".t()
            else -> "Cancel project?".t()
        }
        val body = when (d) {
            "APPROVE" -> "Gate 2 approved for this exact version. Design (Gate 3) is not in this release, so nothing more starts automatically.".t()
            "REQUEST_CHANGES" -> "Write what to change. A new version is prepared and comes back to you for approval.".t()
            else -> "The project stops. Reason is saved in history.".t()
        }
        val key = if (d == "CANCEL") "project_cancel" else "gate"
        TextAskDialog(
            title, body, if (d == "APPROVE") "Note (optional)".t() else "Reason / feedback (required)".t(), if (d == "APPROVE") "Approve".t() else "Send".t(),
            required = d != "APPROVE", busy = key in g.busy, danger = d == "REJECT" || d == "CANCEL", onDismiss = { ask = null },
        ) { txt ->
            if (d == "CANCEL") vm.act("project_cancel", mapOf("id" to id.toString(), "reason" to txt), key, ok = "Project cancelled".t()) { ask = null; vm.load("project", p) }
            else if (ft != null) vm.act(
                "gate",
                mapOf(
                    "project_id" to id.toString(), "gate" to "2", "artifact_id" to ft.l("artifact_id").toString(), "hash" to ft.s("hash"),
                    "decision" to d, "feedback" to txt,
                ),
                key, ok = "Saved".t(),
            ) { ask = null; vm.load("project", p) }
        }
    }
}

/** "PRJ-004 · Features pending · IN US · since 20 Oct". */
private fun projSub(pr: JSONObject): String = listOfNotNull(
    pr.s("puid").ifBlank { null }, stText(pr.s("stage")), pr.arr("countries").strings().filter { it.isNotBlank() }.joinToString(" ").ifBlank { null },
    "since".t() + " " + tFull(pr.s("created_at")),
).joinToString(" · ")

/** Lavender hero: kitne gates approve (x/8) + patti + agla gate / stage pill + blocker. */
@Composable
private fun ProjectHero(pr: JSONObject) {
    KHero(KTint.Lav) {
        val gates = pr.arr("gates").objs()
        val done = gates.count { it.s("state") == "APPROVED" }
        val ng = pr.dn("next_gate")?.toInt()
        val stage = pr.s("stage")
        KHeroHead("Gates approved".t(), icon = KIcons.Shield, trailing = {
            if (ng != null) SHeroPill(tf("Gate %s", ng) + " · " + KKind.ASK.word(), KKind.ASK)
            else KPill(stText(stage), stKind(stage))
        })
        KBigNum(num(done.toDouble()), suffix = "/" + num(gates.size.toDouble()), size = 48.sp)
        SGateStrip(gates, Modifier.padding(top = 12.dp))
        pr.sn("blocker")?.let { KStatus(it, KKind.WARN, Modifier.padding(top = 12.dp)) }
    }
}

/** Gate 2 ka faisla (sirf jab wait ho): spec version, kya badla, Approve / Badlav / Reject. */
@Composable
private fun ProjDecision(pr: JSONObject, ft: JSONObject?, onAsk: (String) -> Unit) {
    if (ft == null || pr.dn("next_gate")?.toInt() != 2) return
    val c = Mfc.c
    KSection(tf("Your decision on spec v%s", ft.d("version").toInt()), trailing = { KPill(KKind.ASK.word(), KKind.ASK) })
    KCard {
        Bullets("What changed".t(), ft.obj("content").arr("changes").strings(), c.brand)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KButton("Approve".t(), { onAsk("APPROVE") }, Modifier.weight(1f), icon = KIcons.Check)
            KButton("Request changes".t(), { onAsk("REQUEST_CHANGES") }, Modifier.weight(1f), kind = KBtn.SOFT)
        }
        KButton("Reject project".t(), { onAsk("REJECT") }, Modifier.fillMaxWidth().padding(top = 10.dp), kind = KBtn.OUTLINE)
    }
}

/** 8 gates (number tile + naam + state · version · kaun · kab) + asli idea. */
@Composable
private fun ProjGates(pr: JSONObject, nav: NavHostController) {
    KSection("Approval gates".t())
    KCard(list = true) {
        pr.arr("gates").objs().forEachIndexed { i, gt ->
            if (i > 0) KListDivider(true)
            val st = gt.s("state")
            val k = gateKind(st)
            val sub = when {
                gt.sn("by") != null -> listOfNotNull(stText(st), gt.dn("version")?.let { "v" + it.toInt() }, gt.s("by"), tFull(gt.s("at"))).joinToString(" · ")
                !gt.b("available") -> stText(st) + " · " + "Later release".t()
                else -> stText(st)
            }
            KRow(gt.s("name"), sub = sub, subKind = k, lead = { SGateTile(gt.d("gate").toInt(), k) }, titleLines = 2)
        }
        KListDivider(true)
        KRow(
            "Original idea".t(), lead = { KIconTile(KIcons.Bulb) }, chevron = true,
            onClick = { nav.navigate("studio/opp/${pr.l("opp_id")}") },
        )
    }
    NeverPublishNote()
}

/** Gate 2 spec: decision card, strategy, top 3 competitors, tulna, features (priority), lists, saboot. */
@Composable
private fun ProjSpec(pr: JSONObject, ft: JSONObject?) {
    val c = Mfc.c
    if (ft == null) {
        KCard(list = true) {
            KRow("Competitor analysis is being prepared".t(), lead = { KIconTile(KIcons.Clock, KKind.ASK) }, titleLines = 2)
        }
        return
    }
    val cc = ft.obj("content")
    val waiting2 = pr.dn("next_gate")?.toInt() == 2
    KSection("Gate 2: feature scope".t(), trailing = {
        KPill("v" + ft.d("version").toInt() + " · " + sAgo(ft.s("created_at")), KKind.NEUTRAL, icon = null)
    })
    val changes = cc.arr("changes").strings().filter { it.isNotBlank() }
    if (!waiting2 && changes.isNotEmpty()) KCard { Bullets("What changed".t(), changes, c.brand) }
    DecisionCard(cc.obj("decision_card"))
    if (!cc.sn("strategy").isNullOrBlank() || !cc.sn("effort_summary").isNullOrBlank()) KCard {
        Para("Strategy".t(), cc.sn("strategy"))
        Para("Effort".t(), cc.sn("effort_summary"))
    }
    val comps = cc.arr("competitors").objs()
    if (comps.isNotEmpty()) {
        KSection("Top 3 competitors".t())
        comps.forEach { CompetitorCard(it) }
    }
    val cmp = cc.obj("comparison")
    if (cmp.length() > 0) {
        KSection("Comparison".t())
        KCard {
            listOf(
                "all_do_well" to "All do well", "users_need" to "Users need", "repeated_complaints" to "Repeated complaints", "too_complex" to "Too complex",
                "missing" to "Missing in all", "simplify" to "We can simplify", "avoid_technical" to "Technical traps to avoid", "differentiators" to "Our differentiators",
            ).forEach { (k, l) -> Bullets(l.t(), cmp.arr(k).strings(), if (k == "differentiators") c.brand else null) }
        }
    }
    val feats = cc.arr("features").objs()
    if (feats.isNotEmpty()) {
        KSection("Features".t(), trailing = { KPill(num(feats.size.toDouble()), KKind.NEUTRAL, icon = null) })
        listOf("MUST HAVE", "SHOULD HAVE", "OPTIONAL", "REJECT").forEach { pri ->
            val fs = feats.filter { it.s("priority") == pri }
            if (fs.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    KPill(priText(pri) + " · " + fs.size, priKind(pri), icon = null)
                }
                KCard(list = true) {
                    fs.forEachIndexed { i, f ->
                        if (i > 0) KListDivider()
                        FeatureRow(f)
                    }
                }
            }
        }
    }
    val lists = listOf(
        Triple("Do not build", cc.arr("avoid").strings(), c.zRed), Triple("Permissions", cc.arr("permissions_summary").strings(), null),
        Triple("Technical limits", cc.arr("technical_limits").strings(), null), Triple("Monetization options", cc.arr("monetization_options").strings(), c.zGreen),
        Triple("Risks", cc.arr("risks").strings(), c.zRed),
    )
    if (lists.any { (_, l, _) -> l.any { it.isNotBlank() } }) KCard {
        lists.forEach { (l, items, col) -> Bullets(l.t(), items, col) }
    }
    EvidenceBlock(pr.arr("evidence").objs())
}

private fun priText(p: String): String = when (p) {
    "MUST HAVE" -> "Must have".t()
    "SHOULD HAVE" -> "Should have".t()
    "OPTIONAL" -> "Optional".t()
    "REJECT" -> "Rejected".t()
    else -> p
}

private fun priKind(p: String): KKind = when (p) {
    "MUST HAVE" -> KKind.OK
    "SHOULD HAVE" -> KKind.ASK
    "REJECT" -> KKind.BAD
    else -> KKind.NEUTRAL
}

/** Versions, approvals, runs, history + project cancel. */
@Composable
private fun ProjLog(pr: JSONObject, o: JSONObject, nav: NavHostController, onAsk: (String) -> Unit) {
    val vers = pr.arr("versions").objs()
    if (vers.isNotEmpty()) {
        KSection("Versions".t())
        KCard(list = true) {
            vers.forEachIndexed { i, v ->
                if (i > 0) KListDivider()
                KRow(
                    v.s("kind") + " v" + v.d("version").toInt(), sub = v.sn("change_note"),
                    value = sAgo(v.s("created_at")), valuePlain = true,
                )
            }
        }
    }
    val apr = pr.arr("approvals").objs()
    if (apr.isNotEmpty()) {
        KSection("Approvals".t())
        KCard(list = true) {
            apr.forEachIndexed { i, a ->
                if (i > 0) KListDivider()
                KRow(
                    tf("Gate %s", a.d("gate").toInt()) + " · v" + a.d("artifact_version").toInt(),
                    sub = decisionText(a.s("decision")), subKind = stKind(a.s("decision")),
                    value = a.s("user").ifBlank { "–" }, valuePlain = true, valueSub = tFull(a.s("created_at")),
                )
                a.sn("feedback")?.let { SNote(it, maxLines = 4) }
            }
        }
    }
    val runs = pr.arr("runs").objs()
    if (runs.isNotEmpty()) {
        KSection("Runs".t())
        KCard(list = true) {
            runs.forEachIndexed { i, r ->
                if (i > 0) KListDivider(true)
                SRunRow(r, nav)
            }
        }
    }
    HistoryBlock(o.arr("history").objs())
    if (pr.s("stage") !in setOf("CANCELLED", "REJECTED", "PUBLISHED")) {
        KButton("Cancel project".t(), { onAsk("CANCEL") }, Modifier.fillMaxWidth(), kind = KBtn.OUTLINE, icon = KIcons.Close)
    }
}

/** Competitor: naam, developer · package, rating · installs; tap = poora analysis. */
@Composable
private fun CompetitorCard(x: JSONObject) {
    val c = Mfc.c
    val open = rememberOpenUrl()
    var more by remember { mutableStateOf(false) }
    KCard(onClick = { more = !more }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KAvatar(x.s("name"))
            Column(Modifier.weight(1f)) {
                SText(x.s("name"), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
                val dev = listOfNotNull(x.s("developer").ifBlank { null }, x.s("package").ifBlank { null }).joinToString(" · ")
                if (dev.isNotBlank()) SText(dev, 13.sp, c.ink3, Modifier.padding(top = 2.dp), lh = 18.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                x.s("rating").ifBlank { null }?.let { SText("★ $it", 15.sp, c.ink, weight = FontWeight.Bold, lh = 20.sp) }
                x.s("installs_indicator").ifBlank { null }?.let { SText(it, 13.sp, c.ink3, lh = 18.sp) }
            }
            SExpandIcon(more)
        }
        x.sn("why_chosen")?.let { SText(it, 14.sp, c.ink2, Modifier.padding(top = 8.dp), lh = 20.sp, maxLines = if (more) Int.MAX_VALUE else 2) }
        if (more) {
            Para("Countries".t(), x.sn("countries")); Para("Updates".t(), x.sn("update_frequency")); Para("Navigation / UI".t(), x.sn("navigation_ui"))
            Bullets("Core features".t(), x.arr("core_features").strings()); Bullets("Secondary features".t(), x.arr("secondary_features").strings())
            Para("Monetization".t(), x.sn("monetization")); Para("Ads experience".t(), x.sn("ads_experience")); Para("Premium".t(), x.sn("premium"))
            Para("Technical limits".t(), x.sn("technical_limits")); Para("User satisfaction".t(), x.sn("satisfaction"))
            Bullets("Weak points".t(), x.arr("weak_points").strings(), c.zAmber)
            val cm = x.arr("complaints").objs()
            if (cm.isNotEmpty()) {
                SText("Complaints".t(), 13.sp, c.ink3, Modifier.padding(top = 6.dp), weight = FontWeight.SemiBold, lh = 18.sp)
                cm.forEach { y ->
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        KStatus(y.s("class").ifBlank { "–" }, if (y.s("class") == "USER-REPORTED ISSUE") KKind.WARN else KKind.NEUTRAL)
                        SText(y.s("issue") + (y.sn("frequency")?.let { " · $it" } ?: ""), 14.sp, c.ink, Modifier.padding(top = 2.dp), lh = 20.sp, maxLines = Int.MAX_VALUE)
                    }
                }
                SText("Reported, not tested".t(), 13.sp, c.ink3, Modifier.padding(top = 6.dp), lh = 18.sp)
            }
            x.sn("play_url")?.let { u ->
                Row(Modifier.padding(top = 8.dp)) { KSmallAction("Open on Google Play".t(), { open(u) }) }
            }
        }
    }
}

/** Feature: naam, mushkil · din, kya karta hai; tap = poori detail. Android par na bane to laal. */
@Composable
private fun FeatureRow(f: JSONObject) {
    val c = Mfc.c
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { more = !more }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SText(f.s("name"), 15.sp, c.ink, Modifier.weight(1f), weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
            val meta = listOfNotNull(f.s("difficulty").ifBlank { null }, f.dn("effort_days")?.let { num(it, 1) + " " + "days".t() }).joinToString(" · ")
            if (meta.isNotBlank()) SText(meta, 13.sp, c.ink3, lh = 18.sp)
            SExpandIcon(more)
        }
        f.sn("what")?.let { SText(it, 14.sp, c.ink2, Modifier.padding(top = 4.dp), lh = 20.sp, maxLines = if (more) Int.MAX_VALUE else 2) }
        if (!f.b("feasible_on_android")) KStatus("Not possible on Android".t(), KKind.BAD, Modifier.padding(top = 4.dp))
        if (more) {
            Para("Problem it solves".t(), f.sn("problem")); Para("Why add".t(), f.sn("why_add")); Para("Why not".t(), f.sn("why_not"))
            Para("Competitor evidence".t(), f.sn("competitor_evidence")); Para("User benefit".t(), f.sn("user_benefit")); Para("Business benefit".t(), f.sn("business_benefit"))
            Para("Implementation".t(), f.sn("implementation")); Para("Android APIs".t(), f.sn("android_apis"))
            Bullets("Permissions".t(), f.arr("permissions").strings()); Para("Privacy".t(), f.sn("privacy")); Para("Battery / performance".t(), f.sn("battery_performance"))
            Para("Monetization impact".t(), f.sn("monetization_impact")); Para("Risks / alternatives".t(), f.sn("risks_alternatives"))
            val ev = f.arr("evidence").strings().filter { it.isNotBlank() }
            if (ev.isNotEmpty()) SText("Evidence".t() + ": " + ev.joinToString(" ") { "#$it" }, 13.sp, c.ink3, Modifier.padding(top = 4.dp), lh = 18.sp, maxLines = 3)
        }
    }
}

// ---------------------------------------------------------------- run detail

@Composable
fun StudioRunScreen(vm: StudioVm, nav: NavHostController, id: Long) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("id" to id.toString())
    SPoll(5_000, id) { vm.load("run", p) }
    val o = g.view("run", p)
    val r = o?.obj("item")
    val wide = isWide()
    KScreen(top = {
        KDetailBar(
            r?.let { jobName(it.s("job")) } ?: ("Run".t() + " #$id"), sBack(nav),
            sub = r?.let { listOfNotNull(it.s("uid").ifBlank { null }, stText(it.s("trigger")), it.sn("by")).joinToString(" · ") } ?: sUpdated(g),
            actions = { RefreshIcon { vm.load("run", p) } },
        )
        if (!wide && r != null) RunHero(r)
        else if (!wide && sSkelOn(g, o)) KSkelHero(KTint.Lav)
    }) {
        SLoad(g, o, { vm.load("run", p) }, { SDetailSkel(wide) }) { oo ->
            val item = oo.obj("item")
            if (wide) KTwoPane(
                left = {
                    RunHero(item)
                    RunFacts(item, g.busy, { vm.act("cancel", mapOf("id" to id.toString()), "cancel", ok = "Cancel requested".t()) { vm.load("run", p) } })
                },
                right = {
                    RunUsage(oo.arr("usage").objs())
                    RunLog(oo.arr("log").objs())
                },
            ) else {
                RunFacts(item, g.busy, { vm.act("cancel", mapOf("id" to id.toString()), "cancel", ok = "Cancel requested".t()) { vm.load("run", p) } })
                RunUsage(oo.arr("usage").objs())
                RunLog(oo.arr("log").objs())
            }
        }
    }
}

/** Lavender hero: steps x/y + stage + patti, phir AI calls | searches | kharcha. */
@Composable
private fun RunHero(r: JSONObject) {
    KHero(KTint.Lav) {
        val cc = Mfc.c
        val st = r.s("status")
        val tot = r.d("steps_total")
        val done = r.d("steps_done")
        KHeroHead("Run".t() + " #" + r.l("id"), icon = KIcons.Play, trailing = { SStatusPill(st) })
        KBigNum(num(done), suffix = "/" + num(tot) + " " + "steps".t(), size = 48.sp)
        r.s("stage").ifBlank { null }?.let { SText(it, 14.sp, cc.ink2, lh = 20.sp, maxLines = 2) }
        if (tot > 0) KProgress(
            (done / tot).toFloat(),
            when (stKind(st)) {
                KKind.OK -> cc.zGreen
                KKind.BAD -> cc.zRed
                KKind.WARN -> cc.zAmber
                else -> cc.brand
            },
        )
        KSplit(listOf("AI calls".t() to num(r.d("ai_calls")), "Searches".t() to num(r.d("searches")), "Cost".t() to usd(r.d("cost_usd"), 3)))
    }
}

@Composable
private fun RunFacts(r: JSONObject, busy: Set<String>, onCancel: () -> Unit) {
    val c = Mfc.c
    KCard(padding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
        KKv("Queued".t(), tFull(r.s("queued_at")))
        KKv("Started".t(), r.sn("started_at")?.let { tFull(it) } ?: "–")
        KKv("Finished".t(), r.sn("finished_at")?.let { tFull(it) } ?: "–")
        KKv("Attempts".t(), num(r.d("attempts")))
    }
    r.sn("summary")?.let { KCard { Para("Summary".t(), it) } }
    r.sn("error_text")?.let {
        KCard {
            KStatus("Errors".t(), KKind.BAD)
            SText(it, 14.sp, c.ink, Modifier.padding(top = 4.dp), lh = 20.sp, maxLines = Int.MAX_VALUE)
        }
    }
    if (r.s("status") in setOf("QUEUED", "RUNNING")) {
        KButton("Cancel run".t(), onCancel, Modifier.fillMaxWidth(), kind = KBtn.OUTLINE, icon = KIcons.Close, busy = "cancel" in busy)
    }
}

/** Har AI call: kism, tokens in / out, searches, USD (fail = ✗). */
@Composable
private fun RunUsage(us: List<JSONObject>) {
    if (us.isEmpty()) return
    val c = Mfc.c
    KSection("AI usage".t(), trailing = { KPill(usd(us.sumOf { it.d("cost_usd") }, 3), KKind.NEUTRAL, icon = null) })
    val w = listOf(1.7f, 1f, 1f, .9f, 1.2f)
    KCard(padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Call".t(), "In".t(), "Out".t(), "Search".t(), "USD").forEachIndexed { i, h ->
                SText(h, 13.sp, c.ink3, Modifier.weight(w[i]), weight = FontWeight.SemiBold, lh = 18.sp, align = if (i == 0) TextAlign.Start else TextAlign.End)
            }
        }
        us.forEachIndexed { i, u ->
            val ok = u.b("ok")
            val cells = listOf(
                "${i + 1} " + u.s("kind") + if (!ok) " ✗" else "", num(u.d("in_tokens")), num(u.d("out_tokens")), num(u.d("searches")), usd(u.d("cost_usd"), 4),
            )
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                cells.forEachIndexed { j, v ->
                    SText(
                        v, 13.sp, if (j == 0 && !ok) c.red else c.ink, Modifier.weight(w[j]), weight = if (j == 4) FontWeight.SemiBold else FontWeight.Normal,
                        lh = 18.sp, align = if (j == 0) TextAlign.Start else TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun RunLog(lg: List<JSONObject>) {
    val c = Mfc.c
    KSection("Log".t())
    KCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        if (lg.isEmpty()) KEmpty("No log yet".t(), KIcons.History)
        lg.forEach { l ->
            val err = l.s("level") == "error"
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SText(tOnly(l.s("created_at")), 13.sp, c.ink3, Modifier.width(44.dp), lh = 18.sp)
                SText(l.s("msg"), 13.sp, if (err) c.red else c.ink2, Modifier.weight(1f), lh = 18.sp, maxLines = 8)
            }
        }
    }
}
