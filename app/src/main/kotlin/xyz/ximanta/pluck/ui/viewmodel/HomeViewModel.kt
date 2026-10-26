package xyz.ximanta.pluck.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.ximanta.pluck.data.ApiClient
import xyz.ximanta.pluck.data.ApiException
import xyz.ximanta.pluck.data.AuthStore
import xyz.ximanta.pluck.data.FetchRequest
import xyz.ximanta.pluck.data.HistoryEntry
import xyz.ximanta.pluck.data.MediaFormat
import xyz.ximanta.pluck.data.ResolveResponse
import xyz.ximanta.pluck.util.FileSaver
import xyz.ximanta.pluck.util.Formatters
import java.io.File

data class SelectedFormat(
    val format: MediaFormat,
    val mediaType: String
)

sealed class HomeResolveState {
    object Idle : HomeResolveState()
    object Loading : HomeResolveState()
    data class Success(val data: ResolveResponse) : HomeResolveState()
    data class Error(val message: String) : HomeResolveState()
}

sealed class HomeFetchState {
    object Idle : HomeFetchState()
    data class Downloading(
        val bytesRead: Long,
        val totalBytes: Long?,
        val percent: Int?,
        val label: String
    ) : HomeFetchState()
    data class Success(
        val file: File,
        val displayName: String,
        val savedAt: String
    ) : HomeFetchState()
    data class Error(val message: String) : HomeFetchState()
}

sealed class HomeHistoryState {
    object Idle : HomeHistoryState()
    object Loading : HomeHistoryState()
    data class Success(val entries: List<HistoryEntry>) : HomeHistoryState()
    data class Error(val message: String) : HomeHistoryState()
}

