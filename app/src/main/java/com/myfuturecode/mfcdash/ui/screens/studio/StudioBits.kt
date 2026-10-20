package com.myfuturecode.mfcdash.ui.screens.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.screens.gads.GLabel
import com.myfuturecode.mfcdash.ui.screens.gads.Small
import com.myfuturecode.mfcdash.ui.screens.health.*
import com.myfuturecode.mfcdash.ui.theme.Info
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------- colours / text

/** Har data point ka label: VERIFIED / THIRD-PARTY ESTIMATE / AI INFERENCE / UNKNOWN. */
@Composable
fun labelColor(l: String): Color = when (l) {
    "VERIFIED" -> MaterialTheme.extra.good; "ESTIMATE" -> Info; "INFERENCE" -> MaterialTheme.extra.warn; else -> MaterialTheme.extra.muted
}

fun labelText(l: String) = when (l) {
    "VERIFIED" -> "VERIFIED"; "ESTIMATE" -> "THIRD-PARTY ESTIMATE"; "INFERENCE" -> "AI INFERENCE"; else -> "UNKNOWN"
}

@Composable
fun recColor(r: String): Color = when (r) {
    "STRONGLY CONSIDER" -> MaterialTheme.extra.good; "TEST FIRST" -> Info; "WATCH MARKET" -> MaterialTheme.extra.warn; "REJECT" -> MaterialTheme.extra.bad
    else -> MaterialTheme.extra.muted
}

@Composable
fun stColor(s: String): Color {
    val ex = MaterialTheme.extra
    return when (s.uppercase()) {
        "DONE", "OK", "APPROVED", "APPROVE", "SELECTED", "FEATURES_APPROVED", "PUBLISHED", "HIGH" -> ex.good
        "FAILED", "ERROR", "REJECTED", "REJECT", "CANCELLED", "LOW" -> ex.bad
        "PARTIAL", "BLOCKED", "WAITING", "CHANGES_REQUESTED", "REQUEST_CHANGES", "WATCH", "MEDIUM", "UNTESTED" -> ex.warn
        "RUNNING", "QUEUED", "NEW", "EVALUATING", "COMPETITOR_RESEARCH", "FEATURE_REVIEW" -> Info
        else -> ex.muted
    }
}

fun stText(s: String) = s.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }.t()

fun score(v: Double?): String = if (v == null) "–" else num(v, 0)

fun JSONArray.objs(): List<JSONObject> = mapObj { it }

fun jobName(j: String) = when (j) {
    "economics" -> "Internal economics".t(); "discover" -> "Daily discovery".t(); "rank" -> "Weekly ranking".t(); "deep" -> "Monthly deep research".t()
    "competitors" -> "Competitor analysis".t(); "revise_features" -> "Revise feature spec".t(); "evaluate_idea" -> "Evaluate idea".t(); "source_check" -> "Source test".t()
    else -> j
}

// ---------------------------------------------------------------- frame

