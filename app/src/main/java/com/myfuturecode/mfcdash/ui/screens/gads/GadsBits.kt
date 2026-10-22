package com.myfuturecode.mfcdash.ui.screens.gads

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.data.GAccount
import com.myfuturecode.mfcdash.data.GAlert
import com.myfuturecode.mfcdash.data.GEvent
import com.myfuturecode.mfcdash.data.GRun
import com.myfuturecode.mfcdash.data.checks
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.GadsVm
import com.myfuturecode.mfcdash.ui.UiState
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDivider
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KLoading
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KSwitch
import com.myfuturecode.mfcdash.ui.kit.fg
import com.myfuturecode.mfcdash.ui.kit.icon
import com.myfuturecode.mfcdash.ui.kit.kRoasColor
import com.myfuturecode.mfcdash.ui.kit.shape
import com.myfuturecode.mfcdash.ui.kit.word
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.mapObj
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.obj
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.short
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tOnly
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import kotlin.math.abs

// ---------------------------------------------------------------- text

/** Tabular text style (kit jaisa). */
internal fun gStyle(size: TextUnit, weight: FontWeight = FontWeight.Normal, lh: TextUnit = TextUnit.Unspecified): TextStyle =
    TextStyle(fontSize = size, fontWeight = weight, lineHeight = lh, fontFeatureSettings = "tnum")

/** Kit rang ka sada text (lamba ho to "..."). */
@Composable
internal fun GTxt(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal,
    lh: TextUnit = TextUnit.Unspecified, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null,
) {
    Text(text, modifier, color = color, style = gStyle(size, weight, lh), maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)
}

// ---------------------------------------------------------------- status (kit kind)

/** Alert level: CRITICAL = Turant, WARNING = Dhyan do, INFO = sada. */
internal fun gLevelKind(l: String): KKind = when (l.uppercase()) {
    "CRITICAL" -> KKind.BAD
    "WARNING" -> KKind.WARN
    else -> KKind.NEUTRAL
}

internal fun gLevelWord(l: String): String = when (l.uppercase()) {
    "CRITICAL" -> KKind.BAD.word()
    "WARNING" -> KKind.WARN.word()
    "INFO" -> "Info".t()
    else -> gText(l)
}

/** Server ka state (HEALTHY, PAUSED, LOW ...) -> kit kind. */
internal fun gStateKind(s: String): KKind = when (s.uppercase()) {
    "HEALTHY", "OK", "SUCCESS", "ENABLED", "CONFIRMED_OK", "BEST", "GOOD", "APPROVED", "REINSTATED" -> KKind.OK
    "CRITICAL", "FAILED", "SUSPENDED", "ERROR", "BAD", "LOW", "DISAPPROVED", "REMOVED" -> KKind.BAD
    "WARNING", "WARN", "PARTIAL", "STALE", "NO_DATA", "PAUSED", "LIMITED", "APPROVED_LIMITED", "SUSPECTED" -> KKind.WARN
    "RUNNING", "LEARNING", "PENDING", "UNDER_REVIEW" -> KKind.ASK
    else -> KKind.NEUTRAL
}

/** Tile / pill ka icon (NEUTRAL = info). */
internal fun gKindIcon(k: KKind): ImageVector = k.icon() ?: KIcons.Info

@Composable
fun levelColor(l: String): Color = gLevelKind(l).let { if (it == KKind.NEUTRAL) Mfc.c.ink3 else it.fg() }

@Composable
fun gStateColor(s: String): Color = gStateKind(s).let { if (it == KKind.NEUTRAL) Mfc.c.ink3 else it.fg() }

fun gText(s: String) = s.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }.t()

// ---------------------------------------------------------------- money / time

/** Currency ka nishaan: INR ₹, USD $, EUR €, GBP £, baaki code. */
internal fun gSym(cur: String): String = when (cur.trim().uppercase()) {
    "INR" -> "₹"
    "USD" -> "\$"
    "EUR" -> "€"
    "GBP" -> "£"
    "" -> ""
    else -> cur.trim().uppercase() + " "
}

internal fun gIsInr(cur: String): Boolean = cur.trim().equals("INR", ignoreCase = true)

/** Table ke khane ke liye chhota: 1 lakh se upar ₹2.90 L / $290K / $1.20M, warna gCash(…, 0). */
internal fun gCashS(v: Double?, cur: String): String {
    if (v == null || !v.isFinite()) return "–"
    val a = abs(v)
    if (a < 1_00_000) return gCash(v, cur, 0)
    val body = when {
        gIsInr(cur) -> short(a)
        a >= 1_000_000 -> num(a / 1_000_000, 2) + "M"
        else -> num(a / 1000, 0) + "K"
    }
    return (if (v < 0) "−" else "") + gSym(cur) + body
}

/** Account ki apni currency me: ₹6,450 · $120 · $12.40 (dec na do to 100 se chhote par 2). */
internal fun gCash(v: Double?, cur: String, dec: Int? = null): String {
    if (v == null || !v.isFinite()) return "–"
    val d = dec ?: if (v != 0.0 && abs(v) < 100) 2 else 0
    return (if (v < 0) "−" else "") + gSym(cur) + num(abs(v), d)
}

