package com.example.service

import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SuperAdminMediaController integrates android.media.session.MediaController
 * and android.media.session.MediaSession to allow the assistant to play, pause,
 * and skip tracks via voice commands within the foreground service.
 */
class SuperAdminMediaController(
    private val context: Context,
    private val localMusicPlayer: LocalMusicPlayer
) {
    companion object {
        private const val TAG = "SuperAdminMediaCtrl"
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private var internalMediaSession: MediaSession? = null

    private val _currentTrackTitle = MutableStateFlow("Quantum Neural Focus")
    val currentTrackTitle: StateFlow<String> = _currentTrackTitle.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        try {
            mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get MediaSessionManager: ${e.message}")
        }
        setupInternalMediaSession()
    }

    private fun setupInternalMediaSession() {
        try {
            internalMediaSession = MediaSession(context, "FrankMediaSession").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setPlaybackState(
                    PlaybackState.Builder()
                        .setActions(
                            PlaybackState.ACTION_PLAY or
                                    PlaybackState.ACTION_PAUSE or
                                    PlaybackState.ACTION_PLAY_PAUSE or
                                    PlaybackState.ACTION_SKIP_TO_NEXT or
                                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                                    PlaybackState.ACTION_STOP
                        )
                        .setState(PlaybackState.STATE_PAUSED, 0, 1.0f)
                        .build()
                )
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        playTrack()
                    }

                    override fun onPause() {
                        pauseTrack()
                    }

                    override fun onSkipToNext() {
                        skipToNext()
                    }

                    override fun onSkipToPrevious() {
                        skipToPrevious()
                    }

                    override fun onStop() {
                        pauseTrack()
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaSession: ${e.message}")
        }
    }

    /**
     * Finds active system MediaControllers.
     */
    private fun getActiveSystemControllers(): List<MediaController> {
        val controllers = mutableListOf<MediaController>()
        try {
            if (mediaSessionManager != null) {
                val activeSessions = mediaSessionManager?.getActiveSessions(null)
                if (activeSessions != null) {
                    controllers.addAll(activeSessions)
                }
            }
        } catch (e: SecurityException) {
            Log.d(TAG, "MediaSessionManager query fallback: ${e.message}")
        } catch (e: Exception) {
            Log.d(TAG, "getActiveSessions exception: ${e.message}")
        }
        return controllers
    }

    /**
     * Executes Play command using MediaController transports or local player.
     */
    fun playTrack(query: String = ""): Pair<Boolean, String> {
        val systemControllers = getActiveSystemControllers()
        var dispatched = false
        var targetAppName = ""

        for (controller in systemControllers) {
            val playbackState = controller.playbackState
            if (playbackState != null && playbackState.state != PlaybackState.STATE_PLAYING) {
                try {
                    controller.transportControls.play()
                    dispatched = true
                    targetAppName = controller.packageName ?: "active media player"
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing via MediaController: ${e.message}")
                }
            }
        }

        return if (dispatched) {
            _isPlaying.value = true
            updateInternalPlaybackState(PlaybackState.STATE_PLAYING)
            Pair(true, "Resumed playback on $targetAppName via MediaController")
        } else {
            val result = if (query.isNotBlank()) {
                localMusicPlayer.playTrackByNameOrGenre(query)
            } else {
                val track = localMusicPlayer.playerState.value.currentTrack
                    ?: localMusicPlayer.availableTracks.first()
                localMusicPlayer.playTrack(track)
                Pair(true, "Playing \"${track.title}\"")
            }
            _isPlaying.value = true
            _currentTrackTitle.value = localMusicPlayer.playerState.value.currentTrack?.title ?: "Ambient Focus"
            updateInternalPlaybackState(PlaybackState.STATE_PLAYING)
            result
        }
    }

    /**
     * Executes Pause command using MediaController transports or local player.
     */
    fun pauseTrack(): Pair<Boolean, String> {
        val systemControllers = getActiveSystemControllers()
        var pausedExternal = false
        var targetAppName = ""

        for (controller in systemControllers) {
            val playbackState = controller.playbackState
            if (playbackState != null && playbackState.state == PlaybackState.STATE_PLAYING) {
                try {
                    controller.transportControls.pause()
                    pausedExternal = true
                    targetAppName = controller.packageName ?: "active media"
                } catch (e: Exception) {
                    Log.e(TAG, "Error pausing via MediaController: ${e.message}")
                }
            }
        }

        // Also pause local audio synthesizer
        localMusicPlayer.pausePlayback()
        _isPlaying.value = false
        updateInternalPlaybackState(PlaybackState.STATE_PAUSED)

        return if (pausedExternal) {
            Pair(true, "Paused playback on $targetAppName via MediaController")
        } else {
            Pair(true, "Music playback paused")
        }
    }

    /**
     * Executes Skip to Next Track using MediaController transports or local player.
     */
    fun skipToNext(): Pair<Boolean, String> {
        val systemControllers = getActiveSystemControllers()
        var skippedExternal = false
        var targetAppName = ""

        for (controller in systemControllers) {
            try {
                controller.transportControls.skipToNext()
                skippedExternal = true
                targetAppName = controller.packageName ?: "active media"
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error skipping next via MediaController: ${e.message}")
            }
        }

        return if (skippedExternal) {
            Pair(true, "Skipped to next track on $targetAppName via MediaController")
        } else {
            localMusicPlayer.nextTrack()
            val newTrack = localMusicPlayer.playerState.value.currentTrack?.title ?: "Next Track"
            _currentTrackTitle.value = newTrack
            _isPlaying.value = true
            updateInternalPlaybackState(PlaybackState.STATE_PLAYING)
            Pair(true, "Skipped to next track: \"$newTrack\"")
        }
    }

    /**
     * Executes Skip to Previous Track using MediaController transports or local player.
     */
    fun skipToPrevious(): Pair<Boolean, String> {
        val systemControllers = getActiveSystemControllers()
        var skippedExternal = false
        var targetAppName = ""

        for (controller in systemControllers) {
            try {
                controller.transportControls.skipToPrevious()
                skippedExternal = true
                targetAppName = controller.packageName ?: "active media"
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error skipping previous via MediaController: ${e.message}")
            }
        }

        return if (skippedExternal) {
            Pair(true, "Skipped to previous track on $targetAppName via MediaController")
        } else {
            localMusicPlayer.previousTrack()
            val newTrack = localMusicPlayer.playerState.value.currentTrack?.title ?: "Previous Track"
            _currentTrackTitle.value = newTrack
            _isPlaying.value = true
            updateInternalPlaybackState(PlaybackState.STATE_PLAYING)
            Pair(true, "Skipped to previous track: \"$newTrack\"")
        }
    }

    private fun updateInternalPlaybackState(state: Int) {
        try {
            internalMediaSession?.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                                PlaybackState.ACTION_PAUSE or
                                PlaybackState.ACTION_SKIP_TO_NEXT or
                                PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(state, 0, 1.0f)
                    .build()
            )
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            internalMediaSession?.isActive = false
            internalMediaSession?.release()
            internalMediaSession = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaSession: ${e.message}")
        }
    }
}
