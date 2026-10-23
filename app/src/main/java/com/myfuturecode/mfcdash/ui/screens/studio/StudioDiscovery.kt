package com.myfuturecode.mfcdash.ui.screens.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.StudioVm
import com.myfuturecode.mfcdash.ui.kit.KBarIcon
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDivider
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KLoadOnChange
import com.myfuturecode.mfcdash.ui.kit.KPageTokens
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KRow
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSectionLink
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSkelBlock
import com.myfuturecode.mfcdash.ui.kit.KSkelHero
import com.myfuturecode.mfcdash.ui.kit.KSkelRows
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.fmtDay
import com.myfuturecode.mfcdash.util.l
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.obj
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tOnly
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.tsMs
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Daily discovery: roz ki web khoj (findings + sources). Hafte ki ranking pichhle 7 din ki khoj padhti hai.

/** Ek source (server ka S1, S2 ... = evidence id). */
internal class DiscSrc(val id: Long, val title: String, val url: String, val age: String)

internal class DiscFinding(val type: String, val title: String, val detail: String, val said: Boolean, val sources: List<DiscSrc>)

internal class DiscItem(
    val id: Long, val createdAt: String, val structured: Boolean, val used: Boolean, val counts: Map<String, Int>,
    val findings: List<DiscFinding>, val sources: List<DiscSrc>, val notes: String,
) {
    /** Purana artifact / structuring fail = sirf notes (findings ki jagah). */
    val notesOnly: Boolean get() = !structured || (findings.isEmpty() && notes.isNotBlank())

    /** Agli weekly ranking padhegi: findings hain + 7 din se naya (ranking sirf pichhle 7 din padhti hai). */
    val forNext: Boolean get() = !used && !notesOnly && findings.isNotEmpty() && discFresh(createdAt)
}

/** Calendar picker ka ek din. */
internal class DiscDay(val id: Long, val createdAt: String, val findings: Int)

private val DISC_TYPES = listOf("NEW_APP" to "new_app", "COMPETITOR" to "competitor", "POLICY" to "policy", "ADS" to "ads", "OTHER" to "other")

/** Anjaan type = OTHER. */
private fun discType(t: String): String = t.trim().uppercase().let { u -> if (DISC_TYPES.any { it.first == u }) u else "OTHER" }

private fun discSrcs(a: JSONArray): List<DiscSrc> = a.objs().map { DiscSrc(it.l("id"), it.s("title").trim(), it.s("url").trim(), it.s("page_age").trim()) }

/** view=discovery ka "item" (null = abhi koi khoj nahi). Har field defensive (purana / adhoora jawab). */
internal fun discItem(o: JSONObject?): DiscItem? {
    val x = o?.optJSONObject("item") ?: return null
    val fs = x.arr("findings").objs().map { f ->
        DiscFinding(discType(f.s("type")), f.s("title").trim(), f.s("detail").trim(), f.s("basis").trim().uppercase() == "SAID", discSrcs(f.arr("sources")))
    }.filter { it.title.isNotBlank() || it.detail.isNotBlank() }
    val cnt = x.obj("counts")
    // server ki ginti; na ho to findings se
    val counts = DISC_TYPES.associate { (t, k) -> t to (cnt.dn(k)?.toInt() ?: fs.count { it.type == t }) }
    return DiscItem(x.l("id"), x.s("created_at"), x.b("structured"), x.b("used_in_ranking"), counts, fs, discSrcs(x.arr("sources")), x.s("notes"))
}

internal fun discDays(o: JSONObject?): List<DiscDay> =
    o?.arr("days")?.objs().orEmpty().map { DiscDay(it.l("id"), it.s("created_at"), it.d("findings").toInt()) }.filter { it.id > 0 }

/** Kaunsa din: calendar se chuna (artifact id), warna run detail wala run, warna sabse naya. */
internal fun discParams(sel: String, run: Long): Map<String, String> = when {
    sel.isNotBlank() -> mapOf("id" to sel)
    run > 0 -> mapOf("run" to run.toString())
    else -> emptyMap()
}

// ---------------------------------------------------------------- shabd / rang

