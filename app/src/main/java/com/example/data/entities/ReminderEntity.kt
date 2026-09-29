package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val triggerTimeMillis: Long,
    val isCompleted: Boolean = false,
    val reminderType: String = "REMINDER", // "REMINDER", "TIMER", "ALARM"
    val createdAt: Long = System.currentTimeMillis()
)
