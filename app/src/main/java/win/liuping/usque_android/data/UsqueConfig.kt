package win.liuping.usque_android.data

import kotlinx.serialization.Serializable

@Serializable
data class UsqueConfig(
    val private_key: String = "",
    val endpoint_v4: String = "",
    val endpoint_v6: String = "",
    val endpoint_h2_v4: String = "",
    val endpoint_h2_v6: String = "",
    val endpoint_pub_key: String = "",
    val license: String = "",
    val id: String = "",
    val access_token: String = "",
    val ipv4: String = "",
    val ipv6: String = "",
)
