package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.PayApp
import com.myfuturecode.mfcdash.data.PayData
import com.myfuturecode.mfcdash.data.PayItem
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.cacheSameDay
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KLazyScreen
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLive
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSkelBlock
import com.myfuturecode.mfcdash.ui.kit.KSkelHero
import com.myfuturecode.mfcdash.ui.kit.KSkelRows
import com.myfuturecode.mfcdash.ui.kit.KSkelSection
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KSortButton
import com.myfuturecode.mfcdash.ui.kit.KSortPill
import com.myfuturecode.mfcdash.ui.kit.KSortState
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KT
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kCardItems
import com.myfuturecode.mfcdash.ui.kit.kItem
import com.myfuturecode.mfcdash.ui.kit.kSorted
import com.myfuturecode.mfcdash.ui.kit.rememberKSort
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.fmtDayRange
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------- data: api/payments.php (DashVm ke saath, apna poll nahi)

/**
 * Ek (app, period) ka payments.php haal. Load kab: screen khuli, period badla, aur jab DashVm ka refresh poora hua (st.lastOk,
 * jaise 20 s wala aaj ka refresh) — par sirf jab copy purana ho ([PAY_AGAIN_MS] / kai din = [PAY_AGAIN_LONG_MS]); apna timer nahi.
 * [reload] (Try again / refresh icon / pull-down) = turant, umar nahi dekhte.
 */
@Stable
internal class PayState {
    var data by mutableStateOf<PayData?>(null)
    /** Network jawab kab aaya (ms). 0 = disk wala copy (Live nahi). */
    var at by mutableLongStateOf(0L)
    /** Server par api/payments.php nahi (404). */
    var missing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var tick by mutableIntStateOf(0)
    internal var gen = 0
    /** Aakhri poora hua load kis [tick] par chala: tick isse alag = user ne maanga. */
    internal var doneTick = 0
    fun reload() { tick++ }
}

/** DashVm ke refresh par itna purana copy ho tabhi dobara: ek din = lagbhag har refresh (20 s), list <-> detail par nahi. */
private const val PAY_AGAIN_MS = 15_000L
/** 7 / 30 din: server par bhaari (poore period ke events), aur purane din badalte nahi: minute me ek baar. */
private const val PAY_AGAIN_LONG_MS = 60_000L
/** Network se pehle itna ruko (DashVm ka SWITCH_WAIT_MS jaisa): period jaldi-jaldi badle to beech wale bheje hi nahi. */
private const val PAY_WAIT_MS = 350L

/** Process bhar ki yaad (server + user + app + period -> jawab, kab): list -> app -> wapas = turant, phir taaza. Sirf main thread. */
private object PayMem {
    val m = HashMap<String, Pair<PayData, Long>>()
}

/** Disk / yaad wala copy kaam ka? (DashVm jaisa) custom = haan; aaj / kal / 7 / 30 din = sirf aaj ka bana ho. */
private fun payUsable(st: UiState, at: Long): Boolean =
    (st.range == "custom" && !st.from.isNullOrBlank() && !st.to.isNullOrBlank()) || cacheSameDay(at)

/** app = "all" ya package. Pehle yaad / disk wala copy, phir network. */
@Composable
internal fun rememberPay(st: UiState, app: String): PayState {
    val a = LocalContext.current.applicationContext as App
    val api = a.api
    val key = a.prefs.baseUrl + "\n" + a.prefs.username + "|" + app + "|" + st.range + "|" + st.from + "|" + st.to
    val s = remember(key) {
        PayState().apply { PayMem.m[key]?.takeIf { payUsable(st, it.second) }?.let { (d, at0) -> data = d; at = at0 } }
    }
    LaunchedEffect(key, st.lastOk, s.tick, st.loggedIn) {
        if (!st.loggedIn) return@LaunchedEffect
        val tick = s.tick
        val asked = tick != s.doneTick
        val oneDay = st.range == "today" || st.range == "yesterday" || (st.range == "custom" && (st.from.isNullOrBlank() || st.from == st.to))
        // DashVm ka refresh / screen khuli: is period ka network copy abhi ka hai (period badal kar wapas, detail -> list) = dobara nahi
        if (!asked && s.at > 0 && System.currentTimeMillis() - s.at < (if (oneDay) PAY_AGAIN_MS else PAY_AGAIN_LONG_MS)) {
            s.busy = false
            return@LaunchedEffect
        }
        val g = ++s.gen
        s.busy = true
        try {
            if (s.data == null) api.paymentsCached(app, st.range, st.from, st.to)?.takeIf { payUsable(st, it.at) }?.let { c -> if (s.data == null) s.data = c.value }
            // Api ki chal rahi request cancel se nahi rukti (server par chalti rehti): tab hop / lastOk ke jhatke me bheji hi na jaaye
            if (!asked) delay(PAY_WAIT_MS)
            val p = api.payments(app, st.range, st.from, st.to)
            if (p == null) s.missing = true
            else {
                val now = System.currentTimeMillis()
                s.data = p; s.at = now; s.missing = false
                PayMem.m[key] = p to now
            }
            s.error = null
            s.doneTick = tick
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            s.error = e.message ?: "No data received".t()
            s.doneTick = tick
        } finally {
            if (g == s.gen) s.busy = false
        }
    }
    return s
}

