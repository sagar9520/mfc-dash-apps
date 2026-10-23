package com.myfuturecode.mfcdash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.data.AlertsData
import com.myfuturecode.mfcdash.data.Api
import com.myfuturecode.mfcdash.data.ApiException
import com.myfuturecode.mfcdash.data.CpData
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.ExtraData
import com.myfuturecode.mfcdash.data.PageLoad
import com.myfuturecode.mfcdash.data.Prefs
import com.myfuturecode.mfcdash.notify.AlertsSync
import com.myfuturecode.mfcdash.notify.MonitorService
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.Lang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    val loggedIn: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val range: String = "today",          // today | yesterday | 7d | 30d | custom
    val from: String? = null, val to: String? = null,
    val data: DashData? = null,           // "All apps" ka data (Home/Paisa/Users/Ads/Live)
    val cp: CpData? = null,
    val extra: ExtraData? = null,          // api/app.php (server par ho to)
    val extraMissing: Boolean = false,     // 404 = abhi upload nahi hua
    val alerts: AlertsData? = null,        // api/alerts.php (smart alerts)
    val alertsMissing: Boolean = false,
    val alertsBusy: Boolean = false,
    val alertsError: String? = null,       // alerts.php tak pahunch nahi paye (net / server busy): message + dobara try
    val appExtra: Map<String, ExtraData> = emptyMap(),
    val appData: Map<String, DashData> = emptyMap(),   // App detail: pkg → us app ka data
    val appCp: Map<String, CpData> = emptyMap(),
    val appLoading: String? = null,
    val lastOk: Long = 0L,
    /** > 0: [data] abhi disk cache ka copy hai (us waqt ka), network wala nahi aaya / fail. Live mat kaho. */
    val cachedAt: Long = 0L,
    /** In apps ka [appData] disk cache ka copy hai: dobara load karna hai (loadApp force ke bina bhi). */
    val appCached: Set<String> = emptySet(),
    val roasTarget: Double = 1.5,
    val notifyUrgent: Boolean = true, val notifyWarn: Boolean = true, val notifyInfo: Boolean = true, val checkMinutes: Int = 15,
    val appFilter: String? = null,        // null = sab apps; pkg = sirf us app ka data (har tab par)
    val lang: String = "en",
    val liveMonitor: Boolean = true,
)

class DashVm(private val app: App) : ViewModel() {
    private val api: Api = app.api
    private val prefs: Prefs = app.prefs
    private val _s = MutableStateFlow(UiState(loggedIn = prefs.hasLogin, range = startRange(), roasTarget = prefs.roasTarget,
        notifyUrgent = prefs.notifyUrgent, notifyWarn = prefs.notifyWarn, notifyInfo = prefs.notifyInfo, checkMinutes = prefs.checkMinutes, lang = prefs.lang, liveMonitor = prefs.liveMonitor))
    val s: StateFlow<UiState> = _s
    /** Notification tap se aayi screen ("alerts"); Root consume karke null kar deta hai. */
    val pendingRoute: StateFlow<String?> = app.pendingRoute
    fun consumeRoute() { app.pendingRoute.value = null }
    private var autoJob: Job? = null
    /** "All apps" ka chal raha load, uska period ("range|from|to") aur ginti (naya load = +1: purana state nahi chhoota). */
    private var dataJob: Job? = null
    private var dataKey = ""
    private var dataGen = 0
    /** App detail / app filter ka chal raha load ("pkg|range|from|to"). */
    private var appJob: Job? = null
    private var appKey = ""
    private var appGen = 0

    init {
        if (prefs.hasLogin) { refresh(); startAuto() }
        // app wapas aaye (peeche se) to turant taaza data: auto refresh peeche ruka rehta hai
        viewModelScope.launch { app.foreground.drop(1).filter { it }.collect { if (_s.value.loggedIn) refreshData(auto = true) } }
    }

    val baseUrl get() = prefs.baseUrl
    val username get() = prefs.username

