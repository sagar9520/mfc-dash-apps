package com.myfuturecode.mfcdash.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.data.PageLoad
import com.myfuturecode.mfcdash.notify.Notify
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBottomNav
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KNavItem
import com.myfuturecode.mfcdash.ui.kit.KNum
import com.myfuturecode.mfcdash.ui.kit.KPageLoadBar
import com.myfuturecode.mfcdash.ui.kit.KTop
import com.myfuturecode.mfcdash.ui.screens.AdsScreen
import com.myfuturecode.mfcdash.ui.screens.AlertsScreen
import com.myfuturecode.mfcdash.ui.screens.AppDetailScreen
import com.myfuturecode.mfcdash.ui.screens.AppsScreen
import com.myfuturecode.mfcdash.ui.screens.CampaignScreen
import com.myfuturecode.mfcdash.ui.screens.CrossPromoScreen
import com.myfuturecode.mfcdash.ui.screens.PaymentsAppScreen
import com.myfuturecode.mfcdash.ui.screens.PaymentsScreen
import com.myfuturecode.mfcdash.ui.screens.PromoAppScreen
import com.myfuturecode.mfcdash.ui.screens.HomeScreen
import com.myfuturecode.mfcdash.ui.screens.LiveScreen
import com.myfuturecode.mfcdash.ui.screens.LoginScreen
import com.myfuturecode.mfcdash.ui.screens.MoneyScreen
import com.myfuturecode.mfcdash.ui.screens.SettingsScreen
import com.myfuturecode.mfcdash.ui.screens.UsersScreen
import com.myfuturecode.mfcdash.ui.screens.WidgetsScreen
import com.myfuturecode.mfcdash.ui.screens.cost.CostScreen
import com.myfuturecode.mfcdash.ui.screens.gads.GadsAccountScreen
import com.myfuturecode.mfcdash.ui.screens.gads.GadsAlertScreen
import com.myfuturecode.mfcdash.ui.screens.gads.GadsCampaignScreen
import com.myfuturecode.mfcdash.ui.screens.gads.GadsRunScreen
import com.myfuturecode.mfcdash.ui.screens.gads.GadsScreen
import com.myfuturecode.mfcdash.ui.screens.health.HealthAppScreen
import com.myfuturecode.mfcdash.ui.screens.health.HealthIssueScreen
import com.myfuturecode.mfcdash.ui.screens.health.HealthRunScreen
import com.myfuturecode.mfcdash.ui.screens.health.HealthScreen
import com.myfuturecode.mfcdash.ui.screens.studio.StudioCompareScreen
import com.myfuturecode.mfcdash.ui.screens.studio.StudioOppScreen
import com.myfuturecode.mfcdash.ui.screens.studio.StudioProjectScreen
import com.myfuturecode.mfcdash.ui.screens.studio.StudioRunScreen
import com.myfuturecode.mfcdash.ui.screens.studio.StudioScreen
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf

/** Neeche ke 4 tab. Money / Users / Ads / Live / Promo ab detail screens hain (back ke saath). */
private val TAB_ROUTES = setOf("home", "apps", "alerts", "settings")

/** Aakhri page (back stack entry id) jis par loading line chali. Process bhar: Activity dobara bane to bhi yaad. */
private object NavSeen { var id: String? = null }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Root(vm: DashVm) {
    val st by vm.s.collectAsStateWithLifecycle()
    if (!st.loggedIn) { LoginScreen(vm); return }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val isTab = route in TAB_ROUTES
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(st.error) { st.error?.let { snack.showSnackbar(it) } }
    val ctx0 = LocalContext.current
    val hvm: HealthVm = viewModel(factory = HealthVm.factory(ctx0.applicationContext as App))
    LaunchedEffect(hvm) { hvm.msg.collect { snack.showSnackbar(it) } }
    val gvm: GadsVm = viewModel(factory = GadsVm.factory(ctx0.applicationContext as App))
    LaunchedEffect(gvm) { gvm.msg.collect { snack.showSnackbar(it) } }
    val svm: StudioVm = viewModel(factory = StudioVm.factory(ctx0.applicationContext as App))
    LaunchedEffect(svm) { svm.msg.collect { snack.showSnackbar(it) } }
    val cvm: CostVm = viewModel(factory = CostVm.factory(ctx0.applicationContext as App))
    LaunchedEffect(cvm) { cvm.msg.collect { snack.showSnackbar(it) } }