// ---------------------------------------------------------------- list: route "payments"

/**
 * Payments (mockup promo_pay frame 4): period tabs, hara hero = period ke ₹ (Paid | Failed), phir "Apps N": har app ka ₹,
 * "N paid · N failed" (failed laal). Sort section ke daayein (Amount / Paid / Failed / Name). Tap = "payments/app/<pkg>".
 */
@Composable
fun PaymentsScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    val pay = rememberPay(st, "all")
    val sort = rememberKSort("amount")
    val sortOpts = listOf("amount" to "Amount".t(), "paid" to "Paid".t(), "failed" to "Failed".t(), "name" to "Name".t())
    // TabScreen jaisa dhaancha, par data.php (st.data) ka intezaar nahi: yahan sab payments.php se, naam ke liye st.data ho to.
    // Refresh icon / pull-down = payments.php dobara (data.php ka refresh payments nahi laata jab woh fail ho raha ho).
    KScreen(top = {
        KDetailBar("Payments".t(), { nav.popBackStack() }, actions = {
            DashRangeIcon(vm, st)
            KBarIcon(KIcons.Refresh, "Refresh".t(), { pay.reload() }, spinning = pay.busy)
        })
        DashPeriodTabs(vm, st, Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp))
    }) {
        val p = pay.data
        if (p == null) {
            if (pay.missing || (pay.error != null && !pay.busy)) PayProblem(pay.missing, pay.error) { pay.reload() } else PaySkel()
            return@KScreen
        }
        val rows = p.apps.map { it to payAppName(st.data, it) }
        val shown = when (sort.key) {
            "paid" -> rows.kSorted(sort.desc) { it.first.paid }
            "failed" -> rows.kSorted(sort.desc) { it.first.failed }
            "name" -> rows.kSorted(sort.desc) { it.second.lowercase() }
            else -> rows.kSorted(sort.desc) { it.first.amount }
        }
        val left: @Composable ColumnScope.() -> Unit = { PayHero(p, pay.at) }
        val right: @Composable ColumnScope.() -> Unit = {
            PayListHead("Apps".t(), rows.size, if (rows.size > 1) sort else null, sortOpts)
            if (shown.isEmpty()) KCard { KEmpty("No payments".t(), KIcons.Card) }
            else KCard(list = true) {
                shown.forEachIndexed { i, (a, name) ->
                    if (i > 0) KListDivider(true)
                    PayLinkRow(
                        name, paySub(null, a.paid, a.failed, a.started), lead = { KAvatar(name) }, value = inr(a.amount),
                        onClick = { nav.navigate("payments/app/" + a.pkg) },
                    )
                }
            }
        }
        if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
    }
}

// ---------------------------------------------------------------- app detail: route "payments/app/{pkg}"

/**
 * Ek app ke payments (frame 5): bar = avatar + naam, "Payments · Today"; hero ₹ (Paid | Failed); tabs All | Paid | Failed;
 * har payment: time (kai din ho to tareekh bhi), plan (+ wajah laal, sirf jab ho), ₹ (failed = kata hua), Paid / Failed chip.
 */
