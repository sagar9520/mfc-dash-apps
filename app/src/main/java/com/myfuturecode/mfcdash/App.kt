package com.myfuturecode.mfcdash

import android.app.Application
import com.myfuturecode.mfcdash.data.Api
import com.myfuturecode.mfcdash.data.Prefs

class App : Application() {
    lateinit var prefs: Prefs
    lateinit var api: Api
    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = Api(prefs)
    }
}
