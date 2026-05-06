package win.liuping.usque_android.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkMonitor(
    context: Context,
    private val scope: CoroutineScope,
    private val onNetworkChanged: () -> Unit,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private var debounceJob: Job? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = notifyChanged()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            notifyChanged()
    }

    fun register() {
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(req, callback)
        Log.d(TAG, "NetworkMonitor registered")
    }

    fun unregister() {
        cm.unregisterNetworkCallback(callback)
        Log.d(TAG, "NetworkMonitor unregistered")
    }

    private fun notifyChanged() {
        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.Main) {
            delay(500)
            Log.d(TAG, "Network changed — notifying tunnel")
            onNetworkChanged()
        }
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
