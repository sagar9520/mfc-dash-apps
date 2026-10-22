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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.data.AlertsData
import com.myfuturecode.mfcdash.data.AlertsTop
import com.myfuturecode.mfcdash.data.Api
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.GAccount
import com.myfuturecode.mfcdash.data.HConsolesData
import com.myfuturecode.mfcdash.data.HOverview
import com.myfuturecode.mfcdash.data.IssueApp
import com.myfuturecode.mfcdash.data.PageLoad
import com.myfuturecode.mfcdash.data.PerApp
import com.myfuturecode.mfcdash.data.TopAccount
import com.myfuturecode.mfcdash.data.TopLiveApp
import com.myfuturecode.mfcdash.data.TopZone
import com.myfuturecode.mfcdash.data.alertsTop
import com.myfuturecode.mfcdash.data.budgetZone
import com.myfuturecode.mfcdash.data.healthTop
import com.myfuturecode.mfcdash.data.issueReason
import com.myfuturecode.mfcdash.data.roasZone
import com.myfuturecode.mfcdash.data.topAccounts
import com.myfuturecode.mfcdash.data.topLiveApps
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
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLive
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
import com.myfuturecode.mfcdash.ui.kit.KSkelCircle
import com.myfuturecode.mfcdash.ui.kit.KSkelLine
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KSteps
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kRoasColor
import com.myfuturecode.mfcdash.ui.kit.kSignedPct
import com.myfuturecode.mfcdash.ui.kit.shape
import com.myfuturecode.mfcdash.ui.kit.word
import com.myfuturecode.mfcdash.ui.theme.Ink
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.MfcColors
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Home (Roop 3 - number aur chhota chart): aaj ka munafa (hero) → Ads kharcha | Live users (widget jaisa card) →
 * aaj ka haal (App Health, Alerts, Google Ads, Research Studio) → aaj ke top apps (profit | loss).
 * Fold khula: do column. AI Kharcha ab Settings me hai.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: DashVm, st: UiState, nav: NavHostController, hvm: HealthVm, gvm: GadsVm, svm: StudioVm) {
    val h by hvm.s.collectAsStateWithLifecycle()
    val g by gvm.s.collectAsStateWithLifecycle()
    val sg by svm.s.collectAsStateWithLifecycle()
    // teen modules ka overview: Home khuli rahe tab har minute (60 sec se purana ho tabhi)
    HomeTicker { loadModules(hvm, gvm, svm, force = false); loadAlertsIfNone(vm, st) }
    LaunchedEffect(Unit) { if (st.data == null && !st.loading) vm.refresh() }
    // data.php fail hote hi alerts khud lao (agle tick ka minute na ruke, warna Alerts me skeleton chalta rahe)
    LaunchedEffect(st.error) { loadAlertsIfNone(vm, st) }

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
        onRefresh = { PageLoad.start(); pulling = true; vm.refresh(); loadModules(hvm, gvm, svm, force = true); HomeLive30.stale() },
        modifier = Modifier.fillMaxSize(),
    ) {
        KScreen(pullRefresh = false, top = {
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
                        TopCard(st, g, open)
                        TopApps(st, money, open, tab)
                        MoreLinks(open)
                    },
                    right = {
                        HealthModule(h, hk, open)
                        AlertsModule(st, tab)
                        GadsModule(g, gk, dash?.r, open)
                        StudioModule(sg, sk, open)
                    },
                )
            } else {
                TopCard(st, g, open)
                KSection("Today's status".t(), trailing = { HaalPill(todo, known) })
                HealthModule(h, hk, open)
                AlertsModule(st, tab)
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
    var alerts = 0L
}

