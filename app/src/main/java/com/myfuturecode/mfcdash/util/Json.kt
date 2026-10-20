package com.myfuturecode.mfcdash.util

import org.json.JSONArray
import org.json.JSONObject

// PHP/MySQL se numbers kabhi string ("12.5") aate hain, kabhi null. Yeh helpers sab sambhal lete hain.
fun JSONObject.d(key: String, def: Double = 0.0): Double {
    if (!has(key) || isNull(key)) return def
    val v = opt(key)
    return when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull() ?: def
        is Boolean -> if (v) 1.0 else 0.0
        else -> def
    }
}

fun JSONObject.dn(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }
}

fun JSONObject.l(key: String): Long = d(key).toLong()
fun JSONObject.s(key: String, def: String = ""): String = if (has(key) && !isNull(key)) optString(key, def) else def
fun JSONObject.b(key: String): Boolean = when (val v = opt(key)) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> v == "1" || v.equals("true", true)
    else -> false
}
fun JSONObject.obj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()
fun JSONObject.arr(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
fun <T> JSONArray.mapObj(f: (JSONObject) -> T): List<T> {
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(f(it)) }
    return out
}
fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }
