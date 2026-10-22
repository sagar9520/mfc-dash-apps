package com.myfuturecode.mfcdash.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KLazyScreen
import com.myfuturecode.mfcdash.ui.kit.KLegend
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLive
import com.myfuturecode.mfcdash.ui.kit.KMiniPill
import com.myfuturecode.mfcdash.ui.kit.KSortButton
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSearch
import com.myfuturecode.mfcdash.ui.kit.KSegBar
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSkelBlock
import com.myfuturecode.mfcdash.ui.kit.KSkelHero
import com.myfuturecode.mfcdash.ui.kit.KSkelRows
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KTabBar
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kCardItems
import com.myfuturecode.mfcdash.ui.kit.kItem
import com.myfuturecode.mfcdash.ui.kit.kRoasKind
import com.myfuturecode.mfcdash.ui.kit.kSortLead
import com.myfuturecode.mfcdash.ui.kit.kSorted
import com.myfuturecode.mfcdash.ui.kit.rememberKSort
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.roasPct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf

/**
 * Apps tab: hero = kitne apps profit me (profit / loss / bina data patti), phir har app ek row:
 * avatar, naam, ROAS (rang niyam), daayein signed munafa (crash zyada ho to neeche laal pill). Tap = app detail.
 * Sort: bar ka Sort button (Filter ke baad), default munafa zyada -> kam; badla ho to list ke upar pill.
 */
@Composable
fun AppsScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }          // 0 sab | 1 profit | 2 loss | 3 bina data
    // sort: bar ka Sort button (default munafa zyada -> kam, aaj jaisa); bina chhede pill nahi
    val sort = rememberKSort("profit")
    val sortOpts = listOf(
        "profit" to "Net profit".t(), "earned" to "Earnings".t(), "roas" to "ROAS", "spend" to "Ads spend".t(),
        "installs" to "Installs".t(), "live" to "Live users".t(), "name" to "Name".t(),
    )
    var show by rememberSaveable { mutableStateOf("") }          // "" sab | paid = ad kharch wale | organic
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = searching) { searching = false; q = "" }
    val d = st.data
    val rows = remember(d) { if (d == null) emptyList() else appsRowsOf(d) }
    val nProfit = rows.count { it.state == 1 }
    val nLoss = rows.count { it.state == 2 }
    val nNone = rows.count { it.state == 3 }
    val needle = q.trim()
    val shown = appsSorted(
        rows.filter { r ->
            (tab == 0 || r.state == tab) &&
                when (show) {
                    "paid" -> r.spend > 0
                    "organic" -> r.spend <= 0
                    else -> true
                } &&
                (needle.isEmpty() || r.name.contains(needle, true) || r.pkg.contains(needle, true))
        },
        sort.key, sort.desc,
    )
    val open: (String) -> Unit = { p -> nav.navigate("app/$p") }
    // Show (purane Home ka app filter) bar ke filter icon ke peeche sheet me; Sort ab apne button me
    val box = remember { FilterBox() }
    CompositionLocalProvider(LocalFilterBox provides box) {
        FilterChips(
            "Show".t(),
            listOf("" to "All".t(), "paid" to "With ad spend".t(), "organic" to "Organic only".t()),
            show,
        ) { show = it }
    }
    val bar: @Composable ColumnScope.() -> Unit = {
        KTabBar("Apps".t()) {
            KBarIcon(if (searching) KIcons.Close else KIcons.Search, "Search".t(), {
                searching = !searching
                if (!searching) q = ""
            })
            KBarIcon(KIcons.Filter, "Filters".t(), { box.open = true }, badge = box.active.size, badgeUrgent = false)
            KSortButton(sort, sortOpts)
            DashRangeIcon(vm, st)
            KBarIcon(KIcons.Refresh, "Refresh".t(), { vm.refresh() }, spinning = st.loading)
        }
    }
    val tabs = listOf("All".t(), "Profit".t(), "Loss".t(), "No data".t())
    if (d == null) {
        // pehli baar: hero + tabs + app rows ka skeleton (error ho to error card)
        val wide = isWide()
        KScreen(top = { bar(); if (st.loading && !wide) KSkelHero() }) {
            DashNoData(st, st.loading, { AppsSkel(wide) }) { vm.refresh() }
        }
    } else if (isWide()) {
        // Fold: baayein hero + tabs, daayein list
        KScreen(top = bar) {
            KTwoPane(
                left = {
                    AppsHero(d, st.range, nProfit, nLoss, nNone)
                    KSegTabs(tabs, tab, { tab = it })
                },
                right = {
                    if (searching) KSearch(q, { q = it }, "Search apps".t())
                    FilterSummary(box, lead = kSortLead(sort, sortOpts))
                    if (shown.isEmpty()) KCard { KEmpty(if (rows.isEmpty()) "No apps".t() else "No match".t(), KIcons.Apps) }
                    else KCard(list = true) {
                        shown.forEachIndexed { i, r ->
                            if (i > 0) KListDivider(true)
                            AppsRow(r, sort.key, st.roasTarget) { open(r.pkg) }
                        }
                    }
                },
            )
        }
    } else {
        KLazyScreen(top = {
            bar()
            AppsHero(d, st.range, nProfit, nLoss, nNone)
        }) {
            if (searching) kItem(key = "apps.search") { KSearch(q, { q = it }, "Search apps".t()) }
            kItem(key = "apps.tabs") { KSegTabs(tabs, tab, { tab = it }) }
            if (box.active.isNotEmpty() || !sort.isDefault) kItem(key = "apps.filters") { FilterSummary(box, lead = kSortLead(sort, sortOpts)) }
            if (shown.isEmpty()) kItem(key = "apps.empty") {
                KCard { KEmpty(if (rows.isEmpty()) "No apps".t() else "No match".t(), KIcons.Apps) }
            }
            else kCardItems(shown, key = { it.pkg }, lead = true) { r -> AppsRow(r, sort.key, st.roasTarget) { open(r.pkg) } }
        }
    }
    FilterSheet(box)
}