private fun loadModules(hvm: HealthVm, gvm: GadsVm, svm: StudioVm, force: Boolean) {
    val now = System.currentTimeMillis()
    // minute wala tick (sirf jab kuch laana ho): upar ki line, 15 s me ek baar (PageLoad.startAuto). Pull-down khud start() karta hai.
    if (!force && (now - HomeLoads.health >= LOAD_GAP_MS || now - HomeLoads.gads >= LOAD_GAP_MS || now - HomeLoads.studio >= LOAD_GAP_MS)) PageLoad.startAuto()
    // consoles view = har app ke khule issue (App Health ke top 3 issue apps, widget jaisa)
    if (force || now - HomeLoads.health >= LOAD_GAP_MS) { HomeLoads.health = now; hvm.loadOverview(); hvm.loadConsoles() }
    if (force || now - HomeLoads.gads >= LOAD_GAP_MS) { HomeLoads.gads = now; gvm.load("overview", GADS_P) }
    if (force || now - HomeLoads.studio >= LOAD_GAP_MS) { HomeLoads.studio = now; svm.load("overview") }
}

/**
 * Alerts DashVm ke refresh ke saath aate hain (har 20 sec). data.php fail ho to woh alerts nahi laata:
 * tab Home khud laaye (minute me ek baar), taaki Alerts module khaali na rahe.
 */
private fun loadAlertsIfNone(vm: DashVm, st: UiState) {
    val now = System.currentTimeMillis()
    if (st.alerts != null || st.alertsMissing || st.loading || st.error == null || now - HomeLoads.alerts < LOAD_GAP_MS) return
    HomeLoads.alerts = now
    PageLoad.startAuto()
    vm.refreshAlerts()
}

/** 30 min ke active users (api/app.php), sirf Home ke 3 live apps ke: saath me, 60 sec cache (process bhar). */
private object HomeLive30 {
    private val v = HashMap<String, Double?>()   // pkg -> ginti (null = nahi mili)
    private val at = HashMap<String, Long>()
    /** Pull-down par badhta hai: cache chhod ke turant dobara. */
    var rev by mutableIntStateOf(0)
        private set

    fun stale() { at.clear(); rev++ }

    /** Jo pehle aa chuke (na aaye = map me nahi, wahan skeleton). */
    fun known(pkgs: List<String>): Map<String, Double?> = pkgs.filter { it in at }.associateWith { v[it] }

