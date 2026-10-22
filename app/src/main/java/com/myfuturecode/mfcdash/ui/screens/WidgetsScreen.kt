package com.myfuturecode.mfcdash.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KT
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.icon
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kSide
import com.myfuturecode.mfcdash.ui.theme.Brand1
import com.myfuturecode.mfcdash.ui.theme.Brand2
import com.myfuturecode.mfcdash.ui.theme.Brand3
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.MfcAngleGradient
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.ui.theme.mfcColors
import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.widget.AdsSnap
import com.myfuturecode.mfcdash.widget.HealthSnap
import com.myfuturecode.mfcdash.widget.PaisaSnap
import com.myfuturecode.mfcdash.widget.WCell
import com.myfuturecode.mfcdash.widget.WKind
import com.myfuturecode.mfcdash.widget.WOpts
import com.myfuturecode.mfcdash.widget.WPal
import com.myfuturecode.mfcdash.widget.WPillM
import com.myfuturecode.mfcdash.widget.WView
import com.myfuturecode.mfcdash.widget.W_BAND
import com.myfuturecode.mfcdash.widget.W_COL_GAP
import com.myfuturecode.mfcdash.widget.W_GAP
import com.myfuturecode.mfcdash.widget.W_PAD
import com.myfuturecode.mfcdash.widget.W_TOP
import com.myfuturecode.mfcdash.widget.WidgetData
import com.myfuturecode.mfcdash.widget.WidgetPins
import com.myfuturecode.mfcdash.widget.WidgetStore
import com.myfuturecode.mfcdash.widget.WidgetSync
import com.myfuturecode.mfcdash.widget.wBandPlan
import com.myfuturecode.mfcdash.widget.wEveryName
import com.myfuturecode.mfcdash.widget.wGridPlan
import com.myfuturecode.mfcdash.widget.wHealthPlan
import com.myfuturecode.mfcdash.widget.wItemName
import com.myfuturecode.mfcdash.widget.wJoinFit
import com.myfuturecode.mfcdash.widget.wLineK
import com.myfuturecode.mfcdash.widget.wPal
import com.myfuturecode.mfcdash.widget.wPillFull
import com.myfuturecode.mfcdash.widget.wPillW
import com.myfuturecode.mfcdash.widget.wRangeName
import com.myfuturecode.mfcdash.widget.wShowSub
import com.myfuturecode.mfcdash.widget.wSmallH
import com.myfuturecode.mfcdash.widget.wThemeName
import com.myfuturecode.mfcdash.widget.wValueSp
import com.myfuturecode.mfcdash.widget.wView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Widgets: teeno home-screen widget ka preview (asli data), kitne lage hain, lagao / badlo.
 * "Badlo" = isi screen me options (kya dikhana, samay, apps, rang, kitni der me update). Save = lage widgets bhi badlein.
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
    }
    val refresh: () -> Unit = {
        scope.launch {
            busy = true
            snaps = wLoadSnaps(app, force = true)
            busy = false
            WidgetSync.redraw(app)
        }
    }

    KScreen(top = {
        KDetailBar(
            "Widgets".t(), { nav.popBackStack() },
            lead = { Image(painterResource(R.drawable.mfc_mark), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))) },
            actions = { KBarIcon(KIcons.Refresh, "Refresh".t(), refresh, spinning = busy) },
        )
    }) {
        if (isWide()) {
            KTwoPane(
                left = {
                    WListItem(WKind.PAISA, snaps[WKind.PAISA], counts[WKind.PAISA] ?: 0, canPin, busy) { onEdit(WKind.PAISA) }
                    WListItem(WKind.HEALTH, snaps[WKind.HEALTH], counts[WKind.HEALTH] ?: 0, canPin, busy) { onEdit(WKind.HEALTH) }
                },
                right = {
                    WListItem(WKind.ADS, snaps[WKind.ADS], counts[WKind.ADS] ?: 0, canPin, busy) { onEdit(WKind.ADS) }
                },
            )
        } else {
            WKind.entries.forEach { k ->
                WListItem(k, snaps[k], counts[k] ?: 0, canPin, busy) { onEdit(k) }
            }
        }
    }
}

