package xyz.ximanta.pluck.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.ximanta.pluck.data.ApiClient
import xyz.ximanta.pluck.data.AuthState
import xyz.ximanta.pluck.data.AuthStore
import xyz.ximanta.pluck.data.FetchRequest
import xyz.ximanta.pluck.data.FetchResult
import xyz.ximanta.pluck.data.HistoryEntry
import xyz.ximanta.pluck.data.MediaFormat
import xyz.ximanta.pluck.data.ResolveResponse
import java.io.File

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

sealed class ResolveState {
    object Idle : ResolveState()
    object Loading : ResolveState()
    data class Success(val data: ResolveResponse) : ResolveState()
    data class Error(val message: String) : ResolveState()
}

sealed class FetchState {
    object Idle : FetchState()
    data class Downloading(val bytesRead: Long, val totalBytes: Long?) : FetchState()
    data class Success(val result: FetchResult) : FetchState()
    data class Error(val message: String) : FetchState()
}

sealed class HistoryState {
    object Idle : HistoryState()
    object Loading : HistoryState()
    data class Success(val entries: List<HistoryEntry>) : HistoryState()
    data class Error(val message: String) : HistoryState()
}

class MainViewModel(
    private val apiClient: ApiClient,
    private val authStore: AuthStore
) : ViewModel() {

    val authState: StateFlow<AuthState> = authStore.state

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _resolveState = MutableStateFlow<ResolveState>(ResolveState.Idle)
    val resolveState: StateFlow<ResolveState> = _resolveState.asStateFlow()

    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Idle)
    val fetchState: StateFlow<FetchState> = _fetchState.asStateFlow()

    private val _historyState = MutableStateFlow<HistoryState>(HistoryState.Idle)
    val historyState: StateFlow<HistoryState> = _historyState.asStateFlow()

    fun login(rawKey: String) {
        val key = rawKey.trim().uppercase()
        if (key.isBlank()) return
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            apiClient.validateKey(key)
                .onSuccess {
                    authStore.save(key, "", "", 0L)
                    _loginState.value = LoginState.Success
                }
                .onFailure { e ->
                    _loginState.value = LoginState.Error(humanizeError(e))
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authStore.clear()
            _loginState.value = LoginState.Idle
            _resolveState.value = ResolveState.Idle
            _fetchState.value = FetchState.Idle
            _historyState.value = HistoryState.Idle
        }
    }

    fun resolve(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _resolveState.value = ResolveState.Loading
            apiClient.resolve(url)
                .onSuccess { _resolveState.value = ResolveState.Success(it) }
                .onFailure { _resolveState.value = ResolveState.Error(humanizeError(it)) }
        }
    }

    fun resetResolve() {
        _resolveState.value = ResolveState.Idle
    }

    fun fetch(
        sourceUrl: String,
        format: MediaFormat?,
        mediaType: String,
        destination: File,
        title: String = "",
        platform: String = "",
        thumbnail: String = "",
        durationSec: Int = 0
    ) {
        viewModelScope.launch {
            _fetchState.value = FetchState.Downloading(0L, null)
            val req = FetchRequest(
                url = sourceUrl,
                formatId = format?.formatId?.takeIf { it.isNotBlank() && format.directUrl.isBlank() },
                directUrl = format?.directUrl?.takeIf { it.isNotBlank() },
                mediaType = mediaType,
                mergeAudio = format?.note?.contains("merge", ignoreCase = true) == true,
                filename = format?.filename?.takeIf { it.isNotBlank() },
                title = title.ifBlank { null },
                platform = platform.ifBlank { null },
                thumbnail = thumbnail.ifBlank { null },
                quality = format?.quality?.takeIf { it.isNotBlank() },
                ext = format?.ext?.takeIf { it.isNotBlank() },
                durationSec = durationSec
            )
            apiClient.fetch(req, destination) { bytes, total ->
                _fetchState.value = FetchState.Downloading(bytes, total)
            }.onSuccess { _fetchState.value = FetchState.Success(it) }
             .onFailure { _fetchState.value = FetchState.Error(humanizeError(it)) }
        }
    }

    fun resetFetch() {
        _fetchState.value = FetchState.Idle
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _historyState.value = HistoryState.Loading
            apiClient.history()
                .onSuccess { _historyState.value = HistoryState.Success(it.entries) }
                .onFailure { _historyState.value = HistoryState.Error(humanizeError(it)) }
        }
    }

    private fun humanizeError(e: Throwable): String {
        val raw = e.message ?: "request failed"
        return when {
            raw.contains("401") || raw.contains("unauthorized", ignoreCase = true) ->
                "Key invalid or expired"
            raw.contains("rate_limited", ignoreCase = true) ->
                "Too many requests, slow down"
            raw.contains("timeout", ignoreCase = true) ->
                "Network timeout"
            raw.contains("Unable to resolve host", ignoreCase = true) ->
                "No internet connection"
            else -> raw
        }
    }

    companion object {
        fun factory(apiClient: ApiClient, authStore: AuthStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(apiClient, authStore) as T
                }
            }
    }
}
