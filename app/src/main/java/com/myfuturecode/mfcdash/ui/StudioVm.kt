package com.myfuturecode.mfcdash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.l
import com.myfuturecode.mfcdash.util.mapObj
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tsMs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Run abhi chal raha / line me (view=live ke runs). */
internal val LIVE_ON = setOf("QUEUED", "RUNNING", "CANCELLING")

internal fun liveOn(status: String) = status.uppercase() in LIVE_ON

/**
 * view=live ka haal. runs = chalu + pichhle 15 min me khatam (naye pehle). offset = server - phone (ms), taaki
 * heartbeat / "2 min pehle khatam" phone ki galat ghadi se na bigde. fin = job -> kitne runs khatam hue (chalte dekhe, ya do poll ke
 * beech shuru + khatam; pages isse apna data dobara laate hain). runs = [] jab aakhri sahi jawab purana ho (StudioVm.liveStale). ok = kam se kam ek baar sahi jawab aaya (purana server = false, pages purana indicator dikhate).
 * cron = server cron aakhri baar kab chala (server ki ghadi, ms; null = kabhi nahi).
 */
data class SLive(
    val runs: List<JSONObject> = emptyList(),
    val offset: Long = 0L,
    val ok: Boolean = false,
    val at: Long = 0L,
    val fin: Map<String, Int> = emptyMap(),
    val cron: Long? = null,
) {
    fun run(id: Long): JSONObject? = runs.firstOrNull { it.l("id") == id }
    fun finOf(vararg jobs: String): Int = jobs.sumOf { fin[it] ?: 0 }
    val active: Boolean get() = runs.any { liveOn(it.s("status")) }
    /** Server ki ghadi ka "abhi" (ms). */
    fun now(): Long = System.currentTimeMillis() + offset
    /** Cron (har 5 min) 12 min se nahi chala: line wala run kabhi shuru nahi hoga (card "Server cron is not running"). */
    fun cronDead(): Boolean = ok && (cron == null || now() - cron > CRON_DEAD_MS)

    companion object {
        const val CRON_DEAD_MS = 12 * 60_000L
    }
}

/** AI App Research Studio: har view ka aakhri jawab (GState hi reuse). Actions = approvals / settings; app kabhi khud publish nahi karta. */
class StudioVm(app: App) : ViewModel() {
    private val api = app.api
    private val _s = MutableStateFlow(GState())
    val s: StateFlow<GState> = _s
    private val _msg = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val msg: SharedFlow<String> = _msg

    private fun say(m: String) { _msg.tryEmit(m) }

    fun load(view: String, p: Map<String, String> = emptyMap()) {
        val k = gkey(view, p)
        val q = p.filterValues { it.isNotBlank() }
        // pehli baar (app khuli): disk wala pichhla jawab turant (abhi sirf overview rakha jaata hai), phir network
        if (k !in _s.value.views) viewModelScope.launch {
            val c = runCatching { api.sGetCached(view, q) }.getOrNull() ?: return@launch
            _s.update { it.withCached(k, c) }
        }
        viewModelScope.launch {
            try {
                val j = api.sGet(view, q)
                _s.update { it.copy(views = it.views + (k to j), cached = it.cached - k, error = null, missing = false, lastOk = System.currentTimeMillis()) }
            } catch (e: Exception) {
                val m = e.message ?: "error"
                _s.update { it.copy(error = m, missing = m.contains("404")) }
            }
        }
    }

    /** POST action. Busy key = button par spinner; then = baad me kya reload karna hai. */
    fun act(action: String, fields: Map<String, String> = emptyMap(), busyKey: String = action, ok: String? = null, then: (JSONObject) -> Unit = {}) {
        if (busyKey in _s.value.busy) return
        _s.update { it.copy(busy = it.busy + busyKey) }
        viewModelScope.launch {
            try {
                val j = api.sAction(action, fields)
                ok?.let { say(it) }
                then(j)
                // run queue hua / cancel hua ho sakta hai: live card turant. Run: naya run card me aane tak button band (do tap = do run nahi)
                if (action == "run") livePollNow() else liveNow()
            } catch (e: Exception) {
                say(e.message ?: "Failed".t())
            }
            _s.update { it.copy(busy = it.busy - busyKey) }
        }
    }

    // ---------------------------------------------------------------- live: chal rahe runs (view=live)
    // Ek hi poller: har Studio screen STARTED par liveStart / STOPPED par liveStop (ginti). App peeche = sab screens
    // STOPPED = poller band. Koi run chalu = har 4 s, warna 30 s (scheduled run pakadne ke liye). Requests kabhi ek ke upar ek nahi.

    private val _live = MutableStateFlow(SLive())
    val live: StateFlow<SLive> = _live
    private var liveUsers = 0
    private var liveJob: Job? = null
    private val liveWake = Channel<Unit>(Channel.CONFLATED)
    private var liveLast = 0L
    /** Pichhle jawab ke chalu runs (id -> job): agle jawab me khatam / gayab = fin event. */
    private var liveBusy: Map<Long, String> = emptyMap()
    /** Sahi jawabon me dekha sabse naya waqt (server_time / finished_at, server ki ghadi; sirf aage badhta). null = VM ka pehla poll baaki. */
    private var liveMark: Long? = null
    /** Kitne poll shuru hue / khatam hue (sahi ya galat): livePollNow agle poll ka intezaar isi se karta hai. */
    private var livePolls = 0
    private val liveDone = MutableStateFlow(0)

