package com.example.service

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class AdminAction {
    data class OpenApp(val appName: String) : AdminAction()
    data class PlayMusic(val query: String) : AdminAction()
    data class MediaControl(val command: String, val param: String = "") : AdminAction() // PLAY, PAUSE, NEXT, PREVIOUS, STOP
    data class ManageSettings(val settingType: String, val param: String = "") : AdminAction()
    data class FileAction(val operation: String, val fileName: String = "", val content: String = "", val folder: String = "") : AdminAction()
    data class RememberFact(val key: String, val value: String, val category: String = "General") : AdminAction()
    data class RecallMemory(val query: String) : AdminAction()
    data class SearchWeb(val query: String) : AdminAction()
    object RunDiagnostics : AdminAction()
    object PurgeMemory : AdminAction()
    data class SetReminder(val title: String, val delayMinutes: Long, val type: String = "REMINDER") : AdminAction()
    object ListReminders : AdminAction()
    object ClearReminders : AdminAction()
    object QueryTime : AdminAction()
    object QueryDate : AdminAction()
    object BringFrankToFront : AdminAction()
    data class GlobalNavigation(val navigationType: String, val param: String = "") : AdminAction()
    data class NotificationAction(val operation: String) : AdminAction()
    data class TimerAction(val operation: String, val label: String = "", val seconds: Long = 0) : AdminAction()
    data class QuickCamera(val mode: String = "PHOTO") : AdminAction()
    data class OfflineMath(val mathResult: MathEvalResult) : AdminAction()
    data class CloseApp(val targetApp: String = "", val closeAllMinimized: Boolean = false) : AdminAction()
    data class UnrecognizedCommand(val query: String) : AdminAction()
    data class GeneralAssistant(val text: String) : AdminAction()
}

data class ParsedCommand(
    val rawText: String,
    val action: AdminAction,
    val confidence: Float,
    val explanation: String
)