    // Android 13+: notification ki permission (ek baar poochho)
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notify.allowed(ctx)) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // Notification tap -> Alerts screen
    val pending by vm.pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val r = pending ?: return@LaunchedEffect
        if (r in TAB_ROUTES) nav.goTab(r) else nav.navigate(r) { launchSingleTop = true }
        vm.consumeRoute()
    }
    // Har naya page (tab badla, back, detail khuli, login ke baad pehli screen) = naya load: upar ki neeli line.
    // Wahi page (same entry id) dobara aaye to nahi: wahi tab dobara dabana, ya Activity dobara bani (phone ghumaya,
    // Fold khola / band kiya: nav wahi entry id ke saath lautata hai, isliye NavSeen process bhar yaad rakhta hai).
    LaunchedEffect(nav) {
        nav.currentBackStackEntryFlow.collect { e ->
            PageLoad.route = e.destination.route   // DashVm ka 20 s refresh line tabhi khole jab uski screen dikh rahi ho
            if (e.id != NavSeen.id) { NavSeen.id = e.id; PageLoad.start() }
        }
    }

    val c = Mfc.c
    val brand = Mfc.style == ThemeStyle.BRAND
    Box(Modifier.fillMaxSize().background(c.page)) {
        // status bar ke peeche: brand me gradient (upar ke band se juda), baaki me page rang
        val strip = Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
        Box(if (brand) strip.background(Mfc.gradient) else strip.background(c.page))
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = c.ink,
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                if (isTab) KBottomNav(
                    listOf(
                        KNavItem("home", "Home".t(), KIcons.Home),
                        KNavItem("apps", "Apps".t(), KIcons.Apps),
                        KNavItem("alerts", "Alerts".t(), KIcons.Bell, st.alerts?.unread ?: 0),
                        KNavItem("settings", "Settings".t(), KIcons.Gear),
                    ),
                    route,
                    { r -> nav.goTab(r) },
                )
            },
        ) { pad ->
            // status bar + bottom nav ki jagah yahin; screens ko dobara nahi deni
            Box(Modifier.padding(pad).consumeWindowInsets(pad)) { AppNav(nav, vm, st, hvm, gvm, svm, cvm) }
        }
        // browser jaisi neeli loading line: sabse upar (screen aur bottom nav ke bhi upar), status bar ke theek neeche
        KPageLoadBar(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars))
    }
}

/** Tab badlo: Home tak pop, har tab ki jagah yaad (saveState / restoreState). */
private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun AppNav(nav: NavHostController, vm: DashVm, st: UiState, hvm: HealthVm, gvm: GadsVm, svm: StudioVm, cvm: CostVm) {
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(vm, st, nav, hvm, gvm, svm) }
        composable("apps") { AppsScreen(vm, st, nav) }
        composable("money") { MoneyScreen(vm, st, nav) }
        composable("users") { UsersScreen(vm, st, nav) }
        composable("ads") { AdsScreen(vm, st, nav) }
        composable("live") { LiveScreen(vm, st, nav) }
        composable("promo") { CrossPromoScreen(vm, st, nav) }
        composable("promo/app/{pkg}", arguments = listOf(navArgument("pkg") { type = NavType.StringType })) {
            PromoAppScreen(vm, st, nav, it.arguments?.getString("pkg") ?: "")
        }
        composable("payments") { PaymentsScreen(vm, st, nav) }
        composable("payments/app/{pkg}", arguments = listOf(navArgument("pkg") { type = NavType.StringType })) {
            PaymentsAppScreen(vm, st, nav, it.arguments?.getString("pkg") ?: "")
        }
        composable("settings") { SettingsScreen(vm, nav) }
        composable("alerts") { AlertsScreen(vm, st, nav) }
        composable("widgets") { WidgetsScreen(nav) }
        composable("app/{pkg}", arguments = listOf(navArgument("pkg") { type = NavType.StringType })) {
            AppDetailScreen(vm, st, nav, it.arguments?.getString("pkg") ?: "")
        }
        // AI App Health & Auto Fix
        composable("health") { HealthScreen(hvm, nav, "overview") }
        composable("health/t/{tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType })) {
            HealthScreen(hvm, nav, it.arguments?.getString("tab") ?: "overview")
        }
        composable("health/issue/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            HealthIssueScreen(hvm, nav, it.arguments?.getLong("id") ?: 0L)
        }
        composable("health/app/{pkg}", arguments = listOf(navArgument("pkg") { type = NavType.StringType })) {
            HealthAppScreen(hvm, nav, it.arguments?.getString("pkg") ?: "")
        }
        composable("health/run/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            HealthRunScreen(hvm, nav, it.arguments?.getLong("id") ?: 0L)
        }
        // Google Ads AI Health Monitor (sirf monitoring)
        composable("gads") { GadsScreen(gvm, nav, "accounts") }
        composable("gads/t/{tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType })) {
            GadsScreen(gvm, nav, it.arguments?.getString("tab") ?: "accounts")
        }
        composable("gads/alert/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            GadsAlertScreen(gvm, nav, it.arguments?.getLong("id") ?: 0L)
        }
        composable("gads/account/{cid}", arguments = listOf(navArgument("cid") { type = NavType.StringType })) {
            GadsAccountScreen(gvm, nav, it.arguments?.getString("cid") ?: "")
        }
        composable("gads/campaign/{cid}/{cmp}", arguments = listOf(navArgument("cid") { type = NavType.StringType }, navArgument("cmp") { type = NavType.StringType })) {
            GadsCampaignScreen(gvm, nav, it.arguments?.getString("cid") ?: "", it.arguments?.getString("cmp") ?: "")
        }
        composable("gads/run/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            GadsRunScreen(gvm, nav, it.arguments?.getLong("id") ?: 0L)
        }
        // AI App Research Studio (research, ranking, approvals; kabhi khud publish nahi)
        composable("studio") { StudioScreen(svm, nav, "overview") }
        composable("studio/{tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType })) {
            StudioScreen(svm, nav, it.arguments?.getString("tab") ?: "overview")
        }
        composable("studio/opp/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            StudioOppScreen(svm, nav, it.arguments?.getLong("id") ?: 0L)
        }
        composable("studio/project/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            StudioProjectScreen(svm, nav, it.arguments?.getLong("id") ?: 0L)
        }
        composable("studio/run/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            StudioRunScreen(svm, nav, it.arguments?.getLong("id") ?: 0L)
        }
        // run detail ka "See findings": us discover run ki Daily discovery
        composable("studio/discovery/run/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            StudioScreen(svm, nav, "discovery", it.arguments?.getLong("id") ?: 0L)
        }
        composable("studio/compare/{ids}", arguments = listOf(navArgument("ids") { type = NavType.StringType })) {
            StudioCompareScreen(svm, nav, it.arguments?.getString("ids") ?: "")
        }
        // AI Cost & Usage Manager (kharcha, budget, limits; Settings se khulta hai)
        composable("cost") { CostScreen(cvm, nav, "overview") }
        composable("cost/t/{tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType })) {
            CostScreen(cvm, nav, it.arguments?.getString("tab") ?: "overview")
        }
        composable("campaign/{idx}", arguments = listOf(navArgument("idx") { type = NavType.IntType })) {
            CampaignScreen(vm, st, nav, it.arguments?.getInt("idx") ?: 0)
        }
    }
}

