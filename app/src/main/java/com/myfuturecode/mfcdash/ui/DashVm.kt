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
import com.myfuturecode.mfcdash.data.Prefs
import com.myfuturecode.mfcdash.notify.AlertsSync
import com.myfuturecode.mfcdash.notify.MonitorService
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.Lang
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val roasTarget: Double = 1.5,
    val notifyUrgent: Boolean = true, val notifyWarn: Boolean = true, val notifyInfo: Boolean = true, val checkMinutes: Int = 15,
    val appFilter: String? = null,        // null = sab apps; pkg = sirf us app ka data (har tab par)
    val lang: String = "en",
    val liveMonitor: Boolean = true,
)

class DashVm(private val app: App) : ViewModel() {
    private val api: Api = app.api
    private val prefs: Prefs = app.prefs
    private val _s = MutableStateFlow(UiState(loggedIn = prefs.hasLogin, range = prefs.range, roasTarget = prefs.roasTarget,
        notifyUrgent = prefs.notifyUrgent, notifyWarn = prefs.notifyWarn, notifyInfo = prefs.notifyInfo, checkMinutes = prefs.checkMinutes, lang = prefs.lang, liveMonitor = prefs.liveMonitor))
    val s: StateFlow<UiState> = _s
    /** Notification tap se aayi screen ("alerts"); Root consume karke null kar deta hai. */
    val pendingRoute: StateFlow<String?> = app.pendingRoute
    fun consumeRoute() { app.pendingRoute.value = null }
    private var autoJob: Job? = null

    init {
        if (prefs.hasLogin) { refresh(); startAuto() }
        // app wapas aaye (peeche se) to turant taaza data: auto refresh peeche ruka rehta hai
        viewModelScope.launch { app.foreground.drop(1).filter { it }.collect { if (_s.value.loggedIn) refresh() } }
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
        AlertsSync.cancel(app)
        MonitorService.stop(app)
        api.logout(); prefs.clearLogin()
        _s.value = UiState(loggedIn = false, range = prefs.range, roasTarget = prefs.roasTarget,
            notifyUrgent = prefs.notifyUrgent, notifyWarn = prefs.notifyWarn, notifyInfo = prefs.notifyInfo, checkMinutes = prefs.checkMinutes, lang = prefs.lang, liveMonitor = prefs.liveMonitor)
    }

    fun setRange(r: String, from: String? = null, to: String? = null) {
        prefs.range = r
        _s.update { it.copy(range = r, from = from, to = to, appData = emptyMap(), appCp = emptyMap(), appExtra = emptyMap()) }
        refresh()
    }

    fun setRoasTarget(v: Double) { prefs.roasTarget = v; _s.update { it.copy(roasTarget = v) } }

    /** Har tab ka app filter (All apps / ek app). Ek app = data.php?app=<pkg> (loadApp). */
    fun setAppFilter(pkg: String?) {
        _s.update { it.copy(appFilter = pkg) }
        if (pkg != null) loadApp(pkg, force = true)
    }

    fun setLang(code: String) {
        prefs.lang = code
        Lang.code = code
        _s.update { it.copy(lang = code) }
        refreshAlerts()   // server ko nayi bhasha batao (AI explanations isi me aayengi)
    }

    fun refresh() {
        val st = _s.value
        if (st.loading) return
        viewModelScope.launch {
            _s.update { it.copy(loading = true) }
            try {
                val d = api.data("all", st.range, st.from, st.to)
                _s.update { it.copy(data = d, loading = false, error = null, lastOk = System.currentTimeMillis()) }
                runCatching { api.crossPromo("all", st.range, st.from, st.to) }.onSuccess { cp -> _s.update { it.copy(cp = cp) } }
                runCatching { api.extra("all", st.range, st.from, st.to) }.onSuccess { ex -> _s.update { it.copy(extra = ex ?: it.extra, extraMissing = ex == null) } }
                refreshAlerts()
            } catch (e: Exception) {
                val unauthorized = (e as? ApiException)?.unauthorized == true
                // unauthorized = dobara login bhi fail (password badla?) -> login screen
                if (unauthorized) { prefs.clearLogin(); api.logout(); AlertsSync.cancel(app); MonitorService.stop(app) }
                _s.update { it.copy(loading = false, error = e.message ?: "No data received".t(), loggedIn = !unauthorized) }
            }
        }
    }

    /** Alerts lao + naye ki notification (AlertsSync). alerts.php na ho to alertsMissing. */
    fun refreshAlerts() {
        viewModelScope.launch {
            runCatching { AlertsSync.check(app) }
                .onSuccess { al -> _s.update { it.copy(alerts = al ?: it.alerts, alertsMissing = al == null, alertsError = null) } }
                .onFailure { e -> _s.update { it.copy(alertsError = e.message ?: "Server not reachable".t()) } }
        }
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
        if (!force && st.appData.containsKey(pkg)) return
        if (st.appLoading != null) return
        viewModelScope.launch {
            _s.update { it.copy(appLoading = pkg) }
            try {
                val d = api.data(pkg, st.range, st.from, st.to)
                val cp = runCatching { api.crossPromo(pkg, st.range, st.from, st.to) }.getOrNull()
                val ex = runCatching { api.extra(pkg, st.range, st.from, st.to) }.getOrNull()
                _s.update { it.copy(appData = it.appData + (pkg to d), appCp = if (cp != null) it.appCp + (pkg to cp) else it.appCp,
                    appExtra = if (ex != null) it.appExtra + (pkg to ex) else it.appExtra, appLoading = null) }
            } catch (e: Exception) {
                _s.update { it.copy(appLoading = null, error = e.message) }
            }
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
        viewModelScope.launch { runCatching { api.extra("all", st.range, st.from, st.to) }.onSuccess { ex -> if (ex != null) _s.update { it.copy(extra = ex) } } }
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
                if (st.data == null || st.data.today) refresh()
                else if (tick % 3 == 0) refreshAlerts()   // purana period dekh rahe ho to bhi har minute alerts
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
