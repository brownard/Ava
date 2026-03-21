# State of the Tasker integration

Ava integrates with the paid [Tasker](https://tasker.joaoapps.com/) app to support automations.
We will be tracking use cases and progress on [this issue](https://github.com/brownard/Ava/issues/49).

You can also use the [Home Assistant Plug-In for Tasker](https://github.com/MarkAdamson/home-assistant-plugin-for-tasker)
to query the voice assistant state via the Home Assistant server.

> [!NOTE]
> The project will not provide general support for Tasker. You should check out their website and
> the `r/tasker` subreddit for support.

## Actions:

- **Wake up satellite**: plays the wake sound (if enabled) and listens to voice commands as if the wake word was detected.
- **Stop ringing**: stops the timer ringing sound, as if "stop" was said.

## State Condition: Ava Activity

The `Ava Activity` state can be used to trigger tasks when the voice satellite enters or exits
specific states:

- A conversation is happening (listening, thinking, replying)
- Voice timers are active (ringing, running, paused)

This [example profile](taskerprofile://H4sIAAAAAAAA/71YXZOiRhR9Hn8FRVU2Lwmfik6WoQpddsuKqxPASVJ5oHqlYUmwsZrW7Pz7dNPgguIow1TmYcRzb597+tIf92r6IP8H4g+AACHHD6IohIfkQVRFgRweREMyJE0RrcGd+YizKElh4bSjz5ooHOCDqDHjnbkJAYGWOh7rqj5R7sfDe92UOcjMsG42JsZ4rJoyPJqjFMS5NTFl/sCgJLQ0U6b/2ZdtEioWJSw+S0C1hgWgFgDaQss+AMHekOSQkGdTZgizeIRGKVRvMqTURVPVCFCvpyzZwNpIjnKHLKSyNUMdK8pQG1EbAwrTdI/CMh0AxwpnvDOfQJoX4AGkJcZothL5N4syTJ6jbI/BVkqzDUihlCACEZHgN4KBNF2s3ak1y9AB4jxB8S8CwXs48JMtxIJLgTNsj9ARY+I6hHmFtJ/J8w5af4MDkFKAYskjmEbvFpdzHINXU7WqGRyBM5fT6NMsSyFA9UENcgSJFFIwTfLNVwmgEGdJKJFiuUuus3Ce7KUfPNnu3J4uHM96l5L3fEY2xuCZ74Z3MXnPDAFgWNCRU2HDf4AYDxyMMyzM6OoZzCOBfIUY/pgLAAmQGX4S6BwISNB3SGArjUWWe4R+vXK1Ur7N41L8Z5jnIL5Z/5a795iCWk1Brr0WhtHN3fHdvm5J8OX01+J0xXcXcPvCLLZKLtkzf75aBvPl49oPZgvb8yy2y+A3sN3RfcUElQPoqVcdXXO025Mr2i7z91J36Wjow9ZdkLteLh335nyx4xPiTiIbEfoJfIOUndN1kOT84bt28LvtBbPV8uP809p1PgRT5+PKdcrTuC/L24i5eO6/CW8Hja7z29rxfErgzz87q7VvGQr9u03I+eA+kU9zMqf3bHzrUr7A1kFPda4FrvO4sGdO8Kvzp2fdFr19bN/gfTbTC4S3yMr3X74UtWB4y7apeXcjf902OB1fxiSsfAx2YJ9THREtWansBtbid1HAuVNjNOaFa5mcJtbmeCVOw6s5nlfDzUAl1uZ4LVDdixf3Mqvuefkv8/qff6GrrWoEVN5h6OLp/WOyKubMXbvkfryuyruKdgVREtcurxof3f0Vn075QMr6N7nofuSi/WH9m1w2cOyZ9XzFCBZEb+ngVGWkTpSLHZyqj+61Sb2Do42afmzXWOvlbTCESFih750YU54hrnRDylZsXLVirLGaKEa9xarNSynnNVKUamacroVZbWNWtSvMJW/Dpr5g00qbcabHlFl+z/I8bMvzyNDG95fzPNFHmnKS52FrnqOoS6J1pUc21JfS0WbTX7ANm7b/9bWq1+fR8lr5J//JxBr8B+9E9JtAEQAA)
turns the screen on when conversing and when timers are running or ringing (but ignores paused timers).

> [!IMPORTANT]
> Tasker only runs tasks when the desired state is achieved, but not when the reason for this state
> change (for example timer running -> ringing). For this, you have to setup several profiles with
> different conditions.
