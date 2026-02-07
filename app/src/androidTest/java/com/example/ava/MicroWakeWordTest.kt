package com.example.ava

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ava.wakewords.microwakeword.MicroWakeWord
import com.example.ava.wakewords.microwakeword.MicroWakeWordDetector
import com.example.ava.wakewords.providers.AssetWakeWordProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Instrumented test for the MicroWakeWord class.
 */
@RunWith(AndroidJUnit4::class)
class MicroWakeWordTest {
    @Test
    fun should_detect_wake_word() {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val detector = runBlocking { createDetector(instrumentationContext, "okay_nabu") }

        val detections = detector.detect(loadWav(instrumentationContext, "wakeWords/okay_nabu.wav"))

        assert(detections.size == 1)
        assert(detections.first().wakeWordId == "okay_nabu")
    }

    @Test
    fun should_detect_wake_word_with_different_stride() {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val detector = runBlocking { createDetector(instrumentationContext, "computer") }

        val result = detector.detect(loadWav(instrumentationContext, "wakeWords/computer.wav"))

        assert(result.size == 1)
        assert(result.first().wakeWordId == "computer")
    }

    suspend fun createDetector(context: Context, wakeWordId: String): MicroWakeWordDetector {
        val wakeWordProvider = AssetWakeWordProvider(context.assets, "wakeWords")
        val wakeWords = wakeWordProvider.get()
        val wakeWord = wakeWords.firstOrNull { it.id == wakeWordId }
        assert(wakeWord != null)
        val model = MicroWakeWord.fromWakeWord(wakeWord!!)
        return MicroWakeWordDetector(listOf(model))
    }

    fun loadWav(context: Context, name: String): ByteBuffer {
        val fd = context.assets.openFd(name)
        val stream = FileInputStream(context.assets.openFd(name).fileDescriptor)
        val fileChannel = stream.channel
        val startOffset: Long = fd.startOffset
        val declaredLength: Long = fd.declaredLength
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
    }
}