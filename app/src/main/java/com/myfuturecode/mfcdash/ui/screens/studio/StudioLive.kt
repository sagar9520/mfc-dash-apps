package com.myfuturecode.mfcdash.ui.screens.studio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.SLive
import com.myfuturecode.mfcdash.ui.StudioVm
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSectionLink
import com.myfuturecode.mfcdash.ui.liveOn
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.l
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs
import com.myfuturecode.mfcdash.util.usd
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.floor

// ---------------------------------------------------------------- Live runs (mockup ui_live): chalta job har page par, step-step

/** Khatam (DONE) card itni der dikhta hai; ruka / adhoora jab tak server bhejta hai (15 min). */
private const val LIVE_DONE_MS = 120_000L

/** Heartbeat isse purana (RUNNING) = "No update for N min" (amber). */
private const val LIVE_SLOW_MS = 180_000L

/** Server ka purana (non-streaming) Claude call: ek request 10 min tak block, beech me heartbeat nahi ho sakta. */
private const val LIVE_SLOW_BLOCK_MS = 660_000L

/** Ye jobs khatam hone par notification bhejte hain (server st_notify); baaki par note me "notification" ka vaada nahi. */
private val LIVE_NOTIFY = setOf("rank", "evaluate_idea", "more_research", "competitors", "revise_features")

/** "Run again" sirf in jobs par (server ka action=run). */
private val LIVE_AGAIN = setOf("economics", "portfolio", "discover", "rank", "deep", "source_check")

/** Screen dikh rahi ho (STARTED) tab tak VM ka ek hi live poller chalu (ginti VM me). Har Studio screen ek baar bulaati hai. */
@Composable
internal fun SLiveUse(vm: StudioVm) {
    val life = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(life, vm) {
        life.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.liveStart()
            try { awaitCancellation() } finally { vm.liveStop() }
        }
    }
}

/** Live haal (poller SLiveUse chalata hai). */
@Composable
internal fun sLive(vm: StudioVm): SLive {
    val l by vm.live.collectAsStateWithLifecycle()
    return l
}

/** Card ka haal. */
internal enum class LState { QUEUED, RUNNING, SLOW, STOPPING, DONE, PARTIAL, STOPPED }

internal fun lState(r: JSONObject, now: Long): LState = when (r.s("status").uppercase()) {
    "QUEUED" -> LState.QUEUED
    "CANCELLING" -> LState.STOPPING
    "RUNNING" -> {
        val beat = tsMs(r.sn("beat_at"))
        // "waiting for Claude" = server ka blocking call (10 min tak): itni der chup rehna normal hai
        val lim = if (r.s("detail") == "waiting for Claude") LIVE_SLOW_BLOCK_MS else LIVE_SLOW_MS
        if (beat != null && now - beat > lim) LState.SLOW else LState.RUNNING
    }
    "DONE" -> LState.DONE
    "PARTIAL" -> LState.PARTIAL
    else -> LState.STOPPED   // FAILED / BLOCKED / CANCELLED
}

private fun LState.active() = this == LState.QUEUED || this == LState.RUNNING || this == LState.SLOW || this == LState.STOPPING

/** 0-100: server ka pct; purana server = steps se. DONE = 100, warna 99 tak. */
internal fun lPct(r: JSONObject): Int {
    if (r.s("status").uppercase() == "DONE") return 100
    r.dn("pct")?.let { return it.toInt().coerceIn(0, 99) }
    val tot = r.d("steps_total")
    if (tot <= 0) return 0
    return floor(100 * (r.d("steps_done") + r.d("frac").coerceIn(0.0, 1.0)) / tot).toInt().coerceIn(0, 99)
}

/** Patti kitni bhari (0..1): step ke andar ka frac bhi, taaki 4 s wale poll par bhi aage badhti dikhe. */
internal fun lFrac(r: JSONObject): Float {
    if (r.s("status").uppercase() == "DONE") return 1f
    val tot = r.d("steps_total")
    if (tot <= 0 || !r.has("frac")) return lPct(r) / 100f
    return ((r.d("steps_done") + r.d("frac").coerceIn(0.0, 1.0)) / tot).toFloat().coerceIn(0f, .99f)
}

