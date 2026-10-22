package com.myfuturecode.mfcdash.widget

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.util.Lang
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Widgets ka data: lage widgets ke sabse chhote interval (15/30/60/180 min) par WorkManager,
 * refresh button / naya widget par ek baar turant. Ek jaise options wale widgets ek hi baar laaye jaate hain.
 */
object WidgetSync {
    private const val PERIODIC = "mfc_widgets_periodic"
    private const val ONCE = "mfc_widgets_once"
    internal const val KEY_FORCE = "force"
    private const val RETRY_MS = 3 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val watching = AtomicBoolean(false)

    /** Periodic kaam: koi widget nahi to band; interval badla to naya. */
    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        val wm = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return
        val min = WidgetStore.minEvery(app)
        if (min == null) {
            wm.cancelUniqueWork(PERIODIC)
            WidgetStore.setScheduled(app, 0)
            return
        }
        val every = min.coerceAtLeast(15)
        val changed = WidgetStore.scheduled(app) != every
        val req = PeriodicWorkRequestBuilder<WidgetWorker>(every.toLong(), TimeUnit.MINUTES).build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            if (changed) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
        if (changed) WidgetStore.setScheduled(app, every)
    }

    /** Ek baar abhi. force = sab naye sire se (refresh button); warna sirf purana data. */
    fun runNow(ctx: Context, force: Boolean) {
        val app = ctx.applicationContext
        val wm = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return
        val req = OneTimeWorkRequestBuilder<WidgetWorker>()
            .setInputData(Data.Builder().putBoolean(KEY_FORCE, force).build())
            .build()
        wm.enqueueUniqueWork(ONCE, if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }

    /** Widget bana / dobara bana: data purana ho to ek baar laao (3 min me ek hi koshish, loop nahi). */
    internal fun kick(ctx: Context, kind: WKind, wid: Int) {
        val app = ctx.applicationContext
        schedule(app)
        if ((app as? App)?.prefs?.hasLogin != true) return
        val o = WidgetStore.opts(app, kind, wid)
        val key = o.dataKey(kind)
        if (WidgetStore.age(app, key) <= o.every * 60_000L) return
        if (System.currentTimeMillis() - WidgetStore.lastTry(app, key) < RETRY_MS) return
        WidgetStore.markTry(app, key)
        runNow(app, force = false)
    }

    /** Sab lage widgets ka data laao, cache karo, phir sab widget dobara banao. */
    internal suspend fun syncAll(ctx: Context, force: Boolean) {
        val app = ctx.applicationContext as? App ?: return
        try {
            if (app.prefs.hasLogin) fetchAll(app, force)
        } catch (e: CancellationException) {
            throw e // naya kaam (REPLACE) "…" aur redraw khud sambhalega
        } catch (e: Exception) {
            // ek galti se baaki widgets na rukein
        }
        WidgetStore.busy = false
        WidgetStore.bump()
        updateAllKinds(app)
    }

    private suspend fun fetchAll(app: App, force: Boolean) {
        // dataKey -> (kind, options); ek key ke kai widgets ho to sabse chhota interval
        val jobs = LinkedHashMap<String, Pair<WKind, WOpts>>()
        for (k in WKind.entries) {
            for (id in WidgetStore.ids(app, k)) {
                val o = WidgetStore.opts(app, k, id)
                val key = o.dataKey(k)
                val prev = jobs[key]
                if (prev == null || o.every < prev.second.every) jobs[key] = k to o
            }
        }
        for ((key, job) in jobs) {
            val (k, o) = job
            // periodic thoda pehle bhi chal sakta hai: 2 min ki chhoot
            if (!force && WidgetStore.age(app, key) < o.every * 60_000L - 2 * 60_000L) continue
            WidgetStore.markTry(app, key)
            try {
                WidgetStore.saveSnap(app, key, WidgetData.fetch(app, k, o))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WidgetStore.markFail(app, key) // purana data rehta hai, band par "kab ka" dikhega
            }
        }
    }

    /** Jis kind ke widget lage hain, unhe dobara banao. */
    internal suspend fun updateAllKinds(ctx: Context) {
        val app = ctx.applicationContext
        for (k in WKind.entries) {
            if (WidgetStore.ids(app, k).isEmpty()) continue
            try {
                k.widget().updateAll(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // launcher ne widget hata diya ho to bhi chalne do
            }
        }
    }

    /**
     * Theme / bhasha / login badle to lage widgets turant naye roop me (data wahi).
     * App / Settings isse bula sakte hain; na bulayein to agle update par badlega.
     */
    fun redraw(ctx: Context) {
        val app = ctx.applicationContext
        WidgetStore.bump()
        scope.launch { runCatching { updateAllKinds(app) } }
    }

    /**
     * Settings me theme (Light / Dark / Brand) ya bhasha badle to "App jaisa" wale widgets bhi turant badlein.
     * Process me ek hi baar; Settings / Widgets screen / widget khud ise chalu karte hain.
     */
    internal fun watchApp(ctx: Context) {
        val app = ctx.applicationContext as? App ?: return
        if (!watching.compareAndSet(false, true)) return
        scope.launch {
            runCatching {
                combine(app.themeStyle, snapshotFlow { Lang.code }) { s, l -> s.key + "|" + l }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { if (WKind.entries.any { k -> WidgetStore.ids(app, k).isNotEmpty() }) redraw(app) }
            }
            watching.set(false)
        }
    }
}

/** WorkManager ka kaam: widgets ka data. */
class WidgetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        WidgetSync.syncAll(applicationContext, inputData.getBoolean(WidgetSync.KEY_FORCE, false))
        return Result.success()
    }
}
