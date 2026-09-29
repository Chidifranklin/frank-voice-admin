package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String, // "Personal", "Work", "Credentials", "Preferences", "System"
    val key: String,      // e.g. "sister_birthday", "favorite_music", "home_wifi"
    val value: String,    // content / fact
    val tags: String = "",// comma-separated tags
    val importance: Int = 3, // 1 to 5
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true
)
