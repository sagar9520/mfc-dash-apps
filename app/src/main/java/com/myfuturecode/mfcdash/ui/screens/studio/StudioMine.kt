package com.myfuturecode.mfcdash.ui.screens.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.myfuturecode.mfcdash.ui.GState
import com.myfuturecode.mfcdash.ui.StudioVm
import com.myfuturecode.mfcdash.ui.kit.KAvatar
import com.myfuturecode.mfcdash.ui.kit.KBigNum
import com.myfuturecode.mfcdash.ui.kit.KBtn
import com.myfuturecode.mfcdash.ui.kit.KButton
import com.myfuturecode.mfcdash.ui.kit.KCard
import com.myfuturecode.mfcdash.ui.kit.KDivider
import com.myfuturecode.mfcdash.ui.kit.KEmpty
import com.myfuturecode.mfcdash.ui.kit.KFit
import com.myfuturecode.mfcdash.ui.kit.KHero
import com.myfuturecode.mfcdash.ui.kit.KHeroHead
import com.myfuturecode.mfcdash.ui.kit.KIconTile
import com.myfuturecode.mfcdash.ui.kit.KIcons
import com.myfuturecode.mfcdash.ui.kit.KKind
import com.myfuturecode.mfcdash.ui.kit.KListDivider
import com.myfuturecode.mfcdash.ui.kit.KPill
import com.myfuturecode.mfcdash.ui.kit.KScreen
import com.myfuturecode.mfcdash.ui.kit.KSection
import com.myfuturecode.mfcdash.ui.kit.KSectionLink
import com.myfuturecode.mfcdash.ui.kit.KSegTabs
import com.myfuturecode.mfcdash.ui.kit.KSkelBlock
import com.myfuturecode.mfcdash.ui.kit.KSkelCard
import com.myfuturecode.mfcdash.ui.kit.KSkelHero
import com.myfuturecode.mfcdash.ui.kit.KSkelRows
import com.myfuturecode.mfcdash.ui.kit.KSkeleton
import com.myfuturecode.mfcdash.ui.kit.KSmallAction
import com.myfuturecode.mfcdash.ui.kit.KSortKind
import com.myfuturecode.mfcdash.ui.kit.KSortPill
import com.myfuturecode.mfcdash.ui.kit.KSortState
import com.myfuturecode.mfcdash.ui.kit.KSplit
import com.myfuturecode.mfcdash.ui.kit.KStatus
import com.myfuturecode.mfcdash.ui.kit.KTint
import com.myfuturecode.mfcdash.ui.kit.KTwoPane
import com.myfuturecode.mfcdash.ui.kit.fg
import com.myfuturecode.mfcdash.ui.kit.isWide
import com.myfuturecode.mfcdash.ui.kit.kLeftLine
import com.myfuturecode.mfcdash.ui.kit.kRoasKind
import com.myfuturecode.mfcdash.ui.kit.kSorted
import com.myfuturecode.mfcdash.ui.kit.kStyle
import com.myfuturecode.mfcdash.ui.screens.moneySigned
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.util.arr
import com.myfuturecode.mfcdash.util.b
import com.myfuturecode.mfcdash.util.d
import com.myfuturecode.mfcdash.util.dn
import com.myfuturecode.mfcdash.util.num
import com.myfuturecode.mfcdash.util.obj
import com.myfuturecode.mfcdash.util.pct
import com.myfuturecode.mfcdash.util.roasPct
import com.myfuturecode.mfcdash.util.s
import com.myfuturecode.mfcdash.util.sn
import com.myfuturecode.mfcdash.util.strings
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import com.myfuturecode.mfcdash.util.usd
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

// ---------------------------------------------------------------- Your apps (portfolio): aapke apne apps ka eCPM, ROAS, desh ka profit / loss

/** Server ka window (ST_PORT_DAYS); overview.mine me days nahi aata. */
private const val MINE_DAYS = 30

/** Sort (bar ka button): Apps aur Countries list par; default = server ka order (profit zyada -> kam). */
internal val MINE_SORT_DEF = "profit" to true

internal fun mineSortOpts(): List<Pair<String, String>> =
    listOf("profit" to "Profit".t(), "roas" to "ROAS", "ecpm" to "eCPM", "installs" to "Installs".t())

