package com.myfuturecode.mfcdash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.data.*
import com.myfuturecode.mfcdash.util.t
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** AI App Health ka state. Har view alag load hota hai; screen khud poll karti hai (Live: 5 sec). */
data class HState(
    val overview: HOverview? = null,
    val consoles: HConsolesData? = null,
    val runs: HRunsData? = null,
    val issues: HIssuesData? = null, val issuesKey: String = "",
    val fixes: List<HFix>? = null, val fixesKey: String = "",
    val builds: List<HBuild>? = null,
    val logs: List<HEvent>? = null, val logsKey: String = "",
    val settings: HSettings? = null,
    val issue: Map<Long, HIssueDetail> = emptyMap(),
    val app: Map<String, HAppDetail> = emptyMap(),          // "pkg|days"
    val run: Map<Long, Pair<HRun, List<HEvent>>> = emptyMap(),
    val busy: Set<String> = emptySet(),                      // chal rahe actions (button par spinner)
    val error: String? = null,
    val missing: Boolean = false,                            // server par api/health.php nahi (404)
    val lastOk: Long = 0L,
    val cached: Set<String> = emptySet(),                    // "overview" / "consoles": abhi disk cache wala copy (live nahi)
    // Export for Claude sheet: apps + ginti (cxKey = kis filter ka), apni galti (screen ka error nahi); cxOld = server purana (view nahi);
    // cxErrKey = kis filter ki ginti fail hui (sheet ka spinner wahin ruke, "Try again" dikhe)
    val cx: HCxApps? = null, val cxKey: String = "", val cxErr: String? = null, val cxOld: Boolean = false, val cxErrKey: String = "",
)

class HealthVm(private val app: App) : ViewModel() {
    private val api = app.api
    private val _s = MutableStateFlow(HState())
    val s: StateFlow<HState> = _s
    private val _msg = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Snackbar ke chhote messages ("Scan shuru", errors). */
    val msg: SharedFlow<String> = _msg

    private fun say(m: String) { _msg.tryEmit(m) }
    /** Screen se chhota snackbar message (jaise "Copied"). */
    fun tell(m: String) = say(m)

