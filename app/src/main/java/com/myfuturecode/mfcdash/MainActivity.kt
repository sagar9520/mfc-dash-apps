package com.myfuturecode.mfcdash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.Root
import com.myfuturecode.mfcdash.ui.kit.KStatusBarBand
import com.myfuturecode.mfcdash.ui.theme.MfcTheme
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val app = application as App
        applyBars(app.themeStyle.value)
        handleIntent(intent)
        setContent {
            val style by app.themeStyle.collectAsState()
            LaunchedEffect(style) { applyBars(style) }
            MfcTheme(style) {
                val vm: DashVm = viewModel(factory = DashVm.factory(app))
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), propagateMinConstraints = true) { Root(vm) }
                    // Brand: status bar ke peeche gradient (KTop se juda hua)
                    KStatusBarBand()
                }
            }
        }
    }

    /** Status/nav bar ke icons: Light = dark icons, Dark = light icons, Brand = upar light (gradient), neeche dark. */
    private fun applyBars(style: ThemeStyle) {
        val clear = android.graphics.Color.TRANSPARENT
        val navScrim = android.graphics.Color.argb(0x80, 0x1B, 0x1B, 0x1B)   // API < 26: light nav icons nahi hote
        val status = if (style == ThemeStyle.LIGHT) SystemBarStyle.light(clear, clear) else SystemBarStyle.dark(clear)
        val nav = if (style == ThemeStyle.DARK) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, navScrim)
        this@MainActivity.enableEdgeToEdge(statusBarStyle = status, navigationBarStyle = nav)
    }

    override fun onStart() { super.onStart(); (application as App).foreground.value = true }
    override fun onStop() { (application as App).foreground.value = false; super.onStop() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Notification (extra "route") ya email link (mfcdash://health/...) se khula ho to wahi screen. */
    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val route = i.getStringExtra("route")?.also { i.removeExtra("route") }
            ?: i.data?.takeIf { it.scheme == "mfcdash" && it.host == "health" }?.let { u -> healthRoute(u.pathSegments).also { i.data = null } }
            ?: i.data?.takeIf { it.scheme == "mfcdash" && it.host == "gads" }?.let { u -> gadsRoute(u.pathSegments).also { i.data = null } }
            ?: i.data?.takeIf { it.scheme == "mfcdash" && it.host == "studio" }?.let { u -> studioRoute(u.pathSegments).also { i.data = null } }
            ?: i.data?.takeIf { it.scheme == "mfcdash" && it.host == "cost" }?.let { u ->
                u.pathSegments.firstOrNull()?.takeIf { it.matches(Regex("[a-z]+")) }?.let { "cost/t/$it" } ?: "cost" }.also { i.data = null }
            ?: return
        (application as App).pendingRoute.value = route
    }

    /** Google Ads monitor email link: mfcdash://gads/alert/12, /account/1112223333, /campaign/cid/id, /<tab>. */
    private fun gadsRoute(seg: List<String>): String {
        val id = seg.getOrNull(1)
        val digits = Regex("\\d{1,30}")
        return when (seg.firstOrNull()) {
            "alert" -> if (id?.toLongOrNull() != null) "gads/alert/$id" else "gads/t/history"
            "run" -> if (id?.toLongOrNull() != null) "gads/run/$id" else "gads/t/live"
            "account" -> if (id != null && id.matches(digits)) "gads/account/$id" else "gads"
            "campaign" -> seg.getOrNull(2).let { c -> if (id != null && c != null && id.matches(digits) && c.matches(digits)) "gads/campaign/$id/$c" else "gads" }
            null, "" -> "gads"
            else -> seg[0].let { t -> if (t.matches(Regex("[a-z]+"))) "gads/t/$t" else "gads" }
        }
    }

    /** Research Studio email link: mfcdash://studio/project/3, /opp/12, /run/5, /discovery/run/7, /<tab>. */
    private fun studioRoute(seg: List<String>): String {
        val id = seg.getOrNull(1)?.toLongOrNull()
        return when (seg.firstOrNull()) {
            "project", "opp", "run" -> if (id != null) "studio/${seg[0]}/$id" else "studio"
            "discovery" -> seg.getOrNull(2)?.toLongOrNull()?.takeIf { seg.getOrNull(1) == "run" }?.let { "studio/discovery/run/$it" } ?: "studio/discovery"
            null, "" -> "studio"
            else -> seg[0].let { t -> if (t.matches(Regex("[a-z]+"))) "studio/$t" else "studio" }
        }
    }

    private fun healthRoute(seg: List<String>): String {
        val id = seg.getOrNull(1)
        return when (seg.firstOrNull()) {
            "issue" -> if (id?.toLongOrNull() != null) "health/issue/$id" else "health/t/issues"
            "run" -> if (id?.toLongOrNull() != null) "health/run/$id" else "health/t/live"
            "app" -> if (!id.isNullOrBlank() && id.matches(Regex("[A-Za-z0-9._]+"))) "health/app/$id" else "health/t/consoles"
            "aab", "builds" -> "health/t/builds"
            null, "", "overview" -> "health"
            else -> seg[0].let { t -> if (t.matches(Regex("[a-z]+"))) "health/t/$t" else "health" }
        }
    }
}
