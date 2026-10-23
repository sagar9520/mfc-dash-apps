package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.CpData
import com.myfuturecode.mfcdash.data.CpRow
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLive
import com.myfuturecode.mfcdash.ui.kit.KRow
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
import com.myfuturecode.mfcdash.ui.kit.kSorted
import com.myfuturecode.mfcdash.ui.kit.rememberKSort
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.fmtDayRange
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.t

/**
 * Cross promo (mockup promo_pay frame 1): period tabs, hero = promo se kitne installs (Views | Clicks), phir "Apps N":
 * har PROMOTE hua app (tgt) ek row: naam, views · clicks, daayein installs. Sort section ke daayein (default installs).
 * Tap = us app ki promo detail ("promo/app/<pkg>"). Data: api/crosspromo.php sab apps ka (DashVm st.cp, usi ke refresh ke saath).
 */
@Composable
fun CrossPromoScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    val sort = rememberKSort("installs")
    val sortOpts = listOf(
        "installs" to "Installs".t(), "views" to "Views".t(), "clicks" to "Clicks".t(), "ctr" to "CTR", "name" to "Name".t(),
    )
    val liveAt = promoLiveAt(st.cp, st.data)   // TabScreen ke bahar: data na ho tab bhi dekhta rahe
    TabScreen("Cross promo".t(), vm, st, nav, back = true, showApps = false, skeleton = { PromoSkel() }) { v ->
        // crosspromo.php ka jawab data.php ke baad aata hai: tab tak (8 sec tak) skeleton, "0 installs" wala hero nahi
        if (dashPending(v.cp == null)) { PromoSkel(); return@TabScreen }
        val d = v.d
        // 8 sec baad bhi nahi aaya (fail / bahut der): "0 installs" + "No promo shown" jhooth hota; galti + Try again
        val cp = v.cp ?: run { DashNoData(st, false) { vm.refresh() }; return@TabScreen }
        val rows = cp.rows
        val apps = remember(cp, d) { promoGroup(rows, d) { it.tgt } }
        val shown = when (sort.key) {
            "views" -> apps.kSorted(sort.desc) { it.views }
            "clicks" -> apps.kSorted(sort.desc) { it.clicks }
            "ctr" -> apps.kSorted(sort.desc) { it.ctr }
            "name" -> apps.kSorted(sort.desc) { it.name.lowercase() }
            else -> apps.kSorted(sort.desc) { it.installs }
        }
        val left: @Composable ColumnScope.() -> Unit = { PromoHero(d, cp, rows, liveAt) }
        val right: @Composable ColumnScope.() -> Unit = {
            PromoListHead("Apps".t(), apps.size, if (apps.size > 1) sort else null, sortOpts)
            if (shown.isEmpty()) KCard { KEmpty("No promo shown".t(), KIcons.Apps) }
            else KCard(list = true) {
                shown.forEachIndexed { i, a ->
                    if (i > 0) KListDivider(true)
                    KRow(
                        a.name,
                        // CTR se sort kiya ho to CTR dikhe (warna views · clicks)
                        sub = if (sort.key == "ctr") "CTR " + pct(a.ctr) + " · " + num(a.clicks) + " " + "clicks".t() else promoViewsClicks(a),
                        lead = { KAvatar(a.name) },
                        value = num(a.installs), valueSub = "installs".t(),
                        chevron = true, onClick = { nav.navigate("promo/app/" + a.pkg) },
                    )
                }
            }
        }
        if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
    }
}

