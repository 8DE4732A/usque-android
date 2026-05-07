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
            .apply {
                // Route all IPv4 except the WARP endpoint to avoid routing loop
                val endpointIp = cfg.endpoint_v4.substringBefore(":")
                if (endpointIp.isNotEmpty()) {
                    splitRouteExcluding(endpointIp).forEach { (ip, prefix) ->
                        addRoute(ip, prefix)
                    }
                } else {
                    addRoute("0.0.0.0", 0)
                }
            }
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("2606:4700:4700::1111")
            .setMtu(settings.mtu)
            .setSession("Usque")
            .establish()
            ?.detachFd()
            ?: run {
                Log.e(TAG, "VpnService.establish() returned null")
                TunnelState.update("error:VPN establish failed")
                stopSelf()
                return START_NOT_STICKY
            }

        controller = UsqueNative.getController()
        TunnelState.update("connecting")

        scope.launch {
            try {
                val ctrl = controller ?: return@launch
                ctrl.startVpn(
                    configJson,
                    tunFd.toLong(),
                    settings.dnsAddrs,
                    settings.sni,
                    settings.mtu.toLong(),
                    settings.useIPv6,
                    settings.useHTTP2,
                )
                TunnelState.update("connected")
                // startVpn is non-blocking; block here until the tunnel goroutines exit
                ctrl.waitUntilStopped()
                Log.d(TAG, "waitUntilStopped returned")
            } catch (e: Exception) {
                Log.e(TAG, "VPN tunnel error: ${e.message}")
                TunnelState.update("error:${e.message}")
            } finally {
                Log.d(TAG, "finally: calling stopForeground+stopSelf")
                TunnelState.update("idle")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onRevoke() {
        // System revoked VPN permission — stop the tunnel, coroutine finally will clean up
        controller?.stop()
        super.onRevoke()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        // controller.stop() is called async from stopVpn(); here we just ensure
        // the reference is cleared. The coroutine finally block will call stopForeground.
        controller = null
        job.cancel()
        super.onDestroy()
    }

    private fun stopVpn() {
        Log.d(TAG, "stopVpn: signalling controller to stop")
        val ctrl = controller
        controller = null
        TunnelState.update("idle")
        // Run stop on IO thread — controller.stop() blocks up to 5s waiting for goroutines
        scope.launch {
            ctrl?.stop()
        }
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

        // Returns a list of (ip, prefixLen) covering 0.0.0.0/0 minus the single /32 excludeIp.
        // This is needed because Android VpnService has no excludeRoute API.
        fun splitRouteExcluding(excludeIp: String): List<Pair<String, Int>> {
            val parts = excludeIp.split(".").map { it.toIntOrNull() ?: return listOf("0.0.0.0" to 0) }
            if (parts.size != 4) return listOf("0.0.0.0" to 0)
            val excl = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
            val routes = mutableListOf<Pair<String, Int>>()
            var base = 0
            var bits = 0
            while (bits < 32) {
                val blockSize = 1 shl (32 - bits)
                if ((excl and (blockSize - 1).inv()) == base && bits < 32) {
                    // this block contains the excluded IP — drill down
                    bits++
                    val half = 1 shl (32 - bits)
                    val lo = base
                    val hi = base or half
                    if ((excl and half) == 0) {
                        // excluded IP in lo half — add hi half
                        routes.add(intToIp(hi) to bits)
                        base = lo
                    } else {
                        // excluded IP in hi half — add lo half
                        routes.add(intToIp(lo) to bits)
                        base = hi
                    }
                } else {
                    break
                }
            }
            // bits==32 means we've narrowed to the /32 itself — don't add it
            return routes
        }

        private fun intToIp(i: Int): String {
            return "${(i ushr 24) and 0xFF}.${(i ushr 16) and 0xFF}.${(i ushr 8) and 0xFF}.${i and 0xFF}"
        }
    }
}
