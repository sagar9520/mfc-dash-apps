package com.myfuturecode.mfcdash.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myfuturecode.mfcdash.data.PageLoad
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Browser jaisi patli neeli loading line: page khulte / refresh par screen ke upar (status bar ke neeche) chalti hai,
// load poora hote hi poori hokar gaayab. Hisaab data/PageLoad me.

/** Neela: Owner ne browser jaisa neela maanga. Light: Google blue, Dark: halka neela, Brand gradient par: aasmani. */
private val LoadLight = Color(0xFF1A73E8)
private val LoadDark = Color(0xFF8AB4F8)
private val LoadOnBrand = Color(0xFF8FDBFF)

/**
 * Upar ki loading line (3dp). Root ise screen ke upar, status bar ke theek neeche rakhta hai.
 * Shuru me jaldi ~25% tak, phir dheere ~90% tak; load khatam = 100% aur fade. Animations band hon to
 * load ke dauraan sthir line.
 */
@Composable
fun KPageLoadBar(modifier: Modifier = Modifier) {
    val seq by PageLoad.seq.collectAsStateWithLifecycle()
    val prog = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val color = when {
        Mfc.style == ThemeStyle.BRAND -> LoadOnBrand
        Mfc.c.isDark -> LoadDark
        else -> LoadLight
    }
    LaunchedEffect(seq) {
        // seq == 0: abhi tak koi load nahi. busy() nahi: purana load (Activity dobara bani, line baad me judi) = mat dikhao
        if (seq == 0 || !PageLoad.busy()) {
            alpha.snapTo(0f)
            prog.snapTo(0f)
            return@LaunchedEffect
        }
        prog.snapTo(0f)
        alpha.snapTo(1f)
        val trickle = launch {
            prog.animateTo(.25f, tween(300, easing = LinearOutSlowInEasing))
            prog.animateTo(.6f, tween(1_800, easing = LinearOutSlowInEasing))
            prog.animateTo(.9f, tween(10_000, easing = LinearOutSlowInEasing))
        }
        while (PageLoad.busy()) delay(80)
        trickle.cancel()
        prog.animateTo(1f, tween(200))
        alpha.animateTo(0f, tween(280))
        prog.snapTo(0f)
    }
    Spacer(
        modifier.fillMaxWidth().height(3.dp).clearAndSetSemantics { }.drawBehind {
            val a = alpha.value
            val w = size.width * prog.value
            if (a <= 0f || w <= 0f) return@drawBehind
            val h = size.height
            drawRoundRect(color.copy(alpha = a), Offset.Zero, Size(w, h), CornerRadius(h / 2, h / 2))
            // aage ka sira thoda chamakta (browser jaisa)
            val tip = minOf(28.dp.toPx(), w)
            drawRect(
                Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = .55f * a)), w - tip, w),
                Offset(w - tip, 0f), Size(tip, h),
            )
        },
    )
}

/**
 * User ne screen ke andar filter / tab / chip badla jisse naya data aata hai: upar ki line ([PageLoad.start]). Pehli
 * baar (screen khuli) kuch nahi: wahan page change wala load pehle se chal raha hai. Keys me sirf user ke chune hue
 * filter do (search box ka text nahi: har akshar par line dobara shuru hogi).
 * `KLoadOnChange(tab, range)` screen me kahin bhi (kuch nahi banata).
 */
@Composable
fun KLoadOnChange(vararg keys: Any?) {
    val first = remember { BooleanArray(1) { true } }
    LaunchedEffect(*keys) { if (first[0]) first[0] = false else PageLoad.start() }
}