    fun login(url: String, user: String, pass: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _s.update { it.copy(loading = true, error = null) }
            try {
                prefs.baseUrl = url
                api.login(user, pass)
                prefs.username = user; prefs.password = pass
                _s.update { it.copy(loggedIn = true, loading = false) }
                onDone(null)
                AlertsSync.schedule(app, prefs.checkMinutes)
                MonitorService.sync(app)
                refresh(); startAuto()
            } catch (e: Exception) {
                _s.update { it.copy(loading = false) }
                onDone(e.message ?: "Login failed".t())
            }
        }
    }

    fun logout() {
        autoJob?.cancel()
        dataJob?.cancel(); appJob?.cancel(); dataGen++; appGen++
        AlertsSync.cancel(app)
        MonitorService.stop(app)
        api.logout(); prefs.clearLogin()
        _s.value = UiState(loggedIn = false, range = startRange(), roasTarget = prefs.roasTarget,
            notifyUrgent = prefs.notifyUrgent, notifyWarn = prefs.notifyWarn, notifyInfo = prefs.notifyInfo, checkMinutes = prefs.checkMinutes, lang = prefs.lang, liveMonitor = prefs.liveMonitor)
    }

    /**
     * App khulne par yaad wala period. Custom ki taareekhein yaad nahi rehtin (sirf "custom"): bina taareekh Api aaj bhejta,
     * tab "Custom" chuna dikhta aur number aaj ke. Isliye custom = aaj.
     */
    private fun startRange(): String = prefs.range.let { if (it == "custom") "today" else it }

    fun setRange(r: String, from: String? = null, to: String? = null) {
        PageLoad.start()   // user ne period badla = naya load (upar ki neeli line); refresh() me nahi, 20 s poll bhi wahi bulata hai
        val st0 = _s.value
        if (st0.range == r && st0.from == from && st0.to == to) { refresh(); return }   // wahi period dobara chuna = bas refresh
        prefs.range = r
        appJob?.cancel(); appGen++   // purane period ka app load: jawab ab kaam ka nahi
        // purane period ke number naye period ke naam ke neeche kabhi nahi: hatao. Naya load pehle disk wala copy
        // (is period ka, ho to) dikhata hai, phir network; chal raha purana load ruk jaata hai (refreshData).
        _s.update {
            it.copy(range = r, from = from, to = to, data = null, cp = null, extra = null, extraMissing = false, cachedAt = 0L,
                appData = emptyMap(), appCp = emptyMap(), appExtra = emptyMap(), appCached = emptySet(), appLoading = null)
        }
        refresh()
    }

    fun setRoasTarget(v: Double) { prefs.roasTarget = v; _s.update { it.copy(roasTarget = v) } }

    /** Har tab ka app filter (All apps / ek app). Ek app = data.php?app=<pkg> (loadApp). */
    fun setAppFilter(pkg: String?) {
        _s.update { it.copy(appFilter = pkg) }
        if (pkg != null) { PageLoad.start(); loadApp(pkg, force = true) }   // ek app chuna = naya load (All apps = data pehle se, load nahi)
    }

    fun setLang(code: String) {
        prefs.lang = code
        Lang.code = code
        _s.update { it.copy(lang = code) }
        refreshAlerts()   // server ko nayi bhasha batao (AI explanations isi me aayengi)
    }

    /** User / screen ne maanga (refresh icon, pull-down, Try again, app khuli). */
    fun refresh() = refreshData(auto = false)

    private fun periodKey(st: UiState) = st.range + "|" + st.from + "|" + st.to

    /**
     * "All apps" ka data + cross promo + app.php, phir alerts. auto = apne aap (20 s poll, app wapas aayi): upar ki line
     * [PageLoad.startAuto] se (15 s me ek baar, sirf DashVm wali screen dikh rahi ho tab).
     * Wahi period pehle se aa raha ho to dobara nahi bhejta; user ne maanga ho to line us load ke poore hone tak rehti hai.
     * Period badal gaya ho (setRange) to purana load rok kar naya: purane period ka jawab naye ke naam ke neeche nahi aata.
     * Screen par kuch na ho to pehle disk wala pichhla jawab (isi period ka), phir network. loading hamesha band hota hai.
     */
    private fun refreshData(auto: Boolean) {
        val st = _s.value
        val key = periodKey(st)
        val running = dataJob?.takeIf { it.isActive }
        if (running != null) {
            if (key == dataKey) {
                // user ne maanga aur wahi load chal raha: line + spinner us load ke poore hone tak. Data aa chuka ho
                // (cross promo / app.php raste me) to loading false hota: Home ka pull spinner 15 s atka rehta. Isliye
                // join ke dauraan loading = true, join ke baad wapas (naya load shuru ho gaya ho to woh khud sambhalta hai).
                if (!auto) {
                    val g = dataGen
                    if (!_s.value.loading) _s.update { it.copy(loading = true) }
                    viewModelScope.launch {
                        try { PageLoad.track { running.join() } }
                        finally { if (g == dataGen && _s.value.loading) _s.update { it.copy(loading = false) } }
                    }
                }
                return
            }
            running.cancel()
        }
        if (auto && PageLoad.route in DASH_ROUTES) PageLoad.startAuto()
        dataKey = key
        val gen = ++dataGen
        dataJob = viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            val peek = if (_s.value.data == null) launch { showCachedData(st, gen) } else null
            if (_s.value.alerts == null) launch { showCachedAlerts() }   // alerts network wale data ke baad aate hain: tab tak disk wale
            try {
                val d = api.data("all", st.range, st.from, st.to)
                peek?.cancel()
                if (gen != dataGen) return@launch
                _s.update { it.copy(data = d, cachedAt = 0L, loading = false, error = null, lastOk = System.currentTimeMillis()) }
                val cp = runCatching { api.crossPromo("all", st.range, st.from, st.to) }
                ensureActive()
                cp.onSuccess { c -> _s.update { it.copy(cp = c) } }
                val ex = runCatching { api.extra("all", st.range, st.from, st.to) }
                ensureActive()
                ex.onSuccess { x -> _s.update { it.copy(extra = x ?: it.extra, extraMissing = x == null) } }
                refreshAlerts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen != dataGen) return@launch
                val unauthorized = (e as? ApiException)?.unauthorized == true
                // unauthorized = dobara login bhi fail (password badla?) -> login screen
                if (unauthorized) { prefs.clearLogin(); api.logout(); AlertsSync.cancel(app); MonitorService.stop(app) }
                // disk wala copy dikh raha ho to wahi rehta hai (screen khaali nahi), upar snackbar me galti
                _s.update { it.copy(loading = false, error = e.message ?: "No data received".t(), loggedIn = !unauthorized) }
            } finally {
                if (gen == dataGen && _s.value.loading) _s.update { it.copy(loading = false) }   // kabhi "loading" par atka na rahe
            }
        }
    }

    /** Disk wala pichhla sahi jawab (isi period ka) jab tak network ka na aaye. cachedAt / lastOk = us copy ka waqt (live nahi). */
    private suspend fun showCachedData(st: UiState, gen: Int) {
        val c = api.dataCached("all", st.range, st.from, st.to)?.takeIf { usable(st, it.at) } ?: return
        if (gen != dataGen) return
        _s.update { if (it.data != null || !it.loggedIn) it else it.copy(data = c.value.cachedCopy(c.at), cachedAt = c.at, lastOk = c.at) }
        // saath wale bhi usi din ke hon (kal ka cross promo aaj ke data ke saath nahi)
        val cp = api.crossPromoCached("all", st.range, st.from, st.to)?.takeIf { usable(st, it.at) }
        val ex = api.extraCached("all", st.range, st.from, st.to)?.takeIf { usable(st, it.at) }
        if (gen != dataGen) return
        _s.update { if (!it.loggedIn) it else it.copy(cp = it.cp ?: cp?.value, extra = it.extra ?: ex?.value) }
    }

    /** Alerts lao + naye ki notification (AlertsSync). alerts.php na ho to alertsMissing. */
    fun refreshAlerts() {
        viewModelScope.launch {
            // abhi koi list nahi (app khuli): disk wali pichhli list turant, network wali aate hi badal jaati hai
            if (_s.value.alerts == null) launch { showCachedAlerts() }
            runCatching { AlertsSync.check(app) }
                // logout ke baad pahuncha jawab (pichhle user ka) nahi
                .onSuccess { al -> _s.update { if (!it.loggedIn) it else it.copy(alerts = al ?: it.alerts, alertsMissing = al == null, alertsError = null) } }
                .onFailure { e -> _s.update { it.copy(alertsError = e.message ?: "Server not reachable".t()) } }
        }
    }

    /** Disk wali pichhli alerts list (AlertsSync wali, limit 150), sirf jab abhi koi list na ho. */
    private suspend fun showCachedAlerts() {
        val c = api.alertsCached() ?: return
        _s.update { if (it.alerts != null || !it.loggedIn) it else it.copy(alerts = c.value) }
    }

    fun markAllRead() {
        viewModelScope.launch {
            runCatching { api.alertsRead(null) }
            refreshAlerts()
        }
    }

    fun resolveAlert(id: Long) {
        // turant UI se hatao, phir server
        _s.update { st -> st.copy(alerts = st.alerts?.let { al -> al.copy(alerts = al.alerts.map { if (it.id == id) it.copy(resolvedAt = "now") else it }) }) }
        viewModelScope.launch { runCatching { api.alertResolve(id) }; refreshAlerts() }
    }

    fun testNotification(onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try { api.alertTest(); refreshAlerts(); onDone(null) } catch (e: Exception) { onDone(e.message ?: "Test failed".t()) }
        }
    }

    /** Server par rules abhi chalao (warna har 4 min / app ke poll par). */
    fun computeAlerts(onDone: (String?) -> Unit) {
        viewModelScope.launch {
            _s.update { it.copy(alertsBusy = true) }
            try {
                val r = api.alertsCompute().optJSONObject("result")
                refreshAlerts()
                val n = r?.optInt("new", 0) ?: 0
                onDone(if (n > 0) tf("%s new alerts", n) else "No new alert — all good".t())
            } catch (e: Exception) { onDone(e.message ?: "Check failed".t()) }
            _s.update { it.copy(alertsBusy = false) }
        }
    }

    fun setAiKey(key: String, model: String, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _s.update { it.copy(alertsBusy = true) }
            try {
                val j = api.aiKey(key.trim(), model)
                refreshAlerts()
                when {
                    key.isBlank() -> onDone(true, "Claude AI turned off".t())
                    j.optBoolean("test_ok") -> onDone(true, "Key works, Claude AI is on ✅".t())
                    else -> onDone(false, "Key saved but the test call failed: ".t() + (j.optJSONObject("ai")?.optString("last_error") ?: "?"))
                }
            } catch (e: Exception) { onDone(false, e.message ?: "Save failed".t()) }
            _s.update { it.copy(alertsBusy = false) }
        }
    }

    /** 24x7 monitor (foreground service, every minute). Also asks Android to exclude the app from battery optimisation. */
    fun setLiveMonitor(on: Boolean) {
        prefs.liveMonitor = on
        _s.update { it.copy(liveMonitor = on) }
        MonitorService.sync(app)
        if (on) MonitorService.requestBatteryExempt(app)
    }

    fun setNotify(level: Int, on: Boolean) {
        when (level) { 3 -> prefs.notifyUrgent = on; 2 -> prefs.notifyWarn = on; else -> prefs.notifyInfo = on }
        _s.update { it.copy(notifyUrgent = prefs.notifyUrgent, notifyWarn = prefs.notifyWarn, notifyInfo = prefs.notifyInfo) }
    }

    fun setCheckMinutes(m: Int) {
        prefs.checkMinutes = m
        _s.update { it.copy(checkMinutes = m) }
        if (prefs.hasLogin) AlertsSync.schedule(app, m)
    }

    /** App detail screen: us app ka data.php (app=<pkg>) alag se. */
    fun loadApp(pkg: String, force: Boolean = false) {
        val st = _s.value
        if (!force && st.appData.containsKey(pkg) && pkg !in st.appCached) return
        // ek waqt par ek app load. Wahi app + period pehle se aa raha = dobara nahi (force = user / screen ne maanga: line
        // us load tak). Dusra app ya period = purana rok kar naya (pehle wala "loading" par atka nahi rehta).
        val key = pkg + "|" + periodKey(st)
        val running = appJob?.takeIf { it.isActive }
        if (running != null) {
            if (key == appKey) {
                if (force) viewModelScope.launch { PageLoad.track { running.join() } }
                return
            }
            running.cancel()
        }
        appKey = key
        val gen = ++appGen
        appJob = viewModelScope.launch {
            _s.update { it.copy(appLoading = pkg) }
            val peek = if (_s.value.appData[pkg] == null) launch { showCachedApp(st, pkg, gen) } else null
            try {
                val d = api.data(pkg, st.range, st.from, st.to)
                peek?.cancel()
                val cp = runCatching { api.crossPromo(pkg, st.range, st.from, st.to) }.getOrNull()
                ensureActive()
                val ex = runCatching { api.extra(pkg, st.range, st.from, st.to) }.getOrNull()
                ensureActive()
                if (gen != appGen) return@launch
                _s.update { it.copy(appData = it.appData + (pkg to d), appCp = if (cp != null) it.appCp + (pkg to cp) else it.appCp,
                    appExtra = if (ex != null) it.appExtra + (pkg to ex) else it.appExtra, appCached = it.appCached - pkg, appLoading = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == appGen) _s.update { it.copy(appLoading = null, error = e.message) }
            } finally {
                if (gen == appGen && _s.value.appLoading == pkg) _s.update { it.copy(appLoading = null) }
            }
        }
    }

    /** App ka disk wala copy (isi period ka) jab tak network ka na aaye. */
    private suspend fun showCachedApp(st: UiState, pkg: String, gen: Int) {
        val c = api.dataCached(pkg, st.range, st.from, st.to)?.takeIf { usable(st, it.at) } ?: return
        val cp = api.crossPromoCached(pkg, st.range, st.from, st.to)?.takeIf { usable(st, it.at) }
        val ex = api.extraCached(pkg, st.range, st.from, st.to)?.takeIf { usable(st, it.at) }
        if (gen != appGen) return
        _s.update {
            if (it.appData[pkg] != null || !it.loggedIn) it
            else it.copy(appData = it.appData + (pkg to c.value.cachedCopy(c.at)), appCached = it.appCached + pkg,
                appCp = if (cp != null && pkg !in it.appCp) it.appCp + (pkg to cp.value) else it.appCp,
                appExtra = if (ex != null && pkg !in it.appExtra) it.appExtra + (pkg to ex.value) else it.appExtra)
        }
    }

    fun addNote(campaign: String, pkg: String, note: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try { api.addNote(campaign, pkg, note); onDone(null); refreshExtra() } catch (e: Exception) { onDone(e.message) }
        }
    }
    fun deleteNote(id: Long) { viewModelScope.launch { runCatching { api.deleteNote(id) }; refreshExtra() } }
    private fun refreshExtra() {
        val st = _s.value
        val key = periodKey(st)
        // beech me period badla to purane period ka app.php naye ke neeche nahi
        viewModelScope.launch { runCatching { api.extra("all", st.range, st.from, st.to) }.onSuccess { ex -> if (ex != null) _s.update { if (periodKey(it) != key) it else it.copy(extra = ex) } } }
    }

    /** Site jaisa: aaj ka data ho to har 20 sec refresh (alerts bhi saath me). App peeche ho to nahi (24x7 monitor alerts alag se dekhta hai). */
    private fun startAuto() {
        autoJob?.cancel()
        autoJob = viewModelScope.launch {
            var tick = 0
            while (true) {
                delay(20_000)
                tick++
                val st = _s.value
                if (!st.loggedIn || !app.foreground.value) continue
                if (st.data == null || st.data.today) refreshData(auto = true)
                else if (tick % 3 == 0) {   // purana period dekh rahe ho to bhi har minute alerts
                    if (PageLoad.route in DASH_ROUTES) PageLoad.startAuto()
                    refreshAlerts()
                }
            }
        }
    }

    companion object {
        fun factory(app: App) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DashVm(app) as T
        }
    }
}

