package com.myfuturecode.mfcdash.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfuturecode.mfcdash.data.CampaignRoas
import com.myfuturecode.mfcdash.data.DashData
import com.myfuturecode.mfcdash.data.ExtraData
import com.myfuturecode.mfcdash.data.Note
import com.myfuturecode.mfcdash.data.ROAS_TARGET_CURVE
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.components.DashText
import com.myfuturecode.mfcdash.ui.components.Empty
import com.myfuturecode.mfcdash.ui.components.Hairline
import com.myfuturecode.mfcdash.ui.components.HeatCell
import com.myfuturecode.mfcdash.ui.components.StatCells
import com.myfuturecode.mfcdash.ui.components.Table
import com.myfuturecode.mfcdash.ui.components.pctText
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.kRoasColor
import com.myfuturecode.mfcdash.ui.kit.kRoasKind
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.inr
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.roasPct
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tFull

/** api/app.php abhi server par nahi: chhota card (kya chahiye). */
@Composable
fun ExtraMissingCard(what: String) {
    KCard(list = true) {
        KRow(what, sub = "Needs api/app.php".t(), subKind = KKind.WARN, lead = { KIconTile(KIcons.Info, KKind.WARN) }, titleLines = 2)
    }
}

/**
 * ROAS cohort: campaign × D0/D1/D3/D7/Total, D-cells target curve ke saamne rangeen. Card ke andar.
 * Total = ROAS rang niyam (target = Settings ka ROAS target, 100% = break-even).
 */
@Composable
fun RoasCohortTable(rows: List<CampaignRoas>, d: DashData?, target: Double = 1.0, onRow: ((CampaignRoas) -> Unit)? = null) {
    val c = Mfc.c
    if (rows.isEmpty()) { Empty("No cohort data".t()); return }
    val heads = listOf("Campaign".t()) + ROAS_TARGET_CURVE.keys + listOf("Total".t())
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        heads.forEachIndexed { i, h ->
            DashText(
                h, 13.sp, c.ink3, if (i == 0) Modifier.weight(1.6f) else Modifier.weight(1f),
                weight = FontWeight.SemiBold, align = if (i == 0) TextAlign.Start else TextAlign.Center,
            )
        }
    }
    // target curve (D0 30%, D1 50% ...)
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1.6f))
        ROAS_TARGET_CURVE.values.forEach { DashText("≥" + pctText(it), 13.sp, c.ink3, Modifier.weight(1f), align = TextAlign.Center) }
        DashText("≥" + pctText(maxOf(target, 1.0)), 13.sp, c.ink3, Modifier.weight(1f), align = TextAlign.Center)
    }
    rows.forEach { cr ->
        val m = if (onRow != null) Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onRow(cr) } else Modifier.fillMaxWidth()
        Row(m.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.6f)) {
                DashText(cr.campaign, 14.sp, c.ink, weight = FontWeight.SemiBold)
                DashText((d?.appName(cr.pkg) ?: cr.pkg) + " · " + num(cr.installs) + " " + "inst".t() + " · " + inr(cr.spend), 13.sp, c.ink3)
            }
            val vals = listOf(cr.roasD0, cr.roasD1, cr.roasD3, cr.roasD7)
            ROAS_TARGET_CURVE.values.forEachIndexed { i, t -> HeatCell(vals[i], t, roasPct(vals[i]), Modifier.weight(1f)) }
            CohortTotalCell(cr.roasTotal, target, Modifier.weight(1f))
        }
    }
}

/** Total ROAS cell: laal < min(target,100%), amber beech me, hara >= max(target,100%) (kit pill rang). */
@Composable
private fun CohortTotalCell(roas: Double?, target: Double, modifier: Modifier) {
    val c = Mfc.c
    val (bg, fg) = when (kRoasKind(roas, target)) {
        KKind.OK -> c.pOkBg to c.pOkFg
        KKind.WARN -> c.pWarnBg to c.pWarnFg
        KKind.BAD -> c.pBadBg to c.pBadFg
        else -> c.pNBg to c.ink3
    }
    Box(modifier.padding(2.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
        DashText(roasPct(roas?.takeIf { it.isFinite() }), 13.sp, fg, weight = FontWeight.SemiBold, align = TextAlign.Center)
    }
}

/** Retention: all / paid / organic × D1 D3 D7 D14 (utility-app benchmark ke saamne rang). */
@Composable
fun RetentionTable(ex: ExtraData) {
    val c = Mfc.c
    val heads = listOf("Cohort".t(), "Installs".t(), "D1", "D3", "D7", "D14")
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        heads.forEachIndexed { i, h ->
            DashText(h, 13.sp, c.ink3, Modifier.weight(if (i == 0) 1.3f else 1f), weight = FontWeight.SemiBold, align = if (i == 0) TextAlign.Start else TextAlign.Center)
        }
    }
    val targets = listOf(0.35, 0.20, 0.12, 0.08)   // Android utility apps: D1 ~35%, D7 ~12%
    listOf("all" to "All".t(), "paid" to "Google Ads", "organic" to "Organic".t()).forEach { (k, label) ->
        val r = ex.ret(k) ?: return@forEach
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            DashText(label, 14.sp, c.ink, Modifier.weight(1.3f), weight = FontWeight.Medium)
            DashText(num(r.installs), 14.sp, c.ink, Modifier.weight(1f), align = TextAlign.Center)
            listOf(r.d1, r.d3, r.d7, r.d14).forEachIndexed { i, v -> HeatCell(v, targets[i], pct(v, 0), Modifier.weight(1f)) }
        }
    }
}

