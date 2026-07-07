package com.klasmeier.internetgatewaypath.data.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ClientPathResult(
    val connection: String,
    val policy: String,
    val expectedPath: String,
    val deviceName: String?,
    val homeIp: String?,
    val obscuraIp: String?,
)

data class EgressResult(
    val homeIp: String?,
    val obscuraIp: String?,
)

class GatewayClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val probeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build(),
) {
    fun fetchClientPath(gatewayUrl: String, token: String): ClientPathResult? {
        return getJson("$gatewayUrl/api/client-path", token)?.let { json ->
            val ref = json.optJSONObject("reference")
            ClientPathResult(
                connection = json.optString("connection"),
                policy = json.optString("policy"),
                expectedPath = json.optString("expected_path"),
                deviceName = json.optString("device_name").ifBlank { null },
                homeIp = ref?.optString("home_ip")?.ifBlank { null },
                obscuraIp = ref?.optString("obscura_ip")?.ifBlank { null },
            )
        }
    }

    fun fetchEgress(gatewayUrl: String, token: String, refresh: Boolean = true): EgressResult? {
        val suffix = if (refresh) "?refresh=true" else ""
        return getJson("$gatewayUrl/api/egress$suffix", token)?.let { json ->
            EgressResult(
                homeIp = json.optJSONObject("local")?.optString("ip")?.ifBlank { null },
                obscuraIp = json.optJSONObject("vpn")?.optString("ip")?.ifBlank { null },
            )
        }
    }

    fun isReachable(gatewayUrl: String, token: String): Boolean {
        val request = Request.Builder()
            .url("$gatewayUrl/api/status")
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            probeClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun getJson(url: String, token: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                JSONObject(response.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            null
        }
    }
}
