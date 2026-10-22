package com.myfuturecode.mfcdash.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.Campaign
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.cpFmtName
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.components.BarLineChart
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.components.Legend
import com.myfuturecode.mfcdash.ui.components.Table
import com.myfuturecode.mfcdash.ui.components.TileGrid
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kSignedPct
import com.myfuturecode.mfcdash.ui.kit.word
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.minutes
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.usd

/**
 * Ek app poora (data.php?app=<pkg>): munafa hero + ROAS, 7 din ka munafa, app ka haal (DAU / installs / crash),
 * Campaigns (isi screen ka page), phir purane sab hisse (users / paisa / ads / cross promo / aur). Filters: Show, Via.
 */
@Composable
fun AppDetailScreen(vm: DashVm, st: UiState, nav: NavHostController, pkg: String) {
    LaunchedEffect(pkg, st.range, st.from, st.to, st.lastOk) { vm.loadApp(pkg) }
    var page by rememberSaveable { mutableStateOf("") }   // "" | camp
    BackHandler(enabled = page.isNotEmpty()) { page = "" }
    val name = st.data?.appName(pkg) ?: st.appData[pkg]?.appName(pkg) ?: pkg
    if (page == "camp") AppCampaigns(vm, st, nav, pkg, name) { page = "" }
    else AppMain(vm, st, nav, pkg, name) { page = it }
}

@Composable
private fun AppMain(vm: DashVm, st: UiState, nav: NavHostController, pkg: String, name: String, go: (String) -> Unit) {
    val all = st.data
    val d = st.appData[pkg]
    var section by rememberSaveable { mutableStateOf("") }   // "" | users | money | ads | promo | more
    var med by rememberSaveable { mutableStateOf("") }
    val box = remember { FilterBox() }
    val wide = isWide()
    val loading = st.appLoading == pkg
    KScreen(top = {
        KDetailBar(
            name, { nav.popBackStack() },
            sub = if (d != null && d.aggLagSec > 300) tf("data %s min old", (d.aggLagSec / 60).toInt()) else null,
            lead = { KAvatar(name, size = 36.dp) },
            actions = {
                DashRangeIcon(vm, st)
                AppOverflow(box, loading) { vm.loadApp(pkg, force = true) }
            },
        )
        if (d != null && !wide) MoneyHero(d, st, moneyProfitLabel(st.range))
    }) {
        if (d == null) {
            DashNoData(st, loading || st.loading) { vm.loadApp(pkg, force = true) }
            return@KScreen
        }
        val meds = optionsOf(d.byFormat.map { it.med } + d.byNetwork.map { it.med })
        CompositionLocalProvider(LocalFilterBox provides box) {
            FilterChips(
                "Show".t(),
                listOf("" to "All".t(), "users" to "Users".t(), "money" to "Money".t(), "ads" to "Ads".t(), "promo" to "Cross promo".t(), "more" to "More".t()),
                section,
            ) { section = it }
            if ((section == "" || section == "ads") && meds.size > 2) FilterChips("Via".t(), meds, med) { med = it }
        }
        FilterSummary(box)
        val top: @Composable ColumnScope.() -> Unit = {
            MoneyWeekCard(d, st.range)
            AppHaal(d)
            AppLinks(d, pkg) { go("camp") }
        }
        val rest: @Composable ColumnScope.() -> Unit = { AppSections(st, nav, pkg, d, all, section, med) }
        if (wide) KTwoPane(
            left = {
                MoneyHero(d, st, moneyProfitLabel(st.range))
                top()
            },
            right = rest,
        )
        else {
            top()
            rest()
        }
    }
    FilterSheet(box)
}