/** Khatam hue kitni der (server ki ghadi se); finished_at nahi = bahut purana. */
private fun lAgo(r: JSONObject, now: Long): Long = tsMs(r.sn("finished_at"))?.let { now - it } ?: Long.MAX_VALUE

/** Page par card dikhe: chalu = haan; DONE = 2 min tak; ruka / adhoora = jab tak server bhejta hai. */
private fun lShow(r: JSONObject, now: Long): Boolean = when {
    liveOn(r.s("status")) -> true
    r.s("status").uppercase() == "DONE" -> lAgo(r, now) < LIVE_DONE_MS
    else -> true
}

/** Har job (+ ref) ka sirf sabse naya run (naya run purane laal card ki jagah le). */
private fun List<JSONObject>.newestPerJob(): List<JSONObject> {
    val seen = HashSet<String>()
    return filter { seen.add(it.s("job") + "#" + it.s("ref_id")) }
}

/** Is page ke card: jobs (aur ref = idea / project id) ke runs jo abhi dikhne chahiye. */
internal fun SLive.forPage(jobs: Set<String>, ref: Long? = null): List<JSONObject> {
    val now = now()
    return runs.filter { it.s("job") in jobs && (ref == null || it.dn("ref_id")?.toLong() == ref) }.newestPerJob().filter { lShow(it, now) }
}

/** Overview "Running now": chalu + 2 min me khatam; chal rahe pehle, phir line wale, phir khatam. */
internal fun SLive.runningNow(): List<JSONObject> {
    val now = now()
    return runs.newestPerJob().filter { liveOn(it.s("status")) || lAgo(it, now) < LIVE_DONE_MS }.sortedBy {
        when (it.s("status").uppercase()) {
            "RUNNING", "CANCELLING" -> 0
            "QUEUED" -> 1
            else -> 2
        }
    }
}

/** Chalu runs ki ginti (bar ki sub line: "2 jobs running"). */
internal fun SLive.activeCount(): Int = runs.newestPerJob().count { liveOn(it.s("status")) }

/** Run detail / runs list: page wale jawab par live ke taaze fields. */
internal fun sMerge(base: JSONObject, live: JSONObject?): JSONObject {
    if (live == null) return base
    val o = JSONObject(base.toString())
    for (k in live.keys()) o.put(k, live.opt(k))
    return o
}

// ---------------------------------------------------------------- shabd

/** Card title ke liye chhota naam ("Ranking ready", "Ranking stopped"). */
internal fun jobShort(j: String): String = when (j) {
    "rank" -> "Ranking".t()
    "deep" -> "Deep research".t()
    "discover" -> "Discovery".t()
    "portfolio" -> "Apps analysis".t()
    "economics" -> "Economics".t()
    "source_check" -> "Source test".t()
    "competitors" -> "Competitor analysis".t()
    "revise_features" -> "Spec update".t()
    "evaluate_idea" -> "Idea check".t()
    "more_research" -> "More research".t()
    else -> jobName(j)
}

internal fun liveIcon(j: String): ImageVector = when (j) {
    "rank" -> KIcons.Search   // mockup: ranking = khoj
    "deep" -> KIcons.Spark
    "discover" -> KIcons.Globe
    "economics" -> KIcons.Rupee
    "portfolio" -> KIcons.Wallet
    "source_check" -> KIcons.Fix
    "competitors" -> KIcons.Target
    "revise_features" -> KIcons.Edit
    "evaluate_idea", "more_research" -> KIcons.Bulb
    else -> KIcons.Play
}

/** "6 min" / "45 sec" / "1 h 5 min". */
internal fun lDur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> tf("%s sec", s)
        s < 3600 -> tf("%s min", s / 60)
        else -> tf("%s h %s min", s / 3600, (s % 3600) / 60)
    }
}

/** Kitni der chala: chalu = abhi tak, khatam = shuru se khatam tak. Shuru hi nahi hua = null. */
private fun lElapsed(r: JSONObject, now: Long): String? {
    val a = tsMs(r.sn("started_at")) ?: return null
    val b = tsMs(r.sn("finished_at")) ?: if (liveOn(r.s("status"))) now else return null
    return lDur(b - a)
}

