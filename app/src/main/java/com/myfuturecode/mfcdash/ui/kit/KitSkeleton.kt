package com.myfuturecode.mfcdash.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.MfcColors
import com.myfuturecode.mfcdash.util.t
import kotlin.math.max

// Skeleton (ad / feed load hone se pehle wale grey dibbe): screen ka asli dhaancha halke blocks me,
// upar se ek halki chamak baayein se daayein chalti hai. Spinner / khaali "–" / "Loading…" ki jagah.

// ---------------- chamak (shimmer) ----------------

/** Ek chakkar (ms): chamak screen ke baayein se daayein. */
private const val SHIM_MS = 1200

/** Patti ka jhukav ~20°: chalne ki disha (unit vector). */
private const val SHIM_DX = .94f
private const val SHIM_DY = .34f

/** Ek skeleton ke saare blocks ki saajhi ghadi. p = null: system ne animation band ki hai (sthir blocks). */
private class KShimClock(val p: State<Float>?)

private val LocalKShim = staticCompositionLocalOf<KShimClock?> { null }

/** Block ki jagah root (screen) me: patti poori screen par ek saath chalti hai, har block alag nahi. */
private class KShimPos {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
}

/**
 * System ka "Remove animations" (animator duration scale 0) = chamak nahi. Compose ise composition ke
 * coroutine context me MotionDurationScale ke roop me deta hai (setting badle to ye bhi badalta hai).
 */
@Composable
internal fun kMotionOn(): Boolean {
    val scope = rememberCoroutineScope()
    return (scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
}

/** Upar se mili ghadi, warna apni (infinite transition). */
@Composable
private fun kShimClock(): KShimClock {
    val up = LocalKShim.current
    if (up != null) return up
    if (!kMotionOn()) return remember { KShimClock(null) }
    val tr = rememberInfiniteTransition(label = "kShim")
    val p = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(SHIM_MS, easing = LinearEasing)), label = "kShimP")
    return remember(p) { KShimClock(p) }
}

/** Skeleton ka base: ink ka halka parda (card, page, tinted hero sab par chalta hai). Brand band me safed parda. */
internal fun kSkelBase(c: MfcColors): Color = c.ink.copy(alpha = if (c.onBrand) .20f else if (c.isDark) .10f else .085f)

/** Chamak ka rang: light me safed, dark me halki safed, brand band me thodi zyada. */
internal fun kSkelGlow(c: MfcColors): Color = Color.White.copy(alpha = if (c.onBrand) .26f else if (c.isDark) .08f else .75f)

/**
 * Halki tirchhi chamak jo har ~1.2 sec baayein se daayein chalti hai (content ke upar). Clip / background ke baad lagao:
 * `Modifier.clip(shape).background(x).shimmer()`. Sab blocks ek hi patti dikhate hain (screen ke hisaab se).
 * System me animations band hon to kuch nahi karta.
 */
fun Modifier.shimmer(): Modifier = composed {
    val p = kShimClock().p ?: return@composed Modifier
    val glow = kSkelGlow(Mfc.c)
    val pos = remember { KShimPos() }
    Modifier
        .onGloballyPositioned { lc ->
            val o = lc.positionInRoot()
            val root = lc.findRootCoordinates().size
            pos.x = o.x
            pos.y = o.y
            pos.w = root.width.toFloat()
            pos.h = root.height.toFloat()
        }
        .drawWithContent {
            drawContent()
            if (pos.w <= 0f) return@drawWithContent
            val band = max(pos.w * .45f, 140.dp.toPx())
            val span = pos.w * SHIM_DX + pos.h * SHIM_DY
            // patti ka shuru screen par, phir is block ke apne kone se
            val s = -band + (span + band) * p.value - (pos.x * SHIM_DX + pos.y * SHIM_DY)
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent, .5f to glow, 1f to Color.Transparent,
                    start = Offset(s * SHIM_DX, s * SHIM_DY), end = Offset((s + band) * SHIM_DX, (s + band) * SHIM_DY),
                ),
            )
        }
}

/** Sabse bahar wala skeleton: ghadi + screen reader ke liye "Loading…". Andar wale bas ghadi le lete hain. */
@Composable
private fun KSkelRoot(content: @Composable (Modifier) -> Unit) {
    if (LocalKShim.current != null) {
        content(Modifier)
        return
    }
    val desc = "Loading…".t()
    CompositionLocalProvider(LocalKShim provides kShimClock()) {
        content(Modifier.clearAndSetSemantics { contentDescription = desc })
    }
}

