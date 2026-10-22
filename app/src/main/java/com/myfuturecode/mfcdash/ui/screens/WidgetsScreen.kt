package com.myfuturecode.mfcdash.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.R
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSkelCircle
import com.myfuturecode.mfcdash.ui.kit.KSkelLine
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KT
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kSide
import com.myfuturecode.mfcdash.ui.theme.LocalMfc
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.ui.theme.mfcColors
import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.widget.AdsSnap
import com.myfuturecode.mfcdash.widget.HealthSnap
import com.myfuturecode.mfcdash.widget.PaisaSnap
import com.myfuturecode.mfcdash.widget.WBrandBrush
import com.myfuturecode.mfcdash.widget.WFetch
import com.myfuturecode.mfcdash.widget.WKind
import com.myfuturecode.mfcdash.widget.WOpts
import com.myfuturecode.mfcdash.widget.WPreview
import com.myfuturecode.mfcdash.widget.WView
import com.myfuturecode.mfcdash.widget.WidgetData
import com.myfuturecode.mfcdash.widget.WidgetPins
import com.myfuturecode.mfcdash.widget.WidgetStore
import com.myfuturecode.mfcdash.widget.WidgetSync
import com.myfuturecode.mfcdash.widget.wEveryName
import com.myfuturecode.mfcdash.widget.wPal
import com.myfuturecode.mfcdash.widget.wRangeName
import com.myfuturecode.mfcdash.widget.wThemeName
import com.myfuturecode.mfcdash.widget.wTint
import com.myfuturecode.mfcdash.widget.wView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Widgets: chaaron home-screen widget ka preview (asli data), kitne lage hain, lagao / badlo.
 * Har widget ka layout fix hai; "Badlo" = samay (Paisa), apps / accounts / consoles, rang, kitni der me update.
 * Save = lage widgets bhi badlein.
 */
@Composable
fun WidgetsScreen(nav: NavHostController) {
    var edit by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = WKind.of(edit)
    if (kind != null) {
        BackHandler { edit = null }
        WConfig(kind, onClose = { edit = null })
    } else {
        WList(nav, onEdit = { edit = it.key })
    }
}

// ---------------------------------------------------------------- list

@Composable
private fun WList(nav: NavHostController, onEdit: (WKind) -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    val scope = rememberCoroutineScope()
    val rev by WidgetStore.rev.collectAsState()
    val counts = rememberWCounts()
    val canPin = remember { WidgetPins.canPin(ctx) }
    var snaps by remember { mutableStateOf<Map<WKind, JSONObject?>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }   // pehla load ho gaya (tab tak bina data = skeleton)

    // cache se turant (widget ne naya data laaya ho to bhi), phir purana ho to naya
    LaunchedEffect(rev) {
        snaps = withContext(Dispatchers.IO) {
            WKind.entries.associateWith { k -> WidgetStore.snap(app, WidgetStore.defaults(app, k).dataKey(k)) }
        }
    }
    LaunchedEffect(Unit) {
        WidgetSync.watchApp(app)
        busy = true
        snaps = wLoadSnaps(app, force = false)
        busy = false
        tried = true
    }
    val refresh: () -> Unit = {
        scope.launch {
            busy = true
            snaps = wLoadSnaps(app, force = true)
            busy = false
            WidgetSync.redraw(app)
        }
    }
    val item: @Composable (WKind) -> Unit = { k ->
        WListItem(k, snaps[k], counts[k] ?: 0, canPin, busy, wait = busy || !tried) { onEdit(k) }
    }

    KScreen(top = {
        KDetailBar(
            "Widgets".t(), { nav.popBackStack() },
            lead = { Image(painterResource(R.drawable.mfc_mark), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))) },
            actions = { KBarIcon(KIcons.Refresh, "Refresh".t(), refresh, spinning = busy) },
        )
    }) {
        if (isWide()) {
            // Fold: baayein Paisa + Health, daayein Ads + Live + Alerts
            KTwoPane(
                left = {
                    item(WKind.PAISA)
                    item(WKind.HEALTH)
                },
                right = {
                    item(WKind.ADS)
                    item(WKind.ALERTS)
                },
            )
        } else {
            WKind.entries.forEach { k -> item(k) }
        }
    }
}