/** Step list me state wala step: index (1 se) + label. last = aakhri (fail: run wahin ruki; pehle wale fail steps ke baad bhi chalti rahi thi). */
private fun lStepAt(r: JSONObject, state: String, last: Boolean = false): Pair<Int, String>? {
    val steps = r.arr("steps").objs()
    val i = if (last) steps.indexOfLast { it.s("state") == state } else steps.indexOfFirst { it.s("state") == state }
    return if (i >= 0) (i + 1) to steps[i].s("label") else null
}

/** Server ka stage (andar ka log text); "done" / "starting" jaise shabd card par nahi. */
private fun lStage(r: JSONObject): String = r.s("stage").trim().takeUnless { it.lowercase() in setOf("done", "starting", "interrupted", "cancelled", "retry queued") } ?: ""

/** "Step N of M · label": steps hon to chalu ('on') step se (list se mel khata, chhota label), warna steps_done + stage (purana server / purani run). */
private fun lLine(r: JSONObject, fmt: String, at: Pair<Int, String>?): String {
    val n = r.arr("steps").length()
    val tot = if (n > 0) n else r.d("steps_total").toInt()
    val label = at?.second?.ifBlank { null } ?: lStage(r)
    if (tot <= 0) return label
    val cur = (at?.first ?: (r.d("steps_done").toInt() + 1)).coerceIn(1, tot)
    return listOf(tf(fmt, cur, tot), label).filter { it.isNotBlank() }.joinToString(" · ")
}

/** Chalu ('on') step ka label; steps nahi = null. */
internal fun lOnLabel(r: JSONObject): String? = lStepAt(r, "on")?.second?.ifBlank { null }

/** "Step 5 of 6 · AI ranking" (chalu run). */
internal fun lStepLine(r: JSONObject): String = lLine(r, "Step %s of %s", lStepAt(r, "on"))

/** "At step 5 of 6 · AI ranking" (ruka hua run): aakhri fail step. */
private fun lStopLine(r: JSONObject): String = lLine(r, "At step %s of %s", lStepAt(r, "fail", last = true))

/**
 * Rukne ki wajah: error_text me galtiyan ' | ' se judi (purani pehle, jis par run ruki wo aakhir me) = aakhri tukda, pehli line.
 * Cancel = aapne roka (pehle ki chhoti galti wajah nahi).
 */
private fun lReason(r: JSONObject): String? {
    if (r.s("status").uppercase() == "CANCELLED") return "You cancelled it".t()
    return r.sn("error_text")?.split(" | ")?.map { it.trim() }?.lastOrNull { it.isNotBlank() }
        ?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotBlank() }
}

/** Khatam run ka nateeja ("11 ideas"): server ka detail, warna aakhri step note (khoj: "3 findings"). */
private fun lResult(r: JSONObject): String? =
    r.s("detail").trim().ifBlank { null }
        ?: r.arr("steps").objs().lastOrNull { it.s("state") != "wait" && it.s("note").isNotBlank() }?.s("note")?.trim()

/** Research bar: "11 new ideas" (khatam ranking ka nateeja) ya null. */
internal fun lNewIdeas(r: JSONObject): String? {
    val n = lResult(r)?.let { Regex("^(\\d+) ideas?$").find(it) }?.groupValues?.get(1) ?: return null
    return if (n == "1") "1 new idea".t() else tf("%s new ideas", n)
}

/**
 * "Starts after the ranking" (koi run chal raha) / "Starts within 5 minutes"; kuch nahi chal raha aur server ka cron ruka (cron_last purana)
 * = "Server cron is not running" (warna ye run kabhi shuru nahi hoga).
 */
private fun lWaitLine(r: JSONObject, cronDead: Boolean): String {
    r.sn("waiting_for")?.let { return tf("Starts after the %s", jobShort(it).lowercase()) }
    return if (cronDead) "Server cron is not running".t() else "Starts within 5 minutes".t()
}