private val MINE_SORT_KINDS = emptyMap<String, KSortKind>()

private fun List<JSONObject>.mineSorted(sort: KSortState?): List<JSONObject> = when {
    sort == null || sort.isDefault -> this
    sort.key == "roas" -> kSorted(sort.desc) { it.dn("roas") }
    sort.key == "ecpm" -> kSorted(sort.desc) { it.dn("ecpm_usd") }
    sort.key == "installs" -> kSorted(sort.desc) { it.dn("installs") }
    else -> kSorted(sort.desc) { it.dn("profit_inr") }
}

/** Bar ki sub line: "Last 30 days · 25 apps" (data aane tak null). */
internal fun mineSub(g: GState): String? {
    val m = g.view("mine")?.takeIf { !it.b("empty") } ?: return null
    return tf("Last %s days", m.d("days", MINE_DAYS.toDouble()).toInt()) + " · " + tf("%s apps", num(m.obj("totals").d("apps")))
}

/** Overview / More list: portfolio job abhi queue me ya chal raha (overview ke haal ke runs se). */
internal fun mineRunning(o: JSONObject): Boolean = o.arr("runs").objs().any { it.s("job") == "portfolio" && stBusy(it.s("status")) }

/** Run ke yeh haal = fail (wajah dikhao, warna "Run now" baar baar bina wajah). */
private val MINE_FAIL = setOf("FAILED", "PARTIAL", "BLOCKED")

/** Overview: haal ke runs (naye pehle) me sabse naya portfolio run fail hua? */
private fun mineOvFailed(o: JSONObject): Boolean =
    o.arr("runs").objs().firstOrNull { it.s("job") == "portfolio" }?.let { it.s("status").uppercase() in MINE_FAIL } == true

// ---------------------------------------------------------------- money / number (chhote, taaki 3 khaane me fit ho)

/** "1.10" -> "1.1", "73.0" -> "73". */
private fun trimZero(s: String): String = if ('.' in s) s.trimEnd('0').trimEnd('.') else s

/** Chhota ₹: ₹980 / ₹73.9k / ₹1.12L / ₹2.4Cr. signed = "+" bhi (ghata hamesha asli minus "−"). */
internal fun inrK(v: Double?, signed: Boolean = false): String {
    if (v == null || !v.isFinite()) return "–"
    val a = abs(v)
    val body = when {
        a >= 99_95_000 -> trimZero(num(a / 1_00_00_000, 2)) + "Cr"
        a >= 99_950 -> trimZero(num(a / 1_00_000, 2)) + "L"
        a >= 999.5 -> trimZero(num(a / 1000, 1)) + "k"
        else -> num(a)
    }
    val sign = when {
        v <= -0.5 -> "−"
        signed && v >= 0.5 -> "+"
        else -> ""
    }
    return sign + "₹" + body
}

/** Installs: 820 / 2.1k / 1.2L. */
private fun numK(v: Double): String = when {
    v >= 99_950 -> trimZero(num(v / 1_00_000, 2)) + "L"
    v >= 999.5 -> trimZero(num(v / 1000, 1)) + "k"
    else -> num(v)
}

/** eCPM: "$1.92" (null = "–"). */
private fun ecpmText(v: Double?): String = if (v == null) "–" else usd(v, 2)

/** ROAS %: 100% se kam ko neeche round (0.996 = "99%", kabhi "100%" nahi), taaki ghate wala row "ROAS 100%" na dikhe. */
internal fun roasTxt(r: Double?): String = when {
    r == null || !r.isFinite() -> "–"
    r < 1.0 -> num(minOf(floor(r * 100 + 1e-9), 99.0)) + "%"
    else -> roasPct(r)
}

/** "US" -> "United States" (English naam, app English me chalta hai). "" = Unknown. */
private fun ccName(cc: String): String {
    if (cc.isBlank()) return "Unknown".t()
    return runCatching { Locale.Builder().setRegion(cc).build().getDisplayCountry(Locale.ENGLISH) }.getOrNull()?.ifBlank { null } ?: cc
}

/** Profit ka rang: 0 ya zyada = hara, ghata = laal. */
@Composable
private fun profitColor(v: Double): Color = if (v < 0) Mfc.c.red else Mfc.c.green

