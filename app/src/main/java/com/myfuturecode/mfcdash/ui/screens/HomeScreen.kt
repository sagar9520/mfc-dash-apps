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
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
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
import com.myfuturecode.mfcdash.data.GAppBudget
import com.myfuturecode.mfcdash.data.HConsolesData
import com.myfuturecode.mfcdash.data.HOverview
import com.myfuturecode.mfcdash.data.IssueApp
import com.myfuturecode.mfcdash.data.PageLoad
import com.myfuturecode.mfcdash.data.PerApp
import com.myfuturecode.mfcdash.data.TopLiveApp
import com.myfuturecode.mfcdash.data.TopSpendApp
import com.myfuturecode.mfcdash.data.TopZone
import com.myfuturecode.mfcdash.data.alertsTop
import com.myfuturecode.mfcdash.data.budgetZone
import com.myfuturecode.mfcdash.data.healthTop
import com.myfuturecode.mfcdash.data.issueReason
import com.myfuturecode.mfcdash.data.roasZone
import com.myfuturecode.mfcdash.data.topLiveApps
import com.myfuturecode.mfcdash.data.topSpendApps
import com.myfuturecode.mfcdash.data.withBudgets
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.GadsVm
import com.myfuturecode.mfcdash.ui.HState
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.StudioVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.cacheSameDay
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
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSkelCircle
import com.myfuturecode.mfcdash.ui.kit.KSkelLine
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KT
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
import com.myfuturecode.mfcdash.util.dayEndMs
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.fmtDayRange
import com.myfuturecode.mfcdash.util.fmtDayWk
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.inrK
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
        onRefresh = { PageLoad.start(); pulling = true; vm.refresh(); loadModules(hvm, gvm, svm, force = true); HomeLive30.stale(); HomeToday.stale() },
        modifier = Modifier.fillMaxSize(),
    ) {
        KScreen(pullRefresh = false, top = {
            if (wide) {
                KHomeBar("MFC", hello, unread, urgent, { tab("alerts") }, middle = { HaalHead(Modifier.weight(1f), todo, known) })
            } else {
                KHomeBar("MFC", hello, unread, urgent, { tab("alerts") })
                HomePeriodTabs(vm, st, Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp))
                HomeHero(st, money, { vm.refresh() }, open, tab)
            }
        }) {
            if (wide) {
                // Fold: baayein period tabs + paisa, daayein aaj ka haal (heading app bar me)
                KTwoPane(
                    left = {
                        HomePeriodTabs(vm, st)
                        HomeHero(st, money, { vm.refresh() }, open, tab)
                        TopCard(st, gOv, open)
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
                TopCard(st, gOv, open)
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
/** Live users (doosre tab par, [HomeToday]) isse purana = nahi dikhate. */
private const val LIVE_OLD_MS = 2 * TICK_MS
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

/**
 * Live users wala aadha hamesha abhi ka: Home par doosra period (kal / 7 din / 30 din / custom) chuna ho tab data.php aaj
 * alag se (6 min ginti + aaj ka ROAS; DashVm purane period ko 20 s me taaza nahi karta). Aaj wala view dikhe tab DashVm ka
 * taaza jawab yahin ([put]), to tab badalte hi wahi; warna disk wala (aaj ka hi), phir network; 60 sec cache (process bhar).
 * Fail = pichhla rahe, par [LIVE_OLD_MS] se purana kabhi nahi (dhaancha); kabhi aaya hi nahi = failed.
 */
private object HomeToday {
    var d by mutableStateOf<DashData?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    private var at = 0L
    /** [d] kab ka hai (network jawab / disk copy ka waqt). */
    private var dAt = 0L
    private var who = ""
    /** Pull-down par badhta hai: cache chhod ke turant dobara. */
    var rev by mutableIntStateOf(0)
        private set

    fun stale() { at = 0L; rev++ }

    /** [d], par [LIVE_OLD_MS] se purana nahi: app peeche raha / call baar-baar fail = dhaancha, ghanton purani ginti nahi. */
    val fresh: DashData? get() = d?.takeIf { System.currentTimeMillis() - dAt <= LIVE_OLD_MS }

    /** Aaj wala view ka network jawab (DashVm, [t] = kab aaya): doosra tab chunte hi yahi, aur ek minute tak nayi call nahi. */
    fun put(v: DashData, user: String, t: Long) { who = user; d = v; dAt = t; failed = false; at = t }

    suspend fun load(api: Api, user: String) {
        // doosra login = pichhle user ka data nahi
        if (user != who) { who = user; d = null; failed = false; at = 0L }
        if (d != null && System.currentTimeMillis() - dAt > LIVE_OLD_MS) { d = null; failed = false; at = 0L }   // at = 0: turant laao (warna dhaancha bina call ke ~55 s)
        if (d == null) api.dataCached("all", "today")?.takeIf { cacheSameDay(it.at) && System.currentTimeMillis() - it.at <= LIVE_OLD_MS }
            ?.let { d = it.value; dAt = it.at }
        if (System.currentTimeMillis() - at < LOAD_GAP_MS) return
        try {
            d = api.data("all", "today")
            dAt = System.currentTimeMillis()
            failed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
        at = System.currentTimeMillis()
    }
}

/**
 * Live users ka data: Home par aaj wala view = wahi (DashVm, 20 s; network wala [HomeToday] me bhi), warna [HomeToday] har
 * minute (null = abhi aaya nahi / bahut purana).
 */
@Composable
private fun rememberLiveData(st: UiState): DashData? {
    val app = LocalContext.current.applicationContext as App
    if (homeToday(st)) {
        val d = st.data
        // disk wala copy nahi (us ka waqt purana); lastOk = yeh jawab kab aaya
        LaunchedEffect(d) { if (d != null && !d.cached) HomeToday.put(d, app.prefs.username, st.lastOk) }
        return d
    }
    val rev = HomeToday.rev
    val life = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(rev, life) {
        life.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                HomeToday.load(app.api, app.prefs.username)
                delay(TICK_MS)
            }
        }
    }
    return HomeToday.fresh
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

/** Home ka paisa: Kamai = ads (ad revenue) + pay (payments), kharcha = Google Ads. Profit / loss Apps tab jaisa hi (ginti mile). */
private class HomeMoney(
    val profit: Double, val ads: Double, val pay: Double, val spend: Double, val roas: Double?, val r: Double,
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
    return HomeMoney(k.profitInr, k.revInr, k.purchaseInr, k.gadsCostInr, roas, r, wins, losses, dd.perApp.size, names)
}

/** "+₹18,450" / "−₹2,140" (asli minus). */
private fun signedInr(v: Double): String = when {
    v > 0 -> "+" + inr(v)
    v < 0 -> "−" + inr(-v)
    else -> inr(v)
}

// ---- period (Home ke tabs, Paisa ke saath ek hi: DashVm.setRange)

private val HOME_RANGES = listOf("today", "yesterday", "7d", "30d")

/** Home ke period tabs: Aaj | Kal | 7 din | 30 din. Paisa par custom chuna ho to paanchva "Custom" (chuna hua). */
@Composable
private fun HomePeriodTabs(vm: DashVm, st: UiState, modifier: Modifier = Modifier) {
    HomeTabs(st.range, modifier) { k ->
        // chuna hua tab dobara = bas refresh (setRange wahi period pehchaan leta hai; custom ki taareekhein bhi wahi)
        if (k == st.range) vm.setRange(k, st.from, st.to) else vm.setRange(k)
    }
}

@Composable
private fun HomeTabs(range: String, modifier: Modifier = Modifier, pick: (String) -> Unit) {
    val keys = if (range == "custom") HOME_RANGES + "custom" else HOME_RANGES
    val labels = keys.map { k -> (RANGES.firstOrNull { it.first == k }?.second ?: k).t() }
    // patle phone par (5 tab bhi) naam chhote font me, "..." nahi; Fold par poori chaudai nahi
    KSegTabs(labels, keys.indexOf(range), { pick(keys[it]) }, modifier.widthIn(max = 560.dp), fit = true)
}

private fun isTodayView(dd: DashData): Boolean = dd.today && dd.single

/** Aaj wala view? Data ho to usi se (custom me aaj ka ek din bhi), warna chuna hua tab (naya period abhi aa raha). */
private fun homeToday(st: UiState): Boolean = st.data?.let { isTodayView(it) } ?: (st.range == "today")

/** Period ki taareekhein (data se, warna custom ki chuni hui): from to to. Pata nahi = null. */
private fun periodDays(st: UiState): Pair<String, String>? {
    val dd = st.data
    val from = (dd?.from ?: st.from)?.takeIf { it.isNotBlank() } ?: return null
    val to = (dd?.to ?: st.to)?.takeIf { it.isNotBlank() } ?: from
    return from to (if (dd?.single == true) from else to)
}

/** Hero ka label, Paisa screen jaisa: Aaj / Kal / 7 din / 30 din ka munafa; custom = "Profit" (taareekh daayein pill me). */
private fun profitLabel(st: UiState): String = when {
    homeToday(st) -> "Today's profit".t()
    st.range == "yesterday" -> "Yesterday's profit".t()
    st.range == "7d" -> "7 days profit".t()
    st.range == "30d" -> "30 days profit".t()
    else -> "Profit".t()
}

/**
 * Top apps ka title: "Today's top apps" / "Yesterday's top apps" / "Top apps · 7 days" / "Top apps · 16 – 22 Oct".
 * Jagah kam (patla phone / bada font) = sirf "Top apps" ([TopAppsHead]; period upar tab aur hero ki goli me hai).
 */
private fun topAppsTitle(st: UiState): String = when {
    homeToday(st) -> "Today's top apps".t()
    st.range == "yesterday" -> "Yesterday's top apps".t()
    st.range == "7d" -> "Top apps".t() + " · " + "7 days".t()
    st.range == "30d" -> "Top apps".t() + " · " + "30 days".t()
    else -> "Top apps".t() + (periodDays(st)?.let { (a, b) -> " · " + fmtDayRange(a, b) } ?: "")
}

/**
 * Ads kharcha ke kul ke daayein column label, pehla jo aa jaye: aaj = "today · ROAS" (pehle jaisa); doosra period =
 * "yesterday · ROAS", jagah kam ho to sirf "yesterday" (ROAS patti ki pill batati hai). 7 / 30 din / custom bhi aise hi.
 */
private fun spendLabels(st: UiState): List<String> = when {
    homeToday(st) -> listOf("today · ROAS".t())
    st.range == "yesterday" -> listOf("yesterday · ROAS".t(), "yesterday".t())
    st.range == "7d" -> listOf("7 days · ROAS".t(), "7 days".t())
    st.range == "30d" -> listOf("30 days · ROAS".t(), "30 days".t())
    else -> listOf("period · ROAS".t(), "period".t())
}

@Composable
private fun HomeHero(st: UiState, m: HomeMoney?, retry: () -> Unit, open: (String) -> Unit, tab: (String) -> Unit) {
    val dd = st.data
    if (dd == null || m == null) {
        // data aa raha (ya abhi shuru hi nahi hua) = hero ka dhaancha; fail = "–" + Dobara try. Label chune hue period ka.
        if (st.loading || st.error == null) { HomeHeroSkel(profitLabel(st)); return }
        KHero(KTint.Green) {
            KHeroHead(profitLabel(st))
            KBigNum("–", size = 48.sp)
            KButton("Try again".t(), retry, Modifier.padding(top = 8.dp), kind = KBtn.SOFT, icon = KIcons.Refresh)
        }
        return
    }
    KHero(KTint.Green, onClick = { open("money") }) {
        val c = Mfc.c
        val pills = if (homeToday(st) || lagInPeriod(dd)) null else dayPills(st)
        if (pills == null) KHeroHead(profitLabel(st), trailing = { HeroFresh(st, dd) }) else HeroDaysHead(profitLabel(st), pills)
        KBigNum(signedInr(m.profit), if (m.profit >= 0) c.green else c.red, size = 48.sp)
        KSplit(moneySplit(m.ads, m.pay, m.spend))
        KRoasMeter(m.roas, st.roasTarget)
        KHeroDuo(winSide(m), lossSide(m)) { tab("apps") }
    }
}

/** Paisa aane tak hero ka dhaancha: label, bada number, Kamai | Ads se | Payments | Kharcha, ROAS meter, profit | loss patti (asli jitna ooncha). */
@Composable
private fun HomeHeroSkel(label: String) {
    KHero(KTint.Green) {
        val c = Mfc.c
        KHeroHead(label)
        KSkeleton {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(.52f, 38.dp) }
                // Kamai | Ads se | Payments | Kharcha (4 khaane, KSplit jaisa 10dp gap)
                Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    HLine(c.line)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        repeat(4) { i ->
                            Column(Modifier.weight(1f).leftLine(i > 0, c.line).padding(start = if (i > 0) 10.dp else 0.dp, end = 4.dp)) {
                                KSkelLine(if (i % 2 == 0) .66f else .84f, 11.dp)
                                KSkelLine(if (i % 2 == 0) .92f else .74f, 16.dp, Modifier.padding(top = 7.dp))
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

/**
 * Hero ke kone me: aaj = "Live · 2 min pehle"; summary purana ho (aur period ke andar tak, [lagInPeriod]) to amber note.
 * Doosre period par note na ho to yahan nahi aate: taareekh ki goli ([HeroDaysHead]).
 */
@Composable
private fun HeroFresh(st: UiState, dd: DashData) {
    if (lagInPeriod(dd)) KStatus(tf("data %s min old", (dd.aggLagSec / 60).toInt()), KKind.WARN)
    else if (dd.today && st.lastOk > 0 && !dd.cached) KLive("Live".t() + " · " + agoText(st.lastOk))   // disk wala copy = Live nahi
}

/**
 * Summary (cron) 15 min se peeche hai aur period ka kuch hissa abhi gina nahi gaya? Aaj wala period = haan. Band period (kal /
 * purana custom) = tabhi jab summary period ke ant se pehle tak hi pahuncha (raat 00:20 par 40 min peeche = kal ke aakhri
 * 20 min nahi). Disk wale copy me aggLagSec me uski umar judi hai, to "abhi - lag" = summary kahan tak tha, wahi sahi.
 */
private fun lagInPeriod(dd: DashData): Boolean {
    if (dd.aggLagSec <= 900) return false
    if (dd.today) return true
    val end = dayEndMs(dd.to) ?: return true
    return System.currentTimeMillis() - (dd.aggLagSec * 1000).toLong() < end
}

/**
 * Doosre period ki goli ke roop, lambe se chhote: Kal = "Wed, 22 Oct" (icon) → "22 Oct" (icon) → "22 Oct"; 7 / 30 din / custom
 * = "16 – 22 Oct" (icon) → bina icon. Aakhri null = goli hi nahi (label poora; period upar ke tab me likha hai). Custom me
 * taareekh hi period batati hai: goli rehti hai. Taareekh pata nahi = null (goli nahi).
 */
private fun dayPills(st: UiState): List<Pair<String, Boolean>?>? {
    val (a, b) = periodDays(st) ?: return null
    val l = if (a != b) fmtDayRange(a, b).let { listOf(it to true, it to false) } else listOf(fmtDayWk(a) to true, fmtDay(a) to true, fmtDay(a) to false)
    return if (st.range == "custom") l else l + null
}

/**
 * Hero ki pehli line, doosra period: label (KHeroHead jaisa) + daayein taareekh ki goli. Label pehle: goli ka pehla roop jisme
 * label poora aaye (null roop = goli nahi); koi na aaye (bada font, patla phone) = sabse chhota roop, label "..." (kam se kam 48dp).
 */
@Composable
private fun HeroDaysHead(label: String, pills: List<Pair<String, Boolean>?>) {
    val c = Mfc.c
    Layout(
        content = {
            KT(label, 15.sp, c.ink2, weight = FontWeight.SemiBold, lh = 20.sp)
            pills.forEach { p -> if (p == null) Box(Modifier) else DaysPill(p.first, p.second) }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
    ) { ms, cs ->
        val w = cs.maxWidth
        val labW = ms[0].maxIntrinsicWidth(Constraints.Infinity)
        val opts = ms.drop(1)
        fun gap(pw: Int) = if (pw > 0) 10.dp.roundToPx() else 0
        val pick = opts.firstOrNull { val pw = it.maxIntrinsicWidth(Constraints.Infinity); labW + gap(pw) + pw <= w } ?: opts.last()
        val pill = pick.measure(Constraints(maxWidth = (w - 10.dp.roundToPx() - min(labW, 48.dp.roundToPx())).coerceAtLeast(0)))
        val lab = ms[0].measure(Constraints(maxWidth = (w - gap(pill.width) - pill.width).coerceAtLeast(0)))
        val h = max(cs.minHeight, max(lab.height, pill.height))
        layout(w, h) {
            lab.place(0, (h - lab.height) / 2)
            pill.place(w - pill.width, (h - pill.height) / 2)
        }
    }
}

/** Taareekh ki goli: card rang (brand band me halki safed), calendar icon (icon = false: bina), 13sp. */
@Composable
private fun DaysPill(text: String, icon: Boolean) {
    val c = Mfc.c
    Row(
        Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(if (c.onBrand) Color.White.copy(alpha = .18f) else c.card)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon) Icon(KIcons.Calendar, null, tint = c.ink2, modifier = Modifier.size(14.dp))
        HT(text, 13.sp, c.ink2, weight = FontWeight.SemiBold, lh = 18.sp)
    }
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

/** Chune hue period ke top apps: ek card, baayein profit wale 2, daayein loss wale 2 (har naam tap = app detail). */
@Composable
private fun TopApps(st: UiState, m: HomeMoney?, open: (String) -> Unit, tab: (String) -> Unit) {
    TopAppsHead(topAppsTitle(st), "Top apps".t()) { KSectionLink("View more".t()) { tab("apps") } }
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

/**
 * KSection jaisa sir (18sp bold + daayein "View more"), par title poora na aaye to [short] ("Top apps"): "Yesterday's top a..."
 * ya "Top apps · 28 Sep – 04 Oct" kat ke nahi dikhte (patla phone, bada font). short bhi na aaye to "...".
 */
@Composable
private fun TopAppsHead(title: String, short: String, trailing: @Composable () -> Unit) {
    val c = Mfc.c
    Layout(
        content = {
            KT(title, 18.sp, c.ink, weight = FontWeight.Bold, lh = 24.sp, ls = (-0.01).em)
            KT(short, 18.sp, c.ink, weight = FontWeight.Bold, lh = 24.sp, ls = (-0.01).em)
            Box { trailing() }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(start = 4.dp, end = 4.dp, top = 2.dp),
    ) { ms, cs ->
        val w = cs.maxWidth
        val tr = ms[2].measure(Constraints(maxWidth = w))
        val room = (w - tr.width - 12.dp.roundToPx()).coerceAtLeast(0)
        val t = (if (ms[0].maxIntrinsicWidth(Constraints.Infinity) <= room) ms[0] else ms[1]).measure(Constraints(maxWidth = room))
        val h = max(cs.minHeight, max(t.height, tr.height))
        layout(w, h) {
            t.place(0, (h - t.height) / 2)
            tr.place(w - tr.width, (h - tr.height) / 2)
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
 * Hero ke neeche ek card, do hisse: baayein chune hue period ka kul Google Ads kharcha + sabse zyada kharch wale 3 apps,
 * daayein abhi ke top 3 live apps (6 min | naam | 30 min + aaj ke ROAS ki line). Hisaab data/TopCards.kt se (Ads + Live
 * widget ka "By app" bhi wahi; widget hamesha aaj ka, apna load). Kharcha hero wale data (DashVm, Home tabs / Paisa ka
 * period) se. Aaj: line me kharcha | naam | ROAS, neeche aaj ke budget ki patti (Google Ads overview gOv ke app_budgets se;
 * overview nahi / monitor set nahi = "—"). Doosra period: kharcha | naam, neeche us period ke ROAS ki line (budget aaj ka hi
 * hota hai). Live users hamesha abhi + aaj ([rememberLiveData]). ROAS ke rang Settings ke target se (hero meter jaisa).
 */
@Composable
private fun TopCard(st: UiState, gOv: JSONObject?, open: (String) -> Unit) {
    val dd = st.data
    val today = homeToday(st)
    val ld = rememberLiveData(st)
    val live = remember(ld) { ld?.let { topLiveApps(it) }.orEmpty() }
    val l30 = rememberLive30(live.map { it.pkg })
    // live ka data na aaya: aaj wale view me DashVm ki galti, warna alag wale load ki
    val liveFailed = if (today) !st.loading && st.error != null else HomeToday.failed
    val bud = remember(gOv) { GAppBudget.list(gOv) }
    val spend = remember(dd, bud, today) { dd?.let { if (today) withBudgets(topSpendApps(it), bud, it.r) else topSpendApps(it) } }
    TopCardBody(st, today, spend, live, ld != null || liveFailed, l30, open)
}

/** liveDone = Live users ka data aa chuka ya fail (live khaali ho to "No data", warna dhaancha). */
@Composable
private fun TopCardBody(
    st: UiState, today: Boolean, spend: List<TopSpendApp>?, live: List<TopLiveApp>, liveDone: Boolean, l30: Map<String, Double?>,
    open: (String) -> Unit,
) {
    val c = Mfc.c
    val dd = st.data
    val failed = !st.loading && st.error != null
    val tg = st.roasTarget
    val line = c.line
    KCard(padding = PaddingValues(top = 14.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().drawBehind {
                val x = size.width * TOP_LEFT
                drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            },
        ) {
            // baayein: Ads kharcha (chuna hua period): kul + top 3 apps
            Column(Modifier.fillMaxWidth(TOP_LEFT).padding(start = 14.dp, end = 10.dp)) {
                TopHead("Ads spend".t()) { open("gads") }
                when {
                    dd != null && spend != null -> {
                        TopTotal(inr(dd.kpi.gadsCostInr.takeIf { it.isFinite() }), if (spend.isEmpty()) emptyList() else spendLabels(st))
                        if (spend.isEmpty()) TopNote(if (today) "No ad spend today".t() else "No ad spend".t(), lines = 2)
                        else spend.forEach { a -> if (today) SpendRow(a, tg) { open("app/${a.pkg}") } else SpendRoasRow(a, tg) { open("app/${a.pkg}") } }
                    }
                    failed -> TopNote("No data".t())
                    else -> TopSkel(live = false)
                }
            }
            // daayein: Live users (6 min | 30 min) + ROAS
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 12.dp)) {
                TopHead("Live users".t()) { open("live") }
                when {
                    live.isNotEmpty() -> {
                        TopCols(tf("%s min", 6), tf("%s min", 30))
                        live.forEach { a -> LiveRow(a, l30, a.pkg in l30, tg) { open("app/${a.pkg}") } }
                    }
                    liveDone -> TopNote("No data".t())
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

/**
 * Kul kharcha (bada) + daayein "aaj · ROAS" / "kal · ROAS" ... (ROAS wale column ka label), baseline par. Widget ke plan
 * jaisa: label ke saath 19sp, na aaye to 17sp; phir agla (chhota) label; koi na aaye to label hatta (19sp, phir 17 / 15sp).
 * Number kabhi nahi katta. labels khaali: period me kharcha hi nahi (ROAS ka column nahi), sirf number.
 */
@Composable
private fun TopTotal(total: String, labels: List<String>) {
    val c = Mfc.c
    Layout(
        content = {
            for (size in listOf(19, 17, 15)) {
                Text(total, color = c.ink, style = hs(size.sp, FontWeight.ExtraBold, 24.sp), maxLines = 1, softWrap = false)
            }
            labels.forEach { Text(it, color = c.ink3, style = hs(11.sp, FontWeight.SemiBold, 14.sp), maxLines = 1, softWrap = false) }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) { ms, cs ->
        val w = cs.maxWidth
        val gap = 6.dp.roundToPx()
        val big = ms[0].measure(Constraints())
        val mid = ms[1].measure(Constraints())
        val labs = ms.drop(3).map { it.measure(Constraints()) }
        val pick = labs.firstNotNullOfOrNull { lab ->
            when {
                big.width + gap + lab.width <= w -> big to lab
                mid.width + gap + lab.width <= w -> mid to lab
                else -> null
            }
        }
        val (t, lab) = pick ?: when {
            big.width <= w -> big to null
            mid.width <= w -> mid to null
            else -> ms[2].measure(Constraints(maxWidth = w)) to null
        }
        layout(w, t.height) {
            t.place(0, 0)
            lab?.place(w - lab.width, (t[FirstBaseline] - lab[FirstBaseline]).coerceAtLeast(0))
        }
    }
}

/** Ek chhoti grey line (Setup nahi / Data nahi). lines = 2: lamba note ("Aaj ad kharcha nahi") kate to "nahi" na gire. */
@Composable
private fun TopNote(text: String, lines: Int = 1) {
    val m = Modifier.padding(top = 8.dp, bottom = 4.dp)
    if (lines <= 1) HT(text, 13.sp, Mfc.c.ink3, m, weight = FontWeight.Medium, lh = 18.sp)
    else Text(text, m, color = Mfc.c.ink3, style = hs(13.sp, FontWeight.Medium, 18.sp), maxLines = lines, overflow = TextOverflow.Ellipsis)
}

/**
 * App: aaj ka Google Ads kharcha | naam (beech) | ROAS % (zone ke rang me, target = Settings ka ROAS target); neeche aaj ke
 * budget ki patti ([BudBar]). Tap = app detail.
 */
@Composable
private fun SpendRow(a: TopSpendApp, target: Double, onClick: () -> Unit) {
    val c = Mfc.c
    val roas = a.roas
    val z = roasZone(roas, target)
    TopRow(onClick) {
        SpendLine(inr(a.spendInr), a.name, roasPct(roas), zoneText(z, c))
        BudBar(a.budgetPct?.takeIf { a.budgetInr != null && it.isFinite() }, a.budgetInr)
    }
}

/**
 * Doosra period (kal / 7 din / 30 din / custom): kharcha | naam (daayein); neeche us period ke ROAS ki 3 zone line + % pill
 * (Live users wali line jaisi). Budget % nahi (budget sirf aaj ka hota hai). Tap = app detail.
 */
@Composable
private fun SpendRoasRow(a: TopSpendApp, target: Double, onClick: () -> Unit) {
    TopRow(onClick) {
        SpendLine(inr(a.spendInr), a.name, null, Mfc.c.ink3)
        RoasBar(a.roas, target)
    }
}

/**
 * Aaj ke budget ki patti (widget jaisi): kharch tak budget zone ka rang (100% se upar bhi bas poori) + sire par % pill, ant me
 * budget ("₹70K", 6dp door, patti se bahar). pct null = budget pata nahi: sirf track + "—", pill nahi.
 */
@Composable
private fun BudBar(pct: Double?, budget: Double?) {
    val c = Mfc.c
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f)) {
            TBar(pct?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() }, pct?.let { num(it, 0) + "%" }, budgetZone(pct), zones = false)
        }
        Text(
            if (pct == null || budget == null) "—" else inrK(budget), color = c.ink3,
            style = hs(11.sp, FontWeight.Bold, 14.sp), maxLines = 1, softWrap = false,
        )
    }
}

/**
 * SpendRow ki line, baseline par: kharcha | naam (beech) | ROAS %. Widget ke plan jaisa: naam ko ~40dp na mile to pehle
 * kharcha / ROAS chhote (15 / 13.5sp se 13 / 12sp); phir naam 24dp tak; phir bhi tang = ROAS % hatta (patti ka rang ROAS
 * batata hai). Kharcha kabhi nahi katta. roas = null (doosra period, ROAS neeche ki line par): kharcha | naam (daayein).
 */
@Composable
private fun SpendLine(amount: String, name: String, roas: String?, roasColor: Color) {
    val c = Mfc.c
    Layout(
        content = {
            Text(amount, color = c.ink, style = hs(15.sp, FontWeight.ExtraBold, 20.sp), maxLines = 1, softWrap = false)
            Text(amount, color = c.ink, style = hs(13.sp, FontWeight.ExtraBold, 20.sp), maxLines = 1, softWrap = false)
            Text(roas.orEmpty(), color = roasColor, style = hs(13.5.sp, FontWeight.ExtraBold, 20.sp), maxLines = 1, softWrap = false)
            Text(roas.orEmpty(), color = roasColor, style = hs(12.sp, FontWeight.ExtraBold, 20.sp), maxLines = 1, softWrap = false)
            Text(
                name, color = c.ink2, style = hs(12.sp, FontWeight.SemiBold, 18.sp),
                textAlign = if (roas == null) TextAlign.End else TextAlign.Center, maxLines = 1, softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp),
    ) { ms, cs ->
        val w = cs.maxWidth
        val gap = 6.dp.roundToPx()
        val big = ms[0].measure(Constraints())
        val small = ms[1].measure(Constraints())
        val rBig = ms[2].measure(Constraints())
        val rSmall = ms[3].measure(Constraints())
        val nameW = ms[4].maxIntrinsicWidth(20.dp.roundToPx())
        fun room(a: Placeable, r: Placeable?) = w - a.width - gap - (if (r != null) gap + r.width else 0)
        val want = min(nameW, 40.dp.roundToPx())
        val least = min(nameW, 24.dp.roundToPx())
        val (amt, ro) = when {
            roas == null -> (if (room(big, null) >= want) big else small) to null
            room(big, rBig) >= want -> big to rBig
            room(small, rSmall) >= want -> small to rSmall
            room(small, rSmall) >= least -> small to rSmall
            else -> small to null
        }
        val nw = room(amt, ro).coerceAtLeast(0)
        val nm = ms[4].measure(Constraints(minWidth = nw, maxWidth = nw))
        val parts = listOfNotNull(amt, nm, ro)
        val base = parts.maxOf { it[FirstBaseline] }
        val h = max(cs.minHeight, parts.maxOf { base - it[FirstBaseline] + it.height })
        layout(w, h) {
            amt.place(0, base - amt[FirstBaseline])
            nm.place(amt.width + gap, base - nm[FirstBaseline])
            ro?.place(w - ro.width, base - ro[FirstBaseline])
        }
    }
}

/** Live app: 6 min ginti | naam (beech) | 30 min ginti; neeche aaj ke ROAS ki 3 zone line + % pill. Tap = app detail. */
@Composable
private fun LiveRow(a: TopLiveApp, l30: Map<String, Double?>, known: Boolean, target: Double, onClick: () -> Unit) {
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
        RoasBar(roas, target)
    }
}

/**
 * ROAS ki 3 zone line + % pill (Live users / doosre period ka Ads kharcha). Zone hero ke ROAS meter jaisa target se: laal
 * min(target, 100%) tak, amber max(target, 100%) tak, phir hara. Line 0 se [roasTop] tak (target 150% tak = 200%).
 */
@Composable
private fun RoasBar(roas: Double?, target: Double) {
    val top = roasTop(target)
    TBar(roas?.let { (it / top).coerceIn(0.0, 1.0).toFloat() }, roas?.let { roasPct(it) }, roasZone(roas, target), zones = true, target = target)
}

/** ROAS line ka sira: 200%, ya target bada ho to target + 50% (hara hissa kabhi gayab nahi). */
private fun roasTop(target: Double): Double = max(2.0, max(roasT(target), 1.0) + .5)

/** kRoasKind jaisa: galat target = 100%. */
private fun roasT(target: Double): Double = if (target > 0 && target.isFinite()) target else 1.0

/** Ek line ka dhaancha: upar number/naam, 5dp, neeche patti (7dp upar-neeche). */
@Composable
private fun TopRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) { content() }
}

/**
 * Data aane tak 3 lines ki jagah (asli line jitni oonchi, taaki card na uchhle). live = false: Ads kharcha wala aadha
 * (upar kul kharcha, line me kharcha | naam | ROAS).
 */
@Composable
private fun TopSkel(live: Boolean) {
    KSkeleton {
        Column(Modifier.fillMaxWidth()) {
            if (!live) {
                Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(24.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(72.dp, 18.dp) }
            }
            repeat(3) { i ->
                Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KSkelLine(if (live) 28.dp else 48.dp, 14.dp)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            KSkelLine(listOf(48.dp, 40.dp, 52.dp)[i], 10.dp)
                        }
                        KSkelLine(if (live) 28.dp else 32.dp, 14.dp)
                    }
                    Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) { KSkelLine(1f, 4.dp) }
                }
            }
        }
    }
}

/**
 * Patli line (16dp) aur us par chhoti pill. zones = ROAS line ([RoasBar]): laal 0 se min(target, 100%), amber max(target, 100%)
 * tak, hara [roasTop] tak (target 80% = 0-80 / 80-100 / 100-200%, widget jaisa); warna budget: track + bhari patti.
 * pos = 0..1; null = sirf grey track, pill nahi.
 */
@Composable
private fun TBar(pos: Float?, pill: String?, zone: TopZone, zones: Boolean, target: Double = .8) {
    val c = Mfc.c
    val fill = zoneColor(zone, c)
    Layout(
        content = { if (pos != null && pill != null) TPill(pill, fill) },
        modifier = Modifier.fillMaxWidth().height(16.dp).drawBehind {
            val y = 6.dp.toPx()
            val h = 4.dp.toPx()
            val r = CornerRadius(h / 2)
            if (zones && pos != null) {
                val t = roasT(target)
                val lo = min(t, 1.0)
                val hi = max(t, 1.0)
                val top = roasTop(target)
                // target 100% = amber hissa nahi (khaali hissa aur uska gap nahi)
                val parts = listOf(lo to c.zRed, (hi - lo) to c.zAmber, (top - hi) to c.zGreen).filter { it.first > .001 }
                val gap = 2.dp.toPx()
                val room = size.width - (parts.size - 1) * gap
                var x = 0f
                parts.forEach { (part, col) ->
                    val pw = (room * part / top).toFloat()
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

/** Number (ROAS %) ka rang: laal / amber / hara; pata nahi = halka. */
private fun zoneText(z: TopZone, c: MfcColors): Color = when (z) {
    TopZone.RED -> c.red
    TopZone.AMBER -> c.amber
    TopZone.GREEN -> c.green
    TopZone.NONE -> c.ink3
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
