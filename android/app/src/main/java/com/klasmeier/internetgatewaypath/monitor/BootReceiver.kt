package com.klasmeier.internetgatewaypath.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.klasmeier.internetgatewaypath.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val configured = SettingsRepository(context).isConfigured.first()
                if (configured) {
                    PathMonitor.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
