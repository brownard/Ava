package com.example.ava.esphome.VoiceSatellite

sealed class VoiceSatelliteState {
    class Stopped() : VoiceSatelliteState()
    class Disconnected() : VoiceSatelliteState()
    class Idle() : VoiceSatelliteState()
    class Listening() : VoiceSatelliteState()
    class Processing() : VoiceSatelliteState()
    class Responding() : VoiceSatelliteState()
}