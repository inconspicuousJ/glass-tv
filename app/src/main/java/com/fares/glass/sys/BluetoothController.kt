@file:Suppress("DEPRECATION", "MissingPermission") // Android 7 Bluetooth API; permissions granted over ADB.

package com.fares.glass.sys

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fares.glass.R

enum class BtKind(@StringRes val label: Int) {
    Audio(R.string.bt_kind_audio),
    Remote(R.string.bt_kind_remote),
    Gamepad(R.string.bt_kind_gamepad),
    Phone(R.string.bt_kind_phone),
    Computer(R.string.bt_kind_computer),
    Other(R.string.bt_device),
}

data class BtDevice(
    val address: String,
    val name: String,
    val bonded: Boolean,
    val connected: Boolean,
    /** Remote / controller: Glass never offers to forget it. */
    val protected: Boolean,
    val kind: BtKind,
)

/** Bluetooth state and commands for the Control Center. */
class BluetoothController(context: Context) {
    private val app = context.applicationContext
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val seen = LinkedHashMap<String, BluetoothDevice>()

    val available get() = adapter != null
    var enabled by mutableStateOf(false)
        private set
    var paired by mutableStateOf<List<BtDevice>>(emptyList())
        private set
    var found by mutableStateOf<List<BtDevice>>(emptyList())
        private set
    var discovering by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)

    private var listening = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.let { seen[it.address] = it }
            }
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                status = when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                    BluetoothDevice.BOND_BONDING -> app.getString(R.string.bt_pairing, dev?.name ?: app.getString(R.string.bt_the_device))
                    BluetoothDevice.BOND_BONDED -> app.getString(R.string.bt_paired, dev?.name ?: app.getString(R.string.bt_device))
                    else -> status
                }
            }
            refresh()
        }
    }

    fun start() {
        if (!listening) {
            app.registerReceiver(receiver, IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            })
            listening = true
        }
        refresh()
    }

    fun stop() {
        if (listening) runCatching { app.unregisterReceiver(receiver) }
        listening = false
        runCatching { if (adapter?.isDiscovering == true) adapter.cancelDiscovery() }
    }

    fun refresh() {
        val a = adapter ?: return
        try {
            enabled = a.isEnabled
            discovering = a.isDiscovering
            val bonded = if (enabled) a.bondedDevices.orEmpty() else emptySet()
            paired = bonded.map { it.toBt() }.sortedWith(compareByDescending<BtDevice> { it.connected }.thenBy { it.name })
            val bondedAddresses = bonded.map { it.address }.toSet()
            found = seen.values.filter { it.address !in bondedAddresses && !it.name.isNullOrBlank() }.map { it.toBt() }
        } catch (e: SecurityException) {
            status = app.getString(R.string.bt_permission_missing)
        }
    }

    /** Turns Bluetooth on or off. Returns false if the system refuses (Android 13+). */
    fun turn(on: Boolean): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33) return false
        val ok = runCatching { if (on) adapter?.enable() else adapter?.disable() }
            .onFailure { Log.w("Glass", "Bluetooth", it) }.getOrNull() == true
        if (ok) {
            enabled = on
            if (!on) { paired = emptyList(); found = emptyList() }
        }
        return ok
    }

    fun discover() {
        val a = adapter ?: return
        seen.clear()
        found = emptyList()
        status = app.getString(
            if (runCatching { a.startDiscovery() }.getOrDefault(false)) R.string.bt_discovering else R.string.bt_discover_failed,
        )
        refresh()
    }

    fun pair(address: String) {
        val dev = seen[address] ?: return
        runCatching { adapter?.cancelDiscovery() }
        status = app.getString(if (dev.createBond()) R.string.bt_pairing else R.string.bt_pair_failed, dev.name)
    }

    /** Forgets a paired device (hidden removeBond method, reachable on Android 7). */
    fun forget(address: String) {
        val dev = adapter?.bondedDevices?.firstOrNull { it.address == address } ?: return
        val ok = runCatching { dev.javaClass.getMethod("removeBond").invoke(dev) as Boolean }.getOrDefault(false)
        status = app.getString(if (ok) R.string.bt_forgotten else R.string.bt_forget_failed, dev.name)
        refresh()
    }

    private fun BluetoothDevice.toBt(): BtDevice {
        val major = bluetoothClass?.majorDeviceClass
        val label = name?.takeIf { it.isNotBlank() } ?: address
        // Device names are matched in English and French ("Amazon Fire TV Remote", "télécommande").
        val namedRemote = label.contains("remote", ignoreCase = true) || label.contains("télécommande", ignoreCase = true)
        val kind = when (major) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> BtKind.Audio
            BluetoothClass.Device.Major.PERIPHERAL -> if (namedRemote) BtKind.Remote else BtKind.Gamepad
            BluetoothClass.Device.Major.PHONE -> BtKind.Phone
            BluetoothClass.Device.Major.COMPUTER -> BtKind.Computer
            else -> BtKind.Other
        }
        // isConnected() is hidden but public on Android 7: called through reflection.
        val connected = runCatching { javaClass.getMethod("isConnected").invoke(this) as Boolean }.getOrDefault(false)
        return BtDevice(address, label, bondState == BluetoothDevice.BOND_BONDED, connected,
            major == BluetoothClass.Device.Major.PERIPHERAL || namedRemote, kind)
    }
}