/**
 * Poori screen ka skeleton gher: andar ke sab blocks ek ghadi (ek hi chamak), screen reader ek baar "Loading…".
 * Column hai, 12dp gap (KScreen content jaisa).
 */
@Composable
fun KSkeleton(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) = KSkelRoot { sem ->
    Column(modifier.fillMaxWidth().then(sem), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

// ---------------- building blocks ----------------

@Composable
private fun KSkelBox(modifier: Modifier, shape: Shape) {
    Box(modifier.clip(shape).background(kSkelBase(Mfc.c)).shimmer())
}

/** Chhoti line gol (pill), badi (bada number) 8dp kone. */
private fun lineShape(h: Dp) = RoundedCornerShape(minOf(h / 2, 8.dp))

/** Text ki jagah ek line (width dp me). */
@Composable
fun KSkelLine(width: Dp, height: Dp = 12.dp, modifier: Modifier = Modifier) =
    KSkelBox(modifier.width(width).height(height), lineShape(height))

/** Line, jagah ke hisaab se (fraction = 0..1 chaudai). */
@Composable
fun KSkelLine(fraction: Float, height: Dp = 12.dp, modifier: Modifier = Modifier) =
    KSkelBox(modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height), lineShape(height))

/** Poori chaudai ka dibba (chart, tabs, card). Default kone asli card jaise (22dp). */
@Composable
fun KSkelBlock(height: Dp, shape: Shape = RoundedCornerShape(22.dp), modifier: Modifier = Modifier) =
    KSkelBox(modifier.fillMaxWidth().height(height), shape)

/** Gol (avatar / icon ki jagah). */
@Composable
fun KSkelCircle(size: Dp, modifier: Modifier = Modifier) = KSkelBox(modifier.size(size), CircleShape)

// alag alag lambai (sab line ek jaisi na lagein)
private val SkelW = listOf(.62f, .46f, .70f, .54f, .66f, .40f)
private val SkelL = listOf(1f, .92f, .84f, .96f, .88f)
private val SkelH = listOf(.55f, .72f, .40f, .82f, .63f, .90f, .70f)

// ---------------- ready-made (kit components jaise) ----------------

/** KHero jaisa: label + pill, bada number, split ke numbers (2, ya 4 = paisa hero), meter. Wahi card / brand band / kone. */
@Composable
fun KSkelHero(tint: KTint = KTint.Green, modifier: Modifier = Modifier, split: Int = 2) = KSkelRoot { sem ->
    KHero(tint, modifier.then(sem)) {
        val c = Mfc.c
        val top = LocalKLine.current.takeOrElse { c.line }
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            KSkelLine(120.dp, 14.dp)
            Spacer(Modifier.weight(1f))
            KSkelLine(76.dp, 24.dp)
        }
        // bada number (48sp)
        Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(.56f, 36.dp) }
        // split: numbers, beech me line (4 khane = KSplit jaisa 10dp gap, chhoti lines)
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            KHLine(top)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                val four = split >= 4
                val gap = if (four) 10.dp else 18.dp
                repeat(split) { i ->
                    Column(Modifier.weight(1f).kLeftLine(c.line, i > 0).padding(start = if (i > 0) gap else 0.dp, end = 4.dp)) {
                        if (four) KSkelLine(if (i % 2 == 0) .66f else .84f, 11.dp) else KSkelLine(if (i == 0) 64.dp else 80.dp, 11.dp)
                        KGap(if (four) 7.dp else 9.dp)
                        if (four) KSkelLine(if (i % 2 == 0) .92f else .74f, 16.dp) else KSkelLine(if (i == 0) 96.dp else 84.dp, 18.dp)
                    }
                }
            }
        }
        // meter: label + % + pill, patti, do tick labels
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            KHLine(c.line)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                KSkelLine(52.dp, 13.dp)
                KWGap(10.dp)
                KSkelLine(64.dp, 22.dp)
                Spacer(Modifier.weight(1f))
                KSkelLine(88.dp, 24.dp)
            }
            KSkelLine(1f, 8.dp, Modifier.padding(top = 14.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                KSkelLine(90.dp, 10.dp)
                Spacer(Modifier.weight(1f))
                KSkelLine(70.dp, 10.dp)
            }
        }
    }
}

