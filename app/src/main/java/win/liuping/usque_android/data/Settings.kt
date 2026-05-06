package win.liuping.usque_android.data

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val sni: String = "",
    val useIPv6: Boolean = false,
    val useHTTP2: Boolean = false,
    val mtu: Int = 1280,
    val dnsAddrs: String = "1.1.1.1,1.0.0.1",
    val listenAddr: String = "127.0.0.1:1080",
)
