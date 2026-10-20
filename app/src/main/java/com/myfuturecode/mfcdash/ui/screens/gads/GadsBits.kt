package com.myfuturecode.mfcdash.ui.screens.gads

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.*
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.GadsVm
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.screens.health.*
import com.myfuturecode.mfcdash.ui.theme.Info
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------- colours / formatting

@Composable
fun levelColor(l: String): Color = when (l) {
    "CRITICAL" -> MaterialTheme.extra.bad; "WARNING" -> MaterialTheme.extra.warn; "INFO" -> Info; else -> MaterialTheme.extra.muted
}

@Composable
fun gStateColor(s: String): Color {
    val ex = MaterialTheme.extra
    return when (s.uppercase()) {
        "HEALTHY", "OK", "SUCCESS", "ENABLED", "CONFIRMED_OK", "BEST", "GOOD", "APPROVED" -> ex.good
        "CRITICAL", "FAILED", "SUSPENDED", "ERROR", "BAD", "LOW", "DISAPPROVED", "REMOVED" -> ex.bad
        "WARNING", "WARN", "PARTIAL", "STALE", "NO_DATA", "PAUSED", "LIMITED", "APPROVED_LIMITED", "SUSPECTED" -> ex.warn
        "RUNNING", "LEARNING", "INFO" -> Info
        else -> ex.muted
    }
}

fun gText(s: String) = s.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }.t()

/** Account ki currency ke saath paisa (kabhi do currency jodte nahi). */
fun money(v: Double?, cur: String, dec: Int = 2): String = if (v == null || v.isNaN()) "–" else (cur.ifBlank { "?" } + " " + num(v, dec))

fun signedPct(v: Double?): String = if (v == null || v.isNaN()) "–" else (if (v > 0) "+" else "") + num(v, 1) + "%"

fun ageMin(m: Int?): String = when {
    m == null -> "never".t()
    m < 1 -> "just now".t()
    m < 120 -> tf("%s min ago", m)
    m < 2880 -> tf("%s hours ago", m / 60)
    else -> tf("%s days ago", m / 1440)
}

fun JSONObject.dOrNull(k: String): Double? = dn(k)

fun shareText(ctx: Context, subject: String, text: String) {
    runCatching {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text), subject)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun updatedText(g: GState) = if (g.lastOk == 0L) "Loading…".t() else "Updated".t() + " " + agoText(g.lastOk)

// ---------------------------------------------------------------- frame bits