@Composable
private fun WListItem(kind: WKind, snap: JSONObject?, count: Int, canPin: Boolean, busy: Boolean, wait: Boolean, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    val c = Mfc.c
    val rev by WidgetStore.rev.collectAsState()
    val o = remember(kind, rev) { WidgetStore.defaults(app, kind) }
    val v = rememberWView(kind, o, snap, busy)
    // data pehli baar aa raha = widget jaisa skeleton (fail / bina data = asli preview "—" ke saath)
    val skel = wait && snap == null && !v.login
    var failed by remember { mutableStateOf(false) }
    val desc = wDesc(kind)
    val pin: () -> Unit = { failed = !WidgetPins.pin(ctx, kind) }
    val status: @Composable () -> Unit = {
        if (count > 0) KPill(tf("%s placed", count), KKind.OK)
        else KPill("Not on home".t(), KKind.NEUTRAL, icon = KIcons.Plus)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!kind.wide) {
            // chhota (2x2) widget: baayein preview, daayein naam + haal + kya dikhta hai
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WPreviewOrSkel(v, skel, Modifier.size(W_SMALL.dp), onEdit)
                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KT(kind.title(), 16.sp, c.ink, weight = FontWeight.SemiBold)
                    status()
                    KT(desc, 13.sp, c.ink2, maxLines = 4, lh = 18.sp)
                }
            }
        } else {
            WPreviewOrSkel(v, skel, Modifier.fillMaxWidth().widthIn(max = W_WIDE_MAX.dp).height(W_SMALL.dp), onEdit)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KT(desc, 13.sp, c.ink2, Modifier.weight(1f), maxLines = 2, lh = 18.sp)
                status()
            }
        }
        WListButtons(count, canPin, onEdit = onEdit, onPin = pin)
        if (!canPin || failed) WPinHint()
    }
}

@Composable
private fun WPreviewOrSkel(v: WView, skel: Boolean, modifier: Modifier, onClick: (() -> Unit)? = null) {
    if (skel) WSkelPreview(v, modifier, onClick) else WPreview(v, modifier, onClick)
}

/**
 * Preview ka data pehli baar aa raha: wahi card (widget ka rang, kone, chhaya), andar us widget ke layout jaisa
 * skeleton. Blocks ka rang widget ke theme se (Brand = safed parda), app ke theme se nahi.
 */
