package com.myfuturecode.mfcdash.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.mfcColors
import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf

/*
 * Sort (har list page, mockup ui_sort frame 2/3): app bar me Filter ke baad 48dp button (teer + 3 lakeeren).
 * Tap = chhota menu: kis cheez se (ek row har option) + kaise (Zyada -> kam / Kam -> zyada). Wahi cheez dobara = ulta.
 * Sort page ke default se alag ho to button brand rang me, aur list ke upar chhota pill (tap = wahi menu, pill ke neeche).
 */

/** Sort option ka type: menu ke direction shabd aur pehli baar chunne par direction ([firstDesc]). */
enum class KSortKind(val firstDesc: Boolean) {
    /** Number: pehle zyada -> kam (default). */
    NUM(true),
    /** Number jahan kam pehle chahiye (CPI, cost / conv): pehle kam -> zyada. */
    NUM_LOW(false),
    /** Naam: A -> Z pehle. Key "name" apne aap TEXT hai. */
    TEXT(false),
    /** Tareekh / time: naya -> purana pehle. */
    DATE(true),
    /** Tareekh / time: purana -> naya pehle. */
    DATE_OLD(false),
}

private val KSortKind.isDate get() = this == KSortKind.DATE || this == KSortKind.DATE_OLD

/** Option ka type: kinds me ho to wo, warna "name" = TEXT, baaki NUM. */
fun kSortKindOf(key: String, kinds: Map<String, KSortKind> = emptyMap()): KSortKind =
    kinds[key] ?: if (key == "name") KSortKind.TEXT else KSortKind.NUM

/** Menu ke direction button ka shabd: "Zyada → kam" / "A → Z" / "Naya → purana" (translated). */
fun kSortDirText(desc: Boolean, kind: KSortKind = KSortKind.NUM): String = when {
    kind == KSortKind.TEXT -> if (desc) "Z → A" else "A → Z"
    kind.isDate -> if (desc) "New → old" else "Old → new"
    else -> if (desc) "High → low" else "Low → high"
}.t()

/** TalkBack: "Sort: Munafa, zyada se kam" (label already translated). */
fun kSortSays(label: String, desc: Boolean, kind: KSortKind = KSortKind.NUM): String = tf(
    when {
        kind == KSortKind.TEXT -> if (desc) "Sort: %s, Z to A" else "Sort: %s, A to Z"
        kind.isDate -> if (desc) "Sort: %s, newest first" else "Sort: %s, oldest first"
        else -> if (desc) "Sort: %s, high to low" else "Sort: %s, low to high"
    },
    label,
)

/**
 * Ek page ka sort: key + direction (desc = bada / naya / Z pehle). [rememberKSort] se banao (rotation / wapas aane par yaad).
 * isDefault = user ne sort nahi chheda (button saada, pill chhupa).
 */
@Stable
class KSortState(val defaultKey: String, val defaultDesc: Boolean = true) {
    var key by mutableStateOf(defaultKey)
    var desc by mutableStateOf(defaultDesc)
    val isDefault: Boolean get() = key == defaultKey && desc == defaultDesc
    fun set(key: String, desc: Boolean) {
        this.key = key
        this.desc = desc
    }
    fun reset() = set(defaultKey, defaultDesc)

    companion object {
        val Saver: Saver<KSortState, Any> = listSaver(
            save = { listOf(it.defaultKey, it.defaultDesc, it.key, it.desc) },
            restore = { KSortState(it[0] as String, it[1] as Boolean).apply { set(it[2] as String, it[3] as Boolean) } },
        )
    }
}

/** Page ka sort state (rememberSaveable). default = aaj ka order, desc = uski direction. */
@Composable
fun rememberKSort(default: String, desc: Boolean = true): KSortState =
    rememberSaveable(saver = KSortState.Saver) { KSortState(default, desc) }

/**
 * List sort: desc = bada pehle. null / NaN hamesha aakhir me (dono direction me); [last] wale bhi aakhir me
 * (jaise "bina data" apps). Barabar wale apni purani jagah (stable).
 */
