# Audio Processing

Many Android devices have some built in support for microphone audio processing that can for example take advantage of
hardware acoustic echo cancellation and noise suppression. Ava exposes a few advanced audio processing settings that can be used to tune the microphone processing for specific devices and use-cases.

> [!NOTE]
> The exact implementation and support for audio processing varies widely between manufacturers and devices. Some experimentation will be required for specific devices and use-cases.

> [!TIP]
> Home Assistant can be configured to create debug recordings of voice commands to help troubleshoot microphone audio.
> 
> Add the following lines to the Home Assistant configuration file (where **/share/assist_pipeline** is the directory where the recordings will be saved):

```yaml
assist_pipeline:
  debug_recording_dir: /share/assist_pipeline
```

## Microphone source:

A microphone source in Android defines both the physical microphone that will be used for capture and the effects and tuning that
will be applied to the captured audio. The exact implementation of each microphone source is device specific but a general outline
is provided below.

- **Voice Recognition (Default)**: Tuned for use with voice assistants, it generally provides a clean, unprocessed signal for accurate wake word detection and speech-to-text in quiet environments.
  - *Downside:* It doesn't block background noise or perform echo cancellation very well, if at all.

- **Voice Communication**: Tuned for voice calls and chat, often applies echo cancellation and noise-suppression tuned for speech. May improve performance in noisier environments.
  - *Note:* On some devices (like Samsung phones), you must also turn on **Communication mode** for this to work correctly.

- **Mic**: General use microphone source, may apply automatic gain control and some noise suppression if available, but will pickup background noise.

- **Camcorder**: Tuned for capturing audio with video, will often use the microphone facing the same direction as the camera, and may use noise suppression tuned for e.g. wind noise, but otherwise will pickup background noise.

- **Default**: Device specific, but often equivalent to the Mic source.

- **Unprocessed**: Tuned for unprocessed (raw) audio, but may not be available on all devices, behaves like Default otherwise.

## Communication mode:
Enables **Communication mode**, an Android audio mode tuned for live voice chat, potentially taking advantage of echo cancellation and noise suppression if available.
However audio may be routed through the device's earpiece, negatively impacting audio quality and volume.

- Default: **Off**

> [!NOTE]
> This may be required on some devices when using the **Voice Communication** audio source to take advantage of echo cancellation and noise suppression.

## Speakerphone:

If using **Communication** mode, turning on Speakerphone may be required to boost mic gain and audio volume to allow use from further away.

> [!NOTE]
> While Speakerphone makes the device louder, it might use the smaller speaker at the bottom of your phone, which can decrease audio playback quality.

## Recommendations

### Default:
Provides good voice recognition and audio quality in quiet environments.
- Source: **Voice recognition**
- Communication mode: **Off**
- Speakerphone: **Off**

### Noisier environments:
To improve performance in noisier environments, including to allow use whilst the device is playing media, experiment with variations of the settings below.
The exact requirements are device dependent.

First try setting
- Source: **Voice communication**

If that doesn't help try additionally setting the mode (and possibly speakerphone)
- Communication mode: **On**
- Speakerphone: **On** (if volume becomes very low)

