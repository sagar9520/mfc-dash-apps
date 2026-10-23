package com.myfuturecode.mfcdash.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.Campaign
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.GadsAccount
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.BarLineChart
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.components.Legend
import com.myfuturecode.mfcdash.ui.components.StatCells
import com.myfuturecode.mfcdash.ui.components.Table
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KDivider
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KKv
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSkelBars
import com.myfuturecode.mfcdash.ui.kit.KSkelCard
import com.myfuturecode.mfcdash.ui.kit.KSkelHero
import com.myfuturecode.mfcdash.ui.kit.KSkelLine
import com.myfuturecode.mfcdash.ui.kit.KSkelRows
import com.myfuturecode.mfcdash.ui.kit.KSkelSection
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KSortButton
import com.myfuturecode.mfcdash.ui.kit.KSortKind
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kRoasColor
import com.myfuturecode.mfcdash.ui.kit.kRoasKind
import com.myfuturecode.mfcdash.ui.kit.kSortLead
import com.myfuturecode.mfcdash.ui.kit.kSorted
import com.myfuturecode.mfcdash.ui.kit.rememberKSort
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.roasPct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tOnly
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.usd

/**
 * Paisa: munafa (hero) + ROAS, 7 din ka munafa, kamai kahan se, kharcha kahan, phir "Aur":
 * Campaigns aur Pura hisaab (isi screen ke andar ke page, back = wapas), Users / Ads / Live / Cross promo / Payments.
 * App filter (bar icon) ek app ka data dikhata hai.
 */
@Composable
fun MoneyScreen(vm: DashVm, st: UiState, nav: NavHostController) {
    var page by rememberSaveable { mutableStateOf("") }   // "" | camp | more
    var appSheet by remember { mutableStateOf(false) }
    BackHandler(enabled = page.isNotEmpty()) { page = "" }
    val pkg = st.appFilter
    LaunchedEffect(pkg, st.range, st.from, st.to, st.lastOk) { if (pkg != null) vm.loadApp(pkg, force = true) }
    val first = dashFirstFrame(pkg, st.range, st.from, st.to, st.lastOk)
    val d = if (pkg == null) st.data else st.appData[pkg]
    val v = if (d == null) null else View(d, if (pkg == null) st.extra else st.appExtra[pkg], st.extraMissing, if (pkg == null) st.cp else st.appCp[pkg], pkg)
    val ctx = PaisaCtx(
        vm, st, nav, v,
        appName = pkg?.let { p -> st.data?.appName(p) ?: p },
        loading = st.loading || (pkg != null && (st.appLoading == pkg || first)),
        retry = { if (pkg != null) vm.loadApp(pkg, force = true) else vm.refresh() },
    )
    when (page) {
        "camp" -> PaisaCampaigns(ctx) { page = "" }
        "more" -> PaisaMore(ctx) { page = "" }
        else -> PaisaMain(ctx, { appSheet = true }) { page = it }
    }
    DashAppSheet(vm, st, appSheet) { appSheet = false }
}

/** Paisa ke teeno page ki saajhi cheezein. */
private class PaisaCtx(
    val vm: DashVm, val st: UiState, val nav: NavHostController, val v: View?,
    val appName: String?, val loading: Boolean, val retry: () -> Unit,
)

// ---------------------------------------------------------------- main page

@Composable
private fun PaisaMain(x: PaisaCtx, openApps: () -> Unit, go: (String) -> Unit) {
    val st = x.st
    val v = x.v
    val wide = isWide()
    KScreen(top = {
        DashBar("Money".t(), x.vm, st, x.nav, sub = x.appName, extra = {
            DashAppFilterIcon(st, openApps)
            DashRangeIcon(x.vm, st)
        })
        DashPeriodTabs(x.vm, st, Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp))
        if (!wide) {
            if (v != null) MoneyHero(v.d, st, moneyProfitLabel(st.range))
            else if (x.loading) KSkelHero(split = 4)   // pehli baar: hero ki jagah skeleton (brand band par bhi)
        }
    }) {
        if (v == null) DashNoData(st, x.loading, { PaisaSkel(wide) }, x.retry)
        else if (wide) KTwoPane(
            left = {
                MoneyHero(v.d, st, moneyProfitLabel(st.range))
                MoneyWeekCard(v.d, st.range)
            },
            right = {
                PaisaSources(v.d)
                PaisaLinks(v, x.nav, go)
            },
        )
        else {
            MoneyWeekCard(v.d, st.range)
            PaisaSources(v.d)
            PaisaLinks(v, x.nav, go)
        }
    }
}

/** Paisa ka skeleton (data aane tak): 7 din bars, kamai / kharcha ke do hisse, "Aur" ki list. Fold: hero baayein. */
@Composable
private fun PaisaSkel(wide: Boolean) {
    KSkeleton {
        val week: @Composable ColumnScope.() -> Unit = {
            KSkelSection()
            KSkelBars()
        }
        val rest: @Composable ColumnScope.() -> Unit = {
            KSkelSection(width = 160.dp)
            PaisaSplitSkel()
            KSkelSection(width = 150.dp)
            PaisaSplitSkel()
            KSkelSection(width = 60.dp)
            KSkelRows(7, value = false)
        }
        if (wide) KTwoPane(left = { KSkelHero(split = 4); week() }, right = rest) else { week(); rest() }
    }
}

/** PaisaSplit jaisa skeleton: do hisse (label, bada number, caption) + patti. */
@Composable
private fun PaisaSplitSkel() {
    val c = Mfc.c
    KCard {
        Row(Modifier.fillMaxWidth()) {
            repeat(2) { i ->
                val m = if (i == 0) Modifier.weight(1f).padding(end = 6.dp)
                else Modifier.weight(1f).drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }.padding(start = 18.dp)
                Column(m) {
                    KSkelLine(if (i == 0) 72.dp else 84.dp, 12.dp)
                    KSkelLine(if (i == 0) 110.dp else 96.dp, 22.dp, Modifier.padding(top = 10.dp))
                    KSkelLine(60.dp, 10.dp, Modifier.padding(top = 10.dp))
                }
            }
        }
        KSkelLine(1f, 10.dp, Modifier.padding(top = 14.dp))
    }
}

/** Do hisson ka card ka ek hissa: label, bada number, chhota caption; weight = patti me hissa. */
private class PaisaPart(val label: String, val value: String, val caption: String?, val weight: Double)

/** "Kamai kahan se" (Ads se | Payments) aur "Kharcha kahan" (Google Ads accounts). */
@Composable
private fun PaisaSources(d: DashData) {
    val k = d.kpi
    val earned = k.revInr + k.purchaseInr
    KSection("Earnings by source".t(), trailing = { KPill(inr(earned), KKind.NEUTRAL, large = true) })
    KCard {
        PaisaSplit(
            listOf(
                PaisaPart("From ads".t(), inr(k.revInr), if (earned > 0) num(k.revInr / earned * 100) + "%" else null, k.revInr),
                PaisaPart("Payments".t(), inr(k.purchaseInr), tf("%s payments", num(k.purchases)), k.purchaseInr),
            ),
        )
    }
    KSection("Spend by account".t(), trailing = { KPill(inr(k.gadsCostInr), KKind.NEUTRAL, large = true) })
    val parts = paisaSpendParts(d)
    KCard {
        if (parts.isEmpty()) KEmpty("No spend".t(), KIcons.Ads) else PaisaSplit(parts)
    }
}

