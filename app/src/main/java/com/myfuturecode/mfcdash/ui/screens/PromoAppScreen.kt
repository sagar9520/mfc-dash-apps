package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.CpRow
import com.myfuturecode.mfcdash.data.cpFmtName
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KFit
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KMiniPill
import com.myfuturecode.mfcdash.ui.kit.KPullAction
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KT
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kSorted
import com.myfuturecode.mfcdash.ui.kit.kStyle
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf

/**
 * Cross promo: ek app ki detail (route "promo/app/{pkg}", mockup promo_pay frame 2). Bar: avatar + naam,
 * "Cross promo · <period>" (period = list wala, DashVm ka). Do tab: "Promoted in" = yeh app kin apps me dikha
 * (tgt == pkg, src se joda) | "Shown here" = is app me kin apps ka promo dikha (src == pkg, tgt se joda).
 * Har tab: hero (installs, views | clicks), 4 ad types ki ek patti (icon + installs), phir apps (installs zyada -> kam,
 * tap = us app ki promo detail). "Shown here" me neeche is app ke designs ka CTR. Naya load / poll nahi: DashVm ka data.
 */
@Composable
fun PromoAppScreen(vm: DashVm, st: UiState, nav: NavHostController, pkg: String) {
    var tab by rememberSaveable { mutableIntStateOf(0) }   // 0 Promoted in | 1 Shown here
    val d = st.data
    val cp = promoData(st, pkg)
    // Live sirf sab apps wale cp par (network se aaya pata ho); us app wala (appCp) fallback = period
    val liveAt = promoLiveAt(st.cp, d).takeIf { cp === st.cp } ?: 0L
    val name = d?.appName(pkg) ?: pkg.substringAfterLast('.')
    val first = dashFirstFrame(pkg)
    val lag = d?.aggLagSec ?: 0.0
    val sub = listOfNotNull(
        "Cross promo".t() + " · " + promoPeriod(st, d),
        if (lag > 300) tf("data %s min old", (lag / 60).toInt()) else null,
    ).joinToString(" · ")
    KScreen(top = {
        // mockup: bar me sirf wapas + avatar + naam (refresh = upar se kheencho)
        KDetailBar(name, { nav.popBackStack() }, sub = sub, lead = { KAvatar(name, size = 36.dp) })
        KSegTabs(
            listOf("Promoted in".t(), "Shown here".t()), tab, { tab = it },
            Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp).widthIn(max = 560.dp),
            fit = true,   // Hinglish naam lambe: bada font / patla phone par "..." nahi
        )
    }) {
        // sab apps wala refresh (cross promo usi ke saath aata hai): naya loop / poll nahi
        KPullAction({ vm.refresh() }, spinning = st.loading)
        if (d == null) {
            DashNoData(st, st.loading || first, { PromoSkel(strip = true) }) { vm.refresh() }
            return@KScreen
        }
        // crosspromo.php data.php ke baad aata hai: tab tak (8 sec tak) skeleton
        if (dashPending(cp == null)) { PromoSkel(strip = true); return@KScreen }
        // 8 sec baad bhi nahi (fail / der): "0" hero + "Not promoted" jhooth hota; galti + Try again
        if (cp == null) { DashNoData(st, false) { vm.refresh() }; return@KScreen }
        val all = cp.rows
        val rows = if (tab == 0) all.filter { it.tgt == pkg } else all.filter { it.src == pkg }
        val apps = promoGroup(rows, d) { if (tab == 0) it.src else it.tgt }
        val left: @Composable ColumnScope.() -> Unit = {
            PromoHero(d, cp, rows, liveAt)
            if (rows.isNotEmpty()) PromoTypeStrip(rows)   // khaali tab me chaar "0" nahi
        }
        val right: @Composable ColumnScope.() -> Unit = {
            if (apps.isEmpty()) KCard { KEmpty(if (tab == 0) "Not promoted".t() else "No promo shown".t(), KIcons.Apps) }
            else KCard(list = true) {
                apps.forEachIndexed { i, a ->
                    if (i > 0) KListDivider(true)
                    PromoPairRow(a) { nav.navigate("promo/app/" + a.pkg) }
                }
            }
            // is app me dikhe promo ke designs: CTR zyada -> kam
            if (tab == 1) {
                val ds = cp.designs.filter { it.src == pkg && it.views > 0 }.kSorted(true) { it.ctr }
                if (ds.isNotEmpty()) {
                    KSection("Designs".t())
                    KCard(list = true) {
                        ds.forEachIndexed { i, x ->
                            if (i > 0) KListDivider(true)
                            KRow(
                                x.design.ifBlank { "–" }, sub = cpFmtName(x.fmt) + " · " + num(x.views) + " " + "views".t(),
                                lead = { KIconTile(promoFmtIcon(x.fmt)) }, value = pct(x.ctr), valueSub = "CTR",
                            )
                        }
                    }
                }
            }
        }
        if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
    }
}

