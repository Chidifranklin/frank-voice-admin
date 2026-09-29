package com.example.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VoiceSettings(
    val primaryWakeWord: String = "Hey Frank",
    val wakeAliases: List<String> = listOf("Frank", "Jarvis", "Computer"),
    val speechPitch: Float = 1.05f,
    val speechRate: Float = 1.0f,
    val isAutoListenAfterCommand: Boolean = true,
    val isInternetSearchPermitted: Boolean = false,
    val isMuteVoiceBeeps: Boolean = true
)

class VoiceSettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("frank_voice_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()

    private fun loadSettings(): VoiceSettings {
        val primary = prefs.getString("primary_wake_word", "Hey Frank") ?: "Hey Frank"
        val aliasesString = prefs.getString("wake_aliases", "Frank,Jarvis,Computer") ?: "Frank,Jarvis,Computer"
        val aliases = aliasesString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val pitch = prefs.getFloat("speech_pitch", 1.05f)
        val rate = prefs.getFloat("speech_rate", 1.0f)
        val autoListen = prefs.getBoolean("auto_listen", true)
        val internetSearch = prefs.getBoolean("internet_search_permitted", false)
        val muteBeeps = prefs.getBoolean("mute_voice_beeps", true)

        return VoiceSettings(
            primaryWakeWord = primary,
            wakeAliases = aliases,
            speechPitch = pitch,
            speechRate = rate,
            isAutoListenAfterCommand = autoListen,
            isInternetSearchPermitted = internetSearch,
            isMuteVoiceBeeps = muteBeeps
        )
    }

    fun updateMuteVoiceBeeps(muted: Boolean) {
        prefs.edit().putBoolean("mute_voice_beeps", muted).apply()
        _settings.value = _settings.value.copy(isMuteVoiceBeeps = muted)
    }

    fun updateInternetSearchPermitted(permitted: Boolean) {
        prefs.edit().putBoolean("internet_search_permitted", permitted).apply()
        _settings.value = _settings.value.copy(isInternetSearchPermitted = permitted)
    }

    fun updatePrimaryWakeWord(word: String) {
        val clean = word.trim()
        if (clean.isBlank()) return
        prefs.edit().putString("primary_wake_word", clean).apply()
        _settings.value = _settings.value.copy(primaryWakeWord = clean)
        updateConstants()
    }

    fun addWakeAlias(alias: String) {
        val clean = alias.trim()
        if (clean.isBlank()) return
        val current = _settings.value.wakeAliases.toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            prefs.edit().putString("wake_aliases", current.joinToString(",")).apply()
            _settings.value = _settings.value.copy(wakeAliases = current)
            updateConstants()
        }
    }

    fun removeWakeAlias(alias: String) {
        val current = _settings.value.wakeAliases.toMutableList()
        if (current.remove(alias)) {
            prefs.edit().putString("wake_aliases", current.joinToString(",")).apply()
            _settings.value = _settings.value.copy(wakeAliases = current)
            updateConstants()
        }
    }

    fun updateSpeechPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat("speech_pitch", clamped).apply()
        _settings.value = _settings.value.copy(speechPitch = clamped)
    }

    fun updateSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat("speech_rate", clamped).apply()
        _settings.value = _settings.value.copy(speechRate = clamped)
    }

    fun updateAutoListen(enabled: Boolean) {
        prefs.edit().putBoolean("auto_listen", enabled).apply()
        _settings.value = _settings.value.copy(isAutoListenAfterCommand = enabled)
    }

    private fun updateConstants() {
        val list = mutableListOf<String>()
        val primary = _settings.value.primaryWakeWord.lowercase()
        list.add(primary)
        _settings.value.wakeAliases.forEach {
            list.add(it.lowercase())
        }
        FrankVoiceConstants.customWakeVariants = list.distinct()
    }

    init {
        updateConstants()
    }
}
