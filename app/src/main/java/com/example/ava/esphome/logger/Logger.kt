package com.example.ava.esphome.logger

import com.example.esphomeproto.api.LogLevel
import com.example.esphomeproto.api.SubscribeLogsResponse
import kotlinx.coroutines.flow.Flow

interface Logger {
    fun setLogLevel(level: LogLevel)
    fun subscribe(): Flow<SubscribeLogsResponse>
}