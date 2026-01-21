package com.example.ava.wakewords.models

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer

data class WakeWordWithId(
    val id: String,
    val wakeWord: WakeWord,
    val load: suspend () -> ByteBuffer
)

@Serializable
data class WakeWord(
    val type: String,
    val wake_word: String,
    val model: String,
    val micro: Micro,
    val author: String = "",
    val website: String = "",
    val trained_languages: Array<String> = arrayOf(),
    val version: Int = 0,
)

@Serializable
data class Micro(
    val probability_cutoff: Float,
    val sliding_window_size: Int,
    val feature_step_size: Int = 0,
    val tensor_arena_size: Int = 0,
    val minimum_esphome_version: String = "",
)