private val DISC_DAY = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** Khoj 7 din se nayi (weekly ranking ki khidki). */
internal fun discFresh(ts: String?): Boolean = tsMs(ts)?.let { System.currentTimeMillis() - it < 7 * 86_400_000L } ?: false

/** Phone ke din: 0 = aaj, 1 = kal, -1 = purana; saath me "yyyy-MM-dd". */
private fun discDay(ms: Long): Pair<Int, String> {
    val cal = Calendar.getInstance()
    val today = DISC_DAY.format(cal.time)
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yest = DISC_DAY.format(cal.time)
    val d = DISC_DAY.format(Date(ms))
    return (if (d == today) 0 else if (d == yest) 1 else -1) to d
}

/** "Today 6:10" / "Yesterday 6:10" / "21 Oct 6:10" (phone ka time). */
internal fun discWhen(ts: String?): String {
    val ms = tsMs(ts) ?: return "–"
    val hm = tOnly(ts).let { if (it.length == 5 && it[0] == '0') it.drop(1) else it }
    val (k, d) = discDay(ms)
    return when (k) {
        0 -> tf("Today %s", hm)
        1 -> tf("Yesterday %s", hm)
        else -> fmtDay(d) + " " + hm
    }
}

/** Hero ka label: dikhaya gaya din ("Today's scan" / "Yesterday's scan" / "21 Oct scan"). */
private fun discScan(ts: String?): String {
    val ms = tsMs(ts) ?: return "Daily discovery".t()
    val (k, d) = discDay(ms)
    return when (k) {
        0 -> "Today's scan".t()
        1 -> "Yesterday's scan".t()
        else -> tf("%s scan", fmtDay(d))
    }
}

internal fun findingsText(n: Int): String = if (n == 1) "1 finding".t() else tf("%s findings", num(n.toDouble()))

private fun sourcesText(n: Int): String = if (n == 1) "1 source".t() else tf("%s sources", num(n.toDouble()))

private fun discIcon(t: String): ImageVector = when (t) {
    "NEW_APP" -> KIcons.TrendUp
    "COMPETITOR" -> Icons.Rounded.Adjust   // mockup: gol target
    "POLICY" -> KIcons.Tri
    "ADS" -> Icons.AutoMirrored.Rounded.VolumeUp   // mockup: speaker
    else -> KIcons.Bulb
}

private fun discKind(t: String): KKind = when (t) {
    "NEW_APP" -> KKind.OK
    "COMPETITOR" -> KKind.ASK
    "POLICY" -> KKind.BAD
    "ADS" -> KKind.WARN
    else -> KKind.NEUTRAL
}

private fun discTypeText(t: String): String = when (t) {
    "NEW_APP" -> "New app".t()
    "COMPETITOR" -> "Competitor".t()
    "POLICY" -> "Policy".t()
    "ADS" -> "Ads".t()
    else -> "Other".t()
}

/** "bbc.com" (www. ke bina); URL na ho to "". */
private fun discDomain(url: String): String = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.").orEmpty()

// ---------------------------------------------------------------- Overview: "Latest discovery" (Your apps se pehle) + Aur list

/**
 * Overview card: pehli 3 findings + "Today 6:10 · 5 findings" | "Used in ranking" / "For next ranking" (sirf jab sach ho).
 * Findings nahi: ek row ("Notes only" / "No findings"). Key nahi (purana server) / null = kuch nahi.
 */
