package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.cpFmtName
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.components.Empty
import com.myfuturecode.mfcdash.ui.components.HBars
import com.myfuturecode.mfcdash.ui.components.Table
import com.myfuturecode.mfcdash.ui.components.TileGrid
import com.myfuturecode.mfcdash.ui.components.shareOf
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.t

/** api/crosspromo.php: kaunsa app kis app ka promo dikhata hai, ad type, views / clicks / installs, design CTR. Filters: shown in, promoted, type. */
@Composable
fun CrossPromoScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var src by rememberSaveable { mutableStateOf("") }
    var tgt by rememberSaveable { mutableStateOf("") }
    var fmt by rememberSaveable { mutableStateOf("") }
    TabScreen("Cross promo".t(), vm, st, nav, back = true, showApps = false, filters = { v ->
        val all = v.cp?.rows ?: emptyList()
        val appOpts = { pk: List<String> -> listOf("" to "All".t()) + pk.filter { it.isNotBlank() }.distinct().map { it to v.d.appName(it) } }
        if (all.isNotEmpty()) {
            FilterChips("Shown in".t(), appOpts(all.map { it.src }), src) { src = it }
            FilterChips("Promoted".t(), appOpts(all.map { it.tgt }), tgt) { tgt = it }
            FilterChips("Type".t(), listOf("" to "All".t()) + all.map { it.fmt }.distinct().sorted().map { it to cpFmtName(it) }, fmt) { fmt = it }
        }
    }) { v ->
        val d = v.d
        val c = Mfc.c
        val cp = v.cp
        val rows = (cp?.rows ?: emptyList()).filter { (src.isBlank() || it.src == src) && (tgt.isBlank() || it.tgt == tgt) && (fmt.isBlank() || it.fmt == fmt) }
        val vw = rows.sumOf { it.views }
        val cl = rows.sumOf { it.clicks }
        val ins = rows.sumOf { it.installs }
        val rw = rows.sumOf { it.rewards }

        val left: @Composable ColumnScope.() -> Unit = {
            // hero: promo se kitne installs
            KHero(KTint.Sand) {
                KHeroHead("Promo installs".t(), trailing = { DashText(periodText(d), 13.sp, Mfc.c.ink2) })
                KBigNum(num(ins), size = 48.sp)
                KSplit(listOf("Views".t() to num(vw), "Clicks".t() to num(cl), "CTR" to pct(if (vw > 0) cl / vw else null)))
            }
            TileGrid(
                listOf(
                    Triple("Install / click".t(), pct(if (cl > 0) ins / cl else null), null),
                    Triple("Rewards".t(), num(rw), "rewarded".t()),
                ),
                cols = 2,
            )
            KSection("By ad type".t())
            KCard {
                val g = rows.groupBy { it.fmt }.map { (f, l) -> Triple(cpFmtName(f), l.sumOf { it.views }, l.sumOf { it.installs }) }.sortedByDescending { it.second }
                Table(
                    listOf("Ad type".t(), "Views".t(), "Installs".t(), "Inst / 1000 views".t()),
                    g.map { listOf(it.first, num(it.second), num(it.third), num(if (it.second > 0) it.third / it.second * 1000 else null, 1)) },
                    empty = "No promo shown".t(),
                )
            }
            KSection("By promoted app".t())
            KCard {
                val g = rows.groupBy { it.tgt }.map { (t, l) -> Triple(d.appName(t), l.sumOf { it.installs }, l.sumOf { it.clicks }) }.sortedByDescending { it.second }
                val tot = g.sumOf { it.second }
                if (g.isEmpty()) Empty("No data".t())
                else HBars(g.map { Triple(it.first, num(it.second) + " " + "installs".t() + " · " + num(it.third) + " " + "clicks".t(), shareOf(it.second, tot)) }, color = c.zGreen)
            }
        }
        val right: @Composable ColumnScope.() -> Unit = {
            KSection("Which app promotes which".t())
            KCard {
                Table(
                    listOf("Shown in".t(), "Promoted".t(), "Type".t(), "Views".t(), "Clicks".t(), "CTR", "Installs".t(), "Inst/click".t()),
                    rows.sortedByDescending { it.views }.map { listOf(d.appName(it.src), d.appName(it.tgt), cpFmtName(it.fmt), num(it.views), num(it.clicks), pct(it.ctr), num(it.installs), pct(it.installPerClick)) },
                    empty = "No promo shown".t(),
                )
            }
            KSection("By design".t())
            KCard {
                val ds = (cp?.designs ?: emptyList()).filter { (src.isBlank() || it.src == src) && (fmt.isBlank() || it.fmt == fmt) }
                Table(
                    listOf("Shown in".t(), "Design".t(), "Type".t(), "Views".t(), "Clicks".t(), "CTR"),
                    ds.sortedByDescending { it.ctr ?: 0.0 }.map { listOf(d.appName(it.src), it.design.ifBlank { "–" }, cpFmtName(it.fmt), num(it.views), num(it.clicks), pct(it.ctr)) },
                    empty = "No design data".t(),
                )
            }
        }
        if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
    }
}