class IntentParser {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Parses text locally with 100% privacy and zero network transfer.
     */
    fun parseOnDevice(text: String): ParsedCommand {
        val raw = text.trim()
        val lower = raw.lowercase()

        // 0. Time & Date Queries
        if (lower in listOf("time", "what time is it", "what is the time", "what's the time", "tell me the time", "current time", "what time", "the time") ||
            lower.startsWith("what time") || lower.startsWith("tell me the time") || lower.startsWith("current time")
        ) {
            return ParsedCommand(raw, AdminAction.QueryTime, 0.99f, "Retrieving current local time")
        }

        if (lower in listOf("date", "what date is it", "what's the date", "what is today's date", "what date is today", "what day is it", "today's date", "what day is today", "today") ||
            lower.startsWith("what date") || lower.startsWith("what day is") || lower.startsWith("today's date")
        ) {
            return ParsedCommand(raw, AdminAction.QueryDate, 0.99f, "Retrieving current local date")
        }

        // 0b. Bring Frank to Foreground / Main UI
        if (lower in listOf("open frank", "show frank", "bring frank to front", "bring frank to the front", "launch frank", "open super admin", "frank app", "show app", "open assistant", "bring to front") ||
            lower.startsWith("open frank") || lower.startsWith("show frank")
        ) {
            return ParsedCommand(raw, AdminAction.BringFrankToFront, 0.99f, "Bringing Frank to foreground")
        }

        // 0c. Overlay & Override System Permission
        if (lower.contains("overlay") || lower.contains("override") || lower.contains("appear on top") || lower.contains("draw over other apps")) {
            return ParsedCommand(raw, AdminAction.ManageSettings("OVERLAY"), 0.99f, "Opening Override Other Apps settings")
        }

        if (lower.contains("accessibility setting") || lower == "accessibility" || lower == "open accessibility") {
            return ParsedCommand(raw, AdminAction.ManageSettings("ACCESSIBILITY"), 0.99f, "Opening Accessibility settings")
        }

        // 0d. Global Accessibility Navigation (Hands-Free Global System Control)
        if (lower in listOf("go home", "go to home", "return home", "home screen", "desktop", "home")) {
            return ParsedCommand(raw, AdminAction.GlobalNavigation("HOME"), 0.99f, "Navigating to Android Home Screen")
        }
        if (lower in listOf("go back", "back", "navigate back", "return back")) {
            return ParsedCommand(raw, AdminAction.GlobalNavigation("BACK"), 0.99f, "Navigating Back")
        }
        if (lower in listOf("open notifications", "show notifications", "pull down notifications", "notification shade", "view notifications", "notifications shade")) {
            return ParsedCommand(raw, AdminAction.GlobalNavigation("NOTIFICATIONS"), 0.99f, "Opening Notification Shade")
        }
        if (lower in listOf("open quick settings", "quick settings", "system toggles", "pull down quick settings")) {
            return ParsedCommand(raw, AdminAction.GlobalNavigation("QUICK_SETTINGS"), 0.99f, "Opening Quick Settings")
        }
        if (lower in listOf("recent apps", "show recents", "overview", "switch apps", "task switcher", "app switcher", "recents")) {
            return ParsedCommand(raw, AdminAction.GlobalNavigation("RECENTS"), 0.99f, "Opening Recent Apps Switcher")
        }
        if (lower in listOf("take screenshot", "capture screen", "screenshot", "snap screen", "screen capture")) {
            return ParsedCommand(raw, AdminAction.GlobalNavigation("SCREENSHOT"), 0.99f, "Taking System Screenshot")
        }
        if (lower in listOf("lock screen", "lock device", "lock phone", "turn off screen")) {
            return ParsedCommand(raw, AdminAction.GlobalNavigation("LOCK"), 0.99f, "Locking Device Screen")
        }
        if (lower in listOf("scroll down", "scroll down screen", "scroll page down")) {
            return ParsedCommand(raw, AdminAction.GlobalNavigation("SCROLL_DOWN"), 0.98f, "Scrolling Down")
        }
        if (lower in listOf("scroll up", "scroll up screen", "scroll page up")) {
            return ParsedCommand(raw, AdminAction.GlobalNavigation("SCROLL_UP"), 0.98f, "Scrolling Up")
        }
        if (lower.startsWith("click ") || lower.startsWith("tap ") || lower.startsWith("press ")) {
            val target = raw.substringAfter(" ").trim()
            if (target.isNotBlank() && !target.contains("photo") && !target.contains("picture")) {
                return ParsedCommand(raw, AdminAction.GlobalNavigation("CLICK", target), 0.95f, "Accessibility clicking on: $target")
            }
        }

        // 0e. Hands-Free Notification Listener & Reading
        if (lower in listOf("read notifications", "read my notifications", "what are my notifications", "check notifications", "read alerts", "what are my alerts", "notifications")) {
            return ParsedCommand(raw, AdminAction.NotificationAction("READ_ALL"), 0.98f, "Reading unread notifications aloud")
        }
        if (lower in listOf("read latest message", "read newest notification", "read latest notification", "what is my latest message", "latest message", "read message")) {
            return ParsedCommand(raw, AdminAction.NotificationAction("READ_LATEST"), 0.98f, "Reading latest incoming notification")
        }
        if (lower in listOf("clear notifications", "clear my notifications", "dismiss notifications", "dismiss all notifications", "clear alerts")) {
            return ParsedCommand(raw, AdminAction.NotificationAction("CLEAR_ALL"), 0.98f, "Dismissing active notifications")
        }
        if (lower.contains("notification access") || lower.contains("notification permission") || lower.contains("notification listener")) {
            return ParsedCommand(raw, AdminAction.NotificationAction("SETTINGS"), 0.98f, "Opening Notification Listener access")
        }

        // 0f. Camera Quick Snap & Video Recording
        if (lower in listOf("take a photo", "take photo", "snap photo", "take picture", "take a picture", "snap picture", "snap a picture", "take selfie", "open camera to take photo", "capture photo")) {
            return ParsedCommand(raw, AdminAction.QuickCamera("PHOTO"), 0.99f, "Activating camera for instant photo capture")
        }
        if (lower in listOf("record video", "take video", "start video recording", "record a video", "shoot video")) {
            return ParsedCommand(raw, AdminAction.QuickCamera("VIDEO"), 0.99f, "Activating video camera")
        }

        // 0g. Stopwatch
        if (lower in listOf("start stopwatch", "begin stopwatch", "stopwatch start", "launch stopwatch")) {
            return ParsedCommand(raw, AdminAction.TimerAction("START_STOPWATCH"), 0.98f, "Starting stopwatch")
        }
        if (lower in listOf("stop stopwatch", "pause stopwatch", "halt stopwatch")) {
            return ParsedCommand(raw, AdminAction.TimerAction("STOP_STOPWATCH"), 0.98f, "Pausing stopwatch")
        }
        if (lower in listOf("reset stopwatch", "clear stopwatch")) {
            return ParsedCommand(raw, AdminAction.TimerAction("RESET_STOPWATCH"), 0.98f, "Resetting stopwatch")
        }
        if (lower in listOf("lap stopwatch", "lap", "record lap")) {
            return ParsedCommand(raw, AdminAction.TimerAction("LAP_STOPWATCH"), 0.98f, "Recording stopwatch lap")
        }

        // 0h. Real-time Countdown Timer Controls
        if (lower in listOf("pause timer", "stop timer", "halt timer")) {
            return ParsedCommand(raw, AdminAction.TimerAction("PAUSE"), 0.98f, "Pausing countdown timer")
        }
        if (lower in listOf("resume timer", "unpause timer", "continue timer")) {
            return ParsedCommand(raw, AdminAction.TimerAction("RESUME"), 0.98f, "Resuming countdown timer")
        }
        if (lower in listOf("cancel timer", "cancel timers", "delete timer", "reset timer")) {
            return ParsedCommand(raw, AdminAction.TimerAction("CANCEL"), 0.98f, "Canceling active timer")
        }
        if (lower in listOf("timer status", "check timer", "how much time is left", "time left", "timer")) {
            return ParsedCommand(raw, AdminAction.TimerAction("STATUS"), 0.98f, "Checking active timer status")
        }

        // Check for countdown timer with duration: "set a timer for 10 minutes", "timer 5 minutes", "timer 30 seconds"
        if (lower.startsWith("set a timer for ") || lower.startsWith("set timer for ") ||
            lower.startsWith("timer for ") || (lower.startsWith("timer ") && (lower.contains("min") || lower.contains("sec") || lower.contains("hour")))
        ) {
            val minMatch = Regex("(\\d+)\\s*(?:min|minute|minutes)").find(lower)
            val secMatch = Regex("(\\d+)\\s*(?:sec|second|seconds)").find(lower)
            val hrMatch = Regex("(\\d+)\\s*(?:hr|hour|hours)").find(lower)
            var totalSeconds = 0L
            if (minMatch != null) totalSeconds += (minMatch.groupValues[1].toLongOrNull() ?: 0L) * 60L
            if (secMatch != null) totalSeconds += (secMatch.groupValues[1].toLongOrNull() ?: 0L)
            if (hrMatch != null) totalSeconds += (hrMatch.groupValues[1].toLongOrNull() ?: 0L) * 3600L

            if (totalSeconds > 0) {
                val label = raw.replace(Regex("(?i)^(set a timer for|set timer for|timer for|timer)\\s*"), "").trim()
                return ParsedCommand(
                    raw,
                    AdminAction.TimerAction("START", label.ifBlank { "Countdown Timer" }, totalSeconds),
                    0.99f,
                    "Starting active countdown timer for $totalSeconds seconds"
                )
            }
        }

        // 0i. Offline Math & Unit Conversions (Zero latency, local calculation)
        val offlineMath = OfflineMathEvaluator.evaluate(raw)
        if (offlineMath != null) {
            return ParsedCommand(
                raw,
                AdminAction.OfflineMath(offlineMath),
                0.99f,
                "Offline math evaluated: ${offlineMath.displayText}"
            )
        }

        // 1. Direct App Launches (e.g. user simply says "youtube" or "camera")
        val directCommonApps = listOf(
            "youtube", "camera", "chrome", "settings", "whatsapp", "calculator",
            "maps", "spotify", "music", "gallery", "photos", "clock", "calendar", "files", "browser"
        )
        if (directCommonApps.contains(lower)) {
            return ParsedCommand(raw, AdminAction.OpenApp(lower), 0.96f, "Super Admin executing direct app launch for: $lower")
        }

        // 1b. App Launch Intent with prefix
        if (lower.startsWith("open app ") || lower.startsWith("launch app ") ||
            lower.startsWith("run app ") || lower.startsWith("start app ") ||
            lower.startsWith("open ") || lower.startsWith("launch ") ||
            lower.startsWith("run ") || lower.startsWith("start ") ||
            lower.startsWith("go to ") || lower.startsWith("switch to ")
        ) {
            val appQuery = lower.removePrefix("open app ")
                .removePrefix("launch app ")
                .removePrefix("run app ")
                .removePrefix("start app ")
                .removePrefix("open ")
                .removePrefix("launch ")
                .removePrefix("run ")
                .removePrefix("start ")
                .removePrefix("go to ")
                .removePrefix("switch to ")
                .trim()

            // Special check for settings, document or music
            if (appQuery.contains("wifi") || appQuery.contains("wi-fi")) {
                return ParsedCommand(raw, AdminAction.ManageSettings("WIFI"), 0.95f, "Opening Wi-Fi configuration")
            } else if (appQuery.contains("bluetooth")) {
                return ParsedCommand(raw, AdminAction.ManageSettings("BLUETOOTH"), 0.95f, "Opening Bluetooth configuration")
            } else if (appQuery.contains("battery")) {
                return ParsedCommand(raw, AdminAction.ManageSettings("BATTERY"), 0.95f, "Opening Battery Saver settings")
            } else if (appQuery.contains("display") || appQuery.contains("brightness")) {
                return ParsedCommand(raw, AdminAction.ManageSettings("DISPLAY"), 0.95f, "Opening Display settings")
            } else if (appQuery.contains("storage")) {
                return ParsedCommand(raw, AdminAction.ManageSettings("STORAGE"), 0.95f, "Opening Storage settings")
            } else if (appQuery.contains("setting")) {
                return ParsedCommand(raw, AdminAction.ManageSettings("ALL"), 0.95f, "Opening System Settings")
            } else if (appQuery.contains("music") || appQuery.contains("player") || appQuery.contains("spotify")) {
                return ParsedCommand(raw, AdminAction.PlayMusic(appQuery), 0.95f, "Activating music engine")
            } else if (appQuery.contains("doc") || appQuery.contains("file") || appQuery.contains("note")) {
                return ParsedCommand(raw, AdminAction.FileAction("READ", appQuery), 0.85f, "Locating document")
            }
            return ParsedCommand(raw, AdminAction.OpenApp(appQuery), 0.95f, "Super Admin executing app launch for: $appQuery")
        }

        // 2. Media & Music Player Intents (Play, Pause, Skip, Next, Previous)
        if (lower == "pause" || lower == "pause music" || lower == "pause track" || lower == "pause song" ||
            lower == "stop music" || lower == "stop audio" || lower == "halt music" || lower == "stop playback"
        ) {
            return ParsedCommand(raw, AdminAction.MediaControl("PAUSE"), 0.98f, "Pausing playback via MediaController")
        }

        if (lower == "next" || lower == "next track" || lower == "next song" ||
            lower == "skip" || lower == "skip track" || lower == "skip song" || lower == "skip to next track"
        ) {
            return ParsedCommand(raw, AdminAction.MediaControl("NEXT"), 0.98f, "Skipping to next track via MediaController")
        }

        if (lower == "previous" || lower == "previous track" || lower == "previous song" ||
            lower == "prev track" || lower == "prev song" || lower == "go back a track" || lower == "back track"
        ) {
            return ParsedCommand(raw, AdminAction.MediaControl("PREVIOUS"), 0.98f, "Skipping to previous track via MediaController")
        }

        if (lower == "resume" || lower == "resume music" || lower == "unpause" || lower == "continue music") {
            return ParsedCommand(raw, AdminAction.MediaControl("PLAY"), 0.98f, "Resuming playback via MediaController")
        }

        if (lower.startsWith("play music") || lower.startsWith("play track") ||
            lower.startsWith("play song") || lower.startsWith("play sound") ||
            lower.startsWith("play ") || lower == "play" || lower == "music"
        ) {
            val query = lower.removePrefix("play music")
                .removePrefix("play track")
                .removePrefix("play song")
                .removePrefix("play sound")
                .removePrefix("play")
                .trim()
            return ParsedCommand(raw, AdminAction.MediaControl("PLAY", query.ifEmpty { "focus" }), 0.95f, "Dispatching playback via MediaController")
        }

        // 3. System Hardware & Settings
        if (lower.contains("beep") || lower.contains("chime") || lower.contains("recognition sound") || lower.contains("listening sound")) {
            val state = if (lower.contains("unmute") || lower.contains("enable") || lower.contains("turn on") || lower.contains("activate")) "OFF" else "ON"
            return ParsedCommand(raw, AdminAction.ManageSettings("MUTE_BEEPS", state), 0.98f, "Configuring voice recognition beep muting: $state")
        }
        if (lower.contains("flashlight") || lower.contains("torch") ||
            lower == "light on" || lower == "light off" || lower == "lights on" || lower == "lights off" ||
            lower.contains("turn on light") || lower.contains("turn off light")
        ) {
            val state = if (lower.contains("off") || lower.contains("disable")) "OFF" else "ON"
            return ParsedCommand(raw, AdminAction.ManageSettings("FLASHLIGHT", state), 0.98f, "Super Admin flashlight trigger: $state")
        }
        if (lower.contains("volume") || lower.contains("sound") || lower.contains("mute") ||
            lower.contains("louder") || lower.contains("quieter") || lower.contains("silence")
        ) {
            val param = when {
                lower.contains("mute") || lower.contains("zero") || lower.contains("silence") -> "0"
                lower.contains("max") || lower.contains("full") || lower.contains("100") -> "100"
                lower.contains("up") || lower.contains("louder") || lower.contains("increase") -> "UP"
                lower.contains("down") || lower.contains("quieter") || lower.contains("decrease") -> "DOWN"
                else -> {
                    val digits = lower.filter { it.isDigit() }
                    if (digits.isNotEmpty()) digits else "50"
                }
            }
            return ParsedCommand(raw, AdminAction.ManageSettings("VOLUME", param), 0.95f, "Adjusting device audio output")
        }
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return ParsedCommand(raw, AdminAction.ManageSettings("WIFI"), 0.95f, "Directing to Wi-Fi settings")
        }
        if (lower.contains("bluetooth")) {
            return ParsedCommand(raw, AdminAction.ManageSettings("BLUETOOTH"), 0.95f, "Directing to Bluetooth settings")
        }
        if (lower.contains("battery optimization") || lower.contains("unrestrict battery") ||
            lower.contains("ignore battery optimization") || lower.contains("disable battery optimization") ||
            lower.contains("background power") || lower.contains("unrestricted power")
        ) {
            return ParsedCommand(raw, AdminAction.ManageSettings("BATTERY_OPTIMIZATION"), 0.98f, "Directing to battery optimization exemption")
        }
        if (lower.contains("battery") || lower.contains("how much power") || lower.contains("charge level")) {
            return ParsedCommand(raw, AdminAction.RunDiagnostics, 0.98f, "Checking battery status & diagnostics")
        }
        if (lower.contains("display") || lower.contains("brightness") || lower.contains("screen")) {
            return ParsedCommand(raw, AdminAction.ManageSettings("DISPLAY"), 0.95f, "Directing to Display & Brightness")
        }
        if (lower.contains("storage") || lower.contains("disk")) {
            return ParsedCommand(raw, AdminAction.ManageSettings("STORAGE"), 0.95f, "Directing to Storage Manager")
        }

