package com.myfuturecode.mfcdash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfuturecode.mfcdash.data.Api
import com.myfuturecode.mfcdash.data.ApiException
import com.myfuturecode.mfcdash.data.CpData
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.ExtraData
import com.myfuturecode.mfcdash.data.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    val appExtra: Map<String, ExtraData> = emptyMap(),
    val appData: Map<String, DashData> = emptyMap(),   // App detail: pkg → us app ka data
    val appCp: Map<String, CpData> = emptyMap(),
    val appLoading: String? = null,
    val lastOk: Long = 0L,
    val roasTarget: Double = 1.5,
)

class DashVm(private val api: Api, private val prefs: Prefs) : ViewModel() {
    private val _s = MutableStateFlow(UiState(loggedIn = prefs.hasLogin, range = prefs.range, roasTarget = prefs.roasTarget))
    val s: StateFlow<UiState> = _s
    private var autoJob: Job? = null

    init { if (prefs.hasLogin) { refresh(); startAuto() } }

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
                refresh(); startAuto()
            } catch (e: Exception) {
                _s.update { it.copy(loading = false) }
                onDone(e.message ?: "Login nahi hua")
            }
        }
    }

    fun logout() {
        autoJob?.cancel()
        api.logout(); prefs.clearLogin()
        _s.value = UiState(loggedIn = false, range = prefs.range, roasTarget = prefs.roasTarget)
    }

    fun setRange(r: String, from: String? = null, to: String? = null) {
        prefs.range = r
        _s.update { it.copy(range = r, from = from, to = to, appData = emptyMap(), appCp = emptyMap(), appExtra = emptyMap()) }
        refresh()
    }

    fun setRoasTarget(v: Double) { prefs.roasTarget = v; _s.update { it.copy(roasTarget = v) } }

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
            } catch (e: Exception) {
                val unauthorized = (e as? ApiException)?.unauthorized == true
                // unauthorized = dobara login bhi fail (password badla?) -> login screen
                if (unauthorized) { prefs.clearLogin(); api.logout() }
                _s.update { it.copy(loading = false, error = e.message ?: "Data nahi aaya", loggedIn = !unauthorized) }
            }
        }
    }

    /** App detail screen: us app ka data.php (app=<pkg>) alag se. */
    fun loadApp(pkg: String, force: Boolean = false) {
        val st = _s.value
        if (!force && st.appData.containsKey(pkg)) return
        if (st.appLoading == pkg) return
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

    /** Site jaisa: aaj ka data ho to har 20 sec refresh. */
    private fun startAuto() {
        autoJob?.cancel()
        autoJob = viewModelScope.launch {
            while (true) {
                delay(20_000)
                val st = _s.value
                if (st.loggedIn && (st.data == null || st.data.today)) refresh()
            }
        }
    }

    companion object {
        fun factory(api: Api, prefs: Prefs) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DashVm(api, prefs) as T
        }
    }
}