/** KModule jaisa: icon + naam + pill, bada number + caption, neeche do naam (status ke saath). */
@Composable
fun KSkelModule(tint: KTint = KTint.Lav, modifier: Modifier = Modifier) = KSkelRoot { sem ->
    KModule(tint, null, modifier.then(sem)) {
        val c = Mfc.c
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            KSkelCircle(18.dp)
            KWGap(8.dp)
            KSkelLine(104.dp, 13.dp)
            Spacer(Modifier.weight(1f))
            KSkelLine(72.dp, 24.dp)
        }
        Box(Modifier.padding(top = 4.dp).height(40.dp), contentAlignment = Alignment.CenterStart) { KSkelLine(84.dp, 28.dp) }
        KSkelLine(160.dp, 11.dp, Modifier.padding(top = 4.dp))
        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            KHLine(c.line)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp)) {
                repeat(2) { i ->
                    Column(Modifier.weight(1f).kLeftLine(c.line, i > 0).padding(start = if (i > 0) 16.dp else 0.dp, end = 4.dp)) {
                        KSkelLine(if (i == 0) 108.dp else 92.dp, 13.dp)
                        KGap(8.dp)
                        KSkelLine(if (i == 0) 84.dp else 100.dp, 11.dp)
                    }
                }
            }
        }
    }
}

/** List card (KRow jaisi rows): gol avatar + 2 lines + daayein value. lead = false: bina avatar. value = false: bina value. */
@Composable
fun KSkelRows(n: Int = 4, modifier: Modifier = Modifier, lead: Boolean = true, value: Boolean = true) = KSkelRoot { sem ->
    KCard(modifier.then(sem), list = true) {
        repeat(n.coerceAtLeast(1)) { i ->
            if (i > 0) KListDivider(lead)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (lead) KSkelCircle(40.dp)
                Column(Modifier.weight(1f)) {
                    KSkelLine(SkelW[i % SkelW.size], 13.dp)
                    KGap(9.dp)
                    KSkelLine(SkelW[(i + 3) % SkelW.size] * .7f, 11.dp)
                }
                if (value) Column(horizontalAlignment = Alignment.End) {
                    KSkelLine(if (i % 2 == 0) 64.dp else 52.dp, 14.dp)
                    KGap(9.dp)
                    KSkelLine(40.dp, 10.dp)
                }
            }
        }
    }
}

/** Sada card: title line + [lines] text lines (table / text card ki jagah). */
@Composable
fun KSkelCard(lines: Int = 3, modifier: Modifier = Modifier, title: Boolean = true) = KSkelRoot { sem ->
    KCard(modifier.then(sem)) {
        if (title) {
            KSkelLine(.42f, 15.dp)
            KGap(16.dp)
        }
        repeat(lines.coerceAtLeast(1)) { i ->
            if (i > 0) KGap(12.dp)
            KSkelLine(if (i == lines - 1 && lines > 1) .58f else SkelL[i % SkelL.size], 12.dp)
        }
    }
}

/** KSection jaisi heading (page par, card ke bahar). */
@Composable
fun KSkelSection(modifier: Modifier = Modifier, width: Dp = 140.dp) = KSkelRoot { sem ->
    Row(
        modifier.fillMaxWidth().heightIn(min = 30.dp).then(sem).padding(start = 4.dp, end = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { KSkelLine(width, 16.dp) }
}

/** Numbers ka card (TileGrid / StatCells jaisa): har cell label + number. */
@Composable
fun KSkelStats(n: Int = 4, cols: Int = 2, modifier: Modifier = Modifier) = KSkelRoot { sem ->
    KCard(modifier.then(sem)) {
        val k = cols.coerceAtLeast(1)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            (0 until n.coerceAtLeast(1)).chunked(k).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { i ->
                        Column(Modifier.weight(1f)) {
                            KSkelLine(SkelW[i % SkelW.size] * .9f, 11.dp)
                            KGap(8.dp)
                            KSkelLine(SkelW[(i + 2) % SkelW.size], 17.dp)
                        }
                    }
                    repeat(k - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Bars ka card (KWeekBars jaisa): n khade bars + neeche chhote labels. */
@Composable
fun KSkelBars(n: Int = 7, height: Dp = 120.dp, modifier: Modifier = Modifier) = KSkelRoot { sem ->
    KCard(modifier.then(sem)) {
        val gap = if (n > 10) 3.dp else 10.dp
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(height), horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(n.coerceAtLeast(1)) { i ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    KSkelBox(
                        Modifier.widthIn(max = 30.dp).fillMaxWidth().fillMaxHeight(SkelH[i % SkelH.size]),
                        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp),
                    )
                }
            }
        }
        if (n <= 10) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(n.coerceAtLeast(1)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { KSkelLine(22.dp, 9.dp) }
            }
        }
    }
}
