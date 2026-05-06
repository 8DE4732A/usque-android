package win.liuping.usque_android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import win.liuping.usque_android.data.ConfigRepository
import win.liuping.usque_android.nativebridge.UsqueNative
import win.liuping.usque_android.ui.MainActivity

class UsqueVpnService : VpnService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var controller: mobile.TunnelController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        val repo = ConfigRepository(applicationContext)
        val configJson = repo.loadConfigJson() ?: run {
            Log.e(TAG, "No config found")
            stopSelf()
            return START_NOT_STICKY
        }
        val settings = repo.loadSettings()
        val cfg = repo.loadConfig() ?: run {
            Log.e(TAG, "Failed to parse config")
            stopSelf()
            return START_NOT_STICKY
        }

        val tunFd = Builder()
            .addAddress(cfg.ipv4, 32)
            .addAddress(cfg.ipv6, 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("2606:4700:4700::1111")
            .setMtu(settings.mtu)
            .setSession("Usque")
            .establish()
            ?.detachFd()
            ?: run {
                Log.e(TAG, "VpnService.establish() returned null")
                stopSelf()
                return START_NOT_STICKY
            }

        controller = UsqueNative.getController()

        scope.launch {
            try {
                controller?.startVpn(
                    configJson,
                    tunFd,
                    settings.dnsAddrs,
                    settings.sni,
                    settings.mtu,
                    settings.useIPv6,
                    settings.useHTTP2,
                )
            } catch (e: Exception) {
                Log.e(TAG, "VPN tunnel error: ${e.message}")
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        controller?.stop()
        job.cancel()
        super.onDestroy()
    }

    private fun stopVpn() {
        controller?.stop()
        controller = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification() = run {
        val channelId = createNotificationChannel()
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, UsqueVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Usque VPN")
            .setContentText("WARP MASQUE tunnel active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
    }

    private fun createNotificationChannel(): String {
        val id = "usque_vpn"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(id, "VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return id
    }

    companion object {
        private const val TAG = "UsqueVpnService"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_STOP = "win.liuping.usque_android.STOP_VPN"
    }
}