@Composable
private fun WListItem(kind: WKind, snap: JSONObject?, count: Int, canPin: Boolean, busy: Boolean, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    val c = Mfc.c
    val rev by WidgetStore.rev.collectAsState()
    val o = remember(kind, rev) { WidgetStore.defaults(app, kind) }
    val v = rememberWView(kind, o, snap, busy)
    var failed by remember { mutableStateOf(false) }
    val names = wItemsText(kind, o)
    val pin: () -> Unit = { failed = !WidgetPins.pin(ctx, kind) }
    val status: @Composable () -> Unit = {
        if (count > 0) KPill(tf("%s placed", count), KKind.OK)
        else KPill("Not on home".t(), KKind.NEUTRAL, icon = KIcons.Plus)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (kind == WKind.HEALTH) {
            // chhota (2x2) widget: baayein preview, daayein haal + kya dikhta hai
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WPreview(v, Modifier.width(174.dp).height(168.dp), onEdit)
                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    status()
                    KT(names, 13.sp, c.ink2, maxLines = 4, lh = 18.sp)
                }
            }
        } else {
            WPreview(v, Modifier.fillMaxWidth().widthIn(max = 460.dp).height(168.dp), onEdit)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KT(names, 13.sp, c.ink2, Modifier.weight(1f))
                status()
            }
        }
        WListButtons(count, canPin, onEdit = onEdit, onPin = pin)
        if (!canPin || failed) WPinHint()
    }
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

/** "Today's profit, ROAS, Earnings, All ads spend" (Health: "Apps fine" pehle). */
private fun wItemsText(kind: WKind, o: WOpts): String {
    val items = (if (kind.fixedHero != null) listOf(kind.fixedHero) else emptyList()) + o.items
    return items.joinToString(", ") { wItemName(kind, it, o.range) }
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
    val target = remember { app.prefs.roasTarget }
    return remember(kind, o, snap, busy, style, lang) { wView(kind, o, snap, login, style.key, target, busy = busy) }
}

/** Preview ka data: cache, 5 min se purana ho (ya force) to server se; mila to widgets bhi wahi dikhayenge. */
private suspend fun wPreviewSnap(app: App, kind: WKind, o: WOpts, force: Boolean, lists: Boolean = false): JSONObject? =
    withContext(Dispatchers.IO) {
        val key = o.dataKey(kind)
        val have = WidgetStore.snap(app, key)
        val noList = lists && kind == WKind.HEALTH && HealthSnap.of(have)?.consoles.isNullOrEmpty()
        val fresh = have != null && WidgetStore.age(app, key) < 5 * 60_000L && !noList
        if ((!force && fresh) || !app.prefs.hasLogin) return@withContext have
        try {
            val j = WidgetData.fetch(app, kind, o, lists)
            WidgetStore.saveSnap(app, key, j)
            j
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            have
        }
    }

private suspend fun wLoadSnaps(app: App, force: Boolean): Map<WKind, JSONObject?> {
    val out = LinkedHashMap<WKind, JSONObject?>()
    for (k in WKind.entries) out[k] = wPreviewSnap(app, k, WidgetStore.defaults(app, k), force)
    return out
}

