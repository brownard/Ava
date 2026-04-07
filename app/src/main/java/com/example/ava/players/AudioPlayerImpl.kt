package com.example.ava.players

import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import java.net.URLEncoder

/**
 * Implementation of AudioPlayer backed by an ExoPlayer.
 */
@UnstableApi
class AudioPlayerImpl(
    private val audioManager: AudioManager,
    val focusGain: Int,
    private val playerBuilder: () -> Player
) : AudioPlayer {
    private var _player: Player? = null
    private var isPlayerInit = false
    private var focusRegistration: AudioFocusRegistration? = null

    private val _state = MutableStateFlow(AudioPlayerState.IDLE)
    override val state = _state.asStateFlow()

    private val _mediaTitle = MutableStateFlow<String?>(null)
    override val mediaTitle = _mediaTitle.asStateFlow()

    private val _mediaArtist = MutableStateFlow<String?>(null)
    override val mediaArtist = _mediaArtist.asStateFlow()

    private val _artworkData = MutableStateFlow<ByteArray?>(null)
    override val artworkData = _artworkData.asStateFlow()

    private val _artworkUri = MutableStateFlow<String?>(null)
    override val artworkUri = _artworkUri.asStateFlow()

    override val currentPosition: Long get() = _player?.currentPosition ?: 0L
    override val duration: Long
        get() = _player?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L

    private val isPlaying: Boolean get() = _player?.isPlaying ?: false

    private val isPaused: Boolean
        get() = _player?.let {
            !it.isPlaying && it.playbackState != Player.STATE_IDLE && it.playbackState != Player.STATE_ENDED
        } ?: false

    private var _volume: Float = 1.0f
    override var volume
        get() = _volume
        set(value) {
            _volume = value
            _player?.volume = value
        }

    private val artworkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var artworkJob: Job? = null

    override fun init() {
        close()
        _player = playerBuilder().apply {
            volume = _volume
        }

        focusRegistration = AudioFocusRegistration.request(
            audioManager = audioManager,
            audioAttributes = _player!!.audioAttributes,
            focusGain = focusGain
        )
        isPlayerInit = true
    }

    override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
        if (!isPlayerInit)
            init()
        // Force recreation of player next time its needed
        isPlayerInit = false
        val player = _player
        check(player != null) { "player not initialized" }

        player.addListener(getPlayerListener(onCompletion))
        runCatching {
            for (mediaUri in mediaUris) {
                if (mediaUri.isNotEmpty()) {
                    player.addMediaItem(MediaItem.fromUri(mediaUri))
                } else Timber.w("Ignoring empty media uri")
            }
            player.playWhenReady = true
            player.prepare()
        }.onFailure {
            Timber.e(it, "Error playing media $mediaUris")
            onCompletion()
            close()
        }
    }

    override fun pause() {
        if (isPlaying)
            _player?.pause()
    }

    override fun unpause() {
        if (isPaused)
            _player?.play()
    }

    override fun skipToNext() {
        _player?.seekToNextMediaItem()
    }

    override fun skipToPrevious() {
        _player?.seekToPreviousMediaItem()
    }

    override fun stop() {
        close()
    }

    private fun getPlayerListener(onCompletion: () -> Unit) = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Timber.d("Playback state changed to $playbackState")
            // If there's a playback error then the player state will return to idle
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                onCompletion()
                close()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying)
                _state.value = AudioPlayerState.PLAYING
            else if (isPaused)
                _state.value = AudioPlayerState.PAUSED
            else
                _state.value = AudioPlayerState.IDLE
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val rawTitle = mediaMetadata.title?.toString()
            val rawArtist = mediaMetadata.artist?.toString()
            // ICY streams encode both as "Artist - Title" in the title field with no separate artist.
            // If we have a title but no artist, try to split on " - ".
            if (rawTitle != null && rawArtist == null && rawTitle.contains(" - ")) {
                val idx = rawTitle.indexOf(" - ")
                _mediaArtist.value = rawTitle.substring(0, idx)
                _mediaTitle.value = rawTitle.substring(idx + 3)
            } else {
                _mediaTitle.value = rawTitle
                _mediaArtist.value = rawArtist
            }
            _artworkData.value = mediaMetadata.artworkData
            _artworkUri.value = mediaMetadata.artworkUri?.toString()
            // If no artwork from stream, look it up via iTunes
            if (mediaMetadata.artworkData == null && mediaMetadata.artworkUri == null) {
                fetchArtworkFromItunes(_mediaArtist.value, _mediaTitle.value)
            }
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                if (entry is IcyInfo) {
                    val raw = entry.rawMetadata?.toString(Charsets.UTF_8) ?: continue
                    Timber.d("ICY raw: %s", raw)
                    // Extract any URL-valued field (StreamImage, StreamUrl, etc.)
                    val artworkUrl = ICY_FIELD_REGEX.findAll(raw)
                        .firstOrNull {
                            it.groupValues[1].contains("image", ignoreCase = true) ||
                            it.groupValues[1].contains("url", ignoreCase = true)
                        }
                        ?.groupValues?.get(2)
                        ?.takeIf { it.isNotEmpty() && it != "null" }
                    if (artworkUrl != null) {
                        _artworkUri.value = artworkUrl
                    }
                }
            }
        }
    }

    private fun fetchArtworkFromItunes(artist: String?, title: String?) {
        if (artist == null || title == null) return
        artworkJob?.cancel()
        artworkJob = artworkScope.launch {
            runCatching {
                val query = URLEncoder.encode("$artist $title", "UTF-8")
                val json = JSONObject(URL("https://itunes.apple.com/search?term=$query&entity=song&limit=1&media=music").readText())
                val results = json.getJSONArray("results")
                if (results.length() > 0) {
                    val artwork = results.getJSONObject(0)
                        .optString("artworkUrl100")
                        .replace("100x100bb", "600x600bb") // Upscale to 600px
                        .takeIf { it.isNotEmpty() }
                    if (artwork != null) {
                        Timber.d("iTunes artwork: $artwork")
                        _artworkUri.value = artwork
                    }
                }
            }.onFailure { Timber.w(it, "iTunes artwork lookup failed") }
        }
    }

    override fun close() {
        artworkJob?.cancel()
        artworkJob = null
        isPlayerInit = false
        _player?.release()
        _player = null
        focusRegistration?.close()
        focusRegistration = null
        _state.value = AudioPlayerState.IDLE
        _mediaTitle.value = null
        _mediaArtist.value = null
        _artworkData.value = null
        _artworkUri.value = null
    }

    companion object {
        // Matches ICY metadata fields: FieldName='value';
        private val ICY_FIELD_REGEX = Regex("""(\w+)='([^']*)';?""")
    }
}
