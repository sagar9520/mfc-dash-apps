package com.myfuturecode.mfcdash

import android.app.Application
import com.myfuturecode.mfcdash.data.Api
import com.myfuturecode.mfcdash.data.Prefs
import com.myfuturecode.mfcdash.notify.AlertsSync
import com.myfuturecode.mfcdash.notify.Notify
import kotlinx.coroutines.flow.MutableStateFlow

class App : Application() {
    lateinit var prefs: Prefs
    lateinit var api: Api
    /** Notification tap -> kaunsi screen kholni hai ("alerts"). Root isse padh kar navigate karta hai. */
    val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = Api(prefs)
        Notify.channels(this)
        // Login hai to background check chalu rakho (app update ke baad bhi)
        if (prefs.hasLogin) AlertsSync.schedule(this, prefs.checkMinutes)
    }
}
