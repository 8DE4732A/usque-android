package win.liuping.usque_android.nativebridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UsqueNative {

    suspend fun ping(): String = withContext(Dispatchers.IO) {
        mobile.Mobile.ping()
    }

    suspend fun registerAccount(
        model: String,
        locale: String,
        jwt: String,
        acceptTos: Boolean,
    ): String = withContext(Dispatchers.IO) {
        try {
            mobile.Mobile.registerAccount(model, locale, jwt, acceptTos)
        } catch (e: Exception) {
            throw ErrorCodes.parse(e)
        }
    }

    suspend fun enrollDevice(accountJson: String, deviceName: String): String =
        withContext(Dispatchers.IO) {
            try {
                mobile.Mobile.enrollDevice(accountJson, deviceName)
            } catch (e: Exception) {
                throw ErrorCodes.parse(e)
            }
        }

    suspend fun enrollExisting(configJson: String, jwt: String = ""): String =
        withContext(Dispatchers.IO) {
            try {
                mobile.Mobile.enrollExisting(configJson, jwt)
            } catch (e: Exception) {
                throw ErrorCodes.parse(e)
            }
        }

    fun getController(): mobile.TunnelController = mobile.Mobile.newTunnelController()
}
