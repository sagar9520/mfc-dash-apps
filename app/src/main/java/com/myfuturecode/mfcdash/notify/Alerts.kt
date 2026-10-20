package com.myfuturecode.mfcdash.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.MainActivity
import com.myfuturecode.mfcdash.R
import com.myfuturecode.mfcdash.data.AlertItem
import com.myfuturecode.mfcdash.data.AlertsData
import java.util.concurrent.TimeUnit

/**
 * Notifications: server (api/alerts.php) rules chalata hai, app sirf naye alerts uthakar notification dikhata hai.
 *  - App khuli ho: har refresh (20 sec) par check
 *  - App band ho: WorkManager har 15 min (Android ki minimum) — phone ki battery setting "unrestricted" rakho
 * 3 channels: Urgent (awaaz + vibration), Dhyan do (normal), Info (chup-chaap).
 */
object Notify {
    const val CH_URGENT = "alerts_urgent"
    const val CH_WARN = "alerts_warn"
    const val CH_INFO = "alerts_info"
    private val BRAND = 0xFF4A1FC4.toInt()

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_URGENT, "Urgent alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Data band, crash, campaign loss — turant dekhna"
            enableVibration(true); vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600); enableLights(true); lightColor = BRAND
        })
        nm.createNotificationChannel(NotificationChannel(CH_WARN, "Dhyan do", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Issue hai par abhi fix zaroori nahi"
        })
        nm.createNotificationChannel(NotificationChannel(CH_INFO, "Info", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Roz ka brief, records, nayi campaign"
        })
    }

    fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, a: AlertItem) {
        if (!allowed(ctx)) return
        val ch = when (a.level) { 3 -> CH_URGENT; 2 -> CH_WARN; else -> CH_INFO }
        val prefix = when (a.level) { 3 -> "🔴 URGENT · "; 2 -> "🟠 "; else -> "" }
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "alerts"); putExtra("alert_id", a.id)
        }
        val pi = PendingIntent.getActivity(ctx, (a.id % Int.MAX_VALUE).toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val big = buildString {
            append(a.body)
            if (a.ai.isNotBlank()) append("\n\nClaude: ").append(a.ai)
        }
        val b = NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setColor(BRAND)
            .setContentTitle(prefix + a.title)
            .setContentText(a.body.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(when (a.level) { 3 -> NotificationCompat.PRIORITY_MAX; 2 -> NotificationCompat.PRIORITY_DEFAULT; else -> NotificationCompat.PRIORITY_LOW })
        if (a.level == 3) b.setDefaults(NotificationCompat.DEFAULT_ALL).setCategory(NotificationCompat.CATEGORY_ALARM)
        if (a.appName.isNotBlank()) b.setSubText(a.appName)
        try { NotificationManagerCompat.from(ctx).notify((a.id % Int.MAX_VALUE).toInt(), b.build()) } catch (e: SecurityException) { }
    }
}

object AlertsSync {
    private const val PERIODIC = "mfc_alerts_periodic"
    private const val ONCE = "mfc_alerts_once"

    /** Server se alerts lao; jo naye hain (id > last) unki notification. Return = poora data (UI ke liye). */
    suspend fun check(app: App): AlertsData? {
        val prefs = app.prefs
        val data = app.api.alerts(limit = 150) ?: return null
        if (data.alerts.isEmpty()) return data
        val last = prefs.lastAlertId
        val maxId = data.alerts.maxOf { it.id }
        val fresh = if (last == 0L) data.alerts.filter { it.level == 3 && it.open }   // pehli baar: sirf abhi ke urgent, purani history nahi
                    else data.alerts.filter { it.id > last }
        fresh.sortedBy { it.id }.forEach { a ->
            val on = when (a.level) { 3 -> prefs.notifyUrgent; 2 -> prefs.notifyWarn; else -> prefs.notifyInfo }
            if (on && a.open) Notify.show(app, a)
        }
        if (maxId > last) prefs.lastAlertId = maxId
        return data
    }

    fun schedule(ctx: Context, minutes: Int) {
        val req = PeriodicWorkRequestBuilder<AlertsWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun runNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<AlertsWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
        WorkManager.getInstance(ctx).cancelUniqueWork(ONCE)
    }
}

class AlertsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? App ?: return Result.success()
        if (!app.prefs.hasLogin) return Result.success()
        return try {
            AlertsSync.check(app)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}
