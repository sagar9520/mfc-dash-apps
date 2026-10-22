package com.myfuturecode.mfcdash.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Upar wali patli loading line (browser jaisi) ka hisaab. Page badalne ya refresh par [start] ek "load" kholta hai;
 * us dauran (ya usse [LOOKBACK_MS] pehle) shuru hui har API request ([track]) ginti me aati hai. Sab poori ho jaayein
 * ([QUIET_MS] tak koi nayi na aaye) aur kam se kam [GRACE_MS] beet jaaye (screen ki pehli request shuru hone ka time)
 * to load khatam. Load se pehle se chal rahi requests line nahi chalatin.
 *
 * Dikh rahi screen ka apne aap wala sync (DashVm ka 20 s refresh, Home tick, Gads / Health / Studio poll) [startAuto]
 * se line kholta hai, par [AUTO_GAP_MS] (15 s) me ek hi baar: 5 sec wala job poll har baar nahi tim-timaata. User ka
 * kaam (page, refresh, pull-down, filter) hamesha [start]. Background ka kaam (widget sync, 24x7 monitor, WorkManager)
 * kabhi nahi kholta.
 *
 * Sab kuch main thread se padha jaata hai ([start], [busy]); [track] kisi bhi thread se.
 */
object PageLoad {
    private const val GRACE_MS = 600L
    /** Isse lamba load (Play publish jaisa slow call) ho to line chhup jaaye. */
    private const val MAX_MS = 45_000L
    /**
     * Nav event kabhi screen ki pehli request ke thoda baad aata hai (app khulte hi DashVm ki pehli refresh pehle chal
     * padti hai, Root ki pehli frame baad me): itna pehle shuru hui request bhi isi load ki.
     */
    private const val LOOKBACK_MS = 1_000L
    /**
     * Load ki aakhri request khatam hone ke baad itna aur: screen kai baar uske jawab par agli request bhejti hai
     * (data aaya -> recomposition -> loadApp / detail). Beech ke 1-2 frame me line poori na ho jaaye.
     */
    private const val QUIET_MS = 200L
    /** Apne aap wala sync isse jaldi dobara line nahi kholta (pichhli line, user wali bhi, kab khuli thi). */
    private const val AUTO_GAP_MS = 15_000L

    private val ids = AtomicLong(0)
    /** Abhi chal rahi har request: id -> shuru hone ka time (ms). */
    private val inFlight = ConcurrentHashMap<Long, Long>()
    @Volatile private var startedAt = 0L
    @Volatile private var open = false
    /** Is load me gini gayi aakhri request kab khatam hui (ms). */
    @Volatile private var lastEnd = 0L
    /** Aakhri line kab khuli ([start] / [startAuto]); null = abhi tak kabhi nahi. */
    @Volatile private var lastOpen: Long? = null
    /**
     * Abhi wala load [startAuto] ka? Tab sirf usi sync ki request ginti me (shuru ke [GRACE_MS] me shuru hui): dheeme net
     * par 4-5 s wala poll pichhli request khatam hone se pehle agli bhej de to bhi line 45 s tak lagataar na chale.
     */
    @Volatile private var auto = false
    /** Abhi dikh rahi screen ka route (Root har naye page par likhta hai): VM ka apna poll sirf apni screen par line khole. */
    @Volatile var route: String? = null
    private val _seq = MutableStateFlow(0)
    /** Har [start] par badhta hai: line shuru se chalti hai. */
    val seq: StateFlow<Int> = _seq.asStateFlow()

    /** Monotonic ms (SystemClock jaisa, par JVM par bhi chalta hai). */
    private fun now(): Long = System.nanoTime() / 1_000_000L

    fun start() = begin(false)

    private fun begin(isAuto: Boolean) {
        val t = now()
        auto = isAuto
        startedAt = t
        lastEnd = t - QUIET_MS   // nanoTime ka shuru kuch bhi ho sakta hai (manfi bhi): 0 nahi, "abhi tak koi nahi"
        open = true
        lastOpen = t
        _seq.value = _seq.value + 1
    }

    /**
     * Dikh rahi screen ka apne aap wala sync (poll / 20 s refresh / Home tick), request se theek pehle. Load pehle se
     * chal raha ho to kuch nahi (request usi me gini jaati hai); pichhli line [AUTO_GAP_MS] se kam pehle khuli ho to bhi
     * nahi. Main thread se.
     */
    fun startAuto() {
        if (busy()) return
        val last = lastOpen
        if (last != null && now() - last < AUTO_GAP_MS) return
        begin(true)
    }

    /** Request (shuru [t] par) is load ki hai? User wala: [LOOKBACK_MS] pehle se. Auto: sirf sync ki apni (shuru ke [GRACE_MS] me). */
    private fun counts(t: Long, t0: Long): Boolean = if (auto) t >= t0 && t <= t0 + GRACE_MS else t >= t0 - LOOKBACK_MS

    /** Load abhi chal raha hai? (Khatam ho gaya ho to yahin band bhi kar deta hai, taaki baad ka poll line wapas na laaye.) */
    fun busy(): Boolean {
        if (!open) return false
        val t0 = startedAt
        val t = now()
        val e = t - t0
        if (e < GRACE_MS) return true
        if (e < MAX_MS) {
            if (inFlight.values.any { counts(it, t0) }) return true
            if (t - lastEnd < QUIET_MS) return true
        }
        if (startedAt == t0) open = false   // beech me naya start() aaya ho to use band mat karo
        return false
    }

    /** Api ki GET / POST isme lipti hai. Retry wali dobara call bhi alag ginti (shuru + khatam dono, to barabar). */
    suspend fun <T> track(block: suspend () -> T): T {
        val id = ids.incrementAndGet()
        val t = now()
        inFlight[id] = t
        try { return block() } finally {
            inFlight.remove(id)
            if (open && counts(t, startedAt)) lastEnd = now()   // isi load ki thi: QUIET_MS ka hisaab
        }
    }
}
