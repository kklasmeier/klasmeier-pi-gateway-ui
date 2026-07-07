package com.klasmeier.internetgatewaypath.data.api

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class IpInfoResult(
    val ip: String?,
    val city: String?,
    val region: String?,
    val country: String?,
    val latitude: Double?,
    val longitude: Double?,
)

class IpInfoClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    fun fetch(token: String?): IpInfoResult {
        val builder = Request.Builder().url("https://ipinfo.io/json")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("ipinfo HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val loc = json.optString("loc")
            val latLng = loc.split(",").map { it.trim() }
            return IpInfoResult(
                ip = json.optString("ip").ifBlank { null },
                city = json.optString("city").ifBlank { null },
                region = json.optString("region").ifBlank { null },
                country = json.optString("country").ifBlank { null },
                latitude = latLng.getOrNull(0)?.toDoubleOrNull(),
                longitude = latLng.getOrNull(1)?.toDoubleOrNull(),
            )
        }
    }
}
