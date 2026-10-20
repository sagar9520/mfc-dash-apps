package com.myfuturecode.mfcdash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.myfuturecode.mfcdash.ui.screens.*

data class Tab(val route: String, val label: String, val icon: ImageVector)
val TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("money", "Paisa", Icons.Filled.CurrencyRupee),
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

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (isTab) NavigationBar(tonalElevation = 0.dp) {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.navigate(t.route) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
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
                    st.loading && d == null -> "Load ho raha hai…"
                    d == null -> "Data nahi"
                    else -> "Updated " + com.myfuturecode.mfcdash.util.agoText(st.lastOk) + (if (d.today) " · auto 20 sec" else "") +
                        (if (d.aggLagSec > 300) " · data ${(d.aggLagSec / 60).toInt()} min purana" else "")
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = { if (back) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = {
            if (st.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Refresh") }
            if (!back) IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Settings") }
        },
    )
}
