package win.liuping.usque_android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import win.liuping.usque_android.data.ConfigRepository
import win.liuping.usque_android.nativebridge.UsqueNative
import win.liuping.usque_android.ui.MainActivity

class SocksProxyService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var controller: mobile.TunnelController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
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

        controller = UsqueNative.getController()
        TunnelState.update("connecting")

        scope.launch {
            try {
                TunnelState.update("connected")
                controller?.startSocks(
                    configJson,
                    settings.listenAddr,
                    settings.dnsAddrs,
                    settings.sni,
                    settings.mtu.toLong(),
                    settings.useIPv6,
                    settings.useHTTP2,
                )
            } catch (e: Exception) {
                Log.e(TAG, "SOCKS5 tunnel error: ${e.message}")
                TunnelState.update("error:${e.message}")
            } finally {
                TunnelState.update("idle")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun stopTunnel() {
        controller?.stop()
        controller = null
        TunnelState.update("idle")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        controller?.stop()
        TunnelState.update("idle")
        job.cancel()
        super.onDestroy()
    }

    private fun buildNotification() = run {
        val channelId = createNotificationChannel()
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SocksProxyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Usque SOCKS5 Proxy")
            .setContentText("Listening on ${ConfigRepository(this).loadSettings().listenAddr}")
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
        val id = "usque_socks"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(id, "SOCKS5 Proxy", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return id
    }

    companion object {
        private const val TAG = "SocksProxyService"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "win.liuping.usque_android.STOP_SOCKS"
    }
}
