package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.entities.IndexedDocumentEntity
import com.example.data.repository.OmniRepository
import java.io.File

class FileManager(
    private val context: Context,
    private val repository: OmniRepository
) {
    val fileSystemHelper = FileSystemHelper(context, repository)

    private val docsDir: File
        get() = fileSystemHelper.rootWorkspaceDir

    init {
        // Initialize default sample documents if empty
        ensureInitialDocs()
    }

    private fun ensureInitialDocs() {
        val files = docsDir.listFiles()
        if (files == null || files.isEmpty()) {
            createDocument(
                "Welcome_Security_Manual.txt",
                "OMNIMEMORY SUPER ADMIN ON-DEVICE SYSTEM\n\nAll personal memory and operations are strictly encrypted on-device.\nZero cloud telemetry in local mode.\nSuper Admin access granted.",
                "Configs"
            )
            createDocument(
                "System_Directives.md",
                "# System Directives\n1. Protect user privacy\n2. Execute commands with admin verification\n3. Cache memories locally in Room SQLite database.",
                "Docs"
            )
        }
    }

    fun listFiles(): List<File> {
        return docsDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun createDocument(name: String, content: String, category: String = "Notes"): Pair<Boolean, String> {
        return try {
            val safeName = if (name.contains(".")) name else "$name.txt"
            val file = File(docsDir, safeName)
            file.writeText(content)

            // Index in Room
            val preview = if (content.length > 120) content.take(120) + "..." else content
            val docEntity = IndexedDocumentEntity(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                lastModified = file.lastModified(),
                contentPreview = preview,
                category = category
            )
            // Fire coroutine or sync
            Pair(true, "Created file: ${file.name} (${file.length()} bytes)")
        } catch (e: Exception) {
            Pair(false, "Failed to create document: ${e.message}")
        }
    }

    fun readDocument(fileName: String): Pair<Boolean, String> {
        return try {
            val file = File(docsDir, fileName)
            if (file.exists()) {
                Pair(true, file.readText())
            } else {
                Pair(false, "File not found: $fileName")
            }
        } catch (e: Exception) {
            Pair(false, "Error reading file: ${e.message}")
        }
    }

    fun updateDocument(fileName: String, newContent: String): Pair<Boolean, String> {
        return try {
            val file = File(docsDir, fileName)
            file.writeText(newContent)
            Pair(true, "Updated file: $fileName")
        } catch (e: Exception) {
            Pair(false, "Error updating file: ${e.message}")
        }
    }

    fun deleteDocument(fileName: String): Pair<Boolean, String> {
        val result = fileSystemHelper.deleteFile(fileName)
        return Pair(result.success, result.message)
    }

    fun createFolder(folderName: String, parentFolder: String = ""): Pair<Boolean, String> {
        val result = fileSystemHelper.createFolder(folderName, parentFolder)
        return Pair(result.success, result.message)
    }

    fun deleteFolder(folderName: String, recursive: Boolean = true): Pair<Boolean, String> {
        val result = fileSystemHelper.deleteFolder(folderName, recursive)
        return Pair(result.success, result.message)
    }

    fun listFolderItems(folder: String = ""): List<FileSystemItem> {
        return fileSystemHelper.listFiles(folder).items
    }

    fun duplicateDocument(fileName: String): Pair<Boolean, String> {
        return try {
            val source = File(docsDir, fileName)
            if (!source.exists()) return Pair(false, "Source file not found")
            val base = source.nameWithoutExtension
            val ext = source.extension
            val newName = "${base}_copy.${if (ext.isNotEmpty()) ext else "txt"}"
            val target = File(docsDir, newName)
            source.copyTo(target, overwrite = true)
            Pair(true, "Duplicated to $newName")
        } catch (e: Exception) {
            Pair(false, "Duplication failed: ${e.message}")
        }
    }

    fun shareDocument(fileName: String): Pair<Boolean, String> {
        return try {
            val file = File(docsDir, fileName)
            if (!file.exists()) return Pair(false, "File not found")

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TEXT, file.readText())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            Pair(true, "Opened share dialog for ${file.name}")
        } catch (e: Exception) {
            Pair(false, "Share failed: ${e.message}")
        }
    }

    suspend fun syncAllFilesToRoom() {
        val files = listFiles()
        for (f in files) {
            try {
                val preview = try {
                    val txt = f.readText()
                    if (txt.length > 100) txt.take(100) + "..." else txt
                } catch (_: Exception) { "" }

                repository.insertDocument(
                    IndexedDocumentEntity(
                        fileName = f.name,
                        filePath = f.absolutePath,
                        fileSize = f.length(),
                        lastModified = f.lastModified(),
                        contentPreview = preview,
                        category = when {
                            f.extension.lowercase() in listOf("md", "txt") -> "Notes"
                            f.extension.lowercase() in listOf("json", "xml", "conf") -> "Configs"
                            else -> "Docs"
                        }
                    )
                )
            } catch (_: Exception) {}
        }
    }
}
