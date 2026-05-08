package win.liuping.usque_android.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

@Serializable
data class IpGeoInfo(
    val ip: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val status: String = "success",
    val message: String = "",
) {
    val latitude: Double get() = lat
    val longitude: Double get() = lon
}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class IpApiResponse(
    val status: String = "success",
    val message: String = "",
    val country: String = "",
    val regionName: String = "",
    val city: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val query: String = "",
)

// ip-api.com uses HTTP only on free tier. We call via direct IP to bypass broken DNS.
// The server IP (208.95.112.1) is stable — it's the only A record for ip-api.com.
private const val IP_API_HOST = "ip-api.com"
private const val IP_API_IP   = "208.95.112.1"

suspend fun fetchIpGeo(ip: String = ""): IpGeoInfo? = withContext(Dispatchers.IO) {
    val target = if (ip.isBlank()) "" else "/$ip"
    val fields = "status,message,country,regionName,city,lat,lon,query"
    // Use direct IP + Host header to bypass VPN's broken UDP DNS
    val url = "http://$IP_API_IP/json$target?fields=$fields"
    try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("Host", IP_API_HOST)
        conn.setRequestProperty("User-Agent", "UsqueVPN/1.0")
        val code = conn.responseCode
        val body = if (code == 200) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            Log.w("IpGeo", "HTTP $code: $err")
            conn.disconnect()
            return@withContext null
        }
        conn.disconnect()
        Log.d("IpGeo", "response: ${body.take(200)}")
        val r = json.decodeFromString<IpApiResponse>(body)
        if (r.status == "fail") {
            Log.w("IpGeo", "api fail: ${r.message}")
            return@withContext null
        }
        IpGeoInfo(ip = r.query, city = r.city, region = r.regionName, country = r.country, lat = r.lat, lon = r.lon)
    } catch (e: Exception) {
        Log.e("IpGeo", "${e.javaClass.simpleName}: ${e.message}")
        null
    }
}
