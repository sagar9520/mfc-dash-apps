package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.data.*
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.components.*
import com.myfuturecode.mfcdash.ui.theme.extra
import com.myfuturecode.mfcdash.util.*

/** api/app.php server par nahi hai: ek line mein batao kya karna hai. */
@Composable
fun ExtraMissingCard(what: String) {
    Panel {
        Text(what, style = MaterialTheme.typography.titleSmall)
        Text("Yeh data server ke naye `api/app.php` se aata hai. Zip ke `server/api/app.php` ko `mfc_dash/api/` mein upload karo, bas.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** ROAS cohort table: campaign × D0/D1/D3/D7/Total, target curve ke mukable rang. */
@Composable
fun RoasCohortTable(rows: List<CampaignRoas>, d: DashData?, onRow: ((CampaignRoas) -> Unit)? = null) {
    if (rows.isEmpty()) { Empty("Is period mein utm_campaign wale installs ya campaign spend nahi"); return }
    val heads = listOf("Campaign") + ROAS_TARGET_CURVE.keys + listOf("Total")
    Row(Modifier.padding(vertical = 4.dp)) {
        heads.forEachIndexed { i, h ->
            Text(h.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (i == 0) Modifier.weight(1.6f) else Modifier.weight(1f), textAlign = if (i == 0) TextAlign.Start else TextAlign.Center)
        }
    }
    Row { Spacer(Modifier.weight(1.6f)); ROAS_TARGET_CURVE.values.forEach { Text("target " + pctText(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1) }; Spacer(Modifier.weight(1f)) }
    rows.forEach { c ->
        Row(Modifier.padding(vertical = 2.dp).let { m -> if (onRow != null) m.clickable { onRow(c) } else m }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.6f)) {
                Text(c.campaign, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((d?.appName(c.pkg) ?: c.pkg) + " · " + num(c.installs) + " inst · " + inr(c.spend), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val vals = listOf(c.roasD0, c.roasD1, c.roasD3, c.roasD7)
            ROAS_TARGET_CURVE.values.forEachIndexed { i, t -> HeatCell(vals[i], t, roasPct(vals[i]), Modifier.weight(1f)) }
            HeatCell(c.roasTotal, 1.0, roasPct(c.roasTotal), Modifier.weight(1f))
        }
    }
    Text("Dn = install ke n din tak ki kamai ÷ spend. Hara = target par, laal = aadhe se kam. Campaign ka naam aur Play link ka utm_campaign same hona chahiye.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

/** Retention table: all / paid / organic × D1 D3 D7 D14 */
@Composable
fun RetentionTable(ex: ExtraData) {
    val heads = listOf("Cohort", "Installs", "D1", "D3", "D7", "D14")
    Row(Modifier.padding(vertical = 4.dp)) { heads.forEachIndexed { i, h -> Text(h.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(if (i == 0) 1.3f else 1f), textAlign = if (i == 0) TextAlign.Start else TextAlign.Center) } }
    val targets = listOf(0.35, 0.20, 0.12, 0.08)   // utility apps ke aam benchmark (Android): D1 ~35%, D7 ~12%
    listOf("all" to "Sab", "paid" to "Google Ads", "organic" to "Organic").forEach { (k, label) ->
        val r = ex.ret(k) ?: return@forEach
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.3f))
            Text(num(r.installs), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            listOf(r.d1, r.d3, r.d7, r.d14).forEachIndexed { i, v -> HeatCell(v, targets[i], pct(v, 0), Modifier.weight(1f)) }
        }
    }
    Text("Dn = install ke n-ve din kitne % wapas aaye (sirf un cohorts par jinka n-vaan din aa chuka). Rang: utility apps ke aam benchmark D1 35% / D7 12% ke mukable.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun RetentionDaysTable(ex: ExtraData) {
    Table(listOf("Install din", "Installs", "Paid", "D1", "D3", "D7", "D14"),
        ex.retentionDays.sortedByDescending { it.day }.map { listOf(fmtDay(it.day), num(it.installs), num(it.paid), pct(it.d1, 0), pct(it.d3, 0), pct(it.d7, 0), pct(it.d14, 0)) },
        empty = "Is period mein installs nahi")
}

/** Campaign change log: list + nayi note. */
@Composable
fun NotesSection(vm: DashVm, notes: List<Note>, campaign: String, pkg: String) {
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Panel {
        if (notes.isEmpty()) Empty("Abhi koi note nahi. Jab budget/bid/creative badlo, yahan likho: baad mein ROAS ke saath milaan hota hai.")
        notes.forEach { n ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(n.note, style = MaterialTheme.typography.bodyMedium)
                    Text(tFull(n.createdAt) + (if (n.campaign.isNotBlank() && n.campaign != campaign) " · " + n.campaign else ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { vm.deleteNote(n.id) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Hatao", style = MaterialTheme.typography.labelSmall) }
            }
            Hairline()
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Jaise: budget 20% badhaya, video creative #3 roka") }, minLines = 1, maxLines = 3)
        if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Button(onClick = { if (text.isNotBlank()) { busy = true; vm.addNote(campaign, pkg, text.trim()) { e -> busy = false; err = e; if (e == null) text = "" } } }, enabled = !busy && text.isNotBlank(), modifier = Modifier.align(Alignment.End)) { Text("Note save karo") }
    }
}

/** ROAS curve: campaign ke D0/D1/D3/D7 vs target (do bars side by side). */
@Composable
fun RoasCurve(c: CampaignRoas, target: Double) {
    val ex = MaterialTheme.extra
    val vals = listOf(c.roasD0, c.roasD1, c.roasD3, c.roasD7)
    val keys = ROAS_TARGET_CURVE.keys.toList(); val tg = ROAS_TARGET_CURVE.values.toList()
    val maxV = maxOf(vals.maxOf { it ?: 0.0 }, tg.max(), target) * 1.15
    Column {
        keys.forEachIndexed { i, k ->
            val v = vals[i]
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.outline)) {
                        Box(Modifier.fillMaxWidth(((v ?: 0.0) / maxV).coerceIn(0.0, 1.0).toFloat()).height(10.dp).background(if (v == null) ex.muted else if (v >= tg[i]) ex.good else if (v >= tg[i] * .7) ex.warn else ex.bad))
                    }
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline)) {
                        Box(Modifier.fillMaxWidth((tg[i] / maxV).toFloat()).height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(76.dp)) {
                    Text(roasPct(v), style = MaterialTheme.typography.labelLarge)
                    Text("target " + pctText(tg[i]), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Total ROAS " + roasPct(c.roasTotal) + " · profit " + inr(c.profit) + " · kamai/install " + inr(c.revPerInstall, 2) + " · D1 wapas " + pct(c.retD1, 0) + " · D7 wapas " + pct(c.retD7, 0),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}