/** ₹ me (sirf INR / USD; USD server ke inr_per_usd se). Pata na ho to null. */
internal fun gToInr(v: Double, cur: String, usdInr: Double?): Double? = when (cur.trim().uppercase()) {
    "INR" -> v
    "USD" -> usdInr?.takeIf { it > 0 && it.isFinite() }?.let { v * it }
    else -> null
}

/** ROAS ratio -> "142%". */
internal fun gRoas(v: Double?): String = if (v == null || !v.isFinite()) "–" else num(v * 100, 0) + "%"

/** Account ki currency ke saath paisa (kabhi do currency jodte nahi). */
fun money(v: Double?, cur: String, dec: Int = 2): String =
    if (v == null || !v.isFinite()) "–" else (if (v < 0) "−" else "") + gSym(cur) + num(abs(v), dec)

fun signedPct(v: Double?): String = if (v == null || !v.isFinite()) "–" else (if (v > 0) "+" else if (v < 0) "−" else "") + num(abs(v), 1) + "%"

fun ageMin(m: Int?): String = when {
    m == null -> "never".t()
    m < 1 -> "just now".t()
    m < 120 -> tf("%s min ago", m)
    m < 2880 -> tf("%s hours ago", m / 60)
    else -> tf("%s days ago", m / 1440)
}

/** Chhota: "12 min", "4 hours", "2 days". */
internal fun gAgeShort(m: Int?): String = when {
    m == null -> "–"
    m < 120 -> tf("%s min", m)
    m < 2880 -> tf("%s hours", m / 60)
    else -> tf("%s days", m / 1440)
}

/** Aaj ka ho to "09:12", warna "20 Oct". */
internal fun gWhen(ts: String?): String {
    val ms = tsMs(ts) ?: return "–"
    val a = Calendar.getInstance().apply { timeInMillis = ms }
    val now = Calendar.getInstance()
    val today = a.get(Calendar.YEAR) == now.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    return if (today) tOnly(ts) else tFull(ts).substringBeforeLast(' ')
}

internal fun gAgo(ts: String?): String = tsMs(ts)?.let { agoText(it) } ?: "–"

fun JSONObject.dOrNull(k: String): Double? = dn(k)

fun shareText(ctx: Context, subject: String, text: String) {
    runCatching {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text), subject)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun updatedText(g: GState) = if (g.lastOk == 0L) "Loading…".t() else "Updated".t() + " " + agoText(g.lastOk)

/** Back: stack khali ho to Home. */
internal fun gBack(nav: NavHostController): () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") }

// ---------------------------------------------------------------- shared context (₹ rate, ROAS target, limits)

private fun Context.gActivity(): ComponentActivity? {
    var c: Context? = this
    while (c != null) {
        if (c is ComponentActivity) return c
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}

/** MainActivity wala hi DashVm (activity scope) ka state. Activity na mile to null. */
@Composable
private fun gDash(): UiState? {
    val act = LocalContext.current.gActivity() ?: return null
    val app = act.application as? App ?: return null
    val dvm: DashVm = viewModel(viewModelStoreOwner = act, factory = DashVm.factory(app))
    val st by dvm.s.collectAsStateWithLifecycle()
    return st
}

/** ₹ per $ (main dashboard ke data se, server ka inr_per_usd). Data na aaya ho to null. */
@Composable
internal fun gUsdInr(): Double? = gDash()?.data?.inrPerUsd?.takeIf { it > 0 && it.isFinite() }

/** ROAS target (Settings; 1.0 = 100%). DashVm se (badle to turant), warna Prefs. */
@Composable
internal fun gRoasT(): Double {
    val ctx = LocalContext.current
    val st = gDash()
    val pref = remember(ctx) { (ctx.applicationContext as? App)?.prefs?.roasTarget ?: 1.5 }
    return st?.roasTarget ?: pref
}

/** Pace ki global limits (drop %, spike %): Settings view load ho to wahan se, warna 40 / 60. */
internal fun gPaceLimits(g: GState): Pair<Double, Double> {
    val th = g.view("settings")?.arr("thresholds")?.mapObj { it }.orEmpty()
    fun v(k: String, d: Double): Double = th.firstOrNull { it.s("key") == k }?.let { it.dn("value") ?: it.dn("default") }?.takeIf { it > 0 } ?: d
    return v("drop_pct", 40.0) to v("spike_pct", 60.0)
}

/** Screen khuli rahe tab tak har [ms] par [tick] (pehli baar turant). App peeche ho to ruk jata hai. */
@Composable
internal fun gPoll(ms: Long, vararg keys: Any?, tick: () -> Unit) {
    val t by rememberUpdatedState(tick)
    val life = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(life, *keys) { life.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { t(); delay(ms) } } }
}

/** Data kitna purana: "Data 4 ghante late". */
internal fun gLate(min: Int): String = tf("Data %s late", gAgeShort(min))

