package win.liuping.usque_android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import win.liuping.usque_android.data.ConfigRepository
import win.liuping.usque_android.data.IpGeoInfo
import win.liuping.usque_android.data.fetchIpGeo
import win.liuping.usque_android.service.TunnelState

enum class TunnelStatus { IDLE, CONNECTING, CONNECTED, RECONNECTING, ERROR }

data class HomeUiState(
    val status: TunnelStatus = TunnelStatus.IDLE,
    val ipv4: String = "",
    val ipv6: String = "",
    val errorMessage: String = "",
    val localGeo: IpGeoInfo? = null,
    val proxyGeo: IpGeoInfo? = null,
    val localGeoLoading: Boolean = false,
    val proxyGeoLoading: Boolean = false,
    val readyToConnect: Boolean = false,
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
        // Fetch local IP immediately on launch
        viewModelScope.launch { fetchLocalGeoAndReady() }
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

                if (status == TunnelStatus.CONNECTED) {
                    loadProxyGeo()
                } else if (status == TunnelStatus.IDLE || status == TunnelStatus.ERROR) {
                    // Clear proxy geo, keep local geo; re-fetch local in case IP changed
                    _state.value = _state.value.copy(
                        proxyGeo = null,
                        readyToConnect = false,
                    )
                    viewModelScope.launch { fetchLocalGeoAndReady() }
                }
            }
        }
    }

    // Fetches local IP geo. Suspends until done (so VPN launch waits for it).
    suspend fun fetchLocalGeoAndReady() {
        _state.value = _state.value.copy(localGeoLoading = true)
        val geo = fetchIpGeo()
        _state.value = _state.value.copy(localGeo = geo, localGeoLoading = false, readyToConnect = true)
    }

    // Called after VPN is connected — fetch proxy/exit IP through VPN.
    private fun loadProxyGeo() {
        viewModelScope.launch {
            _state.value = _state.value.copy(proxyGeoLoading = true)
            val proxyGeo = fetchIpGeo()
            _state.value = _state.value.copy(proxyGeo = proxyGeo, proxyGeoLoading = false)
        }
    }

    fun setIdle() {
        _state.value = _state.value.copy(
            status = TunnelStatus.IDLE,
            errorMessage = "",
            proxyGeo = null,
            readyToConnect = false,
        )
    }

    fun hasConfig(): Boolean = repo.hasConfig()
}
