package com.myfuturecode.mfcdash.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.BuildConfig
import com.myfuturecode.mfcdash.notify.Notify
import com.myfuturecode.mfcdash.ui.CostVm
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KKv
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KNum
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KProgress
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSlider
import com.myfuturecode.mfcdash.ui.kit.KSmallAction
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KSwitch
import com.myfuturecode.mfcdash.ui.kit.KTabBar
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.word
import com.myfuturecode.mfcdash.ui.kit.zone
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.ui.theme.mfcColors
import com.myfuturecode.mfcdash.util.LANGS
import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.l
import com.myfuturecode.mfcdash.util.mapObj
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.widget.WidgetPins
import org.json.JSONObject
import kotlin.math.roundToInt

val AI_MODELS = listOf("claude-opus-5-5" to "Opus 5.5 (best)", "claude-sonnet-5-5" to "Sonnet 5.5", "claude-haiku-5-5" to "Haiku 5.5 (cheap)")

private val SET_MINUTES = listOf(15, 30, 60)

// ROAS target slider: 50% se 400% (purana 100-400% bhi isme), 10% ke kadam
private const val SET_ROAS_MIN = .5f
private const val SET_ROAS_MAX = 4f

/**
 * Settings tab: upar AI Kharcha (mahine ka kharcha / budget, tap = AI Cost screen), phir rows:
 * Claude AI, Bhasha, ROAS target, Rang (Light / Dark / Brand), Widgets | 24x7 monitor, Notifications | account, version.
 * Row tap = wahin khulta hai (ek baar me ek).
 */
@Composable
fun SettingsScreen(vm: DashVm, nav: NavHostController) {
    val st by vm.s.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    // Root wala CostVm hi (activity ka store): ek hi data, ek hi snackbar
    val owner: ViewModelStoreOwner = remember(ctx) { ctx.setActivity() } ?: checkNotNull(LocalViewModelStoreOwner.current)
    val cvm: CostVm = viewModel(viewModelStoreOwner = owner, factory = CostVm.factory(app))
    val g by cvm.s.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { cvm.load("month") }
    var open by rememberSaveable { mutableStateOf("") }       // khula hissa: ai | lang | roas | notif
    var info by rememberSaveable { mutableStateOf(false) }
    var placed by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) { placed = runCatching { WidgetPins.placedCount(ctx) }.getOrNull() }
    val flip: (String) -> Unit = { k -> open = if (open == k) "" else k }
    val m = g.view("month")
    val toCost: () -> Unit = { nav.navigate("cost") }
    val wide = isWide()

    KScreen(top = {
        KTabBar("Settings".t())
        if (!wide) SetCostHero(m, g, toCost)
    }) {
        if (wide) {
            // Fold: baayein AI Kharcha + main rows, daayein monitor / account
            KTwoPane(
                left = {
                    SetCostHero(m, g, toCost)
                    SetMainGroup(vm, st, open, flip, placed) { nav.navigate("widgets") }
                },
                right = {
                    SetMonitorGroup(vm, st, open, flip) { setGoTab(nav, "alerts") }
                    SetAccount(vm)
                    SetVersion(info) { info = !info }
                },
            )
        } else {
            SetMainGroup(vm, st, open, flip, placed) { nav.navigate("widgets") }
            SetMonitorGroup(vm, st, open, flip) { setGoTab(nav, "alerts") }
            SetAccount(vm)
            SetVersion(info) { info = !info }
        }
    }
}

// ---------------------------------------------------------------- AI Kharcha hero

