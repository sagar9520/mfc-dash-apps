package com.myfuturecode.mfcdash.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Home screen par kitne MFC widget lage hain, aur app se naya lagana (Android 8+, launcher maane to). */
object WidgetPins {

    /** Sab MFC widgets ki ginti (Settings ki "%s lage" line). */
    suspend fun placedCount(ctx: Context): Int = withContext(Dispatchers.IO) {
        WidgetSync.watchApp(ctx)
        WKind.entries.sumOf { WidgetStore.ids(ctx, it).size }
    }

    /** Ek widget kitni baar laga hai. */
    fun count(ctx: Context, kind: WKind): Int = WidgetStore.ids(ctx, kind).size

    /** Launcher app se widget lagane deta hai? */
    fun canPin(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching { AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported }.getOrDefault(false)
    }

    /** Launcher ka "Home screen par lagao?" dialog. false = nahi ho saka (hint dikhao). */
    fun pin(ctx: Context, kind: WKind): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            val m = AppWidgetManager.getInstance(ctx)
            m.isRequestPinAppWidgetSupported && m.requestPinAppWidget(ComponentName(ctx, kind.receiver), null, null)
        }.getOrDefault(false)
    }
}
