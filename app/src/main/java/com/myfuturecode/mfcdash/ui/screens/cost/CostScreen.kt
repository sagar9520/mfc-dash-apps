package com.myfuturecode.mfcdash.ui.screens.cost

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.CostVm
import com.myfuturecode.mfcdash.ui.GState
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
import com.myfuturecode.mfcdash.ui.kit.KLoading
import com.myfuturecode.mfcdash.ui.kit.KNum
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KProgress
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSectionLink
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSmallAction
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KSwitch
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.fg
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.word
import com.myfuturecode.mfcdash.ui.kit.zone
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.l
import com.myfuturecode.mfcdash.util.mapObj
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.obj
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.short
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.usd
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Route tabs (cost/t/<tab>, email links). Screen par: Overview | Modules | Usage (apps + tasks) | Budgets. */
val CTABS = listOf("overview" to "Overview", "modules" to "Modules", "apps" to "Apps", "tasks" to "Tasks", "budgets" to "Budgets & limits")
private val CO_MAIN = listOf("overview", "modules", "usage", "budgets")
private val CO_RANGES = listOf("today" to "Today", "7d" to "7 days", "month" to "This month")
private val PROVIDERS = mapOf(
    "anthropic" to "Claude API (server)", "claude_code" to "Claude Code worker", "github_actions" to "GitHub Actions", "image" to "Image generation",
    "video" to "Video generation", "research_api" to "Research data API", "testlab" to "Firebase Test Lab", "github" to "GitHub (billed)", "other" to "Other",
)
private val TASKS = mapOf(
    "alert_explain" to "Alert explanation", "brief" to "Daily AI brief", "investigate" to "Crash / ANR investigation", "ai_fix" to "AI fix", "task" to "New task (Naya kaam)",
    "alert_ai" to "Google Ads alert AI", "daily_review" to "Google Ads daily review", "ads_ai" to "Google Ads AI",
)
private val CO_UNTIL = SimpleDateFormat("dd MMM HH:mm", Locale.US)
private val CO_FIELD = RoundedCornerShape(14.dp)

/** ₹ pehle (Sagar ₹ me sochte hain), $ saath me (provider USD me bill karte hain). */
private fun money(v: Double, r: Double, dec: Int = 0) = inr(v * r, dec)

/** Chhoti rakam me ek decimal (₹4.5). */
private fun coSmall(v: Double, r: Double) = money(v, r, if (v * r < 10) 1 else 0)

private fun coMainOf(tab: String): String = when (tab) {
    "modules" -> "modules"
    "apps", "tasks", "usage" -> "usage"
    "budgets" -> "budgets"
    else -> "overview"
}

/** Budget ka rang: 70% se dhyan do, 90% se turant (server ki warnings jaisa). */
private fun coKind(p: Double?): KKind = when {
    p == null || p.isNaN() -> KKind.NEUTRAL
    p >= 0.9 -> KKind.BAD
    p >= 0.7 -> KKind.WARN
    else -> KKind.OK
}

private fun prov(k: String) = (PROVIDERS[k] ?: k).t()

