package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.BuildConfig
import com.myfuturecode.mfcdash.notify.Notify
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.TopBar
import com.myfuturecode.mfcdash.ui.components.ChipRow
import com.myfuturecode.mfcdash.ui.components.KV
import com.myfuturecode.mfcdash.ui.components.MChip
import com.myfuturecode.mfcdash.ui.components.Panel
import com.myfuturecode.mfcdash.ui.components.SectionTitle
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.num

val AI_MODELS = listOf("claude-opus-5-5" to "Opus 5.5 (best)", "claude-sonnet-5-5" to "Sonnet 5.5", "claude-haiku-5-5" to "Haiku 5.5 (sasta)")

@Composable
fun SettingsScreen(vm: DashVm, nav: NavHostController) {
    val st by vm.s.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var target by remember { mutableFloatStateOf(st.roasTarget.toFloat()) }
    var msg by remember { mutableStateOf<String?>(null) }
    val ai = st.alerts?.ai
    val aiOn = ai != null && ai.enabled
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var model by remember(ai?.model) { mutableStateOf(ai?.model ?: AI_MODELS[0].first) }

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

            // ---------------------------------------------------------------- notifications
            SectionTitle("Notifications", if (st.alertsMissing) "alerts.php chahiye" else null)
            Panel {
                if (!Notify.allowed(ctx)) Text("⚠ Notification permission nahi hai — phone Settings > Apps > MFC Dashboard > Notifications on karo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                ToggleRow("🔴 Urgent", "awaaz + vibration: data band, crash, loss", st.notifyUrgent) { vm.setNotify(3, it) }
                ToggleRow("🟠 Dhyan do", "issue hai, abhi fix zaroori nahi", st.notifyWarn) { vm.setNotify(2, it) }
                ToggleRow("🔵 Info", "subah ka brief, records, nayi campaign", st.notifyInfo) { vm.setNotify(1, it) }
                Spacer(Modifier.height(8.dp))
                Text("App band ho tab kitni der me check", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                ChipRow {
                    listOf(15, 30, 60).forEach { m -> MChip("$m min", st.checkMinutes == m, { vm.setCheckMinutes(m) }) }
                }
                Text("App khuli ho to har 20 sec. 15 min Android ki minimum hai; Xiaomi/Oppo/Vivo par phone Settings > Battery me is app ko \"No restrictions\" do, warna background check ruk jaata hai.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.testNotification { msg = it ?: "Test alert bheja — notification aani chahiye" } }, modifier = Modifier.weight(1f), enabled = !st.alertsMissing) { Text("Test notification") }
                    OutlinedButton(onClick = { nav.navigate("alerts") }, modifier = Modifier.weight(1f)) { Text("Alerts dekho") }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---------------------------------------------------------------- Claude AI
            SectionTitle("Claude AI", if (ai != null && ai.enabled) "chalu · " + ai.model else "band")
            Panel {
                Text("Rules khud sab data check karte hain. Claude AI upar se: har Urgent/Dhyan-do alert par 3 line (kya hua, wajah, abhi kya karo) aur roz subah 9 baje kal ka AI brief.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (ai != null && ai.enabled) {
                    KV("Key", ai.keyHint)
                    KV("Aakhri AI jawab", if (ai.lastOk > 0) agoText(ai.lastOk * 1000) else "abhi nahi")
                    if (ai.lastError != null) Text("Error: " + ai.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    label = { Text(if (aiOn) "Nayi key (badalni ho to)" else "Anthropic API key (sk-ant-…)") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Chhupao" else "Dikhao") } },
                )
                Spacer(Modifier.height(8.dp))
                Text("Model", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                ChipRow { AI_MODELS.forEach { (id, l) -> MChip(l, model == id, { model = id }) } }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setAiKey(key, model) { msg = it; if (it?.contains("chalu") == true) key = "" } }, modifier = Modifier.weight(1f),
                        enabled = !st.alertsBusy && !st.alertsMissing && (key.isNotBlank() || aiOn)) {
                        if (st.alertsBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(if (key.isBlank()) "Model save" else "Save + test")
                    }
                    if (aiOn) OutlinedButton(onClick = { vm.setAiKey("", model) { msg = it } }, enabled = !st.alertsBusy) { Text("AI band") }
                }
                Text("Key: console.anthropic.com > API Keys. Kharcha chhota hai: ek alert ≈ ₹1, roz ka brief ≈ ₹4 (Opus 5.5). Key server ke DB (kv table) me rehti hai, phone me nahi.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            msg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
                KV("Data source", "api/data.php · crosspromo.php · app.php · alerts.php")
                KV("Auto refresh", "Har 20 sec (jab period mein aaj ho)")
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}
