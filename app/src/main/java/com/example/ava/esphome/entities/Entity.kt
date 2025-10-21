package com.example.ava.esphome.entities

import com.google.protobuf.GeneratedMessage
import kotlinx.coroutines.flow.Flow

interface Entity {
    val state: Flow<GeneratedMessage>
    suspend fun handleMessage(message: GeneratedMessage): Sequence<GeneratedMessage>
}