/** Overflow (⋮): Refresh, Filters (ginti badge me). */
@Composable
private fun AppOverflow(box: FilterBox, loading: Boolean, onRefresh: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val n = box.active.size
    Box {
        KBarIcon(KIcons.More, "More".t(), { menu = true }, badge = n, badgeUrgent = false, spinning = false)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            KPageTokens {
                val c = Mfc.c
                Column {
                    DropdownMenuItem(
                        text = { DashText(if (loading) "Loading…".t() else "Refresh".t(), 15.sp, c.ink) },
                        onClick = { menu = false; onRefresh() },
                        leadingIcon = { Icon(KIcons.Refresh, null, tint = c.ink2) },
                        enabled = !loading,
                    )
                    if (box.rows.isNotEmpty()) DropdownMenuItem(
                        text = { DashText(if (n > 0) tf("Filters (%s)", n) else "Filters".t(), 15.sp, c.ink) },
                        onClick = { menu = false; box.open = true },
                        leadingIcon = { Icon(KIcons.Filter, null, tint = c.ink2) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- app ka haal

/**
 * Badlav ka rang: -5% tak theek, -20% tak dhyan, usse zyada girawat = turant. null = neutral.
 * partial = aaj ka din abhi chal raha: poore kal se kam hona normal hai, to girawat par neutral (laal nahi).
 */
private fun appDeltaKind(v: Double?, partial: Boolean): KKind = when {
    v == null || !v.isFinite() -> KKind.NEUTRAL
    v >= -0.05 -> KKind.OK
    partial -> KKind.NEUTRAL
    v > -0.20 -> KKind.WARN
    else -> KKind.BAD
}

/** Crash rate (crash / DAU): < 0.5% theek, < 1.09% dhyan, warna turant (Apps list jaisa: turant sirf 5+ crash par). */
private fun appCrashKind(crashes: Double, dau: Double): KKind {
    if (crashes <= 0) return KKind.OK
    if (dau <= 0) return KKind.WARN
    val r = crashes / dau
    return when {
        r < 0.005 -> KKind.OK
        r < 0.0109 || crashes < 5 -> KKind.WARN
        else -> KKind.BAD
    }
}

/** "App ka haal": DAU, installs (kal se badlav), crash (rate ke saath). Upar sabse buri haalat ka pill. */
@Composable
private fun AppHaal(d: DashData) {
    val k = d.kpi
    val prev = prevDay(d)
    val dauD = delta(k.dau, prev?.dau)
    val instD = delta(k.installs, prev?.installs)
    val dauK = appDeltaKind(dauD, d.today)
    val instK = appDeltaKind(instD, d.today)
    val crashK = appCrashKind(k.crashes, k.dau)
    val worst = listOf(crashK, dauK, instK).let { ks ->
        when {
            KKind.BAD in ks -> KKind.BAD
            KKind.WARN in ks -> KKind.WARN
            else -> KKind.OK
        }
    }
    KSection("App status".t(), trailing = { KPill(if (worst == KKind.OK) "All fine".t() else worst.word(), worst, large = true) })
    KCard(list = true) {
        KRow(
            "DAU",
            sub = if (dauD != null) kSignedPct(dauD * 100) + " " + "vs yesterday".t() else num(k.sessions) + " " + "sessions".t(),
            subKind = dauK,
            lead = { KIconTile(KIcons.Users) }, value = num(k.dau),
        )
        KListDivider(true)
        KRow(
            "Installs".t(),
            sub = if (instD != null) kSignedPct(instD * 100) + " " + "vs yesterday".t() else num(k.uninstalls) + " " + "uninstalls".t(),
            subKind = instK,
            lead = { KIconTile(KIcons.Download) }, value = num(k.installs),
        )
        KListDivider(true)
        val rate = if (k.dau > 0) k.crashes / k.dau else null
        KRow(
            "Crashes".t(),
            sub = crashK.word() + (if (rate != null) " · " + pct(rate, 2) + " " + "of DAU".t() else ""),
            subKind = crashK,
            lead = { KIconTile(KIcons.Bug, crashK) }, value = num(k.crashes),
        )
    }
}

/** Campaigns row: kitni chal rahi + kharcha. Tap = isi screen ka campaigns page. */
@Composable
private fun AppLinks(d: DashData, pkg: String, openCamps: () -> Unit) {
    val camps = appCamps(d, pkg)
    val running = camps.count { it.status.equals("ENABLED", true) }
    val accounts = camps.map { it.account }.filter { it.isNotBlank() }.distinct()
    KCard(list = true) {
        KRow(
            "Campaigns".t(),
            sub = tf("%s running", running) + when (accounts.size) {
                0 -> ""
                1 -> " · " + accounts[0]
                else -> " · " + tf("%s accounts", accounts.size)
            },
            lead = { KIconTile(KIcons.Ads) },
            value = inr(camps.sumOf { it.cost }), valuePlain = true,
            chevron = true, onClick = openCamps,
        )
    }
}

/** Is app ki campaigns (pkg khaali = account-level, wo bhi). */
private fun appCamps(d: DashData, pkg: String): List<Campaign> = d.campaigns.filter { it.pkg == pkg || it.pkg.isBlank() }

// ---------------------------------------------------------------- purane hisse (Show filter)

@Composable
private fun AppSections(st: UiState, nav: NavHostController, pkg: String, d: DashData, all: DashData?, section: String, med: String) {
    fun show(s: String) = section.isBlank() || section == s
    val c = Mfc.c
    val k = d.kpi
    val r = d.r
    val ex2 = st.appExtra[pkg]
    val ax = ex2?.active
    if (show("users")) {
        KSection("Users".t())
        TileGrid(
            listOf(
                Triple("${ex2?.liveMinutes ?: 6} min", num(d.liveTotal), "now".t()), Triple("30 min", num(d.live30), "active".t()),
                Triple("1 hour".t(), num(ax?.live1h), if (ax == null) "app.php" else "active".t()),
                Triple("DAU", num(k.dau), num(k.sessions) + " " + "sessions".t()), Triple("WAU", num(ax?.wau), "7 days".t()),
                Triple("MAU", num(ax?.mau), if (ax?.dauMau == null) "30 days".t() else "DAU/MAU " + pct(ax.dauMau, 0)),
                Triple("Avg session".t(), minutes(k.avgSessionSec), null), Triple("Installs".t(), num(k.installs), "first open".t()),
                Triple("Uninstalls".t(), num(k.uninstalls), "FCM"),
            ),
            colors = mapOf(0 to c.green),
        )
        if (ex2 != null) {
            KSection("Retention".t(), trailing = { DashText("D1 / D3 / D7 / D14", 13.sp, c.ink3) })
            KCard { RetentionTable(ex2) }
        }
    }
    if (show("money")) {
        if (ex2 != null) {
            KSection("ROAS cohort".t())
            KCard {
                RoasCohortTable(ex2.campaignRoas, all, st.roasTarget) { cr ->
                    val idx = all?.campaigns?.indexOfFirst { it.campaign.equals(cr.campaign, true) } ?: -1
                    if (idx >= 0) nav.navigate("campaign/$idx")
                }
            }
        }
        KSection("Money".t())
        val cpi = if (k.gadsInstalls > 0) k.gadsCostInr / k.gadsInstalls else null
        TileGrid(
            listOf(
                Triple("Ad revenue".t(), inr(k.revInr), usd(k.revUsd)),
                Triple("Payments".t(), inr(k.purchaseInr), num(k.purchases) + " · " + num(k.purchaseFailed) + " " + "failed".t()),
                Triple("Cost / conv".t(), inr(cpi, 2), num(k.gadsInstalls, 1) + " " + "conv".t()),
                Triple("Profit / install".t(), inr(if (k.installs > 0) k.profitInr / k.installs else null, 2), null),
                Triple("Ads users rev".t(), inr(k.revFromGadsUsersInr), null), Triple("eCPM", usd(k.ecpmUsd), inr(k.ecpmUsd * r)),
                Triple("ARPDAU", inr(k.arpdauUsd * r, 2), null), Triple("Impr / DAU".t(), num(k.impsPerDau, 1), num(k.imps) + " " + "impr".t()),
                Triple("Fill".t(), pct(d.fill), null),
            ),
            colors = if (k.installs > 0) mapOf(3 to if (k.profitInr >= 0) c.green else c.red) else emptyMap(),
        )
        KSection(if (d.seriesKind == "day") "Daily".t() else "Hourly".t())
        KCard {
            BarLineChart(d.series.map { it.label }, d.series.map { it.revUsd * r }, d.series.map { it.gadsCostInr }, d.series.map { it.installs })
            Legend("Revenue ₹".t() to c.brand, "Ad spend ₹".t() to c.zAmber, "Installs".t() to c.zGreen)
        }
        KSection("Payments".t(), trailing = {
            DashText(num(k.purchases) + " " + "paid".t() + " · " + num(k.purchaseFailed) + " " + "failed".t(), 13.sp, c.ink3)
        })
        val ps = d.purchases.sortedByDescending { it.amt ?: 0.0 }
        if (ps.isEmpty()) KCard { KEmpty("No payments".t(), KIcons.Card) }
        else KCard(list = true) {
            ps.forEachIndexed { i, p ->
                if (i > 0) KListDivider()
                val kind = when (p.ev) {
                    "purchase_done" -> KKind.OK
                    "purchase_failed" -> KKind.BAD
                    else -> KKind.NEUTRAL
                }
                KRow(p.plan, sub = p.statusL, subKind = kind, value = if (p.ev == "purchase_failed") "–" else inr(p.amt), valueSub = "× " + num(p.n))
            }
        }
    }
    if (show("ads")) {
        val fmts = d.byFormat.filter { med.isBlank() || it.med == med }
        val nets = d.byNetwork.filter { med.isBlank() || it.med == med }
        val units = d.byUnit.filter { med.isBlank() || it.med == med }
        val fails = d.failReasons.filter { med.isBlank() || it.med == med }
        KSection("Ads: format".t())
        KCard {
            Table(
                listOf("Format".t(), "Via".t(), "Impr".t(), "Revenue".t(), "eCPM", "Requests".t(), "Fill".t(), "CTR"),
                fmts.sortedByDescending { it.rev }.map { listOf(it.fmt, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm), num(it.adRequest), pct(it.fill), pct(it.ctr)) },
                empty = "No ad data".t(),
            )
        }
        KSection("Ads: network".t())
        KCard {
            Table(
                listOf("Network".t(), "Via".t(), "Impr".t(), "Revenue".t(), "eCPM"),
                nets.sortedByDescending { it.rev }.map { listOf(it.net, it.med, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm)) },
                empty = "No impressions".t(),
            )
        }
        KSection("Ad units".t())
        KCard {
            Table(
                listOf("Ad unit".t(), "Format".t(), "Impr".t(), "Revenue".t(), "eCPM"),
                units.sortedByDescending { it.rev }.map { listOf(it.unit, it.fmt, num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) },
                empty = "No impressions".t(),
            )
        }
        KSection("Ad failure reasons".t())
        KCard {
            Table(
                listOf("Format".t(), "Via".t(), "Reason".t(), "Count".t()),
                fails.sortedByDescending { it.n }.map { listOf(it.fmt, it.med, it.reason, num(it.n)) },
                empty = "No failures".t(),
            )
        }
    }
    if (show("promo")) {
        val cp = st.appCp[pkg]
        val shown = cp?.rows?.filter { it.src == pkg } ?: emptyList()
        val into = cp?.rows?.filter { it.tgt == pkg } ?: emptyList()
        KSection("Promos shown here".t())
        KCard {
            Table(
                listOf("Promoted app".t(), "Type".t(), "Views".t(), "Clicks".t(), "CTR", "Installs".t()),
                shown.map { listOf(all?.appName(it.tgt) ?: it.tgt, cpFmtName(it.fmt), num(it.views), num(it.clicks), pct(it.ctr), num(it.installs)) },
                empty = "No promo shown".t(),
            )
        }
        KSection("Installs from promos".t())
        KCard {
            Table(
                listOf("Shown in".t(), "Type".t(), "Views".t(), "Clicks".t(), "Installs".t(), "Inst/click".t()),
                into.map { listOf(all?.appName(it.src) ?: it.src, cpFmtName(it.fmt), num(it.views), num(it.clicks), num(it.installs), pct(it.installPerClick)) },
                empty = "No promo installs".t(),
            )
        }
    }
    if (show("more")) {
        KSection("Install source".t())
        KCard {
            Table(
                listOf("Source".t(), "Installs".t(), "Users".t(), "Revenue".t()),
                d.bySource.sortedByDescending { it.installs }.map { listOf(it.src.ifBlank { "(none)" }, num(it.installs), num(it.users), inr(it.revUsd * r, 1)) },
                empty = "No installs".t(),
            )
        }
        KSection("Countries".t())
        KCard {
            Table(
                listOf("CC", "Installs".t(), "Users".t(), "Impr".t(), "Revenue".t(), "eCPM"),
                d.byCountry.sortedByDescending { it.rev }.map { listOf(it.cc, num(it.installs), num(it.users), num(it.imps), inr(it.rev * r, 1), usd(it.ecpm())) },
                empty = "No data".t(),
            )
        }
        KSection("Crashes".t(), trailing = { KPill(num(k.crashes), if (k.crashes > 0) KKind.BAD else KKind.OK) })
        KCard {
            Table(
                listOf("Version".t(), "Error".t(), "Where".t(), "Count".t()),
                d.crashes.sortedByDescending { it.n }.map { listOf(it.ver, it.error, it.where, num(it.n)) },
                empty = "No crashes".t(),
            )
        }
        KSection("Versions".t())
        KCard {
            Table(
                listOf("Version".t(), "Users".t()),
                d.versions.sortedByDescending { it.users }.map { listOf(it.ver, num(it.users)) },
                empty = "No data".t(),
            )
        }
        KSection("Daily history".t())
        KCard {
            Table(
                listOf("Date".t(), "Installs".t(), "DAU", "Revenue".t(), "Spend".t(), "Profit".t()),
                d.history.sortedByDescending { it.day }.map { listOf(fmtDay(it.day), num(it.installs), num(it.dau), inr(it.revUsd * r), inr(it.gadsCostInr), inr(it.profit(r))) },
                empty = "No data yet".t(),
            )
        }
    }
}

// ---------------------------------------------------------------- campaigns page

/** Is app ki Google Ads campaigns (kharche se), tap = CampaignScreen. Neeche ROAS cohort. */
@Composable
private fun AppCampaigns(vm: DashVm, st: UiState, nav: NavHostController, pkg: String, name: String, back: () -> Unit) {
    val d = st.appData[pkg]
    val loading = st.appLoading == pkg
    KScreen(top = {
        KDetailBar("Campaigns".t(), back, sub = name, lead = { KAvatar(name, size = 36.dp) }, actions = {
            KBarIcon(KIcons.Refresh, "Refresh".t(), { vm.loadApp(pkg, force = true) }, spinning = loading)
        })
    }) {
        if (d == null) {
            DashNoData(st, loading || st.loading) { vm.loadApp(pkg, force = true) }
            return@KScreen
        }
        val camps = appCamps(d, pkg).sortedByDescending { it.cost }
        val list: @Composable ColumnScope.() -> Unit = {
            KSection("Google Ads campaigns".t(), trailing = { KPill(inr(camps.sumOf { it.cost }), KKind.NEUTRAL, large = true) })
            if (camps.isEmpty()) KCard { KEmpty("No campaigns".t(), KIcons.Ads) }
            else KCard(list = true) {
                camps.forEachIndexed { i, c ->
                    if (i > 0) KListDivider()
                    AppCampRow(c) {
                        val idx = dashCampaignIdx(st, c)
                        if (idx >= 0) nav.navigate("campaign/$idx")
                    }
                }
            }
        }
        val cohort: @Composable ColumnScope.() -> Unit = {
            val ex2 = st.appExtra[pkg]
            KSection("ROAS cohort".t())
            if (ex2 == null) ExtraMissingCard("Campaign-wise ROAS cohort".t())
            else KCard {
                RoasCohortTable(ex2.campaignRoas, st.data ?: d, st.roasTarget) { cr ->
                    val idx = st.data?.campaigns?.indexOfFirst { it.campaign.equals(cr.campaign, true) } ?: -1
                    if (idx >= 0) nav.navigate("campaign/$idx")
                }
            }
        }
        if (isWide()) KTwoPane(left = list, right = cohort) else { list(); cohort() }
    }
}

/** Campaign row: naam, status (shabd + shape + rang) · account, kharcha, conv + cost/conv. */
@Composable
private fun AppCampRow(c: Campaign, onClick: () -> Unit) {
    val enabled = c.status.equals("ENABLED", true)
    val (word, kind) = when {
        c.cost > 0 && c.conversions == 0.0 -> "0 conv".t() to KKind.BAD
        enabled -> "Running".t() to KKind.OK
        else -> "Paused".t() to KKind.NEUTRAL
    }
    KRow(
        c.campaign, sub = if (c.account.isBlank()) word else word + " · " + c.account, subKind = kind,
        value = inr(c.cost), valueSub = tf("%s conv", num(c.conversions, 1)) + (c.cpi?.let { " · " + inr(it, 1) } ?: ""),
        chevron = true, onClick = onClick,
    )
}