@Composable
internal fun DiscoverySection(o: JSONObject, go: (String) -> Unit) {
    val d = o.optJSONObject("discovery") ?: return
    val c = Mfc.c
    val top = d.arr("top").objs().take(3)
    val total = d.d("total").toInt()
    val at = d.s("created_at")
    val used = d.b("used_in_ranking")
    val next = !used && total > 0 && discFresh(at)
    val open = { go("discovery") }
    KSection("Latest discovery".t(), trailing = { KSectionLink("See all".t(), open) })
    KCard(list = true) {
        top.forEachIndexed { i, x ->
            if (i > 0) KDivider()
            val t = discType(x.s("type"))
            val n = x.d("sources").toInt()
            KRow(
                x.s("title").trim().ifBlank { discTypeText(t) },
                sub = discTypeText(t) + if (n > 0) " · " + sourcesText(n) else "",
                lead = { KIconTile(discIcon(t), discKind(t)) }, onClick = open, titleLines = 2,
            )
        }
        // findings nahi: card khaali na lage (structured key nahi = purana server, findings wala maano)
        if (top.isEmpty()) KRow(
            if (d.optBoolean("structured", true)) "No findings".t() else "Notes only".t(),
            lead = { KIconTile(KIcons.Search, KKind.NEUTRAL) }, onClick = open,
        )
        KDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = open).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // baayein poora (din + ginti), daayein label ko jagah kam ho to wahi "…"
            SText(discWhen(at) + if (total > 0) " · " + findingsText(total) else "", 14.sp, c.ink3, lh = 18.sp)
            if (used || next) SText(
                if (used) "Used in ranking".t() else "For next ranking".t(), 14.sp, if (used) c.brand else c.ink3,
                Modifier.weight(1f, fill = false).padding(start = 12.dp), weight = FontWeight.SemiBold, lh = 18.sp, align = TextAlign.End,
            )
        }
    }
}

/** Aur list ki row ki line: "5 findings · 2 hours ago" (0 = sirf waqt) / "Not run yet". */
internal fun discMoreSub(o: JSONObject): String = o.optJSONObject("discovery")?.let {
    val n = it.d("total").toInt()
    (if (n > 0) findingsText(n) + " · " else "") + sAgo(it.s("created_at"))
} ?: "Not run yet".t()

// ---------------------------------------------------------------- bar: sub line + din chunne ka calendar

/** "Today 6:10 · 9 sources" (data aane tak null). */
internal fun discSub(g: GState, p: Map<String, String>): String? {
    val o = g.view("discovery", p) ?: return null
    val it0 = discItem(o) ?: return "Not run yet".t()
    return discWhen(it0.createdAt) + " · " + sourcesText(it0.sources.size)
}

