package com.myfuturecode.mfcdash.data

import com.myfuturecode.mfcdash.util.t
import com.myfuturecode.mfcdash.util.tf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** network = thodi der ki dikkat: server tak pahunch nahi paye, jawab time par nahi aaya, ya server busy (5xx). Login sahi hai, bas dobara try. */
class ApiException(msg: String, val unauthorized: Boolean = false, val network: Boolean = false) : Exception(msg)

/**
 * Site ka hi login use hota hai: index.php par username/password POST → PHP session cookie milti hai,
 * phir api/data.php aur api/crosspromo.php wahi cookie ke saath. Server par kuch naya nahi chahiye.
 * Cookie expire ho jaaye (401) to app chup-chaap dobara login kar leta hai.
 */
class Api(private val prefs: Prefs, private val cache: JsonCache? = null) {

    /**
     * Jawab disk cache me likhna hai? App set karta hai (screen dikh rahi ho tab): 24x7 monitor / widget ke background
     * poll har minute disk par na likhein. Padhna ([dataCached] jaise) hamesha.
     */
    @Volatile var saveCache: () -> Boolean = { true }

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

    // lazy: pehli request par (IO thread) banta hai. OkHttp banate waqt TLS / system certificates load hote hain:
    // App.onCreate (main thread, pehli frame se pehle) me nahi.
    private val client by lazy {
        OkHttpClient.Builder()
            .cookieJar(jar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }
    /** Lambe kaam wale taps (Play publish, alerts abhi check, AI key test): server par Claude/Play call poori hone do. */
    private val slowClient by lazy { client.newBuilder().readTimeout(180, TimeUnit.SECONDS).build() }

    private fun url(path: String) = prefs.baseUrl + path

    /** Ek saath zyada se zyada 4 request: shared hosting par PHP workers kam hote hain, zyada bhejo to sab atakti hain. */
    private val gate = Semaphore(4)
    /** Wahi GET pehle se chal rahi ho to naya call usi ka jawab leta hai (poll jaldi-jaldi chale to requests jama nahi hoti). */
    private val inflight = ConcurrentHashMap<String, Deferred<Pair<Int, String>>>()
    /** Har POST ke baad badhta hai: POST ke baad wali GET, POST se pehle chali GET ka (purana) jawab nahi leti. */
    @Volatile private var writes = 0L
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Request bhejo: code + body. Network ki galti par saaf message (domain/IP wala raw error nahi).
     * Ek baar dobara try: request server tak pahunchi hi nahi (net/DNS/connect), ya GET par 502/503/504 / connection toota.
     * Read timeout par dobara nahi (server pehle se kaam kar raha hai, aur load mat badhao).
     */
    private suspend fun send(req: Request, idempotent: Boolean, slow: Boolean = false): Pair<Int, String> {
        var tries = 0
        while (true) {
            tries++
            try {
                val r = try {
                    gate.withPermit { withContext(Dispatchers.IO) { (if (slow) slowClient else client).newCall(req).execute().use { it.code to (it.body?.string() ?: "") } } }
                } finally { if (req.method != "GET") writes++ }
                if (idempotent && tries == 1 && r.first in 502..504) { delay(2_000); continue }
                return r
            } catch (e: IOException) {
                if (tries == 1 && (notSent(e) || (idempotent && e !is SocketTimeoutException))) { delay(1_500); continue }
                throw netError(e)
            }
        }
    }

    /** Request server tak gayi hi nahi: dobara bhejna safe hai (POST bhi). */
    private fun notSent(e: IOException) = e is UnknownHostException || e is ConnectException || e is NoRouteToHostException ||
        (e is SocketTimeoutException && e.message?.startsWith("failed to connect", ignoreCase = true) == true)

    private fun netError(e: IOException): ApiException = ApiException(network = true, msg = when {
        e is UnknownHostException -> "No internet, or the server could not be found. Check the net and try again.".t()
        e is SocketTimeoutException && e.message?.startsWith("failed to connect", ignoreCase = true) == true ->
            "Could not connect to the server. Try again in a little while.".t()
        e is ConnectException || e is NoRouteToHostException -> "Could not connect to the server. Try again in a little while.".t()
        e is SocketTimeoutException -> "The server took too long to answer. Try again in a little while.".t()
        e is SSLException -> "Secure connection to the server failed. Check the phone's date/time and the net.".t()
        else -> "Network problem. Try again.".t()
    })

    /** 502/503/504 = hosting abhi bhari (thodi der ki baat). 500 = server file/config ki galti: wahi code dikhao. */
    private fun httpError(code: Int): String =
        if (code in 502..504) tf("Server is busy (error %s). Try again in a little while.", code) else "Server error $code"

    /** GET, ek URL ki ek hi request ek waqt par (baaki usi ka jawab lete hain). */
    private suspend fun fetch(u: String): Pair<Int, String> {
        val key = "$u#$writes"
        while (true) {
            val mine = bg.async(start = CoroutineStart.LAZY) {
                send(Request.Builder().url(u).header("Accept", "application/json").header("Cache-Control", "no-store").build(), idempotent = true)
            }
            val cur = inflight.putIfAbsent(key, mine)
            if (cur == null) {
                mine.invokeOnCompletion { inflight.remove(key, mine) }
                mine.start()
                return mine.await()
            }
            mine.cancel()
            if (cur.isCompleted) { inflight.remove(key, cur); continue }   // purana (khatam) jawab dobara mat do
            return cur.await()
        }
    }

    /** Login: 302 (Location: ./) = sahi; 200 = form wapas aaya = galat password. */
    suspend fun login(user: String, pass: String): Unit = withContext(Dispatchers.IO) {
        jar.clear()
        val body = FormBody.Builder()
            .add("username", user).add("password", pass).add("remember", "1").build()
        val req = Request.Builder().url(url("")).post(body).header("Accept", "text/html").build()
        val (code, html) = send(req, idempotent = true)   // login dobara bhejna safe hai
        if (code in 300..399) return@withContext
        if (code == 200) {
            if (html.contains("name=\"password\"")) throw ApiException("Username ya password galat hai", unauthorized = true)
            return@withContext
        }
        throw ApiException(if (code in 502..504) httpError(code) else "Server ne $code diya", network = code in 502..504)
    }

    // PageLoad.track: page load (upar ki neeli line) ke dauraan shuru hui request ginti me
    private suspend fun getJson(path: String, retry: Boolean = true): JSONObject = PageLoad.track { withContext(Dispatchers.IO) {
        val gen = cache?.generation ?: 0
        val (code, text) = fetch(url(path))
        if (code == 401 || (code in 300..399)) {
            if (retry && prefs.hasLogin) {
                login(prefs.username, prefs.password)
                return@withContext getJson(path, retry = false)
            }
            throw ApiException("Login expire ho gaya", unauthorized = true)
        }
        if (code in 500..599) {   // server ka apna JSON error ho to wahi, warna "server busy" (502/503/504 = hosting par jagah nahi)
            val err = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
            throw ApiException(err.ifBlank { httpError(code) }, network = err.isBlank() && code in 502..504)
        }
        if (code != 200) throw ApiException(httpError(code))
        val j = runCatching { JSONObject(text) }.getOrElse { throw ApiException("Jawab JSON nahi tha (login page?)", unauthorized = text.contains("name=\"password\"")) }
        if (!j.optBoolean("ok", false)) throw ApiException(j.optString("error", "error"))
        // sirf sahi jawab (200 + ok) disk par, background me: agli baar screen turant isi se khulti hai
        val c = cache
        if (c != null && cacheable(path) && saveCache()) {
            val sc = runCatching { cacheScope() }.getOrNull()
            val at = System.currentTimeMillis()   // jawab abhi aaya: bg me likhne ka kram ulta ho to bhi naya hi rahe
            if (sc != null) bg.launch { runCatching { c.put(sc, path, text, gen, at) } }
        }
        j
    } }

    // ---- disk cache (JsonCache): pehli screen pichhle sahi jawab se, phir network
    /** Sirf yahi GET disk par: dashboard (data / cross promo / app.php), alerts list, modules ke overview, AI kharcha. */
    private fun cacheable(path: String) = CACHE_PATHS.any { path.startsWith(it) }

    /** Kis server + user ka cache (dusre ka kabhi nahi milta). Login nahi = cache nahi. */
    private fun cacheScope(): String? = if (prefs.hasLogin) prefs.baseUrl + "\n" + prefs.username else null

    /** Disk wala aakhri sahi jawab, model banake (background thread par). Na ho / 7 din purana / dusre user ka = null. Network nahi. */
    private suspend fun <T> peek(path: String, parse: (JSONObject) -> T): Cached<T>? {
        val c = cache ?: return null
        if (!cacheable(path)) return null
        return withContext(Dispatchers.IO) {
            val sc = runCatching { cacheScope() }.getOrNull() ?: return@withContext null
            val e = runCatching { c.get(sc, path) }.getOrNull() ?: return@withContext null
            runCatching { Cached(parse(JSONObject(e.body)), e.savedAt) }.getOrNull()
        }
    }

    /** Network jawab ko model banana (bada JSON) main thread par nahi. */
    private suspend fun <T> getAs(path: String, parse: (JSONObject) -> T): T {
        val j = getJson(path)
        return withContext(Dispatchers.Default) { parse(j) }
    }

    private fun query(app: String, range: String, from: String?, to: String?): String {
        val sb = StringBuilder("app=").append(java.net.URLEncoder.encode(app, "UTF-8"))
        if (range == "custom" && !from.isNullOrBlank() && !to.isNullOrBlank()) sb.append("&from=").append(from).append("&to=").append(to)
        else sb.append("&range=").append(if (range == "custom") "today" else range)
        return sb.toString()
    }

    suspend fun data(app: String, range: String, from: String? = null, to: String? = null): DashData =
        getAs("api/data.php?" + query(app, range, from, to), DashData::parse)

    suspend fun crossPromo(app: String, range: String, from: String? = null, to: String? = null): CpData =
        getAs("api/crosspromo.php?" + query(app, range, from, to), CpData::parse)

    /** Naya endpoint. Server par api/app.php upload na ho (404) to null: baaki app pehle jaisa chalta hai. */
    suspend fun extra(app: String, range: String, from: String? = null, to: String? = null): ExtraData? =
        try { getAs("api/app.php?" + query(app, range, from, to), ExtraData::parse) }
        catch (e: ApiException) { if (e.unauthorized || e.network) throw e else null }   // net ki dikkat = "upload nahi hua" nahi

    // Pichhla sahi jawab disk se (network nahi): screen khulte hi yahi, phir upar wala network call. Na ho = null.
    suspend fun dataCached(app: String, range: String, from: String? = null, to: String? = null): Cached<DashData>? =
        peek("api/data.php?" + query(app, range, from, to), DashData::parse)
    suspend fun crossPromoCached(app: String, range: String, from: String? = null, to: String? = null): Cached<CpData>? =
        peek("api/crosspromo.php?" + query(app, range, from, to), CpData::parse)
    suspend fun extraCached(app: String, range: String, from: String? = null, to: String? = null): Cached<ExtraData>? =
        peek("api/app.php?" + query(app, range, from, to), ExtraData::parse)

    suspend fun addNote(campaign: String, pkg: String, note: String): Unit = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("campaign", campaign).add("pkg", pkg).add("note", note).build()
        val req = Request.Builder().url(url("api/app.php?action=note")).post(body).build()
        val (code, text) = send(req, idempotent = false)
        val j = runCatching { JSONObject(text) }.getOrNull()
        if (code != 200 || j?.optBoolean("ok") != true) throw ApiException(j?.optString("error") ?: "Note save nahi hua ($code)")
    }

