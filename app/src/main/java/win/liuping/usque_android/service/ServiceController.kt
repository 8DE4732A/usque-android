package win.liuping.usque_android.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object ServiceController {

    fun startSocks(context: Context) {
        val intent = Intent(context, SocksProxyService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopSocks(context: Context) {
        val intent = Intent(context, SocksProxyService::class.java)
            .setAction(SocksProxyService.ACTION_STOP)
        context.startService(intent)
    }

    fun stopVpn(context: Context) {
        val intent = Intent(context, UsqueVpnService::class.java)
            .setAction(UsqueVpnService.ACTION_STOP)
        context.startService(intent)
    }

    fun startVpn(context: Context) {
        val intent = Intent(context, UsqueVpnService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }
}