/** Calendar icon: pichhle din (tareekh + findings) ki chhoti list; tap = us din ki khoj. */
@Composable
internal fun DiscDayPicker(g: GState, p: Map<String, String>, onPick: (String) -> Unit) {
    // din ki list kisi bhi discovery jawab se (load ke beech gayab na ho)
    val days = discDays(g.view("discovery", p) ?: g.views.entries.firstOrNull { it.key.startsWith("discovery?") }?.value)
    val cur = g.view("discovery", p)?.optJSONObject("item")?.l("id")
    var open by remember { mutableStateOf(false) }
    Box {
        KBarIcon(KIcons.Calendar, "Pick a day".t(), { open = true }, enabled = days.isNotEmpty())
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            KPageTokens {
                val c = Mfc.c
                days.forEach { d ->
                    val sel = d.id == cur
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SText(discWhen(d.createdAt), 15.sp, if (sel) c.brand else c.ink, weight = if (sel) FontWeight.Bold else FontWeight.Medium, lh = 20.sp)
                                if (d.findings > 0) SText(findingsText(d.findings), 13.sp, c.ink3, lh = 18.sp)
                            }
                        },
                        onClick = { open = false; onPick(d.id.toString()) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        trailingIcon = if (sel) { { Icon(KIcons.Check, null, tint = c.brand, modifier = Modifier.size(18.dp)) } } else null,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- tab "discovery" (mockup ui_disc)

/**
 * Daily discovery: lavender hero (findings + New apps | Rivals | Policy | Ads), phir Findings | Sources.
 * Hero top me (Overview jaisa). Fold: hero + tabs baayein, list daayein (Research jaisa).
 */
@Composable
internal fun DiscoveryPage(g: GState, vm: StudioVm, nav: NavHostController, reload: Int, bar: @Composable ColumnScope.() -> Unit, p: Map<String, String>) {
    val live = sLive(vm)
    // khoj khatam = naya din apne aap
    SPoll(600_000, p, reload, live.finOf("discover")) { vm.load("discovery", p) }
    KLoadOnChange(p)   // calendar se din badla = upar ki line
    val o = g.view("discovery", p)
    val item = remember(o) { discItem(o) }
    val wide = isWide()
    var seg by rememberSaveable { mutableIntStateOf(0) }
    KScreen(top = {
        bar()
        if (!wide && item != null) DiscHero(item)
        else if (!wide && sSkelOn(g, o)) KSkelHero(KTint.Lav, split = 4)
    }) {
        SLiveCards(live.forPage(setOf("discover")), live, g, vm, nav)   // aaj ki khoj chal rahi (mockup ui_live)
        SLoad(g, o, { vm.load("discovery", p) }, { DiscSkel(wide) }) { _ ->
            when {
                item == null -> DiscEmpty()
                wide -> KTwoPane(
                    left = {
                        DiscHero(item)
                        DiscTabs(item, seg) { seg = it }
                    },
                    right = { DiscList(item, seg) },
                )
                else -> {
                    DiscTabs(item, seg) { seg = it }
                    DiscList(item, seg)
                }
            }
        }
    }
}

/**
 * Lavender hero: dikhaya gaya din ("Today's scan"; mockup ka "Last 14 days" nahi, number ek hi din ka hai) + ranking pill
 * (pill sirf jab sach: ranking me liya / agli ranking padhegi), bada findings number, 4 khaane (OTHER sirf list me).
 */
@Composable
private fun DiscHero(it0: DiscItem) {
    KHero(KTint.Lav) {
        KHeroHead(discScan(it0.createdAt), icon = KIcons.Search, trailing = if (it0.used || it0.forNext) {
            { if (it0.used) KPill("Used in ranking".t(), KKind.OK) else KPill("For next ranking".t(), KKind.NEUTRAL) }
        } else null)
        if (it0.notesOnly) {
            // sirf notes: sources ki ginti bar + tab me pehle se, yahan bas "Notes only"
            KBigNum("Notes only".t(), size = 34.sp)
        } else {
            val n = it0.findings.size
            KBigNum(num(n.toDouble()), suffix = " " + (if (n == 1) "finding" else "findings").t(), size = 48.sp)
            val cn = { t: String -> num((it0.counts[t] ?: 0).toDouble()) }
            KSplit(listOf("New apps".t() to cn("NEW_APP"), "Rivals".t() to cn("COMPETITOR"), "Policy".t() to cn("POLICY"), "Ads".t() to cn("ADS")))
        }
    }
}

@Composable
private fun DiscTabs(it0: DiscItem, seg: Int, onSeg: (Int) -> Unit) {
    KSegTabs(listOf(if (it0.notesOnly) "Notes".t() else "Findings".t(), tf("Sources %s", num(it0.sources.size.toDouble()))), seg, onSeg)
}

@Composable
private fun DiscList(it0: DiscItem, seg: Int) {
    when {
        seg == 1 -> DiscSources(it0.sources)
        it0.notesOnly -> DiscNotes(it0.notes)
        else -> DiscFindings(it0)
    }
}

/** Ek card, har finding ek block (beech me line). */
@Composable
private fun DiscFindings(it0: DiscItem) {
    if (it0.findings.isEmpty()) {
        KCard { KEmpty("No findings".t(), KIcons.Search) }
        return
    }
    KCard(list = true) {
        it0.findings.forEachIndexed { i, f ->
            if (i > 0) KDivider()
            DiscBlock(f, "disc-${it0.id}-$i")
        }
    }
}

/** Finding: type tile, bold title, detail, "Source says" / "AI guess" + "2 sources ›". Poora block tap = sources khule / band. */
@Composable
private fun DiscBlock(f: DiscFinding, key: String) {
    val c = Mfc.c
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val n = f.sources.size
    var m = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    if (n > 0) m = m.clickable(role = Role.Button) { open = !open }
    Column(m) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KIconTile(discIcon(f.type), discKind(f.type))
            Column(Modifier.weight(1f)) {
                if (f.title.isNotBlank()) SText(f.title, 15.sp, c.ink, weight = FontWeight.Bold, lh = 20.sp, maxLines = 3)
                if (f.detail.isNotBlank()) SText(f.detail, 14.sp, c.ink2, Modifier.padding(top = 2.dp), lh = 19.sp, maxLines = Int.MAX_VALUE)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiscTag(f.said)
                    if (n > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                        SText(sourcesText(n), 14.sp, c.brand, weight = FontWeight.SemiBold, lh = 18.sp)
                        Icon(KIcons.Chev, null, tint = c.brand, modifier = Modifier.size(18.dp).rotate(if (open) 90f else 0f))
                    }
                }
            }
        }
        // khula: us finding ke sources (naam + domain), tap = browser
        if (open) Column(
            Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp, bottom = 14.dp).clip(RoundedCornerShape(14.dp)).background(c.well),
        ) {
            f.sources.forEachIndexed { i, s ->
                if (i > 0) KDivider()
                DiscSrcRow(s, age = false)
            }
        }
    }
}

