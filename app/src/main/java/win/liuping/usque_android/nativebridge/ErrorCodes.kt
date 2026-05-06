package win.liuping.usque_android.nativebridge

class UsqueException(message: String, val code: ErrorCode) : Exception(message)

enum class ErrorCode {
    TOS_NOT_ACCEPTED,
    INVALID_PUBKEY,
    NETWORK,
    AUTH,
    CONFIG,
    UNKNOWN,
}

object ErrorCodes {
    fun parse(e: Exception): UsqueException {
        val msg = e.message ?: return UsqueException(e.toString(), ErrorCode.UNKNOWN)
        val code = when {
            msg.startsWith("USQUE_ERR_TOS_NOT_ACCEPTED") -> ErrorCode.TOS_NOT_ACCEPTED
            msg.startsWith("USQUE_ERR_INVALID_PUBKEY") -> ErrorCode.INVALID_PUBKEY
            msg.startsWith("USQUE_ERR_NETWORK") -> ErrorCode.NETWORK
            msg.startsWith("USQUE_ERR_AUTH") -> ErrorCode.AUTH
            msg.startsWith("USQUE_ERR_CONFIG") -> ErrorCode.CONFIG
            else -> ErrorCode.UNKNOWN
        }
        return UsqueException(msg, code)
    }
}