    fun liveStart() {
        liveUsers++
        if (liveJob?.isActive != true) {
            liveStale()   // der baad lauti (app peeche thi): purane runs page ke taaze jawab ko na dhakein
            liveJob = viewModelScope.launch { liveLoop() }
        }
    }

    fun liveStop() {
        liveUsers = (liveUsers - 1).coerceAtLeast(0)
        if (liveUsers == 0) {
            liveJob?.cancel()
            liveJob = null
        }
    }

    /** Abhi ek baar dekho (Run again / action ke baad). Poller band ho to agli shuruaat par. */
    fun liveNow() { liveWake.trySend(Unit) }

    /** liveNow + us poll ke khatam hone tak ruko (max LIVE_WAIT). Request poller hi karta hai (ek ke upar ek nahi); poller band = turant wapas. */
    suspend fun livePollNow() {
        if (liveJob?.isActive != true) return
        val want = livePolls + 1   // abhi chal raha poll action se pehle shuru hua ho sakta hai: uske baad wala
        liveNow()
        withTimeoutOrNull(LIVE_WAIT) { liveDone.first { it >= want } }
    }

    /** Aakhri sahi jawab bahut purana (app peeche thi / net nahi): runs hatao, pages apna data dikhayein (purane server jaisa). liveBusy rehta hai: lautne par fin. */
    private fun liveStale() {
        _live.update { if (it.runs.isNotEmpty() && System.currentTimeMillis() - it.at > LIVE_STALE) it.copy(runs = emptyList()) else it }
    }

    private suspend fun liveLoop() {
        // screen badli (ek band, doosri khuli): abhi-abhi dekha tha to turant dobara nahi
        val gap = System.currentTimeMillis() - liveLast
        if (gap in 0 until LIVE_FAST) withTimeoutOrNull(LIVE_FAST - gap) { liveWake.receive() }
        while (true) {
            val next = livePoll()
            withTimeoutOrNull(next) { liveWake.receive() }
        }
    }

    /** Ek poll; jawab = agla poll kitni der baad (ms). */
    private suspend fun livePoll(): Long {
        liveLast = System.currentTimeMillis()
        livePolls++
        val wasBusy = liveBusy.isNotEmpty()
        return try {
            val j = api.sGet("live")
            val runs = j.arr("runs").mapObj { it }
            val st = tsMs(j.sn("server_time"))
            val off = if (st != null) st - System.currentTimeMillis() else 0L
            val cron = tsMs(j.sn("cron_last"))
            val byId = runs.associateBy { it.l("id") }
            // pichhli baar chalu the, ab khatam (ya list se nikal gaye, jaise app der tak peeche rahi)
            val ended = liveBusy.filter { (id, _) -> byId[id]?.let { !liveOn(it.s("status")) } ?: true }.toMutableMap()
            // chalte kabhi nahi dekha (do poll ke beech shuru + khatam, jaise cron ka chhota economics): pichhle jawab ke baad khatam = bhi fin.
            // VM ka pehla poll nahi (app khulte hi reload nahi); mark sirf aage badhta hai, to har run ek hi baar
            val mark = liveMark
            if (mark != null) runs.forEach { r ->
                val id = r.l("id")
                if (id !in liveBusy && !liveOn(r.s("status")) && (tsMs(r.sn("finished_at")) ?: 0L) > mark) ended[id] = r.s("job")
            }
            if (st != null) liveMark = maxOf(mark ?: st, st, runs.maxOfOrNull { tsMs(it.sn("finished_at")) ?: st } ?: st)
            liveBusy = runs.filter { liveOn(it.s("status")) }.associate { it.l("id") to it.s("job") }
            _live.update { cur ->
                val fin = if (ended.isEmpty()) cur.fin else cur.fin.toMutableMap().also { m -> ended.values.forEach { job -> m[job] = (m[job] ?: 0) + 1 } }
                cur.copy(runs = runs, offset = off, ok = true, at = System.currentTimeMillis(), fin = fin, cron = cron)
            }
            // sirf line wale runs aur cron ruka: kuch badlega nahi, tez poll bekaar
            val stuck = liveBusy.isNotEmpty() && runs.none { it.s("status").uppercase() in setOf("RUNNING", "CANCELLING") } && _live.value.cronDead()
            if (liveBusy.isNotEmpty() && !stuck) LIVE_FAST else LIVE_SLOW
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            liveStale()   // net nahi / server ki galti: purana haal page ke taaze data par na chadhe
            // purana server (view=live nahi = 400): bahut kam poll; net ki galti: thodi der baad
            if ((e.message ?: "").contains("400")) LIVE_OLD else if (wasBusy) LIVE_ERR else LIVE_SLOW
        } finally {
            liveDone.value = livePolls
        }
    }

    companion object {
        private const val LIVE_FAST = 4_000L
        private const val LIVE_SLOW = 30_000L
        private const val LIVE_ERR = 15_000L
        private const val LIVE_OLD = 300_000L
        /** Live haal itna purana = stale (runs hatao). */
        private const val LIVE_STALE = 2 * LIVE_SLOW
        /** livePollNow (Run again ka spinner) zyada se zyada itna ruke: poll atka / fail to button phir bhi khul jaaye. */
        private const val LIVE_WAIT = 10_000L

        fun factory(app: App) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StudioVm(app) as T
        }
    }
}
