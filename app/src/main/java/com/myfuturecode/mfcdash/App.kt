package com.myfuturecode.mfcdash

import android.app.Application
import com.myfuturecode.mfcdash.data.Api
import com.myfuturecode.mfcdash.data.JsonCache
import com.myfuturecode.mfcdash.data.Prefs
import com.myfuturecode.mfcdash.notify.AlertsSync
import com.myfuturecode.mfcdash.notify.MonitorService
import com.myfuturecode.mfcdash.notify.Notify
import com.myfuturecode.mfcdash.ui.theme.ThemeStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var prefs: Prefs
    lateinit var api: Api
    /** Notification tap -> kaunsi screen kholni hai ("alerts"). Root isse padh kar navigate karta hai. */
    val pendingRoute = MutableStateFlow<String?>(null)
    /** App screen par dikh rahi hai (MainActivity onStart..onStop). Peeche ho to auto refresh band: server par bekaar load nahi. */
    val foreground = MutableStateFlow(false)
    /** App ka roop (Light / Dark / Brand). Settings se badalta hai, MainActivity isse theme lagata hai. */
    val themeStyle = MutableStateFlow(ThemeStyle.LIGHT)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        com.myfuturecode.mfcdash.util.Lang.code = prefs.lang
        themeStyle.value = ThemeStyle.of(prefs.theme)
        // disk cache (data/JsonCache): screens pichhla sahi data turant dikhati hain. Likhna sirf app dikh rahi ho tab.
        api = Api(prefs, JsonCache(this)).also { a -> a.saveCache = { foreground.value } }
        // channels yahin (main): system dobara shuru kare to MonitorService turant apni notification inhi par dikhata hai
        Notify.channels(this)
        MonitorService.channel(this)
        // Login hai to background check chalu rakho (app update ke baad bhi) + 24x7 monitor. Background thread par (pehli frame nahi rukti).
        boot.launch { if (prefs.hasLogin) { AlertsSync.schedule(this@App, prefs.checkMinutes); MonitorService.sync(this@App) } }
    }

    /** App bhar ka background kaam (startup ka bacha hua). */
    private val boot = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Theme badlo: prefs me save + poori app turant naye rang me. */
    fun setTheme(s: ThemeStyle) {
        prefs.theme = s.key
        themeStyle.value = s
    }
}
