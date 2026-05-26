package com.example.ava.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.example.ava.util.FileLogger

class BluetoothScoController(
    context: Context
) {
    private var monitor: BluetoothMonitor? = null
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var scoRequested: Boolean = false

    fun enableScoIfPossible() {
        // Set communication mode BEFORE requesting SCO
        FileLogger.log("Setting MODE_IN_COMMUNICATION")
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        audioManager.setStreamVolume(
            AudioManager.STREAM_VOICE_CALL,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),0
        )
        if (audioManager.isBluetoothScoOn || scoRequested) {
            FileLogger.log("SCO already active or requested, skipping")
            return
        }

        scoRequested = true

        FileLogger.log("Scheduling delayed SCO start...")

        // Samsung requires a delay before SCO activation
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                FileLogger.log("Requesting SCO start (delayed). isBluetoothScoOn=${audioManager.isBluetoothScoOn}")

                audioManager.startBluetoothSco()
                audioManager.setBluetoothScoOn(true)

                FileLogger.log("SCO start requested. isBluetoothScoOn=${audioManager.isBluetoothScoOn}")
            } catch (e: Exception) {
                FileLogger.log("SCO start failed: ${e.message}")
                scoRequested = false
            }
        }, 500) // 500ms delay required on Samsung
    }

    fun waitForScoActive() {
        // Call this BEFORE creating AudioRecord
        var attempts = 0
        while (!audioManager.isBluetoothScoOn && attempts < 40) { // max 2 seconds
            FileLogger.log("Waiting for SCO to become active... attempt=$attempts")
            Thread.sleep(50)
            attempts++
        }
        FileLogger.log("SCO active state after wait: ${audioManager.isBluetoothScoOn}")
    }

    fun disableScoIfActive() {
        if (!scoRequested && !audioManager.isBluetoothScoOn) {
            FileLogger.log("SCO not active, nothing to stop")
            return
        }

        try {
            FileLogger.log("Requesting SCO stop. isBluetoothScoOn=${audioManager.isBluetoothScoOn}")
            audioManager.stopBluetoothSco()
            audioManager.setBluetoothScoOn(false)
            monitor?.stop()
            FileLogger.log("SCO stopped. isBluetoothScoOn=${audioManager.isBluetoothScoOn}")
        } catch (e: Exception) {
            FileLogger.log("Error stopping SCO: ${e.message}")
        } finally {
            scoRequested = false

            if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                FileLogger.log("Resetting audio mode to NORMAL")
                audioManager.mode = AudioManager.MODE_NORMAL
            }
        }
    }

    fun attachMonitor(context: Context) {
        monitor = BluetoothMonitor(context, this)
        monitor?.start()
    }
}