/** Google Ads kharcha ke do hisse: INR vs doosri currency, warna sabse bada account vs baaki. */
private fun paisaSpendParts(d: DashData): List<PaisaPart> {
    val acc = d.gadsAccounts.filter { it.cost > 0 }.sortedByDescending { it.cost }
    if (acc.isEmpty()) {
        val cost = d.kpi.gadsCostInr
        return if (cost > 0) listOf(PaisaPart("Google Ads", inr(cost), null, cost)) else emptyList()
    }
    fun one(a: GadsAccount) = PaisaPart(
        a.name, inr(a.cost),
        if (a.currency.isNotBlank() && !a.currency.equals("INR", true)) a.currency + " → ₹" else tf("%s campaigns", num(a.campaigns)),
        a.cost,
    )
    fun group(list: List<GadsAccount>, label: String): PaisaPart =
        if (list.size == 1) one(list[0]) else PaisaPart(label, inr(list.sumOf { it.cost }), tf("%s accounts", list.size), list.sumOf { it.cost })
    val inrAcc = acc.filter { it.currency.isBlank() || it.currency.equals("INR", true) }
    val other = acc.filterNot { it.currency.isBlank() || it.currency.equals("INR", true) }
    return when {
        inrAcc.isNotEmpty() && other.isNotEmpty() -> listOf(group(inrAcc, "INR accounts".t()), group(other, "Other currency".t()))
        acc.size <= 2 -> acc.map { one(it) }
        else -> listOf(one(acc[0]), group(acc.drop(1), "Other accounts".t()))
    }
}

@Composable
private fun PaisaSplit(parts: List<PaisaPart>) {
    val c = Mfc.c
    val ps = parts.take(2)
    Row(Modifier.fillMaxWidth()) {
        ps.forEachIndexed { i, p ->
            val m = if (i == 0) Modifier.weight(1f).padding(end = 6.dp)
            else Modifier.weight(1f).drawBehind { drawLine(c.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }.padding(start = 18.dp)
            Column(m) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    PaisaSwatch(i)
                    DashText(p.label, 14.sp, c.ink2)
                }
                Box(Modifier.padding(top = 1.dp)) { KBigNum(p.value, size = 22.sp) }
                p.caption?.let { DashText(it, 13.sp, c.ink3, Modifier.padding(top = 2.dp)) }
            }
        }
    }
    PaisaBar(ps.map { it.weight })
}

/** Rang ka chhota square: pehla = brand, doosra = halka (step) + brand line. */
@Composable
private fun PaisaSwatch(i: Int) {
    val c = Mfc.c
    val shape = RoundedCornerShape(3.dp)
    var m = Modifier.size(10.dp).clip(shape).background(if (i == 0) c.brand else c.step)
    if (i != 0) m = m.border(1.dp, c.brand, shape)
    Box(m)
}

/** Hisson ki patti (10dp). Sab 0 = khaali track. */
@Composable
private fun PaisaBar(w: List<Double>) {
    val c = Mfc.c
    val shown = w.withIndex().filter { it.value.isFinite() && it.value > 0 }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (shown.isEmpty()) Box(Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(c.zTrack))
        shown.forEachIndexed { j, part ->
            val first = j == 0
            val last = j == shown.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (first) 5.dp else 0.dp, bottomStart = if (first) 5.dp else 0.dp,
                topEnd = if (last) 5.dp else 0.dp, bottomEnd = if (last) 5.dp else 0.dp,
            )
            var m = Modifier.weight(part.value.toFloat()).fillMaxHeight().clip(shape).background(if (part.index == 0) c.brand else c.step)
            if (part.index != 0) m = m.border(1.dp, c.brand, shape)
            Box(m)
        }
    }
}

