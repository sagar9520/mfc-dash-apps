package com.myfuturecode.mfcdash.data

import com.myfuturecode.mfcdash.util.*
import org.json.JSONArray
import org.json.JSONObject

/** `api/gads_health.php` (Google Ads AI Health Monitor) ke jawab. Har account apni currency me; timestamps UTC. */

data class GAlert(
    val id: Long, val cid: String, val cidFmt: String, val company: String, val currency: String, val campaignId: String, val campaign: String,
    val adGroupId: String, val country: String, val rule: String, val level: String, val klass: String, val title: String, val titleHi: String,
    val what: String, val whatHi: String, val metrics: JSONObject, val causes: JSONArray, val next: String, val nextHi: String, val impact: String,
    val evidence: String, val freshnessMin: Int?, val firstSeen: String, val lastSeen: String, val resolvedAt: String?, val resolvedBy: String,
    val ackAt: String?, val notifiedAt: String?, val ai: JSONObject?, val aiConf: Int?, val aiAt: String?, val aiErr: String, val sim: Boolean, val closable: Boolean,
) {
    val open get() = resolvedAt == null
    val titleL get() = if (Lang.hi && titleHi.isNotBlank()) titleHi else title
    val whatL get() = if (Lang.hi && whatHi.isNotBlank()) whatHi else what
    val nextL get() = if (Lang.hi && nextHi.isNotBlank()) nextHi else next
    companion object {
        fun of(j: JSONObject) = GAlert(
            j.l("id"), j.s("cid"), j.s("cid_fmt"), j.s("company"), j.s("currency"), j.s("campaign_id"), j.s("campaign"), j.s("ad_group_id"), j.s("country"),
            j.s("rule"), j.s("level"), j.s("klass"), j.s("title"), j.s("title_hi"), j.s("what"), j.s("what_hi"), j.obj("metrics"), j.arr("causes"), j.s("next"), j.s("next_hi"),
            j.s("impact"), j.s("evidence"), j.dn("freshness_min")?.toInt(), j.s("first_seen"), j.s("last_seen"), j.sn("resolved_at"), j.s("resolved_by"), j.sn("ack_at"),
            j.sn("notified_at"), j.optJSONObject("ai"), j.dn("ai_conf")?.toInt(), j.sn("ai_at"), j.s("ai_err"), j.b("sim"), j.b("closable"),
        )
    }
}

data class GAccount(
    val cid: String, val cidFmt: String, val company: String, val accountName: String, val mccLabel: String, val invoiceRef: String, val creditRef: String,
    val currency: String, val tz: String, val notes: String, val srcDashboard: Boolean, val srcScript: Boolean, val srcEmail: Boolean, val monitoring: Boolean,
    val push: Boolean, val email: Boolean, val tokenHint: String, val authorizedBy: String, val authorizedAt: String?, val health: String, val lastState: String,
    val lastScanAt: String?, val scriptLastAt: String?, val scriptVersion: String, val scriptStatus: String, val raw: JSONObject,
) {
    companion object {
        fun of(j: JSONObject) = GAccount(
            j.s("cid"), j.s("cid_fmt"), j.s("company"), j.s("account_name"), j.s("mcc_label"), j.s("invoice_ref"), j.s("credit_ref"), j.s("currency"), j.s("tz"), j.s("notes"),
            j.b("src_dashboard"), j.b("src_script"), j.b("src_email"), j.b("monitoring"), j.b("push"), j.b("email"), j.s("token_hint"), j.s("authorized_by"), j.sn("authorized_at"),
            j.s("health"), j.s("last_state"), j.sn("last_scan_at"), j.sn("script_last_at"), j.s("script_version"), j.s("script_status"), j,
        )
    }
}

data class GRun(
    val id: Long, val uid: String, val type: String, val trigger: String, val by: String, val status: String, val startedAt: String, val finishedAt: String?,
    val accounts: Int, val campaigns: Int, val sources: JSONArray, val issues: Int, val recs: Int, val failedChecks: Int, val errors: String, val nextRunAt: String?, val sim: Boolean,
    val summary: JSONObject?,
) {
    companion object {
        fun of(j: JSONObject) = GRun(
            j.l("id"), j.s("uid"), j.s("type"), j.s("trigger"), j.s("by"), j.s("status"), j.s("started_at"), j.sn("finished_at"), j.d("accounts_checked").toInt(),
            j.d("campaigns_checked").toInt(), j.optJSONArray("sources") ?: JSONArray(), j.d("issues").toInt(), j.d("recs").toInt(), j.d("failed_checks").toInt(), j.s("errors"),
            j.sn("next_run_at"), j.b("sim"), j.optJSONObject("summary"),
        )
    }
}

data class GEvent(val id: Long, val runId: Long?, val cid: String, val alertId: Long?, val level: String, val msg: String, val createdAt: String) {
    companion object {
        fun of(j: JSONObject) = GEvent(j.l("id"), j.dn("run_id")?.toLong(), j.s("customer_id"), j.dn("alert_id")?.toLong(), j.s("level", "info"), j.s("msg"), j.s("created_at"))
    }
}

/**
 * Overview ka "app_budgets": ek app (pkg) ka aaj, ek account currency me: chalu campaigns ke daily budget ka jod aur jin
 * accounts me us app ka budget hai unme app ke sab campaigns ka aaj ka kharcha (ruke / bina budget wale bhi; account wale
 * spend_today / budget % jaisa hi hisaab, wahi source). Paisa usi currency me (kabhi do currency jodte nahi).
 */
data class GAppBudget(val pkg: String, val currency: String, val budget: Double, val spend: Double, val campaigns: Int) {
    companion object {
        fun of(j: JSONObject) = GAppBudget(
            j.s("pkg").trim(), j.s("currency").trim().uppercase(), j.d("budget_today"), j.d("spend_today"), j.d("campaigns").toInt(),
        )

        /** Overview se sirf kaam ki lines (pkg ho, budget > 0); purana server / overview nahi = khaali. */
        fun list(o: JSONObject?): List<GAppBudget> =
            o?.optJSONArray("app_budgets")?.mapObj(::of)?.filter { it.pkg.isNotBlank() && it.budget > 0 && it.budget.isFinite() }.orEmpty()
    }
}

/** [state, text] check (rule engine): OK / WARN / INSUFFICIENT ... */
fun JSONObject.checks(): List<Triple<String, String, String>> = keys().asSequence().mapNotNull { k ->
    val a = optJSONArray(k) ?: return@mapNotNull null
    Triple(k, a.optString(0), a.optString(1))
}.toList()
