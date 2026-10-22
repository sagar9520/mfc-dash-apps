package com.myfuturecode.mfcdash.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.myfuturecode.mfcdash.BuildConfig

/** Login aur settings phone mein encrypted rehti hain (sirf isi app ko milti hain). */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = runCatching {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "mfc_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { ctx.getSharedPreferences("mfc_plain", Context.MODE_PRIVATE) }

    var baseUrl: String
        get() = sp.getString("base_url", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_BASE_URL
        set(v) { sp.edit().putString("base_url", normalizeUrl(v)).apply() }
    var username: String
        get() = sp.getString("user", "") ?: ""
        set(v) { sp.edit().putString("user", v).apply() }
    var password: String
        get() = sp.getString("pass", "") ?: ""
        set(v) { sp.edit().putString("pass", v).apply() }
    var cookies: String
        get() = sp.getString("cookies", "") ?: ""
        set(v) { sp.edit().putString("cookies", v).apply() }
    var range: String
        get() = sp.getString("range", "today") ?: "today"
        set(v) { sp.edit().putString("range", v).apply() }
    var roasTarget: Double
        get() = sp.getFloat("roas_target", 1.5f).toDouble()
        set(v) { sp.edit().putFloat("roas_target", v.toFloat()).apply() }

    var lastAlertId: Long
        get() = sp.getLong("last_alert_id", 0L)
        set(v) { sp.edit().putLong("last_alert_id", v).apply() }
    var notifyUrgent: Boolean
        get() = sp.getBoolean("n_urgent", true)
        set(v) { sp.edit().putBoolean("n_urgent", v).apply() }
    var notifyWarn: Boolean
        get() = sp.getBoolean("n_warn", true)
        set(v) { sp.edit().putBoolean("n_warn", v).apply() }
    var notifyInfo: Boolean
        get() = sp.getBoolean("n_info", true)
        set(v) { sp.edit().putBoolean("n_info", v).apply() }
    var lang: String
        get() = sp.getString("lang", "en") ?: "en"
        set(v) { sp.edit().putString("lang", v).apply() }
    /** App ka roop: "light" / "dark" / "brand" (ThemeStyle.key). */
    var theme: String
        get() = sp.getString("theme", "light") ?: "light"
        set(v) { sp.edit().putString("theme", v).apply() }
    /** 24x7 foreground monitor (every minute). Default on: the user asked for instant alerts. */
    var liveMonitor: Boolean
        get() = sp.getBoolean("live_monitor", true)
        set(v) { sp.edit().putBoolean("live_monitor", v).apply() }
    var checkMinutes: Int
        get() = sp.getInt("check_min", 15)
        set(v) { sp.edit().putInt("check_min", v).apply() }

    val hasLogin get() = username.isNotBlank() && password.isNotBlank()
    fun clearLogin() { sp.edit().remove("user").remove("pass").remove("cookies").remove("last_alert_id").apply() }

    companion object {
        fun normalizeUrl(u: String): String {
            var s = u.trim()
            if (s.isEmpty()) return s
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            if (!s.endsWith("/")) s += "/"
            return s
        }
    }
}
