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

    /** Notification se khula ho to Alerts screen. */
    private fun handleIntent(i: Intent?) {
        val route = i?.getStringExtra("route") ?: return
        (application as App).pendingRoute.value = route
        i.removeExtra("route")
    }
}