        // 4. File System & Workspace Operations (List files, create folder, delete file/folder)
        if (lower == "list files" || lower == "list all files" || lower == "show files" ||
            lower == "show all files" || lower == "show my files" || lower == "list my files" ||
            lower == "what files do i have" || lower == "list documents" || lower == "show documents" ||
            lower == "explore files" || lower == "browse files"
        ) {
            return ParsedCommand(raw, AdminAction.FileAction(operation = "LIST"), 0.98f, "Listing workspace files and folders")
        }

        if (lower.startsWith("list files in ") || lower.startsWith("show files in ") ||
            lower.startsWith("list folder ") || lower.startsWith("show folder ")
        ) {
            val folder = lower.removePrefix("list files in ")
                .removePrefix("show files in ")
                .removePrefix("list folder ")
                .removePrefix("show folder ")
                .removeSuffix(" folder")
                .removeSuffix(" directory")
                .trim()
            return ParsedCommand(raw, AdminAction.FileAction(operation = "LIST", folder = folder), 0.96f, "Listing files in '$folder'")
        }

        if (lower.startsWith("create folder ") || lower.startsWith("make folder ") ||
            lower.startsWith("new folder ") || lower.startsWith("create directory ") ||
            lower.startsWith("make directory ") || lower.startsWith("new directory ") ||
            lower.startsWith("mkdir ")
        ) {
            val folderName = lower.removePrefix("create folder ")
                .removePrefix("make folder ")
                .removePrefix("new folder ")
                .removePrefix("create directory ")
                .removePrefix("make directory ")
                .removePrefix("new directory ")
                .removePrefix("mkdir ")
                .removePrefix("named ")
                .removePrefix("called ")
                .trim()
            return ParsedCommand(raw, AdminAction.FileAction(operation = "CREATE_FOLDER", fileName = folderName), 0.98f, "Creating directory '$folderName'")
        }

