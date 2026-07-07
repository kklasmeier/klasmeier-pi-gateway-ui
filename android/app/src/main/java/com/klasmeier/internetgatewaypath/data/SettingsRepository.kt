package com.klasmeier.internetgatewaypath.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.klasmeier.internetgatewaypath.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class NotificationPrefs(
    val notificationsEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = true,
    val quietStartMinutes: Int = SettingsRepository.DEFAULT_QUIET_START,
    val quietEndMinutes: Int = SettingsRepository.DEFAULT_QUIET_END,
)

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
        val LAST_PATH = stringPreferencesKey("last_path")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_MINUTES = intPreferencesKey("quiet_start_minutes")
        val QUIET_END_MINUTES = intPreferencesKey("quiet_end_minutes")
    }

    val isConfigured: Flow<Boolean> = context.dataStore.data.map { prefs ->
        !prefs[Keys.GATEWAY_URL].isNullOrBlank() && !prefs[Keys.TOKEN].isNullOrBlank()
    }

    val notificationPrefsFlow: Flow<NotificationPrefs> = context.dataStore.data.map { prefs ->
        notificationPrefsFrom(prefs)
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

    suspend fun getLastPath(): String? = context.dataStore.data.first()[Keys.LAST_PATH]

    suspend fun setLastPath(path: String) {
        context.dataStore.edit { prefs -> prefs[Keys.LAST_PATH] = path }
    }

    suspend fun updateNotificationPrefs(transform: (NotificationPrefs) -> NotificationPrefs) {
        context.dataStore.edit { prefs ->
            val current = notificationPrefsFrom(prefs)
            val updated = transform(current)
            prefs[Keys.NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
            prefs[Keys.QUIET_HOURS_ENABLED] = updated.quietHoursEnabled
            prefs[Keys.QUIET_START_MINUTES] = updated.quietStartMinutes
            prefs[Keys.QUIET_END_MINUTES] = updated.quietEndMinutes
        }
    }

    suspend fun notificationPrefs(): NotificationPrefs {
        migrateLegacyQuietHoursIfNeeded()
        return notificationPrefsFrom(context.dataStore.data.first())
    }

    suspend fun migrateLegacyQuietHoursIfNeeded() {
        val prefs = context.dataStore.data.first()
        val start = prefs[Keys.QUIET_START_MINUTES] ?: return
        val end = prefs[Keys.QUIET_END_MINUTES] ?: DEFAULT_QUIET_END
        if (start == LEGACY_QUIET_START && end == DEFAULT_QUIET_END) {
            context.dataStore.edit { edit ->
                edit[Keys.QUIET_START_MINUTES] = DEFAULT_QUIET_START
            }
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

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        AppDatabase.get(context).transitionDao().deleteAll()
    }

    private fun notificationPrefsFrom(prefs: Preferences): NotificationPrefs {
        return NotificationPrefs(
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: true,
            quietStartMinutes = prefs[Keys.QUIET_START_MINUTES] ?: SettingsRepository.DEFAULT_QUIET_START,
            quietEndMinutes = prefs[Keys.QUIET_END_MINUTES] ?: SettingsRepository.DEFAULT_QUIET_END,
        )
    }

    companion object {
        const val DEFAULT_QUIET_START = 3 * 60
        const val DEFAULT_QUIET_END = 7 * 60
        private const val LEGACY_QUIET_START = 23 * 60

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
