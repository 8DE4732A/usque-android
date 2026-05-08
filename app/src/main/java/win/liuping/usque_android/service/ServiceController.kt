package win.liuping.usque_android.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object ServiceController {

    fun startTun2Socks(context: Context) {
        val intent = Intent(context, Tun2SocksVpnService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopTun2Socks(context: Context) {
        val intent = Intent(context, Tun2SocksVpnService::class.java)
            .setAction(Tun2SocksVpnService.ACTION_STOP)
        context.startService(intent)
    }
}
