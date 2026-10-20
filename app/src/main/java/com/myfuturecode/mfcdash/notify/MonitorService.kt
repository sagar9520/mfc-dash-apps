package com.myfuturecode.mfcdash.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.MainActivity
import com.myfuturecode.mfcdash.R
import com.myfuturecode.mfcdash.data.AlertsData
import com.myfuturecode.mfcdash.util.Lang
import com.myfuturecode.mfcdash.util.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 24x7 live monitor: a foreground service that asks the server (api/alerts.php) every minute and shows a notification
 * the moment a new alert appears (which app, which account, which ad unit, what happened, loss estimate, fix).
 * WorkManager alone can only run every 15 min; this is the "instant" path. A small fixed notification stays visible
 * (Android requires it). Server side the rules recompute on every poll if older than AL_RECOMPUTE_SEC, or every
 * minute via cron.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as? App
        if (app == null || !app.prefs.hasLogin || !app.prefs.liveMonitor) { stopSelf(); return START_NOT_STICKY }
        startFg(status("Monitoring…".t()))
        if (loop == null) loop = scope.launch { run(app) }
        return START_STICKY
    }

    private fun startFg(n: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                   else if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try { ServiceCompat.startForeground(this, FG_ID, n, type) } catch (e: Exception) { stopSelf() }
    }

    private suspend fun run(app: App) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        while (scope.isActive) {
            if (!app.prefs.hasLogin || !app.prefs.liveMonitor) { stopSelf(); return }
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mfcdash:monitor").apply { setReferenceCounted(false) }
            try {
                wl.acquire(50_000L)
                val data = runCatching { AlertsSync.check(app) }.getOrNull()
                lastOk = if (data != null) System.currentTimeMillis() else lastOk
                nm().notify(FG_ID, status(summary(data)))
            } catch (e: Exception) {
            } finally { if (wl.isHeld) wl.release() }
            delay(INTERVAL_MS)
        }
    }

    private fun summary(d: AlertsData?): String {
        val t = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        if (d == null) return "Server not reachable".t() + " · " + t
        return when {
            d.urgentOpen > 0 -> "🔴 " + d.urgentOpen + " " + "urgent".t() + (if (d.warnOpen > 0) " · " + d.warnOpen + " " + "watch".t() else "") + " · " + t
            d.warnOpen > 0 -> "🟠 " + d.warnOpen + " " + "watch".t() + " · " + t
            else -> "✅ " + "All good".t() + " · " + "checked".t() + " " + t
        }
    }

    private fun status(text: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "alerts")
        }
        val pi = PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle("MFC live monitor".t())
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun nm() = getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        loop?.cancel(); loop = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CH_MONITOR = "monitor"
        private const val FG_ID = 4242
        private const val INTERVAL_MS = 60_000L
        @Volatile var lastOk = 0L

        fun channel(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CH_MONITOR, "Live monitor", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Fixed status of the 24x7 monitor (silent)"
                setShowBadge(false)
            })
        }

        /** Start/stop according to prefs (login + toggle). Safe to call any time. */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext as? App ?: return
            if (app.prefs.hasLogin && app.prefs.liveMonitor) start(ctx) else stop(ctx)
        }

        fun start(ctx: Context) {
            try { ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java)) } catch (e: Exception) { }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, MonitorService::class.java)) } catch (e: Exception) { }
        }

        /** Doze stops network for a minute-by-minute monitor; ask once to exclude the app from battery optimisation. */
        fun batteryExempt(ctx: Context): Boolean {
            if (Build.VERSION.SDK_INT < 23) return true
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(ctx.packageName)
        }

        fun requestBatteryExempt(ctx: Context) {
            if (Build.VERSION.SDK_INT < 23 || batteryExempt(ctx)) return
            try {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) { }
            }
        }
    }
}

/** After reboot / app update: bring the monitor back if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED || a == "android.intent.action.QUICKBOOT_POWERON") {
            val app = ctx.applicationContext as? App ?: return
            Lang.code = app.prefs.lang
            if (app.prefs.hasLogin) AlertsSync.schedule(ctx, app.prefs.checkMinutes)
            MonitorService.sync(ctx)
        }
    }
}
