package com.myfuturecode.mfcdash.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(msg: String, val unauthorized: Boolean = false) : Exception(msg)

/**
 * Site ka hi login use hota hai: index.php par username/password POST → PHP session cookie milti hai,
 * phir api/data.php aur api/crosspromo.php wahi cookie ke saath. Server par kuch naya nahi chahiye.
 * Cookie expire ho jaaye (401) to app chup-chaap dobara login kar leta hai.
 */
class Api(private val prefs: Prefs) {

    private val jar = object : CookieJar {
        private val store = HashMap<String, Cookie>()
        init { load() }
        private fun load() {
            val base = prefs.baseUrl.toHttpUrlOrNull() ?: return
            prefs.cookies.split("\n").filter { it.isNotBlank() }.forEach { line ->
                Cookie.parse(base, line)?.let { store[it.name] = it }
            }
        }
        private fun save() { prefs.cookies = store.values.joinToString("\n") { it.toString() } }
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { store[it.name] = it }
            save()
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return store.values.filter { it.expiresAt > now && it.matches(url) }
        }
        fun clear() { store.clear(); save() }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private fun url(path: String) = prefs.baseUrl + path

    /** Login: 302 (Location: ./) = sahi; 200 = form wapas aaya = galat password. */
    suspend fun login(user: String, pass: String): Unit = withContext(Dispatchers.IO) {
        jar.clear()
        val body = FormBody.Builder()
            .add("username", user).add("password", pass).add("remember", "1").build()
        val req = Request.Builder().url(url("")).post(body).header("Accept", "text/html").build()
        client.newCall(req).execute().use { res ->
            when {
                res.code in 300..399 -> Unit
                res.code == 200 -> {
                    val html = res.body?.string() ?: ""
                    if (html.contains("name=\"password\"")) throw ApiException("Username ya password galat hai", unauthorized = true)
                }
                else -> throw ApiException("Server ne ${res.code} diya")
            }
        }
    }