/** Account ka haal: (kind, chhota shabd). Overview aur account view dono se. */
internal fun gAcctState(
    monitoring: Boolean, health: String, age: Int?, fresh: Boolean, crit: Int, warn: Int, pace: Double?, drop: Double, spike: Double,
): Pair<KKind, String> {
    val paceTxt = pace?.takeIf { it.isFinite() }?.let { p ->
        when {
            // KPaceMeter jaisa hi: seema par = Normal
            p < -drop -> tf("Spend %s lower", num(-p, 0) + "%")
            p > spike -> tf("Spend %s higher", num(p, 0) + "%")
            else -> null
        }
    }
    val fresh0 = health.isBlank() || health.equals("UNKNOWN", ignoreCase = true)
    return when {
        !monitoring -> KKind.NEUTRAL to "Monitoring off".t()
        fresh0 && age == null -> KKind.NEUTRAL to "Just added".t()
        age == null -> KKind.BAD to "No data".t()
        !fresh -> KKind.WARN to gLate(age)
        crit > 0 -> KKind.BAD to tf("%s alerts", crit + warn)
        warn > 0 -> KKind.WARN to (paceTxt ?: tf("%s alerts", warn))
        paceTxt != null -> KKind.WARN to paceTxt
        else -> KKind.OK to KKind.OK.word()
    }
}

/** Limits ke chhote naam (Settings / account / campaign). */
internal fun gThLabel(k: String): String = when (k) {
    "drop_pct" -> "Spend drop %".t()
    "spike_pct" -> "Spend spike %".t()
    "cpi_over_pct" -> "CPI over target %".t()
    "zero_hours" -> "Zero spend hours".t()
    "min_impr_pace" -> "Min usual impressions".t()
    "min_spend_pace" -> "Min usual spend".t()
    "min_installs" -> "Min installs".t()
    "min_baseline_days" -> "Baseline days".t()
    "stale_hours" -> "Stale after (hours)".t()
    "spend_lag_hours" -> "Spend lag (hours)".t()
    "conv_lag_hours" -> "Install lag (hours)".t()
    "roas_under_pct" -> "ROAS below target %".t()
    "install_drop_pct" -> "Install drop %".t()
    "learning_days" -> "Learning days".t()
    "minor_pct" -> "Info below %".t()
    "cpi_target" -> "CPI target".t()
    "roas_target" -> "ROAS target".t()
    else -> gText(k)
}

// ---------------------------------------------------------------- frame bits

@Composable
fun GProblem(g: GState, onRetry: () -> Unit) {
    val c = Mfc.c
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (g.missing) KIconTile(KIcons.Dhyan, KKind.WARN) else KIconTile(KIcons.Turant, KKind.BAD)
            Column(Modifier.weight(1f)) {
                GTxt(if (g.missing) "Not on server".t() else "Error".t(), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 1)
                GTxt(
                    if (g.missing) "api/gads_health.php · api/gads_ingest.php · lib/gads_health.php · lib/gads_rules.php" else g.error ?: "No data received".t(),
                    13.sp, if (g.missing) c.ink2 else c.red, Modifier.padding(top = 2.dp), lh = 18.sp, maxLines = 4,
                )
            }
        }
        KButton("Try again".t(), onRetry, Modifier.fillMaxWidth().padding(top = 12.dp), kind = KBtn.SOFT, icon = KIcons.Refresh)
    }
}

/** View ka data ya loading / error. */
@Composable
fun GLoad(g: GState, j: JSONObject?, retry: () -> Unit, content: @Composable (JSONObject) -> Unit) {
    if (j == null) { if (g.error != null) GProblem(g, retry) else KLoading(); return }
    content(j)
}

/** Neeche chhoti line: monitor Google Ads me kuch nahi badalta. */
@Composable
fun ReadOnlyNote() {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(KIcons.Lock, null, tint = c.ink3, modifier = Modifier.size(14.dp))
        GTxt("Read-only".t(), 13.sp, c.ink3, lh = 18.sp, maxLines = 1)
    }
}

@Composable
fun GLabel(t: String) {
    GTxt(t, 13.sp, Mfc.c.ink3, Modifier.padding(top = 10.dp, bottom = 2.dp), weight = FontWeight.SemiBold, lh = 18.sp, maxLines = 1)
}

@Composable
fun Small(t: String, color: Color? = null, maxLines: Int = Int.MAX_VALUE) {
    GTxt(t, 13.sp, color ?: Mfc.c.ink2, lh = 18.sp, maxLines = maxLines)
}

/** Label baayein, lamba text value daayein (2-3 lines tak). */
@Composable
internal fun GKv(label: String, value: String, valueColor: Color = Color.Unspecified) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // value pehle naapo (zyada se zyada 180dp), label ko baaki jagah: chhote value par label nahi tootta
        GTxt(label, 15.sp, c.ink2, Modifier.weight(1f), lh = 20.sp, maxLines = 2)
        GTxt(value, 15.sp, valueColor.takeOrElse { c.ink }, Modifier.widthIn(max = 180.dp), weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 3, align = TextAlign.End)
    }
}

