package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.HIssue
import com.myfuturecode.mfcdash.data.HOverview
import com.myfuturecode.mfcdash.data.PerApp
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.GadsVm
import com.myfuturecode.mfcdash.ui.HState
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.StudioVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KCount
import com.myfuturecode.mfcdash.ui.kit.KDuo
import com.myfuturecode.mfcdash.ui.kit.KDuoItem
import com.myfuturecode.mfcdash.ui.kit.KDuoSide
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroDuo
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KHomeBar
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KLegend
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLive
import com.myfuturecode.mfcdash.ui.kit.KLoading
import com.myfuturecode.mfcdash.ui.kit.KMarks
import com.myfuturecode.mfcdash.ui.kit.KModule
import com.myfuturecode.mfcdash.ui.kit.KModuleHead
import com.myfuturecode.mfcdash.ui.kit.KNum
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRoasMeter
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSectionLink
import com.myfuturecode.mfcdash.ui.kit.KSegBar
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KSteps
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kRoasColor
import com.myfuturecode.mfcdash.ui.kit.kSignedPct
import com.myfuturecode.mfcdash.ui.kit.word
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.mapObj
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.obj
import com.myfuturecode.mfcdash.util.roasPct
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Home (Roop 3 - number aur chhota chart): aaj ka munafa (hero) → aaj ka haal (App Health, Google Ads,
 * Research Studio) → aaj ke top apps (profit | loss). Fold khula: do column. AI Kharcha ab Settings me hai.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: DashVm, st: UiState, nav: NavHostController, hvm: HealthVm, gvm: GadsVm, svm: StudioVm) {
    val h by hvm.s.collectAsStateWithLifecycle()
    val g by gvm.s.collectAsStateWithLifecycle()
    val sg by svm.s.collectAsStateWithLifecycle()
    // teen modules ka overview: Home khuli rahe tab har minute (60 sec se purana ho tabhi)
    HomeTicker { loadModules(hvm, gvm, svm, force = false) }
    LaunchedEffect(Unit) { if (st.data == null && !st.loading) vm.refresh() }

    val dash = st.data
    val money = remember(dash) { dash?.let { homeMoney(it) } }
    val gOv = g.view("overview", GADS_P)
    val sOv = sg.view("overview")
    val hk = h.overview?.let { healthKind(it) }
    val (gDrop, gSpike) = gLimits(g)
    val gk = gOv?.let { gadsKind(it, gDrop, gSpike) }
    val sk = sOv?.let { studioKind(it) }
    val kinds = listOfNotNull(hk, gk, sk)
    val todo = kinds.count { it == KKind.BAD || it == KKind.WARN || it == KKind.ASK }
    val known = kinds.isNotEmpty()
    val al = st.alerts
    val unread = al?.unread ?: 0
    val urgent = (al?.urgentOpen ?: 0) > 0
    val name = firstName(vm.username)
    val hello = if (name.isBlank()) null else "Namaste".t() + " " + name
    val wide = isWide()
    val open: (String) -> Unit = { r -> nav.navigate(r) }
    val tab: (String) -> Unit = { r -> nav.goTab(r) }

    // neeche kheencho = sab dobara (auto refresh ka spinner yahan nahi dikhta)
    var pulling by remember { mutableStateOf(false) }
    LaunchedEffect(st.loading) { if (!st.loading) pulling = false }
    LaunchedEffect(pulling) { if (pulling) { delay(15_000); pulling = false } }

    PullToRefreshBox(
        isRefreshing = pulling,
        onRefresh = { pulling = true; vm.refresh(); loadModules(hvm, gvm, svm, force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        KScreen(top = {
            if (wide) {
                KHomeBar("MFC", hello, unread, urgent, { tab("alerts") }, middle = { HaalHead(Modifier.weight(1f), todo, known) })
            } else {
                KHomeBar("MFC", hello, unread, urgent, { tab("alerts") })
                HomeHero(st, money, vm, open, tab)
            }
        }) {
            if (wide) {
                // Fold: baayein paisa, daayein aaj ka haal (heading app bar me)
                KTwoPane(
                    left = {
                        HomeHero(st, money, vm, open, tab)
                        TopApps(st, money, open, tab)
                        MoreLinks(open)
                    },
                    right = {
                        HealthModule(h, hk, open)
                        GadsModule(g, gk, dash?.r, open)
                        StudioModule(sg, sk, open)
                    },
                )
            } else {
                KSection("Today's status".t(), trailing = { HaalPill(todo, known) })
                HealthModule(h, hk, open)
                GadsModule(g, gk, dash?.r, open)
                StudioModule(sg, sk, open)
                TopApps(st, money, open, tab)
                MoreLinks(open)
            }
        }
    }
}

// ---------------------------------------------------------------- loading

private const val TICK_MS = 60_000L
private const val LOAD_GAP_MS = 55_000L
private val GADS_P = mapOf("range" to "7d")   // Google Ads "All Accounts" tab wali key (cache share)

/** Aakhri load kab hua (process bhar): Home par baar-baar aane se server par extra call nahi. */
private object HomeLoads {
    var health = 0L
    var gads = 0L
    var studio = 0L
}

private fun loadModules(hvm: HealthVm, gvm: GadsVm, svm: StudioVm, force: Boolean) {
    val now = System.currentTimeMillis()
    if (force || now - HomeLoads.health >= LOAD_GAP_MS) { HomeLoads.health = now; hvm.loadOverview() }
    if (force || now - HomeLoads.gads >= LOAD_GAP_MS) { HomeLoads.gads = now; gvm.load("overview", GADS_P) }
    if (force || now - HomeLoads.studio >= LOAD_GAP_MS) { HomeLoads.studio = now; svm.load("overview") }
}

/** Screen khuli rahe tab har minute [tick] (pehli baar turant). App peeche ho to ruk jata hai. */
@Composable
private fun HomeTicker(tick: () -> Unit) {
    val t by rememberUpdatedState(tick)
    val life = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(life) {
        life.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { t(); delay(TICK_MS) }
        }
    }
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}

// ---------------------------------------------------------------- money (hero + top apps)

/** Home ka paisa: Kamai = ad revenue + payments, kharcha = Google Ads. Profit / loss Apps tab jaisa hi (ginti mile). */
private class HomeMoney(
    val profit: Double, val earned: Double, val spend: Double, val roas: Double?, val r: Double,
    val wins: List<PerApp>, val losses: List<PerApp>, val appCount: Int, private val names: Map<String, String>,
) {
    /** Apps tab jaisa naam: apps list ka naam, warna per_app ka, warna pkg ka aakhri hissa. */
    fun name(a: PerApp): String = names[a.pkg] ?: a.name.ifBlank { a.pkg.substringAfterLast('.') }
}

private fun homeMoney(dd: DashData): HomeMoney {
    val k = dd.kpi
    val r = dd.r
    val earned = k.revInr + k.purchaseInr
    val roas = k.roasTotal ?: (if (k.gadsCostInr > 0) earned / k.gadsCostInr else null)
    // bina kamai/kharche wale apps kisi taraf nahi (Apps tab me "No data")
    val active = dd.perApp.filter { it.revInr(r) + it.purchaseInr > 0 || it.gadsCostInr > 0 }
    val wins = active.filter { it.profit(r) >= 0 }.sortedByDescending { it.profit(r) }
    val losses = active.filter { it.profit(r) < 0 }.sortedBy { it.profit(r) }
    val names = dd.apps.filter { it.name.isNotBlank() }.distinctBy { it.pkg }.associate { it.pkg to it.name }
    return HomeMoney(k.profitInr, earned, k.gadsCostInr, roas, r, wins, losses, dd.perApp.size, names)
}

/** "+₹18,450" / "−₹2,140" (asli minus). */
private fun signedInr(v: Double): String = when {
    v > 0 -> "+" + inr(v)
    v < 0 -> "−" + inr(-v)
    else -> inr(v)
}

private fun periodLabel(dd: DashData): String = if (dd.single) fmtDay(dd.from) else fmtDay(dd.from) + " – " + fmtDay(dd.to)

private fun isTodayView(dd: DashData?): Boolean = dd == null || (dd.today && dd.single)

/** Hero ka label, Paisa screen jaisa: Aaj / Kal / 7 din / 30 din ka munafa, custom par tareekh. */
private fun profitLabel(range: String, dd: DashData): String = when {
    isTodayView(dd) -> "Today's profit".t()
    range == "yesterday" -> "Yesterday's profit".t()
    range == "7d" -> "7 days profit".t()
    range == "30d" -> "30 days profit".t()
    else -> "Profit".t() + " · " + periodLabel(dd)
}

@Composable
private fun HomeHero(st: UiState, m: HomeMoney?, vm: DashVm, open: (String) -> Unit, tab: (String) -> Unit) {
    val dd = st.data
    if (dd == null || m == null) {
        KHero(KTint.Green) {
            KHeroHead("Today's profit".t())
            if (st.loading) KLoading()
            else {
                KBigNum("–", size = 48.sp)
                KButton("Try again".t(), { vm.refresh() }, Modifier.padding(top = 8.dp), kind = KBtn.SOFT, icon = KIcons.Refresh)
            }
        }
        return
    }
    KHero(KTint.Green, onClick = { open("money") }) {
        val c = Mfc.c
        KHeroHead(profitLabel(st.range, dd), trailing = { HeroFresh(st, dd) })
        KBigNum(signedInr(m.profit), if (m.profit >= 0) c.green else c.red, size = 48.sp)
        KSplit(listOf("Earnings".t() to inr(m.earned), "Ads spend".t() to inr(m.spend)))
        KRoasMeter(m.roas, st.roasTarget)
        KHeroDuo(winSide(m), lossSide(m)) { tab("apps") }
    }
}

/** Hero ke kone me: "Live · 2 min pehle"; summary purana ho to amber note. */
@Composable
private fun HeroFresh(st: UiState, dd: DashData) {
    if (dd.aggLagSec > 900) KStatus(tf("data %s min old", (dd.aggLagSec / 60).toInt()), KKind.WARN)
    else if (dd.today && st.lastOk > 0) KLive("Live".t() + " · " + agoText(st.lastOk))
}

private fun winSide(m: HomeMoney): KDuoSide? {
    if (m.appCount == 0) return null
    val a = m.wins.firstOrNull()
    return KDuoSide(
        tf("%s in profit", m.wins.size), a?.let { m.name(it) } ?: "None".t(), if (a == null) "–" else signedInr(a.profit(m.r)),
        KKind.OK, true, if (a == null) null else "Top:",
    )
}

private fun lossSide(m: HomeMoney): KDuoSide? {
    if (m.appCount == 0) return null
    val a = m.losses.firstOrNull()
    return KDuoSide(
        tf("%s in loss", m.losses.size), a?.let { m.name(it) } ?: "None".t(), if (a == null) "–" else signedInr(a.profit(m.r)),
        KKind.BAD, false, if (a == null) null else "Top:",
    )
}

/** Aaj ke top apps: ek card, baayein profit wale 2, daayein loss wale 2 (har naam tap = app detail). */
@Composable
private fun TopApps(st: UiState, m: HomeMoney?, open: (String) -> Unit, tab: (String) -> Unit) {
    KSection(
        if (isTodayView(st.data)) "Today's top apps".t() else "Top apps".t(),
        trailing = { KSectionLink("View more".t()) { tab("apps") } },
    )
    if (m == null) {
        KCard { if (st.loading) KLoading() else KEmpty("No data".t()) }
        return
    }
    KCard(padding = PaddingValues(top = 18.dp, bottom = 6.dp)) {
        val line = Mfc.c.line
        Row(
            Modifier.fillMaxWidth().drawBehind {
                val x = size.width / 2f
                drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            },
        ) {
            TopCol(m.wins, m, true, st.roasTarget, open, Modifier.weight(1f).padding(start = 20.dp, end = 16.dp))
            TopCol(m.losses, m, false, st.roasTarget, open, Modifier.weight(1f).padding(start = 18.dp, end = 16.dp))
        }
    }
}

@Composable
private fun TopCol(apps: List<PerApp>, m: HomeMoney, up: Boolean, target: Double, open: (String) -> Unit, modifier: Modifier) {
    val c = Mfc.c
    val r = m.r
    val col = if (up) c.green else c.red
    Column(modifier) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(if (up) KIcons.Up else KIcons.Down, null, tint = col, modifier = Modifier.size(12.dp))
                HT(if (up) "In profit".t() else "In loss".t(), 14.sp, col, weight = FontWeight.SemiBold, lh = 18.sp)
            }
            Box(Modifier.padding(top = 3.dp)) { KBigNum(signedInr(apps.sumOf { it.profit(r) }), col, size = 22.sp) }
            HT(tf("%s apps", apps.size), 13.sp, c.ink3, lh = 17.sp)
        }
        HLine(c.line)
        if (apps.isEmpty()) HT("No apps".t(), 15.sp, c.ink3, Modifier.padding(top = 14.dp, bottom = 14.dp), lh = 20.sp)
        apps.take(2).forEachIndexed { i, a ->
            if (i > 0) HLine(c.line)
            val p = a.profit(r)
            val ro = a.roas(r)
            Column(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { open("app/${a.pkg}") }
                    .padding(top = 12.dp, bottom = 10.dp),
            ) {
                HT(m.name(a), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
                Box(Modifier.padding(top = 2.dp)) { KBigNum(signedInr(p), col, size = 18.sp) }
                HT(
                    if (ro == null) "No spend".t() else "ROAS " + roasPct(ro), 13.sp,
                    if (ro == null) c.ink3 else kRoasColor(ro, target), weight = FontWeight.SemiBold, lh = 17.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- aaj ka haal

/** BAD module ka pill "Turant dekho"; baaki kit ke shabd (Dhyan do / OK chahiye / Theek). */
private fun modWord(k: KKind): String = if (k == KKind.BAD) "See now".t() else k.word()

@Composable
private fun HaalPill(todo: Int, known: Boolean) {
    if (todo > 0) KPill(tf("%s to watch", todo), KKind.WARN, large = true)
    else if (known) KPill("All good".t(), KKind.OK, large = true)
}

/** Fold: app bar ke beech me "Aaj ka haal" + pill (daayein column ki heading). */
@Composable
private fun HaalHead(modifier: Modifier, todo: Int, known: Boolean) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Today's status".t(), Modifier.weight(1f, fill = false), color = Mfc.c.ink,
            style = hs(18.sp, FontWeight.Bold, 24.sp).copy(letterSpacing = (-0.01).em), maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        HaalPill(todo, known)
    }
}

/** Module ka pill: data hai to status; nahi to setup nahi / data nahi (loading me kuch nahi). */
@Composable
private fun ModPill(kind: KKind?, missing: Boolean, failed: Boolean, idle: String? = null) {
    if (kind != null) KPill(if (kind == KKind.NEUTRAL) (idle ?: kind.word()) else modWord(kind), kind)
    else if (missing) KPill("Not set up".t(), KKind.NEUTRAL)
    else if (failed) KPill("No data".t(), KKind.NEUTRAL)
}

/** Data aane tak: "–" aur khaali patti. */
@Composable
private fun ModWait() {
    KCount("–")
    KSegBar(emptyList())
}

// ---- App Health (Rose)

private fun healthKind(o: HOverview): KKind = when {
    o.issuesP0 > 0 || !o.cronOk || o.integrations.any { (it.kind == "play" || it.kind == "tracker") && it.status == "ERROR" } -> KKind.BAD
    o.withProblems > 0 || o.needsHuman > 0 -> KKind.WARN
    o.aabReady > 0 -> KKind.ASK
    else -> KKind.OK
}

private fun issueKind(i: HIssue): KKind = when (i.severity) {
    "P0" -> KKind.BAD
    "P1" -> KKind.WARN
    else -> KKind.NEUTRAL
}

/** "Crash · 340 users". */
private fun issueLine(i: HIssue): String {
    val type = when (i.type) {
        "CRASH" -> "Crash".t()
        "ANR" -> "ANR"
        "AD" -> "Ad issue".t()
        "TASK" -> "Task".t()
        "VITALS" -> "Vitals".t()
        else -> i.type.lowercase().replaceFirstChar { it.uppercase() }
    }
    val users = i.users?.takeIf { it > 0 }?.let { tf("%s users", num(it.toDouble())) }
    return listOfNotNull(type.ifBlank { null }, users).joinToString(" · ")
}

@Composable
private fun HealthModule(h: HState, kind: KKind?, open: (String) -> Unit) {
    val o = h.overview
    KModule(KTint.Rose, { open("health") }) {
        KModuleHead(KIcons.Pulse, "App Health".t()) { ModPill(kind, o == null && h.missing, o == null && h.error != null) }
        if (o == null) ModWait() else HealthBody(o, open)
    }
}

@Composable
private fun HealthBody(o: HOverview, open: (String) -> Unit) {
    val c = Mfc.c
    val ok = o.healthy.coerceAtLeast(0)
    val bad = o.withProblems.coerceAtLeast(0)
    val wait = (o.apps - ok - bad).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            KCount(
                num(ok.toDouble()), "/" + num(o.apps.toDouble()), "apps fine".t(),
                if (o.aabReady > 0) tf("%s AI fixes ready", o.aabReady) else null,
            )
        }
        KLegend(
            listOf(
                Triple(c.zGreen, num(ok.toDouble()), "fine".t()),
                Triple(c.zRed, num(bad.toDouble()), "problem".t()),
                Triple(c.zGrey, num(wait.toDouble()), "scan pending".t()),
            ),
        )
    }
    KSegBar(listOf(ok.toFloat() to c.zGreen, bad.toFloat() to c.zRed, wait.toFloat() to c.zGrey))
    // sabse bure 2 apps (har app ek baar)
    val worst = o.attention.distinctBy { it.pkg.ifBlank { it.app } }.take(2)
    val items = worst.map { i -> KDuoItem(i.app.ifBlank { i.pkg.substringAfterLast('.') }, issueLine(i), issueKind(i)) }
    val one = o.attention.singleOrNull()
    KDuo(items.getOrNull(0), items.getOrNull(1)) { open(if (one != null) "health/issue/${one.id}" else "health/t/issues") }
}