@Composable
fun RetentionDaysTable(ex: ExtraData) {
    Table(listOf("Install day".t(), "Installs".t(), "Paid".t(), "D1", "D3", "D7", "D14"),
        ex.retentionDays.sortedByDescending { it.day }.map { listOf(fmtDay(it.day), num(it.installs), num(it.paid), pct(it.d1, 0), pct(it.d3, 0), pct(it.d7, 0), pct(it.d14, 0)) },
        empty = "No installs".t())
}

/** Campaign change log: notes ki list (hatao) + naya note. */
@Composable
fun NotesSection(vm: DashVm, notes: List<Note>, campaign: String, pkg: String) {
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val c = Mfc.c
    KCard {
        if (notes.isEmpty()) Empty("No notes yet".t())
        notes.forEach { n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    DashText(n.note, 15.sp, c.ink, maxLines = 4)
                    DashText(tFull(n.createdAt) + (if (n.campaign.isNotBlank() && n.campaign != campaign) " · " + n.campaign else ""), 13.sp, c.ink3)
                }
                KBarIcon(KIcons.Delete, "Remove".t(), { vm.deleteNote(n.id) })
            }
            Hairline()
        }
        OutlinedTextField(
            text, { text = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            placeholder = { Text("New note".t()) }, minLines = 1, maxLines = 3, shape = RoundedCornerShape(16.dp),
        )
        val e = err
        if (e != null) DashText(e, 13.sp, c.red, Modifier.padding(top = 4.dp), maxLines = 3)
        KButton(
            "Save note".t(),
            {
                if (text.isNotBlank()) {
                    busy = true
                    vm.addNote(campaign, pkg, text.trim()) { msg -> busy = false; err = msg; if (msg == null) text = "" }
                }
            },
            Modifier.align(Alignment.End).padding(top = 10.dp), icon = KIcons.Check, enabled = text.isNotBlank(), busy = busy,
        )
    }
}

/** ROAS curve: campaign ka D0/D1/D3/D7 target ke saamne (moti patti = asli, patli = target). */
@Composable
fun RoasCurve(c: CampaignRoas, target: Double) {
    val k = Mfc.c
    val vals = listOf(c.roasD0, c.roasD1, c.roasD3, c.roasD7)
    val keys = ROAS_TARGET_CURVE.keys.toList(); val tg = ROAS_TARGET_CURVE.values.toList()
    val maxV = maxOf(vals.maxOf { it ?: 0.0 }, tg.max(), target) * 1.15
    Column {
        keys.forEachIndexed { i, key ->
            val v = vals[i]
            val col = when {
                v == null -> k.zGrey
                v >= tg[i] -> k.zGreen
                v >= tg[i] * .7 -> k.zAmber
                else -> k.zRed
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DashText(key, 14.sp, k.ink, Modifier.width(34.dp), weight = FontWeight.SemiBold)
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(k.zTrack)) {
                        val f = ((v ?: 0.0) / maxV).coerceIn(0.0, 1.0).toFloat()
                        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(col))
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(k.zTrack)) {
                        Box(Modifier.fillMaxWidth((tg[i] / maxV).coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().background(k.ink3))
                    }
                }
                Column(Modifier.width(88.dp).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                    DashText(roasPct(v), 15.sp, k.ink, weight = FontWeight.Bold)
                    DashText("target".t() + " " + pctText(tg[i]), 13.sp, k.ink3)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        StatCells(
            listOf(
                Triple("Total ROAS".t(), roasPct(c.roasTotal), null),
                Triple("Profit".t(), inr(c.profit), null),
                Triple("Earned / install".t(), inr(c.revPerInstall, 2), null),
                Triple("D1", pct(c.retD1, 0), "back".t()),
                Triple("D7", pct(c.retD7, 0), "back".t()),
            ),
            cols = 3,
            colors = mapOf(0 to kRoasColor(c.roasTotal, target), 1 to if (c.profit >= 0) k.green else k.red),
        )
    }
}
