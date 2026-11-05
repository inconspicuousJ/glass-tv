@file:Suppress("DEPRECATION") // Android 7 Wi-Fi API: the one the Fire TV runs.

package com.fares.glass.sys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fares.glass.R

data class WifiNet(val ssid: String, val level: Int, val secure: Boolean, val saved: Boolean, val connected: Boolean)

/** Wi-Fi state and commands for the Control Center (Wi-Fi permissions, plus location for scanning). */
class WifiController(context: Context) {
    private val app = context.applicationContext
    private val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager

    var enabled by mutableStateOf(false)
        private set
    var current by mutableStateOf<String?>(null)
        private set
    var ip by mutableStateOf<String?>(null)
        private set
    var level by mutableIntStateOf(0)
        private set
    var networks by mutableStateOf<List<WifiNet>>(emptyList())
        private set
    /** True until the first scan since opening the page has finished (or was refused). */
    var searching by mutableStateOf(false)
        private set
    /** Message shown under the list (connecting, failure, scan unavailable…). */
    var status by mutableStateOf<String?>(null)

    private var listening = false
    private val main = Handler(Looper.getMainLooper())
    private val giveUp = Runnable { searching = false; refresh() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) searching = false
            refresh()
        }
    }

    fun start() {
        if (!listening) {
            app.registerReceiver(receiver, IntentFilter().apply {
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.RSSI_CHANGED_ACTION)
            })
            listening = true
        }
        refresh()
        scan()
    }

    fun stop() {
        if (listening) runCatching { app.unregisterReceiver(receiver) }
        listening = false
        main.removeCallbacks(giveUp)
    }

    fun scan() {
        if (!wm.isWifiEnabled) return
        // Some systems refuse or throttle scans: fall back to the last results instead of waiting forever.
        val started = runCatching { wm.startScan() }.getOrDefault(false)
        searching = started
        main.removeCallbacks(giveUp)
        if (started) main.postDelayed(giveUp, 15_000) // in case the "results available" broadcast never comes
        if (!started) Log.i("Glass", "Wi-Fi: scan refused (sdk ${android.os.Build.VERSION.SDK_INT}, canScan ${canScan()})")
        refresh()
    }

    fun refresh() {
        enabled = wm.isWifiEnabled
        val info = wm.connectionInfo
        val ssid = info?.ssid?.trim('"')?.takeIf { enabled && it.isNotBlank() && it != "<unknown ssid>" && info.networkId != -1 }
        current = ssid
        ip = info?.ipAddress?.takeIf { it != 0 }?.let { "${it and 0xFF}.${it shr 8 and 0xFF}.${it shr 16 and 0xFF}.${it shr 24 and 0xFF}" }
        level = if (ssid != null) WifiManager.calculateSignalLevel(info.rssi, 5) else 0
        if (ssid != null && status == app.getString(R.string.wifi_connecting, ssid)) status = null
        val saved = runCatching { wm.configuredNetworks.orEmpty().mapNotNull { it.SSID?.trim('"') }.toSet() }.getOrDefault(emptySet())
        val results = try {
            wm.scanResults.orEmpty()
        } catch (e: SecurityException) {
            status = app.getString(R.string.wifi_scan_no_location)
            emptyList()
        }
        val locationOff = app.getString(R.string.wifi_location_off)
        if (results.isEmpty() && !searching) {
            Log.i("Glass", "Wi-Fi: no scan results (sdk ${android.os.Build.VERSION.SDK_INT}, canScan ${canScan()})")
        }
        if (results.isEmpty() && enabled && !canScan()) status = locationOff
        else if (status == locationOff) status = null
        networks = results
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (name, list) ->
                val best = list.maxBy { it.level }
                WifiNet(
                    ssid = name,
                    level = WifiManager.calculateSignalLevel(best.level, 5),
                    secure = best.capabilities.contains("WPA") || best.capabilities.contains("WEP"),
                    saved = name in saved,
                    connected = name == ssid,
                )
            }
            .let { list ->
                // The current network may be missing from the results (scan refused, empty cache…).
                if (ssid == null || list.any { it.ssid == ssid }) list
                else list + WifiNet(ssid, level, secure = true, saved = true, connected = true)
            }
            .sortedWith(compareByDescending<WifiNet> { it.connected }.thenByDescending { it.saved }.thenByDescending { it.level })
    }

    /**
     * Whether the system will hand Glass scan results. Android 13+ with NEARBY_WIFI_DEVICES needs nothing
     * more; otherwise scanning needs location services turned on (Android 6+).
     */
    private fun canScan(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            app.checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        ) return true
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return lm.isLocationEnabled
        }
        // Android 6 to 8 also only return scan results while location is on.
        return Settings.Secure.getInt(app.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) !=
            Settings.Secure.LOCATION_MODE_OFF
    }

    /** Android 10+: apps can no longer turn Wi-Fi on or off, or save networks themselves. */
    val canConnect get() = android.os.Build.VERSION.SDK_INT < 29

    /** Turns Wi-Fi on or off. Returns false if the system refuses (Android 10+). */
    fun turn(on: Boolean): Boolean {
        if (!canConnect) return false
        val ok = runCatching { wm.setWifiEnabled(on) }.onFailure { Log.w("Glass", "Wi-Fi", it) }.getOrDefault(false)
        if (ok) {
            enabled = on
            if (!on) networks = emptyList()
        }
        return ok
    }

    /** Saved or open network: connect directly. Otherwise a password is needed. */
    fun needsPassword(net: WifiNet) = net.secure && !net.saved

    fun connect(net: WifiNet, password: String? = null) {
        status = app.getString(R.string.wifi_connecting, net.ssid)
        val quoted = "\"${net.ssid}\""
        val existing = runCatching { wm.configuredNetworks.orEmpty().firstOrNull { it.SSID == quoted } }.getOrNull()
        val id = if (existing != null && password == null) {
            existing.networkId
        } else {
            existing?.let { wm.removeNetwork(it.networkId) }
            wm.addNetwork(WifiConfiguration().apply {
                SSID = quoted
                if (net.secure && password != null) {
                    preSharedKey = "\"$password\""
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            })
        }
        if (id == -1) {
            status = app.getString(R.string.wifi_save_failed)
            return
        }
        val ok = wm.enableNetwork(id, true) && wm.reconnect()
        status = app.getString(if (ok) R.string.wifi_connecting else R.string.wifi_connect_failed, net.ssid)
    }
}