        if (lower.startsWith("delete folder ") || lower.startsWith("remove folder ") ||
            lower.startsWith("delete directory ") || lower.startsWith("remove directory ")
        ) {
            val folderName = lower.removePrefix("delete folder ")
                .removePrefix("remove folder ")
                .removePrefix("delete directory ")
                .removePrefix("remove directory ")
                .trim()
            return ParsedCommand(raw, AdminAction.FileAction(operation = "DELETE_FOLDER", fileName = folderName), 0.98f, "Deleting directory '$folderName'")
        }

        if (lower.startsWith("delete file ") || lower.startsWith("remove file ") ||
            lower.startsWith("delete document ") || lower.startsWith("remove document ") ||
            lower.startsWith("delete note ")
        ) {
            val fileName = raw.substringAfter(" ").substringAfter(" ").trim()
            return ParsedCommand(raw, AdminAction.FileAction("DELETE_FILE", fileName), 0.98f, "Super Admin file removal")
        }

        if (lower.startsWith("create file ") || lower.startsWith("create document ") ||
            lower.startsWith("create note ") || lower.startsWith("make a note ") || lower.startsWith("new note ") ||
            lower.startsWith("new file ") || lower.startsWith("write file ")
        ) {
            val remainder = raw.substringAfter(" ").substringAfter(" ")
            val parts = remainder.split(Regex(" with content | containing | : "), 2)
            val name = parts.firstOrNull()?.trim() ?: "Untitled_Note.txt"
            val content = if (parts.size > 1) parts[1].trim() else "Note created by Frank Super Admin."
            return ParsedCommand(raw, AdminAction.FileAction("CREATE", name, content), 0.95f, "Creating private on-device document")
        }

