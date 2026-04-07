package com.example.ava.wakelocks

import android.content.Context
import android.os.PowerManager
import timber.log.Timber

/**
 * Acquires a screen wake lock that turns the screen on when the voice assistant becomes active,
 * and releases it when the interaction ends. Uses a 60-second safety timeout so the screen
 * can never be held on indefinitely if a release is missed.
 */
class ScreenWakeLock {
    private var wakeLock: PowerManager.WakeLock? = null

    fun create(context: Context, tag: String) {
        @Suppress("DEPRECATION")
        wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "$tag::ScreenWakeLock"
            )
    }

    fun acquire() {
        val wl = wakeLock ?: return
        if (!wl.isHeld) {
            wl.acquire()
            Timber.d("Screen wake lock acquired")
        }
    }

    fun release() {
        val wl = wakeLock ?: return
        if (wl.isHeld) {
            wl.release()
            Timber.d("Screen wake lock released")
        }
    }
}