/** Hero: is mahine ka AI kharcha / budget, % aur aaj; ruka module ho to neeche. Tap = AI Cost screen. */
@Composable
private fun SetCostHero(m: JSONObject?, g: GState, onOpen: () -> Unit) {
    KHero(KTint.Sand, onClick = onOpen) {
        val c = Mfc.c
        val p = m?.dn("pct")
        val paused = m?.arr("modules")?.mapObj { it }?.filter { it.b("paused") }.orEmpty()
        // koi module ruka = dhyan do (kul kharcha kam ho tab bhi)
        val k = setCostKind(p).let { if (it == KKind.OK && paused.isNotEmpty()) KKind.WARN else it }
        val pill: (@Composable () -> Unit)? = if (m != null && k != KKind.NEUTRAL) { { KPill(k.word(), k) } } else null
        KHeroHead("AI spend".t(), icon = KIcons.Spark, trailing = pill)
        if (m == null) {
            val err = g.error
            when {
                g.missing -> KStatus("Not set up".t(), KKind.NEUTRAL, Modifier.padding(top = 8.dp))
                err != null -> KStatus(err, KKind.BAD, Modifier.padding(top = 8.dp))
                else -> KBigNum("–")
            }
        } else {
            val r = m.d("inr_per_usd", 95.0)
            val budget = m.d("budget_usd")
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    KBigNum(inr(m.d("month_usd") * r), suffix = if (budget > 0) "/" + inr(budget * r) else null)
                }
                Column(Modifier.padding(bottom = 5.dp), horizontalAlignment = Alignment.End) {
                    SetText(if (p == null) "–" else pct(p, 0), 20.sp, c.ink, weight = FontWeight.Bold)
                    SetText(tf("Today %s", inr(m.d("today_usd") * r)), 13.sp, c.ink2)
                }
            }
            KProgress((p ?: 0.0).toFloat(), k.zone())
            if (paused.isNotEmpty()) SetPausedLine(paused)
        }
    }
}

/** Hero ki neeche wali patti: kitne module ruke + pehle ka naam · calls/cap. */
@Composable
private fun SetPausedLine(paused: List<JSONObject>) {
    val c = Mfc.c
    val x = paused.first()
    val cap = x.l("cap")
    val detail = x.s("name").t() + (if (cap > 0) " · " + x.l("calls_today") + "/" + cap else "")
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SetText(if (paused.size == 1) "1 module paused".t() else tf("%s modules paused", paused.size), 15.sp, c.ink, weight = FontWeight.SemiBold)
                KStatus(detail, KKind.WARN, Modifier.padding(top = 2.dp))
            }
            Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.size(22.dp))
        }
    }
}

/** Budget ka rang: 70% se dhyan do, 90% se turant (server ki warnings jaisa). */
private fun setCostKind(p: Double?): KKind = when {
    p == null || p.isNaN() -> KKind.NEUTRAL
    p >= 0.9 -> KKind.BAD
    p >= 0.7 -> KKind.WARN
    else -> KKind.OK
}

// ---------------------------------------------------------------- groups

/** Claude AI, Bhasha, ROAS target, Rang, Widgets. */
@Composable
private fun SetMainGroup(vm: DashVm, st: UiState, open: String, flip: (String) -> Unit, placed: Int?, onWidgets: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val ai = st.alerts?.ai
    val aiVal = if (ai != null && ai.enabled) "On".t() + " · " + setModelName(ai.model) else "Off".t()
    var target by remember(st.roasTarget) { mutableFloatStateOf(st.roasTarget.toFloat()) }
    KCard(list = true) {
        // ---- Claude AI
        KRow(
            "Claude AI", lead = { SetTile(KIcons.Spark) }, value = aiVal, valuePlain = true,
            trailing = { SetChev(open == "ai") }, onClick = { flip("ai") },
        )
        if (open == "ai") SetAiPanel(vm, st)
        // ---- Bhasha
        KListDivider(true)
        KRow(
            "Language".t(), lead = { SetTile(KIcons.Globe) }, value = LANGS.firstOrNull { it.first == Lang.code }?.second ?: Lang.code,
            valuePlain = true, trailing = { SetChev(open == "lang") }, onClick = { flip("lang") },
        )
        if (open == "lang") SetPanel {
            KSegTabs(LANGS.map { it.second }, LANGS.indexOfFirst { it.first == Lang.code }, { vm.setLang(LANGS[it].first) })
        }
        // ---- ROAS target (100% = break-even)
        KListDivider(true)
        KRow(
            "ROAS target".t(), lead = { SetTile(KIcons.Target) }, value = num(target * 100.0, 0) + "%",
            trailing = { SetChev(open == "roas") }, onClick = { flip("roas") },
        )
        if (open == "roas") SetPanel {
            KSlider(
                target.coerceIn(SET_ROAS_MIN, SET_ROAS_MAX), { target = it }, range = SET_ROAS_MIN..SET_ROAS_MAX, steps = 34,
                onDone = { vm.setRoasTarget((target * 10f).roundToInt() / 10.0) },
            )
            SetRoasTicks()
        }
        // ---- Rang: teen roop, tap = poori app turant
        KListDivider(true)
        KRow("Theme".t(), lead = { SetTile(KIcons.Palette) })
        val style = Mfc.style
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeStyle.entries.forEach { s -> SetThemeChip(s, s == style, Modifier.weight(1f)) { app.setTheme(s) } }
        }
        // ---- Widgets
        KListDivider(true)
        KRow(
            "Widgets".t(), lead = { SetTile(KIcons.Widgets) }, value = placed?.let { tf("%s placed", it) }, valuePlain = true,
            chevron = true, onClick = onWidgets,
        )
    }
}

