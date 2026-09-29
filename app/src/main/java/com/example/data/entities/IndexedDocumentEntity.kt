package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "indexed_documents")
data class IndexedDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val lastModified: Long = System.currentTimeMillis(),
    val contentPreview: String = "",
    val category: String = "Notes" // "Notes", "Configs", "Logs", "Docs"
)