/** Apps ka skeleton: tabs + app rows (avatar, naam, ROAS, munafa). Fold: baayein hero + tabs, daayein list. */
@Composable
private fun AppsSkel(wide: Boolean) {
    KSkeleton {
        val tabs: @Composable () -> Unit = { KSkelBlock(48.dp, RoundedCornerShape(24.dp)) }
        if (wide) KTwoPane(left = { KSkelHero(); tabs() }, right = { KSkelRows(7) })
        else {
            tabs()
            KSkelRows(7)
        }
    }
}

/** Ek app ki row ka data. state: 1 profit | 2 loss | 3 bina data. */
private class AppsRowData(
    val pkg: String, val name: String, val state: Int, val profit: Double, val earned: Double, val spend: Double,
    val roas: Double?, val installs: Double, val dau: Double, val live: Double, val crashes: Double,
)

/** d.apps (order) + perApp (numbers) + crashes; perApp me naya app ho to wo bhi. */
private fun appsRowsOf(d: DashData): List<AppsRowData> {
    val r = d.r
    val per = d.perApp.associateBy { it.pkg }
    val pkgs = (d.apps.map { it.pkg } + d.perApp.map { it.pkg }).filter { it.isNotBlank() }.distinct()
    return pkgs.map { p ->
        val a = per[p]
        val earned = if (a == null) 0.0 else a.revInr(r) + a.purchaseInr
        val spend = a?.gadsCostInr ?: 0.0
        val has = a != null && (earned > 0 || spend > 0)
        val profit = a?.profit(r) ?: 0.0
        AppsRowData(
            pkg = p, name = d.apps.firstOrNull { it.pkg == p }?.name ?: a?.name?.takeIf { it.isNotBlank() } ?: d.appName(p),
            state = if (!has) 3 else if (profit >= 0) 1 else 2,
            profit = profit, earned = earned, spend = spend, roas = a?.roas(r),
            installs = a?.installs ?: 0.0, dau = a?.dau ?: 0.0, live = d.liveByApp[p] ?: a?.live ?: 0.0,
            crashes = d.crashes.filter { it.pkg == p }.sumOf { it.n },
        )
    }
}