/** ROAS pill: 100% ya zyada = Theek (hara), kam = laal; spend nahi = "No ad spend". */
@Composable
private fun MineRoasPill(roas: Double?) {
    if (roas == null || !roas.isFinite()) KPill("No ad spend".t(), KKind.NEUTRAL, icon = null)
    else KPill("ROAS " + roasTxt(roas), kRoasKind(roas, 1.0))
}

// ---------------------------------------------------------------- Overview card (Needs OK ke baad)

/** Overview ka "Your apps" section: 30 din ka profit | eCPM | Ads on + best / loss desh. Job kabhi nahi chala = chhota card + Run now. */
@Composable
internal fun MineSection(o: JSONObject, g: GState, vm: StudioVm, go: (String) -> Unit) {
    // key hi nahi (purana server / purana cache) = kuch nahi; sirf JSON null = job kabhi nahi chala
    if (!o.has("mine")) return
    KSection("Your apps".t(), trailing = { KSectionLink("Open".t()) { go("mine") } })
    val m = o.optJSONObject("mine")
    if (m == null) {
        // job kabhi nahi chala: chhota card + Run now (chal raha ho to wait line)
        val c = Mfc.c
        val running = mineRunning(o)
        KCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KIconTile(KIcons.Rupee)
                Column(Modifier.weight(1f)) {
                    SText("See eCPM, ROAS and country profit for your apps".t(), 14.sp, c.ink2, lh = 20.sp, maxLines = 3)
                    if (running) MineWait(Modifier.padding(top = 4.dp))
                    else if (mineOvFailed(o)) SText("Last run failed".t(), 13.sp, KKind.BAD.fg(), Modifier.padding(top = 4.dp), weight = FontWeight.SemiBold, lh = 18.sp)
                }
                if (!running) KSmallAction("Run now".t(), {
                    vm.act("run", mapOf("job" to "portfolio"), "run_portfolio", ok = "Queued. The server picks it up within 5 minutes.".t()) {
                        vm.load("overview")
                    }
                }, enabled = "run_portfolio" !in g.busy)
            }
        }
        return
    }
    MineCard(m) { go("mine") }
}

@Composable
private fun MineCard(m: JSONObject, onClick: () -> Unit) {
    val c = Mfc.c
    val profit = m.d("profit_inr")
    KCard(onClick = onClick, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            SText(tf("Last %s days", MINE_DAYS), 15.sp, c.ink, Modifier.weight(1f), weight = FontWeight.Bold, lh = 20.sp)
            MineRoasPill(m.dn("roas"))
        }
        // 3 khaane: Profit | eCPM | Ads on (beech me line, upar line nahi)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            val cells = listOf(
                "Profit".t() to AnnotatedString(moneySigned(profit)),
                "eCPM" to AnnotatedString(ecpmText(m.dn("ecpm_usd"))),
                "Ads on".t() to buildAnnotatedString {
                    append(num(m.d("apps_ads_on")))
                    withStyle(SpanStyle(fontSize = 15.sp, color = c.ink3, fontWeight = FontWeight.SemiBold)) { append(" / " + num(m.d("apps"))) }
                },
            )
            cells.forEachIndexed { i, (k, v) ->
                Column(Modifier.weight(1f).kLeftLine(c.line, i > 0).padding(start = if (i > 0) 14.dp else 0.dp, end = 4.dp)) {
                    SText(k, 14.sp, c.ink2, lh = 18.sp)
                    KFit(
                        v, kStyle(20.sp, FontWeight.Bold, 28.sp), if (i == 0) profitColor(profit) else c.ink, Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
        // do halke chips: sabse achha desh / sabse bada ghata (jo ho wahi)
        val best = m.optJSONObject("best")
        val loss = m.optJSONObject("loss")
        if (best != null || loss != null) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            best?.let { MineChip(tf("Best: %s %s", it.s("cc"), chipMoney(it)), true, Modifier.weight(1f)) }
            loss?.let { MineChip(tf("Loss: %s %s", it.s("cc"), chipMoney(it)), false, Modifier.weight(1f)) }
        }
    }
}

/** Chip ka paisa: "+₹14.2k"; us desh ka spend andaza ho to "≈+₹14.2k" (Cost screen jaisa ≈). */
private fun chipMoney(x: JSONObject): String = (if (x.b("spend_est")) "≈" else "") + inrK(x.d("profit_inr"), true)

