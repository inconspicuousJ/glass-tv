package com.fares.glass.sys

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.fares.glass.MainActivity

/**
 * Brings Glass to the front when the Fire TV starts up or wakes from sleep, instead of Amazon's home
 * screen. (Launcher Manager Mini still handles the Home button.) Fire OS 7 is Android 9, which lets
 * an app open its own screen from these broadcasts.
 */
object AutoStart {
    /** Amazon's home screen draws itself first on boot/wake; open Glass just after it. */
    private const val DELAY_MS = 2500L

    fun openGlass(context: Context, delay: Long = DELAY_MS) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                app.startActivity(
                    Intent(app, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            } catch (e: Exception) {
                Log.w("Glass", "Auto-start failed", e)
            }
        }, delay)
    }
}

/** Device finished starting up (declared in the manifest). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON", Intent.ACTION_MY_PACKAGE_REPLACED,
            -> AutoStart.openGlass(context, 4000L)
        }
    }
}

/** Keeps a "screen turned on" listener for as long as Glass is running (it can't be in the manifest). */
class GlassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) AutoStart.openGlass(context)
            }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
    }
}