@Composable
fun PaymentsAppScreen(vm: DashVm, st: UiState, nav: NavHostController, pkg: String) {
    val pay = rememberPay(st, pkg)
    var tab by rememberSaveable { mutableIntStateOf(0) }   // 0 sab | 1 paid | 2 failed
    var limit by rememberSaveable(pkg, st.range, st.from, st.to) { mutableIntStateOf(PAY_PAGE) }
    val p = pay.data
    val name = payAppTitle(st.data, p, pkg)
    val wide = isWide()
    val bar: @Composable ColumnScope.() -> Unit = {
        KDetailBar(
            name, { nav.popBackStack() }, sub = "Payments".t() + " · " + payPeriod(st, p),
            lead = { KAvatar(name, size = 36.dp) },
            actions = {
                DashRangeIcon(vm, st)
                KBarIcon(KIcons.Refresh, "Refresh".t(), { pay.reload() }, spinning = pay.busy)
            },
        )
    }
    if (p == null) {
        KScreen(top = bar) {
            if (pay.missing || (pay.error != null && !pay.busy)) PayProblem(pay.missing, pay.error) { pay.reload() } else PayAppSkel(wide)
        }
        return
    }
    // mockup jaisa All = Paid + Failed (hero bhi yahi ginta): shuru-par-adhoore (started) list me nahi, paise jaise na dikhein
    val items = p.items.filter { it.paid || it.failed }
    val nPaid = items.count { it.paid }
    val nFailed = items.size - nPaid
    val tabs = listOf(tf("All %s", num(items.size.toDouble())), tf("Paid %s", num(nPaid.toDouble())), tf("Failed %s", num(nFailed.toDouble())))
    val list = when (tab) {
        1 -> items.filter { it.paid }
        2 -> items.filter { it.failed }
        else -> items
    }
    val multiDay = !p.single
    // list poori nahi to tabs ke neeche ek chhoti line (hero ke number poore period ke): server naye 500 tak bhejta hai, aur
    // purane din (raw ke bahar, agg_pay) ki list hoti hi nahi
    val since = if (p.source != "raw" && p.rawFrom.isNotBlank()) tf("List from %s", fmtDay(p.rawFrom)) else null
    // poora period agg_pay se (list hai hi nahi) par payments hue: "No payments" jhooth, wahi line khaali card me
    val noList = items.isEmpty() && since != null && (p.paid + p.failed > 0 || p.amount >= 0.5)
    val note = if (noList) null else listOfNotNull(if (p.items.size >= PAY_ITEMS_MAX) tf("Newest %s", num(PAY_ITEMS_MAX.toDouble())) else null, since)
        .joinToString(" · ").ifBlank { null }
    val empty = since?.takeIf { noList } ?: "No payments".t()
    val segTabs: @Composable () -> Unit = {
        Column {
            KSegTabs(tabs, tab, { tab = it }, fit = true)
            note?.let { DashText(it, 13.sp, Mfc.c.ink3, Modifier.padding(start = 4.dp, top = 8.dp)) }
        }
    }
    if (wide) {
        KScreen(top = bar) {
            KTwoPane(
                left = { PayHero(p, pay.at) },
                right = {
                    segTabs()
                    if (list.isEmpty()) KCard { KEmpty(empty, KIcons.Card) }
                    else KCard(list = true) {
                        list.take(limit).forEachIndexed { i, it ->
                            if (i > 0) KListDivider()
                            PayItemRow(it, multiDay)
                        }
                    }
                    if (list.size > limit) KButton("Show more".t(), { limit += PAY_PAGE }, Modifier.fillMaxWidth(), kind = KBtn.SOFT)
                },
            )
        }
    } else {
        KLazyScreen(top = bar) {
            kItem(key = "pay.hero") { PayHero(p, pay.at) }
            kItem(key = "pay.tabs") { segTabs() }
            if (list.isEmpty()) kItem(key = "pay.empty") { KCard { KEmpty(empty, KIcons.Card) } }
            else kCardItems(list.withIndex().toList(), key = { "pay.i" + it.index + "." + tab }) { (_, it) -> PayItemRow(it, multiDay) }
        }
    }
}

/** Fold par ek baar me itni rows (Column; phone par lazy list). */
private const val PAY_PAGE = 150

/** payments.php ek app ke itne hi items bhejta hai (naye pehle): itne aaye = list kati hui. */
private const val PAY_ITEMS_MAX = 500

// ---------------------------------------------------------------- pieces