/** AI Cost & Usage Manager: hero = is mahine ka kharcha / budget; tabs: Overview, Modules, Usage (app / task), Budgets. */
@Composable
fun CostScreen(vm: CostVm, nav: NavHostController, startTab: String) {
    val g by vm.s.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(coMainOf(startTab)) }
    var sub by rememberSaveable { mutableStateOf(if (startTab == "tasks") "tasks" else "apps") }
    var range by rememberSaveable { mutableStateOf("month") }
    val reload = remember { mutableIntStateOf(0) }
    LaunchedEffect(reload.intValue) { vm.load("month") }
    LaunchedEffect(reload.intValue, range, tab) { if (tab == "usage" && range != "month") vm.load(range) }
    val m = g.view("month")
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") }
    val subT = listOfNotNull(
        m?.s("month")?.takeIf { it.isNotBlank() },
        m?.let { "₹" + num(it.d("inr_per_usd"), 0) + " = $1" },
        if (g.lastOk == 0L) "Loading…".t() else "Updated".t() + " " + agoText(g.lastOk),
    ).joinToString(" · ")
    val bar: @Composable ColumnScope.() -> Unit = {
        KDetailBar("AI Cost & Usage".t(), back, sub = subT, actions = {
            KBarIcon(KIcons.Refresh, "Refresh".t(), { reload.intValue++ }, spinning = g.busy.isNotEmpty())
        })
    }
    val tabs = listOf("Overview".t(), "Modules".t(), "Usage".t(), "Budgets".t())
    val tabRow: @Composable () -> Unit = { KSegTabs(tabs, CO_MAIN.indexOf(tab).coerceAtLeast(0), { tab = CO_MAIN[it] }) }
    val body: @Composable ColumnScope.() -> Unit = {
        if (m == null) {
            if (g.error != null) CoProblem(g) { reload.intValue++ } else KLoading()
        } else {
            val r = m.d("inr_per_usd", 95.0)
            when (tab) {
                "overview" -> CoOverview(m, r) { tab = it }
                "modules" -> CoModules(vm, m, r) { tab = "budgets" }
                "usage" -> CoUsage(g, r, sub, { sub = it }, range, { range = it })
                else -> CoBudgets(vm, g, m, r)
            }
        }
    }

    if (isWide()) {
        // Fold: baayein hero + tabs, daayein tab ka hissa
        KScreen(top = bar) {
            KTwoPane(
                left = {
                    if (m != null) CoHero(m)
                    tabRow()
                },
                right = { body() },
            )
        }
    } else {
        KScreen(top = {
            bar()
            if (m != null) CoHero(m)
        }) {
            tabRow()
            body()
        }
    }
}

@Composable
private fun CoProblem(g: GState, onRetry: () -> Unit) {
    val c = Mfc.c
    KCard {
        if (g.missing) CoText("AI Cost & Usage is not on the server yet".t(), 15.sp, c.ink, weight = FontWeight.SemiBold, maxLines = 2)
        else CoText(g.error ?: "No data received".t(), 14.sp, c.red, maxLines = 4)
        KButton("Try again".t(), onRetry, Modifier.fillMaxWidth().padding(top = 12.dp), kind = KBtn.SOFT, icon = KIcons.Refresh)
    }
}

// ---------------------------------------------------------------- bits

@Composable
private fun CoText(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal, maxLines: Int = 1,
) {
    Text(
        text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = KNum.copy(fontSize = size, fontWeight = weight, lineHeight = (size.value * 1.38f).sp),
    )
}

/** Budget bar: kharch hissa + 70% / 90% ke nishaan. */
@Composable
fun BudgetBar(p: Double?, height: Int = 8, marks: Boolean = false) {
    val c = Mfc.c
    val col = coKind(p).zone()
    val f = (p ?: 0.0).let { if (it.isNaN()) 0.0 else it.coerceIn(0.0, 1.0) }.toFloat()
    BoxWithConstraints(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(height.dp)).background(c.zTrack)) {
        if (f > 0f) Box(Modifier.fillMaxHeight().width(maxWidth * f).clip(RoundedCornerShape(height.dp)).background(col))
        if (marks) listOf(0.7f to c.zAmber, 0.9f to c.zRed).forEach { (x, mc) ->
            Box(Modifier.offset(x = maxWidth * x).width(2.dp).fillMaxHeight().background(mc))
        }
    }
}

private fun JSONArray.objs(): List<JSONObject> = mapObj { it }

/** List card ki ek line (tile + naam + status + rakam). */
private class CoLine(
    val title: String, val sub: String?, val value: String?, val tile: ImageVector, val kind: KKind = KKind.NEUTRAL,
    val subKind: KKind? = null, val onClick: (() -> Unit)? = null,
)

@Composable
private fun CoLines(lines: List<CoLine>) {
    KCard(list = true) {
        lines.forEachIndexed { i, x ->
            if (i > 0) KListDivider(true)
            KRow(
                x.title, sub = x.sub, subKind = x.subKind, lead = { KIconTile(x.tile, x.kind, 36.dp) }, value = x.value,
                chevron = x.onClick != null, onClick = x.onClick,
            )
        }
    }
}

// ---------------------------------------------------------------- hero

