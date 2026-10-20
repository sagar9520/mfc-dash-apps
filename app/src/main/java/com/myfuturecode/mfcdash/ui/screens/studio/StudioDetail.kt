package com.myfuturecode.mfcdash.ui.screens.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.StudioVm
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.screens.gads.GLabel
import com.myfuturecode.mfcdash.ui.screens.gads.Small
import com.myfuturecode.mfcdash.ui.screens.gads.updatedText
import com.myfuturecode.mfcdash.ui.screens.health.*
import com.myfuturecode.mfcdash.ui.theme.Info
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*
import org.json.JSONObject

@Composable
private fun Frame(title: String, g: GState, nav: NavHostController, onRefresh: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HTopBar(title, updatedText(g), nav, busy = false, onRefresh = onRefresh)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp), content = content)
    }
}

// ---------------------------------------------------------------- idea (opportunity) detail

@Composable
fun StudioOppScreen(vm: StudioVm, nav: NavHostController, id: Long) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("id" to id.toString())
    Poll(60_000, id) { vm.load("opp", p) }
    var ask by remember { mutableStateOf<String?>(null) }
    Frame("OPP-$id", g, nav, { vm.load("opp", p) }) {
        SLoad(g, g.view("opp", p), { vm.load("opp", p) }) { o ->
            val it0 = o.obj("item")
            val ex = MaterialTheme.extra
            Text(it0.s("title"), style = MaterialTheme.typography.titleLarge)
            Small(listOfNotNull(it0.s("category").ifBlank { null }, it0.arr("countries").strings().joinToString(" ").ifBlank { null }, stText(it0.s("status")),
                "updated".t() + " " + ago(it0.s("updated_at"))).joinToString(" · "))
            Spacer(Modifier.height(8.dp))
            TileGrid(listOf(Triple("Organic".t(), score(it0.dn("organic")), it0.dn("rank_organic")?.let { "#" + it.toInt() }),
                Triple("Paid".t(), score(it0.dn("paid")), it0.dn("rank_paid")?.let { "#" + it.toInt() }),
                Triple("Data coverage".t(), it0.dn("coverage")?.let { pct(it, 0) } ?: "–", it0.s("confidence").ifBlank { "–" } + " " + "confidence".t())))
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recommendation".t(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    it0.sn("final_rec")?.let { Pill(it, recColor(it)) }
                }
                Small("AI said".t() + " " + (it0.sn("ai_rec") ?: "–") + " · " + "rules say".t() + " " + (it0.sn("rule_rec") ?: "–") + " · " + "the more cautious one is shown".t())
                it0.sn("complexity")?.let { Small("Build complexity".t() + ": " + it) }
                it0.sn("user_note")?.let { Small("Your note".t() + ": " + it) }
            }
            Spacer(Modifier.height(8.dp))
            // Gate 1 actions (sirf NEW / WATCH / REJECTED par)
            val st = it0.s("status")
            o.dn("project_id")?.let { pid ->
                Button(onClick = { nav.navigate("studio/project/${pid.toLong()}") }, modifier = Modifier.fillMaxWidth()) { Text("Open project".t()) }
                Spacer(Modifier.height(8.dp))
            }
            if (st == "NEW" || st == "WATCH") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ask = "APPROVE" }, modifier = Modifier.weight(1f)) { Text("Approve".t()) }
                    OutlinedButton(onClick = { ask = "REJECT" }, modifier = Modifier.weight(1f)) { Text("Reject".t()) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (st != "WATCH") OutlinedButton(onClick = { ask = "WATCH" }, modifier = Modifier.weight(1f)) { Text("Watch".t()) }
                    OutlinedButton(onClick = { ask = "MORE_RESEARCH" }, modifier = Modifier.weight(1f)) { Text("More research".t()) }
                }
                Spacer(Modifier.height(8.dp))
            } else if (st == "REJECTED") {
                OutlinedButton(onClick = { ask = "RESTORE" }, modifier = Modifier.fillMaxWidth()) { Text("Restore idea".t()) }
                Spacer(Modifier.height(8.dp))
            } else if (st == "EVALUATING") Panel { Small("Research is running for this idea. Results appear here when the run finishes.".t()) }

            Para("Concept".t(), it0.sn("concept"))
            Para("Why build it".t(), it0.sn("why_build"))
            Para("Why avoid it".t(), it0.sn("why_avoid"))
            Spacer(Modifier.height(8.dp))
            DecisionCard(it0.obj("decision_card"), it0.sn("confidence"))

            SectionTitle("Score factors".t(), "weight · rating 0-10 · label".t())
            Panel {
                it0.arr("factors").objs().forEach { f ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.s("label"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(f.dn("rating")?.let { num(it, 1) } ?: "–", style = MaterialTheme.typography.titleSmall, color = if (f.dn("rating") == null) ex.muted else MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(6.dp))
                            Pill(labelText(f.s("basis")), labelColor(f.s("basis")))
                        }
                        Small(listOfNotNull(stText(f.s("engine")), "w".t() + " " + num(f.d("weight")), if (f.s("higher_is") == "better") "higher is better".t() else "higher is worse".t(),
                            f.arr("evidence").strings().filter { it.isNotBlank() }.joinToString(" ") { "#$it" }.ifBlank { null }).joinToString(" · "))
                        f.sn("note")?.let { Small(it, maxLines = 4) }
                    }
                    Hairline()
                }
            }
            ProfitPanel(it0.optJSONObject("profit"))
            val comps = it0.arr("competitors").objs()
            if (comps.isNotEmpty()) {
                SectionTitle("Competitors seen".t())
                Panel { comps.forEach { c -> KV(c.s("name"), c.s("package").ifBlank { "–" }) } }
            }
            Panel {
                Bullets("Monetization".t(), it0.arr("monetization").strings())
                Bullets("Risks".t(), it0.arr("risks").strings(), ex.bad)
            }
            val ev = o.arr("evidence").objs()
            SectionTitle("Evidence".t(), ev.size.toString())
            Panel {
                if (ev.isEmpty()) Empty("No evidence stored.".t())
                ev.forEach { EvidenceRow(it) }
            }
            val dec = o.arr("decisions").objs()
            if (dec.isNotEmpty()) {
                SectionTitle("Your decisions".t())
                Panel { dec.forEach { d -> KV(stText(d.s("decision")) + (d.sn("feedback")?.let { " · $it" } ?: ""), d.s("user") + " · " + tFull(d.s("created_at"))) } }
            }
            val hist = o.arr("history").objs()
            if (hist.isNotEmpty()) {
                SectionTitle("History".t())
                Panel { hist.forEach { SEventRow(it, null) } }
            }
        }
    }
    ask?.let { d ->
        val title = when (d) { "APPROVE" -> "Approve (Gate 1)?".t(); "REJECT" -> "Reject this idea?".t(); "WATCH" -> "Watch this market?".t(); "MORE_RESEARCH" -> "Research more?".t(); else -> "Restore idea?".t() }
        val body = when (d) {
            "APPROVE" -> "Creates a project, then competitor analysis + feature spec are prepared for Gate 2. Nothing is built or published.".t()
            "REJECT" -> "The idea stays in history with your reason. You can restore it later.".t()
            "MORE_RESEARCH" -> "Runs another AI + web research pass for this idea (uses AI budget).".t()
            else -> "".t()
        }
        val busyKey = if (d == "APPROVE") "gate1" else "opp"
        TextAskDialog(title, body, if (d == "REJECT") "Reason (required)".t() else "Note (optional)".t(), title.removeSuffix("?"), required = d == "REJECT", busy = busyKey in g.busy,
            danger = d == "REJECT", onDismiss = { ask = null }) { note ->
            if (d == "APPROVE") vm.act("gate1", mapOf("select[0]" to id.toString(), "note" to note), "gate1", ok = "Approved. Competitor analysis queued.".t()) { j ->
                ask = null; vm.load("opp", p); j.optJSONArray("projects")?.optLong(0)?.takeIf { it > 0 }?.let { nav.navigate("studio/project/$it") }
            }
            else vm.act("opp", mapOf("id" to id.toString(), "decision" to d, "note" to note), "opp", ok = "Saved".t()) { ask = null; vm.load("opp", p) }
        }
    }
}