// ---- Google Ads (Sand)

/** Sirf monitor wale accounts (baaki scan nahi hote). */
private fun gAccounts(o: JSONObject): List<JSONObject> =
    o.arr("accounts").mapObj { it }.filter { !it.has("monitoring") || it.b("monitoring") }

/** Pace ki limits (drop %, spike %): Google Ads Settings load ho to wahan se, warna 40 / 60 (Gads screen jaisa). */
private fun gLimits(g: GState): Pair<Double, Double> {
    val th = g.view("settings")?.arr("thresholds")?.mapObj { it }.orEmpty()
    fun v(k: String, d: Double): Double = th.firstOrNull { it.s("key") == k }?.let { it.dn("value") ?: it.dn("default") }?.takeIf { it > 0 } ?: d
    return v("drop_pct", 40.0) to v("spike_pct", 60.0)
}

private fun ageShort(m: Int): String = when {
    m < 120 -> tf("%s min", m)
    m < 2880 -> tf("%s hours", m / 60)
    else -> tf("%s days", m / 1440)
}

/** Account ka haal + chhota shabd, Google Ads screen ke niyam se hi (ginti dono jagah same). */
private fun gAcct(a: JSONObject, drop: Double, spike: Double): Pair<KKind, String> {
    val age = a.dn("data_age_min")?.toInt()
    val crit = a.d("critical").toInt()
    val warn = a.d("warning").toInt()
    val health = a.s("health")
    val paceTxt = a.dn("pace_pct")?.takeIf { it.isFinite() }?.let { p ->
        when {
            p < -drop -> tf("Spend %s lower", num(-p, 0) + "%")
            p > spike -> tf("Spend %s higher", num(p, 0) + "%")
            else -> null
        }
    }
    val fresh0 = health.isBlank() || health.equals("UNKNOWN", ignoreCase = true)
    return when {
        fresh0 && age == null -> KKind.NEUTRAL to "Just added".t()
        age == null -> KKind.BAD to "No data".t()
        !a.b("fresh") -> KKind.WARN to tf("Data %s late", ageShort(age))
        crit > 0 -> KKind.BAD to tf("%s alerts", crit + warn)
        warn > 0 -> KKind.WARN to (paceTxt ?: tf("%s alerts", warn))
        paceTxt != null -> KKind.WARN to paceTxt
        else -> KKind.OK to KKind.OK.word()
    }
}