/** "Aur": Campaigns, Pura hisaab (isi screen me), Users / Ads / Live / Cross promo / Payments (alag screens). */
@Composable
private fun PaisaLinks(v: View, nav: NavHostController, go: (String) -> Unit) {
    val d = v.d
    val k = d.kpi
    val running = d.campaigns.count { it.status.equals("ENABLED", true) }
    KSection("More".t())
    KCard(list = true) {
        KRow(
            "Campaigns".t(), sub = tf("%s running", running) + " · " + inr(k.gadsCostInr),
            lead = { KIconTile(KIcons.Ads) }, chevron = true, onClick = { go("camp") },
        )
        KListDivider(true)
        KRow(
            "Full report".t(), sub = tf("%s payments", num(k.purchases)) + " · " + inr(k.purchaseInr),
            lead = { KIconTile(KIcons.Chart) }, chevron = true, onClick = { go("more") },
        )
        KListDivider(true)
        KRow(
            "Users".t(), sub = "DAU " + num(k.dau) + " · " + num(k.installs) + " " + "installs".t(),
            lead = { KIconTile(KIcons.Users) }, chevron = true, onClick = { nav.navigate("users") },
        )
        KListDivider(true)
        KRow(
            "Ads".t(), sub = inr(k.revInr) + " · eCPM " + usd(k.ecpmUsd),
            lead = { KIconTile(KIcons.Rupee) }, chevron = true, onClick = { nav.navigate("ads") },
        )
        KListDivider(true)
        KRow(
            "Live".t(), sub = num(d.liveTotal) + " " + "active now".t(),
            lead = { KIconTile(KIcons.Live) }, chevron = true, onClick = { nav.navigate("live") },
        )
        KListDivider(true)
        val cp = v.cp
        KRow(
            "Cross promo".t(), sub = if (cp == null) null else num(cp.rows.sumOf { it.installs }) + " " + "installs".t(),
            lead = { KIconTile(KIcons.Apps) }, chevron = true, onClick = { nav.navigate("promo") },
        )
        KListDivider(true)
        // Payments: data.php ke number (Paisa ke "Kamai kahan se" jaise), failed laal. Ek app chuna ho to seedha us app ke payments.
        PayLinkRow(
            "Payments".t(), paySub(inr(k.purchaseInr), k.purchases.toInt(), k.purchaseFailed.toInt()),
            lead = { KIconTile(KIcons.Card) },
            onClick = { nav.navigate(v.pkg?.let { "payments/app/$it" } ?: "payments") },
        )
    }
}

// ---------------------------------------------------------------- campaigns page

@Composable
private fun PaisaCampaigns(x: PaisaCtx, back: () -> Unit) {
    val st = x.st
    val v = x.v
    var status by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    // sort: bar ka Sort button (default kharcha zyada -> kam, aaj jaisa); cost / conv pehle kam -> zyada
    val sort = rememberKSort("cost")
    val sortOpts = listOf("cost" to "Cost".t(), "cpi" to "Cost/conv".t(), "conv" to "Conv".t(), "ctr" to "CTR", "name" to "Name".t())
    val sortKinds = mapOf("cpi" to KSortKind.NUM_LOW)
    val box = remember { FilterBox() }
    val upd = v?.d?.kpi?.gadsUpdated.orEmpty()
    val sub = listOfNotNull(x.appName, if (v == null) null else if (upd.isNotBlank()) "update".t() + " " + tOnly(upd) else "no script data".t())
        .joinToString(" · ")
    KScreen(top = {
        KDetailBar("Campaigns".t(), back, sub = sub, actions = {
            if (box.rows.isNotEmpty()) KBarIcon(KIcons.Filter, "Filters".t(), { box.open = true }, badge = box.active.size, badgeUrgent = false)
            KSortButton(sort, sortOpts, sortKinds)
            KBarIcon(KIcons.Refresh, "Refresh".t(), { x.vm.refresh() }, spinning = st.loading)
        })
    }) {
        if (v == null) DashNoData(st, x.loading, {
            KSkeleton {
                KSkelSection(width = 190.dp)
                KSkelRows(6, lead = false)
            }
        }, x.retry)
        else {
            val d = v.d
            CompositionLocalProvider(LocalFilterBox provides box) {
                FilterChips("Campaigns".t(), listOf("" to "All".t(), "ENABLED" to "Enabled".t(), "PAUSED" to "Paused".t(), "spend" to "With spend".t(), "noconv" to "0 conv".t()), status) { status = it }
                if (d.gadsAccounts.size > 1) FilterChips("Account".t(), listOf("" to "All".t()) + d.gadsAccounts.map { it.name to it.name }, account) { account = it }
            }
            FilterSummary(box, lead = kSortLead(sort, sortOpts, sortKinds))
            val cs = d.campaigns.filter { c ->
                (account.isBlank() || c.account == account) && when (status) {
                    "ENABLED" -> c.status.equals("ENABLED", true)
                    "PAUSED" -> !c.status.equals("ENABLED", true)
                    "spend" -> c.cost > 0
                    "noconv" -> c.cost > 0 && c.conversions == 0.0
                    else -> true
                }
            }.let { list ->
                // bina cost/conv ya CTR wali campaigns dono taraf aakhir me
                when (sort.key) {
                    "cpi" -> list.kSorted(sort.desc) { it.cpi }
                    "conv" -> list.kSorted(sort.desc) { it.conversions }
                    "ctr" -> list.kSorted(sort.desc) { it.ctr }
                    "name" -> list.kSorted(sort.desc) { it.campaign.lowercase() }
                    else -> list.kSorted(sort.desc) { it.cost }
                }
            }
            val left: @Composable ColumnScope.() -> Unit = {
                KSection("Google Ads campaigns".t(), trailing = {
                    KPill(if (cs.size != d.campaigns.size) tf("%s of %s", cs.size, d.campaigns.size) else num(cs.size.toDouble()), KKind.NEUTRAL, large = true)
                })
                if (cs.isEmpty()) KCard { KEmpty("No campaigns match".t(), KIcons.Ads) }
                else KCard(list = true) {
                    cs.forEachIndexed { i, c ->
                        if (i > 0) KListDivider()
                        PaisaCampRow(c, d, sort.key) {
                            // CampaignScreen sab-apps list ka index padhta hai
                            val idx = dashCampaignIdx(st, c)
                            if (idx >= 0) x.nav.navigate("campaign/$idx")
                        }
                    }
                }
            }
            val right: @Composable ColumnScope.() -> Unit = {
                PaisaAccounts(d, account)
                KSection("ROAS cohort: by campaign".t(), trailing = cohortTrailing(v.extra))
                val ex2 = v.extra
                if (ex2 == null) ExtraMissingCard("Campaign-wise ROAS cohort".t(), v.extraPending)
                else KCard {
                    RoasCohortTable(ex2.campaignRoas, st.data ?: d, st.roasTarget) { cr ->
                        val idx = st.data?.campaigns?.indexOfFirst { it.campaign.equals(cr.campaign, true) } ?: -1
                        if (idx >= 0) x.nav.navigate("campaign/$idx")
                    }
                    if (ex2.organicInstalls > 0) {
                        KDivider()
                        KKv("Organic (no utm) installs".t(), num(ex2.organicInstalls))
                    }
                }
                KSection("Change log".t())
                if (ex2 == null) ExtraMissingCard("Change log".t(), v.extraPending) else NotesSection(x.vm, ex2.notes, "", "")
            }
            if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
        }
    }
    FilterSheet(box)
}