/** Claude AI: key, model, save / band. */
@Composable
private fun SetAiPanel(vm: DashVm, st: UiState) {
    val c = Mfc.c
    val ai = st.alerts?.ai
    val aiOn = ai != null && ai.enabled
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var model by remember(ai?.model) { mutableStateOf(ai?.model?.takeIf { it.isNotBlank() } ?: AI_MODELS[0].first) }
    var msg by remember { mutableStateOf<String?>(null) }
    SetPanel {
        if (ai != null && ai.enabled) {
            KKv("Key".t(), ai.keyHint)
            KKv("Last AI reply".t(), if (ai.lastOk > 0) agoText(ai.lastOk * 1000) else "not yet".t())
            val err = ai.lastError
            if (err != null) SetText("Error".t() + ": " + err, 13.sp, c.red, maxLines = 4)
        }
        OutlinedTextField(
            value = key, onValueChange = { key = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(if (aiOn) "New key (to replace)".t() else "Anthropic API key (sk-ant-…)") },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showKey) "Hide".t() else "Show".t())
                }
            },
            shape = RoundedCornerShape(14.dp),
        )
        SetText("Model".t(), 13.sp, c.ink2, Modifier.padding(start = 4.dp, top = 2.dp))
        KSegTabs(AI_MODELS.map { it.second.substringBefore(" (") }, AI_MODELS.indexOfFirst { it.first == model }, { model = AI_MODELS[it].first })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton(
                if (key.isBlank()) "Save model".t() else "Save + test".t(),
                { vm.setAiKey(key, model) { ok, m -> msg = m; if (ok) key = "" } },
                Modifier.weight(1f),
                enabled = !st.alertsBusy && !st.alertsMissing && (key.isNotBlank() || aiOn),
                busy = st.alertsBusy,
            )
            if (aiOn) KButton(
                "Turn AI off".t(), { vm.setAiKey("", model) { _, m -> msg = m } }, Modifier.weight(1f),
                kind = KBtn.OUTLINE, enabled = !st.alertsBusy,
            )
        }
        msg?.let { SetText(it, 13.sp, c.brand, maxLines = 3) }
    }
}

