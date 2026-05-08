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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import win.liuping.usque_android.data.ConfigRepository
import win.liuping.usque_android.nativebridge.UsqueNative
import win.liuping.usque_android.ui.MainActivity
import java.net.InetSocketAddress
import java.net.Socket

class Tun2SocksVpnService : VpnService(), mobile.SocketProtector {

    override fun protect(fd: Long): Boolean = protect(fd.toInt())

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var socksController: mobile.TunnelController? = null
    private var tun2socksController: mobile.Tun2SocksController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        val repo = ConfigRepository(applicationContext)
        val configJson = repo.loadConfigJson() ?: run {
            Log.e(TAG, "No config found")
            TunnelState.update("error:No config found")
            stopSelf()
            return START_NOT_STICKY
        }
        val cfg = repo.loadConfig() ?: run {
            Log.e(TAG, "Failed to parse config")
            TunnelState.update("error:Failed to parse config")
            stopSelf()
            return START_NOT_STICKY
        }
        val settings = repo.loadSettings()

        // listenAddr is where usque SOCKS5 will bind locally.
        // tun2socks uses this as firstHop (loopback, never enters TUN).
        // usque's outbound MASQUE sockets are protected via SocketProtector.
        val socksListenAddr = settings.listenAddr.ifBlank { "127.0.0.1:1080" }
        val upstreamSocks5 = settings.upstreamSocks5.trim()

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
                TunnelState.update("error:VPN establish failed")
                stopSelf()
                return START_NOT_STICKY
            }

        TunnelState.update("connecting")

        scope.launch {
            try {
                // Step 1: start usque SOCKS5 in background (it blocks until stopped).
                val socks = UsqueNative.getController()
                socksController = socks
                launch {
                    try {
                        socks.startSocks(
                            configJson,
                            socksListenAddr,
                            settings.dnsAddrs,
                            settings.sni,
                            settings.mtu.toLong(),
                            settings.useIPv6,
                            settings.useHTTP2,
                            this@Tun2SocksVpnService,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "SOCKS5 error: ${e.message}")
                    }
                }

                // Step 2: wait until the SOCKS5 port is accepting connections.
                if (!waitForPort(socksListenAddr, timeoutMs = 10_000)) {
                    Log.e(TAG, "SOCKS5 did not start in time")
                    TunnelState.update("error:SOCKS5 startup timeout")
                    socks.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                // Step 3: start tun2socks.
                Log.d(TAG, "starting tun2socks: tunFd=$tunFd firstHop=$socksListenAddr upstream=$upstreamSocks5 ipv4=${cfg.ipv4} ipv6=${cfg.ipv6} mtu=${settings.mtu}")
                val t2s = UsqueNative.getTun2SocksController()
                tun2socksController = t2s
                t2s.startFull(
                    tunFd.toLong(),
                    cfg.ipv4,
                    cfg.ipv6,
                    settings.dnsAddrs,
                    socksListenAddr,
                    upstreamSocks5,
                    settings.mtu.toLong(),
                )
                Log.d(TAG, "tun2socks startFull returned (non-blocking)")

                TunnelState.update("connected")
                t2s.waitUntilStopped()
                Log.d(TAG, "tun2socks stopped")
            } catch (e: Exception) {
                Log.e(TAG, "tunnel error: ${e.message}")
                TunnelState.update("error:${e.message}")
            } finally {
                socksController?.stop()
                TunnelState.update("idle")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onRevoke() {
        tun2socksController?.stop()
        socksController?.stop()
        super.onRevoke()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        tun2socksController = null
        socksController = null
        job.cancel()
        super.onDestroy()
    }

    private fun stopTunnel() {
        Log.d(TAG, "stopTunnel")
        val t2s = tun2socksController
        val socks = socksController
        tun2socksController = null
        socksController = null
        TunnelState.update("idle")
        scope.launch {
            t2s?.stop()
            socks?.stop()
        }
    }

    // Polls host:port until connectable or timeout.
    private suspend fun waitForPort(addr: String, timeoutMs: Long): Boolean =
        withContext(Dispatchers.IO) {
            val parts = addr.split(":")
            val host = parts.dropLast(1).joinToString(":")
            val port = parts.last().toIntOrNull() ?: return@withContext false
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(host.trimStart('[').trimEnd(']'), port), 200)
                    }
                    return@withContext true
                } catch (_: Exception) {
                    delay(200)
                }
            }
            false
        }

    private fun buildNotification() = run {
        val channelId = createNotificationChannel()
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, Tun2SocksVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val settings = ConfigRepository(this).loadSettings()
        val subtitle = if (settings.upstreamSocks5.isNotBlank())
            "→ ${settings.upstreamSocks5}"
        else
            "via WARP"
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Usque VPN")
            .setContentText(subtitle)
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
        private const val TAG = "Tun2SocksVpnService"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_STOP = "win.liuping.usque_android.STOP_VPN"
    }
}