@Composable
private fun ProfitPanel(pf: JSONObject?) {
    if (pf == null) return
    val ex = MaterialTheme.extra
    SectionTitle("Profitability".t(), if (pf.s("basis") == "portfolio") "portfolio average".t() else pf.s("category"))
    Panel {
        if (pf.s("status") != "ok") {
            Text("Not enough data to judge profit".t(), style = MaterialTheme.typography.bodyMedium, color = ex.warn)
            Small(pf.s("why"))
        } else {
            if (pf.s("basis") == "portfolio") Small("No apps of yours in this category yet: numbers are your portfolio average, a weak proxy.".t(), ex.warn)
            val sc = pf.obj("scenarios")
            Row(Modifier.fillMaxWidth()) {
                Text("", modifier = Modifier.weight(1.4f))
                listOf("Conservative", "Base", "Optimistic").forEach { Text(it.t(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)) }
            }
            listOf("ltv_usd" to "LTV / install", "cpi_usd" to "CPI", "margin_per_install" to "Margin / install", "payback_installs" to "Installs to pay dev cost").forEach { (k, l) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(l.t(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.4f))
                    listOf("conservative", "base", "optimistic").forEach { n ->
                        val v = sc.obj(n).dn(k)
                        Text(when { v == null -> "–"; k == "payback_installs" -> num(v); else -> usd(v, 3) }, style = MaterialTheme.typography.bodySmall,
                            color = if (k == "margin_per_install" && v != null) (if (v >= 0) ex.good else ex.bad) else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    }
                }
            }
            sc.obj("base").sn("paid_users_basis")?.let { Small("Paid users".t() + ": " + it) }
            pf.obj("by_type").let { bt ->
                listOf("organic", "paid").forEach { t ->
                    bt.optJSONObject(t)?.let { r -> Small(stText(t) + ": " + num(r.d("installs")) + " " + "installs".t() + " · D1 " + pct(r.dn("d1"), 0) + " · D7 " + pct(r.dn("d7"), 0) +
                        " · CPI " + usd(r.dn("cpi_usd"), 3) + (if (!r.b("sufficient")) " · " + "too few".t() else "")) }
                }
            }
            if (pf.s("why").isNotBlank()) Small(pf.s("why"), ex.warn)
        }
        val a = pf.obj("assumptions")
        Small("Assumptions".t() + ": LTV ×" + a.arr("ltv_multiplier").strings().joinToString("/") + " · " + "dev cost".t() + " " + a.obj("dev_cost_usd").toString() + ". " + a.s("note"))
        Small("Label: VERIFIED internal data (your apps) + YOUR assumptions. Not a forecast.".t())
    }
    Spacer(Modifier.height(8.dp))
}