    suspend fun deleteNote(id: Long): Unit = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("id", id.toString()).build()
        val req = Request.Builder().url(url("api/app.php?action=note_del")).post(body).build()
        val (code, _) = send(req, idempotent = true)   // id wala delete dobara chale to bhi same
        if (code != 200) throw ApiException("Delete nahi hua ($code)")
    }

    /** Form POST (login expire ho to ek baar dobara login karke retry). */
    private suspend fun postForm(path: String, fields: Map<String, String>, retry: Boolean = true, slow: Boolean = false): JSONObject = PageLoad.track { withContext(Dispatchers.IO) {
        val fb = FormBody.Builder()
        fields.forEach { (k, v) -> fb.add(k, v) }
        val req = Request.Builder().url(url(path)).post(fb.build()).header("Accept", "application/json").build()
        val (code, text) = send(req, idempotent = false, slow = slow)
        if (code == 401 || code in 300..399) {
            if (retry && prefs.hasLogin) { login(prefs.username, prefs.password); return@withContext postForm(path, fields, retry = false, slow = slow) }
            throw ApiException("Login expire ho gaya", unauthorized = true)
        }
        val j = runCatching { JSONObject(text) }.getOrNull() ?: throw ApiException(httpError(code))
        if (code != 200 || !j.optBoolean("ok", false)) throw ApiException(j.optString("error", httpError(code)))
        j
    } }

    // ---- api/alerts.php (smart alerts). Upload na ho (404) to null.
    private fun alertsPath(limit: Int, open: Boolean) = "api/alerts.php?since=0&limit=$limit&lang=" + prefs.lang + (if (open) "&open=1" else "")
    suspend fun alerts(limit: Int = 150, open: Boolean = false): AlertsData? =
        try { getAs(alertsPath(limit, open), AlertsData::parse) }
        catch (e: ApiException) { if (e.unauthorized || e.network) throw e else null }   // net ki dikkat = "upload nahi hua" nahi
    /** AlertsSync.check wali list (limit 150) disk se. */
    suspend fun alertsCached(limit: Int = 150, open: Boolean = false): Cached<AlertsData>? = peek(alertsPath(limit, open), AlertsData::parse)
    suspend fun alertsRead(ids: List<Long>? = null) { postForm("api/alerts.php?action=read", if (ids == null) mapOf("all" to "1") else mapOf("ids" to ids.joinToString(","))) }
    suspend fun alertResolve(id: Long) { postForm("api/alerts.php?action=resolve", mapOf("id" to id.toString())) }
    suspend fun alertTest() { postForm("api/alerts.php?action=test", emptyMap()) }
    suspend fun alertsCompute(): JSONObject = postForm("api/alerts.php?action=compute", emptyMap(), slow = true)
    /** key khali = AI band. Return: server ka ai status + test_ok. */
    suspend fun aiKey(key: String, model: String): JSONObject = postForm("api/alerts.php?action=ai_key", mapOf("key" to key, "model" to model), slow = true)

    // ---- api/health.php (AI App Health & Auto Fix)
    private fun hq(params: Map<String, String>) = params.filterValues { it.isNotBlank() }.entries
        .joinToString("&") { it.key + "=" + java.net.URLEncoder.encode(it.value, "UTF-8") }
    private fun hpath(view: String, params: Map<String, String> = emptyMap()) =
        "api/health.php?view=$view" + hq(params).let { if (it.isEmpty()) "" else "&$it" }
    private suspend fun hget(view: String, params: Map<String, String> = emptyMap()): JSONObject = getJson(hpath(view, params))
    private suspend fun <T> hgetAs(view: String, params: Map<String, String> = emptyMap(), parse: (JSONObject) -> T): T = getAs(hpath(view, params), parse)
    private suspend fun hpost(action: String, fields: Map<String, String>): JSONObject =
        postForm("api/health.php?action=$action", fields, slow = action == "publish" || action == "playmail_test")   // mailbox IMAP check me der lagti hai

    suspend fun hOverview(): HOverview = hgetAs("overview", parse = HOverview::parse)
    suspend fun hConsoles(): HConsolesData = hgetAs("consoles", parse = HConsolesData::parse)
    suspend fun hOverviewCached(): Cached<HOverview>? = peek(hpath("overview"), HOverview::parse)
    suspend fun hConsolesCached(): Cached<HConsolesData>? = peek(hpath("consoles"), HConsolesData::parse)
    suspend fun hRuns(f: Map<String, String> = emptyMap()): HRunsData = hgetAs("runs", f, HRunsData::parse)
    suspend fun hRun(id: Long): Pair<HRun, List<HEvent>> = hget("run", mapOf("id" to id.toString())).let { j ->
        HRun.of(j.optJSONObject("run") ?: JSONObject()) to (j.optJSONArray("events")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(HEvent::of) } } ?: emptyList())
    }
    suspend fun hIssues(f: Map<String, String>): HIssuesData = hgetAs("issues", f, HIssuesData::parse)
    suspend fun hIssue(id: Long): HIssueDetail = hgetAs("issue", mapOf("id" to id.toString()), HIssueDetail::parse)
    suspend fun hFixes(f: Map<String, String> = emptyMap()): List<HFix> = hget("fixes", f).optJSONArray("fixes").let { a ->
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(HFix::of) }
    }
    suspend fun hBuilds(pkg: String = ""): List<HBuild> = hget("builds", mapOf("pkg" to pkg)).optJSONArray("builds").let { a ->
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(HBuild::of) }
    }
    suspend fun hLogs(f: Map<String, String> = emptyMap()): List<HEvent> = hget("logs", f).optJSONArray("logs").let { a ->
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(HEvent::of) }
    }
    suspend fun hSettings(): HSettings = hgetAs("settings", parse = HSettings::parse)
    suspend fun hApp(pkg: String, days: Int): HAppDetail = hgetAs("app", mapOf("pkg" to pkg, "days" to days.toString()), HAppDetail::parse)
    /** CSV ka 15 min wala signed link (browser me khulta hai). */
    suspend fun hExport(f: Map<String, String>): String = hget("export", f).optString("url")

    suspend fun hAction(action: String, fields: Map<String, String> = emptyMap()): JSONObject = hpost(action, fields)

    /** Play Console emails (lib/playmail.php) ka haal. Purana server overview bhejta hai = configured false. */
    suspend fun playmailStatus(): HPlayMail = HPlayMail.of(getJson("api/health.php?action=playmail_status"))
    /** Mailbox abhi check karo. Galti (config nahi, login fail) = ApiException me server ka error. */
    suspend fun playmailTest(): HPlayMailTest = HPlayMailTest.of(hpost("playmail_test", emptyMap()))

    // ---- api/gads_health.php (Google Ads AI Health Monitor: sirf monitoring, Google Ads me kuch nahi badalta)
    private fun gpath(view: String, params: Map<String, String>) = "api/gads_health.php?view=$view" + hq(params).let { if (it.isEmpty()) "" else "&$it" }
    suspend fun gGet(view: String, params: Map<String, String> = emptyMap()): JSONObject = getJson(gpath(view, params))
    /** Disk wala aakhri jawab (abhi sirf overview rakha jaata hai; baaki view = null). */
    suspend fun gGetCached(view: String, params: Map<String, String> = emptyMap()): Cached<JSONObject>? = peek(gpath(view, params)) { it }
    suspend fun gAction(action: String, fields: Map<String, String> = emptyMap()): JSONObject = postForm("api/gads_health.php?action=$action", fields)

    // ---- api/studio.php (AI App Research Studio: research, ranking, approvals; kabhi khud publish nahi karta)
    private fun spath(view: String, params: Map<String, String>) = "api/studio.php?view=$view" + hq(params).let { if (it.isEmpty()) "" else "&$it" }
    suspend fun sGet(view: String, params: Map<String, String> = emptyMap()): JSONObject = getJson(spath(view, params))
    /** Disk wala aakhri jawab (abhi sirf overview rakha jaata hai; baaki view = null). */
    suspend fun sGetCached(view: String, params: Map<String, String> = emptyMap()): Cached<JSONObject>? = peek(spath(view, params)) { it }
    suspend fun sAction(action: String, fields: Map<String, String> = emptyMap()): JSONObject = postForm("api/studio.php?action=$action", fields)

    // ---- api/cost.php (AI Cost & Usage Manager: kharcha, budget, limits)
    suspend fun cGet(range: String): JSONObject = getJson("api/cost.php?range=$range")
    suspend fun cGetCached(range: String): Cached<JSONObject>? = peek("api/cost.php?range=$range") { it }
    suspend fun cAction(action: String, fields: Map<String, String> = emptyMap()): JSONObject = postForm("api/cost.php?action=$action", fields)

    /** Cookie + disk cache saaf (agla user / server pichhle ka data kabhi na dekhe). */
    fun logout() {
        jar.clear()
        cache?.let { c -> c.forget(); bg.launch { runCatching { c.clear() } } }
    }
}

/** Disk cache se aaya jawab: [value] + kab aaya tha ([at], ms). Live nahi: screen ise "abhi ka" na bataaye. */
class Cached<out T>(val value: T, val at: Long)

private val CACHE_PATHS = listOf(
    "api/data.php?", "api/crosspromo.php?", "api/app.php?", "api/alerts.php?since=0",
    "api/health.php?view=overview", "api/health.php?view=consoles", "api/gads_health.php?view=overview",
    "api/studio.php?view=overview", "api/cost.php?range=",
)
