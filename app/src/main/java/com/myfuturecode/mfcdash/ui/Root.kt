package com.myfuturecode.mfcdash.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myfuturecode.mfcdash.notify.Notify
import com.myfuturecode.mfcdash.ui.screens.*
import com.myfuturecode.mfcdash.util.t

data class Tab(val route: String, val label: String, val icon: ImageVector)
val TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("money", "Money", Icons.Filled.CurrencyRupee),
    Tab("users", "Users", Icons.Filled.Group),
    Tab("ads", "Ads", Icons.Filled.Campaign),
    Tab("live", "Live", Icons.Filled.Bolt),
)

@Composable
fun Root(vm: DashVm) {
    val st by vm.s.collectAsStateWithLifecycle()
    if (!st.loggedIn) { LoginScreen(vm); return }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val isTab = TABS.any { it.route == route }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(st.error) { st.error?.let { snack.showSnackbar(it) } }

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
        nav.navigate(r) { launchSingleTop = true }
        vm.consumeRoute()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (isTab) NavigationBar(tonalElevation = 0.dp) {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.navigate(t.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(t.icon, t.label.t()) }, label = { Text(t.label.t()) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) { AppNav(nav, vm, st) }
    }
}

@Composable
private fun AppNav(nav: NavHostController, vm: DashVm, st: UiState) {
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(vm, st, nav) }
        composable("money") { MoneyScreen(vm, st, nav) }
        composable("users") { UsersScreen(vm, st, nav) }
        composable("ads") { AdsScreen(vm, st, nav) }
        composable("live") { LiveScreen(vm, st, nav) }
        composable("promo") { CrossPromoScreen(vm, st, nav) }
        composable("settings") { SettingsScreen(vm, nav) }
        composable("alerts") { AlertsScreen(vm, st, nav) }
        composable("app/{pkg}", arguments = listOf(navArgument("pkg") { type = NavType.StringType })) {
            AppDetailScreen(vm, st, nav, it.arguments?.getString("pkg") ?: "")
        }
        composable("campaign/{idx}", arguments = listOf(navArgument("idx") { type = NavType.IntType })) {
            CampaignScreen(vm, st, nav, it.arguments?.getInt("idx") ?: 0)
        }
    }
}

/** Har tab ke upar: title, status, refresh, settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(title: String, vm: DashVm, st: UiState, nav: NavHostController, back: Boolean = false) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                val d = st.data
                val sub = when {
                    st.loading && d == null -> "Loading…".t()
                    d == null -> "No data".t()
                    else -> "Updated".t() + " " + com.myfuturecode.mfcdash.util.agoText(st.lastOk) + (if (d.today) " · auto 20 sec" else "") +
                        (if (d.aggLagSec > 300) " · " + com.myfuturecode.mfcdash.util.tf("data %s min old", (d.aggLagSec / 60).toInt()) else "")
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = { if (back) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = {
            if (st.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Refresh") }
            if (!back) {
                val al = st.alerts
                val n = al?.unread ?: 0
                val urgent = (al?.urgentOpen ?: 0) > 0
                IconButton(onClick = { nav.navigate("alerts") }) {
                    BadgedBox(badge = {
                        if (n > 0) Badge(containerColor = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) { Text(if (n > 99) "99+" else n.toString()) }
                    }) { Icon(Icons.Filled.Notifications, "Alerts") }
                }
                IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Settings") }
            }
        },
        // Scaffold already pads the status bar; without this the bar added it a second time (big empty gap on top).
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.background),
    )
}
