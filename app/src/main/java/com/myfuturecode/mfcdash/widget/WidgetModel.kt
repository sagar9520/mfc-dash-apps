package com.myfuturecode.mfcdash.widget

import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import org.json.JSONArray
import org.json.JSONObject

/**
 * Teen home-screen widget. items = kya dikhana hai (pehla = bada number), catalog = sab options.
 * Health ka bada number hamesha "apps theek" (fixedHero), baaki 2 chhoti lines.
 */
enum class WKind(
    val key: String,
    val route: String,
    val maxItems: Int,
    val defaults: List<String>,
    val catalog: List<String>,
    val fixedHero: String? = null,
) {
    PAISA(
        "paisa", "money", 4, listOf("profit", "roas", "earned", "spend"),
        listOf("profit", "roas", "earned", "spend", "pl", "top", "loss", "count"),
    ),
    ADS(
        "ads", "gads", 4, listOf("spend", "watch", "pace", "total"),
        listOf("spend", "watch", "pace", "total", "accounts"),
    ),
    HEALTH(
        "health", "health", 2, listOf("p0", "worst"),
        listOf("p0", "worst", "fixes", "issues"), fixedHero = "fine",
    );

    /** Widget ka naam (band + Widgets screen). */
    fun title(): String = when (this) {
        PAISA -> "App money".t()
        ADS -> "Google Ads".t()
        HEALTH -> "App Health".t()
    }

    /** Chhota naam (Widgets screen ka "Paisa widget set karo"). */
    fun shortTitle(): String = when (this) {
        PAISA -> "Money".t()
        ADS -> "Google Ads".t()
        HEALTH -> "App Health".t()
    }

    companion object {
        fun of(key: String?): WKind? = entries.firstOrNull { it.key == key }
    }
}

/** Ek widget ke options. pick = chune apps (Paisa) / accounts (Ads) / consoles (Health); khaali = sab. */
data class WOpts(
    val items: List<String>,
    val range: String = "today",      // Paisa: today | yesterday | 7d | month
    val pick: List<String> = emptyList(),
    val theme: String = "app",        // app | light | dark | brand
    val every: Int = 60,              // minute: 15 | 30 | 60 | 180
) {
    /** Ek jaise options wale widgets ek hi data share karte hain. */
    fun dataKey(kind: WKind): String =
        kind.key + "|" + (if (kind == WKind.PAISA) range else "") + "|" + pick.sorted().joinToString(",")

    fun toJson(): String = JSONObject()
        .put("items", JSONArray(items))
        .put("range", range)
        .put("pick", JSONArray(pick))
        .put("theme", theme)
        .put("every", every)
        .toString()

    companion object {
        val RANGES = listOf("today", "yesterday", "7d", "month")
        val THEMES = listOf("app", "light", "dark", "brand")
        val EVERY = listOf(15, 30, 60, 180)

        fun defaults(kind: WKind) = WOpts(items = kind.defaults)

        /** Purana / toota JSON ho to bhi sahi options (galat values default se). */
        fun parse(kind: WKind, s: String?): WOpts {
            if (s.isNullOrBlank()) return defaults(kind)
            val j = runCatching { JSONObject(s) }.getOrNull() ?: return defaults(kind)
            val items = strs(j.optJSONArray("items")).filter { it in kind.catalog }.distinct().take(kind.maxItems)
            return WOpts(
                items = if (items.isEmpty() && kind.fixedHero == null) kind.defaults else items,
                range = j.optString("range").takeIf { it in RANGES } ?: "today",
                pick = strs(j.optJSONArray("pick")).filter { it.isNotBlank() }.distinct(),
                theme = j.optString("theme").takeIf { it in THEMES } ?: "app",
                every = j.optInt("every", 60).takeIf { it in EVERY } ?: 60,
            )
        }

        private fun strs(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
    }
}

/** Option ka chhota naam (Widgets screen ki checklist + widget ke label). */
internal fun wItemName(kind: WKind, item: String, range: String = "today"): String = when (kind) {
    WKind.PAISA -> when (item) {
        "profit" -> wProfitLabel(range)
        "roas" -> "ROAS"
        "earned" -> "Earnings".t()
        "spend" -> "All ads spend".t()
        "pl" -> "Profit / loss apps".t()
        "top" -> "Top profit app".t()
        "loss" -> "Top loss app".t()
        "count" -> "Apps count".t()
        else -> item
    }
    WKind.ADS -> when (item) {
        "spend" -> "Google Ads spend".t()
        "watch" -> "Watch".t()
        "pace" -> "Spend pace".t()
        "total" -> "Total, \$ included".t()
        "accounts" -> "Accounts".t()
        else -> item
    }
    WKind.HEALTH -> when (item) {
        "fine" -> "Apps fine".t()
        "p0" -> "P0 issues".t()
        "worst" -> "Problem app".t()
        "fixes" -> "AI fixes".t()
        "issues" -> "Open issues".t()
        else -> item
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

internal fun wEveryName(min: Int): String = when (min) {
    60 -> "1 hour".t()
    180 -> "3 hours".t()
    else -> tf("%s min", min)
}