/**
 * Purani screens ka top bar, kit ke roop me (brand theme me gradient band par).
 * back = true: wapas + title + updated + refresh. Warna bada title + updated + refresh, alerts (badge), settings.
 */
@Composable
fun TopBar(title: String, vm: DashVm, st: UiState, nav: NavHostController, back: Boolean = false) {
    val d = st.data
    val sub = when {
        st.loading && d == null -> "Loading…".t()
        d == null -> "No data".t()
        else -> "Updated".t() + " " + agoText(st.lastOk) + (if (d.today) " · auto 20 sec" else "") +
            (if (d.aggLagSec > 300) " · " + tf("data %s min old", (d.aggLagSec / 60).toInt()) else "")
    }
    // Users / Ads / Live ab tab nahi: "back = false" par bhi wapas ka teer (is screen ka apna route)
    val own = (LocalLifecycleOwner.current as? NavBackStackEntry)?.destination?.route
    val showBack = back || (own != null && own !in TAB_ROUTES)
    KTop {
        if (showBack) {
            KDetailBar(title, { nav.popBackStack() }, sub = sub, actions = { RefreshIcon(vm, st) })
        } else {
            TabTitleBar(title, sub) {
                RefreshIcon(vm, st)
                val al = st.alerts
                KBarIcon(KIcons.Bell, "Alerts".t(), { nav.goTab("alerts") }, badge = al?.unread ?: 0, badgeUrgent = (al?.urgentOpen ?: 0) > 0)
                KBarIcon(KIcons.Gear, "Settings".t(), { nav.goTab("settings") })
            }
        }
    }
}

@Composable
private fun RefreshIcon(vm: DashVm, st: UiState) {
    KBarIcon(KIcons.Refresh, "Refresh".t(), { vm.refresh() }, spinning = st.loading)
}

/** KTabBar jaisa (22sp title) par neeche "Updated ..." line ke saath. */
@Composable
private fun TabTitleBar(title: String, sub: String?, actions: @Composable RowScope.() -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = KNum.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, letterSpacing = (-0.01).em),
            )
            if (!sub.isNullOrBlank()) Text(
                sub, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = KNum.copy(fontSize = 13.sp, lineHeight = 17.sp),
            )
        }
        actions()
    }
}
