package com.myfuturecode.mfcdash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myfuturecode.mfcdash.App
import com.myfuturecode.mfcdash.util.t
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Google Ads AI Health Monitor ka state: har view (view + filters) ka aakhri jawab. Sirf padhna; actions sirf dashboard ke andar (ack, settings). */
data class GState(
    val views: Map<String, JSONObject> = emptyMap(),
    val busy: Set<String> = emptySet(),
    val error: String? = null,
    val missing: Boolean = false,                            // server par api/gads_health.php nahi (404)
    val lastOk: Long = 0L,
) {
    fun view(view: String, p: Map<String, String> = emptyMap()): JSONObject? = views[gkey(view, p)]
}

fun gkey(view: String, p: Map<String, String>) = view + "?" + p.filterValues { it.isNotBlank() }.toSortedMap().toString()

class GadsVm(app: App) : ViewModel() {
    private val api = app.api
    private val _s = MutableStateFlow(GState())
    val s: StateFlow<GState> = _s
    private val _msg = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val msg: SharedFlow<String> = _msg

    private fun say(m: String) { _msg.tryEmit(m) }

    fun load(view: String, p: Map<String, String> = emptyMap()) {
        val k = gkey(view, p)
        viewModelScope.launch {
            try {
                val j = api.gGet(view, p.filterValues { it.isNotBlank() })
                _s.update { it.copy(views = it.views + (k to j), error = null, missing = false, lastOk = System.currentTimeMillis()) }
            } catch (e: Exception) {
                val m = e.message ?: "error"
                _s.update { it.copy(error = m, missing = m.contains("404")) }
            }
        }
    }

    /** POST action. Busy key = button par spinner; then = baad me kya reload karna hai. */
    fun act(action: String, fields: Map<String, String> = emptyMap(), busyKey: String = action, ok: String? = null, then: (JSONObject) -> Unit = {}) {
        if (busyKey in _s.value.busy) return
        _s.update { it.copy(busy = it.busy + busyKey) }
        viewModelScope.launch {
            try {
                val j = api.gAction(action, fields)
                ok?.let { say(it) }
                then(j)
            } catch (e: Exception) {
                say(e.message ?: "Failed".t())
            }
            _s.update { it.copy(busy = it.busy - busyKey) }
        }
    }

    /** Script ka text (CID + URL bhara hua); token app khud daalta hai. */
    fun script(cid: String, onText: (String, String) -> Unit) {
        if ("script$cid" in _s.value.busy) return
        _s.update { it.copy(busy = it.busy + "script$cid") }
        viewModelScope.launch {
            try { val j = api.gGet("script", mapOf("cid" to cid)); onText(j.optString("script"), j.optString("placeholder", "PASTE-TOKEN-HERE")) }
            catch (e: Exception) { say(e.message ?: "Failed".t()) }
            _s.update { it.copy(busy = it.busy - "script$cid") }
        }
    }

    companion object {
        fun factory(app: App) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GadsVm(app) as T
        }
    }
}