/** Amber (koi heartbeat nahi): server 30 min baad atki run pehli baar dobara chalata hai, doosri baar band karta hai (st_work). */
private fun lSlowNote(r: JSONObject): String =
    if (r.d("attempts") < 2) "Still working. If it is stuck, the server restarts it by itself.".t()
    else "Still working. If it is stuck, the server stops it by itself.".t()

/** Run again ke fields: job (+ source_check ka source). Source pata na ho = null (button nahi). */
private fun lAgain(r: JSONObject): Map<String, String>? {
    val job = r.s("job")
    if (job !in LIVE_AGAIN) return null
    if (job == "source_check") return r.sn("source")?.let { mapOf("job" to job, "source" to it) }
    return mapOf("job" to job)
}

@Composable
private fun lColor(st: LState): Color {
    val c = Mfc.c
    return when (st) {
        LState.RUNNING -> c.brand
        LState.DONE -> c.green
        LState.STOPPED -> c.red
        LState.QUEUED -> c.ink3
        else -> c.amber
    }
}

@Composable
private fun lBar(st: LState): Color {
    val c = Mfc.c
    return when (st) {
        LState.RUNNING -> c.brand
        LState.DONE -> c.zGreen
        LState.STOPPED -> c.zRed
        LState.QUEUED -> c.zGrey
        else -> c.zAmber
    }
}

/** Server ki ghadi ka abhi; chalu run par har second (elapsed time chalta dikhe). */
@Composable
private fun rememberLiveNow(offset: Long, tick: Boolean): Long {
    val t by produceState(System.currentTimeMillis(), tick) {
        while (tick) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    return t + offset
}

// ---------------------------------------------------------------- patti

/**
 * Progress patti (kit .prog). Naya % aaye to dheere se aage (animate); chalu run par chamakti lehar.
 * wait = line me (beech me chhota grey tukda, mockup .prog.wait).
 */
@Composable
internal fun SLiveBar(
    frac: Float, color: Color, modifier: Modifier = Modifier, shimmer: Boolean = false, wait: Boolean = false, h: Dp = 8.dp, top: Dp = 12.dp,
) {
    val c = Mfc.c
    val shape = RoundedCornerShape(h / 2)
    val f by animateFloatAsState(if (frac.isNaN()) 0f else frac.coerceIn(0f, 1f), tween(900), label = "lbar")
    Box(modifier.fillMaxWidth().padding(top = top).height(h).clip(shape).background(c.zTrack)) {
        if (wait) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                Spacer(Modifier.weight(.30f))
                Box(Modifier.weight(.28f).fillMaxHeight().clip(shape).alpha(.55f).background(c.zGrey))
                Spacer(Modifier.weight(.42f))
            }
        } else if (f > 0f) {
            val shine = if (shimmer) rememberInfiniteTransition(label = "lshine")
                .animateFloat(-.3f, 1.3f, infiniteRepeatable(tween(1_600, easing = LinearEasing)), label = "lshineA") else null
            val glow = Color.White.copy(alpha = if (c.isDark) .32f else .45f)
            Box(
                Modifier.fillMaxWidth(f).fillMaxHeight().clip(shape).background(color).drawBehind {
                    // lehar sirf draw me padhi: har frame recomposition nahi
                    val p = shine?.value ?: return@drawBehind
                    val w = maxOf(size.width * .4f, 48.dp.toPx())
                    val x = p * size.width
                    drawRect(Brush.horizontalGradient(0f to Color.Transparent, .5f to glow, 1f to Color.Transparent, startX = x - w / 2, endX = x + w / 2))
                },
            )
        }
    }
}

/** Chalu step ka ghoomta icon (sirf draw me ghoomta). */
@Composable
private fun SSpin(color: Color) {
    val a by rememberInfiniteTransition(label = "lspin").animateFloat(0f, 360f, infiniteRepeatable(tween(1_200, easing = LinearEasing)), label = "lspinA")
    Icon(KIcons.Refresh, null, tint = color, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = a })
}

// ---------------------------------------------------------------- step list

