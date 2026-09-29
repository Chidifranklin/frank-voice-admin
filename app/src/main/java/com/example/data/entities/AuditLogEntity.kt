package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val actionType: String, // "APP_LAUNCH", "SYSTEM_SETTING", "FILE_OP", "VOICE_COMMAND", "MEMORY_STORE", "MUSIC_PLAY"
    val description: String,
    val status: String,     // "SUCCESS", "EXECUTED", "WARNING", "DENIED"
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = ""
)
