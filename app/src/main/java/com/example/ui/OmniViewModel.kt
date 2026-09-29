package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.entities.AuditLogEntity
import com.example.data.entities.IndexedDocumentEntity
import com.example.data.entities.MemoryEntity
import com.example.data.repository.OmniRepository
import com.example.service.AdminAction
import com.example.service.DeviceDiagnostics
import com.example.service.FileManager
import com.example.service.FileSystemHelper
import com.example.service.FileSystemItem
import com.example.service.FrankVoiceConstants
import com.example.service.InstalledApp
import com.example.service.IntentParser
import com.example.service.LocalMusicPlayer
import com.example.service.MusicTrack
import com.example.service.ParsedCommand
import com.example.service.PlayerState
import com.example.service.SpeechTtsManager
import com.example.service.SuperAdminDeviceManager
import com.example.service.VoiceActivationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab {
    ASSISTANT,
    MEMORY_VAULT,
    DEVICE_ADMIN,
    FILE_WORKSPACE,
    AUDIT_PRIVACY
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "ASSISTANT"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionBadge: String? = null,
    val isSystemAction: Boolean = false,
    val searchSources: List<com.example.service.SearchSourceItem> = emptyList()
)

class OmniViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val repository = OmniRepository(database)

    val deviceManager = SuperAdminDeviceManager(application)
    val fileManager = FileManager(application, repository)
    val fileSystemHelper = fileManager.fileSystemHelper
    val musicPlayer = LocalMusicPlayer(application)
    val mediaController = com.example.service.SuperAdminMediaController(application, musicPlayer)
    val ttsManager = SpeechTtsManager(application)
    val intentParser = IntentParser()
    val liveClient = com.example.service.GeminiLiveClient(application, viewModelScope)
    val searchGroundingService = com.example.service.GeminiSearchGroundingService()
    val reminderScheduler = com.example.service.SuperAdminReminderScheduler(application, repository)
    val timerManager = com.example.service.FrankTimerManager(application)
    val voiceSettingsManager = com.example.service.VoiceSettingsManager(application)
    val quickCameraManager = com.example.service.QuickCameraManager(application)

    // Active Countdown Timers & Stopwatch
    val timers: StateFlow<List<com.example.service.ActiveTimer>> = timerManager.timers
    val stopwatch: StateFlow<com.example.service.StopwatchState> = timerManager.stopwatch

    // Intercepted System Notifications
    val capturedNotifications: StateFlow<List<com.example.service.CapturedNotification>> =
        com.example.service.FrankNotificationListenerService.notifications
    val isNotificationListenerActive: StateFlow<Boolean> =
        com.example.service.FrankNotificationListenerService.isListenerConnected

    // Global Accessibility Service State
    val isAccessibilityActive: StateFlow<Boolean> =
        com.example.service.FrankAccessibilityService.isServiceActive

    // Voice Persona & Custom Wake Words
    val voiceSettings: StateFlow<com.example.service.VoiceSettings> = voiceSettingsManager.settings

    // Reminders & Alarms StateFlow
    val allReminders: StateFlow<List<com.example.data.entities.ReminderEntity>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeReminders: StateFlow<List<com.example.data.entities.ReminderEntity>> = repository.activeReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeReminderCount: StateFlow<Int> = repository.activeReminderCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Gemini Live API State
    val liveConnectionState = liveClient.connectionState
    val liveTranscript = liveClient.liveTranscript
    val isLiveSpeaking = liveClient.isSpeaking

    // Navigation
    private val _currentTab = MutableStateFlow(MainTab.ASSISTANT)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    // Conversational Feed
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            sender = "ASSISTANT",
            text = "Frank Super Admin initialized. Call 'Hey Frank' anytime in foreground or background to command device.",
            actionBadge = "HEY FRANK ACTIVE"
        )
    ))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Background Service States ("Hey Frank")
    val isFrankServiceRunning: StateFlow<Boolean> = VoiceActivationService.isServiceRunning
    val isFrankHotwordListening: StateFlow<Boolean> = VoiceActivationService.isHotwordListening
    val isContinuousListeningMode: StateFlow<Boolean> = VoiceActivationService.isContinuousListeningMode
    val lastFrankUtterance: StateFlow<String> = VoiceActivationService.lastHeardUtterance
    val frankStatusMessage: StateFlow<String> = VoiceActivationService.lastStatusMessage
    val frankAudioLevel: StateFlow<Float> = VoiceActivationService.audioLevel

    // Voice & Input State
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _voiceInputText = MutableStateFlow("")
    val voiceInputText: StateFlow<String> = _voiceInputText.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Hardware & Device State
    private val _flashlightOn = MutableStateFlow(false)
    val flashlightOn: StateFlow<Boolean> = _flashlightOn.asStateFlow()

    private val _volumePercent = MutableStateFlow(0.5f)
    val volumePercent: StateFlow<Float> = _volumePercent.asStateFlow()

    private val _deviceStats = MutableStateFlow<DeviceDiagnostics?>(null)
    val deviceStats: StateFlow<DeviceDiagnostics?> = _deviceStats.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    val filteredApps: StateFlow<List<InstalledApp>> = combine(_installedApps, _appSearchQuery) { apps, query ->
        if (query.isBlank()) apps else apps.filter {
            it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Memory Vault
    val allMemories: StateFlow<List<MemoryEntity>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memoryCount: StateFlow<Int> = repository.memoryCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _memorySearchQuery = MutableStateFlow("")
    val memorySearchQuery: StateFlow<String> = _memorySearchQuery.asStateFlow()

    private val _selectedMemoryCategory = MutableStateFlow("All")
    val selectedMemoryCategory: StateFlow<String> = _selectedMemoryCategory.asStateFlow()

    val filteredMemories: StateFlow<List<MemoryEntity>> = combine(
        allMemories,
        _memorySearchQuery,
        _selectedMemoryCategory
    ) { memories, query, category ->
        memories.filter { mem ->
            val matchesCategory = category == "All" || mem.category.equals(category, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    mem.key.contains(query, ignoreCase = true) ||
                    mem.value.contains(query, ignoreCase = true) ||
                    mem.tags.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Documents / Files
    val allDocuments: StateFlow<List<IndexedDocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documentCount: StateFlow<Int> = repository.documentCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Security & Audit
    val allLogs: StateFlow<List<AuditLogEntity>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogCount: StateFlow<Int> = repository.auditLogCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Music Player
    val playerState: StateFlow<PlayerState> = musicPlayer.playerState
    val availableTracks: List<MusicTrack> = musicPlayer.availableTracks

    // Privacy Mode
    private val _cloudBoostEnabled = MutableStateFlow(false)
    val cloudBoostEnabled: StateFlow<Boolean> = _cloudBoostEnabled.asStateFlow()

    val cloudBytesTransmitted: Long = 0L // Pure on-device zero cloud transmission

    init {
        loadInitialState()
        observeBackgroundVoiceEvents()
    }

    private fun observeBackgroundVoiceEvents() {
        viewModelScope.launch {
            VoiceActivationService.commandExecutedEvents.collect { event ->
                val userMsg = ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    sender = "USER",
                    text = event.userText,
                    timestamp = event.timestamp
                )
                val assistantMsg = ChatMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    sender = "ASSISTANT",
                    text = event.replyText,
                    actionBadge = event.badge,
                    isSystemAction = true,
                    timestamp = event.timestamp + 1
                )
                _chatMessages.value = _chatMessages.value + userMsg + assistantMsg
            }
        }
    }

    private fun loadInitialState() {
        viewModelScope.launch(Dispatchers.IO) {
            // Load apps
            val apps = deviceManager.getInstalledApps()
            _installedApps.value = apps

            // Refresh stats
            _deviceStats.value = deviceManager.getDeviceDiagnostics()

            // Calculate volume
            val currentVol = deviceManager.getMusicVolume()
            val maxVol = deviceManager.getMaxMusicVolume()
            _volumePercent.value = if (maxVol > 0) currentVol.toFloat() / maxVol else 0.5f

            // Sync initial documents
            fileManager.syncAllFilesToRoom()

            // Pre-seed sample memories if empty
            val existing = database.memoryDao().getMemoryByKey("system_role")
            if (existing == null) {
                repository.insertMemory(
                    MemoryEntity(
                        category = "System",
                        key = "system_role",
                        value = "Frank Super Admin: Autonomous On-Device Privacy Guardian with full system control.",
                        tags = "system,admin,core",
                        importance = 5
                    )
                )
                repository.insertMemory(
                    MemoryEntity(
                        category = "Preferences",
                        key = "favorite_music",
                        value = "Quantum Neural Focus Lo-Fi",
                        tags = "audio,music,focus",
                        importance = 4
                    )
                )
                repository.insertMemory(
                    MemoryEntity(
                        category = "Credentials",
                        key = "home_wifi",
                        value = "OmniSec_5G [Encrypted on-device]",
                        tags = "network,credentials,wifi",
                        importance = 5
                    )
                )
                repository.logAction("SYSTEM_INIT", "Frank on-device neural database mounted successfully.")
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
    }

    fun setVoiceInputText(text: String) {
        _voiceInputText.value = text
    }

    fun setAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun setMemorySearchQuery(query: String) {
        _memorySearchQuery.value = query
    }

    fun setMemoryCategory(category: String) {
        _selectedMemoryCategory.value = category
    }

    fun toggleCloudBoost(enable: Boolean) {
        _cloudBoostEnabled.value = enable
        val statusText = if (enable) "Hybrid Cloud AI Boost enabled." else "Strict On-Device Zero-Cloud Mode engaged."
        speakAndPostMessage(statusText, "PRIVACY")
        viewModelScope.launch(Dispatchers.IO) {
            repository.logAction("PRIVACY_MODE", statusText)
        }
    }

    fun toggleFrankService(enable: Boolean) {
        val app = getApplication<Application>()
        if (enable) {
            VoiceActivationService.start(app)
            val msg = "Hey Frank Background Service started. Now listening 24/7 for 'Hey Frank' activation."
            speakAndPostMessage(msg, "BACKGROUND SERVICE")
        } else {
            VoiceActivationService.stop(app)
            val msg = "Hey Frank Background Service stopped."
            speakAndPostMessage(msg, "BACKGROUND SERVICE")
        }
    }

    fun toggleContinuousListeningMode(enable: Boolean) {
        VoiceActivationService.setContinuousListeningMode(enable)
        val msg = if (enable) {
            "Continuous hands-free mode active. Frank is listening constantly without tapping."
        } else {
            "Continuous hands-free listening paused. 'Hey Frank' wake word required."
        }
        speakAndPostMessage(msg, "CONTINUOUS VOICE")
    }

    fun restartVoiceListening() {
        VoiceActivationService.restartListeningNow()
    }

    // --- Command & Voice Execution Pipeline ---

    fun onVoiceRecognitionResult(spokenText: String) {
        if (spokenText.isBlank()) return
        // Check if user spoke wake word ("Hey Frank ...")
        val (wakeTriggered, extractedCommand) = FrankVoiceConstants.extractCommandFromWakeWord(spokenText)
        if (wakeTriggered) {
            if (extractedCommand.isNotBlank()) {
                executeNaturalLanguageCommand(extractedCommand)
            } else {
                val greeting = "Yes, I am Frank. Super Admin ready for your command."
                speakAndPostMessage(greeting, "HEY FRANK")
            }
        } else {
            executeNaturalLanguageCommand(spokenText)
        }
    }

    fun executeNaturalLanguageCommand(rawCommand: String) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return

        // Add user message to chat
        val userMsg = ChatMessage(sender = "USER", text = trimmed)
        _chatMessages.value = _chatMessages.value + userMsg

        _isProcessing.value = true

        viewModelScope.launch {
            try {
                val parsed = intentParser.parseOnDevice(trimmed)

                // Log voice command in audit table
                repository.logAction(
                    actionType = "VOICE_COMMAND",
                    description = "Input: \"$trimmed\"",
                    details = "Action: ${parsed.action::class.simpleName}"
                )

                executeParsedCommand(parsed)
            } catch (e: Throwable) {
                val err = "Error processing command: ${e.message ?: "Action failed"}"
                speakAndPostMessage(err, "SYSTEM")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun executeParsedCommand(parsed: ParsedCommand) {
        when (val action = parsed.action) {
            is AdminAction.OpenApp -> {
                val (success, message) = deviceManager.findAndLaunchAppByName(action.appName)
                val status = if (success) "SUCCESS" else "FAILED"
                repository.logAction("APP_LAUNCH", message, status)
                speakAndPostMessage(message, "APP LAUNCH")
            }

            is AdminAction.PlayMusic -> {
                val (success, message) = mediaController.playTrack(action.query)
                repository.logAction("MUSIC_PLAY", message, "SUCCESS")
                speakAndPostMessage(message, "MEDIA CONTROLLER")
            }

            is AdminAction.MediaControl -> {
                val (success, message) = when (action.command.uppercase()) {
                    "PLAY" -> mediaController.playTrack(action.param)
                    "PAUSE", "STOP" -> mediaController.pauseTrack()
                    "NEXT" -> mediaController.skipToNext()
                    "PREVIOUS" -> mediaController.skipToPrevious()
                    else -> mediaController.playTrack()
                }
                repository.logAction("MEDIA_CONTROL", message, if (success) "SUCCESS" else "FAILED")
                speakAndPostMessage(message, "MEDIA CONTROLLER")
            }

            is AdminAction.ManageSettings -> {
                when (action.settingType.uppercase()) {
                    "FLASHLIGHT" -> {
                        val turnOn = action.param.uppercase() == "ON"
                        val (success, message) = deviceManager.toggleFlashlight(turnOn)
                        if (success) _flashlightOn.value = turnOn
                        repository.logAction("SYSTEM_SETTING", message, if (success) "SUCCESS" else "FAILED")
                        speakAndPostMessage(message, "HARDWARE")
                    }
                    "VOLUME" -> {
                        val percent = when (action.param.uppercase()) {
                            "0" -> 0f
                            "100" -> 1f
                            "UP" -> (_volumePercent.value + 0.2f).coerceAtMost(1f)
                            "DOWN" -> (_volumePercent.value - 0.2f).coerceAtLeast(0f)
                            else -> (action.param.toFloatOrNull() ?: 50f) / 100f
                        }
                        val (success, message) = deviceManager.setMusicVolumePercent(percent)
                        if (success) _volumePercent.value = percent
                        repository.logAction("SYSTEM_SETTING", message, "SUCCESS")
                        speakAndPostMessage(message, "AUDIO")
                    }
                    "BATTERY_OPTIMIZATION" -> {
                        requestBatteryOptimizationExemption()
                    }
                    "ACCESSIBILITY" -> {
                        val (success, message) = com.example.service.FrankAccessibilityService.openAccessibilitySettings(getApplication())
                        speakAndPostMessage(message, "ACCESSIBILITY")
                    }
                    "INTERNET_SEARCH" -> {
                        val enable = action.param.uppercase() == "ON"
                        updateInternetSearchPermitted(enable)
                    }
                    "NOTIFICATION", "NOTIFICATIONS" -> {
                        val (success, message) = com.example.service.FrankNotificationListenerService.openNotificationAccessSettings(getApplication())
                        speakAndPostMessage(message, "NOTIFICATIONS")
                    }
                    else -> {
                        val (success, message) = deviceManager.openSettings(action.settingType)
                        repository.logAction("SYSTEM_SETTING", message, if (success) "SUCCESS" else "FAILED")
                        speakAndPostMessage(message, "SYSTEM SETTINGS")
                    }
                }
            }

            is AdminAction.FileAction -> {
                val result = fileSystemHelper.executeVoiceIntent(
                    operation = action.operation,
                    target = action.fileName,
                    content = action.content,
                    folder = action.folder
                )
                if (result.success) {
                    fileManager.syncAllFilesToRoom()
                }
                val logOp = when (action.operation.uppercase()) {
                    "CREATE_FOLDER", "MKDIR" -> "FOLDER_CREATE"
                    "DELETE_FOLDER" -> "FOLDER_DELETE"
                    "DELETE_FILE", "DELETE" -> "FILE_DELETE"
                    "LIST", "LIST_FILES" -> "FILE_LIST"
                    else -> "FILE_OP"
                }
                repository.logAction(logOp, result.message, if (result.success) "SUCCESS" else "FAILED")
                speakAndPostMessage(result.spokenSummary, "FILE SYSTEM")
            }

            is AdminAction.RememberFact -> {
                val mem = MemoryEntity(
                    category = action.category,
                    key = action.key,
                    value = action.value,
                    tags = "${action.category.lowercase()},voice_note",
                    importance = 4
                )
                repository.insertMemory(mem)
                val reply = "Secured to on-device memory: \"${action.key}\" = \"${action.value}\"."
                repository.logAction("MEMORY_STORE", reply, "SUCCESS")
                speakAndPostMessage(reply, "MEMORY VAULT")
            }

            is AdminAction.RecallMemory -> {
                val direct = repository.getMemoryByKey(action.query)
                if (direct != null) {
                    val reply = "Here is what you remembered about ${direct.key}: ${direct.value}"
                    speakAndPostMessage(reply, "RECALL")
                } else {
                    // Search in values or tags
                    val memories = allMemories.value
                    val match = memories.firstOrNull {
                        it.key.contains(action.query, ignoreCase = true) ||
                        it.value.contains(action.query, ignoreCase = true) ||
                        it.tags.contains(action.query, ignoreCase = true)
                    }
                    if (match != null) {
                        val reply = "Found memory [${match.key}]: ${match.value}"
                        speakAndPostMessage(reply, "RECALL")
                    } else {
                        val reply = "No on-device memory found matching \"${action.query}\"."
                        speakAndPostMessage(reply, "RECALL")
                    }
                }
            }

            is AdminAction.SearchWeb -> {
                if (_cloudBoostEnabled.value) {
                    val groundedResult = searchGroundingService.queryWithSearchGrounding(action.query)
                    repository.logAction("GOOGLE_SEARCH_GROUNDED", "Grounded query: ${action.query}", "SUCCESS")
                    val msg = ChatMessage(
                        sender = "ASSISTANT",
                        text = groundedResult.answer,
                        actionBadge = "GOOGLE SEARCH GROUNDED",
                        isSystemAction = true,
                        searchSources = groundedResult.searchSources
                    )
                    _chatMessages.value = _chatMessages.value + msg
                    ttsManager.speak(groundedResult.answer)
                } else {
                    val (success, message) = deviceManager.executeWebSearch(action.query)
                    repository.logAction("SEARCH", message, "SUCCESS")
                    speakAndPostMessage(message, "WEB SEARCH")
                }
            }

            is AdminAction.RunDiagnostics -> {
                val stats = deviceManager.getDeviceDiagnostics()
                _deviceStats.value = stats
                val reply = "Diagnostics: Battery ${stats.batteryPercent}%, RAM ${stats.availableRamMb}/${stats.totalRamMb} MB, Free Disk ${stats.freeStorageGb} GB, Network: ${stats.networkType}."
                repository.logAction("DIAGNOSTICS", reply, "SUCCESS")
                speakAndPostMessage(reply, "DEVICE AUDIT")
            }

            is AdminAction.PurgeMemory -> {
                repository.clearMemories()
                val reply = "All on-device memories have been securely purged. Zero traces remain."
                repository.logAction("PRIVACY_PURGE", reply, "SUCCESS")
                speakAndPostMessage(reply, "PURGE")
            }

            is AdminAction.CloseApp -> {
                val (success, message) = when {
                    action.closeAllMinimized -> deviceManager.closeAllMinimizedApps()
                    action.targetApp.isNotBlank() -> deviceManager.closeAppByName(action.targetApp)
                    else -> deviceManager.closeActiveApp()
                }
                repository.logAction("CLOSE_APP", message, if (success) "SUCCESS" else "FAILED")
                speakAndPostMessage(message, "CLOSE APP")
            }

            is AdminAction.UnrecognizedCommand -> {
                val isPermitted = voiceSettingsManager.settings.value.isInternetSearchPermitted
                if (isPermitted) {
                    if (_cloudBoostEnabled.value) {
                        val groundedResult = searchGroundingService.queryWithSearchGrounding(action.query)
                        repository.logAction("AUTO_GOOGLE_SEARCH", "Auto-search unrecognized command: ${action.query}", "SUCCESS")
                        val msg = ChatMessage(
                            sender = "ASSISTANT",
                            text = "I didn't recognize that command. Searching the web for \"${action.query}\":\n\n${groundedResult.answer}",
                            actionBadge = "AUTO WEB SEARCH",
                            isSystemAction = true,
                            searchSources = groundedResult.searchSources
                        )
                        _chatMessages.value = _chatMessages.value + msg
                        ttsManager.speak("I didn't recognize that command. Searching the web for \"${action.query}\". ${groundedResult.answer}")
                    } else {
                        val (success, message) = deviceManager.executeWebSearch(action.query)
                        val reply = "I didn't recognize that command. Searching the web for \"${action.query}\"."
                        repository.logAction("AUTO_WEB_SEARCH", reply, "SUCCESS")
                        speakAndPostMessage(reply, "AUTO WEB SEARCH")
                    }
                } else {
                    val reply = "I did not understand that command. Internet search is not permitted. You can enable it in Voice Settings or say 'Allow internet search'."
                    repository.logAction("COMMAND_UNKNOWN", reply, "RESTRICTED")
                    speakAndPostMessage(reply, "COMMAND UNKNOWN")
                }
            }

            is AdminAction.GeneralAssistant -> {
                // If cloud boost is enabled, use Gemini 3.5 Flash with Google Search Grounding to get up-to-date accurate information
                if (_cloudBoostEnabled.value) {
                    val grounded = searchGroundingService.queryWithSearchGrounding(action.text)
                    repository.logAction("GEMINI_SEARCH_GROUNDED", action.text, "SUCCESS")
                    val msg = ChatMessage(
                        sender = "ASSISTANT",
                        text = grounded.answer,
                        actionBadge = if (grounded.searchSources.isNotEmpty()) "GOOGLE SEARCH GROUNDED" else "GEMINI 3.5 FLASH",
                        isSystemAction = true,
                        searchSources = grounded.searchSources
                    )
                    _chatMessages.value = _chatMessages.value + msg
                    ttsManager.speak(grounded.answer)
                } else {
                    val reply = "I am Frank, your private on-device AI Super Admin. You can ask me to open any app, play focus music, toggle flashlight or settings, manage files, or remember private information."
                    speakAndPostMessage(reply, "FRANK")
                }
            }

            is AdminAction.SetReminder -> {
                val (success, message) = reminderScheduler.scheduleReminder(
                    action.title,
                    action.delayMinutes,
                    action.type
                )
                repository.logAction("ALARM_VOICE", message, if (success) "SUCCESS" else "FAILED")
                speakAndPostMessage(message, "REMINDER")
            }

            is AdminAction.ListReminders -> {
                val list = repository.getPendingRemindersList()
                val reply = if (list.isEmpty()) {
                    "You have no pending reminders or alarms scheduled."
                } else {
                    val count = list.size
                    val topTitles = list.take(3).joinToString(", ") { it.title }
                    "You have $count active reminder${if (count > 1) "s" else ""}: $topTitles"
                }
                speakAndPostMessage(reply, "REMINDER")
            }

            is AdminAction.ClearReminders -> {
                repository.clearCompletedReminders()
                speakAndPostMessage("Completed reminders have been cleared.", "REMINDER")
            }

            is AdminAction.QueryTime -> {
                val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                val reply = "The current time is ${timeFormat.format(java.util.Date())}."
                speakAndPostMessage(reply, "TIME")
            }

            is AdminAction.QueryDate -> {
                val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault())
                val reply = "Today is ${dateFormat.format(java.util.Date())}."
                speakAndPostMessage(reply, "DATE")
            }

            is AdminAction.BringFrankToFront -> {
                val (success, msg) = deviceManager.bringFrankToForeground()
                speakAndPostMessage(msg, "SUPER ADMIN")
            }

            is AdminAction.GlobalNavigation -> {
                val (success, message) = when (action.navigationType.uppercase()) {
                    "HOME" -> com.example.service.FrankAccessibilityService.goHome()
                    "BACK" -> com.example.service.FrankAccessibilityService.goBack()
                    "NOTIFICATIONS" -> com.example.service.FrankAccessibilityService.openNotifications()
                    "QUICK_SETTINGS" -> com.example.service.FrankAccessibilityService.openQuickSettings()
                    "RECENTS" -> com.example.service.FrankAccessibilityService.showRecentApps()
                    "SCREENSHOT" -> com.example.service.FrankAccessibilityService.takeScreenshot()
                    "LOCK" -> com.example.service.FrankAccessibilityService.lockScreen()
                    "SCROLL_DOWN" -> com.example.service.FrankAccessibilityService.scrollDown()
                    "SCROLL_UP" -> com.example.service.FrankAccessibilityService.scrollUp()
                    "CLICK" -> com.example.service.FrankAccessibilityService.clickText(action.param)
                    else -> Pair(false, "Unknown navigation action: ${action.navigationType}")
                }
                repository.logAction("ACCESSIBILITY_NAV", message, if (success) "SUCCESS" else "FAILED")
                speakAndPostMessage(message, "ACCESSIBILITY")
            }

            is AdminAction.NotificationAction -> {
                val reply = when (action.operation.uppercase()) {
                    "READ_ALL" -> com.example.service.FrankNotificationListenerService.readNotificationsSummary()
                    "READ_LATEST" -> com.example.service.FrankNotificationListenerService.readLatestMessage()
                    "CLEAR_ALL" -> {
                        val (success, msg) = com.example.service.FrankNotificationListenerService.clearAll()
                        msg
                    }
                    "SETTINGS" -> {
                        val (success, msg) = com.example.service.FrankNotificationListenerService.openNotificationAccessSettings(getApplication())
                        msg
                    }
                    else -> "Notification action processed."
                }
                speakAndPostMessage(reply, "NOTIFICATIONS")
            }

            is AdminAction.TimerAction -> {
                val reply = when (action.operation.uppercase()) {
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
                speakAndPostMessage(reply, "TIMER")
            }

            is AdminAction.QuickCamera -> {
                val (success, message) = if (action.mode.uppercase() == "VIDEO") {
                    quickCameraManager.launchCameraForVideo()
                } else {
                    quickCameraManager.launchCameraForPhoto()
                }
                repository.logAction("CAMERA_CAPTURE", message, if (success) "SUCCESS" else "FAILED")
                speakAndPostMessage(message, "CAMERA")
            }

            is AdminAction.OfflineMath -> {
                repository.logAction("OFFLINE_MATH", action.mathResult.displayText, "SUCCESS")
                speakAndPostMessage(action.mathResult.spokenAnswer, "MATH")
            }
        }
    }

    // Helper functions for UI controls
    fun startCountdownTimer(label: String, seconds: Long) {
        val timer = timerManager.startTimer(label, seconds)
        speakAndPostMessage("Timer started for ${timer.formattedRemaining}", "TIMER")
    }

    fun pauseCountdownTimer(id: String) {
        timerManager.pauseTimer(id)
    }

    fun resumeCountdownTimer(id: String) {
        timerManager.resumeTimer(id)
    }

    fun cancelCountdownTimer(id: String) {
        timerManager.cancelTimer(id)
    }

    fun startStopwatch() {
        timerManager.startStopwatch()
    }

    fun pauseStopwatch() {
        timerManager.pauseStopwatch()
    }

    fun resetStopwatch() {
        timerManager.resetStopwatch()
    }

    fun lapStopwatch() {
        timerManager.lapStopwatch()
    }

    fun readNotificationsAloud() {
        val summary = com.example.service.FrankNotificationListenerService.readNotificationsSummary()
        speakAndPostMessage(summary, "NOTIFICATIONS")
    }

    fun clearAllNotifications() {
        val (success, msg) = com.example.service.FrankNotificationListenerService.clearAll()
        speakAndPostMessage(msg, "NOTIFICATIONS")
    }

    fun openAccessibilitySettings() {
        com.example.service.FrankAccessibilityService.openAccessibilitySettings(getApplication())
    }

    fun openNotificationSettings() {
        com.example.service.FrankNotificationListenerService.openNotificationAccessSettings(getApplication())
    }

    fun executeGlobalNavAction(actionType: Int) {
        val (success, message) = com.example.service.FrankAccessibilityService.executeGlobalAction(actionType)
        speakAndPostMessage(message, "ACCESSIBILITY")
    }

    fun updateVoicePitch(pitch: Float) {
        voiceSettingsManager.updateSpeechPitch(pitch)
        ttsManager.setPitch(pitch)
    }

    fun updateVoiceRate(rate: Float) {
        voiceSettingsManager.updateSpeechRate(rate)
        ttsManager.setSpeechRate(rate)
    }

    fun updatePrimaryWakeWord(word: String) {
        voiceSettingsManager.updatePrimaryWakeWord(word)
    }

    fun addWakeAlias(alias: String) {
        voiceSettingsManager.addWakeAlias(alias)
    }

    fun removeWakeAlias(alias: String) {
        voiceSettingsManager.removeWakeAlias(alias)
    }

    fun updateMuteVoiceBeeps(muted: Boolean) {
        voiceSettingsManager.updateMuteVoiceBeeps(muted)
        com.example.service.VoiceActivationService.setMuteVoiceBeeps(muted)
    }

    fun takeQuickPhoto() {
        val (success, message) = quickCameraManager.launchCameraForPhoto()
        speakAndPostMessage(message, "CAMERA")
    }

    fun scheduleReminder(title: String, delayMinutes: Long, type: String = "REMINDER") {
        viewModelScope.launch {
            val (success, message) = reminderScheduler.scheduleReminder(title, delayMinutes, type)
            speakAndPostMessage(message, "REMINDER")
        }
    }

    fun cancelReminder(id: Long) {
        viewModelScope.launch {
            val (success, message) = reminderScheduler.cancelReminder(id)
            speakAndPostMessage(message, "REMINDER")
        }
    }

    fun clearCompletedReminders() {
        viewModelScope.launch {
            repository.clearCompletedReminders()
        }
    }

    private fun speakAndPostMessage(text: String, badge: String? = null) {
        val msg = ChatMessage(
            sender = "ASSISTANT",
            text = text,
            actionBadge = badge,
            isSystemAction = badge != null
        )
        _chatMessages.value = _chatMessages.value + msg
        ttsManager.speak(text)
    }

    // --- Direct UI Super Admin Actions ---

    private val _isBatteryExempt = MutableStateFlow(deviceManager.isBatteryOptimizationIgnored())
    val isBatteryExempt: StateFlow<Boolean> = _isBatteryExempt.asStateFlow()

    private val _isOverlayGranted = MutableStateFlow(deviceManager.hasOverlayPermission())
    val isOverlayGranted: StateFlow<Boolean> = _isOverlayGranted.asStateFlow()

    fun refreshBatteryStatus() {
        _isBatteryExempt.value = deviceManager.isBatteryOptimizationIgnored()
    }

    fun refreshOverlayStatus() {
        _isOverlayGranted.value = deviceManager.hasOverlayPermission()
    }

    fun requestBatteryOptimizationExemption() {
        val (success, msg) = deviceManager.requestIgnoreBatteryOptimization()
        _isBatteryExempt.value = deviceManager.isBatteryOptimizationIgnored()
        speakAndPostMessage(msg, "BACKGROUND POWER")
    }

    fun requestOverlayPermission() {
        val (success, msg) = deviceManager.requestOverlayPermission()
        _isOverlayGranted.value = deviceManager.hasOverlayPermission()
        speakAndPostMessage(msg, "OVERRIDE PERMISSION")
    }

    fun toggleFlashlight() {
        val next = !_flashlightOn.value
        val (success, msg) = deviceManager.toggleFlashlight(next)
        if (success) {
            _flashlightOn.value = next
            viewModelScope.launch(Dispatchers.IO) {
                repository.logAction("FLASHLIGHT_TOGGLE", msg, "SUCCESS")
            }
            deviceManager.vibrate(40)
        }
    }

    fun setVolume(percent: Float) {
        val (success, msg) = deviceManager.setMusicVolumePercent(percent)
        if (success) {
            _volumePercent.value = percent
            deviceManager.vibrate(20)
        }
    }

    fun launchAppByPackage(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val (success, msg) = deviceManager.launchApp(packageName)
            repository.logAction("APP_LAUNCH", msg, if (success) "SUCCESS" else "FAILED")
        }
    }

    fun openSystemSetting(type: String) {
        if (type.uppercase() == "BATTERY_OPTIMIZATION") {
            requestBatteryOptimizationExemption()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val (success, msg) = deviceManager.openSettings(type)
            repository.logAction("SYSTEM_SETTING", msg, if (success) "SUCCESS" else "FAILED")
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            _deviceStats.value = deviceManager.getDeviceDiagnostics()
            repository.logAction("DIAGNOSTICS", "System diagnostics refreshed.")
        }
    }

    // --- Memory Operations ---

    fun saveMemory(category: String, key: String, value: String, tags: String, importance: Int = 3) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = MemoryEntity(
                category = category.ifBlank { "Personal" },
                key = key.trim(),
                value = value.trim(),
                tags = tags.trim(),
                importance = importance
            )
            repository.insertMemory(entity)
            repository.logAction("MEMORY_STORE", "Stored memory: \"$key\"", "SUCCESS")
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMemory(id)
            repository.logAction("MEMORY_DELETE", "Deleted memory item #$id", "SUCCESS")
        }
    }

    fun purgeAllMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearMemories()
            repository.logAction("MEMORY_PURGE", "Purged all on-device memories", "SUCCESS")
        }
    }

    // --- File Operations ---

    fun createNewFile(name: String, content: String, category: String = "Notes") {
        viewModelScope.launch(Dispatchers.IO) {
            val (success, msg) = fileManager.createDocument(name, content, category)
            if (success) fileManager.syncAllFilesToRoom()
            repository.logAction("FILE_CREATE", msg, if (success) "SUCCESS" else "FAILED")
        }
    }

    fun deleteFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val (success, msg) = fileManager.deleteDocument(name)
            if (success) fileManager.syncAllFilesToRoom()
            repository.logAction("FILE_DELETE", msg, if (success) "SUCCESS" else "FAILED")
        }
    }

    fun duplicateFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val (success, msg) = fileManager.duplicateDocument(name)
            if (success) fileManager.syncAllFilesToRoom()
            repository.logAction("FILE_DUPLICATE", msg, if (success) "SUCCESS" else "FAILED")
        }
    }

    fun shareFile(name: String) {
        fileManager.shareDocument(name)
    }

    fun readFileContent(name: String): String {
        val (_, content) = fileManager.readDocument(name)
        return content
    }

    fun updateFileContent(name: String, newContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val (success, msg) = fileManager.updateDocument(name, newContent)
            if (success) fileManager.syncAllFilesToRoom()
            repository.logAction("FILE_UPDATE", msg, if (success) "SUCCESS" else "FAILED")
        }
    }

    fun createFolder(name: String, parentFolder: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val res = fileSystemHelper.createFolder(name, parentFolder)
            if (res.success) fileManager.syncAllFilesToRoom()
            repository.logAction("FOLDER_CREATE", res.message, if (res.success) "SUCCESS" else "FAILED")
        }
    }

    fun deleteFolder(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = fileSystemHelper.deleteFolder(name)
            if (res.success) fileManager.syncAllFilesToRoom()
            repository.logAction("FOLDER_DELETE", res.message, if (res.success) "SUCCESS" else "FAILED")
        }
    }

    fun listWorkspaceItems(folder: String = ""): List<FileSystemItem> {
        return fileSystemHelper.listFiles(folder).items
    }

    // --- Music & Media Controls ---

    fun toggleMusic() {
        val isCurrentlyPlaying = musicPlayer.playerState.value.isPlaying || mediaController.isPlaying.value
        if (isCurrentlyPlaying) {
            mediaController.pauseTrack()
        } else {
            mediaController.playTrack()
        }
    }

    fun playTrack(track: MusicTrack) {
        mediaController.playTrack(track.title)
    }

    fun nextTrack() {
        mediaController.skipToNext()
    }

    fun prevTrack() {
        mediaController.skipToPrevious()
    }

    fun launchSystemMusic() {
        musicPlayer.launchSystemMusicApp()
    }

    // --- Clear Audit Logs ---

    fun clearAuditLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
        }
    }

    // --- Gemini 3.8 Live API Controls ---

    fun startLiveSession() {
        liveClient.startLiveSession()
        viewModelScope.launch(Dispatchers.IO) {
            repository.logAction("GEMINI_LIVE_START", "Initiated Gemini 3.8 Live API real-time voice session.", "SUCCESS")
        }
    }

    fun endLiveSession() {
        liveClient.endLiveSession()
        viewModelScope.launch(Dispatchers.IO) {
            repository.logAction("GEMINI_LIVE_END", "Ended Gemini 3.8 Live API session.", "SUCCESS")
        }
    }

    fun sendLiveTextMessage(text: String) {
        liveClient.sendTextMessage(text)
    }

    // --- Active & Minimized App Closing Controls ---

    fun closeActiveApp() {
        viewModelScope.launch {
            val (success, msg) = deviceManager.closeActiveApp()
            repository.logAction("CLOSE_APP", msg, if (success) "SUCCESS" else "FAILED")
            speakAndPostMessage(msg, "CLOSE APP")
        }
    }

    fun closeAllMinimizedApps() {
        viewModelScope.launch {
            val (success, msg) = deviceManager.closeAllMinimizedApps()
            repository.logAction("CLOSE_APP", msg, if (success) "SUCCESS" else "FAILED")
            speakAndPostMessage(msg, "CLOSE APP")
        }
    }

    fun closeAppByName(appName: String) {
        viewModelScope.launch {
            val (success, msg) = deviceManager.closeAppByName(appName)
            repository.logAction("CLOSE_APP", msg, if (success) "SUCCESS" else "FAILED")
            speakAndPostMessage(msg, "CLOSE APP")
        }
    }

    // --- Internet Search Permission Control ---

    fun updateInternetSearchPermitted(permitted: Boolean) {
        voiceSettingsManager.updateInternetSearchPermitted(permitted)
        val msg = if (permitted) {
            "Internet search permission enabled. Frank will automatically search the web for unrecognized commands."
        } else {
            "Internet search permission disabled. Frank will not search the internet for unrecognized commands."
        }
        viewModelScope.launch {
            repository.logAction("INTERNET_SEARCH_PERM", msg, "SUCCESS")
            speakAndPostMessage(msg, "SETTINGS")
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController.release()
        musicPlayer.stopPlayback()
        ttsManager.shutdown()
        liveClient.release()
    }
}
