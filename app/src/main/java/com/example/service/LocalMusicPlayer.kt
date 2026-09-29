package com.example.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val baseFreq1: Double,
    val baseFreq2: Double,
    val isExternalLauncher: Boolean = false
)

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentTrack: MusicTrack? = null,
    val currentPositionSeconds: Int = 0,
    val waveformAmplitude: Float = 0f
)

class LocalMusicPlayer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    val availableTracks = listOf(
        MusicTrack("track_1", "Quantum Neural Focus", "Frank Ambient", "Binaural Lo-Fi", 220.0, 440.0),
        MusicTrack("track_2", "Cybernetic Rain", "OmniSynth", "Ambient Noise", 174.0, 396.0),
        MusicTrack("track_3", "Deep Memory Alpha Wave", "Privacy Core", "Focus Frequency", 136.1, 272.2),
        MusicTrack("track_4", "Super Admin Synthwave", "Aegis Sound", "Electro Ambient", 261.6, 523.2),
        MusicTrack("track_5", "Deep Space Meditation", "Zero-Cloud Audio", "432Hz Calm", 216.0, 432.0)
    )

    init {
        _playerState.value = _playerState.value.copy(currentTrack = availableTracks[0])
    }

    fun playTrack(track: MusicTrack) {
        stopPlayback()
        _playerState.value = _playerState.value.copy(
            isPlaying = true,
            currentTrack = track,
            currentPositionSeconds = 0
        )
        startAudioSynthesis(track)
    }

    fun playTrackByNameOrGenre(query: String): Pair<Boolean, String> {
        val q = query.lowercase().trim()
        val match = availableTracks.firstOrNull {
            it.title.lowercase().contains(q) ||
            it.genre.lowercase().contains(q) ||
            it.artist.lowercase().contains(q)
        } ?: availableTracks.first()

        playTrack(match)
        return Pair(true, "Playing \"${match.title}\" (${match.genre})")
    }

    fun togglePlayPause() {
        val current = _playerState.value
        if (current.isPlaying) {
            pausePlayback()
        } else {
            val track = current.currentTrack ?: availableTracks.first()
            playTrack(track)
        }
    }

    fun pausePlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}
        _playerState.value = _playerState.value.copy(isPlaying = false, waveformAmplitude = 0f)
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        _playerState.value = _playerState.value.copy(isPlaying = false, waveformAmplitude = 0f)
    }

    fun nextTrack() {
        val current = _playerState.value.currentTrack
        val currentIndex = availableTracks.indexOfFirst { it.id == current?.id }
        val nextIndex = if (currentIndex in availableTracks.indices) {
            (currentIndex + 1) % availableTracks.size
        } else 0
        playTrack(availableTracks[nextIndex])
    }

    fun previousTrack() {
        val current = _playerState.value.currentTrack
        val currentIndex = availableTracks.indexOfFirst { it.id == current?.id }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else availableTracks.size - 1
        playTrack(availableTracks[prevIndex])
    }

    fun launchSystemMusicApp(): Pair<Boolean, String> {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Pair(true, "Opened default music app")
            } else {
                // Try popular music apps or web fallback
                val ytMusic = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music")
                val spotify = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                when {
                    ytMusic != null -> {
                        context.startActivity(ytMusic.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        Pair(true, "Opened YouTube Music")
                    }
                    spotify != null -> {
                        context.startActivity(spotify.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        Pair(true, "Opened Spotify")
                    }
                    else -> {
                        // Play built-in private audio track
                        playTrack(availableTracks.first())
                        Pair(true, "Playing built-in privacy focus music")
                    }
                }
            }
        } catch (e: Exception) {
            playTrack(availableTracks.first())
            Pair(true, "Playing built-in ambient track: ${e.message}")
        }
    }

    private fun startAudioSynthesis(track: MusicTrack) {
        playbackJob = scope.launch(Dispatchers.Default) {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val trackInstance = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = trackInstance
            try {
                trackInstance.play()
            } catch (_: Exception) {
                return@launch
            }

            val buffer = ShortArray(bufferSize / 2)
            var sampleIndex = 0L
            val freq1 = track.baseFreq1
            val freq2 = track.baseFreq2

            var elapsedSeconds = 0
            var lastTimerTick = System.currentTimeMillis()

            while (isActive && _playerState.value.isPlaying) {
                for (i in buffer.indices) {
                    val time = sampleIndex.toDouble() / sampleRate
                    // Harmonic blend of frequencies with subtle LFO modulation for warm ambient feel
                    val lfo = 0.5 + 0.5 * sin(2.0 * Math.PI * 0.2 * time)
                    val s1 = sin(2.0 * Math.PI * freq1 * time)
                    val s2 = sin(2.0 * Math.PI * freq2 * time)
                    val s3 = sin(2.0 * Math.PI * (freq1 * 1.5) * time) * 0.3
                    val sampleValue = ((s1 * 0.4 + s2 * 0.4 + s3) * lfo * Short.MAX_VALUE * 0.3).toInt()
                    buffer[i] = sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    sampleIndex++
                }

                trackInstance.write(buffer, 0, buffer.size)

                // Update UI amplitude & position timer
                val now = System.currentTimeMillis()
                if (now - lastTimerTick >= 1000) {
                    elapsedSeconds++
                    lastTimerTick = now
                    val amp = (0.3f + (0.7f * (sin(elapsedSeconds.toDouble()).toFloat() + 1f) / 2f)).coerceIn(0.1f, 1f)
                    _playerState.value = _playerState.value.copy(
                        currentPositionSeconds = elapsedSeconds,
                        waveformAmplitude = amp
                    )
                }
            }
        }
    }
}