    private suspend fun getJson(path: String, retry: Boolean = true): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(path)).header("Accept", "application/json").header("Cache-Control", "no-store").build()
        val (code, text) = client.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
        if (code == 401 || (code in 300..399)) {
            if (retry && prefs.hasLogin) {
                login(prefs.username, prefs.password)
                return@withContext getJson(path, retry = false)
            }
            throw ApiException("Login expire ho gaya", unauthorized = true)
        }
        if (code != 200) throw ApiException("Server error $code")
        val j = runCatching { JSONObject(text) }.getOrElse { throw ApiException("Jawab JSON nahi tha (login page?)", unauthorized = text.contains("name=\"password\"")) }
        if (!j.optBoolean("ok", false)) throw ApiException(j.optString("error", "error"))
        j
    }

    private fun query(app: String, range: String, from: String?, to: String?): String {
        val sb = StringBuilder("app=").append(java.net.URLEncoder.encode(app, "UTF-8"))
        if (range == "custom" && !from.isNullOrBlank() && !to.isNullOrBlank()) sb.append("&from=").append(from).append("&to=").append(to)
        else sb.append("&range=").append(if (range == "custom") "today" else range)
        return sb.toString()
    }

    suspend fun data(app: String, range: String, from: String? = null, to: String? = null): DashData =
        DashData.parse(getJson("api/data.php?" + query(app, range, from, to)))

    suspend fun crossPromo(app: String, range: String, from: String? = null, to: String? = null): CpData =
        CpData.parse(getJson("api/crosspromo.php?" + query(app, range, from, to)))

    /** Naya endpoint. Server par api/app.php upload na ho (404) to null: baaki app pehle jaisa chalta hai. */
    suspend fun extra(app: String, range: String, from: String? = null, to: String? = null): ExtraData? =
        try { ExtraData.parse(getJson("api/app.php?" + query(app, range, from, to))) }
        catch (e: ApiException) { if (e.unauthorized) throw e else null }

    suspend fun addNote(campaign: String, pkg: String, note: String): Unit = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("campaign", campaign).add("pkg", pkg).add("note", note).build()
        val req = Request.Builder().url(url("api/app.php?action=note")).post(body).build()
        client.newCall(req).execute().use { res ->
            val j = runCatching { JSONObject(res.body?.string() ?: "") }.getOrNull()
            if (res.code != 200 || j?.optBoolean("ok") != true) throw ApiException(j?.optString("error") ?: "Note save nahi hua (${res.code})")
        }
    }

    suspend fun deleteNote(id: Long): Unit = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("id", id.toString()).build()
        val req = Request.Builder().url(url("api/app.php?action=note_del")).post(body).build()
        client.newCall(req).execute().use { if (it.code != 200) throw ApiException("Delete nahi hua (${it.code})") }
    }

    /** Form POST (login expire ho to ek baar dobara login karke retry). */
    private suspend fun postForm(path: String, fields: Map<String, String>, retry: Boolean = true): JSONObject = withContext(Dispatchers.IO) {
        val fb = FormBody.Builder()
        fields.forEach { (k, v) -> fb.add(k, v) }
        val req = Request.Builder().url(url(path)).post(fb.build()).header("Accept", "application/json").build()
        val (code, text) = client.newCall(req).execute().use { it.code to (it.body?.string() ?: "") }
        if (code == 401 || code in 300..399) {
            if (retry && prefs.hasLogin) { login(prefs.username, prefs.password); return@withContext postForm(path, fields, retry = false) }
            throw ApiException("Login expire ho gaya", unauthorized = true)
        }
        val j = runCatching { JSONObject(text) }.getOrNull() ?: throw ApiException("Server error $code")
        if (code != 200 || !j.optBoolean("ok", false)) throw ApiException(j.optString("error", "Server error $code"))
        j
    }

    // ---- api/alerts.php (smart alerts). Upload na ho (404) to null.
    suspend fun alerts(limit: Int = 150, open: Boolean = false): AlertsData? =
        try { AlertsData.parse(getJson("api/alerts.php?since=0&limit=$limit&lang=" + prefs.lang + (if (open) "&open=1" else ""))) }
        catch (e: ApiException) { if (e.unauthorized) throw e else null }
    suspend fun alertsRead(ids: List<Long>? = null) { postForm("api/alerts.php?action=read", if (ids == null) mapOf("all" to "1") else mapOf("ids" to ids.joinToString(","))) }
    suspend fun alertResolve(id: Long) { postForm("api/alerts.php?action=resolve", mapOf("id" to id.toString())) }
    suspend fun alertTest() { postForm("api/alerts.php?action=test", emptyMap()) }
    suspend fun alertsCompute(): JSONObject = postForm("api/alerts.php?action=compute", emptyMap())
    /** key khali = AI band. Return: server ka ai status + test_ok. */
    suspend fun aiKey(key: String, model: String): JSONObject = postForm("api/alerts.php?action=ai_key", mapOf("key" to key, "model" to model))

    // ---- api/health.php (AI App Health & Auto Fix)
    private fun hq(params: Map<String, String>) = params.filterValues { it.isNotBlank() }.entries
        .joinToString("&") { it.key + "=" + java.net.URLEncoder.encode(it.value, "UTF-8") }
    private suspend fun hget(view: String, params: Map<String, String> = emptyMap()): JSONObject =
        getJson("api/health.php?view=$view" + hq(params).let { if (it.isEmpty()) "" else "&$it" })
    private suspend fun hpost(action: String, fields: Map<String, String>): JSONObject = postForm("api/health.php?action=$action", fields)

    suspend fun hOverview() = HOverview.parse(hget("overview"))
    suspend fun hConsoles() = HConsolesData.parse(hget("consoles"))
    suspend fun hRuns(f: Map<String, String> = emptyMap()) = HRunsData.parse(hget("runs", f))
    suspend fun hRun(id: Long): Pair<HRun, List<HEvent>> = hget("run", mapOf("id" to id.toString())).let { j ->
        HRun.of(j.optJSONObject("run") ?: JSONObject()) to (j.optJSONArray("events")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(HEvent::of) } } ?: emptyList())
    }
    suspend fun hIssues(f: Map<String, String>) = HIssuesData.parse(hget("issues", f))
    suspend fun hIssue(id: Long) = HIssueDetail.parse(hget("issue", mapOf("id" to id.toString())))
    suspend fun hFixes(f: Map<String, String> = emptyMap()): List<HFix> = hget("fixes", f).optJSONArray("fixes").let { a ->
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(HFix::of) }
    }
    suspend fun hBuilds(pkg: String = ""): List<HBuild> = hget("builds", mapOf("pkg" to pkg)).optJSONArray("builds").let { a ->
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(HBuild::of) }
    }
    suspend fun hLogs(f: Map<String, String> = emptyMap()): List<HEvent> = hget("logs", f).optJSONArray("logs").let { a ->
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(HEvent::of) }
    }
    suspend fun hSettings() = HSettings.parse(hget("settings"))
    suspend fun hApp(pkg: String, days: Int) = HAppDetail.parse(hget("app", mapOf("pkg" to pkg, "days" to days.toString())))
    /** CSV ka 15 min wala signed link (browser me khulta hai). */
    suspend fun hExport(f: Map<String, String>): String = hget("export", f).optString("url")

    suspend fun hAction(action: String, fields: Map<String, String> = emptyMap()): JSONObject = hpost(action, fields)

    // ---- api/gads_health.php (Google Ads AI Health Monitor: sirf monitoring, Google Ads me kuch nahi badalta)
    suspend fun gGet(view: String, params: Map<String, String> = emptyMap()): JSONObject =
        getJson("api/gads_health.php?view=$view" + hq(params).let { if (it.isEmpty()) "" else "&$it" })
    suspend fun gAction(action: String, fields: Map<String, String> = emptyMap()): JSONObject = postForm("api/gads_health.php?action=$action", fields)

    // ---- api/studio.php (AI App Research Studio: research, ranking, approvals; kabhi khud publish nahi karta)
    suspend fun sGet(view: String, params: Map<String, String> = emptyMap()): JSONObject =
        getJson("api/studio.php?view=$view" + hq(params).let { if (it.isEmpty()) "" else "&$it" })
    suspend fun sAction(action: String, fields: Map<String, String> = emptyMap()): JSONObject = postForm("api/studio.php?action=$action", fields)

    fun logout() { jar.clear() }
}