/** Halka hara / laal chip (36dp, 12dp kone) + trend teer. Text jagah kam ho to chhota hota hai, paisa kabhi "…" nahi. */
@Composable
private fun MineChip(text: String, good: Boolean, modifier: Modifier = Modifier) {
    val c = Mfc.c
    val fg = if (good) c.green else c.red
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(if (good) c.greenSoft else c.redSoft).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (good) KIcons.TrendUp else KIcons.TrendDown, null, tint = fg, modifier = Modifier.size(14.dp))
        KFit(text, kStyle(13.sp, FontWeight.SemiBold, 18.sp), fg)
    }
}

// ---------------------------------------------------------------- tab "mine" (mockup st_a2..a4)

/**
 * Your apps: hero (30 din ka profit + ROAS, Earnings | Ads spend | eCPM), phir Apps | Countries | For research.
 * Hero top me (Overview jaisa). Fold: hero + "For research" baayein, Apps | Countries daayein.
 * Job chal raha ho to har 15 s par load, warna khulne / refresh par (aur 5 min me ek baar).
 */
@Composable
internal fun MinePage(
    g: GState, vm: StudioVm, nav: NavHostController, reload: Int, bar: @Composable ColumnScope.() -> Unit, sort: KSortState?,
    seg: Int, onSeg: (Int) -> Unit,
) {
    val m = g.view("mine")
    val running = m?.b("running") == true
    val live = sLive(vm)
    val lr = live.forPage(setOf("portfolio", "economics"))
    // live chalu (naya server): khatam hone par fin se reload, 15 s wala poll nahi; purana server = pehle jaisa
    val fast = running && !live.ok
    SPoll(if (fast) 15_000L else 300_000L, reload, fast, live.finOf("portfolio", "economics")) { vm.load("mine") }
    val wide = isWide()
    val ready = m != null && !m.b("empty")
    // live card dikh raha = apni "Updating…" / "Running…" line nahi (wahi baat card me)
    val liveCard = lr.any { it.s("job") == "portfolio" }
    KScreen(top = {
        bar()
        if (!wide && m != null && ready) MineHero(m, !liveCard)
        else if (!wide && sSkelOn(g, m)) KSkelHero(KTint.Green, split = 3)
    }) {
        SLiveCards(lr, live, g, vm, nav)
        SLoad(g, m, { vm.load("mine") }, { MineSkel(wide) }) { mm ->
            if (mm.b("empty")) MineEmpty(mm, g, vm, !liveCard)
            else MineBody(mm, g, vm, nav, wide, sort, seg, onSeg, !liveCard)
        }
    }
}

/** Phone par "For research" khula = bar ka Sort chhupa (us list me sort nahi). seg StudioScreen me yaad rehta hai. */
internal fun mineSortOn(seg: Int, wide: Boolean): Boolean = wide || seg < 2

@Composable
private fun MineBody(
    m: JSONObject, g: GState, vm: StudioVm, nav: NavHostController, wide: Boolean, sort: KSortState?, seg: Int, onSeg: (Int) -> Unit,
    showRun: Boolean = true,
) {
    if (wide) {
        KTwoPane(
            left = {
                MineHero(m, showRun)
                KSection("For research".t())
                MineResearch(m, g, vm)
            },
            right = {
                // Fold: "For research" baayein hai, yahan sirf Apps | Countries
                val s = seg.coerceAtMost(1)
                KSegTabs(listOf("Apps".t(), "Countries".t()), s, onSeg)
                if (s == 0) MineApps(m, nav, sort) else MineCountries(m, sort)
                MineUpdated(m)
            },
        )
        return
    }
    KSegTabs(listOf("Apps".t(), "Countries".t(), "For research".t()), seg, onSeg)
    when (seg) {
        0 -> MineApps(m, nav, sort)
        1 -> MineCountries(m, sort)
        else -> MineResearch(m, g, vm)
    }
    MineUpdated(m)
}

