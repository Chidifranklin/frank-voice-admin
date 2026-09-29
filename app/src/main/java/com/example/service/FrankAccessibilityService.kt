package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FrankAccessibilityService : AccessibilityService() {

    var currentForegroundPackage: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Log.d(TAG, "FrankAccessibilityService connected: Full device control enabled.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != packageName && !pkg.contains("launcher") && !pkg.contains("systemui")) {
                currentForegroundPackage = pkg
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "FrankAccessibilityService interrupted.")
        _isServiceActive.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        _isServiceActive.value = false
        Log.d(TAG, "FrankAccessibilityService destroyed.")
    }

    /**
     * Finds the first scrollable node in the current active window and scrolls it.
     */
    fun performScroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollableNode = findScrollableNode(root)
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val result = scrollableNode?.performAction(action) ?: false
        scrollableNode?.recycle()
        root.recycle()
        return result
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    /**
     * Searches for a clickable node with matching text and clicks it.
     */
    fun clickNodeWithText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) {
            root.recycle()
            return false
        }

        for (node in nodes) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) {
                val parent = target.parent
                if (target != node) target.recycle()
                target = parent
            }
            if (target != null && target.isClickable) {
                val success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                target.recycle()
                for (n in nodes) n.recycle()
                root.recycle()
                return success
            }
            node.recycle()
        }
        root.recycle()
        return false
    }

    companion object {
        const val TAG = "FrankAccessibility"
        var instance: FrankAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedServiceName = "${context.packageName}/${FrankAccessibilityService::class.java.canonicalName}"
            return try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                val accessibilityEnabled = Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                )
                accessibilityEnabled == 1 && enabledServices.contains(expectedServiceName)
            } catch (e: Exception) {
                false
            }
        }

        fun openAccessibilitySettings(context: Context): Pair<Boolean, String> {
            return try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Pair(true, "Opening Accessibility Settings for Frank Super Admin")
            } catch (e: Exception) {
                Pair(false, "Could not open Accessibility Settings: ${e.message}")
            }
        }

        fun executeGlobalAction(actionType: Int): Pair<Boolean, String> {
            val svc = instance
            if (svc == null) {
                return Pair(false, "Frank Accessibility Service is not enabled. Please enable it in Settings.")
            }
            val success = svc.performGlobalAction(actionType)
            val actionName = when (actionType) {
                GLOBAL_ACTION_HOME -> "Go Home"
                GLOBAL_ACTION_BACK -> "Go Back"
                GLOBAL_ACTION_NOTIFICATIONS -> "Open Notifications"
                GLOBAL_ACTION_QUICK_SETTINGS -> "Open Quick Settings"
                GLOBAL_ACTION_RECENTS -> "Recent Apps"
                GLOBAL_ACTION_TAKE_SCREENSHOT -> "Take Screenshot"
                GLOBAL_ACTION_LOCK_SCREEN -> "Lock Screen"
                else -> "Global Action $actionType"
            }
            return if (success) {
                Pair(true, "Executed $actionName")
            } else {
                Pair(false, "Failed to perform $actionName")
            }
        }

        fun goHome(): Pair<Boolean, String> = executeGlobalAction(GLOBAL_ACTION_HOME)
        fun goBack(): Pair<Boolean, String> = executeGlobalAction(GLOBAL_ACTION_BACK)
        fun openNotifications(): Pair<Boolean, String> = executeGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        fun openQuickSettings(): Pair<Boolean, String> = executeGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        fun showRecentApps(): Pair<Boolean, String> = executeGlobalAction(GLOBAL_ACTION_RECENTS)

        fun takeScreenshot(): Pair<Boolean, String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                executeGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            } else {
                Pair(false, "Take screenshot requires Android 9+")
            }
        }

        fun lockScreen(): Pair<Boolean, String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                executeGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                Pair(false, "Lock screen requires Android 9+")
            }
        }

        fun scrollDown(): Pair<Boolean, String> {
            val svc = instance ?: return Pair(false, "Accessibility Service not enabled")
            val success = svc.performScroll(forward = true)
            return Pair(success, if (success) "Scrolled down" else "Unable to scroll down on current screen")
        }

        fun scrollUp(): Pair<Boolean, String> {
            val svc = instance ?: return Pair(false, "Accessibility Service not enabled")
            val success = svc.performScroll(forward = false)
            return Pair(success, if (success) "Scrolled up" else "Unable to scroll up on current screen")
        }

        fun clickText(text: String): Pair<Boolean, String> {
            val svc = instance ?: return Pair(false, "Accessibility Service not enabled")
            val success = svc.clickNodeWithText(text)
            return Pair(success, if (success) "Clicked on '$text'" else "Could not find clickable item with text '$text'")
        }

        fun getActivePackage(): String? {
            return instance?.currentForegroundPackage
        }

        fun closeActiveAppOnScreen(): Pair<Boolean, String> {
            val svc = instance
            if (svc == null) {
                return Pair(false, "Accessibility Service not connected")
            }
            // Navigate home to close/dismiss active app from screen
            val success = svc.performGlobalAction(GLOBAL_ACTION_HOME)
            val closedPkg = svc.currentForegroundPackage ?: "active app"
            svc.currentForegroundPackage = null
            return if (success) {
                Pair(true, "Closed $closedPkg from screen and navigated Home")
            } else {
                Pair(false, "Failed to dismiss active app")
            }
        }
    }
}
