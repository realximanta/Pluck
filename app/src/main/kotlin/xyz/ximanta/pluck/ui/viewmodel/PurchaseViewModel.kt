package xyz.ximanta.pluck.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.ximanta.pluck.data.ApiClient
import xyz.ximanta.pluck.data.ApiException
import xyz.ximanta.pluck.data.AuthStore
import xyz.ximanta.pluck.data.Plan
import xyz.ximanta.pluck.data.QRResponse
import xyz.ximanta.pluck.data.VerifyResponse

sealed class PlansState {
    object Idle : PlansState()
    object Loading : PlansState()
    data class Success(val plans: List<Plan>) : PlansState()
    data class Error(val message: String) : PlansState()
}

sealed class QrState {
    object Idle : QrState()
    object Loading : QrState()
    data class Ready(val qr: QRResponse) : QrState()
    data class Expired(val finalAmount: String) : QrState()
    data class Error(val message: String) : QrState()
}

sealed class VerifyState {
    object Idle : VerifyState()
    object Checking : VerifyState()
    data class NotFound(val message: String) : VerifyState()
    data class Verified(
        val key: String,
        val planName: String,
        val expiresAt: Long
    ) : VerifyState()
    data class Error(val message: String) : VerifyState()
}

class PurchaseViewModel(
    private val apiClient: ApiClient,
    private val authStore: AuthStore
) : ViewModel() {

    private val _plansState = MutableStateFlow<PlansState>(PlansState.Idle)
    val plansState: StateFlow<PlansState> = _plansState.asStateFlow()

    private val _selectedPlan = MutableStateFlow<Plan?>(null)
    val selectedPlan: StateFlow<Plan?> = _selectedPlan.asStateFlow()

    private val _qrState = MutableStateFlow<QrState>(QrState.Idle)
    val qrState: StateFlow<QrState> = _qrState.asStateFlow()

    private val _verifyState = MutableStateFlow<VerifyState>(VerifyState.Idle)
    val verifyState: StateFlow<VerifyState> = _verifyState.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private var countdownJob: Job? = null
    private var currentFinalAmount: String = ""

    fun loadPlans() {
        if (_plansState.value is PlansState.Success) return
        viewModelScope.launch {
            _plansState.value = PlansState.Loading
            apiClient.plans()
                .onSuccess { _plansState.value = PlansState.Success(it) }
                .onFailure { _plansState.value = PlansState.Error(humanize(it)) }
        }
    }

    fun selectPlan(plan: Plan) {
        _selectedPlan.value = plan
        resetQr()
    }

    fun requestQr(name: String) {
        val plan = _selectedPlan.value ?: return
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _qrState.value = QrState.Error("Enter your name")
            return
        }
        viewModelScope.launch {
            _qrState.value = QrState.Loading
            _verifyState.value = VerifyState.Idle
            apiClient.requestQR(plan.id, cleanName)
                .onSuccess { resp ->
                    currentFinalAmount = resp.finalAmount
                    _qrState.value = QrState.Ready(resp)
                    startCountdown(resp.expiresIn)
                }
                .onFailure { _qrState.value = QrState.Error(humanize(it)) }
        }
    }

    fun verify() {
        if (currentFinalAmount.isBlank()) return
        viewModelScope.launch {
            _verifyState.value = VerifyState.Checking
            apiClient.verify(currentFinalAmount)
                .onSuccess { resp -> handleVerifyResponse(resp) }
                .onFailure { e ->
                    _verifyState.value = if (isRateLimited(e)) {
                        VerifyState.NotFound("Too many checks. Wait a moment.")
                    } else {
                        VerifyState.Error(humanize(e))
                    }
                }
        }
    }

    private suspend fun handleVerifyResponse(resp: VerifyResponse) {
        when {
            resp.verified && !resp.key.isNullOrBlank() -> {
                val key = resp.key
                val planName = resp.planName ?: _selectedPlan.value?.name ?: ""
                val expires = resp.expiresAt
                authStore.save(
                    key = key,
                    name = "",
                    planName = planName,
                    expiresAt = expires
                )
                countdownJob?.cancel()
                _verifyState.value = VerifyState.Verified(key, planName, expires)
            }
            resp.status == "not_found" -> {
                _verifyState.value = VerifyState.NotFound(
                    resp.message ?: "Payment not detected yet. Wait and try again."
                )
            }
            resp.status == "expired" -> {
                _qrState.value = QrState.Expired(currentFinalAmount)
                _verifyState.value = VerifyState.Error("QR expired — generate a new one")
            }
            resp.status == "rate_limited" -> {
                _verifyState.value = VerifyState.NotFound(
                    "Too many checks. Wait ${resp.retryAfterSeconds.coerceAtLeast(30)}s."
                )
            }
            else -> {
                _verifyState.value = VerifyState.Error(
                    resp.message ?: "Status: ${resp.status.ifBlank { "unknown" }}"
                )
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        _remainingSeconds.value = seconds.coerceAtLeast(0)
        countdownJob = viewModelScope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1_000)
                _remainingSeconds.value = (_remainingSeconds.value - 1).coerceAtLeast(0)
            }
            if (_qrState.value is QrState.Ready) {
                _qrState.value = QrState.Expired(currentFinalAmount)
            }
        }
    }

    fun resetQr() {
        countdownJob?.cancel()
        currentFinalAmount = ""
        _remainingSeconds.value = 0
        _qrState.value = QrState.Idle
        _verifyState.value = VerifyState.Idle
    }

    fun clearAll() {
        resetQr()
        _selectedPlan.value = null
    }

    private fun isRateLimited(e: Throwable): Boolean {
        val msg = e.message ?: return false
        return msg.contains("rate_limited", ignoreCase = true) ||
            (e is ApiException && e.statusCode == 429)
    }

    private fun humanize(e: Throwable): String {
        val raw = e.message ?: "request failed"
        return when {
            raw.contains("Unable to resolve host", ignoreCase = true) ||
                raw.contains("network", ignoreCase = true) -> "No internet connection"
            raw.contains("timeout", ignoreCase = true) -> "Network timeout"
            raw.contains("HTTP 502", ignoreCase = true) -> "Server busy, try again"
            else -> raw
        }
    }

    companion object {
        fun factory(apiClient: ApiClient, authStore: AuthStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PurchaseViewModel(apiClient, authStore) as T
            }
    }
}
