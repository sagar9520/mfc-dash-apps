package com.myfuturecode.mfcdash.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.myfuturecode.mfcdash.R
import com.myfuturecode.mfcdash.ui.theme.BrandStops
import com.myfuturecode.mfcdash.ui.theme.LocalMfc
import com.myfuturecode.mfcdash.ui.theme.Mfc
import com.myfuturecode.mfcdash.ui.theme.MfcAngleGradient
import com.myfuturecode.mfcdash.ui.theme.OnBrandTop
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import com.myfuturecode.mfcdash.ui.theme.mfcColors
import com.myfuturecode.mfcdash.util.t

// ---------------- context ----------------

/** Fold 7 khula (>= 600dp): do column. */
@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/** KTop ke andar? (KHero isse jaanta hai: brand me transparent ya gradient card). */
internal val LocalKInTop = staticCompositionLocalOf { false }

/** Card/hero/module ke andar? (seg tabs ka track page rang). */
internal val LocalKInCard = staticCompositionLocalOf { false }

/** Split ke upar ki line: green hero me hero-line, baaki jagah line. */
internal val LocalKLine = staticCompositionLocalOf { Color.Unspecified }

/** Screen ki side padding: phone 16, Fold 24. */
@Composable
fun kSide(): Dp = if (isWide()) 24.dp else 16.dp

/** Brand band (status bar ke neeche wala hissa) ki height px me: status bar strip ka gradient isse milta hai. */
internal object KTopBand {
    var height by mutableIntStateOf(0)
}

private val TopShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

// .t-brand .top: radial glow (120% 70% at 12% 0%) + 165deg gradient
private fun DrawScope.kBrandFill(extraTop: Float, extraBottom: Float) {
    drawRect(MfcAngleGradient(165f, BrandStops, extraTop = extraTop, extraBottom = extraBottom))
    val totalH = size.height + extraTop + extraBottom
    val rx = size.width * 1.2f
    val ry = totalH * .7f
    if (rx <= 0f || ry <= 0f) return
    val center = Offset(size.width * .12f, -extraTop)
    scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
        drawCircle(
            Brush.radialGradient(0f to Color.White.copy(alpha = .14f), .6f to Color.Transparent, center = center, radius = rx),
            radius = rx, center = center,
        )
    }
}

/** Brand gradient card ka background (hero bahar content me, Fold column). */
internal fun Modifier.kBrandCard(): Modifier = drawBehind { kBrandFill(0f, 0f) }

/**
 * Screen ka upar wala hissa (app bar + hero). Light/dark: sada. Brand: gradient band, neeche 28dp gol kone,
 * andar safed tokens (OnBrandTop).
 */
@Composable
fun KTop(content: @Composable ColumnScope.() -> Unit) {
    if (Mfc.style == ThemeStyle.BRAND) {
        val sb = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
        Column(
            Modifier.fillMaxWidth()
                .onSizeChanged { KTopBand.height = it.height }
                .clip(TopShape)
                .drawBehind { kBrandFill(sb, 0f) }
                .padding(bottom = 4.dp),
        ) {
            CompositionLocalProvider(LocalKInTop provides true, LocalKInCard provides false) {
                OnBrandTop { content() }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalKInTop provides true, LocalKInCard provides false) { content() }
        }
    }
}

/**
 * Brand theme: status bar ke peeche gradient patti (KTop ke gradient se judi hui). Baaki themes me kuch nahi.
 * MainActivity ise Root ke upar rakhta hai.
 */
@Composable
fun KStatusBarBand(modifier: Modifier = Modifier) {
    if (Mfc.style != ThemeStyle.BRAND) return
    Box(
        modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .drawBehind { kBrandFill(0f, KTopBand.height.toFloat()) },
    )
}

/** Popup/dialog jo band ke andar se khule: wapas page wale tokens. */
@Composable
fun KPageTokens(content: @Composable () -> Unit) {
    val c = mfcColors(Mfc.style)
    CompositionLocalProvider(LocalMfc provides c, LocalContentColor provides c.ink, LocalKInTop provides false, content = content)
}