/** Is mahine: kharcha / budget, % + aaj, budget line, 7 din | bacha | andaza. */
@Composable
private fun CoHero(m: JSONObject) {
    KHero(KTint.Sand) {
        val c = Mfc.c
        val r = m.d("inr_per_usd", 95.0)
        val p = m.dn("pct")
        val k = coKind(p)
        val budget = m.d("budget_usd")
        val rem = m.d("remaining_usd")
        val fc = m.d("forecast_usd")
        val pill: (@Composable () -> Unit)? = if (k != KKind.NEUTRAL) { { KPill(k.word(), k) } } else null
        KHeroHead("This month".t(), icon = KIcons.Spark, trailing = pill)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) {
                KBigNum(money(m.d("month_usd"), r), suffix = if (budget > 0) "/" + money(budget, r) else null)
            }
            Column(Modifier.padding(bottom = 5.dp), horizontalAlignment = Alignment.End) {
                CoText(if (p == null) "–" else pct(p, 0), 20.sp, c.ink, weight = FontWeight.Bold)
                CoText(tf("Today %s", money(m.d("today_usd"), r)), 13.sp, c.ink2)
            }
        }
        KProgress((p ?: 0.0).toFloat(), k.zone())
        val fcColor: Color? = when {
            budget > 0 && fc > budget -> c.red
            budget > 0 && fc > budget * 0.9 -> c.amber
            else -> null
        }
        KSplit(
            listOf("7 days".t() to money(m.d("d7_usd"), r), "Remaining".t() to money(rem, r), "Forecast".t() to money(fc, r)),
            listOf(null, if (rem < 0) c.red else c.green, fcColor),
        )
    }
}

// ---------------------------------------------------------------- Overview

@Composable
private fun CoOverview(m: JSONObject, r: Double, goTab: (String) -> Unit) {
    val c = Mfc.c
    val budget = m.d("budget_usd")
    val p = m.dn("pct")
    val k = coKind(p)
    val rem = m.d("remaining_usd")
    val fc = m.d("forecast_usd")
    // ---- poora hisaab ₹ + $
    KCard {
        KKv("Today".t(), money(m.d("today_usd"), r), valueSub = usd(m.d("today_usd")))
        KKv("7 days".t(), money(m.d("d7_usd"), r), valueSub = usd(m.d("d7_usd")))
        KKv("This month".t(), money(m.d("month_usd"), r), valueSub = usd(m.d("month_usd")))
        KKv("Monthly budget".t(), money(budget, r), valueSub = usd(budget, 0))
        KKv("Used".t(), if (p == null) "–" else pct(p, 0), valueColor = if (k == KKind.NEUTRAL) Color.Unspecified else k.fg())
        KKv("Remaining".t(), money(rem, r), valueColor = if (rem < 0) c.red else c.green, valueSub = usd(rem))
        KKv(
            "Forecast month-end".t(), money(fc, r), valueSub = usd(fc),
            valueColor = if (budget > 0 && fc > budget) c.red else if (budget > 0 && fc > budget * 0.9) c.amber else Color.Unspecified,
        )
        if (m.d("fixed_usd") > 0) KKv("Fixed bills (hosting etc.)".t(), money(m.d("fixed_usd"), r), valueSub = usd(m.d("fixed_usd")))
        KKv("Critical reserve".t(), m.l("reserve_pct").toString() + "%")
    }

    // ---- asli bill vs andaza
    KSection("Billed vs estimate".t())
    val billed = m.arr("billed").objs()
    val est = m.obj("estimate_by_provider")
    val keys = est.keys().asSequence().toList().sortedByDescending { est.d(it) }
    val kk = m.obj("keys")
    val lines = ArrayList<CoLine>()
    billed.forEach { b ->
        lines += CoLine(prov(b.s("provider")), "Billed".t() + " · " + "synced".t() + " " + tFull(b.s("synced_at")), money(b.d("usd"), r), KIcons.Check, KKind.OK)
    }
    m.arr("fixed").objs().filter { it.d("usd") > 0 }.forEach { f ->
        lines += CoLine(f.s("name"), "Fixed bill".t(), money(f.d("usd"), r), KIcons.Card, KKind.OK)
    }
    keys.forEach { key -> lines += CoLine(prov(key), "Estimate".t(), money(est.d(key), r), KIcons.Chart) }
    if (kk.s("admin_key").isBlank()) lines += CoLine("Admin API key".t(), null, "Not set".t(), KIcons.Key, onClick = { goTab("budgets") })
    kk.sn("sync_error")?.let { e -> lines += CoLine("Sync".t(), e, null, KIcons.Turant, KKind.BAD, KKind.BAD) }
    if (billed.isEmpty() && keys.isEmpty()) KCard { KEmpty("No paid AI use".t(), KIcons.Rupee) }
    if (lines.isNotEmpty()) CoLines(lines)

    // ---- module-wise
    val mods = m.arr("modules").objs()
    if (mods.isNotEmpty()) {
        KSection("By module".t(), trailing = { KSectionLink("Modules".t()) { goTab("modules") } })
        KCard(list = true) {
            mods.forEachIndexed { i, x ->
                if (i > 0) KListDivider()
                CoModuleLine(x, r) { goTab("modules") }
            }
        }
    }

    // ---- ruke hue kaam (pichhle 3 din)
    val paused = m.arr("paused").objs()
    if (paused.isNotEmpty()) {
        KSection("Paused jobs".t())
        KCard(list = true) {
            paused.forEachIndexed { i, x ->
                if (i > 0) KListDivider(true)
                KRow(
                    (TASKS[x.s("task")] ?: x.s("task").ifBlank { x.s("module") }).t(),
                    sub = x.s("why").ifBlank { null }, subKind = KKind.WARN,
                    lead = { KIconTile(KIcons.Pause, KKind.WARN, 36.dp) },
                    value = tf("%s× skipped", x.l("hits")), valuePlain = true, valueSub = tFull(x.s("last_at")),
                )
            }
        }
    }
}