/** 24x7 monitor, Notifications (urgent / dhyan do / info, check ka gap, test). */
@Composable
private fun SetMonitorGroup(vm: DashVm, st: UiState, open: String, flip: (String) -> Unit, onAlerts: () -> Unit) {
    val c = Mfc.c
    val ctx = LocalContext.current
    val allowed = Notify.allowed(ctx)
    var msg by remember { mutableStateOf<String?>(null) }
    val nOn = listOf(st.notifyUrgent, st.notifyWarn, st.notifyInfo).count { it }
    val sub = when {
        st.alertsMissing -> "needs alerts.php".t()
        !allowed -> "No permission".t()
        else -> null
    }
    KCard(list = true) {
        KRow(
            "24×7 monitor".t(), lead = { SetTile(KIcons.Shield) },
            trailing = { KSwitch(st.liveMonitor, { vm.setLiveMonitor(it) }) }, onClick = { vm.setLiveMonitor(!st.liveMonitor) },
        )
        KListDivider(true)
        KRow(
            "Notifications".t(), lead = { SetTile(KIcons.Bell) }, sub = sub, subKind = if (st.alertsMissing) KKind.WARN else KKind.BAD,
            value = tf("%s on", nOn), valuePlain = true, trailing = { SetChev(open == "notif") }, onClick = { flip("notif") },
        )
        if (open == "notif") {
            if (!allowed) {
                KListDivider(true)
                KRow(
                    "No permission".t(), lead = { KIconTile(KIcons.Turant, KKind.BAD, 36.dp) },
                    trailing = { KSmallAction("Allow".t(), { setOpenNotifSettings(ctx) }) },
                )
            }
            KListDivider(true)
            // Alerts tab wale hi shabd: Turant / Dhyan do / Info
            SetToggle(KIcons.Turant, KKind.BAD, KKind.BAD.word(), st.notifyUrgent) { vm.setNotify(3, it) }
            KListDivider(true)
            SetToggle(KIcons.Dhyan, KKind.WARN, KKind.WARN.word(), st.notifyWarn) { vm.setNotify(2, it) }
            KListDivider(true)
            SetToggle(KIcons.Info, KKind.NEUTRAL, "Info".t(), st.notifyInfo) { vm.setNotify(1, it) }
            KListDivider(true)
            SetPanel(top = 12.dp) {
                SetText("Check every".t(), 13.sp, c.ink2, Modifier.padding(start = 4.dp))
                KSegTabs(
                    SET_MINUTES.map { tf("%s min", it) }, SET_MINUTES.indexOf(st.checkMinutes),
                    { vm.setCheckMinutes(SET_MINUTES[it]) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KButton(
                        "Send test".t(),
                        { vm.testNotification { e -> msg = e ?: "Test alert sent — a notification should arrive".t() } },
                        Modifier.weight(1f), kind = KBtn.OUTLINE, enabled = !st.alertsMissing,
                    )
                    KButton("View alerts".t(), onAlerts, Modifier.weight(1f), kind = KBtn.SOFT)
                }
                msg?.let { SetText(it, 13.sp, c.brand, maxLines = 3) }
            }
        }
    }
}

/** Account: naam + Logout, server address. */
@Composable
private fun SetAccount(vm: DashVm) {
    val name = vm.username.ifBlank { "–" }
    KCard(list = true) {
        KRow(
            name, lead = { KAvatar(name, size = 36.dp, seed = 0, round = true) },
            trailing = { KButton("Logout".t(), { vm.logout() }, kind = KBtn.OUTLINE, icon = KIcons.Logout) },
        )
        KListDivider(true)
        KRow("Server".t(), lead = { SetTile(KIcons.Lock) }, sub = vm.baseUrl)
    }
}

/** "MFC 1.6.3" (tap = data source, auto refresh). */
@Composable
private fun SetVersion(open: Boolean, onToggle: () -> Unit) {
    val c = Mfc.c
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onToggle).heightIn(min = 48.dp).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        SetText("MFC " + BuildConfig.VERSION_NAME, 13.sp, c.ink3, align = TextAlign.Center)
        if (open) {
            SetText("Data source".t() + ": api/data.php · crosspromo.php · app.php · alerts.php", 13.sp, c.ink3, maxLines = 3, align = TextAlign.Center)
            SetText("Auto refresh".t() + ": " + "Every 20 sec (when the period includes today)".t(), 13.sp, c.ink3, maxLines = 3, align = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun SetText(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal, maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align,
        style = KNum.copy(fontSize = size, fontWeight = weight, lineHeight = (size.value * 1.38f).sp),
    )
}

/** Slider ke neeche: 50% | 100% break-even (thumb ki sahi jagah par) | 400%. */
@Composable
private fun SetRoasTicks() {
    val c = Mfc.c
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // KSlider ka thumb 24dp: track 12dp se (width - 12dp) tak
        val x = 12.dp + (maxWidth - 24.dp) * ((1f - SET_ROAS_MIN) / (SET_ROAS_MAX - SET_ROAS_MIN))
        SetText("50%", 12.sp, c.ink3, Modifier.align(Alignment.CenterStart))
        SetText("100% " + "break-even".t(), 12.sp, c.ink2, Modifier.align(Alignment.CenterStart).offset(x = x - 14.dp))
        SetText(num(SET_ROAS_MAX * 100.0, 0) + "%", 12.sp, c.ink3, Modifier.align(Alignment.CenterEnd))
    }
}

/** Row ke shuru ka chhota grey tile (36dp). */
@Composable
private fun SetTile(icon: ImageVector) = KIconTile(icon, KKind.NEUTRAL, 36.dp)

/** Khulne wali row ka teer (khula = ulta). */
@Composable
private fun SetChev(open: Boolean) {
    Icon(KIcons.ChevDown, null, tint = Mfc.c.chev, modifier = Modifier.size(22.dp).rotate(if (open) 180f else 0f))
}

/** Row ke neeche khula hissa (card ke andar, bina naye dibbe ke). */
@Composable
private fun SetPanel(top: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = top, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SetToggle(icon: ImageVector, kind: KKind, title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    KRow(title, lead = { KIconTile(icon, kind, 36.dp) }, trailing = { KSwitch(on, onChange) }, onClick = { onChange(!on) })
}

/** Rang ka chhota preview: page, upar ki patti (brand = gradient), card + button. Chuna hua = brand ring + tick (dibbe me dibba nahi). */
@Composable
private fun SetThemeChip(s: ThemeStyle, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Mfc.c
    val p = mfcColors(s)
    val mini = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).selectable(selected = on, role = Role.RadioButton, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().height(56.dp).clip(mini).background(p.page)
                    .border(if (on) 2.dp else 1.dp, if (on) c.brand else c.line, mini).padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val band = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp))
                Box(if (s == ThemeStyle.BRAND) band.background(Mfc.gradient) else band.background(p.card))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(p.card))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(p.btnBg))
                }
            }
            // chuna hua: preview ke kone me brand rang ka gol tick
            if (on) Box(
                Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(20.dp).clip(CircleShape).background(c.btnBg),
                contentAlignment = Alignment.Center,
            ) { Icon(KIcons.Check, null, tint = c.btnFg, modifier = Modifier.size(14.dp)) }
        }
        SetText(
            setThemeName(s), 13.sp, if (on) c.ink else c.ink2, Modifier.fillMaxWidth(),
            weight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 2, align = TextAlign.Center,
        )
    }
}

private fun setThemeName(s: ThemeStyle): String = when (s) {
    ThemeStyle.LIGHT -> "Light mode".t()
    ThemeStyle.DARK -> "Dark mode".t()
    else -> "Brand colour".t()
}

/** "claude-opus-5-5" -> "Opus 5.5". */
private fun setModelName(id: String): String =
    AI_MODELS.firstOrNull { it.first == id }?.second?.substringBefore(" (") ?: id.ifBlank { "–" }

/** Tab badlo (Root ke bottom nav jaisa). */
private fun setGoTab(nav: NavHostController, route: String) = nav.navigate(route) {
    popUpTo("home") { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Phone ki notification settings (is app ki). */
private fun setOpenNotifSettings(ctx: Context) {
    val i = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
    }
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Context se activity (ViewModel store ke liye). */
private tailrec fun Context.setActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.setActivity()
    else -> null
}
