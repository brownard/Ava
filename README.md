# Android Voice Assistant (Ava)

Experimental Android voice assistant for [Home Assistant][homeassistant] that uses the [ESPHome][esphome] protocol.

Intended for turning your existing Android wall panel or similar into a local voice assistant for controlling your smart home using Home Assistant.

⚠️**Warning: This app is intended for 'static' local devices like a wall panel on a trusted network, it is not suitable for your mobile device (though it probably will work) for the following reasons**⚠️
- It exposes a port to allow incoming connections from Home Assistant with no authentication whatsoever (the device is the server in the ESPHome API) so shouldn't be used on any untrusted networks. Additionally the device needs to be reachable from Home Assistant for the app to function, which probably means the device will need to remain on the local network.
- It is constantly using the microphone to listen for the wake word, which is not only a privacy issue but also a battery drain. Android does not allow non-system apps to listen 'passively' like the built in assistants.

Requires Android 8 or above.

[![paypal](https://www.paypalobjects.com/en_GB/i/btn/btn_donate_LG.gif)](https://www.paypal.com/donate/?business=EU5MC92FG3JF6&no_recurring=0&currency_code=GBP)

# Implemented
- Local wake word detection using up to two microWakeWord models
- Stop word detection
- Custom wake word support
- Voice commands
- Announcements and conversations
- Media player
- Microphone mute switch
- Wake/preannounce sounds
- Timers

# Todo
- Improved Assist feedback on screen
- Display timer status on screen
- Display media playback status on screen

# Setup
- Install and run the app
- Optionally change the satellite name and port settings
- Start the satellite service, it will continue to run in the background until manually stopped.
- In Home Assistant:
  - Add or open the ESPHome integration
  - The satellite should be auto-detected in most cases, if not, manually add the satellite using the device's IP address and configured port (Default: 6053)
  - Complete the voice assistant setup wizard
 
# App Settings
Once connected, the satellite is fully configurable from within Home Assistant as a ESPHome voice satellite and media player, however some settings can also be changed from within the app.

- Name: The display name of the satellite. Requires service restart
- Port: The port to listen on. Requires service restart
- Autostart service: Whether to automatically start the satellite service on application start
- Wake word 1 and Wake word 2: The wake words to use (second is optional). Note that changing this in the app does immediately change the wake word, however currently the change is not reflected in Home Assistant. It is recommended to change the wake word from Home Assistant to ensure the configuration remains in sync.
- Custom wake words: Specify a directory on the device containing custom wake word models
- Enable wake sound: Whether to play a sound when the satellite is woken by a wake word
- Custom timer sound: Specify an audio file to play instead of the default one
- Repeat timer sound: By default, the timer sound is repeated until stopped by the user

# Custom wake word models
The app includes a default [set of wake words](https://github.com/brownard/Ava/tree/master/app/src/main/assets/wakeWords), however you can also specify a directory containing custom wake words supported by microWakeWord.  
Create a directory on your device, copy the wake word model(s) as well as valid json file(s) describing each model ([example](https://github.com/brownard/Ava/blob/master/app/src/main/assets/wakeWords/okay_nabu.json)), a minimum valid example json is:
```
{
  "type": "micro",
  "wake_word": "Custom Wake Word",
  "model": "custom_wakeword.tflite",
  "micro": {
    "probability_cutoff": 0.97,
    "sliding_window_size": 5
  }
}
```
Choose the directory in the 'Custom wake words' setting in the app. The wake word(s) in the directory should now be selectable in the app, however you will need to restart the service in order for the new wake words to show in Home Assistant.  
**If your custom wake words do not appear, double check you have included a valid json file with at least the properties shown above.**

# Development
1. Clone the repo
2. Open in Android Studio

# Debugging on an emulated device
You will need to redirect incoming connections on the server port to the emulated device
1. From a terminal run `telnet localhost 5554` to telnet into the emulator
2. Enter your auth token if required
3. Run `redir add tcp:6053:6053` to add a redirect for the server port (default 6053) to allow incoming connections to be routed to the device

<!-- Links -->
[homeassistant]: https://www.home-assistant.io/
[esphome]: https://esphome.io/
