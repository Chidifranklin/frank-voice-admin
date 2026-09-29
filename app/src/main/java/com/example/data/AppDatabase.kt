package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.AuditLogDao
import com.example.data.dao.DocumentDao
import com.example.data.dao.MemoryDao
import com.example.data.dao.ReminderDao
import com.example.data.entities.AuditLogEntity
import com.example.data.entities.IndexedDocumentEntity
import com.example.data.entities.MemoryEntity
import com.example.data.entities.ReminderEntity

@Database(
    entities = [
        MemoryEntity::class,
        AuditLogEntity::class,
        IndexedDocumentEntity::class,
        ReminderEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun memoryDao(): MemoryDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun documentDao(): DocumentDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omni_memory.db"
                ).fallbackToDestructiveMigration(dropAllTables = false).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
