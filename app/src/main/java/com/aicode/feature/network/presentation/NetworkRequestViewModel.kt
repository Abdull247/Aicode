package com.aicode.feature.network.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.R
import com.aicode.feature.network.domain.HttpMethod
import com.aicode.feature.network.domain.HttpRequestExecutor
import com.aicode.feature.network.domain.NetworkSendState
import com.aicode.feature.network.domain.normalizeRequestUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class NetworkRequestUiState(
    val url: String = "",
    val method: HttpMethod = HttpMethod.GET,
    val sendState: NetworkSendState = NetworkSendState.Idle
)

@HiltViewModel
class NetworkRequestViewModel @Inject constructor(
    private val executor: HttpRequestExecutor,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(NetworkRequestUiState())
    val state: StateFlow<NetworkRequestUiState> = _state.asStateFlow()

    fun updateUrl(url: String) {
        _state.update { it.copy(url = url) }
    }

    fun selectMethod(method: HttpMethod) {
        _state.update { it.copy(method = method) }
    }

    fun clear() {
        _state.value = NetworkRequestUiState()
    }

    fun send() {
        val current = _state.value
        val url = normalizeRequestUrl(current.url)
        if (url.isBlank()) {
            _state.update {
                it.copy(sendState = NetworkSendState.Failure(context.getString(R.string.network_request_url_empty)))
            }
            return
        }
        _state.update { it.copy(url = url, sendState = NetworkSendState.Sending) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                executor.execute(current.method, url)
            }
            _state.update { it.copy(sendState = result) }
        }
    }
}
