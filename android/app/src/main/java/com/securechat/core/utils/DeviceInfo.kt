package com.securechat.core.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID

object DeviceInfo {

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER ?: "unknown"
        val model = Build.MODEL ?: "device"
        return if (model.lowercase().startsWith(manufacturer.lowercase())) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    fun deviceIdentifier(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }
}