/** desc = bada pehle (naam: Z -> A). Bina ROAS wale aur (munafa me) bina data wale dono direction me aakhir me. */
private fun appsSorted(list: List<AppsRowData>, sort: String, desc: Boolean): List<AppsRowData> = when (sort) {
    "earned" -> list.kSorted(desc) { it.earned }
    "roas" -> list.kSorted(desc) { it.roas }
    "spend" -> list.kSorted(desc) { it.spend }
    "installs" -> list.kSorted(desc) { it.installs }
    "live" -> list.kSorted(desc) { it.live }
    "name" -> list.kSorted(desc) { it.name.lowercase() }
    // munafa (default, zyada -> kam): bina data wale aakhir me
    else -> list.kSorted(desc, last = { it.state == 3 }) { it.profit }
}

/** Crash zyada: 5+ crash aur DAU ka 1.09% se upar. */
private fun appsCrashHigh(a: AppsRowData) = a.crashes >= 5 && a.dau > 0 && a.crashes / a.dau >= 0.0109

/** Hero: "Aaj ke apps" + 5/8 apps profit me + legend + patti. */
@Composable
private fun AppsHero(d: DashData, range: String, nProfit: Int, nLoss: Int, nNone: Int) {
    KHero(KTint.Green) {
        val c = Mfc.c
        val label = when (range) {
            "today" -> "Today's apps".t()
            "yesterday" -> "Yesterday's apps".t()
            "7d" -> "Apps · 7 days".t()
            "30d" -> "Apps · 30 days".t()
            else -> "Apps".t() + " · " + periodText(d)
        }
        KHeroHead(label, trailing = { if (d.today && !d.cached) KLive("Live".t()) })
        val total = nProfit + nLoss + nNone
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // mockup: "18" hara + "/25" chhota, neeche "apps profit mein"
            Column(Modifier.weight(1f)) {
                KBigNum(num(nProfit.toDouble()), if (nProfit > 0) c.green else Color.Unspecified, suffix = "/" + num(total.toDouble()))
                DashText("apps in profit".t(), 15.sp, c.ink2, Modifier.padding(top = 2.dp))
            }
            KLegend(
                listOf(
                    Triple(c.zGreen, num(nProfit.toDouble()), "profit".t()),
                    Triple(c.zRed, num(nLoss.toDouble()), "loss".t()),
                    Triple(c.zGrey, num(nNone.toDouble()), "no data".t()),
                ),
            )
        }
        KSegBar(listOf(nProfit.toFloat() to c.zGreen, nLoss.toFloat() to c.zRed, nNone.toFloat() to c.zGrey), top = 12.dp)
    }
}

/** Ek app: avatar, naam, ROAS (shabd + shape + rang), signed munafa (+ sort wala number, + crash pill), chevron. */
@Composable
private fun AppsRow(a: AppsRowData, sort: String, target: Double, onClick: () -> Unit) {
    val c = Mfc.c
    val roas = a.roas
    // ROAS hamesha (pata ho to); crash zyada ho to alag laal pill value ke neeche
    val (sub, kind) = when {
        roas != null && roas.isFinite() -> "ROAS " + roasPct(roas) to kRoasKind(roas, target)
        a.state != 3 -> "No ad spend".t() to KKind.NEUTRAL
        else -> "No data".t() to KKind.NEUTRAL
    }
    // sort wala number value ke neeche (munafa sort par kuch nahi)
    val extra = when (sort) {
        "earned" -> inr(a.earned)
        "spend" -> tf("spend %s", inr(a.spend))
        "installs" -> tf("%s installs", num(a.installs))
        "live" -> tf("%s live", num(a.live))
        else -> null
    }
    KRow(
        a.name, sub = sub, subKind = kind,
        lead = { KAvatar(a.name) },
        value = if (a.state == 3) "–" else moneySigned(a.profit),
        valueColor = when (a.state) {
            1 -> if (a.profit > 0) c.green else c.ink
            2 -> c.red
            else -> c.ink3
        },
        valueSub = extra,
        chevron = true, onClick = onClick,
        valueBottom = if (appsCrashHigh(a)) ({ KMiniPill(tf("%s crash", num(a.crashes)), KKind.BAD, icon = KIcons.Bug) }) else null,
    )
}
