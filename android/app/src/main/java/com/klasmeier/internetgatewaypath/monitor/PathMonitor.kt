package com.klasmeier.internetgatewaypath.monitor

import android.content.Context
import com.klasmeier.internetgatewaypath.data.InternetPath
import com.klasmeier.internetgatewaypath.data.PathCheckRepository
import com.klasmeier.internetgatewaypath.data.PathCheckResult
import com.klasmeier.internetgatewaypath.data.SettingsRepository
import com.klasmeier.internetgatewaypath.notification.PathNotificationHelper
import com.klasmeier.internetgatewaypath.util.QuietHours

object PathMonitor {
    @Volatile
    private var networkMonitor: NetworkChangeMonitor? = null

    fun start(context: Context) {
        val appContext = context.applicationContext
        PathCheckWorker.schedule(appContext)
        if (networkMonitor == null) {
            networkMonitor = NetworkChangeMonitor(appContext) {
                PathCheckWorker.enqueueNow(appContext)
            }.also { it.register() }
        }
    }

    fun stop(context: Context) {
        networkMonitor?.unregister()
        networkMonitor = null
        PathCheckWorker.cancelAll(context.applicationContext)
    }

    suspend fun runCheckAndNotify(context: Context): PathCheckResult? {
        val appContext = context.applicationContext
        val settingsRepository = SettingsRepository(appContext)
        if (!settingsRepository.snapshot().configured) return null

        val previousName = settingsRepository.getLastPath()
        val previous = previousName?.let {
            runCatching { InternetPath.valueOf(it) }.getOrNull()
        }

        val repository = PathCheckRepository(appContext, settingsRepository = settingsRepository)
        val result = repository.runCheck()
        val path = result.path
        if (path == InternetPath.CHECK_FAILED || path == InternetPath.UNKNOWN) {
            return result
        }

        val prefs = settingsRepository.notificationPrefs()
        val changed = previous != null && previous != path
        if (
            changed &&
            prefs.notificationsEnabled &&
            !QuietHours.isQuietNow(prefs.quietHoursEnabled, prefs.quietStartMinutes, prefs.quietEndMinutes)
        ) {
            PathNotificationHelper(appContext).showPathChange(previous!!, path)
        }
        return result
    }
}
