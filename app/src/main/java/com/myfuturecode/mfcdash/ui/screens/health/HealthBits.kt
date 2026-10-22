package com.myfuturecode.mfcdash.ui.screens.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.HApp
import com.myfuturecode.mfcdash.data.HBuild
import com.myfuturecode.mfcdash.data.HEvent
import com.myfuturecode.mfcdash.data.HIssue
import com.myfuturecode.mfcdash.data.HRun
import com.myfuturecode.mfcdash.ui.HState
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.components.AppIcon
import com.myfuturecode.mfcdash.ui.components.MChip
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDetailBar
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KKv
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLoading
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KProgress
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KSwitch
import com.myfuturecode.mfcdash.ui.kit.fg
import com.myfuturecode.mfcdash.ui.kit.shape
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tOnly
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs
import kotlinx.coroutines.delay
import java.util.Calendar

// ---------------------------------------------------------------- colours / labels

/** Server status -> kit kind (shabd + shape + rang). */
internal fun hStatusKind(s: String): KKind = when (s) {
    "HEALTHY", "OK", "COMPLETED_NO_ISSUES", "FIXED", "RESOLVED", "PUBLISHED", "SUCCESS", "ok", "passed" -> KKind.OK
    "PROBLEM", "ERROR", "FAILED", "ISSUES_DETECTED", "fail", "failed", "error" -> KKind.BAD
    "WATCH", "NEEDS_HUMAN_REVIEW", "NOT_SET", "NOT_SYNCED", "PARTIAL", "warn", "REJECTED", "DISCARDED" -> KKind.WARN
    "READY_FOR_REVIEW", "APPROVED", "AAB_BUILDING", "TESTING", "FIX_IN_PROGRESS", "INVESTIGATING", "RUNNING", "QUEUED", "running", "started" -> KKind.ASK
    else -> KKind.NEUTRAL
}

/** P0 = turant, P1 = dhyan do, baaki sada. */
internal fun hSevKind(s: String): KKind = when (s) {
    "P0" -> KKind.BAD
    "P1" -> KKind.WARN
    else -> KKind.NEUTRAL
}

@Composable
fun statusColor(s: String): Color {
    val k = hStatusKind(s)
    return if (k == KKind.NEUTRAL) Mfc.c.ink3 else k.fg()
}

@Composable
fun sevColor(s: String): Color {
    val c = Mfc.c
    return when (s) {
        "P0" -> c.red
        "P1" -> c.amber
        "P2" -> c.brand
        else -> c.ink3
    }
}

fun statusText(s: String) = s.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }.t()

@Composable
fun StatusPill(s: String, text: String = statusText(s)) = KPill(text, hStatusKind(s))

fun typeText(t: String) = when (t) { "CRASH" -> "Crash".t(); "ANR" -> "ANR"; "AD" -> "Ad issue".t(); "TASK" -> "Task".t(); "VITALS" -> "Vitals"; else -> t }

fun jobText(t: String) = when (t) {
    "manual_full" -> "Full scan".t(); "critical" -> "Critical crashes".t(); "play_errors" -> "Play errors".t(); "vitals" -> "Vitals".t(); "ads" -> "Ads".t()
    "discovery" -> "App discovery".t(); "investigate" -> "AI investigation".t(); "ai_fix" -> "AI fix".t(); "verify" -> "Repo verify".t(); "audit" -> "Daily audit".t()
    "summary" -> "Daily summary".t(); else -> t
}

/** Issue type ka icon. */
internal fun hTypeIcon(t: String): ImageVector = when (t) {
    "CRASH" -> KIcons.Bug
    "ANR" -> KIcons.Clock
    "AD" -> KIcons.Ads
    "TASK" -> KIcons.Wand
    "VITALS" -> KIcons.Pulse
    else -> KIcons.Info
}

