package com.example.service

import android.content.Context
import android.os.Environment
import com.example.data.entities.IndexedDocumentEntity
import com.example.data.repository.OmniRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Representation of a file or folder in the sandboxed file system.
 */
data class FileSystemItem(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val childCount: Int = 0,
    val extension: String = ""
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "$childCount items"
            return when {
                sizeBytes < 1024 -> "$sizeBytes B"
                sizeBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
                else -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
            }
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }
}

/**
 * Result data class for file system operations, containing human-readable messages
 * and voice-optimized summaries for Text-To-Speech.
 */
data class FileOpResult(
    val success: Boolean,
    val message: String,
    val spokenSummary: String = message,
    val affectedPath: String? = null,
    val items: List<FileSystemItem> = emptyList(),
    val fileContent: String? = null
)

/**
 * Helper class to perform basic file system operations:
 * - Listing files and directories
 * - Creating folders (with sandbox traversal protection)
 * - Deleting files and folders
 * - Creating and reading files
 * - Natural language voice intent execution
 */
class FileSystemHelper(
    private val context: Context,
    private val repository: OmniRepository? = null
) {
    val rootWorkspaceDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: File(context.filesDir, "documents")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    init {
        ensureInitialStructure()
    }

    private fun ensureInitialStructure() {
        try {
            val root = rootWorkspaceDir
            val files = root.listFiles()
            if (files == null || files.isEmpty()) {
                // Create sample workspace folders
                File(root, "Notes").mkdirs()
                File(root, "Configs").mkdirs()
                File(root, "Backups").mkdirs()

                // Create initial security manual
                val welcome = File(File(root, "Configs"), "Welcome_Security_Manual.txt")
                welcome.writeText(
                    "OMNIMEMORY SUPER ADMIN ON-DEVICE SYSTEM\n\n" +
                            "All personal memory and operations are strictly encrypted on-device.\n" +
                            "Zero cloud telemetry in local mode.\n" +
                            "Super Admin access granted."
                )

                val directives = File(File(root, "Notes"), "System_Directives.md")
                directives.writeText(
                    "# System Directives\n" +
                            "1. Protect user privacy\n" +
                            "2. Execute commands with admin verification\n" +
                            "3. Cache memories locally in Room SQLite database."
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * Resolves a path relative to the root workspace and prevents directory traversal attacks.
     */
    fun resolveSafeFile(relativePath: String): File {
        val clean = relativePath.trim().removePrefix("/").replace("..", "")
        val target = if (clean.isEmpty()) rootWorkspaceDir else File(rootWorkspaceDir, clean)
        val canonicalRoot = rootWorkspaceDir.canonicalPath
        val canonicalTarget = target.canonicalPath
        if (!canonicalTarget.startsWith(canonicalRoot)) {
            throw SecurityException("Access denied: path traversal attempt outside sandbox workspace: $relativePath")
        }
        return target
    }

    /**
     * Sanitizes a folder or file name by stripping dangerous characters.
     */
    fun sanitizeName(raw: String): String {
        return raw.trim()
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .replace("..", "")
            .trim()
    }

    /**
     * Lists files and folders in the specified folder (or workspace root).
     */
    fun listFiles(relativeSubfolder: String = "", recursive: Boolean = false): FileOpResult {
        return try {
            val targetDir = resolveSafeFile(relativeSubfolder)
            if (!targetDir.exists()) {
                return FileOpResult(
                    success = false,
                    message = "Directory '${targetDir.name}' does not exist.",
                    spokenSummary = "Directory ${targetDir.name} was not found in your workspace."
                )
            }

            if (!targetDir.isDirectory) {
                return FileOpResult(
                    success = false,
                    message = "'${targetDir.name}' is a file, not a directory.",
                    spokenSummary = "${targetDir.name} is a file, not a folder."
                )
            }

            val items = mutableListOf<FileSystemItem>()
            val files = if (recursive) {
                targetDir.walkTopDown().maxDepth(5).filter { it != targetDir }.toList().toTypedArray()
            } else {
                targetDir.listFiles() ?: emptyArray()
            }

            for (f in files) {
                val rel = f.relativeTo(rootWorkspaceDir).path
                val isDir = f.isDirectory
                val childCount = if (isDir) f.listFiles()?.size ?: 0 else 0
                items.add(
                    FileSystemItem(
                        name = f.name,
                        relativePath = rel,
                        absolutePath = f.absolutePath,
                        isDirectory = isDir,
                        sizeBytes = if (isDir) 0L else f.length(),
                        lastModified = f.lastModified(),
                        childCount = childCount,
                        extension = f.extension
                    )
                )
            }

            // Sort directories first, then files alphabetically
            items.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            val folderCount = items.count { it.isDirectory }
            val fileCount = items.count { !it.isDirectory }
            val folderLabel = if (relativeSubfolder.isBlank()) "Workspace" else targetDir.name

            val spokenSummary = when {
                items.isEmpty() -> "The $folderLabel folder is empty."
                items.size <= 5 -> {
                    val names = items.joinToString(", ") { if (it.isDirectory) "${it.name} folder" else it.name }
                    "Found $folderCount folders and $fileCount files in $folderLabel: $names."
                }
                else -> {
                    val preview = items.take(4).joinToString(", ") { it.name }
                    "Found $folderCount folders and $fileCount files in $folderLabel, including $preview, and ${items.size - 4} others."
                }
            }

            FileOpResult(
                success = true,
                message = "Listed ${items.size} item(s) in $folderLabel ($folderCount folders, $fileCount files)",
                spokenSummary = spokenSummary,
                affectedPath = targetDir.absolutePath,
                items = items
            )
        } catch (e: Exception) {
            FileOpResult(
                success = false,
                message = "Error listing files: ${e.message}",
                spokenSummary = "Failed to list files: ${e.message}"
            )
        }
    }

    /**
     * Creates a new folder inside the workspace.
     */
    fun createFolder(folderName: String, parentFolder: String = ""): FileOpResult {
        return try {
            val cleanName = sanitizeName(folderName)
            if (cleanName.isBlank()) {
                return FileOpResult(
                    success = false,
                    message = "Folder name cannot be empty.",
                    spokenSummary = "Please specify a valid folder name."
                )
            }

            val parentDir = resolveSafeFile(parentFolder)
            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }

            val newDir = File(parentDir, cleanName)
            if (newDir.exists()) {
                return FileOpResult(
                    success = true,
                    message = "Folder '${newDir.name}' already exists.",
                    spokenSummary = "Folder ${newDir.name} already exists in your workspace.",
                    affectedPath = newDir.absolutePath
                )
            }

            val created = newDir.mkdirs()
            if (created || newDir.exists()) {
                FileOpResult(
                    success = true,
                    message = "Created folder: ${newDir.name} (${newDir.relativeTo(rootWorkspaceDir).path})",
                    spokenSummary = "Created folder ${newDir.name}.",
                    affectedPath = newDir.absolutePath
                )
            } else {
                FileOpResult(
                    success = false,
                    message = "Failed to create directory '${newDir.name}'.",
                    spokenSummary = "Could not create folder ${newDir.name}."
                )
            }
        } catch (e: Exception) {
            FileOpResult(
                success = false,
                message = "Error creating folder: ${e.message}",
                spokenSummary = "Error creating folder: ${e.message}"
            )
        }
    }

    /**
     * Deletes a specific file.
     */
    fun deleteFile(fileNameOrPath: String): FileOpResult {
        return try {
            val clean = sanitizeName(fileNameOrPath)
            var target = resolveSafeFile(fileNameOrPath)

            // If not found at direct path, search recursively in workspace
            if (!target.exists() || target.isDirectory) {
                val candidate = rootWorkspaceDir.walkTopDown()
                    .filter { it.isFile && (it.name.equals(clean, ignoreCase = true) || it.name.startsWith(clean, ignoreCase = true)) }
                    .firstOrNull()
                if (candidate != null) {
                    target = candidate
                }
            }

            if (!target.exists()) {
                return FileOpResult(
                    success = false,
                    message = "File not found: $fileNameOrPath",
                    spokenSummary = "Could not find file $fileNameOrPath to delete."
                )
            }

            if (target.isDirectory) {
                return FileOpResult(
                    success = false,
                    message = "'${target.name}' is a folder. Use delete folder command instead.",
                    spokenSummary = "${target.name} is a folder. Say delete folder ${target.name} instead."
                )
            }

            val name = target.name
            val deleted = target.delete()
            if (deleted) {
                FileOpResult(
                    success = true,
                    message = "Deleted file: $name",
                    spokenSummary = "Successfully deleted file $name.",
                    affectedPath = target.absolutePath
                )
            } else {
                FileOpResult(
                    success = false,
                    message = "Failed to delete file: $name",
                    spokenSummary = "Failed to delete file $name."
                )
            }
        } catch (e: Exception) {
            FileOpResult(
                success = false,
                message = "Error deleting file: ${e.message}",
                spokenSummary = "Error deleting file: ${e.message}"
            )
        }
    }

    /**
     * Deletes a folder and optionally all its contents recursively.
     */
    fun deleteFolder(folderNameOrPath: String, recursive: Boolean = true): FileOpResult {
        return try {
            val clean = sanitizeName(folderNameOrPath)
            var target = resolveSafeFile(folderNameOrPath)

            if (!target.exists() || !target.isDirectory) {
                val candidate = rootWorkspaceDir.walkTopDown()
                    .filter { it.isDirectory && it.name.equals(clean, ignoreCase = true) && it != rootWorkspaceDir }
                    .firstOrNull()
                if (candidate != null) {
                    target = candidate
                }
            }

            if (target == rootWorkspaceDir) {
                return FileOpResult(
                    success = false,
                    message = "Cannot delete workspace root directory.",
                    spokenSummary = "Super Admin security prevents deleting the workspace root."
                )
            }

            if (!target.exists()) {
                return FileOpResult(
                    success = false,
                    message = "Folder not found: $folderNameOrPath",
                    spokenSummary = "Could not find folder $folderNameOrPath to delete."
                )
            }

            val name = target.name
            val deleted = if (recursive) target.deleteRecursively() else target.delete()

            if (deleted) {
                FileOpResult(
                    success = true,
                    message = "Deleted folder: $name",
                    spokenSummary = "Successfully deleted folder $name and its contents.",
                    affectedPath = target.absolutePath
                )
            } else {
                FileOpResult(
                    success = false,
                    message = "Failed to delete folder: $name",
                    spokenSummary = "Failed to delete folder $name."
                )
            }
        } catch (e: Exception) {
            FileOpResult(
                success = false,
                message = "Error deleting folder: ${e.message}",
                spokenSummary = "Error deleting folder: ${e.message}"
            )
        }
    }

    /**
     * Deletes either a file or folder by auto-detecting the item type.
     */
    fun deleteItem(nameOrPath: String): FileOpResult {
        val target = try {
            resolveSafeFile(nameOrPath)
        } catch (_: Exception) {
            null
        }

        if (target != null && target.exists()) {
            return if (target.isDirectory) deleteFolder(nameOrPath) else deleteFile(nameOrPath)
        }

        // Search in workspace
        val clean = sanitizeName(nameOrPath)
        val fileMatch = rootWorkspaceDir.walkTopDown().firstOrNull { it.name.equals(clean, ignoreCase = true) }
        return when {
            fileMatch == null -> FileOpResult(
                success = false,
                message = "Item not found: $nameOrPath",
                spokenSummary = "Could not find $nameOrPath to delete."
            )
            fileMatch.isDirectory -> deleteFolder(fileMatch.name)
            else -> deleteFile(fileMatch.name)
        }
    }

    /**
     * Creates or writes a text file.
     */
    fun createFile(
        fileName: String,
        content: String,
        folder: String = "",
        category: String = "Notes"
    ): FileOpResult {
        return try {
            val safeName = sanitizeName(fileName).let {
                if (it.contains(".")) it else "$it.txt"
            }
            val parentDir = resolveSafeFile(folder)
            if (!parentDir.exists()) parentDir.mkdirs()

            val file = File(parentDir, safeName)
            file.writeText(content)

            FileOpResult(
                success = true,
                message = "Created file: ${file.name} (${file.length()} bytes in ${file.parentFile?.name ?: "root"})",
                spokenSummary = "Created file ${file.name}.",
                affectedPath = file.absolutePath,
                fileContent = content
            )
        } catch (e: Exception) {
            FileOpResult(
                success = false,
                message = "Failed to create file: ${e.message}",
                spokenSummary = "Error creating file: ${e.message}"
            )
        }
    }

    /**
     * Reads a text file.
     */
    fun readFile(fileNameOrPath: String): FileOpResult {
        return try {
            var target = resolveSafeFile(fileNameOrPath)
            if (!target.exists() || target.isDirectory) {
                val clean = sanitizeName(fileNameOrPath)
                val candidate = rootWorkspaceDir.walkTopDown()
                    .filter { it.isFile && (it.name.equals(clean, ignoreCase = true) || it.name.startsWith(clean, ignoreCase = true)) }
                    .firstOrNull()
                if (candidate != null) target = candidate
            }

            if (!target.exists()) {
                return FileOpResult(
                    success = false,
                    message = "File not found: $fileNameOrPath",
                    spokenSummary = "Could not find file $fileNameOrPath."
                )
            }

            val text = target.readText()
            val spokenSnippet = if (text.length > 150) text.take(150) + "..." else text
            FileOpResult(
                success = true,
                message = "Read ${target.name} (${target.length()} bytes)",
                spokenSummary = "Content of ${target.name}: $spokenSnippet",
                affectedPath = target.absolutePath,
                fileContent = text
            )
        } catch (e: Exception) {
            FileOpResult(
                success = false,
                message = "Error reading file: ${e.message}",
                spokenSummary = "Error reading file: ${e.message}"
            )
        }
    }

    /**
     * Computes storage usage stats.
     */
    fun getStorageStats(): FileOpResult {
        var totalSize = 0L
        var fileCount = 0
        var folderCount = 0

        rootWorkspaceDir.walkTopDown().forEach {
            if (it != rootWorkspaceDir) {
                if (it.isDirectory) folderCount++
                else {
                    fileCount++
                    totalSize += it.length()
                }
            }
        }

        val formattedSize = when {
            totalSize < 1024 -> "$totalSize bytes"
            totalSize < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", totalSize / 1024.0)
            else -> String.format(Locale.US, "%.1f MB", totalSize / (1024.0 * 1024.0))
        }

        val summary = "Workspace has $folderCount folders and $fileCount files using $formattedSize of storage."
        return FileOpResult(
            success = true,
            message = summary,
            spokenSummary = summary
        )
    }

    /**
     * Executes structured file voice intents.
     */
    fun executeVoiceIntent(
        operation: String,
        target: String = "",
        content: String = "",
        folder: String = ""
    ): FileOpResult {
        return when (operation.uppercase()) {
            "LIST", "LIST_FILES", "LIST_FOLDER" -> {
                val subfolder = if (folder.isNotBlank()) folder else target
                listFiles(subfolder)
            }
            "CREATE_FOLDER", "MAKE_FOLDER", "MKDIR" -> {
                createFolder(folderName = target, parentFolder = folder)
            }
            "DELETE_FILE", "REMOVE_FILE" -> {
                deleteFile(target)
            }
            "DELETE_FOLDER", "REMOVE_FOLDER" -> {
                deleteFolder(target)
            }
            "DELETE", "REMOVE" -> {
                deleteItem(target)
            }
            "CREATE", "CREATE_FILE", "WRITE", "WRITE_FILE" -> {
                createFile(fileName = target, content = content, folder = folder)
            }
            "READ", "READ_FILE", "OPEN", "OPEN_FILE" -> {
                readFile(target)
            }
            "STATS", "COUNT", "STORAGE" -> {
                getStorageStats()
            }
            else -> {
                FileOpResult(
                    success = false,
                    message = "Unknown file system operation: $operation",
                    spokenSummary = "Unknown file operation $operation."
                )
            }
        }
    }

    /**
     * Direct parser and executor for voice sentences involving file system operations.
     */
    fun parseAndExecuteVoiceCommand(command: String): FileOpResult {
        val lower = command.trim().lowercase()

        // 1. List files intent
        if (lower == "list files" || lower == "list all files" || lower == "show files" ||
            lower == "show all files" || lower == "what files do i have" || lower == "show my files" ||
            lower == "list my files" || lower == "list documents" || lower == "show documents"
        ) {
            return listFiles()
        }

        if (lower.startsWith("list files in ") || lower.startsWith("show files in ")) {
            val folder = lower.removePrefix("list files in ").removePrefix("show files in ")
                .removeSuffix(" folder").removeSuffix(" directory").trim()
            return listFiles(folder)
        }

        // 2. Create folder intent
        if (lower.startsWith("create folder ") || lower.startsWith("make folder ") ||
            lower.startsWith("new folder ") || lower.startsWith("create directory ") ||
            lower.startsWith("make directory ") || lower.startsWith("new directory ") ||
            lower.startsWith("mkdir ")
        ) {
            val folderName = lower.removePrefix("create folder ")
                .removePrefix("make folder ")
                .removePrefix("new folder ")
                .removePrefix("create directory ")
                .removePrefix("make directory ")
                .removePrefix("new directory ")
                .removePrefix("mkdir ")
                .removePrefix("named ")
                .removePrefix("called ")
                .trim()
            return createFolder(folderName)
        }

        // 3. Delete folder intent
        if (lower.startsWith("delete folder ") || lower.startsWith("remove folder ") ||
            lower.startsWith("delete directory ") || lower.startsWith("remove directory ")
        ) {
            val folderName = lower.removePrefix("delete folder ")
                .removePrefix("remove folder ")
                .removePrefix("delete directory ")
                .removePrefix("remove directory ")
                .trim()
            return deleteFolder(folderName)
        }

        // 4. Delete file intent
        if (lower.startsWith("delete file ") || lower.startsWith("remove file ") ||
            lower.startsWith("delete document ") || lower.startsWith("remove document ")
        ) {
            val fileName = lower.removePrefix("delete file ")
                .removePrefix("remove file ")
                .removePrefix("delete document ")
                .removePrefix("remove document ")
                .trim()
            return deleteFile(fileName)
        }

        // 5. Create file intent
        if (lower.startsWith("create file ") || lower.startsWith("new file ") ||
            lower.startsWith("write file ") || lower.startsWith("make file ")
        ) {
            val raw = command.trim()
            val remainder = raw.substringAfter(" ").substringAfter(" ")
            val parts = remainder.split(Regex(" with content | containing | : "), 2)
            val name = parts.firstOrNull()?.trim() ?: "Voice_Note.txt"
            val content = if (parts.size > 1) parts[1].trim() else "Created by Super Admin voice command."
            return createFile(fileName = name, content = content)
        }

        // 6. Read file intent
        if (lower.startsWith("read file ") || lower.startsWith("open file ") ||
            lower.startsWith("read document ") || lower.startsWith("view file ")
        ) {
            val fileName = lower.removePrefix("read file ")
                .removePrefix("open file ")
                .removePrefix("read document ")
                .removePrefix("view file ")
                .trim()
            return readFile(fileName)
        }

        // 7. Storage / count stats
        if (lower.contains("file count") || lower.contains("storage stats") || lower.contains("how many files")) {
            return getStorageStats()
        }

        return FileOpResult(
            success = false,
            message = "Unrecognized file command: $command",
            spokenSummary = "I did not recognize the file command '$command'."
        )
    }

    /**
     * Synchronizes all workspace files to the Room database index.
     */
    suspend fun syncAllFilesToRoom() = withContext(Dispatchers.IO) {
        if (repository == null) return@withContext
        val files = rootWorkspaceDir.walkTopDown().filter { it.isFile }.toList()
        for (f in files) {
            try {
                val preview = try {
                    val txt = f.readText()
                    if (txt.length > 100) txt.take(100) + "..." else txt
                } catch (_: Exception) { "" }

                val cat = when {
                    f.extension.lowercase() in listOf("md", "txt") -> "Notes"
                    f.extension.lowercase() in listOf("json", "xml", "conf") -> "Configs"
                    else -> "Docs"
                }

                repository.insertDocument(
                    IndexedDocumentEntity(
                        fileName = f.name,
                        filePath = f.absolutePath,
                        fileSize = f.length(),
                        lastModified = f.lastModified(),
                        contentPreview = preview,
                        category = cat
                    )
                )
            } catch (_: Exception) {}
        }
    }
}