/** Ek module: naam (+ ruka), kharch / budget, line. */
@Composable
private fun CoModuleLine(x: JSONObject, r: Double, onClick: () -> Unit) {
    val c = Mfc.c
    val p = x.dn("pct")
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                CoText(x.s("name").t(), 15.sp, c.ink, weight = FontWeight.SemiBold)
                if (x.b("paused")) KStatus("Paused".t(), KKind.WARN, Modifier.padding(top = 2.dp))
            }
            CoText(money(x.d("month_usd"), r) + " / " + money(x.d("budget"), r), 15.sp, c.ink, weight = FontWeight.Bold)
        }
        KProgress((p ?: 0.0).toFloat(), coKind(p).zone(), top = 10.dp)
    }
}

// ---------------------------------------------------------------- Modules

@Composable
private fun CoModules(vm: CostVm, m: JSONObject, r: Double, toBudgets: () -> Unit) {
    val c = Mfc.c
    val now = System.currentTimeMillis() / 1000
    val mods = m.arr("modules").objs()
    if (mods.isEmpty()) KCard { KEmpty("No data".t(), KIcons.Spark) }
    mods.forEach { x ->
        val key = x.s("key")
        KCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoText(x.s("name").t(), 16.sp, c.ink, Modifier.weight(1f), weight = FontWeight.Bold, maxLines = 2)
                CoText(money(x.d("month_usd"), r) + " / " + money(x.d("budget"), r), 15.sp, c.ink, weight = FontWeight.Bold)
            }
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp)) { BudgetBar(x.dn("pct"), 10, marks = true) }
            KKv("Today".t(), money(x.d("today_usd"), r), valueSub = usd(x.d("today_usd")))
            KKv("AI calls today".t(), x.l("calls_today").toString() + (if (x.l("cap") > 0) " / " + x.l("cap") else ""))
            if (key == "health" && x.l("fix_cap") > 0) KKv("AI fix / task runs per day (max)".t(), x.l("fix_cap").toString())
            x.arr("counts").objs().sortedByDescending { it.l("n") }.take(6).forEach { cn ->
                CoText(
                    (TASKS[cn.s("task")] ?: cn.s("task").ifBlank { "other" }).t() + " · " + prov(cn.s("provider")) + " · " + cn.l("n") + "×",
                    13.sp, c.ink2, Modifier.padding(top = 4.dp),
                )
            }
            val until = x.l("override_until")
            if (x.b("paused")) {
                val why = x.s("paused_why")
                KStatus("Paused".t(), KKind.WARN, Modifier.padding(top = 12.dp))
                if (why.isNotBlank()) CoText(why, 13.sp, c.ink2, Modifier.padding(top = 2.dp), maxLines = 3)
                val btn = Modifier.fillMaxWidth().padding(top = 10.dp)
                when {
                    why.contains("failed AI calls") -> KButton(
                        "Allow retry now".t(), { vm.act("breaker", mapOf("module" to key), "Retry allowed".t()) }, btn, kind = KBtn.SOFT,
                    )
                    !why.contains("reserve") -> KButton(
                        "Resume for 24 hours (my OK)".t(),
                        { vm.act("resume", mapOf("module" to key, "hours" to "24"), "Resumed for 24 hours".t()) }, btn, kind = KBtn.SOFT,
                    )
                    // reserve bhi khatam: total budget badhao
                    else -> KButton("Budgets & limits".t(), toBudgets, btn, kind = KBtn.OUTLINE)
                }
            } else if (until > now) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        KStatus("Over budget · on".t(), KKind.ASK)
                        CoText(tf("until %s", CO_UNTIL.format(Date(until * 1000))), 13.sp, c.ink2)
                    }
                    KSmallAction("Stop".t(), { vm.act("pause_now", mapOf("module" to key), "Budget rules back on".t()) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Usage (apps / tasks)

@Composable
private fun CoUsage(g: GState, r: Double, sub: String, onSub: (String) -> Unit, range: String, onRange: (String) -> Unit) {
    KSegTabs(listOf("Apps".t(), "Tasks".t()), if (sub == "tasks") 1 else 0, { onSub(if (it == 1) "tasks" else "apps") })
    KSegTabs(
        CO_RANGES.map { it.second.t() }, CO_RANGES.indexOfFirst { it.first == range }.coerceAtLeast(0),
        { onRange(CO_RANGES[it].first) },
    )
    val j = g.view(range)
    if (j == null) KLoading() else if (sub == "tasks") CoTasks(j, r) else CoApps(j, r)
}

@Composable
private fun CoApps(j: JSONObject, r: Double) {
    val apps = j.arr("apps").objs()
    val noApp = j.d("no_app_usd")
    if (apps.isEmpty() && noApp <= 0) {
        KCard { KEmpty("No app cost".t(), KIcons.Apps) }
        return
    }
    KCard(list = true) {
        apps.forEachIndexed { i, a ->
            if (i > 0) KListDivider(true)
            val kinds = a.s("kinds").split(",").filter { it.isNotBlank() }.joinToString(", ") { (TASKS[it] ?: it).t() }
            KRow(
                a.s("name"),
                sub = listOf(kinds, tf("%s tasks · %s calls", a.l("tasks"), a.l("calls"))).filter { it.isNotBlank() }.joinToString(" · "),
                lead = { KAvatar(a.s("name")) },
                value = coSmall(a.d("usd"), r),
            )
        }
        if (noApp > 0) {
            if (apps.isNotEmpty()) KListDivider(true)
            KRow("No single app".t(), lead = { KIconTile(KIcons.Apps) }, value = money(noApp, r))
        }
    }
}

@Composable
private fun CoTasks(j: JSONObject, r: Double) {
    val tasks = j.arr("tasks").objs()
    if (tasks.isEmpty()) {
        KCard { KEmpty("No AI tasks".t(), KIcons.Spark) }
        return
    }
    // sab andaza (tokens × list price), mehenga pehle
    KCard(list = true) {
        tasks.forEachIndexed { i, tk ->
            if (i > 0) KListDivider(true)
            val ok = tk.b("ok")
            val name = (TASKS[tk.s("task")] ?: tk.s("task").ifBlank { "Task" }).t() + (if (tk.s("app").isNotBlank()) " · " + tk.s("app") else "")
            val bits = listOfNotNull(
                if (ok) null else "Failed".t(),
                tk.s("ref").ifBlank { null },
                tk.s("models").ifBlank { null },
                tf("%s calls", tk.l("calls")),
                if (tk.l("in_tok") + tk.l("out_tok") > 0) short(tk.d("in_tok")) + " in / " + short(tk.d("out_tok")) + " out" else null,
                tk.s("note").ifBlank { null },
            ).joinToString(" · ")
            KRow(
                name, sub = bits, subKind = if (ok) null else KKind.BAD,
                lead = { KIconTile(if (ok) KIcons.Spark else KIcons.Turant, if (ok) KKind.ASK else KKind.BAD, 36.dp) },
                value = "≈" + coSmall(tk.d("usd"), r), valueSub = tFull(tk.s("last_at")), titleLines = 2,
            )
        }
    }
}

// ---------------------------------------------------------------- Budgets & limits (editable)

@Composable
private fun CoNum(label: String, v: String, suffix: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        v, { s -> onChange(s.filter { it.isDigit() || it == '.' }.take(9)) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, suffix = { Text(suffix) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier, shape = CO_FIELD,
    )
}

private fun coFmt(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

@Composable
private fun CoBudgets(vm: CostVm, g: GState, m: JSONObject, r: Double) {
    val c = Mfc.c
    val s = m.obj("settings")
    val mods = m.arr("modules").objs()
    val stamp = s.toString()
    var total by remember(stamp) { mutableStateOf(coFmt(s.d("total"))) }
    var reserve by remember(stamp) { mutableStateOf(s.l("reserve_pct").toString()) }
    var actions by remember(stamp) { mutableStateOf(coFmt(s.d("actions_usd_min"))) }
    var email by remember(stamp) { mutableStateOf(s.b("warn_email")) }
    val budget = remember(stamp) { mutableStateMapOf<String, String>().apply { mods.forEach { put(it.s("key"), coFmt(s.obj("modules").obj(it.s("key")).d("budget"))) } } }
    val cap = remember(stamp) { mutableStateMapOf<String, String>().apply { mods.forEach { put(it.s("key"), s.obj("modules").obj(it.s("key")).l("cap").toString()) } } }
    var fixCap by remember(stamp) { mutableStateOf(s.obj("modules").obj("health").l("fix_cap").toString()) }
    val fixed = remember(stamp) { mutableStateListOf<Pair<String, String>>().apply { s.arr("fixed").objs().forEach { add(it.s("name") to coFmt(it.d("usd"))) } } }
    val sumMods = budget.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val tot = total.toDoubleOrNull() ?: 0.0

    // ---- total
    KSection("Total monthly budget".t())
    KCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoNum("Total".t(), total, "$", Modifier.weight(1f)) { total = it }
            CoNum("Critical reserve".t(), reserve, "%", Modifier.weight(1f)) { reserve = it }
        }
        KKv("Per month".t(), inr(tot * r))
        KKv("Modules total".t(), usd(sumMods, 0), valueColor = if (sumMods > tot + 0.01) c.amber else Color.Unspecified)
    }
    // ---- har module
    mods.forEach { x ->
        val k = x.s("key")
        KCard {
            CoText(x.s("name").t(), 16.sp, c.ink, weight = FontWeight.Bold, maxLines = 2)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoNum("Budget / month".t(), budget[k] ?: "", "$", Modifier.weight(1f)) { budget[k] = it }
                CoNum("AI calls / day".t(), cap[k] ?: "", "max", Modifier.weight(1f)) { v -> cap[k] = v.filter { ch -> ch.isDigit() } }
            }
            if (k == "health") CoNum("AI fix / task runs per day".t(), fixCap, "max", Modifier.fillMaxWidth().padding(top = 8.dp)) { v ->
                fixCap = v.filter { ch -> ch.isDigit() }
            }
            KKv("Per month".t(), inr((budget[k]?.toDoubleOrNull() ?: 0.0) * r))
        }
    }
    // ---- fixed bills + GitHub Actions + email
    KSection("Fixed monthly bills".t())
    KCard {
        fixed.forEachIndexed { i, (n, v) ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    n, { fixed[i] = it.take(60) to v }, label = { Text("Name".t()) }, singleLine = true,
                    modifier = Modifier.weight(1.4f), shape = CO_FIELD,
                )
                CoNum("Per month".t(), v, "$", Modifier.weight(1f)) { fixed[i] = n to it }
                KBarIcon(KIcons.Delete, "Delete".t(), { fixed.removeAt(i) })
            }
        }
        KSmallAction("+ " + "Add bill".t(), { fixed.add("" to "0") })
        CoNum("GitHub Actions / min".t(), actions, "$", Modifier.fillMaxWidth().padding(top = 12.dp)) { actions = it }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoText("Email warnings".t() + " 70 / 90 / 100%", 15.sp, c.ink, Modifier.weight(1f), maxLines = 2)
            KSwitch(email, { email = it })
        }
    }
    KButton(
        "Save budgets & limits".t(),
        {
            val js = JSONObject().put("total", tot).put("reserve_pct", reserve.toIntOrNull() ?: 10)
                .put("actions_usd_min", actions.toDoubleOrNull() ?: 0.0).put("warn_email", email)
            val mj = JSONObject()
            mods.forEach { x ->
                val k = x.s("key")
                mj.put(k, JSONObject().put("budget", budget[k]?.toDoubleOrNull() ?: 0.0).put("cap", cap[k]?.toIntOrNull() ?: 0).apply {
                    if (k == "health") put("fix_cap", fixCap.toIntOrNull() ?: 0)
                })
            }
            js.put("modules", mj)
            js.put("fixed", JSONArray().apply {
                fixed.filter { it.first.isNotBlank() }.forEach { (n, v) -> put(JSONObject().put("name", n).put("usd", v.toDoubleOrNull() ?: 0.0)) }
            })
            vm.act("settings", mapOf("json" to js.toString()), "Budgets saved".t())
        },
        Modifier.fillMaxWidth(),
        busy = "settings" in g.busy,
    )
    CoKeys(vm, g, m)
}

