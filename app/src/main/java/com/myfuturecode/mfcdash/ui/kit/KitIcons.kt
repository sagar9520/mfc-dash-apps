package com.myfuturecode.mfcdash.ui.kit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Kit ke icons. Status shapes (Ok/Dhyan/Turant/Wait + Dia/Tri/Up/Down) mockup ki tarah;
 * baaki material-icons-extended ke line icons.
 */
object KIcons {
    // ---- status (pill / tile / marks)
    val Ok: ImageVector = Icons.Filled.CheckCircle
    val Dhyan: ImageVector by lazy { dhyan() }
    val Turant: ImageVector by lazy { turant() }
    val Wait: ImageVector = Icons.Filled.WatchLater
    // ---- plain shapes (row sub line, duo strip)
    val Dia: ImageVector by lazy { simple("k.dia") { moveTo(10f, .8f); lineTo(19.2f, 10f); lineTo(10f, 19.2f); lineTo(.8f, 10f); close() } }
    val Tri: ImageVector by lazy { triangle() }
    val Up: ImageVector by lazy { simple("k.up") { moveTo(10f, 2f); lineTo(19f, 17f); lineTo(1f, 17f); close() } }
    val Down: ImageVector by lazy { simple("k.down") { moveTo(10f, 18f); lineTo(19f, 3f); lineTo(1f, 3f); close() } }

    // ---- line icons
    val Chev: ImageVector = Icons.Rounded.ChevronRight
    val ChevDown: ImageVector = Icons.Rounded.KeyboardArrowDown
    val Pulse: ImageVector = Icons.Outlined.MonitorHeart
    val Ads: ImageVector = Icons.Outlined.Campaign
    val Bulb: ImageVector = Icons.Outlined.Lightbulb
    val Bell: ImageVector = Icons.Outlined.Notifications
    val Home: ImageVector = Icons.Outlined.Home
    val Apps: ImageVector = Icons.Outlined.GridView
    val Gear: ImageVector = Icons.Outlined.Settings
    val Search: ImageVector = Icons.Rounded.Search
    val Filter: ImageVector = Icons.Rounded.Tune
    val Refresh: ImageVector = Icons.Rounded.Refresh
    val More: ImageVector = Icons.Rounded.MoreVert
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val Spark: ImageVector = Icons.Outlined.AutoAwesome
    val Globe: ImageVector = Icons.Outlined.Language
    val Target: ImageVector = Icons.Rounded.TrackChanges
    val Widgets: ImageVector = Icons.Outlined.Widgets
    val Shield: ImageVector = Icons.Outlined.VerifiedUser
    val Logout: ImageVector = Icons.AutoMirrored.Rounded.Logout
    val Users: ImageVector = Icons.Outlined.Group
    val User: ImageVector = Icons.Outlined.Person
    val Download: ImageVector = Icons.Rounded.Download
    val Bug: ImageVector = Icons.Outlined.BugReport
    val Rupee: ImageVector = Icons.Rounded.CurrencyRupee
    val Live: ImageVector = Icons.Rounded.Bolt
    val Calendar: ImageVector = Icons.Outlined.CalendarMonth
    val Clock: ImageVector = Icons.Outlined.Schedule
    val Fix: ImageVector = Icons.Outlined.Build
    val Wand: ImageVector = Icons.Outlined.AutoFixHigh
    val Open: ImageVector = Icons.AutoMirrored.Rounded.OpenInNew
    val DoneAll: ImageVector = Icons.Rounded.DoneAll
    val Check: ImageVector = Icons.Rounded.Check
    val Close: ImageVector = Icons.Rounded.Close
    val Plus: ImageVector = Icons.Rounded.Add
    val Moon: ImageVector = Icons.Outlined.DarkMode
    val Palette: ImageVector = Icons.Outlined.Palette
    val Key: ImageVector = Icons.Outlined.Key
    val Lock: ImageVector = Icons.Outlined.Lock
    val Pause: ImageVector = Icons.Rounded.Pause
    val Play: ImageVector = Icons.Rounded.PlayArrow
    val Phone: ImageVector = Icons.Outlined.PhoneAndroid
    val Wallet: ImageVector = Icons.Outlined.AccountBalanceWallet
    val Card: ImageVector = Icons.Outlined.CreditCard
    val Chart: ImageVector = Icons.Outlined.BarChart
    val TrendUp: ImageVector = Icons.AutoMirrored.Rounded.TrendingUp
    val TrendDown: ImageVector = Icons.AutoMirrored.Rounded.TrendingDown
    val History: ImageVector = Icons.Rounded.History
    val Sort: ImageVector = Icons.AutoMirrored.Rounded.Sort
    // sort button: teer + 3 lakeeren (mockup ui_sort .i-sortd / .i-sortu). Desc = zyada -> kam, Asc = kam -> zyada
    val SortDesc: ImageVector by lazy { sortIcon(true) }
    val SortAsc: ImageVector by lazy { sortIcon(false) }
    // Play Store jaisa trikon (outline, mockup .i-playst)
    val PlayStore: ImageVector by lazy { playStore() }
    val Send: ImageVector = Icons.AutoMirrored.Rounded.Send
    val Info: ImageVector = Icons.Outlined.Info
    val Copy: ImageVector = Icons.Outlined.ContentCopy
    val Edit: ImageVector = Icons.Outlined.Edit
    val Delete: ImageVector = Icons.Outlined.Delete
}

