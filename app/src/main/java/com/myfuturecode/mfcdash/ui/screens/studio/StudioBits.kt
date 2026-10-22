package com.myfuturecode.mfcdash.ui.screens.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KLoading
import com.myfuturecode.mfcdash.ui.kit.KNum
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.fg
import com.myfuturecode.mfcdash.ui.kit.icon
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.agoText
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.l
import com.myfuturecode.mfcdash.util.mapObj
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.strings
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull
import com.myfuturecode.mfcdash.util.tsMs
import com.myfuturecode.mfcdash.util.usd
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------- status (shabd + shape + rang)

/** Har data point ka label: VERIFIED / THIRD-PARTY ESTIMATE / AI INFERENCE / UNKNOWN. */
@Composable
fun labelColor(l: String): Color {
    val c = Mfc.c
    return when (l) {
        "VERIFIED" -> c.green
        "ESTIMATE" -> c.brand
        "INFERENCE" -> c.amber
        else -> c.ink3
    }
}

fun labelText(l: String) = when (l) {
    "VERIFIED" -> "VERIFIED"; "ESTIMATE" -> "THIRD-PARTY ESTIMATE"; "INFERENCE" -> "AI INFERENCE"; else -> "UNKNOWN"
}

/** Label ka kind: VERIFIED = theek (check), AI INFERENCE = dhyan (triangle), baaki sada. */
internal fun labelKind(l: String): KKind = when (l) {
    "VERIFIED" -> KKind.OK
    "INFERENCE" -> KKind.WARN
    else -> KKind.NEUTRAL
}

@Composable
fun recColor(r: String): Color {
    val k = recKind(r)
    return if (k == KKind.NEUTRAL) Mfc.c.ink3 else k.fg()
}

/** Salah: STRONGLY CONSIDER / TEST FIRST / WATCH MARKET / REJECT. */
internal fun recKind(r: String): KKind = when (r) {
    "STRONGLY CONSIDER" -> KKind.OK
    "TEST FIRST" -> KKind.ASK
    "WATCH MARKET" -> KKind.WARN
    "REJECT" -> KKind.BAD
    else -> KKind.NEUTRAL
}

internal fun recText(r: String): String = when (r) {
    "STRONGLY CONSIDER" -> "Strongly consider".t()
    "TEST FIRST" -> "Test first".t()
    "WATCH MARKET" -> "Watch market".t()
    "REJECT" -> "Reject".t()
    else -> r
}

@Composable
fun stColor(s: String): Color {
    val k = stKind(s)
    return if (k == KKind.NEUTRAL) Mfc.c.ink3 else k.fg()
}

/** Run / gate / stage / source status ka kind. */
internal fun stKind(s: String): KKind = when (s.uppercase()) {
    "DONE", "OK", "APPROVED", "APPROVE", "SELECTED", "FEATURES_APPROVED", "PUBLISHED", "HIGH" -> KKind.OK
    "FAILED", "ERROR", "REJECTED", "REJECT", "CANCELLED", "LOW" -> KKind.BAD
    "PARTIAL", "BLOCKED", "CHANGES_REQUESTED", "REQUEST_CHANGES", "WATCH", "MEDIUM", "UNTESTED" -> KKind.WARN
    "WAITING", "FEATURES_PENDING", "NEW" -> KKind.ASK
    else -> KKind.NEUTRAL
}

/** Chal raha (RUNNING / QUEUED ...): pill par ghadi. */
internal fun stBusy(s: String): Boolean = s.uppercase() in setOf("RUNNING", "QUEUED", "EVALUATING", "COMPETITOR_RESEARCH", "FEATURE_REVIEW")

fun stText(s: String) = s.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }.t()

/** Idea ka status (WATCH = nazar me, "Dhyan do" nahi). */
internal fun oppStatusText(s: String): String = when (s) {
    "NEW" -> "New".t()
    "WATCH" -> "Watching".t()
    "SELECTED" -> "Selected".t()
    "REJECTED" -> "Rejected".t()
    "EVALUATING" -> "Evaluating".t()
    else -> stText(s)
}

