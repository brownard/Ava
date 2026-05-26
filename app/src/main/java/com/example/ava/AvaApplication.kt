package com.example.ava

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import com.example.ava.util.FileLogger

@HiltAndroidApp
class AvaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
//        FileLogger.init(this)
    }
}