// ---------------- screens ----------------

/** Ek scroll: top (KTop me) + content (16dp side, 12dp gap), neeche nav bar + 24dp. Status bar Root sambhalta hai. */
@Composable
fun KScreen(
    top: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Mfc.c
    val side = kSide()
    Column(modifier.fillMaxSize().background(c.page).verticalScroll(scroll)) {
        KTop(top)
        Column(
            Modifier.fillMaxWidth().padding(start = side, end = side, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        Spacer(Modifier.height(24.dp))
    }
}

/** KScreen jaisa, par LazyColumn. Items kItem / kItems / kCardItems se (khud side padding + 12dp gap). */
@Composable
fun KLazyScreen(
    top: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val c = Mfc.c
    LazyColumn(modifier.fillMaxSize().background(c.page), state = state) {
        item(key = "k.top", contentType = "k.top") {
            Column {
                KTop(top)
                Spacer(Modifier.height(12.dp))
            }
        }
        content()
        item(key = "k.end", contentType = "k.end") {
            Column {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Lazy item: side padding + neeche 12dp. */
fun LazyListScope.kItem(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key) {
        Column(Modifier.fillMaxWidth().padding(start = kSide(), end = kSide(), bottom = 12.dp)) { content() }
    }
}

/** Har item alag (apna card khud banaye). */
fun <T> LazyListScope.kItems(items: List<T>, key: ((T) -> Any)? = null, content: @Composable (T) -> Unit) {
    items(items, key = key) { item ->
        Column(Modifier.fillMaxWidth().padding(start = kSide(), end = kSide(), bottom = 12.dp)) { content(item) }
    }
}

/** Lambi list ek hi card me (rows ke beech line). lead = rows me avatar/tile hai (line 68dp se). Har row KRow ho. */
fun <T> LazyListScope.kCardItems(items: List<T>, key: ((T) -> Any)? = null, lead: Boolean = false, content: @Composable (T) -> Unit) {
    val n = items.size
    val k: ((Int, T) -> Any)? = key?.let { f -> { _: Int, x: T -> f(x) } }
    itemsIndexed(items, key = k) { i, item ->
        val c = Mfc.c
        val first = i == 0
        val last = i == n - 1
        val shape = RoundedCornerShape(
            topStart = if (first) 22.dp else 0.dp, topEnd = if (first) 22.dp else 0.dp,
            bottomStart = if (last) 22.dp else 0.dp, bottomEnd = if (last) 22.dp else 0.dp,
        )
        Column(Modifier.fillMaxWidth().padding(start = kSide(), end = kSide(), bottom = if (last) 12.dp else 0.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(shape).background(c.card)
                    .padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp),
            ) {
                CompositionLocalProvider(LocalKInCard provides true, LocalKLine provides c.line) {
                    if (!first) KListDivider(lead)
                    content(item)
                }
            }
        }
    }
}

/** Fold: do barabar column, 16dp gap. */
@Composable
fun KTwoPane(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = left)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = right)
    }
}

// ---------------- app bars ----------------

/** 48dp gol icon button (back, bell, search, refresh...). spinning = refresh chal raha. */
@Composable
fun KBarIcon(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    badge: Int = 0,
    badgeUrgent: Boolean = true,
    spinning: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Mfc.c
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val angle = if (spinning) kSpin() else 0f
        Icon(icon, desc, tint = c.ink, modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = angle })
        if (badge > 0) KBadge(badge, Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 4.dp), urgent = badgeUrgent)
    }
}

@Composable
private fun kSpin(): Float {
    val tr = rememberInfiniteTransition(label = "kSpin")
    val a by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "kSpinA")
    return a
}

