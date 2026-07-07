package com.klasmeier.internetgatewaypath.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

class NetworkChangeMonitor(
    context: Context,
    private val onNetworkChange: () -> Unit,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = schedule()
        override fun onLost(network: Network) = schedule()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = schedule()
    }

    fun register() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, callback)
    }

    fun unregister() {
        pending?.let(handler::removeCallbacks)
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
    }

    private fun schedule() {
        pending?.let(handler::removeCallbacks)
        pending = Runnable { onNetworkChange() }.also {
            handler.postDelayed(it, DEBOUNCE_MS)
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 1500L
    }
}
