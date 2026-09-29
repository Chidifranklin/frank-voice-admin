package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.entities.MemoryEntity
import com.example.data.repository.OmniRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class VoiceCommandEvent(
    val userText: String,
    val replyText: String,
    val badge: String,
    val timestamp: Long = System.currentTimeMillis()
)

class VoiceActivationService : Service(), RecognitionListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastRecognitionActiveTimestamp = System.currentTimeMillis()

    private lateinit var database: AppDatabase
    private lateinit var repository: OmniRepository
    private lateinit var deviceManager: SuperAdminDeviceManager
    private lateinit var fileManager: FileManager
    private lateinit var fileSystemHelper: FileSystemHelper
    private lateinit var musicPlayer: LocalMusicPlayer
    private lateinit var mediaController: SuperAdminMediaController
    private lateinit var ttsManager: SpeechTtsManager
    private lateinit var reminderScheduler: SuperAdminReminderScheduler
    private lateinit var overlayManager: FrankFloatingOverlayManager
    private lateinit var timerManager: FrankTimerManager
    private lateinit var voiceSettingsManager: VoiceSettingsManager
    private lateinit var quickCameraManager: QuickCameraManager
    private lateinit var beepMuter: VoiceBeepMuter
    private val intentParser = IntentParser()
    private var lastFrankSpokenUtterance: String = ""

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VoiceActivationService onCreate: Initializing continuous voice listener")
        activeInstance = this

        val app = application
        database = AppDatabase.getInstance(app)
        repository = OmniRepository(database)
        deviceManager = SuperAdminDeviceManager(app)
        fileManager = FileManager(app, repository)
        fileSystemHelper = fileManager.fileSystemHelper
        musicPlayer = LocalMusicPlayer(app)
        mediaController = SuperAdminMediaController(app, musicPlayer)
        ttsManager = SpeechTtsManager(app)
        reminderScheduler = SuperAdminReminderScheduler(app, repository)
        overlayManager = FrankFloatingOverlayManager(this)
        timerManager = FrankTimerManager(app)
        voiceSettingsManager = VoiceSettingsManager(app)
        quickCameraManager = QuickCameraManager(app)
        beepMuter = VoiceBeepMuter(this)

        // Apply saved persona settings
        val settings = voiceSettingsManager.settings.value
        ttsManager.setPitch(settings.speechPitch)
        ttsManager.setSpeechRate(settings.speechRate)

        acquireWakeLock()
        createNotificationChannel()
        startForegroundWithNotification(
            title = "Frank: Always Listening",
            content = "Constantly active • Speak anytime without tapping (open or minimized)"
        )

        _isServiceRunning.value = true
        _lastStatusMessage.value = "Always Active: Listening continuously..."

        // Observe timer alarms
        serviceScope.launch {
            timerManager.timerFinishedEvents.collect { finishedTimer ->
                val alert = "Timer finished! ${finishedTimer.label} is done."
                lastFrankSpokenUtterance = alert
                ttsManager.speak(alert)
                overlayManager.showExecution("Timer Alert", alert, "TIMER FINISHED")
                _commandExecutedEvents.emit(
                    VoiceCommandEvent(
                        userText = "Timer Finished",
                        replyText = alert,
                        badge = "TIMER ALARM"
                    )
                )
            }
        }
        _lastStatusMessage.value = "Always Active: Listening continuously..."

        // Observe TTS state: whenever TTS finishes speaking, immediately resume listening after delay for echo dissipation
        serviceScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                if (speaking) {
                    beepMuter.unmuteBeeps()
                } else if (_isServiceRunning.value) {
                    mainHandler.postDelayed({
                        if (_isServiceRunning.value && !ttsManager.isSpeaking.value && !_isHotwordListening.value) {
                            if (voiceSettingsManager.settings.value.isMuteVoiceBeeps && !musicPlayer.playerState.value.isPlaying) {
                                beepMuter.muteBeeps()
                            }
                            destroyAndRecreateRecognizer()
                        }
                    }, 400)
                }
            }
        }

        // Observe media player: ensure sound plays when user wants music, but mute beeps when idle
        serviceScope.launch {
            musicPlayer.playerState.collect { state ->
                if (state.isPlaying) {
                    beepMuter.unmuteBeeps()
                } else if (_isServiceRunning.value && voiceSettingsManager.settings.value.isMuteVoiceBeeps) {
                    beepMuter.muteBeeps()
                }
            }
        }

        // Observe voice settings changes
        serviceScope.launch {
            voiceSettingsManager.settings.collect { settings ->
                if (!settings.isMuteVoiceBeeps) {
                    beepMuter.unmuteBeeps()
                } else if (!musicPlayer.playerState.value.isPlaying && _isServiceRunning.value) {
                    beepMuter.muteBeeps()
                }
            }
        }

        // Background watchdog to prevent recognizer deadlocks or silent termination
        startWatchdog()

        // Kick off continuous speech recognition
        destroyAndRecreateRecognizer()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Frank:ContinuousVoiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max safety
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_VOICE -> {
                deviceManager.vibrate(60)
                val prompt = "Frank is listening for your command..."
                beepMuter.unmuteBeeps()
                ttsManager.speak("Yes, Frank is listening.")
                updateNotification(prompt)
                scheduleRestartListening(150)
            }
            ACTION_TOGGLE_TORCH -> {
                val nextState = !deviceManager.isFlashlightOn
                val (_, msg) = deviceManager.toggleFlashlight(nextState)
                updateNotification(msg)
                deviceManager.vibrate(40)
            }
            ACTION_MEDIA_PLAY -> {
                val (_, msg) = mediaController.playTrack()
                updateNotification(msg)
            }
            ACTION_MEDIA_PAUSE -> {
                val (_, msg) = mediaController.pauseTrack()
                updateNotification(msg)
            }
            ACTION_MEDIA_NEXT -> {
                val (_, msg) = mediaController.skipToNext()
                updateNotification(msg)
            }
            ACTION_MEDIA_PREV -> {
                val (_, msg) = mediaController.skipToPrevious()
                updateNotification(msg)
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved: Frank app minimized or closed from Recents. Ensuring voice service stays running.")
        try {
            val restartServiceIntent = Intent(applicationContext, VoiceActivationService::class.java).apply {
                setPackage(packageName)
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext,
                999,
                restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                restartPendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm restart on task removed: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Frank Always-Active Voice Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors continuously for voice commands without requiring manual button taps"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification(title: String, content: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val triggerIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, VoiceActivationService::class.java).apply {
                action = ACTION_TRIGGER_VOICE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val torchIntent = PendingIntent.getService(
            this,
            6,
            Intent(this, VoiceActivationService::class.java).apply {
                action = ACTION_TOGGLE_TORCH
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, VoiceActivationService::class.java).apply {
                action = ACTION_MEDIA_PAUSE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceActivationService::class.java).apply {
                action = ACTION_STOP_SERVICE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Speak", triggerIntent)
            .addAction(android.R.drawable.ic_menu_camera, "Torch", torchIntent)
            .addAction(android.R.drawable.ic_media_pause, "Audio", playPauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForeground: ${e.message}", e)
        }
    }

    private fun updateNotification(content: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val triggerIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, VoiceActivationService::class.java).apply {
                    action = ACTION_TRIGGER_VOICE
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val stopIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, VoiceActivationService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val torchIntent = PendingIntent.getService(
                this,
                6,
                Intent(this, VoiceActivationService::class.java).apply {
                    action = ACTION_TOGGLE_TORCH
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Frank: Super Admin")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_btn_speak_now, "Speak", triggerIntent)
                .addAction(android.R.drawable.ic_menu_camera, "Torch", torchIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification: ${e.message}")
        }
    }

    /**
     * Resets previous SpeechRecognizer instance and sets up a clean listener.
     * Crucial on Android: SpeechRecognizer CANNOT be reused after error/results
     * without leading to ERROR_CLIENT or ERROR_RECOGNIZER_BUSY.
     */
    @Synchronized
    private fun destroyAndRecreateRecognizer() {
        if (!_isServiceRunning.value) return

        // If TTS is currently speaking, do not record its output
        if (ttsManager.isSpeaking.value) {
            Log.d(TAG, "TTS currently speaking; delaying recognizer start")
            return
        }

        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning old recognizer: ${e.message}")
        }
        speechRecognizer = null

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer unavailable on device")
            _lastStatusMessage.value = "Speech recognition engine unavailable."
            return
        }

        try {
            val recognizer = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(this)
                }
            } catch (eDevice: Exception) {
                Log.w(TAG, "On-device recognizer initialization failed, using standard recognizer: ${eDevice.message}")
                SpeechRecognizer.createSpeechRecognizer(this)
            }

            if (recognizer == null) {
                Log.w(TAG, "Recognizer instance is null")
                scheduleRestartListening(2000)
                return
            }

            recognizer.setRecognitionListener(this@VoiceActivationService)
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra("android.speech.extra.DICTATION_MODE", true)
            }
            isListening = true
            _isHotwordListening.value = true
            lastRecognitionActiveTimestamp = System.currentTimeMillis()

            // Suppress SpeechRecognizer start/end/error beeps during idle listening or voice commands
            if (voiceSettingsManager.settings.value.isMuteVoiceBeeps && !musicPlayer.playerState.value.isPlaying) {
                beepMuter.muteBeeps()
            }

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech listening: ${e.message}", e)
            scheduleRestartListening(1500)
        }
    }

    private fun scheduleRestartListening(delayMillis: Long = 600) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (_isServiceRunning.value) {
                destroyAndRecreateRecognizer()
            }
        }, delayMillis)
    }

    private fun startWatchdog() {
        serviceScope.launch {
            while (isActive) {
                delay(6000)
                if (_isServiceRunning.value && !ttsManager.isSpeaking.value) {
                    val idleTime = System.currentTimeMillis() - lastRecognitionActiveTimestamp
                    if (idleTime > 9000 || !_isHotwordListening.value) {
                        Log.d(TAG, "Watchdog: Resuming continuous voice recognition loop")
                        destroyAndRecreateRecognizer()
                    }
                }
            }
        }
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        _isHotwordListening.value = true
        lastRecognitionActiveTimestamp = System.currentTimeMillis()
    }

    override fun onBeginningOfSpeech() {
        lastRecognitionActiveTimestamp = System.currentTimeMillis()
    }

    override fun onRmsChanged(rmsdB: Float) {
        _audioLevel.value = rmsdB
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isHotwordListening.value = false
        if (voiceSettingsManager.settings.value.isMuteVoiceBeeps && !musicPlayer.playerState.value.isPlaying) {
            beepMuter.muteBeeps()
        }
    }

    override fun onError(error: Int) {
        _isHotwordListening.value = false
        if (voiceSettingsManager.settings.value.isMuteVoiceBeeps && !musicPlayer.playerState.value.isPlaying) {
            beepMuter.muteBeeps()
        }
        val delay = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 800L
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH -> 300L
            else -> 600L
        }
        scheduleRestartListening(delay)
    }

    override fun onResults(results: Bundle?) {
        _isHotwordListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            handleRecognizedVoice(text)
        } else {
            scheduleRestartListening(400)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            _lastHeardUtterance.value = text
            lastRecognitionActiveTimestamp = System.currentTimeMillis()
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    /**
     * Handles speech caught continuously in the foreground or background.
     * Supports both wake word ("Hey Frank ...") AND direct commands
     * ("turn on flashlight", "what is my wifi", "play music", etc.) without requiring tapping!
     */
    fun handleRecognizedVoice(spokenText: String) {
        try {
            val clean = spokenText.trim()
            if (clean.isBlank()) {
                scheduleRestartListening(300)
                return
            }

            // Suppress speaker mic audio echo / acoustic feedback loop
            val lowerClean = clean.lowercase()
            if (lastFrankSpokenUtterance.isNotBlank()) {
                val lowerSpoken = lastFrankSpokenUtterance.lowercase()
                if (lowerClean == lowerSpoken ||
                    (lowerSpoken.length > 8 && lowerClean.contains(lowerSpoken)) ||
                    (lowerClean.length > 8 && lowerSpoken.contains(lowerClean))
                ) {
                    Log.d(TAG, "Suppressed acoustic mic echo of Frank's own speech: '$clean'")
                    scheduleRestartListening(400)
                    return
                }
            }
            if (lowerClean.contains("frank received") || lowerClean.contains("super admin ready") ||
                lowerClean.contains("you can speak any command") || lowerClean.contains("standing by for command")
            ) {
                Log.d(TAG, "Suppressed system prompt echo loop: '$clean'")
                scheduleRestartListening(400)
                return
            }

            _lastHeardUtterance.value = clean
            Log.d(TAG, "Recognized continuous voice: '$clean'")
            overlayManager.showListening(clean)

            val (isTriggered, extractedCommand) = FrankVoiceConstants.extractCommandFromWakeWord(clean)
            val continuousMode = _isContinuousListeningMode.value

            if (isTriggered) {
                deviceManager.vibrate(80)
                if (extractedCommand.isNotBlank()) {
                    updateNotification("Executing: $extractedCommand")
                    _lastStatusMessage.value = "Hey Frank activated: \"$extractedCommand\""
                    executeCommand(extractedCommand)
                } else {
                    val greeting = "Yes, I am Frank. Ready for your command."
                    _lastStatusMessage.value = "Hey Frank awake: Standing by..."
                    updateNotification("Frank listening for your command...")
                    lastFrankSpokenUtterance = greeting
                    beepMuter.unmuteBeeps()
                    ttsManager.speak(greeting)
                    overlayManager.showExecution("Hey Frank", greeting, "HEY FRANK")
                    serviceScope.launch {
                        _wakeWordEvents.emit("Wake word 'Hey Frank' activated.")
                        _commandExecutedEvents.emit(
                            VoiceCommandEvent(
                                userText = "Hey Frank",
                                replyText = greeting,
                                badge = "HEY FRANK ACTIVE"
                            )
                        )
                    }
                }
            } else if (continuousMode && clean.isNotBlank()) {
                // Direct command uttered without wake word in continuous listening mode
                deviceManager.vibrate(40)
                updateNotification("Executing: $clean")
                _lastStatusMessage.value = "Continuous Voice: \"$clean\""
                executeCommand(clean)
            } else {
                // Restart listening
                scheduleRestartListening(400)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error handling recognized voice: ${e.message}", e)
            scheduleRestartListening(1000)
        }
    }

    private fun executeCommand(commandText: String) {
        // Immediately pause / cancel speech recognition so microphone does not capture TTS audio
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        _isHotwordListening.value = false

        serviceScope.launch {
            var replySpoken = ""
            var badgeText = "SUPER ADMIN"
            try {
                val parsed = intentParser.parseOnDevice(commandText)
                _lastStatusMessage.value = "Frank executing: ${parsed.explanation}"

                repository.logAction(
                    actionType = "VOICE_COMMAND",
                    description = "Spoken: \"$commandText\"",
                    details = "Executed via Continuous Voice Service: ${parsed.action::class.simpleName}"
                )

                when (val action = parsed.action) {
                    is AdminAction.QueryTime -> {
                        badgeText = "TIME"
                        val timeFormat = java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
                        replySpoken = "The time is ${timeFormat.format(java.util.Date())}."
                    }
                    is AdminAction.QueryDate -> {
                        badgeText = "DATE"
                        val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                        replySpoken = "Today is ${dateFormat.format(java.util.Date())}."
                    }
                    is AdminAction.BringFrankToFront -> {
                        badgeText = "SUPER ADMIN"
                        val (success, message) = deviceManager.bringFrankToForeground()
                        replySpoken = message
                    }
                    is AdminAction.OpenApp -> {
                        badgeText = "APP LAUNCH"
                        val (success, message) = deviceManager.findAndLaunchAppByName(action.appName)
                        replySpoken = message
                    }
                    is AdminAction.PlayMusic -> {
                        badgeText = "MUSIC PLAYER"
                        val (success, message) = mediaController.playTrack(action.query)
                        replySpoken = message
                    }
                    is AdminAction.MediaControl -> {
                        badgeText = "MEDIA CONTROL"
                        val (success, message) = when (action.command.uppercase()) {
                            "PLAY" -> mediaController.playTrack(action.param)
                            "PAUSE", "STOP" -> mediaController.pauseTrack()
                            "NEXT" -> mediaController.skipToNext()
                            "PREVIOUS" -> mediaController.skipToPrevious()
                            else -> mediaController.playTrack()
                        }
                        replySpoken = message
                    }
                    is AdminAction.ManageSettings -> {
                        badgeText = "HARDWARE"
                        when (action.settingType.uppercase()) {
                            "FLASHLIGHT" -> {
                                val turnOn = action.param.uppercase() == "ON"
                                val (success, message) = deviceManager.toggleFlashlight(turnOn)
                                replySpoken = message
                            }
                            "VOLUME" -> {
                                val percent = when (action.param.uppercase()) {
                                    "0" -> 0f
                                    "100" -> 1f
                                    "UP" -> 0.8f
                                    "DOWN" -> 0.2f
                                    else -> (action.param.toFloatOrNull() ?: 50f) / 100f
                                }
                                val (success, message) = deviceManager.setMusicVolumePercent(percent)
                                replySpoken = message
                            }
                            "BATTERY_OPTIMIZATION" -> {
                                val (success, message) = deviceManager.requestIgnoreBatteryOptimization()
                                replySpoken = message
                            }
                            "OVERLAY", "OVERRIDE" -> {
                                val (success, message) = deviceManager.requestOverlayPermission()
                                replySpoken = message
                            }
                            "ACCESSIBILITY" -> {
                                val (success, message) = FrankAccessibilityService.openAccessibilitySettings(this@VoiceActivationService)
                                replySpoken = message
                            }
                            "INTERNET_SEARCH" -> {
                                val enable = action.param.uppercase() == "ON"
                                voiceSettingsManager.updateInternetSearchPermitted(enable)
                                replySpoken = if (enable) {
                                    "Internet search permission granted. Frank will automatically search the web for unrecognized commands."
                                } else {
                                    "Internet search permission revoked. Frank will not search the internet for unrecognized commands."
                                }
                            }
                            "NOTIFICATION", "NOTIFICATIONS" -> {
                                val (success, message) = FrankNotificationListenerService.openNotificationAccessSettings(this@VoiceActivationService)
                                replySpoken = message
                            }
                            "MUTE_BEEPS", "MUTE_BEEP", "BEEP", "BEEPS" -> {
                                val mute = action.param.uppercase() == "ON"
                                voiceSettingsManager.updateMuteVoiceBeeps(mute)
                                if (mute) {
                                    beepMuter.muteBeeps()
                                    replySpoken = "Voice recognition beep sounds muted. Frank will listen silently in the background."
                                } else {
                                    beepMuter.unmuteBeeps()
                                    replySpoken = "Voice recognition beep sounds unmuted."
                                }
                            }
                            else -> {
                                val (success, message) = deviceManager.openSettings(action.settingType)
                                replySpoken = message
                            }
                        }
                    }
                    is AdminAction.GlobalNavigation -> {
                        badgeText = "ACCESSIBILITY"
                        val (success, message) = when (action.navigationType.uppercase()) {
                            "HOME" -> FrankAccessibilityService.goHome()
                            "BACK" -> FrankAccessibilityService.goBack()
                            "NOTIFICATIONS" -> FrankAccessibilityService.openNotifications()
                            "QUICK_SETTINGS" -> FrankAccessibilityService.openQuickSettings()
                            "RECENTS" -> FrankAccessibilityService.showRecentApps()
                            "SCREENSHOT" -> FrankAccessibilityService.takeScreenshot()
                            "LOCK" -> FrankAccessibilityService.lockScreen()
                            "SCROLL_DOWN" -> FrankAccessibilityService.scrollDown()
                            "SCROLL_UP" -> FrankAccessibilityService.scrollUp()
                            "CLICK" -> FrankAccessibilityService.clickText(action.param)
                            else -> Pair(false, "Unknown navigation action: ${action.navigationType}")
                        }
                        replySpoken = message
                    }
                    is AdminAction.NotificationAction -> {
                        badgeText = "NOTIFICATIONS"
                        replySpoken = when (action.operation.uppercase()) {
                            "READ_ALL" -> FrankNotificationListenerService.readNotificationsSummary()
                            "READ_LATEST" -> FrankNotificationListenerService.readLatestMessage()
                            "CLEAR_ALL" -> {
                                val (success, msg) = FrankNotificationListenerService.clearAll()
                                msg
                            }
                            "SETTINGS" -> {
                                val (success, msg) = FrankNotificationListenerService.openNotificationAccessSettings(this@VoiceActivationService)
                                msg
                            }
                            else -> "Notification action processed."
                        }
                    }
                    is AdminAction.TimerAction -> {
                        badgeText = "TIMER"
                        replySpoken = when (action.operation.uppercase()) {
                            "START" -> {
                                val timer = timerManager.startTimer(action.label, action.seconds)
                                val mins = timer.totalSeconds / 60
                                val secs = timer.totalSeconds % 60
                                val durationDesc = if (mins > 0 && secs > 0) "$mins minutes and $secs seconds"
                                    else if (mins > 0) "$mins minutes"
                                    else "$secs seconds"
                                "Timer set for $durationDesc."
                            }
                            "PAUSE" -> {
                                val active = timerManager.timers.value.firstOrNull { it.isRunning }
                                if (active != null) {
                                    timerManager.pauseTimer(active.id)
                                    "Paused ${active.label}."
                                } else {
                                    "No active timer running to pause."
                                }
                            }
                            "RESUME" -> {
                                val paused = timerManager.timers.value.firstOrNull { !it.isRunning }
                                if (paused != null) {
                                    timerManager.resumeTimer(paused.id)
                                    "Resumed ${paused.label}."
                                } else {
                                    "No paused timer to resume."
                                }
                            }
                            "CANCEL" -> {
                                timerManager.cancelAllTimers()
                                "Countdown timer cancelled."
                            }
                            "STATUS" -> timerManager.getActiveTimerSummary()
                            "START_STOPWATCH" -> {
                                timerManager.startStopwatch()
                                "Stopwatch started."
                            }
                            "STOP_STOPWATCH" -> {
                                timerManager.pauseStopwatch()
                                val elapsed = timerManager.stopwatch.value.formattedTime
                                "Stopwatch paused at $elapsed."
                            }
                            "RESET_STOPWATCH" -> {
                                timerManager.resetStopwatch()
                                "Stopwatch reset to zero."
                            }
                            "LAP_STOPWATCH" -> {
                                timerManager.lapStopwatch()
                                "Stopwatch lap recorded."
                            }
                            else -> "Timer command recognized."
                        }
                    }
                    is AdminAction.QuickCamera -> {
                        badgeText = "CAMERA"
                        val (success, message) = if (action.mode.uppercase() == "VIDEO") {
                            quickCameraManager.launchCameraForVideo()
                        } else {
                            quickCameraManager.launchCameraForPhoto()
                        }
                        replySpoken = message
                    }
                    is AdminAction.OfflineMath -> {
                        badgeText = "CALCULATION"
                        replySpoken = action.mathResult.spokenAnswer
                    }
                    is AdminAction.FileAction -> {
                        badgeText = "FILE SYSTEM"
                        val result = fileSystemHelper.executeVoiceIntent(
                            operation = action.operation,
                            target = action.fileName,
                            content = action.content,
                            folder = action.folder
                        )
                        if (result.success) {
                            fileManager.syncAllFilesToRoom()
                        }
                        replySpoken = result.spokenSummary
                    }
                    is AdminAction.RememberFact -> {
                        badgeText = "MEMORY VAULT"
                        val mem = MemoryEntity(
                            category = action.category,
                            key = action.key,
                            value = action.value,
                            tags = "${action.category.lowercase()},voice_note",
                            importance = 4
                        )
                        repository.insertMemory(mem)
                        replySpoken = "Secured to memory: ${action.key} is ${action.value}."
                    }
                    is AdminAction.RecallMemory -> {
                        badgeText = "RECALL"
                        val direct = repository.getMemoryByKey(action.query)
                        replySpoken = if (direct != null) {
                            "Remembered ${direct.key}: ${direct.value}"
                        } else {
                            "No memory found for ${action.query}"
                        }
                    }
                    is AdminAction.SearchWeb -> {
                        badgeText = "WEB SEARCH"
                        val (success, message) = deviceManager.executeWebSearch(action.query)
                        replySpoken = "Searching web for ${action.query}"
                    }
                    is AdminAction.RunDiagnostics -> {
                        badgeText = "DEVICE AUDIT"
                        val stats = deviceManager.getDeviceDiagnostics()
                        replySpoken = "Battery is ${stats.batteryPercent} percent, RAM has ${stats.availableRamMb} megabytes available."
                    }
                    is AdminAction.PurgeMemory -> {
                        badgeText = "PURGE"
                        repository.clearMemories()
                        replySpoken = "All on-device memories have been wiped."
                    }
                    is AdminAction.CloseApp -> {
                        badgeText = "CLOSE APP"
                        val (success, message) = when {
                            action.closeAllMinimized -> deviceManager.closeAllMinimizedApps()
                            action.targetApp.isNotBlank() -> deviceManager.closeAppByName(action.targetApp)
                            else -> deviceManager.closeActiveApp()
                        }
                        replySpoken = message
                    }
                    is AdminAction.UnrecognizedCommand -> {
                        val isPermitted = voiceSettingsManager.settings.value.isInternetSearchPermitted
                        if (isPermitted) {
                            badgeText = "AUTO WEB SEARCH"
                            val (success, msg) = deviceManager.executeWebSearch(action.query)
                            replySpoken = "I did not recognize that command. Searching the web for \"${action.query}\"."
                        } else {
                            badgeText = "COMMAND UNKNOWN"
                            replySpoken = "I did not understand that command. Internet search is not permitted. You can enable it in Voice Settings or say 'Allow internet search'."
                        }
                    }
                    is AdminAction.GeneralAssistant -> {
                        badgeText = "SUPER ADMIN"
                        val lower = action.text.lowercase()
                        replySpoken = when {
                            lower.contains("who are you") || lower.contains("your name") ->
                                "I am Frank, your on-device AI Super Admin."
                            lower.contains("what can you do") || lower.contains("help") ->
                                "I can launch and close apps (active or minimized), toggle flashlight, adjust audio, manage timers, read notifications, and search the web when permitted."
                            lower.contains("override") || lower.contains("appear on top") -> {
                                val (ok, msg) = deviceManager.requestOverlayPermission()
                                msg
                            }
                            else -> {
                                "Frank Super Admin is standing by. Say 'close app' to dismiss the active app, or 'help' for features."
                            }
                        }
                    }
                    is AdminAction.SetReminder -> {
                        badgeText = "REMINDER"
                        val (success, message) = reminderScheduler.scheduleReminder(
                            action.title,
                            action.delayMinutes,
                            action.type
                        )
                        replySpoken = message
                    }
                    is AdminAction.ListReminders -> {
                        badgeText = "REMINDER"
                        val list = repository.getPendingRemindersList()
                        replySpoken = if (list.isEmpty()) {
                            "You have no pending reminders scheduled."
                        } else {
                            val count = list.size
                            val topTitles = list.take(3).joinToString(", ") { it.title }
                            "You have $count active reminder${if (count > 1) "s" else ""}: $topTitles"
                        }
                    }
                    is AdminAction.ClearReminders -> {
                        badgeText = "REMINDER"
                        repository.clearCompletedReminders()
                        replySpoken = "Completed reminders cleared."
                    }
                }

                // Speak and show feedback on Floating Overlay HUD over any active app
                lastFrankSpokenUtterance = replySpoken
                beepMuter.unmuteBeeps()
                ttsManager.speak(replySpoken)
                updateNotification(replySpoken)
                overlayManager.showExecution(commandText, replySpoken, badgeText)

                _wakeWordEvents.emit("Frank completed: $commandText")
                _commandExecutedEvents.emit(
                    VoiceCommandEvent(
                        userText = commandText,
                        replyText = replySpoken,
                        badge = badgeText
                    )
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Error executing voice command: ${e.message}", e)
                val errReply = "Frank: ${e.message ?: "Command failed"}"
                lastFrankSpokenUtterance = errReply
                beepMuter.unmuteBeeps()
                ttsManager.speak(errReply)
                updateNotification(errReply)
                overlayManager.showExecution(commandText, errReply, "ERROR")
                _commandExecutedEvents.emit(
                    VoiceCommandEvent(
                        userText = commandText,
                        replyText = errReply,
                        badge = "ERROR"
                    )
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VoiceActivationService onDestroy")
        overlayManager.hideOverlay()
        if (activeInstance == this) {
            activeInstance = null
        }
        _isServiceRunning.value = false
        _isHotwordListening.value = false
        _lastStatusMessage.value = "Frank Service Stopped."

        releaseWakeLock()
        mainHandler.removeCallbacksAndMessages(null)
        beepMuter.unmuteBeeps()
        try {
            speechRecognizer?.setRecognitionListener(null)
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null

        ttsManager.shutdown()
        timerManager.release()
        mediaController.release()
        musicPlayer.stopPlayback()
        serviceScope.cancel()
    }

    companion object {
        const val TAG = "VoiceActivationService"
        const val CHANNEL_ID = "omnimemory_voice_activation"
        const val NOTIFICATION_ID = 2026
        const val ACTION_STOP_SERVICE = "com.example.service.STOP_VOICE_SERVICE"
        const val ACTION_TRIGGER_VOICE = "com.example.service.TRIGGER_VOICE"
        const val ACTION_TOGGLE_TORCH = "com.example.service.TOGGLE_TORCH"
        const val ACTION_MEDIA_PLAY = "com.example.service.MEDIA_PLAY"
        const val ACTION_MEDIA_PAUSE = "com.example.service.MEDIA_PAUSE"
        const val ACTION_MEDIA_NEXT = "com.example.service.MEDIA_NEXT"
        const val ACTION_MEDIA_PREV = "com.example.service.MEDIA_PREV"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _isHotwordListening = MutableStateFlow(false)
        val isHotwordListening: StateFlow<Boolean> = _isHotwordListening.asStateFlow()

        private val _isContinuousListeningMode = MutableStateFlow(true)
        val isContinuousListeningMode: StateFlow<Boolean> = _isContinuousListeningMode.asStateFlow()

        private val _isAppInForeground = MutableStateFlow(true)
        val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

        fun setAppInForeground(inForeground: Boolean) {
            _isAppInForeground.value = inForeground
        }

        private val _lastHeardUtterance = MutableStateFlow("")
        val lastHeardUtterance: StateFlow<String> = _lastHeardUtterance.asStateFlow()

        private val _lastStatusMessage = MutableStateFlow("Frank Standby")
        val lastStatusMessage: StateFlow<String> = _lastStatusMessage.asStateFlow()

        private val _audioLevel = MutableStateFlow(0f)
        val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

        private val _wakeWordEvents = MutableSharedFlow<String>(extraBufferCapacity = 10)
        val wakeWordEvents: SharedFlow<String> = _wakeWordEvents.asSharedFlow()

        private val _commandExecutedEvents = MutableSharedFlow<VoiceCommandEvent>(extraBufferCapacity = 50)
        val commandExecutedEvents: SharedFlow<VoiceCommandEvent> = _commandExecutedEvents.asSharedFlow()

        fun setContinuousListeningMode(enabled: Boolean) {
            _isContinuousListeningMode.value = enabled
        }

        fun setMuteVoiceBeeps(muted: Boolean) {
            activeInstance?.let { service ->
                service.voiceSettingsManager.updateMuteVoiceBeeps(muted)
                if (muted) {
                    service.beepMuter.muteBeeps()
                } else {
                    service.beepMuter.unmuteBeeps()
                }
            }
        }

        fun restartListeningNow() {
            activeInstance?.let { service ->
                service.mainHandler.post {
                    service.destroyAndRecreateRecognizer()
                }
            }
        }

        fun start(context: Context) {
            try {
                val intent = Intent(context, VoiceActivationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start VoiceActivationService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, VoiceActivationService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop VoiceActivationService: ${e.message}", e)
            }
        }

        /**
         * Test trigger for simulating or feeding an utterance into the service
         * (helpful in emulator environments where SpeechRecognizer hardware is emulated).
         */
        fun simulateUtterance(context: Context, utterance: String) {
            val service = activeInstance
            if (service != null && _isServiceRunning.value) {
                service.handleRecognizedVoice(utterance)
            } else {
                start(context)
            }
        }

        var activeInstance: VoiceActivationService? = null
    }
}
