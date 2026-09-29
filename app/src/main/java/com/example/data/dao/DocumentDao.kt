package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entities.IndexedDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM indexed_documents ORDER BY lastModified DESC")
    fun getAllDocuments(): Flow<List<IndexedDocumentEntity>>

    @Query("SELECT * FROM indexed_documents WHERE fileName LIKE '%' || :query || '%' OR contentPreview LIKE '%' || :query || '%'")
    fun searchDocuments(query: String): Flow<List<IndexedDocumentEntity>>

    @Query("SELECT * FROM indexed_documents WHERE filePath = :path LIMIT 1")
    suspend fun getDocumentByPath(path: String): IndexedDocumentEntity?

    @Query("SELECT COUNT(*) FROM indexed_documents")
    fun getDocumentCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: IndexedDocumentEntity): Long

    @Update
    suspend fun updateDocument(document: IndexedDocumentEntity)

    @Query("DELETE FROM indexed_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("DELETE FROM indexed_documents WHERE filePath = :path")
    suspend fun deleteDocumentByPath(path: String)

    @Query("DELETE FROM indexed_documents")
    suspend fun clearAllDocuments()
}
