package com.myfuturecode.mfcdash.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * Disk par kuch GET (path + query) ka aakhri sahi JSON jawab: app khulte hi screen pichhla data turant dikhaye, phir
 * network se taaza (stale-while-revalidate). App ki apni filesDir/json_cache me (sirf isi app ko milta, Android
 * cache saaf kare to bhi nahi jaata). Kul [MAX_BYTES] tak: zyada ho to sabse purana pehle hatta hai (LRU), [MAX_AGE_MS]
 * (7 din) se purana kabhi nahi diya jaata. Sirf Api.getJson ka 200 + ok:true jawab aata hai: POST, login page, error
 * kabhi nahi. Logout par saaf; server URL / user badle to bhi (har entry [scope] ke saath, alag mila = sab saaf).
 *
 * File ka waqt (lastModified) = jawab kab aaya / dobara wahi aaya (savedAt). Wahi jawab dobara aaye to sirf waqt
 * badalta hai, file dobara nahi likhi jaati.
 *
 * [get] / [put] / [clear] disk ka kaam hain: sirf background thread (Dispatchers.IO) se. [forget] kahin se bhi.
 */
class JsonCache(private val ctx: Context) {
    class Entry(val body: String, val savedAt: Long)

    private val dir by lazy { File(ctx.filesDir, "json_cache").apply { mkdirs() } }
    private val lock = Any()
    /** Abhi kis server + user ka cache hai (hash). null = abhi marker padha nahi. */
    private var owner: String? = null
    /** key -> aakhri likhe jawab ka hash (is process me): wahi jawab ho to file dobara nahi. */
    private val lastHash = HashMap<String, Int>()
    /** key -> aakhri likhe jawab ke aane ka waqt: baad me aaya purana jawab (bg thread ka kram ulta) naye ko na dhak de. */
    private val lastAt = HashMap<String, Long>()
    /** Logout par badhta hai: usse pehle shuru hui request ka jawab baad me pahunche to bhi disk par nahi. */
    private val gen = AtomicInteger(0)
    /** Abhi ki ginti: request shuru hote waqt lo, [put] me do. */
    val generation: Int get() = gen.get()

    /** Turant (bina disk, kisi bhi thread se): ab tak shuru hui requests ke [put] bekaar. Phir background me [clear]. */
    fun forget() { gen.incrementAndGet() }

    fun get(scope: String, key: String): Entry? = synchronized(lock) {
        if (!own(scope)) return null
        val f = file(key)
        val at = f.lastModified()
        if (at <= 0L) return null
        if (System.currentTimeMillis() - at > MAX_AGE_MS) { f.delete(); return null }
        val body = runCatching { f.readText() }.getOrNull() ?: return null
        if (body.isEmpty()) return null
        Entry(body, at)
    }

    /**
     * [g] = request shuru hote waqt ka [generation]: beech me logout hua to kuch nahi likhta. [at] = jawab kab aaya:
     * isi key ka usse naya pehle likh chuka ho to kuch nahi.
     */
    fun put(scope: String, key: String, body: String, g: Int, at: Long = System.currentTimeMillis()) { synchronized(lock) {
        if (g != gen.get() || body.length > MAX_ENTRY || !own(scope)) return
        if ((lastAt[key] ?: Long.MIN_VALUE) > at) return
        val f = file(key)
        val h = body.hashCode()
        if (lastHash[key] == h && f.isFile && f.setLastModified(at)) { lastAt[key] = at; return }
        val tmp = File(dir, f.name + ".tmp")
        try {
            tmp.writeText(body)
            if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) { tmp.delete(); return } }
            lastHash[key] = h
            lastAt[key] = at
        } catch (e: Exception) {
            tmp.delete()
            return
        }
        prune()
    } }

    fun clear() { synchronized(lock) {
        dir.listFiles()?.forEach { it.delete() }
        lastHash.clear()
        lastAt.clear()
        owner = null
    } }

    /** Marker me wahi server + user? Nahi to sab hatao aur naya marker. Pehli baar purane / adhoore bhi saaf. */
    private fun own(scope: String): Boolean {
        val s = sha1(scope)
        if (owner == s) return true
        val m = File(dir, OWNER)
        val cur = runCatching { m.readText() }.getOrNull()
        if (cur != s) {
            dir.listFiles()?.forEach { if (it.name != OWNER) it.delete() }
            lastHash.clear()
            lastAt.clear()
            if (runCatching { m.writeText(s) }.isFailure) return false
        }
        owner = s
        dir.listFiles { x -> x.name.endsWith(".tmp") }?.forEach { it.delete() }
        prune()
        return true
    }

    /** 7 din se purane hatao; kul [MAX_BYTES] se zyada ho to sabse purane (waqt ke hisaab) pehle. */
    private fun prune() {
        val now = System.currentTimeMillis()
        val all = dir.listFiles { x -> x.name.endsWith(".json") } ?: return
        val live = ArrayList<Triple<File, Long, Long>>(all.size)
        for (f in all) {
            val t = f.lastModified()
            if (now - t > MAX_AGE_MS) f.delete() else live.add(Triple(f, t, f.length()))
        }
        var total = live.sumOf { it.third }
        if (total <= MAX_BYTES) return
        live.sortBy { it.second }
        for ((f, _, n) in live) {
            if (total <= MAX_BYTES) break
            if (f.delete()) total -= n
        }
    }

    private fun file(key: String) = File(dir, sha1(key) + ".json")

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        const val MAX_BYTES = 6L * 1024 * 1024
        /** Ek jawab isse bada ho to nahi rakhte (ek hi sab jagah na kha le). */
        const val MAX_ENTRY = 2 * 1024 * 1024
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val OWNER = "owner"
    }
}
