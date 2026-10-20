package com.myfuturecode.mfcdash.ui.screens.cost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.CostVm
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.screens.gads.Small
import com.myfuturecode.mfcdash.ui.screens.gads.updatedText
import com.myfuturecode.mfcdash.ui.screens.health.HTopBar
import com.myfuturecode.mfcdash.ui.screens.health.Loading
import com.myfuturecode.mfcdash.ui.theme.Info
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*
import org.json.JSONArray
import org.json.JSONObject

val CTABS = listOf("overview" to "Overview", "modules" to "Modules", "apps" to "Apps", "tasks" to "Tasks", "budgets" to "Budgets & limits")
private val RANGES = listOf("today" to "Today", "7d" to "7 days", "month" to "This month")
private val PROVIDERS = mapOf(
    "anthropic" to "Claude API (server)", "claude_code" to "Claude Code worker", "github_actions" to "GitHub Actions", "image" to "Image generation",
    "video" to "Video generation", "research_api" to "Research data API", "testlab" to "Firebase Test Lab", "github" to "GitHub (billed)", "other" to "Other",
)
private val TASKS = mapOf(
    "alert_explain" to "Alert explanation", "brief" to "Daily AI brief", "investigate" to "Crash / ANR investigation", "ai_fix" to "AI fix", "task" to "New task (Naya kaam)",
    "alert_ai" to "Google Ads alert AI", "daily_review" to "Google Ads daily review", "ads_ai" to "Google Ads AI",
)

/** ₹ pehle (Sagar ₹ me sochte hain), $ saath me (provider USD me bill karte hain). */
private fun money(usd: Double, r: Double, dec: Int = 0) = inr(usd * r, dec)
private fun money2(usd: Double, r: Double) = inr(usd * r) + " · " + usd(usd)

/** AI Cost & Usage Manager: aaj / 7 din / mahina, har module, har app, har AI task, budgets aur limits. */
@Composable
fun CostScreen(vm: CostVm, nav: NavHostController, startTab: String) {
    val g by vm.s.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (CTABS.any { it.first == startTab }) startTab else "overview") }
    var range by rememberSaveable { mutableStateOf("month") }
    val reload = remember { mutableIntStateOf(0) }
    LaunchedEffect(reload.intValue) { vm.load("month") }
    LaunchedEffect(reload.intValue, range, tab) { if ((tab == "apps" || tab == "tasks") && range != "month") vm.load(range) }
    val m = g.view("month")

    Column(Modifier.fillMaxSize()) {
        HTopBar("AI Cost & Usage".t(), m?.let { it.s("month") + " · ₹" + num(it.d("inr_per_usd"), 0) + " = $1 · " + updatedText(g) } ?: updatedText(g), nav,
            busy = g.busy.isNotEmpty(), onRefresh = { reload.intValue++ })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp)) {
            ChipRow { CTABS.forEach { (k, l) -> MChip(l.t(), tab == k, { tab = k }) } }
            Spacer(Modifier.height(10.dp))
            if (m == null) { if (g.error != null) CProblem(g) { reload.intValue++ } else Loading(); return@Column }
            val r = m.d("inr_per_usd", 95.0)
            when (tab) {
                "overview" -> Overview(m, r, nav) { tab = it }
                "modules" -> Modules(vm, g, m, r)
                "apps", "tasks" -> {
                    ChipRow { RANGES.forEach { (k, l) -> MChip(l.t(), range == k, { range = k }) } }
                    Spacer(Modifier.height(10.dp))
                    val j = g.view(range)
                    if (j == null) Loading() else if (tab == "apps") Apps(j, r) else Tasks(j, r)
                }
                "budgets" -> Budgets(vm, g, m, r)
            }
        }
    }
}

