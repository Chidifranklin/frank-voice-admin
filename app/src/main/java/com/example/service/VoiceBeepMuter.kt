package com.example.service

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Manages audio stream suppression during continuous voice recognition and background idle listening.
 *
 * Android's SpeechRecognizer emits audible beep/tone alerts on start-listening,
 * end-of-speech, and errors/timeouts. When running continuously in the background or
 * waiting for voice commands, these regular beeps can be disruptive.
 *
 * VoiceBeepMuter suppresses the relevant audio streams (STREAM_MUSIC, STREAM_SYSTEM,
 * and STREAM_NOTIFICATION) while speech recognition is active or idle in the background,
 * and restores audio when Text-To-Speech speaks, media playback starts, or the service stops.
 */
class VoiceBeepMuter(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private var isMuted = false
    private var savedMusicVolume: Int = -1
    private var savedSystemVolume: Int = -1

    @Synchronized
    fun muteBeeps() {
        val am = audioManager ?: return
        try {
            // Save original volume before muting
            val currentMusicVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentMusicVol > 0 && savedMusicVolume <= 0) {
                savedMusicVolume = currentMusicVol
            }

            val currentSysVol = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
            if (currentSysVol > 0 && savedSystemVolume <= 0) {
                savedSystemVolume = currentSysVol
            }

            // 1. Mute STREAM_MUSIC (Where Google SpeechRecognizer plays start/end/error tones)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                am.setStreamMute(AudioManager.STREAM_MUSIC, true)
            }

            // Fallback for devices where ADJUST_MUTE doesn't fully suppress recognizer audio
            try {
                if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                }
            } catch (e: Exception) {
                Log.d(TAG, "setStreamVolume 0 fallback: ${e.message}")
            }

            // 2. Mute STREAM_SYSTEM (System prompt/key sounds)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    am.setStreamMute(AudioManager.STREAM_SYSTEM, true)
                }
            } catch (e: Exception) {
                Log.d(TAG, "System stream mute non-critical: ${e.message}")
            }

            // 3. Mute STREAM_NOTIFICATION if Do Not Disturb access allows
            try {
                val canModifyDnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    notificationManager?.isNotificationPolicyAccessGranted == true
                } else true

                if (canModifyDnd) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        am.setStreamMute(AudioManager.STREAM_NOTIFICATION, true)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Notification stream mute non-critical: ${e.message}")
            }

            isMuted = true
            Log.d(TAG, "Voice beep muter activated: Recognition beeps silenced")
        } catch (e: Exception) {
            Log.w(TAG, "Error muting recognition beeps: ${e.message}")
        }
    }

    @Synchronized
    fun unmuteBeeps() {
        val am = audioManager ?: return
        try {
            // Unmute STREAM_MUSIC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                am.setStreamMute(AudioManager.STREAM_MUSIC, false)
            }

            // If music volume is still 0 and we had saved a volume, restore it
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 && savedMusicVolume > 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVolume, 0)
            }

            // Unmute STREAM_SYSTEM
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    am.setStreamMute(AudioManager.STREAM_SYSTEM, false)
                }
                if (am.getStreamVolume(AudioManager.STREAM_SYSTEM) == 0 && savedSystemVolume > 0) {
                    am.setStreamVolume(AudioManager.STREAM_SYSTEM, savedSystemVolume, 0)
                }
            } catch (_: Exception) {}

            // Unmute STREAM_NOTIFICATION
            try {
                val canModifyDnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    notificationManager?.isNotificationPolicyAccessGranted == true
                } else true

                if (canModifyDnd) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                    } else {
                        @Suppress("DEPRECATION")
                        am.setStreamMute(AudioManager.STREAM_NOTIFICATION, false)
                    }
                }
            } catch (_: Exception) {}

            isMuted = false
            Log.d(TAG, "Voice beep muter deactivated: Audio output unmuted")
        } catch (e: Exception) {
            Log.w(TAG, "Error unmuting recognition beeps: ${e.message}")
        }
    }

    fun isMuted(): Boolean = isMuted

    companion object {
        private const val TAG = "VoiceBeepMuter"
    }
}