        if (lower.startsWith("read file ") || lower.startsWith("read doc ") ||
            lower.startsWith("open file ") || lower.startsWith("open document ") ||
            lower.startsWith("view file ")
        ) {
            val fileName = raw.substringAfter(" ").substringAfter(" ").trim()
            return ParsedCommand(raw, AdminAction.FileAction("READ", fileName), 0.92f, "Opening sandboxed file")
        }

        if (lower.contains("file count") || lower.contains("storage stats") || lower.contains("how many files")) {
            return ParsedCommand(raw, AdminAction.FileAction("STATS"), 0.95f, "Retrieving file system storage stats")
        }

        // 5. On-Device Memory Store & Recall
        if (lower.startsWith("remember ") || lower.startsWith("save ") || lower.startsWith("store ")) {
            val clean = raw.removePrefix("remember ").removePrefix("save ").removePrefix("store ").trim()
            // e.g. "my sister's birthday is June 4" or "wifi password is secret"
            val parts = clean.split(Regex(" is | as | : "), 2)
            val key = parts.firstOrNull()?.trim() ?: "Memory_${System.currentTimeMillis()}"
            val value = if (parts.size > 1) parts[1].trim() else clean
            return ParsedCommand(raw, AdminAction.RememberFact(key, value), 0.95f, "Storing encrypted memory item in local SQLite")
        }
        if (lower.startsWith("what is ") || lower.startsWith("what's ") ||
            lower.startsWith("where is ") || lower.startsWith("recall ") ||
            lower.startsWith("find ") || lower.startsWith("lookup ")
        ) {
            val query = lower.removePrefix("what is ")
                .removePrefix("what's ")
                .removePrefix("where is ")
                .removePrefix("recall ")
                .removePrefix("my ")
                .trim()
            return ParsedCommand(raw, AdminAction.RecallMemory(query), 0.90f, "Querying on-device memory index")
        }