@Composable
private fun WSkelPreview(v: WView, modifier: Modifier, onClick: (() -> Unit)?) {
    val p = remember(v.style) { wPal(v.style) }
    val shape = RoundedCornerShape(24.dp)
    var m = modifier.shadow(4.dp, shape).clip(shape)
    val tint = wTint(v)
    m = when {
        p.brand -> m.background(WBrandBrush)
        p.dark -> m.background(p.bg).border(1.dp, Color(0xFF2A2640), shape)
        tint != null -> m.background(Brush.verticalGradient(0f to tint.top, tint.stop to Color.White, 1f to Color.White))
        else -> m.background(p.bg)
    }
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    CompositionLocalProvider(LocalMfc provides mfcColors(v.style, top = p.brand)) {
        KSkeleton(m.padding(horizontal = if (v.kind == WKind.ADS) 14.dp else 16.dp, vertical = 14.dp)) {
            if (v.kind == WKind.ADS) {
                // do column: Ads kharcha | Live users, har ek me 3 lines + patli patti
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    repeat(2) { side ->
                        Column(Modifier.weight(1f)) {
                            KSkelLine(if (side == 0) .7f else .6f, 13.dp)
                            KSkelLine(.85f, 9.dp, Modifier.padding(top = 10.dp))
                            repeat(3) { i ->
                                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    KSkelLine(listOf(44.dp, 36.dp, 40.dp)[i], 13.dp)
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { KSkelLine(listOf(.6f, .5f, .7f)[i], 9.dp) }
                                }
                                KSkelLine(1f, 4.dp, Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                return@KSkeleton
            }
            // sir: naam + samay
            Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                KSkelLine(if (v.kind.wide) 120.dp else 84.dp, 13.dp)
                Box(Modifier.weight(1f))
                KSkelLine(32.dp, 10.dp)
            }
            when (v.kind) {
                WKind.PAISA -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) { KSkelLine(.55f, 30.dp) }
                        KSkelLine(64.dp, 20.dp)
                    }
                    KSkelLine(1f, 6.dp)
                    Box(Modifier.weight(1f))
                    // 4 khane: Kamai | Ads se | Payments | Ads kharcha
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(4) { i ->
                            Column(Modifier.weight(1f)) {
                                KSkelLine(listOf(.7f, .65f, .75f, .8f)[i], 9.dp)
                                KSkelLine(listOf(.85f, .8f, .5f, .85f)[i], 14.dp, Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                WKind.HEALTH -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) {
                            Column(Modifier.weight(1f)) {
                                KSkelLine(26.dp, 20.dp)
                                KSkelLine(30.dp, 9.dp, Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                    repeat(2) { i ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KSkelLine(22.dp, 16.dp)
                            KSkelLine(listOf(.7f, .55f)[i], 11.dp)
                        }
                    }
                }
                else -> repeat(3) { i ->
                    // Alerts: nishaan + app + kism
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KSkelCircle(14.dp)
                        Column(Modifier.weight(1f)) {
                            KSkelLine(listOf(.7f, .8f, .6f)[i], 11.dp)
                            KSkelLine(listOf(.45f, .5f, .38f)[i], 9.dp, Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Preview ka size: 2x2 = 172dp ka chaukor (mockup), 4x2 = poori chaudai (max 460) x 172. */
private const val W_SMALL = 172f
private const val W_WIDE_MAX = 460f

/** Widget me kya dikhta hai (ek line). */
private fun wDesc(kind: WKind): String = when (kind) {
    WKind.PAISA -> "Profit, ROAS, earnings, ads spend and live users".t()
    WKind.ADS -> "Top 3 ad accounts with budget, top 3 live apps".t()
    WKind.HEALTH -> "Apps with issues, top 3 by open issues".t()
    WKind.ALERTS -> "3 newest open alerts, most urgent first".t()
}

/** Pehli baar: "Home screen par lagao" + Badlo. Lag gaya: Badlo + "Ek aur lagao". */
@Composable
private fun WListButtons(count: Int, canPin: Boolean, onEdit: () -> Unit, onPin: () -> Unit) {
    val first = count == 0 && canPin
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (first) {
            KButton("Add to home screen".t(), onPin, Modifier.weight(1f), icon = KIcons.Home)
            KButton("Change".t(), onEdit, kind = KBtn.OUTLINE, icon = KIcons.Edit)
        } else {
            KButton("Change".t(), onEdit, Modifier.weight(1f), icon = KIcons.Edit)
            if (canPin) KButton("Add one more".t(), onPin, kind = KBtn.OUTLINE, icon = KIcons.Plus)
        }
    }
}

/** Launcher app se widget nahi lagane deta: haath se lagane ka raasta. */
@Composable
private fun WPinHint() {
    val c = Mfc.c
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(KIcons.Info, null, tint = c.brand, modifier = Modifier.size(18.dp))
        KT("Long-press home → Widgets → MFC".t(), 13.sp, c.ink2, maxLines = 2, lh = 18.sp)
    }
}

/** Har widget kitni baar laga hai (screen dikhte waqt har 2 second; launcher se lagte hi badle). */
@Composable
private fun rememberWCounts(): Map<WKind, Int> {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    var counts by remember { mutableStateOf(WKind.entries.associateWith { 0 }) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                counts = withContext(Dispatchers.IO) { WKind.entries.associateWith { k -> WidgetPins.count(app, k) } }
                delay(2_000)
            }
        }
    }
    return counts
}

@Composable
private fun rememberWView(kind: WKind, o: WOpts, snap: JSONObject?, busy: Boolean): WView {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    val style = Mfc.style
    val lang = Lang.code
    val login = remember { app.prefs.hasLogin }
    return remember(kind, o, snap, busy, style, lang) { wView(kind, o, snap, login, style.key, busy = busy) }
}

/** Chunne ki list (apps / accounts / consoles) jo snap ke saath aati hai. Alerts me koi nahi. */
private fun wChoices(kind: WKind, snap: JSONObject?): List<Pair<String, String>>? = when (kind) {
    WKind.PAISA -> PaisaSnap.of(snap)?.allApps
    WKind.ADS -> AdsSnap.of(snap)?.all
    WKind.HEALTH -> HealthSnap.of(snap)?.consoles
    WKind.ALERTS -> null
}

/**
 * Preview ka data: cache, 5 min se purana ho (ya force) to server se; mila to widgets bhi wahi dikhayenge.
 * lists = settings khuli hain: chunne ki list khaali ho to bhi naya laao. f = ek baar ka data.php sab me share.
 */
private suspend fun wPreviewSnap(
    app: App, kind: WKind, o: WOpts, force: Boolean, lists: Boolean = false, f: WFetch = WFetch(app),
): JSONObject? = withContext(Dispatchers.IO) {
    val key = o.dataKey(kind)
    val have = WidgetStore.snap(app, key)
    val noList = lists && kind.picks && wChoices(kind, have).isNullOrEmpty()
    val fresh = have != null && WidgetStore.age(app, key) < 5 * 60_000L && !noList
    if ((!force && fresh) || !app.prefs.hasLogin) return@withContext have
    try {
        val j = WidgetData.fetch(app, kind, o, f)
        WidgetStore.saveSnap(app, key, j)
        j
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        have
    }
}

private suspend fun wLoadSnaps(app: App, force: Boolean): Map<WKind, JSONObject?> {
    val f = WFetch(app) // Paisa (aaj) aur Ads + Live ek hi data.php se
    val out = LinkedHashMap<WKind, JSONObject?>()
    for (k in WKind.entries) out[k] = wPreviewSnap(app, k, WidgetStore.defaults(app, k), force, f = f)
    return out
}

// ---------------------------------------------------------------- set up (options)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WConfig(kind: WKind, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    var o by remember(kind) { mutableStateOf(WidgetStore.defaults(app, kind)) }
    var snap by remember { mutableStateOf<JSONObject?>(null) }
    var choices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }   // kis key ka load ho chuka (tab tak bina data = skeleton)
    var picker by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val counts = rememberWCounts()
    val placed = counts[kind] ?: 0
    val canPin = remember { WidgetPins.canPin(ctx) }
    val key = o.dataKey(kind)

    LaunchedEffect(key) {
        snap = withContext(Dispatchers.IO) { WidgetStore.snap(app, key) }
        busy = true
        snap = wPreviewSnap(app, kind, o, force = false, lists = true) ?: snap
        busy = false
        done = key
    }
    // apps / accounts / consoles ki list (khaali data se purani list na mite)
    LaunchedEffect(snap) {
        val l = wChoices(kind, snap)
        if (!l.isNullOrEmpty()) choices = l
    }
    val v = rememberWView(kind, o, snap, busy)

    val save: (Boolean) -> Unit = { pin ->
        WidgetStore.applyToAll(app, kind, o)
        WidgetSync.schedule(app)
        WidgetSync.redraw(app)
        WidgetSync.runNow(app, force = false)
        val ok = pin && WidgetPins.pin(ctx, kind)
        failed = pin && !ok
        if (!failed) onClose()
    }
    val pickTitle = when (kind) {
        WKind.PAISA -> "Which apps".t()
        WKind.ADS -> "Which accounts".t()
        else -> "Which consoles".t()
    }
    val allText = when (kind) {
        WKind.PAISA -> if (choices.isEmpty()) "All apps".t() else tf("All apps (%s)", choices.size)
        WKind.ADS -> if (choices.isEmpty()) "All accounts".t() else tf("All accounts (%s)", choices.size)
        else -> if (choices.isEmpty()) "All consoles".t() else tf("All consoles (%s)", choices.size)
    }
    val pickText = if (o.pick.isEmpty()) allText else tf("%s chosen", o.pick.size)
    val pickOne = when (kind) {
        WKind.PAISA -> "Pick apps".t()
        WKind.ADS -> "Pick accounts".t()
        else -> "Pick consoles".t()
    }

    // kya data: samay (Paisa) + kaun se apps / accounts / consoles. Alerts me kuch chunna nahi.
    val data: @Composable ColumnScope.() -> Unit = {
        if (kind == WKind.PAISA || kind.picks) {
            KCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (kind == WKind.PAISA) {
                        WLabel("Time range".t(), top = false)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WOpts.RANGES.forEach { r -> WChip(wRangeName(r), o.range == r) { o = o.copy(range = r) } }
                        }
                    }
                    if (kind.picks) {
                        WLabel(pickTitle, top = kind == WKind.PAISA)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WChip(allText, o.pick.isEmpty()) { o = o.copy(pick = emptyList()) }
                            WChip(if (o.pick.isEmpty()) pickOne else pickText, o.pick.isNotEmpty(), chev = true) { picker = true }
                        }
                    }
                }
            }
        }
    }
    // kaisa dikhe: rang + kitni der me update
    val look: @Composable ColumnScope.() -> Unit = {
        KCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WLabel("Widget colour".t(), top = false)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WOpts.THEMES.forEach { th ->
                        WChip(wThemeName(th), o.theme == th, swatch = wSwatch(th, Mfc.style)) { o = o.copy(theme = th) }
                    }
                }
                WLabel("Update every".t())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WOpts.EVERY.forEach { m -> WChip(wEveryName(m), o.every == m) { o = o.copy(every = m) } }
                }
            }
        }
    }
    val actions: @Composable ColumnScope.() -> Unit = {
        if (placed == 0 && canPin) {
            KButton("Save and add to home".t(), { save(true) }, Modifier.fillMaxWidth(), icon = KIcons.Home)
        } else {
            KButton("Save".t(), { save(false) }, Modifier.fillMaxWidth(), icon = KIcons.Check)
            if (canPin) KButton("Add one more".t(), { save(true) }, Modifier.fillMaxWidth(), kind = KBtn.OUTLINE, icon = KIcons.Plus)
        }
        if (!canPin || failed) WPinHint()
    }

    KScreen(top = {
        KDetailBar(tf("Set up %s widget", kind.shortTitle()), onClose)
        Box(Modifier.fillMaxWidth().padding(start = kSide(), end = kSide(), top = 4.dp, bottom = 16.dp)) {
            val m = if (kind.wide) Modifier.fillMaxWidth().widthIn(max = W_WIDE_MAX.dp).height(W_SMALL.dp) else Modifier.size(W_SMALL.dp)
            WPreviewOrSkel(v, snap == null && !v.login && (busy || done != key), m)
        }
    }) {
        KSection("Settings".t())
        if (isWide()) {
            KTwoPane(left = {
                data()
                look()
            }, right = actions)
        } else {
            data()
            look()
            actions()
        }
    }

    if (picker) {
        WPickDialog(pickTitle, allText, choices, o.pick, onDone = { sel ->
            o = o.copy(pick = sel)
            picker = false
        }, onClose = { picker = false })
    }
}

