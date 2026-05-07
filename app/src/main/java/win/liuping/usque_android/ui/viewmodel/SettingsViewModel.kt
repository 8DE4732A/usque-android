package win.liuping.usque_android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import win.liuping.usque_android.data.ConfigRepository
import win.liuping.usque_android.data.Settings
import win.liuping.usque_android.data.UsqueConfig
import win.liuping.usque_android.nativebridge.ErrorCode
import win.liuping.usque_android.nativebridge.UsqueException
import win.liuping.usque_android.nativebridge.UsqueNative

sealed interface EnrollUiState {
    data object Idle : EnrollUiState
    data object Loading : EnrollUiState
    data object Success : EnrollUiState
    data class Error(val message: String, val code: ErrorCode) : EnrollUiState
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConfigRepository(app)

    private val _settings = MutableStateFlow(repo.loadSettings())
    val settings: StateFlow<Settings> = _settings

    private val _config = MutableStateFlow(repo.loadConfig() ?: UsqueConfig())
    val config: StateFlow<UsqueConfig> = _config

    private val _enrollState = MutableStateFlow<EnrollUiState>(EnrollUiState.Idle)
    val enrollState: StateFlow<EnrollUiState> = _enrollState

    fun update(settings: Settings) {
        _settings.value = settings
        viewModelScope.launch { repo.saveSettings(settings) }
    }

    fun updateConfigField(key: String, value: String) {
        repo.updateConfigFields(mapOf(key to value))
        _config.value = repo.loadConfig() ?: _config.value
    }

    fun enroll(jwt: String = "") {
        viewModelScope.launch {
            _enrollState.value = EnrollUiState.Loading
            try {
                val configJson = repo.loadConfigJson()
                    ?: run {
                        _enrollState.value = EnrollUiState.Error("No config found", ErrorCode.UNKNOWN)
                        return@launch
                    }
                val enrolled = UsqueNative.enrollExisting(configJson, jwt)
                repo.saveConfig(enrolled)
                _config.value = repo.loadConfig() ?: _config.value
                _enrollState.value = EnrollUiState.Success
            } catch (e: UsqueException) {
                _enrollState.value = EnrollUiState.Error(e.message ?: "Unknown error", e.code)
            } catch (e: Exception) {
                _enrollState.value = EnrollUiState.Error(e.message ?: "Unknown error", ErrorCode.UNKNOWN)
            }
        }
    }

    fun resetEnrollState() {
        _enrollState.value = EnrollUiState.Idle
    }

    fun clearConfig() {
        repo.clearConfig()
    }
}
