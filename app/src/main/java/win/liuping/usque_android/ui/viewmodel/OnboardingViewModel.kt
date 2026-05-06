package win.liuping.usque_android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import win.liuping.usque_android.data.ConfigRepository
import win.liuping.usque_android.nativebridge.ErrorCode
import win.liuping.usque_android.nativebridge.UsqueException
import win.liuping.usque_android.nativebridge.UsqueNative

sealed interface OnboardingUiState {
    data object Idle : OnboardingUiState
    data object Loading : OnboardingUiState
    data object Success : OnboardingUiState
    data class Error(val message: String, val code: ErrorCode) : OnboardingUiState
}

class OnboardingViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConfigRepository(app)

    private val _state = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val state: StateFlow<OnboardingUiState> = _state

    fun register(jwt: String = "", acceptTos: Boolean = false) {
        if (!acceptTos) {
            _state.value = OnboardingUiState.Error(
                "You must accept the Terms of Service",
                ErrorCode.TOS_NOT_ACCEPTED,
            )
            return
        }
        viewModelScope.launch {
            _state.value = OnboardingUiState.Loading
            try {
                val accountJson = UsqueNative.registerAccount("PC", "en_US", jwt, true)
                val configJson = UsqueNative.enrollDevice(accountJson, "Android")
                repo.saveConfig(configJson)
                _state.value = OnboardingUiState.Success
            } catch (e: UsqueException) {
                _state.value = OnboardingUiState.Error(e.message ?: "Unknown error", e.code)
            } catch (e: Exception) {
                _state.value = OnboardingUiState.Error(e.message ?: "Unknown error", ErrorCode.UNKNOWN)
            }
        }
    }

    fun resetState() {
        _state.value = OnboardingUiState.Idle
    }
}