/** Asli bill ke numbers (optional, sirf padhna): Anthropic Admin key, GitHub token. */
@Composable
private fun CoKeys(vm: CostVm, g: GState, m: JSONObject) {
    val c = Mfc.c
    val k = m.obj("keys")
    var admin by remember { mutableStateOf("") }
    var gh by remember { mutableStateOf("") }
    var user by remember(k.s("gh_user")) { mutableStateOf(k.s("gh_user")) }
    KSection("Billing keys".t())
    KCard {
        OutlinedTextField(
            admin, { admin = it.trim() }, label = { Text(if (k.s("admin_key").isNotBlank()) k.s("admin_key") else "Admin API key".t()) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = CO_FIELD,
        )
        OutlinedTextField(
            user, { user = it.trim() }, label = { Text("GitHub username".t()) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = CO_FIELD,
        )
        OutlinedTextField(
            gh, { gh = it.trim() }, label = { Text(if (k.s("gh_token").isNotBlank()) k.s("gh_token") else "GitHub token".t()) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = CO_FIELD,
        )
        k.sn("anthropic_sync")?.let { KKv("Anthropic synced".t(), it) }
        k.sn("github_sync")?.let { KKv("GitHub synced".t(), it) }
        k.sn("sync_error")?.let { CoText(it, 13.sp, c.red, Modifier.padding(top = 6.dp), maxLines = 3) }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton(
                "Save keys".t(),
                {
                    val f = mutableMapOf("gh_user" to user)
                    if (admin.isNotBlank()) f["admin_key"] = admin
                    if (gh.isNotBlank()) f["gh_token"] = gh
                    vm.act("keys", f, "Saved".t()) { admin = ""; gh = "" }
                },
                Modifier.weight(1f), busy = "keys" in g.busy,
            )
            KButton(
                "Sync now".t(), { vm.act("sync", emptyMap(), "Synced".t()) }, Modifier.weight(1f),
                kind = KBtn.OUTLINE, busy = "sync" in g.busy,
            )
        }
        if (k.s("admin_key").isNotBlank() || k.s("gh_token").isNotBlank()) KButton(
            "Remove keys".t(), { vm.act("keys", mapOf("admin_key" to "", "gh_token" to ""), "Keys removed".t()) },
            Modifier.fillMaxWidth().padding(top = 8.dp), kind = KBtn.OUTLINE,
        )
    }
}
