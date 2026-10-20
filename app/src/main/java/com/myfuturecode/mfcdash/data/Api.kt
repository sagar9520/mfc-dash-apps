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

    fun logout() { jar.clear() }
}
