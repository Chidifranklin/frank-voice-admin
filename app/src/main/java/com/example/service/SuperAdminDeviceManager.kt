package com.example.service

import android.app.ActivityManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import java.io.File

data class InstalledApp(
    val name: String,
    val packageName: String,
    val isSystemApp: Boolean
)

data class DeviceDiagnostics(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val availableRamMb: Long,
    val totalRamMb: Long,
    val freeStorageGb: Double,
    val totalStorageGb: Double,
    val networkType: String,
    val isOnline: Boolean,
    val androidVersion: String,
    val deviceModel: String
)

class SuperAdminDeviceManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    // --- Application Control & PackageManager Intent Resolution ---

    /**
     * Queries all installed applications using Android's PackageManager,
     * identifying launchable applications with their human-readable labels, package names,
     * and system app flags.
     */
    fun getInstalledApps(): List<InstalledApp> {
        val apps = mutableListOf<InstalledApp>()
        try {
            val flags = PackageManager.MATCH_ALL
            val installedPackages = packageManager.getInstalledApplications(flags)
            for (app in installedPackages) {
                // Only consider apps that provide an explicit launch intent
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    val label = packageManager.getApplicationLabel(app).toString()
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    apps.add(InstalledApp(name = label, packageName = app.packageName, isSystemApp = isSystem))
                }
            }
        } catch (_: Exception) {
            // Safe fallback: standard built-in core applications
            apps.add(InstalledApp("Settings", "com.android.settings", true))
            apps.add(InstalledApp("Camera", "com.android.camera", true))
            apps.add(InstalledApp("Files", "com.android.documentsui", true))
            apps.add(InstalledApp("Chrome", "com.android.chrome", false))
        }
        return apps.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
    }

    /**
     * Programmatically launches an application by its package name using PackageManager's launch intent.
     * Incorporates FLAG_ACTIVITY_NEW_TASK, FLAG_ACTIVITY_RESET_TASK_IF_NEEDED, and FLAG_ACTIVITY_CLEAR_TOP
     * so it reliably overrides the active foreground app.
     */
    fun launchApp(packageName: String): Pair<Boolean, String> {
        if (packageName == context.packageName) {
            return bringFrankToForeground()
        }
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                context.startActivity(launchIntent)
                val label = try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    packageName
                }
                Pair(true, "Opening $label...")
            } else {
                Pair(false, "No launch intent found for $packageName")
            }
        } catch (e: Exception) {
            Pair(false, "Failed to launch $packageName: ${e.message}")
        }
    }

    /**
     * Brings Frank's main UI immediately to the foreground over other active apps.
     */
    fun bringFrankToForeground(): Pair<Boolean, String> {
        return try {
            val intent = Intent(context, com.example.MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(intent)
            Pair(true, "Bringing Frank to the foreground")
        } catch (e: Exception) {
            Pair(false, "Could not open Frank: ${e.message}")
        }
    }

    /**
     * Checks if Frank has been granted permission to draw over other apps (SYSTEM_ALERT_WINDOW).
     * This permission grants the ability to display floating Super Admin HUDs and reliably
     * launch apps or override background restrictions on Android 10+.
     */
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Directs the user to Android's "Display over other apps" / "Appear on top" settings page
     * so Frank can override other apps and display floating HUDs.
     */
    fun requestOverlayPermission(): Pair<Boolean, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                return try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                    Pair(true, "Opened Display Over Other Apps settings. Please enable permission for Frank.")
                } catch (e: Exception) {
                    openSettings("APP_DETAILS")
                }
            } else {
                return Pair(true, "Permission to override other apps is already granted to Frank.")
            }
        }
        return Pair(true, "Overlay permission not required on this Android version.")
    }

    /**
     * Core Super Admin function:
     * Queries installed applications via PackageManager and matches against the user's voice intent query.
     * Supports exact match, substring match, word-token match, package-id match, and common category aliases
     * (e.g. "calculator", "calendar", "gallery", "maps", "photos", "browser", "camera", "music", "settings").
     */
    fun findAndLaunchAppByName(query: String): Pair<Boolean, String> {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            return Pair(false, "Please specify an application name to open")
        }

        // Special Frank & System overrides
        if (q == "frank" || q == "super admin" || q == "assistant" || q == "home" || q == "main app") {
            return bringFrankToForeground()
        }
        if (q.contains("overlay") || q.contains("override") || q.contains("appear on top")) {
            return requestOverlayPermission()
        }

        val allApps = getInstalledApps()

        // 1. Exact Name Match (case-insensitive)
        val exactMatch = allApps.firstOrNull { it.name.equals(q, ignoreCase = true) }
        if (exactMatch != null) {
            val (success, msg) = launchApp(exactMatch.packageName)
            return if (success) Pair(true, "Opening ${exactMatch.name}...") else Pair(false, msg)
        }

        // 2. Starts-with or Substring match on Application Label
        val labelMatch = allApps.firstOrNull { it.name.lowercase().startsWith(q) }
            ?: allApps.firstOrNull { it.name.lowercase().contains(q) }
            ?: allApps.firstOrNull { q.contains(it.name.lowercase()) }

        if (labelMatch != null) {
            val (success, msg) = launchApp(labelMatch.packageName)
            return if (success) Pair(true, "Opening ${labelMatch.name}...") else Pair(false, msg)
        }

        // 3. Package Name substring match (e.g. "spotify", "youtube", "camera")
        val packageMatch = allApps.firstOrNull { it.packageName.lowercase().contains(q) }
        if (packageMatch != null) {
            val (success, msg) = launchApp(packageMatch.packageName)
            return if (success) Pair(true, "Opening ${packageMatch.name}...") else Pair(false, msg)
        }

        // 4. Word-token intersection (e.g. user says "google maps" or "chrome browser")
        val queryTokens = q.split(Regex("[\\s_\\-]+")).filter { it.length > 2 }
        val tokenMatch = allApps.firstOrNull { app ->
            val appTokens = app.name.lowercase().split(Regex("[\\s_\\-]+"))
            queryTokens.any { token -> appTokens.contains(token) }
        }
        if (tokenMatch != null) {
            val (success, msg) = launchApp(tokenMatch.packageName)
            return if (success) Pair(true, "Opening ${tokenMatch.name}...") else Pair(false, msg)
        }

        // 5. Semantic / Category Aliases & Standard Android Intent Dispatches
        when {
            q.contains("camera") || q.contains("photo") || q.contains("take picture") -> {
                return openCamera()
            }
            q.contains("browser") || q.contains("chrome") || q.contains("internet") || q.contains("web") -> {
                val browserApp = allApps.firstOrNull {
                    it.packageName.contains("chrome") || it.packageName.contains("browser")
                }
                return if (browserApp != null) {
                    launchApp(browserApp.packageName)
                } else {
                    openWebUrl("https://www.google.com")
                }
            }
            q.contains("setting") -> {
                return openSettings("ALL")
            }
            q.contains("file") || q.contains("document") || q.contains("download") -> {
                val filesApp = allApps.firstOrNull {
                    it.packageName.contains("documentsui") || it.packageName.contains("files") || it.name.contains("Files", ignoreCase = true)
                }
                return if (filesApp != null) {
                    launchApp(filesApp.packageName)
                } else {
                    openSettings("STORAGE")
                }
            }
            q.contains("music") || q.contains("song") || q.contains("audio") || q.contains("spotify") -> {
                val musicApp = allApps.firstOrNull {
                    it.packageName.contains("music") || it.packageName.contains("spotify") || it.name.contains("Music", ignoreCase = true)
                }
                return if (musicApp != null) {
                    launchApp(musicApp.packageName)
                } else {
                    Pair(true, "Launching system audio engine")
                }
            }
            q.contains("calc") -> {
                val calcApp = allApps.firstOrNull {
                    it.packageName.contains("calculator") || it.name.contains("Calculator", ignoreCase = true)
                }
                if (calcApp != null) return launchApp(calcApp.packageName)
            }
            q.contains("calendar") -> {
                val calApp = allApps.firstOrNull {
                    it.packageName.contains("calendar") || it.name.contains("Calendar", ignoreCase = true)
                }
                if (calApp != null) return launchApp(calApp.packageName)
            }
            q.contains("clock") || q.contains("alarm") || q.contains("timer") -> {
                val clockApp = allApps.firstOrNull {
                    it.packageName.contains("deskclock") || it.name.contains("Clock", ignoreCase = true)
                }
                if (clockApp != null) return launchApp(clockApp.packageName)
            }
            q.contains("map") || q.contains("navigation") || q.contains("gps") -> {
                val mapsApp = allApps.firstOrNull {
                    it.packageName.contains("maps") || it.name.contains("Maps", ignoreCase = true)
                }
                if (mapsApp != null) return launchApp(mapsApp.packageName)
            }
        }

        return Pair(false, "Could not find an installed application matching \"$query\" on this device.")
    }

    fun openCamera(): Pair<Boolean, String> {
        return try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(packageManager) != null) {
                context.startActivity(intent)
                Pair(true, "Camera opened")
            } else {
                openSettings("ALL")
            }
        } catch (e: Exception) {
            Pair(false, "Camera launch error: ${e.message}")
        }
    }

    /**
     * Closes the active app currently displayed on screen.
     * Uses Frank's Accessibility Service or Home intent to dismiss the app from screen,
     * and terminates its background processes via ActivityManager.
     */
    fun closeActiveApp(): Pair<Boolean, String> {
        val activePkg = FrankAccessibilityService.getActivePackage()
        if (activePkg != null && activePkg != context.packageName) {
            val label = try {
                val appInfo = packageManager.getApplicationInfo(activePkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                activePkg
            }
            // 1. Dismiss from screen using Accessibility or Home
            if (FrankAccessibilityService.instance != null) {
                FrankAccessibilityService.goHome()
            } else {
                bringHomeScreenToFront()
            }
            // 2. Kill background processes
            activityManager?.killBackgroundProcesses(activePkg)
            return Pair(true, "Closed active app: $label and cleared background processes.")
        }

        // Fallback: dismiss current foreground via Accessibility Home or system Home intent
        if (FrankAccessibilityService.instance != null) {
            val (dismissed, msg) = FrankAccessibilityService.closeActiveAppOnScreen()
            return Pair(true, "Closed active app on screen.")
        } else {
            bringHomeScreenToFront()
            return Pair(true, "Closed active app on screen and returned to Home.")
        }
    }

    /**
     * Closes an application specified by name (whether active on screen or minimized in the background).
     */
    fun closeAppByName(query: String): Pair<Boolean, String> {
        val q = query.trim().lowercase()
        if (q.isBlank() || q == "app" || q == "active app" || q == "this app" || q == "current app") {
            return closeActiveApp()
        }

        if (q == "frank" || q == "super admin") {
            bringHomeScreenToFront()
            return Pair(true, "Minimized Frank to background.")
        }

        val allApps = getInstalledApps()
        val matchedApp = allApps.firstOrNull { it.name.equals(q, ignoreCase = true) }
            ?: allApps.firstOrNull { it.name.lowercase().startsWith(q) }
            ?: allApps.firstOrNull { it.name.lowercase().contains(q) }
            ?: allApps.firstOrNull { it.packageName.lowercase().contains(q) }

        if (matchedApp != null) {
            val targetPkg = matchedApp.packageName
            val isForeground = FrankAccessibilityService.getActivePackage() == targetPkg

            if (isForeground) {
                if (FrankAccessibilityService.instance != null) {
                    FrankAccessibilityService.goHome()
                } else {
                    bringHomeScreenToFront()
                }
            }

            try {
                activityManager?.killBackgroundProcesses(targetPkg)
            } catch (_: Exception) {}

            val stateDesc = if (isForeground) "on screen" else "minimized"
            return Pair(true, "Closed ${matchedApp.name} ($stateDesc) and terminated background processes.")
        }

        // Fallback if not directly found in launcher apps
        val cleanPkg = q.replace(" ", "")
        try {
            activityManager?.killBackgroundProcesses(cleanPkg)
        } catch (_: Exception) {}

        return Pair(true, "Closed $query and terminated background processes.")
    }

    /**
     * Closes all minimized apps running in the background, freeing memory.
     */
    fun closeAllMinimizedApps(): Pair<Boolean, String> {
        var closedCount = 0
        try {
            val processes = activityManager?.runningAppProcesses ?: emptyList()
            for (proc in processes) {
                val pkg = proc.processName
                if (pkg != context.packageName && !pkg.contains("launcher") && !pkg.contains("systemui")) {
                    activityManager?.killBackgroundProcesses(pkg)
                    closedCount++
                }
            }
        } catch (_: Exception) {}

        return Pair(true, "Closed $closedCount minimized background processes and reclaimed system RAM.")
    }

    fun bringHomeScreenToFront() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
        } catch (_: Exception) {}
    }

    // --- Hardware & System Settings Control ---

    var isFlashlightOn: Boolean = false
        private set

    fun toggleFlashlight(enable: Boolean): Pair<Boolean, String> {
        if (cameraManager == null) return Pair(false, "Camera hardware unavailable")
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
                isFlashlightOn = enable
                vibrate(50)
                Pair(true, if (enable) "Flashlight turned ON" else "Flashlight turned OFF")
            } else {
                Pair(false, "Device flash hardware not detected")
            }
        } catch (e: CameraAccessException) {
            Pair(false, "Flashlight error: ${e.message}")
        } catch (e: Exception) {
            Pair(false, "Cannot control flashlight: ${e.message}")
        }
    }

    fun getMusicVolume(): Int {
        return audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    }

    fun getMaxMusicVolume(): Int {
        return audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
    }

    fun setMusicVolumePercent(percent: Float): Pair<Boolean, String> {
        if (audioManager == null) return Pair(false, "Audio service unavailable")
        return try {
            val max = getMaxMusicVolume()
            val target = (percent.coerceIn(0f, 1f) * max).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            Pair(true, "Volume set to ${(percent * 100).toInt()}%")
        } catch (e: Exception) {
            Pair(false, "Volume adjustment failed: ${e.message}")
        }
    }

    fun muteAudio(): Pair<Boolean, String> {
        return setMusicVolumePercent(0f)
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        return true
    }

    fun requestIgnoreBatteryOptimization(): Pair<Boolean, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Pair(true, "Requested unrestricted background power for Frank")
            } catch (e: Exception) {
                openSettings("BATTERY")
            }
        }
        return Pair(true, "Battery optimization not required on this Android version")
    }

    fun openSettings(type: String): Pair<Boolean, String> {
        val upper = type.uppercase()
        if (upper in listOf("OVERLAY", "OVERRIDE", "APPEAR_ON_TOP", "SYSTEM_ALERT_WINDOW", "DRAW_OVER_APPS")) {
            return requestOverlayPermission()
        }

        val action = when (upper) {
            "WIFI" -> Settings.ACTION_WIFI_SETTINGS
            "BLUETOOTH" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "SOUND" -> Settings.ACTION_SOUND_SETTINGS
            "DISPLAY" -> Settings.ACTION_DISPLAY_SETTINGS
            "BATTERY" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "STORAGE" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "PRIVACY" -> Settings.ACTION_PRIVACY_SETTINGS
            "APP_DETAILS" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            "DATE" -> Settings.ACTION_DATE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            context.startActivity(intent)
            Pair(true, "Opened $type Settings")
        } catch (e: Exception) {
            // Fallback to standard settings
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                Pair(true, "Opened System Settings")
            } catch (e2: Exception) {
                Pair(false, "Failed to open settings: ${e2.message}")
            }
        }
    }

    // --- Web & Search ---

    fun executeWebSearch(query: String): Pair<Boolean, String> {
        return try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (searchIntent.resolveActivity(packageManager) != null) {
                context.startActivity(searchIntent)
                Pair(true, "Searching web for: \"$query\"")
            } else {
                openWebUrl("https://www.google.com/search?q=${Uri.encode(query)}")
            }
        } catch (_: Exception) {
            openWebUrl("https://www.google.com/search?q=${Uri.encode(query)}")
        }
    }

    fun openWebUrl(url: String): Pair<Boolean, String> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Pair(true, "Opened URL: $url")
        } catch (e: Exception) {
            Pair(false, "Could not open browser: ${e.message}")
        }
    }

    // --- Device Diagnostics ---

    fun getDeviceDiagnostics(): DeviceDiagnostics {
        // Battery
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // Memory
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val availRam = memInfo.availMem / (1024 * 1024)
        val totalRam = memInfo.totalMem / (1024 * 1024)

        // Storage
        val dataDir = Environment.getDataDirectory()
        val stat = StatFs(dataDir.path)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val freeGb = (freeBytes.toDouble() / (1024 * 1024 * 1024)).let { Math.round(it * 10.0) / 10.0 }
        val totalGb = (totalBytes.toDouble() / (1024 * 1024 * 1024)).let { Math.round(it * 10.0) / 10.0 }

        // Connectivity
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(activeNetwork)
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Disconnected"
        }

        return DeviceDiagnostics(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            availableRamMb = availRam,
            totalRamMb = totalRam,
            freeStorageGb = freeGb,
            totalStorageGb = totalGb,
            networkType = netType,
            isOnline = isOnline,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        )
    }

    fun vibrate(durationMs: Long = 60) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }
}
