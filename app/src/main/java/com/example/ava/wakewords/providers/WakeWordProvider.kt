package com.example.ava.wakewords.providers

import com.example.ava.wakewords.models.WakeWordWithId

interface WakeWordProvider {
    suspend fun get(): List<WakeWordWithId>
}