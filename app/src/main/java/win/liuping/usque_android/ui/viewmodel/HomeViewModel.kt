package win.liuping.usque_android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import win.liuping.usque_android.data.ConfigRepository
import win.liuping.usque_android.service.ServiceMode
import win.liuping.usque_android.service.TunnelState

enum class TunnelStatus { IDLE, CONNECTING, CONNECTED, RECONNECTING, ERROR }

data class HomeUiState(
    val status: TunnelStatus = TunnelStatus.IDLE,
    val mode: ServiceMode = ServiceMode.SOCKS5,
    val ipv4: String = "",
    val ipv6: String = "",
    val errorMessage: String = "",
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConfigRepository(app)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        val cfg = repo.loadConfig()
        if (cfg != null) {
            _state.value = _state.value.copy(ipv4 = cfg.ipv4, ipv6 = cfg.ipv6)
        }
        viewModelScope.launch {
            TunnelState.status.collect { raw ->
                val status = when {
                    raw == "idle" -> TunnelStatus.IDLE
                    raw == "connecting" -> TunnelStatus.CONNECTING
                    raw == "connected" -> TunnelStatus.CONNECTED
                    raw == "reconnecting" -> TunnelStatus.RECONNECTING
                    raw.startsWith("error:") -> TunnelStatus.ERROR
                    else -> TunnelStatus.IDLE
                }
                val err = if (status == TunnelStatus.ERROR) raw.removePrefix("error:") else ""
                _state.value = _state.value.copy(status = status, errorMessage = err)
            }
        }
    }

    fun setMode(mode: ServiceMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setIdle() {
        _state.value = _state.value.copy(status = TunnelStatus.IDLE, errorMessage = "")
    }

    fun hasConfig(): Boolean = repo.hasConfig()
}