/** "Source says" (hara) / "AI guess" (amber) chhota tag. */
@Composable
private fun DiscTag(said: Boolean) {
    val c = Mfc.c
    Box(
        Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(if (said) c.pOkBg else c.pWarnBg).padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) { SText(if (said) "Source says".t() else "AI guess".t(), 13.sp, if (said) c.pOkFg else c.pWarnFg, weight = FontWeight.SemiBold, lh = 16.sp) }
}

/** Source: title, domain (· page age), tap = browser me. */
@Composable
private fun DiscSrcRow(s: DiscSrc, age: Boolean) {
    val c = Mfc.c
    val open = rememberOpenUrl()
    val link = s.url.startsWith("http")
    val dom = discDomain(s.url)
    var m = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (link) m = m.clickable(role = Role.Button) { open(s.url) }
    Row(
        m.padding(horizontal = if (age) 16.dp else 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            SText(s.title.ifBlank { dom.ifBlank { s.url.ifBlank { "#" + s.id } } }, if (age) 15.sp else 14.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp, maxLines = 2)
            val meta = listOfNotNull(dom.ifBlank { null }, if (age) s.age.ifBlank { null } else null).joinToString(" · ")
            if (meta.isNotBlank()) SText(meta, 13.sp, c.ink3, Modifier.padding(top = 2.dp), lh = 18.sp)
        }
        if (link) Icon(KIcons.Open, null, tint = c.brand, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DiscSources(list: List<DiscSrc>) {
    if (list.isEmpty()) {
        KCard { KEmpty("No sources".t(), KIcons.Globe) }
        return
    }
    KCard(list = true) {
        list.forEachIndexed { i, s ->
            if (i > 0) KDivider(16.dp, 16.dp)
            DiscSrcRow(s, age = true)
        }
    }
}

/** structured = false: AI ke raw notes (select / copy ho sakte hain). */
@Composable
private fun DiscNotes(notes: String) {
    val c = Mfc.c
    KCard {
        // "Notes" heading nahi: upar tab pehle se "Notes"
        if (notes.isBlank()) KEmpty("No findings".t(), KIcons.Search)
        else SelectionContainer {
            Text(notes.trim(), Modifier.fillMaxWidth(), color = c.ink, style = sStyle(14.sp, lh = 20.sp))
        }
    }
}

/** Abhi koi khoj nahi: kab chalti hai. */
@Composable
private fun DiscEmpty() {
    KCard {
        KEmpty("No discovery yet".t(), KIcons.Search)
        SText(
            "Daily discovery runs every day when it is on in Settings.".t(), 14.sp, Mfc.c.ink2, Modifier.fillMaxWidth(), lh = 20.sp, maxLines = 3,
            align = TextAlign.Center,
        )
    }
}

/** Skeleton: tabs + rows. Fold: hero + tabs baayein, rows daayein. Phone ka hero top me (DiscoveryPage). */
@Composable
private fun DiscSkel(wide: Boolean) {
    KSkeleton {
        if (wide) KTwoPane(
            left = { KSkelHero(KTint.Lav, split = 4); KSkelBlock(48.dp, RoundedCornerShape(24.dp)) },
            right = { KSkelRows(4) },
        ) else {
            KSkelBlock(48.dp, RoundedCornerShape(24.dp))
            KSkelRows(4)
        }
    }
}