private fun gadsKind(o: JSONObject, drop: Double, spike: Double): KKind {
    val accs = gAccounts(o)
    val setup = o.obj("setup")
    val run = o.optJSONObject("last_run")?.s("status").orEmpty()
    val ks = accs.map { gAcct(it, drop, spike).first }
    return when {
        accs.isEmpty() -> KKind.NEUTRAL
        (setup.has("cron_ok") && !setup.b("cron_ok")) || run == "FAILED" || KKind.BAD in ks -> KKind.BAD
        KKind.WARN in ks || run == "PARTIAL" -> KKind.WARN
        else -> KKind.OK
    }
}

/** Sab accounts ki milkar raftaar: Σ aaj / Σ aam − 1 (alag currency ho to ₹ me), Gads hero jaisa. Na bane to null. */
private fun gPace(accs: List<JSONObject>, usdInr: Double?): Double? {
    val mixed = accs.map { it.s("currency").trim().uppercase() }.filter { it.isNotBlank() }.distinct().size > 1
    var now = 0.0
    var usual = 0.0
    for (a in accs) {
        if (!a.b("fresh")) continue
        val p = a.dn("pace_pct")?.takeIf { it.isFinite() } ?: continue
        val u = a.dn("usual_by_now")?.takeIf { it > 0 && it.isFinite() } ?: continue
        val k = if (!mixed) 1.0 else when (a.s("currency").trim().uppercase()) {
            "INR" -> 1.0
            "USD" -> usdInr?.takeIf { it > 0 && it.isFinite() } ?: continue
            else -> continue
        }
        now += u * (1 + p / 100.0) * k
        usual += u * k
    }
    return if (usual > 0) (now / usual - 1) * 100 else null
}