/** In screens par DashVm ka data dikhta hai: inke khule rehte 20 s wala refresh upar ki line kholta hai (Root ka route). */
private val DASH_ROUTES = setOf("home", "apps", "money", "users", "ads", "live", "promo", "alerts", "app/{pkg}", "campaign/{idx}")

/**
 * Disk wala copy kaam ka? Custom period (pakki taareekhein) = haan. Aaj / kal / 7 din / 30 din = sirf aaj ka bana ho:
 * kal raat ka "today" subah "Aaj ka munafa" ke neeche nahi. (Custom bina taareekh = Api "today" bhejta hai: aaj wala niyam.)
 */
private fun usable(st: UiState, at: Long): Boolean =
    (st.range == "custom" && !st.from.isNullOrBlank() && !st.to.isNullOrBlank()) || cacheSameDay(at)

/** Disk wala copy aaj (phone ka calendar din) ka hai? Aaj / 7 din / is mahine jaise period wale jawab isi par. */
internal fun cacheSameDay(at: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.US)
    return f.format(Date(at)) == f.format(Date())
}

/** Disk wala copy: [DashData.cached] (Live nahi), fetchedAt = kab ka tha, aur "data N min old" me uski umar bhi jud jaati hai. */
private fun DashData.cachedCopy(at: Long): DashData =
    copy(cached = true, fetchedAt = at, aggLagSec = aggLagSec + ((System.currentTimeMillis() - at) / 1000.0).coerceAtLeast(0.0))