        // 5b. Scheduled Reminders, Alarms & Timers
        if (lower.startsWith("remind me") || lower.startsWith("set a reminder") ||
            lower.startsWith("set reminder") || lower.startsWith("set timer") ||
            lower.startsWith("set a timer") || lower.startsWith("timer ") ||
            lower.startsWith("set alarm") || lower.startsWith("set an alarm") ||
            lower.contains("list reminders") || lower.contains("my reminders") ||
            lower.contains("show reminders") || lower.contains("what are my reminders") ||
            lower.contains("clear reminders") || lower.contains("delete reminders")
        ) {
            if (lower.contains("list reminders") || lower.contains("my reminders") ||
                lower.contains("show reminders") || lower.contains("what are my reminders")
            ) {
                return ParsedCommand(raw, AdminAction.ListReminders, 0.98f, "Listing active scheduled reminders")
            }
            if (lower.contains("clear reminders") || lower.contains("delete reminders")) {
                return ParsedCommand(raw, AdminAction.ClearReminders, 0.98f, "Clearing completed reminders")
            }

            val isTimer = lower.contains("timer")
            val isAlarm = lower.contains("alarm")
            val type = if (isTimer) "TIMER" else if (isAlarm) "ALARM" else "REMINDER"

            var delayMinutes = 15L
            val minMatch = Regex("(\\d+)\\s*(?:min|minute|minutes)").find(lower)
            val hrMatch = Regex("(\\d+)\\s*(?:hr|hour|hours)").find(lower)
            val secMatch = Regex("(\\d+)\\s*(?:sec|second|seconds)").find(lower)

            if (minMatch != null) {
                delayMinutes = minMatch.groupValues[1].toLongOrNull() ?: 15L
            } else if (hrMatch != null) {
                delayMinutes = (hrMatch.groupValues[1].toLongOrNull() ?: 1L) * 60L
            } else if (secMatch != null) {
                delayMinutes = 1L
            } else if (lower.contains("tomorrow")) {
                delayMinutes = 1440L
            }

            var title = raw
            if (lower.contains(" to ")) {
                title = raw.substring(lower.indexOf(" to ") + 4).trim()
                title = title.replace(Regex("(?i)\\s*in\\s+\\d+\\s*(?:min|minute|minutes|hr|hour|hours|sec|seconds)"), "").trim()
            } else if (isTimer) {
                title = "$delayMinutes Minute Timer"
            } else if (isAlarm) {
                title = "$delayMinutes Minute Alarm"
            } else {
                title = raw.removePrefix("remind me ").removePrefix("set a reminder ").removePrefix("set reminder ").trim()
                title = title.replace(Regex("(?i)in\\s+\\d+\\s*(?:min|minute|minutes|hr|hour|hours|sec|seconds)"), "").trim()
            }

            if (title.isBlank()) {
                title = if (isTimer) "$delayMinutes min timer" else "Scheduled reminder"
            }

            return ParsedCommand(
                raw,
                AdminAction.SetReminder(title, delayMinutes, type),
                0.95f,
                "Scheduling $type in $delayMinutes min: $title"
            )
        }