/** Faisla: APPROVE / REJECT / REQUEST_CHANGES / WATCH / MORE_RESEARCH / RESTORE. */
internal fun decisionText(d: String): String = when (d) {
    "APPROVE" -> "Approved".t()
    "REJECT" -> "Rejected".t()
    "REQUEST_CHANGES" -> "Changes requested".t()
    "WATCH" -> "Watching".t()
    "MORE_RESEARCH" -> "More research".t()
    "RESTORE" -> "Restored".t()
    else -> stText(d)
}

internal fun engineText(e: String): String = when (e) {
    "organic" -> "Organic".t()
    "paid" -> "Paid".t()
    "both" -> "Both".t()
    "combined" -> "Combined".t()
    else -> stText(e)
}

/** Status pill (chal raha ho to ghadi). */
@Composable
internal fun SStatusPill(s: String, text: String = stText(s)) {
    val k = stKind(s)
    KPill(text, k, icon = if (stBusy(s)) KIcons.Clock else k.icon())
}

fun score(v: Double?): String = if (v == null) "–" else num(v, 0)

/** Organic / paid me jo zyada (server ki "top" ranking jaisa). */
internal fun bestScore(o: JSONObject): Double? = listOfNotNull(o.dn("organic"), o.dn("paid")).maxOrNull()

fun JSONArray.objs(): List<JSONObject> = mapObj { it }

fun jobName(j: String) = when (j) {
    "economics" -> "Internal economics".t(); "discover" -> "Daily discovery".t(); "rank" -> "Weekly ranking".t(); "deep" -> "Monthly deep research".t()
    "competitors" -> "Competitor analysis".t(); "revise_features" -> "Revise feature spec".t(); "evaluate_idea" -> "Evaluate idea".t(); "source_check" -> "Source test".t()
    else -> j
}

internal fun sAgo(ts: String?): String = tsMs(ts)?.let { agoText(it) } ?: "–"

internal fun sUpdated(g: GState): String = if (g.lastOk == 0L) "Loading…".t() else "Updated".t() + " " + agoText(g.lastOk)

/** Wapas: stack khali ho to Studio. */
internal fun sBack(nav: NavHostController): () -> Unit = { if (!nav.popBackStack()) nav.navigate("studio") }

/** "studio/ranking" ab "research" tab hai. Studio ke tab ho to tab badlo, warna navigate. */
internal fun sOpen(route: String, nav: NavHostController, go: ((String) -> Unit)?) {
    val r = if (route == "studio/ranking") "studio/research" else route
    val tab = r.removePrefix("studio/")
    if (go != null && r.startsWith("studio/") && '/' !in tab && tab.isNotBlank()) go(tab) else if (r.isNotBlank()) nav.navigate(r)
}

/** Link browser me (Android: ACTION_VIEW). */
@Composable
internal fun rememberOpenUrl(): (String) -> Unit {
    val h = LocalUriHandler.current
    return remember(h) {
        { u: String ->
            runCatching { h.openUri(u) }
            Unit
        }
    }
}

/** Screen khuli rahe tab tak har [ms] par [tick] (pehli baar turant). App peeche ho to ruk jata hai. */
@Composable
internal fun SPoll(ms: Long, vararg keys: Any?, tick: () -> Unit) {
    val t by rememberUpdatedState(tick)
    val life = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(life, *keys) { life.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { t(); delay(ms) } } }
}

// ---------------------------------------------------------------- text

internal fun sStyle(size: TextUnit, weight: FontWeight = FontWeight.Normal, lh: TextUnit = TextUnit.Unspecified): TextStyle =
    KNum.copy(fontSize = size, fontWeight = weight, lineHeight = lh)

