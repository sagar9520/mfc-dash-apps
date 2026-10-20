package com.myfuturecode.mfcdash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfuturecode.mfcdash.ui.DashVm
import com.myfuturecode.mfcdash.ui.Root
import com.myfuturecode.mfcdash.ui.theme.MfcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        handleIntent(intent)
        setContent {
            MfcTheme {
                val vm: DashVm = viewModel(factory = DashVm.factory(app))
                Root(vm)
            }
        }
    }

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