/** "→ kya karna hai" wali line. */
@Composable
internal fun GStep(text: String) {
    val c = Mfc.c
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(KIcons.Chev, null, tint = c.brand, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        GTxt(text, 14.sp, c.ink, Modifier.weight(1f), lh = 20.sp)
    }
}

/** Code / script / log (monospace, well rang). */
@Composable
internal fun GMono(text: String, maxLines: Int = Int.MAX_VALUE) {
    val c = Mfc.c
    Text(
        text, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.well).padding(12.dp),
        color = c.ink2, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

/** Chip (48dp touch): chuna hua = brand. */
@Composable
internal fun GChip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Mfc.c
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.heightIn(min = 48.dp).clip(shape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.height(36.dp).clip(shape).background(if (on) c.btnBg else c.card).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            GTxt(text, 14.sp, if (on) c.btnFg else c.ink2, weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * List card ki row: title (2 lines), status line (shape + rang), neeche lamba text, laal error.
 * KRow jaisi (16dp side), par body kai lines ki ho sakti hai.
 */
@Composable
internal fun GRow(
    title: String,
    modifier: Modifier = Modifier,
    status: Pair<String, KKind>? = null,
    body: String? = null,
    bodyLines: Int = 2,
    err: String? = null,
    lead: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    titleLines: Int = 2,
    onClick: (() -> Unit)? = null,
) {
    val c = Mfc.c
    var m = modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(
        m.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        lead?.invoke()
        Column(Modifier.weight(1f)) {
            GTxt(title, 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = titleLines)
            if (status != null && status.first.isNotBlank()) KStatus(status.first, status.second, Modifier.padding(top = 2.dp))
            if (!body.isNullOrBlank()) GTxt(body, 13.sp, c.ink2, Modifier.padding(top = 2.dp), lh = 18.sp, maxLines = bodyLines)
            if (!err.isNullOrBlank()) GTxt(err, 13.sp, c.red, Modifier.padding(top = 2.dp), lh = 18.sp, maxLines = 2)
        }
        trailing?.invoke()
        if (chevron) Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.size(22.dp))
    }
}

/** Switch wali row (busy = spinner). List card me. */
@Composable
internal fun GSwitchRow(title: String, sub: String?, on: Boolean, busy: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    val c = Mfc.c
    GRow(
        title, body = sub, titleLines = 1,
        lead = if (icon != null) { { KIconTile(icon) } } else null,
        trailing = {
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = c.brand, strokeWidth = 2.dp)
            KSwitch(on, onChange, enabled = !busy)
        },
    )
}

/** Chhoti note line: shape + rang + text (3 lines tak). Card ke andar (bina side padding). */
@Composable
internal fun GNote(text: String, kind: KKind, modifier: Modifier = Modifier) {
    val c = Mfc.c
    val col = if (kind == KKind.NEUTRAL) c.ink2 else kind.fg()
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val sh = kind.shape()
        if (sh != null) Icon(sh, null, tint = col, modifier = Modifier.padding(top = 3.dp).size(13.dp))
        GTxt(text, 13.sp, col, Modifier.weight(1f), weight = FontWeight.Medium, lh = 18.sp, maxLines = 3)
    }
}

/** Creative asset: naam + kis tarah ka / impr. / CTR, daayein Google ka label (Low / Good / Best). Card ke andar. */
@Composable
internal fun GAssetRow(a: JSONObject, ctr: Boolean) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            GTxt(a.s("name").ifBlank { a.s("url") }.ifBlank { a.s("asset_id") }, 14.sp, c.ink, weight = FontWeight.Medium, lh = 18.sp, maxLines = 1)
            val meta = listOfNotNull(
                gText(a.s("field_type")).ifBlank { null }, if (ctr) gText(a.s("asset_type")).ifBlank { null } else null,
                short(a.dn("impressions")) + " " + "impr.".t(), if (ctr) "CTR " + pct(a.dn("ctr"), 2) else null,
            )
            GTxt(meta.joinToString(" · "), 13.sp, c.ink3, lh = 18.sp, maxLines = 1)
        }
        val l = a.s("label")
        KPill(if (l.isBlank()) "–" else gText(l), gStateKind(l))
    }
}

/** Text field ke liye saada number: 40, 12.5 (bina comma / exponent). */
internal fun gPlain(v: Double?): String =
    if (v == null || !v.isFinite()) "" else BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Limit ki value dikhane ke liye: CPI target paisa, ROAS target %, _pct %, baaki number. */
internal fun gThVal(k: String, v: Double?, cur: String): String = when {
    v == null || !v.isFinite() -> "–"
    k == "cpi_target" -> gCash(v, cur, 2)
    k == "roas_target" -> gRoas(v)
    k.endsWith("_pct") -> num(v, if (v % 1.0 == 0.0) 0 else 1) + "%"
    else -> num(v, if (v % 1.0 == 0.0) 0 else 2)
}

