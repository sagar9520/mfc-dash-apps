package com.myfuturecode.mfcdash.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import com.myfuturecode.mfcdash.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Widgets ki apni chhoti store (SharedPreferences "mfc_widgets"):
 *  def_<kind>  naye widget ke default options (Widgets screen se)
 *  opt_<id>    har lage widget (appWidgetId) ke options
 *  snap_<key>  aakhri sahi data (offline me bhi yahi dikhta hai), try_/fail_ = aakhri koshish
 * rev badalne par chalu widgets dobara bante hain.
 */
internal object WidgetStore {
    private const val FILE = "mfc_widgets"
    private const val DAY = 24 * 60 * 60 * 1000L

    private val _rev = MutableStateFlow(0L)
    val rev: StateFlow<Long> = _rev

    @Volatile private var busyAt: Long = 0L

    /** Refresh chal raha hai (band par "…"). 2 minute se zyada nahi (kaam atak jaye to bhi samay wapas dikhe). */
    var busy: Boolean
        get() = busyAt > 0L && System.currentTimeMillis() - busyAt < 2 * 60_000L
        set(v) { busyAt = if (v) System.currentTimeMillis() else 0L }

    private fun sp(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun bump() { _rev.value = _rev.value + 1 }

    // ---- lage hue widgets

    fun ids(ctx: Context, kind: WKind): IntArray =
        runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, kind.receiver)) }.getOrNull() ?: IntArray(0)

    // ---- options

    fun defaults(ctx: Context, kind: WKind): WOpts = WOpts.parse(kind, sp(ctx).getString("def_" + kind.key, null))

    fun opts(ctx: Context, kind: WKind, wid: Int): WOpts {
        val s = sp(ctx).getString("opt_$wid", null) ?: return defaults(ctx, kind)
        return WOpts.parse(kind, s)
    }

    /** Naya widget: abhi ke defaults uske apne options ban jaate hain. */
    fun ensureOpts(ctx: Context, kind: WKind, wid: Int) {
        val p = sp(ctx)
        if (!p.contains("opt_$wid")) p.edit().putString("opt_$wid", defaults(ctx, kind).toJson()).apply()
    }

    /** Widgets screen ka Save: naye widgets ke default + pehle se lage sab widgets par. */
    fun applyToAll(ctx: Context, kind: WKind, o: WOpts) {
        val e = sp(ctx).edit().putString("def_" + kind.key, o.toJson())
        ids(ctx, kind).forEach { e.putString("opt_$it", o.toJson()) }
        e.apply()
    }

    fun dropOpts(ctx: Context, wid: Int) { sp(ctx).edit().remove("opt_$wid").apply() }

    /** Sabse chhota update interval (minute) sab lage widgets me; koi nahi to null. */
    fun minEvery(ctx: Context): Int? =
        WKind.entries.flatMap { k -> ids(ctx, k).map { opts(ctx, k, it).every } }.minOrNull()

    // ---- data cache

    fun snap(ctx: Context, key: String): JSONObject? =
        sp(ctx).getString("snap_$key", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun saveSnap(ctx: Context, key: String, j: JSONObject) {
        sp(ctx).edit().putString("snap_$key", j.toString()).putBoolean("fail_$key", false).apply()
    }

    fun lastTry(ctx: Context, key: String): Long = sp(ctx).getLong("try_$key", 0L)
    fun markTry(ctx: Context, key: String) { sp(ctx).edit().putLong("try_$key", System.currentTimeMillis()).apply() }
    fun markFail(ctx: Context, key: String) { sp(ctx).edit().putBoolean("fail_$key", true).apply() }
    fun failed(ctx: Context, key: String): Boolean = sp(ctx).getBoolean("fail_$key", false)

    /** Snap kitna purana (ms); na ho to Long.MAX_VALUE. */
    fun age(ctx: Context, key: String): Long {
        val at = snap(ctx, key)?.optLong("at", 0L) ?: 0L
        return if (at <= 0L) Long.MAX_VALUE else System.currentTimeMillis() - at
    }

    // ---- ₹ per $ (Google Ads ke kul ke liye)

    fun rate(ctx: Context, fresh: Boolean): Double? {
        val p = sp(ctx)
        val r = p.getFloat("rate", 0f).toDouble()
        if (r <= 0.0) return null
        if (fresh && System.currentTimeMillis() - p.getLong("rate_at", 0L) > DAY) return null
        return r
    }

    fun saveRate(ctx: Context, r: Double) {
        sp(ctx).edit().putFloat("rate", r.toFloat()).putLong("rate_at", System.currentTimeMillis()).apply()
    }

    // ---- periodic kaam ka interval (badle tabhi dobara schedule)

    fun scheduled(ctx: Context): Int = sp(ctx).getInt("sched", 0)
    fun setScheduled(ctx: Context, min: Int) { sp(ctx).edit().putInt("sched", min).apply() }

    // ---- widget ka poora roop

    /** Ek lage widget ka dikhne wala roop (login, options, data, theme). */
    fun view(ctx: Context, kind: WKind, wid: Int): WView {
        val app = ctx.applicationContext as? App
        val o = opts(ctx, kind, wid)
        val loggedIn = app?.prefs?.hasLogin == true
        val appTheme = app?.prefs?.theme ?: "light"
        val target = app?.prefs?.roasTarget ?: 1.5
        val key = o.dataKey(kind)
        return wView(kind, o, snap(ctx, key), loggedIn, appTheme, target, busy, failed(ctx, key))
    }
}