        // 6. Diagnostics & System Super Admin
        if (lower.contains("diagnostics") || lower.contains("system status") ||
            lower.contains("device health") || lower.contains("ram") || lower.contains("memory audit")
        ) {
            return ParsedCommand(raw, AdminAction.RunDiagnostics, 0.98f, "Running Super Admin device diagnostics")
        }

        // 7. Purge Memory
        if (lower.contains("purge all memory") || lower.contains("wipe memory") || lower.contains("clear all memory")) {
            return ParsedCommand(raw, AdminAction.PurgeMemory, 0.99f, "Super Admin privacy memory purge request")
        }

        // 8. Internet Search Permission Configuration
        if (lower in listOf("allow internet search", "permit internet search", "enable internet search", "turn on internet search", "allow web search", "enable web search", "permit web search")) {
            return ParsedCommand(raw, AdminAction.ManageSettings("INTERNET_SEARCH", "ON"), 0.99f, "Granting permission to search internet for unknown commands")
        }
        if (lower in listOf("deny internet search", "disallow internet search", "disable internet search", "turn off internet search", "revoke internet search", "block internet search", "disable web search")) {
            return ParsedCommand(raw, AdminAction.ManageSettings("INTERNET_SEARCH", "OFF"), 0.99f, "Revoking permission to search internet for unknown commands")
        }