@Composable
private fun CProblem(g: GState, onRetry: () -> Unit) {
    Panel {
        if (g.missing) {
            Text("AI Cost & Usage is not on the server yet".t(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Small("Upload api/cost.php and lib/cost.php to mfc_dash (see the setup guide), then tap Try again.".t())
        } else Text(g.error ?: "No data received".t(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("Try again".t()) }
    }
}

// ---------------------------------------------------------------- bits

@Composable
private fun pctColor(p: Double?): Color {
    val ex = MaterialTheme.extra
    return when { p == null -> ex.muted; p >= 1.0 -> ex.bad; p >= 0.9 -> ex.bad; p >= 0.7 -> ex.warn; else -> ex.good }
}

/** Budget bar: kharch hissa + 70% / 90% ke nishaan. */
@Composable
fun BudgetBar(p: Double?, height: Int = 8, marks: Boolean = false) {
    val c = pctColor(p)
    val track = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(height.dp)).background(track)) {
        Box(Modifier.fillMaxHeight().width(maxWidth * (p ?: 0.0).coerceIn(0.0, 1.0).toFloat()).clip(RoundedCornerShape(height.dp)).background(c))
        if (marks) listOf(0.7 to MaterialTheme.extra.warn, 0.9 to MaterialTheme.extra.bad).forEach { (x, col) ->
            Box(Modifier.offset(x = maxWidth * x.toFloat()).width(2.dp).fillMaxHeight().background(col))
        }
    }
}

@Composable
private fun Tag(billed: Boolean) = Pill(if (billed) "BILLED".t() else "ESTIMATE".t(), if (billed) Info else MaterialTheme.extra.muted)

private fun JSONArray.objs(): List<JSONObject> = mapObj { it }

// ---------------------------------------------------------------- Overview

@Composable
private fun Overview(m: JSONObject, r: Double, nav: NavHostController, goTab: (String) -> Unit) {
    val ex = MaterialTheme.extra
    TileGrid(listOf(
        Triple("Today".t(), money(m.d("today_usd"), r), usd(m.d("today_usd"))),
        Triple("7 days".t(), money(m.d("d7_usd"), r), usd(m.d("d7_usd"))),
        Triple("This month".t(), money(m.d("month_usd"), r), usd(m.d("month_usd"))),
    ))
    Spacer(Modifier.height(10.dp))
    val budget = m.d("budget_usd")
    val p = m.dn("pct")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Monthly budget".t(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(money(budget, r) + " (" + usd(budget, 0) + ")", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        BudgetBar(p, 12, marks = true)
        Spacer(Modifier.height(8.dp))
        KV("Used".t(), (if (p == null) "–" else pct(p, 0)) + " · " + money2(m.d("month_usd"), r), pctColor(p), bold = true)
        val rem = m.d("remaining_usd")
        KV("Remaining".t(), money2(rem, r), if (rem < 0) ex.bad else ex.good)
        val fc = m.d("forecast_usd")
        KV("Forecast month-end".t(), money2(fc, r), if (budget > 0 && fc > budget) ex.bad else if (budget > 0 && fc > budget * 0.9) ex.warn else null, bold = true)
        if (m.d("fixed_usd") > 0) KV("Fixed bills (hosting etc.)".t(), money2(m.d("fixed_usd"), r))
        Small(tf("Warnings at 70%%, 90%% and 100%% (app + email). Critical reserve %s%% only for urgent monitoring.", m.l("reserve_pct")))
    }
    Spacer(Modifier.height(10.dp))

    SectionTitle("Billed vs estimate".t(), "this month".t())
    Panel {
        val billed = m.arr("billed").objs()
        billed.forEach { b -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
            Tag(true); Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) { Text((PROVIDERS[b.s("provider")] ?: b.s("provider")).t(), style = MaterialTheme.typography.bodyMedium); Small("synced".t() + " " + tFull(b.s("synced_at"))) }
            Text(money(b.d("usd"), r), fontWeight = FontWeight.SemiBold)
        } }
        m.arr("fixed").objs().filter { it.d("usd") > 0 }.forEach { f -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
            Tag(true); Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) { Text(f.s("name"), style = MaterialTheme.typography.bodyMedium); Small("fixed monthly bill (you entered)".t()) }
            Text(money(f.d("usd"), r), fontWeight = FontWeight.SemiBold)
        } }
        val est = m.obj("estimate_by_provider")
        val keys = est.keys().asSequence().toList().sortedByDescending { est.d(it) }
        keys.forEach { k -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
            Tag(false); Spacer(Modifier.width(6.dp))
            Text((PROVIDERS[k] ?: k).t(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(money(est.d(k), r), fontWeight = FontWeight.SemiBold)
        } }
        if (keys.isEmpty() && billed.isEmpty()) Empty("No paid AI usage logged this month yet.".t())
        val kk = m.obj("keys")
        if (kk.s("admin_key").isBlank()) Small("Estimate = tokens × list price. Add an Anthropic Admin API key in Budgets & limits to also see the billed amount.".t())
        kk.sn("sync_error")?.let { Small(it, MaterialTheme.extra.bad) }
    }
    Spacer(Modifier.height(10.dp))

    SectionTitle("By module".t(), "spent / budget".t())
    Panel(onClick = { goTab("modules") }) {
        m.arr("modules").objs().forEach { x ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(x.s("name").t(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (x.b("paused")) { Pill("PAUSED".t(), MaterialTheme.extra.warn); Spacer(Modifier.width(6.dp)) }
                Text(money(x.d("month_usd"), r) + " / " + money(x.d("budget"), r), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp)); BudgetBar(x.dn("pct")); Spacer(Modifier.height(8.dp))
        }
    }
    val paused = m.arr("paused").objs()
    if (paused.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        SectionTitle("Paused jobs".t(), "last 3 days".t())
        Panel {
            paused.forEach { x ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(((TASKS[x.s("task")] ?: x.s("task").ifBlank { x.s("module") })).t(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Pill(tf("%s× skipped", x.l("hits")), MaterialTheme.extra.warn)
                }
                Small(x.s("why")); Small(tFull(x.s("last_at"))); Spacer(Modifier.height(6.dp))
            }
            Small("Critical monitoring (rules, scans, alerts) keeps running. Resume a module only with your OK in Modules.".t())
        }
    }
}

// ---------------------------------------------------------------- Modules

@Composable
private fun Modules(vm: CostVm, g: GState, m: JSONObject, r: Double) {
    m.arr("modules").objs().forEach { x ->
        val key = x.s("key")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(x.s("name").t(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(money(x.d("month_usd"), r) + " / " + money(x.d("budget"), r), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp)); BudgetBar(x.dn("pct"), 10, marks = true); Spacer(Modifier.height(6.dp))
            KV("Today".t(), money2(x.d("today_usd"), r))
            KV("AI calls today".t(), x.l("calls_today").toString() + (if (x.l("cap") > 0) " / " + x.l("cap") else ""))
            if (key == "health" && x.l("fix_cap") > 0) KV("AI fix / task runs per day (max)".t(), x.l("fix_cap").toString())
            x.arr("counts").objs().sortedByDescending { it.l("n") }.take(6).forEach { c ->
                Small("• " + (TASKS[c.s("task")] ?: c.s("task").ifBlank { "other" }).t() + " · " + (PROVIDERS[c.s("provider")] ?: c.s("provider")).t() + " · " + c.l("n") + "×")
            }
            val until = x.l("override_until")
            val now = System.currentTimeMillis() / 1000
            if (x.b("paused")) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Pill("PAUSED".t(), MaterialTheme.extra.warn); Spacer(Modifier.width(6.dp)); Small(x.s("paused_why"), maxLines = 3) }
                Spacer(Modifier.height(6.dp))
                val why = x.s("paused_why")
                if (why.contains("failed AI calls")) OutlinedButton(onClick = { vm.act("breaker", mapOf("module" to key), "Retry allowed".t()) }) { Text("Allow retry now".t()) }
                else if (!why.contains("reserve")) OutlinedButton(onClick = { vm.act("resume", mapOf("module" to key, "hours" to "24"), "Resumed for 24 hours".t()) }) { Text("Resume for 24 hours (my OK)".t()) }
                else Small("Raise the total budget in Budgets & limits to run more this month.".t())
            } else if (until > now) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("OVER BUDGET · RESUMED".t(), Info); Spacer(Modifier.width(6.dp))
                    Small(tf("until %s", java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.US).format(java.util.Date(until * 1000))), modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.act("pause_now", mapOf("module" to key), "Budget rules back on".t()) }) { Text("Stop".t()) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Small("Always on, free: rule engines, Play / Ads scans, crash and ANR detection, alerts. Only paid AI work pauses.".t())
}