@Composable
private fun WLabel(text: String, top: Boolean = true) {
    KT(text, 14.sp, Mfc.c.ink2, if (top) Modifier.padding(top = 6.dp) else Modifier, weight = FontWeight.SemiBold)
}

/** Option chip (48dp): chuna = brand halka + check. chev = list kholta hai. */
@Composable
private fun WChip(text: String, on: Boolean, swatch: Brush? = null, chev: Boolean = false, onClick: () -> Unit) {
    val c = Mfc.c
    val shape = RoundedCornerShape(24.dp)
    var m = Modifier.heightIn(min = 48.dp).clip(shape).background(if (on) c.brandSoft else c.well)
    if (on) m = m.border(1.5.dp, c.brand, shape)
    Row(
        m.selectable(selected = on, role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (on) Icon(KIcons.Check, null, tint = c.brand, modifier = Modifier.size(16.dp))
        if (swatch != null) Box(Modifier.size(18.dp).clip(CircleShape).background(swatch).border(1.dp, c.line, CircleShape))
        KT(text, 14.sp, if (on) c.brand else c.ink, weight = FontWeight.SemiBold)
        if (chev) Icon(KIcons.Chev, null, tint = if (on) c.brand else c.chev, modifier = Modifier.size(18.dp))
    }
}

/** Rang chip ka gola: widget ka card rang (dark = widget ka #16131F). */
private fun wSwatch(theme: String, app: ThemeStyle): Brush = when (theme) {
    "light" -> Brush.linearGradient(listOf(Color.White, Color.White))
    "dark" -> Brush.linearGradient(listOf(Color(0xFF16131F), Color(0xFF16131F)))
    "brand" -> WBrandBrush
    else -> wSwatch(app.key, app)
}

/** Apps / accounts / consoles chuno. Kuch na chuna = sab. */
@Composable
private fun WPickDialog(
    title: String, allLabel: String, choices: List<Pair<String, String>>, picked: List<String>,
    onDone: (List<String>) -> Unit, onClose: () -> Unit,
) {
    var sel by remember { mutableStateOf(picked.toSet()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            KPageTokens {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    WCheckRow(allLabel, sel.isEmpty()) { sel = emptySet() }
                    choices.forEach { (id, name) ->
                        WCheckRow(name, id in sel) { sel = if (id in sel) sel - id else sel + id }
                    }
                    if (choices.isEmpty()) KEmpty("No data".t())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val out = if (choices.isEmpty()) sel.toList() else choices.map { it.first }.filter { it in sel }
                onDone(if (choices.isNotEmpty() && out.size == choices.size) emptyList() else out)
            }) { Text("Done".t()) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel".t()) } },
    )
}

@Composable
private fun WCheckRow(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = on, role = Role.Checkbox, onValueChange = { onClick() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (on) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(c.btnBg), contentAlignment = Alignment.Center) {
                Icon(KIcons.Check, null, tint = c.btnFg, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(Modifier.size(22.dp).border(1.5.dp, c.chev, RoundedCornerShape(6.dp)))
        }
        KT(text, 15.sp, c.ink, Modifier.weight(1f), maxLines = 2, lh = 20.sp)
    }
}
