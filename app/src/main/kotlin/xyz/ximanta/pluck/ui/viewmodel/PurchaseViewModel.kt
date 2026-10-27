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
import xyz.ximanta.pluck.data.*
import java.io.File

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
    data class Verified(val key: String, val planName: String, val expiresAt: Long) : VerifyState()
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
                .onFailure { _plansState.value = PlansState.Error(it.message ?: "Error loading plans") }
        }
    }

    fun selectPlan(plan: Plan) {
        _selectedPlan.value = plan
        resetQr()
    }

    fun requestQr(name: String) {
        val plan = _selectedPlan.value ?: return
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            _qrState.value = QrState.Loading
            _verifyState.value = VerifyState.Idle
            apiClient.requestQR(plan.id, cleanName)
                .onSuccess { resp ->
                    currentFinalAmount = resp.finalAmount
                    _qrState.value = QrState.Ready(resp)
                    startCountdown(resp.expiresIn)
                }
                .onFailure { _qrState.value = QrState.Error(it.message ?: "Error") }
        }
    }

    fun verify() {
        if (currentFinalAmount.isBlank()) return
        viewModelScope.launch {
            _verifyState.value = VerifyState.Checking
            apiClient.verify(currentFinalAmount)
                .onSuccess { resp ->
                    if (resp.verified && !resp.key.isNullOrBlank()) {
                        val key = resp.key
                        val planName = resp.planName ?: _selectedPlan.value?.name ?: ""
                        val expires = resp.expiresAt
                        authStore.save(key, "", planName, expires)
                        countdownJob?.cancel()
                        _verifyState.value = VerifyState.Verified(key, planName, expires)
                    } else if (resp.status == "not_found") {
                        _verifyState.value = VerifyState.NotFound(resp.message ?: "Wait and try again")
                    } else {
                        _verifyState.value = VerifyState.Error(resp.message ?: "Status: ${resp.status}")
                    }
                }
                .onFailure { _verifyState.value = VerifyState.Error(it.message ?: "Error") }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        _remainingSeconds.value = seconds
        countdownJob = viewModelScope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000)
                _remainingSeconds.value = (_remainingSeconds.value - 1).coerceAtLeast(0)
            }
            if (_qrState.value is QrState.Ready) _qrState.value = QrState.Expired(currentFinalAmount)
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

    companion object {
        fun factory(apiClient: ApiClient, authStore: AuthStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = PurchaseViewModel(apiClient, authStore) as T
            }
    }
}