/** Hara hero: "Profit · 30 days" + ROAS pill, bada profit, Earnings | Ads spend | eCPM. Job chal raha = chhoti "Updating" line (live card na ho to). */
@Composable
private fun MineHero(m: JSONObject, showRun: Boolean = true) {
    val t = m.obj("totals")
    val profit = t.d("profit_inr")
    KHero(KTint.Green) {
        KHeroHead(tf("Profit · %s days", m.d("days", MINE_DAYS.toDouble()).toInt()), icon = KIcons.Chart, trailing = { MineRoasPill(t.dn("roas")) })
        KBigNum(moneySigned(profit), profitColor(profit))
        KSplit(
            listOf("Earnings".t() to inrK(t.d("earn_inr")), "Ads spend".t() to inrK(t.d("spend_inr")), "eCPM" to ecpmText(t.dn("ecpm_usd"))),
            compact = true,
        )
        if (showRun && m.b("running")) KStatus("Updating… new numbers in a few minutes".t(), KKind.ASK, Modifier.padding(top = 10.dp))
    }
}

/** Apps: avatar, naam, "eCPM $2.40 · [Ads on]", daayein profit + ROAS. Tap = app detail. */
@Composable
private fun MineApps(m: JSONObject, nav: NavHostController, sort: KSortState?) {
    val apps = m.arr("apps").objs().mineSorted(sort)
    if (sort != null) KSortPill(sort, mineSortOpts(), MINE_SORT_KINDS)
    if (apps.isEmpty()) {
        KCard { KEmpty("No apps".t(), KIcons.Apps) }
        return
    }
    KCard(list = true) {
        apps.forEachIndexed { i, a ->
            if (i > 0) KListDivider(true)
            val pkg = a.s("pkg")
            MineRow(
                lead = { KAvatar(a.s("name").ifBlank { pkg }) },
                title = a.s("name").ifBlank { pkg },
                sub = a.dn("ecpm_usd")?.let { "eCPM " + usd(it, 2) },
                chip = { MineAdsChip(a.b("ads_on")) },
                profit = a.d("profit_inr"),
                roasLine = mineRoasLine(a, false),
                onClick = if (pkg.isNotBlank()) ({ nav.navigate("app/$pkg") }) else null,
            )
        }
    }
}

/**
 * Countries: 2 akshar ka tile, desh ka naam, "eCPM $4.10 · 2.1k installs", daayein profit + ROAS.
 * "est." sirf "mixed" me (kuch desh exact, kuch andaza); sab andaza ho to neeche ki note kaafi hai (mockup st_a3).
 */
@Composable
private fun MineCountries(m: JSONObject, sort: KSortState?) {
    val c = Mfc.c
    val mixed = m.s("spend_by_cc") == "mixed"
    val list = m.arr("countries").objs().mineSorted(sort)
    if (sort != null) KSortPill(sort, mineSortOpts(), MINE_SORT_KINDS)
    if (list.isEmpty()) KCard { KEmpty("No country data".t(), KIcons.Globe) }
    else KCard(list = true) {
        list.forEachIndexed { i, x ->
            if (i > 0) KListDivider(true)
            val cc = x.s("cc")
            MineRow(
                lead = { MineCcTile(cc) },
                title = ccName(cc),
                sub = listOfNotNull(x.dn("ecpm_usd")?.let { "eCPM " + usd(it, 2) }, tf("%s installs", numK(x.d("installs")))).joinToString(" · "),
                chip = null,
                profit = x.d("profit_inr"),
                roasLine = mineRoasLine(x, mixed && x.b("spend_est")),
                onClick = null,
            )
        }
    }
    // spend ka desh-wise hissa andaza ho to saaf likho (Ads script aane par exact)
    if (m.s("spend_by_cc") in setOf("estimate", "mixed")) SText(
        "Ads spend by country is an estimate (from paid installs) until the Google Ads script is on. Then it is exact.".t(),
        13.sp, c.ink3, Modifier.fillMaxWidth().padding(horizontal = 6.dp), lh = 18.sp, maxLines = 4,
    )
}

/** "ROAS 210%" / "ROAS 81% est." / "no ad spend". */
private fun mineRoasLine(x: JSONObject, est: Boolean): String {
    val r = x.dn("roas")
    if (r == null || x.d("spend_inr") <= 0.0) return "no ad spend".t()
    return "ROAS " + roasTxt(r) + if (est) " " + "est.".t() else ""
}