@Composable
private fun PaceLine(p: Double, drop: Double, spike: Double) {
    val c = Mfc.c
    val k = if (p < -drop || p > spike) KKind.WARN else KKind.OK
    val w = when {
        p < -drop -> "Below normal".t()
        p > spike -> "Above normal".t()
        else -> "Normal".t()
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            buildAnnotatedString {
                append("Spend".t() + " ")
                withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)) { append(kSignedPct(p)) }
            },
            Modifier.weight(1f), color = c.ink2, style = hs(14.sp, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        KStatus(w, k)
    }
}

@Composable
private fun GadsModule(g: GState, kind: KKind?, usdInr: Double?, open: (String) -> Unit) {
    val o = g.view("overview", GADS_P)
    KModule(KTint.Sand, { open("gads") }) {
        KModuleHead(KIcons.Ads, "Google Ads".t()) {
            ModPill(kind, o == null && g.missing, o == null && g.error != null, idle = "Not set up".t())
        }
        if (o == null) ModWait() else GadsBody(o, gLimits(g), usdInr, open)
    }
}

@Composable
private fun GadsBody(o: JSONObject, lim: Pair<Double, Double>, usdInr: Double?, open: (String) -> Unit) {
    val (drop, spike) = lim
    val accs = gAccounts(o)
    val st = accs.map { gAcct(it, drop, spike) }
    val bad = st.count { it.first == KKind.BAD }
    val warn = st.count { it.first == KKind.WARN }
    val ok = st.count { it.first == KKind.OK }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            if (accs.isEmpty()) KCount("0", caption = "No accounts".t())
            else KCount(num((bad + warn).toDouble()), "/" + num(accs.size.toDouble()), "accounts to watch".t())
        }
        if (accs.isNotEmpty()) KMarks(ok, warn, bad)
    }
    gPace(accs, usdInr)?.let { PaceLine(it, drop, spike) }
    // dikkat wale 2 accounts (turant wale pehle)
    val worst = accs.zip(st).filter { it.second.first == KKind.BAD || it.second.first == KKind.WARN }
        .sortedBy { if (it.second.first == KKind.BAD) 0 else 1 }.take(2)
    val items = worst.map { (a, kw) ->
        KDuoItem(a.s("company").ifBlank { a.s("account_name").ifBlank { a.s("cid_fmt") } }, kw.second, kw.first)
    }
    val cid = worst.singleOrNull()?.first?.s("cid")
    KDuo(items.getOrNull(0), items.getOrNull(1)) { open(if (cid.isNullOrBlank()) "gads" else "gads/account/$cid") }
}

