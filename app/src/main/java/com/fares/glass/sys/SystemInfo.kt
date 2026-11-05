package com.fares.glass.sys

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.fares.glass.R

/** Memory, storage, OS version and animation speed (WRITE_SECURE_SETTINGS granted over ADB). */
class SystemInfo(context: Context) {
    private val app = context.applicationContext
    private val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    class Usage(val usedFraction: Float, val label: String, val free: String)

    fun memory(): Usage {
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return usage(info.availMem, info.totalMem, ::mb)
    }

    fun storage(): Usage {
        val fs = StatFs(Environment.getDataDirectory().path)
        return usage(fs.availableBlocksLong * fs.blockSizeLong, fs.blockCountLong * fs.blockSizeLong, ::gb)
    }

    private fun usage(free: Long, total: Long, size: (Long) -> String) = Usage(
        1f - free.toFloat() / total,
        app.getString(R.string.usage_free_of, size(free), size(total)),
        app.getString(R.string.usage_free, size(free)),
    )

    val osVersion: String by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, "ro.build.version.name") as String
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Android ${android.os.Build.VERSION.RELEASE}"
    }

    /** Current animation speed: 1, 0.5 or 0. */
    fun animationScale(): Float = Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    fun missing(permission: String) = app.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED

    fun canWriteSettings() =
        app.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Switches to the next speed (1× → 0.5× → off → 1×). Returns false without the permission. */
    fun cycleAnimations(): Boolean {
        if (!canWriteSettings()) return false
        val next = when (animationScale()) {
            1f -> 0.5f
            0.5f -> 0f
            else -> 1f
        }
        val cr = app.contentResolver
        return runCatching {
            Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, next)
            Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, next)
            Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, next)
        }.isSuccess
    }

    private fun mb(bytes: Long) = app.getString(R.string.size_mb, bytes / (1024 * 1024))
    private fun gb(bytes: Long) = app.getString(R.string.size_gb, bytes / 1_073_741_824.0)
}
