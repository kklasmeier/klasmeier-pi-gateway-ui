package com.klasmeier.internetgatewaypath.ui

import android.content.Context
import com.klasmeier.internetgatewaypath.R
import com.klasmeier.internetgatewaypath.data.InternetPath

object PathVisuals {
    fun iconRes(path: InternetPath): Int = when (path) {
        InternetPath.OBSCURA -> R.drawable.ic_path_obscura
        InternetPath.HOME -> R.drawable.ic_path_home
        InternetPath.PHONE -> R.drawable.ic_path_phone
        InternetPath.UNKNOWN, InternetPath.CHECK_FAILED -> R.drawable.ic_path_unknown
    }

    fun iconRes(pathName: String?): Int {
        val path = pathName?.let { runCatching { InternetPath.valueOf(it) }.getOrNull() }
        return iconRes(path ?: InternetPath.UNKNOWN)
    }

    fun notificationIconRes(path: InternetPath): Int = when (path) {
        InternetPath.OBSCURA -> R.drawable.ic_notif_obscura
        InternetPath.HOME -> R.drawable.ic_notif_home
        InternetPath.PHONE -> R.drawable.ic_notif_phone
        InternetPath.UNKNOWN, InternetPath.CHECK_FAILED -> R.drawable.ic_path_unknown
    }

    fun label(context: Context, path: InternetPath): String = when (path) {
        InternetPath.OBSCURA -> context.getString(R.string.path_obscura)
        InternetPath.HOME -> context.getString(R.string.path_home)
        InternetPath.PHONE -> context.getString(R.string.path_phone)
        InternetPath.UNKNOWN, InternetPath.CHECK_FAILED -> context.getString(R.string.path_unknown)
    }

    fun label(context: Context, pathName: String?): String {
        val path = pathName?.let { runCatching { InternetPath.valueOf(it) }.getOrNull() }
        return label(context, path ?: InternetPath.UNKNOWN)
    }
}
