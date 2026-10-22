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
import com.myfuturecode.mfcdash.util.LANGS
import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.t

val AI_MODELS = listOf("claude-opus-5-5" to "Opus 5.5 (best)", "claude-sonnet-5-5" to "Sonnet 5.5", "claude-haiku-5-5" to "Haiku 5.5 (cheap)")

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
        TopBar("Settings".t(), vm, st, nav, back = true)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            // ---------------------------------------------------------------- language
            SectionTitle("Language".t())
            Panel {
                ChipRow { LANGS.forEach { (code, label) -> MChip(label, Lang.code == code, { vm.setLang(code) }) } }
                Text("Applies to the whole app, alert texts and Claude AI explanations. Default: English.".t(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))

            SectionTitle("Account".t())
            Panel {
                KV("Dashboard", vm.baseUrl)
                KV("Username".t(), vm.username)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("Logout".t()) }
            }
            Spacer(Modifier.height(16.dp))

            // ---------------------------------------------------------------- live monitoring
            SectionTitle("24x7 live monitoring".t(), if (st.liveMonitor) "on".t() else "off".t())
            Panel {
                ToggleRow("⚡ " + "Always-on monitor".t(), "checks the server every minute, even with the app closed; a small fixed notification stays visible".t(), st.liveMonitor) { vm.setLiveMonitor(it) }
                Text("Off = check every 15–60 min via Android background work (see below). On = issue → notification within about a minute: which app, which account, which ad unit, what happened, loss estimate and fix.".t(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))

            // ---------------------------------------------------------------- notifications
            SectionTitle("Notifications".t(), if (st.alertsMissing) "needs alerts.php".t() else null)
            Panel {
                if (!Notify.allowed(ctx)) Text("⚠ No notification permission — turn it on in phone Settings > Apps > MFC > Notifications.".t(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                ToggleRow("🔴 " + "Urgent".t(), "sound + vibration: data stopped, crash, loss".t(), st.notifyUrgent) { vm.setNotify(3, it) }
                ToggleRow("🟠 " + "Watch".t(), "there is an issue, no fix needed right now".t(), st.notifyWarn) { vm.setNotify(2, it) }
                ToggleRow("🔵 " + "Info".t(), "morning brief, records, new campaign".t(), st.notifyInfo) { vm.setNotify(1, it) }
                Spacer(Modifier.height(8.dp))
                Text("Background check interval when the monitor is off".t(), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                ChipRow {
                    listOf(15, 30, 60).forEach { m -> MChip("$m min", st.checkMinutes == m, { vm.setCheckMinutes(m) }) }
                }
                Text("With the app open: every 20 sec. 15 min is Android's minimum; on Xiaomi/Oppo/Vivo set this app to \"No restrictions\" in phone Settings > Battery, otherwise background checks stop.".t(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.testNotification { msg = it ?: "Test alert sent — a notification should arrive".t() } }, modifier = Modifier.weight(1f), enabled = !st.alertsMissing) { Text("Test notification".t()) }
                    OutlinedButton(onClick = { nav.navigate("alerts") }, modifier = Modifier.weight(1f)) { Text("View alerts".t()) }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---------------------------------------------------------------- Claude AI
            SectionTitle("Claude AI", if (ai != null && ai.enabled) "on".t() + " · " + ai.model else "off".t())
            Panel {
                Text("The rules check all data by themselves. Claude AI adds: for every Urgent/Watch alert, what happened, the cause, the loss/benefit and what to do now, plus a daily 9 AM AI brief.".t(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (ai != null && ai.enabled) {
                    KV("Key".t(), ai.keyHint)
                    KV("Last AI reply".t(), if (ai.lastOk > 0) agoText(ai.lastOk * 1000) else "not yet".t())
                    if (ai.lastError != null) Text("Error: " + ai.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    label = { Text(if (aiOn) "New key (to replace)".t() else "Anthropic API key (sk-ant-…)") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide".t() else "Show".t()) } },
                )
                Spacer(Modifier.height(8.dp))
                Text("Model".t(), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                ChipRow { AI_MODELS.forEach { (id, l) -> MChip(l.t(), model == id, { model = id }) } }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setAiKey(key, model) { ok, m -> msg = m; if (ok) key = "" } }, modifier = Modifier.weight(1f),
                        enabled = !st.alertsBusy && !st.alertsMissing && (key.isNotBlank() || aiOn)) {
                        if (st.alertsBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(if (key.isBlank()) "Save model".t() else "Save + test".t())
                    }
                    if (aiOn) OutlinedButton(onClick = { vm.setAiKey("", model) { _, m -> msg = m } }, enabled = !st.alertsBusy) { Text("Turn AI off".t()) }
                }
                Text("Key: console.anthropic.com > API Keys. Cost is small: one alert ≈ ₹1, the daily brief ≈ ₹4 (Opus 5.5). The key stays in the server DB (kv table), not on the phone.".t(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            msg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))

            SectionTitle("ROAS target".t(), "marker on the Home profit bar".t())
            Panel {
                Text("Target".t() + ": " + num(target * 100.0, 0) + "%", style = MaterialTheme.typography.titleMedium)
                Slider(value = target, onValueChange = { target = it }, onValueChangeFinished = { vm.setRoasTarget(target.toDouble()) }, valueRange = 1f..4f, steps = 11)
                Text("100% = break-even (ad revenue + payments = ad spend). Per research, scale a campaign only above 150%.".t(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("App".t())
            Panel {
                KV("Version".t(), BuildConfig.VERSION_NAME)
                KV("Data source".t(), "api/data.php · crosspromo.php · app.php · alerts.php")
                KV("Auto refresh".t(), "Every 20 sec (when the period includes today)".t())
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
