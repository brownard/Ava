package com.example.ava.esphome.entities

import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.Flow

interface Entity {
    val state: Flow<MessageLite>
    suspend fun start() {}
    fun handleMessage(message: MessageLite): Flow<MessageLite>
}