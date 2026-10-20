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
        val route = i?.getStringExtra("route")?.also { i.removeExtra("route") }
            ?: i?.data?.takeIf { it.scheme == "mfcdash" && it.host == "health" }?.let { u -> healthRoute(u.pathSegments).also { i.data = null } }
            ?: return
        (application as App).pendingRoute.value = route
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