fun <T, R : Comparable<R>> List<T>.kSorted(desc: Boolean, last: (T) -> Boolean = { false }, by: (T) -> R?): List<T> {
    if (size < 2) return toList()
    // har item ki key ek hi baar (by / last mehenge ho sakte hain: JSON, tareekh parse; har recomposition par chalta hai)
    val keys = map { x -> by(x)?.takeUnless { (it is Double && it.isNaN()) || (it is Float && it.isNaN()) } }
    val lasts = map(last)
    return indices.sortedWith { i, j ->
        val la = lasts[i]
        val lb = lasts[j]
        if (la != lb) return@sortedWith if (la) 1 else -1
        val va = keys[i]
        val vb = keys[j]
        when {
            va == null && vb == null -> 0
            va == null -> 1
            vb == null -> -1
            desc -> vb.compareTo(va)
            else -> va.compareTo(vb)
        }
    }.map { this[it] }
}

// ---------------- button ----------------

/**
 * App bar ka sort button (48dp, Filter icon ke baad). Icon = SortDesc / SortAsc. Default se alag (ya menu khula):
 * brand-soft gol background + brand rang (Brand theme band par: halka safed + safed). Tap = menu iske neeche.
 * options = (key, translated label); onChange(key, desc); default = (key, desc) jo page aaj karta hai.
 */
@Composable
fun KSortButton(
    options: List<Pair<String, String>>,
    key: String,
    desc: Boolean,
    onChange: (key: String, desc: Boolean) -> Unit,
    default: Pair<String, Boolean>,
    kinds: Map<String, KSortKind> = emptyMap(),
) {
    val c = Mfc.c
    var open by remember { mutableStateOf(false) }
    val kind = kSortKindOf(key, kinds)
    val on = open || key != default.first || desc != default.second
    val bg = when {
        !on -> Color.Transparent
        c.onBrand -> c.pNBg
        else -> c.brandSoft
    }
    Box {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(bg)
                .clickable(role = Role.Button, onClickLabel = "Change sort".t()) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (desc) KIcons.SortDesc else KIcons.SortAsc, kSortSays(kSortLabel(options, key), desc, kind),
                tint = if (on) c.brand else c.ink, modifier = Modifier.size(24.dp),
            )
        }
        KSortMenu(open, { open = false }, options, key, desc, onChange, default, kinds)
    }
}

/** [KSortButton] ek [KSortState] ke saath (page ke liye sabse aasaan). */
@Composable
fun KSortButton(sort: KSortState, options: List<Pair<String, String>>, kinds: Map<String, KSortKind> = emptyMap()) =
    KSortButton(options, sort.key, sort.desc, { k, d -> sort.set(k, d) }, sort.defaultKey to sort.defaultDesc, kinds)

// ---------------- pill ----------------

/**
 * Chhota brand-soft pill: teer + field ka naam (mockup frame 3). FilterSummary row ke shuru me (ya list ke upar akela),
 * sirf jab sort default se alag ho (ye overload hamesha dikhta hai: dikhana caller ka kaam). 48dp touch.
 */
@Composable
fun KSortPill(label: String, desc: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, kind: KSortKind = KSortKind.NUM) {
    val c = Mfc.c
    val bg = if (c.onBrand) c.pNBg else c.brandSoft
    val says = kSortSays(label, desc, kind)
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = "Change sort".t(), onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).background(bg).padding(start = 10.dp, end = 12.dp)
                .clearAndSetSemantics { contentDescription = says },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(if (desc) KIcons.Down else KIcons.Up, null, tint = c.brand, modifier = Modifier.size(12.dp))
            KT(label, 14.sp, c.brand, weight = FontWeight.SemiBold)
        }
    }
}

/**
 * [KSortPill] ek [KSortState] ke saath: default par kuch nahi dikhata; tap = wahi sort menu (pill ke neeche).
 * Use: `FilterSummary(box, lead = kSortLead(sort, opts))` ya list ke upar `KSortPill(sort, opts)`.
 */
@Composable
fun KSortPill(sort: KSortState, options: List<Pair<String, String>>, kinds: Map<String, KSortKind> = emptyMap(), modifier: Modifier = Modifier) {
    if (sort.isDefault) return
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        KSortPill(kSortLabel(options, sort.key), sort.desc, { open = true }, kind = kSortKindOf(sort.key, kinds))
        KSortMenu(open, { open = false }, options, sort.key, sort.desc, { k, d -> sort.set(k, d) }, sort.defaultKey to sort.defaultDesc, kinds)
    }
}