/**
 * 4 ad types ek line me: icon tile + installs, beech me koi line nahi. TalkBack: poori baat ek saath.
 * Sabse lamba number apne khane me na aaye to chaaron numbers ek hi chhote size me (ek chhota, baaki bade nahi).
 */
@Composable
private fun PromoTypeStrip(rows: List<CpRow>) {
    val c = Mfc.c
    val per = PROMO_FMTS.map { f -> f to rows.filter { it.fmt.equals(f, true) }.sumOf { it.installs } }
    val texts = per.map { num(it.second) }
    val says = "Installs by ad type".t() + ": " + per.joinToString(", ") { (f, n) -> cpFmtName(f) + " " + num(n) }
    KCard(padding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = says }) {
            val tm = rememberTextMeasurer()
            val density = LocalDensity.current
            val base = kStyle(17.sp, FontWeight.Bold, 22.sp)
            val cell = maxWidth / 4
            val k = remember(texts, cell, density, tm) {
                val widest = texts.maxOf { tm.measure(it, base, maxLines = 1, softWrap = false).size.width }.toFloat()
                // khana = tile 30 + gap 8 + do taraf 2 + 2
                val room = with(density) { (cell - 42.dp).toPx() }
                if (widest <= room || widest <= 0f || room <= 0f) 1f else (room / widest * .98f).coerceAtLeast(.6f)
            }
            val style = if (k < 1f) kStyle(17.sp * k, FontWeight.Bold, 22.sp * k) else base
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                per.forEachIndexed { i, (f, _) ->
                    Row(
                        Modifier.weight(1f).padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(c.brandSoft), contentAlignment = Alignment.Center) {
                            Icon(promoFmtIcon(f), null, tint = c.brand, modifier = Modifier.size(16.dp))
                        }
                        // phir bhi na aaye (60% se neeche) to KFit khud aur chhota: kabhi "..." nahi
                        KFit(texts[i], style, c.ink, Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
    }
}

/**
 * Doosre app ki row: avatar, naam, uske saare ad types (chips), views · clicks; daayein installs. Tap = us app ki detail.
 * Mockup jaisa chevron nahi: chips ko jagah (do chip ek line me).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromoPairRow(a: PromoAgg, onClick: () -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KAvatar(a.name)
        Column(Modifier.weight(1f)) {
            KT(a.name, 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
            if (a.fmts.isNotEmpty()) FlowRow(
                Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                a.fmts.forEach { f -> KMiniPill(cpFmtName(f), KKind.NEUTRAL, icon = promoFmtIcon(f)) }
            }
            KT(promoViewsClicks(a), 13.sp, c.ink2, Modifier.padding(top = 5.dp), lh = 18.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            KT(num(a.installs), 16.sp, c.ink, weight = FontWeight.Bold, lh = 20.sp)
            KT("installs".t(), 13.sp, c.ink3, Modifier.padding(top = 2.dp), lh = 18.sp)
        }
    }
}