// ---------------------------------------------------------------- table

/** Ek cell: pehla column text (2 lines), baaki right. wide = fixed width (side scroll). */
@Composable
private fun RowScope.GCell(s: String, i: Int, header: Boolean, wide: Boolean, firstWeight: Float, color: Color) {
    val c = Mfc.c
    val m = if (wide) Modifier.width(if (i == 0) 128.dp else 76.dp) else if (i == 0) Modifier.weight(firstWeight) else Modifier.weight(1f)
    GTxt(
        s, if (header) 13.sp else 14.sp, if (header) c.ink3 else color.takeOrElse { c.ink }, m.padding(horizontal = 3.dp),
        weight = if (header) FontWeight.SemiBold else if (i == 0) FontWeight.Medium else FontWeight.Normal,
        lh = 18.sp, maxLines = if (i == 0 && !header) 2 else 1, align = if (i == 0) TextAlign.Start else TextAlign.End,
    )
}

/** Number column ki kam se kam chaudai (isse kam jagah ho to table side scroll). */
private val G_COL_MIN = 66.dp

/**
 * Table (kit rang): header + rows. Jagah kam ho (firstMin + har column 66dp) tabhi side scroll, warna poori chaudai.
 * onRow = row tap (48dp). tint = cell ka rang (ROAS).
 */
@Composable
internal fun GTable(
    headers: List<String>,
    rows: List<List<String>>,
    empty: String = "No data".t(),
    firstWeight: Float = 1.6f,
    onRow: ((Int) -> Unit)? = null,
    tint: (Int, Int) -> Color = { _, _ -> Color.Unspecified },
    firstMin: Dp = 96.dp,
) {
    val c = Mfc.c
    if (rows.isEmpty()) {
        if (empty.isNotBlank()) KEmpty(empty)
        return
    }
    val chev = if (onRow != null) 18.dp else 0.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth < firstMin + G_COL_MIN * (headers.size - 1) + chev
        val scroll = rememberScrollState()
        var box = Modifier.fillMaxWidth()
        if (wide) box = box.horizontalScroll(scroll).width(128.dp + 76.dp * (headers.size - 1) + chev)
        Column(box) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                headers.forEachIndexed { i, h -> GCell(h, i, true, wide, firstWeight, Color.Unspecified) }
                if (onRow != null) Spacer(Modifier.width(chev))
            }
            KDivider()
            rows.forEachIndexed { ri, r ->
                var rm = Modifier.fillMaxWidth().heightIn(min = if (onRow != null) 48.dp else 40.dp)
                if (onRow != null) rm = rm.clickable { onRow(ri) }
                Row(rm.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    r.forEachIndexed { i, s -> GCell(s, i, false, wide, firstWeight, tint(ri, i)) }
                    if (onRow != null) Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.size(chev))
                }
                if (ri < rows.lastIndex) KDivider()
            }
        }
    }
}