/** Ek row: lead, naam, sub (+ chip), daayein signed profit (hara / laal) + ROAS line. */
@Composable
private fun MineRow(
    lead: @Composable () -> Unit,
    title: String,
    sub: String?,
    chip: (@Composable () -> Unit)?,
    profit: Double,
    roasLine: String,
    onClick: (() -> Unit)?,
) {
    val c = Mfc.c
    var mod = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (onClick != null) mod = mod.clickable(onClick = onClick)
    Row(
        mod.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        lead()
        Column(Modifier.weight(1f)) {
            SText(title, 15.sp, c.ink, weight = FontWeight.SemiBold, lh = 20.sp)
            if (sub != null || chip != null) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (sub != null) SText(if (chip != null) "$sub · " else sub, 13.sp, c.ink2, Modifier.weight(1f, fill = false), lh = 18.sp)
                chip?.invoke()
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            SText(moneySigned(profit), 16.sp, profitColor(profit), weight = FontWeight.Bold, lh = 20.sp)
            SText(roasLine, 13.sp, c.ink3, Modifier.padding(top = 2.dp), lh = 18.sp)
        }
    }
}

/** "Ads on" (hara) / "No ads" (sada) chhota chip: Google Ads par pichhle 7 din me kharcha hua ya nahi. */
@Composable
private fun MineAdsChip(on: Boolean) {
    val c = Mfc.c
    Box(
        Modifier.height(22.dp).clip(RoundedCornerShape(11.dp)).background(if (on) c.pOkBg else c.pNBg).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { SText(if (on) "Ads on".t() else "No ads".t(), 12.sp, if (on) c.pOkFg else c.pNFg, weight = FontWeight.Bold, lh = 16.sp) }
}

/** Desh ka 40dp tile (2 akshar). */
@Composable
private fun MineCcTile(cc: String) {
    val c = Mfc.c
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.well), contentAlignment = Alignment.Center) {
        SText(cc.ifBlank { "?" }, 15.sp, c.ink, weight = FontWeight.Bold, lh = 20.sp)
    }
}

// ---------------------------------------------------------------- For research (insights)

private class MineIns(val icon: ImageVector, val kind: KKind, val title: String, val line: AnnotatedString)

/** Template (pehle .t()) ke %s ki jagah bold values. */
private fun fill(template: String, args: List<String>, bold: Color): AnnotatedString = buildAnnotatedString {
    val parts = template.split("%s")
    parts.forEachIndexed { i, p ->
        append(p)
        if (i < parts.size - 1) withStyle(SpanStyle(color = bold, fontWeight = FontWeight.SemiBold)) { append(args.getOrElse(i) { "" }) }
    }
}

/** eCPM ki seema, hamesha neeche round (asli minimum se zyada kabhi nahi): 3.20 = "$3+", 0.458 = "$0.45+". */
private fun ecpmMin(v: Double): String =
    (if (v >= 1.0) usd(floor(v + 1e-9), 0) else usd(floor(v * 100 + 1e-9) / 100, 2)) + "+"

