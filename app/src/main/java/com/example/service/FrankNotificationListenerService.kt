package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTimeMillis: Long,
    val isClearable: Boolean
) {
    val formattedTime: String
        get() = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(postTimeMillis))
}

class FrankNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isListenerConnected.value = true
        Log.d(TAG, "NotificationListener connected: Intercepting incoming alerts.")
        syncActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) {
            instance = null
        }
        _isListenerConnected.value = false
        Log.d(TAG, "NotificationListener disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val captured = extractNotification(sbn) ?: return

        // Skip our own Frank continuous listening / foreground notifications
        if (captured.packageName == packageName) return

        val current = _notifications.value.toMutableList()
        current.removeAll { it.id == captured.id }
        current.add(0, captured)
        // Keep last 30
        if (current.size > 30) {
            _notifications.value = current.take(30)
        } else {
            _notifications.value = current
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val id = "${sbn.packageName}_${sbn.id}"
        val current = _notifications.value.toMutableList()
        if (current.removeAll { it.id == id }) {
            _notifications.value = current
        }
    }

    fun syncActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            val list = mutableListOf<CapturedNotification>()
            for (sbn in active) {
                if (sbn.packageName == packageName) continue
                extractNotification(sbn)?.let { list.add(it) }
            }
            _notifications.value = list.sortedByDescending { it.postTimeMillis }.take(30)
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing notifications: ${e.message}")
        }
    }

    private fun extractNotification(sbn: StatusBarNotification): CapturedNotification? {
        val extras = sbn.notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: ""

        if (title.isBlank() && text.isBlank()) return null

        val appLabel = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName.substringAfterLast('.')
        }

        val id = "${sbn.packageName}_${sbn.id}"
        return CapturedNotification(
            id = id,
            packageName = sbn.packageName,
            appName = appLabel,
            title = title.ifEmpty { appLabel },
            text = text,
            postTimeMillis = sbn.postTime,
            isClearable = sbn.isClearable
        )
    }

    companion object {
        const val TAG = "FrankNotificationListener"
        var instance: FrankNotificationListenerService? = null
            private set

        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

        private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
        val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
            val myComponent = ComponentName(context, FrankNotificationListenerService::class.java).flattenToString()
            return flat.contains(myComponent)
        }

        fun openNotificationAccessSettings(context: Context): Pair<Boolean, String> {
            return try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Pair(true, "Opening Notification Access Settings for Frank")
            } catch (e: Exception) {
                Pair(false, "Could not open Notification Listener Settings: ${e.message}")
            }
        }

        fun readNotificationsSummary(): String {
            val list = _notifications.value
            if (list.isEmpty()) {
                return "You have no unread notifications."
            }
            val count = list.size
            val itemsToRead = list.take(3)
            val builder = StringBuilder()
            builder.append("You have $count notification${if (count > 1) "s" else ""}. ")
            itemsToRead.forEachIndexed { index, notif ->
                builder.append("From ${notif.appName}: ${notif.title}. ${if (notif.text.isNotBlank()) notif.text + ". " else ""}")
            }
            return builder.toString().trim()
        }

        fun readLatestMessage(): String {
            val latest = _notifications.value.firstOrNull()
            return if (latest != null) {
                "Latest notification from ${latest.appName}: ${latest.title}. ${latest.text}"
            } else {
                "No notifications available to read."
            }
        }

        fun clearAll(): Pair<Boolean, String> {
            val svc = instance
            return if (svc != null) {
                try {
                    svc.cancelAllNotifications()
                    _notifications.value = emptyList()
                    Pair(true, "All clearable notifications dismissed.")
                } catch (e: Exception) {
                    Pair(false, "Could not dismiss notifications: ${e.message}")
                }
            } else {
                _notifications.value = emptyList()
                Pair(true, "Notification feed cleared.")
            }
        }
    }
}
