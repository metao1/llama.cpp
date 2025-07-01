package com.example.llama

import android.app.DownloadManager
import android.net.Uri
import android.os.StatFs
import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.database.getLongOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

data class Downloadable(val name: String, val source: Uri, val destination: File) {
    companion object {
        @JvmStatic
        private val tag: String? = this::class.qualifiedName

        sealed interface State
        data object Ready: State
        data class Downloading(val id: Long): State
        data class Downloaded(val downloadable: Downloadable): State
        data class Error(val message: String): State

        private fun validateGGUFFile(file: File): Boolean {
            if (!file.exists()) {
                Log.d(tag, "File does not exist: ${file.path}")
                return false
            }

            val fileSize = file.length()
            if (fileSize < 1024 * 1024) { // Less than 1MB is definitely too small for a model
                Log.w(tag, "File too small to be a valid model: ${fileSize} bytes")
                return false
            }

            try {
                FileInputStream(file).use { fis ->
                    val magic = ByteArray(4)
                    val bytesRead = fis.read(magic)
                    if (bytesRead != 4) {
                        Log.w(tag, "Could not read magic bytes, only read $bytesRead bytes")
                        return false
                    }

                    // GGUF magic number is "GGUF" (0x47475546)
                    val isValid = magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))

                    if (isValid) {
                        Log.d(
                            tag,
                            "GGUF file validation passed: ${file.path} (${formatBytes(fileSize)})"
                        )
                    } else {
                        Log.w(
                            tag,
                            "Invalid GGUF magic bytes: ${magic.joinToString(" ") { "%02x".format(it) }}"
                        )
                    }

                    return isValid
                }
            } catch (e: Exception) {
                Log.e(tag, "Error validating GGUF file ${file.path}: ${e.message}")
                // Don't automatically assume the file is bad if we can't read it
                // It might be a permission issue or the file is being used
                return true
            }
        }

        private fun getAvailableStorageSpace(file: File): Long {
            return try {
                val stat = StatFs(file.parentFile!!.absolutePath)
                stat.availableBlocksLong * stat.blockSizeLong
            } catch (e: Exception) {
                Log.e("TAG", "Error getting available storage space: ${e.message}")
                0L
            }
        }

        private fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
                bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
                else -> "$bytes bytes"
            }
        }

        @JvmStatic
        @Composable
        fun Button(
            viewModel: MainViewModel,
            dm: DownloadManager,
            item: Downloadable,
            hasStoragePermissions: () -> Boolean,
            requestStoragePermissions: () -> Unit
        ) {
            val context = LocalContext.current
            var status: State by remember {
                mutableStateOf(
                    if (item.destination.exists()) {
                        val fileSize = item.destination.length()
                        Log.d(tag, "Found existing file: ${item.destination.path} (${formatBytes(fileSize)})")

                        if (fileSize < 1024 * 1024) { // Less than 1MB
                            Log.w(tag, "File too small, deleting: ${formatBytes(fileSize)}")
                            item.destination.delete()
                            Ready
                        } else if (validateGGUFFile(item.destination)) {
                            Log.d(tag, "File validation passed, marking as downloaded")
                            Downloaded(item)
                        } else {
                            Log.w(tag, "File validation failed, but keeping file (might be valid)")
                            // Don't delete the file immediately - let user decide
                            Downloaded(item)
                        }
                    } else {
                        Log.d(tag, "No existing file found: ${item.destination.path}")
                        Ready
                    }
                )
            }
            var progress by remember { mutableDoubleStateOf(0.0) }

            val coroutineScope = rememberCoroutineScope()

            suspend fun waitForDownload(result: Downloading, item: Downloadable): State {
                while (true) {
                    val cursor = dm.query(DownloadManager.Query().setFilterById(result.id))

                    if (cursor == null) {
                        Log.e(tag, "dm.query() returned null")
                        return Error("dm.query() returned null")
                    }

                    if (!cursor.moveToFirst() || cursor.count < 1) {
                        cursor.close()
                        Log.i(tag, "cursor.moveToFirst() returned false or cursor.count < 1, download canceled?")
                        return Ready
                    }

                    val pix = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val tix = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                    val sofar = cursor.getLongOrNull(pix) ?: 0
                    val total = cursor.getLongOrNull(tix) ?: 1
                    val status = cursor.getInt(statusIndex)
                    val reason = cursor.getInt(reasonIndex)

                    cursor.close()

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            // Verify file integrity
                            if (item.destination.exists() && item.destination.length() > 0) {
                                Log.i(tag, "Download completed successfully: ${item.destination.path}, size: ${item.destination.length()} bytes")
                                return Downloaded(item)
                            } else {
                                Log.e(tag, "Download marked successful but file is missing or empty")
                                return Error("Downloaded file is missing or empty")
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val errorMessage = when (reason) {
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space. Please free up space and try again."
                                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "External storage not found or not mounted."
                                DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists."
                                DownloadManager.ERROR_FILE_ERROR -> "File system error occurred."
                                DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error during download."
                                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many HTTP redirects."
                                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP response code."
                                DownloadManager.ERROR_UNKNOWN -> "Unknown download error."
                                DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download."
                                else -> "Download failed with error code: $reason"
                            }
                            Log.e(tag, "Download failed: $errorMessage (code: $reason)")
                            return Error(errorMessage)
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            Log.w(tag, "Download paused with reason: $reason")
                        }
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING -> {
                            // Continue monitoring
                        }
                    }

                    if (total > 0) {
                        progress = (sofar * 1.0) / total
                        Log.d(tag, "Download progress: $sofar / $total bytes (${(progress * 100).toInt()}%)")
                    }

                    delay(1000L)
                }
            }

            fun onClick() {
                when (val s = status) {
                    is Downloaded -> {
                        // Validate file before loading
                        if (!item.destination.exists()) {
                            viewModel.log("Error: Model file not found at ${item.destination.path}")
                            status = Ready
                            return
                        }

                        val fileSize = item.destination.length()
                        if (fileSize < 1024) { // File should be at least 1KB
                            viewModel.log("Error: Model file appears to be corrupted (size: $fileSize bytes)")
                            status = Ready
                            return
                        }

                        // Let the native llama.cpp code validate the file
                        // Our validation might be too strict
                        viewModel.log("File exists, attempting to load...")

                        viewModel.log("Loading model from ${item.destination.path} (size: ${fileSize / (1024 * 1024)} MB)")
                        viewModel.load(item.destination.path)
                    }

                    is Downloading -> {
                        coroutineScope.launch {
                            status = waitForDownload(s, item)
                        }
                    }

                    is Error -> {
                        // Reset to Ready state to allow retry
                        status = Ready
                        onClick()
                    }

                    else -> {
                        // Check permissions before starting download
                        if (!hasStoragePermissions()) {
                            viewModel.log("Storage permissions required for downloading")
                            requestStoragePermissions()
                            return
                        }

                        // Check available storage space (with some buffer)
                        val availableSpace = getAvailableStorageSpace(item.destination)
                        val requiredSpace = when {
                            item.name.contains("Phi-2 7B") -> 1800L * 1024 * 1024 // ~1.8 GB (1.6 + buffer)
                            item.name.contains("TinyLlama") -> 2500L * 1024 * 1024 // ~2.5 GB (2.2 + buffer)
                            item.name.contains("Phi 2 DPO") -> 1700L * 1024 * 1024 // ~1.7 GB (1.5 + buffer)
                            else -> 2200L * 1024 * 1024 // Default 2.2 GB
                        }

                        viewModel.log("Storage check: Available: ${formatBytes(availableSpace)}, Required: ${formatBytes(requiredSpace)}")

                        if (availableSpace < requiredSpace) {
                            val message = "Insufficient storage space. Available: ${formatBytes(availableSpace)}, Required: ${formatBytes(requiredSpace)}. Please free up space."
                            viewModel.log(message)
                            status = Error(message)
                            return
                        }

                        item.destination.delete()

                        // Ensure parent directory exists
                        item.destination.parentFile?.mkdirs()

                        val request = DownloadManager.Request(item.source).apply {
                            setTitle("Downloading model")
                            setDescription("Downloading model: ${item.name}")
                            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                            setAllowedOverMetered(true)
                            setAllowedOverRoaming(true)

                            // Use setDestinationInExternalFilesDir which is more reliable
                            setDestinationInExternalFilesDir(
                                context,
                                null,
                                item.destination.name
                            )
                        }

                        viewModel.log("Saving ${item.name} to ${item.destination.path}")
                        viewModel.log("Available storage: ${formatBytes(availableSpace)}")
                        Log.i(tag, "Saving ${item.name} to ${item.destination.path}")

                        val id = dm.enqueue(request)
                        status = Downloading(id)
                        onClick()
                    }
                }
            }

            Row {
                Button(onClick = { onClick() }, enabled = status !is Downloading) {
                    when (status) {
                        is Downloading -> Text(text = "Downloading ${(progress * 100).toInt()}%")
                        is Downloaded -> Text("Load ${item.name}")
                        is Ready -> {
                            if (hasStoragePermissions()) {
                                Text("Download ${item.name}")
                            } else {
                                Text("Grant Permissions & Download ${item.name}")
                            }
                        }
                        is Error -> Text("Retry Download ${item.name}")
                    }
                }

                // Add delete button for downloaded models
                if (status is Downloaded) {
                    IconButton(
                        onClick = {
                            item.destination.delete()
                            status = Ready
                            viewModel.log("Deleted ${item.name}")
                        },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete"
                        )
                    }
                }
            }
        }

    }
}
