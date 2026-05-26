package com.example.ava.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.ava.util.FileLogger
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothMonitor(
    private val context: Context,
    private val scoController: BluetoothScoController
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val startupTime = System.currentTimeMillis()
    private val reconnecting = AtomicBoolean(false)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val now = System.currentTimeMillis()
                    if (now - startupTime < 5000) {
                        FileLogger.log("BT device disconnected — starting reconnection loop")
                        return
                    }
                    FileLogger.log("BT device disconnected - starting reconnection loop")
                    startReconnectionLoop()
                }

                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    FileLogger.log("BT device connected — enabling SCO")
                    reconnecting.set(false)
                    scoController.enableScoIfPossible()
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        FileLogger.log("BluetoothMonitor registered")
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
        scope.cancel()
        FileLogger.log("BluetoothMonitor stopped")
    }

    private fun startReconnectionLoop() {
        if (reconnecting.getAndSet(true)) return

        scope.launch {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            var attempts = 0

            // First 5 minutes: retry every 10 seconds
            while (attempts < 30 && reconnecting.get()) {
                FileLogger.log("Reconnection attempt $attempts (10s interval)")
                tryReconnect(adapter)
                delay(10_000)
                attempts++
            }

            // After 5 minutes: retry every 5 minutes
            while (reconnecting.get()) {
                FileLogger.log("Reconnection attempt (5m interval)")
                tryReconnect(adapter)
                delay(5 * 60_000)
            }
        }
    }

    private fun tryReconnect(adapter: BluetoothAdapter?) {
        val device = adapter?.bondedDevices?.firstOrNull {
            it.type == BluetoothDevice.DEVICE_TYPE_CLASSIC ||
            it.type == BluetoothDevice.DEVICE_TYPE_DUAL
        }

        if (device == null) {
            FileLogger.log("No bonded BT device found for reconnection")
            return
        }

        FileLogger.log("Attempting reconnect to ${device.name}")

        try {
            val method = device.javaClass.getMethod("connect")
            method.invoke(device)
        } catch (e: Exception) {
            FileLogger.log("Reconnect failed: ${e.message}")
        }
    }
}