// ---------------------------------------------------------------- compare

@Composable
fun StudioCompareScreen(vm: StudioVm, nav: NavHostController, ids: String) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("ids" to ids)
    LaunchedEffect(ids) { vm.load("compare", p) }
    Frame("Compare ideas".t(), g, nav, { vm.load("compare", p) }) {
        SLoad(g, g.view("compare", p), { vm.load("compare", p) }) { o ->
            val items = o.arr("items").objs()
            val rows = listOf<Pair<String, (JSONObject) -> String>>(
                "Organic".t() to { score(it.dn("organic")) }, "Paid".t() to { score(it.dn("paid")) }, "Combined".t() to { score(it.dn("combined")) },
                "Confidence".t() to { it.s("confidence").ifBlank { "–" } }, "Data coverage".t() to { it.dn("coverage")?.let { c -> pct(c, 0) } ?: "–" },
                "Recommendation".t() to { it.s("final_rec") }, "Complexity".t() to { it.s("complexity").ifBlank { "–" } },
                "Base margin / install".t() to { it.optJSONObject("profit")?.obj("scenarios")?.obj("base")?.dn("margin_per_install")?.let { m -> usd(m, 3) } ?: "–" },
                "Verified factors".t() to { num(it.obj("labels").d("VERIFIED")) }, "Unknown factors".t() to { num(it.obj("labels").d("UNKNOWN")) },
            )
            Table(listOf("") + items.map { "OPP-" + it.l("id") }, rows.map { (l, f) -> listOf(l) + items.map(f) }, firstWeight = 1.4f)
            Spacer(Modifier.height(10.dp))
            items.forEach { OppCard(it, nav) }
        }
    }
}