@Composable
fun SProblem(g: GState, onRetry: () -> Unit) {
    Panel {
        if (g.missing) {
            Text("Research Studio is not on the server yet".t(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Upload lib/studio.php and api/studio.php to mfc_dash and add the cron line (see the setup guide), then tap Try again.".t(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text(g.error ?: "No data received".t(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry) { Text("Try again".t()) }
    }
}

@Composable
fun SLoad(g: GState, j: JSONObject?, retry: () -> Unit, content: @Composable (JSONObject) -> Unit) {
    if (j == null) { if (g.error != null) SProblem(g, retry) else Loading(); return }
    content(j)
}

@Composable
fun NeverPublishNote() {
    Small("Studio never publishes an app by itself. Every gate needs your explicit approval; silence is never approval.".t())
}

// ---------------------------------------------------------------- rows

@Composable
fun OppCard(o: JSONObject, nav: NavHostController, engine: String = "organic", selected: Boolean? = null, onToggle: (() -> Unit)? = null) {
    val ex = MaterialTheme.extra
    Panel(onClick = { nav.navigate("studio/opp/${o.l("id")}") }) {
        Row(verticalAlignment = Alignment.Top) {
            if (selected != null && onToggle != null) {
                Checkbox(selected, { onToggle() }, modifier = Modifier.size(32.dp).padding(end = 6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(o.s("title"), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Small(listOfNotNull("OPP-" + o.l("id"), o.s("category").ifBlank { null }, o.arr("countries").strings().joinToString(" ").ifBlank { null },
                    if (o.s("origin") == "user") "your idea".t() else null).joinToString(" · "), maxLines = 1)
            }
            val main = when (engine) { "paid" -> o.dn("paid"); "combined" -> o.dn("combined"); else -> o.dn("organic") }
            Column(horizontalAlignment = Alignment.End) {
                Text(score(main), style = MaterialTheme.typography.headlineSmall, color = if (main == null) ex.muted else MaterialTheme.colorScheme.onSurface)
                Small(o.s("confidence").ifBlank { "–" } + " " + "conf.".t())
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            o.sn("final_rec")?.let { Pill(it, recColor(it)) }
            if (o.s("status") != "NEW") Pill(stText(o.s("status")), stColor(o.s("status")))
            Small("Org".t() + " " + score(o.dn("organic")) + " · " + "Paid".t() + " " + score(o.dn("paid")) + " · " + "data".t() + " " + (o.dn("coverage")?.let { pct(it, 0) } ?: "–"), maxLines = 1)
        }
        val lab = o.obj("labels")
        if (lab.length() > 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            listOf("VERIFIED", "ESTIMATE", "INFERENCE", "UNKNOWN").forEach { k -> val n = lab.d(k).toInt(); if (n > 0) Pill("$n " + labelText(k).lowercase(), labelColor(k)) }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun EvidenceRow(e: JSONObject) {
    val ctx = LocalContext.current
    val url = e.s("url")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).let { if (url.startsWith("http")) it.clickable { openUrl(ctx, url) } else it }.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(labelText(e.s("label")), labelColor(e.s("label"))); Spacer(Modifier.width(6.dp))
            Text("#" + e.l("id") + " " + e.s("title").ifBlank { e.s("data_type") }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Small(listOfNotNull(e.s("source"), e.s("cc").ifBlank { null }, e.s("category").ifBlank { null }, e.sn("data_as_of")?.let { "as of".t() + " " + it },
            "collected".t() + " " + ago(e.s("collected_at")), e.s("confidence").ifBlank { null }?.let { it + " " + "confidence".t() }).joinToString(" · "))
        if (url.isNotBlank()) Small(url, Info, maxLines = 1)
        e.sn("method")?.let { Small("Method".t() + ": " + it, maxLines = 3) }
    }
    Hairline()
}

@Composable
fun SRunRow(r: JSONObject, nav: NavHostController) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { nav.navigate("studio/run/${r.l("id")}") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(jobName(r.s("job")) + " · " + r.s("uid"), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Small(listOfNotNull(r.s("stage").ifBlank { null }, if (r.d("steps_total") > 0) "${r.d("steps_done").toInt()}/${r.d("steps_total").toInt()}" else null,
                usd(r.d("cost_usd"), 2), ago(r.sn("finished_at") ?: r.sn("started_at") ?: r.s("queued_at"))).joinToString(" · "), maxLines = 1)
            r.sn("summary")?.let { Small(it, maxLines = 2) }
        }
        Pill(r.s("status"), stColor(r.s("status")))
    }
    Hairline()
}

@Composable
fun SEventRow(e: JSONObject, nav: NavHostController?) {
    val route = when {
        e.dn("project_id") != null -> "studio/project/${e.l("project_id")}"
        e.dn("opp_id") != null -> "studio/opp/${e.l("opp_id")}"
        e.dn("run_id") != null -> "studio/run/${e.l("run_id")}"
        else -> null
    }
    Column(Modifier.fillMaxWidth().let { if (nav != null && route != null) it.clickable { nav.navigate(route) } else it }.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (e.s("level") == "error") { Pill("error".t(), MaterialTheme.extra.bad); Spacer(Modifier.width(6.dp)) }
            else if (e.s("kind") == "decision") { Pill("decision".t(), Info); Spacer(Modifier.width(6.dp)) }
            Text(e.s("msg"), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Small(listOfNotNull(e.s("actor").ifBlank { null }, tFull(e.s("created_at")) + " UTC").joinToString(" · "))
    }
}

@Composable
fun Bullets(title: String, items: List<String>, color: Color? = null) {
    if (items.isEmpty()) return
    GLabel(title)
    items.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = color ?: MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 1.dp)) }
}

@Composable
fun Para(title: String, text: String?) {
    if (text.isNullOrBlank()) return
    GLabel(title)
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/** Decision format: WHAT / WHY / EVIDENCE / BENEFIT / COST / RISK / ALTERNATIVES / CONFIDENCE. */
@Composable
fun DecisionCard(c: JSONObject, confidence: String? = null) {
    if (c.length() == 0) return
    Panel {
        Text("Decision card".t(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        listOf("what" to "WHAT", "why" to "WHY", "evidence" to "EVIDENCE", "benefit" to "BENEFIT", "cost" to "COST", "risk" to "RISK", "alternatives" to "ALTERNATIVES",
            "confidence" to "CONFIDENCE", "recommendation" to "RECOMMENDATION").forEach { (k, l) -> Para(l, c.sn(k)) }
        if (c.sn("confidence") == null && confidence != null) Para("CONFIDENCE", confidence)
    }
    Spacer(Modifier.height(8.dp))
}

/** Lamba text (reason/feedback) maangne wala dialog. required = khali par button band. */
@Composable
fun TextAskDialog(title: String, body: String, label: String, confirm: String, required: Boolean, busy: Boolean, danger: Boolean = false,
                  onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var txt by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            Text(body, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(txt, { txt = it }, label = { Text(label) }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        BusyButton(confirm, busy, enabled = !required || txt.isNotBlank(), color = if (danger) MaterialTheme.extra.bad else null) { onConfirm(txt.trim()) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel".t()) } })
}