/** FilterSummary ke `lead` ke liye: sort default par null (row nahi banti), warna pill. */
fun kSortLead(sort: KSortState, options: List<Pair<String, String>>, kinds: Map<String, KSortKind> = emptyMap()): (@Composable () -> Unit)? =
    if (sort.isDefault) null else ({ KSortPill(sort, options, kinds) })

// ---------------- menu ----------------

private fun kSortLabel(options: List<Pair<String, String>>, key: String): String = options.firstOrNull { it.first == key }?.second ?: key

/**
 * Sort menu card (236dp, 18dp kone): "Sort" chhota label, har option ek 44dp row (chuna = brand + brand-soft + check),
 * phir do 44dp buttons (desc upar, asc neeche; chuna = bhara brand). Option tap = uski apni direction (page default ki
 * apni, number = zyada -> kam, naam = A -> Z); chuna hua option dobara tap = ulta. Kuch bhi chunne par band.
 */
@Composable
private fun KSortMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    options: List<Pair<String, String>>,
    key: String,
    desc: Boolean,
    onChange: (key: String, desc: Boolean) -> Unit,
    default: Pair<String, Boolean>,
    kinds: Map<String, KSortKind>,
) {
    // band (Brand theme ka gradient) ke andar se khulta hai: card page wale tokens se
    val pc = mfcColors(Mfc.style)
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = pc.card,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = if (pc.isDark) BorderStroke(1.dp, pc.cardBd) else null,
    ) {
        KPageTokens {
            val c = Mfc.c
            Column(Modifier.width(236.dp).padding(bottom = 2.dp)) {
                KT(
                    "Sort".t(), 13.sp, c.ink3, Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 6.dp).semantics { heading() },
                    weight = FontWeight.SemiBold, lh = 18.sp,
                )
                options.forEach { (k, label) ->
                    val sel = k == key
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).background(if (sel) c.brandSoft else Color.Transparent)
                            .selectable(selected = sel, role = Role.RadioButton) {
                                val d = when {
                                    sel -> !desc
                                    k == default.first -> default.second
                                    else -> kSortKindOf(k, kinds).firstDesc
                                }
                                onChange(k, d)
                                onDismiss()
                            }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KT(label, 16.sp, if (sel) c.brand else c.ink, Modifier.weight(1f), weight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        if (sel) Icon(KIcons.Check, null, tint = c.brand, modifier = Modifier.size(20.dp))
                    }
                }
                val kind = kSortKindOf(key, kinds)
                val label = kSortLabel(options, key)
                listOf(true, false).forEach { d ->
                    val on = desc == d
                    val fg = if (on) c.btnFg else c.ink2
                    Box(
                        Modifier.padding(start = 12.dp, end = 12.dp, top = if (d) 10.dp else 6.dp).fillMaxWidth().heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp)).background(if (on) c.btnBg else c.well)
                            .selectable(selected = on, role = Role.RadioButton) {
                                onChange(key, d)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            Modifier.clearAndSetSemantics { contentDescription = kSortSays(label, d, kind) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(if (d) KIcons.Down else KIcons.Up, null, tint = fg, modifier = Modifier.size(12.dp))
                            KT(kSortDirText(d, kind), 14.sp, fg, weight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- mini pill ----------------

/**
 * Chhoti 20dp pill (icon + shabd), jaise app row me value ke neeche "141 crash" (laal shabd, laal-soft bg).
 * `KRow(..., valueBottom = { KMiniPill(tf("%s crash", n), KKind.BAD, icon = KIcons.Bug) })`.
 */
@Composable
fun KMiniPill(text: String, kind: KKind = KKind.BAD, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = Mfc.c
    val (bg, fg) = when (kind) {
        KKind.OK -> c.pOkBg to c.pOkFg
        KKind.WARN -> c.pWarnBg to c.pWarnFg
        KKind.BAD -> c.pBadBg to c.pBadFg
        KKind.ASK -> c.pAskBg to c.pAskFg
        KKind.NEUTRAL -> c.pNBg to c.pNFg
    }
    Row(
        modifier.heightIn(min = 20.dp).clip(RoundedCornerShape(10.dp)).background(bg)
            .padding(start = if (icon != null) 5.dp else 7.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
        KT(text, 13.sp, fg, weight = FontWeight.SemiBold, lh = 16.sp)
    }
}