// ---- Research Studio (Lav)

private fun studioKind(o: JSONObject): KKind {
    val sp = o.obj("spend")
    val lim = sp.d("limit_usd")
    val used = sp.d("month_usd")
    val cron = tsMs(o.sn("cron_last"))
    val now = tsMs(o.sn("server_time")) ?: System.currentTimeMillis()
    val key = o.arr("sources").mapObj { it }.firstOrNull { it.s("id") == "claude_web" }?.s("status")
    // Studio screen ke hero jaisa: cron band / AI key nahi / budget = turant, cron late = dhyan do
    return when {
        cron == null || key == "NOT_CONFIGURED" || lim <= 0.0 || used >= lim -> KKind.BAD
        now - cron > 20 * 60_000L -> KKind.WARN
        o.arr("pending").length() > 0 -> KKind.ASK
        else -> KKind.OK
    }
}

/** "Gate 2 · 2 din se" (detail: "PRJ-004 · since 2026-10-20 09:14:33 UTC"). */
private fun gateLine(p: JSONObject): String {
    val gate = tf("Gate %s", p.d("gate").toInt())
    val ms = Regex("since (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})").find(p.s("detail"))?.groupValues?.getOrNull(1)?.let { tsMs(it) }
        ?: return gate
    val days = ((System.currentTimeMillis() - ms) / 86_400_000L).coerceAtLeast(0L)
    return gate + " · " + (if (days == 0L) "today".t() else tf("since %s days", days))
}