// ---------------------------------------------------------------- set up (options)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WConfig(kind: WKind, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    val c = Mfc.c
    var o by remember(kind) { mutableStateOf(WidgetStore.defaults(app, kind)) }
    var snap by remember { mutableStateOf<JSONObject?>(null) }
    var choices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var heroMenu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
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
    }
    // apps / accounts / consoles ki list (khaali data se purani list na mite)
    LaunchedEffect(snap) {
        val l = when (kind) {
            WKind.PAISA -> PaisaSnap.of(snap)?.allApps
            WKind.ADS -> AdsSnap.of(snap)?.all
            WKind.HEALTH -> HealthSnap.of(snap)?.consoles
        }
        if (!l.isNullOrEmpty()) choices = l
    }
    LaunchedEffect(full) {
        if (full) {
            delay(2_000)
            full = false
        }
    }
    val v = rememberWView(kind, o, snap, busy)

    val toggle: (String) -> Unit = { item ->
        val cur = o.items
        o = when {
            item in cur -> if (cur.size <= 1 && kind.fixedHero == null) o else o.copy(items = cur - item)
            cur.size >= kind.maxItems -> {
                full = true
                o
            }
            else -> o.copy(items = cur + item)
        }
    }
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
        WKind.HEALTH -> "Which consoles".t()
    }
    val allText = when (kind) {
        WKind.PAISA -> if (choices.isEmpty()) "All apps".t() else tf("All apps (%s)", choices.size)
        WKind.ADS -> if (choices.isEmpty()) "All accounts".t() else tf("All accounts (%s)", choices.size)
        WKind.HEALTH -> if (choices.isEmpty()) "All consoles".t() else tf("All consoles (%s)", choices.size)
    }
    val pickText = if (o.pick.isEmpty()) allText else tf("%s chosen", o.pick.size)
    val pickOne = when (kind) {
        WKind.PAISA -> "Pick apps".t()
        WKind.ADS -> "Pick accounts".t()
        WKind.HEALTH -> "Pick consoles".t()
    }
    val summary = listOfNotNull(
        if (kind == WKind.PAISA) wRangeName(o.range) else null,
        pickText, wThemeName(o.theme), wEveryName(o.every),
    ).joinToString(" · ")
    val first = if (kind.fixedHero != null) 2 else 1

    val what: @Composable ColumnScope.() -> Unit = {
        KCard {
            KSection("What to show".t(), trailing = {
                KT(tf("%s/%s chosen", o.items.size, kind.maxItems), 14.sp, c.ink2, weight = FontWeight.SemiBold)
            })
            Spacer(Modifier.height(6.dp))
            kind.catalog.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { item ->
                        val idx = o.items.indexOf(item)
                        WOptRow(
                            wItemName(kind, item, o.range), if (idx >= 0) idx + first else null, Modifier.weight(1f),
                        ) { toggle(item) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (full) KStatus(tf("Max %s", kind.maxItems), KKind.BAD, Modifier.padding(top = 6.dp))
        }
    }
    val settings: @Composable ColumnScope.() -> Unit = {
        KCard(list = true) {
            if (kind.fixedHero == null) {
                Box {
                    KRow(
                        "Big number".t(), sub = o.items.firstOrNull()?.let { wItemName(kind, it, o.range) },
                        lead = { WNumBadge(1) }, chevron = true, onClick = { heroMenu = true },
                    )
                    DropdownMenu(expanded = heroMenu, onDismissRequest = { heroMenu = false }) {
                        kind.catalog.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(wItemName(kind, item, o.range)) },
                                onClick = {
                                    heroMenu = false
                                    o = o.copy(items = (listOf(item) + o.items.filter { it != item }).take(kind.maxItems))
                                },
                            )
                        }
                    }
                }
            } else {
                KRow("Big number".t(), sub = wItemName(kind, kind.fixedHero, o.range), lead = { WNumBadge(1) })
            }
            KListDivider(lead = true)
            KRow(
                "More settings".t(), sub = summary, lead = { KIconTile(KIcons.Filter) },
                trailing = {
                    Icon(
                        KIcons.ChevDown, null, tint = c.chev,
                        modifier = Modifier.size(24.dp).rotate(if (more) 180f else 0f),
                    )
                },
                onClick = { more = !more },
            )
            if (more) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (kind == WKind.PAISA) {
                        WLabel("Time range".t())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            WOpts.RANGES.forEach { r -> WChip(wRangeName(r), o.range == r) { o = o.copy(range = r) } }
                        }
                    }
                    WLabel(pickTitle)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WChip(allText, o.pick.isEmpty()) { o = o.copy(pick = emptyList()) }
                        WChip(if (o.pick.isEmpty()) pickOne else pickText, o.pick.isNotEmpty(), chev = true) { picker = true }
                    }
                    WLabel("Widget colour".t())
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
            val m = if (kind == WKind.HEALTH) Modifier.width(174.dp) else Modifier.fillMaxWidth().widthIn(max = 460.dp)
            WPreview(v, m.height(168.dp))
        }
    }) {
        if (isWide()) {
            KTwoPane(left = what, right = {
                settings()
                actions()
            })
        } else {
            what()
            settings()
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
private fun WLabel(text: String) {
    KT(text, 14.sp, Mfc.c.ink2, Modifier.padding(top = 6.dp), weight = FontWeight.SemiBold)
}

/** Brand rang ka chhota number (1 = bada number). */
@Composable
private fun WNumBadge(n: Int) {
    val c = Mfc.c
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(c.btnBg),
        contentAlignment = Alignment.Center,
    ) {
        KT(n.toString(), 14.sp, c.btnFg, weight = FontWeight.Bold)
    }
}

/** "Kya dikhana hai" ki ek line: chuna = number, nahi = khaali dabba. 48dp. */
@Composable
private fun WOptRow(name: String, num: Int?, modifier: Modifier, onToggle: () -> Unit) {
    val c = Mfc.c
    val on = num != null
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (num != null) {
            WNumBadge(num)
        } else {
            val dash = c.chev
            Box(
                Modifier.size(28.dp).drawBehind {
                    val sw = 1.5.dp.toPx()
                    drawRoundRect(
                        color = dash, topLeft = Offset(sw / 2f, sw / 2f), size = Size(size.width - sw, size.height - sw),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                },
            )
        }
        KT(
            name, 15.sp, if (on) c.ink else c.ink2, Modifier.weight(1f),
            weight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, lh = 20.sp,
        )
    }
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

private val WBrandBrush: Brush = MfcAngleGradient(135f, listOf(0f to Brand1, .52f to Brand2, 1f to Brand3))

/** Rang chip ka gola: widget ka card rang. */
private fun wSwatch(theme: String, app: ThemeStyle): Brush = when (theme) {
    "light" -> Brush.linearGradient(listOf(Color.White, Color.White))
    "dark" -> Brush.linearGradient(listOf(Color(0xFF1A1728), Color(0xFF1A1728)))
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

// ---------------------------------------------------------------- preview (home screen wale widget jaisa)

@Composable
private fun WPreview(v: WView, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val p = remember(v.style, v.kind) { wPal(v.style, v.kind) }
    val bd = mfcColors(v.style).cardBd
    val shape = RoundedCornerShape(20.dp)
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    var m = modifier.shadow(4.dp, shape).clip(shape)
    m = if (p.brand) m.background(WBrandBrush) else m.background(p.bg)
    if (!p.brand && bd.alpha > 0f) m = m.border(1.dp, bd, shape)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    BoxWithConstraints(m) {
        val w = maxWidth.value
        val bodyH = maxHeight.value - W_BAND
        Column(Modifier.fillMaxSize()) {
            WpBand(v, p, w, scale)
            when {
                v.login -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    KT("Log in".t(), 18.sp, p.ink, weight = FontWeight.Bold)
                }
                v.kind == WKind.HEALTH -> WpHealth(v, p, w, bodyH, scale)
                else -> WpGrid(v, p, w, bodyH, scale)
            }
        }
    }
}

private fun wpGlyph(k: WKind): Int = when (k) {
    WKind.PAISA -> R.drawable.widget_glyph_paisa
    WKind.ADS -> R.drawable.widget_glyph_ads
    WKind.HEALTH -> R.drawable.widget_glyph_health
}

@Composable
private fun WpBand(v: WView, p: WPal, w: Float, scale: Float) {
    val plan = wBandPlan(v, w, scale)
    Row(
        Modifier.fillMaxWidth().height(W_BAND.dp).background(p.band).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (plan.mark) {
            Image(painterResource(R.drawable.mfc_mark), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Image(painterResource(wpGlyph(v.kind)), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        KT(v.title, 14.sp, p.ink, Modifier.weight(1f), weight = FontWeight.Medium)
        if (plan.refresh) {
            Row(
                Modifier.padding(start = if (plan.time) 10.dp else 17.dp, end = if (plan.time) 14.dp else 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(KIcons.Refresh, null, tint = p.ink2, modifier = Modifier.size(14.dp))
                if (plan.time) {
                    Spacer(Modifier.width(6.dp))
                    KT(v.time, 13.sp, if (v.stale) p.warn else p.ink2)
                }
            }
        } else {
            Spacer(Modifier.width(14.dp))
        }
    }
}

@Composable
private fun WpGrid(v: WView, p: WPal, w: Float, bodyH: Float, scale: Float) {
    val hero = v.cells.firstOrNull() ?: return
    val rest = v.cells.drop(1)
    val plan = wGridPlan(v, w, bodyH, scale)
    val heroH = plan.heroSp * wLineK(scale)
    Column(Modifier.fillMaxWidth().padding(start = W_PAD.dp, end = W_PAD.dp, top = W_TOP.dp)) {
        if (plan.narrow) {
            WpCell(hero, p, plan.heroRoom, plan.heroSp, true, heroH, scale)
            rest.take(plan.rows).forEach { c ->
                Spacer(Modifier.height(4.dp))
                WpCompact(c, p, plan.heroRoom, scale)
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) { WpCell(hero, p, plan.heroRoom, plan.heroSp, true, heroH, scale) }
                Spacer(Modifier.width(W_COL_GAP.dp))
                Column(Modifier.width(plan.c2.dp)) {
                    rest.getOrNull(0)?.let { c -> WpCell(c, p, plan.c2, wValueSp(c, plan.c2, 18f, 13f, scale), false, heroH, scale) }
                }
            }
            if (plan.two) {
                Spacer(Modifier.height(W_GAP.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        rest.getOrNull(1)?.let { c ->
                            WpCell(c, p, plan.heroRoom, wValueSp(c, plan.heroRoom, 18f, 13f, scale), false, wSmallH(scale), scale)
                        }
                    }
                    Spacer(Modifier.width(W_COL_GAP.dp))
                    Column(Modifier.width(plan.c2.dp)) {
                        rest.getOrNull(2)?.let { c ->
                            WpCell(c, p, plan.c2, wValueSp(c, plan.c2, 18f, 13f, scale), false, wSmallH(scale), scale)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WpCell(c: WCell, p: WPal, room: Float, sp: Float, big: Boolean, lineH: Float, scale: Float) {
    KT(c.label, 13.sp, if (c.strong) p.ink else p.ink2, weight = if (c.strong) FontWeight.Medium else FontWeight.Normal)
    val sub = c.sub
    val pill = c.pill
    Row(
        Modifier.height(lineH.dp),
        verticalAlignment = if (big) Alignment.Bottom else Alignment.CenterVertically,
    ) {
        if (c.value.isNotEmpty()) {
            KT(c.value, sp.sp, p.tone(c.tone), weight = if (big) FontWeight.Bold else FontWeight.Medium)
        }
        if (sub != null && wShowSub(c, room, scale)) {
            Spacer(Modifier.width(6.dp))
            KT(sub, 14.sp, p.ink2, Modifier.padding(bottom = (sp * 0.12f).dp))
        }
        if (pill != null) {
            if (c.value.isNotEmpty()) Spacer(Modifier.width(8.dp))
            WpPill(pill, p, full = wPillFull(c, room, sp, scale))
        }
    }
}

@Composable
private fun WpCompact(c: WCell, p: WPal, room: Float, scale: Float) {
    val pill = c.pill
    Row(Modifier.fillMaxWidth().height(wSmallH(scale).dp), verticalAlignment = Alignment.CenterVertically) {
        KT(c.label, 13.sp, if (c.strong) p.ink else p.ink2, Modifier.weight(1f))
        if (c.value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            KT(c.value, 15.sp, p.tone(c.tone), weight = FontWeight.Medium)
        }
        if (pill != null) {
            Spacer(Modifier.width(6.dp))
            WpPill(pill, p, full = c.value.isEmpty() && wPillW(pill, scale) <= room * 0.6f)
        }
    }
}

@Composable
private fun WpHealth(v: WView, p: WPal, w: Float, bodyH: Float, scale: Float) {
    val hero = v.cells.firstOrNull() ?: return
    val plan = wHealthPlan(v, w, bodyH, scale)
    val of = hero.of
    Column(Modifier.fillMaxWidth().padding(start = W_PAD.dp, end = W_PAD.dp, top = W_TOP.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            KT(hero.value, plan.heroSp.sp, p.ink, weight = FontWeight.Bold)
            if (of != null) KT(of, 18.sp, p.ink, Modifier.padding(bottom = (plan.heroSp * 0.12f).dp))
            Spacer(Modifier.width(6.dp))
            KT(hero.label, 13.sp, p.ink2, Modifier.padding(bottom = (plan.heroSp * 0.14f).dp))
        }
        v.cells.drop(1).take(plan.lines).forEach { c ->
            Spacer(Modifier.height(W_GAP.dp))
            val pill = c.pill
            if (pill != null) WpPill(pill, p, full = true)
            else KT(wJoinFit(c.value, c.sub, w - 2 * W_PAD, 14f, scale), 14.sp, p.tone(c.tone))
        }
    }
}

@Composable
private fun WpPill(pm: WPillM, p: WPal, full: Boolean) {
    val (bg, fg) = p.pill(pm.kind)
    val icon = pm.kind.icon()
    val text = full || icon == null
    Row(
        Modifier.height(26.dp).clip(CircleShape).background(bg)
            .padding(start = if (icon != null) 6.dp else 10.dp, end = if (text) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, if (text) null else pm.text, tint = fg, modifier = Modifier.size(16.dp))
        if (text) {
            if (icon != null) Spacer(Modifier.width(4.dp))
            KT(pm.text, 13.sp, fg, weight = FontWeight.Medium)
        }
    }
}
