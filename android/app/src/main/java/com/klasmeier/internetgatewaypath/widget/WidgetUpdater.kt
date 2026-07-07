package com.klasmeier.internetgatewaypath.widget

import android.content.Context
import com.klasmeier.internetgatewaypath.data.InternetPath
import com.klasmeier.internetgatewaypath.data.PathCheckResult
import com.klasmeier.internetgatewaypath.data.SettingsRepository

object WidgetUpdater {
    suspend fun onCheckComplete(context: Context, result: PathCheckResult) {
        val appContext = context.applicationContext
        val settings = SettingsRepository(appContext)
        if (result.path != InternetPath.CHECK_FAILED && result.path != InternetPath.UNKNOWN) {
            settings.saveWidgetState(result.path.name, result.checkedAtEpochMs)
        }
        PathWidget().updateAll(appContext)
    }

    suspend fun refreshAll(context: Context) {
        PathWidget().updateAll(context.applicationContext)
    }
}