@Composable
fun GProblem(g: GState, onRetry: () -> Unit) {
    Panel {
        if (g.missing) {
            Text("Google Ads monitor is not on the server yet".t(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Upload api/gads_health.php, api/gads_ingest.php, lib/gads_health.php and lib/gads_rules.php to mfc_dash (see the setup guide), then tap Try again.".t(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text(g.error ?: "No data received".t(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("Try again".t()) }
    }
}

/** View ka data ya loading / error. */
@Composable
fun GLoad(g: GState, j: JSONObject?, retry: () -> Unit, content: @Composable (JSONObject) -> Unit) {
    if (j == null) { if (g.error != null) GProblem(g, retry) else Loading(); return }
    content(j)
}

@Composable
fun ReadOnlyNote() {
    Text("Read-only: this monitor never changes anything in Google Ads. Do any step yourself in Google Ads.".t(),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
fun GLabel(t: String) {
    Text(t.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
fun Small(t: String, color: Color? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(t, style = MaterialTheme.typography.bodySmall, color = color ?: MaterialTheme.colorScheme.onSurfaceVariant, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

// ---------------------------------------------------------------- rows

@Composable
fun GAlertRow(a: GAlert, nav: NavHostController, showAccount: Boolean = true) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.navigate("gads/alert/${a.id}") }.padding(vertical = 8.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(a.level, levelColor(a.level)); Spacer(Modifier.width(5.dp))
            Pill(a.klass, gStateColor(a.klass))
            if (a.sim) { Spacer(Modifier.width(5.dp)); Pill("SIMULATED", MaterialTheme.extra.muted) }
            Spacer(Modifier.weight(1f))
            Text(if (a.open) ago(a.firstSeen) else "closed".t(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(3.dp))
        Text(a.titleL, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Small(listOfNotNull(if (showAccount) a.company.ifBlank { null } else null, if (showAccount) "CID " + a.cidFmt else null, a.campaign.ifBlank { null },
            a.country.ifBlank { null }).joinToString(" · "), maxLines = 1)
        Small(a.whatL, maxLines = 2)
    }
}

@Composable
fun GAlertList(list: List<GAlert>, nav: NavHostController, empty: String, showAccount: Boolean = true) {
    Panel {
        if (list.isEmpty()) Empty(empty)
        list.forEachIndexed { i, a -> GAlertRow(a, nav, showAccount); if (i < list.lastIndex) Hairline() }
    }
}

@Composable
fun GRunRow(r: GRun, nav: NavHostController) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav.navigate("gads/run/${r.id}") }.padding(vertical = 8.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(runType(r.type), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("  " + r.uid, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            if (r.sim) { Spacer(Modifier.width(5.dp)); Pill("SIMULATED", MaterialTheme.extra.muted) }
            Spacer(Modifier.weight(1f))
            Pill(r.status, gStateColor(r.status))
        }
        Small(listOfNotNull(tFull(r.startedAt), r.trigger.ifBlank { null }, if (r.accounts > 0) tf("%s accounts", r.accounts) else null,
            if (r.campaigns > 0) tf("%s campaigns", r.campaigns) else null, if (r.issues > 0) tf("%s issues", r.issues) else null,
            if (r.failedChecks > 0) tf("%s failed checks", r.failedChecks) else null).joinToString(" · "), maxLines = 2)
        if (r.errors.isNotBlank()) Small(r.errors, MaterialTheme.extra.bad, maxLines = 2)
    }
}

fun runType(t: String) = when (t) {
    "scan" -> "Scan".t(); "email" -> "Email check".t(); "deep" -> "Deep AI analysis".t(); "daily" -> "Daily review".t(); "simulation" -> "Simulation".t(); "ai" -> "AI analysis".t(); else -> t
}

@Composable
fun GEventRow(e: GEvent, nav: NavHostController?) {
    val c = when (e.level) { "error" -> MaterialTheme.extra.bad; "warn" -> MaterialTheme.extra.warn; "ok" -> MaterialTheme.extra.good; else -> MaterialTheme.extra.muted }
    Row(Modifier.fillMaxWidth().let { m -> if (nav != null && e.alertId != null) m.clickable { nav.navigate("gads/alert/${e.alertId}") } else m }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
        Text(tOnly(e.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(e.msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/** Rule engine checks: OK / WARN / INSUFFICIENT + text. */
@Composable
fun ChecksList(j: JSONObject?) {
    val list = j?.checks().orEmpty()
    if (list.isEmpty()) { Empty("No checks yet (runs on the next scan)".t()); return }
    list.forEach { (k, st, txt) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
            Pill(st, gStateColor(st)); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(gText(k), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                if (txt.isNotBlank()) Small(txt)
            }
        }
    }
}

/** Data sources: dashboard / script / email, kitna purana. */
@Composable
fun SourcesList(src: JSONObject?) {
    if (src == null || src.length() == 0) { Small("No data source enabled".t(), MaterialTheme.extra.bad); return }
    src.keys().forEach { k ->
        val o = src.optJSONObject(k) ?: return@forEach
        val age = o.dn("age_min")?.toInt()
        val label = when (k) { "dashboard" -> "Existing dashboard data".t(); "script" -> "Read-only Ads Script".t(); "email" -> "Google Ads emails".t(); else -> k }
        val extra = listOfNotNull(o.sn("version")?.let { "v$it" }, if (k == "email" && !o.b("configured")) "not configured".t() else null).joinToString(" · ")
        KV(label, (if (age == null) "no data yet".t() else ageMin(age)) + (if (extra.isNotBlank()) " · $extra" else ""),
            valueColor = if (age == null) MaterialTheme.extra.bad else if (age > 180) MaterialTheme.extra.warn else null)
    }
}

/** metrics JSON (server ke numbers) ko label: value list me. */
@Composable
fun MetricsList(m: JSONObject, cur: String) {
    if (m.length() == 0) { Empty("No metrics".t()); return }
    m.keys().forEach { k ->
        val v = m.opt(k)
        val txt = when {
            v == null || v == JSONObject.NULL -> "–"
            v is Number && (k.endsWith("_pct") || k == "diff_pct" || k == "pace_pct") -> signedPct(v.toDouble())
            v is Number && (k.contains("cost") || k.contains("spend") || k.contains("cpi") || k.contains("cpa") || k.contains("budget") || k.contains("usual") || k == "now" || k.contains("cpc")) -> money(v.toDouble(), cur)
            v is Number && k.contains("roas") -> num(v.toDouble() * 100, 0) + "%"
            v is Number -> num(v.toDouble(), if (v.toDouble() % 1.0 == 0.0) 0 else 2)
            v is JSONArray -> (0 until v.length()).joinToString(", ") { v.opt(it).toString() }
            else -> v.toString()
        }
        KV(gText(k), txt)
    }
}

// ---------------------------------------------------------------- windows table (today vs yesterday / weekday / 3-7-14-30 days)

@Composable
fun WindowsTable(w: JSONObject, cur: String, kind: String = "spend") {
    val order = listOf("today", "yesterday_same", "weekday_same", "usual", "d3", "d7", "d14", "d30").filter { w.has(it) }
    val headers = when (kind) {
        "roas" -> listOf("Window".t(), "Spend".t(), "Value".t(), "ROAS")
        "cpi" -> listOf("Window".t(), "Spend".t(), "Installs".t(), "CPI")
        else -> listOf("Window".t(), "Spend".t(), "Impr.".t(), "Installs".t())
    }
    Table(headers, order.map { k ->
        val x = w.obj(k)
        val label = x.s("label", k).t() + if (x.b("partial")) " *" else ""
        when (kind) {
            "roas" -> listOf(label, money(x.dn("cost"), cur, 0), x.dn("conv_value")?.let { money(it, cur, 0) } ?: "–", x.dn("roas")?.let { num(it * 100, 0) + "%" } ?: "–")
            "cpi" -> listOf(label, money(x.dn("cost"), cur, 0), num(x.dn("conversions"), 0), money(x.dn("cpi"), cur))
            else -> listOf(label, money(x.dn("cost"), cur, 0), short(x.dn("impressions")), num(x.dn("conversions"), 0))
        }
    }, firstWeight = 2.2f)
}

// ---------------------------------------------------------------- dialogs

/** Ek number threshold badalna (khali = default). */
@Composable
fun ThresholdDialog(title: String, scope: String, key: String, current: String, default: String, vm: GadsVm, onDone: () -> Unit, onClose: () -> Unit) {
    var v by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column {
                Small(gText(key) + " · " + scopeText(scope))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(v, { v = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, singleLine = true, label = { Text("Value".t()) },
                    placeholder = { Text("default".t() + " " + default) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Small("Leave empty to use the default.".t())
            }
        },
        confirmButton = { TextButton(onClick = { vm.act("threshold", mapOf("scope" to scope, "key" to key, "value" to v.trim()), busyKey = "th$scope$key", ok = "Saved".t()) { onDone() }; onClose() }) { Text("Save".t()) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel".t()) } })
}

fun scopeText(s: String): String = when {
    s == "global" -> "all accounts".t()
    s.startsWith("acct:") -> "account".t() + " " + s.removePrefix("acct:")
    s.startsWith("camp:") -> "campaign".t() + " " + s.substringAfterLast(':') + " · CID " + s.split(':').getOrElse(1) { "" }
    s.startsWith("country:") -> "country".t() + " " + s.substringAfterLast(':') + " · CID " + s.split(':').getOrElse(1) { "" }
    else -> s
}

/** Naya token: sirf ek baar dikhta hai (server ke paas sirf hash). Script me paste karna hai. */
@Composable
fun TokenDialog(cid: String, token: String, vm: GadsVm, g: GState, onClose: () -> Unit) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    AlertDialog(onDismissRequest = {},
        title = { Text("Script token".t()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Small("Shown only once. The server keeps only a hash. Put it in this account's read-only script.".t())
                Spacer(Modifier.height(8.dp))
                Mono(token)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clip.setText(AnnotatedString(token)) }, modifier = Modifier.weight(1f)) { Text("Copy".t()) }
                    BusyButton("Share script".t(), "script$cid" in g.busy, Modifier.weight(1.3f)) {
                        vm.script(cid) { text, ph -> shareText(ctx, "MFC Dash read-only Google Ads script · $cid", text.replace(ph, token)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Small("Google Ads > Tools > Bulk actions > Scripts > + New script: paste, Authorize, Preview, then schedule it Hourly. The script only reads (search) and sends numbers to your dashboard.".t())
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done".t()) } })
}

/** Account jodna / badalna. Naya account sirf saaf authorization ke saath. Kabhi link / MCC nahi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(acc: GAccount?, preCid: String, preName: String, g: GState, vm: GadsVm, onSaved: (String, String?) -> Unit, onClose: () -> Unit) {
    var cid by remember { mutableStateOf(acc?.cidFmt ?: preCid) }
    var company by remember { mutableStateOf(acc?.company ?: "") }
    var name by remember { mutableStateOf(acc?.accountName ?: preName) }
    var mcc by remember { mutableStateOf(acc?.mccLabel ?: "") }
    var invoice by remember { mutableStateOf(acc?.invoiceRef ?: "") }
    var credit by remember { mutableStateOf(acc?.creditRef ?: "") }
    var cur by remember { mutableStateOf(acc?.currency ?: "") }
    var tz by remember { mutableStateOf(acc?.tz ?: "") }
    var notes by remember { mutableStateOf(acc?.notes ?: "") }
    var srcDash by remember { mutableStateOf(acc?.srcDashboard ?: true) }
    var srcScript by remember { mutableStateOf(acc?.srcScript ?: false) }
    var srcEmail by remember { mutableStateOf(acc?.srcEmail ?: false) }
    var auth by remember { mutableStateOf(acc != null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            Text(if (acc == null) "Add Google Ads account".t() else "Edit account".t(), style = MaterialTheme.typography.titleLarge)
            Small("Each account stays separate: its own company, MCC, invoice, credit line, currency and time zone. Nothing is linked.".t())
            Spacer(Modifier.height(10.dp))
            val field = @Composable { v: String, set: (String) -> Unit, label: String, enabled: Boolean ->
                OutlinedTextField(v, set, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
            field(cid, { cid = it.filter { c -> c.isDigit() || c == '-' }.take(12) }, "Customer ID (xxx-xxx-xxxx)".t(), acc == null)
            field(company, { company = it.take(160) }, "Company *".t(), true)
            field(name, { name = it.take(255) }, "Account name".t(), true)
            field(mcc, { mcc = it.take(120) }, "MCC (label only, never linked)".t(), true)
            field(invoice, { invoice = it.take(120) }, "Invoice / billing reference".t(), true)
            field(credit, { credit = it.take(120) }, "Credit line reference".t(), true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cur, { cur = it.filter { c -> c.isLetter() }.uppercase().take(3) }, label = { Text("Currency".t()) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(tz, { tz = it.take(64) }, label = { Text("Time zone".t()) }, placeholder = { Text("Asia/Kolkata") }, singleLine = true, modifier = Modifier.weight(1.6f))
            }
            Small("Empty = taken from Google Ads data.".t())
            field(notes, { notes = it.take(1000) }, "Notes".t(), true)
            GLabel("Data sources".t())
            CheckLine("Existing dashboard data".t(), srcDash) { srcDash = it }
            CheckLine("Read-only Ads Script in this account (hourly)".t(), srcScript) { srcScript = it }
            CheckLine("Google Ads notification emails".t(), srcEmail) { srcEmail = it }
            if (acc == null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.extra.warn.copy(alpha = .10f)).clickable { auth = !auth }.padding(6.dp)) {
                    Checkbox(auth, { auth = it })
                    Text("I am authorized to monitor this account. Read-only: no changes, no account or MCC linking.".t(), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            BusyButton(if (acc == null) "Add account".t() else "Save".t(), "account_save" in g.busy, Modifier.fillMaxWidth().height(46.dp),
                enabled = cid.count { it.isDigit() } == 10 && company.isNotBlank() && auth) {
                vm.act("account_save", mapOf("cid" to cid, "company" to company.trim(), "account_name" to name.trim(), "mcc_label" to mcc.trim(), "invoice_ref" to invoice.trim(),
                    "credit_ref" to credit.trim(), "currency" to cur, "tz" to tz.trim(), "notes" to notes.trim(), "src_dashboard" to if (srcDash) "1" else "0",
                    "src_script" to if (srcScript) "1" else "0", "src_email" to if (srcEmail) "1" else "0", "authorize" to if (auth) "1" else "0"),
                    ok = "Saved".t()) { j -> onSaved(j.optString("cid"), j.optString("token").takeIf { it.isNotBlank() && it != "null" }) }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
fun CheckLine(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!on) }) {
        Checkbox(on, onChange); Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SwitchLine(text: String, sub: String?, on: Boolean, busy: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (sub != null) Small(sub)
        }
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Switch(on, onChange)
    }
}