/** Home app bar: logo + MFC + "Namaste ..." + bell (badge). middle = Fold par beech me "Aaj ka haal" + pill. */
@Composable
fun KHomeBar(
    title: String,
    sub: String?,
    unread: Int,
    urgent: Boolean,
    onBell: () -> Unit,
    modifier: Modifier = Modifier,
    middle: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Mfc.c
    val logoShape = RoundedCornerShape(24)
    var logo = Modifier.size(40.dp)
    logo = when {
        c.onBrand -> logo.shadow(4.dp, logoShape).border(1.5.dp, Color.White.copy(alpha = .7f), logoShape)
        c.isDark -> logo.border(1.dp, Color.White.copy(alpha = .12f), logoShape)
        else -> logo
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(R.drawable.mfc_mark), contentDescription = "MFC", modifier = logo.clip(logoShape))
        Column(Modifier.weight(1f)) {
            KT(title, 18.sp, c.ink, weight = FontWeight.Bold, lh = 21.sp, ls = .01.em)
            if (!sub.isNullOrBlank()) KT(sub, 13.sp, c.ink3, lh = 17.sp)
        }
        middle?.invoke(this)
        KBarIcon(KIcons.Bell, "Alerts".t(), onBell, badge = unread, badgeUrgent = urgent)
    }
}

/** Detail app bar: back + (avatar) + title 20sp + sub. */
@Composable
fun KDetailBar(
    title: String,
    onBack: () -> Unit,
    sub: String? = null,
    lead: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KBarIcon(KIcons.Back, "Back".t(), onBack)
        if (lead != null) {
            lead()
            Spacer(Modifier.size(4.dp))
        }
        Column(Modifier.weight(1f).padding(start = if (lead == null) 4.dp else 0.dp)) {
            KT(title, 20.sp, c.ink, weight = FontWeight.Bold, lh = 26.sp, ls = (-0.01).em)
            if (!sub.isNullOrBlank()) KT(sub, 13.sp, c.ink3, lh = 17.sp)
        }
        actions()
    }
}

/** Tab screen app bar (Apps / Alerts / Settings): bada title 22sp, back nahi. */
@Composable
fun KTabBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Mfc.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KT(title, 22.sp, c.ink, Modifier.weight(1f), weight = FontWeight.Bold, lh = 28.sp, ls = (-0.01).em)
        actions()
    }
}

// ---------------- sections ----------------

/** Section heading 18sp bold + daayein pill/link. */
@Composable
fun KSection(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val c = Mfc.c
    Row(
        modifier.fillMaxWidth().heightIn(min = 30.dp).padding(start = 4.dp, end = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KT(title, 18.sp, c.ink, Modifier.weight(1f), weight = FontWeight.Bold, lh = 24.sp, ls = (-0.01).em)
        trailing?.invoke()
    }
}

/** "View more >" link (brand, 48dp). */
@Composable
fun KSectionLink(text: String, onClick: () -> Unit) {
    val c = Mfc.c
    Row(
        Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClick = onClick).padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        KT(text, 15.sp, c.brand, weight = FontWeight.SemiBold)
        Icon(KIcons.Chev, null, tint = c.brand, modifier = Modifier.size(20.dp))
    }
}

// ---------------- bottom nav ----------------

data class KNavItem(val route: String, val label: String, val icon: ImageVector, val badge: Int = 0)

/** Neeche ka nav: active = brand pill, 12sp label, laal badge. Nav bar inset khud leta hai. */
@Composable
fun KBottomNav(items: List<KNavItem>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Mfc.c
    Column(modifier.fillMaxWidth().background(c.navBg)) {
        KHLine(c.line)
        Row(Modifier.fillMaxWidth().height(62.dp)) {
            items.forEach { item ->
                val on = item.route == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().selectable(selected = on, role = Role.Tab, onClick = { onSelect(item.route) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Box(Modifier.size(56.dp, 28.dp), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)).background(c.brandSoft))
                        Icon(item.icon, null, tint = if (on) c.brand else c.ink3, modifier = Modifier.size(22.dp))
                        if (item.badge > 0) KBadge(
                            item.badge, Modifier.align(Alignment.TopStart).offset(x = 27.dp, y = (-5).dp), ring = c.navBg,
                        )
                    }
                    KT(item.label, 12.sp, if (on) c.brand else c.ink3, weight = if (on) FontWeight.Bold else FontWeight.Medium, lh = 16.sp)
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