/** Hara hero: "Payments" + Live · 2 min pehle (aaj ka taaza) ya period, bada ₹, Paid | Failed (hara / laal). */
@Composable
private fun PayHero(p: PayData, at: Long) {
    val c = Mfc.c
    KHero(KTint.Green) {
        KHeroHead("Payments".t(), trailing = {
            if (p.today && at > 0) KLive("Live".t() + " · " + agoText(at))
            else DashText(fmtDayRange(p.from, p.to), 13.sp, c.ink2)
        })
        KBigNum(inr(p.amount), if (p.amount >= 0.5) c.green else Color.Unspecified, size = 48.sp)
        KSplit(
            listOf("Paid".t() to num(p.paid.toDouble()), "Failed".t() to num(p.failed.toDouble())),
            colors = listOf(if (p.paid > 0) c.green else null, if (p.failed > 0) c.red else null),
        )
    }
}

/** Section heading "Apps" + chhoti ginti (ink3), daayein Sort (badla ho to pehle pill). sort = null: sirf heading. (Cross promo jaisa) */
@Composable
private fun PayListHead(title: String, count: Int, sort: KSortState?, opts: List<Pair<String, String>>) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(start = 4.dp, end = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KT(title, 18.sp, c.ink, weight = FontWeight.Bold, lh = 24.sp, ls = (-0.01).em)
        KT(num(count.toDouble()), 15.sp, c.ink3, Modifier.padding(start = 6.dp).weight(1f), weight = FontWeight.SemiBold, lh = 24.sp)
        if (sort != null) {
            KSortPill(sort, opts)
            KSortButton(sort, opts)
        }
    }
}

/**
 * "₹3,462 · 38 paid · 6 failed": failed laal (0 ho to nahi). lead = aage ka hissa (₹), null = sirf ginti.
 * Na paid na failed, sirf shuru hue = "2 started".
 */
@Composable
internal fun paySub(lead: String?, paid: Int, failed: Int, started: Int = 0): AnnotatedString {
    val c = Mfc.c
    return buildAnnotatedString {
        if (lead != null) append(lead + " · ")
        if (paid == 0 && failed == 0 && started > 0) append(num(started.toDouble()) + " " + "started".t())
        else append(num(paid.toDouble()) + " " + "paid".t())
        if (failed > 0) {
            append(" · ")
            withStyle(SpanStyle(color = c.red, fontWeight = FontWeight.SemiBold)) { append(num(failed.toDouble()) + " " + "failed".t()) }
        }
    }
}

/** KRow jaisi list row (lead + title + rangeen sub + bold value + chevron), sub me alag rang ke hisse (Paisa "Payments" row bhi). */
@Composable
internal fun PayLinkRow(title: String, sub: AnnotatedString, lead: @Composable () -> Unit, value: String? = null, onClick: () -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        lead()
        Column(Modifier.weight(1f)) {
            KT(title, 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
            Text(
                sub, Modifier.padding(top = 2.dp), color = c.ink2,
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (value != null) KT(value, 16.sp, c.ink, weight = FontWeight.Bold, lh = 20.sp)
        Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.offset(x = 6.dp).size(22.dp))
    }
}

/** Ek payment: time (| tareekh) · plan (+ wajah) · ₹ (failed = kata hua) + chip. */
@Composable
private fun PayItemRow(it: PayItem, multiDay: Boolean) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 71.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // time ka khaana sp me (bada font = chauda): "12:00 am" kabhi "12:00 …" nahi
        Column(Modifier.width(with(LocalDensity.current) { 80.sp.toDp() })) {
            KT(payTime(it.ts), 14.sp, c.ink2, weight = FontWeight.SemiBold, lh = 20.sp)
            if (multiDay) KT(fmtDay(it.day), 12.sp, c.ink3, lh = 16.sp)
        }
        Column(Modifier.weight(1f)) {
            KT(payPlanName(it.plan), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
            if (it.reason.isNotBlank()) KT(it.reason.t(), 13.sp, c.red, Modifier.padding(top = 2.dp), weight = FontWeight.SemiBold, lh = 18.sp)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val known = it.amount >= 0.5
            if (it.failed && known) Text(
                inr(it.amount), color = c.ink3, maxLines = 1,
                style = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.LineThrough, fontFeatureSettings = "tnum"),
            )
            else KT(if (known) inr(it.amount) else "–", 17.sp, if (known) c.ink else c.ink3, weight = FontWeight.Bold, lh = 22.sp)
            when {
                it.paid -> KPill("Paid".t(), KKind.OK)
                it.failed -> KPill("Failed".t(), KKind.BAD)
                else -> KPill("Started".t(), KKind.NEUTRAL)
            }
        }
    }
}