/** Ek campaign: naam, status (shabd + shape + rang) · app, kharcha, sort wala number. */
@Composable
private fun PaisaCampRow(c: Campaign, d: DashData, sort: String, onClick: () -> Unit) {
    val enabled = c.status.equals("ENABLED", true)
    val (word, kind) = when {
        c.cost > 0 && c.conversions == 0.0 -> "0 conv".t() to KKind.BAD
        enabled -> "Running".t() to KKind.OK
        else -> "Paused".t() to KKind.NEUTRAL
    }
    val app = d.appName(c.pkg)
    val extra = when (sort) {
        "cpi" -> (c.cpi?.let { inr(it, 1) } ?: "–") + " / conv"
        "ctr" -> "CTR " + pct(c.ctr)
        else -> tf("%s conv", num(c.conversions, 1))
    }
    KRow(
        c.campaign, sub = if (app.isBlank()) word else "$word · $app", subKind = kind,
        value = inr(c.cost), valueSub = extra, chevron = true, onClick = onClick,
    )
}

/** Google Ads accounts: har account ek card (kharcha + chhote numbers). */
@Composable
private fun PaisaAccounts(d: DashData, account: String) {
    val list = d.gadsAccounts.filter { account.isBlank() || it.name == account }
    if (list.isEmpty()) return
    val c = Mfc.c
    KSection("Google Ads accounts".t())
    list.forEach { a ->
        KCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    DashText(a.name, 16.sp, c.ink, weight = FontWeight.SemiBold)
                    DashText("ID " + a.idFmt + (if (a.currency.isNotBlank() && a.currency != "INR") " · ${a.currency} → ₹" else ""), 13.sp, c.ink3)
                }
                DashText(inr(a.cost), 20.sp, c.ink, Modifier.padding(start = 8.dp), weight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            StatCells(
                listOf(
                    Triple("Impr".t(), num(a.impressions), null), Triple("Clicks".t(), num(a.clicks), null), Triple("CTR", pct(a.ctr), null),
                    Triple("Conv".t(), num(a.conversions, 1), null), Triple("Cost/conv".t(), inr(a.cpi, 1), null), Triple("Campaigns".t(), num(a.campaigns), null),
                ),
            )
            DashText(
                a.pkgs.joinToString { d.appName(it) } + " · " + "updated".t() + " " + tOnly(a.updatedAt), 13.sp, c.ink3,
                Modifier.padding(top = 12.dp), maxLines = 2,
            )
        }
    }
}

