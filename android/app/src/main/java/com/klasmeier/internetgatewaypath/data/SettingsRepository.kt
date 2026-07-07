package com.klasmeier.internetgatewaypath.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val GATEWAY_URL = stringPreferencesKey("gateway_url")
        val TOKEN = stringPreferencesKey("token")
        val HOME_SSID = stringPreferencesKey("home_ssid")
        val GATEWAY_IP = stringPreferencesKey("gateway_ip")
        val IPINFO_TOKEN = stringPreferencesKey("ipinfo_token")
        val DEVICE_LABEL = stringPreferencesKey("device_label")
        val HOME_IP = stringPreferencesKey("home_ip")
        val OBSCURA_IP = stringPreferencesKey("obscura_ip")
    }

    val isConfigured: Flow<Boolean> = context.dataStore.data.map { prefs ->
        !prefs[Keys.GATEWAY_URL].isNullOrBlank() && !prefs[Keys.TOKEN].isNullOrBlank()
    }

    suspend fun saveSetup(payload: SetupPayload) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GATEWAY_URL] = payload.gatewayUrl.trimEnd('/')
            prefs[Keys.TOKEN] = payload.token
            prefs[Keys.HOME_SSID] = payload.homeSsid
            prefs[Keys.GATEWAY_IP] = payload.gatewayIp
            payload.ipinfoToken?.let { prefs[Keys.IPINFO_TOKEN] = it }
            payload.deviceLabel?.let { prefs[Keys.DEVICE_LABEL] = it }
        }
    }

    suspend fun saveReferenceIps(reference: ReferenceIps) {
        context.dataStore.edit { prefs ->
            reference.homeIp?.let { prefs[Keys.HOME_IP] = it }
            reference.obscuraIp?.let { prefs[Keys.OBSCURA_IP] = it }
        }
    }

    suspend fun snapshot(): SettingsSnapshot {
        val prefs = context.dataStore.data.first()
        return SettingsSnapshot(
            gatewayUrl = prefs[Keys.GATEWAY_URL],
            token = prefs[Keys.TOKEN],
            homeSsid = prefs[Keys.HOME_SSID],
            gatewayIp = prefs[Keys.GATEWAY_IP],
            ipinfoToken = prefs[Keys.IPINFO_TOKEN],
            deviceLabel = prefs[Keys.DEVICE_LABEL],
            homeIp = prefs[Keys.HOME_IP],
            obscuraIp = prefs[Keys.OBSCURA_IP],
        )
    }

    companion object {
        fun parseSetupJson(raw: String): SetupPayload {
            val json = JSONObject(raw.trim())
            return SetupPayload(
                version = json.optInt("v", 1),
                gatewayUrl = json.getString("gateway_url"),
                token = json.getString("token"),
                homeSsid = json.getString("home_ssid"),
                gatewayIp = json.getString("gateway_ip"),
                ipinfoToken = json.optString("ipinfo_token").ifBlank { null },
                deviceLabel = json.optString("device_label").ifBlank { null },
            )
        }
    }
}

data class SettingsSnapshot(
    val gatewayUrl: String? = null,
    val token: String? = null,
    val homeSsid: String? = null,
    val gatewayIp: String? = null,
    val ipinfoToken: String? = null,
    val deviceLabel: String? = null,
    val homeIp: String? = null,
    val obscuraIp: String? = null,
) {
    val configured: Boolean
        get() = !gatewayUrl.isNullOrBlank() && !token.isNullOrBlank()
}