/** Scan / job type ka icon. */
internal fun hJobIcon(t: String): ImageVector = when (t) {
    "manual_full" -> KIcons.Refresh
    "critical", "play_errors" -> KIcons.Bug
    "vitals" -> KIcons.Pulse
    "ads" -> KIcons.Ads
    "discovery" -> KIcons.Search
    "investigate" -> KIcons.Spark
    "ai_fix" -> KIcons.Wand
    "verify" -> KIcons.Shield
    "audit" -> KIcons.DoneAll
    "summary" -> KIcons.Send
    else -> KIcons.History
}

/** Integration (overview) ka icon. */
internal fun hIntegIcon(kind: String): ImageVector = when (kind) {
    "play" -> KIcons.Globe
    "tracker" -> KIcons.Chart
    "cron" -> KIcons.Clock
    "ai" -> KIcons.Spark
    "email" -> KIcons.Send
    "github" -> KIcons.Fix
    else -> KIcons.Info
}

fun ago(ts: String?): String = tsMs(ts)?.let { agoText(it) } ?: "–"
fun bytes(n: Long): String = when { n >= 1 shl 20 -> num(n / 1048576.0, 1) + " MB"; n >= 1024 -> num(n / 1024.0, 0) + " KB"; else -> "$n B" }

/** Aaj ka ho to "09:12", warna "20 Oct". */
internal fun hWhen(ts: String?): String {
    val ms = tsMs(ts) ?: return "–"
    val a = Calendar.getInstance().apply { timeInMillis = ms }
    val now = Calendar.getInstance()
    val today = a.get(Calendar.YEAR) == now.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    return if (today) tOnly(ts) else tFull(ts).substringBeforeLast(' ')
}

internal fun hN(v: Int): String = num(v.toDouble())
internal fun hN(v: Long): String = num(v.toDouble())

/** Tabular text style (kit jaisa). */
internal fun hStyle(size: TextUnit, weight: FontWeight = FontWeight.Normal, lh: TextUnit = TextUnit.Unspecified): TextStyle =
    TextStyle(fontSize = size, fontWeight = weight, lineHeight = lh, fontFeatureSettings = "tnum")

fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Screen khuli rahe tab tak har [ms] par [tick] (pehli baar turant). App peeche (home/doosri app) ho to ruk jata hai, wapas aate hi turant. */
@Composable
fun Poll(ms: Long, vararg keys: Any?, tick: () -> Unit) {
    val t by rememberUpdatedState(tick)
    val life = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(life, *keys) { life.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { t(); delay(ms) } } }
}

// ---------------------------------------------------------------- top bar / frame

/** Detail app bar (kit): back + title + sub, daayein refresh (busy = ghoomta) + actions. */
@Composable
fun HTopBar(title: String, sub: String?, nav: NavHostController, busy: Boolean, onRefresh: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    KDetailBar(title, { if (!nav.popBackStack()) nav.navigate("home") }, sub = sub, actions = {
        KBarIcon(KIcons.Refresh, "Refresh".t(), onRefresh, spinning = busy)
        actions()
    })
}

fun updatedText(h: HState) = if (h.lastOk == 0L) "Loading…".t() else "Updated".t() + " " + agoText(h.lastOk)