        // 9. Close Active App on Screen or When Minimized
        if (lower in listOf(
                "close all apps", "close all minimized apps", "close background apps",
                "kill background apps", "kill all apps", "clear all apps", "close minimized apps",
                "kill minimized apps", "terminate background apps", "close all background apps"
            )
        ) {
            return ParsedCommand(raw, AdminAction.CloseApp(targetApp = "", closeAllMinimized = true), 0.99f, "Closing all minimized background apps")
        }

        if (lower in listOf(
                "close app", "close this app", "close active app", "close current app",
                "exit app", "exit this app", "quit app", "quit this app",
                "kill this app", "terminate this app", "dismiss app", "shut down app",
                "close the app", "close active", "close screen app", "kill active app"
            )
        ) {
            return ParsedCommand(raw, AdminAction.CloseApp(targetApp = ""), 0.99f, "Closing active app on screen")
        }

        if (lower.startsWith("close ") || lower.startsWith("kill ") ||
            lower.startsWith("exit ") || lower.startsWith("terminate ") ||
            lower.startsWith("quit ") || lower.startsWith("shut down ") ||
            lower.startsWith("dismiss ") || lower.startsWith("stop app ")
        ) {
            val target = lower.removePrefix("close ")
                .removePrefix("kill ")
                .removePrefix("exit ")
                .removePrefix("terminate ")
                .removePrefix("quit ")
                .removePrefix("shut down ")
                .removePrefix("dismiss ")
                .removePrefix("stop app ")
                .removePrefix("app ")
                .trim()
            if (target.isNotBlank() && target != "all" && !target.contains("notification") &&
                !target.contains("reminder") && !target.contains("timer") && !target.contains("stopwatch")
            ) {
                return ParsedCommand(raw, AdminAction.CloseApp(targetApp = target), 0.96f, "Closing app: $target (active or minimized)")
            }
        }

        // 10. Manual Web Search
        if (lower.startsWith("search ") || lower.startsWith("google ") || lower.startsWith("web search ")) {
            val query = raw.removePrefix("search ").removePrefix("google ").removePrefix("web search ").trim()
            return ParsedCommand(raw, AdminAction.SearchWeb(query), 0.92f, "Dispatching Web Search Intent")
        }

        // 11. Core Frank Assistant Queries
        if (lower in listOf("who are you", "what is your name", "who made you", "what can you do", "help", "commands", "about you", "what are you", "hi", "hello", "hey") ||
            lower.startsWith("who are you") || lower.startsWith("what can you do")
        ) {
            return ParsedCommand(raw, AdminAction.GeneralAssistant(raw), 0.95f, "Frank core assistant info")
        }

        // 12. Fallback: Command not understood -> Dispatch UnrecognizedCommand to check internet search permission
        return ParsedCommand(raw, AdminAction.UnrecognizedCommand(raw), 0.35f, "Command not understood - evaluating internet search permission")
    }

    /**
     * Optional Gemini AI reasoning if user opts into Hybrid Cloud Boost.
     */
    suspend fun queryGeminiBoost(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Running in 100% on-device local privacy mode. Add your Gemini API key in Secrets for hybrid cloud reasoning."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val jsonPayload = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are Frank, a private on-device AI Super Admin for Android. Answer concisely: $prompt")
                        })
                    })
                })
            }
            put("contents", contents)
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrEmpty()) {
                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text")
                text ?: "Command acknowledged by Frank."
            } else {
                "Cloud boost unavailable (${response.code}). Falling back to on-device memory."
            }
        } catch (e: Exception) {
            "Local processing active: ${e.message}"
        }
    }
}