/** Kit jaisa text (tabular numbers, ellipsis). */
@Composable
internal fun SText(
    text: String, size: TextUnit, color: Color, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal,
    lh: TextUnit = TextUnit.Unspecified, maxLines: Int = 1, align: TextAlign = TextAlign.Start,
) {
    Text(
        text, modifier, color = color, style = sStyle(size, weight, lh), maxLines = maxLines, softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis, textAlign = align,
    )
}

/** List card me row ke neeche chhoti line (feedback, summary). lead = avatar/tile wali row. */
@Composable
internal fun SNote(text: String, lead: Boolean = false, color: Color = Color.Unspecified, maxLines: Int = 3) {
    val c = Mfc.c
    SText(
        text, 13.sp, color.takeOrElse { c.ink2 }, Modifier.fillMaxWidth().padding(start = if (lead) 68.dp else 16.dp, end = 16.dp, bottom = 10.dp),
        lh = 18.sp, maxLines = maxLines,
    )
}

// ---------------------------------------------------------------- frame

@Composable
fun SProblem(g: GState, onRetry: () -> Unit) {
    val c = Mfc.c
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (g.missing) KIconTile(KIcons.Dhyan, KKind.WARN) else KIconTile(KIcons.Turant, KKind.BAD)
            Column(Modifier.weight(1f)) {
                SText(if (g.missing) "Not on server".t() else "Error".t(), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
                SText(
                    if (g.missing) "lib/studio.php · api/studio.php" else (g.error ?: "No data received".t()), 13.sp,
                    if (g.missing) c.ink2 else c.red, Modifier.padding(top = 2.dp), lh = 18.sp, maxLines = 4,
                )
            }
        }
        KButton("Try again".t(), onRetry, Modifier.fillMaxWidth().padding(top = 12.dp), kind = KBtn.SOFT, icon = KIcons.Refresh)
    }
}

@Composable
fun SLoad(g: GState, j: JSONObject?, retry: () -> Unit, content: @Composable (JSONObject) -> Unit) {
    if (j == null) {
        if (g.error != null) SProblem(g, retry) else KLoading()
        return
    }
    content(j)
}

/** Studio kabhi khud publish nahi karta: har gate par aapka OK. */
@Composable
fun NeverPublishNote() {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(KIcons.Shield, null, tint = c.ink3, modifier = Modifier.size(18.dp))
        SText("Never auto-publish".t(), 13.sp, c.ink3, lh = 18.sp)
    }
}

// ---------------------------------------------------------------- small pieces

/** Score badge (brand-soft gol pill). muted = score nahi. */
@Composable
internal fun SScore(text: String, muted: Boolean = false) {
    val c = Mfc.c
    Box(
        Modifier.widthIn(min = 44.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (muted) c.pNBg else c.brandSoft)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { SText(text, 16.sp, if (muted) c.ink3 else c.brand, weight = FontWeight.Bold, lh = 20.sp) }
}

/** Kit checkbox: 48dp touch, 24dp gol-kona dabba. */
@Composable
internal fun SCheck(checked: Boolean, onToggle: () -> Unit) {
    val c = Mfc.c
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(48.dp).clip(CircleShape).toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        var box = Modifier.size(24.dp).clip(shape)
        box = if (checked) box.background(c.btnBg) else box.border(2.dp, c.swOff, shape)
        Box(box, contentAlignment = Alignment.Center) {
            if (checked) Icon(KIcons.Check, null, tint = c.btnFg, modifier = Modifier.size(18.dp))
        }
    }
}

/** Hero ka pill: "OK chahiye" lavender hero par card rang ka (mockup), baaki kit pill. */
@Composable
internal fun SHeroPill(text: String, kind: KKind) {
    if (kind != KKind.ASK) {
        KPill(text, kind)
        return
    }
    val c = Mfc.c
    Row(
        Modifier.height(26.dp).clip(CircleShape).background(if (c.onBrand) c.pAskBg else c.card).padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(KIcons.Wait, null, tint = c.pAskFg, modifier = Modifier.size(14.dp))
        SText(text, 13.sp, c.pAskFg, weight = FontWeight.SemiBold)
    }
}