@Composable
private fun StudioModule(sg: GState, kind: KKind?, open: (String) -> Unit) {
    val o = sg.view("overview")
    KModule(KTint.Lav, { open("studio") }) {
        KModuleHead(KIcons.Bulb, "Research Studio".t()) { ModPill(kind, o == null && sg.missing, o == null && sg.error != null) }
        if (o == null) ModWait() else StudioBody(o, open)
    }
}

@Composable
private fun StudioBody(o: JSONObject, open: (String) -> Unit) {
    val cnt = o.obj("counts")
    val newN = cnt.d("ideas_new").toInt()
    val watchN = cnt.d("ideas_watch").toInt()
    val pend = o.arr("pending").mapObj { it }
    val gates = pend.filter { it.d("gate") >= 2 }
    KCount(num(gates.size.toDouble()), " " + "ideas".t(), "awaiting your OK".t())
    KSteps(
        listOf(
            num(newN.toDouble()) to "new".t(),
            num(watchN.toDouble()) to "watching".t(),
            num(gates.size.toDouble()) to "needs OK".t(),
        ),
        active = when {
            gates.isNotEmpty() -> 2
            newN > 0 -> 0
            watchN > 0 -> 1
            else -> -1
        },
    )
    val items = gates.take(2).map { p ->
        KDuoItem(p.s("title").substringAfterLast(" · ").ifBlank { p.s("title") }, gateLine(p), KKind.ASK)
    }.toMutableList()
    if (items.size < 2 && newN > 0) items += KDuoItem("New ideas".t(), tf("Gate %s", 1) + " · " + num(newN.toDouble()) + " " + "new".t(), KKind.ASK)
    KDuo(items.getOrNull(0), items.getOrNull(1)) { open(if (pend.isNotEmpty()) "studio/approvals" else "studio") }
}

