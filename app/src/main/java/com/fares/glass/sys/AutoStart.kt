package com.fares.glass.sys

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.fares.glass.MainActivity

/**
 * Brings Glass to the front when the Fire TV starts up or wakes from sleep, instead of Amazon's home
 * screen. (Launcher Manager Mini still handles the Home button.) Fire OS 7 is Android 9, which lets
 * an app open its own screen from these broadcasts.
 */
object AutoStart {
    /** Amazon's home screen draws itself first on boot/wake; open Glass just after it. */
    private const val DELAY_MS = 1200L

    /**
     * The log watcher only acts for a short window after the TV starts or wakes up. Amazon's Settings and
     * other screens live inside its home-screen app, so outside that window Glass leaves them alone.
     */
    @Volatile var armedUntil = 0L
        private set

    fun arm(ms: Long = 20_000L) { armedUntil = SystemClock.uptimeMillis() + ms }
    fun disarm() { armedUntil = 0L }

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
            -> { AutoStart.arm(); AutoStart.openGlass(context, 4000L) }
        }
    }
}

/** Keeps a "screen turned on" listener for as long as Glass is running (it can't be in the manifest). */
class GlassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) { AutoStart.arm(); AutoStart.openGlass(context) }
            }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
        // A fresh process right after boot: allow the takeover briefly.
        if (SystemClock.elapsedRealtime() < 3 * 60_000L) AutoStart.arm()
        watchAmazonHome()
    }

    /**
     * The instant path: reads the system log (needs READ_LOGS, granted once over ADB) and the moment
     * Amazon's home screen is brought up - on boot, on wake, or by anything else - opens Glass over it.
     * Without the permission logcat only shows Glass's own lines, so this simply never fires.
     */
    private fun watchAmazonHome() {
        Thread {
            var last = 0L
            while (true) {
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("logcat", "-T", "1", "-b", "events", "-b", "main", "-v", "brief"))
                    p.inputStream.bufferedReader().forEachLine { line ->
                        if (SystemClock.uptimeMillis() < AutoStart.armedUntil &&
                            line.contains("com.amazon.tv.launcher") && line.contains("HomeActivity") &&
                            !line.contains("ettings", ignoreCase = true) &&
                            (line.contains("am_") || line.contains("START u0") || line.contains("Displayed"))
                        ) {
                            val now = SystemClock.uptimeMillis()
                            if (now - last > 1500) {
                                last = now
                                AutoStart.disarm() // once is enough: after that, Amazon's screens are yours
                                AutoStart.openGlass(this, 0L)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Glass", "log watch stopped", e)
                }
                try { Thread.sleep(5000) } catch (_: InterruptedException) { return@Thread }
            }
        }.apply { isDaemon = true; name = "glass-home-watch" }.start()
    }
}
