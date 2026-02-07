package com.example.ava

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ava.wakewords.providers.AssetWakeWordProvider
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the AssetWakeWordProvider class.
 */
@RunWith(AndroidJUnit4::class)
class AssetWakeWordProviderTest {
    @Test
    fun should_get_wake_words_from_assets_directory() {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val wakeWordProvider = AssetWakeWordProvider(instrumentationContext.assets, "")

        val wakeWords = runBlocking { wakeWordProvider.get() }

        assert(wakeWords.size == 1)
        val wakeWord = wakeWords.first()
        assert(wakeWord.id == "hey_jarvis")
        assert(wakeWord.wakeWord.model == "hey_jarvis.tflite")
        val model = runBlocking { wakeWord.load() }
        assert(model.remaining() > 0)
    }

    @Test
    fun should_get_wake_words_from_assets_subdirectory() {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val wakeWordProvider = AssetWakeWordProvider(instrumentationContext.assets, "wakeWords")

        val wakeWords = runBlocking { wakeWordProvider.get() }
        
        assert(wakeWords.size == 2)
        val computerWakeWord = wakeWords.firstOrNull { it.id == "computer" }
        assert(computerWakeWord != null)
        assert(computerWakeWord!!.wakeWord.model == "computer.tflite")
        val computerModel = runBlocking { computerWakeWord.load() }
        assert(computerModel.remaining() > 0)

        val okayNabuWakeWord = wakeWords.firstOrNull { it.id == "okay_nabu" }
        assert(okayNabuWakeWord != null)
        assert(okayNabuWakeWord!!.wakeWord.model == "okay_nabu.tflite")
        val okayNabuModel = runBlocking { okayNabuWakeWord.load() }
        assert(okayNabuModel.remaining() > 0)
    }
}