internal fun gateKind(state: String): KKind = when (state) {
    "APPROVED" -> KKind.OK
    "WAITING" -> KKind.ASK
    "REJECTED" -> KKind.BAD
    "CHANGES_REQUESTED" -> KKind.WARN
    else -> KKind.NEUTRAL
}

/** 8 gates ki patti: hara = approve, brand = aapka OK chahiye, laal = reject, amber = badlav. */
@Composable
internal fun SGateStrip(gates: List<JSONObject>, modifier: Modifier = Modifier) {
    if (gates.isEmpty()) return
    val c = Mfc.c
    Row(modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        gates.forEach { gt ->
            val col = when (gt.s("state")) {
                "APPROVED" -> c.zGreen
                "WAITING" -> c.brand
                "REJECTED" -> c.zRed
                "CHANGES_REQUESTED" -> c.zAmber
                else -> c.zTrack
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(col))
        }
    }
}

/** Gate ka number tile (40dp, kind ka halka rang). */
@Composable
internal fun SGateTile(n: Int, kind: KKind) {
    val c = Mfc.c
    val (bg, fg) = when (kind) {
        KKind.OK -> c.greenSoft to c.green
        KKind.WARN -> c.amberSoft to c.amber
        KKind.BAD -> c.redSoft to c.red
        KKind.ASK -> c.brandSoft to c.brand
        else -> c.pNBg to c.ink2
    }
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        SText(n.toString(), 16.sp, fg, weight = FontWeight.Bold, lh = 20.sp)
    }
}

/** Kit text field (well rang, 48dp, gol kone). */
@Composable
internal fun SField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    number: Boolean = false,
    caps: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val c = Mfc.c
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.heightIn(min = 48.dp),
        textStyle = sStyle(15.sp, lh = 20.sp).copy(color = c.ink),
        keyboardOptions = KeyboardOptions(
            capitalization = if (caps) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
            keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
        ),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 8,
        minLines = if (singleLine) 1 else minLines.coerceIn(1, 8),
        cursorBrush = SolidColor(c.brand),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(c.well).border(1.dp, c.line, shape)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) SText(placeholder, 15.sp, c.ink3, lh = 20.sp)
                inner()
            }
        },
    )
}

/** Save ka gol button (48dp, check). busy = spinner. */
@Composable
internal fun SSaveBtn(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Mfc.c
    Box(
        Modifier.size(48.dp).alpha(if (enabled || busy) 1f else .4f).clip(CircleShape).background(c.brandSoft)
            .clickable(enabled = enabled && !busy, onClickLabel = "Save".t(), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = c.brand, strokeWidth = 2.dp)
        else Icon(KIcons.Check, "Save".t(), tint = c.brand, modifier = Modifier.size(22.dp))
    }
}

/** Filter chip: "Status: Active ▾", tap = menu. Default se alag ho to brand rang. */
@Composable
internal fun SDrop(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = Mfc.c
    var open by remember { mutableStateOf(false) }
    val on = selected != (options.firstOrNull()?.first ?: selected)
    val cur = options.firstOrNull { it.first == selected }?.second ?: selected
    val pill = RoundedCornerShape(18.dp)
    Box {
        Box(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.height(36.dp).clip(pill).background(if (on) c.brandSoft else c.card).border(1.dp, if (on) c.brandSoft else c.line, pill)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.ink3)) { append("$label: ") }
                        withStyle(SpanStyle(color = if (on) c.brand else c.ink, fontWeight = FontWeight.SemiBold)) { append(cur) }
                    },
                    style = sStyle(14.sp, lh = 18.sp), maxLines = 1, softWrap = false,
                )
                Icon(KIcons.ChevDown, null, tint = if (on) c.brand else c.ink3, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (k, l) ->
                val sel = k == selected
                DropdownMenuItem(
                    text = { SText(l, 15.sp, if (sel) c.brand else c.ink, weight = if (sel) FontWeight.Bold else FontWeight.Medium, lh = 20.sp) },
                    onClick = { open = false; onSelect(k) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    trailingIcon = if (sel) { { Icon(KIcons.Check, null, tint = c.brand, modifier = Modifier.size(18.dp)) } } else null,
                )
            }
        }
    }
}

