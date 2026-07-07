package com.klasmeier.internetgatewaypath

import android.app.Application
import com.klasmeier.internetgatewaypath.data.db.AppDatabase

class InternetGatewayPathApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
}