    private fun load(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _s.update { it.copy(error = null, missing = false, lastOk = System.currentTimeMillis()) }
            } catch (e: Exception) {
                val m = e.message ?: "error"
                _s.update { it.copy(error = m, missing = m.contains("404")) }
            }
        }
    }

    /**
     * Screen par abhi kuch nahi (app khuli): disk wala pichhla jawab turant. [HState.cached] me naam, lastOk = us copy
     * ka waqt ("Updated 3 h ago", abhi ka nahi). Network wala aate hi badal jaata hai; network fail = copy dikhta rahe.
     */
    private fun <T> showCached(name: String, has: (HState) -> Boolean, read: suspend () -> Cached<T>?, put: (HState, T) -> HState) {
        if (has(_s.value)) return
        viewModelScope.launch {
            val c = runCatching { read() }.getOrNull() ?: return@launch
            _s.update { if (has(it)) it else put(it, c.value).copy(cached = it.cached + name, lastOk = if (it.lastOk == 0L) c.at else it.lastOk) }
        }
    }

    fun loadOverview() {
        showCached("overview", { it.overview != null }, { api.hOverviewCached() }) { st, v -> st.copy(overview = v) }
        load { val o = api.hOverview(); _s.update { it.copy(overview = o, cached = it.cached - "overview") } }
    }
    fun loadConsoles() {
        showCached("consoles", { it.consoles != null }, { api.hConsolesCached() }) { st, v -> st.copy(consoles = v) }
        load { val c = api.hConsoles(); _s.update { it.copy(consoles = c, cached = it.cached - "consoles") } }
    }
    fun loadRuns() = load { val r = api.hRuns(mapOf("limit" to "60")); _s.update { it.copy(runs = r) } }
    fun loadIssues(f: Map<String, String>) {
        val key = f.toSortedMap().toString()
        pendingIssues = key
        // filter jaldi-jaldi badle to sirf aakhri wala jawab dikhao
        load { val r = api.hIssues(f); if (key == pendingIssues) _s.update { it.copy(issues = r, issuesKey = key) } }
    }
    private var pendingIssues = ""
    fun loadFixes(result: String) {
        pendingFixes = result
        // filter jaldi badla: purane filter ka (der se aaya) jawab naye ko na dhak de
        load { val r = api.hFixes(mapOf("result" to result)); if (result == pendingFixes) _s.update { it.copy(fixes = r, fixesKey = result) } }
    }
    private var pendingFixes = ""
    fun loadBuilds() = load { val r = api.hBuilds(); _s.update { it.copy(builds = r) } }
    fun loadLogs(level: String, q: String) {
        val key = "$level|$q"
        pendingLogs = key
        load { val r = api.hLogs(mapOf("level" to level, "q" to q)); if (key == pendingLogs) _s.update { it.copy(logs = r, logsKey = key) } }
    }
    private var pendingLogs = ""
    fun loadSettings() = load { val r = api.hSettings(); _s.update { it.copy(settings = r) } }
    fun loadIssue(id: Long) = load { val r = api.hIssue(id); _s.update { it.copy(issue = it.issue + (id to r)) } }
    fun loadApp(pkg: String, days: Int) = load { val r = api.hApp(pkg, days); _s.update { it.copy(app = it.app + ("$pkg|$days" to r)) } }
    fun loadRun(id: Long) = load { val r = api.hRun(id); _s.update { it.copy(run = it.run + (id to r)) } }

    /** POST action. Busy key = button par spinner; then = baad me kya reload karna hai. */
    fun act(action: String, fields: Map<String, String> = emptyMap(), busyKey: String = action, ok: String? = null,
            then: (JSONObject) -> Unit = {}) {
        if (busyKey in _s.value.busy) return
        _s.update { it.copy(busy = it.busy + busyKey) }
        viewModelScope.launch {
            try {
                val j = api.hAction(action, fields)
                j.optString("warning").takeIf { it.isNotBlank() }?.let { say(it) } ?: ok?.let { say(it) }
                then(j)
            } catch (e: Exception) {
                say(e.message ?: "Failed".t())
            }
            _s.update { it.copy(busy = it.busy - busyKey) }
        }
    }

    fun scan(pkg: String = "", type: String = "manual_full") =
        act("scan", mapOf("pkg" to pkg, "type" to type), busyKey = "scan$pkg", ok = "Scan started".t()) { loadRuns(); loadOverview(); if (pkg.isNotBlank()) loadAppAll(pkg) }

    private fun loadAppAll(pkg: String) { _s.value.app.keys.filter { it.startsWith("$pkg|") }.forEach { loadApp(pkg, it.substringAfter('|').toInt()) } }

    fun issueAction(id: Long, action: String, extra: Map<String, String> = emptyMap(), ok: String) =
        act(action, mapOf("id" to id.toString()) + extra, busyKey = "$action$id", ok = ok) { loadIssue(id); loadOverview() }

    fun appSetting(pkg: String, field: String, on: Boolean) {
        // turant UI me dikhao, phir server
        _s.update { st -> st.copy(settings = st.settings?.let { se -> se.copy(apps = se.apps.map { if (it.pkg == pkg) it.flip(field, on) else it }) },
            app = st.app.mapValues { (_, d) -> if (d.app.pkg == pkg) d.copy(app = d.app.flip(field, on)) else d }) }
        act("app_setting", mapOf("pkg" to pkg, "field" to field, "value" to if (on) "1" else "0"), busyKey = "set$pkg$field") { loadSettings() }
    }

    fun mailSetting(key: String, on: Boolean) {
        _s.update { st -> st.copy(settings = st.settings?.let { se -> se.copy(mail = se.mail.map { if (it.key == key) it.copy(on = on) else it }) }) }
        act("setting", mapOf("key" to key, "value" to if (on) "1" else "0"), busyKey = "mail$key") { loadSettings() }
    }

    fun task(pkg: String, title: String, details: String, onDone: (Long?) -> Unit) =
        act("task", mapOf("pkg" to pkg, "title" to title, "details" to details), busyKey = "task", ok = "Task saved: AI is working on it".t()) { j ->
            loadOverview(); loadRuns()
            onDone(j.optLong("issue_id").takeIf { it > 0 })
        }

    /** AAB/APK ka 15 min wala signed link. */
    fun link(buildId: Long, kind: String, onUrl: (String) -> Unit) =
        act("link", mapOf("id" to buildId.toString(), "kind" to kind), busyKey = "link$kind$buildId") { j -> j.optString("url").takeIf { it.isNotBlank() }?.let(onUrl) }

    fun buildStatus(buildId: Long, status: String) =
        act("build_status", mapOf("id" to buildId.toString(), "status" to status), busyKey = "bs$buildId", ok = "Saved".t()) { loadBuilds(); reloadIssues() }

    /** Play par release: sirf Sagar ke confirm dialog se. */
    fun publish(buildId: Long, track: String, rollout: Double) =
        act("publish", mapOf("id" to buildId.toString(), "track" to track, "rollout" to rollout.toString(), "confirm" to "1"), busyKey = "pub$buildId") { j ->
            say("Sent to Play: ".t() + j.optString("track") + (j.optString("note").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
            loadBuilds(); reloadIssues(); loadOverview()
        }

    fun verify(pkg: String) = act("verify", mapOf("pkg" to pkg), busyKey = "verify$pkg", ok = "Verification started (GitHub Actions)".t()) { loadSettings() }
    fun testEmail() = act("test_email", busyKey = "test_email", ok = "Test email sent".t())

    fun export(f: Map<String, String>, onUrl: (String) -> Unit) {
        if ("export" in _s.value.busy) return
        _s.update { it.copy(busy = it.busy + "export") }
        viewModelScope.launch {
            try { api.hExport(f).takeIf { it.isNotBlank() }?.let(onUrl) } catch (e: Exception) { say(e.message ?: "Failed".t()) }
            _s.update { it.copy(busy = it.busy - "export") }
        }
    }

    // ---- Export for Claude
    /** Sheet ki app list + ginti (filter badle to dobara; jaldi badle to sirf aakhri jawab). */
    fun loadClaudeApps(f: Map<String, String>) {
        val key = f.toSortedMap().toString()
        pendingCx = key
        _s.update { it.copy(cxErr = null, cxOld = false, cxErrKey = "") }
        viewModelScope.launch {
            try {
                val r = api.hClaudeApps(f)
                if (key == pendingCx) _s.update { it.copy(cx = r, cxKey = key) }
            } catch (e: Exception) {
                if (key == pendingCx) cxFail(e, key)
            }
        }
    }
    private var pendingCx = ""

    /** .md file banwao (Copy / Share). Busy = "claude" (button par spinner). */
    fun claudeExport(f: Map<String, String>, onDone: (HCxExport) -> Unit) {
        if ("claude" in _s.value.busy) return
        _s.update { it.copy(busy = it.busy + "claude", cxErr = null, cxOld = false) }
        viewModelScope.launch {
            try { onDone(api.hClaudeExport(f)) } catch (e: Exception) { cxFail(e) }
            _s.update { it.copy(busy = it.busy - "claude") }
        }
    }

    // purana server: view nahi = 400 ("unknown view"), health.php hi nahi = 404, sirf api/health.php naya (lib purani) = 500
    // "Call to undefined function h_cx_params()" -> "naya server upload karo" (PHP ki galti nahi dikhani)
    private fun cxFail(e: Exception, key: String = "") {
        val m = e.message ?: "Failed".t()
        val old = (e as? ApiException)?.let { !it.network && !it.unauthorized } == true &&
            (m.contains("400") || m.contains("404") || m.contains("unknown view") || m.contains("undefined function", ignoreCase = true))
        _s.update { it.copy(cxErr = if (old) null else m, cxOld = old, cxErrKey = key) }
    }

    private fun reloadIssues() { _s.value.issue.keys.forEach { loadIssue(it) } }

    companion object {
        fun factory(app: App) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HealthVm(app) as T
        }
    }
}

private fun HApp.flip(field: String, on: Boolean) = when (field) {
    "monitoring" -> copy(monitoring = on)
    "autofix" -> copy(autofix = on, autofixEffective = on && repoVerified && signingVerified)
    "email" -> copy(email = on)
    else -> this
}