/** api/health.php server par nahi mila (404) ya koi aur error. */
@Composable
fun HProblem(h: HState, onRetry: () -> Unit) {
    val c = Mfc.c
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (h.missing) KIconTile(KIcons.Dhyan, KKind.WARN) else KIconTile(KIcons.Turant, KKind.BAD)
            Column(Modifier.weight(1f)) {
                Text(
                    if (h.missing) "Not on server".t() else "Error".t(), color = c.ink, style = hStyle(15.sp, FontWeight.SemiBold, 20.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (h.missing) "lib/health.php · api/health.php · api/health_hook.php · health_config.php" else h.error ?: "No data received".t(),
                    Modifier.padding(top = 2.dp), color = if (h.missing) c.ink2 else c.red, style = hStyle(13.sp, lh = 18.sp),
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        KButton("Try again".t(), onRetry, Modifier.fillMaxWidth().padding(top = 12.dp), kind = KBtn.SOFT, icon = KIcons.Refresh)
    }
}

@Composable
fun Loading() {
    KLoading()
}

/** Kit button (48dp). outlined = OUTLINE; color = rangeen bg (jaise laal "Hatao"). busy = spinner. */
@Composable
fun BusyButton(text: String, busy: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, outlined: Boolean = false, color: Color? = null, onClick: () -> Unit) {
    if (color == null || outlined) {
        KButton(text, onClick, modifier, kind = if (outlined) KBtn.OUTLINE else KBtn.PRIMARY, enabled = enabled, busy = busy)
        return
    }
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.heightIn(min = 48.dp).alpha(if (enabled) 1f else .45f).clip(shape).background(color, shape)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        Text(text, color = Color.White, style = hStyle(15.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Code / log ka dibba (monospace, well rang). */
@Composable
fun Mono(text: String, maxLines: Int = Int.MAX_VALUE) {
    val c = Mfc.c
    Text(
        text, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.well).padding(12.dp),
        color = c.ink2, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** Ek test ka natija: check (pass) / diamond (fail) / ghadi (baaki). */
@Composable
fun TestMark(label: String, ok: Boolean?) {
    val c = Mfc.c
    val (icon, col) = when (ok) {
        true -> KIcons.Ok to c.green
        false -> KIcons.Turant to c.red
        null -> KIcons.Wait to c.ink3
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, null, tint = col, modifier = Modifier.size(16.dp))
        Text(label, color = c.ink2, style = hStyle(13.sp, FontWeight.Medium, 18.sp), maxLines = 1)
    }
}

/** Card ke andar chhoti rangeen line: shape + text (kai line tak). */
@Composable
internal fun HNote(text: String, kind: KKind, modifier: Modifier = Modifier, maxLines: Int = 3) {
    val col = kind.fg()
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        kind.shape()?.let { Icon(it, null, tint = col, modifier = Modifier.padding(top = 3.dp).size(12.dp)) }
        Text(text, color = col, style = hStyle(13.sp, FontWeight.Medium, 18.sp), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Chhota label (card ke andar heading). */
@Composable
internal fun HLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 12.dp), color = Mfc.c.ink3, style = hStyle(13.sp, FontWeight.SemiBold, 18.sp), maxLines = 1)
}

/** Section ke daayein chhota grey text. */
@Composable
internal fun HSecNote(text: String) {
    Text(text, color = Mfc.c.ink3, style = hStyle(13.sp, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Grid ka ek number: label, value, chhoti line. onClick = us tab par. */
internal class HCell(val label: String, val value: String, val sub: String? = null, val color: Color? = null, val onClick: (() -> Unit)? = null)

/** Card ke andar numbers ka grid (andar koi dibba nahi). */
@Composable
internal fun HGrid(cells: List<HCell>, cols: Int = 3) {
    val c = Mfc.c
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { x ->
                    var m = Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                    val click = x.onClick
                    if (click != null) m = m.clickable(role = Role.Button, onClick = click)
                    Column(m.padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Text(x.label, color = c.ink2, style = hStyle(13.sp, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(x.value, color = x.color ?: c.ink, style = hStyle(20.sp, FontWeight.Bold, 26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        x.sub?.let { Text(it, color = c.ink3, style = hStyle(13.sp, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** KRow jaisi row, par teen line: title, status (shape + rang), halki line. extra = progress waghera. */
@Composable
internal fun HRow3(
    title: String,
    status: String?,
    kind: KKind,
    modifier: Modifier = Modifier,
    lead: (@Composable () -> Unit)? = null,
    note: String? = null,
    noteColor: Color = Color.Unspecified,
    value: String? = null,
    valueSub: String? = null,
    titleLines: Int = 1,
    onClick: (() -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = Mfc.c
    var m = modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(
        m.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        lead?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, style = hStyle(15.sp, FontWeight.SemiBold, 20.sp), maxLines = titleLines, overflow = TextOverflow.Ellipsis)
            if (!status.isNullOrBlank()) KStatus(status, kind, Modifier.padding(top = 2.dp))
            if (!note.isNullOrBlank()) Text(
                note, Modifier.padding(top = 2.dp), color = noteColor.takeOrElse { c.ink3 }, style = hStyle(13.sp, lh = 18.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            extra?.invoke(this)
        }
        if (value != null || valueSub != null) Column(horizontalAlignment = Alignment.End) {
            if (value != null) Text(value, color = c.ink2, style = hStyle(14.sp, lh = 18.sp), maxLines = 1)
            if (valueSub != null) Text(valueSub, Modifier.padding(top = 2.dp), color = c.ink3, style = hStyle(13.sp, lh = 18.sp), maxLines = 1)
        }
        if (onClick != null) Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.offset(x = 6.dp).size(22.dp))
    }
}

/** Card ke andar label + brand value + chevron (48dp). */
@Composable
internal fun HLinkRow(label: String, value: String, onClick: () -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.weight(1f), color = c.ink2, style = hStyle(15.sp, lh = 20.sp), maxLines = 1)
        Text(value, color = c.brand, style = hStyle(16.sp, FontWeight.Bold, 20.sp), maxLines = 1)
        Icon(KIcons.Chev, null, tint = c.brand, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------- rows

/** Issue row: logo (ya type tile), title, "P0 · Crash · 340 users" (rang), daayein time + status. */
@Composable
fun IssueRow(i: HIssue, nav: NavHostController, showApp: Boolean = true) {
    val k = hSevKind(i.severity)
    val app = i.app.ifBlank { i.pkg }
    val line = listOfNotNull(
        i.severity.ifBlank { null }, typeText(i.type).ifBlank { null },
        i.users?.takeIf { it > 0 }?.let { tf("%s users", num(it.toDouble())) },
    ).joinToString(" · ")
    val meta = listOfNotNull(
        "ISS-${i.id}", tf("%s events", num(i.events.toDouble())), i.version.takeIf { it.isNotBlank() }?.let { "v$it" },
    ).joinToString(" · ")
    HRow3(
        title = if (showApp) app else i.title,
        status = line,
        kind = k,
        lead = { if (showApp) AppIcon(i.icon, app, 40.dp) else KIconTile(hTypeIcon(i.type), k) },
        note = if (showApp) i.title else meta,
        value = hWhen(i.lastSeen),
        valueSub = statusText(i.status),
        titleLines = if (showApp) 1 else 2,
        onClick = { nav.navigate("health/issue/${i.id}") },
    )
}

/** App row: logo, naam, status (+ khule issues), crash / ANR / scan line. */
@Composable
fun AppRow(a: HApp, nav: NavHostController) {
    val k = if (a.openIssues > 0 && a.topSeverity == "P0") KKind.BAD else hStatusKind(a.status)
    val st = statusText(a.status) + if (a.openIssues > 0) " · " + tf("%s open", a.openIssues) else ""
    val note = listOfNotNull(
        a.crashRate?.let { "Crash".t() + " " + pct(it, 2) }, a.anrRate?.let { "ANR " + pct(it, 2) },
        if (!a.monitoring) "monitoring off".t() else null, a.lastScanAt?.let { "scan".t() + " " + ago(it) },
    ).joinToString(" · ").ifBlank { "not scanned yet".t() }
    HRow3(
        title = a.name.ifBlank { a.pkg },
        status = st,
        kind = k,
        lead = { AppIcon(a.icon, a.name.ifBlank { a.pkg }, 40.dp) },
        note = note,
        onClick = { nav.navigate("health/app/${a.pkg}") },
    )
}

/** Scan run row: job tile, status, app / trigger / time; chalu ho to progress + stage. */
@Composable
fun RunRow(r: HRun, nav: NavHostController) {
    val k = hStatusKind(r.status)
    val note = listOfNotNull(
        r.app.ifBlank { if (r.pkg.isBlank()) "all apps".t() else r.pkg }, r.trigger.takeIf { it.isNotBlank() },
        r.durationMs?.let { num(it / 1000.0, 1) + " s" }, r.uid.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    HRow3(
        title = jobText(r.type),
        status = statusText(r.status),
        kind = k,
        lead = { KIconTile(hJobIcon(r.type), k) },
        note = note,
        value = hWhen(r.queuedAt),
        onClick = { nav.navigate("health/run/${r.id}") },
    ) {
        val c = Mfc.c
        if (r.active) {
            if (r.checksTotal > 0) KProgress(r.checksDone.toFloat() / r.checksTotal, c.brand, top = 6.dp)
            else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = c.brand, trackColor = c.zTrack)
            if (r.stage.isNotBlank()) Text(
                r.stage, Modifier.padding(top = 4.dp), color = c.brand, style = hStyle(13.sp, lh = 18.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else if (r.problems > 0 || r.errors > 0) {
            val t = listOfNotNull(
                if (r.problems > 0) tf("%s problems", r.problems) else null, if (r.errors > 0) tf("%s errors", r.errors) else null,
            ).joinToString(" · ") + (r.errorText.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
            Text(
                t, Modifier.padding(top = 2.dp), color = if (r.errors > 0) c.red else c.amber, style = hStyle(13.sp, FontWeight.Medium, 18.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Log / feed ki ek line: level ka shape + time + message. issue ho to tap = issue. */
@Composable
fun EventRow(e: HEvent, nav: NavHostController?) {
    val c = Mfc.c
    val k = when (e.level) { "error" -> KKind.BAD; "warn" -> KKind.WARN; "ok" -> KKind.OK; else -> KKind.NEUTRAL }
    val id = e.issueId
    var m = Modifier.fillMaxWidth()
    if (nav != null && id != null) m = m.heightIn(min = 48.dp).clickable { nav.navigate("health/issue/$id") }
    Row(m.padding(vertical = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(top = 3.dp).size(12.dp), contentAlignment = Alignment.Center) {
            val sh = k.shape()
            if (sh != null) Icon(sh, null, tint = k.fg(), modifier = Modifier.size(12.dp))
            else Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(c.ink3))
        }
        Text(tOnly(e.createdAt), color = c.ink3, style = hStyle(13.sp, lh = 18.sp), maxLines = 1)
        Text(e.msg, Modifier.weight(1f), color = c.ink, style = hStyle(13.sp, lh = 18.sp))
    }
}

/** Switch wali row (list card ke andar). */
@Composable
internal fun HSwitchRow(label: String, on: Boolean, enabled: Boolean = true, sub: String? = null, subKind: KKind? = null, onChange: (Boolean) -> Unit) {
    KRow(label, sub = sub, subKind = subKind, quiet = !enabled, titleLines = 2, trailing = { KSwitch(on, onChange, enabled) })
}

/** Repo + signing verify ka haal, daayein Verify button. */
@Composable
internal fun HVerifyRow(a: HApp, busy: Boolean, onVerify: () -> Unit) {
    val ok = a.repoVerified && a.signingVerified
    val line = (if (a.repoVerified) "repo ✓" else "repo ✗") + "  " + (if (a.signingVerified) "signing ✓" else "signing ✗") +
        (a.verifyNote.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
    KRow(
        "Repo & signing".t(), sub = line, subKind = if (ok) KKind.OK else KKind.WARN,
        trailing = if (a.repo.isNotBlank()) { { KButton("Verify".t(), onVerify, kind = KBtn.SOFT, busy = busy) } } else null,
    )
}

/** Har app ke switches (Settings tab aur app screen dono me). List card ke andar. */
@Composable
internal fun HAppSwitches(a: HApp, h: HState, vm: HealthVm) {
    HSwitchRow("24/7 monitoring".t(), a.monitoring) { vm.appSetting(a.pkg, "monitoring", it) }
    KListDivider()
    HSwitchRow(
        "Automatic safe fix attempts".t(), a.autofix,
        sub = if (a.autofix && !a.autofixEffective) "waiting: repo/signing not verified".t() else null, subKind = KKind.WARN,
    ) { vm.appSetting(a.pkg, "autofix", it) }
    KListDivider()
    HSwitchRow("Email notifications".t(), a.email) { vm.appSetting(a.pkg, "email", it) }
    KListDivider()
    HSwitchRow("Production auto-publish".t(), false, enabled = false, sub = "Always off".t()) { }
    KListDivider()
    HVerifyRow(a, "verify${a.pkg}" in h.busy) { vm.verify(a.pkg) }
}

// ---------------------------------------------------------------- builds (AAB / test APK / Approve & Live)

/** Ek build: app, version, tests, size, links; review wala ho to Hatao / Approve & Live (confirm ke saath). */
@Composable
fun BuildCard(b: HBuild, h: HState, vm: HealthVm, nav: NavHostController?) {
    val ctx = LocalContext.current
    var publish by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val c = Mfc.c
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(b.icon, b.app, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(b.app, color = c.ink, style = hStyle(15.sp, FontWeight.SemiBold, 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "v${b.versionName} (${b.versionCode}) · " + tFull(b.createdAt), Modifier.padding(top = 2.dp), color = c.ink3,
                    style = hStyle(13.sp, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(b.status)
        }
        // server: {"unit":{"passed":n,"failed":n,"total":n},"build":true,"device":true}
        val t = b.tests
        val u = t.optJSONObject("unit")
        val uPass = u?.optInt("passed", -1) ?: -1
        val uTot = u?.optInt("total", -1) ?: -1
        val uFail = u?.optInt("failed", 0) ?: 0
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TestMark("Unit".t() + if (uTot > 0) " $uPass/$uTot" else "", if (u == null) null else uFail == 0)
            TestMark("Release build".t(), if (t.has("build")) t.optBoolean("build") else null)
            TestMark("Emulator".t(), if (t.has("device")) t.optBoolean("device") else null)
        }
        KKv("AAB", bytes(b.size), Modifier.padding(top = 4.dp))
        if (b.hasApk) KKv("Test APK".t(), bytes(b.apkSize))
        Text(
            "SHA-256 " + b.sha256.take(16) + "…" + (if (b.commitSha.isNotBlank()) " · Commit " + b.commitSha.take(10) else ""),
            color = c.ink3, style = hStyle(13.sp, lh = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val iid = b.issueId
        if (iid != null && nav != null) HLinkRow("Issue".t(), "ISS-$iid") { nav.navigate("health/issue/$iid") }
        if (b.releaseNotes.isNotBlank()) {
            HLabel("Release notes".t())
            Text(b.releaseNotes, Modifier.padding(top = 2.dp), color = c.ink, style = hStyle(14.sp, lh = 20.sp))
        }
        if (b.status == "PUBLISHED") HNote(
            tf("Live on Play: %s track", b.publishedTrack) + " · " + tFull(b.publishedAt) + (b.publishNote.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            KKind.OK, Modifier.padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (b.hasApk) KButton(
                "Test APK".t(), { vm.link(b.id, "apk") { openUrl(ctx, it) } }, Modifier.weight(1f), kind = KBtn.SOFT, icon = KIcons.Download,
                busy = "linkapk${b.id}" in h.busy,
            )
            KButton(
                "AAB", { vm.link(b.id, "aab") { openUrl(ctx, it) } }, Modifier.weight(1f), kind = KBtn.SOFT, icon = KIcons.Download,
                busy = "linkaab${b.id}" in h.busy,
            )
        }
        if (b.reviewable) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton("Discard".t(), { discard = true }, Modifier.weight(1f), kind = KBtn.OUTLINE, busy = "bs${b.id}" in h.busy)
            KButton("Approve & Live".t(), { publish = true }, Modifier.weight(1.4f), icon = KIcons.Send, busy = "pub${b.id}" in h.busy)
        }
    }
    if (publish) PublishDialog(b, onDismiss = { publish = false }) { track, rollout -> publish = false; vm.publish(b.id, track, rollout) }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        title = { Text("Discard this build?".t()) },
        text = { Text("Kept in history · not publishable".t()) },
        confirmButton = { TextButton(onClick = { discard = false; vm.buildStatus(b.id, "DISCARDED") }) { Text("Discard".t()) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Cancel".t()) } },
    )
}

/** Play par live: track chuno (production me rollout %), "maine test kiya" tick ke bina button band. */
@Composable
fun PublishDialog(b: HBuild, onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var track by remember { mutableStateOf("internal") }
    var rollout by remember { mutableStateOf("100") }
    var sure by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make it live on Play?".t()) },
        text = {
            val c = Mfc.c
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${b.app} v${b.versionName} (${b.versionCode})", color = c.ink, style = hStyle(15.sp, FontWeight.SemiBold, 20.sp))
                HLabel("Track".t())
                listOf("internal" to "Internal test".t(), "alpha" to "Closed test".t(), "beta" to "Open test".t(), "production" to "Production".t()).forEach { (k, l) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                            .selectable(selected = track == k, role = Role.RadioButton, onClick = { track = k }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = track == k, onClick = null)
                        Text(l, Modifier.padding(start = 8.dp), color = c.ink, style = hStyle(15.sp, lh = 20.sp))
                    }
                }
                if (track == "production") OutlinedTextField(
                    rollout, { rollout = it.filter { ch -> ch.isDigit() }.take(3) }, Modifier.fillMaxWidth().padding(top = 4.dp),
                    label = { Text("Rollout %".t()) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .toggleable(value = sure, role = Role.Checkbox, onValueChange = { sure = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = sure, onCheckedChange = null)
                    Text("I tested this build and approve the release".t(), Modifier.padding(start = 8.dp), color = c.ink, style = hStyle(14.sp, lh = 20.sp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = sure, onClick = {
                onConfirm(track, if (track == "production") (rollout.toIntOrNull() ?: 100).coerceIn(1, 100) / 100.0 else 1.0)
            }) { Text("Approve & Live".t()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel".t()) } },
    )
}

// ---------------------------------------------------------------- "Naya kaam" (manual task → AI → test APK)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskSheet(h: HState, vm: HealthVm, nav: NavHostController, prePkg: String?, onClose: () -> Unit) {
    LaunchedEffect(Unit) { if (h.consoles == null) vm.loadConsoles() }
    val data = h.consoles
    val firstApp = data?.apps?.firstOrNull { it.pkg == prePkg }
    var console by remember(data) { mutableStateOf(firstApp?.consoleId ?: data?.consoles?.firstOrNull()?.id ?: "") }
    var pkg by remember(data) { mutableStateOf(prePkg ?: "") }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        KPageTokens {
            val c = Mfc.c
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
                Text("New task for AI".t(), color = c.ink, style = hStyle(20.sp, FontWeight.Bold, 26.sp))
                if (data == null) {
                    KLoading()
                    return@Column
                }
                val cons = data.consoles.map { it.id to it.name } +
                    (if (data.apps.any { a -> data.consoles.none { it.id == a.consoleId } }) listOf("" to "Not linked".t()) else emptyList())
                HLabel("Console".t())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cons.forEach { (k, l) -> MChip(l, console == k, { console = k; pkg = "" }) }
                }
                HLabel("App".t())
                val apps = data.apps.filter { a -> if (console.isBlank()) data.consoles.none { it.id == a.consoleId } else a.consoleId == console }
                if (apps.isEmpty()) KEmpty("No app here".t(), KIcons.Apps)
                apps.forEach { a ->
                    val on = pkg == a.pkg
                    val ok = a.repoVerified && a.signingVerified
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (on) c.brandSoft else Color.Transparent)
                            .selectable(selected = on, role = Role.RadioButton, onClick = { pkg = a.pkg })
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(a.icon, a.name, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(a.name, color = c.ink, style = hStyle(15.sp, FontWeight.SemiBold, 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            KStatus(if (ok) "repo ✓ signing ✓".t() else "Not verified".t(), if (ok) KKind.OK else KKind.WARN, Modifier.padding(top = 2.dp))
                        }
                        RadioButton(selected = on, onClick = null)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    title, { title = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("What to do (short)".t()) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    details, { details = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("Details (screens, text, behaviour)".t()) }, minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(16.dp))
                KButton(
                    "Send to AI".t(),
                    { vm.task(pkg, title.trim(), details.trim()) { id -> onClose(); if (id != null) nav.navigate("health/issue/$id") } },
                    Modifier.fillMaxWidth(), icon = KIcons.Send, enabled = pkg.isNotBlank() && title.isNotBlank(), busy = "task" in h.busy,
                )
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}