/** payments.php nahi (404): naya server file upload karo; warna galti + Dobara try. */
@Composable
private fun PayProblem(missing: Boolean, error: String?, onRetry: () -> Unit) {
    val c = Mfc.c
    KCard {
        if (missing) {
            KT("Payments is not on the server yet".t(), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
            KT("Upload server/api/payments.php into mfc_dash/api/".t(), 13.sp, c.ink2, Modifier.padding(top = 4.dp), lh = 18.sp, maxLines = 3)
        } else KT(error ?: "No data received".t(), 14.sp, c.red, lh = 20.sp, maxLines = 4)
        KButton("Try again".t(), onRetry, Modifier.fillMaxWidth().padding(top = 12.dp), kind = KBtn.SOFT, icon = KIcons.Refresh)
    }
}

/** List ka skeleton: hero + "Apps" + rows. Fold: hero baayein, list daayein. */
@Composable
private fun PaySkel() {
    KSkeleton {
        val left: @Composable ColumnScope.() -> Unit = { KSkelHero(KTint.Green) }
        val right: @Composable ColumnScope.() -> Unit = {
            KSkelSection(width = 90.dp)
            KSkelRows(6)
        }
        if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
    }
}

/** App detail ka skeleton: hero + tabs + payment rows. */
@Composable
private fun PayAppSkel(wide: Boolean) {
    KSkeleton {
        val left: @Composable ColumnScope.() -> Unit = { KSkelHero(KTint.Green) }
        val right: @Composable ColumnScope.() -> Unit = {
            KSkelBlock(48.dp, RoundedCornerShape(24.dp))
            KSkelRows(7, lead = false)
        }
        if (wide) KTwoPane(left = left, right = right) else { left(); right() }
    }
}

// ---------------------------------------------------------------- helpers

/** App ka naam: data.php wala (app list), warna server ka (apps() config), warna package ka aakhri hissa. */
private fun payAppName(d: DashData?, a: PayApp): String =
    d?.apps?.firstOrNull { it.pkg == a.pkg }?.name ?: a.name.takeIf { it.isNotBlank() && it != a.pkg }
        ?: d?.appName(a.pkg) ?: a.pkg.substringAfterLast('.')

private fun payAppTitle(d: DashData?, p: PayData?, pkg: String): String =
    d?.apps?.firstOrNull { it.pkg == pkg }?.name
        ?: p?.apps?.firstOrNull { it.pkg == pkg }?.name?.takeIf { it.isNotBlank() && it != pkg }
        ?: d?.appName(pkg) ?: pkg.substringAfterLast('.')

/** Subtitle ka period: Today / Yesterday / 7 days / 30 days, custom = tareekh. */
private fun payPeriod(st: UiState, p: PayData?): String = when (st.range) {
    "today" -> "Today".t()
    "yesterday" -> "Yesterday".t()
    "7d" -> "7 days".t()
    "30d" -> "30 days".t()
    else -> if (p != null) fmtDayRange(p.from, p.to) else "Custom".t()
}

private val payTsIn = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
private val payTimeOut = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

/** Server ka local "2026-10-23 21:32:00" -> "9:32 pm" (wahi ghadi ka waqt, phone ka zone nahi lagta). */
internal fun payTime(ts: String): String =
    runCatching { payTimeOut.format(payTsIn.parse(ts.take(19))!!).lowercase(Locale.US) }.getOrElse { ts.drop(11).take(5) }

/** Plan / product id ko padhne layak: "no_ads" -> "No ads", "com.app.premium_monthly" -> "Premium monthly". */
internal fun payPlanName(plan: String): String {
    val t = plan.trim()
    if (t.contains(' ')) return t   // pehle se padhne layak naam ("Monthly Pro")
    val p = t.substringAfterLast('/').substringAfterLast('.').replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ").trim()
    return if (p.isEmpty()) "–" else p.replaceFirstChar { it.uppercase() }
}