// ---------------------------------------------------------------- aur (purane tabs)

private val MORE: List<Triple<String, String, ImageVector>> = listOf(
    Triple("users", "Users", KIcons.Users),
    Triple("live", "Live", KIcons.Live),
    Triple("ads", "Ad revenue", KIcons.Chart),
    Triple("promo", "Cross promo", KIcons.Spark),
)

/** Users / Live / Ad revenue / Cross promo ab tab nahi: yahan neeche se khulte hain. */
@Composable
private fun MoreLinks(open: (String) -> Unit) {
    KSection("More".t())
    KCard(list = true) {
        MORE.forEachIndexed { i, (route, label, icon) ->
            if (i > 0) KListDivider(lead = true)
            KRow(label.t(), lead = { KIconTile(icon) }, chevron = true, onClick = { open(route) })
        }
    }
}

// ---------------------------------------------------------------- small bits

/** "sagar" / "sagar.k@x.com" -> "Sagar". */
private fun firstName(u: String): String {
    val w = u.substringBefore('@').split(' ', '.', '_', '-').firstOrNull { it.isNotBlank() } ?: return ""
    return w.trim().lowercase().replaceFirstChar { it.titlecase() }
}

private fun hs(size: TextUnit, weight: FontWeight = FontWeight.Normal, lh: TextUnit = TextUnit.Unspecified): TextStyle =
    KNum.copy(fontSize = size, fontWeight = weight, lineHeight = lh)

/** Ek line ka text (tabular numbers, ellipsis). */
@Composable
private fun HT(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal, lh: TextUnit = TextUnit.Unspecified,
) {
    Text(text, modifier, color = color, style = hs(size, weight, lh), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun HLine(color: Color) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Chhota label + value (purana public helper; abhi koi screen use nahi karti, rakha hai taaki kuch na toote). */
@Composable
fun Mini(l: String, v: String, modifier: Modifier = Modifier, color: Color? = null) {
    Column(modifier) {
        Text(l.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
