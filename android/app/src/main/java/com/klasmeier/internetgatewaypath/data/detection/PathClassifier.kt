package com.klasmeier.internetgatewaypath.data.detection

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.klasmeier.internetgatewaypath.data.InternetPath
import com.klasmeier.internetgatewaypath.data.PathCheckResult
import com.klasmeier.internetgatewaypath.data.ReferenceIps
import com.klasmeier.internetgatewaypath.data.api.ClientPathResult
import com.klasmeier.internetgatewaypath.data.api.IpInfoResult
import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkContext(
    val wifiSsid: String?,
    val localIp: String?,
    val onHomeLan: Boolean,
    val gatewayMatches: Boolean,
    val vpnActive: Boolean,
    val gatewayReachable: Boolean,
) {
    /** True when the gateway LAN API could plausibly be reachable (home WiFi or home VPN). */
    val mightReachGateway: Boolean
        get() = vpnActive || onHomeLan
}

class PathClassifier {
    fun probeLocalNetwork(context: Context, homeSsid: String?): NetworkContext {
        val wifiSsid = currentWifiSsid(context)
        val localIp = primaryIpv4()
        val onHomeLanIp = localIp?.startsWith("192.168.1.") == true
        val ssidMatches = !homeSsid.isNullOrBlank() &&
            wifiSsid?.equals(homeSsid, ignoreCase = true) == true
        val onHomeLan = onHomeLanIp && (homeSsid.isNullOrBlank() || ssidMatches)
        return NetworkContext(
            wifiSsid = wifiSsid,
            localIp = localIp,
            onHomeLan = onHomeLan,
            gatewayMatches = onHomeLan,
            vpnActive = isVpnActive(context),
            gatewayReachable = false,
        )
    }

    fun buildNetworkContext(
        context: Context,
        homeSsid: String?,
        gatewayIp: String?,
        gatewayReachable: Boolean,
    ): NetworkContext {
        val wifiSsid = currentWifiSsid(context)
        val localIp = primaryIpv4()
        val onHomeLan = localIp?.startsWith("192.168.1.") == true
        val ssidMatches = !homeSsid.isNullOrBlank() &&
            wifiSsid?.equals(homeSsid, ignoreCase = true) == true
        return NetworkContext(
            wifiSsid = wifiSsid,
            localIp = localIp,
            onHomeLan = onHomeLan && (homeSsid.isNullOrBlank() || ssidMatches),
            gatewayMatches = !gatewayIp.isNullOrBlank() && onHomeLan,
            vpnActive = isVpnActive(context),
            gatewayReachable = gatewayReachable,
        )
    }

    fun classify(
        ipInfo: IpInfoResult,
        reference: ReferenceIps,
        network: NetworkContext,
        clientPath: ClientPathResult?,
    ): PathCheckResult {
        val publicIp = ipInfo.ip
        val location = listOfNotNull(ipInfo.city, ipInfo.region, ipInfo.country)
            .joinToString(", ")
            .ifBlank { null }

        val homeIp = reference.homeIp ?: clientPath?.homeIp
        val obscuraIp = reference.obscuraIp ?: clientPath?.obscuraIp

        val path = when {
            publicIp != null && obscuraIp != null && publicIp == obscuraIp -> InternetPath.OBSCURA
            publicIp != null && homeIp != null && publicIp == homeIp &&
                network.onHomeLan && network.gatewayReachable -> InternetPath.HOME
            publicIp != null && homeIp != null && publicIp == homeIp &&
                network.vpnActive && network.gatewayReachable -> InternetPath.OBSCURA
            publicIp != null && homeIp != null && publicIp == homeIp -> InternetPath.PHONE
            publicIp != null && homeIp != null && publicIp != homeIp &&
                obscuraIp != null && publicIp != obscuraIp -> InternetPath.PHONE
            publicIp != null && !network.mightReachGateway -> InternetPath.PHONE
            else -> InternetPath.UNKNOWN
        }

        val expectedPath = clientPath?.expectedPath
        val observed = when (path) {
            InternetPath.OBSCURA -> "obscura"
            InternetPath.HOME -> "home"
            InternetPath.PHONE -> "phone"
            else -> "unknown"
        }
        val policyMismatch = expectedPath != null &&
            expectedPath != "unknown" &&
            ((expectedPath == "obscura" && path != InternetPath.OBSCURA) ||
                (expectedPath == "home" && path != InternetPath.HOME))

        val details = buildList {
            if (network.vpnActive) add("VPN transport active")
            network.wifiSsid?.let { add("WiFi: $it") }
            network.localIp?.let { add("Local IP: $it") }
            if (network.gatewayReachable) add("Gateway reachable") else add("Gateway not reachable")
            clientPath?.connection?.let { add("Gateway sees: $it") }
            clientPath?.policy?.let { add("Policy: $it") }
            if (policyMismatch) add("Expected $expectedPath, seeing $observed")
        }

        return PathCheckResult(
            path = path,
            publicIp = publicIp,
            location = location,
            latitude = ipInfo.latitude,
            longitude = ipInfo.longitude,
            connectionDetail = details,
            expectedPath = expectedPath,
            policyMismatch = policyMismatch,
            checkedAtEpochMs = System.currentTimeMillis(),
        )
    }

    fun failed(message: String, previous: PathCheckResult? = null): PathCheckResult {
        return PathCheckResult(
            path = InternetPath.CHECK_FAILED,
            publicIp = previous?.publicIp,
            location = previous?.location,
            latitude = previous?.latitude,
            longitude = previous?.longitude,
            connectionDetail = listOf(message),
            expectedPath = previous?.expectedPath,
            policyMismatch = false,
            checkedAtEpochMs = System.currentTimeMillis(),
            errorMessage = message,
        )
    }

    private fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    @Suppress("DEPRECATION")
    private fun currentWifiSsid(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        val ssid = wifi.connectionInfo?.ssid ?: return null
        return ssid.trim('"').ifBlank { null }
    }

    private fun primaryIpv4(): String? {
        return NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }
}
