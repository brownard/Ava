package com.example.ava.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {

    private var logUri: Uri? = null
    private var outputStream: OutputStream? = null

    fun init(context: Context) {
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "ava_debug_log.txt")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        logUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        outputStream = logUri?.let { resolver.openOutputStream(it, "wa") }

        log("=== Logger initialized at ${Date()} ===")
    }

    fun log(message: String) {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            outputStream?.write("$timestamp  $message\n".toByteArray())
            outputStream?.flush()
        } catch (_: Exception) {
        }
    }
}
