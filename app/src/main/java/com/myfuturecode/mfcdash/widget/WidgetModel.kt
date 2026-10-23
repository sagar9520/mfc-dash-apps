package com.myfuturecode.mfcdash.widget

import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Chaar home-screen widget, har ek ka layout fix (mockup 2026-10-22): Paisa (4x2), Ads + Live (4x2),
 * App Health (2x2), Alerts (2x2). ADS ka key / receiver wahi rakha, taaki pehle se lage widget chalte rahein.
 */
enum class WKind(val key: String, val route: String) {
    PAISA("paisa", "money"),
    ADS("ads", "gads"),
    HEALTH("health", "health"),
    ALERTS("alerts", "alerts");

    /** Widget ka naam (Widgets screen). */
    fun title(): String = when (this) {
        PAISA -> "Money".t()
        ADS -> "Ads + Live".t()
        HEALTH -> "App Health".t()
        ALERTS -> "Alerts".t()
    }

    /** Chhota naam (Widgets screen ka "Paisa widget set karo"). */
    fun shortTitle(): String = title()

    /** 4x2 (chauda) widget? baaki 2x2. */
    val wide: Boolean get() = this == PAISA || this == ADS

    /** Apps / accounts / consoles chun sakte hain? (Alerts me nahi) */
    val picks: Boolean get() = this != ALERTS

    companion object {
        fun of(key: String?): WKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Ek widget ke options. pick = chune apps (Paisa) / accounts (Ads + Live) / consoles (Health); khaali = sab.
 * by = Ads + Live ka baayan aadha: "app" (kul kharcha + har app ka kharcha / ROAS) ya "account" (accounts + budget %).
 * Purane JSON ka "items" (pehle kya dikhana hai ki list) ab nahi chahiye: padhte waqt chhod dete hain; "by" nahi = "app".
 */
data class WOpts(
    val range: String = "today",      // Paisa: today | yesterday | 7d | month
    val pick: List<String> = emptyList(),
    val theme: String = "app",        // app | light | dark | brand
    val every: Int = 60,              // minute: 15 | 30 | 60 | 180
    val by: String = "app",           // Ads + Live: app | account
) {
    /** Ads + Live ka baayan aadha apps wala (accounts wala nahi)? */
    val byApp: Boolean get() = by != "account"

    /** pick (apps / accounts / consoles) is widget me kaam ka hai? Ads + Live "By app" me accounts ka chunav nahi lagta. */
    fun usesPick(kind: WKind): Boolean = kind.picks && !(kind == WKind.ADS && byApp)

    /**
     * Ek jaise options wale widgets ek hi data share karte hain. "2|" = naye layout ka snap (purana format alag rahe).
     * Ads + Live: "By account" ki key pehle jaisi (purana snap chalta rahe), "By app" ki alag ("|app" pe khatam).
     */
    fun dataKey(kind: WKind): String =
        kind.key + "2|" + (if (kind == WKind.PAISA) range else "") + "|" + (if (usesPick(kind)) pick.sorted().joinToString(",") else "") +
            (if (kind == WKind.ADS && byApp) "|app" else "")

    fun toJson(): String = JSONObject()
        .put("range", range)
        .put("pick", JSONArray(pick))
        .put("theme", theme)
        .put("every", every)
        .put("by", by)
        .toString()

    companion object {
        val RANGES = listOf("today", "yesterday", "7d", "month")
        val THEMES = listOf("app", "light", "dark", "brand")
        val EVERY = listOf(15, 30, 60, 180)
        val BY = listOf("app", "account")

        fun defaults(kind: WKind) = WOpts()

        /** Purana / toota JSON ho to bhi sahi options (galat values default se). */
        fun parse(kind: WKind, s: String?): WOpts {
            if (s.isNullOrBlank()) return defaults(kind)
            val j = runCatching { JSONObject(s) }.getOrNull() ?: return defaults(kind)
            return WOpts(
                range = j.optString("range").takeIf { it in RANGES } ?: "today",
                pick = if (kind.picks) strs(j.optJSONArray("pick")).filter { it.isNotBlank() }.distinct() else emptyList(),
                theme = j.optString("theme").takeIf { it in THEMES } ?: "app",
                every = j.optInt("every", 60).takeIf { it in EVERY } ?: 60,
                by = j.optString("by").takeIf { it in BY } ?: "app",
            )
        }

        private fun strs(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
    }
}

/** "Aaj ka munafa" / "Kal ka munafa" / "7 din ka munafa" / "Is mahine ka munafa". */
internal fun wProfitLabel(range: String): String = when (range) {
    "yesterday" -> "Yesterday's profit".t()
    "7d" -> "7-day profit".t()
    "month" -> "This month's profit".t()
    else -> "Today's profit".t()
}

internal fun wRangeName(range: String): String = when (range) {
    "yesterday" -> "Yesterday".t()
    "7d" -> "7 days".t()
    "month" -> "This month".t()
    else -> "Today".t()
}

internal fun wThemeName(theme: String): String = when (theme) {
    "light" -> "Light".t()
    "dark" -> "Dark".t()
    "brand" -> "MFC brand".t()
    else -> "Same as app".t()
}

/** Ads + Live ki "Ads spend list": "By app" / "By account". */
internal fun wByName(by: String): String = if (by == "account") "By account".t() else "By app".t()

internal fun wEveryName(min: Int): String = when (min) {
    60 -> "1 hour".t()
    180 -> "3 hours".t()
    else -> tf("%s min", min)
}
