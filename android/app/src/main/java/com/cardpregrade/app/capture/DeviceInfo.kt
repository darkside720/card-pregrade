package com.cardpregrade.app.capture

import android.os.Build
import com.cardpregrade.app.BuildConfig

/** Static device facts recorded with every capture. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val appVersion: String,
) {
    companion object {
        fun current() = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            appVersion = BuildConfig.VERSION_NAME,
        )
    }
}