/** Server ke insights (sirf type + numbers) -> app ke shabd. */
private fun mineInsights(m: JSONObject, ink: Color): List<MineIns> {
    val days = m.d("days", MINE_DAYS.toDouble()).toInt()
    return m.arr("insights").objs().mapNotNull { x ->
        when (x.s("type")) {
            "focus" -> {
                val ccs = x.arr("ccs").strings().filter { it.isNotBlank() }
                val one = ccs.size == 1
                if (ccs.isEmpty()) null
                else MineIns(
                    KIcons.Target, KKind.OK,
                    if (one) tf("Focus country: %s", ccs[0]) else tf("Focus countries: %s", ccs.joinToString(", ")),
                    fill(
                        if (one) "eCPM %s and profit on ads. New ideas target this country first.".t()
                        else "eCPM %s and profit on ads. New ideas target these first.".t(),
                        listOf(ecpmMin(x.d("ecpm_min_usd"))), ink,
                    ),
                )
            }
            "avoid_paid" -> {
                val ccs = x.arr("ccs").strings().filter { it.isNotBlank() }
                // ghate wale desh: ROAS neeche round (kabhi "100%" nahi)
                val lo = roasTxt(x.d("roas_min"))
                val hi = roasTxt(x.d("roas_max"))
                val range = if (lo == hi) lo else lo.removeSuffix("%") + "–" + hi
                if (ccs.isEmpty()) null
                else MineIns(
                    KIcons.TrendDown, KKind.BAD, tf("Avoid paid ads: %s", ccs.joinToString(", ")),
                    buildAnnotatedString {
                        val tpl = if (ccs.size == 1) "Ads cost more than they earn (ROAS %s). Ideas for this country are organic only.".t()
                        else "Ads cost more than they earn (ROAS %s). Ideas for these are organic only.".t()
                        append(fill(tpl, listOf(range), ink))
                        if (x.b("est")) append(" " + "Spend is an estimate.".t())
                    },
                )
            }
            "strong" -> {
                val cat = x.s("category")
                MineIns(
                    KIcons.Ok, KKind.OK, tf("Strongest: %s apps", cat),
                    buildAnnotatedString {
                        // jo number ho wahi: ROAS, D7; dono na hon to eCPM, warna profit
                        val parts = listOfNotNull(x.dn("roas")?.let { "ROAS" to roasTxt(it) }, x.dn("d7")?.let { "D7" to pct(it, 0) })
                            .ifEmpty { listOfNotNull(x.dn("ecpm_usd")?.let { "eCPM" to usd(it, 2) }) }
                        if (parts.isEmpty()) append(fill("Profit %s in %s days.".t(), listOf(moneySigned(x.d("profit_inr")), days.toString()), ink))
                        else {
                            parts.forEachIndexed { i, (k, v) ->
                                if (i > 0) append(", ")
                                append("$k ")
                                withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(v) }
                            }
                            append(".")
                        }
                        append(" " + tf("Ranking gives %s ideas a higher score.", cat))
                    },
                )
            }
            "weak" -> {
                val cat = x.s("category")
                val loss = x.d("profit_inr") < 0
                val e = x.dn("ecpm_usd")
                MineIns(
                    KIcons.Tri, if (loss) KKind.BAD else KKind.WARN, tf("Weak: %s apps", cat),
                    buildAnnotatedString {
                        when {
                            loss && e != null -> append(fill("eCPM %s, loss on ads.".t(), listOf(usd(e, 2)), ink))
                            loss -> append(fill("Loss on ads: %s in %s days.".t(), listOf(moneySigned(x.d("profit_inr")), days.toString()), ink))
                            e != null -> append(fill("Low eCPM: %s.".t(), listOf(usd(e, 2)), ink))
                        }
                        append(" " + tf("%s ideas need a better plan.", cat))
                    },
                )
            }
            "app_loss" -> {
                val names = x.arr("names").strings().filter { it.isNotBlank() }.ifEmpty { x.arr("pkgs").strings() }
                if (names.isEmpty()) null
                else MineIns(
                    KIcons.Apps, KKind.BAD, tf("Losing on ads: %s", names.joinToString(", ")),
                    fill(
                        if (names.size == 1) "Ads spend is more than it earns: %s in %s days.".t() else "Ads spend is more than they earn: %s in %s days.".t(),
                        listOf(moneySigned(-x.d("loss_inr")), days.toString()), ink,
                    ),
                )
            }
            else -> null
        }
    }
}

/** For research: insights card + "Run ranking with this" + note. */
@Composable
private fun MineResearch(m: JSONObject, g: GState, vm: StudioVm) {
    val c = Mfc.c
    val ins = mineInsights(m, c.ink)
    if (ins.isEmpty()) KCard { KEmpty("Not enough data yet for clear advice.".t(), KIcons.Info) }
    else KCard(list = true) {
        ins.forEachIndexed { i, x ->
            if (i > 0) KDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KIconTile(x.icon, x.kind)
                Column(Modifier.weight(1f)) {
                    SText(x.title, 15.sp, c.ink, weight = FontWeight.Bold, lh = 20.sp, maxLines = 2)
                    Text(x.line, Modifier.padding(top = 2.dp), color = c.ink2, style = sStyle(14.sp, lh = 19.sp))
                }
            }
        }
    }
    KButton(
        "Run ranking with this".t(), {
            vm.act("run", mapOf("job" to "rank"), "run_rank", ok = "Ranking queued. It starts within 5 minutes.".t())
        },
        Modifier.fillMaxWidth(), kind = KBtn.SOFT, icon = KIcons.Play, busy = "run_rank" in g.busy,
    )
    SText(weeklyNote(g), 13.sp, c.ink3, Modifier.fillMaxWidth().padding(horizontal = 6.dp), lh = 18.sp, maxLines = 3)
}