/** Steps: ho gaya = hara check, chalu = ghoomta (bold), baaki = ghadi, fail = laal x. Daayein chhota note ("9 sources"). */
@Composable
internal fun SLiveSteps(steps: List<JSONObject>, slow: Boolean = false) {
    val c = Mfc.c
    steps.forEach { s ->
        val state = s.s("state")
        Row(
            Modifier.fillMaxWidth().heightIn(min = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (state) {
                "done" -> Icon(KIcons.Check, null, tint = c.green, modifier = Modifier.size(18.dp))
                "on" -> if (slow) Icon(KIcons.Clock, null, tint = c.amber, modifier = Modifier.size(18.dp)) else SSpin(c.brand)
                "fail" -> Icon(KIcons.Close, null, tint = c.red, modifier = Modifier.size(18.dp))
                else -> Icon(KIcons.Clock, null, tint = c.ink3, modifier = Modifier.size(18.dp).alpha(.6f))
            }
            SText(
                s.s("label"), 14.sp,
                when (state) {
                    "done" -> c.ink3
                    "on" -> c.ink
                    "fail" -> c.red
                    else -> c.ink2
                },
                Modifier.weight(1f), weight = if (state == "on" || state == "fail") FontWeight.SemiBold else FontWeight.Normal, lh = 19.sp,
            )
            s.sn("note")?.let { SText(it, 13.sp, c.ink3, weight = FontWeight.Medium, lh = 18.sp) }
        }
    }
}

// ---------------------------------------------------------------- bada card (Research, Discovery, Your apps, idea, project, Sources)

/** Footer ki baayein line: shuru ka number bold ("**4 of ~10** ideas written"). */
@Composable
private fun LDetail(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    val c = Mfc.c
    val m = Regex("^[~≈]?\\d[\\d,.]*(\\s+of\\s+[~≈]?\\d[\\d,.]*)?").find(text)
    Text(
        buildAnnotatedString {
            if (m != null && m.value.isNotEmpty()) {
                withStyle(SpanStyle(color = c.ink2, fontWeight = FontWeight.SemiBold)) { append(m.value) }
                append(text.substring(m.value.length))
            } else append(text)
        },
        modifier, color = c.ink3, style = sStyle(13.sp, lh = 18.sp), maxLines = maxLines, softWrap = maxLines > 1, overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Live card (mockup ui_live 1 / 3 / 4). full = step list bhi (chalu par). onAgain null = "Run again" nahi.
 * onDetails = card tap + "Details" (run detail). offset = server - phone (ms).
 */
@Composable
internal fun SLiveCardUi(
    r: JSONObject, offset: Long, full: Boolean, busy: Boolean, onAgain: (() -> Unit)?, onDetails: (() -> Unit)?, cronDead: Boolean = false,
) {
    val c = Mfc.c
    val job = r.s("job")
    val tick = liveOn(r.s("status"))
    val now = rememberLiveNow(offset, tick)
    val st = lState(r, now)
    val pct = lPct(r)
    val cost = usd(r.d("cost_usd"), 2)
    val elapsed = lElapsed(r, now)
    val title = when (st) {
        LState.DONE -> tf("%s ready", jobShort(job))
        LState.STOPPED -> tf("%s stopped", jobShort(job))
        LState.PARTIAL -> tf("%s partly done", jobShort(job))
        else -> jobName(job)
    }
    val sub = when (st) {
        LState.QUEUED -> lWaitLine(r, cronDead)
        LState.SLOW -> tf("No update for %s min", ((now - (tsMs(r.sn("beat_at")) ?: now)) / 60_000L).coerceAtLeast(3))
        LState.STOPPING -> "Stopping…".t()
        LState.STOPPED -> lStopLine(r)
        LState.DONE -> listOfNotNull(lResult(r), elapsed, cost).joinToString(" · ")
        LState.PARTIAL -> listOfNotNull(lResult(r), elapsed).joinToString(" · ")   // kharcha neeche footer me (do baar nahi)
        LState.RUNNING -> lStepLine(r)
    }
    KCard(onClick = onDetails, padding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (st) {
                LState.QUEUED -> KIconTile(KIcons.Clock, KKind.NEUTRAL)
                LState.RUNNING -> KIconTile(liveIcon(job), KKind.ASK)
                LState.SLOW -> KIconTile(KIcons.Clock, KKind.WARN)
                LState.STOPPING -> KIconTile(liveIcon(job), KKind.WARN)
                LState.DONE -> KIconTile(KIcons.Check, KKind.OK)
                LState.PARTIAL -> KIconTile(KIcons.Dhyan, KKind.WARN)
                LState.STOPPED -> KIconTile(KIcons.Close, KKind.BAD)
            }
            Column(Modifier.weight(1f)) {
                SText(title, 16.sp, c.ink, weight = FontWeight.Bold, lh = 21.sp)
                // full: 2 line tak (lamba stage / bada font kate nahi)
                if (sub.isNotBlank()) SText(sub, 14.sp, c.ink2, Modifier.padding(top = 1.dp), lh = 19.sp, maxLines = if (full) 2 else 1)
            }
            if (st == LState.QUEUED) SText("Waiting".t(), 15.sp, c.ink3, weight = FontWeight.Bold, lh = 20.sp)
            else SText("$pct%", 22.sp, lColor(st), weight = FontWeight.ExtraBold, lh = 26.sp)
        }
        SLiveBar(lFrac(r), lBar(st), shimmer = st == LState.RUNNING, wait = st == LState.QUEUED)
        // footer: baayein kya ho raha / wajah, daayein waqt · kharcha
        val left: String?
        val right: String?
        var leftLines = 1
        when (st) {
            LState.QUEUED -> {
                left = tsMs(r.sn("queued_at"))?.let { "Queued".t() + " " + agoText(it - offset) }
                right = null
            }
            LState.SLOW -> {
                left = lSlowNote(r)
                right = listOfNotNull(elapsed, cost).joinToString(" · ")
                leftLines = 2
            }
            LState.DONE -> {
                val ago = tsMs(r.sn("finished_at"))?.let { agoText(it - offset) } ?: "just now".t()
                val ideas = job == "rank" || job == "deep"
                left = if (ideas) tf("List updated %s", ago) else tf("Updated %s", ago)
                right = if (ideas) "Pick 3-5 ideas".t() else null
            }
            LState.STOPPED, LState.PARTIAL -> {
                left = lReason(r) ?: stText(r.s("status"))
                right = cost
                leftLines = 2
            }
            else -> {
                left = r.s("detail").trim().ifBlank { null }
                right = listOfNotNull(elapsed, cost).joinToString(" · ")
            }
        }
        if (left != null || !right.isNullOrBlank()) Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (left != null) LDetail(left, Modifier.weight(1f), leftLines) else Spacer(Modifier.weight(1f))
            if (!right.isNullOrBlank()) SText(right, 13.sp, c.ink3, lh = 18.sp)
        }
        // step list: sirf chalu run par (khatam / ruka = chhota card, mockup 3 / 4)
        val steps = r.arr("steps").objs()
        if (full && st.active() && steps.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Spacer(Modifier.height(6.dp))
                SLiveSteps(steps, slow = st == LState.SLOW || st == LState.STOPPING)
            }
        }
        if (st == LState.STOPPED || st == LState.PARTIAL) {
            if (onAgain != null || onDetails != null) Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Run again ko zyada jagah (icon / spinner + shabd, chhote phone par kate nahi)
                if (onAgain != null) KButton("Run again".t(), onAgain, Modifier.weight(1.5f), icon = KIcons.Refresh, busy = busy)
                if (onDetails != null) KButton("Details".t(), onDetails, Modifier.weight(1f), kind = KBtn.SOFT)
            }
            if (r.b("can_reuse")) SText(
                "Research is saved. Run again within 24 h: no new web search cost.".t(), 13.sp, c.ink3,
                Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 10.dp), lh = 18.sp, maxLines = 3,
            )
        }
    }
}

