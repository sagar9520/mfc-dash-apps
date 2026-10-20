package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.BuildConfig
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.TopBar
import com.myfuturecode.mfcdash.ui.components.KV
import com.myfuturecode.mfcdash.ui.components.Panel
import com.myfuturecode.mfcdash.ui.components.SectionTitle
import com.myfuturecode.mfcdash.util.num

@Composable
fun SettingsScreen(vm: DashVm, nav: NavHostController) {
    val st by vm.s.collectAsStateWithLifecycle()
    var target by remember { mutableFloatStateOf(st.roasTarget.toFloat()) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", vm, st, nav, back = true)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionTitle("Account")
            Panel {
                KV("Dashboard", vm.baseUrl)
                KV("Username", vm.username)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("ROAS target", "Home ke profit bar ka nishaan")
            Panel {
                Text("Target: " + num(target * 100.0, 0) + "%", style = MaterialTheme.typography.titleMedium)
                Slider(value = target, onValueChange = { target = it }, onValueChangeFinished = { vm.setRoasTarget(target.toDouble()) }, valueRange = 1f..4f, steps = 11)
                Text("100% = break-even (ad revenue + payments = ad spend). Research ke hisaab se 150% se upar hi campaign scale karo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("App")
            Panel {
                KV("Version", BuildConfig.VERSION_NAME)
                KV("Data source", "api/data.php + api/crosspromo.php")
                KV("Auto refresh", "Har 20 sec (jab period mein aaj ho)")
            }
        }
    }
}
