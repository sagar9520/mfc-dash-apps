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
