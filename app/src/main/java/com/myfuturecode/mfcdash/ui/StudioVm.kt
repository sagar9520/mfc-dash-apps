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

/** AI App Research Studio: har view ka aakhri jawab (GState hi reuse). Actions = approvals / settings; app kabhi khud publish nahi karta. */
class StudioVm(app: App) : ViewModel() {
    private val api = app.api
    private val _s = MutableStateFlow(GState())
    val s: StateFlow<GState> = _s
    private val _msg = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val msg: SharedFlow<String> = _msg

    private fun say(m: String) { _msg.tryEmit(m) }

    fun load(view: String, p: Map<String, String> = emptyMap()) {
        val k = gkey(view, p)
        val q = p.filterValues { it.isNotBlank() }
        // pehli baar (app khuli): disk wala pichhla jawab turant (abhi sirf overview rakha jaata hai), phir network
        if (k !in _s.value.views) viewModelScope.launch {
            val c = runCatching { api.sGetCached(view, q) }.getOrNull() ?: return@launch
            _s.update { it.withCached(k, c) }
        }
        viewModelScope.launch {
            try {
                val j = api.sGet(view, q)
                _s.update { it.copy(views = it.views + (k to j), cached = it.cached - k, error = null, missing = false, lastOk = System.currentTimeMillis()) }
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
                val j = api.sAction(action, fields)
                ok?.let { say(it) }
                then(j)
            } catch (e: Exception) {
                say(e.message ?: "Failed".t())
            }
            _s.update { it.copy(busy = it.busy - busyKey) }
        }
    }

    companion object {
        fun factory(app: App) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StudioVm(app) as T
        }
    }
}
