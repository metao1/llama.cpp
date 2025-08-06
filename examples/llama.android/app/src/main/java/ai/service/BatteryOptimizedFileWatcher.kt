package com.metao.ai.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.metao.ai.domain.model.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Battery-optimized file watcher using efficient approaches:
 * 1. Recursive FileObserver for directory monitoring (no polling)
 * 2. MediaStore ContentObserver for media files
 * 3. Dynamic FileObserver registration for new directories
 */
class BatteryOptimizedFileWatcher(
    private val context: Context,
    private val onFilesDetected: (List<FileItem>) -> Unit
) {
    companion object {
        private const val TAG = "BatteryOptimizedWatcher"
        private const val MAX_DEPTH = 5 // Prevent infinite recursion
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaObserver: MediaStoreObserver? = null
    private val fileObservers = mutableMapOf<String, RecursiveFileObserver>()
    private var isActive = false

    fun startWatching(directories: List<String>) {
        if (isActive) return

        Log.d(TAG, "Starting battery-optimized file watching for ${directories.size} directories")
        isActive = true

        // 1. Use MediaStore ContentObserver for media files (most efficient)
        startMediaStoreObserver()

        // 2. Start recursive FileObservers for each directory
        directories.forEach { path ->
            startRecursiveFileObserver(path, 0)
        }

        Log.d(TAG, "Battery-optimized file watching started with ${fileObservers.size} observers")
    }

    fun stopWatching() {
        if (!isActive) return

        Log.d(TAG, "Stopping battery-optimized file watching")
        isActive = false

        // Stop MediaStore observer
        mediaObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            mediaObserver = null
        }

        // Stop all FileObservers
        fileObservers.values.forEach { it.stopWatching() }
        fileObservers.clear()

        Log.d(TAG, "Battery-optimized file watching stopped")
    }

    private fun startMediaStoreObserver() {
        mediaObserver = MediaStoreObserver(Handler(Looper.getMainLooper())) { uri ->
            scope.launch {
                handleMediaStoreChange(uri)
            }
        }

        // Register for different media types
        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external")
        )

        uris.forEach { uri ->
            context.contentResolver.registerContentObserver(
                uri, true, mediaObserver!!
            )
        }

        Log.d(TAG, "MediaStore observer registered for ${uris.size} content types")
    }

    private suspend fun handleMediaStoreChange(uri: Uri) {
        try {
            Log.d(TAG, "MediaStore change detected: $uri")

            // Query recent files from MediaStore
            val recentFiles = queryRecentMediaFiles()

            if (recentFiles.isNotEmpty()) {
                Log.d(TAG, "Found ${recentFiles.size} recent media files")
                onFilesDetected(recentFiles)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling MediaStore change", e)
        }
    }

    private fun queryRecentMediaFiles(): List<FileItem> {
        val files = mutableListOf<FileItem>()
        val cutoffTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5) // Last 5 minutes

        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.MIME_TYPE
            )

            val selection = "${MediaStore.Files.FileColumns.DATE_ADDED} > ?"
            val selectionArgs = arrayOf((cutoffTime / 1000).toString()) // MediaStore uses seconds

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                while (cursor.moveToNext() && files.size < 10) { // Limit to 10 recent files
                    val path = cursor.getString(dataIndex)
                    val name = cursor.getString(nameIndex)
                    val size = cursor.getLong(sizeIndex)
                    val dateAdded = cursor.getLong(dateIndex) * 1000 // Convert to milliseconds
                    val mimeType = cursor.getString(mimeIndex)

                    val file = File(path)
                    if (file.exists() && file.isFile) {
                        files.add(
                            FileItem(
                                file = file,
                                name = name ?: file.name,
                                path = path,
                                sizeBytes = size,
                                extension = file.extension,
                                mimeType = mimeType,
                                lastModified = java.util.Date(dateAdded),
                                isDirectory = false,
                                contentPreview = null
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying recent media files", e)
        }

        return files
    }

    private fun startRecursiveFileObserver(directoryPath: String, depth: Int) {
        if (depth > MAX_DEPTH) {
            Log.w(TAG, "Max depth reached for directory: $directoryPath")
            return
        }

        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
            Log.w(TAG, "Cannot watch directory: $directoryPath")
            return
        }

        try {
            val observer = RecursiveFileObserver(directoryPath, depth) { event, path ->
                handleFileSystemEvent(event, path, depth)
            }
            observer.startWatching()
            fileObservers[directoryPath] = observer

            Log.d(TAG, "Started FileObserver for: $directoryPath (depth: $depth)")

            // Recursively watch existing subdirectories
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory && !file.name.startsWith(".")) {
                    startRecursiveFileObserver(file.absolutePath, depth + 1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting FileObserver for $directoryPath", e)
        }
    }

    private fun handleFileSystemEvent(event: Int, filePath: String, depth: Int) {
        scope.launch {
            try {
                val file = File(filePath)

                when (event and FileObserver.ALL_EVENTS) {
                    FileObserver.CREATE, FileObserver.MOVED_TO -> {
                        if (file.isDirectory) {
                            // New directory created - start watching it
                            Log.d(TAG, "New directory detected: $filePath")
                            startRecursiveFileObserver(filePath, depth + 1)
                        } else if (file.isFile && file.canRead()) {
                            // New file created
                            Log.d(TAG, "New file detected: $filePath")
                            val fileItem = createFileItem(file)
                            onFilesDetected(listOf(fileItem))
                        }
                    }
                    FileObserver.DELETE, FileObserver.MOVED_FROM -> {
                        if (file.isDirectory) {
                            // Directory deleted - stop watching it
                            Log.d(TAG, "Directory deleted: $filePath")
                            stopWatchingDirectory(filePath)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling file system event for $filePath", e)
            }
        }
    }

    private fun stopWatchingDirectory(directoryPath: String) {
        fileObservers[directoryPath]?.let { observer ->
            observer.stopWatching()
            fileObservers.remove(directoryPath)
            Log.d(TAG, "Stopped watching directory: $directoryPath")
        }

        // Also stop watching any subdirectories
        val subdirectoriesToRemove = fileObservers.keys.filter { it.startsWith("$directoryPath/") }
        subdirectoriesToRemove.forEach { subdir ->
            fileObservers[subdir]?.stopWatching()
            fileObservers.remove(subdir)
            Log.d(TAG, "Stopped watching subdirectory: $subdir")
        }
    }

    private fun createFileItem(file: File): FileItem {
        return FileItem(
            file = file,
            name = file.name,
            path = file.absolutePath,
            sizeBytes = file.length(),
            extension = file.extension,
            mimeType = null, // Could be determined later
            lastModified = java.util.Date(file.lastModified()),
            isDirectory = false,
            contentPreview = null
        )
    }

    private class MediaStoreObserver(
        handler: Handler,
        private val onChange: (Uri) -> Unit
    ) : ContentObserver(handler) {

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            uri?.let { onChange(it) }
        }
    }

    /**
     * Recursive FileObserver that watches a directory and handles CREATE events
     * for both files and subdirectories
     */
    private class RecursiveFileObserver(
        private val path: String,
        private val depth: Int,
        private val onEvent: (Int, String) -> Unit
    ) : FileObserver(path, CREATE or MOVED_TO or DELETE or MOVED_FROM) {

        companion object {
            private const val TAG = "RecursiveFileObserver"
        }

        override fun onEvent(event: Int, fileName: String?) {
            if (fileName != null && !fileName.startsWith(".")) {
                val fullPath = "$path/$fileName"
                Log.v(TAG, "FileObserver event: $event for $fullPath (depth: $depth)")
                onEvent(event, fullPath)
            }
        }
    }
}