/** Card + vm: Run again (POST run, busy key = "run_<job>", phir live turant) aur Details. */
@Composable
internal fun SLiveCard(r: JSONObject, live: SLive, g: GState, vm: StudioVm, nav: NavHostController, full: Boolean = true) {
    val fields = lAgain(r)
    val key = "run_" + r.s("job")
    val id = r.l("id")
    SLiveCardUi(
        r, live.offset, full, busy = key in g.busy,
        onAgain = fields?.let { f -> { vm.act("run", f, key, ok = "Queued. The server picks it up within 5 minutes.".t()) } },
        onDetails = { nav.navigate("studio/run/$id") },
        cronDead = live.cronDead(),
    )
}

/** Page ke live cards + chalu ho to "Updates every few seconds" note. runs = SLive.forPage(...). */
@Composable
internal fun SLiveCards(runs: List<JSONObject>, live: SLive, g: GState, vm: StudioVm, nav: NavHostController) {
    if (runs.isEmpty()) return
    runs.forEach { SLiveCard(it, live, g, vm, nav) }
    val on = runs.filter { liveOn(it.s("status")) }
    if (on.isNotEmpty()) SLiveNote(notify = on.any { it.s("job") in LIVE_NOTIFY })
}

/** notify = ye job khatam hone par notification bhejta hai (server); warna sirf "Updates every few seconds." */
@Composable
internal fun SLiveNote(notify: Boolean = true) {
    SText(
        if (notify) "Updates every few seconds. You can close the app; you get a notification when it ends.".t() else "Updates every few seconds.".t(), 13.sp, Mfc.c.ink3,
        Modifier.fillMaxWidth().padding(horizontal = 6.dp), lh = 18.sp, maxLines = 3,
    )
}

