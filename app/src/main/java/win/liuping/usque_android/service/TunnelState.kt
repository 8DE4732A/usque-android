package win.liuping.usque_android.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object TunnelState {
    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status

    fun update(status: String) {
        _status.value = status
    }
}
