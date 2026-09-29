package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "Reminder alert"
        val reminderType = intent.getStringExtra(EXTRA_REMINDER_TYPE) ?: "REMINDER"

        Log.d(TAG, "Reminder triggered: id=$reminderId, title='$title', type=$reminderType")

        // 1. Mark as completed in Room DB
        if (reminderId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    db.reminderDao().markCompleted(reminderId)
                    db.auditLogDao().insertLog(
                        com.example.data.entities.AuditLogEntity(
                            actionType = "ALARM_TRIGGERED",
                            description = "Fired: \"$title\"",
                            status = "EXECUTED",
                            details = "Type: $reminderType",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating reminder in DB: ${e.message}")
                }
            }
        }

        // 2. Post high-priority alert notification
        showReminderNotification(context, reminderId, title, reminderType)

        // 3. Vibrate device for tactile alert
        val deviceManager = SuperAdminDeviceManager(context)
        deviceManager.vibrate(300)
    }

    private fun showReminderNotification(
        context: Context,
        id: Long,
        title: String,
        type: String
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Frank Reminders & Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority scheduled reminders and countdown alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_TAB", "ASSISTANT")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.toInt(),
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val headline = when (type.uppercase()) {
            "TIMER" -> "⏳ Frank Timer Alert"
            "ALARM" -> "⏰ Frank Alarm Alert"
            else -> "🔔 Frank Scheduled Reminder"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(headline)
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Hey! Frank reminder: $title"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setContentIntent(pendingIntent)
            .build()

        try {
            manager.notify((10000 + id).toInt(), notification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post reminder notification: ${e.message}")
        }
    }

    companion object {
        const val TAG = "ReminderReceiver"
        const val CHANNEL_ID = "frank_reminders_channel"
        const val ACTION_REMINDER_TRIGGER = "com.example.ACTION_REMINDER_TRIGGER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
    }
}
