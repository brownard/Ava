package com.example.ava.players

import androidx.media3.common.Player

interface MediaPlayer {
    var volume: Float
    fun addListener(listener: Player.Listener)
    fun removeListener(listener: Player.Listener)
}