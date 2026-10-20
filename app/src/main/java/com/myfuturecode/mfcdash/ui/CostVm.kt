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

/** AI Cost & Usage Manager ka state: har range (today | 7d | month) ka aakhri jawab. GState wahi frame hai jo Google Ads monitor use karta hai. */
class CostVm(app: App) : ViewModel() {
    private val api = app.api
    private val _s = MutableStateFlow(GState())
    val s: StateFlow<GState> = _s
    private val _msg = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val msg: SharedFlow<String> = _msg

    fun load(range: String = "month") {
        viewModelScope.launch {
            try {
                val j = api.cGet(range)
                _s.update { it.copy(views = it.views + (gkey(range, emptyMap()) to j), error = null, missing = false, lastOk = System.currentTimeMillis()) }
            } catch (e: Exception) {
                val m = e.message ?: "error"
                _s.update { it.copy(error = m, missing = m.contains("404")) }
            }
        }
    }

    /** POST action; ho jaane par range dobara load. */
    fun act(action: String, fields: Map<String, String> = emptyMap(), ok: String? = null, reload: String = "month", then: (JSONObject) -> Unit = {}) {
        if (action in _s.value.busy) return
        _s.update { it.copy(busy = it.busy + action) }
        viewModelScope.launch {
            try {
                val j = api.cAction(action, fields)
                ok?.let { _msg.tryEmit(it) }
                then(j)
                load(reload)
            } catch (e: Exception) {
                _msg.tryEmit(e.message ?: "Failed".t())
            }
            _s.update { it.copy(busy = it.busy - action) }
        }
    }

    companion object {
        fun factory(app: App) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CostVm(app) as T
        }
    }
}
