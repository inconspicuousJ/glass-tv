package com.fares.glass.sys

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.StringRes
import com.fares.glass.R

/**
 * Shortcuts to system settings screens that work on any Android TV.
 * Each section tries several screens (Fire OS, Android TV, Google TV, AOSP) and keeps the first one
 * a third-party app is really allowed to open: exported, with no reserved permission (Fire OS, for
 * example, guards "Display & Sounds" with a signature|amazon permission), and not the settings home.
 * A section with no usable screen is not offered.
 */
class SettingsShortcuts(context: Context) {
    private val app = context.applicationContext
    private val pm = app.packageManager

    enum class Section(@StringRes val title: Int, vararg val actions: String) {
        Display(R.string.section_display, "com.amazon.device.settings.action.DISPLAY_AND_SOUNDS", Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SOUND_SETTINGS),
        Devices(R.string.section_devices, "android.settings.CONTROLLERS_SETTINGS", Settings.ACTION_BLUETOOTH_SETTINGS),
        Apps(R.string.section_apps, Settings.ACTION_APPLICATION_SETTINGS, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        Accessibility(R.string.section_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS, "com.amazon.device.settings.action.ACCESSIBILITY"),
        About(R.string.section_about, "com.amazon.device.settings.action.DEVICE", Settings.ACTION_DEVICE_INFO_SETTINGS),
        Network(R.string.section_network, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS),
        All(R.string.section_all, Settings.ACTION_SETTINGS),
    }

    private val home: ComponentName? by lazy { component(Intent(Settings.ACTION_SETTINGS)) }
    private val cache = HashMap<Section, Intent?>()

    /** The screen to open for [section], or null if this TV offers none that Glass may open. */
    fun intent(section: Section): Intent? = cache.getOrPut(section) {
        section.actions.asSequence()
            .map { Intent(it) }
            .firstOrNull { candidate ->
                val target = usable(candidate) ?: return@firstOrNull false
                section == Section.All || target != home
            }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun available(section: Section) = intent(section) != null

    private fun component(intent: Intent): ComponentName? =
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.let { ComponentName(it.packageName, it.name) }

    /** The component that will receive [intent] if Glass is allowed to open it, otherwise null. */
    private fun usable(intent: Intent): ComponentName? {
        val info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return null
        if (!info.exported) return null
        val needed = info.permission
        if (needed != null && app.checkSelfPermission(needed) != PackageManager.PERMISSION_GRANTED) return null
        return ComponentName(info.packageName, info.name)
    }
}
