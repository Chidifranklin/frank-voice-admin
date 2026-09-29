package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.entities.MemoryEntity
import com.example.service.AdminAction
import com.example.service.FrankVoiceConstants
import com.example.service.IntentParser
import com.example.service.SuperAdminDeviceManager
import com.example.service.VoiceActivationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context verifies Frank app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Frank", appName)
    }

    @Test
    fun `wake word extractor correctly detects Hey Frank and separates payload`() {
        val (wake1, cmd1) = FrankVoiceConstants.extractCommandFromWakeWord("Hey Frank turn on flashlight")
        assertTrue(wake1)
        assertEquals("turn on flashlight", cmd1)

        val (wake2, cmd2) = FrankVoiceConstants.extractCommandFromWakeWord("Hey Frank, open camera")
        assertTrue(wake2)
        assertEquals("open camera", cmd2)

        val (wake3, cmd3) = FrankVoiceConstants.extractCommandFromWakeWord("Hey Frank")
        assertTrue(wake3)
        assertEquals("", cmd3)

        val (wake4, cmd4) = FrankVoiceConstants.extractCommandFromWakeWord("What is the weather today")
        assertFalse(wake4)
        assertEquals("What is the weather today", cmd4)
    }

    @Test
    fun `intent parser handles app launch and music commands`() {
        val parser = IntentParser()

        val appCmd = parser.parseOnDevice("open camera")
        assertTrue(appCmd.action is AdminAction.OpenApp)
        assertEquals("camera", (appCmd.action as AdminAction.OpenApp).appName)

        val musicCmd = parser.parseOnDevice("play focus music")
        assertTrue(musicCmd.action is AdminAction.MediaControl)
        assertEquals("PLAY", (musicCmd.action as AdminAction.MediaControl).command)
    }

    @Test
    fun `intent parser handles flashlight and settings commands`() {
        val parser = IntentParser()

        val torchOn = parser.parseOnDevice("turn on flashlight")
        assertTrue(torchOn.action is AdminAction.ManageSettings)
        assertEquals("FLASHLIGHT", (torchOn.action as AdminAction.ManageSettings).settingType)
        assertEquals("ON", (torchOn.action as AdminAction.ManageSettings).param)

        val torchOff = parser.parseOnDevice("turn off flashlight")
        assertEquals("OFF", (torchOff.action as AdminAction.ManageSettings).param)

        val wifi = parser.parseOnDevice("open wifi settings")
        assertTrue(wifi.action is AdminAction.ManageSettings)
        assertEquals("WIFI", (wifi.action as AdminAction.ManageSettings).settingType)
    }

    @Test
    fun `intent parser handles private memory remembering and recall`() {
        val parser = IntentParser()

        val rememberCmd = parser.parseOnDevice("remember sister's birthday is June 4")
        assertTrue(rememberCmd.action is AdminAction.RememberFact)
        val fact = rememberCmd.action as AdminAction.RememberFact
        assertEquals("sister's birthday", fact.key)
        assertEquals("June 4", fact.value)

        val recallCmd = parser.parseOnDevice("what is sister's birthday?")
        assertTrue(recallCmd.action is AdminAction.RecallMemory)
    }

    @Test
    fun `memory entity model encapsulates on-device security fields`() {
        val memory = MemoryEntity(
            category = "Credentials",
            key = "lockbox_code",
            value = "9821",
            tags = "vault,home",
            importance = 5,
            isEncrypted = true
        )

        assertEquals("Credentials", memory.category)
        assertEquals("lockbox_code", memory.key)
        assertEquals("9821", memory.value)
        assertTrue(memory.isEncrypted)
        assertEquals(5, memory.importance)
    }

    @Test
    fun `super admin device manager queries apps via package manager and launches by voice intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceManager = SuperAdminDeviceManager(context)

        // Query installed applications using PackageManager
        val installedApps = deviceManager.getInstalledApps()
        assertNotNull(installedApps)
        assertTrue(installedApps.isNotEmpty())

        // Test launch by voice intent query
        val (success, message) = deviceManager.findAndLaunchAppByName("Settings")
        assertNotNull(message)

        // Test intent parsing with run and switch to prefixes
        val parser = IntentParser()
        val runCmd = parser.parseOnDevice("run YouTube")
        assertTrue(runCmd.action is AdminAction.OpenApp)
        assertEquals("youtube", (runCmd.action as AdminAction.OpenApp).appName)

        val switchCmd = parser.parseOnDevice("switch to Chrome")
        assertTrue(switchCmd.action is AdminAction.OpenApp)
        assertEquals("chrome", (switchCmd.action as AdminAction.OpenApp).appName)
    }

    @Test
    fun `voice activation foreground service constants and actions verified`() {
        assertEquals("com.example.service.STOP_VOICE_SERVICE", VoiceActivationService.ACTION_STOP_SERVICE)
        assertEquals("com.example.service.TRIGGER_VOICE", VoiceActivationService.ACTION_TRIGGER_VOICE)
        assertEquals("omnimemory_voice_activation", VoiceActivationService.CHANNEL_ID)
        assertEquals(2026, VoiceActivationService.NOTIFICATION_ID)
    }

    @Test
    fun `gemini live api client and search grounding models configured correctly`() {
        // Verify gemini-3.8-live model configuration
        assertEquals("gemini-3.8-live", com.example.service.GeminiLiveClient.LIVE_MODEL)

        // Verify gemini-3.5-flash model configuration
        assertEquals("gemini-3.5-flash", com.example.service.GeminiSearchGroundingService.MODEL)

        // Verify SearchSourceItem data structure
        val source = com.example.service.SearchSourceItem(
            title = "Android Developers",
            url = "https://developer.android.com"
        )
        assertEquals("Android Developers", source.title)
        assertEquals("https://developer.android.com", source.url)

        val groundedResult = com.example.service.GroundedSearchResult(
            answer = "Grounded response",
            searchSources = listOf(source),
            searchQueries = listOf("Android latest features")
        )
        assertEquals("Grounded response", groundedResult.answer)
        assertEquals(1, groundedResult.searchSources.size)
        assertEquals("Android latest features", groundedResult.searchQueries.first())
    }

    @Test
    fun `media controller voice commands parse and execute play pause skip`() {
        val parser = IntentParser()

        // 1. Play command
        val playCmd = parser.parseOnDevice("play cybernetic rain")
        assertTrue(playCmd.action is AdminAction.MediaControl)
        val playAction = playCmd.action as AdminAction.MediaControl
        assertEquals("PLAY", playAction.command)
        assertEquals("cybernetic rain", playAction.param)

        // 2. Pause command
        val pauseCmd = parser.parseOnDevice("pause music")
        assertTrue(pauseCmd.action is AdminAction.MediaControl)
        val pauseAction = pauseCmd.action as AdminAction.MediaControl
        assertEquals("PAUSE", pauseAction.command)

        // 3. Skip next track command
        val nextCmd = parser.parseOnDevice("next track")
        assertTrue(nextCmd.action is AdminAction.MediaControl)
        val nextAction = nextCmd.action as AdminAction.MediaControl
        assertEquals("NEXT", nextAction.command)

        // 4. Skip previous track command
        val prevCmd = parser.parseOnDevice("previous track")
        assertTrue(prevCmd.action is AdminAction.MediaControl)
        val prevAction = prevCmd.action as AdminAction.MediaControl
        assertEquals("PREVIOUS", prevAction.command)

        // 5. Test SuperAdminMediaController integration
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localMusicPlayer = com.example.service.LocalMusicPlayer(context)
        val mediaController = com.example.service.SuperAdminMediaController(context, localMusicPlayer)

        val (playSuccess, playMsg) = mediaController.playTrack("Quantum Neural Focus")
        assertTrue(playSuccess)
        assertNotNull(playMsg)

        val (pauseSuccess, pauseMsg) = mediaController.pauseTrack()
        assertTrue(pauseSuccess)
        assertNotNull(pauseMsg)

        val (nextSuccess, nextMsg) = mediaController.skipToNext()
        assertTrue(nextSuccess)
        assertNotNull(nextMsg)

        val (prevSuccess, prevMsg) = mediaController.skipToPrevious()
        assertTrue(prevSuccess)
        assertNotNull(prevMsg)

        mediaController.release()
    }

    @Test
    fun `file system helper performs basic operations listing creating folders deleting files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileSystemHelper = com.example.service.FileSystemHelper(context)

        // 1. Create a folder
        val createFolderResult = fileSystemHelper.createFolder("TestProjects")
        assertTrue(createFolderResult.success)
        assertTrue(createFolderResult.message.contains("TestProjects"))

        // 2. Create a file inside folder
        val createFileResult = fileSystemHelper.createFile("project_spec.txt", "Voice intent file system spec", "TestProjects")
        assertTrue(createFileResult.success)

        // 3. List files in folder
        val listResult = fileSystemHelper.listFiles("TestProjects")
        assertTrue(listResult.success)
        assertTrue(listResult.items.any { it.name == "project_spec.txt" })

        // 4. Read file content
        val readResult = fileSystemHelper.readFile("project_spec.txt")
        assertTrue(readResult.success)
        assertEquals("Voice intent file system spec", readResult.fileContent)

        // 5. Delete file
        val deleteFileResult = fileSystemHelper.deleteFile("project_spec.txt")
        assertTrue(deleteFileResult.success)

        // Verify file is gone
        val listAfterDelete = fileSystemHelper.listFiles("TestProjects")
        assertFalse(listAfterDelete.items.any { it.name == "project_spec.txt" })

        // 6. Delete folder
        val deleteFolderResult = fileSystemHelper.deleteFolder("TestProjects")
        assertTrue(deleteFolderResult.success)
    }

    @Test
    fun `intent parser handles file system voice intents for listing creating folders and deleting files`() {
        val parser = IntentParser()

        // 1. List files intent
        val listCmd = parser.parseOnDevice("list files")
        assertTrue(listCmd.action is AdminAction.FileAction)
        assertEquals("LIST", (listCmd.action as AdminAction.FileAction).operation)

        // 2. List files in specific folder
        val listSubCmd = parser.parseOnDevice("list files in Documents folder")
        assertTrue(listSubCmd.action is AdminAction.FileAction)
        val listSubAction = listSubCmd.action as AdminAction.FileAction
        assertEquals("LIST", listSubAction.operation)
        assertEquals("documents", listSubAction.folder.lowercase())

        // 3. Create folder voice intent
        val createFolderCmd = parser.parseOnDevice("create folder SecurityArchives")
        assertTrue(createFolderCmd.action is AdminAction.FileAction)
        val createFolderAction = createFolderCmd.action as AdminAction.FileAction
        assertEquals("CREATE_FOLDER", createFolderAction.operation)
        assertEquals("securityarchives", createFolderAction.fileName.lowercase())

        // 4. Delete folder voice intent
        val deleteFolderCmd = parser.parseOnDevice("delete folder OldBackups")
        assertTrue(deleteFolderCmd.action is AdminAction.FileAction)
        val deleteFolderAction = deleteFolderCmd.action as AdminAction.FileAction
        assertEquals("DELETE_FOLDER", deleteFolderAction.operation)
        assertEquals("oldbackups", deleteFolderAction.fileName.lowercase())

        // 5. Delete file voice intent
        val deleteFileCmd = parser.parseOnDevice("delete file secret_key.txt")
        assertTrue(deleteFileCmd.action is AdminAction.FileAction)
        val deleteFileAction = deleteFileCmd.action as AdminAction.FileAction
        assertEquals("DELETE_FILE", deleteFileAction.operation)
        assertEquals("secret_key.txt", deleteFileAction.fileName)
    }

    @Test
    fun `file system helper executes voice intents and natural language commands`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = com.example.service.FileSystemHelper(context)

        // Voice intent execution: CREATE_FOLDER
        val folderOp = helper.executeVoiceIntent("CREATE_FOLDER", target = "VoiceCreatedFolder")
        assertTrue(folderOp.success)

        // Voice intent execution: CREATE_FILE
        val fileOp = helper.executeVoiceIntent("CREATE_FILE", target = "voice_memo.txt", content = "Voice Memo 1", folder = "VoiceCreatedFolder")
        assertTrue(fileOp.success)

        // Natural language parsing & execution
        val nlListOp = helper.parseAndExecuteVoiceCommand("list files")
        assertTrue(nlListOp.success)
        assertNotNull(nlListOp.spokenSummary)

        // Clean up
        val delFileOp = helper.executeVoiceIntent("DELETE_FILE", target = "voice_memo.txt")
        assertTrue(delFileOp.success)

        val delFolderOp = helper.executeVoiceIntent("DELETE_FOLDER", target = "VoiceCreatedFolder")
        assertTrue(delFolderOp.success)
    }

    @Test
    fun `continuous voice service mode and battery optimization check functional`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceManager = SuperAdminDeviceManager(context)

        // Verify battery optimization query runs safely
        val isExempt = deviceManager.isBatteryOptimizationIgnored()
        assertNotNull(isExempt)

        // Verify continuous listening state toggle
        VoiceActivationService.setContinuousListeningMode(true)
        assertTrue(VoiceActivationService.isContinuousListeningMode.value)
        VoiceActivationService.setContinuousListeningMode(false)
        assertFalse(VoiceActivationService.isContinuousListeningMode.value)

        // Verify simulate utterance pipeline
        VoiceActivationService.simulateUtterance(context, "Hey Frank, system diagnostics")
        assertNotNull(VoiceActivationService.lastHeardUtterance.value)
    }

    @Test
    fun `intent parser correctly parses reminder and timer commands`() {
        val parser = IntentParser()

        val reminderCmd = parser.parseOnDevice("remind me in 10 minutes to drink water")
        assertTrue(reminderCmd.action is AdminAction.SetReminder)
        val reminderAction = reminderCmd.action as AdminAction.SetReminder
        assertEquals(10L, reminderAction.delayMinutes)
        assertEquals("drink water", reminderAction.title)
        assertEquals("REMINDER", reminderAction.type)

        val alarmCmd = parser.parseOnDevice("set an alarm for 5 minutes")
        assertTrue(alarmCmd.action is AdminAction.SetReminder)
        val alarmAction = alarmCmd.action as AdminAction.SetReminder
        assertEquals(5L, alarmAction.delayMinutes)
        assertEquals("ALARM", alarmAction.type)

        val listCmd = parser.parseOnDevice("what are my reminders")
        assertTrue(listCmd.action is AdminAction.ListReminders)

        val clearCmd = parser.parseOnDevice("clear reminders")
        assertTrue(clearCmd.action is AdminAction.ClearReminders)
    }

    @Test
    fun `reminder entity creates valid model and trigger timestamps`() {
        val now = System.currentTimeMillis()
        val reminder = com.example.data.entities.ReminderEntity(
            title = "Check oven",
            triggerTimeMillis = now + 15 * 60 * 1000,
            reminderType = "TIMER",
            isCompleted = false
        )
        assertEquals("Check oven", reminder.title)
        assertEquals("TIMER", reminder.reminderType)
        assertFalse(reminder.isCompleted)
        assertTrue(reminder.triggerTimeMillis > now)
    }

    @Test
    fun `frank quick settings tile service instantiated and verified`() {
        val tileService = com.example.service.FrankTileService()
        assertNotNull(tileService)
    }

    @Test
    fun `intent parser handles time, date, overlay, and frank foreground queries`() {
        val parser = IntentParser()

        val timeCmd = parser.parseOnDevice("what time is it")
        assertTrue(timeCmd.action is AdminAction.QueryTime)

        val dateCmd = parser.parseOnDevice("what date is today")
        assertTrue(dateCmd.action is AdminAction.QueryDate)

        val frontCmd = parser.parseOnDevice("bring frank to front")
        assertTrue(frontCmd.action is AdminAction.BringFrankToFront)

        val overlayCmd = parser.parseOnDevice("override other apps")
        assertTrue(overlayCmd.action is AdminAction.ManageSettings)
        assertEquals("OVERLAY", (overlayCmd.action as AdminAction.ManageSettings).settingType)

        val directAppCmd = parser.parseOnDevice("camera")
        assertTrue(directAppCmd.action is AdminAction.OpenApp)
        assertEquals("camera", (directAppCmd.action as AdminAction.OpenApp).appName)

        val batteryCmd = parser.parseOnDevice("what is my battery level")
        assertTrue(batteryCmd.action is AdminAction.RunDiagnostics)
    }

    @Test
    fun `wake word extractor cleans polite phrasing and handles wake word variants`() {
        val (wake1, cmd1) = FrankVoiceConstants.extractCommandFromWakeWord("Hey Frank please open YouTube")
        assertTrue(wake1)
        assertEquals("open YouTube", cmd1)

        val (wake2, cmd2) = FrankVoiceConstants.extractCommandFromWakeWord("Open camera, Frank")
        assertTrue(wake2)
        assertEquals("Open camera", cmd2)

        val (wake3, cmd3) = FrankVoiceConstants.extractCommandFromWakeWord("Can you please turn on flashlight Frank")
        assertTrue(wake3)
        assertEquals("turn on flashlight", cmd3)
    }

    @Test
    fun `device manager handles overlay permission checks and bringFrankToForeground`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceManager = SuperAdminDeviceManager(context)

        // Robolectric environment checks
        assertNotNull(deviceManager.hasOverlayPermission())

        val (foregroundSuccess, foregroundMsg) = deviceManager.bringFrankToForeground()
        assertTrue(foregroundSuccess)
        assertTrue(foregroundMsg.contains("Frank"))

        val (overlaySuccess, overlayMsg) = deviceManager.requestOverlayPermission()
        assertTrue(overlaySuccess)
    }

    @Test
    fun `floating overlay manager instantiates and checks overlay availability`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val overlayManager = com.example.service.FrankFloatingOverlayManager(context)
        assertNotNull(overlayManager)
        assertNotNull(overlayManager.canDrawOverlay())
    }

    @Test
    fun `frank accessibility service checks status and settings launcher`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val isEnabled = com.example.service.FrankAccessibilityService.isAccessibilityEnabled(context)
        assertNotNull(isEnabled)

        val (openOk, openMsg) = com.example.service.FrankAccessibilityService.openAccessibilitySettings(context)
        assertTrue(openOk)
        assertTrue(openMsg.contains("Accessibility Settings"))
    }

    @Test
    fun `frank notification listener service status and summaries`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val isEnabled = com.example.service.FrankNotificationListenerService.isNotificationListenerEnabled(context)
        assertNotNull(isEnabled)

        val (settingsOk, settingsMsg) = com.example.service.FrankNotificationListenerService.openNotificationAccessSettings(context)
        assertTrue(settingsOk)
        assertTrue(settingsMsg.contains("Notification Access Settings"))

        val summary = com.example.service.FrankNotificationListenerService.readNotificationsSummary()
        assertNotNull(summary)

        val (clearOk, clearMsg) = com.example.service.FrankNotificationListenerService.clearAll()
        assertTrue(clearOk)
    }

    @Test
    fun `frank timer manager handles countdown timers and precision stopwatch`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val timerManager = com.example.service.FrankTimerManager(context)

        // 1. Countdown timer
        val timer = timerManager.startTimer("Test Pasta", 180)
        assertEquals("Test Pasta", timer.label)
        assertEquals(180L, timer.totalSeconds)
        assertEquals(180L, timer.remainingSeconds)
        assertTrue(timer.isRunning)
        assertEquals("03:00", timer.formattedRemaining)

        timerManager.pauseTimer(timer.id)
        assertFalse(timerManager.timers.value.first { it.id == timer.id }.isRunning)

        timerManager.resumeTimer(timer.id)
        assertTrue(timerManager.timers.value.first { it.id == timer.id }.isRunning)

        timerManager.cancelTimer(timer.id)
        assertTrue(timerManager.timers.value.none { it.id == timer.id })

        // 2. Stopwatch
        timerManager.startStopwatch()
        assertTrue(timerManager.stopwatch.value.isRunning)

        timerManager.lapStopwatch()
        timerManager.pauseStopwatch()
        assertFalse(timerManager.stopwatch.value.isRunning)

        timerManager.resetStopwatch()
        assertEquals(0L, timerManager.stopwatch.value.elapsedMillis)

        timerManager.release()
    }

    @Test
    fun `offline math evaluator evaluates arithmetic and unit conversions`() {
        // Multiplication
        val mul = com.example.service.OfflineMathEvaluator.evaluate("what is 45 times 87")
        assertNotNull(mul)
        assertTrue(mul!!.success)
        assertTrue(mul.spokenAnswer.contains("3,915"))

        // Percentages
        val pct = com.example.service.OfflineMathEvaluator.evaluate("15 percent of 200")
        assertNotNull(pct)
        assertTrue(pct!!.success)
        assertTrue(pct.spokenAnswer.contains("30"))

        // Square root
        val sq = com.example.service.OfflineMathEvaluator.evaluate("square root of 144")
        assertNotNull(sq)
        assertTrue(sq!!.success)
        assertTrue(sq.spokenAnswer.contains("12"))

        // Unit conversion: miles to km
        val convMiles = com.example.service.OfflineMathEvaluator.evaluate("convert 10 miles to km")
        assertNotNull(convMiles)
        assertTrue(convMiles!!.success)
        assertTrue(convMiles.spokenAnswer.contains("16.09"))

        // Unit conversion: celsius to fahrenheit
        val convTemp = com.example.service.OfflineMathEvaluator.evaluate("convert 100 celsius to fahrenheit")
        assertNotNull(convTemp)
        assertTrue(convTemp!!.success)
        assertTrue(convTemp.spokenAnswer.contains("212"))
    }

    @Test
    fun `voice settings manager customizes pitch rate and wake word aliases`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settingsManager = com.example.service.VoiceSettingsManager(context)

        settingsManager.updatePrimaryWakeWord("Computer")
        assertEquals("Computer", settingsManager.settings.value.primaryWakeWord)

        settingsManager.addWakeAlias("Jarvis")
        assertTrue(settingsManager.settings.value.wakeAliases.contains("Jarvis"))

        settingsManager.updateSpeechPitch(1.2f)
        assertEquals(1.2f, settingsManager.settings.value.speechPitch, 0.01f)

        settingsManager.updateSpeechRate(1.1f)
        assertEquals(1.1f, settingsManager.settings.value.speechRate, 0.01f)

        // Verify dynamic wake word extraction recognizes new alias
        val (wakeTriggered, cmd) = FrankVoiceConstants.extractCommandFromWakeWord("Jarvis, what time is it")
        assertTrue(wakeTriggered)
        assertEquals("what time is it", cmd)
    }

    @Test
    fun `intent parser handles new high value actions`() {
        val parser = IntentParser()

        // 1. Accessibility navigation
        val homeCmd = parser.parseOnDevice("go home")
        assertTrue(homeCmd.action is AdminAction.GlobalNavigation)
        assertEquals("HOME", (homeCmd.action as AdminAction.GlobalNavigation).navigationType)

        val backCmd = parser.parseOnDevice("go back")
        assertTrue(backCmd.action is AdminAction.GlobalNavigation)
        assertEquals("BACK", (backCmd.action as AdminAction.GlobalNavigation).navigationType)

        val notifShadeCmd = parser.parseOnDevice("open notifications")
        assertTrue(notifShadeCmd.action is AdminAction.GlobalNavigation)
        assertEquals("NOTIFICATIONS", (notifShadeCmd.action as AdminAction.GlobalNavigation).navigationType)

        val screenCmd = parser.parseOnDevice("take screenshot")
        assertTrue(screenCmd.action is AdminAction.GlobalNavigation)
        assertEquals("SCREENSHOT", (screenCmd.action as AdminAction.GlobalNavigation).navigationType)

        val scrollCmd = parser.parseOnDevice("scroll down")
        assertTrue(scrollCmd.action is AdminAction.GlobalNavigation)
        assertEquals("SCROLL_DOWN", (scrollCmd.action as AdminAction.GlobalNavigation).navigationType)

        // 2. Notification listener actions
        val readNotifCmd = parser.parseOnDevice("read my notifications")
        assertTrue(readNotifCmd.action is AdminAction.NotificationAction)
        assertEquals("READ_ALL", (readNotifCmd.action as AdminAction.NotificationAction).operation)

        val clearNotifCmd = parser.parseOnDevice("clear notifications")
        assertTrue(clearNotifCmd.action is AdminAction.NotificationAction)
        assertEquals("CLEAR_ALL", (clearNotifCmd.action as AdminAction.NotificationAction).operation)

        // 3. Real-time timer and stopwatch
        val timerCmd = parser.parseOnDevice("set a timer for 10 minutes")
        assertTrue(timerCmd.action is AdminAction.TimerAction)
        val timerAction = timerCmd.action as AdminAction.TimerAction
        assertEquals("START", timerAction.operation)
        assertEquals(600L, timerAction.seconds)

        val stopwatchCmd = parser.parseOnDevice("start stopwatch")
        assertTrue(stopwatchCmd.action is AdminAction.TimerAction)
        assertEquals("START_STOPWATCH", (stopwatchCmd.action as AdminAction.TimerAction).operation)

        // 4. Camera quick snap
        val photoCmd = parser.parseOnDevice("take a photo")
        assertTrue(photoCmd.action is AdminAction.QuickCamera)
        assertEquals("PHOTO", (photoCmd.action as AdminAction.QuickCamera).mode)

        // 5. Offline math
        val mathCmd = parser.parseOnDevice("what is 50 times 4")
        assertTrue(mathCmd.action is AdminAction.OfflineMath)
    }

    @Test
    fun `close active app on screen and minimized app commands parsed and executed`() {
        val parser = IntentParser()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deviceManager = SuperAdminDeviceManager(context)

        // 1. Close active app on screen
        val closeActive1 = parser.parseOnDevice("close app")
        assertTrue(closeActive1.action is AdminAction.CloseApp)
        val closeAction1 = closeActive1.action as AdminAction.CloseApp
        assertEquals("", closeAction1.targetApp)
        assertFalse(closeAction1.closeAllMinimized)

        val closeActive2 = parser.parseOnDevice("close this app")
        assertTrue(closeActive2.action is AdminAction.CloseApp)

        val closeActive3 = parser.parseOnDevice("close active app")
        assertTrue(closeActive3.action is AdminAction.CloseApp)

        val closeActive4 = parser.parseOnDevice("exit app")
        assertTrue(closeActive4.action is AdminAction.CloseApp)

        // 2. Close specific app by name (active or minimized)
        val closeSpotify = parser.parseOnDevice("close Spotify")
        assertTrue(closeSpotify.action is AdminAction.CloseApp)
        val closeSpotifyAction = closeSpotify.action as AdminAction.CloseApp
        assertEquals("spotify", closeSpotifyAction.targetApp)

        val closeChrome = parser.parseOnDevice("kill Chrome")
        assertTrue(closeChrome.action is AdminAction.CloseApp)
        assertEquals("chrome", (closeChrome.action as AdminAction.CloseApp).targetApp)

        // 3. Close all minimized background apps
        val closeAll = parser.parseOnDevice("close background apps")
        assertTrue(closeAll.action is AdminAction.CloseApp)
        val closeAllAction = closeAll.action as AdminAction.CloseApp
        assertTrue(closeAllAction.closeAllMinimized)

        val closeMinimized = parser.parseOnDevice("close all minimized apps")
        assertTrue(closeMinimized.action is AdminAction.CloseApp)
        assertTrue((closeMinimized.action as AdminAction.CloseApp).closeAllMinimized)

        // 4. Device Manager execution
        val (closeActiveSuccess, closeActiveMsg) = deviceManager.closeActiveApp()
        assertTrue(closeActiveSuccess)
        assertNotNull(closeActiveMsg)

        val (closeNamedSuccess, closeNamedMsg) = deviceManager.closeAppByName("Settings")
        assertTrue(closeNamedSuccess)
        assertNotNull(closeNamedMsg)

        val (closeAllSuccess, closeAllMsg) = deviceManager.closeAllMinimizedApps()
        assertTrue(closeAllSuccess)
        assertNotNull(closeAllMsg)
    }

    @Test
    fun `internet search permission management and unrecognized command auto search verification`() {
        val parser = IntentParser()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voiceSettingsManager = com.example.service.VoiceSettingsManager(context)

        // 1. Initial permission state is disabled
        assertFalse(voiceSettingsManager.settings.value.isInternetSearchPermitted)

        // 2. Parse permission grant commands
        val allowSearch = parser.parseOnDevice("allow internet search")
        assertTrue(allowSearch.action is AdminAction.ManageSettings)
        val allowAction = allowSearch.action as AdminAction.ManageSettings
        assertEquals("INTERNET_SEARCH", allowAction.settingType)
        assertEquals("ON", allowAction.param)

        // Update permission
        voiceSettingsManager.updateInternetSearchPermitted(true)
        assertTrue(voiceSettingsManager.settings.value.isInternetSearchPermitted)

        // 3. Parse permission revoke commands
        val denySearch = parser.parseOnDevice("deny internet search")
        assertTrue(denySearch.action is AdminAction.ManageSettings)
        val denyAction = denySearch.action as AdminAction.ManageSettings
        assertEquals("INTERNET_SEARCH", denyAction.settingType)
        assertEquals("OFF", denyAction.param)

        voiceSettingsManager.updateInternetSearchPermitted(false)
        assertFalse(voiceSettingsManager.settings.value.isInternetSearchPermitted)

        // 4. Unrecognized command parsing
        val unrecognized = parser.parseOnDevice("what causes the northern lights aurora")
        assertTrue(unrecognized.action is AdminAction.UnrecognizedCommand)
        val unrecAction = unrecognized.action as AdminAction.UnrecognizedCommand
        assertEquals("what causes the northern lights aurora", unrecAction.query)

        // 5. Explicit manual search still parses as SearchWeb
        val manualSearch = parser.parseOnDevice("search quantum computing advances")
        assertTrue(manualSearch.action is AdminAction.SearchWeb)
        assertEquals("quantum computing advances", (manualSearch.action as AdminAction.SearchWeb).query)
    }

    @Test
    fun `microphone icon drawable and wake word only activation in background verified`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify custom microphone vector drawable exists
        val micDrawable = androidx.core.content.ContextCompat.getDrawable(context, com.example.R.drawable.ic_microphone)
        assertNotNull(micDrawable)

        // 2. Verify background service defaults to wake-word-only mode (direct mode disabled by default)
        assertFalse(VoiceActivationService.isContinuousListeningMode.value)

        // 3. Verify wake word triggers vs ambient speech separation
        val (wakeTriggered1, cmd1) = FrankVoiceConstants.extractCommandFromWakeWord("Hey Frank, turn on flashlight")
        assertTrue(wakeTriggered1)
        assertEquals("turn on flashlight", cmd1)

        val (wakeTriggered2, cmd2) = FrankVoiceConstants.extractCommandFromWakeWord("Ambient background chatter and conversation")
        assertFalse(wakeTriggered2)
        assertEquals("Ambient background chatter and conversation", cmd2)

        val (wakeTriggered3, _) = FrankVoiceConstants.extractCommandFromWakeWord("Random ambient noise and words")
        assertFalse(wakeTriggered3)

        // 4. Verify foreground / background state management
        VoiceActivationService.setAppInForeground(false)
        assertFalse(VoiceActivationService.isAppInForeground.value)

        VoiceActivationService.setAppInForeground(true)
        assertTrue(VoiceActivationService.isAppInForeground.value)
    }
}