/** Ghante ka kharcha: aaj (brand) vs aam (grey), 24 bars. */
@Composable
internal fun GHourBars(now: JSONArray, usual: JSONArray) {
    val c = Mfc.c
    val n = (0 until 24).map { h -> if (now.isNull(h)) null else now.optDouble(h).takeIf { it.isFinite() } }
    val u = (0 until 24).map { h -> if (usual.isNull(h)) null else usual.optDouble(h).takeIf { it.isFinite() } }
    val maxV = (n + u).maxOf { it ?: 0.0 }.coerceAtLeast(1e-9)
    val brand = c.brand
    val grey = c.zGrey.copy(alpha = .45f)
    val tick = c.tick
    Canvas(Modifier.fillMaxWidth().padding(top = 8.dp).height(110.dp)) {
        val slot = size.width / 24f
        val bw = slot * .7f
        val r = CornerRadius(2.dp.toPx())
        for (h in 0 until 24) {
            val x = h * slot + (slot - bw) / 2f
            val uv = u[h]
            if (uv != null && uv > 0) {
                val bh = (uv / maxV).toFloat() * size.height
                drawRoundRect(grey, Offset(x, size.height - bh), Size(bw, bh), r)
            }
            val nv = n[h]
            if (nv != null && nv > 0) {
                val bh = ((nv / maxV).toFloat() * size.height).coerceAtLeast(2.dp.toPx())
                drawRoundRect(brand, Offset(x + bw * .2f, size.height - bh), Size(bw * .6f, bh), r)
            }
        }
        drawLine(tick, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        listOf("00:00", "06:00", "12:00", "18:00").forEach { GTxt(it, 12.sp, c.ink3, Modifier.weight(1f), lh = 16.sp, maxLines = 1) }
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(brand))
        GTxt("Today".t(), 13.sp, c.ink2, lh = 18.sp, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(grey))
        GTxt("Usual".t(), 13.sp, c.ink2, lh = 18.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- rows

/** Alert ki row (list card me): tile + title + status + kahan + kab. */
@Composable
fun GAlertRow(a: GAlert, nav: NavHostController, showAccount: Boolean = true) {
    val c = Mfc.c
    val k = if (a.open) gLevelKind(a.level) else KKind.OK
    val status = listOfNotNull(
        if (a.open) gLevelWord(a.level) else "Closed".t(), gText(a.klass).ifBlank { null }, if (a.sim) "Simulated".t() else null,
    ).joinToString(" · ")
    val where = listOfNotNull(
        if (showAccount) a.company.ifBlank { null } else null, if (showAccount) "CID " + a.cidFmt else null,
        a.campaign.ifBlank { null }, a.country.ifBlank { null },
    ).joinToString(" · ")
    GRow(
        a.titleL, status = status to k, body = where, bodyLines = 1,
        lead = { KIconTile(gKindIcon(k), k) },
        trailing = { GTxt(gWhen(a.firstSeen), 13.sp, c.ink3, lh = 18.sp, maxLines = 1) },
        onClick = { nav.navigate("gads/alert/${a.id}") },
    )
}

@Composable
fun GAlertList(list: List<GAlert>, nav: NavHostController, empty: String, showAccount: Boolean = true) {
    KCard(list = true) {
        if (list.isEmpty()) KEmpty(empty, KIcons.Ok)
        list.forEachIndexed { i, a ->
            if (i > 0) KListDivider(lead = true)
            GAlertRow(a, nav, showAccount)
        }
    }
}

internal fun gRunIcon(t: String): ImageVector = when (t) {
    "scan" -> KIcons.Refresh
    "email" -> KIcons.Send
    "deep", "ai" -> KIcons.Spark
    "daily" -> KIcons.Calendar
    "simulation" -> KIcons.Wand
    else -> KIcons.History
}

/** Run ki row: type + status + kab / kitne accounts. */
@Composable
fun GRunRow(r: GRun, nav: NavHostController) {
    val k = gStateKind(r.status)
    val status = listOfNotNull(
        gText(r.status), if (r.issues > 0) tf("%s issues", r.issues) else null, if (r.failedChecks > 0) tf("%s failed checks", r.failedChecks) else null,
    ).joinToString(" · ")
    val body = listOfNotNull(
        tFull(r.startedAt), r.trigger.ifBlank { null }, if (r.accounts > 0) tf("%s accounts", r.accounts) else null,
        if (r.campaigns > 0) tf("%s campaigns", r.campaigns) else null, r.uid.ifBlank { null },
    ).joinToString(" · ")
    GRow(
        runType(r.type) + if (r.sim) " · " + "Simulated".t() else "", status = status to k, body = body, err = r.errors.ifBlank { null },
        lead = { KIconTile(gRunIcon(r.type), k) }, chevron = true, titleLines = 1,
        onClick = { nav.navigate("gads/run/${r.id}") },
    )
}

fun runType(t: String) = when (t) {
    "scan" -> "Scan".t(); "email" -> "Email check".t(); "deep" -> "Deep AI analysis".t(); "daily" -> "Daily review".t(); "simulation" -> "Simulation".t(); "ai" -> "AI analysis".t(); else -> t
}

/** Log ki ek line: shape + time + baat. Alert se juda ho to tap = alert. */
@Composable
fun GEventRow(e: GEvent, nav: NavHostController?) {
    val c = Mfc.c
    val k = when (e.level) {
        "error" -> KKind.BAD
        "warn" -> KKind.WARN
        "ok" -> KKind.OK
        else -> KKind.NEUTRAL
    }
    val id = e.alertId
    val tap = nav != null && id != null
    var m = Modifier.fillMaxWidth().heightIn(min = if (tap) 48.dp else 36.dp)
    if (nav != null && id != null) m = m.clickable { nav.navigate("gads/alert/$id") }
    Row(m.padding(vertical = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val sh = k.shape()
        Box(Modifier.padding(top = 3.dp).size(12.dp), contentAlignment = Alignment.Center) {
            if (sh != null) Icon(sh, null, tint = k.fg(), modifier = Modifier.size(12.dp))
            else Box(Modifier.size(7.dp).clip(CircleShape).background(c.zGrey))
        }
        GTxt(tOnly(e.createdAt), 13.sp, c.ink3, lh = 18.sp, maxLines = 1)
        GTxt(e.msg, 14.sp, c.ink, Modifier.weight(1f), lh = 18.sp)
        if (tap) Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.size(18.dp))
    }
}

/** Rule engine checks: OK / WARN / INSUFFICIENT + text (list card me). */
@Composable
fun ChecksList(j: JSONObject?) {
    val list = j?.checks().orEmpty()
    if (list.isEmpty()) {
        KEmpty("No checks yet".t(), KIcons.Clock)
        return
    }
    list.forEachIndexed { i, (k, st, txt) ->
        if (i > 0) KListDivider(lead = true)
        val kind = gStateKind(st)
        GRow(gText(k), status = gText(st) to kind, body = txt, bodyLines = 3, titleLines = 1, lead = { KIconTile(gKindIcon(kind), kind, 36.dp) })
    }
}

/** Data sources: dashboard / script / email, kitna purana. */
@Composable
fun SourcesList(src: JSONObject?) {
    val c = Mfc.c
    if (src == null || src.length() == 0) {
        KStatus("No data source".t(), KKind.BAD, Modifier.padding(vertical = 10.dp))
        return
    }
    src.keys().forEach { k ->
        val o = src.optJSONObject(k) ?: return@forEach
        val age = o.dn("age_min")?.toInt()
        val label = when (k) {
            "dashboard" -> "Existing dashboard data".t()
            "script" -> "Read-only Ads Script".t()
            "email" -> "Google Ads emails".t()
            else -> k
        }
        val extra = listOfNotNull(o.sn("version")?.let { "v$it" }, if (k == "email" && !o.b("configured")) "not configured".t() else null).joinToString(" · ")
        GKv(
            label, (if (age == null) "no data yet".t() else ageMin(age)) + (if (extra.isNotBlank()) " · $extra" else ""),
            valueColor = if (age == null) c.red else if (age > 180) c.amber else Color.Unspecified,
        )
    }
}

/** metrics JSON (server ke numbers) ko label: value list me. */
@Composable
fun MetricsList(m: JSONObject, cur: String) {
    if (m.length() == 0) {
        KEmpty("No metrics".t())
        return
    }
    val roasT = gRoasT()
    m.keys().forEach { k ->
        val v = m.opt(k)
        val txt = when {
            v == null || v == JSONObject.NULL -> "–"
            v is Number && (k.endsWith("_pct") || k == "diff_pct" || k == "pace_pct") -> signedPct(v.toDouble())
            v is Number && (k.contains("cost") || k.contains("spend") || k.contains("cpi") || k.contains("cpa") || k.contains("budget") || k.contains("usual") || k == "now" || k.contains("cpc")) -> money(v.toDouble(), cur)
            v is Number && k.contains("roas") -> gRoas(v.toDouble())
            v is Number -> num(v.toDouble(), if (v.toDouble() % 1.0 == 0.0) 0 else 2)
            v is JSONArray -> (0 until v.length()).joinToString(", ") { v.opt(it).toString() }
            else -> v.toString()
        }
        val col = if (k == "roas" && v is Number) kRoasColor(v.toDouble(), roasT) else Color.Unspecified
        GKv(gText(k), txt, col)
    }
}

// ---------------------------------------------------------------- windows table (today vs yesterday / weekday / 3-7-14-30 days)

private fun gWindowName(k: String): String = when (k) {
    "today" -> "Today".t()
    "yesterday_same" -> "Yesterday".t()
    "weekday_same" -> "Same weekday".t()
    "usual" -> "Usual".t()
    "d3" -> "3 days".t()
    "d7" -> "7 days".t()
    "d14" -> "14 days".t()
    "d30" -> "30 days".t()
    else -> k
}

@Composable
fun WindowsTable(w: JSONObject, cur: String, kind: String = "spend") {
    val roasT = gRoasT()
    val order = listOf("today", "yesterday_same", "weekday_same", "usual", "d3", "d7", "d14", "d30").filter { w.has(it) }
    val objs = order.map { w.obj(it) }
    val headers = when (kind) {
        "roas" -> listOf("Window".t(), "Spend".t(), "Value".t(), "ROAS")
        "cpi" -> listOf("Window".t(), "Spend".t(), "Installs".t(), "CPI")
        else -> listOf("Window".t(), "Spend".t(), "Impr.".t(), "Installs".t())
    }
    val roasCols = objs.map { kRoasColor(it.dn("roas"), roasT) }
    GTable(headers, order.mapIndexed { i, k ->
        val x = objs[i]
        val label = gWindowName(k) + if (x.b("partial")) " *" else ""
        when (kind) {
            "roas" -> listOf(label, gCashS(x.dn("cost"), cur), x.dn("conv_value")?.let { gCashS(it, cur) } ?: "–", gRoas(x.dn("roas")))
            "cpi" -> listOf(label, gCashS(x.dn("cost"), cur), num(x.dn("conversions"), 0), gCash(x.dn("cpi"), cur, 2))
            else -> listOf(label, gCashS(x.dn("cost"), cur), short(x.dn("impressions")), num(x.dn("conversions"), 0))
        }
    }, firstWeight = 1.8f, tint = { ri, ci -> if (kind == "roas" && ci == 3) roasCols[ri] else Color.Unspecified })
}

// ---------------------------------------------------------------- dialogs

/** Ek number limit badalna (khali = default). */
@Composable
fun ThresholdDialog(title: String, scope: String, key: String, current: String, default: String, vm: GadsVm, onDone: () -> Unit, onClose: () -> Unit) {
    var v by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            KPageTokens {
                val c = Mfc.c
                Column {
                    GTxt(gThLabel(key), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
                    GTxt(scopeText(scope), 13.sp, c.ink3, lh = 18.sp, maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        v, { v = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, singleLine = true, label = { Text("Value".t()) },
                        placeholder = { Text("default".t() + " " + default.ifBlank { "–" }) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.act("threshold", mapOf("scope" to scope, "key" to key, "value" to v.trim()), busyKey = "th$scope$key", ok = "Saved".t()) { onDone() }
                onClose()
            }) { Text("Save".t()) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel".t()) } },
    )
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
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Script token".t()) },
        text = {
            KPageTokens {
                val c = Mfc.c
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    KStatus("Shown only once".t(), KKind.WARN)
                    Spacer(Modifier.height(8.dp))
                    GMono(token)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KButton("Copy".t(), { clip.setText(AnnotatedString(token)) }, Modifier.weight(1f), kind = KBtn.OUTLINE, icon = KIcons.Copy)
                        KButton(
                            "Share script".t(), { vm.script(cid) { text, ph -> shareText(ctx, "MFC Dash read-only Google Ads script · $cid", text.replace(ph, token)) } },
                            Modifier.weight(1.3f), icon = KIcons.Send, busy = "script$cid" in g.busy,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // script lagane ke 4 kadam (chhote labels)
                    listOf(
                        "Google Ads › Tools › Scripts".t(), "+ New script · paste".t(), "Authorize · Preview".t(), "Schedule: Hourly".t(),
                    ).forEachIndexed { i, s -> GTxt("${i + 1}. $s", 14.sp, c.ink2, Modifier.padding(top = 4.dp), lh = 20.sp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done".t()) } },
    )
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
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state) {
        KPageTokens {
            val c = Mfc.c
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
                GTxt(if (acc == null) "Add Google Ads account".t() else "Edit account".t(), 20.sp, c.ink, weight = FontWeight.Bold, lh = 26.sp, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                val field = @Composable { v: String, set: (String) -> Unit, label: String, enabled: Boolean ->
                    OutlinedTextField(
                        v, set, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                field(cid, { cid = it.filter { ch -> ch.isDigit() || ch == '-' }.take(12) }, "Customer ID (xxx-xxx-xxxx)".t(), acc == null)
                field(company, { company = it.take(160) }, "Company *".t(), true)
                field(name, { name = it.take(255) }, "Account name".t(), true)
                field(mcc, { mcc = it.take(120) }, "MCC (label only, never linked)".t(), true)
                field(invoice, { invoice = it.take(120) }, "Invoice / billing reference".t(), true)
                field(credit, { credit = it.take(120) }, "Credit line reference".t(), true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        cur, { cur = it.filter { ch -> ch.isLetter() }.uppercase().take(3) }, label = { Text("Currency".t()) }, placeholder = { Text("Auto".t()) },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        tz, { tz = it.take(64) }, label = { Text("Time zone".t()) }, placeholder = { Text("Auto".t()) }, singleLine = true, modifier = Modifier.weight(1.6f),
                    )
                }
                field(notes, { notes = it.take(1000) }, "Notes".t(), true)
                GLabel("Data sources".t())
                CheckLine("Existing dashboard data".t(), srcDash) { srcDash = it }
                CheckLine("Read-only Ads Script".t(), srcScript) { srcScript = it }
                CheckLine("Google Ads emails".t(), srcEmail) { srcEmail = it }
                if (acc == null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.amberSoft).clickable { auth = !auth }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(auth, { auth = it })
                        GTxt("I am authorized to monitor this account. Read-only: no changes, no account or MCC linking.".t(), 13.sp, c.ink, lh = 18.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                KButton(
                    if (acc == null) "Add account".t() else "Save".t(),
                    {
                        vm.act(
                            "account_save",
                            mapOf(
                                "cid" to cid, "company" to company.trim(), "account_name" to name.trim(), "mcc_label" to mcc.trim(), "invoice_ref" to invoice.trim(),
                                "credit_ref" to credit.trim(), "currency" to cur, "tz" to tz.trim(), "notes" to notes.trim(), "src_dashboard" to if (srcDash) "1" else "0",
                                "src_script" to if (srcScript) "1" else "0", "src_email" to if (srcEmail) "1" else "0", "authorize" to if (auth) "1" else "0",
                            ),
                            ok = "Saved".t(),
                        ) { j -> onSaved(j.optString("cid"), j.optString("token").takeIf { it.isNotBlank() && it != "null" }) }
                    },
                    Modifier.fillMaxWidth(),
                    enabled = cid.count { it.isDigit() } == 10 && company.isNotBlank() && auth,
                    busy = "account_save" in g.busy,
                )
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
fun CheckLine(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(on, onChange)
        GTxt(text, 15.sp, Mfc.c.ink, lh = 20.sp)
    }
}

/** Switch line (kit switch). Bina side padding: card ke andar seedha. */
@Composable
fun SwitchLine(text: String, sub: String?, on: Boolean, busy: Boolean = false, onChange: (Boolean) -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            GTxt(text, 15.sp, c.ink, weight = FontWeight.Medium, lh = 20.sp)
            if (sub != null) GTxt(sub, 13.sp, c.ink2, lh = 18.sp)
        }
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = c.brand, strokeWidth = 2.dp)
        KSwitch(on, onChange, enabled = !busy)
    }
}