class HomeViewModel(
    private val context: Context,
    private val apiClient: ApiClient,
    private val authStore: AuthStore
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _resolveState = MutableStateFlow<HomeResolveState>(HomeResolveState.Idle)
    val resolveState: StateFlow<HomeResolveState> = _resolveState.asStateFlow()

    private val _selected = MutableStateFlow<SelectedFormat?>(null)
    val selected: StateFlow<SelectedFormat?> = _selected.asStateFlow()

    private val _fetchState = MutableStateFlow<HomeFetchState>(HomeFetchState.Idle)
    val fetchState: StateFlow<HomeFetchState> = _fetchState.asStateFlow()

    private val _historyState = MutableStateFlow<HomeHistoryState>(HomeHistoryState.Idle)
    val historyState: StateFlow<HomeHistoryState> = _historyState.asStateFlow()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    fun onUrlChange(value: String) {
        _urlInput.value = value
    }

    fun clearInput() {
        _urlInput.value = ""
    }

    fun setDrawerOpen(open: Boolean) {
        _drawerOpen.value = open
    }

    fun resolve() {
        val url = _urlInput.value.trim()
        if (url.isBlank()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _resolveState.value = HomeResolveState.Error("URL must start with http(s)://")
            return
        }
        viewModelScope.launch {
            _resolveState.value = HomeResolveState.Loading
            _selected.value = null
            _fetchState.value = HomeFetchState.Idle
            apiClient.resolve(url)
                .onSuccess { resp ->
                    if (!resp.hasAnyFormats) {
                        _resolveState.value = HomeResolveState.Error("No downloadable content found")
                    } else {
                        _resolveState.value = HomeResolveState.Success(resp)
                    }
                }
                .onFailure { _resolveState.value = HomeResolveState.Error(humanize(it)) }
        }
    }

    fun selectFormat(format: MediaFormat, mediaType: String) {
        _selected.value = SelectedFormat(format, mediaType)
        _fetchState.value = HomeFetchState.Idle
    }

    fun clearSelection() {
        _selected.value = null
    }

    fun download() {
        val resolve = (_resolveState.value as? HomeResolveState.Success)?.data ?: return
        val pick = _selected.value ?: return
        val format = pick.format

        val fallbackName = buildFilename(resolve, format, pick.mediaType)
        val destination = FileSaver.destinationFor(context, pick.mediaType, fallbackName)

        viewModelScope.launch {
            _fetchState.value = HomeFetchState.Downloading(0L, null, null, "Starting…")
            val request = FetchRequest(
                url = resolve.url,
                formatId = format.formatId.takeIf { it.isNotBlank() && format.directUrl.isBlank() },
                directUrl = format.directUrl.takeIf { it.isNotBlank() },
                mediaType = pick.mediaType,
                mergeAudio = format.note.contains("merge", ignoreCase = true),
                filename = format.filename.takeIf { it.isNotBlank() },
                title = resolve.title.takeIf { it.isNotBlank() },
                platform = resolve.platform.takeIf { it.isNotBlank() },
                thumbnail = resolve.thumbnail.takeIf { it.isNotBlank() },
                quality = format.quality.takeIf { it.isNotBlank() },
                ext = format.ext.takeIf { it.isNotBlank() },
                durationSec = resolve.durationSec
            )

            apiClient.fetch(request, destination) { bytes, total ->
                _fetchState.value = HomeFetchState.Downloading(
                    bytesRead = bytes,
                    totalBytes = total,
                    percent = Formatters.progressPercent(bytes, total),
                    label = labelFor(bytes, total)
                )
            }.onSuccess { result ->
                val display = result.file.name
                val location = FileSaver.displayPath(context, pick.mediaType)
                _fetchState.value = HomeFetchState.Success(
                    file = result.file,
                    displayName = display,
                    savedAt = location
                )
                refreshHistory()
            }.onFailure { e ->
                FileSaver.deleteQuietly(destination)
                _fetchState.value = HomeFetchState.Error(humanize(e))
            }
        }
    }

    fun dismissFetch() {
        if (_fetchState.value is HomeFetchState.Success ||
            _fetchState.value is HomeFetchState.Error
        ) {
            _fetchState.value = HomeFetchState.Idle
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _historyState.value = HomeHistoryState.Loading
            apiClient.history()
                .onSuccess { _historyState.value = HomeHistoryState.Success(it.entries) }
                .onFailure { _historyState.value = HomeHistoryState.Error(humanize(it)) }
        }
    }

    fun loadHistoryFromEntry(entry: HistoryEntry) {
        _urlInput.value = entry.url
        _resolveState.value = HomeResolveState.Idle
        _selected.value = null
        _fetchState.value = HomeFetchState.Idle
        _drawerOpen.value = false
    }

    private fun buildFilename(resolve: ResolveResponse, format: MediaFormat, mediaType: String): String {
        val title = resolve.title.ifBlank { "media" }
        if (format.filename.isNotBlank()) {
            return Formatters.safeFilename(format.filename)
        }
        val ext = when {
            format.ext.isNotBlank() -> format.ext
            mediaType == "audio" -> "m4a"
            mediaType == "video" -> "mp4"
            mediaType == "image" -> "jpg"
            mediaType == "archive" -> "zip"
            mediaType == "document" -> "pdf"
            else -> "bin"
        }
        val quality = format.quality.ifBlank { "" }
        val stem = if (quality.isBlank()) title else "$title - $quality"
        return Formatters.safeFilename("$stem.$ext", fallback = "download.$ext")
    }

    private fun labelFor(read: Long, total: Long?): String {
        val readStr = Formatters.humanBytes(read)
        return if (total != null && total > 0) "$readStr / ${Formatters.humanBytes(total)}"
        else readStr
    }

    private fun humanize(e: Throwable): String {
        val raw = e.message ?: "request failed"
        return when {
            e is ApiException && e.statusCode == 401 -> "Key invalid or expired"
            e is ApiException && e.statusCode == 429 -> "Too many requests, slow down"
            raw.contains("Unable to resolve host", ignoreCase = true) ||
                raw.contains("network", ignoreCase = true) -> "No internet connection"
            raw.contains("timeout", ignoreCase = true) -> "Network timeout"
            raw.contains("HTTP 502", ignoreCase = true) -> "Server busy, try again"
            raw.contains("HTTP 413", ignoreCase = true) -> "File too large"
            else -> raw
        }
    }

    companion object {
        fun factory(context: Context, apiClient: ApiClient, authStore: AuthStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(context.applicationContext, apiClient, authStore) as T
            }
    }
}
