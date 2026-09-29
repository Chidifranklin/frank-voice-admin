package com.example.data.repository

import com.example.data.AppDatabase
import com.example.data.entities.AuditLogEntity
import com.example.data.entities.IndexedDocumentEntity
import com.example.data.entities.MemoryEntity
import com.example.data.entities.ReminderEntity
import kotlinx.coroutines.flow.Flow

class OmniRepository(private val database: AppDatabase) {

    private val memoryDao = database.memoryDao()
    private val auditLogDao = database.auditLogDao()
    private val documentDao = database.documentDao()
    private val reminderDao = database.reminderDao()

    // Reminders & Alarms
    val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders()
    val activeReminders: Flow<List<ReminderEntity>> = reminderDao.getActiveReminders()
    val activeReminderCount: Flow<Int> = reminderDao.getActiveReminderCount()

    suspend fun insertReminder(reminder: ReminderEntity): Long = reminderDao.insertReminder(reminder)
    suspend fun getReminderById(id: Long): ReminderEntity? = reminderDao.getReminderById(id)
    suspend fun getPendingRemindersList(): List<ReminderEntity> = reminderDao.getPendingRemindersList()
    suspend fun markReminderCompleted(id: Long) = reminderDao.markCompleted(id)
    suspend fun deleteReminder(id: Long) = reminderDao.deleteReminderById(id)
    suspend fun clearCompletedReminders() = reminderDao.clearCompletedReminders()
    suspend fun clearAllReminders() = reminderDao.clearAllReminders()

    // Memories
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()
    val memoryCount: Flow<Int> = memoryDao.getMemoryCount()

    fun searchMemories(query: String): Flow<List<MemoryEntity>> = memoryDao.searchMemories(query)

    fun getMemoriesByCategory(category: String): Flow<List<MemoryEntity>> =
        memoryDao.getMemoriesByCategory(category)

    suspend fun getMemoryByKey(key: String): MemoryEntity? = memoryDao.getMemoryByKey(key)

    suspend fun insertMemory(memory: MemoryEntity): Long = memoryDao.insertMemory(memory)

    suspend fun updateMemory(memory: MemoryEntity) = memoryDao.updateMemory(memory)

    suspend fun deleteMemory(id: Long) = memoryDao.deleteMemoryById(id)

    suspend fun clearMemories() = memoryDao.clearAllMemories()

    // Audit Logs
    val allLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAllAuditLogs()
    val auditLogCount: Flow<Int> = auditLogDao.getAuditLogCount()

    suspend fun logAction(
        actionType: String,
        description: String,
        status: String = "SUCCESS",
        details: String = ""
    ): Long {
        val entry = AuditLogEntity(
            actionType = actionType,
            description = description,
            status = status,
            details = details,
            timestamp = System.currentTimeMillis()
        )
        return auditLogDao.insertLog(entry)
    }

    suspend fun clearLogs() = auditLogDao.clearAllLogs()

    // Documents
    val allDocuments: Flow<List<IndexedDocumentEntity>> = documentDao.getAllDocuments()
    val documentCount: Flow<Int> = documentDao.getDocumentCount()

    fun searchDocuments(query: String): Flow<List<IndexedDocumentEntity>> =
        documentDao.searchDocuments(query)

    suspend fun insertDocument(doc: IndexedDocumentEntity): Long = documentDao.insertDocument(doc)

    suspend fun deleteDocument(id: Long) = documentDao.deleteDocumentById(id)

    suspend fun deleteDocumentByPath(path: String) = documentDao.deleteDocumentByPath(path)

    suspend fun clearDocuments() = documentDao.clearAllDocuments()
}