/** Section heading "Apps" + chhoti ginti (ink3), daayein Sort (badla ho to pehle pill). sort = null: sirf heading. */
@Composable
private fun PromoListHead(title: String, count: Int, sort: KSortState?, opts: List<Pair<String, String>>) {
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

// ---------------------------------------------------------------- list + detail ke saajhe hisse (PromoAppScreen bhi)

/** Ek app ki promo jod: views / clicks / installs + kaunse ad types (fmts, [PROMO_FMTS] ke order me). */
internal class PromoAgg(val pkg: String, val name: String, val views: Double, val clicks: Double, val installs: Double, val fmts: List<String>) {
    val ctr: Double? get() = if (views > 0) clicks / views else null
}

/** Strip / chips ka order (mockup): App open, Inter image, Rewarded, Inter video. */
internal val PROMO_FMTS = listOf("APP_OPEN", "INTER_IMAGE", "REWARDED", "INTER_VIDEO")

/** rows ko key (src ya tgt) se jodo: installs zyada -> kam (barabar ho to views). Khaali pkg nahi. */
internal fun promoGroup(rows: List<CpRow>, d: DashData?, key: (CpRow) -> String): List<PromoAgg> =
    rows.filter { key(it).isNotBlank() }.groupBy(key).map { (p, l) ->
        PromoAgg(
            p, d?.appName(p) ?: p.substringAfterLast('.'), l.sumOf { it.views }, l.sumOf { it.clicks }, l.sumOf { it.installs },
            l.map { it.fmt.uppercase() }.filter { it.isNotBlank() }.distinct().sortedBy { f -> PROMO_FMTS.indexOf(f).let { if (it < 0) 99 else it } },
        )
    }.sortedWith(compareByDescending<PromoAgg> { it.installs }.thenByDescending { it.views })

/** "6,100 views · 478 clicks" */
internal fun promoViewsClicks(a: PromoAgg): String = num(a.views) + " " + "views".t() + " · " + num(a.clicks) + " " + "clicks".t()

/** Ad type ka icon (mockup): App open = phone, Inter image = tasveer, Rewarded = gift, Inter video = play. */
internal fun promoFmtIcon(f: String): ImageVector = when (f.uppercase()) {
    "APP_OPEN" -> KIcons.Phone
    "INTER_IMAGE" -> Icons.Outlined.Image
    "INTER_VIDEO" -> Icons.Outlined.SmartDisplay
    "REWARDED" -> Icons.Outlined.CardGiftcard
    else -> KIcons.Ads
}

/** Detail ke subtitle ka period: Today / Yesterday / 7 days / 30 days (custom = tareekh). */
internal fun promoPeriod(st: UiState, d: DashData?): String = when (st.range) {
    "today" -> "Today".t()
    "yesterday" -> "Yesterday".t()
    "7d" -> "7 days".t()
    "30d" -> "30 days".t()
    else -> d?.let { periodText(it) } ?: "Custom".t()
}

/** Hero: "Promo installs" + Live / period, bada installs, neeche Views | Clicks. liveAt = [promoLiveAt] (0 = Live nahi). */
@Composable
internal fun PromoHero(d: DashData, cp: CpData, rows: List<CpRow>, liveAt: Long) {
    KHero(KTint.Sand) {
        KHeroHead("Promo installs".t(), trailing = { PromoNote(d, cp, liveAt) })
        KBigNum(num(rows.sumOf { it.installs }), size = 48.sp)
        KSplit(listOf("Views".t() to num(rows.sumOf { it.views }), "Clicks".t() to num(rows.sumOf { it.clicks })))
    }
}

/**
 * Hero ke daayein: cp network se aaya (liveAt) aur aaj ke period ka = Live · 2 min pehle (cp ki umar, data.php ki nahi).
 * Warna cp ka apna period (disk / fail ke baad bacha purana copy Live nahi; raat 12 ke baad kal wala cp = kal ki tareekh).
 */
@Composable
private fun PromoNote(d: DashData, cp: CpData, liveAt: Long) {
    val same = cp.from.isBlank() || (cp.from == d.from && cp.to == d.to)
    if (liveAt > 0 && d.today && same) KLive("Live".t() + " · " + agoText(liveAt))
    else DashText(if (cp.from.isNotBlank()) fmtDayRange(cp.from, cp.to) else periodText(d), 13.sp, Mfc.c.ink2)
}

/**
 * cp (crosspromo.php) data.php ke baad alag aata hai; fail ho to DashVm purana / disk wala copy rakhta hai, aur UiState me
 * cp ka apna waqt nahi. Isliye yahan: cp ka jo copy screen ke saamne network wale d ke BAAD naya aaya, woh network ka
 * (disk wala copy sirf disk wale d ke saath aata hai) — uska waqt. Aakhri kuch copy yaad (=== se): list -> detail par bhi Live.
 */
private object PromoNet {
    private val seen = ArrayDeque<Pair<CpData, Long>>()
    fun at(cp: CpData): Long = seen.firstOrNull { it.first === cp }?.second ?: 0L
    fun mark(cp: CpData) {
        if (at(cp) > 0L) return
        seen.addFirst(cp to System.currentTimeMillis())
        while (seen.size > 4) seen.removeLast()
    }
}

/** Pichhle frame ka cp, aur us waqt d network wala tha? */
private class PromoPrev { var cp: CpData? = null; var net = false; var seen = false }

/**
 * st.cp network se kab aaya (ms); 0 = pata nahi / disk wala copy (Live mat kaho). Pehli baar dekha copy (kab ka, pata nahi)
 * = 0: agle refresh ka naya copy Live. Har frame bulao (data na ho tab bhi), cp = st.cp (sab apps wala).
 */
@Composable
internal fun promoLiveAt(cp: CpData?, d: DashData?): Long {
    val net = d != null && !d.cached
    val prev = remember { PromoPrev() }
    if (cp != null && prev.seen && prev.net && net && prev.cp !== cp) PromoNet.mark(cp)
    SideEffect { prev.cp = cp; prev.net = net; prev.seen = true }
    return cp?.let { PromoNet.at(it) } ?: 0L
}

/** Skeleton: hero + (detail: ad type strip) + app rows. Fold: hero baayein, list daayein. */
@Composable
internal fun PromoSkel(strip: Boolean = false) {
    KSkeleton {
        val left: @Composable ColumnScope.() -> Unit = {
            KSkelHero(KTint.Sand)
            if (strip) KSkelBlock(54.dp)
        }
        val right: @Composable ColumnScope.() -> Unit = {
            if (!strip) KSkelSection(width = 90.dp)
            KSkelRows(6)
        }
        if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
    }
}

/** Cross promo ka data (sab apps wala; na ho to us app wala): detail page isi se. */
internal fun promoData(st: UiState, pkg: String): CpData? = st.cp ?: st.appCp[pkg]