// ---------------------------------------------------------------- project (gates timeline + Gate 2 spec review)

@Composable
fun StudioProjectScreen(vm: StudioVm, nav: NavHostController, id: Long) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("id" to id.toString())
    Poll(30_000, id) { vm.load("project", p) }
    var ask by remember { mutableStateOf<String?>(null) }
    Frame("Project".t(), g, nav, { vm.load("project", p) }) {
        SLoad(g, g.view("project", p), { vm.load("project", p) }) { o ->
            val pr = o.obj("item")
            val ex = MaterialTheme.extra
            Text(pr.s("name"), style = MaterialTheme.typography.titleLarge)
            Small(listOfNotNull(pr.s("puid"), stText(pr.s("stage")), pr.arr("countries").strings().joinToString(" ").ifBlank { null }, "since".t() + " " + tFull(pr.s("created_at"))).joinToString(" · "))
            TextButton(onClick = { nav.navigate("studio/opp/${pr.l("opp_id")}") }) { Text("Open the original idea".t()) }
            pr.sn("blocker")?.let { Panel { Text(it, color = ex.bad, style = MaterialTheme.typography.bodyMedium) }; Spacer(Modifier.height(8.dp)) }

            SectionTitle("Approval gates".t())
            Panel {
                pr.arr("gates").objs().forEach { gt ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(gt.d("gate").toInt().toString(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(gt.s("name"), style = MaterialTheme.typography.bodyMedium)
                            if (gt.sn("by") != null) Small(listOfNotNull(gt.dn("version")?.let { "v" + it.toInt() }, gt.s("by"), tFull(gt.s("at"))).joinToString(" · "))
                            else if (!gt.b("available")) Small("Coming in a later release".t())
                        }
                        Pill(stText(gt.s("state")), stColor(gt.s("state")))
                    }
                }
                Small("Publishing (Gate 8) always needs its own separate approval. Before any build you will be asked which Play Console account to use.".t())
            }

            val ft = pr.optJSONObject("features")
            val waiting2 = pr.dn("next_gate")?.toInt() == 2
            if (ft == null) {
                Spacer(Modifier.height(8.dp))
                Panel {
                    Text("Competitor analysis is being prepared".t(), style = MaterialTheme.typography.titleSmall)
                    Small("You get a notification when the feature spec is ready for Gate 2.".t())
                }
            } else {
                val c = ft.obj("content")
                SectionTitle("Gate 2: feature scope".t(), "v" + ft.d("version").toInt() + " · " + ago(ft.s("created_at")))
                val changes = c.arr("changes").strings()
                if (changes.isNotEmpty()) Panel { Bullets("What changed in this version".t(), changes, Info) }
                if (waiting2) {
                    Spacer(Modifier.height(6.dp))
                    Panel {
                        Text(tf("Your decision on spec v%s", ft.d("version").toInt()), style = MaterialTheme.typography.titleSmall)
                        Small("Approval is tied to this exact version. If the spec changes, you approve again.".t())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            Button(onClick = { ask = "APPROVE" }, modifier = Modifier.weight(1f)) { Text("Approve".t()) }
                            OutlinedButton(onClick = { ask = "REQUEST_CHANGES" }, modifier = Modifier.weight(1f)) { Text("Request changes".t()) }
                        }
                        TextButton(onClick = { ask = "REJECT" }) { Text("Reject project".t(), color = ex.bad) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                DecisionCard(c.obj("decision_card"))
                Panel { Para("Strategy".t(), c.sn("strategy")); Para("Effort".t(), c.sn("effort_summary")) }
                Spacer(Modifier.height(8.dp))

                SectionTitle("Top 3 competitors".t())
                c.arr("competitors").objs().forEach { cp -> CompetitorCard(cp) }

                val cmp = c.obj("comparison")
                if (cmp.length() > 0) {
                    SectionTitle("Comparison".t())
                    Panel {
                        listOf("all_do_well" to "All do well", "users_need" to "Users need", "repeated_complaints" to "Repeated complaints", "too_complex" to "Too complex",
                            "missing" to "Missing in all", "simplify" to "We can simplify", "avoid_technical" to "Technical traps to avoid", "differentiators" to "Our differentiators")
                            .forEach { (k, l) -> Bullets(l.t(), cmp.arr(k).strings()) }
                    }
                }

                SectionTitle("Features".t())
                listOf("MUST HAVE", "SHOULD HAVE", "OPTIONAL", "REJECT").forEach { pri ->
                    val fs = c.arr("features").objs().filter { it.s("priority") == pri }
                    if (fs.isNotEmpty()) {
                        GLabel(pri + " · " + fs.size)
                        fs.forEach { FeatureCard(it) }
                    }
                }
                Panel {
                    Bullets("Do not build".t(), c.arr("avoid").strings(), ex.bad)
                    Bullets("Permissions".t(), c.arr("permissions_summary").strings())
                    Bullets("Technical limits".t(), c.arr("technical_limits").strings())
                    Bullets("Monetization options".t(), c.arr("monetization_options").strings())
                    Bullets("Risks".t(), c.arr("risks").strings(), ex.bad)
                }
                Spacer(Modifier.height(8.dp))
                val ev = pr.arr("evidence").objs()
                SectionTitle("Evidence".t(), ev.size.toString())
                Panel { if (ev.isEmpty()) Empty("No evidence stored.".t()); ev.forEach { EvidenceRow(it) } }
            }

            val vers = pr.arr("versions").objs()
            if (vers.isNotEmpty()) {
                SectionTitle("Versions".t())
                Panel { vers.forEach { v -> KV(v.s("kind") + " v" + v.d("version").toInt() + (v.sn("change_note")?.let { " · $it" } ?: ""), ago(v.s("created_at"))) } }
            }
            val apr = pr.arr("approvals").objs()
            if (apr.isNotEmpty()) {
                SectionTitle("Approvals".t())
                Panel {
                    apr.forEach { a ->
                        KV("Gate".t() + " " + a.d("gate").toInt() + " · v" + a.d("artifact_version").toInt() + " · " + stText(a.s("decision")), a.s("user") + " · " + tFull(a.s("created_at")))
                        a.sn("feedback")?.let { Small(it, maxLines = 4) }
                    }
                }
            }
            val runs = pr.arr("runs").objs()
            if (runs.isNotEmpty()) {
                SectionTitle("Runs".t())
                Panel { runs.forEach { SRunRow(it, nav) } }
            }
            val hist = o.arr("history").objs()
            if (hist.isNotEmpty()) {
                SectionTitle("History".t())
                Panel { hist.forEach { SEventRow(it, null) } }
            }
            if (pr.s("stage") !in setOf("CANCELLED", "REJECTED", "PUBLISHED")) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { ask = "CANCEL" }) { Text("Cancel project".t(), color = ex.bad) }
            }

            ask?.let { d ->
                val ver = ft?.d("version")?.toInt() ?: 0
                val title = when (d) { "APPROVE" -> tf("Approve spec v%s?", ver); "REQUEST_CHANGES" -> "Request changes".t(); "REJECT" -> "Reject project?".t(); else -> "Cancel project?".t() }
                val body = when (d) {
                    "APPROVE" -> "Gate 2 approved for this exact version. Design (Gate 3) is not in this release, so nothing more starts automatically.".t()
                    "REQUEST_CHANGES" -> "Write what to change. A new version is prepared and comes back to you for approval.".t()
                    "REJECT" -> "The project stops. Reason is saved in history.".t()
                    else -> "The project stops. Reason is saved in history.".t()
                }
                val key = if (d == "CANCEL") "project_cancel" else "gate"
                TextAskDialog(title, body, if (d == "APPROVE") "Note (optional)".t() else "Reason / feedback (required)".t(), if (d == "APPROVE") "Approve".t() else "Send".t(),
                    required = d != "APPROVE", busy = key in g.busy, danger = d == "REJECT" || d == "CANCEL", onDismiss = { ask = null }) { txt ->
                    if (d == "CANCEL") vm.act("project_cancel", mapOf("id" to id.toString(), "reason" to txt), key, ok = "Project cancelled".t()) { ask = null; vm.load("project", p) }
                    else if (ft != null) vm.act("gate", mapOf("project_id" to id.toString(), "gate" to "2", "artifact_id" to ft.l("artifact_id").toString(), "hash" to ft.s("hash"),
                        "decision" to d, "feedback" to txt), key, ok = "Saved".t()) { ask = null; vm.load("project", p) }
                }
            }
        }
    }
}