/** Khulne / band hone wala teer (neeche / upar). */
@Composable
internal fun SExpandIcon(open: Boolean) {
    Icon(KIcons.ChevDown, null, tint = Mfc.c.chev, modifier = Modifier.size(22.dp).rotate(if (open) 180f else 0f))
}

/** Lavender idea row: avatar (ya checkbox) + naam + salah + meta, daayein score badge. */
@Composable
internal fun IdeaRow(o: JSONObject, engine: String, selected: Boolean?, onToggle: (() -> Unit)?, onClick: () -> Unit) {
    val c = Mfc.c
    val main = when (engine) {
        "paid" -> o.dn("paid")
        "combined" -> o.dn("combined")
        "best" -> bestScore(o)
        else -> o.dn("organic")
    }
    val pick = selected != null && onToggle != null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick)
            .padding(start = if (pick) 4.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (selected != null && onToggle != null) SCheck(selected, onToggle) else KAvatar(o.s("title"))
        Column(Modifier.weight(1f)) {
            SText(o.s("title"), 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
            val rec = o.sn("final_rec")
            if (rec != null) KStatus(recText(rec), recKind(rec), Modifier.padding(top = 2.dp))
            val meta = ideaMeta(o)
            if (meta.isNotBlank()) SText(meta, 13.sp, c.ink2, Modifier.padding(top = 2.dp), lh = 18.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SScore(score(main), main == null)
            SText("score".t(), 13.sp, c.ink3, Modifier.padding(top = 1.dp), lh = 18.sp)
        }
        Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.offset(x = 6.dp).size(22.dp))
    }
}

/** "Health · Naya · IN US · aapka idea · High conf." */
private fun ideaMeta(o: JSONObject): String = listOfNotNull(
    o.s("category").ifBlank { null },
    o.s("status").ifBlank { null }?.let { oppStatusText(it) },
    o.arr("countries").strings().filter { it.isNotBlank() }.take(4).joinToString(" ").ifBlank { null },
    if (o.s("origin") == "user") "your idea".t() else null,
    o.sn("confidence")?.let { it + " " + "conf.".t() },
).joinToString(" · ")

// ---------------------------------------------------------------- rows

/** Ek idea, apne card me (compare / akela). List ke liye IdeaRow + KCard(list). */
@Composable
fun OppCard(o: JSONObject, nav: NavHostController, engine: String = "organic", selected: Boolean? = null, onToggle: (() -> Unit)? = null) {
    KCard(list = true) {
        IdeaRow(o, engine, selected, onToggle) { nav.navigate("studio/opp/${o.l("id")}") }
    }
}