@Composable
private fun Small(t: String, color: Color? = null, maxLines: Int = Int.MAX_VALUE, modifier: Modifier) {
    Box(modifier) { Small(t, color, maxLines) }
}

// ---------------------------------------------------------------- Apps

@Composable
private fun Apps(j: JSONObject, r: Double) {
    val apps = j.arr("apps").objs()
    Panel {
        if (apps.isEmpty()) Empty("No app-level AI cost in this period.".t())
        apps.forEach { a ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.s("name"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Small(a.s("kinds").split(",").filter { it.isNotBlank() }.joinToString(", ") { (TASKS[it] ?: it).t() } + " · " + tf("%s tasks · %s calls", a.l("tasks"), a.l("calls")), maxLines = 2)
                }
                Text(money(a.d("usd"), r, if (a.d("usd") * r < 10) 1 else 0), fontWeight = FontWeight.SemiBold)
            }
            Hairline()
        }
        if (j.d("no_app_usd") > 0) Row(Modifier.padding(vertical = 5.dp)) {
            Text("Not tied to one app (briefs, Ads reviews, research)".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(money(j.d("no_app_usd"), r))
        }
    }
}

// ---------------------------------------------------------------- Tasks

@Composable
private fun Tasks(j: JSONObject, r: Double) {
    val tasks = j.arr("tasks").objs()
    if (tasks.isEmpty()) { Panel { Empty("No AI tasks in this period.".t()) }; return }
    Small("Most expensive first. Every row is an estimate from tokens × list price (Claude Code: its own cost report).".t())
    Spacer(Modifier.height(6.dp))
    tasks.forEach { t ->
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((TASKS[t.s("task")] ?: t.s("task").ifBlank { "Task" }).t() + (if (t.s("app").isNotBlank()) " · " + t.s("app") else ""),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
                Text(money(t.d("usd"), r, if (t.d("usd") * r < 10) 1 else 0), fontWeight = FontWeight.SemiBold)
            }
            Small(listOfNotNull(t.s("ref").ifBlank { null }, t.s("models").ifBlank { null }, tf("%s calls", t.l("calls")),
                if (t.l("in_tok") + t.l("out_tok") > 0) short(t.d("in_tok")) + " in / " + short(t.d("out_tok")) + " out" else null,
                t.s("note").ifBlank { null }).joinToString(" · "), maxLines = 3)
            Spacer(Modifier.height(4.dp))
            Row { Tag(false); if (!t.b("ok")) { Spacer(Modifier.width(6.dp)); Pill("FAILED".t(), MaterialTheme.extra.bad) }; Spacer(Modifier.width(6.dp)); Small(tFull(t.s("last_at"))) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------- Budgets & limits (editable)

@Composable
private fun NumField(label: String, v: String, suffix: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(v, { s -> onChange(s.filter { it.isDigit() || it == '.' }.take(9)) }, label = { Text(label) }, suffix = { Text(suffix) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier)
}

private fun fmt(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

@Composable
private fun Budgets(vm: CostVm, g: GState, m: JSONObject, r: Double) {
    val s = m.obj("settings")
    val mods = m.arr("modules").objs()
    val stamp = s.toString()
    var total by remember(stamp) { mutableStateOf(fmt(s.d("total"))) }
    var reserve by remember(stamp) { mutableStateOf(s.l("reserve_pct").toString()) }
    var actions by remember(stamp) { mutableStateOf(fmt(s.d("actions_usd_min"))) }
    var email by remember(stamp) { mutableStateOf(s.b("warn_email")) }
    val budget = remember(stamp) { mutableStateMapOf<String, String>().apply { mods.forEach { put(it.s("key"), fmt(s.obj("modules").obj(it.s("key")).d("budget"))) } } }
    val cap = remember(stamp) { mutableStateMapOf<String, String>().apply { mods.forEach { put(it.s("key"), s.obj("modules").obj(it.s("key")).l("cap").toString()) } } }
    var fixCap by remember(stamp) { mutableStateOf(s.obj("modules").obj("health").l("fix_cap").toString()) }
    val fixed = remember(stamp) { mutableStateListOf<Pair<String, String>>().apply { s.arr("fixed").objs().forEach { add(it.s("name") to fmt(it.d("usd"))) } } }
    val sumMods = budget.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val tot = total.toDoubleOrNull() ?: 0.0

    Panel {
        Text("Total monthly budget".t(), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Total".t(), total, "$", Modifier.weight(1f)) { total = it }
            NumField("Critical reserve".t(), reserve, "%", Modifier.weight(1f)) { reserve = it }
        }
        Small(tf("= %s per month. Module budgets add up to %s.", inr(tot * r), usd(sumMods, 0)), if (sumMods > tot + 0.01) MaterialTheme.extra.warn else null)
        Small("Reserve: after the total is used up, only urgent monitoring AI (urgent alert explanation, first look at a new P0/P1 crash) may use this extra share.".t())
    }
    Spacer(Modifier.height(10.dp))
    mods.forEach { x ->
        val k = x.s("key")
        Panel {
            Text(x.s("name").t(), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Budget / month".t(), budget[k] ?: "", "$", Modifier.weight(1f)) { budget[k] = it }
                NumField("AI calls / day".t(), cap[k] ?: "", "max", Modifier.weight(1f)) { cap[k] = it.filter { c -> c.isDigit() } }
            }
            if (k == "health") { Spacer(Modifier.height(6.dp)); NumField("AI fix / task runs per day".t(), fixCap, "max", Modifier.fillMaxWidth()) { fixCap = it.filter { c -> c.isDigit() } } }
            Small(tf("= %s per month. At 100%%: %s", inr((budget[k]?.toDoubleOrNull() ?: 0.0) * r), when (k) {
                "analytics" -> "daily AI brief and non-urgent explanations pause; rules and alerts keep running.".t()
                "health" -> "AI fix, new tasks and manual Investigate pause; crash / ANR scans and alerts keep running.".t()
                "gads" -> "6-hour deep AI and daily review pause; Ads rules and alerts keep running.".t()
                else -> "research runs, image / video and new project builds pause; cached results stay available.".t()
            }))
        }
        Spacer(Modifier.height(10.dp))
    }
    Panel {
        Text("Fixed monthly bills".t(), style = MaterialTheme.typography.titleSmall)
        Small("Hosting, database or any other fixed bill. Counted as billed.".t())
        fixed.forEachIndexed { i, (n, v) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(n, { fixed[i] = it.take(60) to v }, label = { Text("Name".t()) }, singleLine = true, modifier = Modifier.weight(1.4f))
                NumField("Per month".t(), v, "$", Modifier.weight(1f)) { fixed[i] = n to it }
                TextButton(onClick = { fixed.removeAt(i) }) { Text("✕") }
            }
        }
        TextButton(onClick = { fixed.add("" to "0") }) { Text("+ " + "Add bill".t()) }
        Spacer(Modifier.height(6.dp))
        NumField("GitHub Actions price per minute (0 = public repos, free)".t(), actions, "$", Modifier.fillMaxWidth()) { actions = it }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Email the 70 / 90 / 100% warnings".t(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(email, { email = it })
        }
    }
    Spacer(Modifier.height(10.dp))
    Button(enabled = "settings" !in g.busy, modifier = Modifier.fillMaxWidth(), onClick = {
        val js = JSONObject().put("total", tot).put("reserve_pct", reserve.toIntOrNull() ?: 10).put("actions_usd_min", actions.toDoubleOrNull() ?: 0.0).put("warn_email", email)
        val mj = JSONObject()
        mods.forEach { x -> val k = x.s("key"); mj.put(k, JSONObject().put("budget", budget[k]?.toDoubleOrNull() ?: 0.0).put("cap", cap[k]?.toIntOrNull() ?: 0).apply { if (k == "health") put("fix_cap", fixCap.toIntOrNull() ?: 0) }) }
        js.put("modules", mj)
        js.put("fixed", JSONArray().apply { fixed.filter { it.first.isNotBlank() }.forEach { (n, v) -> put(JSONObject().put("name", n).put("usd", v.toDoubleOrNull() ?: 0.0)) } })
        vm.act("settings", mapOf("json" to js.toString()), "Budgets saved".t())
    }) { Text("Save budgets & limits".t()) }
    Spacer(Modifier.height(14.dp))
    Keys(vm, g, m)
    Spacer(Modifier.height(10.dp))
    Small("Cloud budget alerts (Google Cloud, GitHub) only send emails; they do not stop spending. This server checks the budget before every paid AI call. Nothing paid is switched on from here.".t())
}

@Composable
private fun Keys(vm: CostVm, g: GState, m: JSONObject) {
    val k = m.obj("keys")
    var admin by remember { mutableStateOf("") }
    var gh by remember { mutableStateOf("") }
    var user by remember(k.s("gh_user")) { mutableStateOf(k.s("gh_user")) }
    Panel {
        Text("Actual billed numbers (optional, read-only)".t(), style = MaterialTheme.typography.titleSmall)
        Small("Anthropic Admin API key (sk-ant-admin…, Claude Console > Settings > Admin keys; organisation accounts only). Reads the cost report only.".t())
        OutlinedTextField(admin, { admin = it.trim() }, label = { Text(if (k.s("admin_key").isNotBlank()) k.s("admin_key") else "Admin API key".t()) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Small("GitHub fine-grained token with \"Plan\" read access, for Actions billing.".t())
        OutlinedTextField(user, { user = it.trim() }, label = { Text("GitHub username".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(gh, { gh = it.trim() }, label = { Text(if (k.s("gh_token").isNotBlank()) k.s("gh_token") else "GitHub token".t()) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        k.sn("anthropic_sync")?.let { Small("Anthropic synced".t() + " " + it) }
        k.sn("github_sync")?.let { Small("GitHub synced".t() + " " + it) }
        k.sn("sync_error")?.let { Small(it, MaterialTheme.extra.bad) }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = "keys" !in g.busy, onClick = {
                val f = mutableMapOf("gh_user" to user)
                if (admin.isNotBlank()) f["admin_key"] = admin
                if (gh.isNotBlank()) f["gh_token"] = gh
                vm.act("keys", f, "Saved".t()) { admin = ""; gh = "" }
            }) { Text("Save keys".t()) }
            OutlinedButton(enabled = "sync" !in g.busy, onClick = { vm.act("sync", emptyMap(), "Synced".t()) }) { Text("Sync now".t()) }
        }
        if (k.s("admin_key").isNotBlank() || k.s("gh_token").isNotBlank()) TextButton(onClick = { vm.act("keys", mapOf("admin_key" to "", "gh_token" to ""), "Keys removed".t()) }) { Text("Remove keys".t()) }
    }
}
