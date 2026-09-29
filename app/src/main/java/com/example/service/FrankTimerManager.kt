package com.example.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class ActiveTimer(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val totalSeconds: Long,
    val remainingSeconds: Long,
    val isRunning: Boolean,
    val createdAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalSeconds > 0) (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedRemaining: String
        get() {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            val hrs = mins / 60
            val remMins = mins % 60
            return if (hrs > 0) {
                String.format(java.util.Locale.US, "%d:%02d:%02d", hrs, remMins, secs)
            } else {
                String.format(java.util.Locale.US, "%02d:%02d", remMins, secs)
            }
        }
}

data class StopwatchState(
    val elapsedMillis: Long = 0L,
    val isRunning: Boolean = false,
    val laps: List<Long> = emptyList()
) {
    val formattedTime: String
        get() {
            val totalSec = elapsedMillis / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val hundredths = (elapsedMillis % 1000) / 10
            return String.format(java.util.Locale.US, "%02d:%02d.%02d", min, sec, hundredths)
        }
}

class FrankTimerManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private var stopwatchJob: Job? = null

    private val _timers = MutableStateFlow<List<ActiveTimer>>(emptyList())
    val timers: StateFlow<List<ActiveTimer>> = _timers.asStateFlow()

    private val _stopwatch = MutableStateFlow(StopwatchState())
    val stopwatch: StateFlow<StopwatchState> = _stopwatch.asStateFlow()

    private val _timerFinishedEvents = MutableSharedFlow<ActiveTimer>(extraBufferCapacity = 10)
    val timerFinishedEvents: SharedFlow<ActiveTimer> = _timerFinishedEvents.asSharedFlow()

    init {
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = _timers.value
                if (current.isEmpty()) continue

                val updated = mutableListOf<ActiveTimer>()
                for (timer in current) {
                    if (timer.isRunning) {
                        val newRemaining = timer.remainingSeconds - 1
                        if (newRemaining <= 0) {
                            onTimerCompleted(timer)
                        } else {
                            updated.add(timer.copy(remainingSeconds = newRemaining))
                        }
                    } else {
                        updated.add(timer)
                    }
                }
                _timers.value = updated
            }
        }
    }

    private fun onTimerCompleted(timer: ActiveTimer) {
        scope.launch {
            _timerFinishedEvents.emit(timer)
            playChimeAndVibrate()
        }
    }

    fun startTimer(label: String, seconds: Long): ActiveTimer {
        val safeSeconds = seconds.coerceAtLeast(1)
        val timer = ActiveTimer(
            label = label.ifBlank { "$safeSeconds second timer" },
            totalSeconds = safeSeconds,
            remainingSeconds = safeSeconds,
            isRunning = true
        )
        _timers.value = _timers.value + timer
        return timer
    }

    fun pauseTimer(id: String) {
        _timers.value = _timers.value.map {
            if (it.id == id) it.copy(isRunning = false) else it
        }
    }

    fun resumeTimer(id: String) {
        _timers.value = _timers.value.map {
            if (it.id == id) it.copy(isRunning = true) else it
        }
    }

    fun cancelTimer(id: String) {
        _timers.value = _timers.value.filterNot { it.id == id }
    }

    fun cancelAllTimers() {
        _timers.value = emptyList()
    }

    fun getActiveTimerSummary(): String {
        val current = _timers.value
        return if (current.isEmpty()) {
            "No active timers running."
        } else {
            val first = current.first()
            "${first.label} has ${first.formattedRemaining} remaining."
        }
    }

    // --- Stopwatch ---

    fun startStopwatch() {
        if (_stopwatch.value.isRunning) return
        _stopwatch.value = _stopwatch.value.copy(isRunning = true)
        stopwatchJob?.cancel()
        stopwatchJob = scope.launch {
            var lastTick = System.currentTimeMillis()
            while (isActive && _stopwatch.value.isRunning) {
                delay(50)
                val now = System.currentTimeMillis()
                val delta = now - lastTick
                lastTick = now
                _stopwatch.value = _stopwatch.value.copy(
                    elapsedMillis = _stopwatch.value.elapsedMillis + delta
                )
            }
        }
    }

    fun pauseStopwatch() {
        _stopwatch.value = _stopwatch.value.copy(isRunning = false)
        stopwatchJob?.cancel()
    }

    fun resetStopwatch() {
        pauseStopwatch()
        _stopwatch.value = StopwatchState()
    }

    fun lapStopwatch() {
        val current = _stopwatch.value
        if (current.isRunning) {
            _stopwatch.value = current.copy(laps = current.laps + current.elapsedMillis)
        }
    }

    private fun playChimeAndVibrate() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1200)
        } catch (e: Exception) {
            Log.w("FrankTimerManager", "ToneGenerator unavailable: ${e.message}")
        }

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                mgr?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 400), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(600)
            }
        } catch (_: Exception) {}
    }

    fun release() {
        tickerJob?.cancel()
        stopwatchJob?.cancel()
    }
}