    suspend fun load(api: Api, pkgs: List<String>): Map<String, Double?> {
        val now = System.currentTimeMillis()
        val due = pkgs.filter { now - (at[it] ?: 0L) >= LOAD_GAP_MS }
        if (due.isNotEmpty()) coroutineScope {
            due.map { p ->
                async {
                    p to try {
                        api.extra(p, "today")?.active?.live30m
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll()
        }.forEach { (p, n) ->
            // fail = pichhli ginti rahe (pehli baar = "–")
            if (n != null || p !in v) v[p] = n
            at[p] = now
        }
        return known(pkgs)
    }
}

/** Dikhne wale apps ki 30 min ginti: Home khuli rahe tab har minute (cache 60 sec), app peeche ho to ruk jata hai. */
@Composable
private fun rememberLive30(pkgs: List<String>): Map<String, Double?> {
    val key = pkgs.joinToString(",")
    var m by remember(key) { mutableStateOf(HomeLive30.known(pkgs)) }
    if (pkgs.isEmpty()) return m
    val api = (LocalContext.current.applicationContext as App).api
    val rev = HomeLive30.rev
    val life = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key, rev, life) {
        life.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                m = HomeLive30.load(api, pkgs)
                delay(TICK_MS)
            }
        }
    }
    return m
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
        // data aa raha (ya abhi shuru hi nahi hua) = hero ka dhaancha; fail = "–" + Dobara try
        if (st.loading || st.error == null) { HomeHeroSkel(); return }
        KHero(KTint.Green) {
            KHeroHead("Today's profit".t())
            KBigNum("–", size = 48.sp)
            KButton("Try again".t(), { vm.refresh() }, Modifier.padding(top = 8.dp), kind = KBtn.SOFT, icon = KIcons.Refresh)
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

/** Paisa aane tak hero ka dhaancha: label, bada number, Kamai | Kharcha, ROAS meter, profit | loss patti (asli jitna ooncha). */
@Composable
private fun HomeHeroSkel() {
    KHero(KTint.Green) {
        val c = Mfc.c
        KHeroHead("Today's profit".t())
        KSkeleton {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(.52f, 38.dp) }
                // Kamai | Kharcha
                Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    HLine(c.line)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        repeat(2) { i ->
                            Column(Modifier.weight(1f).leftLine(i > 0, c.line).padding(start = if (i > 0) 18.dp else 0.dp, end = 4.dp)) {
                                KSkelLine(if (i == 0) 64.dp else 80.dp, 11.dp)
                                KSkelLine(if (i == 0) 96.dp else 84.dp, 18.dp, Modifier.padding(top = 9.dp))
                            }
                        }
                    }
                }
                // ROAS meter
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    HLine(c.line)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        KSkelLine(52.dp, 13.dp)
                        KSkelLine(64.dp, 22.dp, Modifier.padding(start = 10.dp))
                        Box(Modifier.weight(1f))
                        KSkelLine(88.dp, 24.dp)
                    }
                    KSkelLine(1f, 8.dp, Modifier.padding(top = 14.dp))
                }
                // profit wale | loss wale
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    HLine(c.line)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 10.dp)) {
                        repeat(2) { i ->
                            Column(Modifier.weight(1f).leftLine(i > 0, c.line).padding(start = if (i > 0) 16.dp else 0.dp, end = 4.dp)) {
                                KSkelLine(if (i == 0) 84.dp else 72.dp, 12.dp)
                                KSkelLine(if (i == 0) 110.dp else 96.dp, 13.dp, Modifier.padding(top = 7.dp))
                                KSkelLine(76.dp, 17.dp, Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Hero ke kone me: "Live · 2 min pehle"; summary purana ho to amber note. */
@Composable
private fun HeroFresh(st: UiState, dd: DashData) {
    if (dd.aggLagSec > 900) KStatus(tf("data %s min old", (dd.aggLagSec / 60).toInt()), KKind.WARN)
    else if (dd.today && st.lastOk > 0 && !dd.cached) KLive("Live".t() + " · " + agoText(st.lastOk))   // disk wala copy = Live nahi
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
        // data aa raha = do column ka dhaancha; fail = "Data nahi"
        if (st.loading || st.error == null) TopAppsSkel() else KCard { KEmpty("No data".t()) }
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

/** Top apps aane tak: wahi card, do column (profit | loss), har taraf sir + 2 naam ki jagah. */
@Composable
private fun TopAppsSkel() {
    KSkeleton {
        KCard(padding = PaddingValues(top = 18.dp, bottom = 6.dp)) {
            val c = Mfc.c
            Row(Modifier.fillMaxWidth()) {
                repeat(2) { side ->
                    Column(Modifier.weight(1f).leftLine(side > 0, c.line).padding(start = if (side > 0) 18.dp else 20.dp, end = 16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Box(Modifier.height(18.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(if (side == 0) 72.dp else 60.dp, 12.dp) }
                            Box(Modifier.padding(top = 3.dp).height(28.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(88.dp, 20.dp) }
                            Box(Modifier.height(17.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(48.dp, 10.dp) }
                        }
                        repeat(2) { i ->
                            HLine(c.line)
                            Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp, bottom = 10.dp)) {
                                Box(Modifier.height(20.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(listOf(.78f, .6f)[i], 13.dp) }
                                Box(Modifier.padding(top = 2.dp).height(24.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(72.dp, 16.dp) }
                                Box(Modifier.height(17.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(64.dp, 10.dp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Ads kharcha | Live users (widget jaisa card)

/** Card me baayein (Ads) hisse ki chaudai, mockup ki tarah 47%. */
private const val TOP_LEFT = .47f

/**
 * Hero ke neeche ek card, do hisse: baayein aaj ke top 3 Google Ads accounts (kharcha + budget %), daayein abhi ke
 * top 3 live apps (6 min | naam | 30 min + ROAS line). Hisaab data/TopCards.kt se (Ads + Live widget bhi wahi).
 */
@Composable
private fun TopCard(st: UiState, g: GState, open: (String) -> Unit) {
    val c = Mfc.c
    val dd = st.data
    val live = remember(dd) { dd?.let { topLiveApps(it) }.orEmpty() }
    val l30 = rememberLive30(live.map { it.pkg })
    val o = g.view("overview", GADS_P)
    val accs = remember(o, dd?.r) { o?.let { topAccounts(it.arr("accounts").mapObj(GAccount::of), dd?.r) } }
    val line = c.line
    KCard(padding = PaddingValues(top = 14.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().drawBehind {
                val x = size.width * TOP_LEFT
                drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            },
        ) {
            // baayein: Ads kharcha (aaj) + budget %
            Column(Modifier.fillMaxWidth(TOP_LEFT).padding(start = 14.dp, end = 10.dp)) {
                TopHead("Ads spend".t()) { open("gads") }
                when {
                    accs != null && accs.isNotEmpty() -> {
                        TopCols("today".t(), "budget %".t())
                        accs.forEach { a -> AcctRow(a) { open(if (a.cid.isBlank()) "gads" else "gads/account/${a.cid}") } }
                    }
                    accs != null || g.missing -> TopNote("Not set up".t())
                    g.error != null -> TopNote("No data".t())
                    else -> {
                        TopCols("today".t(), "budget %".t())
                        TopSkel(live = false)
                    }
                }
            }
            // daayein: Live users (6 min | 30 min) + ROAS
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 12.dp)) {
                TopHead("Live users".t()) { open("live") }
                when {
                    live.isNotEmpty() -> {
                        TopCols(tf("%s min", 6), tf("%s min", 30))
                        live.forEach { a -> LiveRow(a, l30, a.pkg in l30) { open("app/${a.pkg}") } }
                    }
                    dd != null || (!st.loading && st.error != null) -> TopNote("No data".t())
                    else -> {
                        TopCols(tf("%s min", 6), tf("%s min", 30))
                        TopSkel(live = true)
                    }
                }
            }
        }
    }
}

/**
 * Aadhe hisse ka sir: naam (bold) + "View more ›" (brand). Jagah kam ho to sirf "›".
 * Link dikhne me 24dp ka, tap ke liye 48dp (upar-neeche card ki padding me).
 */
@Composable
private fun TopHead(title: String, onMore: () -> Unit) {
    val c = Mfc.c
    val more = "View more".t()
    Layout(
        content = {
            Text(title, color = c.ink, style = hs(13.5.sp, FontWeight.Bold, 20.sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            TopMore(more, true, onMore)
            TopMore(more, false, onMore)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { ms, cs ->
        val w = cs.maxWidth
        val h = 24.dp.roundToPx()
        val gap = 4.dp.roundToPx()
        val full = ms[1].measure(Constraints())
        val short = ms[2].measure(Constraints())
        val link = if (ms[0].maxIntrinsicWidth(h) + gap + full.width <= w) full else short
        val t = ms[0].measure(Constraints(maxWidth = (w - gap - link.width).coerceAtLeast(0)))
        layout(w, max(h, t.height)) {
            t.place(0, (max(h, t.height) - t.height) / 2)
            link.place(w - link.width, (max(h, t.height) - link.height) / 2)
        }
    }
}

@Composable
private fun TopMore(text: String, full: Boolean, onClick: () -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (full) 2.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (full) Text(text, color = c.brand, style = hs(11.sp, FontWeight(650), 14.sp), maxLines = 1, softWrap = false)
        Icon(KIcons.Chev, if (full) null else text, tint = c.brand, modifier = Modifier.size(if (full) 12.dp else 16.dp))
    }
}

/** Chhote label: "aaj" / "budget %", "6 min" / "30 min". */
@Composable
private fun TopCols(left: String, right: String) {
    val c = Mfc.c
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HT(left, 11.sp, c.ink3, Modifier.weight(1f), weight = FontWeight.SemiBold, lh = 14.sp)
        HT(right, 11.sp, c.ink3, weight = FontWeight.SemiBold, lh = 14.sp)
    }
}

/** Ek chhoti grey line (Setup nahi / Data nahi). */
@Composable
private fun TopNote(text: String) {
    HT(text, 13.sp, Mfc.c.ink3, Modifier.padding(top = 8.dp, bottom = 4.dp), weight = FontWeight.Medium, lh = 18.sp)
}

/** Account: aaj ka kharcha (apni currency) + naam; neeche budget ki patti + % pill. Tap = us account ki screen. */
@Composable
private fun AcctRow(a: TopAccount, onClick: () -> Unit) {
    val c = Mfc.c
    val pct = a.budgetPct
    TopRow(onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(cash(a.spend, a.currency), Modifier.alignByBaseline(), color = c.ink, style = hs(15.sp, FontWeight.ExtraBold, 20.sp), maxLines = 1, softWrap = false)
            Text(
                a.name, Modifier.weight(1f).alignByBaseline(), color = c.ink2, style = hs(12.sp, FontWeight.SemiBold, 18.sp),
                textAlign = TextAlign.End, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
        }
        TBar(pct?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() }, pct?.let { num(it, 0) + "%" }, budgetZone(pct), zones = false)
    }
}

/** Live app: 6 min ginti | naam (beech) | 30 min ginti; neeche ROAS ki 3 zone line + % pill. Tap = app detail. */
@Composable
private fun LiveRow(a: TopLiveApp, l30: Map<String, Double?>, known: Boolean, onClick: () -> Unit) {
    val c = Mfc.c
    val roas = a.roas
    TopRow(onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(num(a.live6, 0), Modifier.alignByBaseline(), color = c.ink, style = hs(15.sp, FontWeight.ExtraBold, 20.sp), maxLines = 1, softWrap = false)
            Text(
                a.name, Modifier.weight(1f).alignByBaseline(), color = c.ink2, style = hs(12.sp, FontWeight.SemiBold, 18.sp),
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
            if (known) {
                Text(
                    l30[a.pkg]?.let { num(it, 0) } ?: "–", Modifier.alignByBaseline(), color = c.ink2,
                    style = hs(15.sp, FontWeight(750), 20.sp), maxLines = 1, softWrap = false,
                )
            } else {
                Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) { KSkelLine(26.dp, 12.dp) }
            }
        }
        TBar(roas?.let { (it / 2.0).coerceIn(0.0, 1.0).toFloat() }, roas?.let { roasPct(it) }, roasZone(roas), zones = true)
    }
}

/** Ek line ka dhaancha: upar number/naam, 5dp, neeche patti (7dp upar-neeche). */
@Composable
private fun TopRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) { content() }
}

/** Data aane tak 3 lines ki jagah (asli line jitni oonchi, taaki card na uchhle). */
@Composable
private fun TopSkel(live: Boolean) {
    KSkeleton {
        Column(Modifier.fillMaxWidth()) {
            repeat(3) { i ->
                Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KSkelLine(if (live) 28.dp else 56.dp, 14.dp)
                        Box(Modifier.weight(1f), contentAlignment = if (live) Alignment.Center else Alignment.CenterEnd) {
                            KSkelLine(listOf(48.dp, 40.dp, 52.dp)[i], 10.dp)
                        }
                        if (live) KSkelLine(28.dp, 14.dp)
                    }
                    Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) { KSkelLine(1f, 4.dp) }
                }
            }
        }
    }
}

/**
 * Patli line (16dp) aur us par chhoti pill. zones = ROAS line: laal 0-80%, amber 80-100%, hara 100-200% (Paisa meter jaisa);
 * warna budget: track + bhari patti. pos = 0..1; null = sirf grey track, pill nahi.
 */
@Composable
private fun TBar(pos: Float?, pill: String?, zone: TopZone, zones: Boolean) {
    val c = Mfc.c
    val fill = zoneColor(zone, c)
    Layout(
        content = { if (pos != null && pill != null) TPill(pill, fill) },
        modifier = Modifier.fillMaxWidth().height(16.dp).drawBehind {
            val y = 6.dp.toPx()
            val h = 4.dp.toPx()
            val r = CornerRadius(h / 2)
            if (zones && pos != null) {
                val gap = 2.dp.toPx()
                val room = size.width - 2 * gap
                var x = 0f
                listOf(80f to c.zRed, 20f to c.zAmber, 100f to c.zGreen).forEach { (part, col) ->
                    val pw = room * part / 200f
                    drawRoundRect(col, Offset(x, y), Size(pw, h), r)
                    x += pw + gap
                }
            } else {
                drawRoundRect(c.zTrack, Offset(0f, y), Size(size.width, h), r)
                if (pos != null && pos > 0f) drawRoundRect(fill, Offset(0f, y), Size(size.width * pos, h), r)
            }
        },
    ) { ms, cs ->
        val w = if (cs.hasBoundedWidth) cs.maxWidth else cs.minWidth
        val h = 16.dp.roundToPx()
        val p = ms.firstOrNull()?.measure(Constraints())
        layout(w, h) {
            if (p != null && pos != null) {
                // pill ka beech patti par us jagah, par patti se bahar nahi
                val half = p.width / 2
                val edge = max(20.dp.roundToPx(), half)
                val cx = (w * pos).roundToInt().coerceIn(min(edge, w / 2), max(w - edge, w / 2))
                p.place(cx - half, (h - p.height) / 2)
            }
        }
    }
}

/** Patti par "72%" pill: zone ka rang, gehra text, card rang ki 2dp ring (patti se alag dikhe). */
@Composable
private fun TPill(text: String, bg: Color) {
    val c = Mfc.c
    Box(
        Modifier.background(c.card, RoundedCornerShape(10.dp)).padding(2.dp).background(bg, RoundedCornerShape(8.dp))
            .height(16.dp).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink, style = hs(11.sp, FontWeight.ExtraBold, 16.sp), maxLines = 1, softWrap = false)
    }
}

private fun zoneColor(z: TopZone, c: MfcColors): Color = when (z) {
    TopZone.RED -> c.zRed
    TopZone.AMBER -> c.zAmber
    TopZone.GREEN -> c.zGreen
    TopZone.NONE -> c.zTrack
}

/** Account ki apni currency: ₹ INR, $ USD, baaki code ("EUR 120"). */
private fun cash(v: Double, cur: String): String {
    val sym = when (cur) {
        "INR" -> "₹"
        "USD" -> "\$"
        "" -> ""
        else -> "$cur "
    }
    return (if (v < 0) "−" else "") + sym + num(abs(v), 0)
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

/**
 * Module ka data nahi: wait = abhi aa raha (skeleton: number, caption, patti, neeche do naam);
 * warna (setup nahi / fail) "–" aur khaali patti, pill batata hai kyun.
 */
@Composable
private fun ModWait(wait: Boolean) {
    if (!wait) {
        KCount("–")
        KSegBar(emptyList())
        return
    }
    val c = Mfc.c
    KSkeleton(Modifier.padding(top = 4.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(76.dp, 28.dp) }
            Box(Modifier.height(18.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(150.dp, 11.dp) }
            KSkelLine(1f, 8.dp, Modifier.padding(top = 14.dp))
            // KDuo jaisi neeche ki patti
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                HLine(c.line)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 10.dp)) {
                    repeat(2) { i ->
                        Column(Modifier.weight(1f).leftLine(i > 0, c.line).padding(start = if (i > 0) 16.dp else 0.dp, end = 4.dp)) {
                            Box(Modifier.height(20.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(if (i == 0) 104.dp else 88.dp, 13.dp) }
                            Box(Modifier.padding(top = 2.dp).height(18.dp), contentAlignment = Alignment.CenterStart) {
                                KSkelLine(if (i == 0) 84.dp else 96.dp, 10.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- App Health (Rose)

private fun healthKind(o: HOverview): KKind = when {
    o.issuesP0 > 0 || !o.cronOk || o.integrations.any { (it.kind == "play" || it.kind == "tracker") && it.status == "ERROR" } -> KKind.BAD
    o.withProblems > 0 || o.needsHuman > 0 -> KKind.WARN
    o.aabReady > 0 -> KKind.ASK
    else -> KKind.OK
}

/** Widget jaisa: kul apps | issue wale (laal) | theek (hare) + sabse zyada khule issue wale 3 apps (hisaab TopCards.kt me). */
@Composable
private fun HealthModule(h: HState, kind: KKind?, open: (String) -> Unit) {
    val o = h.overview
    KModule(KTint.Rose, { open("health") }) {
        KModuleHead(KIcons.Pulse, "App Health".t()) { ModPill(kind, o == null && h.missing, o == null && h.error != null) }
        when {
            o != null -> HealthBody(o, h.consoles, open)
            h.missing || h.error != null -> HealthTri("–", "–", "–")
            else -> HealthSkel()
        }
    }
}

@Composable
private fun HealthBody(o: HOverview, cons: HConsolesData?, open: (String) -> Unit) {
    val c = Mfc.c
    val t = remember(o, cons) { healthTop(o, cons) }
    HealthTri(num(t.total.toDouble()), num(t.withIssues.toDouble()), num(t.fine.toDouble()))
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (t.top.isEmpty()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(KIcons.Ok, null, tint = c.markOk, modifier = Modifier.size(20.dp))
                HT("All fine".t(), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
            }
        }
        t.top.forEachIndexed { i, a ->
            if (i > 0) HLine(c.line)
            IssueRow(a, issueReason(o, a.pkg)) { open(if (a.pkg.isBlank()) "health" else "health/app/${a.pkg}") }
        }
    }
}

/** Teen number, beech me patli line: "47 apps | 25 issue | 22 theek". */
@Composable
private fun HealthTri(total: String, bad: String, fine: String) {
    val c = Mfc.c
    val cells = listOf(Triple(total, "apps".t(), c.ink), Triple(bad, "issue".t(), c.red), Triple(fine, "fine".t(), c.green))
    Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        cells.forEachIndexed { i, (n, label, col) ->
            Column(Modifier.weight(1f).leftLine(i > 0, c.line).padding(start = if (i > 0) 14.dp else 0.dp, end = 4.dp)) {
                Text(
                    n, color = col, style = hs(28.sp, FontWeight.ExtraBold, 32.sp).copy(letterSpacing = (-0.01).em),
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                )
                HT(label, 13.sp, c.ink3, weight = FontWeight.Medium, lh = 16.sp)
            }
        }
    }
}

/** Issue wala app: laal ginti + naam + chhoti wajah (Crash / ANR / Ads). Tap = us app ka AI Health. */
@Composable
private fun IssueRow(a: IssueApp, reason: String?, onClick: () -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.widthIn(min = 32.dp).height(24.dp).clip(RoundedCornerShape(7.dp)).background(c.redSoft).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(num(a.open.toDouble()), color = c.red, style = hs(14.sp, FontWeight.ExtraBold, 18.sp), maxLines = 1, softWrap = false)
        }
        HT(a.name, 15.sp, c.ink, Modifier.weight(1f), weight = FontWeight.SemiBold, lh = 20.sp)
        if (reason != null) HT(reason.t(), 12.5.sp, c.ink3, weight = FontWeight.SemiBold, lh = 16.sp)
    }
}

/** Health ka data aane tak: teen number aur 3 lines ki jagah (chamak ke saath). */
@Composable
private fun HealthSkel() {
    val c = Mfc.c
    KSkeleton(Modifier.padding(top = 10.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) {
                repeat(3) { i ->
                    Column(Modifier.weight(1f).leftLine(i > 0, c.line).padding(start = if (i > 0) 14.dp else 0.dp)) {
                        Box(Modifier.height(32.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(if (i == 0) 40.dp else 32.dp, 24.dp) }
                        Box(Modifier.height(16.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(36.dp, 10.dp) }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                repeat(3) { i ->
                    if (i > 0) HLine(c.line)
                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KSkelLine(32.dp, 24.dp)
                        Box(Modifier.weight(1f)) { KSkelLine(listOf(.62f, .5f, .4f)[i], 13.dp) }
                        KSkelLine(36.dp, 10.dp)
                    }
                }
            }
        }
    }
}

// ---- Alerts (Lav): naye 3 khule alerts, widget jaisa

@Composable
private fun AlertsModule(st: UiState, tab: (String) -> Unit) {
    val al = st.alerts
    val top = remember(al) { al?.let { alertsTop(it) } }
    KModule(KTint.Lav, { tab("alerts") }) {
        KModuleHead(KIcons.Bell, "Alerts".t()) {
            when {
                top != null && top.unread > 0 -> KPill(tf("%s new", num(top.unread.toDouble())), KKind.BAD, icon = null)
                al == null && st.alertsMissing -> KPill("Not set up".t(), KKind.NEUTRAL)
                al == null && st.alertsError != null -> KPill("No data".t(), KKind.NEUTRAL)
            }
        }
        when {
            top != null && al != null -> AlertsBody(top, al)
            st.alertsMissing || st.alertsError != null -> {}   // pill hi kaafi (Setup nahi / Data nahi)
            else -> AlertsSkel()
        }
    }
}

@Composable
private fun AlertsBody(t: AlertsTop, al: AlertsData) {
    val c = Mfc.c
    // kab aaya: Alerts tab jaisa created_at (TopCards ka card id rakhta hai)
    val at = remember(al) { al.alerts.associate { it.id to tsMs(it.createdAt) } }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (t.items.isEmpty()) AlertLine(KKind.OK, "No alerts".t(), "All fine".t(), "")
        t.items.forEachIndexed { i, a ->
            if (i > 0) HLine(c.line)
            AlertLine(a.kind, a.nameL(), a.arg?.let { tf(a.label, it) } ?: a.label.t(), agoShort(at[a.id]))
        }
    }
}

/** Ek alert: nishaan (laal diamond / amber triangle / hara check / info) + app (bold) + kism + kitni der pehle. */
@Composable
private fun AlertLine(k: KKind, name: String, label: String, ago: String) {
    val c = Mfc.c
    val mark = when (k) {
        KKind.OK -> c.markOk
        KKind.WARN -> c.markWarn
        KKind.BAD -> c.markBad
        else -> c.brand
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 55.dp).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(k.shape() ?: KIcons.Info, k.word(), tint = mark, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            HT(name, 15.sp, c.ink, weight = FontWeight(650), lh = 20.sp)
            if (label.isNotBlank()) HT(label, 13.sp, c.ink2, weight = FontWeight.Medium, lh = 17.sp)
        }
        if (ago.isNotBlank()) HT(ago, 12.5.sp, c.ink3, lh = 16.sp)
    }
}

/** Alerts aane tak: 3 lines ki jagah. */
@Composable
private fun AlertsSkel() {
    val c = Mfc.c
    KSkeleton(Modifier.padding(top = 6.dp)) {
        Column(Modifier.fillMaxWidth()) {
            repeat(3) { i ->
                if (i > 0) HLine(c.line)
                Row(Modifier.fillMaxWidth().height(55.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KSkelCircle(20.dp)
                    Column(Modifier.weight(1f)) {
                        KSkelLine(listOf(.55f, .66f, .48f)[i], 13.dp)
                        KSkelLine(listOf(.32f, .26f, .38f)[i], 10.dp, Modifier.padding(top = 7.dp))
                    }
                    KSkelLine(40.dp, 10.dp)
                }
            }
        }
    }
}

/** "10 min" / "1 ghanta" / "2 ghante" / "3 din" (alert kab aaya). */
private fun agoShort(ms: Long?): String {
    if (ms == null) return ""
    val m = ((System.currentTimeMillis() - ms) / 60_000L).coerceAtLeast(0L)
    return when {
        m < 1 -> "now".t()
        m < 60 -> tf("%s min", m)
        m < 120 -> "1 hour".t()
        m < 1440 -> tf("%s hours", m / 60)
        m < 2880 -> "1 day".t()
        else -> tf("%s days", m / 1440)
    }
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
        if (o == null) ModWait(!g.missing && g.error == null) else GadsBody(o, gLimits(g), usdInr, open)
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
        if (o == null) ModWait(!sg.missing && sg.error == null) else StudioBody(o, open)
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

/** Baayein patli khadi line (number / column ke beech). */
private fun Modifier.leftLine(on: Boolean, color: Color): Modifier =
    if (!on) this else drawBehind { drawLine(color, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx()) }

/** Chhota label + value (purana public helper; abhi koi screen use nahi karti, rakha hai taaki kuch na toote). */
@Composable
fun Mini(l: String, v: String, modifier: Modifier = Modifier, color: Color? = null) {
    Column(modifier) {
        Text(l.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