/** List khaali (mockup): bulb + bold title + ek line. */
@Composable
internal fun SLiveEmpty(title: String, body: String) {
    val c = Mfc.c
    KCard(padding = PaddingValues(horizontal = 20.dp, vertical = 28.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(KIcons.Bulb, null, tint = c.ink3, modifier = Modifier.size(28.dp))
            SText(title, 16.sp, c.ink, Modifier.padding(top = 8.dp), weight = FontWeight.Bold, lh = 21.sp, align = TextAlign.Center)
            SText(body, 14.sp, c.ink2, Modifier.fillMaxWidth().padding(top = 4.dp), lh = 19.sp, maxLines = 3, align = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------- Overview: "Running now" (mockup 2)

/** Chhoti row: tile, naam + % (line me = "Waiting"), step line, patli patti. Tap = run detail. */
@Composable
internal fun SLiveMini(r: JSONObject, offset: Long, onClick: (() -> Unit)?, cronDead: Boolean = false) {
    val c = Mfc.c
    val job = r.s("job")
    val now = System.currentTimeMillis() + offset
    val st = lState(r, now)
    val sub = when (st) {
        LState.QUEUED -> lWaitLine(r, cronDead)
        LState.SLOW -> tf("No update for %s min", ((now - (tsMs(r.sn("beat_at")) ?: now)) / 60_000L).coerceAtLeast(3))
        LState.STOPPING -> "Stopping…".t()
        LState.STOPPED, LState.PARTIAL -> lReason(r) ?: lStopLine(r)
        LState.DONE -> listOfNotNull(lResult(r), lElapsed(r, now), usd(r.d("cost_usd"), 2)).joinToString(" · ")
        LState.RUNNING -> lStepLine(r)
    }
    var m = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(
        m.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (st) {
            LState.QUEUED -> KIconTile(KIcons.Clock, KKind.NEUTRAL)
            LState.RUNNING -> KIconTile(liveIcon(job), KKind.ASK)
            LState.DONE -> KIconTile(KIcons.Check, KKind.OK)
            LState.STOPPED -> KIconTile(KIcons.Close, KKind.BAD)
            LState.PARTIAL -> KIconTile(KIcons.Dhyan, KKind.WARN)
            else -> KIconTile(KIcons.Clock, KKind.WARN)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SText(jobName(job), 15.sp, c.ink, Modifier.weight(1f), weight = FontWeight.SemiBold, lh = 20.sp)
                if (st == LState.QUEUED) SText("Waiting".t(), 13.sp, c.ink3, weight = FontWeight.SemiBold, lh = 20.sp)
                else SText("${lPct(r)}%", 15.sp, lColor(st), weight = FontWeight.ExtraBold, lh = 20.sp)
            }
            if (sub.isNotBlank()) SText(sub, 13.sp, c.ink2, Modifier.padding(top = 2.dp), lh = 18.sp)
            SLiveBar(lFrac(r), lBar(st), shimmer = st == LState.RUNNING, wait = st == LState.QUEUED, h = 6.dp, top = 6.dp)
        }
    }
}

/** Overview ke sabse upar: jo chal raha (aur 2 min me khatam). Kuch nahi = section hi nahi. */
@Composable
internal fun RunningNowSection(live: SLive, nav: NavHostController, go: (String) -> Unit) {
    val list = live.runningNow()
    if (list.isEmpty()) return
    KSection("Running now".t(), trailing = { KSectionLink("Runs".t()) { go("runs") } })
    KCard(list = true) {
        list.forEachIndexed { i, r ->
            if (i > 0) KListDivider(true)
            val id = r.l("id")
            SLiveMini(r, live.offset, { nav.navigate("studio/run/$id") }, live.cronDead())
        }
    }
}

/** Runs list / project runs ki row ke neeche patli patti (sirf chalu run). */
@Composable
internal fun SRunRowBar(r: JSONObject, offset: Long) {
    if (!liveOn(r.s("status"))) return
    val st = lState(r, System.currentTimeMillis() + offset)
    SLiveBar(
        lFrac(r), lBar(st), Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp),
        shimmer = st == LState.RUNNING, wait = st == LState.QUEUED, h = 4.dp, top = 0.dp,
    )
}

/** Runs list ki row ka % / "Waiting" aur rang (chalu run). */
@Composable
internal fun sRunRowPct(r: JSONObject, offset: Long): Pair<String, Color> {
    val st = lState(r, System.currentTimeMillis() + offset)
    return if (st == LState.QUEUED) "Waiting".t() to Mfc.c.ink3 else "${lPct(r)}%" to lColor(st)
}

// ---------------------------------------------------------------- run detail hero ka hissa

/** Hero me: "Step 5 of 6 · stage", patti, detail + chala kitni der. */
@Composable
internal fun SRunLive(r: JSONObject, offset: Long, cronDead: Boolean = false) {
    val c = Mfc.c
    val tick = liveOn(r.s("status"))
    val now = rememberLiveNow(offset, tick)
    val st = lState(r, now)
    val line = when (st) {
        LState.STOPPED, LState.PARTIAL -> lStopLine(r)
        LState.QUEUED -> lWaitLine(r, cronDead)
        LState.SLOW -> tf("No update for %s min", ((now - (tsMs(r.sn("beat_at")) ?: now)) / 60_000L).coerceAtLeast(3))
        else -> lStepLine(r)
    }
    if (line.isNotBlank()) SText(line, 14.sp, c.ink2, lh = 20.sp, maxLines = 2)
    SLiveBar(lFrac(r), lBar(st), shimmer = st == LState.RUNNING, wait = st == LState.QUEUED)
    val detail = when (st) {
        LState.SLOW -> lSlowNote(r)
        LState.DONE, LState.PARTIAL -> lResult(r) ?: ""
        else -> r.s("detail").trim()
    }
    val el = lElapsed(r, now)
    if (detail.isNotBlank() || el != null) Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (detail.isNotBlank()) LDetail(detail, Modifier.weight(1f), 2) else Spacer(Modifier.weight(1f))
        if (el != null) SText(el, 13.sp, c.ink3, lh = 18.sp)
    }
}

/** Run detail: steps ka card (jo server bheje; purane run = kuch nahi). */
@Composable
internal fun SRunSteps(r: JSONObject, offset: Long) {
    val steps = r.arr("steps").objs()
    if (steps.isEmpty()) return
    val st = lState(r, System.currentTimeMillis() + offset)
    KSection("Steps".t(), trailing = { SText("${steps.count { it.s("state") == "done" }}/${steps.size}", 14.sp, Mfc.c.ink3, weight = FontWeight.SemiBold) })
    KCard(padding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        SLiveSteps(steps, slow = st == LState.SLOW || st == LState.STOPPING)
    }
}
