package com.myfuturecode.mfcdash.ui.screens.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.*
import com.myfuturecode.mfcdash.ui.HState
import com.myfuturecode.mfcdash.ui.HealthVm
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.Info
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- colours / labels

@Composable
fun statusColor(s: String): Color {
    val ex = MaterialTheme.extra
    return when (s) {
        "HEALTHY", "OK", "COMPLETED_NO_ISSUES", "FIXED", "RESOLVED", "PUBLISHED", "SUCCESS", "ok", "passed" -> ex.good
        "PROBLEM", "ERROR", "FAILED", "ISSUES_DETECTED", "fail", "failed", "error" -> ex.bad
        "WATCH", "NEEDS_HUMAN_REVIEW", "NOT_SET", "NOT_SYNCED", "PARTIAL", "warn", "REJECTED", "DISCARDED" -> ex.warn
        "READY_FOR_REVIEW", "APPROVED", "AAB_BUILDING", "TESTING", "FIX_IN_PROGRESS", "INVESTIGATING", "RUNNING", "QUEUED", "running", "started" -> Info
        else -> ex.muted
    }
}

@Composable
fun sevColor(s: String): Color = when (s) { "P0" -> MaterialTheme.extra.bad; "P1" -> MaterialTheme.extra.warn; "P2" -> Info; else -> MaterialTheme.extra.muted }

fun statusText(s: String) = s.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }.t()

@Composable
fun StatusPill(s: String, text: String = statusText(s)) = Pill(text, statusColor(s))

fun typeText(t: String) = when (t) { "CRASH" -> "Crash".t(); "ANR" -> "ANR"; "AD" -> "Ad issue".t(); "TASK" -> "Task".t(); "VITALS" -> "Vitals"; else -> t }

fun jobText(t: String) = when (t) {
    "manual_full" -> "Full scan".t(); "critical" -> "Critical crashes".t(); "play_errors" -> "Play errors".t(); "vitals" -> "Vitals".t(); "ads" -> "Ads".t()
    "discovery" -> "App discovery".t(); "investigate" -> "AI investigation".t(); "ai_fix" -> "AI fix".t(); "verify" -> "Repo verify".t(); "audit" -> "Daily audit".t()
    "summary" -> "Daily summary".t(); else -> t
}

fun ago(ts: String?): String = tsMs(ts)?.let { agoText(it) } ?: "–"
fun bytes(n: Long): String = when { n >= 1 shl 20 -> num(n / 1048576.0, 1) + " MB"; n >= 1024 -> num(n / 1024.0, 0) + " KB"; else -> "$n B" }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HTopBar(title: String, sub: String?, nav: NavHostController, busy: Boolean, onRefresh: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = { IconButton(onClick = { if (!nav.popBackStack()) nav.navigate("home") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh") }
            actions()
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.background),
    )
}

fun updatedText(h: HState) = if (h.lastOk == 0L) "Loading…".t() else "Updated".t() + " " + agoText(h.lastOk)

/** api/health.php server par nahi mila (404) ya koi aur error. */
@Composable
fun HProblem(h: HState, onRetry: () -> Unit) {
    Panel {
        if (h.missing) {
            Text("AI App Health is not on the server yet".t(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Upload lib/health.php, api/health.php, api/health_hook.php and health_config.php to mfc_dash (see the setup guide), then tap Try again.".t(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(h.error ?: "No data received".t(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("Try again".t()) }
    }
}

@Composable
fun Loading() { Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }

@Composable
fun BusyButton(text: String, busy: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, outlined: Boolean = false, color: Color? = null, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        if (busy) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (outlined) OutlinedButton(onClick = onClick, enabled = enabled && !busy, modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp), content = content)
    else Button(onClick = onClick, enabled = enabled && !busy, modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp),
        colors = if (color != null) ButtonDefaults.buttonColors(containerColor = color) else ButtonDefaults.buttonColors(), content = content)
}

@Composable
fun Mono(text: String, maxLines: Int = Int.MAX_VALUE) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp))
}