// 20x20 viewport, mockup (kit.css @icons) ke path
private inline fun simple(name: String, crossinline d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 20f, viewportHeight = 20f)
        .path(fill = SolidColor(Color.Black)) { d() }
        .build()

// 24x24 line icon (stroke 2, gol sire), mockup ke SVG path
private inline fun line24(
    name: String, width: Float = 2f, crossinline d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector.Builder =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { d() }

// desc: M7 4v16 M3.5 16.5 7 20l3.5-3.5 M13 6h8 M13 12h6 M13 18h3 (asc: teer upar, lakeeren chhoti -> lambi)
private fun sortIcon(desc: Boolean): ImageVector =
    line24(if (desc) "k.sortDesc" else "k.sortAsc") {
        if (desc) {
            moveTo(7f, 4f); lineTo(7f, 20f)
            moveTo(3.5f, 16.5f); lineTo(7f, 20f); lineTo(10.5f, 16.5f)
            moveTo(13f, 6f); lineTo(21f, 6f)
            moveTo(13f, 12f); lineTo(19f, 12f)
            moveTo(13f, 18f); lineTo(16f, 18f)
        } else {
            moveTo(7f, 20f); lineTo(7f, 4f)
            moveTo(3.5f, 7.5f); lineTo(7f, 4f); lineTo(10.5f, 7.5f)
            moveTo(13f, 6f); lineTo(16f, 6f)
            moveTo(13f, 12f); lineTo(19f, 12f)
            moveTo(13f, 18f); lineTo(21f, 18f)
        }
    }.build()

// M5 3.6v16.8c0 .8.9 1.3 1.6.9l14.2-8.4c.7-.4.7-1.4 0-1.8L6.6 2.7C5.9 2.3 5 2.8 5 3.6Z + andar M5.4 3 15 12l-9.6 9 (1.6)
private fun playStore(): ImageVector =
    line24("k.playStore") {
        moveTo(5f, 3.6f); lineTo(5f, 20.4f)
        curveToRelative(0f, .8f, .9f, 1.3f, 1.6f, .9f)
        lineToRelative(14.2f, -8.4f)
        curveToRelative(.7f, -.4f, .7f, -1.4f, 0f, -1.8f)
        lineTo(6.6f, 2.7f)
        curveTo(5.9f, 2.3f, 5f, 2.8f, 5f, 3.6f)
        close()
    }.path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5.4f, 3f); lineTo(15f, 12f); lineToRelative(-9.6f, 9f)
    }.build()

private fun triangle(): ImageVector =
    ImageVector.Builder(name = "k.tri", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 20f, viewportHeight = 20f)
        .path(fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black), strokeLineWidth = 1.2f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(10f, 1.2f); lineTo(19.4f, 18f); lineTo(.6f, 18f); close()
        }
        .build()

// Turant: diamond, andar "!" kata hua
private fun turant(): ImageVector =
    ImageVector.Builder(name = "k.turant", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 20f, viewportHeight = 20f)
        .path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(10f, .8f); lineTo(19.2f, 10f); lineTo(10f, 19.2f); lineTo(.8f, 10f); close()
            // "!" ki dandi (gol sire)
            moveTo(8.9f, 5.5f)
            arcTo(1.1f, 1.1f, 0f, false, true, 11.1f, 5.5f)
            lineTo(11.1f, 10.7f)
            arcTo(1.1f, 1.1f, 0f, false, true, 8.9f, 10.7f)
            close()
            // bindu
            moveTo(11.3f, 14f)
            arcTo(1.3f, 1.3f, 0f, false, true, 8.7f, 14f)
            arcTo(1.3f, 1.3f, 0f, false, true, 11.3f, 14f)
            close()
        }
        .build()

// Dhyan do: triangle (gol kone), andar "!" kata hua
private fun dhyan(): ImageVector =
    ImageVector.Builder(name = "k.dhyan", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 20f, viewportHeight = 20f)
        // kone gol karne ke liye sirf bahar ki line
        .path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.2f, strokeLineJoin = StrokeJoin.Round) {
            moveTo(10f, 1.2f); lineTo(19.4f, 18f); lineTo(.6f, 18f); close()
        }
        .path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(10f, 1.2f); lineTo(19.4f, 18f); lineTo(.6f, 18f); close()
            moveTo(8.9f, 7.2f)
            arcTo(1.1f, 1.1f, 0f, false, true, 11.1f, 7.2f)
            lineTo(11.1f, 12.2f)
            arcTo(1.1f, 1.1f, 0f, false, true, 8.9f, 12.2f)
            close()
            moveTo(11.2f, 15.2f)
            arcTo(1.2f, 1.2f, 0f, false, true, 8.8f, 15.2f)
            arcTo(1.2f, 1.2f, 0f, false, true, 11.2f, 15.2f)
            close()
        }
        .build()