// ---------------------------------------------------------------- full report page

@Composable
private fun PaisaMore(x: PaisaCtx, back: () -> Unit) {
    val st = x.st
    val v = x.v
    var pay by rememberSaveable { mutableIntStateOf(0) }   // 0 sab | 1 paid | 2 failed | 3 started
    // do list, har ek ka apna Sort button section ke daayein (default = aaj ka order)
    val paySort = rememberKSort("amt")
    val daySort = rememberKSort("day")
    KScreen(top = {
        KDetailBar("Full report".t(), back, sub = x.appName ?: v?.let { periodText(it.d) }, actions = {
            KBarIcon(KIcons.Refresh, "Refresh".t(), { x.vm.refresh() }, spinning = st.loading)
        })
    }) {
        if (v == null) DashNoData(st, x.loading, {
            KSkeleton {
                KSkelSection(width = 170.dp)
                KSkelCard(6, title = false)
                KSkelSection(width = 160.dp)
                KSkelBars(14)
                KSkelSection(width = 100.dp)
                KSkelRows(4, lead = false)
            }
        }, x.retry)
        else {
            val d = v.d
            val k = d.kpi
            val r = d.r
            val c = Mfc.c
            val earned = k.revInr + k.purchaseInr
            val hist = d.history.sortedBy { it.day }.takeLast(14)
            val left: @Composable ColumnScope.() -> Unit = {
                // ---- per install
                KSection("Per-install economics".t(), trailing = { DashText(periodText(d), 13.sp, c.ink3) })
                val cpi = if (k.gadsInstalls > 0) k.gadsCostInr / k.gadsInstalls else null
                val revPer = if (k.installs > 0) earned / k.installs else null
                val profPer = if (k.installs > 0) k.profitInr / k.installs else null
                val arpdauInr = k.arpdauUsd * r
                val payback = if (cpi != null && arpdauInr > 0) cpi / arpdauInr else null
                KCard {
                    KKv("Cost / conv".t(), inr(cpi, 2))
                    KDivider()
                    KKv("Earned / install".t(), inr(revPer, 2))
                    KDivider()
                    KKv("Profit / install".t(), inr(profPer, 2), valueColor = if ((profPer ?: 0.0) >= 0) c.green else c.red)
                    KDivider()
                    KKv("ARPDAU", inr(arpdauInr, 2))
                    KDivider()
                    KKv("Payback".t(), if (payback == null) "–" else "~" + num(payback, 1) + " " + "days".t())
                    KDivider()
                    KKv("Ads users rev".t(), inr(k.revFromGadsUsersInr))
                }
                // ---- 14 din: kamai vs kharcha
                KSection("Daily: earned vs spent".t(), trailing = { DashText(tf("last %s days", hist.size), 13.sp, c.ink3) })
                KCard {
                    BarLineChart(hist.map { fmtDay(it.day).take(2) }, hist.map { it.revUsd * r + it.purchaseInr }, hist.map { it.gadsCostInr })
                    Legend("Earned ₹".t() to c.brand, "Ad spend ₹".t() to c.zAmber)
                    val p14 = hist.sumOf { it.profit(r) }
                    val s14 = hist.sumOf { it.gadsCostInr }
                    val k14 = hist.sumOf { it.revUsd * r + it.purchaseInr }
                    val roas14 = if (s14 > 0) k14 / s14 else null
                    Spacer(Modifier.height(14.dp))
                    StatCells(
                        listOf(
                            Triple("Profit".t(), inr(p14), null), Triple("ROAS", roasPct(roas14), null),
                            Triple("Earned".t(), inr(k14), null), Triple("Spent".t(), inr(s14), null),
                        ),
                        cols = 2,
                        colors = mapOf(0 to if (p14 >= 0) c.green else c.red, 1 to kRoasColor(roas14, st.roasTarget)),
                    )
                }
                // ---- roz ka ROAS (ROAS rang niyam)
                if (hist.isNotEmpty()) {
                    KSection("Daily ROAS".t())
                    KCard {
                        hist.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { h -> PaisaRoasCell(h.roas(r), st.roasTarget, Modifier.weight(1f)) }
                                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                week.forEach { h -> DashText(fmtDay(h.day).take(2), 12.sp, c.ink3, Modifier.weight(1f), align = TextAlign.Center) }
                                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            val right: @Composable ColumnScope.() -> Unit = {
                // ---- payments
                SortSection("Payments".t(), paySort, paySortOpts(), trailing = {
                    DashText(num(k.purchases) + " " + "paid".t() + " · " + num(k.purchaseFailed) + " " + "failed".t(), 13.sp, c.ink3)
                })
                KSegTabs(listOf("All".t(), "Paid".t(), "Failed".t(), "Started".t()), pay, { pay = it })
                val evs = listOf("", "purchase_done", "purchase_failed", "purchase_start")
                val ev = evs.getOrElse(pay) { "" }
                val ps = d.purchases.filter { ev.isBlank() || it.ev == ev }.paySorted(paySort.key, paySort.desc) { d.appName(it.pkg) + " · " + it.plan }
                if (ps.isEmpty()) KCard { KEmpty("No payments".t(), KIcons.Card) }
                else KCard(list = true) {
                    ps.forEachIndexed { i, p ->
                        if (i > 0) KListDivider()
                        val kind = when (p.ev) {
                            "purchase_done" -> KKind.OK
                            "purchase_failed" -> KKind.BAD
                            else -> KKind.NEUTRAL
                        }
                        KRow(
                            d.appName(p.pkg) + " · " + p.plan, sub = p.statusL, subKind = kind,
                            value = if (p.ev == "purchase_failed") "–" else inr(p.amt), valueSub = "× " + num(p.n),
                        )
                    }
                }
                // ---- roz ka hisaab (cron)
                SortSection("Daily summary".t(), daySort, historySortOpts(), HISTORY_SORT_KINDS)
                KCard {
                    Table(
                        listOf("Date".t(), "Installs".t(), "DAU", "Revenue".t(), "Pay".t(), "Spend".t(), "Profit".t(), "Uninst".t()),
                        d.history.historySorted(daySort.key, daySort.desc, r).map {
                            listOf(fmtDay(it.day), num(it.installs), num(it.dau), inr(it.revUsd * r), inr(it.purchaseInr), inr(it.gadsCostInr), inr(it.profit(r)), num(it.uninstalls))
                        },
                        empty = "No data yet".t(),
                    )
                }
            }
            if (isWide()) KTwoPane(left = left, right = right) else { left(); right() }
        }
    }
}

/** Roz ka ROAS cell: ROAS rang niyam (laal < min(target,100%), hara >= max(target,100%)). */
@Composable
private fun PaisaRoasCell(roas: Double?, target: Double, modifier: Modifier) {
    val c = Mfc.c
    val (bg, fg) = when (kRoasKind(roas, target)) {
        KKind.OK -> c.pOkBg to c.pOkFg
        KKind.WARN -> c.pWarnBg to c.pWarnFg
        KKind.BAD -> c.pBadBg to c.pBadFg
        else -> c.pNBg to c.ink3
    }
    Box(modifier.padding(2.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
        DashText(roasPct(roas?.takeIf { it.isFinite() }), 13.sp, fg, weight = FontWeight.SemiBold, align = TextAlign.Center)
    }
}