/**
 * Hafte ki ranking khud is analysis ko use karti hai. Din: overview.schedule.weekly_dow, warna Settings tab ka data;
 * pata na ho to din ka naam nahi (galat "Monday" kabhi nahi). Band ho to saaf likho.
 */
private fun weeklyNote(g: GState): String {
    val sch = g.view("overview")?.obj("schedule")
    val on = sch?.let { if (it.has("weekly")) it.b("weekly") else null } ?: true
    if (!on) return "Weekly ranking is off. Run it here when you want.".t()
    val dow = sch?.takeIf { it.has("weekly_dow") }?.d("weekly_dow")?.toInt()
        ?: g.view("settings")?.obj("settings")?.sn("st_weekly_dow")?.toIntOrNull()
    return if (dow == 1) "Every Monday ranking uses this analysis on its own.".t() else "Weekly ranking uses this analysis on its own.".t()
}

/** "Updated 2 hours ago" (server ka computed_at). */
@Composable
private fun MineUpdated(m: JSONObject) {
    val at = m.sn("computed_at") ?: return
    SText(
        "Updated".t() + " " + sAgo(at), 13.sp, Mfc.c.ink3, Modifier.fillMaxWidth().padding(vertical = 4.dp), lh = 18.sp, align = TextAlign.Center,
    )
}

// ---------------------------------------------------------------- khaali / skeleton

/** Pichhla portfolio run fail hua (server last_run): wajah, warna null. Chal raha ho to null. */
private fun mineFail(m: JSONObject): String? {
    if (m.b("running")) return null
    val r = m.optJSONObject("last_run") ?: return null
    if (r.s("status").uppercase() !in MINE_FAIL) return null
    return r.sn("error_text")?.trim()?.ifBlank { null }?.let { "Last run failed".t() + ": " + it } ?: "Last run failed".t()
}

/** Job kabhi nahi chala (ya pichhla run fail): "No analysis yet" + wajah + Run now (chal raha ho to wait line). */
@Composable
private fun MineEmpty(m: JSONObject, g: GState, vm: StudioVm, showRun: Boolean = true) {
    val c = Mfc.c
    val running = m.b("running")
    KCard {
        KEmpty("No analysis yet".t(), KIcons.Rupee)
        SText(
            "See eCPM, ROAS and country profit for your apps".t(), 14.sp, c.ink2, Modifier.fillMaxWidth(), lh = 20.sp, maxLines = 3,
            align = TextAlign.Center,
        )
        mineFail(m)?.let {
            SText(
                it, 13.sp, KKind.BAD.fg(), Modifier.fillMaxWidth().padding(top = 10.dp), weight = FontWeight.SemiBold, lh = 18.sp, maxLines = 3,
                align = TextAlign.Center,
            )
        }
        if (running) { if (showRun) MineWait(Modifier.fillMaxWidth().padding(top = 14.dp), TextAlign.Center) }
        else KButton(
            "Run now".t(), {
                vm.act("run", mapOf("job" to "portfolio"), "run_portfolio", ok = "Queued. The server picks it up within 5 minutes.".t()) { vm.load("mine") }
            },
            Modifier.fillMaxWidth().padding(top = 14.dp), kind = KBtn.SOFT, icon = KIcons.Play, busy = "run_portfolio" in g.busy,
        )
    }
}

/** "Chal raha hai… server 5 minute me uthata hai" (brand rang, 2 line tak). */
@Composable
private fun MineWait(modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    SText(
        "Running… the server picks it up within 5 minutes".t(), 13.sp, KKind.ASK.fg(), modifier, weight = FontWeight.SemiBold, lh = 18.sp,
        maxLines = 2, align = align,
    )
}

/** Skeleton: tabs + rows. Fold: hero + card baayein, rows daayein. Phone ka hero top me (MinePage). */
@Composable
private fun MineSkel(wide: Boolean) {
    KSkeleton {
        if (wide) KTwoPane(
            left = { KSkelHero(KTint.Green, split = 3); KSkelCard(3) },
            right = { KSkelBlock(48.dp, RoundedCornerShape(24.dp)); KSkelRows(6) },
        ) else {
            KSkelBlock(48.dp, RoundedCornerShape(24.dp))
            KSkelRows(6)
        }
    }
}
