package com.myfuturecode.mfcdash.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent

/**
 * Chaaron widget ek hi tarah bante hain: har lage widget (appWidgetId) ke apne options, data cache se.
 * rev badle (naya data / Save / theme) to chalu widget turant dobara banta hai.
 */
abstract class MfcWidget(private val kind: WKind) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val wid = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(0)
        WidgetStore.ensureOpts(context, kind, wid)
        WidgetSync.kick(context, kind, wid)
        WidgetSync.watchApp(context)
        provideContent {
            val rev by WidgetStore.rev.collectAsState()
            val v = remember(rev) { WidgetStore.view(context, kind, wid) }
            WGlanceRoot(v)
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull()
            ?.let { WidgetStore.dropOpts(context, it) }
        WidgetSync.schedule(context)
    }
}

class PaisaWidget : MfcWidget(WKind.PAISA)
class AdsWidget : MfcWidget(WKind.ADS)
class HealthWidget : MfcWidget(WKind.HEALTH)
class AlertsWidget : MfcWidget(WKind.ALERTS)

class PaisaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PaisaWidget()
}

class AdsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AdsWidget()
}

class HealthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HealthWidget()
}

class AlertsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlertsWidget()
}

internal val WKind.receiver: Class<out GlanceAppWidgetReceiver>
    get() = when (this) {
        WKind.PAISA -> PaisaWidgetReceiver::class.java
        WKind.ADS -> AdsWidgetReceiver::class.java
        WKind.HEALTH -> HealthWidgetReceiver::class.java
        WKind.ALERTS -> AlertsWidgetReceiver::class.java
    }

internal fun WKind.widget(): GlanceAppWidget = when (this) {
    WKind.PAISA -> PaisaWidget()
    WKind.ADS -> AdsWidget()
    WKind.HEALTH -> HealthWidget()
    WKind.ALERTS -> AlertsWidget()
}

/** Band ka refresh: turant "…", phir sab widgets ka data naye sire se. */
class WidgetRefresh : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetStore.busy = true
        WidgetStore.bump()
        val wid = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(glanceId) }.getOrNull()
        val kind = if (wid == null) null else WKind.entries.firstOrNull { k -> wid in WidgetStore.ids(context, k) }
        if (kind != null) runCatching { kind.widget().update(context, glanceId) }
        WidgetSync.runNow(context, force = true)
    }
}