/** Saboot: #id + title, label (shape + rang), source · desh · kab, URL (tap = khule). Divider caller lagata hai. */
@Composable
fun EvidenceRow(e: JSONObject) {
    val c = Mfc.c
    val open = rememberOpenUrl()
    val url = e.s("url")
    val link = url.startsWith("http")
    var m = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (link) m = m.clickable(role = Role.Button) { open(url) }
    Column(m.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SText(
                "#" + e.l("id") + " " + e.s("title").ifBlank { e.s("data_type") }, 15.sp, c.ink, Modifier.weight(1f),
                weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2,
            )
            if (link) Icon(KIcons.Open, null, tint = c.brand, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        }
        KStatus(labelText(e.s("label")), labelKind(e.s("label")), Modifier.padding(top = 2.dp))
        val meta = listOfNotNull(
            e.s("source").ifBlank { null }, e.s("cc").ifBlank { null }, e.s("category").ifBlank { null },
            e.sn("data_as_of")?.let { "as of".t() + " " + it }, "collected".t() + " " + sAgo(e.s("collected_at")),
            e.s("confidence").ifBlank { null }?.let { it + " " + "confidence".t() },
        ).joinToString(" · ")
        SText(meta, 13.sp, c.ink3, Modifier.padding(top = 2.dp), lh = 18.sp, maxLines = 3)
        if (url.isNotBlank()) SText(url, 13.sp, c.brand, Modifier.padding(top = 2.dp), lh = 18.sp)
        e.sn("method")?.let { SText("Method".t() + ": " + it, 13.sp, c.ink2, Modifier.padding(top = 2.dp), lh = 18.sp, maxLines = 3) }
    }
}

/** Run row: job, status · steps · stage, kharcha, kab. Tap = run detail. Divider caller lagata hai. */
@Composable
fun SRunRow(r: JSONObject, nav: NavHostController) {
    val st = r.s("status")
    val k = stKind(st)
    val steps = if (r.d("steps_total") > 0) "${r.d("steps_done").toInt()}/${r.d("steps_total").toInt()}" else null
    val extra = if (st == "RUNNING") r.s("stage").ifBlank { null } else r.s("uid").ifBlank { null }
    KRow(
        jobName(r.s("job")),
        sub = listOfNotNull(stText(st), steps, extra).joinToString(" · "),
        subKind = k,
        lead = { KIconTile(if (stBusy(st)) KIcons.Clock else (k.icon() ?: KIcons.Play), k) },
        value = usd(r.d("cost_usd"), 2),
        valuePlain = true,
        valueSub = sAgo(r.sn("finished_at") ?: r.sn("started_at") ?: r.s("queued_at")),
        chevron = true,
        onClick = { nav.navigate("studio/run/${r.l("id")}") },
    )
    r.sn("summary")?.let { SNote(it, lead = true, maxLines = 2) }
}

private fun eventIcon(kind: String): ImageVector = when (kind) {
    "artifact" -> KIcons.Copy
    "action" -> KIcons.Play
    "notify" -> KIcons.Bell
    "log" -> KIcons.History
    else -> KIcons.Info
}

/** Activity / history ki line: tile + message + kaun · kab. nav != null aur link ho to tap = khule. */
@Composable
fun SEventRow(e: JSONObject, nav: NavHostController?) {
    val c = Mfc.c
    val route = when {
        e.dn("project_id") != null -> "studio/project/${e.l("project_id")}"
        e.dn("opp_id") != null -> "studio/opp/${e.l("opp_id")}"
        e.dn("run_id") != null -> "studio/run/${e.l("run_id")}"
        else -> null
    }
    val err = e.s("level") == "error"
    val dec = e.s("kind") == "decision"
    val tap = nav != null && route != null
    var m = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (nav != null && route != null) m = m.clickable { nav.navigate(route) }
    Row(
        m.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KIconTile(
            if (err) KIcons.Turant else if (dec) KIcons.Check else eventIcon(e.s("kind")),
            if (err) KKind.BAD else if (dec) KKind.ASK else KKind.NEUTRAL,
        )
        Column(Modifier.weight(1f)) {
            SText(e.s("msg"), 14.sp, c.ink, lh = 20.sp, maxLines = 4)
            val meta = listOfNotNull(
                if (err) "error".t() else if (dec) "decision".t() else null,
                e.s("actor").ifBlank { null },
                tFull(e.s("created_at")),
            ).joinToString(" · ")
            SText(meta, 13.sp, if (err) c.red else c.ink3, Modifier.padding(top = 2.dp), lh = 18.sp)
        }
        if (tap) Icon(KIcons.Chev, null, tint = c.chev, modifier = Modifier.offset(x = 6.dp).size(22.dp))
    }
}