/** ✓ / ✗ / … ek test ka natija. */
@Composable
fun TestMark(label: String, ok: Boolean?) {
    val ex = MaterialTheme.extra
    val (t, c) = when (ok) { true -> "✓" to ex.good; false -> "✗" to ex.bad; null -> "…" to ex.muted }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t, color = c, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- rows

@Composable
fun IssueRow(i: HIssue, nav: NavHostController, showApp: Boolean = true) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.navigate("health/issue/${i.id}") }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (showApp) { AppIcon(i.icon, i.app, 34.dp); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(i.severity, sevColor(i.severity)); Spacer(Modifier.width(5.dp))
                Text(typeText(i.type) + " · ISS-${i.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showApp) { Text(" · " + i.app, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.height(2.dp))
            Text(i.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(i.users?.let { tf("%s users", num(it.toDouble())) }, tf("%s events", num(i.events.toDouble())),
                i.version.takeIf { it.isNotBlank() }?.let { "v$it" }, ago(i.lastSeen)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(6.dp))
        StatusPill(i.status)
    }
}

@Composable
fun AppRow(a: HApp, nav: NavHostController) {
    val ex = MaterialTheme.extra
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.navigate("health/app/${a.pkg}") }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AppIcon(a.icon, a.name, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(a.crashRate?.let { "crash " + pct(it, 2) }, a.anrRate?.let { "ANR " + pct(it, 2) },
                if (!a.monitoring) "monitoring off".t() else null, a.lastScanAt?.let { "scan".t() + " " + ago(it) }).joinToString(" · ").ifBlank { "not scanned yet".t() },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusPill(a.status)
            if (a.openIssues > 0) { Spacer(Modifier.height(4.dp)); Text(tf("%s open", a.openIssues), style = MaterialTheme.typography.labelSmall, color = if (a.topSeverity == "P0") ex.bad else ex.warn) }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun RunRow(r: HRun, nav: NavHostController) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.navigate("health/run/${r.id}") }.padding(vertical = 8.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(jobText(r.type), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("  " + r.uid, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            StatusPill(r.status)
        }
        Text(listOfNotNull(r.app.ifBlank { if (r.pkg.isBlank()) "all apps".t() else r.pkg }, r.trigger.takeIf { it.isNotBlank() }, tFull(r.queuedAt),
            r.durationMs?.let { num(it / 1000.0, 1) + " s" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (r.active) {
            Spacer(Modifier.height(4.dp))
            if (r.checksTotal > 0) LinearProgressIndicator(progress = { r.checksDone.toFloat() / r.checksTotal }, Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            if (r.stage.isNotBlank()) Text(r.stage, style = MaterialTheme.typography.bodySmall, color = Info, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else if (r.problems > 0 || r.errors > 0) {
            Text(listOfNotNull(if (r.problems > 0) tf("%s problems", r.problems) else null, if (r.errors > 0) tf("%s errors", r.errors) else null).joinToString(" · ") +
                (r.errorText.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = if (r.errors > 0) MaterialTheme.extra.bad else MaterialTheme.extra.warn, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun EventRow(e: HEvent, nav: NavHostController?) {
    val c = when (e.level) { "error" -> MaterialTheme.extra.bad; "warn" -> MaterialTheme.extra.warn; "ok" -> MaterialTheme.extra.good; else -> MaterialTheme.extra.muted }
    Row(Modifier.fillMaxWidth().let { m -> if (nav != null && e.issueId != null) m.clickable { nav.navigate("health/issue/${e.issueId}") } else m }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
        Text(tOnly(e.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(e.msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- builds (AAB / test APK / Approve & Live)

@Composable
fun BuildCard(b: HBuild, h: HState, vm: HealthVm, nav: NavHostController?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var publish by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(b.icon, b.app, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(b.app, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("v${b.versionName} (${b.versionCode}) · " + tFull(b.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(b.status)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // server: {"unit":{"passed":n,"failed":n,"total":n},"build":true,"device":true}
            val t = b.tests
            val u = t.optJSONObject("unit")
            val uPass = u?.optInt("passed", -1) ?: -1; val uTot = u?.optInt("total", -1) ?: -1; val uFail = u?.optInt("failed", 0) ?: 0
            TestMark("Unit".t() + if (uTot > 0) " $uPass/$uTot" else "", if (u == null) null else uFail == 0)
            TestMark("Release build".t(), if (t.has("build")) t.optBoolean("build") else null)
            TestMark("Emulator".t(), if (t.has("device")) t.optBoolean("device") else null)
        }
        Spacer(Modifier.height(6.dp))
        KV("AAB", bytes(b.size))
        if (b.hasApk) KV("Test APK".t(), bytes(b.apkSize))
        KV("SHA-256", b.sha256.take(16) + "…")
        if (b.commitSha.isNotBlank()) KV("Commit", b.commitSha.take(10))
        b.issueId?.let { id -> if (nav != null) TextButton(onClick = { nav.navigate("health/issue/$id") }, contentPadding = PaddingValues(0.dp)) { Text("ISS-$id →") } }
        if (b.releaseNotes.isNotBlank()) {
            Text("Release notes".t().uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(b.releaseNotes, style = MaterialTheme.typography.bodySmall)
        }
        if (b.status == "PUBLISHED") {
            Spacer(Modifier.height(6.dp))
            Text(tf("Live on Play: %s track", b.publishedTrack) + " · " + tFull(b.publishedAt) + (b.publishNote.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extra.good)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (b.hasApk) BusyButton("Test APK".t(), "linkapk${b.id}" in h.busy, Modifier.weight(1f), outlined = true) { vm.link(b.id, "apk") { openUrl(ctx, it) } }
            BusyButton("AAB ⤓", "linkaab${b.id}" in h.busy, Modifier.weight(1f), outlined = true) { vm.link(b.id, "aab") { openUrl(ctx, it) } }
        }
        if (b.reviewable) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { discard = true }, modifier = Modifier.weight(1f)) { Text("Discard".t()) }
                BusyButton("Approve & Live".t(), "pub${b.id}" in h.busy, Modifier.weight(1.4f), color = MaterialTheme.extra.good) { publish = true }
            }
            Text("Nothing goes to Play until you tap Approve & Live and confirm.".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (publish) PublishDialog(b, onDismiss = { publish = false }) { track, rollout -> publish = false; vm.publish(b.id, track, rollout) }
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text("Discard this build?".t()) }, text = { Text("It stays in history but cannot be published.".t()) },
        confirmButton = { TextButton(onClick = { discard = false; vm.buildStatus(b.id, "DISCARDED") }) { Text("Discard".t()) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Cancel".t()) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublishDialog(b: HBuild, onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var track by remember { mutableStateOf("internal") }
    var rollout by remember { mutableStateOf("100") }
    var sure by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make it live on Play?".t()) },
        text = {
            Column {
                Text("${b.app} v${b.versionName} (${b.versionCode})", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("Track".t().uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("internal" to "Internal test".t(), "alpha" to "Closed test".t(), "beta" to "Open test".t(), "production" to "Production".t()).forEach { (k, l) ->
                        MChip(l, track == k, { track = k })
                    }
                }
                if (track == "production") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(rollout, { rollout = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Rollout %".t()) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    Text("Start small (e.g. 10%) and raise it in Play Console.".t(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { sure = !sure }) {
                    Checkbox(sure, { sure = it })
                    Text("I tested this build and approve the release".t(), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = sure, onClick = { onConfirm(track, if (track == "production") (rollout.toIntOrNull() ?: 100).coerceIn(1, 100) / 100.0 else 1.0) }) { Text("Approve & Live".t()) }
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            Text("New task for AI".t(), style = MaterialTheme.typography.titleLarge)
            Text("Write what to change. AI makes the change, runs tests and builds a test APK. You check it, then tap Approve & Live.".t(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (data == null) { Loading(); return@Column }
            val cons = data.consoles.map { it.id to it.name } + (if (data.apps.any { a -> data.consoles.none { it.id == a.consoleId } }) listOf("" to "Not linked".t()) else emptyList())
            Text("Console".t().uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cons.forEach { (k, l) -> MChip(l, console == k, { console = k; pkg = "" }) }
            }
            Spacer(Modifier.height(10.dp))
            Text("App".t().uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val apps = data.apps.filter { a -> if (console.isBlank()) data.consoles.none { it.id == a.consoleId } else a.consoleId == console }
            if (apps.isEmpty()) Empty("No app in this console yet".t())
            apps.forEach { a ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (pkg == a.pkg) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { pkg = a.pkg }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.icon, a.name, 30.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(if (a.repoVerified && a.signingVerified) "repo ✓ signing ✓".t() else "repo/signing not verified: task will wait for you".t(),
                            style = MaterialTheme.typography.bodySmall, color = if (a.repoVerified && a.signingVerified) MaterialTheme.extra.good else MaterialTheme.extra.warn)
                    }
                    RadioButton(pkg == a.pkg, { pkg = a.pkg })
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(title, { title = it.take(200) }, label = { Text("What to do (short)".t()) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(details, { details = it.take(4000) }, label = { Text("Details (screens, text, behaviour)".t()) }, minLines = 4, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Spacer(Modifier.height(12.dp))
            BusyButton("Send to AI".t(), "task" in h.busy, Modifier.fillMaxWidth().height(46.dp), enabled = pkg.isNotBlank() && title.isNotBlank()) {
                vm.task(pkg, title.trim(), details.trim()) { id -> onClose(); if (id != null) nav.navigate("health/issue/$id") }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
