package com.klasmeier.internetgatewaypath

import android.app.Application
import com.klasmeier.internetgatewaypath.data.db.AppDatabase
import org.osmdroid.config.Configuration

class InternetGatewayPathApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE),
        )
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
    }
}