@Composable
fun Bullets(title: String, items: List<String>, color: Color? = null) {
    val list = items.filter { it.isNotBlank() }
    if (list.isEmpty()) return
    val c = Mfc.c
    val dot = color ?: c.ink3
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SText(title, 13.sp, c.ink3, weight = FontWeight.SemiBold, lh = 18.sp)
        list.forEach { s ->
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(dot))
                SText(s, 14.sp, c.ink, Modifier.weight(1f), lh = 20.sp, maxLines = Int.MAX_VALUE)
            }
        }
    }
}

@Composable
fun Para(title: String, text: String?) {
    if (text.isNullOrBlank()) return
    val c = Mfc.c
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SText(title, 13.sp, c.ink3, weight = FontWeight.SemiBold, lh = 18.sp)
        SText(text, 14.sp, c.ink, Modifier.padding(top = 2.dp), lh = 20.sp, maxLines = Int.MAX_VALUE)
    }
}

private val DC_KEYS = listOf(
    "what" to "What", "why" to "Why", "evidence" to "Evidence", "benefit" to "Benefit", "cost" to "Cost", "risk" to "Risk",
    "alternatives" to "Alternatives", "confidence" to "Confidence", "recommendation" to "Recommendation",
)

/** Decision format: WHAT / WHY / EVIDENCE / BENEFIT / COST / RISK / ALTERNATIVES / CONFIDENCE. */
@Composable
fun DecisionCard(c: JSONObject, confidence: String? = null) {
    if (c.length() == 0) return
    val k = Mfc.c
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(KIcons.Target, null, tint = k.brand, modifier = Modifier.size(20.dp))
            SText("Decision card".t(), 16.sp, k.ink, weight = FontWeight.Bold, lh = 22.sp)
        }
        Spacer(Modifier.height(4.dp))
        DC_KEYS.forEach { (key, l) -> Para(l.t(), c.sn(key)) }
        if (c.sn("confidence") == null && confidence != null) Para("Confidence".t(), confidence)
    }
}

/** Laal button (reject / cancel). */
@Composable
private fun SDangerBtn(text: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Mfc.c
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.heightIn(min = 48.dp).alpha(if (enabled) 1f else .45f).clip(shape).background(if (c.isDark) c.zRed else c.red, shape)
            .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        SText(text, 15.sp, Color.White, weight = FontWeight.Bold)
    }
}

/** Faisle se pehle poochne wala dialog (note / wajah). required = khali par button band. Kabhi khud approve nahi. */
@Composable
fun TextAskDialog(
    title: String, body: String, label: String, confirm: String, required: Boolean, busy: Boolean, danger: Boolean = false,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit,
) {
    var txt by remember { mutableStateOf("") }
    KPageTokens {
        val c = Mfc.c
        val ok = !required || txt.isNotBlank()
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { SText(title, 20.sp, c.ink, weight = FontWeight.Bold, lh = 26.sp, maxLines = 3) },
            text = {
                Column {
                    if (body.isNotBlank()) SText(body, 15.sp, c.ink2, lh = 21.sp, maxLines = Int.MAX_VALUE)
                    OutlinedTextField(
                        txt, { txt = it }, label = { Text(label) }, minLines = 2, maxLines = 6,
                        modifier = Modifier.fillMaxWidth().padding(top = if (body.isNotBlank()) 12.dp else 0.dp),
                    )
                }
            },
            confirmButton = {
                if (danger) SDangerBtn(confirm, busy, ok) { onConfirm(txt.trim()) }
                else KButton(confirm, { onConfirm(txt.trim()) }, enabled = ok, busy = busy)
            },
            dismissButton = { KButton("Cancel".t(), onDismiss, kind = KBtn.OUTLINE) },
            containerColor = c.card,
        )
    }
}

/** List card ka halka khaali hissa (padding ke saath). */
@Composable
internal fun SGap(h: Dp) = Spacer(Modifier.height(h))
