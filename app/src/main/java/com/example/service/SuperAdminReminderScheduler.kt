package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.entities.ReminderEntity
import com.example.data.repository.OmniRepository

class SuperAdminReminderScheduler(
    private val context: Context,
    private val repository: OmniRepository
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    suspend fun scheduleReminder(
        title: String,
        delayMinutes: Long,
        type: String = "REMINDER"
    ): Pair<Boolean, String> {
        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000)
        return scheduleReminderAt(title, triggerTime, type)
    }

    suspend fun scheduleReminderAt(
        title: String,
        triggerTimeMillis: Long,
        type: String = "REMINDER"
    ): Pair<Boolean, String> {
        val cleanTitle = title.trim().replaceFirstChar { it.uppercase() }
        if (cleanTitle.isBlank()) {
            return Pair(false, "Reminder text cannot be empty.")
        }

        val reminder = ReminderEntity(
            title = cleanTitle,
            triggerTimeMillis = triggerTimeMillis,
            reminderType = type,
            isCompleted = false
        )

        val id = repository.insertReminder(reminder)
        setSystemAlarm(id, cleanTitle, triggerTimeMillis, type)

        val deltaMs = triggerTimeMillis - System.currentTimeMillis()
        val deltaMin = (deltaMs / (60 * 1000)).coerceAtLeast(1)
        val timeDesc = if (deltaMin < 60) {
            "in $deltaMin minute${if (deltaMin > 1) "s" else ""}"
        } else {
            val hours = deltaMin / 60
            val remMin = deltaMin % 60
            if (remMin > 0) "in $hours hr $remMin min" else "in $hours hour${if (hours > 1) "s" else ""}"
        }

        repository.logAction(
            actionType = "ALARM_SCHEDULED",
            description = "Scheduled $type: \"$cleanTitle\"",
            details = "Due $timeDesc (ID: $id)"
        )

        return Pair(true, "Reminder scheduled for $timeDesc: \"$cleanTitle\"")
    }

    fun setSystemAlarm(
        id: Long,
        title: String,
        triggerTimeMillis: Long,
        type: String
    ) {
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager unavailable on device")
            return
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER_TRIGGER
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id)
            putExtra(ReminderReceiver.EXTRA_REMINDER_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_REMINDER_TYPE, type)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "System alarm registered successfully for ID $id at $triggerTimeMillis")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission denied, falling back to inexact: ${e.message}")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting alarm: ${e.message}", e)
        }
    }

    suspend fun cancelReminder(id: Long): Pair<Boolean, String> {
        val reminder = repository.getReminderById(id)
        if (reminder != null) {
            cancelSystemAlarm(id)
            repository.deleteReminder(id)
            repository.logAction("ALARM_CANCELLED", "Cancelled reminder: \"${reminder.title}\"")
            return Pair(true, "Reminder \"${reminder.title}\" cancelled.")
        }
        return Pair(false, "Reminder not found.")
    }

    fun cancelSystemAlarm(id: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    suspend fun rescheduleAllPendingAlarms() {
        val pending = repository.getPendingRemindersList()
        val now = System.currentTimeMillis()
        Log.d(TAG, "Rescheduling ${pending.size} pending alarms on boot/restore")
        for (item in pending) {
            if (item.triggerTimeMillis > now) {
                setSystemAlarm(item.id, item.title, item.triggerTimeMillis, item.reminderType)
            } else {
                // If expired while phone was off, mark as completed
                repository.markReminderCompleted(item.id)
            }
        }
    }

    companion object {
        const val TAG = "ReminderScheduler"
    }
}