@Composable
private fun CompetitorCard(c: JSONObject) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Panel(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.s("name"), style = MaterialTheme.typography.titleSmall)
                Small(listOfNotNull(c.s("developer").ifBlank { null }, c.s("package").ifBlank { null }).joinToString(" · "), maxLines = 1)
            }
            Small(listOfNotNull(c.s("rating").ifBlank { null }?.let { "★ $it" }, c.s("installs_indicator").ifBlank { null }).joinToString(" · "))
        }
        Small(c.s("why_chosen"), maxLines = if (open) Int.MAX_VALUE else 2)
        if (open) {
            Para("Countries".t(), c.sn("countries")); Para("Updates".t(), c.sn("update_frequency")); Para("Navigation / UI".t(), c.sn("navigation_ui"))
            Bullets("Core features".t(), c.arr("core_features").strings()); Bullets("Secondary features".t(), c.arr("secondary_features").strings())
            Para("Monetization".t(), c.sn("monetization")); Para("Ads experience".t(), c.sn("ads_experience")); Para("Premium".t(), c.sn("premium"))
            Para("Technical limits".t(), c.sn("technical_limits")); Para("User satisfaction".t(), c.sn("satisfaction"))
            Bullets("Weak points".t(), c.arr("weak_points").strings())
            val cm = c.arr("complaints").objs()
            if (cm.isNotEmpty()) {
                GLabel("Complaints".t())
                cm.forEach { x ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Pill(x.s("class"), if (x.s("class") == "USER-REPORTED ISSUE") MaterialTheme.extra.warn else MaterialTheme.extra.muted); Spacer(Modifier.width(6.dp))
                        Text(x.s("issue") + (x.sn("frequency")?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Small("Complaints are what users or sources report. Nothing was reproduced or confirmed by testing.".t())
            }
            c.sn("play_url")?.let { u -> TextButton(onClick = { openUrl(ctx, u) }) { Text("Open on Google Play".t()) } }
        } else Small("Tap for full analysis".t(), Info)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun FeatureCard(f: JSONObject) {
    var open by remember { mutableStateOf(false) }
    val ex = MaterialTheme.extra
    Panel(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.s("name"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Small(listOfNotNull(f.s("difficulty").ifBlank { null }, f.dn("effort_days")?.let { num(it, 1) + " " + "days".t() }).joinToString(" · "))
        }
        Small(f.s("what"), maxLines = if (open) Int.MAX_VALUE else 2)
        if (!f.b("feasible_on_android")) Small("Not possible with public Android APIs: rejected".t(), ex.bad)
        if (open) {
            Para("Problem it solves".t(), f.sn("problem")); Para("Why add".t(), f.sn("why_add")); Para("Why not".t(), f.sn("why_not"))
            Para("Competitor evidence".t(), f.sn("competitor_evidence")); Para("User benefit".t(), f.sn("user_benefit")); Para("Business benefit".t(), f.sn("business_benefit"))
            Para("Implementation".t(), f.sn("implementation")); Para("Android APIs".t(), f.sn("android_apis"))
            Bullets("Permissions".t(), f.arr("permissions").strings()); Para("Privacy".t(), f.sn("privacy")); Para("Battery / performance".t(), f.sn("battery_performance"))
            Para("Monetization impact".t(), f.sn("monetization_impact")); Para("Risks / alternatives".t(), f.sn("risks_alternatives"))
            val ev = f.arr("evidence").strings().filter { it.isNotBlank() }
            if (ev.isNotEmpty()) Small("Evidence".t() + ": " + ev.joinToString(" ") { "#$it" })
        }
    }
    Spacer(Modifier.height(6.dp))
}

// ---------------------------------------------------------------- run detail

@Composable
fun StudioRunScreen(vm: StudioVm, nav: NavHostController, id: Long) {
    val g by vm.s.collectAsStateWithLifecycle()
    val p = mapOf("id" to id.toString())
    Poll(5_000, id) { vm.load("run", p) }
    Frame("Run".t() + " #$id", g, nav, { vm.load("run", p) }) {
        SLoad(g, g.view("run", p), { vm.load("run", p) }) { o ->
            val r = o.obj("item")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(jobName(r.s("job")), style = MaterialTheme.typography.titleLarge)
                    Small(r.s("uid") + " · " + stText(r.s("trigger")) + (r.sn("by")?.let { " · $it" } ?: ""))
                }
                Pill(r.s("status"), stColor(r.s("status")))
            }
            Spacer(Modifier.height(8.dp))
            val tot = r.d("steps_total")
            if (r.s("status") == "RUNNING" && tot > 0) LinearProgressIndicator(progress = { (r.d("steps_done") / tot).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            TileGrid(listOf(Triple("Steps".t(), "${r.d("steps_done").toInt()}/${tot.toInt()}", r.s("stage")), Triple("AI calls".t(), num(r.d("ai_calls")), num(r.d("searches")) + " " + "searches".t()),
                Triple("Cost".t(), usd(r.d("cost_usd"), 3), tf("attempt %s", r.d("attempts").toInt()))))
            Panel {
                KV("Queued".t(), tFull(r.s("queued_at")))
                KV("Started".t(), r.sn("started_at")?.let { tFull(it) } ?: "–")
                KV("Finished".t(), r.sn("finished_at")?.let { tFull(it) } ?: "–")
                r.sn("summary")?.let { Para("Summary".t(), it) }
                r.sn("error_text")?.let { Para("Errors".t(), it) }
                if (r.s("status") in setOf("QUEUED", "RUNNING"))
                    BusyButton("Cancel run".t(), "cancel" in g.busy, outlined = true) { vm.act("cancel", mapOf("id" to id.toString()), "cancel", ok = "Cancel requested".t()) { vm.load("run", p) } }
            }
            val us = o.arr("usage").objs()
            if (us.isNotEmpty()) {
                SectionTitle("AI usage".t())
                Table(listOf("Call".t(), "In".t(), "Out".t(), "Search".t(), "USD"), us.mapIndexed { i, u ->
                    listOf("${i + 1} " + u.s("kind") + if (!u.b("ok")) " ✗" else "", num(u.d("in_tokens")), num(u.d("out_tokens")), num(u.d("searches")), usd(u.d("cost_usd"), 4))
                })
            }
            SectionTitle("Log".t())
            Panel {
                val lg = o.arr("log").objs()
                if (lg.isEmpty()) Empty("No log lines yet.".t())
                lg.forEach { l ->
                    Text(tOnly(l.s("created_at")) + "  " + l.s("msg"), style = MaterialTheme.typography.bodySmall,
                        color = if (l.s("level") == "error") MaterialTheme.extra.bad else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
